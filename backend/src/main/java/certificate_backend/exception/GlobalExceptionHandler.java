package certificate_backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handling for all REST controllers.
 *
 * <p>Rules:
 * <ul>
 *   <li>Never expose stack traces, internal class names, or Spring internals.</li>
 *   <li>Never expose database error messages that might leak schema information.</li>
 *   <li>Log full details at server side; return safe, structured JSON to clients.</li>
 * </ul>
 *
 * <p>All error responses share the structure:
 * <pre>{@code
 * {
 *   "timestamp": "2024-05-15T10:30:00Z",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "No certificate found with ID: CERT-2024-99999"
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Certificate domain errors ────────────────────────────────────────────

    @ExceptionHandler(CertificateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(CertificateNotFoundException ex) {
        log.debug("Certificate not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateCertificateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateCertificateException ex) {
        log.warn("Duplicate certificate attempt: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ── Bean Validation errors ───────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        log.debug("Validation failed: {}", details);
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed: " + details);
    }


    // ── Malformed or missing request body ─────────────────────────────────────

@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<Map<String, Object>> handleUnreadableBody(
        HttpMessageNotReadableException ex) {
    log.debug("Request body is missing or unreadable: {}", ex.getMessage());
    return buildError(HttpStatus.BAD_REQUEST,
        "Request body is missing or invalid.");
}


        @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return buildError(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds maximum limit of 10 MB.");
    }

    // ── Catch-all: unexpected errors ─────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // Log full details server-side; never reveal to client
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
