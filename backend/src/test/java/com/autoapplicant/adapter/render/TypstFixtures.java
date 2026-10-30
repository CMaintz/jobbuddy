package com.autoapplicant.adapter.render;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.DocumentIdentity;
import com.autoapplicant.domain.document.structured.DocumentRenderOptions;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Real Typst renders for the adapter tests. The binary comes from {@code TYPST_PATH} or PATH
 * (mise provides it locally and in CI), exactly as the application finds it.
 */
final class TypstFixtures {

    static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final String NAME = "Jane Doe";
    static final String HEADLINE = "Platform engineer";
    static final String EXPERIENCE = "Experience";
    static final String SKILLS = "Skills";
    static final String ROLE = "Lead Developer";
    static final String COMPANY = "Acme";
    static final String SKILL = "Kubernetes";

    private static final TypstAssets ASSETS = createAssets();

    private TypstFixtures() {}

    static TypstAssets assets() {
        return ASSETS;
    }

    static String typstPath() {
        String configured = System.getenv("TYPST_PATH");
        return configured != null && !configured.isBlank() ? configured : "typst";
    }

    static TypstCompiler compiler(Duration timeout) {
        return new TypstCompiler(new TypstCommand(typstPath(), ASSETS), timeout);
    }

    static TypstRendererAdapter adapter(Path uploads) {
        return new TypstRendererAdapter(ASSETS, compiler(TIMEOUT),
                new TypstImageStager(new UploadsFiles(uploads)));
    }

    static TypstRendererAdapter adapter() {
        return new TypstRendererAdapter(ASSETS, compiler(TIMEOUT), new TypstImageStager());
    }

    static StructuredDocument cv(String name, DocumentTheme theme) {
        StructuredDocumentItem role = new StructuredDocumentItem(null, ROLE, COMPANY, null, "2020 - 2024",
                "Ran the platform team.", List.of("Cut deploy time in half"), List.of("Java"), List.of(),
                List.of(), null);
        StructuredDocumentItem skill = new StructuredDocumentItem(null, SKILL, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null);
        return new StructuredDocument(null, DocumentType.CV, "ATS", "ats-classic", identity(name, null),
                new DocumentRenderOptions(false, theme),
                List.of(new StructuredDocumentSection("exp", "experience", EXPERIENCE, null, List.of(role)),
                        new StructuredDocumentSection("skills", "skills", SKILLS, null, List.of(skill))),
                null, null);
    }

    /** A designed CV that shows the profile photo, so the image reference is staged. */
    static StructuredDocument designedCvWithPhoto(String photoUrl) {
        return new StructuredDocument(null, DocumentType.CV, "DESIGNED", "modern-sidebar",
                identity(NAME, photoUrl), new DocumentRenderOptions(true, DocumentTheme.defaults()),
                List.of(), null, null);
    }

    static StructuredDocument letter(String body) {
        return new StructuredDocument(null, DocumentType.COVER_LETTER, "ATS", "ats-letter",
                identity(NAME, null), DocumentRenderOptions.defaults(), List.of(), body, null);
    }

    static DocumentIdentity identity(String name, String photoUrl) {
        return new DocumentIdentity(name, HEADLINE, "jane@example.com", null, "Copenhagen", null, null,
                null, photoUrl);
    }

    static String text(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    static List<String> fontsOnFirstPage(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            PDResources resources = document.getPage(0).getResources();
            List<String> names = new ArrayList<>();
            for (COSName name : resources.getFontNames()) {
                names.add(resources.getFont(name).getName());
            }
            return names;
        }
    }

    private static TypstAssets createAssets() {
        try {
            return new TypstAssets();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
