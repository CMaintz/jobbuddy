package com.autoapplicant.adapter.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Writes a tiny PNG and counts the images a rendered PDF actually embedded. */
final class PdfTestImages {

    private static final int SIZE = 8;

    private PdfTestImages() {}

    static Path writePng(Path file) throws IOException {
        ImageIO.write(new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        return file;
    }

    static int imagesOnFirstPage(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            PDResources resources = document.getPage(0).getResources();
            int images = 0;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) images++;
            }
            return images;
        }
    }
}
