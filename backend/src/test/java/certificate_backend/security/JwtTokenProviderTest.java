package certificate_backend.security;

import certificate_backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                "SuperSecretTestingKeyMustBeAtLeast256BitsLongForHmacSha256Security123456",
                3600000 // 1 hour
        );
    }

    @Test
    @DisplayName("should generate and validate valid token")
    void shouldGenerateAndValidateToken() {
        User user = new User("Alice Admin", "admin@certchain.local", "hashedpass", "ADMIN", "CertChain Univ");
        user.setId("user-123");

        String token = tokenProvider.generateToken(user);
        assertThat(token).isNotBlank();

        boolean isValid = tokenProvider.validateToken(token);
        assertThat(isValid).isTrue();

        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo("admin@certchain.local");
        assertThat(tokenProvider.getRoleFromToken(token)).isEqualTo("ADMIN");
        assertThat(tokenProvider.getNameFromToken(token)).isEqualTo("Alice Admin");
    }

    @Test
    @DisplayName("should reject malformed or tampered token")
    void shouldRejectMalformedToken() {
        assertThat(tokenProvider.validateToken("invalid.token.string")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("should reject expired token")
    void shouldRejectExpiredToken() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
                "SuperSecretTestingKeyMustBeAtLeast256BitsLongForHmacSha256Security123456",
                -1000 // already expired
        );

        User user = new User("Bob", "bob@example.com", "pass", "ISSUER", "Test");
        String token = shortLivedProvider.generateToken(user);

        assertThat(tokenProvider.validateToken(token)).isFalse();
    }
}
