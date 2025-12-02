package com.waqiti.transaction.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.transaction.service.TransactionService;
import com.waqiti.transaction.service.TransactionValidationService;
import com.waqiti.transaction.repository.TransactionRepository;
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
 * DLQ Consumer for transaction processing failures.
 * Handles critical transaction processing errors with financial integrity and audit compliance.
 */
@Component
@Slf4j
public class TransactionProcessingDlqConsumer extends BaseDlqConsumer {

    private final TransactionService transactionService;
    private final TransactionValidationService transactionValidationService;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransactionProcessingDlqConsumer(DlqHandler dlqHandler,
                                           AuditService auditService,
                                           NotificationService notificationService,
                                           MeterRegistry meterRegistry,
                                           TransactionService transactionService,
                                           TransactionValidationService transactionValidationService,
                                           TransactionRepository transactionRepository,
                                           KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.transactionService = transactionService;
        this.transactionValidationService = transactionValidationService;
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"transaction-processing.DLQ"},
        groupId = "transaction-processing-dlq-consumer-group",
        containerFactory = "criticalTransactionKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "transaction-processing-dlq", fallbackMethod = "handleTransactionProcessingDlqFallback")
    public void handleTransactionProcessingDlq(@Payload Object originalMessage,
                                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                              @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                              @Header(KafkaHeaders.OFFSET) long offset,
                                              Acknowledgment acknowledgment,
                                              @Header Map<String, Object> headers) {

        log.info("Processing transaction processing DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String transactionId = extractTransactionId(originalMessage);
            String userId = extractUserId(originalMessage);
            String accountId = extractAccountId(originalMessage);
            BigDecimal amount = extractAmount(originalMessage);
            String currency = extractCurrency(originalMessage);
            String transactionType = extractTransactionType(originalMessage);
            String merchantId = extractMerchantId(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing transaction processing DLQ: txnId={}, userId={}, amount={} {}, type={}, messageId={}",
                transactionId, userId, amount, currency, transactionType, messageId);

            // Validate transaction status and check for financial integrity
            if (transactionId != null) {
                validateTransactionStatus(transactionId, messageId);
                assessFinancialIntegrity(transactionId, amount, currency, originalMessage, messageId);
                handleTransactionRecovery(transactionId, transactionType, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts with financial urgency
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for balance integrity impact
            assessBalanceIntegrity(transactionId, accountId, amount, originalMessage, messageId);

            // Handle specific transaction failure types
            handleSpecificTransactionFailure(transactionType, transactionId, originalMessage, messageId);

            // Trigger manual transaction review
            triggerManualTransactionReview(transactionId, accountId, amount, messageId);

        } catch (Exception e) {
            log.error("Error in transaction processing DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "transaction-processing-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_TRANSACTIONS";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal amount = extractAmount(originalMessage);
        String transactionType = extractTransactionType(originalMessage);
        String accountId = extractAccountId(originalMessage);

        // Critical if high-value transaction
        if (amount != null && amount.compareTo(new BigDecimal("25000")) > 0) {
            return true;
        }

        // Critical transaction types
        if (isCriticalTransactionType(transactionType)) {
            return true;
        }

        // Critical if business account
        return isBusinessAccount(accountId);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String transactionId = extractTransactionId(originalMessage);
        String userId = extractUserId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);
        String currency = extractCurrency(originalMessage);
        String transactionType = extractTransactionType(originalMessage);
        String merchantId = extractMerchantId(originalMessage);

        try {
            // IMMEDIATE escalation for transaction failures - these have direct financial integrity impact
            String alertTitle = String.format("FINANCIAL CRITICAL: Transaction Processing Failed - %s %s",
                amount != null ? amount : "Unknown", currency != null ? currency : "USD");
            String alertMessage = String.format(
                "💳 TRANSACTION INTEGRITY ALERT 💳\n\n" +
                "A transaction processing event has failed and requires IMMEDIATE attention:\n\n" +
                "Transaction ID: %s\n" +
                "User ID: %s\n" +
                "Account ID: %s\n" +
                "Amount: %s %s\n" +
                "Transaction Type: %s\n" +
                "Merchant ID: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: This failure may result in financial inconsistency or customer impact.\n" +
                "Immediate transaction operations intervention required.",
                transactionId != null ? transactionId : "unknown",
                userId != null ? userId : "unknown",
                accountId != null ? accountId : "unknown",
                amount != null ? amount : "unknown",
                currency != null ? currency : "USD",
                transactionType != null ? transactionType : "unknown",
                merchantId != null ? merchantId : "unknown",
                exceptionMessage
            );

            // Send finance alert for all transaction DLQ issues
            notificationService.sendFinanceAlert(alertTitle, alertMessage, "CRITICAL");

            // Send specific transaction ops alert
            notificationService.sendTransactionOpsAlert(
                "URGENT: Transaction Processing DLQ",
                alertMessage,
                "CRITICAL"
            );

            // Send accounting alert for balance integrity
            notificationService.sendAccountingAlert(
                "Transaction Balance Integrity Risk",
                String.format("Transaction %s failure may affect account %s balance integrity. " +
                    "Review accounting reconciliation.", transactionId, accountId),
                "HIGH"
            );

            // Customer notification for transaction failure
            if (userId != null) {
                notificationService.sendNotification(userId,
                    "Transaction Processing Issue",
                    String.format("We're experiencing an issue processing your transaction of %s %s. " +
                        "Our team is working to resolve this immediately and ensure accurate account balances.",
                        amount != null ? amount : "the requested amount", currency != null ? currency : ""),
                    messageId);
            }

            // Merchant notification if applicable
            if (merchantId != null) {
                notificationService.sendMerchantAlert(merchantId,
                    "Transaction Processing Issue",
                    String.format("Transaction processing issue for transaction %s. " +
                        "Our operations team is addressing this immediately.", transactionId),
                    "HIGH"
                );
            }

            // Risk management alert for operational risk
            notificationService.sendRiskManagementAlert(
                "Transaction Processing Risk Alert",
                String.format("Transaction processing failure for %s may indicate operational risks. " +
                    "Review transaction processing infrastructure.", transactionId),
                "MEDIUM"
            );

            // Audit alert for regulatory compliance
            if (isRegulatoryReportableTransaction(transactionType, amount)) {
                notificationService.sendAuditAlert(
                    "Regulatory Transaction Failure",
                    String.format("Regulatory reportable transaction %s failed processing. " +
                        "Review audit trail and regulatory reporting impact.", transactionId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send transaction processing DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateTransactionStatus(String transactionId, String messageId) {
        try {
            var transaction = transactionRepository.findById(transactionId);
            if (transaction.isPresent()) {
                String status = transaction.get().getStatus();
                String state = transaction.get().getState();

                log.info("Transaction status validation for DLQ: txnId={}, status={}, state={}, messageId={}",
                    transactionId, status, state, messageId);

                // Check for critical transaction states
                if ("PROCESSING".equals(status) || "PENDING_SETTLEMENT".equals(status)) {
                    log.warn("Critical transaction state in DLQ: txnId={}, status={}", transactionId, status);

                    notificationService.sendFinanceAlert(
                        "URGENT: Critical Transaction State Failed",
                        String.format("Transaction %s in critical state %s has failed processing. " +
                            "Immediate financial reconciliation required.", transactionId, status),
                        "CRITICAL"
                    );
                }

                // Check for partial processing scenarios
                if ("PARTIALLY_PROCESSED".equals(state)) {
                    notificationService.sendAccountingAlert(
                        "Partial Transaction Processing Failed",
                        String.format("Partially processed transaction %s failed. " +
                            "Review double-booking and rollback procedures.", transactionId),
                        "HIGH"
                    );
                }
            } else {
                log.error("Transaction not found for DLQ: txnId={}, messageId={}", transactionId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating transaction status for DLQ: txnId={}, error={}",
                transactionId, e.getMessage());
        }
    }

    private void assessFinancialIntegrity(String transactionId, BigDecimal amount, String currency,
                                        Object originalMessage, String messageId) {
        try {
            log.info("Assessing financial integrity: txnId={}, amount={} {}", transactionId, amount, currency);

            // Check for high-value transaction impact
            if (amount != null && amount.compareTo(new BigDecimal("100000")) > 0) {
                log.warn("High-value transaction DLQ impact: txnId={}, amount={}", transactionId, amount);

                notificationService.sendExecutiveAlert(
                    "CRITICAL: High-Value Transaction Failed",
                    String.format("High-value transaction %s for %s %s has failed processing. " +
                        "Immediate executive and finance review required.", transactionId, amount, currency)
                );

                // Create high-value transaction incident
                kafkaTemplate.send("high-value-transaction-incidents", Map.of(
                    "transactionId", transactionId,
                    "amount", amount.toString(),
                    "currency", currency,
                    "incidentType", "TRANSACTION_DLQ_HIGH_VALUE",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Assess double-booking risk
            boolean hasDoubleBookingRisk = transactionService.assessDoubleBookingRisk(transactionId);
            if (hasDoubleBookingRisk) {
                notificationService.sendAccountingAlert(
                    "CRITICAL: Double-Booking Risk",
                    String.format("Transaction %s failure poses double-booking risk. " +
                        "Immediate accounting reconciliation required.", transactionId),
                    "CRITICAL"
                );
            }

            // Check for balance integrity impact
            boolean affectsBalanceIntegrity = transactionService.affectsBalanceIntegrity(transactionId);
            if (affectsBalanceIntegrity) {
                notificationService.sendFinanceAlert(
                    "Balance Integrity Risk Alert",
                    String.format("Transaction %s failure may affect balance integrity. " +
                        "Review account balancing procedures.", transactionId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing financial integrity: txnId={}, error={}", transactionId, e.getMessage());
        }
    }

    private void handleTransactionRecovery(String transactionId, String transactionType, Object originalMessage,
                                         String exceptionMessage, String messageId) {
        try {
            // Attempt automatic transaction recovery for recoverable failures
            boolean recoveryAttempted = transactionService.attemptTransactionRecovery(
                transactionId, transactionType, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic transaction recovery attempted: txnId={}, type={}", transactionId, transactionType);

                kafkaTemplate.send("transaction-recovery-queue", Map.of(
                    "transactionId", transactionId,
                    "transactionType", transactionType,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                // Recovery not possible - escalate for manual intervention
                log.warn("Automatic transaction recovery not possible: txnId={}", transactionId);

                notificationService.sendTransactionOpsAlert(
                    "Manual Transaction Recovery Required",
                    String.format("Transaction %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", transactionId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling transaction recovery: txnId={}, error={}", transactionId, e.getMessage());
        }
    }

    private void assessBalanceIntegrity(String transactionId, String accountId, BigDecimal amount,
                                      Object originalMessage, String messageId) {
        try {
            if (accountId != null && amount != null) {
                // Check current account balance consistency
                boolean hasBalanceInconsistency = transactionService.hasBalanceInconsistency(accountId, transactionId);
                if (hasBalanceInconsistency) {
                    log.warn("Balance inconsistency detected: txnId={}, accountId={}", transactionId, accountId);

                    notificationService.sendAccountingAlert(
                        "URGENT: Balance Inconsistency Detected",
                        String.format("Account %s shows balance inconsistency related to failed transaction %s. " +
                            "Immediate reconciliation required.", accountId, transactionId),
                        "CRITICAL"
                    );

                    // Create balance reconciliation task
                    kafkaTemplate.send("balance-reconciliation-queue", Map.of(
                        "accountId", accountId,
                        "transactionId", transactionId,
                        "amount", amount.toString(),
                        "reconciliationType", "TRANSACTION_DLQ_BALANCE_CHECK",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }

                // Check for overdraft scenarios
                if (transactionService.wouldCauseOverdraft(accountId, amount)) {
                    notificationService.sendRiskManagementAlert(
                        "Overdraft Risk from Transaction Failure",
                        String.format("Failed transaction %s may cause overdraft on account %s. " +
                            "Review overdraft policies and customer communication.", transactionId, accountId),
                        "MEDIUM"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing balance integrity: error={}", e.getMessage());
        }
    }

    private void handleSpecificTransactionFailure(String transactionType, String transactionId, Object originalMessage, String messageId) {
        try {
            switch (transactionType) {
                case "DEBIT":
                    handleDebitTransactionFailure(transactionId, originalMessage, messageId);
                    break;
                case "CREDIT":
                    handleCreditTransactionFailure(transactionId, originalMessage, messageId);
                    break;
                case "TRANSFER":
                    handleTransferTransactionFailure(transactionId, originalMessage, messageId);
                    break;
                case "WITHDRAWAL":
                    handleWithdrawalTransactionFailure(transactionId, originalMessage, messageId);
                    break;
                case "DEPOSIT":
                    handleDepositTransactionFailure(transactionId, originalMessage, messageId);
                    break;
                case "REFUND":
                    handleRefundTransactionFailure(transactionId, originalMessage, messageId);
                    break;
                case "CHARGEBACK":
                    handleChargebackTransactionFailure(transactionId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for transaction type: {}", transactionType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific transaction failure: type={}, txnId={}, error={}",
                transactionType, transactionId, e.getMessage());
        }
    }

    private void handleDebitTransactionFailure(String transactionId, Object originalMessage, String messageId) {
        String accountId = extractAccountId(originalMessage);
        notificationService.sendAccountingAlert(
            "Debit Transaction Failed",
            String.format("Debit transaction %s failed for account %s. Review account debiting procedures.", transactionId, accountId),
            "HIGH"
        );
    }

    private void handleCreditTransactionFailure(String transactionId, Object originalMessage, String messageId) {
        String accountId = extractAccountId(originalMessage);
        notificationService.sendAccountingAlert(
            "Credit Transaction Failed",
            String.format("Credit transaction %s failed for account %s. Review account crediting procedures.", transactionId, accountId),
            "HIGH"
        );
    }

    private void handleTransferTransactionFailure(String transactionId, Object originalMessage, String messageId) {
        notificationService.sendFinanceAlert(
            "Transfer Transaction Failed",
            String.format("Transfer transaction %s failed. Review inter-account transfer integrity.", transactionId),
            "HIGH"
        );
    }

    private void handleWithdrawalTransactionFailure(String transactionId, Object originalMessage, String messageId) {
        notificationService.sendRiskManagementAlert(
            "Withdrawal Transaction Failed",
            String.format("Withdrawal transaction %s failed. Review liquidity and customer communication.", transactionId),
            "MEDIUM"
        );
    }

    private void handleDepositTransactionFailure(String transactionId, Object originalMessage, String messageId) {
        notificationService.sendAccountingAlert(
            "Deposit Transaction Failed",
            String.format("Deposit transaction %s failed. Review deposit processing and customer notification.", transactionId),
            "MEDIUM"
        );
    }

    private void handleRefundTransactionFailure(String transactionId, Object originalMessage, String messageId) {
        notificationService.sendCustomerServiceAlert(
            "Refund Transaction Failed",
            String.format("Refund transaction %s failed. Immediate customer service follow-up required.", transactionId),
            "HIGH"
        );
    }

    private void handleChargebackTransactionFailure(String transactionId, Object originalMessage, String messageId) {
        notificationService.sendDisputeAlert(
            "Chargeback Transaction Failed",
            String.format("Chargeback transaction %s failed. Review dispute processing procedures.", transactionId),
            "HIGH"
        );
    }

    private void triggerManualTransactionReview(String transactionId, String accountId, BigDecimal amount, String messageId) {
        try {
            // All transaction DLQ messages require manual review due to financial implications
            kafkaTemplate.send("manual-transaction-review-queue", Map.of(
                "transactionId", transactionId,
                "accountId", accountId,
                "amount", amount != null ? amount.toString() : "unknown",
                "reviewReason", "DLQ_PROCESSING_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "balanceIntegrityCheck", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual transaction review for DLQ: txnId={}, accountId={}", transactionId, accountId);
        } catch (Exception e) {
            log.error("Error triggering manual transaction review: txnId={}, error={}", transactionId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleTransactionProcessingDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                      int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String transactionId = extractTransactionId(originalMessage);
        BigDecimal amount = extractAmount(originalMessage);

        // This is a CRITICAL situation - transaction system circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL SYSTEM FAILURE: Transaction DLQ Circuit Breaker",
                String.format("EMERGENCY: Transaction DLQ circuit breaker triggered for transaction %s (%s). " +
                    "This represents a complete failure of transaction processing systems. " +
                    "IMMEDIATE C-LEVEL AND FINANCE ESCALATION REQUIRED.", transactionId, amount)
            );

            // Mark as emergency financial issue
            transactionService.markEmergencyFinancialIssue(transactionId, "CIRCUIT_BREAKER_TRANSACTION_DLQ");

        } catch (Exception e) {
            log.error("Error in transaction processing DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for classification
    private boolean isCriticalTransactionType(String transactionType) {
        return transactionType != null && (
            transactionType.contains("WIRE") || transactionType.contains("LARGE_VALUE") ||
            transactionType.contains("INTERNATIONAL") || transactionType.contains("BUSINESS")
        );
    }

    private boolean isBusinessAccount(String accountId) {
        return accountId != null && transactionService.isBusinessAccount(accountId);
    }

    private boolean isRegulatoryReportableTransaction(String transactionType, BigDecimal amount) {
        if (amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
            return true;
        }
        return transactionType != null && (
            transactionType.contains("WIRE") || transactionType.contains("INTERNATIONAL") ||
            transactionType.contains("CASH")
        );
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

    private String extractAccountId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object accountId = messageMap.get("accountId");
                return accountId != null ? accountId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountId from message: {}", e.getMessage());
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

    private String extractTransactionType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object transactionType = messageMap.get("transactionType");
                if (transactionType == null) transactionType = messageMap.get("type");
                return transactionType != null ? transactionType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract transactionType from message: {}", e.getMessage());
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