package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.payment.event.PaymentEvent;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.payment.service.GatewayService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Map;

/**
 * DLQ Handler for PaymentProcessingConsumer
 *
 * Production-grade recovery logic for failed payment processing events
 *
 * Recovery Strategies:
 * 1. Transient Errors (network, timeout) -> RETRY with backoff
 * 2. Gateway Failures -> Switch to backup gateway and RETRY
 * 3. Data Validation Errors -> Manual intervention with detailed logging
 * 4. Insufficient Funds -> Notify user, mark for later retry
 * 5. Fraud Detection Failures -> Escalate to security team
 * 6. Settlement Errors -> Compensate and reconcile
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Service
@Slf4j
public class PaymentProcessingConsumerDlqHandler extends BaseDlqConsumer<Object> {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private RefundService refundService;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public PaymentProcessingConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentProcessingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PaymentProcessingConsumer.dlq:PaymentProcessingConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            // Extract failure reason
            String failureReason = (String) headers.getOrDefault("kafka_exception-message", "Unknown");
            int failureCount = (int) headers.getOrDefault("kafka_dlt-original-offset", 0);

            log.warn("Processing DLQ event: failureReason={} failureCount={}", failureReason, failureCount);

            // Convert event to PaymentEvent if possible
            PaymentEvent paymentEvent = convertToPaymentEvent(event);

            if (paymentEvent == null) {
                log.error("Unable to parse payment event from DLQ");
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Categorize failure and apply recovery strategy
            if (isTransientError(failureReason)) {
                return handleTransientError(paymentEvent, failureReason, failureCount);
            }

            if (isGatewayError(failureReason)) {
                return handleGatewayError(paymentEvent, failureReason);
            }

            if (isInsufficientFundsError(failureReason)) {
                return handleInsufficientFunds(paymentEvent);
            }

            if (isDataValidationError(failureReason)) {
                return handleDataValidationError(paymentEvent, failureReason);
            }

            if (isFraudDetectionError(failureReason)) {
                return handleFraudDetectionError(paymentEvent);
            }

            if (isSettlementError(failureReason)) {
                return handleSettlementError(paymentEvent);
            }

            // Unknown error - requires manual intervention
            log.error("Unknown DLQ error type: {} for payment: {}", failureReason, paymentEvent.getPaymentId());
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Handle transient errors (network, timeout, temporary service unavailability)
     */
    private DlqProcessingResult handleTransientError(PaymentEvent event, String reason, int failureCount) {
        if (failureCount >= 5) {
            log.error("Max retry attempts exceeded for transient error: paymentId={}", event.getPaymentId());

            // Compensate payment
            if (event.getPaymentId() != null) {
                paymentService.markAsFailed(event.getPaymentId(), "MAX_RETRIES_EXCEEDED: " + reason);
                initiateRefundIfPaid(event);
            }

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        log.info("Scheduling retry for transient error: paymentId={} attempt={}", event.getPaymentId(), failureCount + 1);
        return DlqProcessingResult.RETRY;
    }

    /**
     * Handle gateway errors (switch to backup and retry)
     */
    private DlqProcessingResult handleGatewayError(PaymentEvent event, String reason) {
        try {
            String currentGateway = event.getGateway();
            String backupGateway = gatewayService.getBackupGateway(currentGateway);

            if (backupGateway != null) {
                log.info("Switching payment to backup gateway: paymentId={} from={} to={}",
                    event.getPaymentId(), currentGateway, backupGateway);

                // Update payment to use backup gateway
                paymentService.updateGateway(event.getPaymentId(), backupGateway);

                // Retry payment on backup gateway
                kafkaTemplate.send("payment-tracking", event);

                return DlqProcessingResult.SUCCESS;
            } else {
                log.error("No backup gateway available: paymentId={}", event.getPaymentId());
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Failed to switch gateway: paymentId={}", event.getPaymentId(), e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle insufficient funds errors
     */
    private DlqProcessingResult handleInsufficientFunds(PaymentEvent event) {
        try {
            log.info("Handling insufficient funds: paymentId={}", event.getPaymentId());

            // Mark payment as failed
            paymentService.markAsFailed(event.getPaymentId(), "INSUFFICIENT_FUNDS");

            // Send notification to user
            paymentService.sendAlertNotification(
                "INSUFFICIENT_FUNDS",
                event.getPaymentId(),
                String.format("Payment of %s %s failed due to insufficient funds",
                    event.getAmount(), event.getCurrency())
            );

            // Schedule payment for retry when funds are available
            if (event.isAutoReschedule()) {
                paymentService.scheduleForRetry(event.getPaymentId(), 24); // Retry in 24 hours
            }

            return DlqProcessingResult.SUCCESS;
        } catch (Exception e) {
            log.error("Failed to handle insufficient funds: paymentId={}", event.getPaymentId(), e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle data validation errors
     */
    private DlqProcessingResult handleDataValidationError(PaymentEvent event, String reason) {
        log.error("Data validation error for payment: paymentId={} reason={}", event.getPaymentId(), reason);

        // Mark payment as permanently failed
        if (event.getPaymentId() != null) {
            paymentService.markAsFailed(event.getPaymentId(), "VALIDATION_ERROR: " + reason);
        }

        // Create detailed audit log
        logValidationFailure(event, reason);

        // Permanent failure - cannot be recovered
        return DlqProcessingResult.PERMANENT_FAILURE;
    }

    /**
     * Handle fraud detection errors
     */
    private DlqProcessingResult handleFraudDetectionError(PaymentEvent event) {
        log.warn("Fraud detection error for payment: paymentId={}", event.getPaymentId());

        try {
            // Mark payment as suspicious
            paymentService.flagSuspiciousPayment(event);

            // Freeze funds if already captured
            if (event.getPaymentId() != null && "CAPTURED".equals(event.getStatus())) {
                paymentService.freezeFunds(event.getPaymentId(), "FRAUD_DETECTION_FAILURE");
            }

            // Escalate to security team
            kafkaTemplate.send("fraud-escalation-queue", event);

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        } catch (Exception e) {
            log.error("Failed to handle fraud detection error: paymentId={}", event.getPaymentId(), e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle settlement errors
     */
    private DlqProcessingResult handleSettlementError(PaymentEvent event) {
        try {
            log.warn("Settlement error for payment: paymentId={} settlementId={}",
                event.getPaymentId(), event.getSettlementId());

            // Check if settlement can be retried
            boolean canRetry = settlementService.canRetrySettlement(event.getSettlementId());

            if (canRetry) {
                // Retry settlement
                settlementService.retrySettlement(event.getSettlementId());
                return DlqProcessingResult.SUCCESS;
            } else {
                // Initiate reconciliation process
                settlementService.initiateReconciliation(
                    event.getSettlementId(),
                    event.getMerchantId(),
                    event.getSettledAmount()
                );

                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Failed to handle settlement error: settlementId={}", event.getSettlementId(), e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    // ========== Helper Methods ==========

    private PaymentEvent convertToPaymentEvent(Object event) {
        try {
            if (event instanceof PaymentEvent) {
                return (PaymentEvent) event;
            }
            return objectMapper.convertValue(event, PaymentEvent.class);
        } catch (Exception e) {
            log.error("Failed to convert event to PaymentEvent", e);
            return null;
        }
    }

    private boolean isTransientError(String reason) {
        return reason != null && (
            reason.contains("timeout") ||
            reason.contains("connection") ||
            reason.contains("network") ||
            reason.contains("unavailable") ||
            reason.contains("SocketTimeoutException") ||
            reason.contains("ConnectException")
        );
    }

    private boolean isGatewayError(String reason) {
        return reason != null && (
            reason.contains("gateway") ||
            reason.contains("provider") ||
            reason.contains("payment service") ||
            reason.contains("503") ||
            reason.contains("502")
        );
    }

    private boolean isInsufficientFundsError(String reason) {
        return reason != null && (
            reason.contains("insufficient") ||
            reason.contains("balance") ||
            reason.contains("NSF") ||
            reason.contains("NOT_SUFFICIENT_FUNDS")
        );
    }

    private boolean isDataValidationError(String reason) {
        return reason != null && (
            reason.contains("validation") ||
            reason.contains("invalid") ||
            reason.contains("malformed") ||
            reason.contains("constraint") ||
            reason.contains("parse")
        );
    }

    private boolean isFraudDetectionError(String reason) {
        return reason != null && (
            reason.contains("fraud") ||
            reason.contains("suspicious") ||
            reason.contains("risk") ||
            reason.contains("security")
        );
    }

    private boolean isSettlementError(String reason) {
        return reason != null && (
            reason.contains("settlement") ||
            reason.contains("reconciliation") ||
            reason.contains("clearing")
        );
    }

    private void initiateRefundIfPaid(PaymentEvent event) {
        try {
            if (event.getPaymentId() != null && "CAPTURED".equals(event.getStatus())) {
                log.info("Initiating refund for failed payment: paymentId={}", event.getPaymentId());

                String refundId = refundService.createRefund(
                    event.getPaymentId(),
                    event.getAmount(),
                    "PAYMENT_PROCESSING_FAILURE"
                );

                refundService.processRefund(refundId, "AUTOMATIC");
            }
        } catch (Exception e) {
            log.error("Failed to initiate refund: paymentId={}", event.getPaymentId(), e);
        }
    }

    private void logValidationFailure(PaymentEvent event, String reason) {
        log.error("VALIDATION_FAILURE_AUDIT: paymentId={} userId={} amount={} currency={} reason={} timestamp={}",
            event.getPaymentId(),
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            reason,
            System.currentTimeMillis());
    }

    @Override
    protected String getServiceName() {
        return "PaymentProcessingConsumer";
    }

    @Override
    protected boolean isCriticalEvent(Object event) {
        if (event instanceof PaymentEvent) {
            PaymentEvent paymentEvent = (PaymentEvent) event;
            // High-value payments are critical
            return paymentEvent.getAmount() != null &&
                   paymentEvent.getAmount().compareTo(new BigDecimal("10000")) > 0;
        }
        return true;
    }
}
