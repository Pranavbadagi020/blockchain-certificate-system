package certificate_backend.service;

import certificate_backend.dto.InfosysParsedCredential;
import certificate_backend.dto.InfosysVerificationResponse;
import certificate_backend.service.InfosysCredentialVerificationService.AssessmentResult;
import certificate_backend.service.InfosysCredentialVerificationService.SignatureStatus;
import certificate_backend.service.InfosysPdfComparisonService.ComparisonResult;
import certificate_backend.service.InfosysQrExtractionService.ExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the end-to-end Infosys Springboard certificate verification workflow:
 *
 * <ol>
 *   <li>File validation</li>
 *   <li>QR code extraction from PDF</li>
 *   <li>QR credential parsing</li>
 *   <li>Structural signature assessment</li>
 *   <li>PDF text extraction and field comparison</li>
 *   <li>Result aggregation and response building</li>
 * </ol>
 *
 * <p>The service is deliberately conservative: it never reports a higher
 * confidence level than the evidence supports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfosysCertificateService {

    private static final String ISSUER = "Infosys Springboard";
    private static final String VERIFICATION_TYPE = "EXTERNAL_CREDENTIAL";

    private final InfosysQrExtractionService qrExtractionService;
    private final InfosysCredentialParserService credentialParserService;
    private final InfosysCredentialVerificationService credentialVerificationService;
    private final InfosysPdfComparisonService pdfComparisonService;

    /**
     * Verifies an uploaded Infosys Springboard certificate PDF.
     *
     * @param file multipart PDF upload
     * @return structured verification response
     */
    public InfosysVerificationResponse verify(MultipartFile file) {
        List<String> warnings = new ArrayList<>();

        // ── Step 1: Read PDF bytes ────────────────────────────────────────────
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Could not read uploaded file bytes", e);
            return buildUnable("Could not read uploaded file.", List.of(e.getMessage()));
        }

        // ── Step 2: QR Extraction ─────────────────────────────────────────────
        ExtractionResult extraction = qrExtractionService.extractQrFromPdf(file);

        if (!extraction.success()) {
            String code = extraction.errorCode();
            if ("FILE_TOO_LARGE".equals(code) || "INVALID_FILE_TYPE".equals(code)) {
                return InfosysVerificationResponse.builder()
                        .issuer(ISSUER)
                        .verificationType(VERIFICATION_TYPE)
                        .result("UNABLE_TO_VERIFY")
                        .qrExtractionStatus("REJECTED")
                        .signatureVerificationStatus(SignatureStatus.UNABLE_TO_VERIFY_NO_PUBLIC_KEY.name())
                        .issuerTrusted(false)
                        .message(extraction.errorMessage())
                        .warnings(List.of(extraction.errorMessage()))
                        .build();
            }

            // QR not found or unreadable
            return InfosysVerificationResponse.builder()
                    .issuer(ISSUER)
                    .verificationType(VERIFICATION_TYPE)
                    .result("UNABLE_TO_VERIFY")
                    .qrExtractionStatus(extraction.errorCode())
                    .signatureVerificationStatus(SignatureStatus.UNABLE_TO_VERIFY_NO_PUBLIC_KEY.name())
                    .issuerTrusted(false)
                    .officialVerificationUrl("https://verify.onwingspan.com")
                    .message("No QR code was found in the uploaded PDF. "
                            + "This does not prove the certificate is fraudulent, but "
                            + "independent verification is not possible.")
                    .warnings(List.of(extraction.errorMessage()))
                    .build();
        }

        // ── Step 3: Parse QR credential ───────────────────────────────────────
        InfosysParsedCredential credential = credentialParserService.parse(extraction.qrText());

        if (credential.getParseWarnings() != null) {
            warnings.addAll(credential.getParseWarnings());
        }

        // If format is unknown, return NEEDS_REVIEW
        if ("UNKNOWN".equals(credential.getQrContentType())) {
            return InfosysVerificationResponse.builder()
                    .issuer(ISSUER)
                    .verificationType(VERIFICATION_TYPE)
                    .result("NEEDS_REVIEW")
                    .qrExtractionStatus("SUCCESS")
                    .signatureVerificationStatus(SignatureStatus.STRUCTURALLY_INVALID.name())
                    .issuerTrusted(false)
                    .credential(credential)
                    .officialVerificationUrl("https://verify.onwingspan.com")
                    .message("QR code was found but its format is not recognised. "
                            + "Manual verification required.")
                    .warnings(warnings)
                    .build();
        }

        // If URL-type QR, we can provide the link but cannot do field comparison
        if ("URL".equals(credential.getQrContentType())) {
            return InfosysVerificationResponse.builder()
                    .issuer(ISSUER)
                    .verificationType(VERIFICATION_TYPE)
                    .result("NEEDS_REVIEW")
                    .qrExtractionStatus("SUCCESS")
                    .signatureVerificationStatus(SignatureStatus.URL_TYPE_NO_CREDENTIAL_DATA.name())
                    .issuerTrusted(false)
                    .pdfDetailsMatch(null)
                    .credential(credential)
                    .officialVerificationUrl(credential.getOfficialVerificationUrl())
                    .message("The QR code contains a verification URL, not an embedded credential. "
                            + "Field-by-field comparison is not possible. "
                            + "Please visit the official verification portal to confirm authenticity.")
                    .warnings(warnings)
                    .build();
        }

        // ── Step 4: Structural signature assessment ───────────────────────────
        AssessmentResult assessment = credentialVerificationService.assessSignature(credential);
        if (assessment.structuralWarnings() != null) {
            warnings.addAll(assessment.structuralWarnings());
        }

        // Structurally invalid or no proof — cannot proceed to comparison meaningfully
        if (assessment.signatureStatus() == SignatureStatus.NO_PROOF_PRESENT
                || assessment.signatureStatus() == SignatureStatus.STRUCTURALLY_INVALID) {

            return InfosysVerificationResponse.builder()
                    .issuer(ISSUER)
                    .verificationType(VERIFICATION_TYPE)
                    .result("INVALID_SIGNATURE")
                    .qrExtractionStatus("SUCCESS")
                    .signatureVerificationStatus(assessment.signatureStatus().name())
                    .issuerTrusted(false)
                    .credential(credential)
                    .officialVerificationUrl(
                            firstNonNull(credential.getOfficialVerificationUrl(),
                                    "https://verify.onwingspan.com"))
                    .message("The credential's digital proof is absent or structurally invalid. "
                            + "The certificate cannot be verified.")
                    .warnings(warnings)
                    .build();
        }

        // Unsupported proof type
        if (assessment.signatureStatus() == SignatureStatus.UNSUPPORTED_PROOF_TYPE) {
            return InfosysVerificationResponse.builder()
                    .issuer(ISSUER)
                    .verificationType(VERIFICATION_TYPE)
                    .result("NEEDS_REVIEW")
                    .qrExtractionStatus("SUCCESS")
                    .signatureVerificationStatus(assessment.signatureStatus().name())
                    .issuerTrusted(false)
                    .credential(credential)
                    .officialVerificationUrl("https://verify.onwingspan.com")
                    .message("The credential uses an unsupported format. Manual verification required.")
                    .warnings(warnings)
                    .build();
        }

        // ── Step 5: PDF field comparison ──────────────────────────────────────
        ComparisonResult comparison = pdfComparisonService.compare(pdfBytes, credential);
        if (comparison.warnings() != null) {
            warnings.addAll(comparison.warnings());
        }

        // Build field comparisons
        String officialUrl = firstNonNull(
                credential.getOfficialVerificationUrl(),
                "https://verify.onwingspan.com"
        );

        // ── Step 6: Determine final result ────────────────────────────────────
        String result;
        String message;

        if (!comparison.textExtractionSucceeded()) {
            result = "NEEDS_REVIEW";
            message = "PDF text extraction failed (the certificate may be image-based). "
                    + "Automatic field comparison is not possible. "
                    + "Please verify at: " + officialUrl;

        } else if (comparison.anyMismatch()) {
            result = "TAMPERING_DETECTED";
            message = buildTamperingMessage(comparison);

        } else if (comparison.needsReview()) {
            result = "NEEDS_REVIEW";
            message = "Some fields could not be extracted from the PDF for comparison. "
                    + "Partial match only. Please verify at: " + officialUrl;

        } else {
            // All extractable fields match — still cannot claim signature is valid
            result = "VERIFIED";
            message = "All extracted PDF fields match the QR credential. "
                    + "NOTE: The Ed25519 signature could NOT be independently verified "
                    + "(no public key available for " + credential.getVerificationMethod() + "). "
                    + "Please confirm authenticity at: " + officialUrl;
        }

        return InfosysVerificationResponse.builder()
                .issuer(ISSUER)
                .verificationType(VERIFICATION_TYPE)
                .result(result)
                .qrExtractionStatus("SUCCESS")
                // Signature status is always UNABLE_TO_VERIFY_NO_PUBLIC_KEY for Infosys
                .signatureVerificationStatus(assessment.signatureStatus().name())
                .signatureValid(null) // never set to true without real verification
                .issuerTrusted(false) // never set to true without key anchor
                .pdfDetailsMatch(!comparison.anyMismatch() && !comparison.needsReview()
                        && comparison.textExtractionSucceeded())
                .studentName(comparison.studentName())
                .course(comparison.course())
                .completionDate(comparison.completionDate())
                .issuanceDate(comparison.issuanceDate())
                .credential(credential)
                .officialVerificationUrl(officialUrl)
                .message(message)
                .warnings(warnings.isEmpty() ? null : warnings)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InfosysVerificationResponse buildUnable(String message, List<String> warnings) {
        return InfosysVerificationResponse.builder()
                .issuer(ISSUER)
                .verificationType(VERIFICATION_TYPE)
                .result("UNABLE_TO_VERIFY")
                .qrExtractionStatus("ERROR")
                .signatureVerificationStatus(SignatureStatus.UNABLE_TO_VERIFY_NO_PUBLIC_KEY.name())
                .issuerTrusted(false)
                .officialVerificationUrl("https://verify.onwingspan.com")
                .message(message)
                .warnings(warnings)
                .build();
    }

    private String buildTamperingMessage(ComparisonResult comparison) {
        StringBuilder sb = new StringBuilder(
                "TAMPERING DETECTED: The following fields in the uploaded PDF "
                + "do not match the QR credential.");

        appendMismatch(sb, "Student name", comparison.studentName());
        appendMismatch(sb, "Course", comparison.course());
        appendMismatch(sb, "Completion date", comparison.completionDate());
        appendMismatch(sb, "Issuance date", comparison.issuanceDate());

        return sb.toString();
    }

    private void appendMismatch(StringBuilder sb,
                                 String label,
                                 certificate_backend.dto.InfosysFieldComparison fc) {
        if (fc != null && Boolean.FALSE.equals(fc.getMatches())) {
            sb.append(" | ").append(label)
              .append(": PDF='").append(fc.getPdfValue())
              .append("' vs Credential='").append(fc.getCredentialValue()).append("'");
        }
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}

