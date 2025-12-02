package com.waqiti.investment.kafka;

import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.InvestmentHolding;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.repository.InvestmentHoldingRepository;
import com.waqiti.investment.service.InvestmentService;
import com.waqiti.investment.service.PortfolioService;
import com.waqiti.investment.service.OrderExecutionService;
import com.waqiti.investment.service.AdvancedTradingService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class OptionsOrderEventsConsumer {

    private final InvestmentOrderRepository orderRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final InvestmentService investmentService;
    private final PortfolioService portfolioService;
    private final OrderExecutionService orderExecutionService;
    private final AdvancedTradingService advancedTradingService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    // DEPRECATED: In-memory cache - replaced by persistent IdempotencyService
    // Kept for backwards compatibility during migration, will be removed
    @Deprecated
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("options_orders_processed_total")
            .description("Total number of successfully processed options order events")
            .register(meterRegistry);
        errorCounter = Counter.builder("options_orders_errors_total")
            .description("Total number of options order processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("options_orders_processing_duration")
            .description("Time taken to process options order events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"options-order-events", "options-exercise", "options-expiration", "options-assignment"},
        groupId = "options-order-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "options-orders", fallbackMethod = "handleOptionsOrderEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleOptionsOrderEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventType = (String) eventData.get("eventType");
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String optionSymbol = (String) eventData.get("optionSymbol");
        String correlationId = String.format("opt-%s-p%d-o%d", orderId, partition, offset);

        // CRITICAL SECURITY: Enhanced idempotency key with all unique identifiers
        String idempotencyKey = String.format("options-order:%s:%s:%s:%s",
            orderId, eventType, optionSymbol, eventData.get("timestamp"));
        UUID operationId = UUID.randomUUID();

        try {
            // CRITICAL SECURITY: Persistent idempotency check (survives service restarts)
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate options order event ignored - already processed: orderId={}, eventType={}, idempotencyKey={}",
                        orderId, eventType, idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("SECURITY: Processing new options order with persistent idempotency: orderId={}, eventType={}, topic={}, idempotencyKey={}",
                orderId, eventType, topic, idempotencyKey);

            // DEPRECATED: Old in-memory cleanup (will be removed)
            cleanExpiredEntries();

            switch (eventType != null ? eventType.toUpperCase() : "UNKNOWN") {
                case "CALL_ORDER":
                    processCallOptionOrder(eventData, correlationId);
                    break;

                case "PUT_ORDER":
                    processPutOptionOrder(eventData, correlationId);
                    break;

                case "OPTION_EXERCISE":
                    processOptionExercise(eventData, correlationId);
                    break;

                case "OPTION_ASSIGNMENT":
                    processOptionAssignment(eventData, correlationId);
                    break;

                case "OPTION_EXPIRATION":
                    processOptionExpiration(eventData, correlationId);
                    break;

                case "SPREAD_ORDER":
                    processSpreadOrder(eventData, correlationId);
                    break;

                case "COVERED_CALL":
                    processCoveredCall(eventData, correlationId);
                    break;

                case "PROTECTIVE_PUT":
                    processProtectivePut(eventData, correlationId);
                    break;

                case "STRADDLE":
                case "STRANGLE":
                    processVolatilityStrategy(eventData, correlationId);
                    break;

                case "IRON_CONDOR":
                case "BUTTERFLY":
                    processComplexStrategy(eventData, correlationId);
                    break;

                default:
                    log.warn("Unknown options order event type: {}", eventType);
                    processGenericOptionsEvent(eventData, correlationId);
                    break;
            }

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("orderId", orderId, "eventType", eventType,
                       "accountId", accountId, "optionSymbol", optionSymbol,
                       "correlationId", correlationId, "topic", topic,
                       "status", "COMPLETED"), Duration.ofDays(7));

            // DEPRECATED: Old in-memory tracking (will be removed)
            String eventKey = String.format("%s-%s-%s", orderId, eventType, eventData.get("timestamp"));
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("OPTIONS_ORDER_EVENT_PROCESSED",
                accountId,
                Map.of("orderId", orderId, "eventType", eventType,
                    "optionSymbol", optionSymbol,
                    "correlationId", correlationId, "topic", topic,
                    "idempotencyKey", idempotencyKey,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("SECURITY: Failed to process options order event: {}", e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed in persistent storage for retry logic
            idempotencyService.failOperation(idempotencyKey, operationId,
                String.format("Options order failed: %s", e.getMessage()));

            // Send fallback event
            kafkaTemplate.send("options-order-fallback-events", Map.of(
                "originalEvent", eventData, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleOptionsOrderEventFallback(
            Map<String, Object> eventData,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String orderId = (String) eventData.get("orderId");
        String correlationId = String.format("opt-fallback-%s-p%d-o%d", orderId, partition, offset);

        log.error("Circuit breaker fallback triggered for options order: orderId={}, error={}",
            orderId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("options-order-dlq", Map.of(
            "originalEvent", eventData,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Options Order Circuit Breaker Triggered",
                String.format("Options order %s failed: %s", orderId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltOptionsOrderEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String orderId = (String) eventData.get("orderId");
        String correlationId = String.format("dlt-opt-%s-%d", orderId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Options order permanently failed: orderId={}, topic={}, error={}",
            orderId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("OPTIONS_ORDER_DLT_EVENT",
            (String) eventData.get("accountId"),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "orderId", orderId, "eventType", eventData.get("eventType"),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Options Order Dead Letter Event",
                String.format("Options order %s sent to DLT: %s", orderId, exceptionMessage),
                Map.of("orderId", orderId, "eventType", eventData.get("eventType"),
                       "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processCallOptionOrder(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String optionSymbol = (String) eventData.get("optionSymbol");
        String underlyingSymbol = (String) eventData.get("underlyingSymbol");
        BigDecimal strikePrice = new BigDecimal(eventData.get("strikePrice").toString());
        LocalDate expirationDate = LocalDate.parse((String) eventData.get("expirationDate"));
        BigDecimal contracts = new BigDecimal(eventData.get("contracts").toString());
        String orderAction = (String) eventData.get("orderAction"); // BUY_TO_OPEN, SELL_TO_CLOSE, etc.

        // Create call option order
        InvestmentOrder order = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(optionSymbol)
            .orderType("MARKET")
            .orderSide(getOrderSide(orderAction))
            .quantity(contracts)
            .assetType("CALL_OPTION")
            .status("PENDING_EXECUTION")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(order);

        // Validate options trading eligibility
        validateOptionsEligibility(accountId, orderAction, correlationId);

        // Calculate margin requirements
        calculateMarginRequirement(accountId, optionSymbol, contracts, orderAction, correlationId);

        // Validate position limits
        validatePositionLimits(accountId, underlyingSymbol, contracts, correlationId);

        // Calculate Greeks and risk metrics
        calculateOptionsRisk(optionSymbol, contracts, correlationId);

        // Send execution to market
        kafkaTemplate.send("options-execution-requests", Map.of(
            "orderId", orderId,
            "optionSymbol", optionSymbol,
            "orderAction", orderAction,
            "contracts", contracts,
            "strikePrice", strikePrice,
            "expirationDate", expirationDate,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send notification
        notificationService.sendNotification(userId, "Call Option Order Submitted",
            String.format("Your call option order for %s contracts of %s has been submitted.", contracts, optionSymbol),
            correlationId);

        log.info("Call option order processed: orderId={}, symbol={}, contracts={}, strike={}",
            orderId, optionSymbol, contracts, strikePrice);
    }

    private void processPutOptionOrder(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String optionSymbol = (String) eventData.get("optionSymbol");
        String underlyingSymbol = (String) eventData.get("underlyingSymbol");
        BigDecimal strikePrice = new BigDecimal(eventData.get("strikePrice").toString());
        LocalDate expirationDate = LocalDate.parse((String) eventData.get("expirationDate"));
        BigDecimal contracts = new BigDecimal(eventData.get("contracts").toString());
        String orderAction = (String) eventData.get("orderAction");

        // Create put option order
        InvestmentOrder order = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(optionSymbol)
            .orderType("MARKET")
            .orderSide(getOrderSide(orderAction))
            .quantity(contracts)
            .assetType("PUT_OPTION")
            .status("PENDING_EXECUTION")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(order);

        // Validate options trading eligibility
        validateOptionsEligibility(accountId, orderAction, correlationId);

        // Calculate margin requirements
        calculateMarginRequirement(accountId, optionSymbol, contracts, orderAction, correlationId);

        // Send notification
        notificationService.sendNotification(userId, "Put Option Order Submitted",
            String.format("Your put option order for %s contracts of %s has been submitted.", contracts, optionSymbol),
            correlationId);

        log.info("Put option order processed: orderId={}, symbol={}, contracts={}, strike={}",
            orderId, optionSymbol, contracts, strikePrice);
    }

    private void processOptionExercise(Map<String, Object> eventData, String correlationId) {
        String exerciseId = (String) eventData.get("exerciseId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String optionSymbol = (String) eventData.get("optionSymbol");
        String underlyingSymbol = (String) eventData.get("underlyingSymbol");
        BigDecimal contracts = new BigDecimal(eventData.get("contracts").toString());
        BigDecimal strikePrice = new BigDecimal(eventData.get("strikePrice").toString());
        String optionType = (String) eventData.get("optionType"); // CALL or PUT
        String exerciseType = (String) eventData.get("exerciseType"); // AMERICAN or EUROPEAN

        // Validate option holding
        InvestmentHolding optionHolding = holdingRepository.findByAccountIdAndSymbol(accountId, optionSymbol)
            .orElseThrow(() -> new RuntimeException("Option holding not found for exercise: " + optionSymbol));

        if (optionHolding.getQuantity().compareTo(contracts) < 0) {
            throw new RuntimeException("Insufficient option contracts for exercise");
        }

        // Process exercise based on option type
        if ("CALL".equals(optionType)) {
            processCallExercise(accountId, userId, underlyingSymbol, contracts, strikePrice, correlationId);
        } else if ("PUT".equals(optionType)) {
            processPutExercise(accountId, userId, underlyingSymbol, contracts, strikePrice, correlationId);
        }

        // Update option position
        portfolioService.updateOptionPositionAfterExercise(accountId, optionSymbol, contracts);

        // Send exercise confirmation
        notificationService.sendNotification(userId, "Option Exercise Processed",
            String.format("Your %s option exercise for %s contracts has been processed.", optionType, contracts),
            correlationId);

        log.info("Option exercise processed: exerciseId={}, symbol={}, contracts={}, type={}",
            exerciseId, optionSymbol, contracts, optionType);
    }

    private void processOptionAssignment(Map<String, Object> eventData, String correlationId) {
        String assignmentId = (String) eventData.get("assignmentId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String optionSymbol = (String) eventData.get("optionSymbol");
        String underlyingSymbol = (String) eventData.get("underlyingSymbol");
        BigDecimal contracts = new BigDecimal(eventData.get("contracts").toString());
        BigDecimal strikePrice = new BigDecimal(eventData.get("strikePrice").toString());
        String optionType = (String) eventData.get("optionType");

        // Process assignment based on option type
        if ("CALL".equals(optionType)) {
            processCallAssignment(accountId, userId, underlyingSymbol, contracts, strikePrice, correlationId);
        } else if ("PUT".equals(optionType)) {
            processPutAssignment(accountId, userId, underlyingSymbol, contracts, strikePrice, correlationId);
        }

        // Update option position
        portfolioService.updateOptionPositionAfterAssignment(accountId, optionSymbol, contracts);

        // Send assignment notification
        notificationService.sendNotification(userId, "Option Assignment Notice",
            String.format("You have been assigned on %s contracts of %s %s options.", contracts, optionSymbol, optionType),
            correlationId);

        log.info("Option assignment processed: assignmentId={}, symbol={}, contracts={}, type={}",
            assignmentId, optionSymbol, contracts, optionType);
    }

    private void processOptionExpiration(Map<String, Object> eventData, String correlationId) {
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String optionSymbol = (String) eventData.get("optionSymbol");
        LocalDate expirationDate = LocalDate.parse((String) eventData.get("expirationDate"));
        String expirationStatus = (String) eventData.get("expirationStatus"); // EXPIRED_WORTHLESS, AUTO_EXERCISED

        // Process expiration
        portfolioService.processOptionExpiration(accountId, optionSymbol, expirationStatus);

        // Send expiration notification
        String message = "EXPIRED_WORTHLESS".equals(expirationStatus) ?
            String.format("Your %s options have expired worthless on %s.", optionSymbol, expirationDate) :
            String.format("Your %s options have been auto-exercised on %s.", optionSymbol, expirationDate);

        notificationService.sendNotification(userId, "Option Expiration Notice", message, correlationId);

        log.info("Option expiration processed: symbol={}, expirationDate={}, status={}",
            optionSymbol, expirationDate, expirationStatus);
    }

    private void processSpreadOrder(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String spreadType = (String) eventData.get("spreadType"); // BULL_CALL, BEAR_PUT, etc.

        // Process complex spread strategy
        advancedTradingService.processSpreadOrder(eventData, correlationId);

        log.info("Spread order processed: orderId={}, spreadType={}", orderId, spreadType);
    }

    private void processCoveredCall(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String underlyingSymbol = (String) eventData.get("underlyingSymbol");
        BigDecimal shares = new BigDecimal(eventData.get("shares").toString());

        // Validate sufficient stock holdings for covered call
        InvestmentHolding stockHolding = holdingRepository.findByAccountIdAndSymbol(accountId, underlyingSymbol)
            .orElseThrow(() -> new RuntimeException("No stock holding found for covered call: " + underlyingSymbol));

        if (stockHolding.getQuantity().compareTo(shares) < 0) {
            throw new RuntimeException("Insufficient shares for covered call strategy");
        }

        advancedTradingService.processCoveredCall(eventData, correlationId);

        log.info("Covered call processed: orderId={}, underlying={}, shares={}", orderId, underlyingSymbol, shares);
    }

    private void processProtectivePut(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");

        advancedTradingService.processProtectivePut(eventData, correlationId);

        log.info("Protective put processed: orderId={}", orderId);
    }

    private void processVolatilityStrategy(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String strategyType = (String) eventData.get("eventType");

        advancedTradingService.processVolatilityStrategy(eventData, correlationId);

        log.info("Volatility strategy processed: orderId={}, strategy={}", orderId, strategyType);
    }

    private void processComplexStrategy(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String strategyType = (String) eventData.get("eventType");

        advancedTradingService.processComplexStrategy(eventData, correlationId);

        log.info("Complex strategy processed: orderId={}, strategy={}", orderId, strategyType);
    }

    private void processGenericOptionsEvent(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String eventType = (String) eventData.get("eventType");

        log.info("Processing generic options event: orderId={}, eventType={}", orderId, eventType);

        // Store the event for manual processing
        auditService.logAccountEvent("GENERIC_OPTIONS_EVENT",
            (String) eventData.get("accountId"),
            Map.of("eventType", eventType, "eventData", eventData,
                "correlationId", correlationId, "requiresManualProcessing", true,
                "timestamp", Instant.now()));
    }

    private void validateOptionsEligibility(String accountId, String orderAction, String correlationId) {
        try {
            investmentService.validateOptionsEligibility(accountId, orderAction);
        } catch (Exception e) {
            log.error("Options eligibility validation failed: {}", e.getMessage());
            kafkaTemplate.send("compliance-violations", Map.of(
                "accountId", accountId,
                "violationType", "OPTIONS_ELIGIBILITY",
                "orderAction", orderAction,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void calculateMarginRequirement(String accountId, String optionSymbol, BigDecimal contracts, String orderAction, String correlationId) {
        kafkaTemplate.send("margin-calculation-requests", Map.of(
            "accountId", accountId,
            "optionSymbol", optionSymbol,
            "contracts", contracts,
            "orderAction", orderAction,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void validatePositionLimits(String accountId, String underlyingSymbol, BigDecimal contracts, String correlationId) {
        try {
            portfolioService.validateOptionsPositionLimits(accountId, underlyingSymbol, contracts);
        } catch (Exception e) {
            log.error("Position limit validation failed: {}", e.getMessage());
        }
    }

    private void calculateOptionsRisk(String optionSymbol, BigDecimal contracts, String correlationId) {
        kafkaTemplate.send("options-risk-calculation", Map.of(
            "optionSymbol", optionSymbol,
            "contracts", contracts,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processCallExercise(String accountId, String userId, String underlyingSymbol, BigDecimal contracts, BigDecimal strikePrice, String correlationId) {
        // Buy underlying shares at strike price
        BigDecimal shares = contracts.multiply(BigDecimal.valueOf(100)); // 100 shares per contract
        BigDecimal totalCost = shares.multiply(strikePrice);

        portfolioService.addStockPosition(accountId, underlyingSymbol, shares, strikePrice, totalCost);

        log.info("Call exercise completed: accountId={}, symbol={}, shares={}, strikePrice={}",
            accountId, underlyingSymbol, shares, strikePrice);
    }

    private void processPutExercise(String accountId, String userId, String underlyingSymbol, BigDecimal contracts, BigDecimal strikePrice, String correlationId) {
        // Sell underlying shares at strike price
        BigDecimal shares = contracts.multiply(BigDecimal.valueOf(100)); // 100 shares per contract
        BigDecimal totalValue = shares.multiply(strikePrice);

        portfolioService.reduceStockPosition(accountId, underlyingSymbol, shares, strikePrice, totalValue);

        log.info("Put exercise completed: accountId={}, symbol={}, shares={}, strikePrice={}",
            accountId, underlyingSymbol, shares, strikePrice);
    }

    private void processCallAssignment(String accountId, String userId, String underlyingSymbol, BigDecimal contracts, BigDecimal strikePrice, String correlationId) {
        // Sell underlying shares at strike price (for short call)
        BigDecimal shares = contracts.multiply(BigDecimal.valueOf(100));
        BigDecimal totalValue = shares.multiply(strikePrice);

        portfolioService.reduceStockPosition(accountId, underlyingSymbol, shares, strikePrice, totalValue);

        log.info("Call assignment completed: accountId={}, symbol={}, shares={}, strikePrice={}",
            accountId, underlyingSymbol, shares, strikePrice);
    }

    private void processPutAssignment(String accountId, String userId, String underlyingSymbol, BigDecimal contracts, BigDecimal strikePrice, String correlationId) {
        // Buy underlying shares at strike price (for short put)
        BigDecimal shares = contracts.multiply(BigDecimal.valueOf(100));
        BigDecimal totalCost = shares.multiply(strikePrice);

        portfolioService.addStockPosition(accountId, underlyingSymbol, shares, strikePrice, totalCost);

        log.info("Put assignment completed: accountId={}, symbol={}, shares={}, strikePrice={}",
            accountId, underlyingSymbol, shares, strikePrice);
    }

    private String getOrderSide(String orderAction) {
        if (orderAction.contains("BUY")) {
            return "BUY";
        } else if (orderAction.contains("SELL")) {
            return "SELL";
        }
        return "UNKNOWN";
    }
}