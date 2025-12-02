package com.waqiti.ledger.kafka;

import com.waqiti.common.events.TransactionReversalEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.TransactionReversal;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.TransactionReversalRepository;
import com.waqiti.ledger.service.BalanceCalculationService;
import com.waqiti.ledger.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * CRITICAL: Transaction Reversal Event Consumer
 * 
 * Handles transaction reversals to maintain balanced books and financial integrity.
 * This consumer is ESSENTIAL for:
 * - Maintaining double-entry bookkeeping accuracy
 * - Ensuring financial statements balance
 * - Handling payment failures and refunds
 * - Regulatory compliance for financial reporting
 * 
 * WITHOUT THIS: Books won't balance, financial reports will be incorrect,
 * and auditors will flag major discrepancies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionReversalEventConsumer {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final TransactionReversalRepository reversalRepository;
    private final BalanceCalculationService balanceService;
    private final IdempotencyService idempotencyService;
    private final AuditTrailService auditService;
    private final MetricsService metricsService;

    private static final String CONSUMER_GROUP = "ledger-transaction-reversal";

    @KafkaListener(
        topics = "transaction-reversal-events",
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void handleTransactionReversal(
            @Payload TransactionReversalEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String idempotencyKey = generateIdempotencyKey(event);
        
        log.info("Processing transaction reversal: originalTxId={}, amount={}, reason={}", 
                event.getOriginalTransactionId(), event.getAmount(), event.getReversalReason());
        
        metricsService.incrementCounter("ledger.transaction_reversal.received");
        
        try {
            // Check idempotency to prevent duplicate reversals
            if (idempotencyService.isAlreadyProcessed(idempotencyKey)) {
                log.info("Transaction reversal already processed: {}", event.getOriginalTransactionId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Find original transaction entries
            Optional<LedgerEntry> originalDebitEntry = ledgerEntryRepository
                .findByTransactionIdAndEntryType(event.getOriginalTransactionId(), LedgerEntry.EntryType.DEBIT);
            
            Optional<LedgerEntry> originalCreditEntry = ledgerEntryRepository
                .findByTransactionIdAndEntryType(event.getOriginalTransactionId(), LedgerEntry.EntryType.CREDIT);
            
            if (!originalDebitEntry.isPresent() || !originalCreditEntry.isPresent()) {
                log.error("Original transaction not found for reversal: {}", event.getOriginalTransactionId());
                throw new IllegalStateException("Cannot reverse non-existent transaction");
            }
            
            // Validate reversal is allowed
            validateReversalAllowed(originalDebitEntry.get(), originalCreditEntry.get(), event);
            
            // Create reversal entries (opposite of original)
            LedgerEntry reversalDebitEntry = createReversalEntry(
                originalCreditEntry.get(), // Credit becomes debit
                LedgerEntry.EntryType.DEBIT,
                event
            );
            
            LedgerEntry reversalCreditEntry = createReversalEntry(
                originalDebitEntry.get(), // Debit becomes credit
                LedgerEntry.EntryType.CREDIT,
                event
            );
            
            // Update account balances
            updateAccountBalances(reversalDebitEntry, reversalCreditEntry);
            
            // Save reversal entries
            ledgerEntryRepository.save(reversalDebitEntry);
            ledgerEntryRepository.save(reversalCreditEntry);
            
            // Create reversal record for audit
            TransactionReversal reversal = createReversalRecord(event, reversalDebitEntry, reversalCreditEntry);
            reversalRepository.save(reversal);
            
            // Mark original entries as reversed
            markOriginalEntriesAsReversed(originalDebitEntry.get(), originalCreditEntry.get(), reversal.getId());
            
            // Create audit trail
            auditService.logTransactionReversal(
                event.getOriginalTransactionId(),
                reversal.getId(),
                event.getAmount(),
                event.getReversalReason()
            );
            
            // Mark as processed
            idempotencyService.markAsProcessed(idempotencyKey, reversal.getId());
            
            // Update metrics
            metricsService.incrementCounter("ledger.transaction_reversal.success");
            metricsService.recordGauge("ledger.reversal.amount", event.getAmount().doubleValue());
            
            log.info("Transaction reversal completed: originalTxId={}, reversalId={}", 
                    event.getOriginalTransactionId(), reversal.getId());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process transaction reversal: {}", event.getOriginalTransactionId(), e);
            metricsService.incrementCounter("ledger.transaction_reversal.error");
            
            // Create alert for finance team
            createReversalFailureAlert(event, e);
            
            throw e;
        }
    }

    private void validateReversalAllowed(LedgerEntry debitEntry, LedgerEntry creditEntry, 
                                         TransactionReversalEvent event) {
        // Check if already reversed
        if (debitEntry.isReversed() || creditEntry.isReversed()) {
            throw new IllegalStateException("Transaction already reversed: " + event.getOriginalTransactionId());
        }
        
        // Check reversal amount matches original
        if (debitEntry.getAmount().compareTo(event.getAmount()) != 0) {
            throw new IllegalArgumentException(String.format(
                "Reversal amount %s doesn't match original amount %s",
                event.getAmount(), debitEntry.getAmount()
            ));
        }
        
        // Check if reversal is within allowed time window (e.g., 180 days)
        long daysSinceTransaction = java.time.temporal.ChronoUnit.DAYS.between(
            debitEntry.getTransactionDate(), LocalDateTime.now()
        );
        
        if (daysSinceTransaction > 180) {
            log.warn("Attempting to reverse old transaction: {} days old", daysSinceTransaction);
            // Could throw exception or require special approval
        }
    }

    private LedgerEntry createReversalEntry(LedgerEntry originalEntry, 
                                           LedgerEntry.EntryType newType,
                                           TransactionReversalEvent event) {
        return LedgerEntry.builder()
            .ledgerEntryId(UUID.randomUUID())
            .accountId(originalEntry.getAccountId())
            .transactionId(event.getReversalTransactionId())
            .entryType(newType)
            .amount(event.getAmount())
            .currency(originalEntry.getCurrency())
            .description("REVERSAL: " + event.getReversalReason() + " (Original: " + 
                        event.getOriginalTransactionId() + ")")
            .transactionDate(LocalDateTime.now())
            .valueDate(LocalDateTime.now())
            .referenceNumber("REV-" + event.getReversalTransactionId())
            .reversalOf(event.getOriginalTransactionId())
            .metadata(event.getMetadata())
            .posted(true)
            .reversed(false)
            .build();
    }

    private void updateAccountBalances(LedgerEntry debitEntry, LedgerEntry creditEntry) {
        // Update debit account (decrease balance)
        Account debitAccount = accountRepository.findById(debitEntry.getAccountId())
            .orElseThrow(() -> new IllegalStateException("Debit account not found"));
        
        BigDecimal newDebitBalance = debitAccount.getCurrentBalance().subtract(debitEntry.getAmount());
        debitAccount.setCurrentBalance(newDebitBalance);
        debitAccount.setLastTransactionDate(LocalDateTime.now());
        accountRepository.save(debitAccount);
        
        // Update credit account (increase balance)
        Account creditAccount = accountRepository.findById(creditEntry.getAccountId())
            .orElseThrow(() -> new IllegalStateException("Credit account not found"));
        
        BigDecimal newCreditBalance = creditAccount.getCurrentBalance().add(creditEntry.getAmount());
        creditAccount.setCurrentBalance(newCreditBalance);
        creditAccount.setLastTransactionDate(LocalDateTime.now());
        accountRepository.save(creditAccount);
        
        log.debug("Updated account balances - Debit account {} new balance: {}, Credit account {} new balance: {}",
                debitAccount.getAccountCode(), newDebitBalance,
                creditAccount.getAccountCode(), newCreditBalance);
    }

    private TransactionReversal createReversalRecord(TransactionReversalEvent event,
                                                    LedgerEntry debitEntry,
                                                    LedgerEntry creditEntry) {
        return TransactionReversal.builder()
            .id(UUID.randomUUID())
            .originalTransactionId(event.getOriginalTransactionId())
            .reversalTransactionId(event.getReversalTransactionId())
            .debitEntryId(debitEntry.getLedgerEntryId())
            .creditEntryId(creditEntry.getLedgerEntryId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .reversalReason(event.getReversalReason())
            .reversalType(event.getReversalType())
            .initiatedBy(event.getInitiatedBy())
            .approvedBy(event.getApprovedBy())
            .reversalDate(LocalDateTime.now())
            .status(TransactionReversal.ReversalStatus.COMPLETED)
            .build();
    }

    private void markOriginalEntriesAsReversed(LedgerEntry debitEntry, LedgerEntry creditEntry, 
                                              UUID reversalId) {
        debitEntry.setReversed(true);
        debitEntry.setReversalId(reversalId);
        debitEntry.setReversalDate(LocalDateTime.now());
        ledgerEntryRepository.save(debitEntry);
        
        creditEntry.setReversed(true);
        creditEntry.setReversalId(reversalId);
        creditEntry.setReversalDate(LocalDateTime.now());
        ledgerEntryRepository.save(creditEntry);
    }

    private void createReversalFailureAlert(TransactionReversalEvent event, Exception error) {
        log.error("CRITICAL: Transaction reversal failed - Books may be out of balance!");
        log.error("Original Transaction: {}, Amount: {}, Reason: {}", 
                event.getOriginalTransactionId(), event.getAmount(), event.getReversalReason());
        
        // Send alert to finance team
        auditService.createCriticalAlert(
            "TRANSACTION_REVERSAL_FAILURE",
            String.format("Failed to reverse transaction %s - Manual intervention required", 
                         event.getOriginalTransactionId()),
            error.getMessage()
        );
    }

    private String generateIdempotencyKey(TransactionReversalEvent event) {
        return String.format("reversal-%s-%s", 
                event.getOriginalTransactionId(), 
                event.getReversalTransactionId());
    }
}