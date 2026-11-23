package com.autoapplicant.adapter.render;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import com.autoapplicant.domain.document.design.ResumeDesign;
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
 * Renders structured documents and resume-builder designs with the Typst CLI and the built-in
 * templates. Each render gets its own workspace holding the template, the document as JSON and
 * any staged image, and the workspace is deleted afterwards.
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
        return withPhotoFallback(source(document), this::pdf);
    }

    @Override
    public List<byte[]> renderSvgPages(StructuredDocument document) {
        return withPhotoFallback(source(document), this::svgPages);
    }

    @Override
    public RenderedPdf renderPdf(ResumeDesign design) {
        return withPhotoFallback(source(design), this::pdf);
    }

    @Override
    public List<byte[]> renderSvgPages(ResumeDesign design) {
        return withPhotoFallback(source(design), this::svgPages);
    }

    /** What one compile needs: the template and how to build its data once the photo is staged. */
    private record Source(String template, String photoReference, Function<String, Object> data) {

        Source withoutPhoto() {
            return new Source(template, null, data);
        }
    }

    private Source source(StructuredDocument document) {
        boolean shown = TypstDocumentData.isDesigned(document)
                && document.options() != null
                && document.options().showProfileImage()
                && document.identity() != null;
        String photo = shown ? document.identity().profileImageUrl() : null;
        return new Source(assets.template(), photo, staged -> TypstDocumentData.from(document, staged));
    }

    private Source source(ResumeDesign design) {
        String photo = design.photo() != null ? design.photo().url() : null;
        return new Source(assets.resumeTemplate(), photo, staged -> TypstResumeData.from(design, staged));
    }

    /**
     * A photo can pass the format check and still be undecodable (a truncated upload), which
     * fails the whole compile. The photo is the one input the user cannot see is broken, so
     * the document is rendered again without it rather than not at all.
     */
    private <T> T withPhotoFallback(Source source, Function<Source, T> render) {
        try {
            return render.apply(source);
        } catch (DocumentRenderException e) {
            if (source.photoReference() == null) throw e;
            log.warn("Typst could not render the document with its photo; rendering it without");
            return render.apply(source.withoutPhoto());
        }
    }

    private RenderedPdf pdf(Source source) {
        try (TypstWorkspace workspace = prepare(source)) {
            Path output = workspace.outputDir().resolve(PDF_FILE);
            compiler.compile(workspace, Format.PDF, output);
            byte[] pdf = Files.readAllBytes(output);
            return new RenderedPdf(pdf, pageCount(pdf));
        } catch (IOException e) {
            throw new DocumentRenderException("Could not render the PDF", e);
        }
    }

    private List<byte[]> svgPages(Source source) {
        try (TypstWorkspace workspace = prepare(source)) {
            compiler.compile(workspace, Format.SVG, workspace.outputDir().resolve(SVG_PAGE_PATTERN));
            return readPages(workspace.outputDir());
        } catch (IOException e) {
            throw new DocumentRenderException("Could not render the SVG pages", e);
        }
    }

    /** A workspace with the template, the data and its image (when shown) in place. */
    private TypstWorkspace prepare(Source source) throws IOException {
        TypstWorkspace workspace = TypstWorkspace.create();
        try {
            Files.writeString(workspace.mainFile(), source.template(), StandardCharsets.UTF_8);
            String photo = source.photoReference() == null
                    ? null
                    : images.stage(source.photoReference(), workspace.root()).orElse(null);
            json.writeValue(workspace.dataFile().toFile(), source.data().apply(photo));
            return workspace;
        } catch (IOException | RuntimeException e) {
            workspace.close();
            throw e;
        }
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
