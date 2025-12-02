package com.waqiti.business;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base Integration Test Class
 *
 * Provides TestContainers infrastructure for integration tests:
 * - PostgreSQL 15 database
 * - Apache Kafka message broker
 *
 * All integration tests should extend this class to ensure
 * consistent test environment setup.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine")
    )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true); // Reuse container across tests for performance

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
    )
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        // Clean state before each test if needed
        // Can be overridden in subclasses
    }

    /**
     * Wait for async operations to complete
     */
    protected void waitForAsyncProcessing() throws InterruptedException {
        Thread.sleep(100);
    }

    /**
     * Wait with custom timeout
     */
    protected void waitForAsyncProcessing(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
