package com.waqiti.corebanking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for integration tests
 * Provides PostgreSQL, Redis, and Kafka containers for testing
 */
@Slf4j
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    private static final String POSTGRES_IMAGE = "postgres:15-alpine";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.8.0";

    /**
     * PostgreSQL container for database testing
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("waqiti_core_banking_test")
                .withUsername("test_user")
                .withPassword("test_password")
                .withReuse(true) // Reuse container across test runs for speed
                .withCommand(
                        "postgres",
                        "-c", "fsync=off", // Faster for tests
                        "-c", "max_connections=50",
                        "-c", "shared_buffers=256MB"
                );

        postgres.start();

        log.info("PostgreSQL Testcontainer started: {}:{}",
                postgres.getHost(),
                postgres.getFirstMappedPort());
        log.info("JDBC URL: {}", postgres.getJdbcUrl());

        return postgres;
    }

    /**
     * Redis container for caching and session testing
     */
    @Bean
    public GenericContainer<?> redisContainer() {
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(6379)
                .withReuse(true)
                .withCommand("redis-server", "--requirepass", "test_redis_password");

        redis.start();

        log.info("Redis Testcontainer started: {}:{}",
                redis.getHost(),
                redis.getFirstMappedPort());

        return redis;
    }

    /**
     * Kafka container for event-driven testing
     */
    @Bean
    public KafkaContainer kafkaContainer() {
        KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withReuse(true)
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
                .withEnv("KAFKA_NUM_PARTITIONS", "1")
                .withEnv("KAFKA_DEFAULT_REPLICATION_FACTOR", "1");

        kafka.start();

        log.info("Kafka Testcontainer started: {}", kafka.getBootstrapServers());

        return kafka;
    }

    /**
     * Dynamic property source configuration for Testcontainers
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Note: PostgreSQL properties are auto-configured by @ServiceConnection
        // We only need to manually configure Redis and Kafka
        log.info("Configuring test properties from Testcontainers");
    }

    /**
     * Configure Redis properties from container
     */
    @Bean
    public RedisPropertyConfigurer redisPropertyConfigurer(GenericContainer<?> redisContainer,
                                                           DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379).toString());
        registry.add("spring.data.redis.password", () -> "test_redis_password");

        log.info("Redis configured at {}:{}",
                redisContainer.getHost(),
                redisContainer.getMappedPort(6379));

        return new RedisPropertyConfigurer();
    }

    /**
     * Configure Kafka properties from container
     */
    @Bean
    public KafkaPropertyConfigurer kafkaPropertyConfigurer(KafkaContainer kafkaContainer,
                                                           DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);

        log.info("Kafka configured at {}", kafkaContainer.getBootstrapServers());

        return new KafkaPropertyConfigurer();
    }

    /**
     * Marker class for Redis configuration
     */
    public static class RedisPropertyConfigurer {}

    /**
     * Marker class for Kafka configuration
     */
    public static class KafkaPropertyConfigurer {}
}
