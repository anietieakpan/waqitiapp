package com.waqiti.common.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Health Indicator for Spring Boot Actuator
 *
 * Provides health check endpoint for Kafka connectivity
 * Accessible via /actuator/health in Spring Boot applications
 *
 * Health Status:
 * - UP: Kafka cluster is accessible and responsive
 * - DOWN: Cannot connect to Kafka cluster or timeout
 *
 * Includes diagnostic details:
 * - Cluster ID
 * - Number of brokers
 * - Controller ID
 * - Connection latency
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
                DescribeClusterOptions options = new DescribeClusterOptions()
                        .timeoutMs((int) TIMEOUT.toMillis());

                DescribeClusterResult clusterResult = adminClient.describeCluster(options);

                String clusterId = clusterResult.clusterId().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                int nodeCount = clusterResult.nodes().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).size();
                String controllerId = clusterResult.controller().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).idString();

                long latency = System.currentTimeMillis() - startTime;

                return Health.up()
                        .withDetail("clusterId", clusterId)
                        .withDetail("brokerCount", nodeCount)
                        .withDetail("controllerId", controllerId)
                        .withDetail("latencyMs", latency)
                        .withDetail("status", "CONNECTED")
                        .build();
            }

        } catch (Exception e) {
            logger.error("Kafka health check failed", e);
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .withDetail("status", "DISCONNECTED")
                    .build();
        }
    }

    /**
     * Check if Kafka is healthy (UP status)
     *
     * @return true if Kafka is accessible, false otherwise
     */
    public boolean isHealthy() {
        Health health = health();
        return health.getStatus().equals(org.springframework.boot.actuate.health.Status.UP);
    }

    /**
     * Get detailed health information as a string
     *
     * @return Health status summary
     */
    public String getHealthSummary() {
        Health health = health();
        return String.format("Kafka Status: %s, Details: %s",
                health.getStatus(),
                health.getDetails());
    }
}
