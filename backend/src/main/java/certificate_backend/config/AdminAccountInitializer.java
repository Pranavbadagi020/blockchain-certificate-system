package certificate_backend.config;

import certificate_backend.model.User;
import certificate_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.admin.initialize", havingValue = "true", matchIfMissing = true)
public class AdminAccountInitializer implements CommandLineRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            logger.warn(
                    "Admin account not initialized: set the ADMIN_PASSWORD environment variable.");
            return;
        }

        if (adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must contain at least 12 characters.");
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            User existing = userRepository.findByEmail(normalizedEmail).orElse(null);
            if (existing != null && !passwordEncoder.matches(adminPassword, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(existing);
                logger.info("Admin password synchronized with configured ADMIN_PASSWORD.");
            } else {
                logger.info("Configured admin account already exists and is up to date.");
            }
            return;
        }

        User admin = new User(
                "System Administrator",
                normalizedEmail,
                passwordEncoder.encode(adminPassword),
                "ADMIN",
                "Certificate Verification System"
        );

        userRepository.save(admin);

        logger.info("Admin account created successfully.");
    }
}