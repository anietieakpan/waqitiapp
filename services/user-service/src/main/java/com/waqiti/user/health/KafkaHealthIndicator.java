package com.waqiti.user.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka Health Indicator
 *
 * Checks Kafka cluster connectivity and broker availability
 *
 * KUBERNETES CONTEXT:
 * - Used by readiness probe to prevent traffic during Kafka outage
 * - Critical for event-driven architecture
 *
 * PRODUCTION REQUIREMENTS:
 * - Must complete in <2 seconds
 * - Should not block application startup
 * - Must handle connection timeouts gracefully
 */
@Slf4j
@Component("kafka")
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 2;

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            // Check Kafka cluster connectivity
            KafkaHealthStatus status = checkKafkaHealth();

            long responseTime = System.currentTimeMillis() - startTime;

            if (status.isHealthy()) {
                return Health.up()
                    .withDetail("cluster", "Kafka")
                    .withDetail("brokers", status.getBrokerCount())
                    .withDetail("clusterId", status.getClusterId())
                    .withDetail("responseTime", responseTime + "ms")
                    .build();
            } else {
                log.error("HEALTH: Kafka health check failed: {}", status.getErrorMessage());
                return Health.down()
                    .withDetail("cluster", "Kafka")
                    .withDetail("error", status.getErrorMessage())
                    .withDetail("timeout", HEALTH_CHECK_TIMEOUT_SECONDS + "s")
                    .build();
            }

        } catch (Exception e) {
            log.error("HEALTH: Kafka health check error", e);
            return Health.down()
                .withDetail("cluster", "Kafka")
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }

    /**
     * Check Kafka cluster health
     */
    private KafkaHealthStatus checkKafkaHealth() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            // Describe cluster with timeout
            DescribeClusterResult clusterResult = adminClient.describeCluster();

            String clusterId = clusterResult.clusterId()
                .get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            int brokerCount = clusterResult.nodes()
                .get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .size();

            if (brokerCount == 0) {
                return KafkaHealthStatus.unhealthy("No brokers available");
            }

            return KafkaHealthStatus.healthy(clusterId, brokerCount);

        } catch (Exception e) {
            log.error("HEALTH: Kafka connectivity check failed: {}", e.getMessage());
            return KafkaHealthStatus.unhealthy(e.getMessage());
        }
    }

    /**
     * Kafka health status
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class KafkaHealthStatus {
        private boolean healthy;
        private String clusterId;
        private int brokerCount;
        private String errorMessage;

        public static KafkaHealthStatus healthy(String clusterId, int brokerCount) {
            return new KafkaHealthStatus(true, clusterId, brokerCount, null);
        }

        public static KafkaHealthStatus unhealthy(String errorMessage) {
            return new KafkaHealthStatus(false, null, 0, errorMessage);
        }
    }
}
