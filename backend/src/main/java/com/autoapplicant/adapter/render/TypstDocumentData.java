package com.autoapplicant.adapter.render;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentRenderOptions;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The JSON the built-in template reads ({@code structured-document.typ}); field names are its
 * contract. Text is ATS-normalized here because the PDF text layer is what parsers read.
 */
record TypstDocumentData(
        String kind,
        boolean designed,
        boolean showPhoto,
        TypstTheme theme,
        Identity identity,
        List<Section> sections,
        List<String> paragraphs) {

    private static final String KIND_CV = "cv";
    private static final String KIND_LETTER = "letter";
    private static final String DESIGNED = "DESIGNED";
    private static final String SKILLS = "skills";
    private static final int MAX_INITIALS = 2;

    record Identity(String name, String headline, List<String> contacts, String photo, String initials) {}

    record Section(String type, String heading, String body, boolean compact, List<Item> items) {}

    record Item(
            String title,
            String subtitle,
            String dateRange,
            String description,
            List<String> bullets,
            List<String> technologies) {}

    /** @param photo the staged image's path inside the workspace root, or null for none */
    static TypstDocumentData from(StructuredDocument document, String photo) {
        boolean cv = document.documentType() == DocumentType.CV;
        DocumentRenderOptions options = document.options();
        return new TypstDocumentData(
                cv ? KIND_CV : KIND_LETTER,
                isDesigned(document),
                options != null && options.showProfileImage(),
                TypstTheme.from(options != null ? options.theme() : null),
                identity(document.identity(), photo),
                cv ? sections(document.sections()) : List.of(),
                cv ? List.of() : paragraphs(document.bodyContent()));
    }

    /**
     * Designed layouts show a header band and, when asked for, the profile photo. The export mode
     * is already derived from the template when the document is built.
     */
    static boolean isDesigned(StructuredDocument document) {
        return DESIGNED.equalsIgnoreCase(document.exportMode());
    }

    private static Identity identity(DocumentIdentity identity, String photo) {
        if (identity == null) return new Identity("", null, List.of(), null, "");
        List<String> contacts = Stream.of(identity.email(), identity.phone(), identity.location(),
                        identity.linkedinUrl(), identity.githubUrl(), identity.websiteUrl())
                .filter(TypstDocumentData::present)
                .map(AtsText::normalize)
                .toList();
        String name = text(identity.name());
        return new Identity(name, AtsText.normalize(identity.headline()), contacts, photo, initials(name));
    }

    private static List<Section> sections(List<StructuredDocumentSection> sections) {
        if (sections == null) return List.of();
        return sections.stream().map(TypstDocumentData::section).toList();
    }

    private static Section section(StructuredDocumentSection section) {
        boolean compact = SKILLS.equals(section.type()) || StructuredDocumentSection.TYPE_CUSTOM.equals(section.type());
        List<StructuredDocumentItem> items = section.items() != null ? section.items() : List.of();
        return new Section(section.type(), text(section.heading()), AtsText.normalize(section.body()),
                compact, items.stream().map(TypstDocumentData::item).toList());
    }

    private static Item item(StructuredDocumentItem item) {
        return new Item(
                text(item.title()),
                AtsText.normalize(item.subtitle()),
                AtsText.normalize(item.dateRange()),
                AtsText.normalize(item.description()),
                normalized(item.bullets()),
                normalized(item.technologies()));
    }

    private static List<String> paragraphs(String content) {
        if (content == null || content.isBlank()) return List.of();
        return Arrays.stream(content.split("\\R\\s*\\R|\\R"))
                .filter(part -> !part.isBlank())
                .map(part -> AtsText.normalize(part.trim()))
                .toList();
    }

    /** Normalized text for a field the template always prints, so it is never null. */
    private static String text(String value) {
        return Objects.requireNonNullElse(AtsText.normalize(value), "");
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(TypstDocumentData::present).map(AtsText::normalize).toList();
    }

    private static String initials(String name) {
        List<String> letters = new ArrayList<>();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isBlank() && letters.size() < MAX_INITIALS) {
                letters.add(Character.toString(part.codePointAt(0)).toUpperCase(Locale.ROOT));
            }
        }
        return String.join("", letters);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
