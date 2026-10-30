package certificate_backend.controller;

import certificate_backend.dto.CertificateResponse;
import certificate_backend.model.AuditEvent;
import certificate_backend.model.Certificate;
import certificate_backend.model.CertificateStatus;
import certificate_backend.model.User;
import certificate_backend.repository.CertificateRepository;
import certificate_backend.service.AuditService;
import certificate_backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CertificateRepository certificateRepository;
    private final UserService userService;
    private final AuditService auditService;

    public AdminController(
            CertificateRepository certificateRepository,
            UserService userService,
            AuditService auditService) {
        this.certificateRepository = certificateRepository;
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        long total = certificateRepository.count();
        long valid = certificateRepository.countByStatus(CertificateStatus.VALID);
        long revoked = certificateRepository.countByStatus(CertificateStatus.REVOKED);
        long verifyAttempts = auditService.countByEventType("VERIFY_ATTEMPT");
        long tamperDetections = auditService.countByEventType("TAMPER_DETECTED");
        long issuers = userService.countIssuers();

        metrics.put("totalCertificates", total);
        metrics.put("validCertificates", valid);
        metrics.put("revokedCertificates", revoked);
        metrics.put("verificationAttempts", verifyAttempts);
        metrics.put("tamperingDetections", tamperDetections);
        metrics.put("totalIssuers", issuers);

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/certificates")
    public ResponseEntity<Map<String, Object>> getCertificates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String status) {

        int boundedSize = Math.min(Math.max(size, 1), 50);
        int boundedPage = Math.max(page, 0);

        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Certificate> certPage;

        boolean hasSearch = StringUtils.hasText(search);
        boolean filterStatus = StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status);

        if (filterStatus) {
            CertificateStatus certStatus;
            try {
                certStatus = CertificateStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                certStatus = CertificateStatus.VALID;
            }

            if (hasSearch) {
                certPage = certificateRepository.searchByStatus(certStatus, search.trim(), pageable);
            } else {
                certPage = certificateRepository.findByStatus(certStatus, pageable);
            }
        } else {
            if (hasSearch) {
                certPage = certificateRepository.searchAll(search.trim(), pageable);
            } else {
                certPage = certificateRepository.findAll(pageable);
            }
        }

        List<CertificateResponse> dtos = certPage.getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", dtos);
        response.put("currentPage", certPage.getNumber());
        response.put("totalElements", certPage.getTotalElements());
        response.put("totalPages", certPage.getTotalPages());
        response.put("size", certPage.getSize());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditEvent>> getAuditLogs(
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(auditService.getRecentEvents(limit));
    }

    @GetMapping("/issuers")
    public ResponseEntity<List<Map<String, Object>>> getIssuers() {
        List<Map<String, Object>> issuers = userService.getAllIssuers()
                .stream()
                .map(user -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", user.getId());
                    map.put("name", user.getName());
                    map.put("email", user.getEmail());
                    map.put("role", user.getRole());
                    map.put("institution", user.getInstitution() != null ? user.getInstitution() : "");
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(issuers);
    }

    @PostMapping("/issuers")
    public ResponseEntity<?> createIssuer(
            @Valid @RequestBody CreateIssuerRequest request,
            HttpServletRequest httpRequest) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null) ? auth.getName() : "ADMIN";

        try {
            User issuer = userService.createIssuer(
                    request.name(),
                    request.email(),
                    request.password(),
                    request.institution()
            );

            auditService.logEvent(
                    "ISSUER_CREATED",
                    actor,
                    httpRequest.getRemoteAddr(),
                    "SUCCESS",
                    Map.of("issuerEmail", issuer.getEmail(), "institution", issuer.getInstitution() != null ? issuer.getInstitution() : "")
            );

            Map<String, Object> response = new HashMap<>();
            response.put("id", issuer.getId());
            response.put("name", issuer.getName());
            response.put("email", issuer.getEmail());
            response.put("role", issuer.getRole());
            response.put("institution", issuer.getInstitution());
            response.put("message", "Issuer registered successfully.");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private CertificateResponse toResponse(Certificate cert) {
        return CertificateResponse.builder()
                .certificateId(cert.getCertificateId())
                .studentName(cert.getStudentName())
                .studentRollNumber(cert.getStudentRollNumber())
                .course(cert.getCourse())
                .institution(cert.getInstitution())
                .grade(cert.getGrade())
                .issueDate(cert.getIssueDate())
                .certificateHash(cert.getCertificateHash())
                .transactionHash(cert.getTransactionHash())
                .status(cert.getStatus())
                .createdAt(cert.getCreatedAt())
                .build();
    }

    public record CreateIssuerRequest(
            @NotBlank(message = "Name is required") String name,
            @NotBlank(message = "Email is required") @Email(message = "Valid email is required") String email,
            @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,
            @NotBlank(message = "Institution is required") String institution
    ) {}
}
