package com.autoapplicant.adapter.render;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Turns the builder's rich text (the editor's HTML, or plain text from generation) into
 * paragraphs and lists of styled runs, which the Typst templates lay out as data. User text never
 * becomes Typst source, and anything the editor cannot produce (links, images, scripts, unknown
 * tags) contributes only its text. A colour or size outside the allowed form is dropped, and a
 * font only survives when it names a bundled family.
 */
final class TypstRichText {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern SHORT_HEX = Pattern.compile("#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])");
    private static final Pattern RGB = Pattern.compile("rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})[^)]*\\)");
    private static final Pattern STYLE_COLOR = Pattern.compile("(?:^|;)\\s*color\\s*:\\s*([^;]+)");
    private static final Pattern STYLE_SIZE = Pattern.compile("(?:^|;)\\s*font-size\\s*:\\s*([0-9.]+)px");
    private static final Pattern STYLE_FONT =
            Pattern.compile("(?:^|;)\\s*font-family\\s*:\\s*([^;,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_ALIGN =
            Pattern.compile("(?:^|;)\\s*text-align\\s*:\\s*(left|center|right|justify)\\b");
    /** Editor font sizes outside this range are typing mistakes, not design. */
    private static final double MIN_SIZE = 6;
    private static final double MAX_SIZE = 48;
    private static final int MAX_CHANNEL = 255;

    private static final Set<String> BLOCKS = Set.of("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote");
    private static final Set<String> LISTS = Set.of("ul", "ol");
    private static final Set<String> BOLD = Set.of("strong", "b");
    private static final Set<String> ITALIC = Set.of("em", "i");
    private static final Set<String> STRIKE = Set.of("s", "strike", "del");

    private TypstRichText() {}

    sealed interface Block permits Paragraph, ListBlock {}

    /** @param align left, center, right or justify as the editor set it, or null for the default */
    record Paragraph(String kind, String align, List<Run> runs) implements Block {
        Paragraph(String align, List<Run> runs) {
            this("par", align, runs);
        }

        Paragraph(List<Run> runs) {
            this(null, runs);
        }
    }

    record ListBlock(String kind, boolean ordered, List<List<Run>> items) implements Block {
        ListBlock(boolean ordered, List<List<Run>> items) {
            this("list", ordered, items);
        }
    }

    /**
     * One stretch of text with one style; a line break is a run of its own.
     *
     * @param font a bundled Typst family, or null for the surrounding one
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    record Run(String text, boolean bold, boolean italic, boolean underline, boolean strike, boolean br,
               String color, Double size, String font) {

        static Run lineBreak() {
            return new Run("", false, false, false, false, true, null, null, null);
        }
    }

    private record Style(boolean bold, boolean italic, boolean underline, boolean strike, String color, Double size,
                         String font) {

        static final Style PLAIN = new Style(false, false, false, false, null, null, null);

        Run run(String text) {
            return new Run(text, bold, italic, underline, strike, false, color, size, font);
        }

        /** This style with what {@code element}'s tag and inline CSS add to it. */
        Style inside(Element element) {
            String tag = element.normalName();
            String style = element.attr("style");
            String css = style.toLowerCase(Locale.ROOT);
            String ownColor = colorFrom(css);
            Double ownSize = sizeFrom(css);
            // Read from the original: font names are matched case-sensitively against the bundle.
            String ownFont = fontFrom(style);
            return new Style(bold || BOLD.contains(tag), italic || ITALIC.contains(tag),
                    underline || "u".equals(tag), strike || STRIKE.contains(tag),
                    ownColor != null ? ownColor : color, ownSize != null ? ownSize : size,
                    ownFont != null ? ownFont : font);
        }
    }

    static List<Block> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        if (!value.contains("<")) return plain(value);
        return new Collector().collect(Jsoup.parseBodyFragment(value).body().childNodes());
    }

    private static List<Block> plain(String value) {
        return value.lines()
                .filter(line -> !line.isBlank())
                .<Block>map(line -> new Paragraph(List.of(Style.PLAIN.run(AtsText.normalize(line.strip())))))
                .toList();
    }

    /** Walks block-level HTML, gathering loose inline content into paragraphs between blocks. */
    private static final class Collector {

        private final List<Block> blocks = new ArrayList<>();
        private final List<Run> runs = new ArrayList<>();
        private String align;

        List<Block> collect(List<Node> nodes) {
            nodes.forEach(this::visit);
            flush();
            return blocks;
        }

        private void visit(Node node) {
            if (isList(node)) {
                flush();
                addList((Element) node);
            } else if (node instanceof Element element && BLOCKS.contains(element.normalName())) {
                addBlock(element);
            } else {
                inline(node, Style.PLAIN, runs);
            }
        }

        /** A block's paragraphs take its alignment, or the enclosing block's when it sets none. */
        private void addBlock(Element block) {
            flush();
            String outer = align;
            String own = alignFrom(block);
            align = own != null ? own : outer;
            block.childNodes().forEach(this::visit);
            flush();
            align = outer;
        }

        private void addList(Element list) {
            List<List<Run>> items = new ArrayList<>();
            listItems(list, items);
            if (!items.isEmpty()) blocks.add(new ListBlock("ol".equals(list.normalName()), items));
        }

        private void flush() {
            if (hasText(runs)) blocks.add(new Paragraph(align, List.copyOf(runs)));
            runs.clear();
        }
    }

    /**
     * Nested lists flatten into the outer one: the layouts have one bullet depth. A list item's
     * alignment is not kept, so the bullets stay in line down the left edge.
     */
    private static void listItems(Element list, List<List<Run>> items) {
        for (Element li : list.children()) {
            items.addAll(item(li));
        }
    }

    /** One list item's runs, followed by the items of any list nested inside it. */
    private static List<List<Run>> item(Element li) {
        ItemCollector collector = new ItemCollector();
        li.childNodes().forEach(collector::visit);
        return collector.finish();
    }

    /** Gathers one list item; a nested list closes the text before it as an item of its own. */
    private static final class ItemCollector {

        private final List<List<Run>> items = new ArrayList<>();
        private List<Run> runs = new ArrayList<>();

        void visit(Node child) {
            if (isList(child)) {
                closeItem();
                listItems((Element) child, items);
            } else {
                itemContent(child, runs);
            }
        }

        List<List<Run>> finish() {
            closeItem();
            return items;
        }

        private void closeItem() {
            if (hasText(runs)) items.add(List.copyOf(runs));
            runs = new ArrayList<>();
        }
    }

    /** The editor wraps item text in a paragraph; a second paragraph continues on a new line. */
    private static void itemContent(Node child, List<Run> runs) {
        if (child instanceof Element para && "p".equals(para.normalName())) {
            if (hasText(runs)) runs.add(Run.lineBreak());
            para.childNodes().forEach(inner -> inline(inner, Style.PLAIN, runs));
        } else {
            inline(child, Style.PLAIN, runs);
        }
    }

    private static void inline(Node node, Style style, List<Run> runs) {
        if (node instanceof TextNode text) {
            addText(text, style, runs);
        } else if (node instanceof Element element) {
            inlineElement(element, style, runs);
        }
    }

    private static void addText(TextNode text, Style style, List<Run> runs) {
        String content = AtsText.normalize(text.text());
        if (!content.isEmpty()) runs.add(style.run(content));
    }

    private static void inlineElement(Element element, Style style, List<Run> runs) {
        if ("br".equals(element.normalName())) {
            runs.add(Run.lineBreak());
            return;
        }
        Style inner = style.inside(element);
        element.childNodes().forEach(child -> inline(child, inner, runs));
    }

    private static String colorFrom(String css) {
        Matcher m = STYLE_COLOR.matcher(css);
        return m.find() ? color(m.group(1).trim()) : null;
    }

    /** A {@code #rrggbb} colour from the forms the editor writes, or null for anything else. */
    static String color(String value) {
        if (value == null) return null;
        if (HEX.matcher(value).matches()) return value.toLowerCase(Locale.ROOT);
        Matcher shortHex = SHORT_HEX.matcher(value);
        if (shortHex.matches()) {
            return hex(shortHex.group(1).repeat(2), shortHex.group(2).repeat(2), shortHex.group(3).repeat(2));
        }
        Matcher rgb = RGB.matcher(value);
        return rgb.matches() ? fromChannels(rgb.group(1), rgb.group(2), rgb.group(3)) : null;
    }

    private static String fromChannels(String r, String g, String b) {
        int red = Integer.parseInt(r);
        int green = Integer.parseInt(g);
        int blue = Integer.parseInt(b);
        if (red > MAX_CHANNEL || green > MAX_CHANNEL || blue > MAX_CHANNEL) return null;
        return String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue);
    }

    private static String hex(String r, String g, String b) {
        return ("#" + r + g + b).toLowerCase(Locale.ROOT);
    }

    /** The first family the style names, when it is one Typst has; anything else is dropped. */
    private static String fontFrom(String style) {
        Matcher m = STYLE_FONT.matcher(style);
        return m.find() ? TypstFonts.bundled(m.group(1).replace("\"", "").replace("'", ""), null) : null;
    }

    private static String alignFrom(Element element) {
        Matcher m = STYLE_ALIGN.matcher(element.attr("style").toLowerCase(Locale.ROOT));
        return m.find() ? m.group(1) : null;
    }

    private static Double sizeFrom(String css) {
        Matcher m = STYLE_SIZE.matcher(css);
        if (!m.find()) return null;
        try {
            double px = Double.parseDouble(m.group(1));
            return px >= MIN_SIZE && px <= MAX_SIZE ? px : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isList(Node node) {
        return node instanceof Element element && LISTS.contains(element.normalName());
    }

    private static boolean hasText(List<Run> runs) {
        return runs.stream().anyMatch(run -> !run.br() && !run.text().isBlank());
    }
}
