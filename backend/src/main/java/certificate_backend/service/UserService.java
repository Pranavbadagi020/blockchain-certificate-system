package certificate_backend.service;

import certificate_backend.model.User;
import certificate_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createAdmin(
            String name,
            String email,
            String rawPassword,
            String institution) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (rawPassword == null || rawPassword.length() < 12) {
            throw new IllegalArgumentException(
                    "Password must contain at least 12 characters.");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        User admin = new User(
                name,
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                "ADMIN",
                institution
        );

        return userRepository.save(admin);
    }

    public User createIssuer(
            String name,
            String email,
            String rawPassword,
            String institution) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters.");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        User issuer = new User(
                name,
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                "ISSUER",
                institution
        );

        return userRepository.save(issuer);
    }

    public List<User> getAllIssuers() {
        return userRepository.findByRole("ISSUER");
    }

    public long countIssuers() {
        return userRepository.countByRole("ISSUER");
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public boolean verifyPassword(User user, String rawPassword) {
        return user != null
                && rawPassword != null
                && passwordEncoder.matches(
                        rawPassword,
                        user.getPassword()
                );
    }
}
