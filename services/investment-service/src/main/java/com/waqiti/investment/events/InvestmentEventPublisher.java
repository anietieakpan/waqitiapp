package com.waqiti.investment.events;

import com.waqiti.investment.events.model.InvestmentEvent;
import com.waqiti.investment.events.model.DeadLetterInvestmentEvent;
import com.waqiti.investment.events.store.InvestmentEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise-grade investment event publisher with comprehensive reliability features
 * Features: DLQ handling, circuit breaker, event store, metrics, correlation tracking,
 * batch publishing, high-priority events, transactional publishing, event replay
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvestmentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final InvestmentEventStore eventStore;
    
    // Topic constants
    private static final String STOCK_ORDER_EVENTS_TOPIC = "stock-order-events";
    private static final String BOND_ORDER_EVENTS_TOPIC = "bond-order-events";
    private static final String MUTUAL_FUND_ORDER_EVENTS_TOPIC = "mutual-fund-order-events";
    private static final String ETF_ORDER_EVENTS_TOPIC = "etf-order-events";
    private static final String OPTIONS_ORDER_EVENTS_TOPIC = "options-order-events";
    private static final String CORPORATE_ACTION_EVENTS_TOPIC = "corporate-action-events";
    private static final String TAX_LOT_SELECTION_EVENTS_TOPIC = "tax-lot-selection-events";
    private static final String INVESTMENT_REPORTING_EVENTS_TOPIC = "investment-reporting-events";
    private static final String DLQ_SUFFIX = ".dlq";
    
    // Event tracking and circuit breaker
    private final Queue<InvestmentEvent> failedEvents = new ConcurrentLinkedQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 60 seconds
    private static final int FAILURE_THRESHOLD = 10;
    
    // Correlation tracking for sagas
    private final Map<String, EventCorrelation> correlationTracker = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter investmentEventsPublished;
    private Counter investmentEventsFailure;
    private Timer investmentEventPublishLatency;
    
    /**
     * Publishes stock order event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishStockOrder(
            String orderId, String accountId, String userId, String symbol, String orderType,
            String orderSide, BigDecimal quantity, BigDecimal price, String timeInForce,
            String orderStatus, Instant orderTimestamp) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("STOCK_ORDER_PLACED")
                .orderId(orderId)
                .accountId(accountId)
                .userId(userId)
                .symbol(symbol)
                .instrumentType("STOCK")
                .orderType(orderType)
                .orderSide(orderSide)
                .quantity(quantity)
                .price(price)
                .timeInForce(timeInForce)
                .status(orderStatus)
                .orderTimestamp(orderTimestamp)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(STOCK_ORDER_EVENTS_TOPIC, event, orderId);
    }
    
    /**
     * Publishes bond order event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishBondOrder(
            String orderId, String accountId, String userId, String cusip, String bondType,
            String orderSide, BigDecimal quantity, BigDecimal price, BigDecimal yieldToMaturity,
            String maturityDate, String creditRating, String orderStatus) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BOND_ORDER_PLACED")
                .orderId(orderId)
                .accountId(accountId)
                .userId(userId)
                .cusip(cusip)
                .instrumentType("BOND")
                .bondType(bondType)
                .orderSide(orderSide)
                .quantity(quantity)
                .price(price)
                .yieldToMaturity(yieldToMaturity)
                .maturityDate(maturityDate)
                .creditRating(creditRating)
                .status(orderStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(BOND_ORDER_EVENTS_TOPIC, event, orderId);
    }
    
    /**
     * Publishes mutual fund order event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishMutualFundOrder(
            String orderId, String accountId, String userId, String fundSymbol, String fundName,
            String orderSide, BigDecimal dollarAmount, BigDecimal shares, BigDecimal nav,
            String fundCategory, BigDecimal expenseRatio, String orderStatus) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MUTUAL_FUND_ORDER_PLACED")
                .orderId(orderId)
                .accountId(accountId)
                .userId(userId)
                .symbol(fundSymbol)
                .fundName(fundName)
                .instrumentType("MUTUAL_FUND")
                .orderSide(orderSide)
                .dollarAmount(dollarAmount)
                .shares(shares)
                .nav(nav)
                .fundCategory(fundCategory)
                .expenseRatio(expenseRatio)
                .status(orderStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(MUTUAL_FUND_ORDER_EVENTS_TOPIC, event, orderId);
    }
    
    /**
     * Publishes ETF order event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishETFOrder(
            String orderId, String accountId, String userId, String etfSymbol, String etfName,
            String orderType, String orderSide, BigDecimal quantity, BigDecimal price,
            String underlyingIndex, BigDecimal expenseRatio, String orderStatus) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ETF_ORDER_PLACED")
                .orderId(orderId)
                .accountId(accountId)
                .userId(userId)
                .symbol(etfSymbol)
                .etfName(etfName)
                .instrumentType("ETF")
                .orderType(orderType)
                .orderSide(orderSide)
                .quantity(quantity)
                .price(price)
                .underlyingIndex(underlyingIndex)
                .expenseRatio(expenseRatio)
                .status(orderStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(ETF_ORDER_EVENTS_TOPIC, event, orderId);
    }
    
    /**
     * Publishes options order event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishOptionsOrder(
            String orderId, String accountId, String userId, String underlyingSymbol,
            String optionType, BigDecimal strikePrice, String expirationDate, String orderSide,
            BigDecimal contracts, BigDecimal premium, String strategy, String orderStatus) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("OPTIONS_ORDER_PLACED")
                .orderId(orderId)
                .accountId(accountId)
                .userId(userId)
                .underlyingSymbol(underlyingSymbol)
                .instrumentType("OPTION")
                .optionType(optionType)
                .strikePrice(strikePrice)
                .expirationDate(expirationDate)
                .orderSide(orderSide)
                .contracts(contracts)
                .premium(premium)
                .strategy(strategy)
                .status(orderStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(OPTIONS_ORDER_EVENTS_TOPIC, event, orderId);
    }
    
    /**
     * Publishes corporate action event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishCorporateAction(
            String actionId, String accountId, String userId, String symbol, String actionType,
            String actionDescription, Instant effectiveDate, Instant payableDate,
            BigDecimal cashAmount, BigDecimal shareQuantity, String electionRequired) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CORPORATE_ACTION_RECEIVED")
                .actionId(actionId)
                .accountId(accountId)
                .userId(userId)
                .symbol(symbol)
                .actionType(actionType)
                .actionDescription(actionDescription)
                .effectiveDate(effectiveDate)
                .payableDate(payableDate)
                .cashAmount(cashAmount)
                .shareQuantity(shareQuantity)
                .electionRequired(electionRequired)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("PENDING")
                .build();
        
        return publishHighPriorityEvent(CORPORATE_ACTION_EVENTS_TOPIC, event, actionId);
    }
    
    /**
     * Publishes tax lot selection event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishTaxLotSelection(
            String selectionId, String accountId, String userId, String orderId, String symbol,
            String selectionMethod, BigDecimal quantitySold, BigDecimal realizedGainLoss,
            BigDecimal shortTermGain, BigDecimal longTermGain, String taxYear) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TAX_LOT_SELECTION_MADE")
                .selectionId(selectionId)
                .accountId(accountId)
                .userId(userId)
                .orderId(orderId)
                .symbol(symbol)
                .selectionMethod(selectionMethod)
                .quantitySold(quantitySold)
                .realizedGainLoss(realizedGainLoss)
                .shortTermGain(shortTermGain)
                .longTermGain(longTermGain)
                .taxYear(taxYear)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .status("PROCESSED")
                .build();
        
        return publishEvent(TAX_LOT_SELECTION_EVENTS_TOPIC, event, selectionId);
    }
    
    /**
     * Publishes investment report event
     */
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<SendResult<String, Object>> publishInvestmentReport(
            String reportId, String accountId, String userId, String reportType, String reportFormat,
            Instant reportPeriodStart, Instant reportPeriodEnd, BigDecimal portfolioValue,
            BigDecimal totalGainLoss, BigDecimal dividendIncome, String reportStatus) {
        
        InvestmentEvent event = InvestmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INVESTMENT_REPORT_GENERATED")
                .reportId(reportId)
                .accountId(accountId)
                .userId(userId)
                .reportType(reportType)
                .reportFormat(reportFormat)
                .reportPeriodStart(reportPeriodStart)
                .reportPeriodEnd(reportPeriodEnd)
                .portfolioValue(portfolioValue)
                .totalGainLoss(totalGainLoss)
                .dividendIncome(dividendIncome)
                .status(reportStatus)
                .timestamp(Instant.now())
                .correlationId(getCorrelationIdFromContext())
                .version("1.0")
                .build();
        
        return publishEvent(INVESTMENT_REPORTING_EVENTS_TOPIC, event, reportId);
    }
    
    /**
     * Publishes batch events for bulk operations
     */
    @Transactional
    public CompletableFuture<List<SendResult<String, Object>>> publishBatchEvents(
            List<InvestmentEvent> events) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Group events by topic for optimized publishing
        Map<String, List<InvestmentEvent>> eventsByTopic = groupEventsByTopic(events);
        
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, List<InvestmentEvent>> entry : eventsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<InvestmentEvent> topicEvents = entry.getValue();
            
            for (InvestmentEvent event : topicEvents) {
                CompletableFuture<SendResult<String, Object>> future = 
                    publishEvent(topic, event, getKeyForEvent(event));
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()))
            .whenComplete((result, ex) -> {
                sample.stop(getInvestmentEventPublishLatency());
                if (ex == null) {
                    log.info("Batch investment events published: count={}, topics={}", 
                        events.size(), eventsByTopic.keySet());
                    getInvestmentEventsPublished().increment(events.size());
                } else {
                    log.error("Failed to publish batch investment events", ex);
                    getInvestmentEventsFailure().increment(events.size());
                }
            });
    }
    
    /**
     * Core event publishing method with reliability features
     */
    private CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic, InvestmentEvent event, String key) {
        
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker open, queueing investment event: {}", event.getEventType());
            queueFailedEvent(event);
            return CompletableFuture.failedFuture(
                new RuntimeException("Circuit breaker open for investment events")
            );
        }
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Store event first for durability
            eventStore.storeEvent(event);
            
            // Create Kafka record with headers
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);
            
            // Publish with callback
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(record).toCompletableFuture();
            
            future.whenComplete((sendResult, ex) -> {
                sample.stop(getInvestmentEventPublishLatency());
                
                if (ex == null) {
                    onPublishSuccess(event, sendResult);
                } else {
                    onPublishFailure(event, topic, key, ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            sample.stop(getInvestmentEventPublishLatency());
            log.error("Failed to publish investment event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publishes high-priority events with immediate delivery
     */
    private CompletableFuture<SendResult<String, Object>> publishHighPriorityEvent(
            String topic, InvestmentEvent event, String key) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Store event first
            eventStore.storeEvent(event);
            
            // Create record with high priority header
            ProducerRecord<String, Object> record = createKafkaRecord(topic, key, event);
            record.headers().add("priority", "HIGH".getBytes(StandardCharsets.UTF_8));
            
            // Synchronous send with timeout for high-priority events
            SendResult<String, Object> result = kafkaTemplate.send(record)
                .get(5, TimeUnit.SECONDS);
            
            sample.stop(getInvestmentEventPublishLatency());
            onPublishSuccess(event, result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            sample.stop(getInvestmentEventPublishLatency());
            log.error("Failed to publish high-priority investment event: {}", event.getEventType(), e);
            onPublishFailure(event, topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Event replay capability for recovering failed events
     */
    public CompletableFuture<Void> replayFailedEvents() {
        if (failedEvents.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Replaying {} failed investment events", failedEvents.size());
        
        List<CompletableFuture<SendResult<String, Object>>> replayFutures = new ArrayList<>();
        
        InvestmentEvent event;
        while ((event = failedEvents.poll()) != null) {
            String topic = getTopicForEventType(event.getEventType());
            CompletableFuture<SendResult<String, Object>> future = 
                publishEvent(topic, event, getKeyForEvent(event));
            replayFutures.add(future);
        }
        
        return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Completed investment event replay"));
    }
    
    // Helper methods (similar structure to previous publishers)
    
    private void onPublishSuccess(InvestmentEvent event, SendResult<String, Object> result) {
        publishedCount.incrementAndGet();
        getInvestmentEventsPublished().increment();
        
        // Track correlation for saga patterns
        if (event.getCorrelationId() != null) {
            trackEventCorrelation(event);
        }
        
        log.debug("Investment event published successfully: type={}, accountId={}, offset={}", 
            event.getEventType(), event.getAccountId(), result.getRecordMetadata().offset());
    }
    
    private void onPublishFailure(InvestmentEvent event, String topic, String key, Throwable ex) {
        long currentFailureCount = failedCount.incrementAndGet();
        getInvestmentEventsFailure().increment();
        
        log.error("Failed to publish investment event: type={}, accountId={}, topic={}", 
            event.getEventType(), event.getAccountId(), topic, ex);
        
        // Circuit breaker logic
        if (currentFailureCount >= FAILURE_THRESHOLD) {
            openCircuitBreaker();
        }
        
        // Queue for retry
        queueFailedEvent(event);
        
        // Send to DLQ
        publishToDeadLetterQueue(topic, event, key, ex);
    }
    
    private void queueFailedEvent(InvestmentEvent event) {
        failedEvents.offer(event);
        log.warn("Queued failed investment event for retry: type={}, accountId={}", 
            event.getEventType(), event.getAccountId());
    }
    
    private boolean isCircuitBreakerOpen() {
        if (!circuitBreakerOpen) {
            return false;
        }
        
        if (System.currentTimeMillis() - circuitBreakerOpenTime > CIRCUIT_BREAKER_TIMEOUT) {
            closeCircuitBreaker();
            return false;
        }
        
        return true;
    }
    
    private void openCircuitBreaker() {
        circuitBreakerOpen = true;
        circuitBreakerOpenTime = System.currentTimeMillis();
        log.error("Investment event publisher circuit breaker OPENED due to high failure rate");
    }
    
    private void closeCircuitBreaker() {
        circuitBreakerOpen = false;
        circuitBreakerOpenTime = 0;
        failedCount.set(0);
        log.info("Investment event publisher circuit breaker CLOSED");
    }
    
    private void publishToDeadLetterQueue(String originalTopic, InvestmentEvent event, 
                                        String key, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;
        
        try {
            DeadLetterInvestmentEvent dlqEvent = DeadLetterInvestmentEvent.builder()
                .originalEvent(event)
                .originalTopic(originalTopic)
                .errorMessage(error.getMessage())
                .failureTimestamp(Instant.now())
                .retryCount(1)
                .build();
            
            ProducerRecord<String, Object> dlqRecord = createKafkaRecord(dlqTopic, key, dlqEvent);
            dlqRecord.headers().add("original-topic", originalTopic.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("error-message", error.getMessage().getBytes(StandardCharsets.UTF_8));
            
            kafkaTemplate.send(dlqRecord).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.warn("Investment event sent to DLQ: topic={}, eventType={}", 
                        dlqTopic, event.getEventType());
                } else {
                    log.error("Failed to send investment event to DLQ", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to publish investment event to dead letter queue", e);
        }
    }
    
    private ProducerRecord<String, Object> createKafkaRecord(String topic, String key, Object event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        
        // Add standard headers
        record.headers().add("event-type", 
            event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", 
            String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        record.headers().add("service", "investment-service".getBytes(StandardCharsets.UTF_8));
        
        return record;
    }
    
    private void trackEventCorrelation(InvestmentEvent event) {
        correlationTracker.put(event.getCorrelationId(),
            new EventCorrelation(event.getCorrelationId(), event.getAccountId(), 
                event.getEventType(), Instant.now()));
        
        // Clean up old correlations (older than 24 hours)
        Instant cutoff = Instant.now().minusSeconds(86400);
        correlationTracker.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp().isBefore(cutoff));
    }
    
    private Map<String, List<InvestmentEvent>> groupEventsByTopic(List<InvestmentEvent> events) {
        return events.stream()
            .collect(Collectors.groupingBy(event -> getTopicForEventType(event.getEventType())));
    }
    
    private String getTopicForEventType(String eventType) {
        switch (eventType) {
            case "STOCK_ORDER_PLACED":
                return STOCK_ORDER_EVENTS_TOPIC;
            case "BOND_ORDER_PLACED":
                return BOND_ORDER_EVENTS_TOPIC;
            case "MUTUAL_FUND_ORDER_PLACED":
                return MUTUAL_FUND_ORDER_EVENTS_TOPIC;
            case "ETF_ORDER_PLACED":
                return ETF_ORDER_EVENTS_TOPIC;
            case "OPTIONS_ORDER_PLACED":
                return OPTIONS_ORDER_EVENTS_TOPIC;
            case "CORPORATE_ACTION_RECEIVED":
                return CORPORATE_ACTION_EVENTS_TOPIC;
            case "TAX_LOT_SELECTION_MADE":
                return TAX_LOT_SELECTION_EVENTS_TOPIC;
            case "INVESTMENT_REPORT_GENERATED":
                return INVESTMENT_REPORTING_EVENTS_TOPIC;
            default:
                throw new IllegalArgumentException("Unknown investment event type: " + eventType);
        }
    }
    
    private String getKeyForEvent(InvestmentEvent event) {
        if (event.getOrderId() != null) return event.getOrderId();
        if (event.getActionId() != null) return event.getActionId();
        if (event.getSelectionId() != null) return event.getSelectionId();
        if (event.getReportId() != null) return event.getReportId();
        return event.getAccountId();
    }
    
    private String getCorrelationIdFromContext() {
        // Implementation would extract correlation ID from thread local or request context
        return "investment-corr-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    // Lazy initialization of metrics to avoid circular dependencies
    private Counter getInvestmentEventsPublished() {
        if (investmentEventsPublished == null) {
            investmentEventsPublished = Counter.builder("investment.events.published")
                .description("Number of investment events published")
                .register(meterRegistry);
        }
        return investmentEventsPublished;
    }
    
    private Counter getInvestmentEventsFailure() {
        if (investmentEventsFailure == null) {
            investmentEventsFailure = Counter.builder("investment.events.failure")
                .description("Number of investment events that failed to publish")
                .register(meterRegistry);
        }
        return investmentEventsFailure;
    }
    
    private Timer getInvestmentEventPublishLatency() {
        if (investmentEventPublishLatency == null) {
            investmentEventPublishLatency = Timer.builder("investment.events.publish.latency")
                .description("Latency of investment event publishing")
                .register(meterRegistry);
        }
        return investmentEventPublishLatency;
    }
    
    /**
     * Event correlation tracking for saga patterns
     */
    private static class EventCorrelation {
        private final String correlationId;
        private final String accountId;
        private final String eventType;
        private final Instant timestamp;
        
        public EventCorrelation(String correlationId, String accountId, String eventType, Instant timestamp) {
            this.correlationId = correlationId;
            this.accountId = accountId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
}