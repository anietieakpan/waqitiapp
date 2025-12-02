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
 * DLQ Consumer for core banking ledger failures.
 * Handles critical ledger posting and balance management errors with immediate escalation.
 */
@Component
@Slf4j
public class CoreBankingLedgerDlqConsumer extends BaseDlqConsumer {

    private final LedgerService ledgerService;
    private final LedgerValidationService ledgerValidationService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CoreBankingLedgerDlqConsumer(DlqHandler dlqHandler,
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
        topics = {"core-banking-ledger.DLQ"},
        groupId = "core-banking-ledger-dlq-consumer-group",
        containerFactory = "criticalLedgerKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "core-banking-ledger-dlq", fallbackMethod = "handleCoreBankingLedgerDlqFallback")
    public void handleCoreBankingLedgerDlq(@Payload Object originalMessage,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment,
                                         @Header Map<String, Object> headers) {

        log.info("Processing core banking ledger DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String entryId = extractEntryId(originalMessage);
            String accountId = extractAccountId(originalMessage);
            String amount = extractAmount(originalMessage);
            String entryType = extractEntryType(originalMessage);
            String entryStatus = extractEntryStatus(originalMessage);
            String transactionId = extractTransactionId(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing core banking ledger DLQ: entryId={}, accountId={}, amount={}, type={}, messageId={}",
                entryId, accountId, amount, entryType, messageId);

            // Validate ledger entry state and integrity
            if (entryId != null || transactionId != null) {
                validateLedgerEntryState(entryId, transactionId, messageId);
                assessLedgerIntegrity(entryId, accountId, originalMessage, messageId);
                handleLedgerRecovery(entryId, accountId, transactionId, originalMessage, exceptionMessage, messageId);
            }

            // Generate critical alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for balance integrity impact
            assessBalanceIntegrityImpact(accountId, amount, entryType, originalMessage, messageId);

            // Handle regulatory reporting impact
            assessRegulatoryLedgerImpact(entryId, entryType, amount, originalMessage, messageId);

            // Trigger manual ledger review
            triggerManualLedgerReview(entryId, accountId, transactionId, entryType, amount, messageId);

        } catch (Exception e) {
            log.error("Error in core banking ledger DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "core-banking-ledger-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "CORE_BANKING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String amount = extractAmount(originalMessage);
        String entryType = extractEntryType(originalMessage);

        // All ledger failures are critical for financial integrity
        if (isHighValueEntry(amount)) {
            return true;
        }

        // Regulatory entries are critical
        if (isRegulatoryEntry(entryType)) {
            return true;
        }

        // Balance-affecting entries are critical
        return isBalanceAffectingEntry(entryType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String entryId = extractEntryId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        String amount = extractAmount(originalMessage);
        String entryType = extractEntryType(originalMessage);
        String entryStatus = extractEntryStatus(originalMessage);
        String transactionId = extractTransactionId(originalMessage);

        try {
            // CRITICAL escalation for ledger failures
            String alertTitle = String.format("CORE BANKING CRITICAL: Ledger Processing Failed - %s",
                entryType != null ? entryType : "Unknown Entry");
            String alertMessage = String.format(
                "🏦 CORE BANKING LEDGER FAILURE 🏦\n\n" +
                "A core banking ledger operation has FAILED and requires IMMEDIATE attention:\n\n" +
                "Entry ID: %s\n" +
                "Transaction ID: %s\n" +
                "Account ID: %s\n" +
                "Amount: %s\n" +
                "Entry Type: %s\n" +
                "Entry Status: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: Ledger failures can cause:\n" +
                "- Account balance discrepancies\n" +
                "- Financial data integrity issues\n" +
                "- Regulatory compliance violations\n" +
                "- Reconciliation failures\n" +
                "- Audit trail gaps\n\n" +
                "IMMEDIATE ledger operations and financial controls escalation required.",
                entryId != null ? entryId : "unknown",
                transactionId != null ? transactionId : "unknown",
                accountId != null ? accountId : "unknown",
                amount != null ? amount : "unknown",
                entryType != null ? entryType : "unknown",
                entryStatus != null ? entryStatus : "unknown",
                exceptionMessage
            );

            // Send ledger operations alert
            notificationService.sendLedgerAlert(alertTitle, alertMessage, "CRITICAL");

            // Send core banking operations alert
            notificationService.sendCoreBankingAlert(
                "URGENT: Core Banking Ledger Failed",
                alertMessage,
                "CRITICAL"
            );

            // Send financial controls alert
            notificationService.sendFinancialControlsAlert(
                "Ledger Financial Controls Alert",
                String.format("Ledger entry %s failed posting. " +
                    "Review financial controls and balance integrity.", entryId),
                "HIGH"
            );

            // High-value entry specific alerts
            if (isHighValueEntry(amount)) {
                notificationService.sendHighValueTransactionAlert(
                    "HIGH VALUE LEDGER ENTRY FAILED",
                    String.format("High-value ledger entry %s (%s) failed. " +
                        "Immediate review for financial impact and balance integrity.", entryId, amount),
                    "CRITICAL"
                );
            }

            // Regulatory entry specific alerts
            if (isRegulatoryEntry(entryType)) {
                notificationService.sendRegulatoryAlert(
                    "Regulatory Ledger Entry Failed",
                    String.format("Regulatory ledger entry %s failed. " +
                        "Review compliance requirements and regulatory reporting impact.", entryId),
                    "HIGH"
                );
            }

            // Balance integrity alert
            notificationService.sendBalanceIntegrityAlert(
                "Balance Integrity Risk",
                String.format("Ledger entry %s failure may cause balance discrepancies for account %s. " +
                    "Immediate balance verification required.", entryId, accountId),
                "HIGH"
            );

            // Reconciliation impact alert
            notificationService.sendReconciliationAlert(
                "Reconciliation Impact Alert",
                String.format("Ledger entry %s failure will impact reconciliation processes. " +
                    "Review daily reconciliation procedures.", entryId),
                "MEDIUM"
            );

            // Audit trail alert
            notificationService.sendAuditAlert(
                "Audit Trail Integrity Alert",
                String.format("Ledger entry %s failure may create audit trail gaps. " +
                    "Review audit logging and trail completeness.", entryId),
                "MEDIUM"
            );

            // Technology escalation for system integrity
            notificationService.sendTechnologyAlert(
                "Ledger System Integrity Alert",
                String.format("Ledger processing failure may indicate system integrity issues. " +
                    "Entry: %s, Account: %s", entryId, accountId),
                "HIGH"
            );

        } catch (Exception e) {
            log.error("Failed to send core banking ledger DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateLedgerEntryState(String entryId, String transactionId, String messageId) {
        try {
            if (entryId != null) {
                var entry = ledgerEntryRepository.findById(entryId);
                if (entry.isPresent()) {
                    String status = entry.get().getStatus();
                    String state = entry.get().getState();

                    log.info("Ledger entry state validation: entryId={}, status={}, state={}, messageId={}",
                        entryId, status, state, messageId);

                    // Check for critical entry states
                    if ("POSTING".equals(status) || "PENDING_SETTLEMENT".equals(status)) {
                        log.warn("Critical ledger entry state in DLQ: entryId={}, status={}", entryId, status);

                        notificationService.sendLedgerAlert(
                            "CRITICAL: Ledger Entry State Inconsistency",
                            String.format("Ledger entry %s in critical state %s found in DLQ. " +
                                "Immediate ledger state reconciliation required.", entryId, status),
                            "CRITICAL"
                        );
                    }

                    // Check for balance-affecting states
                    if ("BALANCE_PENDING".equals(state) || "RECONCILIATION_PENDING".equals(state)) {
                        notificationService.sendBalanceIntegrityAlert(
                            "Balance State Entry Failed",
                            String.format("Ledger entry %s with balance state %s failed. " +
                                "Review balance integrity and reconciliation.", entryId, state),
                            "HIGH"
                        );
                    }
                } else {
                    log.warn("Ledger entry not found despite entryId present: entryId={}, messageId={}",
                        entryId, messageId);
                }
            }

        } catch (Exception e) {
            log.error("Error validating ledger entry state: entryId={}, error={}",
                entryId, e.getMessage());
        }
    }

    private void assessLedgerIntegrity(String entryId, String accountId, Object originalMessage, String messageId) {
        try {
            // Validate ledger data integrity
            boolean integrityValid = ledgerValidationService.validateLedgerIntegrity(entryId, accountId);
            if (!integrityValid) {
                log.error("Ledger integrity validation failed: entryId={}, accountId={}", entryId, accountId);

                notificationService.sendLedgerAlert(
                    "CRITICAL: Ledger Data Integrity Failure",
                    String.format("Ledger entry %s for account %s failed data integrity validation. " +
                        "Immediate ledger consistency review required.", entryId, accountId),
                    "CRITICAL"
                );

                // Create data integrity incident
                kafkaTemplate.send("data-integrity-incidents", Map.of(
                    "entryId", entryId != null ? entryId : "unknown",
                    "accountId", accountId != null ? accountId : "unknown",
                    "incidentType", "LEDGER_INTEGRITY_FAILURE",
                    "severity", "CRITICAL",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing ledger integrity: entryId={}, error={}",
                entryId, e.getMessage());
        }
    }

    private void handleLedgerRecovery(String entryId, String accountId, String transactionId, Object originalMessage,
                                    String exceptionMessage, String messageId) {
        try {
            // Attempt automatic ledger recovery
            boolean recoveryAttempted = ledgerService.attemptLedgerRecovery(
                entryId, accountId, transactionId, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic ledger recovery attempted: entryId={}, accountId={}, transactionId={}",
                    entryId, accountId, transactionId);

                kafkaTemplate.send("ledger-recovery-queue", Map.of(
                    "entryId", entryId != null ? entryId : "unknown",
                    "accountId", accountId != null ? accountId : "unknown",
                    "transactionId", transactionId != null ? transactionId : "unknown",
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                log.warn("Automatic ledger recovery not possible: entryId={}", entryId);

                notificationService.sendLedgerAlert(
                    "Manual Ledger Recovery Required",
                    String.format("Ledger entry %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", entryId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling ledger recovery: entryId={}, error={}",
                entryId, e.getMessage());
        }
    }

    private void assessBalanceIntegrityImpact(String accountId, String amount, String entryType,
                                            Object originalMessage, String messageId) {
        try {
            if (isBalanceAffectingEntry(entryType)) {
                log.warn("Balance-affecting ledger entry failure: accountId={}, entryType={}, amount={}",
                    accountId, entryType, amount);

                notificationService.sendBalanceIntegrityAlert(
                    "CRITICAL: Balance Integrity Risk",
                    String.format("Balance-affecting ledger entry (type: %s, amount: %s) failed for account %s. " +
                        "This may cause balance discrepancies. " +
                        "Immediate balance verification and correction required.", entryType, amount, accountId),
                    "CRITICAL"
                );

                // Create balance integrity incident
                kafkaTemplate.send("balance-integrity-incidents", Map.of(
                    "accountId", accountId != null ? accountId : "unknown",
                    "entryType", entryType,
                    "amount", amount,
                    "incidentType", "BALANCE_AFFECTING_ENTRY_FAILURE",
                    "balanceRisk", true,
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing balance integrity impact: accountId={}, error={}",
                accountId, e.getMessage());
        }
    }

    private void assessRegulatoryLedgerImpact(String entryId, String entryType, String amount,
                                            Object originalMessage, String messageId) {
        try {
            if (isRegulatoryReportableEntry(entryType, amount)) {
                log.warn("Regulatory reportable ledger entry failure: entryId={}, type={}, amount={}",
                    entryId, entryType, amount);

                notificationService.sendRegulatoryAlert(
                    "CRITICAL: Regulatory Ledger Entry Failed",
                    String.format("Regulatory reportable ledger entry %s (type: %s, amount: %s) failed. " +
                        "This may impact regulatory reporting and compliance. " +
                        "Immediate compliance team review required.", entryId, entryType, amount),
                    "CRITICAL"
                );

                // Create regulatory compliance incident
                kafkaTemplate.send("regulatory-compliance-incidents", Map.of(
                    "entryId", entryId != null ? entryId : "unknown",
                    "entryType", entryType,
                    "amount", amount,
                    "incidentType", "REGULATORY_LEDGER_ENTRY_FAILURE",
                    "reportingImpact", true,
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing regulatory ledger impact: entryId={}, error={}",
                entryId, e.getMessage());
        }
    }

    private void triggerManualLedgerReview(String entryId, String accountId, String transactionId,
                                         String entryType, String amount, String messageId) {
        try {
            // All ledger DLQ requires manual review due to financial integrity impact
            kafkaTemplate.send("manual-ledger-review-queue", Map.of(
                "entryId", entryId != null ? entryId : "unknown",
                "accountId", accountId != null ? accountId : "unknown",
                "transactionId", transactionId != null ? transactionId : "unknown",
                "entryType", entryType,
                "amount", amount,
                "reviewReason", "CORE_BANKING_LEDGER_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "requiresIntegrityCheck", true,
                "balanceImpact", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual ledger review for DLQ: entryId={}, accountId={}", entryId, accountId);
        } catch (Exception e) {
            log.error("Error triggering manual ledger review: entryId={}, error={}",
                entryId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleCoreBankingLedgerDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                  int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String entryId = extractEntryId(originalMessage);
        String accountId = extractAccountId(originalMessage);
        String amount = extractAmount(originalMessage);

        // EMERGENCY situation - ledger circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Core Banking Ledger DLQ Circuit Breaker",
                String.format("CRITICAL SYSTEM FAILURE: Core banking ledger DLQ circuit breaker triggered " +
                    "for entry %s (account %s, amount %s). " +
                    "This represents complete failure of ledger processing and financial data integrity. " +
                    "IMMEDIATE C-LEVEL, TECHNOLOGY, FINANCIAL, AND OPERATIONS ESCALATION REQUIRED.",
                    entryId, accountId, amount)
            );

            // Mark as emergency financial operations issue
            ledgerService.markEmergencyLedgerIssue(entryId, accountId, "CIRCUIT_BREAKER_LEDGER_DLQ");

        } catch (Exception e) {
            log.error("Error in core banking ledger DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for ledger classification
    private boolean isHighValueEntry(String amount) {
        try {
            if (amount != null) {
                BigDecimal entryAmount = new BigDecimal(amount);
                return entryAmount.compareTo(new BigDecimal("10000")) >= 0; // $10,000+
            }
        } catch (Exception e) {
            log.debug("Could not parse entry amount: {}", amount);
        }
        return false;
    }

    private boolean isRegulatoryEntry(String entryType) {
        return entryType != null && (
            entryType.contains("REGULATORY") || entryType.contains("COMPLIANCE") ||
            entryType.contains("CTR") || entryType.contains("SAR")
        );
    }

    private boolean isBalanceAffectingEntry(String entryType) {
        return entryType != null && (
            entryType.contains("DEBIT") || entryType.contains("CREDIT") ||
            entryType.contains("BALANCE") || entryType.contains("ADJUSTMENT")
        );
    }

    private boolean isRegulatoryReportableEntry(String entryType, String amount) {
        return isRegulatoryEntry(entryType) || isHighValueEntry(amount);
    }

    // Data extraction helper methods
    private String extractEntryId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object entryId = messageMap.get("entryId");
                if (entryId == null) entryId = messageMap.get("id");
                if (entryId == null) entryId = messageMap.get("ledgerEntryId");
                return entryId != null ? entryId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract entryId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAccountId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object accountId = messageMap.get("accountId");
                if (accountId == null) accountId = messageMap.get("debitAccountId");
                if (accountId == null) accountId = messageMap.get("creditAccountId");
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
                if (amount == null) amount = messageMap.get("entryAmount");
                return amount != null ? amount.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractEntryType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("entryType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract entryType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractEntryStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("entryStatus");
                if (status == null) status = messageMap.get("status");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract entryStatus from message: {}", e.getMessage());
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
}