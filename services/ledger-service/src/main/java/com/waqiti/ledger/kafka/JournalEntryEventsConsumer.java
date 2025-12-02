package com.waqiti.ledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.ledger.service.JournalEntryService;
import com.waqiti.ledger.service.LedgerBalanceService;
import com.waqiti.ledger.service.ReconciliationService;
import com.waqiti.common.exception.LedgerProcessingException;
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
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Journal Entry Events
 * Handles double-entry bookkeeping, balance updates, and financial reconciliation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JournalEntryEventsConsumer {
    
    private final JournalEntryService journalService;
    private final LedgerBalanceService balanceService;
    private final ReconciliationService reconciliationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"journal-entry-events", "journal-entry-created", "balance-updated", "reconciliation-required"},
        groupId = "ledger-service-journal-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000)
    )
    @Transactional
    public void handleJournalEntryEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID journalEntryId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            journalEntryId = UUID.fromString((String) event.get("journalEntryId"));
            eventType = (String) event.get("eventType");
            String transactionId = (String) event.get("transactionId");
            String accountCode = (String) event.get("accountCode");
            BigDecimal debitAmount = new BigDecimal((String) event.get("debitAmount"));
            BigDecimal creditAmount = new BigDecimal((String) event.get("creditAmount"));
            String currency = (String) event.get("currency");
            String description = (String) event.get("description");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing journal entry event - JournalEntryId: {}, Type: {}, Account: {}, Debit: {}, Credit: {}", 
                    journalEntryId, eventType, accountCode, debitAmount, creditAmount);
            
            switch (eventType) {
                case "JOURNAL_ENTRY_CREATED":
                    journalService.createJournalEntry(journalEntryId, transactionId, accountCode,
                            debitAmount, creditAmount, currency, description, timestamp);
                    break;
                case "BALANCE_UPDATED":
                    balanceService.updateAccountBalance(accountCode, debitAmount, creditAmount,
                            currency, timestamp);
                    break;
                case "RECONCILIATION_REQUIRED":
                    reconciliationService.initiateReconciliation(journalEntryId, accountCode,
                            timestamp);
                    break;
                default:
                    journalService.processGenericJournalEvent(journalEntryId, eventType, event, timestamp);
            }
            
            // Validate double-entry principle
            journalService.validateDoubleEntry(journalEntryId, debitAmount, creditAmount, timestamp);
            
            auditService.auditFinancialEvent(
                    "JOURNAL_ENTRY_EVENT_PROCESSED",
                    accountCode,
                    String.format("Journal entry event processed - Type: %s, Debit: %s, Credit: %s %s", 
                            eventType, debitAmount, creditAmount, currency),
                    Map.of(
                            "journalEntryId", journalEntryId.toString(),
                            "eventType", eventType,
                            "transactionId", transactionId,
                            "accountCode", accountCode,
                            "debitAmount", debitAmount.toString(),
                            "creditAmount", creditAmount.toString(),
                            "currency", currency,
                            "description", description
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed journal entry event - JournalEntryId: {}, EventType: {}", 
                    journalEntryId, eventType);
            
        } catch (Exception e) {
            log.error("Journal entry event processing failed - JournalEntryId: {}, Error: {}", 
                    journalEntryId, e.getMessage(), e);
            throw new LedgerProcessingException("Journal entry event processing failed", e);
        }
    }
}