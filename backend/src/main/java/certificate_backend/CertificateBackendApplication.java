package certificate_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Blockchain Certificate Verification System backend.
 *
 * <p>MongoDB auditing ({@code @CreatedDate}) is enabled in
 * {@link certificate_backend.config.MongoConfig} rather than here,
 * so that {@code @WebMvcTest} slices do not pull in MongoDB beans.
 */
@SpringBootApplication
public class CertificateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CertificateBackendApplication.class, args);
	}
}

