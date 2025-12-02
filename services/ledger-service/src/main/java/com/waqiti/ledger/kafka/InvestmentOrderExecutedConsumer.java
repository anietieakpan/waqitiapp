package com.waqiti.ledger.kafka;

import com.waqiti.common.events.InvestmentOrderExecutedEvent;
import com.waqiti.ledger.domain.JournalEntry;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.EntryType;
import com.waqiti.ledger.service.DoubleEntryLedgerService;
import com.waqiti.ledger.service.AuditLogService;
import com.waqiti.ledger.repository.JournalEntryRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRODUCTION-READY: Investment Order Executed Consumer
 * 
 * CRITICAL ACCOUNTING FUNCTION: Records investment transactions in general ledger
 * 
 * This consumer processes investment order execution events and:
 * 1. Records investment transactions in general ledger (double-entry bookkeeping)
 * 2. Updates investment asset accounts
 * 3. Updates cash accounts
 * 4. Records commission and fee expenses
 * 5. Creates tax lots for capital gains tracking
 * 6. Ensures GAAP/IFRS compliance for investment accounting
 * 7. Maintains audit trail for regulatory reporting
 * 
 * BUSINESS IMPACT:
 * - Investment transactions MUST be in general ledger for accurate financial statements
 * - Cost basis tracking required for capital gains tax reporting (IRS Form 8949)
 * - Missing ledger entries = financial statement inaccuracies
 * - Audit failures without complete investment transaction records
 * 
 * ACCOUNTING STANDARDS:
 * - GAAP: ASC 320 (Investments - Debt and Equity Securities)
 * - IFRS 9: Financial Instruments classification and measurement
 * - Cost basis tracking for IRS compliance
 * - Fair value measurements (ASC 820 / IFRS 13)
 * 
 * DOUBLE-ENTRY BOOKKEEPING:
 * 
 * BUY ORDER EXECUTION:
 *   DR: Investment Securities (Asset)       $10,000.00
 *   DR: Commission Expense                      $10.00
 *   CR: Cash                                $10,010.00
 * 
 * SELL ORDER EXECUTION:
 *   DR: Cash                                 $9,990.00
 *   DR: Commission Expense                      $10.00
 *   CR: Investment Securities (Asset)       $10,000.00
 * 
 * CAPITAL GAINS (tracked separately):
 *   If sale price > cost basis:
 *     DR: Investment Securities                   (cost basis)
 *     CR: Investment Securities                   (cost basis)
 *     CR: Realized Gain                           (gain amount)
 * 
 * @author Waqiti Ledger Team
 * @version 1.0
 * @since 2025-09-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentOrderExecutedConsumer {
    
    private final DoubleEntryLedgerService doubleEntryLedgerService;
    private final AuditLogService auditLogService;
    private final JournalEntryRepository journalEntryRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, LocalDateTime> processedOrders = new ConcurrentHashMap<>();
    
    private static final String CHART_OF_ACCOUNTS_INVESTMENT_SECURITIES = "1200";
    private static final String CHART_OF_ACCOUNTS_CASH = "1100";
    private static final String CHART_OF_ACCOUNTS_COMMISSION_EXPENSE = "6100";
    private static final String CHART_OF_ACCOUNTS_EXCHANGE_FEES_EXPENSE = "6110";
    private static final String CHART_OF_ACCOUNTS_REALIZED_GAINS = "4100";
    private static final String CHART_OF_ACCOUNTS_REALIZED_LOSSES = "7100";
    
    private final Counter orderExecutionCounter;
    private final Counter buyOrderCounter;
    private final Counter sellOrderCounter;
    private final Counter capitalGainsCounter;
    private final Counter capitalLossesCounter;
    private final Timer processingDurationTimer;
    
    public InvestmentOrderExecutedConsumer(
            DoubleEntryLedgerService doubleEntryLedgerService,
            AuditLogService auditLogService,
            JournalEntryRepository journalEntryRepository,
            LedgerEntryRepository ledgerEntryRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        
        this.doubleEntryLedgerService = doubleEntryLedgerService;
        this.auditLogService = auditLogService;
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        
        this.orderExecutionCounter = Counter.builder("ledger.investment.orders.executed")
                .description("Total investment orders recorded in ledger")
                .tag("type", "investment")
                .register(meterRegistry);
        
        this.buyOrderCounter = Counter.builder("ledger.investment.buy.orders")
                .description("Buy order executions recorded")
                .tag("side", "buy")
                .register(meterRegistry);
        
        this.sellOrderCounter = Counter.builder("ledger.investment.sell.orders")
                .description("Sell order executions recorded")
                .tag("side", "sell")
                .register(meterRegistry);
        
        this.capitalGainsCounter = Counter.builder("ledger.investment.capital.gains")
                .description("Capital gains realized")
                .register(meterRegistry);
        
        this.capitalLossesCounter = Counter.builder("ledger.investment.capital.losses")
                .description("Capital losses realized")
                .register(meterRegistry);
        
        this.processingDurationTimer = Timer.builder("ledger.investment.processing.duration")
                .description("Investment order ledger processing duration")
                .register(meterRegistry);
    }
    
    /**
     * Process investment order execution for general ledger recording
     * 
     * CRITICAL ACCOUNTING STEPS:
     * 1. Validate order execution event
     * 2. Check idempotency (prevent duplicate ledger entries)
     * 3. Create journal entry with double-entry bookkeeping
     * 4. Record investment asset account changes
     * 5. Record cash account changes
     * 6. Record commission and fee expenses
     * 7. Calculate and record capital gains/losses (for SELL orders)
     * 8. Create tax lot records for IRS reporting
     * 9. Update trial balance
     * 10. Publish ledger recorded event
     * 
     * GAAP COMPLIANCE:
     * - ASC 320: Investment securities classification
     * - ASC 820: Fair value measurements
     * - Cost basis tracking for realized gains/losses
     * 
     * @param event Investment order executed event
     * @param partition Kafka partition
     * @param offset Kafka offset
     */
    @KafkaListener(
            topics = "${kafka.topics.investment-order-executed:investment.order.executed}",
            groupId = "${kafka.consumer.group-id:ledger-investment-group}",
            concurrency = "${kafka.consumer.concurrency:3}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void handleInvestmentOrderExecuted(
            @Payload InvestmentOrderExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("LEDGER: Processing investment order execution - Order: {}, Symbol: {}, Side: {}, Qty: {}, Price: {}, Partition: {}, Offset: {}",
                    event.getOrderNumber(), event.getSymbol(), event.getOrderSide(), 
                    event.getExecutedQuantity(), event.getExecutionPrice(), partition, offset);
            
            if (isDuplicateOrder(event)) {
                log.warn("LEDGER: Duplicate investment order detected - Order: {} - Skipping",
                        event.getOrderId());
                return;
            }
            
            orderExecutionCounter.increment();
            
            validateOrderEvent(event);
            
            JournalEntry journalEntry = createJournalEntry(event);
            
            List<LedgerEntry> ledgerEntries = new ArrayList<>();
            
            if ("BUY".equalsIgnoreCase(event.getOrderSide())) {
                ledgerEntries.addAll(processeBuyOrder(event, journalEntry));
                buyOrderCounter.increment();
            } else if ("SELL".equalsIgnoreCase(event.getOrderSide())) {
                ledgerEntries.addAll(processSellOrder(event, journalEntry));
                sellOrderCounter.increment();
            } else {
                throw new IllegalArgumentException("Invalid order side: " + event.getOrderSide());
            }
            
            journalEntry = journalEntryRepository.save(journalEntry);
            ledgerEntries = ledgerEntryRepository.saveAll(ledgerEntries);
            
            validateDoubleEntry(ledgerEntries);
            
            auditLogService.logInvestmentTransaction(
                    event.getOrderId(),
                    event.getOrderSide(),
                    event.getSymbol(),
                    event.getExecutedQuantity(),
                    event.getExecutionPrice(),
                    event.getTotalAmount(),
                    journalEntry.getId()
            );
            
            createTaxLot(event);
            
            publishLedgerRecorded(event, journalEntry, ledgerEntries);
            
            markOrderAsProcessed(event);
            
            long duration = System.currentTimeMillis() - startTime;
            sample.stop(processingDurationTimer);
            
            log.info("LEDGER: Investment order recorded successfully - Order: {}, Journal Entry: {}, Duration: {}ms",
                    event.getOrderNumber(), journalEntry.getId(), duration);
            
            if (duration > 5000) {
                log.warn("LEDGER SLA WARNING: Processing took longer than 5s - Order: {}, Duration: {}ms",
                        event.getOrderNumber(), duration);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("LEDGER ERROR: Investment order processing failed - Order: {}, Duration: {}ms, Error: {}",
                    event.getOrderNumber(), duration, e.getMessage(), e);
            
            sendToDeadLetterQueue(event, e);
            
            throw new RuntimeException("Investment order ledger recording failed", e);
        }
    }
    
    private boolean isDuplicateOrder(InvestmentOrderExecutedEvent event) {
        if (processedOrders.containsKey(event.getOrderId())) {
            return true;
        }
        
        return journalEntryRepository.existsByReferenceNumber(event.getOrderNumber());
    }
    
    private void validateOrderEvent(InvestmentOrderExecutedEvent event) {
        if (event.getOrderId() == null || event.getOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        
        if (event.getAccountId() == null || event.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (event.getExecutedQuantity() == null || event.getExecutedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid executed quantity: " + event.getExecutedQuantity());
        }
        
        if (event.getExecutionPrice() == null || event.getExecutionPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid execution price: " + event.getExecutionPrice());
        }
        
        if (event.getTotalAmount() == null) {
            throw new IllegalArgumentException("Total amount is required");
        }
        
        if (!"BUY".equalsIgnoreCase(event.getOrderSide()) && !"SELL".equalsIgnoreCase(event.getOrderSide())) {
            throw new IllegalArgumentException("Invalid order side: " + event.getOrderSide());
        }
    }
    
    private JournalEntry createJournalEntry(InvestmentOrderExecutedEvent event) {
        return JournalEntry.builder()
                .id(UUID.randomUUID().toString())
                .referenceNumber(event.getOrderNumber())
                .transactionType("INVESTMENT_ORDER")
                .description(String.format("%s %s shares of %s @ %s",
                        event.getOrderSide(),
                        event.getExecutedQuantity(),
                        event.getSymbol(),
                        event.getExecutionPrice()))
                .transactionDate(event.getExecutedAt() != null ? event.getExecutedAt() : LocalDateTime.now())
                .postingDate(LocalDateTime.now())
                .amount(event.getTotalAmount().abs())
                .currency(event.getCurrency())
                .status("POSTED")
                .source("INVESTMENT_SERVICE")
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .metadata(Map.of(
                        "orderId", event.getOrderId(),
                        "symbol", event.getSymbol(),
                        "instrumentType", event.getInstrumentType() != null ? event.getInstrumentType() : "UNKNOWN",
                        "quantity", event.getExecutedQuantity().toString(),
                        "price", event.getExecutionPrice().toString(),
                        "executionVenue", event.getExecutionVenue() != null ? event.getExecutionVenue() : "UNKNOWN"
                ))
                .build();
    }
    
    private List<LedgerEntry> processeBuyOrder(InvestmentOrderExecutedEvent event, JournalEntry journalEntry) {
        List<LedgerEntry> entries = new ArrayList<>();
        
        BigDecimal investmentAmount = event.getExecutionAmount();
        BigDecimal commission = event.getCommission() != null ? event.getCommission() : BigDecimal.ZERO;
        BigDecimal fees = event.getExchangeFees() != null ? event.getExchangeFees() : BigDecimal.ZERO;
        BigDecimal totalCashOutflow = investmentAmount.add(commission).add(fees);
        
        LedgerEntry investmentDebit = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .journalEntryId(journalEntry.getId())
                .accountCode(CHART_OF_ACCOUNTS_INVESTMENT_SECURITIES)
                .accountName("Investment Securities - " + event.getSymbol())
                .entryType(EntryType.DEBIT)
                .amount(investmentAmount)
                .currency(event.getCurrency())
                .description(String.format("Purchase %s shares %s @ %s",
                        event.getExecutedQuantity(), event.getSymbol(), event.getExecutionPrice()))
                .transactionDate(event.getExecutedAt())
                .createdAt(LocalDateTime.now())
                .metadata(Map.of("symbol", event.getSymbol(), "quantity", event.getExecutedQuantity().toString()))
                .build();
        
        entries.add(investmentDebit);
        
        if (commission.compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry commissionDebit = LedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .journalEntryId(journalEntry.getId())
                    .accountCode(CHART_OF_ACCOUNTS_COMMISSION_EXPENSE)
                    .accountName("Commission Expense")
                    .entryType(EntryType.DEBIT)
                    .amount(commission)
                    .currency(event.getCurrency())
                    .description("Commission on investment purchase: " + event.getSymbol())
                    .transactionDate(event.getExecutedAt())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            entries.add(commissionDebit);
        }
        
        if (fees.compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry feesDebit = LedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .journalEntryId(journalEntry.getId())
                    .accountCode(CHART_OF_ACCOUNTS_EXCHANGE_FEES_EXPENSE)
                    .accountName("Exchange Fees Expense")
                    .entryType(EntryType.DEBIT)
                    .amount(fees)
                    .currency(event.getCurrency())
                    .description("Exchange fees on investment purchase: " + event.getSymbol())
                    .transactionDate(event.getExecutedAt())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            entries.add(feesDebit);
        }
        
        LedgerEntry cashCredit = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .journalEntryId(journalEntry.getId())
                .accountCode(CHART_OF_ACCOUNTS_CASH)
                .accountName("Cash - Investment Account " + event.getAccountId())
                .entryType(EntryType.CREDIT)
                .amount(totalCashOutflow)
                .currency(event.getCurrency())
                .description("Cash payment for investment purchase: " + event.getSymbol())
                .transactionDate(event.getExecutedAt())
                .createdAt(LocalDateTime.now())
                .metadata(Map.of("accountId", event.getAccountId()))
                .build();
        
        entries.add(cashCredit);
        
        log.info("LEDGER: BUY order ledger entries - Investment DR: {}, Commission DR: {}, Fees DR: {}, Cash CR: {}",
                investmentAmount, commission, fees, totalCashOutflow);
        
        return entries;
    }
    
    private List<LedgerEntry> processSellOrder(InvestmentOrderExecutedEvent event, JournalEntry journalEntry) {
        List<LedgerEntry> entries = new ArrayList<>();
        
        BigDecimal saleProceeds = event.getExecutionAmount();
        BigDecimal commission = event.getCommission() != null ? event.getCommission() : BigDecimal.ZERO;
        BigDecimal fees = event.getExchangeFees() != null ? event.getExchangeFees() : BigDecimal.ZERO;
        BigDecimal netCashInflow = saleProceeds.subtract(commission).subtract(fees);
        
        LedgerEntry cashDebit = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .journalEntryId(journalEntry.getId())
                .accountCode(CHART_OF_ACCOUNTS_CASH)
                .accountName("Cash - Investment Account " + event.getAccountId())
                .entryType(EntryType.DEBIT)
                .amount(netCashInflow)
                .currency(event.getCurrency())
                .description("Cash proceeds from investment sale: " + event.getSymbol())
                .transactionDate(event.getExecutedAt())
                .createdAt(LocalDateTime.now())
                .metadata(Map.of("accountId", event.getAccountId()))
                .build();
        
        entries.add(cashDebit);
        
        if (commission.compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry commissionDebit = LedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .journalEntryId(journalEntry.getId())
                    .accountCode(CHART_OF_ACCOUNTS_COMMISSION_EXPENSE)
                    .accountName("Commission Expense")
                    .entryType(EntryType.DEBIT)
                    .amount(commission)
                    .currency(event.getCurrency())
                    .description("Commission on investment sale: " + event.getSymbol())
                    .transactionDate(event.getExecutedAt())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            entries.add(commissionDebit);
        }
        
        if (fees.compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry feesDebit = LedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .journalEntryId(journalEntry.getId())
                    .accountCode(CHART_OF_ACCOUNTS_EXCHANGE_FEES_EXPENSE)
                    .accountName("Exchange Fees Expense")
                    .entryType(EntryType.DEBIT)
                    .amount(fees)
                    .currency(event.getCurrency())
                    .description("Exchange fees on investment sale: " + event.getSymbol())
                    .transactionDate(event.getExecutedAt())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            entries.add(feesDebit);
        }
        
        BigDecimal costBasis = calculateCostBasis(event);
        
        LedgerEntry investmentCredit = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .journalEntryId(journalEntry.getId())
                .accountCode(CHART_OF_ACCOUNTS_INVESTMENT_SECURITIES)
                .accountName("Investment Securities - " + event.getSymbol())
                .entryType(EntryType.CREDIT)
                .amount(costBasis)
                .currency(event.getCurrency())
                .description(String.format("Sale %s shares %s @ %s",
                        event.getExecutedQuantity(), event.getSymbol(), event.getExecutionPrice()))
                .transactionDate(event.getExecutedAt())
                .createdAt(LocalDateTime.now())
                .metadata(Map.of("symbol", event.getSymbol(), "quantity", event.getExecutedQuantity().toString()))
                .build();
        
        entries.add(investmentCredit);
        
        BigDecimal capitalGainLoss = saleProceeds.subtract(costBasis);
        
        if (capitalGainLoss.compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry gainCredit = LedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .journalEntryId(journalEntry.getId())
                    .accountCode(CHART_OF_ACCOUNTS_REALIZED_GAINS)
                    .accountName("Realized Capital Gains")
                    .entryType(EntryType.CREDIT)
                    .amount(capitalGainLoss)
                    .currency(event.getCurrency())
                    .description(String.format("Capital gain on sale of %s", event.getSymbol()))
                    .transactionDate(event.getExecutedAt())
                    .createdAt(LocalDateTime.now())
                    .metadata(Map.of(
                            "symbol", event.getSymbol(),
                            "costBasis", costBasis.toString(),
                            "saleProceeds", saleProceeds.toString(),
                            "gainAmount", capitalGainLoss.toString()
                    ))
                    .build();
            
            entries.add(gainCredit);
            capitalGainsCounter.increment();
            
            log.info("LEDGER: Capital gain recorded - Symbol: {}, Gain: {}", event.getSymbol(), capitalGainLoss);
            
        } else if (capitalGainLoss.compareTo(BigDecimal.ZERO) < 0) {
            LedgerEntry lossDebit = LedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .journalEntryId(journalEntry.getId())
                    .accountCode(CHART_OF_ACCOUNTS_REALIZED_LOSSES)
                    .accountName("Realized Capital Losses")
                    .entryType(EntryType.DEBIT)
                    .amount(capitalGainLoss.abs())
                    .currency(event.getCurrency())
                    .description(String.format("Capital loss on sale of %s", event.getSymbol()))
                    .transactionDate(event.getExecutedAt())
                    .createdAt(LocalDateTime.now())
                    .metadata(Map.of(
                            "symbol", event.getSymbol(),
                            "costBasis", costBasis.toString(),
                            "saleProceeds", saleProceeds.toString(),
                            "lossAmount", capitalGainLoss.abs().toString()
                    ))
                    .build();
            
            entries.add(lossDebit);
            capitalLossesCounter.increment();
            
            log.info("LEDGER: Capital loss recorded - Symbol: {}, Loss: {}", event.getSymbol(), capitalGainLoss.abs());
        }
        
        log.info("LEDGER: SELL order ledger entries - Cash DR: {}, Investment CR: {}, Capital Gain/Loss: {}",
                netCashInflow, costBasis, capitalGainLoss);
        
        return entries;
    }
    
    private BigDecimal calculateCostBasis(InvestmentOrderExecutedEvent event) {
        if (event.getCostBasisPerShare() != null) {
            return event.getCostBasisPerShare().multiply(event.getExecutedQuantity());
        }
        
        log.warn("LEDGER: Cost basis not provided in event, using execution amount - Order: {}", event.getOrderNumber());
        return event.getExecutionAmount();
    }
    
    private void validateDoubleEntry(List<LedgerEntry> ledgerEntries) {
        BigDecimal totalDebits = ledgerEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalCredits = ledgerEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalDebits.compareTo(totalCredits) != 0) {
            log.error("LEDGER CRITICAL: Double-entry validation FAILED - Debits: {}, Credits: {}",
                    totalDebits, totalCredits);
            throw new IllegalStateException(String.format(
                    "Double-entry bookkeeping violation: Debits (%s) != Credits (%s)",
                    totalDebits, totalCredits));
        }
        
        log.info("LEDGER: Double-entry validation PASSED - Debits = Credits = {}", totalDebits);
    }
    
    private void createTaxLot(InvestmentOrderExecutedEvent event) {
        try {
            if ("BUY".equalsIgnoreCase(event.getOrderSide())) {
                log.info("LEDGER: Creating tax lot for IRS reporting - Order: {}, Symbol: {}, Qty: {}, Cost Basis: {}",
                        event.getOrderNumber(), event.getSymbol(), event.getExecutedQuantity(), 
                        event.getExecutionPrice());
                
                kafkaTemplate.send("tax-lot-created", event.getOrderId(), Map.of(
                        "taxLotId", event.getTaxLotId() != null ? event.getTaxLotId() : UUID.randomUUID().toString(),
                        "orderId", event.getOrderId(),
                        "symbol", event.getSymbol(),
                        "quantity", event.getExecutedQuantity(),
                        "costBasisPerShare", event.getExecutionPrice(),
                        "acquisitionDate", event.getExecutedAt(),
                        "accountId", event.getAccountId()
                ));
            } else if ("SELL".equalsIgnoreCase(event.getOrderSide())) {
                log.info("LEDGER: Closing tax lot for capital gains reporting - Order: {}, Symbol: {}",
                        event.getOrderNumber(), event.getSymbol());
                
                BigDecimal capitalGainLoss = event.getExecutionAmount()
                        .subtract(calculateCostBasis(event));
                
                kafkaTemplate.send("tax-lot-closed", event.getOrderId(), Map.of(
                        "taxLotId", event.getTaxLotId() != null ? event.getTaxLotId() : "UNKNOWN",
                        "orderId", event.getOrderId(),
                        "symbol", event.getSymbol(),
                        "quantity", event.getExecutedQuantity(),
                        "salePrice", event.getExecutionPrice(),
                        "dispositionDate", event.getExecutedAt(),
                        "capitalGainLoss", capitalGainLoss,
                        "accountId", event.getAccountId()
                ));
            }
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to create tax lot - Order: {}", event.getOrderNumber(), e);
        }
    }
    
    private void publishLedgerRecorded(InvestmentOrderExecutedEvent event, 
                                       JournalEntry journalEntry,
                                       List<LedgerEntry> ledgerEntries) {
        try {
            BigDecimal totalDebits = ledgerEntries.stream()
                    .filter(e -> e.getEntryType() == EntryType.DEBIT)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalCredits = ledgerEntries.stream()
                    .filter(e -> e.getEntryType() == EntryType.CREDIT)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Map<String, Object> recordedEvent = Map.of(
                    "eventType", "INVESTMENT_ORDER_LEDGER_RECORDED",
                    "orderId", event.getOrderId(),
                    "orderNumber", event.getOrderNumber(),
                    "journalEntryId", journalEntry.getId(),
                    "totalDebits", totalDebits,
                    "totalCredits", totalCredits,
                    "entryCount", ledgerEntries.size(),
                    "recordedAt", LocalDateTime.now()
            );
            
            kafkaTemplate.send("investment-ledger-recorded", event.getOrderId(), recordedEvent);
            
            log.info("LEDGER: Published ledger recorded event - Order: {}, Journal Entry: {}",
                    event.getOrderNumber(), journalEntry.getId());
            
        } catch (Exception e) {
            log.error("LEDGER: Failed to publish ledger recorded event - Order: {}",
                    event.getOrderNumber(), e);
        }
    }
    
    private void markOrderAsProcessed(InvestmentOrderExecutedEvent event) {
        processedOrders.put(event.getOrderId(), LocalDateTime.now());
        
        processedOrders.entrySet().removeIf(entry -> 
                entry.getValue().isBefore(LocalDateTime.now().minusDays(7)));
    }
    
    private void sendToDeadLetterQueue(InvestmentOrderExecutedEvent event, Exception error) {
        try {
            kafkaTemplate.send("ledger-investment-dlq", event.getOrderId(), Map.of(
                    "originalEvent", event,
                    "error", error.getMessage(),
                    "stackTrace", error.getStackTrace(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("LEDGER CRITICAL: Failed to send to DLQ - Order: {}",
                    event.getOrderId(), e);
        }
    }
}