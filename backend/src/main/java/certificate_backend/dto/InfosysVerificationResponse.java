package certificate_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full structured response returned by POST /api/certificates/verify/infosys.
 *
 * <p>Result semantics:
 * <ul>
 *   <li>VERIFIED            – QR decoded, credential parsed, PDF fields match credential,
 *                             teacher should still confirm at the official portal.</li>
 *   <li>TAMPERING_DETECTED  – QR decoded, parsed OK, but PDF fields differ from credential.</li>
 *   <li>INVALID_SIGNATURE   – Proof block is structurally corrupt or explicitly rejected.</li>
 *   <li>UNABLE_TO_VERIFY    – No QR found, or external service unreachable.</li>
 *   <li>NEEDS_REVIEW        – QR decoded but format unrecognised, or extraction confidence low.</li>
 * </ul>
 *
 * <p><strong>IMPORTANT</strong>: {@code signatureVerificationStatus} is always
 * {@code UNABLE_TO_VERIFY_NO_PUBLIC_KEY} because Infosys does not publish the
 * Ed25519 public key for the {@code did:india} verification method.
 * Never set {@code signatureValid = true} without actual cryptographic verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfosysVerificationResponse {

    // ── Top-level result ──────────────────────────────────────────────────────

    private String issuer;               // "Infosys Springboard"
    private String verificationType;     // "EXTERNAL_CREDENTIAL"
    private String result;               // see JavaDoc above

    // ── Signature status ──────────────────────────────────────────────────────

    /**
     * Always UNABLE_TO_VERIFY_NO_PUBLIC_KEY for Infosys certificates because
     * no public API or published key material allows independent Ed25519
     * verification of the did:india proof.
     */
    private String signatureVerificationStatus;

    /**
     * Always null / absent — never set to true without cryptographic verification.
     */
    private Boolean signatureValid;

    /** Whether the issuer identity has been established through a trusted mechanism. */
    private Boolean issuerTrusted;       // always false — no trusted key anchor available

    // ── PDF vs Credential comparison ─────────────────────────────────────────

    private Boolean pdfDetailsMatch;

    private InfosysFieldComparison studentName;
    private InfosysFieldComparison course;
    private InfosysFieldComparison completionDate;
    private InfosysFieldComparison issuanceDate;

    // ── Parsed credential ─────────────────────────────────────────────────────

    private InfosysParsedCredential credential;

    // ── QR extraction status ──────────────────────────────────────────────────

    private String qrExtractionStatus;  // "SUCCESS" | "NOT_FOUND" | "UNREADABLE" | "MALFORMED"

    // ── Guidance for the teacher ──────────────────────────────────────────────

    /**
     * Official URL the teacher should visit to complete verification.
     * Populated whenever a URL can be determined from the QR or credential.
     */
    private String officialVerificationUrl;

    /** Human-readable summary message. */
    private String message;

    /** Any errors or warnings encountered during processing. */
    private java.util.List<String> warnings;
}

