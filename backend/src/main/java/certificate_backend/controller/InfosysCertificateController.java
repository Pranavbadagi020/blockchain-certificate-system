package certificate_backend.controller;

import certificate_backend.dto.InfosysVerificationResponse;
import certificate_backend.service.InfosysCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for Infosys Springboard certificate verification.
 *
 * <p>Endpoint: {@code POST /api/certificates/verify/infosys}
 *
 * <p>Accepts a multipart PDF upload and returns a structured JSON response
 * describing the verification outcome.
 *
 * <p>This endpoint does NOT require authentication so that teachers can use
 * it without creating accounts. Apply Spring Security rules in
 * {@link certificate_backend.config.SecurityConfig} if authentication is needed.
 */
@Slf4j
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class InfosysCertificateController {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final InfosysCertificateService infosysCertificateService;

    /**
     * Verifies an Infosys Springboard certificate uploaded as a PDF.
     *
     * <p>The file must be:
     * <ul>
     *   <li>A PDF (Content-Type: application/pdf or filename ending in .pdf)</li>
     *   <li>At most 10 MB</li>
     * </ul>
     *
     * @param file the uploaded PDF
     * @return 200 with verification response, or 400 for validation errors
     */
    @PostMapping(
            value = "/verify/infosys",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<InfosysVerificationResponse> verifyInfosysCertificate(
            @RequestParam("file") MultipartFile file) {

        log.info("Received Infosys certificate verification request. "
                + "Filename: {}, Size: {} bytes",
                sanitise(file.getOriginalFilename()), file.getSize());

        // Basic input validation (defence-in-depth; also validated in service)
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    InfosysVerificationResponse.builder()
                            .result("UNABLE_TO_VERIFY")
                            .message("No file was uploaded.")
                            .build());
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(
                    InfosysVerificationResponse.builder()
                            .result("UNABLE_TO_VERIFY")
                            .message("File exceeds the 10 MB maximum size limit.")
                            .build());
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType)
                || (filename != null && filename.toLowerCase().endsWith(".pdf"));

        if (!isPdf) {
            return ResponseEntity.badRequest().body(
                    InfosysVerificationResponse.builder()
                            .result("UNABLE_TO_VERIFY")
                            .message("Uploaded file must be a PDF.")
                            .build());
        }

        InfosysVerificationResponse response = infosysCertificateService.verify(file);
        log.info("Infosys verification result: {}", response.getResult());

        return ResponseEntity.ok(response);
    }

    /** Sanitise filename for logging — prevent log injection. */
    private String sanitise(String name) {
        if (name == null) return "<null>";
        return name.replaceAll("[\\r\\n\\t]", "_")
                   .replaceAll("[^a-zA-Z0-9._\\-]", "_")
                   .substring(0, Math.min(name.length(), 80));
    }
}

