package com.waqiti.wallet.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletCompensationService;
import com.waqiti.wallet.repository.WalletTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * P0 DLQ Consumer for wallet compensation failures.
 * Handles critical compensation operations that ensure financial accuracy and customer satisfaction.
 */
@Component
@Slf4j
public class WalletCompensationDlqConsumer extends BaseDlqConsumer {

    private final WalletService walletService;
    private final WalletCompensationService compensationService;
    private final WalletTransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WalletCompensationDlqConsumer(DlqHandler dlqHandler,
                                       AuditService auditService,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry,
                                       WalletService walletService,
                                       WalletCompensationService compensationService,
                                       WalletTransactionRepository transactionRepository,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.walletService = walletService;
        this.compensationService = compensationService;
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"wallet-compensation-dlq"},
        groupId = "wallet-compensation-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "wallet-compensation-dlq", fallbackMethod = "handleWalletCompensationDlqFallback")
    public void handleWalletCompensationDlq(@Payload Object originalMessage,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment,
                                          @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing wallet compensation DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String compensationId = extractCompensationId(originalMessage);
            String walletId = extractWalletId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String transactionId = extractTransactionId(originalMessage);
            BigDecimal amount = extractAmount(originalMessage);
            String compensationType = extractCompensationType(originalMessage);
            String status = extractStatus(originalMessage);

            log.info("Processing wallet compensation DLQ: compensationId={}, walletId={}, customerId={}, amount={}, type={}, messageId={}",
                compensationId, walletId, customerId, amount, compensationType, messageId);

            // Validate wallet compensation state and financial integrity
            if (compensationId != null || walletId != null) {
                validateWalletCompensationState(compensationId, walletId, messageId);
                assessFinancialCompensationIntegrity(compensationId, amount, originalMessage, messageId);
                handleCompensationRecovery(compensationId, walletId, customerId, amount, originalMessage, exceptionMessage, messageId);
            }

            // Generate critical financial alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for customer financial impact
            assessCustomerFinancialImpact(customerId, amount, compensationType, originalMessage, messageId);

            // Handle regulatory compliance for compensation
            handleCompensationCompliance(compensationId, customerId, amount, compensationType, originalMessage, messageId);

            // Trigger manual financial review
            triggerManualFinancialReview(compensationId, walletId, customerId, amount, compensationType, messageId);

        } catch (Exception e) {
            log.error("Error in wallet compensation DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "wallet-compensation-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "WALLET_FINANCIAL";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal amount = extractAmount(originalMessage);
        String compensationType = extractCompensationType(originalMessage);

        // All compensation failures are critical for financial accuracy
        if (amount != null && amount.compareTo(new BigDecimal("1000")) >= 0) {
            return true;
        }

        // Critical compensation types
        if (isHighPriorityCompensationType(compensationType)) {
            return true;
        }

        // System error compensations are always critical
        return isSystemErrorCompensation(compensationType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String compensationId = extractCompensationId(originalMessage);
        String walletId = extractWalletId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String transactionId = extractTransactionId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);
        String compensationType = extractCompensationType(originalMessage);
        String status = extractStatus(originalMessage);

        try {
            // CRITICAL financial escalation for compensation failures
            String alertTitle = String.format("FINANCIAL CRITICAL: Wallet Compensation Failed - %s",
                compensationType != null ? compensationType : "Unknown Type");
            String alertMessage = String.format(
                "💰 WALLET COMPENSATION SYSTEM FAILURE 💰\n\n" +
                "A wallet compensation operation has FAILED and requires IMMEDIATE financial attention:\n\n" +
                "Compensation ID: %s\n" +
                "Wallet ID: %s\n" +
                "Customer ID: %s\n" +
                "Transaction ID: %s\n" +
                "Amount: $%s\n" +
                "Compensation Type: %s\n" +
                "Status: %s\n" +
                "Error: %s\n\n" +
                "💰 CRITICAL: Compensation failures can cause:\n" +
                "- Customer financial losses\n" +
                "- Accounting discrepancies\n" +
                "- Regulatory compliance violations\n" +
                "- Customer satisfaction issues\n" +
                "- Financial audit problems\n\n" +
                "IMMEDIATE financial operations and customer compensation required.",
                compensationId != null ? compensationId : "unknown",
                walletId != null ? walletId : "unknown",
                customerId != null ? customerId : "unknown",
                transactionId != null ? transactionId : "unknown",
                amount != null ? amount.toString() : "unknown",
                compensationType != null ? compensationType : "unknown",
                status != null ? status : "unknown",
                exceptionMessage
            );

            // Send financial operations alert
            notificationService.sendFinancialAlert(alertTitle, alertMessage, "CRITICAL");

            // Send customer service alert
            notificationService.sendCustomerServiceAlert(
                "Customer Compensation Issue",
                String.format("Customer %s compensation failed (amount: $%s). " +
                    "IMMEDIATE customer service intervention required for compensation resolution.",
                    customerId, amount),
                "CRITICAL"
            );

            // Send accounting alert for financial reconciliation
            notificationService.sendAccountingAlert(
                "Compensation Accounting Issue",
                String.format("Wallet compensation %s (amount: $%s) failed for customer %s. " +
                    "Review financial records and ensure proper compensation accounting.",
                    compensationId, amount, customerId),
                "HIGH"
            );

            // High-value compensation specific alerts
            if (isHighValueCompensation(amount)) {
                notificationService.sendHighValueAlert(
                    "HIGH VALUE COMPENSATION FAILED",
                    String.format("High-value compensation %s ($%s) failed for customer %s. " +
                        "IMMEDIATE executive review and customer notification required.",
                        compensationId, amount, customerId),
                    "CRITICAL"
                );
            }

            // System error compensation specific alerts
            if (isSystemErrorCompensation(compensationType)) {
                notificationService.sendTechnologyAlert(
                    "System Error Compensation Failed",
                    String.format("System error compensation failed for customer %s. " +
                        "Review system integrity and customer impact. Amount: $%s",
                        customerId, amount),
                    "HIGH"
                );
            }

            // Fraud-related compensation alerts
            if (isFraudCompensation(compensationType)) {
                notificationService.sendFraudAlert(
                    "Fraud Compensation Failed",
                    String.format("Fraud-related compensation %s failed for customer %s. " +
                        "Review fraud case and ensure customer protection. Amount: $%s",
                        compensationId, customerId, amount),
                    "HIGH"
                );
            }

            // Compliance alert for regulatory requirements
            notificationService.sendComplianceAlert(
                "Compensation Compliance Risk",
                String.format("Compensation failure may impact regulatory compliance. " +
                    "Customer: %s, Amount: $%s, Type: %s",
                    customerId, amount, compensationType),
                "MEDIUM"
            );

            // Risk management alert for financial exposure
            notificationService.sendRiskManagementAlert(
                "Financial Compensation Risk",
                String.format("Compensation failure creates financial risk exposure. " +
                    "Customer: %s, Amount: $%s, Review compensation procedures.",
                    customerId, amount),
                "HIGH"
            );

        } catch (Exception e) {
            log.error("Failed to send wallet compensation DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateWalletCompensationState(String compensationId, String walletId, String messageId) {
        try {
            if (compensationId != null) {
                var compensation = compensationService.findById(compensationId);
                if (compensation.isPresent()) {
                    String status = compensation.get().getStatus();
                    String state = compensation.get().getState();

                    log.info("Wallet compensation state validation: compensationId={}, status={}, state={}, messageId={}",
                        compensationId, status, state, messageId);

                    // Check for critical compensation states
                    if ("PROCESSING".equals(status) || "PENDING_APPROVAL".equals(status)) {
                        log.warn("Critical wallet compensation state in DLQ: compensationId={}, status={}", compensationId, status);

                        notificationService.sendFinancialAlert(
                            "CRITICAL: Wallet Compensation State Inconsistency",
                            String.format("Wallet compensation %s in critical state %s found in DLQ. " +
                                "Immediate financial compensation state reconciliation required.", compensationId, status),
                            "CRITICAL"
                        );
                    }

                    // Check for high-value compensation states
                    if ("HIGH_VALUE_REVIEW".equals(state) || "EXECUTIVE_APPROVAL".equals(state)) {
                        notificationService.sendExecutiveAlert(
                            "Executive Compensation Review Failed",
                            String.format("High-value compensation %s requiring executive approval failed. " +
                                "IMMEDIATE executive and financial review required.", compensationId)
                        );
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error validating wallet compensation state: compensationId={}, error={}",
                compensationId, e.getMessage());
        }
    }

    private void assessFinancialCompensationIntegrity(String compensationId, BigDecimal amount, Object originalMessage, String messageId) {
        try {
            // Validate compensation financial integrity
            boolean integrityValid = compensationService.validateCompensationIntegrity(compensationId);
            if (!integrityValid) {
                log.error("Wallet compensation integrity validation failed: compensationId={}", compensationId);

                notificationService.sendFinancialAlert(
                    "CRITICAL: Compensation Financial Integrity Failure",
                    String.format("Wallet compensation %s failed financial integrity validation in DLQ processing. " +
                        "Amount: $%s. Immediate financial reconciliation required.", compensationId, amount),
                    "CRITICAL"
                );

                // Create financial integrity incident
                kafkaTemplate.send("financial-integrity-incidents", Map.of(
                    "compensationId", compensationId != null ? compensationId : "unknown",
                    "amount", amount != null ? amount.toString() : "0",
                    "incidentType", "COMPENSATION_INTEGRITY_FAILURE",
                    "severity", "CRITICAL",
                    "financialImpact", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing compensation integrity: compensationId={}, error={}",
                compensationId, e.getMessage());
        }
    }

    private void handleCompensationRecovery(String compensationId, String walletId, String customerId, BigDecimal amount,
                                          Object originalMessage, String exceptionMessage, String messageId) {
        try {
            // Attempt automatic compensation recovery
            boolean recoveryAttempted = compensationService.attemptCompensationRecovery(
                compensationId, walletId, customerId, amount, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic compensation recovery attempted: compensationId={}, walletId={}, customerId={}, amount={}",
                    compensationId, walletId, customerId, amount);

                kafkaTemplate.send("compensation-recovery-queue", Map.of(
                    "compensationId", compensationId != null ? compensationId : "unknown",
                    "walletId", walletId != null ? walletId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "amount", amount != null ? amount.toString() : "0",
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                log.warn("Automatic compensation recovery not possible: compensationId={}", compensationId);

                notificationService.sendFinancialAlert(
                    "Manual Compensation Recovery Required",
                    String.format("Compensation %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful. Amount: $%s", compensationId, amount),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling compensation recovery: compensationId={}, error={}",
                compensationId, e.getMessage());
        }
    }

    private void assessCustomerFinancialImpact(String customerId, BigDecimal amount, String compensationType,
                                             Object originalMessage, String messageId) {
        try {
            if (customerId != null && hasCustomerFinancialImpact(amount, compensationType)) {
                log.warn("Customer financial impact due to compensation failure: customerId={}, amount={}, type={}",
                    customerId, amount, compensationType);

                notificationService.sendCustomerServiceAlert(
                    "CRITICAL: Customer Financial Impact",
                    String.format("Customer %s affected by compensation failure (amount: $%s, type: %s). " +
                        "This represents immediate customer financial impact. " +
                        "IMMEDIATE customer service response and compensation resolution required.",
                        customerId, amount, compensationType),
                    "CRITICAL"
                );

                // Create customer impact incident
                kafkaTemplate.send("customer-financial-impact-incidents", Map.of(
                    "customerId", customerId,
                    "amount", amount != null ? amount.toString() : "0",
                    "compensationType", compensationType,
                    "incidentType", "COMPENSATION_CUSTOMER_IMPACT",
                    "impactLevel", "HIGH",
                    "financialImpact", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing customer financial impact: customerId={}, error={}",
                customerId, e.getMessage());
        }
    }

    private void handleCompensationCompliance(String compensationId, String customerId, BigDecimal amount,
                                            String compensationType, Object originalMessage, String messageId) {
        try {
            if (requiresRegulatoryReporting(amount, compensationType)) {
                log.info("Compensation requires regulatory compliance review: compensationId={}, amount={}, type={}",
                    compensationId, amount, compensationType);

                kafkaTemplate.send("compensation-compliance-queue", Map.of(
                    "compensationId", compensationId != null ? compensationId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "amount", amount != null ? amount.toString() : "0",
                    "compensationType", compensationType,
                    "complianceReason", "COMPENSATION_DLQ_FAILURE",
                    "reportingRequired", true,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));

                notificationService.sendComplianceAlert(
                    "Compensation Regulatory Review Required",
                    String.format("Compensation failure requires regulatory compliance review. " +
                        "Customer: %s, Amount: $%s, Type: %s", customerId, amount, compensationType),
                    "HIGH"
                );
            }
        } catch (Exception e) {
            log.error("Error handling compensation compliance: compensationId={}, error={}",
                compensationId, e.getMessage());
        }
    }

    private void triggerManualFinancialReview(String compensationId, String walletId, String customerId,
                                            BigDecimal amount, String compensationType, String messageId) {
        try {
            // All compensation DLQ requires manual financial review due to customer impact
            kafkaTemplate.send("manual-financial-review-queue", Map.of(
                "compensationId", compensationId != null ? compensationId : "unknown",
                "walletId", walletId != null ? walletId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "amount", amount != null ? amount.toString() : "0",
                "compensationType", compensationType,
                "reviewReason", "COMPENSATION_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "customerImpact", true,
                "requiresImmediateReview", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual financial review for compensation DLQ: compensationId={}, customerId={}, amount={}",
                compensationId, customerId, amount);
        } catch (Exception e) {
            log.error("Error triggering manual financial review: compensationId={}, error={}",
                compensationId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleWalletCompensationDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                   int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String compensationId = extractCompensationId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);

        // EMERGENCY situation - compensation circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Wallet Compensation DLQ Circuit Breaker",
                String.format("CRITICAL FINANCIAL FAILURE: Wallet compensation DLQ circuit breaker triggered " +
                    "for compensation %s (customer %s, amount $%s). " +
                    "This represents complete failure of compensation systems. " +
                    "IMMEDIATE C-LEVEL, FINANCIAL, AND CUSTOMER SERVICE ESCALATION REQUIRED.",
                    compensationId, customerId, amount)
            );

            // Mark as emergency financial issue
            compensationService.markEmergencyFinancialIssue(compensationId, customerId, amount, "CIRCUIT_BREAKER_COMPENSATION_DLQ");

        } catch (Exception e) {
            log.error("Error in wallet compensation DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for compensation classification
    private boolean isHighPriorityCompensationType(String compensationType) {
        return compensationType != null && (
            compensationType.contains("SYSTEM_ERROR") || compensationType.contains("FRAUD_REFUND") ||
            compensationType.contains("SERVICE_FAILURE") || compensationType.contains("DISPUTE_RESOLUTION")
        );
    }

    private boolean isSystemErrorCompensation(String compensationType) {
        return compensationType != null && compensationType.contains("SYSTEM_ERROR");
    }

    private boolean isFraudCompensation(String compensationType) {
        return compensationType != null && compensationType.contains("FRAUD");
    }

    private boolean isHighValueCompensation(BigDecimal amount) {
        return amount != null && amount.compareTo(new BigDecimal("5000")) >= 0;
    }

    private boolean hasCustomerFinancialImpact(BigDecimal amount, String compensationType) {
        return amount != null && amount.compareTo(new BigDecimal("100")) >= 0;
    }

    private boolean requiresRegulatoryReporting(BigDecimal amount, String compensationType) {
        return amount != null && amount.compareTo(new BigDecimal("10000")) >= 0;
    }

    // Data extraction helper methods
    private String extractCompensationId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object compensationId = messageMap.get("compensationId");
                if (compensationId == null) compensationId = messageMap.get("id");
                return compensationId != null ? compensationId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract compensationId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractWalletId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object walletId = messageMap.get("walletId");
                return walletId != null ? walletId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract walletId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCustomerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object customerId = messageMap.get("customerId");
                if (customerId == null) customerId = messageMap.get("userId");
                return customerId != null ? customerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract customerId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractTransactionId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object transactionId = messageMap.get("transactionId");
                if (transactionId == null) transactionId = messageMap.get("txnId");
                return transactionId != null ? transactionId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract transactionId from message: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount == null) amount = messageMap.get("compensationAmount");
                if (amount instanceof Number) {
                    return new BigDecimal(amount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCompensationType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("compensationType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract compensationType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("status");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract status from message: {}", e.getMessage());
        }
        return null;
    }
}