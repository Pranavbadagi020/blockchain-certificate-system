package certificate_backend.config;

import certificate_backend.model.User;
import certificate_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAccountInitializer")
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin@certchain.local";
    private static final String VALID_PASSWORD = "StrongPassword123!";

    @Test
    @DisplayName("should create new admin account when none exists")
    void shouldCreateAdminAccountWhenNotExists() {
        when(userRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("hashed_strong_password");

        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userRepository, passwordEncoder, ADMIN_EMAIL, VALID_PASSWORD
        );

        initializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(savedUser.getRole()).isEqualTo("ADMIN");
        assertThat(savedUser.getPassword()).isEqualTo("hashed_strong_password");
    }

    @Test
    @DisplayName("should synchronize password when configured password changes")
    void shouldSynchronizePasswordWhenChanged() {
        User existingUser = new User("System Administrator", ADMIN_EMAIL, "old_hash", "ADMIN", "CertChain");
        when(userRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(VALID_PASSWORD, "old_hash")).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("new_hash");

        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userRepository, passwordEncoder, ADMIN_EMAIL, VALID_PASSWORD
        );

        initializer.run();

        verify(userRepository).save(existingUser);
        assertThat(existingUser.getPassword()).isEqualTo("new_hash");
    }

    @Test
    @DisplayName("should not modify admin account when password already matches")
    void shouldNotModifyWhenPasswordMatches() {
        User existingUser = new User("System Administrator", ADMIN_EMAIL, "current_hash", "ADMIN", "CertChain");
        when(userRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(VALID_PASSWORD, "current_hash")).thenReturn(true);

        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userRepository, passwordEncoder, ADMIN_EMAIL, VALID_PASSWORD
        );

        initializer.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should throw exception when password contains fewer than 12 characters")
    void shouldRejectShortPassword() {
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userRepository, passwordEncoder, ADMIN_EMAIL, "short"
        );

        assertThatThrownBy(initializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must contain at least 12 characters");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should do nothing when password is blank or null")
    void shouldSkipWhenPasswordBlank() {
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userRepository, passwordEncoder, ADMIN_EMAIL, ""
        );

        initializer.run();

        verify(userRepository, never()).save(any());
    }
}
