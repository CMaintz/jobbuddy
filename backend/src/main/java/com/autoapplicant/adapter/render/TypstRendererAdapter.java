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
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders structured documents with the Typst CLI and the built-in template. Each render gets
 * its own workspace holding the template, the document as JSON and any staged image, and the
 * workspace is deleted afterwards.
 */
@Component
public class TypstRendererAdapter implements DocumentRendererPort {

    private static final Logger log = LoggerFactory.getLogger(TypstRendererAdapter.class);

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
        return withPhotoFallback(document, photo -> pdf(document, photo));
    }

    @Override
    public List<byte[]> renderSvgPages(StructuredDocument document) {
        return withPhotoFallback(document, photo -> svgPages(document, photo));
    }

    /**
     * A photo can pass the format check and still be undecodable (a truncated upload), which
     * fails the whole compile. The photo is the one input the user cannot see is broken, so
     * the document is rendered again without it rather than not at all.
     */
    private <T> T withPhotoFallback(StructuredDocument document, Function<Boolean, T> render) {
        try {
            return render.apply(true);
        } catch (DocumentRenderException e) {
            if (!photoShown(document)) throw e;
            log.warn("Typst could not render the document with its photo; rendering it without");
            return render.apply(false);
        }
    }

    private RenderedPdf pdf(StructuredDocument document, boolean withPhoto) {
        try (TypstWorkspace workspace = prepare(document, withPhoto)) {
            Path output = workspace.outputDir().resolve(PDF_FILE);
            compiler.compile(workspace, Format.PDF, output);
            byte[] pdf = Files.readAllBytes(output);
            return new RenderedPdf(pdf, pageCount(pdf));
        } catch (IOException e) {
            throw new DocumentRenderException("Could not render the PDF", e);
        }
    }

    private List<byte[]> svgPages(StructuredDocument document, boolean withPhoto) {
        try (TypstWorkspace workspace = prepare(document, withPhoto)) {
            compiler.compile(workspace, Format.SVG, workspace.outputDir().resolve(SVG_PAGE_PATTERN));
            return readPages(workspace.outputDir());
        } catch (IOException e) {
            throw new DocumentRenderException("Could not render the SVG pages", e);
        }
    }

    /** A workspace with the template, the document data and its image (when shown) in place. */
    private TypstWorkspace prepare(StructuredDocument document, boolean withPhoto) throws IOException {
        TypstWorkspace workspace = TypstWorkspace.create();
        try {
            Files.writeString(workspace.mainFile(), assets.template(), StandardCharsets.UTF_8);
            String photo = withPhoto && photoShown(document)
                    ? images.stage(document.identity().profileImageUrl(), workspace.root()).orElse(null)
                    : null;
            json.writeValue(workspace.dataFile().toFile(), TypstDocumentData.from(document, photo));
            return workspace;
        } catch (IOException | RuntimeException e) {
            workspace.close();
            throw e;
        }
    }

    private static boolean photoShown(StructuredDocument document) {
        return TypstDocumentData.isDesigned(document)
                && document.options() != null
                && document.options().showProfileImage()
                && document.identity() != null;
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
