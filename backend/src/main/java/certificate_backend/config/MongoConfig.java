package certificate_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB-specific configuration.
 *
 * <p>{@link EnableMongoAuditing} is placed here rather than on
 * {@link certificate_backend.CertificateBackendApplication} so that
 * {@code @WebMvcTest} slices (which load only the web layer) do not
 * pull in MongoDB auditing beans and fail with a missing
 * {@code mongoMappingContext}.
 *
 * <p>This class is picked up by Spring's component scan during a full
 * application context load (production and {@code @SpringBootTest}),
 * but is excluded from web-layer slice tests.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
    // No additional beans needed — @EnableMongoAuditing registers the auditing
    // infrastructure that populates @CreatedDate on Certificate.createdAt.
}
