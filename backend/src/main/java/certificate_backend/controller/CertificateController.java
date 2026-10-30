package certificate_backend.controller;

import java.util.List;
import certificate_backend.dto.CertificateResponse;
import certificate_backend.dto.IssueCertificateRequest;
import certificate_backend.dto.VerifyResponse;
import certificate_backend.service.CertificateService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for certificate management.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST /api/certificates/issue          Issue a new certificate
 *   GET  /api/certificates/verify/{id}    Verify a certificate by ID
 *   POST /api/certificates/revoke/{id}    Revoke a certificate (auth required)
 * </pre>
 *
 * <h3>Security</h3>
 * Access control is enforced by {@link certificate_backend.security.SecurityConfig}:
 * <ul>
 *   <li>Verify is public.</li>
 *   <li>Issue is temporarily public for development (see SecurityConfig TODO).</li>
 *   <li>Revoke requires authentication — returns 401 without credentials.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private static final Logger log = LoggerFactory.getLogger(CertificateController.class);

    private final CertificateService service;

    public CertificateController(CertificateService service) {
        this.service = service;
    }

    /**
     * Issue a new certificate and record it in MongoDB.
     *
     * <p>The certificate ID and SHA-256 content hash are generated server-side.
     * The blockchain transaction hash is not set — it will be null until the
     * Solidity smart contract is deployed.
     *
     * @param request validated certificate details
     * @return 201 Created with the certificate response (including generated ID and hash)
     *
     * <pre>
     * POST /api/certificates/issue
     * Content-Type: application/json
     * {
     *   "studentName":       "Arjun Mehta",
     *   "studentRollNumber": "CS2020001",
     *   "course":            "B.Tech Computer Science",
     *   "institution":       "IIT Delhi",
     *   "grade":             "First Class with Distinction",
     *   "issueDate":         "2024-05-15"
     * }
     * </pre>
     */
    @PostMapping("/issue")
    public ResponseEntity<CertificateResponse> issue(
            @Valid @RequestBody IssueCertificateRequest request) {

        log.debug("Issue request received for student: {}", request.getStudentName());
        CertificateResponse response = service.issueCertificate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Verify a certificate by its certificate ID.
     *
     * <p>Recomputes the SHA-256 hash and compares it with the stored value.
     * Returns VALID, REVOKED, or TAMPERED as the verification result.
     *
     * <p><b>Note:</b> This is database-level verification. Blockchain verification
     * will be added when the Solidity smart contract is deployed.
     *
     * @param id the certificate ID (e.g. {@code CERT-2024-00142})
     * @return 200 OK with verification result, or 404 if the ID does not exist
     *
     * <pre>
     * GET /api/certificates/verify/CERT-2024-00142
     * </pre>
     */
    @GetMapping("/verify/{id}")
    public ResponseEntity<VerifyResponse> verify(@PathVariable String id) {
        log.debug("Verify request for certificate ID: {}", id);
        VerifyResponse response = service.verifyCertificate(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public List<CertificateResponse> getAllCertificates() {
         return service.getAllCertificates();
}

    /**
     * Revoke a certificate.
     *
     * <p><b>Authentication required.</b> This endpoint is protected by the security
     * configuration and returns 401 if no valid credentials are provided.
     * Full role-based access control (ROLE_ADMIN) will be enforced once the
     * authentication system is implemented.
     *
     * <p>Revocation is a soft delete — the certificate record is preserved for audit.
     *
     * @param id the certificate ID to revoke
     * @return 200 OK with the updated certificate response, or 404 if not found
     *
     * <pre>
     * POST /api/certificates/revoke/CERT-2024-00142
     * Authorization: Bearer {token}   (required — returns 401 without this)
     * </pre>
     */
    @PostMapping("/revoke/{id}")
    public ResponseEntity<CertificateResponse> revoke(@PathVariable String id) {
        log.info("Revoke request for certificate ID: {}", id);
        CertificateResponse response = service.revokeCertificate(id);
        return ResponseEntity.ok(response);
    }
    /**
     * Verify an uploaded PDF certificate against the database and blockchain registry.
     *
     * @param file the uploaded PDF file
     * @return 200 OK with detailed verification result (VERIFIED, TAMPERING_DETECTED, REVOKED, NOT_FOUND, UNABLE_TO_VERIFY)
     */
    @PostMapping(value = "/verify/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerifyResponse> verifyUpload(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        log.info("Received PDF upload verification request");

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    VerifyResponse.builder()
                            .verificationResult("UNABLE_TO_VERIFY")
                            .message("No file was uploaded or the file is empty.")
                            .blockchainVerified(false)
                            .build()
            );
        }

        try {
            VerifyResponse response = service.verifyCertificatePdf(file.getBytes());
            return ResponseEntity.ok(response);
        } catch (java.io.IOException e) {
            log.warn("Failed to read uploaded file bytes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    VerifyResponse.builder()
                            .verificationResult("UNABLE_TO_VERIFY")
                            .message("Failed to read uploaded file.")
                            .blockchainVerified(false)
                            .build()
            );
        }
    }

}
