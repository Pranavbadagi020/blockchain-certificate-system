package certificate_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CertificateBackendApplication")
class CertificateBackendApplicationTests {

    @Test
    @DisplayName("should load application context successfully in isolated test environment")
    void contextLoads() {
        // Passes if the Spring application context starts successfully.
    }
}
