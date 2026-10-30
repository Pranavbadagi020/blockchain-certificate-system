package certificate_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing a blockchain-backed certificate.
 *
 * <h3>Field notes</h3>
 * <ul>
 *   <li>{@link #certificateId} — human-readable unique ID (e.g. {@code CERT-2024-00142}).
 *       Indexed as unique in MongoDB.</li>
 *   <li>{@link #certificateHash} — SHA-256 of the <em>immutable</em> certificate fields
 *       (see {@code CertificateService.computeHash()}). Used to detect tampering.</li>
 *   <li>{@link #transactionHash} — Ethereum transaction hash. {@code null} until the
 *       Solidity smart contract is deployed and the certificate is recorded on-chain.
 *       Do not confuse with {@link #certificateHash}.</li>
 *   <li>{@link #status} — {@code VALID} or {@code REVOKED}. Not included in the
 *       certificate content hash so revocation does not invalidate the hash itself.</li>
 * </ul>
 */
@Document(collection = "certificates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    /** MongoDB internal document ID (ObjectId as String). */
    @Id
    private String id;

    /**
     * Human-readable certificate ID, unique across all certificates.
     * Format: {@code CERT-YYYY-NNNNN}
     * Indexed with a unique constraint to prevent duplicate issuance.
     */
    @Indexed(unique = true)
    private String certificateId;

    /** Full name of the certificate holder. */
    private String studentName;

    /** Institutional roll number or student ID. */
    private String studentRollNumber;

    /** Programme or course title (e.g. B.Tech Computer Science). */
    private String course;

    /** Full name of the issuing institution. */
    private String institution;

    /**
     * Grade or classification awarded (e.g. "First Class with Distinction").
     * Included in the content hash — changing the grade is detected as tampering.
     */
    private String grade;

    /**
     * Date of issue in ISO-8601 format (e.g. {@code 2024-05-15}).
     * Stored as a String to avoid timezone ambiguity.
     * Included in the content hash.
     */
    private String issueDate;

    /**
     * SHA-256 hex digest computed from the deterministic canonical form of
     * the immutable certificate fields. See {@code CertificateService.computeHash()}.
     *
     * <p>Canonical form: {@code studentName|studentRollNumber|course|institution|grade|issueDate}
     */
    private String certificateHash;

    /**
     * Ethereum transaction hash from the on-chain recording.
     * {@code null} until blockchain integration is complete.
     *
     * <p><b>NOT</b> the same as {@link #certificateHash}.
     */
    private String transactionHash;  // nullable — set by blockchain integration later

    /**
     * Certificate lifecycle status.
     * {@code VALID} — certificate is active and verifiable.
     * {@code REVOKED} — certificate has been revoked by the issuing institution.
     */
    private CertificateStatus status;

    /** Timestamp when this document was first persisted. Set automatically. */
    @CreatedDate
    private Instant createdAt;
}
