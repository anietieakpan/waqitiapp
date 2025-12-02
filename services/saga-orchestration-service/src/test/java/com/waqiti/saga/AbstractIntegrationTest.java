package com.waqiti.saga;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.waqiti.saga.repository.SagaRepository;

/**
 * Base class for integration tests with TestContainers
 *
 * Provides:
 * - PostgreSQL container for database tests
 * - Kafka container for messaging tests
 * - Redis container for distributed locking tests
 * - Common test utilities
 *
 * All integration tests should extend this class.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test"
    }
)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * PostgreSQL 15 container for database tests
     * Shared across all tests in the same JVM
     */
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("waqiti_saga_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    /**
     * Kafka container for messaging tests
     * Confluent Platform 7.8.0 (Kafka 3.9.0)
     */
    @Container
    protected static final KafkaContainer KAFKA_CONTAINER =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
            .withReuse(true);

    /**
     * Redis 7 container for distributed locking tests
     */
    @Container
    protected static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @Autowired
    protected SagaRepository sagaRepository;

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Configure Spring properties dynamically from TestContainers
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);

        // Kafka configuration
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
        registry.add("kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);

        // Redis configuration
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }

    /**
     * Clean up database before each test
     */
    @BeforeEach
    public void setUp() {
        // Clean up any existing saga data
        sagaRepository.deleteAll();
    }

    /**
     * Wait for Kafka messages to be processed
     *
     * @param timeoutMs Maximum time to wait in milliseconds
     */
    protected void waitForKafkaProcessing(long timeoutMs) throws InterruptedException {
        Thread.sleep(timeoutMs);
    }

    /**
     * Wait for async saga execution to complete
     *
     * @param timeoutMs Maximum time to wait in milliseconds
     */
    protected void waitForSagaCompletion(long timeoutMs) throws InterruptedException {
        Thread.sleep(timeoutMs);
    }
}
