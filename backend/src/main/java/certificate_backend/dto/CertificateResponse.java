package certificate_backend.dto;

import certificate_backend.model.CertificateStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response body returned after a successful {@code POST /api/certificates/issue}.
 *
 * <p>Also used as the certificate detail payload inside {@link VerifyResponse}.
 */
@Data
@Builder
public class CertificateResponse {

    /** Human-readable certificate ID (e.g. {@code CERT-2024-00142}). */
    private String certificateId;

    private String studentName;
    private String studentRollNumber;
    private String course;
    private String institution;
    private String grade;
    private String issueDate;

    /**
     * SHA-256 content hash of the immutable certificate fields.
     * Clients can independently verify this using the documented canonicalization.
     */
    private String certificateHash;

    /**
     * Ethereum transaction hash. {@code null} until blockchain integration is complete.
     * Do not confuse with {@link #certificateHash}.
     */
    private String transactionHash;  // null until blockchain integration

    private CertificateStatus status;
    private Instant createdAt;
}
