package certificate_backend.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Extracts QR codes from uploaded PDF files.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Render each PDF page to a {@link BufferedImage} using PDFBox at 150 DPI.</li>
 *   <li>Attempt ZXing QR decode on the full-page image.</li>
 *   <li>If that fails, attempt at 300 DPI (higher resolution for small QR codes).</li>
 *   <li>Return the first successfully decoded QR text.</li>
 *   <li>Stop after the configured page limit to prevent resource exhaustion.</li>
 * </ol>
 *
 * <p>Security: No URLs are fetched. QR content is returned as raw text only.
 */
@Slf4j
@Service
public class InfosysQrExtractionService {

    private static final int MAX_PAGES = 5;
    private static final float DPI_STANDARD = 150f;
    private static final float DPI_HIGH = 300f;
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    /**
     * Extracts the first QR code text found in the uploaded PDF.
     *
     * @param file the uploaded PDF file
     * @return extraction result record
     */
    public ExtractionResult extractQrFromPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ExtractionResult.failure("NO_QR_FOUND", "Uploaded file is empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ExtractionResult.failure("FILE_TOO_LARGE",
                    "Uploaded file exceeds the 10 MB limit.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            // Also allow when content type is not set but filename ends in .pdf
            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
                return ExtractionResult.failure("INVALID_FILE_TYPE",
                        "Uploaded file must be a PDF.");
            }
        }

        try (InputStream inputStream = file.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return extractFromPdfBytes(bytes);
        } catch (IOException e) {
            log.error("Error reading uploaded PDF file", e);
            return ExtractionResult.failure("READ_ERROR",
                    "Could not read the uploaded file: " + sanitiseMessage(e.getMessage()));
        }
    }

    /**
     * Extracts QR from raw PDF bytes. Public for testing.
     */
    public ExtractionResult extractFromPdfBytes(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = Math.min(document.getNumberOfPages(), MAX_PAGES);
            if (pageCount == 0) {
                return ExtractionResult.failure("NO_QR_FOUND", "PDF contains no pages.");
            }

            PDFRenderer renderer = new PDFRenderer(document);

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                // Try standard resolution first
                String qrText = tryDecode(renderer, pageIndex, DPI_STANDARD);
                if (qrText != null) {
                    log.debug("QR found on page {} at {} DPI", pageIndex + 1, DPI_STANDARD);
                    return ExtractionResult.success(qrText, pageIndex + 1);
                }
                // Try high resolution for small/dense QR codes
                qrText = tryDecode(renderer, pageIndex, DPI_HIGH);
                if (qrText != null) {
                    log.debug("QR found on page {} at {} DPI", pageIndex + 1, DPI_HIGH);
                    return ExtractionResult.success(qrText, pageIndex + 1);
                }
            }

            return ExtractionResult.failure("NO_QR_FOUND",
                    "No QR code was detected in the uploaded certificate.");

        } catch (Exception e) {
            log.error("Error processing PDF for QR extraction", e);
            return ExtractionResult.failure("PDF_PROCESSING_ERROR",
                    "Could not process PDF: " + sanitiseMessage(e.getMessage()));
        }
    }

    private String tryDecode(PDFRenderer renderer, int pageIndex, float dpi) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            return decodeQr(image);
        } catch (Exception e) {
            log.trace("QR decode attempt failed on page {} at {} DPI: {}", pageIndex + 1, dpi, e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to decode a QR code from a BufferedImage using ZXing.
     * Public for testing.
     *
     * @return decoded text, or null if no QR code found
     */
    public String decodeQr(BufferedImage image) {
        if (image == null) return null;
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            log.trace("ZXing decode error: {}", e.getMessage());
            return null;
        }
    }

    /** Remove any potentially sensitive file content from error messages. */
    private String sanitiseMessage(String msg) {
        if (msg == null) return "unknown error";
        return msg.length() > 120 ? msg.substring(0, 120) + "…" : msg;
    }

    // ── Result record ──────────────────────────────────────────────────────────

    public record ExtractionResult(
            boolean success,
            String qrText,
            int pageFound,          // 1-based; 0 if not found
            String errorCode,
            String errorMessage
    ) {
        static ExtractionResult success(String qrText, int page) {
            return new ExtractionResult(true, qrText, page, null, null);
        }

        static ExtractionResult failure(String code, String message) {
            return new ExtractionResult(false, null, 0, code, message);
        }
    }
}
