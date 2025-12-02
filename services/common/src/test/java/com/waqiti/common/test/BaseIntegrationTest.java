package com.waqiti.common.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

/**
 * Base class for integration tests using TestContainers.
 *
 * Provides a complete integration test environment with:
 * - PostgreSQL database container
 * - Redis cache container
 * - Database migration and cleanup
 * - Shared container lifecycle management
 * - Spring Boot application context
 *
 * Containers are shared across all tests to improve performance.
 * Each test gets a clean database state via @BeforeEach cleanup.
 *
 * Usage:
 * <pre>
 * {@code
 * @SpringBootTest
 * class PaymentIntegrationTest extends BaseIntegrationTest {
 *     @Autowired
 *     private PaymentService paymentService;
 *
 *     @Test
 *     void shouldProcessPaymentEndToEnd() {
 *         // Integration test implementation
 *     }
 * }
 * }
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration-test")
public abstract class BaseIntegrationTest {

    /**
     * Shared network for container communication
     */
    protected static Network network = Network.newNetwork();

    /**
     * PostgreSQL container for database testing.
     * Shared across all tests for performance.
     */
    protected static PostgreSQLContainer<?> postgresContainer;

    /**
     * Redis container for cache testing.
     * Shared across all tests for performance.
     */
    protected static GenericContainer<?> redisContainer;

    @Autowired(required = false)
    protected DataSource dataSource;

    @Autowired(required = false)
    protected JdbcTemplate jdbcTemplate;

    /**
     * Start all TestContainers before any tests run.
     * Containers are reused across all test classes for efficiency.
     */
    @BeforeAll
    public static void startContainers() {
        // Start PostgreSQL container
        postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("waqiti_test")
                .withUsername("waqiti_test")
                .withPassword("waqiti_test_password")
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withReuse(true);

        postgresContainer.start();

        // Start Redis container
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withNetwork(network)
                .withNetworkAliases("redis")
                .withReuse(true);

        redisContainer.start();

        System.out.println("=================================================================");
        System.out.println("TestContainers Started Successfully");
        System.out.println("PostgreSQL JDBC URL: " + postgresContainer.getJdbcUrl());
        System.out.println("Redis Host: " + redisContainer.getHost());
        System.out.println("Redis Port: " + redisContainer.getFirstMappedPort());
        System.out.println("=================================================================");
    }

    /**
     * Stop all TestContainers after all tests complete.
     */
    @AfterAll
    public static void stopContainers() {
        if (postgresContainer != null && postgresContainer.isRunning()) {
            postgresContainer.stop();
        }
        if (redisContainer != null && redisContainer.isRunning()) {
            redisContainer.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    /**
     * Configure Spring properties dynamically with container connection details.
     *
     * @param registry Dynamic property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // JPA configuration for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // Flyway configuration
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");

        // Redis configuration
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getFirstMappedPort().toString());

        // Kafka configuration (will be overridden in BaseKafkaTest)
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");

        // Disable cloud config for tests
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");

        // Test-specific configurations
        registry.add("logging.level.org.hibernate.SQL", () -> "DEBUG");
        registry.add("logging.level.org.hibernate.type.descriptor.sql.BasicBinder", () -> "TRACE");
    }

    /**
     * Clean database before each test to ensure isolation.
     * Override this method in subclasses for custom cleanup logic.
     */
    @BeforeEach
    public void cleanDatabase() {
        if (jdbcTemplate != null) {
            // Truncate all tables (customize based on your schema)
            // This is a safe approach that maintains referential integrity
            jdbcTemplate.execute("SET session_replication_role = 'replica'");

            // Get all table names
            jdbcTemplate.query(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                (rs, rowNum) -> rs.getString("tablename")
            ).forEach(tableName -> {
                try {
                    jdbcTemplate.execute("TRUNCATE TABLE " + tableName + " CASCADE");
                } catch (Exception e) {
                    // Log but don't fail - some tables might not exist
                    System.out.println("Could not truncate table: " + tableName);
                }
            });

            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }
    }

    /**
     * Execute SQL script for test data setup.
     *
     * @param sql SQL script to execute
     */
    protected void executeSql(String sql) {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute(sql);
        }
    }

    /**
     * Count rows in a table.
     *
     * @param tableName Table name
     * @return Row count
     */
    protected long countRowsInTable(String tableName) {
        if (jdbcTemplate == null) {
            return 0;
        }
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName,
            Long.class
        );
        return count != null ? count : 0;
    }

    /**
     * Verify table is empty.
     *
     * @param tableName Table name
     * @return true if table is empty
     */
    protected boolean isTableEmpty(String tableName) {
        return countRowsInTable(tableName) == 0;
    }

    /**
     * Get PostgreSQL container for advanced usage.
     *
     * @return PostgreSQL container
     */
    protected PostgreSQLContainer<?> getPostgresContainer() {
        return postgresContainer;
    }

    /**
     * Get Redis container for advanced usage.
     *
     * @return Redis container
     */
    protected GenericContainer<?> getRedisContainer() {
        return redisContainer;
    }

    /**
     * Log integration test message.
     *
     * @param message Message to log
     */
    protected void log(String message) {
        System.out.println("[INTEGRATION TEST] " + message);
    }

    /**
     * Log integration test message with formatting.
     *
     * @param format Format string
     * @param args Arguments
     */
    protected void log(String format, Object... args) {
        System.out.println("[INTEGRATION TEST] " + String.format(format, args));
    }
}
