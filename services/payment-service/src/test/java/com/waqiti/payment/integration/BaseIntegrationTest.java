package com.waqiti.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests with full infrastructure.
 *
 * INFRASTRUCTURE:
 * - PostgreSQL (Testcontainers)
 * - Kafka (Testcontainers)
 * - Redis (Testcontainers)
 * - Spring Boot application context
 * - MockMvc for HTTP testing
 *
 * FEATURES:
 * - Automatic cleanup between tests
 * - Transaction rollback for data isolation
 * - Embedded Kafka for event testing
 * - Helper methods for common operations
 *
 * USAGE:
 * Extend this class for all integration tests that need full infrastructure.
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // PostgreSQL container
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("waqiti_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    // Kafka container
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
        .withReuse(true);

    // Redis container
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withReuse(true);

    /**
     * Configure Spring properties dynamically from Testcontainers.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Test-specific configurations
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");
    }

    @BeforeEach
    void setUp() {
        // Override in subclasses for test-specific setup
    }

    /**
     * Helper method to create test authorization header.
     */
    protected String createAuthHeader(String userId, String... roles) {
        // Create JWT token for testing
        return "Bearer " + createTestJwt(userId, roles);
    }

    /**
     * Creates a test JWT token.
     */
    protected String createTestJwt(String userId, String... roles) {
        // Implementation would create actual JWT for testing
        // For now, return mock token
        return "test-jwt-token-" + userId;
    }

    /**
     * Helper to convert object to JSON string.
     */
    protected String toJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    /**
     * Helper to convert JSON string to object.
     */
    protected <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }
}
