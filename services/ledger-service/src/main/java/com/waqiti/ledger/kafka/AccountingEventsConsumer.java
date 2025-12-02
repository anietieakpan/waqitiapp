package com.waqiti.ledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.ledger.service.GeneralLedgerService;
import com.waqiti.ledger.service.AccountingService;
import com.waqiti.ledger.service.JournalEntryService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for accounting-events topic
 * 
 * CRITICAL FINANCIAL LEDGER CONSUMER
 * Processes accounting events for double-entry bookkeeping, general ledger updates,
 * and financial reporting.
 * 
 * This consumer ensures:
 * - Accurate financial records
 * - Double-entry bookkeeping compliance
 * - Audit trail for all financial transactions
 * - Reconciliation support
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountingEventsConsumer {
    
    private final GeneralLedgerService generalLedgerService;
    private final AccountingService accountingService;
    private final JournalEntryService journalEntryService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = "accounting-events",
        groupId = "ledger-service-accounting-events-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleAccountingEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("CRITICAL ACCOUNTING: Processing accounting event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID eventId = null;
        String eventType = null;
        
        try {
            // Parse accounting event
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            eventId = UUID.fromString((String) event.get("eventId"));
            eventType = (String) event.get("eventType");
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            String accountingPeriod = (String) event.get("accountingPeriod");
            LocalDateTime eventTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing accounting event - EventId: {}, Type: {}, Period: {}, TransactionId: {}", 
                    eventId, eventType, accountingPeriod, transactionId);
            
            // Process based on event type
            switch (eventType) {
                case "JOURNAL_ENTRY" -> processJournalEntry(event, eventId, accountingPeriod);
                case "REVENUE_RECOGNITION" -> processRevenueRecognition(event, eventId, accountingPeriod);
                case "EXPENSE_ACCRUAL" -> processExpenseAccrual(event, eventId, accountingPeriod);
                case "ASSET_DEPRECIATION" -> processAssetDepreciation(event, eventId, accountingPeriod);
                case "PERIOD_CLOSE" -> processPeriodClose(event, eventId, accountingPeriod);
                case "BALANCE_ADJUSTMENT" -> processBalanceAdjustment(event, eventId, accountingPeriod);
                case "RECONCILIATION" -> processReconciliation(event, eventId, accountingPeriod);
                case "TAX_ACCRUAL" -> processTaxAccrual(event, eventId, accountingPeriod);
                default -> {
                    log.warn("Unknown accounting event type: {}", eventType);
                    processGenericAccountingEvent(event, eventId, eventType, accountingPeriod);
                }
            }
            
            // Validate ledger balance
            validateLedgerBalance(eventId);
            
            // Audit the accounting event
            auditAccountingEvent(eventId, eventType, accountingPeriod, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Accounting event processed successfully - EventId: {}, Type: {}, ProcessingTime: {}ms", 
                    eventId, eventType, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Accounting event processing failed - EventId: {}, Type: {}, Error: {}", 
                    eventId, eventType, e.getMessage(), e);
            
            if (eventId != null) {
                handleAccountingEventFailure(eventId, eventType, e);
            }
            
            throw new RuntimeException("Accounting event processing failed", e);
        }
    }
    
    private void processJournalEntry(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) event.get("entries");
            String description = (String) event.get("description");
            String reference = (String) event.get("reference");
            
            log.info("Processing journal entry - EventId: {}, Entries: {}", eventId, entries.size());
            
            // Validate double-entry bookkeeping (debits = credits)
            validateDoubleEntry(entries);
            
            // Create journal entry
            UUID journalEntryId = journalEntryService.createJournalEntry(
                    eventId, accountingPeriod, description, reference, entries);
            
            // Post to general ledger
            generalLedgerService.postJournalEntry(journalEntryId, entries);
            
            log.info("Journal entry posted - EventId: {}, JournalEntryId: {}", eventId, journalEntryId);
            
        } catch (Exception e) {
            log.error("Failed to process journal entry - EventId: {}", eventId, e);
            throw new RuntimeException("Journal entry processing failed", e);
        }
    }
    
    private void processRevenueRecognition(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            BigDecimal revenueAmount = new BigDecimal(event.get("revenueAmount").toString());
            String revenueType = (String) event.get("revenueType");
            String customerId = (String) event.get("customerId");
            String contractId = (String) event.get("contractId");
            
            log.info("Processing revenue recognition - EventId: {}, Amount: {}, Type: {}", 
                    eventId, revenueAmount, revenueType);
            
            accountingService.recognizeRevenue(eventId, accountingPeriod, revenueAmount, 
                    revenueType, customerId, contractId);
            
            log.info("Revenue recognized - EventId: {}, Amount: {}", eventId, revenueAmount);
            
        } catch (Exception e) {
            log.error("Failed to process revenue recognition - EventId: {}", eventId, e);
            throw new RuntimeException("Revenue recognition failed", e);
        }
    }
    
    private void processExpenseAccrual(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            BigDecimal expenseAmount = new BigDecimal(event.get("expenseAmount").toString());
            String expenseCategory = (String) event.get("expenseCategory");
            String vendorId = (String) event.get("vendorId");
            LocalDateTime accrualDate = LocalDateTime.parse((String) event.get("accrualDate"));
            
            log.info("Processing expense accrual - EventId: {}, Amount: {}, Category: {}", 
                    eventId, expenseAmount, expenseCategory);
            
            accountingService.accrueExpense(eventId, accountingPeriod, expenseAmount, 
                    expenseCategory, vendorId, accrualDate);
            
            log.info("Expense accrued - EventId: {}, Amount: {}", eventId, expenseAmount);
            
        } catch (Exception e) {
            log.error("Failed to process expense accrual - EventId: {}", eventId, e);
            throw new RuntimeException("Expense accrual failed", e);
        }
    }
    
    private void processAssetDepreciation(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            String assetId = (String) event.get("assetId");
            BigDecimal depreciationAmount = new BigDecimal(event.get("depreciationAmount").toString());
            String depreciationMethod = (String) event.get("depreciationMethod");
            
            log.info("Processing asset depreciation - EventId: {}, AssetId: {}, Amount: {}", 
                    eventId, assetId, depreciationAmount);
            
            accountingService.recordDepreciation(eventId, accountingPeriod, assetId, 
                    depreciationAmount, depreciationMethod);
            
            log.info("Depreciation recorded - EventId: {}, AssetId: {}", eventId, assetId);
            
        } catch (Exception e) {
            log.error("Failed to process asset depreciation - EventId: {}", eventId, e);
            throw new RuntimeException("Asset depreciation failed", e);
        }
    }
    
    private void processPeriodClose(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            String closeType = (String) event.get("closeType"); // MONTH_END, QUARTER_END, YEAR_END
            boolean finalClose = Boolean.parseBoolean(event.getOrDefault("finalClose", "false").toString());
            
            log.info("Processing period close - EventId: {}, Period: {}, Type: {}, Final: {}", 
                    eventId, accountingPeriod, closeType, finalClose);
            
            accountingService.closePeriod(eventId, accountingPeriod, closeType, finalClose);
            
            log.info("Period closed - EventId: {}, Period: {}", eventId, accountingPeriod);
            
        } catch (Exception e) {
            log.error("Failed to process period close - EventId: {}, Period: {}", eventId, accountingPeriod, e);
            throw new RuntimeException("Period close failed", e);
        }
    }
    
    private void processBalanceAdjustment(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            String accountCode = (String) event.get("accountCode");
            BigDecimal adjustmentAmount = new BigDecimal(event.get("adjustmentAmount").toString());
            String adjustmentReason = (String) event.get("adjustmentReason");
            String approvedBy = (String) event.get("approvedBy");
            
            log.info("Processing balance adjustment - EventId: {}, Account: {}, Amount: {}", 
                    eventId, accountCode, adjustmentAmount);
            
            accountingService.adjustBalance(eventId, accountingPeriod, accountCode, 
                    adjustmentAmount, adjustmentReason, approvedBy);
            
            log.info("Balance adjusted - EventId: {}, Account: {}", eventId, accountCode);
            
        } catch (Exception e) {
            log.error("Failed to process balance adjustment - EventId: {}", eventId, e);
            throw new RuntimeException("Balance adjustment failed", e);
        }
    }
    
    private void processReconciliation(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            String accountCode = (String) event.get("accountCode");
            BigDecimal expectedBalance = new BigDecimal(event.get("expectedBalance").toString());
            BigDecimal actualBalance = new BigDecimal(event.get("actualBalance").toString());
            BigDecimal variance = expectedBalance.subtract(actualBalance);
            
            log.info("Processing reconciliation - EventId: {}, Account: {}, Variance: {}", 
                    eventId, accountCode, variance);
            
            accountingService.reconcileAccount(eventId, accountingPeriod, accountCode, 
                    expectedBalance, actualBalance, variance);
            
            if (variance.abs().compareTo(new BigDecimal("0.01")) > 0) {
                log.warn("Reconciliation variance detected - EventId: {}, Account: {}, Variance: {}", 
                        eventId, accountCode, variance);
            }
            
            log.info("Reconciliation completed - EventId: {}, Account: {}", eventId, accountCode);
            
        } catch (Exception e) {
            log.error("Failed to process reconciliation - EventId: {}", eventId, e);
            throw new RuntimeException("Reconciliation failed", e);
        }
    }
    
    private void processTaxAccrual(Map<String, Object> event, UUID eventId, String accountingPeriod) {
        try {
            BigDecimal taxAmount = new BigDecimal(event.get("taxAmount").toString());
            String taxType = (String) event.get("taxType");
            String jurisdiction = (String) event.get("jurisdiction");
            
            log.info("Processing tax accrual - EventId: {}, Amount: {}, Type: {}, Jurisdiction: {}", 
                    eventId, taxAmount, taxType, jurisdiction);
            
            accountingService.accrueTax(eventId, accountingPeriod, taxAmount, taxType, jurisdiction);
            
            log.info("Tax accrued - EventId: {}, Amount: {}", eventId, taxAmount);
            
        } catch (Exception e) {
            log.error("Failed to process tax accrual - EventId: {}", eventId, e);
            throw new RuntimeException("Tax accrual failed", e);
        }
    }
    
    private void processGenericAccountingEvent(Map<String, Object> event, UUID eventId, 
                                              String eventType, String accountingPeriod) {
        try {
            log.info("Processing generic accounting event - EventId: {}, Type: {}", eventId, eventType);
            
            accountingService.processGenericEvent(eventId, eventType, accountingPeriod, event);
            
            log.info("Generic accounting event processed - EventId: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process generic accounting event - EventId: {}", eventId, e);
            throw new RuntimeException("Generic accounting event processing failed", e);
        }
    }
    
    private void validateDoubleEntry(List<Map<String, Object>> entries) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        
        for (Map<String, Object> entry : entries) {
            String entryType = (String) entry.get("entryType");
            BigDecimal amount = new BigDecimal(entry.get("amount").toString());
            
            if ("DEBIT".equals(entryType)) {
                totalDebits = totalDebits.add(amount);
            } else if ("CREDIT".equals(entryType)) {
                totalCredits = totalCredits.add(amount);
            }
        }
        
        if (totalDebits.compareTo(totalCredits) != 0) {
            log.error("Double-entry validation failed - Debits: {}, Credits: {}", totalDebits, totalCredits);
            throw new IllegalArgumentException("Double-entry bookkeeping violation: debits != credits");
        }
        
        log.debug("Double-entry validation passed - Debits: {}, Credits: {}", totalDebits, totalCredits);
    }
    
    private void validateLedgerBalance(UUID eventId) {
        try {
            boolean isBalanced = generalLedgerService.validateLedgerBalance();
            
            if (!isBalanced) {
                log.error("CRITICAL: Ledger balance validation failed after event - EventId: {}", eventId);
                throw new IllegalStateException("Ledger is not balanced");
            }
            
            log.debug("Ledger balance validated - EventId: {}", eventId);
            
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ledger balance validation error - EventId: {}", eventId, e);
        }
    }
    
    private void auditAccountingEvent(UUID eventId, String eventType, String accountingPeriod, 
                                     LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "ACCOUNTING_EVENT_PROCESSED",
                    eventId,
                    accountingPeriod,
                    Map.of(
                            "eventId", eventId.toString(),
                            "eventType", eventType,
                            "accountingPeriod", accountingPeriod,
                            "processingTimeMs", processingTimeMs,
                            "description", String.format("Accounting event processed - EventId: %s, Type: %s, Period: %s",
                                    eventId, eventType, accountingPeriod)
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit accounting event - EventId: {}", eventId, e);
        }
    }
    
    private void handleAccountingEventFailure(UUID eventId, String eventType, Exception error) {
        try {
            accountingService.handleEventFailure(eventId, eventType, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "ACCOUNTING_EVENT_FAILED",
                    eventId != null ? eventId : UUID.randomUUID(),
                    "UNKNOWN",
                    Map.of(
                            "eventId", eventId != null ? eventId.toString() : "UNKNOWN",
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage() != null ? error.getMessage() : "Unknown error",
                            "description", "Accounting event processing failed: " + error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle accounting event failure - EventId: {}", eventId, e);
        }
    }
    
    @KafkaListener(
        topics = "accounting-events.DLQ",
        groupId = "ledger-service-accounting-events-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Accounting event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID eventId = event.containsKey("eventId") ? 
                    UUID.fromString((String) event.get("eventId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Accounting event failed permanently - EventId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    eventId, eventType);
            
            if (eventId != null) {
                accountingService.markEventForManualReview(eventId, eventType, "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse accounting event DLQ message: {}", eventJson, e);
        }
    }
}