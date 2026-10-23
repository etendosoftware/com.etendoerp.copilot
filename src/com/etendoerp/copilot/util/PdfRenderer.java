package com.etendoerp.copilot.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Renders every page of a PDF at a caller-selected scale in RGB. */
public final class PdfRenderer {
  private PdfRenderer() {}

  public static List<BufferedImage> render(byte[] pdf, float scale) throws IOException {
    List<BufferedImage> images = new ArrayList<>();
    try (var document = Loader.loadPDF(pdf)) {
      PDFRenderer renderer = new PDFRenderer(document);
      for (int page = 0; page < document.getNumberOfPages(); page++) {
        images.add(renderer.renderImage(page, scale, ImageType.RGB));
      }
    }
    return images;
  }

  public static List<BufferedImage> render(Path pdf, float scale) throws IOException {
    List<BufferedImage> images = new ArrayList<>();
    try (var document = Loader.loadPDF(pdf.toFile())) {
      PDFRenderer renderer = new PDFRenderer(document);
      for (int page = 0; page < document.getNumberOfPages(); page++) {
        images.add(renderer.renderImage(page, scale, ImageType.RGB));
      }
    }
    return images;
  }
}
