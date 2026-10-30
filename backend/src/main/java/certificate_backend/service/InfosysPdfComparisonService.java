package certificate_backend.service;

import certificate_backend.dto.InfosysFieldComparison;
import certificate_backend.dto.InfosysParsedCredential;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts visible text from an Infosys Springboard certificate PDF and
 * compares it field-by-field against the signed QR credential.
 *
 * <h2>Purpose</h2>
 * <p>This is the primary fraud-detection mechanism. A fraudster who changes
 * only the visible text of a genuine PDF will have mismatched fields when
 * compared to the original credential embedded in the QR code.
 *
 * <h2>Extraction approach</h2>
 * <ol>
 *   <li>PDFBox text extraction (works for text-based PDFs).</li>
 *   <li>If the PDF is image-based and text extraction yields nothing,
 *       returns {@code NEEDS_REVIEW} — OCR is not performed.</li>
 * </ol>
 *
 * <h2>Normalisation rules</h2>
 * <ul>
 *   <li>Collapse multiple whitespace characters to a single space.</li>
 *   <li>Trim leading/trailing whitespace.</li>
 *   <li>Case-insensitive comparison.</li>
 *   <li>Never normalise so aggressively that two distinct names match.</li>
 * </ul>
 */
@Slf4j
@Service
public class InfosysPdfComparisonService {

    // Heuristic patterns for extracting fields from Infosys certificate text.
    // These are deliberately lenient to handle minor layout variations.
    // They match text that typically surrounds the student name on Infosys PDFs.

    private static final int MIN_MEANINGFUL_TEXT_LENGTH = 20;

    /**
     * Result of a complete PDF vs credential comparison.
     */
    public record ComparisonResult(
            String pdfText,
            boolean textExtractionSucceeded,
            InfosysFieldComparison studentName,
            InfosysFieldComparison course,
            InfosysFieldComparison completionDate,
            InfosysFieldComparison issuanceDate,
            boolean anyMismatch,
            boolean needsReview,
            List<String> warnings
    ) {}

    /**
     * Compares the visible PDF text with the fields in the parsed credential.
     *
     * @param pdfBytes    raw PDF bytes
     * @param credential  parsed credential from QR
     * @return comparison result
     */
    public ComparisonResult compare(byte[] pdfBytes, InfosysParsedCredential credential) {
        List<String> warnings = new ArrayList<>();

        // ── Extract PDF text ──────────────────────────────────────────────────
        String pdfText = extractPdfText(pdfBytes, warnings);

        if (pdfText == null || pdfText.length() < MIN_MEANINGFUL_TEXT_LENGTH) {
            warnings.add("PDF text extraction yielded insufficient content. "
                    + "The certificate may be image-based. OCR is required for image-based PDFs.");
            return new ComparisonResult(
                    pdfText, false, null, null, null, null,
                    false, true, warnings
            );
        }

        // ── Extract individual fields from PDF text ───────────────────────────
        String pdfName = extractNameFromPdfText(pdfText, warnings);
        String pdfCourse = extractCourseFromPdfText(pdfText, warnings);
        String pdfCompletionDate = extractCompletionDateFromPdfText(pdfText, warnings);
        String pdfIssuanceDate = extractIssuanceDateFromPdfText(pdfText, warnings);

        // ── Compare against credential ────────────────────────────────────────
        InfosysFieldComparison nameComparison = compareField(
                "Student name", pdfName, credential.getIssuedTo(), warnings);
        InfosysFieldComparison courseComparison = compareField(
                "Course name", pdfCourse, credential.getCourseName(), warnings);
        InfosysFieldComparison completionDateComparison = compareField(
                "Completion date", pdfCompletionDate, credential.getCompletionDate(), warnings);
        InfosysFieldComparison issuanceDateComparison = compareField(
                "Issuance date", pdfIssuanceDate, credential.getIssuanceDate(), warnings);

        // ── Determine overall match ───────────────────────────────────────────
        boolean anyMismatch = isMismatch(nameComparison)
                || isMismatch(courseComparison)
                || isMismatch(completionDateComparison)
                || isMismatch(issuanceDateComparison);

        // Needs review if any field comparison was inconclusive (null matches)
        boolean needsReview = isInconclusive(nameComparison)
                || isInconclusive(courseComparison)
                || isInconclusive(completionDateComparison)
                || isInconclusive(issuanceDateComparison);

        return new ComparisonResult(
                pdfText, true,
                nameComparison, courseComparison,
                completionDateComparison, issuanceDateComparison,
                anyMismatch, needsReview, warnings
        );
    }

    // ── PDF Text Extraction ───────────────────────────────────────────────────

    private String extractPdfText(byte[] pdfBytes, List<String> warnings) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.debug("Extracted {} characters from PDF", text == null ? 0 : text.length());
            return text;
        } catch (IOException e) {
            log.warn("PDF text extraction failed", e);
            warnings.add("PDF text extraction error: " + e.getMessage());
            return null;
        }
    }

    // ── Field Extraction Heuristics ───────────────────────────────────────────

    /**
     * Extracts the recipient name from PDF text.
     *
     * <p>Infosys Springboard certificates typically show the name in large
     * text after "certify that" or "awarded to" or "This is to certify that".
     * We also look for the name between known certificate boilerplate lines.
     */
    private String extractNameFromPdfText(String text, List<String> warnings) {
        // Strategy 1: look for "certify that\n<Name>" or "awarded to\n<Name>"
        List<Pattern> namePatterns = List.of(
                Pattern.compile("certif(?:y|ied) that\\s*[\\r\\n]+\\s*([A-Za-z][A-Za-z .'-]{2,60})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("awarded to\\s*[\\r\\n]+\\s*([A-Za-z][A-Za-z .'-]{2,60})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("presented to\\s*[\\r\\n]+\\s*([A-Za-z][A-Za-z .'-]{2,60})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("issued to\\s*[\\r\\n]+\\s*([A-Za-z][A-Za-z .'-]{2,60})",
                        Pattern.CASE_INSENSITIVE),
                // Inline on same line: "certify that John Doe has"
                Pattern.compile("certif(?:y|ied) that\\s+([A-Za-z][A-Za-z .'-]{2,60})\\s+has",
                        Pattern.CASE_INSENSITIVE)
        );

        for (Pattern p : namePatterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String candidate = normalise(m.group(1));
                if (candidate.split("\\s+").length >= 1) {
                    log.debug("Extracted PDF name via pattern '{}': {}", p.pattern(), candidate);
                    return candidate;
                }
            }
        }

        warnings.add("Could not extract student name from PDF text using known patterns. "
                + "Name comparison will be marked inconclusive.");
        return null;
    }

    /**
     * Extracts the course name from PDF text.
     *
     * <p>Infosys certificates typically show the course name after "successfully completed"
     * or "has completed the" or similar phrases.
     */
    private String extractCourseFromPdfText(String text, List<String> warnings) {
        List<Pattern> coursePatterns = List.of(
                Pattern.compile("successfully completed(?:\\s+the\\s+course)?\\s*[\\r\\n]+\\s*([^\\r\\n,]{3,120})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("successfully completed\\s+(?:the\\s+course\\s+|the\\s+)?([^\\r\\n,]{3,120})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("has completed(?:\\s+the\\s+course)?\\s*[\\r\\n]+\\s*([^\\r\\n,]{3,120})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("has completed\\s+(?:the\\s+course\\s+|the\\s+)?([^\\r\\n,]{3,120})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:course|program)[:\\s]+(?:[\\r\\n]+\\s*)?([^\\r\\n,]{3,120})",
                        Pattern.CASE_INSENSITIVE)
        );

        for (Pattern p : coursePatterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String candidate = normalise(m.group(1));
                if (!candidate.isBlank() && candidate.length() > 2
                        && !candidate.equalsIgnoreCase("course")
                        && !candidate.equalsIgnoreCase("the course")) {
                    log.debug("Extracted PDF course via pattern '{}': {}", p.pattern(), candidate);
                    return candidate;
                }
            }
        }

        warnings.add("Could not extract course name from PDF text using known patterns.");
        return null;
    }

    /**
     * Extracts the completion date from PDF text.
     */
    private String extractCompletionDateFromPdfText(String text, List<String> warnings) {
        // Look for date labels followed by date values
        List<Pattern> datePatterns = List.of(
                Pattern.compile("(?:completed on|completion date|date of completion)[:\\s]+([A-Za-z]+ \\d{1,2},?\\s+\\d{4})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:^|[\\r\\n]|course[^\r\n]*)\\s+on\\s+([A-Za-z]+ \\d{1,2},?\\s+\\d{4})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("on\\s+([A-Za-z]+ \\d{1,2},?\\s+\\d{4})",
                        Pattern.CASE_INSENSITIVE),
                // ISO style
                Pattern.compile("(?:completed on|completion date)[:\\s]+(\\d{4}-\\d{2}-\\d{2})",
                        Pattern.CASE_INSENSITIVE)
        );

        for (Pattern p : datePatterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return normalise(m.group(1));
            }
        }

        warnings.add("Could not extract completion date from PDF text.");
        return null;
    }

    /**
     * Extracts the issuance date from PDF text.
     */
    private String extractIssuanceDateFromPdfText(String text, List<String> warnings) {
        List<Pattern> datePatterns = List.of(
                Pattern.compile("(?:issued on|issue date|date of issue|issuance date)[:\\s]+(?:(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\\s+)?([A-Za-z]+ \\d{1,2},?\\s+\\d{4})",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:issued on|issue date)[:\\s]+(?:(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\\s+)?(\\d{4}-\\d{2}-\\d{2})",
                        Pattern.CASE_INSENSITIVE)
        );

        for (Pattern p : datePatterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return normalise(m.group(1));
            }
        }

        warnings.add("Could not extract issuance date from PDF text.");
        return null;
    }

    // ── Field Comparison ──────────────────────────────────────────────────────

    private InfosysFieldComparison compareField(
            String fieldName, String pdfValue, String credentialValue,
            List<String> warnings) {

        String confidence = pdfValue != null ? "HIGH" : "LOW";

        if (pdfValue == null && credentialValue == null) {
            return InfosysFieldComparison.builder()
                    .pdfValue(null)
                    .credentialValue(null)
                    .matches(null)
                    .pdfExtractionConfidence("LOW")
                    .note(fieldName + " could not be extracted from PDF or credential.")
                    .build();
        }

        if (pdfValue == null) {
            return InfosysFieldComparison.builder()
                    .pdfValue(null)
                    .credentialValue(credentialValue)
                    .matches(null)
                    .pdfExtractionConfidence("LOW")
                    .note(fieldName + " could not be extracted from PDF. Manual review required.")
                    .build();
        }

        if (credentialValue == null) {
            return InfosysFieldComparison.builder()
                    .pdfValue(pdfValue)
                    .credentialValue(null)
                    .matches(null)
                    .pdfExtractionConfidence(confidence)
                    .note(fieldName + " not present in QR credential.")
                    .build();
        }

        boolean matches = valuesMatch(fieldName, pdfValue, credentialValue);

        return InfosysFieldComparison.builder()
                .pdfValue(pdfValue)
                .credentialValue(credentialValue)
                .matches(matches)
                .pdfExtractionConfidence(confidence)
                .note(matches ? fieldName + " matches." : fieldName + " MISMATCH detected.")
                .build();
    }

    private boolean valuesMatch(String fieldName, String pdfValue, String credentialValue) {
        String p = normalise(pdfValue);
        String c = normalise(credentialValue);
        if (p.equalsIgnoreCase(c)) {
            return true;
        }
        if (fieldName != null && fieldName.toLowerCase().contains("date")) {
            LocalDate d1 = parseDate(p);
            LocalDate d2 = parseDate(c);
            if (d1 != null && d2 != null) {
                return Math.abs(ChronoUnit.DAYS.between(d1, d2)) <= 1;
            }
        }
        return false;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() >= 10 && raw.charAt(4) == '-' && raw.charAt(7) == '-') {
            try {
                return LocalDate.parse(raw.substring(0, 10));
            } catch (Exception ignored) {}
        }
        String cleaned = raw.replaceAll(",", "").trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        for (DateTimeFormatter dtf : formatters) {
            try {
                return LocalDate.parse(cleaned, dtf);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isMismatch(InfosysFieldComparison comparison) {
        return comparison != null
                && Boolean.FALSE.equals(comparison.getMatches());
    }

    private boolean isInconclusive(InfosysFieldComparison comparison) {
        return comparison != null && comparison.getMatches() == null;
    }

    /**
     * Normalises a string for comparison: collapse whitespace, trim.
     * Case comparison is done externally via equalsIgnoreCase.
     * Does NOT remove hyphens, apostrophes, or other name characters.
     */
    private String normalise(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }
}
