package certificate_backend.exception;

/**
 * Thrown when an attempt is made to issue a certificate with an ID that already exists.
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 *
 * <p>This is a defensive exception — the unique MongoDB index will also reject duplicates
 * at the database level, but an application-level check provides a cleaner error message.
 */
public class DuplicateCertificateException extends RuntimeException {

    public DuplicateCertificateException(String certificateId) {
        super("A certificate with ID '" + certificateId + "' already exists");
    }
}
