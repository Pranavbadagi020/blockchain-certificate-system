package certificate_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response body for certificate verification endpoints.
 *
 * <p>Supported verification results:
 * <ul>
 *   <li>{@code VERIFIED} / {@code VALID} — certificate found, hash matches, on-chain confirmed</li>
 *   <li>{@code TAMPERING_DETECTED} / {@code TAMPERED} — certificate details or hash do not match</li>
 *   <li>{@code REVOKED} — certificate marked or recorded as revoked</li>
 *   <li>{@code NOT_FOUND} — certificate record not found in registry</li>
 *   <li>{@code UNABLE_TO_VERIFY} — unparseable/encrypted PDF or blockchain unreachable</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResponse {

    private String verificationResult;
    private String message;
    private CertificateResponse certificate;
    private boolean blockchainVerified;
    private String blockchainNote;

    /** Specific field mismatch descriptions when tampering is detected. */
    private List<FieldMismatch> mismatchDetails;

    /** Normalized details extracted from the uploaded PDF document. */
    private Map<String, String> extractedDetails;

    /** Cryptographic SHA-256 hash of the uploaded PDF file bytes. */
    private String uploadedFileHash;

    /** AI-assisted heuristic and structural document anomaly analysis. */
    private AiAnalysisResult aiAnalysis;
}
