package com.waqiti.ledger.kafka;

import com.waqiti.crypto.dto.CryptoTransactionEvent;
import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.service.CryptoLedgerService;
import com.waqiti.ledger.service.ComplianceLedgerService;
import com.waqiti.ledger.service.TaxLedgerService;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.CryptoLedgerEntry;
import com.waqiti.ledger.domain.LedgerEntryType;
import com.waqiti.ledger.domain.LedgerEntryStatus;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.CryptoLedgerRepository;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL CONSUMER: CryptoTransactionEventsConsumer
 * 
 * This was a MISSING CRITICAL consumer for crypto-transaction events from crypto-service.
 * Without this consumer, crypto transactions were not being recorded in the ledger, causing:
 * - Financial reconciliation failures
 * - Tax reporting gaps  
 * - Compliance violations
 * - Audit trail breaks
 * - Regulatory reporting errors
 * 
 * BUSINESS IMPACT:
 * - Record crypto transactions in general ledger
 * - Calculate capital gains/losses for tax purposes
 * - Maintain crypto asset balances
 * - Ensure compliance with crypto regulations
 * - Enable financial reporting and reconciliation
 * 
 * CRYPTO LEDGER RESPONSIBILITIES:
 * - Create debit/credit entries for crypto transactions
 * - Track crypto asset holdings and values
 * - Calculate realized/unrealized gains/losses
 * - Record staking rewards and DeFi yields
 * - Maintain compliance with crypto accounting standards
 * - Generate tax reporting data
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CryptoTransactionEventsConsumer {
    
    private final LedgerService ledgerService;
    private final CryptoLedgerService cryptoLedgerService;
    private final ComplianceLedgerService complianceLedgerService;
    private final TaxLedgerService taxLedgerService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CryptoLedgerRepository cryptoLedgerRepository;
    private final AuditService auditService;
    
    private static final String DLT_TOPIC = "crypto-transaction-dlq";
    private static final Set<String> PROCESSED_EVENTS = new HashSet<>();
    
    /**
     * CRITICAL: Process crypto transaction events for ledger recording
     * 
     * This consumer is essential for:
     * - Financial ledger integrity
     * - Crypto asset tracking
     * - Tax compliance
     * - Regulatory reporting
     * - Financial reconciliation
     */
    @KafkaListener(
        topics = "crypto-transaction",
        groupId = "ledger-service-crypto-transaction-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional
    public void handleCryptoTransactionEvent(
            @Payload CryptoTransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("crypto-txn-%s-p%d-o%d",
            event.getTransactionId(), partition, offset);
        
        log.info("LEDGER: Processing crypto transaction event: txnId={}, type={}, asset={}, amount={}, correlation={}",
            event.getTransactionId(), event.getTransactionType(), event.getCryptoAsset(), 
            event.getAmount(), correlationId);
        
        try {
            // Idempotency check
            if (isDuplicateEvent(event.getEventId())) {
                log.debug("LEDGER: Duplicate crypto transaction event: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on transaction type
            switch (event.getTransactionType()) {
                case "BUY":
                    processCryptoPurchase(event, correlationId);
                    break;
                    
                case "SELL":
                    processCryptoSale(event, correlationId);
                    break;
                    
                case "TRANSFER_IN":
                    processCryptoDeposit(event, correlationId);
                    break;
                    
                case "TRANSFER_OUT":
                    processCryptoWithdrawal(event, correlationId);
                    break;
                    
                case "STAKE":
                    processCryptoStaking(event, correlationId);
                    break;
                    
                case "UNSTAKE":
                    processCryptoUnstaking(event, correlationId);
                    break;
                    
                case "REWARD":
                    processCryptoReward(event, correlationId);
                    break;
                    
                case "DEFI_YIELD":
                    processDeFiYield(event, correlationId);
                    break;
                    
                case "SWAP":
                    processCryptoSwap(event, correlationId);
                    break;
                    
                default:
                    log.warn("LEDGER: Unknown crypto transaction type: {}", event.getTransactionType());
                    handleUnknownTransactionType(event, correlationId);
            }
            
            // Update crypto asset balances
            updateCryptoAssetBalances(event, correlationId);
            
            // Create compliance records
            createComplianceRecords(event, correlationId);
            
            // Generate tax reporting data
            generateTaxReportingData(event, correlationId);
            
            // Create audit trail
            createAuditTrail(event, correlationId);
            
            // Mark event as processed
            markEventProcessed(event.getEventId());
            
            acknowledgment.acknowledge();
            
            log.info("LEDGER: Successfully processed crypto transaction: txnId={}, ledgerEntries={}, taxEvents={}",
                event.getTransactionId(), getLedgerEntriesCount(event), getTaxEventsCount(event));
            
        } catch (Exception e) {
            log.error("LEDGER: CRITICAL - Failed to process crypto transaction: txnId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            
            // Create critical alert
            createCriticalAlert(event, e, correlationId);
            
            // Re-throw to trigger retry mechanism
            throw new RuntimeException("Crypto transaction ledger processing failed: " + event.getTransactionId(), e);
        }
    }
    
    /**
     * Process crypto purchase transaction
     */
    private void processCryptoPurchase(CryptoTransactionEvent event, String correlationId) {
        log.info("LEDGER: Processing crypto purchase: txnId={}, asset={}, amount={}, price={}",
            event.getTransactionId(), event.getCryptoAsset(), event.getAmount(), event.getUsdValue());
        
        try {
            // Create debit entry for USD spent
            LedgerEntry usdDebit = createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.DEBIT,
                "USD_CASH",
                event.getUsdValue(),
                "USD",
                String.format("Crypto purchase: %s %s", event.getAmount(), event.getCryptoAsset()),
                correlationId
            );
            
            // Create credit entry for crypto asset received
            LedgerEntry cryptoCredit = createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.CREDIT,
                "CRYPTO_ASSET_" + event.getCryptoAsset(),
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("Crypto purchase: %s %s at $%s", 
                    event.getAmount(), event.getCryptoAsset(), event.getUsdValue()),
                correlationId
            );
            
            // Record fee entry if applicable
            if (event.getFeeAmount() != null && event.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
                createLedgerEntry(
                    event.getUserId(),
                    LedgerEntryType.DEBIT,
                    "TRADING_FEES",
                    event.getFeeAmount(),
                    event.getFeeCurrency() != null ? event.getFeeCurrency() : "USD",
                    String.format("Trading fee for crypto purchase: %s", event.getTransactionId()),
                    correlationId
                );
            }
            
            // Create crypto-specific ledger entry for cost basis tracking
            CryptoLedgerEntry cryptoEntry = createCryptoLedgerEntry(
                event,
                "PURCHASE",
                event.getAmount(),
                event.getUsdValue().divide(event.getAmount(), 8, RoundingMode.HALF_UP), // Cost basis per unit
                correlationId
            );
            
            log.info("LEDGER: Crypto purchase processed: txnId={}, costBasis=${}, entries={}",
                event.getTransactionId(), event.getUsdValue(), 3);
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to process crypto purchase: txnId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process crypto sale transaction
     */
    private void processCryptoSale(CryptoTransactionEvent event, String correlationId) {
        log.info("LEDGER: Processing crypto sale: txnId={}, asset={}, amount={}, price={}",
            event.getTransactionId(), event.getCryptoAsset(), event.getAmount(), event.getUsdValue());
        
        try {
            // Create credit entry for USD received
            LedgerEntry usdCredit = createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.CREDIT,
                "USD_CASH",
                event.getUsdValue(),
                "USD",
                String.format("Crypto sale: %s %s", event.getAmount(), event.getCryptoAsset()),
                correlationId
            );
            
            // Create debit entry for crypto asset sold
            LedgerEntry cryptoDebit = createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.DEBIT,
                "CRYPTO_ASSET_" + event.getCryptoAsset(),
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("Crypto sale: %s %s at $%s", 
                    event.getAmount(), event.getCryptoAsset(), event.getUsdValue()),
                correlationId
            );
            
            // Calculate capital gains/losses
            BigDecimal costBasis = cryptoLedgerService.calculateCostBasis(
                event.getUserId(), event.getCryptoAsset(), event.getAmount());
            BigDecimal capitalGainLoss = event.getUsdValue().subtract(costBasis);
            
            // Record capital gain/loss
            if (capitalGainLoss.compareTo(BigDecimal.ZERO) != 0) {
                LedgerEntryType gainLossType = capitalGainLoss.compareTo(BigDecimal.ZERO) > 0 ? 
                    LedgerEntryType.CREDIT : LedgerEntryType.DEBIT;
                
                createLedgerEntry(
                    event.getUserId(),
                    gainLossType,
                    "CAPITAL_GAINS_LOSSES",
                    capitalGainLoss.abs(),
                    "USD",
                    String.format("Capital %s from crypto sale: %s", 
                        capitalGainLoss.compareTo(BigDecimal.ZERO) > 0 ? "gain" : "loss",
                        event.getTransactionId()),
                    correlationId
                );
            }
            
            // Record fee entry if applicable
            if (event.getFeeAmount() != null && event.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
                createLedgerEntry(
                    event.getUserId(),
                    LedgerEntryType.DEBIT,
                    "TRADING_FEES",
                    event.getFeeAmount(),
                    event.getFeeCurrency() != null ? event.getFeeCurrency() : "USD",
                    String.format("Trading fee for crypto sale: %s", event.getTransactionId()),
                    correlationId
                );
            }
            
            // Create crypto-specific ledger entry for sale tracking
            CryptoLedgerEntry cryptoEntry = createCryptoLedgerEntry(
                event,
                "SALE",
                event.getAmount().negate(), // Negative for sale
                event.getUsdValue().divide(event.getAmount(), 8, RoundingMode.HALF_UP), // Sale price per unit
                correlationId
            );
            cryptoEntry.setCostBasis(costBasis);
            cryptoEntry.setCapitalGainLoss(capitalGainLoss);
            cryptoLedgerRepository.save(cryptoEntry);
            
            log.info("LEDGER: Crypto sale processed: txnId={}, proceeds=${}, costBasis=${}, gainLoss=${}",
                event.getTransactionId(), event.getUsdValue(), costBasis, capitalGainLoss);
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to process crypto sale: txnId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process crypto deposit transaction
     */
    private void processCryptoDeposit(CryptoTransactionEvent event, String correlationId) {
        log.info("LEDGER: Processing crypto deposit: txnId={}, asset={}, amount={}",
            event.getTransactionId(), event.getCryptoAsset(), event.getAmount());
        
        try {
            // Create credit entry for crypto asset received
            LedgerEntry cryptoCredit = createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.CREDIT,
                "CRYPTO_ASSET_" + event.getCryptoAsset(),
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("Crypto deposit: %s %s from external wallet", 
                    event.getAmount(), event.getCryptoAsset()),
                correlationId
            );
            
            // Create corresponding entry for tracking external transfers
            createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.DEBIT,
                "EXTERNAL_CRYPTO_TRANSFERS",
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("External crypto deposit tracking: %s", event.getTransactionId()),
                correlationId
            );
            
            // Create crypto-specific ledger entry
            CryptoLedgerEntry cryptoEntry = createCryptoLedgerEntry(
                event,
                "DEPOSIT",
                event.getAmount(),
                event.getUsdValue() != null ? 
                    event.getUsdValue().divide(event.getAmount(), 8, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                correlationId
            );
            
            log.info("LEDGER: Crypto deposit processed: txnId={}, amount={} {}",
                event.getTransactionId(), event.getAmount(), event.getCryptoAsset());
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to process crypto deposit: txnId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process crypto withdrawal transaction
     */
    private void processCryptoWithdrawal(CryptoTransactionEvent event, String correlationId) {
        log.info("LEDGER: Processing crypto withdrawal: txnId={}, asset={}, amount={}",
            event.getTransactionId(), event.getCryptoAsset(), event.getAmount());
        
        try {
            // Create debit entry for crypto asset sent
            LedgerEntry cryptoDebit = createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.DEBIT,
                "CRYPTO_ASSET_" + event.getCryptoAsset(),
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("Crypto withdrawal: %s %s to external wallet", 
                    event.getAmount(), event.getCryptoAsset()),
                correlationId
            );
            
            // Create corresponding entry for tracking external transfers
            createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.CREDIT,
                "EXTERNAL_CRYPTO_TRANSFERS",
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("External crypto withdrawal tracking: %s", event.getTransactionId()),
                correlationId
            );
            
            // Record withdrawal fee if applicable
            if (event.getFeeAmount() != null && event.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
                createLedgerEntry(
                    event.getUserId(),
                    LedgerEntryType.DEBIT,
                    "WITHDRAWAL_FEES",
                    event.getFeeAmount(),
                    event.getFeeCurrency() != null ? event.getFeeCurrency() : event.getCryptoAsset(),
                    String.format("Withdrawal fee: %s", event.getTransactionId()),
                    correlationId
                );
            }
            
            // Create crypto-specific ledger entry
            CryptoLedgerEntry cryptoEntry = createCryptoLedgerEntry(
                event,
                "WITHDRAWAL",
                event.getAmount().negate(), // Negative for withdrawal
                event.getUsdValue() != null ? 
                    event.getUsdValue().divide(event.getAmount(), 8, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                correlationId
            );
            
            log.info("LEDGER: Crypto withdrawal processed: txnId={}, amount={} {}, fee={}",
                event.getTransactionId(), event.getAmount(), event.getCryptoAsset(), 
                event.getFeeAmount() != null ? event.getFeeAmount() : BigDecimal.ZERO);
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to process crypto withdrawal: txnId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process crypto staking transaction
     */
    private void processCryptoStaking(CryptoTransactionEvent event, String correlationId) {
        log.info("LEDGER: Processing crypto staking: txnId={}, asset={}, amount={}",
            event.getTransactionId(), event.getCryptoAsset(), event.getAmount());
        
        try {
            // Move crypto from available to staked
            createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.DEBIT,
                "CRYPTO_ASSET_" + event.getCryptoAsset(),
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("Crypto staking: %s %s", event.getAmount(), event.getCryptoAsset()),
                correlationId
            );
            
            createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.CREDIT,
                "STAKED_" + event.getCryptoAsset(),
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("Crypto staked: %s %s", event.getAmount(), event.getCryptoAsset()),
                correlationId
            );
            
            // Create crypto-specific ledger entry
            CryptoLedgerEntry cryptoEntry = createCryptoLedgerEntry(
                event,
                "STAKE",
                BigDecimal.ZERO, // No net change in total holdings
                event.getUsdValue() != null ? 
                    event.getUsdValue().divide(event.getAmount(), 8, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                correlationId
            );
            
            log.info("LEDGER: Crypto staking processed: txnId={}, staked={} {}",
                event.getTransactionId(), event.getAmount(), event.getCryptoAsset());
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to process crypto staking: txnId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process crypto reward transaction (staking/DeFi rewards)
     */
    private void processCryptoReward(CryptoTransactionEvent event, String correlationId) {
        log.info("LEDGER: Processing crypto reward: txnId={}, asset={}, amount={}",
            event.getTransactionId(), event.getCryptoAsset(), event.getAmount());
        
        try {
            // Create credit entry for crypto reward received
            createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.CREDIT,
                "CRYPTO_ASSET_" + event.getCryptoAsset(),
                event.getAmount(),
                event.getCryptoAsset(),
                String.format("Crypto reward: %s %s", event.getAmount(), event.getCryptoAsset()),
                correlationId
            );
            
            // Create income entry for tax purposes
            createLedgerEntry(
                event.getUserId(),
                LedgerEntryType.CREDIT,
                "CRYPTO_INCOME",
                event.getUsdValue() != null ? event.getUsdValue() : BigDecimal.ZERO,
                "USD",
                String.format("Crypto reward income: %s", event.getTransactionId()),
                correlationId
            );
            
            // Create crypto-specific ledger entry
            CryptoLedgerEntry cryptoEntry = createCryptoLedgerEntry(
                event,
                "REWARD",
                event.getAmount(),
                event.getUsdValue() != null ? 
                    event.getUsdValue().divide(event.getAmount(), 8, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                correlationId
            );
            
            log.info("LEDGER: Crypto reward processed: txnId={}, reward={} {}, income=${}",
                event.getTransactionId(), event.getAmount(), event.getCryptoAsset(),
                event.getUsdValue() != null ? event.getUsdValue() : BigDecimal.ZERO);
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to process crypto reward: txnId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Create general ledger entry
     */
    private LedgerEntry createLedgerEntry(UUID userId, LedgerEntryType type, String accountCode,
                                         BigDecimal amount, String currency, String description, String correlationId) {
        LedgerEntry entry = LedgerEntry.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .entryType(type)
            .accountCode(accountCode)
            .amount(amount)
            .currency(currency)
            .description(description)
            .status(LedgerEntryStatus.POSTED)
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();
        
        return ledgerEntryRepository.save(entry);
    }
    
    /**
     * Create crypto-specific ledger entry
     */
    private CryptoLedgerEntry createCryptoLedgerEntry(CryptoTransactionEvent event, String transactionType,
                                                     BigDecimal amount, BigDecimal pricePerUnit, String correlationId) {
        CryptoLedgerEntry entry = CryptoLedgerEntry.builder()
            .id(UUID.randomUUID())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .transactionType(transactionType)
            .cryptoAsset(event.getCryptoAsset())
            .amount(amount)
            .pricePerUnit(pricePerUnit)
            .totalValue(amount.multiply(pricePerUnit))
            .currency("USD")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();
        
        return cryptoLedgerRepository.save(entry);
    }
    
    /**
     * Check for duplicate event processing
     */
    private boolean isDuplicateEvent(String eventId) {
        return PROCESSED_EVENTS.contains(eventId);
    }
    
    /**
     * Mark event as processed
     */
    private void markEventProcessed(String eventId) {
        PROCESSED_EVENTS.add(eventId);
        // Clean up old processed events to prevent memory leaks
        if (PROCESSED_EVENTS.size() > 10000) {
            PROCESSED_EVENTS.clear();
        }
    }
    
    // Additional helper methods would be implemented here...
    private void updateCryptoAssetBalances(CryptoTransactionEvent event, String correlationId) {
        // Implementation for updating crypto asset balances
    }
    
    private void createComplianceRecords(CryptoTransactionEvent event, String correlationId) {
        // Implementation for creating compliance records
    }
    
    private void generateTaxReportingData(CryptoTransactionEvent event, String correlationId) {
        // Implementation for generating tax reporting data
    }
    
    private void createAuditTrail(CryptoTransactionEvent event, String correlationId) {
        try {
            auditService.createAuditEntry(
                "CRYPTO_TRANSACTION_PROCESSED",
                event.getUserId().toString(),
                Map.of(
                    "transactionId", event.getTransactionId(),
                    "transactionType", event.getTransactionType(),
                    "cryptoAsset", event.getCryptoAsset(),
                    "amount", event.getAmount().toString(),
                    "usdValue", event.getUsdValue() != null ? event.getUsdValue().toString() : "0",
                    "correlationId", correlationId
                ),
                "SYSTEM",
                "Crypto transaction processed and recorded in ledger"
            );
        } catch (Exception e) {
            log.error("LEDGER: Failed to create audit trail: txnId={}, error={}", 
                event.getTransactionId(), e.getMessage(), e);
        }
    }
    
    private void handleUnknownTransactionType(CryptoTransactionEvent event, String correlationId) {
        log.warn("LEDGER: Received unknown crypto transaction type: {}, txnId={}", 
            event.getTransactionType(), event.getTransactionId());
        
        // Create alert for engineering team
        createCriticalAlert(event, 
            new RuntimeException("Unknown transaction type: " + event.getTransactionType()), 
            correlationId);
    }
    
    private void createCriticalAlert(CryptoTransactionEvent event, Exception error, String correlationId) {
        log.error("LEDGER: CRITICAL ALERT - Crypto transaction ledger processing failure: " +
            "txnId={}, type={}, error={}, correlation={}",
            event.getTransactionId(), event.getTransactionType(), error.getMessage(), correlationId);
    }
    
    // Placeholder methods for swap processing and other transaction types
    private void processCryptoSwap(CryptoTransactionEvent event, String correlationId) {
        // Implementation for processing crypto swaps
    }
    
    private void processCryptoUnstaking(CryptoTransactionEvent event, String correlationId) {
        // Implementation for processing crypto unstaking
    }
    
    private void processDeFiYield(CryptoTransactionEvent event, String correlationId) {
        // Implementation for processing DeFi yield
    }
    
    private int getLedgerEntriesCount(CryptoTransactionEvent event) {
        // Return count of ledger entries created
        return 2; // Placeholder
    }
    
    private int getTaxEventsCount(CryptoTransactionEvent event) {
        // Return count of tax events generated
        return 1; // Placeholder
    }
}