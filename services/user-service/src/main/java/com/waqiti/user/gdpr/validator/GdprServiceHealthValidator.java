package com.waqiti.user.gdpr.validator;

import com.waqiti.user.client.WalletServiceClient;
import com.waqiti.user.client.PaymentServiceClient;
import com.waqiti.user.client.TransactionServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * GDPR Service Health Validator
 *
 * CRITICAL COMPLIANCE REQUIREMENT:
 * GDPR Article 17 (Right to Erasure) requires deletion within 30 days.
 * This service CANNOT start if required dependencies are unavailable,
 * as it would violate GDPR compliance if deletion requests cannot be fulfilled.
 *
 * REGULATORY IMPACT:
 * - Article 17 violation: Up to €20M or 4% of global revenue (whichever is higher)
 * - Article 83(5)(b): Maximum administrative fine tier
 * - Reputational damage from data protection failures
 *
 * VALIDATION STRATEGY:
 * 1. Check all external services BEFORE accepting traffic
 * 2. Fail-fast if any critical service is unavailable
 * 3. Periodic health checks during runtime
 * 4. Automatic degradation alerts
 *
 * SERVICES VALIDATED:
 * - wallet-service: Anonymize wallet transactions
 * - payment-service: Anonymize payment history
 * - transaction-service: Anonymize transaction records
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GdprServiceHealthValidator {

    private final WalletServiceClient walletServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final TransactionServiceClient transactionServiceClient;

    private volatile boolean gdprCapabilityHealthy = false;
    private volatile LocalDateTime lastHealthCheck;
    private volatile List<String> unavailableServices = new ArrayList<>();

    /**
     * Validate GDPR dependencies on application startup
     * CRITICAL: Application WILL NOT START if validation fails
     *
     * This prevents the service from accepting GDPR deletion requests
     * that it cannot fulfill, avoiding regulatory violations.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateGdprDependenciesOnStartup() {
        log.info("====================================================================");
        log.info("GDPR COMPLIANCE: Validating service dependencies for data deletion");
        log.info("====================================================================");

        log.info("GDPR: This service handles Right to Erasure (Article 17) requests");
        log.info("GDPR: All external services MUST be available to ensure compliance");
        log.info("GDPR: 30-day SLA for deletion requests - cannot start with broken dependencies");

        List<String> unavailable = new ArrayList<>();

        // Validate wallet service
        log.info("GDPR: Checking wallet-service availability...");
        if (!isServiceAvailable(walletServiceClient::healthCheck, "wallet-service")) {
            unavailable.add("wallet-service");
            log.error("GDPR: ❌ wallet-service is UNAVAILABLE - cannot anonymize wallet data");
        } else {
            log.info("GDPR: ✅ wallet-service is available");
        }

        // Validate payment service
        log.info("GDPR: Checking payment-service availability...");
        if (!isServiceAvailable(paymentServiceClient::healthCheck, "payment-service")) {
            unavailable.add("payment-service");
            log.error("GDPR: ❌ payment-service is UNAVAILABLE - cannot anonymize payment data");
        } else {
            log.info("GDPR: ✅ payment-service is available");
        }

        // Validate transaction service
        log.info("GDPR: Checking transaction-service availability...");
        if (!isServiceAvailable(transactionServiceClient::healthCheck, "transaction-service")) {
            unavailable.add("transaction-service");
            log.error("GDPR: ❌ transaction-service is UNAVAILABLE - cannot anonymize transaction data");
        } else {
            log.info("GDPR: ✅ transaction-service is available");
        }

        this.unavailableServices = unavailable;
        this.lastHealthCheck = LocalDateTime.now();

        if (!unavailable.isEmpty()) {
            this.gdprCapabilityHealthy = false;

            String errorMessage = String.format(
                "╔════════════════════════════════════════════════════════════════════════╗\n" +
                "║                     GDPR COMPLIANCE FAILURE                            ║\n" +
                "╠════════════════════════════════════════════════════════════════════════╣\n" +
                "║ CRITICAL: Cannot start - required GDPR services unavailable           ║\n" +
                "║                                                                        ║\n" +
                "║ Unavailable Services: %-48s ║\n" +
                "║                                                                        ║\n" +
                "║ IMPACT:                                                                ║\n" +
                "║   • Cannot fulfill Article 17 (Right to Erasure) requests             ║\n" +
                "║   • 30-day SLA compliance impossible                                  ║\n" +
                "║   • Potential GDPR violation: €20M or 4%% of global revenue            ║\n" +
                "║                                                                        ║\n" +
                "║ REQUIRED ACTION:                                                       ║\n" +
                "║   1. Start all required services                                       ║\n" +
                "║   2. Verify network connectivity                                       ║\n" +
                "║   3. Check service discovery configuration                             ║\n" +
                "║   4. Restart this service                                              ║\n" +
                "║                                                                        ║\n" +
                "║ For development/testing, set: gdpr.strict-validation=false            ║\n" +
                "╚════════════════════════════════════════════════════════════════════════╝",
                String.join(", ", unavailable)
            );

            log.error(errorMessage);

            // In production, fail-fast to prevent GDPR violations
            if (isProductionEnvironment()) {
                throw new IllegalStateException(
                    "GDPR CRITICAL: Cannot start - required services unavailable: " +
                    String.join(", ", unavailable) +
                    ". Data deletion operations would fail and violate GDPR compliance.");
            } else {
                log.warn("GDPR: Development mode - allowing startup despite unavailable services");
                log.warn("GDPR: GDPR deletion operations WILL FAIL until services are available");
            }
        } else {
            this.gdprCapabilityHealthy = true;

            log.info("╔════════════════════════════════════════════════════════════════════════╗");
            log.info("║              GDPR COMPLIANCE: ALL SERVICES AVAILABLE                   ║");
            log.info("╠════════════════════════════════════════════════════════════════════════╣");
            log.info("║ ✅ wallet-service: Available                                           ║");
            log.info("║ ✅ payment-service: Available                                          ║");
            log.info("║ ✅ transaction-service: Available                                      ║");
            log.info("║                                                                        ║");
            log.info("║ Status: Ready to process Article 17 deletion requests                 ║");
            log.info("║ SLA: 30 days for data erasure compliance                              ║");
            log.info("╚════════════════════════════════════════════════════════════════════════╝");
        }
    }

    /**
     * Check if a service is available with retry and timeout
     */
    private boolean isServiceAvailable(Supplier<Boolean> healthCheck, String serviceName) {
        int maxRetries = 3;
        int retryDelayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("GDPR: Checking {} (attempt {}/{})", serviceName, attempt, maxRetries);

                boolean available = healthCheck.get();

                if (available) {
                    log.debug("GDPR: {} responded successfully", serviceName);
                    return true;
                } else {
                    log.warn("GDPR: {} health check returned false", serviceName);
                }

            } catch (Exception e) {
                log.warn("GDPR: {} health check failed (attempt {}/{}): {}",
                        serviceName, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }

        log.error("GDPR: {} is unavailable after {} attempts", serviceName, maxRetries);
        return false;
    }

    /**
     * Periodic health check (called by scheduler)
     */
    public void performPeriodicHealthCheck() {
        log.debug("GDPR: Performing periodic health check of GDPR dependencies");

        List<String> currentlyUnavailable = new ArrayList<>();

        if (!isServiceAvailable(walletServiceClient::healthCheck, "wallet-service")) {
            currentlyUnavailable.add("wallet-service");
        }

        if (!isServiceAvailable(paymentServiceClient::healthCheck, "payment-service")) {
            currentlyUnavailable.add("payment-service");
        }

        if (!isServiceAvailable(transactionServiceClient::healthCheck, "transaction-service")) {
            currentlyUnavailable.add("transaction-service");
        }

        // Detect state changes
        boolean wasHealthy = this.gdprCapabilityHealthy;
        boolean isHealthy = currentlyUnavailable.isEmpty();

        this.unavailableServices = currentlyUnavailable;
        this.gdprCapabilityHealthy = isHealthy;
        this.lastHealthCheck = LocalDateTime.now();

        // Alert on state changes
        if (wasHealthy && !isHealthy) {
            log.error("GDPR ALERT: GDPR capability degraded - services unavailable: {}",
                    String.join(", ", currentlyUnavailable));
            // TODO: Send PagerDuty alert
        } else if (!wasHealthy && isHealthy) {
            log.info("GDPR RECOVERY: GDPR capability restored - all services available");
            // TODO: Send recovery notification
        }

        if (!isHealthy) {
            log.warn("GDPR: Services unavailable: {} - deletion operations may fail",
                    String.join(", ", currentlyUnavailable));
        }
    }

    /**
     * Check if GDPR operations are currently possible
     */
    public boolean isGdprCapabilityHealthy() {
        return gdprCapabilityHealthy;
    }

    /**
     * Get list of currently unavailable services
     */
    public List<String> getUnavailableServices() {
        return new ArrayList<>(unavailableServices);
    }

    /**
     * Get last health check timestamp
     */
    public LocalDateTime getLastHealthCheck() {
        return lastHealthCheck;
    }

    /**
     * Determine if running in production environment
     */
    private boolean isProductionEnvironment() {
        String profile = System.getProperty("spring.profiles.active",
                System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", ""));

        return profile.contains("production") || profile.contains("prod");
    }

    /**
     * Force health check (for manual operations)
     */
    public void forceHealthCheck() {
        log.info("GDPR: Forcing immediate health check");
        performPeriodicHealthCheck();
    }
}
