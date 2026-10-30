package certificate_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DatabaseIsolationTest")
class DatabaseIsolationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("test environment must use isolated test database and never the development database")
    void shouldVerifyIsolatedTestDatabase() {
        String dbName = mongoTemplate.getDb().getName();
        assertThat(dbName)
                .as("Automated tests must connect to an isolated test database and NEVER to development certchain")
                .isNotEqualTo("certchain")
                .contains("test");
    }
}
