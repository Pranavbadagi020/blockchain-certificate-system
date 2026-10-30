package certificate_backend.exception;

/**
 * Thrown when a certificate lookup finds no document with the given certificate ID.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(String certificateId) {
        super("No certificate found with ID: " + certificateId);
    }
}
