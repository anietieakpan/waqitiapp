package com.waqiti.ledger.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.service.LedgerValidationService;
import com.waqiti.ledger.repository.LedgerEntryRepository;
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
 * DLQ Consumer for ledger posting failures.
 * Handles critical ledger posting errors with accounting integrity and audit compliance.
 */
@Component
@Slf4j
public class LedgerPostingDlqConsumer extends BaseDlqConsumer {

    private final LedgerService ledgerService;
    private final LedgerValidationService ledgerValidationService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LedgerPostingDlqConsumer(DlqHandler dlqHandler,
                                   AuditService auditService,
                                   NotificationService notificationService,
                                   MeterRegistry meterRegistry,
                                   LedgerService ledgerService,
                                   LedgerValidationService ledgerValidationService,
                                   LedgerEntryRepository ledgerEntryRepository,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.ledgerService = ledgerService;
        this.ledgerValidationService = ledgerValidationService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"ledger-posting.DLQ"},
        groupId = "ledger-posting-dlq-consumer-group",
        containerFactory = "criticalLedgerKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "ledger-posting-dlq", fallbackMethod = "handleLedgerPostingDlqFallback")
    public void handleLedgerPostingDlq(@Payload Object originalMessage,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment,
                                      @Header Map<String, Object> headers) {

        log.info("Processing ledger posting DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String ledgerEntryId = extractLedgerEntryId(originalMessage);
            String transactionId = extractTransactionId(originalMessage);
            String accountId = extractAccountId(originalMessage);
            BigDecimal debitAmount = extractDebitAmount(originalMessage);
            BigDecimal creditAmount = extractCreditAmount(originalMessage);
            String currency = extractCurrency(originalMessage);
            String entryType = extractEntryType(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing ledger posting DLQ: ledgerEntryId={}, txnId={}, debit={}, credit={}, type={}, messageId={}",
                ledgerEntryId, transactionId, debitAmount, creditAmount, entryType, messageId);

            // Validate ledger entry status and check for accounting integrity
            if (ledgerEntryId != null) {
                validateLedgerEntryStatus(ledgerEntryId, messageId);
                assessAccountingIntegrity(ledgerEntryId, debitAmount, creditAmount, originalMessage, messageId);
                handleLedgerRecovery(ledgerEntryId, entryType, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts with accounting urgency
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for balance equation integrity
            assessBalanceEquation(ledgerEntryId, debitAmount, creditAmount, originalMessage, messageId);

            // Handle specific ledger failure types
            handleSpecificLedgerFailure(entryType, ledgerEntryId, originalMessage, messageId);

            // Trigger manual ledger review
            triggerManualLedgerReview(ledgerEntryId, transactionId, debitAmount, creditAmount, messageId);

        } catch (Exception e) {
            log.error("Error in ledger posting DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "ledger-posting-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_LEDGER";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        BigDecimal debitAmount = extractDebitAmount(originalMessage);
        BigDecimal creditAmount = extractCreditAmount(originalMessage);
        String entryType = extractEntryType(originalMessage);

        // Critical if high-value ledger entry
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (debitAmount != null) totalAmount = totalAmount.add(debitAmount);
        if (creditAmount != null) totalAmount = totalAmount.add(creditAmount);

        if (totalAmount.compareTo(new BigDecimal("50000")) > 0) {
            return true;
        }

        // Critical ledger entry types
        return isCriticalLedgerEntryType(entryType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String ledgerEntryId = extractLedgerEntryId(originalMessage);
        String transactionId = extractTransactionId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        BigDecimal debitAmount = extractDebitAmount(originalMessage);
        BigDecimal creditAmount = extractCreditAmount(originalMessage);
        String currency = extractCurrency(originalMessage);
        String entryType = extractEntryType(originalMessage);

        try {
            // IMMEDIATE escalation for ledger failures - these have direct accounting integrity impact
            String alertTitle = String.format("ACCOUNTING CRITICAL: Ledger Posting Failed - Dr:%s Cr:%s %s",
                debitAmount != null ? debitAmount : "0", creditAmount != null ? creditAmount : "0",
                currency != null ? currency : "USD");
            String alertMessage = String.format(
                "📊 LEDGER INTEGRITY ALERT 📊\n\n" +
                "A ledger posting event has failed and requires IMMEDIATE attention:\n\n" +
                "Ledger Entry ID: %s\n" +
                "Transaction ID: %s\n" +
                "Account ID: %s\n" +
                "Debit Amount: %s %s\n" +
                "Credit Amount: %s %s\n" +
                "Entry Type: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: This failure may result in accounting imbalance or audit violations.\n" +
                "Immediate accounting operations intervention required.",
                ledgerEntryId != null ? ledgerEntryId : "unknown",
                transactionId != null ? transactionId : "unknown",
                accountId != null ? accountId : "unknown",
                debitAmount != null ? debitAmount : "0",
                currency != null ? currency : "USD",
                creditAmount != null ? creditAmount : "0",
                currency != null ? currency : "USD",
                entryType != null ? entryType : "unknown",
                exceptionMessage
            );

            // Send accounting alert for all ledger DLQ issues
            notificationService.sendAccountingAlert(alertTitle, alertMessage, "CRITICAL");

            // Send specific ledger ops alert
            notificationService.sendLedgerOpsAlert(
                "URGENT: Ledger Posting DLQ",
                alertMessage,
                "CRITICAL"
            );

            // Send finance alert for balance integrity
            notificationService.sendFinanceAlert(
                "Ledger Balance Integrity Risk",
                String.format("Ledger entry %s failure may affect accounting balance integrity. " +
                    "Review general ledger reconciliation.", ledgerEntryId),
                "HIGH"
            );

            // Send audit alert for regulatory compliance
            notificationService.sendAuditAlert(
                "Ledger Audit Trail Risk",
                String.format("Ledger posting failure for entry %s may affect audit trail integrity. " +
                    "Review audit and regulatory reporting impact.", ledgerEntryId),
                "HIGH"
            );

            // CFO alert for significant financial impact
            BigDecimal totalAmount = BigDecimal.ZERO;
            if (debitAmount != null) totalAmount = totalAmount.add(debitAmount);
            if (creditAmount != null) totalAmount = totalAmount.add(creditAmount);

            if (totalAmount.compareTo(new BigDecimal("100000")) > 0) {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: High-Value Ledger Entry Failed",
                    String.format("High-value ledger entry %s for %s %s has failed processing. " +
                        "Immediate CFO and accounting review required.", ledgerEntryId, totalAmount, currency)
                );
            }

            // Risk management alert for operational risk
            notificationService.sendRiskManagementAlert(
                "Ledger Processing Risk Alert",
                String.format("Ledger posting failure for %s may indicate operational or system risks. " +
                    "Review ledger processing infrastructure.", ledgerEntryId),
                "MEDIUM"
            );

            // Controller's office alert for month-end impact
            if (isMonthEndCritical()) {
                notificationService.sendControllerAlert(
                    "Month-End Critical: Ledger Posting Failed",
                    String.format("Ledger posting failure during month-end period. " +
                        "Review impact on financial close procedures."),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send ledger posting DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateLedgerEntryStatus(String ledgerEntryId, String messageId) {
        try {
            var ledgerEntry = ledgerEntryRepository.findById(ledgerEntryId);
            if (ledgerEntry.isPresent()) {
                String status = ledgerEntry.get().getStatus();
                String postingState = ledgerEntry.get().getPostingState();

                log.info("Ledger entry status validation for DLQ: entryId={}, status={}, postingState={}, messageId={}",
                    ledgerEntryId, status, postingState, messageId);

                // Check for critical posting states
                if ("POSTING".equals(status) || "PENDING_REVERSAL".equals(status)) {
                    log.warn("Critical ledger posting state in DLQ: entryId={}, status={}", ledgerEntryId, status);

                    notificationService.sendAccountingAlert(
                        "URGENT: Critical Ledger State Failed",
                        String.format("Ledger entry %s in critical state %s has failed processing. " +
                            "Immediate accounting reconciliation required.", ledgerEntryId, status),
                        "CRITICAL"
                    );
                }

                // Check for partial posting scenarios
                if ("PARTIALLY_POSTED".equals(postingState)) {
                    notificationService.sendLedgerOpsAlert(
                        "Partial Ledger Posting Failed",
                        String.format("Partially posted ledger entry %s failed. " +
                            "Review double-entry integrity and reversal procedures.", ledgerEntryId),
                        "HIGH"
                    );
                }
            } else {
                log.error("Ledger entry not found for DLQ: entryId={}, messageId={}", ledgerEntryId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating ledger entry status for DLQ: entryId={}, error={}",
                ledgerEntryId, e.getMessage());
        }
    }

    private void assessAccountingIntegrity(String ledgerEntryId, BigDecimal debitAmount, BigDecimal creditAmount,
                                         Object originalMessage, String messageId) {
        try {
            log.info("Assessing accounting integrity: entryId={}, debit={}, credit={}",
                ledgerEntryId, debitAmount, creditAmount);

            // Check double-entry balance
            boolean isBalanced = ledgerValidationService.validateDoubleEntryBalance(debitAmount, creditAmount);
            if (!isBalanced) {
                log.error("CRITICAL: Unbalanced double-entry in DLQ: entryId={}, debit={}, credit={}",
                    ledgerEntryId, debitAmount, creditAmount);

                notificationService.sendAccountingAlert(
                    "CRITICAL: Unbalanced Double-Entry Failed",
                    String.format("Unbalanced ledger entry %s (Dr:%s, Cr:%s) has failed processing. " +
                        "This violates fundamental accounting principles. IMMEDIATE CFO ESCALATION REQUIRED.",
                        ledgerEntryId, debitAmount, creditAmount),
                    "CRITICAL"
                );

                // Create emergency accounting incident
                kafkaTemplate.send("emergency-accounting-incidents", Map.of(
                    "ledgerEntryId", ledgerEntryId,
                    "debitAmount", debitAmount != null ? debitAmount.toString() : "0",
                    "creditAmount", creditAmount != null ? creditAmount.toString() : "0",
                    "incidentType", "UNBALANCED_DOUBLE_ENTRY_DLQ",
                    "severity", "CRITICAL",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Check for trial balance impact
            boolean affectsTrialBalance = ledgerService.affectsTrialBalance(ledgerEntryId);
            if (affectsTrialBalance) {
                notificationService.sendControllerAlert(
                    "Trial Balance Impact Alert",
                    String.format("Ledger entry %s failure may affect trial balance integrity. " +
                        "Review month-end and financial reporting impact.", ledgerEntryId),
                    "HIGH"
                );
            }

            // Assess period closing impact
            if (ledgerService.isPeriodClosingCritical(ledgerEntryId)) {
                notificationService.sendAccountingAlert(
                    "CRITICAL: Period Closing Impact",
                    String.format("Ledger entry %s failure affects period closing procedures. " +
                        "Review financial close timeline impact.", ledgerEntryId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing accounting integrity: entryId={}, error={}", ledgerEntryId, e.getMessage());
        }
    }

    private void handleLedgerRecovery(String ledgerEntryId, String entryType, Object originalMessage,
                                    String exceptionMessage, String messageId) {
        try {
            // Attempt automatic ledger recovery for recoverable failures
            boolean recoveryAttempted = ledgerService.attemptLedgerRecovery(
                ledgerEntryId, entryType, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic ledger recovery attempted: entryId={}, type={}", ledgerEntryId, entryType);

                kafkaTemplate.send("ledger-recovery-queue", Map.of(
                    "ledgerEntryId", ledgerEntryId,
                    "entryType", entryType,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                // Recovery not possible - escalate for manual intervention
                log.warn("Automatic ledger recovery not possible: entryId={}", ledgerEntryId);

                notificationService.sendLedgerOpsAlert(
                    "Manual Ledger Recovery Required",
                    String.format("Ledger entry %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", ledgerEntryId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling ledger recovery: entryId={}, error={}", ledgerEntryId, e.getMessage());
        }
    }

    private void assessBalanceEquation(String ledgerEntryId, BigDecimal debitAmount, BigDecimal creditAmount,
                                     Object originalMessage, String messageId) {
        try {
            // Validate fundamental accounting equation: Assets = Liabilities + Equity
            if (debitAmount != null && creditAmount != null) {
                boolean maintainsEquation = ledgerValidationService.validateAccountingEquation(
                    ledgerEntryId, debitAmount, creditAmount);

                if (!maintainsEquation) {
                    log.error("CRITICAL: Accounting equation violation in DLQ: entryId={}", ledgerEntryId);

                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Accounting Equation Violation",
                        String.format("Ledger entry %s violates the fundamental accounting equation. " +
                            "This is a critical accounting integrity failure requiring immediate CFO attention.",
                            ledgerEntryId)
                    );

                    // Create critical accounting violation incident
                    kafkaTemplate.send("critical-accounting-violations", Map.of(
                        "ledgerEntryId", ledgerEntryId,
                        "violationType", "ACCOUNTING_EQUATION_VIOLATION",
                        "debitAmount", debitAmount.toString(),
                        "creditAmount", creditAmount.toString(),
                        "severity", "CRITICAL",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }

                // Check for control account reconciliation impact
                boolean affectsControlAccounts = ledgerService.affectsControlAccountReconciliation(ledgerEntryId);
                if (affectsControlAccounts) {
                    notificationService.sendAccountingAlert(
                        "Control Account Reconciliation Impact",
                        String.format("Ledger entry %s failure affects control account reconciliation. " +
                            "Review subsidiary ledger consistency.", ledgerEntryId),
                        "MEDIUM"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing balance equation: error={}", e.getMessage());
        }
    }

    private void handleSpecificLedgerFailure(String entryType, String ledgerEntryId, Object originalMessage, String messageId) {
        try {
            switch (entryType) {
                case "REVENUE_POSTING":
                    handleRevenuePostingFailure(ledgerEntryId, originalMessage, messageId);
                    break;
                case "EXPENSE_POSTING":
                    handleExpensePostingFailure(ledgerEntryId, originalMessage, messageId);
                    break;
                case "ASSET_POSTING":
                    handleAssetPostingFailure(ledgerEntryId, originalMessage, messageId);
                    break;
                case "LIABILITY_POSTING":
                    handleLiabilityPostingFailure(ledgerEntryId, originalMessage, messageId);
                    break;
                case "EQUITY_POSTING":
                    handleEquityPostingFailure(ledgerEntryId, originalMessage, messageId);
                    break;
                case "ADJUSTMENT_POSTING":
                    handleAdjustmentPostingFailure(ledgerEntryId, originalMessage, messageId);
                    break;
                case "CLOSING_POSTING":
                    handleClosingPostingFailure(ledgerEntryId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for ledger entry type: {}", entryType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific ledger failure: type={}, entryId={}, error={}",
                entryType, ledgerEntryId, e.getMessage());
        }
    }

    private void handleRevenuePostingFailure(String ledgerEntryId, Object originalMessage, String messageId) {
        notificationService.sendAccountingAlert(
            "Revenue Posting Failed",
            String.format("Revenue posting %s failed. Review revenue recognition and financial reporting impact.", ledgerEntryId),
            "HIGH"
        );
    }

    private void handleExpensePostingFailure(String ledgerEntryId, Object originalMessage, String messageId) {
        notificationService.sendAccountingAlert(
            "Expense Posting Failed",
            String.format("Expense posting %s failed. Review expense matching and period allocation.", ledgerEntryId),
            "HIGH"
        );
    }

    private void handleAssetPostingFailure(String ledgerEntryId, Object originalMessage, String messageId) {
        notificationService.sendAccountingAlert(
            "Asset Posting Failed",
            String.format("Asset posting %s failed. Review asset valuation and balance sheet impact.", ledgerEntryId),
            "HIGH"
        );
    }

    private void handleLiabilityPostingFailure(String ledgerEntryId, Object originalMessage, String messageId) {
        notificationService.sendAccountingAlert(
            "Liability Posting Failed",
            String.format("Liability posting %s failed. Review liability recognition and disclosure requirements.", ledgerEntryId),
            "HIGH"
        );
    }

    private void handleEquityPostingFailure(String ledgerEntryId, Object originalMessage, String messageId) {
        notificationService.sendControllerAlert(
            "Equity Posting Failed",
            String.format("Equity posting %s failed. Review capital structure and shareholder equity reporting.", ledgerEntryId),
            "HIGH"
        );
    }

    private void handleAdjustmentPostingFailure(String ledgerEntryId, Object originalMessage, String messageId) {
        notificationService.sendAccountingAlert(
            "Adjustment Posting Failed",
            String.format("Adjustment posting %s failed. Review journal entry corrections and audit trail.", ledgerEntryId),
            "MEDIUM"
        );
    }

    private void handleClosingPostingFailure(String ledgerEntryId, Object originalMessage, String messageId) {
        notificationService.sendControllerAlert(
            "CRITICAL: Closing Posting Failed",
            String.format("Period closing posting %s failed. This affects financial statement preparation.", ledgerEntryId),
            "CRITICAL"
        );
    }

    private void triggerManualLedgerReview(String ledgerEntryId, String transactionId,
                                         BigDecimal debitAmount, BigDecimal creditAmount, String messageId) {
        try {
            // All ledger DLQ messages require manual review due to accounting implications
            kafkaTemplate.send("manual-ledger-review-queue", Map.of(
                "ledgerEntryId", ledgerEntryId,
                "transactionId", transactionId,
                "debitAmount", debitAmount != null ? debitAmount.toString() : "0",
                "creditAmount", creditAmount != null ? creditAmount.toString() : "0",
                "reviewReason", "DLQ_PROCESSING_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "accountingIntegrityCheck", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual ledger review for DLQ: entryId={}, txnId={}", ledgerEntryId, transactionId);
        } catch (Exception e) {
            log.error("Error triggering manual ledger review: entryId={}, error={}", ledgerEntryId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleLedgerPostingDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                              int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String ledgerEntryId = extractLedgerEntryId(originalMessage);
        BigDecimal debitAmount = extractDebitAmount(originalMessage);
        BigDecimal creditAmount = extractCreditAmount(originalMessage);

        // This is a CRITICAL situation - ledger system circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL SYSTEM FAILURE: Ledger DLQ Circuit Breaker",
                String.format("EMERGENCY: Ledger DLQ circuit breaker triggered for entry %s (Dr:%s, Cr:%s). " +
                    "This represents a complete failure of accounting systems. " +
                    "IMMEDIATE C-LEVEL AND CFO ESCALATION REQUIRED.",
                    ledgerEntryId, debitAmount, creditAmount)
            );

            // Mark as emergency accounting issue
            ledgerService.markEmergencyAccountingIssue(ledgerEntryId, "CIRCUIT_BREAKER_LEDGER_DLQ");

        } catch (Exception e) {
            log.error("Error in ledger posting DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for classification
    private boolean isCriticalLedgerEntryType(String entryType) {
        return entryType != null && (
            entryType.contains("CLOSING") || entryType.contains("ADJUSTMENT") ||
            entryType.contains("REVENUE") || entryType.contains("EQUITY")
        );
    }

    private boolean isMonthEndCritical() {
        return ledgerService.isWithinMonthEndPeriod();
    }

    // Data extraction helper methods
    private String extractLedgerEntryId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object entryId = messageMap.get("ledgerEntryId");
                if (entryId == null) entryId = messageMap.get("id");
                return entryId != null ? entryId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract ledgerEntryId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractTransactionId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object transactionId = messageMap.get("transactionId");
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
                return accountId != null ? accountId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountId from message: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal extractDebitAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object debitAmount = messageMap.get("debitAmount");
                if (debitAmount != null) {
                    return new BigDecimal(debitAmount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract debitAmount from message: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal extractCreditAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object creditAmount = messageMap.get("creditAmount");
                if (creditAmount != null) {
                    return new BigDecimal(creditAmount.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract creditAmount from message: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
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

    private String extractEntryType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object entryType = messageMap.get("entryType");
                if (entryType == null) entryType = messageMap.get("type");
                return entryType != null ? entryType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract entryType from message: {}", e.getMessage());
        }
        return null;
    }
}