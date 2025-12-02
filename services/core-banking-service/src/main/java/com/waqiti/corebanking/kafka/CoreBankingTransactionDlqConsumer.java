package com.waqiti.corebanking.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.corebanking.service.CoreBankingService;
import com.waqiti.corebanking.service.TransactionValidationService;
import com.waqiti.corebanking.repository.CoreBankingTransactionRepository;
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
 * DLQ Consumer for core banking transaction failures.
 * Handles critical core banking transaction processing errors with immediate escalation.
 */
@Component
@Slf4j
public class CoreBankingTransactionDlqConsumer extends BaseDlqConsumer {

    private final CoreBankingService coreBankingService;
    private final TransactionValidationService transactionValidationService;
    private final CoreBankingTransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CoreBankingTransactionDlqConsumer(DlqHandler dlqHandler,
                                           AuditService auditService,
                                           NotificationService notificationService,
                                           MeterRegistry meterRegistry,
                                           CoreBankingService coreBankingService,
                                           TransactionValidationService transactionValidationService,
                                           CoreBankingTransactionRepository transactionRepository,
                                           KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.coreBankingService = coreBankingService;
        this.transactionValidationService = transactionValidationService;
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"core-banking-transaction.DLQ"},
        groupId = "core-banking-transaction-dlq-consumer-group",
        containerFactory = "criticalCoreBankingKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "core-banking-transaction-dlq", fallbackMethod = "handleCoreBankingTransactionDlqFallback")
    public void handleCoreBankingTransactionDlq(@Payload Object originalMessage,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                               @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                               @Header(KafkaHeaders.OFFSET) long offset,
                                               Acknowledgment acknowledgment,
                                               @Header Map<String, Object> headers) {

        log.info("Processing core banking transaction DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String transactionId = extractTransactionId(originalMessage);
            String accountId = extractAccountId(originalMessage);
            String amount = extractAmount(originalMessage);
            String transactionType = extractTransactionType(originalMessage);
            String status = extractTransactionStatus(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing core banking transaction DLQ: transactionId={}, accountId={}, amount={}, type={}, messageId={}",
                transactionId, accountId, amount, transactionType, messageId);

            // Validate transaction state
            if (transactionId != null) {
                validateTransactionState(transactionId, messageId);
                assessTransactionIntegrity(transactionId, originalMessage, messageId);
                handleTransactionRecovery(transactionId, accountId, originalMessage, exceptionMessage, messageId);
            }

            // Generate immediate escalation alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for regulatory reporting impact
            assessRegulatoryReportingImpact(transactionId, transactionType, amount, originalMessage, messageId);

            // Handle transaction reconciliation impact
            handleTransactionReconciliationImpact(transactionId, accountId, amount, messageId);

            // Trigger manual transaction review
            triggerManualTransactionReview(transactionId, accountId, transactionType, amount, messageId);

        } catch (Exception e) {
            log.error("Error in core banking transaction DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "core-banking-transaction-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "CORE_BANKING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String amount = extractAmount(originalMessage);
        String transactionType = extractTransactionType(originalMessage);

        // All core banking transactions are critical
        if (isHighValueTransaction(amount)) {
            return true;
        }

        // Regulatory transactions are critical
        if (isRegulatoryTransaction(transactionType)) {
            return true;
        }

        // Wire transfers and ACH are critical
        return isWireOrAchTransaction(transactionType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String transactionId = extractTransactionId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        String amount = extractAmount(originalMessage);
        String transactionType = extractTransactionType(originalMessage);
        String status = extractTransactionStatus(originalMessage);

        try {
            // CRITICAL escalation for core banking transaction failures
            String alertTitle = String.format("CORE BANKING CRITICAL: Transaction Processing Failed - %s",
                transactionType != null ? transactionType : "Unknown Type");
            String alertMessage = String.format(
                "🏦 CORE BANKING TRANSACTION FAILURE 🏦\n\n" +
                "A core banking transaction has FAILED and requires IMMEDIATE attention:\n\n" +
                "Transaction ID: %s\n" +
                "Account ID: %s\n" +
                "Amount: %s\n" +
                "Transaction Type: %s\n" +
                "Status: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: Core banking transaction failures can cause:\n" +
                "- Financial data integrity issues\n" +
                "- Regulatory compliance violations\n" +
                "- Customer fund access problems\n" +
                "- Accounting reconciliation discrepancies\n\n" +
                "IMMEDIATE core banking operations and technology escalation required.",
                transactionId != null ? transactionId : "unknown",
                accountId != null ? accountId : "unknown",
                amount != null ? amount : "unknown",
                transactionType != null ? transactionType : "unknown",
                status != null ? status : "unknown",
                exceptionMessage
            );

            // Send core banking operations alert
            notificationService.sendCoreBankingAlert(alertTitle, alertMessage, "CRITICAL");

            // Send transaction processing alert
            notificationService.sendTransactionAlert(
                "URGENT: Core Banking Transaction Failed",
                alertMessage,
                "CRITICAL"
            );

            // Send financial operations alert
            notificationService.sendFinancialOpsAlert(
                "Core Banking Transaction DLQ",
                String.format("Core banking transaction %s failed processing. " +
                    "Review transaction integrity and financial impact.", transactionId),
                "HIGH"
            );

            // High-value transaction specific alerts
            if (isHighValueTransaction(amount)) {
                notificationService.sendHighValueTransactionAlert(
                    "HIGH VALUE TRANSACTION FAILED",
                    String.format("High-value core banking transaction %s (%s) failed. " +
                        "Immediate review for financial and regulatory impact required.", transactionId, amount),
                    "CRITICAL"
                );
            }

            // Wire transfer specific alerts
            if (isWireTransfer(transactionType)) {
                notificationService.sendWireTransferAlert(
                    "Wire Transfer Core Banking Failure",
                    String.format("Wire transfer %s failed in core banking. " +
                        "Review correspondent banking and settlement impact.", transactionId),
                    "HIGH"
                );
            }

            // ACH transaction specific alerts
            if (isAchTransaction(transactionType)) {
                notificationService.sendAchAlert(
                    "ACH Core Banking Transaction Failed",
                    String.format("ACH transaction %s failed in core banking. " +
                        "Review ACH processing and regulatory compliance.", transactionId),
                    "HIGH"
                );
            }

            // Regulatory reporting alert
            if (isRegulatoryTransaction(transactionType)) {
                notificationService.sendRegulatoryAlert(
                    "Regulatory Transaction Failed",
                    String.format("Regulatory core banking transaction %s failed. " +
                        "Review compliance and reporting requirements immediately.", transactionId),
                    "HIGH"
                );
            }

            // Technology escalation for system integrity
            notificationService.sendTechnologyAlert(
                "Core Banking System Integrity Alert",
                String.format("Core banking transaction processing failure may indicate system integrity issues. " +
                    "Transaction ID: %s, Account: %s", transactionId, accountId),
                "HIGH"
            );

        } catch (Exception e) {
            log.error("Failed to send core banking transaction DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateTransactionState(String transactionId, String messageId) {
        try {
            var transaction = transactionRepository.findById(transactionId);
            if (transaction.isPresent()) {
                String status = transaction.get().getStatus();
                String state = transaction.get().getState();

                log.info("Core banking transaction state validation: transactionId={}, status={}, state={}, messageId={}",
                    transactionId, status, state, messageId);

                // Check for critical transaction states
                if ("PROCESSING".equals(status) || "PENDING_SETTLEMENT".equals(status)) {
                    log.warn("Critical transaction state in DLQ: transactionId={}, status={}", transactionId, status);

                    notificationService.sendCoreBankingAlert(
                        "CRITICAL: Transaction State Inconsistency",
                        String.format("Transaction %s in critical state %s found in DLQ. " +
                            "Immediate transaction state reconciliation required.", transactionId, status),
                        "CRITICAL"
                    );
                }

                // Check for settlement states
                if ("SETTLEMENT_PENDING".equals(state) || "CLEARING_PENDING".equals(state)) {
                    notificationService.sendSettlementAlert(
                        "Settlement State Transaction Failed",
                        String.format("Transaction %s with settlement state %s failed. " +
                            "Review settlement and clearing processes.", transactionId, state),
                        "HIGH"
                    );
                }
            } else {
                log.warn("Transaction not found despite transactionId present: transactionId={}, messageId={}",
                    transactionId, messageId);
            }

        } catch (Exception e) {
            log.error("Error validating transaction state: transactionId={}, error={}",
                transactionId, e.getMessage());
        }
    }

    private void assessTransactionIntegrity(String transactionId, Object originalMessage, String messageId) {
        try {
            // Validate transaction data integrity
            boolean integrityValid = transactionValidationService.validateTransactionIntegrity(transactionId);
            if (!integrityValid) {
                log.error("Transaction integrity validation failed: transactionId={}", transactionId);

                notificationService.sendCoreBankingAlert(
                    "CRITICAL: Transaction Data Integrity Failure",
                    String.format("Transaction %s failed data integrity validation in DLQ processing. " +
                        "Immediate data consistency review required.", transactionId),
                    "CRITICAL"
                );

                // Create data integrity incident
                kafkaTemplate.send("data-integrity-incidents", Map.of(
                    "transactionId", transactionId,
                    "incidentType", "TRANSACTION_INTEGRITY_FAILURE",
                    "severity", "CRITICAL",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing transaction integrity: transactionId={}, error={}",
                transactionId, e.getMessage());
        }
    }

    private void handleTransactionRecovery(String transactionId, String accountId, Object originalMessage,
                                         String exceptionMessage, String messageId) {
        try {
            // Attempt automatic transaction recovery
            boolean recoveryAttempted = coreBankingService.attemptTransactionRecovery(
                transactionId, accountId, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic transaction recovery attempted: transactionId={}, accountId={}",
                    transactionId, accountId);

                kafkaTemplate.send("transaction-recovery-queue", Map.of(
                    "transactionId", transactionId,
                    "accountId", accountId,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                log.warn("Automatic transaction recovery not possible: transactionId={}", transactionId);

                notificationService.sendCoreBankingAlert(
                    "Manual Transaction Recovery Required",
                    String.format("Core banking transaction %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", transactionId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling transaction recovery: transactionId={}, error={}",
                transactionId, e.getMessage());
        }
    }

    private void assessRegulatoryReportingImpact(String transactionId, String transactionType, String amount,
                                               Object originalMessage, String messageId) {
        try {
            if (isRegulatoryReportableTransaction(transactionType, amount)) {
                log.warn("Regulatory reportable transaction failure: transactionId={}, type={}, amount={}",
                    transactionId, transactionType, amount);

                notificationService.sendRegulatoryAlert(
                    "CRITICAL: Regulatory Reportable Transaction Failed",
                    String.format("Regulatory reportable transaction %s (type: %s, amount: %s) failed. " +
                        "This may impact regulatory reporting deadlines and compliance. " +
                        "Immediate compliance team review required.", transactionId, transactionType, amount),
                    "CRITICAL"
                );

                // Create regulatory compliance incident
                kafkaTemplate.send("regulatory-compliance-incidents", Map.of(
                    "transactionId", transactionId,
                    "transactionType", transactionType,
                    "amount", amount,
                    "incidentType", "REGULATORY_TRANSACTION_FAILURE",
                    "reportingImpact", true,
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing regulatory reporting impact: transactionId={}, error={}",
                transactionId, e.getMessage());
        }
    }

    private void handleTransactionReconciliationImpact(String transactionId, String accountId, String amount, String messageId) {
        try {
            // All core banking transaction failures impact reconciliation
            notificationService.sendReconciliationAlert(
                "Transaction Reconciliation Impact",
                String.format("Core banking transaction %s failure will impact daily reconciliation. " +
                    "Account: %s, Amount: %s. Review reconciliation procedures.",
                    transactionId, accountId, amount),
                "MEDIUM"
            );

            // Send to reconciliation queue for impact tracking
            kafkaTemplate.send("reconciliation-impact-queue", Map.of(
                "transactionId", transactionId,
                "accountId", accountId,
                "amount", amount,
                "impactType", "CORE_BANKING_TRANSACTION_FAILURE",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Error handling reconciliation impact: transactionId={}, error={}",
                transactionId, e.getMessage());
        }
    }

    private void triggerManualTransactionReview(String transactionId, String accountId, String transactionType,
                                              String amount, String messageId) {
        try {
            // All core banking transaction DLQ requires manual review
            kafkaTemplate.send("manual-transaction-review-queue", Map.of(
                "transactionId", transactionId,
                "accountId", accountId,
                "transactionType", transactionType,
                "amount", amount,
                "reviewReason", "CORE_BANKING_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "requiresIntegrityCheck", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual transaction review for core banking DLQ: transactionId={}", transactionId);
        } catch (Exception e) {
            log.error("Error triggering manual transaction review: transactionId={}, error={}",
                transactionId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleCoreBankingTransactionDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                       int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String transactionId = extractTransactionId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        String amount = extractAmount(originalMessage);

        // EMERGENCY situation - core banking transaction circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Core Banking Transaction DLQ Circuit Breaker",
                String.format("CRITICAL SYSTEM FAILURE: Core banking transaction DLQ circuit breaker triggered " +
                    "for transaction %s (account %s, amount %s). " +
                    "This represents complete failure of core banking transaction processing. " +
                    "IMMEDIATE C-LEVEL, TECHNOLOGY, AND OPERATIONS ESCALATION REQUIRED.",
                    transactionId, accountId, amount)
            );

            // Mark as emergency financial operations issue
            coreBankingService.markEmergencyTransactionIssue(transactionId, accountId, "CIRCUIT_BREAKER_CORE_BANKING_DLQ");

        } catch (Exception e) {
            log.error("Error in core banking transaction DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for transaction classification
    private boolean isHighValueTransaction(String amount) {
        try {
            if (amount != null) {
                BigDecimal transactionAmount = new BigDecimal(amount);
                return transactionAmount.compareTo(new BigDecimal("10000")) >= 0; // $10,000+
            }
        } catch (Exception e) {
            log.debug("Could not parse transaction amount: {}", amount);
        }
        return false;
    }

    private boolean isRegulatoryTransaction(String transactionType) {
        return transactionType != null && (
            transactionType.contains("CTR") || transactionType.contains("SAR") ||
            transactionType.contains("REGULATORY") || transactionType.contains("COMPLIANCE")
        );
    }

    private boolean isWireOrAchTransaction(String transactionType) {
        return isWireTransfer(transactionType) || isAchTransaction(transactionType);
    }

    private boolean isWireTransfer(String transactionType) {
        return transactionType != null && (
            transactionType.contains("WIRE") || transactionType.contains("SWIFT")
        );
    }

    private boolean isAchTransaction(String transactionType) {
        return transactionType != null && transactionType.contains("ACH");
    }

    private boolean isRegulatoryReportableTransaction(String transactionType, String amount) {
        return isRegulatoryTransaction(transactionType) || isHighValueTransaction(amount);
    }

    // Data extraction helper methods
    private String extractTransactionId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object transactionId = messageMap.get("transactionId");
                if (transactionId == null) transactionId = messageMap.get("id");
                return transactionId != null ? transactionId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract transactionId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAccountId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object accountId = messageMap.get("accountId");
                if (accountId == null) accountId = messageMap.get("fromAccountId");
                if (accountId == null) accountId = messageMap.get("toAccountId");
                return accountId != null ? accountId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount == null) amount = messageMap.get("transactionAmount");
                return amount != null ? amount.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractTransactionType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("transactionType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract transactionType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractTransactionStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("status");
                if (status == null) status = messageMap.get("transactionStatus");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract status from message: {}", e.getMessage());
        }
        return null;
    }
}