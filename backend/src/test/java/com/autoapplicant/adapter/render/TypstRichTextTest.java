package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.render.TypstRichText.Block;
import com.autoapplicant.adapter.render.TypstRichText.ListBlock;
import com.autoapplicant.adapter.render.TypstRichText.Paragraph;
import com.autoapplicant.adapter.render.TypstRichText.Run;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The editor's HTML keeps its words, emphasis, lists and colours on the way to Typst. */
class TypstRichTextTest {

    /** Bug caught: text inside formatting tags is lost, or the formatting is. */
    @Test
    void keepsEmphasisOnTheRightWords() {
        List<Block> blocks = TypstRichText.parse("<p>Jeg leder <strong>klart sprog</strong> og <em>compliance</em>.</p>");

        List<Run> runs = ((Paragraph) blocks.get(0)).runs();
        assertThat(text(runs)).isEqualTo("Jeg leder klart sprog og compliance.");
        assertThat(runs).filteredOn(Run::bold).extracting(Run::text).containsExactly("klart sprog");
        assertThat(runs).filteredOn(Run::italic).extracting(Run::text).containsExactly("compliance");
    }

    /** Bug caught: a nested list, or a list item wrapped in a paragraph, drops its bullets. */
    @Test
    void flattensNestedListsWithoutLosingItems() {
        List<Block> blocks = TypstRichText.parse(
                "<ul><li><p>Først</p><ul><li>Indlejret</li></ul></li><li>Sidst</li></ul>");

        ListBlock list = (ListBlock) blocks.get(0);
        assertThat(list.items()).extracting(TypstRichTextTest::text).containsExactly("Først", "Indlejret", "Sidst");
    }

    /** Bug caught: plain text from generation (one paragraph per line) collapses into one paragraph. */
    @Test
    void plainTextBecomesOneParagraphPerLine() {
        List<Block> blocks = TypstRichText.parse("Første linje\n\nAnden linje");

        assertThat(blocks).extracting(b -> text(((Paragraph) b).runs())).containsExactly("Første linje", "Anden linje");
    }

    /** Bug caught: the editor's rgb() colour is passed through unvalidated, or valid colours are dropped. */
    @Test
    void normalisesEditorColoursAndDropsAnythingElse() {
        assertThat(TypstRichText.color("rgb(255, 0, 16)")).isEqualTo("#ff0010");
        assertThat(TypstRichText.color("#ABC")).isEqualTo("#aabbcc");
        assertThat(TypstRichText.color("rgb(300, 0, 0)")).isNull();
        assertThat(TypstRichText.color("red\") + read(\"x")).isNull();
    }

    /** Bug caught: a script's source ends up printed in the CV. */
    @Test
    void scriptsContributeNoText() {
        List<Block> blocks = TypstRichText.parse("<p>Hej<script>alert(1)</script></p>");

        assertThat(text(((Paragraph) blocks.get(0)).runs())).isEqualTo("Hej");
    }

    private static String text(List<Run> runs) {
        return runs.stream().map(Run::text).collect(Collectors.joining());
    }
}
