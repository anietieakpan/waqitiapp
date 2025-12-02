package com.waqiti.payment.kafka;

import com.waqiti.common.eventsourcing.FraudDetectedEvent;
import com.waqiti.common.kafka.dlq.DlqProcessingResult;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.service.AlertingService;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.service.PaymentBlockingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * P0 CRITICAL FIX: DLQ Handler for FraudDetectedEvent
 *
 * This handler implements comprehensive recovery logic for failed fraud event processing.
 *
 * Recovery Strategy:
 * 1. Analyze failure reason (duplicate, blocking error, notification error)
 * 2. Attempt automatic recovery for transient errors
 * 3. Route to manual review for complex issues
 * 4. CRITICAL: Always ensure fraud is blocked even if notifications fail
 *
 * Safety Principle:
 * - Better to block legitimate transaction than allow fraudulent one
 * - All DLQ fraud events are escalated to fraud ops team
 *
 * @author Waqiti Engineering Team
 * @since 2025-10-25
 */
@Slf4j
@Component
public class FraudDetectedEventConsumerDlqHandler extends UniversalDLQHandler<FraudDetectedEvent> {

    private static final String DLQ_TOPIC = "fraud-detected-events.DLT";
    private static final String DLQ_GROUP = "payment-service-fraud-detected-dlq";

    private final PaymentBlockingService paymentBlockingService;
    private final AuditService auditService;
    private final AlertingService alertingService;

    public FraudDetectedEventConsumerDlqHandler(
            PaymentBlockingService paymentBlockingService,
            AuditService auditService,
            AlertingService alertingService) {
        super(FraudDetectedEvent.class);
        this.paymentBlockingService = paymentBlockingService;
        this.auditService = auditService;
        this.alertingService = alertingService;
    }

    @KafkaListener(
        topics = DLQ_TOPIC,
        groupId = DLQ_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlqMessage(
            FraudDetectedEvent event,
            Acknowledgment acknowledgment,
            Map<String, Object> headers) {

        log.error("⚠️ DLQ PROCESSING: FraudDetectedEvent - fraudId={}, transactionId={}, riskScore={}",
                event.getFraudId(), event.getTransactionId(), event.getRiskScore());

        DlqProcessingResult result = processDlqEvent(event, headers);

        auditService.logDlqProcessing(
                "FraudDetectedEvent",
                event.getFraudId(),
                result.getStatus().toString(),
                result.getMessage(),
                LocalDateTime.now()
        );

        acknowledgment.acknowledge();
    }

    /**
     * Implements custom recovery logic for failed fraud event processing.
     *
     * Recovery Rules:
     * 1. DUPLICATE_ERROR → Skip (already processed)
     * 2. NOTIFICATION_FAILURE → Block payment anyway, manual notification
     * 3. DATABASE_ERROR → Retry blocking
     * 4. VALIDATION_ERROR → Manual review
     * 5. UNKNOWN_ERROR → Block payment defensively, manual review
     */
    @Override
    protected DlqProcessingResult handleDlqEvent(FraudDetectedEvent event, Map<String, Object> headers) {
        String fraudId = event.getFraudId();
        String transactionId = event.getTransactionId();
        String errorReason = extractErrorReason(headers);

        log.info("🔍 DLQ FRAUD RECOVERY: fraudId={}, transactionId={}, reason={}",
                fraudId, transactionId, errorReason);

        try {
            // Check if payment is already blocked
            if (paymentBlockingService.isPaymentBlocked(transactionId)) {
                log.info("✅ DLQ RECOVERY: Payment {} already blocked - marking as recovered", transactionId);
                return DlqProcessingResult.recovered("Payment already blocked");
            }

            // Analyze error and attempt recovery
            if (errorReason != null) {
                if (errorReason.contains("duplicate") || errorReason.contains("already exists")) {
                    return DlqProcessingResult.recovered("Duplicate fraud event, already processed");
                }

                if (errorReason.contains("notification") || errorReason.contains("email") ||
                    errorReason.contains("sms")) {
                    // CRITICAL: Block payment even if notification failed
                    blockPaymentDefensively(event);
                    alertFraudOpsTeamManually(event, "Notification failure - payment blocked but customer not notified");
                    return DlqProcessingResult.recovered("Payment blocked despite notification failure");
                }

                if (errorReason.contains("timeout") || errorReason.contains("connection") ||
                    errorReason.contains("database")) {
                    // Attempt retry for transient errors
                    boolean retried = attemptFraudBlockingRetry(event);
                    if (retried) {
                        return DlqProcessingResult.recovered("Successfully blocked payment on retry");
                    } else {
                        return DlqProcessingResult.manualReview("Retry failed - database connectivity issues");
                    }
                }
            }

            // For unknown errors, ALWAYS block defensively
            log.error("⚠️ DEFENSIVE BLOCKING: Unknown error for fraud event {}, blocking payment {}",
                    fraudId, transactionId);
            blockPaymentDefensively(event);
            alertFraudOpsTeamManually(event, "DLQ recovery - defensive block applied");

            return DlqProcessingResult.manualReview(
                "Payment defensively blocked - requires manual fraud review");

        } catch (Exception e) {
            log.error("❌ DLQ RECOVERY FAILED: fraudId={}, transactionId={}, error={}",
                    fraudId, transactionId, e.getMessage(), e);

            // CRITICAL: Even if recovery fails, try to block payment
            try {
                blockPaymentDefensively(event);
                alertFraudOpsTeamManually(event, "DLQ recovery exception - emergency defensive block");
            } catch (Exception blockingException) {
                log.error("🚨 CRITICAL: Failed to defensively block fraudulent payment {} - MANUAL INTERVENTION REQUIRED",
                        transactionId, blockingException);
            }

            return DlqProcessingResult.failed("Recovery threw exception: " + e.getMessage());
        }
    }

    /**
     * Attempts to retry fraud blocking.
     */
    private boolean attemptFraudBlockingRetry(FraudDetectedEvent event) {
        try {
            log.info("🔄 RETRY ATTEMPT: Blocking payment {} due to fraud {}", event.getTransactionId(), event.getFraudId());

            blockPaymentDefensively(event);

            log.info("✅ RETRY SUCCESS: Payment {} blocked from DLQ", event.getTransactionId());

            auditService.logDlqRecoverySuccess(
                    event.getFraudId(),
                    "Successful fraud blocking retry from DLQ",
                    LocalDateTime.now()
            );

            return true;

        } catch (Exception e) {
            log.error("❌ RETRY FAILED: fraudId={}, transactionId={}, error={}",
                    event.getFraudId(), event.getTransactionId(), e.getMessage());
            return false;
        }
    }

    /**
     * Defensively block payment - err on side of caution.
     *
     * This method uses simplified blocking logic to ensure the payment
     * is blocked even if full processing fails.
     */
    private void blockPaymentDefensively(FraudDetectedEvent event) {
        try {
            paymentBlockingService.emergencyBlock(
                event.getTransactionId(),
                event.getUserId(),
                "FRAUD_DETECTED_DLQ_RECOVERY",
                event.getFraudType(),
                event.getRiskScore()
            );

            log.warn("🛡️ DEFENSIVE BLOCK: Payment {} blocked due to fraud {} (DLQ recovery)",
                    event.getTransactionId(), event.getFraudId());

        } catch (Exception e) {
            log.error("🚨 CRITICAL: Defensive blocking failed for payment {} - URGENT MANUAL INTERVENTION REQUIRED",
                    event.getTransactionId(), e);
            throw e; // Re-throw to trigger further alerting
        }
    }

    /**
     * ✅ PRODUCTION IMPLEMENTATION: Alerts fraud operations team
     * FIXED: November 18, 2025 - Integrated with AlertingService for multi-channel alerts
     */
    private void alertFraudOpsTeamManually(FraudDetectedEvent event, String reason) {
        log.error("🚨 FRAUD OPS ALERT: {} - fraudId={}, transactionId={}, riskScore={}",
                reason, event.getFraudId(), event.getTransactionId(), event.getRiskScore());

        try {
            // Use AlertingService for multi-channel alerting
            String title = String.format("🚨 FRAUD DETECTED - DLQ Recovery Required");
            String message = String.format(
                    "Fraud Detection: %s\n" +
                    "Fraud ID: %s\n" +
                    "Transaction ID: %s\n" +
                    "Risk Score: %s\n" +
                    "Reason: %s\n" +
                    "User ID: %s\n" +
                    "\n" +
                    "ACTION REQUIRED: Manual review needed due to DLQ processing failure",
                    event.getFraudType(),
                    event.getFraudId(),
                    event.getTransactionId(),
                    event.getRiskScore(),
                    reason,
                    event.getUserId()
            );

            // Send alerts through all channels
            alertingService.sendCriticalAlert(title, message);

            // Create PagerDuty incident for high-risk fraud
            if (event.getRiskScore().doubleValue() >= 80.0) {
                alertingService.createPagerDutyIncident("P1",
                        String.format("High-Risk Fraud DLQ: %s (Score: %s)",
                                event.getTransactionId(), event.getRiskScore()));
            }

            log.info("✅ Fraud ops team alerted via AlertingService");

        } catch (Exception e) {
            log.error("Failed to alert fraud ops team: {}", e.getMessage(), e);
        }

        // Also create audit record for compliance
        auditService.logFraudOpsAlert(
                event.getFraudId(),
                event.getTransactionId(),
                event.getRiskScore().toString(),
                reason,
                LocalDateTime.now()
        );
    }

    /**
     * Extracts error reason from Kafka headers.
     */
    private String extractErrorReason(Map<String, Object> headers) {
        if (headers == null) {
            return null;
        }

        Object exception = headers.get("kafka_dlt-exception-message");
        if (exception != null) {
            return exception.toString();
        }

        Object stacktrace = headers.get("kafka_dlt-exception-stacktrace");
        if (stacktrace != null) {
            String trace = stacktrace.toString();
            int newlineIndex = trace.indexOf('\n');
            return newlineIndex > 0 ? trace.substring(0, newlineIndex) : trace;
        }

        return "Unknown error";
    }
}
