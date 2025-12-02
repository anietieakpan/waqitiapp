package com.waqiti.payment.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.PaymentRecoveryService;
import com.waqiti.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for payment processing failures.
 * Handles critical payment processing errors with financial reconciliation and recovery.
 */
@Component
@Slf4j
public class PaymentProcessingDlqConsumer extends BaseDlqConsumer {

    private final PaymentService paymentService;
    private final PaymentRecoveryService paymentRecoveryService;
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentProcessingDlqConsumer(DlqHandler dlqHandler,
                                       AuditService auditService,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry,
                                       PaymentService paymentService,
                                       PaymentRecoveryService paymentRecoveryService,
                                       PaymentRepository paymentRepository,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.paymentService = paymentService;
        this.paymentRecoveryService = paymentRecoveryService;
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"payment-processing.DLQ"},
        groupId = "payment-processing-dlq-consumer-group",
        containerFactory = "criticalPaymentKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "payment-processing-dlq", fallbackMethod = "handlePaymentProcessingDlqFallback")
    public void handlePaymentProcessingDlq(@Payload Object originalMessage,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment,
                                          @Header Map<String, Object> headers) {

        log.info("Processing payment processing DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String paymentId = extractPaymentId(originalMessage);
            String userId = extractUserId(originalMessage);
            BigDecimal amount = extractAmount(originalMessage);
            String currency = extractCurrency(originalMessage);
            String paymentMethod = extractPaymentMethod(originalMessage);
            String merchantId = extractMerchantId(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing payment processing DLQ: paymentId={}, amount={} {}, method={}, messageId={}",
                paymentId, amount, currency, paymentMethod, messageId);

            // Validate payment status and check for financial impact
            if (paymentId != null) {
                validatePaymentStatus(paymentId, messageId);
                assessFinancialImpact(paymentId, amount, currency, originalMessage, messageId);
                handlePaymentRecovery(paymentId, paymentMethod, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts with financial urgency
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for merchant reconciliation impacts
            assessMerchantReconciliation(paymentId, merchantId, amount, originalMessage, messageId);

            // Handle specific payment failure types
            handleSpecificPaymentFailure(paymentMethod, paymentId, originalMessage, messageId);

            // Trigger manual payment review
            triggerManualPaymentReview(paymentId, amount, paymentMethod, messageId);

        } catch (Exception e) {
            log.error("Error in payment processing DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "payment-processing-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_PAYMENTS";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal amount = extractAmount(originalMessage);
        String paymentMethod = extractPaymentMethod(originalMessage);
        String merchantId = extractMerchantId(originalMessage);

        // Critical if high-value payment
        if (amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
            return true;
        }

        // Critical payment methods
        if (isHighValuePaymentMethod(paymentMethod)) {
            return true;
        }

        // Critical merchants
        return isHighPriorityMerchant(merchantId);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String paymentId = extractPaymentId(originalMessage);
        String userId = extractUserId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);
        String currency = extractCurrency(originalMessage);
        String paymentMethod = extractPaymentMethod(originalMessage);
        String merchantId = extractMerchantId(originalMessage);

        try {
            // IMMEDIATE escalation for payment failures - these have direct financial impact
            String alertTitle = String.format("FINANCIAL CRITICAL: Payment Processing Failed - %s %s",
                amount != null ? amount : "Unknown", currency != null ? currency : "USD");
            String alertMessage = String.format(
                "💰 FINANCIAL IMPACT ALERT 💰\n\n" +
                "A payment processing event has failed and requires IMMEDIATE attention:\n\n" +
                "Payment ID: %s\n" +
                "Amount: %s %s\n" +
                "Payment Method: %s\n" +
                "User ID: %s\n" +
                "Merchant ID: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: This failure may result in financial loss or customer impact.\n" +
                "Immediate financial operations intervention required.",
                paymentId != null ? paymentId : "unknown",
                amount != null ? amount : "unknown",
                currency != null ? currency : "USD",
                paymentMethod != null ? paymentMethod : "unknown",
                userId != null ? userId : "unknown",
                merchantId != null ? merchantId : "unknown",
                exceptionMessage
            );

            // Send treasury alert for all payment DLQ issues
            notificationService.sendTreasuryAlert(alertTitle, alertMessage);

            // Send specific payment ops alert
            notificationService.sendPaymentOpsAlert(
                "URGENT: Payment Processing DLQ",
                alertMessage,
                "CRITICAL"
            );

            // Send finance team alert
            notificationService.sendFinanceAlert(
                "Payment Processing Failure",
                String.format("Payment %s for %s %s has failed processing. Review financial impact.",
                    paymentId, amount, currency),
                "HIGH"
            );

            // Customer notification for payment failure
            if (userId != null) {
                notificationService.sendNotification(userId,
                    "Payment Processing Issue",
                    String.format("We're experiencing an issue processing your payment of %s %s. " +
                        "Our team is working to resolve this immediately. We'll update you shortly.",
                        amount != null ? amount : "the requested amount", currency != null ? currency : ""),
                    messageId);
            }

            // Merchant notification if applicable
            if (merchantId != null && isBusinessCriticalMerchant(merchantId)) {
                notificationService.sendMerchantAlert(merchantId,
                    "Payment Processing Issue",
                    String.format("Payment processing issue for payment %s. " +
                        "Our payment operations team is addressing this immediately.", paymentId),
                    "HIGH"
                );
            }

            // Risk management alert for fraud potential
            notificationService.sendRiskManagementAlert(
                "Payment Processing Risk Alert",
                String.format("Payment processing failure for %s may indicate system or fraud risks. " +
                    "Review required.", paymentId),
                "MEDIUM"
            );

        } catch (Exception e) {
            log.error("Failed to send payment processing DLQ notifications: {}", e.getMessage());
        }
    }

    private void validatePaymentStatus(String paymentId, String messageId) {
        try {
            var payment = paymentRepository.findById(paymentId);
            if (payment.isPresent()) {
                String status = payment.get().getStatus();
                String state = payment.get().getState();

                log.info("Payment status validation for DLQ: paymentId={}, status={}, state={}, messageId={}",
                    paymentId, status, state, messageId);

                // Check for critical payment states
                if ("PROCESSING".equals(status) || "PENDING_CAPTURE".equals(status)) {
                    log.warn("Critical payment state in DLQ: paymentId={}, status={}", paymentId, status);

                    notificationService.sendTreasuryAlert(
                        "URGENT: Critical Payment State Failed",
                        String.format("Payment %s in critical state %s has failed processing. " +
                            "Immediate financial reconciliation required.", paymentId, status)
                    );
                }

                // Check for settlement impact
                if ("CAPTURED".equals(status) && !"SETTLED".equals(state)) {
                    notificationService.sendPaymentOpsAlert(
                        "Settlement Impact Alert",
                        String.format("Captured payment %s failed processing. " +
                            "Review settlement reconciliation impact.", paymentId),
                        "HIGH"
                    );
                }
            } else {
                log.error("Payment not found for DLQ: paymentId={}, messageId={}", paymentId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating payment status for DLQ: paymentId={}, error={}",
                paymentId, e.getMessage());
        }
    }

    private void assessFinancialImpact(String paymentId, BigDecimal amount, String currency,
                                     Object originalMessage, String messageId) {
        try {
            log.info("Assessing financial impact: paymentId={}, amount={} {}", paymentId, amount, currency);

            // Check for high-value payment impact
            if (amount != null && amount.compareTo(new BigDecimal("50000")) > 0) {
                log.warn("High-value payment DLQ impact: paymentId={}, amount={}", paymentId, amount);

                notificationService.sendExecutiveAlert(
                    "CRITICAL: High-Value Payment Failed",
                    String.format("High-value payment %s for %s %s has failed processing. " +
                        "Immediate executive and treasury review required.", paymentId, amount, currency)
                );

                // Create high-value payment incident
                kafkaTemplate.send("high-value-payment-incidents", Map.of(
                    "paymentId", paymentId,
                    "amount", amount.toString(),
                    "currency", currency,
                    "incidentType", "PAYMENT_DLQ_HIGH_VALUE",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Assess currency exposure
            if ("USD".equals(currency) && amount != null && amount.compareTo(new BigDecimal("25000")) > 0) {
                notificationService.sendTreasuryAlert(
                    "USD Exposure Alert",
                    String.format("Significant USD payment failure: %s. Review treasury exposure.", amount)
                );
            }

            // Check for volume concentration risk
            boolean isVolumeRisk = paymentService.assessVolumeConcentrationRisk(paymentId, currency);
            if (isVolumeRisk) {
                notificationService.sendRiskManagementAlert(
                    "Payment Volume Risk Alert",
                    String.format("Payment failure for %s may indicate volume concentration risk. " +
                        "Review payment distribution patterns.", paymentId),
                    "MEDIUM"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing financial impact: paymentId={}, error={}", paymentId, e.getMessage());
        }
    }

    private void handlePaymentRecovery(String paymentId, String paymentMethod, Object originalMessage,
                                     String exceptionMessage, String messageId) {
        try {
            // Attempt automatic recovery for recoverable failures
            boolean recoveryAttempted = paymentRecoveryService.attemptAutomaticRecovery(
                paymentId, paymentMethod, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic payment recovery attempted: paymentId={}, method={}", paymentId, paymentMethod);

                kafkaTemplate.send("payment-recovery-queue", Map.of(
                    "paymentId", paymentId,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "paymentMethod", paymentMethod,
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                // Recovery not possible - escalate for manual intervention
                log.warn("Automatic recovery not possible for payment DLQ: paymentId={}", paymentId);

                notificationService.sendPaymentOpsAlert(
                    "Manual Payment Recovery Required",
                    String.format("Payment %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", paymentId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling payment recovery: paymentId={}, error={}", paymentId, e.getMessage());
        }
    }

    private void assessMerchantReconciliation(String paymentId, String merchantId, BigDecimal amount,
                                            Object originalMessage, String messageId) {
        try {
            if (merchantId != null) {
                // Check for merchant settlement impact
                boolean hasPendingSettlement = paymentService.hasPendingMerchantSettlement(merchantId, paymentId);
                if (hasPendingSettlement) {
                    log.warn("Payment DLQ affects merchant settlement: paymentId={}, merchantId={}",
                        paymentId, merchantId);

                    notificationService.sendMerchantOpsAlert(
                        "Merchant Settlement Impact",
                        String.format("Payment failure for merchant %s affects pending settlement. " +
                            "Review merchant reconciliation impact.", merchantId),
                        "HIGH"
                    );

                    // Create merchant reconciliation task
                    kafkaTemplate.send("merchant-reconciliation-queue", Map.of(
                        "merchantId", merchantId,
                        "paymentId", paymentId,
                        "amount", amount != null ? amount.toString() : "unknown",
                        "reconciliationType", "PAYMENT_DLQ_IMPACT",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }

                // Check for high-volume merchant
                if (isHighVolumeMerchant(merchantId)) {
                    notificationService.sendPaymentOpsAlert(
                        "High-Volume Merchant Payment Failure",
                        String.format("High-volume merchant %s payment %s failed. " +
                            "Review merchant relationship impact.", merchantId, paymentId),
                        "MEDIUM"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing merchant reconciliation: error={}", e.getMessage());
        }
    }

    private void handleSpecificPaymentFailure(String paymentMethod, String paymentId, Object originalMessage, String messageId) {
        try {
            switch (paymentMethod) {
                case "CREDIT_CARD":
                    handleCreditCardFailure(paymentId, originalMessage, messageId);
                    break;
                case "DEBIT_CARD":
                    handleDebitCardFailure(paymentId, originalMessage, messageId);
                    break;
                case "ACH":
                    handleAchFailure(paymentId, originalMessage, messageId);
                    break;
                case "WIRE_TRANSFER":
                    handleWireTransferFailure(paymentId, originalMessage, messageId);
                    break;
                case "DIGITAL_WALLET":
                    handleDigitalWalletFailure(paymentId, originalMessage, messageId);
                    break;
                case "BANK_TRANSFER":
                    handleBankTransferFailure(paymentId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for payment method: {}", paymentMethod);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific payment failure: method={}, paymentId={}, error={}",
                paymentMethod, paymentId, e.getMessage());
        }
    }

    private void handleCreditCardFailure(String paymentId, Object originalMessage, String messageId) {
        notificationService.sendPaymentOpsAlert(
            "Credit Card Payment Failed",
            String.format("Credit card payment %s failed. Review card processor status and fraud indicators.", paymentId),
            "HIGH"
        );

        // Check for card processor issues
        kafkaTemplate.send("card-processor-health-check", Map.of(
            "paymentId", paymentId,
            "checkType", "DLQ_TRIGGERED_HEALTH_CHECK",
            "messageId", messageId,
            "timestamp", Instant.now()
        ));
    }

    private void handleDebitCardFailure(String paymentId, Object originalMessage, String messageId) {
        notificationService.sendPaymentOpsAlert(
            "Debit Card Payment Failed",
            String.format("Debit card payment %s failed. Review PIN network status and account verification.", paymentId),
            "MEDIUM"
        );
    }

    private void handleAchFailure(String paymentId, Object originalMessage, String messageId) {
        notificationService.sendTreasuryAlert(
            "ACH Payment Failed",
            String.format("ACH payment %s failed. Review bank connectivity and settlement windows.", paymentId)
        );
    }

    private void handleWireTransferFailure(String paymentId, Object originalMessage, String messageId) {
        notificationService.sendExecutiveAlert(
            "Wire Transfer Failed",
            String.format("Wire transfer %s failed. This requires immediate executive review due to regulatory implications.", paymentId)
        );
    }

    private void handleDigitalWalletFailure(String paymentId, Object originalMessage, String messageId) {
        notificationService.sendPaymentOpsAlert(
            "Digital Wallet Payment Failed",
            String.format("Digital wallet payment %s failed. Review wallet provider connectivity.", paymentId),
            "MEDIUM"
        );
    }

    private void handleBankTransferFailure(String paymentId, Object originalMessage, String messageId) {
        notificationService.sendTreasuryAlert(
            "Bank Transfer Failed",
            String.format("Bank transfer %s failed. Review bank integration status and liquidity.", paymentId)
        );
    }

    private void triggerManualPaymentReview(String paymentId, BigDecimal amount, String paymentMethod, String messageId) {
        try {
            // All payment DLQ messages require manual review due to financial implications
            kafkaTemplate.send("manual-payment-review-queue", Map.of(
                "paymentId", paymentId,
                "amount", amount != null ? amount.toString() : "unknown",
                "paymentMethod", paymentMethod,
                "reviewReason", "DLQ_PROCESSING_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "financialImpact", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual payment review for DLQ: paymentId={}, method={}", paymentId, paymentMethod);
        } catch (Exception e) {
            log.error("Error triggering manual payment review: paymentId={}, error={}", paymentId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handlePaymentProcessingDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                  int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String paymentId = extractPaymentId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);

        // This is a CRITICAL situation - payment system circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL SYSTEM FAILURE: Payment DLQ Circuit Breaker",
                String.format("EMERGENCY: Payment DLQ circuit breaker triggered for payment %s (%s). " +
                    "This represents a complete failure of payment processing systems. " +
                    "IMMEDIATE C-LEVEL AND TREASURY ESCALATION REQUIRED.", paymentId, amount)
            );

            // Mark as emergency financial issue
            paymentService.markEmergencyFinancialIssue(paymentId, "CIRCUIT_BREAKER_PAYMENT_DLQ");

        } catch (Exception e) {
            log.error("Error in payment processing DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for classification
    private boolean isHighValuePaymentMethod(String paymentMethod) {
        return paymentMethod != null && (
            paymentMethod.contains("WIRE") || paymentMethod.contains("SWIFT") ||
            paymentMethod.contains("CORPORATE") || paymentMethod.contains("PREMIUM")
        );
    }

    private boolean isHighPriorityMerchant(String merchantId) {
        return merchantId != null && paymentService.isHighPriorityMerchant(merchantId);
    }

    private boolean isBusinessCriticalMerchant(String merchantId) {
        return merchantId != null && paymentService.isBusinessCriticalMerchant(merchantId);
    }

    private boolean isHighVolumeMerchant(String merchantId) {
        return merchantId != null && paymentService.isHighVolumeMerchant(merchantId);
    }

    // Data extraction helper methods
    private String extractPaymentId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object paymentId = messageMap.get("paymentId");
                if (paymentId == null) paymentId = messageMap.get("id");
                return paymentId != null ? paymentId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract paymentId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractUserId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object userId = messageMap.get("userId");
                if (userId == null) userId = messageMap.get("customerId");
                return userId != null ? userId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from message: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount != null) {
                    return new BigDecimal(amount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCurrency(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object currency = messageMap.get("currency");
                return currency != null ? currency.toString() : "USD";
            }
        } catch (Exception e) {
            log.debug("Could not extract currency from message: {}", e.getMessage());
        }
        return "USD";
    }

    private String extractPaymentMethod(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object paymentMethod = messageMap.get("paymentMethod");
                if (paymentMethod == null) paymentMethod = messageMap.get("method");
                return paymentMethod != null ? paymentMethod.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract paymentMethod from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractMerchantId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object merchantId = messageMap.get("merchantId");
                return merchantId != null ? merchantId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract merchantId from message: {}", e.getMessage());
        }
        return null;
    }
}