package com.autoapplicant.adapter.render;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.out.document.DocumentRenderException;
import com.autoapplicant.port.out.document.DocumentRendererPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * Renders structured documents with the Typst CLI and the built-in template. Each render gets
 * its own workspace holding the template, the document as JSON and any staged image, and the
 * workspace is deleted afterwards.
 */
@Component
public class TypstRendererAdapter implements DocumentRendererPort {

    private static final String PDF_FILE = "document.pdf";
    /** Typst numbers the pages; zero padding keeps name order equal to page order. */
    private static final String SVG_PAGE_PATTERN = "page-{0p}.svg";

    private final TypstAssets assets;
    private final TypstCompiler compiler;
    private final TypstImageStager images;
    private final ObjectMapper json = new ObjectMapper();

    TypstRendererAdapter(TypstAssets assets, TypstCompiler compiler, TypstImageStager images) {
        this.assets = assets;
        this.compiler = compiler;
        this.images = images;
    }

    @Override
    public RenderedPdf renderPdf(StructuredDocument document) {
        try (TypstWorkspace workspace = prepare(document)) {
            Path output = workspace.outputDir().resolve(PDF_FILE);
            compiler.compile(workspace, Format.PDF, output);
            byte[] pdf = Files.readAllBytes(output);
            return new RenderedPdf(pdf, pageCount(pdf));
        } catch (IOException e) {
            throw new DocumentRenderException("Could not render the PDF", e);
        }
    }

    @Override
    public List<byte[]> renderSvgPages(StructuredDocument document) {
        try (TypstWorkspace workspace = prepare(document)) {
            compiler.compile(workspace, Format.SVG, workspace.outputDir().resolve(SVG_PAGE_PATTERN));
            return readPages(workspace.outputDir());
        } catch (IOException e) {
            throw new DocumentRenderException("Could not render the SVG pages", e);
        }
    }

    /** A workspace with the template, the document data and its image (when shown) in place. */
    private TypstWorkspace prepare(StructuredDocument document) throws IOException {
        TypstWorkspace workspace = TypstWorkspace.create();
        try {
            Files.writeString(workspace.mainFile(), assets.template(), StandardCharsets.UTF_8);
            String photo = stagePhoto(document, workspace.root());
            json.writeValue(workspace.dataFile().toFile(), TypstDocumentData.from(document, photo));
            return workspace;
        } catch (IOException | RuntimeException e) {
            workspace.close();
            throw e;
        }
    }

    private String stagePhoto(StructuredDocument document, Path root) throws IOException {
        boolean shown = TypstDocumentData.isDesigned(document)
                && document.options() != null
                && document.options().showProfileImage()
                && document.identity() != null;
        if (!shown) return null;
        return images.stage(document.identity().profileImageUrl(), root).orElse(null);
    }

    private static List<byte[]> readPages(Path outputDir) throws IOException {
        List<byte[]> pages = new ArrayList<>();
        try (Stream<Path> files = Files.list(outputDir)) {
            for (Path page : files.sorted().toList()) {
                pages.add(Files.readAllBytes(page));
            }
        }
        return pages;
    }

    private static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return document.getNumberOfPages();
        }
    }
}
