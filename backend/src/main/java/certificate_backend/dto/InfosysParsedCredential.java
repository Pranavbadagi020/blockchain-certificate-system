package certificate_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a parsed Infosys Springboard / Wingspan Verifiable Credential
 * extracted from the QR code.  All fields are optional because real-world
 * certificates may vary in structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfosysParsedCredential {

    // ── Credential identity ───────────────────────────────────────────────────

    /** Raw QR text content before parsing. */
    private String rawQrContent;

    /** Whether the QR content was a URL or an embedded JSON credential. */
    private String qrContentType; // "URL", "JSON_LD", "JWT", "UNKNOWN"

    /** Credential ID (urn:uuid:… or similar). */
    private String credentialId;

    /** Credential types list, e.g. ["VerifiableCredential", "TrainingCertificate"]. */
    private List<String> credentialTypes;

    // ── Subject fields ────────────────────────────────────────────────────────

    /** Recipient / student name as stated in the credential. */
    private String issuedTo;

    /** Course or training name. */
    private String courseName;

    /** Course or content identifier. */
    private String courseId;

    /** Date the course was completed (ISO-8601 or human-readable). */
    private String completionDate;

    /** Date the credential was issued. */
    private String issuanceDate;

    // ── Issuer information ────────────────────────────────────────────────────

    /** Issuer identifier (URL or DID). */
    private String issuer;

    // ── Proof block ───────────────────────────────────────────────────────────

    /** Cryptographic proof type, e.g. "Ed25519Signature2018". */
    private String proofType;

    /** Verification method DID/URL, e.g. "did:india". */
    private String verificationMethod;

    /** Proof purpose, e.g. "assertionMethod". */
    private String proofPurpose;

    /** JWS or proof value (present but NOT verified by this system). */
    private String proofValuePresent; // "YES" | "NO"

    /** Timestamp when the proof was created. */
    private String proofCreated;

    // ── Verification URL ─────────────────────────────────────────────────────

    /**
     * If the QR is a URL, or if a URL can be derived from the credential,
     * this is the official Infosys verification URL the teacher should visit.
     */
    private String officialVerificationUrl;

    // ── Parse metadata ────────────────────────────────────────────────────────

    /** Any warnings encountered during parsing. */
    private List<String> parseWarnings;
}

