package com.waqiti.ledger.events.consumers;

import com.waqiti.ledger.domain.*;
import com.waqiti.ledger.repository.*;
import com.waqiti.ledger.service.*;
import com.waqiti.common.events.crypto.CryptoTransactionEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoTransactionEventsConsumer {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final CryptoLedgerRepository cryptoLedgerRepository;
    private final BalanceReconciliationService balanceReconciliationService;
    private final ComplianceReportingService complianceReportingService;
    private final EventProcessingTrackingService eventProcessingTrackingService;
    private final MeterRegistry meterRegistry;

    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter transactionTypesCounter;
    private Counter ledgerEntriesCreatedCounter;
    private Timer eventProcessingTimer;

    public CryptoTransactionEventsConsumer(
            LedgerEntryRepository ledgerEntryRepository,
            CryptoLedgerRepository cryptoLedgerRepository,
            BalanceReconciliationService balanceReconciliationService,
            ComplianceReportingService complianceReportingService,
            EventProcessingTrackingService eventProcessingTrackingService,
            MeterRegistry meterRegistry) {
        
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.cryptoLedgerRepository = cryptoLedgerRepository;
        this.balanceReconciliationService = balanceReconciliationService;
        this.complianceReportingService = complianceReportingService;
        this.eventProcessingTrackingService = eventProcessingTrackingService;
        this.meterRegistry = meterRegistry;

        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("crypto_transaction_events_processed_total")
                .description("Total number of crypto transaction events processed")
                .tag("consumer", "crypto-transaction-consumer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("crypto_transaction_events_failed_total")
                .description("Total number of crypto transaction events failed")
                .tag("consumer", "crypto-transaction-consumer")
                .register(meterRegistry);

        this.transactionTypesCounter = Counter.builder("crypto_transaction_types_total")
                .description("Total crypto transactions by type")
                .tag("consumer", "crypto-transaction-consumer")
                .register(meterRegistry);

        this.ledgerEntriesCreatedCounter = Counter.builder("crypto_ledger_entries_created_total")
                .description("Total crypto ledger entries created")
                .tag("consumer", "crypto-transaction-consumer")
                .register(meterRegistry);

        this.eventProcessingTimer = Timer.builder("crypto_transaction_event_processing_duration")
                .description("Time taken to process crypto transaction events")
                .tag("consumer", "crypto-transaction-consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = {"${kafka.topics.crypto-transaction:crypto-transaction}", 
                     "${kafka.topics.crypto-transaction-events:crypto-transaction-events}"},
            groupId = "${kafka.consumer.group-id:ledger-crypto-transaction-consumer-group}",
            containerFactory = "criticalFinancialKafkaListenerContainerFactory",
            concurrency = "${kafka.consumer.concurrency:5}"
    )
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
            include = {ServiceIntegrationException.class, Exception.class},
            exclude = {IllegalArgumentException.class},
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "true"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 60)
    public void handleCryptoTransaction(
            @Payload CryptoTransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId();

        try {
            log.info("Processing crypto transaction event: eventId={}, transactionId={}, userId={}, " +
                    "type={}, status={}, crypto={} {}, topic={}, partition={}, offset={}, correlationId={}",
                    event.getEventId(), event.getTransactionId(), event.getUserId(), 
                    event.getTransactionType(), event.getTransactionStatus(), 
                    event.getCryptoAmount(), event.getCryptocurrency(),
                    topic, partition, offset, correlationId);

            if (eventProcessingTrackingService.isEventAlreadyProcessed(event.getEventId(), "CRYPTO_TRANSACTION")) {
                log.warn("Duplicate crypto transaction event detected: eventId={}, correlationId={}. Skipping processing.",
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }

            validateEvent(event);

            createLedgerEntries(event, correlationId);

            if ("CONFIRMED".equals(event.getTransactionStatus())) {
                processConfirmedTransaction(event, correlationId);
            }

            if (event.getRequiresCompliance() || isHighValueTransaction(event)) {
                reportComplianceActivity(event, correlationId);
            }

            reconcileBalance(event, correlationId);

            eventProcessingTrackingService.markEventAsProcessed(
                    event.getEventId(),
                    "CRYPTO_TRANSACTION",
                    "ledger-service",
                    correlationId
            );

            eventsProcessedCounter.increment();
            Counter.builder("crypto_transaction_types_processed")
                    .tag("transaction_type", event.getTransactionType())
                    .tag("status", event.getTransactionStatus())
                    .register(meterRegistry)
                    .increment();

            acknowledgment.acknowledge();

            log.info("Successfully processed crypto transaction event: eventId={}, transactionId={}, correlationId={}",
                    event.getEventId(), event.getTransactionId(), correlationId);

        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process crypto transaction event: eventId={}, transactionId={}, correlationId={}, error={}",
                    event.getEventId(), event.getTransactionId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Crypto transaction event processing failed", e);
        } finally {
            sample.stop(eventProcessingTimer);
        }
    }

    private void validateEvent(CryptoTransactionEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }

        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (event.getTransactionType() == null || event.getTransactionType().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type is required");
        }

        if (event.getTransactionStatus() == null || event.getTransactionStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction status is required");
        }

        if (event.getCryptocurrency() == null || event.getCryptocurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Cryptocurrency is required");
        }

        if (event.getCryptoAmount() == null || event.getCryptoAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Crypto amount must be positive");
        }
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "createLedgerEntriesFallback")
    @Retry(name = "ledgerService")
    @TimeLimiter(name = "ledgerService")
    private void createLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        log.debug("Creating ledger entries for crypto transaction: transactionId={}, type={}, correlationId={}",
                event.getTransactionId(), event.getTransactionType(), correlationId);

        switch (event.getTransactionType()) {
            case "BUY":
                createBuyLedgerEntries(event, correlationId);
                break;
            case "SELL":
                createSellLedgerEntries(event, correlationId);
                break;
            case "SEND":
                createSendLedgerEntries(event, correlationId);
                break;
            case "RECEIVE":
                createReceiveLedgerEntries(event, correlationId);
                break;
            case "SWAP":
                createSwapLedgerEntries(event, correlationId);
                break;
            case "STAKE":
                createStakeLedgerEntries(event, correlationId);
                break;
            case "UNSTAKE":
                createUnstakeLedgerEntries(event, correlationId);
                break;
            default:
                log.warn("Unknown transaction type, creating generic ledger entry: type={}, transactionId={}",
                        event.getTransactionType(), event.getTransactionId());
                createGenericLedgerEntry(event, correlationId);
        }

        ledgerEntriesCreatedCounter.increment();

        log.debug("Ledger entries created: transactionId={}, type={}, correlationId={}",
                event.getTransactionId(), event.getTransactionType(), correlationId);
    }

    private void createLedgerEntriesFallback(CryptoTransactionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for ledger entries creation: transactionId={}, correlationId={}, error={}",
                event.getTransactionId(), correlationId, e.getMessage());
    }

    private void createBuyLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry debitFiat = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("FIAT_WALLET")
                .amount(event.getFiatAmount())
                .currency(event.getFiatCurrency())
                .description(String.format("Fiat payment for crypto purchase: %s %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        LedgerEntry creditCrypto = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("CREDIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto purchase: %s %s at rate %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol(), event.getExchangeRate()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.saveAll(List.of(debitFiat, creditCrypto));

        if (event.getPlatformFee() != null && event.getPlatformFee().compareTo(BigDecimal.ZERO) > 0) {
            createFeeLedgerEntry(event, event.getPlatformFee(), event.getFiatCurrency(), 
                    "Platform fee for crypto purchase", correlationId);
        }
    }

    private void createSellLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry debitCrypto = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto sale: %s %s at rate %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol(), event.getExchangeRate()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        LedgerEntry creditFiat = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("CREDIT")
                .accountType("FIAT_WALLET")
                .amount(event.getFiatAmount())
                .currency(event.getFiatCurrency())
                .description(String.format("Fiat received from crypto sale: %s %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.saveAll(List.of(debitCrypto, creditFiat));

        if (event.getPlatformFee() != null && event.getPlatformFee().compareTo(BigDecimal.ZERO) > 0) {
            createFeeLedgerEntry(event, event.getPlatformFee(), event.getFiatCurrency(), 
                    "Platform fee for crypto sale", correlationId);
        }
    }

    private void createSendLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry debit = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto sent: %s %s to %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol(), 
                        maskAddress(event.getDestinationAddress())))
                .transactionType(event.getTransactionType())
                .metadata(Map.of(
                        "blockchainNetwork", event.getBlockchainNetwork(),
                        "transactionHash", event.getTransactionHash() != null ? event.getTransactionHash() : "pending",
                        "destinationAddress", event.getDestinationAddress()
                ))
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.save(debit);

        if (event.getNetworkFee() != null && event.getNetworkFee().compareTo(BigDecimal.ZERO) > 0) {
            createFeeLedgerEntry(event, event.getNetworkFee(), event.getCryptocurrency(), 
                    "Network fee for crypto send", correlationId);
        }
    }

    private void createReceiveLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry credit = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("CREDIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto received: %s %s from %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol(), 
                        maskAddress(event.getSourceAddress())))
                .transactionType(event.getTransactionType())
                .metadata(Map.of(
                        "blockchainNetwork", event.getBlockchainNetwork(),
                        "transactionHash", event.getTransactionHash() != null ? event.getTransactionHash() : "pending",
                        "sourceAddress", event.getSourceAddress()
                ))
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.save(credit);
    }

    private void createSwapLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry debitFrom = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto swap from: %s %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.save(debitFrom);

        if (event.getPlatformFee() != null && event.getPlatformFee().compareTo(BigDecimal.ZERO) > 0) {
            createFeeLedgerEntry(event, event.getPlatformFee(), event.getCryptocurrency(), 
                    "Platform fee for crypto swap", correlationId);
        }
    }

    private void createStakeLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry debit = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto staked: %s %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        LedgerEntry credit = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("CREDIT")
                .accountType("CRYPTO_STAKING")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto staked: %s %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.saveAll(List.of(debit, credit));
    }

    private void createUnstakeLedgerEntries(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry debit = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("CRYPTO_STAKING")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto unstaked: %s %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        LedgerEntry credit = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("CREDIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto unstaked: %s %s", 
                        event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.saveAll(List.of(debit, credit));
    }

    private void createGenericLedgerEntry(CryptoTransactionEvent event, String correlationId) {
        LedgerEntry entry = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("CRYPTO_WALLET")
                .amount(event.getCryptoAmount())
                .currency(event.getCryptocurrency())
                .description(String.format("Crypto transaction: %s - %s %s", 
                        event.getTransactionType(), event.getCryptoAmount(), event.getCryptoSymbol()))
                .transactionType(event.getTransactionType())
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.save(entry);
    }

    private void createFeeLedgerEntry(CryptoTransactionEvent event, BigDecimal feeAmount, 
                                     String feeCurrency, String description, String correlationId) {
        LedgerEntry feeEntry = LedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .entryType("DEBIT")
                .accountType("FEE")
                .amount(feeAmount)
                .currency(feeCurrency)
                .description(description)
                .transactionType("FEE")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        ledgerEntryRepository.save(feeEntry);
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "processConfirmedTransactionFallback")
    @Retry(name = "ledgerService")
    @TimeLimiter(name = "ledgerService")
    private void processConfirmedTransaction(CryptoTransactionEvent event, String correlationId) {
        log.debug("Processing confirmed crypto transaction: transactionId={}, hash={}, confirmations={}, correlationId={}",
                event.getTransactionId(), event.getTransactionHash(), event.getConfirmations(), correlationId);

        CryptoLedgerEntry cryptoEntry = CryptoLedgerEntry.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .transactionType(event.getTransactionType())
                .transactionStatus("CONFIRMED")
                .cryptocurrency(event.getCryptocurrency())
                .cryptoSymbol(event.getCryptoSymbol())
                .cryptoAmount(event.getCryptoAmount())
                .fiatAmount(event.getFiatAmount())
                .fiatCurrency(event.getFiatCurrency())
                .exchangeRate(event.getExchangeRate())
                .transactionHash(event.getTransactionHash())
                .blockchainNetwork(event.getBlockchainNetwork())
                .confirmations(event.getConfirmations())
                .confirmedAt(LocalDateTime.now())
                .sourceAddress(event.getSourceAddress())
                .destinationAddress(event.getDestinationAddress())
                .networkFee(event.getNetworkFee())
                .platformFee(event.getPlatformFee())
                .correlationId(correlationId)
                .build();

        cryptoLedgerRepository.save(cryptoEntry);

        transactionTypesCounter.increment();

        log.debug("Confirmed crypto transaction recorded: transactionId={}, hash={}, correlationId={}",
                event.getTransactionId(), event.getTransactionHash(), correlationId);
    }

    private void processConfirmedTransactionFallback(CryptoTransactionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for confirmed transaction processing: transactionId={}, correlationId={}, error={}",
                event.getTransactionId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "complianceService", fallbackMethod = "reportComplianceActivityFallback")
    @Retry(name = "complianceService")
    @TimeLimiter(name = "complianceService")
    private void reportComplianceActivity(CryptoTransactionEvent event, String correlationId) {
        log.debug("Reporting compliance activity: transactionId={}, fiatAmount={}, correlationId={}",
                event.getTransactionId(), event.getFiatAmount(), correlationId);

        Map<String, Object> complianceData = new HashMap<>();
        complianceData.put("transactionId", event.getTransactionId());
        complianceData.put("userId", event.getUserId());
        complianceData.put("transactionType", event.getTransactionType());
        complianceData.put("cryptocurrency", event.getCryptocurrency());
        complianceData.put("cryptoAmount", event.getCryptoAmount());
        complianceData.put("fiatAmount", event.getFiatAmount());
        complianceData.put("fiatCurrency", event.getFiatCurrency());
        complianceData.put("transactionHash", event.getTransactionHash());
        complianceData.put("blockchainNetwork", event.getBlockchainNetwork());
        complianceData.put("sourceAddress", event.getSourceAddress());
        complianceData.put("destinationAddress", event.getDestinationAddress());
        complianceData.put("timestamp", event.getTimestamp());

        complianceReportingService.reportCryptoActivity(complianceData, correlationId);

        log.debug("Compliance activity reported: transactionId={}, correlationId={}",
                event.getTransactionId(), correlationId);
    }

    private void reportComplianceActivityFallback(CryptoTransactionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for compliance reporting: transactionId={}, correlationId={}, error={}",
                event.getTransactionId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "reconcileBalanceFallback")
    @Retry(name = "ledgerService")
    @TimeLimiter(name = "ledgerService")
    private void reconcileBalance(CryptoTransactionEvent event, String correlationId) {
        log.debug("Reconciling crypto balance: userId={}, cryptocurrency={}, correlationId={}",
                event.getUserId(), event.getCryptocurrency(), correlationId);

        if ("CONFIRMED".equals(event.getTransactionStatus())) {
            balanceReconciliationService.reconcileCryptoBalance(
                    event.getUserId(),
                    event.getCryptocurrency(),
                    correlationId
            );
        }

        log.debug("Crypto balance reconciliation completed: userId={}, cryptocurrency={}, correlationId={}",
                event.getUserId(), event.getCryptocurrency(), correlationId);
    }

    private void reconcileBalanceFallback(CryptoTransactionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for balance reconciliation: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    private boolean isHighValueTransaction(CryptoTransactionEvent event) {
        if (event.getFiatAmount() == null) {
            return false;
        }
        return event.getFiatAmount().compareTo(new BigDecimal("10000")) >= 0;
    }

    private String maskAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
}