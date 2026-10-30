package certificate_backend.model;

/**
 * Lifecycle status of a certificate.
 *
 * <ul>
 *   <li>{@link #VALID}   — the certificate is active, verifiable, and has not been revoked.</li>
 *   <li>{@link #REVOKED} — the issuing institution has revoked this certificate.
 *       The certificate record is preserved for audit purposes.</li>
 * </ul>
 */
public enum CertificateStatus {
    VALID,
    REVOKED
}
