package certificate_backend.controller;

import certificate_backend.model.User;
import certificate_backend.security.JwtTokenProvider;
import certificate_backend.service.AuditService;
import certificate_backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    public AuthController(
            UserService userService,
            JwtTokenProvider jwtTokenProvider,
            AuditService auditService) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIp(httpRequest);

        if (request == null
                || request.email() == null
                || request.password() == null
                || request.email().isBlank()
                || request.password().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and password are required."));
        }

        User user = userService.findByEmail(request.email())
                .orElse(null);

        if (user == null || !userService.verifyPassword(user, request.password())) {
            auditService.logEvent(
                    "LOGIN_FAILED",
                    request.email() != null ? request.email().trim().toLowerCase() : "UNKNOWN",
                    ipAddress,
                    "FAILED",
                    Map.of("reason", "Invalid credentials")
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password."));
        }

        String role = user.getRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"ISSUER".equalsIgnoreCase(role)) {
            auditService.logEvent(
                    "LOGIN_FAILED",
                    user.getEmail(),
                    ipAddress,
                    "FAILED",
                    Map.of("reason", "Unauthorized role: " + role)
            );
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Role not authorized for dashboard access."));
        }

        String token = jwtTokenProvider.generateToken(user);

        auditService.logEvent(
                "LOGIN_SUCCESS",
                user.getEmail(),
                ipAddress,
                "SUCCESS",
                Map.of("role", user.getRole())
        );

        return ResponseEntity.ok(Map.of(
                "message", "Login successful.",
                "token", token,
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "institution", user.getInstitution() != null ? user.getInstitution() : ""
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }

        String email = auth.getName();
        return userService.findByEmail(email)
                .map(user -> ResponseEntity.ok(Map.of(
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "role", user.getRole(),
                        "institution", user.getInstitution() != null ? user.getInstitution() : ""
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record LoginRequest(String email, String password) {}
}
