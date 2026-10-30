package certificate_backend.controller;


import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import certificate_backend.security.SecurityConfig;
import certificate_backend.dto.CertificateResponse;
import certificate_backend.dto.VerifyResponse;
import certificate_backend.exception.CertificateNotFoundException;
import certificate_backend.exception.DuplicateCertificateException;
import certificate_backend.model.CertificateStatus;
import certificate_backend.service.CertificateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link CertificateController}.
 *
 * <p>Uses {@code @WebMvcTest} so only the web layer (controllers, security,
 * exception handlers) is loaded — no MongoDB, no Spring Data.
 * {@link CertificateService} is replaced with a Mockito mock.
 *
 * <p>Security is active in this slice, so the tests also verify that:
 * <ul>
 *   <li>Public endpoints ({@code /api/health}, {@code GET /api/certificates/verify/**})
 *       return results without credentials.</li>
 *   <li>The revoke endpoint returns 401 without credentials.</li>
 * </ul>
 *
 * <p>Note: Uses Spring Boot 4.1.1 package names:
 * {@code org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest}
 */
@WebMvcTest(controllers = {CertificateController.class, HealthController.class})
@Import(SecurityConfig.class)
@DisplayName("CertificateController")
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CertificateService service;

    // ── Shared test data ─────────────────────────────────────────────────────

    private static final String VALID_REQUEST = """
        {
          "studentName":       "Arjun Mehta",
          "studentRollNumber": "CS2020001",
          "course":            "B.Tech Computer Science",
          "institution":       "IIT Delhi",
          "grade":             "First Class with Distinction",
          "issueDate":         "2024-05-15"
        }
        """;

    private CertificateResponse sampleResponse() {
        return CertificateResponse.builder()
            .certificateId("CERT-2024-10001")
            .studentName("Arjun Mehta")
            .studentRollNumber("CS2020001")
            .course("B.Tech Computer Science")
            .institution("IIT Delhi")
            .grade("First Class with Distinction")
            .issueDate("2024-05-15")
            .certificateHash("a".repeat(64))
            .transactionHash(null)
            .status(CertificateStatus.VALID)
            .createdAt(Instant.now())
            .build();
    }

    private VerifyResponse validVerifyResponse() {
        return VerifyResponse.builder()
            .verificationResult("VALID")
            .message("Certificate is authentic and has not been modified.")
            .certificate(sampleResponse())
            .blockchainVerified(false)
            .blockchainNote("Blockchain verification is not yet available.")
            .build();
    }

    // ── GET /api/health (existing endpoint — must remain public) ──────────────

    @Nested
    @DisplayName("GET /api/health")
    class HealthTests {

        @Test
        @DisplayName("returns 200 without authentication")
        void healthIsPublic() throws Exception {
            mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
        }
    }

    // ── POST /api/certificates/issue ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/certificates/issue")
    @WithMockUser(roles = "ISSUER")
    class IssueTests {

        @Test
        @DisplayName("returns 201 with valid request body")
        void returns201WithValidRequest() throws Exception {
            when(service.issueCertificate(any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/certificates/issue")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificateId").value("CERT-2024-10001"))
                .andExpect(jsonPath("$.studentName").value("Arjun Mehta"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.certificateHash").exists());
        }

        @Test
        @DisplayName("transactionHash is absent from response (blockchain not integrated)")
        void transactionHashAbsentWhenNull() throws Exception {
            when(service.issueCertificate(any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/certificates/issue")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                // Jackson configured with non_null — null fields omitted from JSON
                .andExpect(jsonPath("$.transactionHash").doesNotExist());
        }

        @Test
        @DisplayName("returns 400 when studentName is blank")
        void returns400WhenNameBlank() throws Exception {
            String bad = """
                {
                  "studentName":       "",
                  "studentRollNumber": "CS2020001",
                  "course":            "B.Tech CS",
                  "institution":       "IIT Delhi",
                  "issueDate":         "2024-05-15"
                }
                """;

            mockMvc.perform(post("/api/certificates/issue")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("returns 400 when issueDate format is wrong")
        void returns400WhenDateFormatInvalid() throws Exception {
            String bad = """
                {
                  "studentName":       "Alice",
                  "studentRollNumber": "R001",
                  "course":            "CS",
                  "institution":       "MIT",
                  "issueDate":         "15-05-2024"
                }
                """;

            mockMvc.perform(post("/api/certificates/issue")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("returns 400 when body is missing entirely")
        void returns400WhenBodyMissing() throws Exception {
            mockMvc.perform(post("/api/certificates/issue")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 409 when certificate ID already exists")
        void returns409OnDuplicate() throws Exception {
            when(service.issueCertificate(any()))
                .thenThrow(new DuplicateCertificateException("CERT-2024-10001"));

            mockMvc.perform(post("/api/certificates/issue")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        }
    }

    // ── GET /api/certificates/verify/{id} ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/certificates/verify/{id}")
    class VerifyTests {

        @Test
        @DisplayName("returns 200 with VALID result — no authentication required")
        void returns200WithValidResultPublicly() throws Exception {
            when(service.verifyCertificate("CERT-2024-10001"))
                .thenReturn(validVerifyResponse());

            // No auth, no CSRF token — GET verify is public
            mockMvc.perform(get("/api/certificates/verify/CERT-2024-10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationResult").value("VALID"))
                .andExpect(jsonPath("$.blockchainVerified").value(false))
                .andExpect(jsonPath("$.blockchainNote").isNotEmpty())
                .andExpect(jsonPath("$.certificate.certificateId").value("CERT-2024-10001"));
        }

        @Test
        @DisplayName("returns 200 with REVOKED result")
        void returns200WithRevokedResult() throws Exception {
            VerifyResponse revokedResp = VerifyResponse.builder()
                .verificationResult("REVOKED")
                .message("This certificate has been revoked by the issuing institution.")
                .certificate(sampleResponse())
                .blockchainVerified(false)
                .blockchainNote("Blockchain verification not yet available.")
                .build();

            when(service.verifyCertificate("CERT-2024-10001")).thenReturn(revokedResp);

            mockMvc.perform(get("/api/certificates/verify/CERT-2024-10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationResult").value("REVOKED"));
        }

        @Test
        @DisplayName("returns 404 for unknown certificate ID")
        void returns404ForUnknownId() throws Exception {
            when(service.verifyCertificate("CERT-9999-99999"))
                .thenThrow(new CertificateNotFoundException("CERT-9999-99999"));

            mockMvc.perform(get("/api/certificates/verify/CERT-9999-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                    "No certificate found with ID: CERT-9999-99999"));
        }

        @Test
        @DisplayName("error response never exposes internal details")
        void errorResponseHidesInternalDetails() throws Exception {
            when(service.verifyCertificate(any()))
                .thenThrow(new RuntimeException("internal DB error — do not expose"));

            mockMvc.perform(get("/api/certificates/verify/CERT-2024-10001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(
                    "An unexpected error occurred. Please try again later."));
        }
    }

    // ── POST /api/certificates/revoke/{id} ───────────────────────────────────

    @Nested
    @DisplayName("POST /api/certificates/revoke/{id}")

    

    class RevokeTests {

    @Test
    @DisplayName("returns 401 when called without authentication")
    void returns401WithoutAuthentication() throws Exception {
        // No credentials: revocation must not be publicly accessible.
        mockMvc.perform(post("/api/certificates/revoke/CERT-2024-10001")
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }
}

    // ── POST /api/certificates/verify/upload ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/certificates/verify/upload")
    class VerifyUploadTests {

        @Test
        @DisplayName("returns 200 with VERIFIED result for valid PDF upload")
        void returns200WithVerifiedResultForPdfUpload() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "cert.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    "%PDF-1.4 sample content".getBytes()
            );

            VerifyResponse verifiedResp = VerifyResponse.builder()
                    .verificationResult("VERIFIED")
                    .message("Certificate verified: the uploaded document matches the authentic blockchain record.")
                    .certificate(sampleResponse())
                    .blockchainVerified(true)
                    .blockchainNote("Certificate successfully verified against the Ethereum blockchain registry.")
                    .build();

            when(service.verifyCertificatePdf(any())).thenReturn(verifiedResp);

            mockMvc.perform(multipart("/api/certificates/verify/upload")
                            .file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationResult").value("VERIFIED"))
                    .andExpect(jsonPath("$.blockchainVerified").value(true));
        }

        @Test
        @DisplayName("returns 400 when uploaded file is empty")
        void returns400WhenFileEmpty() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file",
                    "empty.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    new byte[0]
            );

            mockMvc.perform(multipart("/api/certificates/verify/upload")
                            .file(emptyFile))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.verificationResult").value("UNABLE_TO_VERIFY"));
        }
    }

}
