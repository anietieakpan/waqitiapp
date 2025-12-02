package com.waqiti.reconciliation.kafka;

import com.waqiti.common.events.WalletDebitedEvent;
import com.waqiti.common.events.ReconciliationMismatchEvent;
import com.waqiti.reconciliation.domain.ReconciliationEntry;
import com.waqiti.reconciliation.domain.ReconciliationStatus;
import com.waqiti.reconciliation.domain.MismatchType;
import com.waqiti.reconciliation.service.ReconciliationService;
import com.waqiti.reconciliation.service.AlertService;
import com.waqiti.reconciliation.exception.ReconciliationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX: Consumer for WalletDebitedEvent
 * This was missing and causing reconciliation failures
 * 
 * Responsibilities:
 * - Reconcile wallet debits with ledger entries
 * - Detect balance mismatches
 * - Alert on discrepancies
 * - Maintain audit trail
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletDebitedEventConsumer {
    
    private final ReconciliationService reconciliationService;
    private final AlertService alertService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String MISMATCH_TOPIC = "reconciliation-mismatch-events";
    private static final String DLQ_TOPIC = "wallet-debited-events-dlq";
    private static final BigDecimal MISMATCH_THRESHOLD = new BigDecimal("0.01"); // 1 cent threshold
    
    /**
     * Processes wallet debited events for reconciliation
     * 
     * CRITICAL: This ensures wallet balances match ledger records
     * Any mismatch triggers immediate alerts to prevent financial loss
     * 
     * @param event The wallet debited event
     * @param partition Kafka partition
     * @param offset Message offset
     * @param acknowledgment Manual acknowledgment
     */
    @KafkaListener(
        topics = "wallet-debited-events",
        groupId = "reconciliation-service-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @Transactional
    public void handleWalletDebited(
            @Payload WalletDebitedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("wallet-debit-%s-p%d-o%d",
            event.getWalletId(), partition, offset);
        
        log.info("Reconciling wallet debit: walletId={}, transactionId={}, amount={}, correlation={}",
            event.getWalletId(), event.getTransactionId(), event.getAmount(), correlationId);
        
        try {
            // Check for duplicate processing
            if (reconciliationService.isEventProcessed(event.getEventId())) {
                log.debug("Event already processed: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Validate event
            validateEvent(event);
            
            // Create reconciliation entry
            ReconciliationEntry entry = ReconciliationEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(event.getTransactionId())
                .walletId(event.getWalletId())
                .eventType("WALLET_DEBIT")
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .walletBalanceBefore(event.getBalanceBefore())
                .walletBalanceAfter(event.getBalanceAfter())
                .eventTimestamp(event.getTimestamp())
                .correlationId(correlationId)
                .status(ReconciliationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
            
            // Perform reconciliation
            ReconciliationResult result = reconciliationService.reconcile(entry);
            
            // Update entry with results
            entry.setStatus(result.getStatus());
            entry.setLedgerBalance(result.getLedgerBalance());
            entry.setDiscrepancy(result.getDiscrepancy());
            entry.setReconciledAt(LocalDateTime.now());
            
            reconciliationService.saveEntry(entry);
            
            // Handle mismatches
            if (result.hasMismatch()) {
                handleMismatch(event, result, correlationId);
            } else {
                log.info("Reconciliation successful for transaction: {}", event.getTransactionId());
            }
            
            // Mark event as processed
            reconciliationService.markEventProcessed(event.getEventId());
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to reconcile wallet debit: walletId={}, transactionId={}, error={}",
                event.getWalletId(), event.getTransactionId(), e.getMessage(), e);
            
            // Send to DLQ
            sendToDeadLetterQueue(event, e);
            
            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
            
            // Alert operations team
            alertService.sendOperationalAlert(
                "RECONCILIATION_FAILURE",
                String.format("Failed to reconcile wallet %s for transaction %s: %s",
                    event.getWalletId(), event.getTransactionId(), e.getMessage()),
                AlertService.Priority.HIGH
            );
        }
    }
    
    /**
     * Handles reconciliation mismatches
     */
    private void handleMismatch(WalletDebitedEvent event, ReconciliationResult result, String correlationId) {
        BigDecimal discrepancy = result.getDiscrepancy().abs();
        
        // Determine severity
        MismatchSeverity severity;
        if (discrepancy.compareTo(new BigDecimal("1000")) > 0) {
            severity = MismatchSeverity.CRITICAL;
        } else if (discrepancy.compareTo(new BigDecimal("100")) > 0) {
            severity = MismatchSeverity.HIGH;
        } else if (discrepancy.compareTo(MISMATCH_THRESHOLD) > 0) {
            severity = MismatchSeverity.MEDIUM;
        } else {
            severity = MismatchSeverity.LOW;
        }
        
        log.error("RECONCILIATION MISMATCH DETECTED: walletId={}, transactionId={}, " +
            "expectedBalance={}, actualBalance={}, discrepancy={}, severity={}",
            event.getWalletId(), event.getTransactionId(),
            result.getExpectedBalance(), result.getActualBalance(),
            discrepancy, severity);
        
        // Create mismatch event
        ReconciliationMismatchEvent mismatchEvent = ReconciliationMismatchEvent.builder()
            .mismatchId(UUID.randomUUID())
            .walletId(event.getWalletId())
            .transactionId(event.getTransactionId())
            .mismatchType(result.getMismatchType())
            .expectedBalance(result.getExpectedBalance())
            .actualBalance(result.getActualBalance())
            .discrepancyAmount(discrepancy)
            .currency(event.getCurrency())
            .severity(severity.name())
            .detectedAt(Instant.now())
            .correlationId(correlationId)
            .metadata(buildMismatchMetadata(event, result))
            .build();
        
        // Publish mismatch event
        kafkaTemplate.send(MISMATCH_TOPIC, mismatchEvent);
        
        // Send alerts based on severity
        if (severity == MismatchSeverity.CRITICAL || severity == MismatchSeverity.HIGH) {
            // Critical alert - immediate action required
            alertService.sendCriticalAlert(
                "CRITICAL_RECONCILIATION_MISMATCH",
                String.format("Critical mismatch detected for wallet %s: discrepancy=%s %s",
                    event.getWalletId(), discrepancy, event.getCurrency()),
                AlertService.Channel.ALL
            );
            
            // Page on-call engineer
            alertService.pageOnCall(
                String.format("Reconciliation mismatch >$%s detected", discrepancy)
            );
        } else {
            // Standard alert
            alertService.sendAlert(
                "RECONCILIATION_MISMATCH",
                String.format("Mismatch detected for wallet %s: discrepancy=%s %s",
                    event.getWalletId(), discrepancy, event.getCurrency()),
                AlertService.Priority.MEDIUM
            );
        }
        
        // Log to audit trail
        reconciliationService.auditMismatch(event.getWalletId(), event.getTransactionId(), result);
    }
    
    /**
     * Validates the event before processing
     */
    private void validateEvent(WalletDebitedEvent event) {
        if (event.getWalletId() == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Invalid debit amount: " + event.getAmount());
        }
        if (event.getBalanceAfter() == null || event.getBalanceBefore() == null) {
            throw new IllegalArgumentException("Balance information is required");
        }
        
        // Verify balance calculation
        BigDecimal expectedBalance = event.getBalanceBefore().subtract(event.getAmount());
        if (expectedBalance.compareTo(event.getBalanceAfter()) != 0) {
            throw new ReconciliationException(String.format(
                "Balance calculation mismatch in event: before=%s - amount=%s != after=%s",
                event.getBalanceBefore(), event.getAmount(), event.getBalanceAfter()
            ));
        }
    }
    
    /**
     * Builds metadata for mismatch tracking
     */
    private Map<String, Object> buildMismatchMetadata(WalletDebitedEvent event, ReconciliationResult result) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("walletBalanceBefore", event.getBalanceBefore());
        metadata.put("walletBalanceAfter", event.getBalanceAfter());
        metadata.put("debitAmount", event.getAmount());
        metadata.put("ledgerBalance", result.getLedgerBalance());
        metadata.put("ledgerEntries", result.getLedgerEntryIds());
        metadata.put("eventTimestamp", event.getTimestamp());
        metadata.put("reconciliationTimestamp", Instant.now());
        return metadata;
    }
    
    /**
     * Sends failed events to dead letter queue
     */
    private void sendToDeadLetterQueue(WalletDebitedEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalEvent", event);
            dlqMessage.put("errorMessage", error.getMessage());
            dlqMessage.put("errorClass", error.getClass().getName());
            dlqMessage.put("failedAt", Instant.now());
            dlqMessage.put("service", "reconciliation-service");
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Sent failed wallet debit event to DLQ: walletId={}, transactionId={}",
                event.getWalletId(), event.getTransactionId());
        } catch (Exception dlqError) {
            log.error("Failed to send event to DLQ", dlqError);
        }
    }
    
    /**
     * Reconciliation result from the service
     */
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationResult {
        private ReconciliationStatus status;
        private BigDecimal expectedBalance;
        private BigDecimal actualBalance;
        private BigDecimal ledgerBalance;
        private BigDecimal discrepancy;
        private MismatchType mismatchType;
        private List<UUID> ledgerEntryIds;
        
        public boolean hasMismatch() {
            return status == ReconciliationStatus.MISMATCH || 
                   (discrepancy != null && discrepancy.abs().compareTo(BigDecimal.ZERO) > 0);
        }
    }
    
    /**
     * Severity levels for mismatches
     */
    private enum MismatchSeverity {
        LOW,      // < $0.01
        MEDIUM,   // < $100
        HIGH,     // < $1000
        CRITICAL  // >= $1000
    }
}