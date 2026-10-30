package certificate_backend.controller;

import certificate_backend.model.User;
import certificate_backend.security.JwtTokenProvider;
import certificate_backend.service.AuditService;
import certificate_backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuditService auditService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userService, jwtTokenProvider, auditService);
    }

    @Test
    @DisplayName("should login successfully and return JWT token for valid credentials")
    void shouldLoginSuccessfully() {
        User user = new User("System Admin", "admin@certchain.local", "encoded", "ADMIN", "CertChain");
        when(userService.findByEmail("admin@certchain.local")).thenReturn(Optional.of(user));
        when(userService.verifyPassword(user, "ValidPass12345")).thenReturn(true);
        when(jwtTokenProvider.generateToken(user)).thenReturn("mock.jwt.token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.login(
                new AuthController.LoginRequest("admin@certchain.local", "ValidPass12345"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("token")).isEqualTo("mock.jwt.token");
        assertThat(body.get("role")).isEqualTo("ADMIN");
        assertThat(body.get("email")).isEqualTo("admin@certchain.local");

        verify(auditService).logEvent(eq("LOGIN_SUCCESS"), eq("admin@certchain.local"), any(), eq("SUCCESS"), any());
    }

    @Test
    @DisplayName("should reject login with 401 when password is invalid")
    void shouldRejectInvalidPassword() {
        User user = new User("System Admin", "admin@certchain.local", "encoded", "ADMIN", "CertChain");
        when(userService.findByEmail("admin@certchain.local")).thenReturn(Optional.of(user));
        when(userService.verifyPassword(user, "WrongPassword")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = controller.login(
                new AuthController.LoginRequest("admin@certchain.local", "WrongPassword"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(auditService).logEvent(eq("LOGIN_FAILED"), eq("admin@certchain.local"), any(), eq("FAILED"), any());
    }

    @Test
    @DisplayName("should reject login with 400 when credentials are empty")
    void shouldRejectEmptyCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = controller.login(
                new AuthController.LoginRequest("", ""),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
