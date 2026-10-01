package com.insurance.document.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * OCR for scanned/photographed documents — the fallback path when
 * {@link PdfTextExtractionService} finds too little embedded text to trust,
 * and the direct path for plain image uploads (a photographed Aadhaar/PAN
 * card, for instance).
 *
 * IMPORTANT DEPLOYMENT REQUIREMENT: this is a JNI binding (via Tess4J) to
 * the native Tesseract OCR engine — it is NOT a self-contained Java OCR
 * implementation. The deployment host must have Tesseract installed
 * separately. The service's Dockerfile already does this for container
 * deployments (`apk add tesseract-ocr tesseract-ocr-data-eng` on the Alpine
 * base image, which places tessdata at /usr/share/tessdata — see
 * application.yml's tesseract.data-path). A non-Docker or different-base-image
 * deployment must install the equivalent package and confirm the tessdata
 * path matches. Without this, every call here throws at runtime — there is
 * no way to verify this actually works without a real server to test
 * against, which this sandbox does not have. Flagged explicitly in the
 * Phase 8 changelog.
 */
@Slf4j
@Service
public class OcrEngineService {

    @Value("${tesseract.data-path:/usr/share/tesseract-ocr/5/tessdata}")
    private String tessDataPath;

    @Value("${tesseract.language:eng}")
    private String language;

    /** Rendering resolution for converting PDF pages to images before OCR — higher is more
     *  accurate but slower; 300 DPI is the generally-recommended baseline for OCR quality. */
    private static final int RENDER_DPI = 300;

    private Tesseract newTesseractInstance() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage(language);
        return tesseract;
    }

    /** OCRs every page of a (presumed scanned) PDF and concatenates the results. */
    public String ocrPdf(InputStream pdfStream) throws IOException, TesseractException {
        Tesseract tesseract = newTesseractInstance();
        StringBuilder combined = new StringBuilder();

        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI);
                String pageText = tesseract.doOCR(image);
                combined.append(pageText).append("\n");
            }
        }
        return combined.toString();
    }

    /** OCRs a plain image file directly (e.g. a photographed ID card upload, not wrapped in a PDF). */
    public String ocrImage(InputStream imageStream) throws IOException, TesseractException {
        BufferedImage image = ImageIO.read(imageStream);
        if (image == null) {
            throw new IOException("Could not decode image stream — unsupported or corrupt format.");
        }
        return newTesseractInstance().doOCR(image);
    }
}
