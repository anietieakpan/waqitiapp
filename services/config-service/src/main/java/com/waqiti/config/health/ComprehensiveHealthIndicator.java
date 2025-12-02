package com.waqiti.config.health;

import com.waqiti.config.client.NotificationServiceClient;
import com.waqiti.config.repository.ConfigurationRepository;
import com.waqiti.config.repository.FeatureFlagRepository;
import com.waqiti.config.service.ConfigEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Comprehensive health indicator for config-service
 * Checks all critical dependencies: PostgreSQL, Kafka, Vault, Eureka, Notification Service
 */
@Slf4j
@Component("configService")
@RequiredArgsConstructor
public class ComprehensiveHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final VaultTemplate vaultTemplate;
    private final ConfigurationRepository configurationRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final ConfigEncryptionService encryptionService;

    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public Health health() {
        Instant startTime = Instant.now();
        Map<String, Object> details = new HashMap<>();
        boolean allHealthy = true;

        try {
            // Run all health checks in parallel with timeout
            CompletableFuture<HealthStatus> databaseHealth = CompletableFuture.supplyAsync(
                this::checkDatabaseHealth, executorService);
            CompletableFuture<HealthStatus> kafkaHealth = CompletableFuture.supplyAsync(
                this::checkKafkaHealth, executorService);
            CompletableFuture<HealthStatus> vaultHealth = CompletableFuture.supplyAsync(
                this::checkVaultHealth, executorService);
            CompletableFuture<HealthStatus> notificationHealth = CompletableFuture.supplyAsync(
                this::checkNotificationServiceHealth, executorService);
            CompletableFuture<HealthStatus> encryptionHealth = CompletableFuture.supplyAsync(
                this::checkEncryptionHealth, executorService);

            // Wait for all checks with timeout
            CompletableFuture.allOf(databaseHealth, kafkaHealth, vaultHealth,
                notificationHealth, encryptionHealth)
                .get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Collect results
            HealthStatus dbStatus = databaseHealth.get();
            HealthStatus kfkStatus = kafkaHealth.get();
            HealthStatus vltStatus = vaultHealth.get();
            HealthStatus notStatus = notificationHealth.get();
            HealthStatus encStatus = encryptionHealth.get();

            details.put("database", dbStatus.getDetails());
            details.put("kafka", kfkStatus.getDetails());
            details.put("vault", vltStatus.getDetails());
            details.put("notificationService", notStatus.getDetails());
            details.put("encryption", encStatus.getDetails());

            // Determine overall health
            allHealthy = dbStatus.isHealthy() && kfkStatus.isHealthy() &&
                        vltStatus.isHealthy() && encStatus.isHealthy();
            // Notification service is non-critical (degraded operation allowed)

        } catch (TimeoutException e) {
            log.error("Health check timed out after {} seconds", HEALTH_CHECK_TIMEOUT_SECONDS);
            details.put("error", "Health check timeout");
            allHealthy = false;
        } catch (Exception e) {
            log.error("Health check failed", e);
            details.put("error", e.getMessage());
            allHealthy = false;
        }

        Duration duration = Duration.between(startTime, Instant.now());
        details.put("responseTime", duration.toMillis() + "ms");
        details.put("timestamp", Instant.now().toString());

        return allHealthy ?
            Health.up().withDetails(details).build() :
            Health.down().withDetails(details).build();
    }

    private HealthStatus checkDatabaseHealth() {
        Map<String, Object> details = new HashMap<>();
        try {
            Instant start = Instant.now();

            // Test connection
            try (Connection connection = dataSource.getConnection()) {
                boolean valid = connection.isValid(2);
                if (!valid) {
                    details.put("status", "DOWN");
                    details.put("error", "Connection validation failed");
                    return new HealthStatus(false, details);
                }
            }

            // Test repository access
            long configCount = configurationRepository.count();
            long flagCount = featureFlagRepository.count();

            Duration duration = Duration.between(start, Instant.now());
            details.put("status", "UP");
            details.put("database", "PostgreSQL");
            details.put("configurationCount", configCount);
            details.put("featureFlagCount", flagCount);
            details.put("responseTime", duration.toMillis() + "ms");

            return new HealthStatus(true, details);

        } catch (Exception e) {
            log.error("Database health check failed", e);
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }

    private HealthStatus checkKafkaHealth() {
        Map<String, Object> details = new HashMap<>();
        try {
            Instant start = Instant.now();

            // Check if Kafka producer is available
            // Note: This is a lightweight check - doesn't actually send messages
            kafkaTemplate.getProducerFactory().createProducer();

            Duration duration = Duration.between(start, Instant.now());
            details.put("status", "UP");
            details.put("broker", kafkaTemplate.getProducerFactory().getConfigurationProperties()
                .get("bootstrap.servers"));
            details.put("responseTime", duration.toMillis() + "ms");

            return new HealthStatus(true, details);

        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }

    private HealthStatus checkVaultHealth() {
        Map<String, Object> details = new HashMap<>();
        try {
            Instant start = Instant.now();

            // Check Vault connectivity
            vaultTemplate.opsForSys().health();

            Duration duration = Duration.between(start, Instant.now());
            details.put("status", "UP");
            details.put("responseTime", duration.toMillis() + "ms");

            return new HealthStatus(true, details);

        } catch (Exception e) {
            log.warn("Vault health check failed", e);
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }

    private HealthStatus checkNotificationServiceHealth() {
        Map<String, Object> details = new HashMap<>();
        try {
            Instant start = Instant.now();

            boolean healthy = notificationServiceClient.isNotificationServiceHealthy();

            Duration duration = Duration.between(start, Instant.now());
            details.put("status", healthy ? "UP" : "DOWN");
            details.put("responseTime", duration.toMillis() + "ms");
            details.put("critical", false); // Non-critical dependency

            return new HealthStatus(healthy, details);

        } catch (Exception e) {
            log.warn("Notification service health check failed", e);
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            details.put("critical", false);
            return new HealthStatus(true, details); // Non-critical, don't fail overall health
        }
    }

    private HealthStatus checkEncryptionHealth() {
        Map<String, Object> details = new HashMap<>();
        try {
            Instant start = Instant.now();

            ConfigEncryptionService.EncryptionHealthInfo healthInfo =
                encryptionService.getEncryptionHealth();

            Duration duration = Duration.between(start, Instant.now());
            details.put("status", healthInfo.getStatus());
            details.put("vaultConnected", healthInfo.isVaultConnected());
            details.put("encryptionKeyExists", healthInfo.isEncryptionKeyExists());
            details.put("encryptionWorking", healthInfo.isEncryptionWorking());
            details.put("algorithm", healthInfo.getAlgorithm());
            details.put("keySize", healthInfo.getKeySize());
            details.put("responseTime", duration.toMillis() + "ms");

            boolean healthy = "HEALTHY".equals(healthInfo.getStatus());
            return new HealthStatus(healthy, details);

        } catch (Exception e) {
            log.error("Encryption health check failed", e);
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            return new HealthStatus(false, details);
        }
    }

    // Helper class for health status
    private static class HealthStatus {
        private final boolean healthy;
        private final Map<String, Object> details;

        public HealthStatus(boolean healthy, Map<String, Object> details) {
            this.healthy = healthy;
            this.details = details;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }
}
