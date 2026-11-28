package com.autoapplicant.adapter.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Where things landed on a rendered PDF's first page, in points from the left edge. */
final class PdfLayout {

    private PdfLayout() {}

    /** The left edge of the first line that starts with {@code text}. */
    static float textX(byte[] pdf, String text) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            List<Float> found = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String line, List<TextPosition> positions) {
                    if (found.isEmpty() && line.startsWith(text)) found.add(positions.get(0).getXDirAdj());
                }
            };
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            stripper.getText(document);
            if (found.isEmpty()) throw new AssertionError("No line starts with " + text);
            return found.get(0);
        }
    }

    /** The left edge of every raster image drawn on the first page, in drawing order. */
    static List<Float> imageXs(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            ImageFinder finder = new ImageFinder();
            finder.processPage(document.getPage(0));
            return finder.xs;
        }
    }

    private static final class ImageFinder extends PDFStreamEngine {

        private final List<Float> xs = new ArrayList<>();

        ImageFinder() {
            addOperator(new Concatenate());
            addOperator(new DrawObject());
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new SetMatrix());
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName name) {
                PDXObject object = getResources().getXObject(name);
                if (object instanceof PDImageXObject) {
                    xs.add(getGraphicsState().getCurrentTransformationMatrix().getTranslateX());
                    return;
                }
                if (object instanceof PDFormXObject form) {
                    showForm(form);
                    return;
                }
            }
            super.processOperator(operator, operands);
        }
    }
}
