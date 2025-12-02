package com.waqiti.user.health;

import com.waqiti.user.gdpr.client.PaymentServiceClient;
import com.waqiti.user.gdpr.client.TransactionServiceClient;
import com.waqiti.user.gdpr.client.WalletServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * External Services Health Indicator
 *
 * Checks connectivity to critical external services
 *
 * KUBERNETES CONTEXT:
 * - Used by readiness probe to ensure all dependencies are available
 * - Prevents cascading failures by blocking traffic when dependencies down
 *
 * CRITICAL SERVICES:
 * - Wallet Service (GDPR deletion required)
 * - Payment Service (GDPR deletion required)
 * - Transaction Service (GDPR deletion required)
 *
 * PRODUCTION REQUIREMENTS:
 * - Non-blocking checks with circuit breakers
 * - Fast timeout (<1s per service)
 * - Graceful degradation for non-critical services
 */
@Slf4j
@Component("externalServices")
@RequiredArgsConstructor
public class ExternalServicesHealthIndicator implements HealthIndicator {

    private final WalletServiceClient walletServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final TransactionServiceClient transactionServiceClient;

    @Override
    public Health health() {
        Map<String, ServiceHealth> serviceHealthMap = new HashMap<>();
        boolean allHealthy = true;
        int unhealthyCount = 0;

        // Check Wallet Service
        ServiceHealth walletHealth = checkServiceHealth(
            "wallet-service",
            () -> {
                // Circuit breaker will handle failures
                walletServiceClient.deleteUserData(java.util.UUID.randomUUID());
                return true;
            }
        );
        serviceHealthMap.put("wallet-service", walletHealth);
        if (!walletHealth.isHealthy()) {
            allHealthy = false;
            unhealthyCount++;
        }

        // Check Payment Service
        ServiceHealth paymentHealth = checkServiceHealth(
            "payment-service",
            () -> {
                paymentServiceClient.deleteUserData(java.util.UUID.randomUUID());
                return true;
            }
        );
        serviceHealthMap.put("payment-service", paymentHealth);
        if (!paymentHealth.isHealthy()) {
            allHealthy = false;
            unhealthyCount++;
        }

        // Check Transaction Service
        ServiceHealth transactionHealth = checkServiceHealth(
            "transaction-service",
            () -> {
                transactionServiceClient.deleteUserData(java.util.UUID.randomUUID());
                return true;
            }
        );
        serviceHealthMap.put("transaction-service", transactionHealth);
        if (!transactionHealth.isHealthy()) {
            allHealthy = false;
            unhealthyCount++;
        }

        // Build health response
        Health.Builder healthBuilder = allHealthy ? Health.up() : Health.down();

        healthBuilder
            .withDetail("totalServices", serviceHealthMap.size())
            .withDetail("healthyServices", serviceHealthMap.size() - unhealthyCount)
            .withDetail("unhealthyServices", unhealthyCount)
            .withDetail("services", serviceHealthMap);

        if (!allHealthy) {
            log.warn("HEALTH: {} external services are unhealthy", unhealthyCount);
        }

        return healthBuilder.build();
    }

    /**
     * Check individual service health
     */
    private ServiceHealth checkServiceHealth(String serviceName, HealthCheck check) {
        try {
            long startTime = System.currentTimeMillis();
            check.execute();
            long responseTime = System.currentTimeMillis() - startTime;

            return ServiceHealth.builder()
                .healthy(true)
                .serviceName(serviceName)
                .responseTime(responseTime)
                .build();

        } catch (Exception e) {
            // Circuit breaker open or service down
            String errorType = e.getClass().getSimpleName();

            log.debug("HEALTH: Service {} health check failed: {}", serviceName, e.getMessage());

            return ServiceHealth.builder()
                .healthy(false)
                .serviceName(serviceName)
                .error(errorType)
                .message(e.getMessage())
                .build();
        }
    }

    /**
     * Functional interface for health checks
     */
    @FunctionalInterface
    private interface HealthCheck {
        boolean execute() throws Exception;
    }

    /**
     * Service health status
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ServiceHealth {
        private boolean healthy;
        private String serviceName;
        private Long responseTime;
        private String error;
        private String message;
    }
}
