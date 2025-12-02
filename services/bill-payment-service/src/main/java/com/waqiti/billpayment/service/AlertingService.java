package com.waqiti.billpayment.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for alerting on critical failures in bill payment operations
 * Provides monitoring, logging, and notification for production issues
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertingService {

    private final MeterRegistry meterRegistry;

    // Metrics counters
    private Counter compensationFailureCounter;
    private Counter auditFailureCounter;
    private Counter walletServiceFailureCounter;
    private Counter billerIntegrationFailureCounter;
    private Counter criticalErrorCounter;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        compensationFailureCounter = Counter.builder("bill.payment.compensation.failure")
                .description("Number of compensation (refund) failures")
                .tag("severity", "critical")
                .register(meterRegistry);

        auditFailureCounter = Counter.builder("bill.payment.audit.failure")
                .description("Number of audit log failures")
                .tag("severity", "high")
                .register(meterRegistry);

        walletServiceFailureCounter = Counter.builder("bill.payment.wallet.service.failure")
                .description("Number of wallet service failures")
                .tag("severity", "high")
                .register(meterRegistry);

        billerIntegrationFailureCounter = Counter.builder("bill.payment.biller.integration.failure")
                .description("Number of biller integration failures")
                .tag("severity", "medium")
                .register(meterRegistry);

        criticalErrorCounter = Counter.builder("bill.payment.critical.error")
                .description("Number of critical errors")
                .tag("severity", "critical")
                .register(meterRegistry);
    }

    /**
     * Alert on compensation failure - CRITICAL
     * This means money was debited but cannot be refunded
     * Requires immediate manual intervention
     */
    public void alertCompensationFailure(UUID paymentId, String userId, String errorMessage, Exception exception) {
        compensationFailureCounter.increment();
        criticalErrorCounter.increment();

        Map<String, Object> context = new HashMap<>();
        context.put("paymentId", paymentId);
        context.put("userId", userId);
        context.put("errorMessage", errorMessage);
        context.put("timestamp", LocalDateTime.now());

        // Log as ERROR with structured data
        log.error("CRITICAL ALERT: Compensation failure for payment: {}. User: {}. Error: {}. " +
                  "MANUAL INTERVENTION REQUIRED - Money debited but refund failed!",
                  paymentId, userId, errorMessage, exception);

        // In production, this would:
        // 1. Send PagerDuty/Opsgenie alert
        // 2. Send Slack notification to #critical-alerts channel
        // 3. Create Jira ticket for finance team
        // 4. Send email to on-call engineer
        // 5. Trigger incident response workflow

        sendCriticalAlert("COMPENSATION_FAILURE", "Compensation failure - refund failed", context);
    }

    /**
     * Alert on audit log failure - HIGH
     * Compliance and audit requirements may be violated
     */
    public void alertAuditFailure(String entityType, UUID entityId, String action, String errorMessage) {
        auditFailureCounter.increment();

        Map<String, Object> context = new HashMap<>();
        context.put("entityType", entityType);
        context.put("entityId", entityId);
        context.put("action", action);
        context.put("errorMessage", errorMessage);
        context.put("timestamp", LocalDateTime.now());

        log.error("HIGH ALERT: Audit log failure. EntityType: {}, EntityId: {}, Action: {}, Error: {}",
                  entityType, entityId, action, errorMessage);

        // In production, this would:
        // 1. Send notification to compliance team
        // 2. Log to separate compliance audit system
        // 3. Create monitoring alert if failure rate exceeds threshold

        sendHighSeverityAlert("AUDIT_FAILURE", "Audit logging failed", context);
    }

    /**
     * Alert on wallet service failure - HIGH
     * Payment processing is blocked
     */
    public void alertWalletServiceFailure(String operation, String errorMessage, Exception exception) {
        walletServiceFailureCounter.increment();

        Map<String, Object> context = new HashMap<>();
        context.put("operation", operation);
        context.put("errorMessage", errorMessage);
        context.put("timestamp", LocalDateTime.now());

        log.error("HIGH ALERT: Wallet service failure. Operation: {}, Error: {}",
                  operation, errorMessage, exception);

        // In production, this would:
        // 1. Check if circuit breaker is open
        // 2. Send alert if failure rate exceeds threshold
        // 3. Notify wallet service team

        sendHighSeverityAlert("WALLET_SERVICE_FAILURE", "Wallet service unavailable", context);
    }

    /**
     * Alert on biller integration failure - MEDIUM
     * Bill payment to specific biller is failing
     */
    public void alertBillerIntegrationFailure(String billerName, String errorMessage, Exception exception) {
        billerIntegrationFailureCounter.increment();

        Map<String, Object> context = new HashMap<>();
        context.put("billerName", billerName);
        context.put("errorMessage", errorMessage);
        context.put("timestamp", LocalDateTime.now());

        log.warn("MEDIUM ALERT: Biller integration failure. Biller: {}, Error: {}",
                 billerName, errorMessage, exception);

        // In production, this would:
        // 1. Track failure count per biller
        // 2. Alert if specific biller has high failure rate
        // 3. Notify biller integration team

        sendMediumSeverityAlert("BILLER_INTEGRATION_FAILURE",
                               "Biller integration failed for " + billerName, context);
    }

    /**
     * Alert on data inconsistency - CRITICAL
     * Database state may be inconsistent
     */
    public void alertDataInconsistency(String description, Map<String, Object> details) {
        criticalErrorCounter.increment();

        Map<String, Object> context = new HashMap<>(details);
        context.put("description", description);
        context.put("timestamp", LocalDateTime.now());

        log.error("CRITICAL ALERT: Data inconsistency detected. Description: {}, Details: {}",
                  description, details);

        sendCriticalAlert("DATA_INCONSISTENCY", description, context);
    }

    /**
     * Alert on concurrent modification detected
     * Optimistic locking failure or race condition
     */
    public void alertConcurrentModification(String entityType, UUID entityId, String operation) {
        Map<String, Object> context = new HashMap<>();
        context.put("entityType", entityType);
        context.put("entityId", entityId);
        context.put("operation", operation);
        context.put("timestamp", LocalDateTime.now());

        log.warn("ALERT: Concurrent modification detected. EntityType: {}, EntityId: {}, Operation: {}",
                 entityType, entityId, operation);

        // This is expected occasionally, but high frequency indicates a problem
        // Monitor and alert if rate exceeds threshold
    }

    /**
     * Send critical severity alert
     * Requires immediate action
     */
    private void sendCriticalAlert(String alertType, String message, Map<String, Object> context) {
        // In production implementation:
        // - Send to PagerDuty/Opsgenie with P1 severity
        // - Send to Slack #critical-alerts with @here mention
        // - Send email to on-call engineer
        // - Create high-priority Jira ticket
        // - Trigger incident response runbook

        log.error("CRITICAL ALERT [{}]: {} - Context: {}", alertType, message, context);

        // TODO: Integrate with actual alerting system (PagerDuty, Opsgenie, etc.)
        // Example:
        // pagerDutyService.triggerIncident(alertType, message, Severity.CRITICAL, context);
        // slackService.sendMessage("#critical-alerts", "@here CRITICAL: " + message);
    }

    /**
     * Send high severity alert
     * Requires action within 1 hour
     */
    private void sendHighSeverityAlert(String alertType, String message, Map<String, Object> context) {
        // In production implementation:
        // - Send to monitoring dashboard
        // - Send to Slack #alerts channel
        // - Create normal-priority Jira ticket if persistent

        log.error("HIGH ALERT [{}]: {} - Context: {}", alertType, message, context);

        // TODO: Integrate with monitoring/alerting system
    }

    /**
     * Send medium severity alert
     * Should be investigated but not urgent
     */
    private void sendMediumSeverityAlert(String alertType, String message, Map<String, Object> context) {
        // In production implementation:
        // - Log to monitoring system
        // - Send to Slack #monitoring channel
        // - Track trends

        log.warn("MEDIUM ALERT [{}]: {} - Context: {}", alertType, message, context);

        // TODO: Integrate with monitoring system
    }

    /**
     * Get metrics snapshot for health checks
     */
    public Map<String, Object> getMetricsSnapshot() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("compensationFailures", compensationFailureCounter.count());
        metrics.put("auditFailures", auditFailureCounter.count());
        metrics.put("walletServiceFailures", walletServiceFailureCounter.count());
        metrics.put("billerIntegrationFailures", billerIntegrationFailureCounter.count());
        metrics.put("criticalErrors", criticalErrorCounter.count());
        metrics.put("timestamp", LocalDateTime.now());
        return metrics;
    }
}
