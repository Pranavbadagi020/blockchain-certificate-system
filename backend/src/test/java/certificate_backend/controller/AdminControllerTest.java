package certificate_backend.controller;

import certificate_backend.model.AuditEvent;
import certificate_backend.model.Certificate;
import certificate_backend.model.CertificateStatus;
import certificate_backend.model.User;
import certificate_backend.repository.CertificateRepository;
import certificate_backend.service.AuditService;
import certificate_backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController")
class AdminControllerTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(certificateRepository, userService, auditService);
    }

    @Test
    @DisplayName("should return metrics with real counts")
    void shouldReturnMetrics() {
        when(certificateRepository.count()).thenReturn(42L);
        when(certificateRepository.countByStatus(CertificateStatus.VALID)).thenReturn(40L);
        when(certificateRepository.countByStatus(CertificateStatus.REVOKED)).thenReturn(2L);
        when(auditService.countByEventType("VERIFY_ATTEMPT")).thenReturn(150L);
        when(auditService.countByEventType("TAMPER_DETECTED")).thenReturn(5L);
        when(userService.countIssuers()).thenReturn(3L);

        ResponseEntity<Map<String, Object>> response = controller.getMetrics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("totalCertificates")).isEqualTo(42L);
        assertThat(body.get("validCertificates")).isEqualTo(40L);
        assertThat(body.get("revokedCertificates")).isEqualTo(2L);
        assertThat(body.get("verificationAttempts")).isEqualTo(150L);
        assertThat(body.get("tamperingDetections")).isEqualTo(5L);
        assertThat(body.get("totalIssuers")).isEqualTo(3L);
    }

    @Test
    @DisplayName("should return paginated certificates")
    void shouldReturnPaginatedCertificates() {
        Certificate cert = Certificate.builder()
                .certificateId("CERT-2024-10001")
                .studentName("Arjun Mehta")
                .studentRollNumber("CS2020001")
                .course("B.Tech Computer Science")
                .institution("IIT Delhi")
                .status(CertificateStatus.VALID)
                .createdAt(Instant.now())
                .build();

        when(certificateRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cert)));

        ResponseEntity<Map<String, Object>> response = controller.getCertificates(0, 10, null, "ALL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("totalElements")).isEqualTo(1L);
        assertThat(body.get("currentPage")).isEqualTo(0);
        List<?> list = (List<?>) body.get("content");
        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("should return recent audit logs")
    void shouldReturnAuditLogs() {
        AuditEvent event = new AuditEvent("CERT_ISSUED", "admin@certchain.local", "127.0.0.1", "SUCCESS", Map.of());
        when(auditService.getRecentEvents(25)).thenReturn(List.of(event));

        ResponseEntity<List<AuditEvent>> response = controller.getAuditLogs(25);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("should create new issuer account")
    void shouldCreateNewIssuer() {
        User issuer = new User("Dr. Sharma", "sharma@iitd.ac.in", "hashed", "ISSUER", "IIT Delhi");
        issuer.setId("user-iss-1");

        when(userService.createIssuer(anyString(), anyString(), anyString(), anyString())).thenReturn(issuer);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        AdminController.CreateIssuerRequest req = new AdminController.CreateIssuerRequest(
                "Dr. Sharma",
                "sharma@iitd.ac.in",
                "SecureIssuerPassword123",
                "IIT Delhi"
        );

        ResponseEntity<?> response = controller.createIssuer(req, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(auditService).logEvent(eq("ISSUER_CREATED"), anyString(), eq("127.0.0.1"), eq("SUCCESS"), any());
    }
}
