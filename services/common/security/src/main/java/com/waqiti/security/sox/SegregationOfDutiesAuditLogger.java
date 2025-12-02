package com.waqiti.security.sox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Logger for Segregation of Duties Events
 *
 * CRITICAL COMPLIANCE:
 * - All SoD violations must be logged
 * - Audit trail required for SOX compliance
 * - Real-time alerts for critical violations
 *
 * LOGGING DESTINATIONS:
 * 1. Application logs (Elasticsearch/Splunk)
 * 2. Database (sod_violations table)
 * 3. Kafka (compliance-events topic)
 * 4. Prometheus metrics
 * 5. PagerDuty/Email alerts for critical violations
 *
 * @author Waqiti Compliance Team
 * @version 3.0.0
 */
@Component
@Slf4j
public class SegregationOfDutiesAuditLogger {

    private final SegregationOfDutiesRepository sodRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter sodViolationCounter;
    private final Counter dualAuthCounter;
    private final Counter makerCheckerCounter;

    public SegregationOfDutiesAuditLogger(
            SegregationOfDutiesRepository sodRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {

        this.sodRepository = sodRepository;
        this.kafkaTemplate = kafkaTemplate;

        // Initialize Prometheus metrics
        this.sodViolationCounter = Counter.builder("sod.violations.total")
            .description("Total number of SoD violations detected")
            .tag("service", "security")
            .register(meterRegistry);

        this.dualAuthCounter = Counter.builder("sod.dual_auth.total")
            .description("Total number of dual authorization events")
            .tag("service", "security")
            .register(meterRegistry);

        this.makerCheckerCounter = Counter.builder("sod.maker_checker.total")
            .description("Total number of maker-checker events")
            .tag("service", "security")
            .register(meterRegistry);
    }

    /**
     * Log a Segregation of Duties violation
     *
     * CRITICAL: This method is called for ALL SoD violations
     * - Logs to application log
     * - Records in database
     * - Publishes to Kafka
     * - Increments Prometheus counter
     * - Triggers alerts for critical violations
     */
    public void logSoDViolation(UUID userId, String violationType, String action1,
                                String action2, String description) {

        // 1. Application log (structured logging)
        log.error("SOX_SOD_VIOLATION: User={}, Type={}, Action1={}, Action2={}, Description={}, Timestamp={}",
            userId, violationType, action1, action2, description, Instant.now());

        // 2. Database record
        sodRepository.recordSoDViolation(userId, violationType, action1, action2, description);

        // 3. Kafka event
        publishSoDViolationEvent(userId, violationType, action1, action2, description);

        // 4. Prometheus counter
        sodViolationCounter.increment();

        // 5. Alert for critical violations
        if (isCriticalViolation(violationType)) {
            sendCriticalViolationAlert(userId, violationType, description);
        }
    }

    /**
     * Log successful dual authorization
     */
    public void logDualAuthorization(UUID transactionId, UUID initiatorId,
                                     UUID approverId, String action) {

        log.info("SOX_DUAL_AUTH: Transaction={}, Initiator={}, Approver={}, Action={}, Timestamp={}",
            transactionId, initiatorId, approverId, action, Instant.now());

        // Publish Kafka event
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "DUAL_AUTHORIZATION");
        event.put("transactionId", transactionId.toString());
        event.put("initiatorId", initiatorId.toString());
        event.put("approverId", approverId.toString());
        event.put("action", action);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("severity", "INFO");

        kafkaTemplate.send("compliance-events", transactionId.toString(), event);

        // Increment counter
        dualAuthCounter.increment();
    }

    /**
     * Log maker-checker event
     */
    public void logMakerChecker(UUID transactionId, UUID makerId, UUID checkerId) {
        log.info("SOX_MAKER_CHECKER: Transaction={}, Maker={}, Checker={}, Timestamp={}",
            transactionId, makerId, checkerId, Instant.now());

        makerCheckerCounter.increment();
    }

    /**
     * Log role assignment validation
     */
    public void logRoleAssignmentValidation(UUID userId, String role, boolean approved) {
        if (approved) {
            log.info("SOX_ROLE_ASSIGNMENT_APPROVED: User={}, Role={}, Timestamp={}",
                userId, role, Instant.now());
        } else {
            log.warn("SOX_ROLE_ASSIGNMENT_REJECTED: User={}, Role={}, Timestamp={}",
                userId, role, Instant.now());
        }
    }

    /**
     * Log transaction action validation
     */
    public void logActionValidation(UUID userId, UUID transactionId, String action,
                                    boolean approved) {
        if (approved) {
            log.debug("SOX_ACTION_APPROVED: User={}, Transaction={}, Action={}, Timestamp={}",
                userId, transactionId, action, Instant.now());
        } else {
            log.warn("SOX_ACTION_REJECTED: User={}, Transaction={}, Action={}, Timestamp={}",
                userId, transactionId, action, Instant.now());
        }
    }

    /**
     * Publish SoD violation event to Kafka
     */
    private void publishSoDViolationEvent(UUID userId, String violationType, String action1,
                                          String action2, String description) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "SOD_VIOLATION");
            event.put("userId", userId != null ? userId.toString() : null);
            event.put("violationType", violationType);
            event.put("action1", action1);
            event.put("action2", action2);
            event.put("description", description);
            event.put("timestamp", LocalDateTime.now().toString());
            event.put("severity", "CRITICAL");

            kafkaTemplate.send("compliance-events",
                userId != null ? userId.toString() : "SYSTEM",
                event);

            log.debug("Published SoD violation event to Kafka");

        } catch (Exception e) {
            log.error("Failed to publish SoD violation event to Kafka", e);
            // Don't fail the operation if Kafka is down
        }
    }

    /**
     * Determine if violation is critical (requires immediate attention)
     */
    private boolean isCriticalViolation(String violationType) {
        return violationType.equals("TRANSACTION_ACTION") ||
               violationType.equals("DUAL_AUTHORIZATION") ||
               violationType.equals("MAKER_CHECKER");
    }

    /**
     * Send alert for critical SoD violations
     */
    private void sendCriticalViolationAlert(UUID userId, String violationType, String description) {
        try {
            // Publish high-priority alert event
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "SOD_CRITICAL_VIOLATION");
            alert.put("userId", userId != null ? userId.toString() : null);
            alert.put("violationType", violationType);
            alert.put("description", description);
            alert.put("timestamp", LocalDateTime.now().toString());
            alert.put("priority", "CRITICAL");
            alert.put("requiresAction", true);

            kafkaTemplate.send("compliance-alerts",
                userId != null ? userId.toString() : "SYSTEM",
                alert);

            log.error("CRITICAL SOD VIOLATION ALERT SENT: Type={}, User={}", violationType, userId);

        } catch (Exception e) {
            log.error("Failed to send critical SoD violation alert", e);
        }
    }

    /**
     * Log SoD validation summary (for reporting)
     */
    public void logValidationSummary(int totalValidations, int violations, int approvals) {
        log.info("SOX_VALIDATION_SUMMARY: Total={}, Violations={}, Approvals={}, Timestamp={}",
            totalValidations, violations, approvals, Instant.now());

        // Publish summary to Kafka
        Map<String, Object> summary = new HashMap<>();
        summary.put("eventType", "SOD_VALIDATION_SUMMARY");
        summary.put("totalValidations", totalValidations);
        summary.put("violations", violations);
        summary.put("approvals", approvals);
        summary.put("violationRate", totalValidations > 0 ?
            (double) violations / totalValidations * 100 : 0);
        summary.put("timestamp", LocalDateTime.now().toString());

        kafkaTemplate.send("compliance-events", "SYSTEM", summary);
    }
}
