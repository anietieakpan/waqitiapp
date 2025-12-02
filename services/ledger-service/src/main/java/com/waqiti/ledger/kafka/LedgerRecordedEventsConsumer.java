package com.waqiti.ledger.kafka;

import com.waqiti.common.events.LedgerRecordedEvent;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.service.DoubleEntryBookkeepingService;
import com.waqiti.ledger.service.BalanceCalculationService;
import com.waqiti.ledger.service.ReconciliationService;
import com.waqiti.ledger.metrics.LedgerMetricsService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class LedgerRecordedEventsConsumer {
    
    private final LedgerEntryRepository ledgerRepository;
    private final DoubleEntryBookkeepingService bookkeepingService;
    private final BalanceCalculationService balanceService;
    private final ReconciliationService reconciliationService;
    private final LedgerMetricsService metricsService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"ledger-recorded-events", "accounting-entries", "financial-transactions"},
        groupId = "ledger-recorded-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleLedgerRecordedEvent(
            @Payload LedgerRecordedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("ledger-%s-p%d-o%d", 
            event.getTransactionId(), partition, offset);
        
        log.info("Processing ledger recorded event: transactionId={}, amount={}, type={}",
            event.getTransactionId(), event.getAmount(), event.getTransactionType());
        
        try {
            switch (event.getTransactionType()) {
                case "PAYMENT":
                    recordPaymentTransaction(event, correlationId);
                    break;
                    
                case "REFUND":
                    recordRefundTransaction(event, correlationId);
                    break;
                    
                case "CHARGEBACK":
                    recordChargebackTransaction(event, correlationId);
                    break;
                    
                case "FEE":
                    recordFeeTransaction(event, correlationId);
                    break;
                    
                case "SETTLEMENT":
                    recordSettlementTransaction(event, correlationId);
                    break;
                    
                case "ADJUSTMENT":
                    recordAdjustmentTransaction(event, correlationId);
                    break;
                    
                case "TRANSFER":
                    recordTransferTransaction(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown transaction type: {}", event.getTransactionType());
                    break;
            }
            
            balanceService.updateAccountBalances(
                event.getFromAccountId(),
                event.getToAccountId(),
                event.getAmount()
            );
            
            reconciliationService.validateDoubleEntry(event.getTransactionId());
            
            metricsService.recordLedgerEntry(event.getTransactionType(), event.getAmount());
            
            auditService.logLedgerEvent("LEDGER_ENTRY_RECORDED", event.getTransactionId(),
                Map.of("transactionType", event.getTransactionType(), "amount", event.getAmount(),
                    "fromAccount", event.getFromAccountId(), "toAccount", event.getToAccountId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process ledger recorded event: {}", e.getMessage(), e);
            kafkaTemplate.send("ledger-recorded-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void recordPaymentTransaction(LedgerRecordedEvent event, String correlationId) {
        List<LedgerEntry> entries = bookkeepingService.createDoubleEntry(
            event.getTransactionId(),
            event.getFromAccountId(), // Customer account
            event.getToAccountId(),   // Merchant account
            event.getAmount(),
            "PAYMENT",
            correlationId
        );
        
        for (LedgerEntry entry : entries) {
            ledgerRepository.save(entry);
        }
        
        kafkaTemplate.send("balance-updates", Map.of(
            "transactionId", event.getTransactionId(),
            "fromAccountId", event.getFromAccountId(),
            "toAccountId", event.getToAccountId(),
            "amount", event.getAmount(),
            "transactionType", "PAYMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Payment transaction recorded: id={}, amount={}, from={}, to={}", 
            event.getTransactionId(), event.getAmount(), event.getFromAccountId(), event.getToAccountId());
    }
    
    private void recordRefundTransaction(LedgerRecordedEvent event, String correlationId) {
        List<LedgerEntry> entries = bookkeepingService.createDoubleEntry(
            event.getTransactionId(),
            event.getFromAccountId(), // Merchant account
            event.getToAccountId(),   // Customer account
            event.getAmount(),
            "REFUND",
            correlationId
        );
        
        for (LedgerEntry entry : entries) {
            ledgerRepository.save(entry);
        }
        
        reconciliationService.linkRefundToOriginalPayment(
            event.getTransactionId(),
            event.getOriginalTransactionId()
        );
        
        log.info("Refund transaction recorded: id={}, amount={}, originalId={}", 
            event.getTransactionId(), event.getAmount(), event.getOriginalTransactionId());
    }
    
    private void recordChargebackTransaction(LedgerRecordedEvent event, String correlationId) {
        List<LedgerEntry> entries = bookkeepingService.createDoubleEntry(
            event.getTransactionId(),
            event.getFromAccountId(), // Merchant account
            "CHARGEBACK_LIABILITY",   // Chargeback liability account
            event.getAmount(),
            "CHARGEBACK",
            correlationId
        );
        
        for (LedgerEntry entry : entries) {
            ledgerRepository.save(entry);
        }
        
        BigDecimal chargebackFee = event.getAmount().multiply(new BigDecimal("0.0025"));
        
        List<LedgerEntry> feeEntries = bookkeepingService.createDoubleEntry(
            event.getTransactionId() + "_FEE",
            event.getFromAccountId(),
            "CHARGEBACK_FEE_REVENUE",
            chargebackFee,
            "CHARGEBACK_FEE",
            correlationId
        );
        
        for (LedgerEntry entry : feeEntries) {
            ledgerRepository.save(entry);
        }
        
        log.error("Chargeback transaction recorded: id={}, amount={}, fee={}", 
            event.getTransactionId(), event.getAmount(), chargebackFee);
    }
    
    private void recordFeeTransaction(LedgerRecordedEvent event, String correlationId) {
        List<LedgerEntry> entries = bookkeepingService.createDoubleEntry(
            event.getTransactionId(),
            event.getFromAccountId(), // Customer/Merchant account
            "FEE_REVENUE",           // Fee revenue account
            event.getAmount(),
            "FEE",
            correlationId
        );
        
        for (LedgerEntry entry : entries) {
            ledgerRepository.save(entry);
        }
        
        log.info("Fee transaction recorded: id={}, amount={}, feeType={}", 
            event.getTransactionId(), event.getAmount(), event.getFeeType());
    }
    
    private void recordSettlementTransaction(LedgerRecordedEvent event, String correlationId) {
        List<LedgerEntry> entries = bookkeepingService.createDoubleEntry(
            event.getTransactionId(),
            "SETTLEMENT_CLEARING",    // Settlement clearing account
            event.getToAccountId(),   // Bank account
            event.getAmount(),
            "SETTLEMENT",
            correlationId
        );
        
        for (LedgerEntry entry : entries) {
            ledgerRepository.save(entry);
        }
        
        reconciliationService.markSettlementComplete(
            event.getSettlementBatchId(),
            event.getTransactionId()
        );
        
        log.info("Settlement transaction recorded: id={}, amount={}, batchId={}", 
            event.getTransactionId(), event.getAmount(), event.getSettlementBatchId());
    }
    
    private void recordAdjustmentTransaction(LedgerRecordedEvent event, String correlationId) {
        List<LedgerEntry> entries = bookkeepingService.createDoubleEntry(
            event.getTransactionId(),
            event.getFromAccountId(),
            event.getToAccountId(),
            event.getAmount(),
            "ADJUSTMENT",
            correlationId
        );
        
        for (LedgerEntry entry : entries) {
            entry.setAdjustmentReason(event.getAdjustmentReason());
            entry.setApprovedBy(event.getApprovedBy());
            ledgerRepository.save(entry);
        }
        
        auditService.logLedgerEvent("ADJUSTMENT_PROCESSED", event.getTransactionId(),
            Map.of("reason", event.getAdjustmentReason(), "approvedBy", event.getApprovedBy(),
                "amount", event.getAmount(), "correlationId", correlationId));
        
        log.warn("Adjustment transaction recorded: id={}, amount={}, reason={}", 
            event.getTransactionId(), event.getAmount(), event.getAdjustmentReason());
    }
    
    private void recordTransferTransaction(LedgerRecordedEvent event, String correlationId) {
        List<LedgerEntry> entries = bookkeepingService.createDoubleEntry(
            event.getTransactionId(),
            event.getFromAccountId(),
            event.getToAccountId(),
            event.getAmount(),
            "TRANSFER",
            correlationId
        );
        
        for (LedgerEntry entry : entries) {
            ledgerRepository.save(entry);
        }
        
        if ("INTERNATIONAL".equals(event.getTransferType())) {
            recordFxTransaction(event, correlationId);
        }
        
        log.info("Transfer transaction recorded: id={}, amount={}, type={}", 
            event.getTransactionId(), event.getAmount(), event.getTransferType());
    }
    
    private void recordFxTransaction(LedgerRecordedEvent event, String correlationId) {
        BigDecimal fxSpread = event.getAmount().multiply(new BigDecimal("0.005"));
        
        List<LedgerEntry> fxEntries = bookkeepingService.createDoubleEntry(
            event.getTransactionId() + "_FX",
            event.getFromAccountId(),
            "FX_REVENUE",
            fxSpread,
            "FX_SPREAD",
            correlationId
        );
        
        for (LedgerEntry entry : fxEntries) {
            ledgerRepository.save(entry);
        }
        
        log.info("FX spread recorded: transactionId={}, spread={}", 
            event.getTransactionId(), fxSpread);
    }
}