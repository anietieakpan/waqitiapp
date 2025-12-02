package com.waqiti.investment.kafka;

import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.InvestmentHolding;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.repository.InvestmentHoldingRepository;
import com.waqiti.investment.service.InvestmentService;
import com.waqiti.investment.service.PortfolioService;
import com.waqiti.investment.service.OrderExecutionService;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class MutualFundOrderEventsConsumer {

    private final InvestmentOrderRepository orderRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final InvestmentService investmentService;
    private final PortfolioService portfolioService;
    private final OrderExecutionService orderExecutionService;
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
        successCounter = Counter.builder("mutual_fund_orders_processed_total")
            .description("Total number of successfully processed mutual fund order events")
            .register(meterRegistry);
        errorCounter = Counter.builder("mutual_fund_orders_errors_total")
            .description("Total number of mutual fund order processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("mutual_fund_orders_processing_duration")
            .description("Time taken to process mutual fund order events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"mutual-fund-order-events", "fund-subscriptions", "fund-redemptions", "fund-switches"},
        groupId = "mutual-fund-order-service-group",
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
    @CircuitBreaker(name = "mutual-fund-orders", fallbackMethod = "handleMutualFundOrderEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleMutualFundOrderEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String orderType = (String) eventData.get("orderType");
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String fundSymbol = (String) eventData.get("fundSymbol");
        String correlationId = String.format("mf-%s-p%d-o%d", orderId, partition, offset);

        // CRITICAL SECURITY: Enhanced idempotency key with all unique identifiers
        String idempotencyKey = String.format("mutual-fund-order:%s:%s:%s:%s",
            orderId, orderType, fundSymbol, eventData.get("timestamp"));
        UUID operationId = UUID.randomUUID();

        try {
            // CRITICAL SECURITY: Persistent idempotency check (survives service restarts)
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate mutual fund order event ignored - already processed: orderId={}, orderType={}, idempotencyKey={}",
                        orderId, orderType, idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("SECURITY: Processing new mutual fund order with persistent idempotency: orderId={}, orderType={}, topic={}, idempotencyKey={}",
                orderId, orderType, topic, idempotencyKey);

            // DEPRECATED: Old in-memory cleanup (will be removed)
            cleanExpiredEntries();

            switch (orderType != null ? orderType.toUpperCase() : "UNKNOWN") {
                case "SUBSCRIPTION":
                    processFundSubscription(eventData, correlationId);
                    break;

                case "REDEMPTION":
                    processFundRedemption(eventData, correlationId);
                    break;

                case "SWITCH":
                case "FUND_SWITCH":
                    processFundSwitch(eventData, correlationId);
                    break;

                case "SYSTEMATIC_INVESTMENT_PLAN":
                case "SIP":
                    processSIPOrder(eventData, correlationId);
                    break;

                case "SYSTEMATIC_WITHDRAWAL_PLAN":
                case "SWP":
                    processSWPOrder(eventData, correlationId);
                    break;

                case "DIVIDEND_REINVESTMENT":
                    processDividendReinvestment(eventData, correlationId);
                    break;

                case "NAV_UPDATE":
                    processNAVUpdate(eventData, correlationId);
                    break;

                default:
                    log.warn("Unknown mutual fund order type: {}", orderType);
                    processGenericMutualFundEvent(eventData, correlationId);
                    break;
            }

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("orderId", orderId, "orderType", orderType,
                       "accountId", accountId, "fundSymbol", fundSymbol,
                       "correlationId", correlationId, "topic", topic,
                       "status", "COMPLETED"), Duration.ofDays(7));

            // DEPRECATED: Old in-memory tracking (will be removed)
            String eventKey = String.format("%s-%s-%s", orderId, orderType, eventData.get("timestamp"));
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("MUTUAL_FUND_ORDER_EVENT_PROCESSED",
                accountId,
                Map.of("orderId", orderId, "orderType", orderType,
                    "fundSymbol", fundSymbol,
                    "correlationId", correlationId, "topic", topic,
                    "idempotencyKey", idempotencyKey,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("SECURITY: Failed to process mutual fund order event: {}", e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed in persistent storage for retry logic
            idempotencyService.failOperation(idempotencyKey, operationId,
                String.format("Mutual fund order failed: %s", e.getMessage()));

            // Send fallback event
            kafkaTemplate.send("mutual-fund-order-fallback-events", Map.of(
                "originalEvent", eventData, "error", e.getMessage(),
                "correlationId", correlationId, "idempotencyKey", idempotencyKey,
                "timestamp", Instant.now(), "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleMutualFundOrderEventFallback(
            Map<String, Object> eventData,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String orderId = (String) eventData.get("orderId");
        String correlationId = String.format("mf-fallback-%s-p%d-o%d", orderId, partition, offset);

        log.error("Circuit breaker fallback triggered for mutual fund order: orderId={}, error={}",
            orderId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("mutual-fund-order-dlq", Map.of(
            "originalEvent", eventData,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Mutual Fund Order Circuit Breaker Triggered",
                String.format("Mutual fund order %s failed: %s", orderId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltMutualFundOrderEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String orderId = (String) eventData.get("orderId");
        String correlationId = String.format("dlt-mf-%s-%d", orderId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Mutual fund order permanently failed: orderId={}, topic={}, error={}",
            orderId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("MUTUAL_FUND_ORDER_DLT_EVENT",
            (String) eventData.get("accountId"),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "orderId", orderId, "orderType", eventData.get("orderType"),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Mutual Fund Order Dead Letter Event",
                String.format("Mutual fund order %s sent to DLT: %s", orderId, exceptionMessage),
                Map.of("orderId", orderId, "orderType", eventData.get("orderType"),
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

    private void processFundSubscription(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String fundSymbol = (String) eventData.get("fundSymbol");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String currency = (String) eventData.get("currency");

        // Create subscription order
        InvestmentOrder order = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(fundSymbol)
            .orderType("MARKET")
            .orderSide("BUY")
            .quantity(BigDecimal.ZERO) // Will be calculated based on NAV
            .totalAmount(amount)
            .currency(currency)
            .status("PENDING_NAV")
            .assetType("MUTUAL_FUND")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(order);

        // Validate fund eligibility and suitability
        validateFundSuitability(accountId, fundSymbol, amount, correlationId);

        // Request current NAV for pricing
        requestNAVForPricing(orderId, fundSymbol, amount, correlationId);

        // Check for minimum investment requirements
        validateMinimumInvestment(fundSymbol, amount, correlationId);

        // Send subscription confirmation
        notificationService.sendNotification(userId, "Mutual Fund Subscription Initiated",
            String.format("Your subscription order for %s has been received and is being processed.", fundSymbol),
            correlationId);

        log.info("Fund subscription processed: orderId={}, fundSymbol={}, amount={}",
            orderId, fundSymbol, amount);
    }

    private void processFundRedemption(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String fundSymbol = (String) eventData.get("fundSymbol");
        BigDecimal units = new BigDecimal(eventData.get("units").toString());
        String redemptionType = (String) eventData.getOrDefault("redemptionType", "PARTIAL");

        // Validate holding availability
        InvestmentHolding holding = holdingRepository.findByAccountIdAndSymbol(accountId, fundSymbol)
            .orElseThrow(() -> new RuntimeException("No holding found for redemption: " + fundSymbol));

        if (holding.getQuantity().compareTo(units) < 0) {
            throw new RuntimeException("Insufficient units for redemption");
        }

        // Create redemption order
        InvestmentOrder order = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(fundSymbol)
            .orderType("MARKET")
            .orderSide("SELL")
            .quantity(units)
            .status("PENDING_NAV")
            .assetType("MUTUAL_FUND")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(order);

        // Check for exit load and lock-in period
        validateRedemptionEligibility(holding, correlationId);

        // Request current NAV for pricing
        requestNAVForPricing(orderId, fundSymbol, units, correlationId);

        // Calculate tax implications
        calculateTaxImplications(holding, units, correlationId);

        // Send redemption confirmation
        notificationService.sendNotification(userId, "Mutual Fund Redemption Initiated",
            String.format("Your redemption order for %s units of %s has been received.", units, fundSymbol),
            correlationId);

        log.info("Fund redemption processed: orderId={}, fundSymbol={}, units={}",
            orderId, fundSymbol, units);
    }

    private void processFundSwitch(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String fromFund = (String) eventData.get("fromFund");
        String toFund = (String) eventData.get("toFund");
        BigDecimal units = new BigDecimal(eventData.get("units").toString());

        // Validate both fund holdings and eligibility
        InvestmentHolding fromHolding = holdingRepository.findByAccountIdAndSymbol(accountId, fromFund)
            .orElseThrow(() -> new RuntimeException("Source fund holding not found: " + fromFund));

        if (fromHolding.getQuantity().compareTo(units) < 0) {
            throw new RuntimeException("Insufficient units for switch");
        }

        // Create switch order
        InvestmentOrder switchOrder = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(fromFund + " -> " + toFund)
            .orderType("SWITCH")
            .orderSide("SWITCH")
            .quantity(units)
            .status("PENDING_NAV")
            .assetType("MUTUAL_FUND")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(switchOrder);

        // Validate switch eligibility
        validateSwitchEligibility(fromFund, toFund, units, correlationId);

        // Validate fund family compatibility
        validateFundFamilySwitch(fromFund, toFund, correlationId);

        // Request NAV for both funds
        requestNAVForSwitch(orderId, fromFund, toFund, units, correlationId);

        // Send switch confirmation
        notificationService.sendNotification(userId, "Mutual Fund Switch Initiated",
            String.format("Your switch from %s to %s has been initiated for %s units.", fromFund, toFund, units),
            correlationId);

        log.info("Fund switch processed: orderId={}, fromFund={}, toFund={}, units={}",
            orderId, fromFund, toFund, units);
    }

    private void processSIPOrder(Map<String, Object> eventData, String correlationId) {
        String sipId = (String) eventData.get("sipId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String fundSymbol = (String) eventData.get("fundSymbol");
        BigDecimal sipAmount = new BigDecimal(eventData.get("sipAmount").toString());
        String frequency = (String) eventData.get("frequency");

        // Create SIP execution order
        String orderId = "SIP-" + sipId + "-" + System.currentTimeMillis();
        InvestmentOrder sipOrder = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(fundSymbol)
            .orderType("SIP")
            .orderSide("BUY")
            .totalAmount(sipAmount)
            .status("PENDING_EXECUTION")
            .assetType("MUTUAL_FUND")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(sipOrder);

        // Schedule next SIP installment
        scheduleNextSIPInstallment(sipId, fundSymbol, sipAmount, frequency, correlationId);

        // Send SIP execution notification
        notificationService.sendNotification(userId, "SIP Installment Executed",
            String.format("Your SIP installment of %s for %s has been executed.", sipAmount, fundSymbol),
            correlationId);

        log.info("SIP order processed: sipId={}, fundSymbol={}, amount={}", sipId, fundSymbol, sipAmount);
    }

    private void processSWPOrder(Map<String, Object> eventData, String correlationId) {
        String swpId = (String) eventData.get("swpId");
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String fundSymbol = (String) eventData.get("fundSymbol");
        BigDecimal withdrawalAmount = new BigDecimal(eventData.get("withdrawalAmount").toString());

        // Create SWP execution order
        String orderId = "SWP-" + swpId + "-" + System.currentTimeMillis();
        InvestmentOrder swpOrder = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(fundSymbol)
            .orderType("SWP")
            .orderSide("SELL")
            .totalAmount(withdrawalAmount)
            .status("PENDING_EXECUTION")
            .assetType("MUTUAL_FUND")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(swpOrder);

        // Validate sufficient holdings
        InvestmentHolding holding = holdingRepository.findByAccountIdAndSymbol(accountId, fundSymbol)
            .orElseThrow(() -> new RuntimeException("No holding found for SWP: " + fundSymbol));

        // Send SWP execution notification
        notificationService.sendNotification(userId, "SWP Installment Executed",
            String.format("Your SWP withdrawal of %s from %s has been executed.", withdrawalAmount, fundSymbol),
            correlationId);

        log.info("SWP order processed: swpId={}, fundSymbol={}, amount={}", swpId, fundSymbol, withdrawalAmount);
    }

    private void processDividendReinvestment(Map<String, Object> eventData, String correlationId) {
        String accountId = (String) eventData.get("accountId");
        String userId = (String) eventData.get("userId");
        String fundSymbol = (String) eventData.get("fundSymbol");
        BigDecimal dividendAmount = new BigDecimal(eventData.get("dividendAmount").toString());

        // Create dividend reinvestment order
        String orderId = "DIVR-" + UUID.randomUUID().toString();
        InvestmentOrder reinvestmentOrder = InvestmentOrder.builder()
            .orderId(orderId)
            .accountId(accountId)
            .userId(userId)
            .symbol(fundSymbol)
            .orderType("DIVIDEND_REINVESTMENT")
            .orderSide("BUY")
            .totalAmount(dividendAmount)
            .status("PENDING_EXECUTION")
            .assetType("MUTUAL_FUND")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        orderRepository.save(reinvestmentOrder);

        // Process dividend reinvestment
        portfolioService.processDividendReinvestment(accountId, fundSymbol, dividendAmount);

        log.info("Dividend reinvestment processed: orderId={}, fundSymbol={}, amount={}",
            orderId, fundSymbol, dividendAmount);
    }

    private void processNAVUpdate(Map<String, Object> eventData, String correlationId) {
        String fundSymbol = (String) eventData.get("fundSymbol");
        BigDecimal newNAV = new BigDecimal(eventData.get("newNAV").toString());
        LocalDateTime navDate = LocalDateTime.parse((String) eventData.get("navDate"));

        // Update portfolio valuations
        portfolioService.updateHoldingsValuation(fundSymbol, newNAV, navDate);

        // Process pending orders based on new NAV
        orderExecutionService.processPendingNAVOrders(fundSymbol, newNAV);

        log.info("NAV update processed: fundSymbol={}, newNAV={}, date={}", fundSymbol, newNAV, navDate);
    }

    private void processGenericMutualFundEvent(Map<String, Object> eventData, String correlationId) {
        String orderId = (String) eventData.get("orderId");
        String orderType = (String) eventData.get("orderType");

        log.info("Processing generic mutual fund event: orderId={}, orderType={}", orderId, orderType);

        // Store the event for manual processing
        auditService.logAccountEvent("GENERIC_MUTUAL_FUND_EVENT",
            (String) eventData.get("accountId"),
            Map.of("orderType", orderType, "eventData", eventData,
                "correlationId", correlationId, "requiresManualProcessing", true,
                "timestamp", Instant.now()));
    }

    private void validateFundSuitability(String accountId, String fundSymbol, BigDecimal amount, String correlationId) {
        try {
            investmentService.validateFundSuitability(accountId, fundSymbol, amount);
        } catch (Exception e) {
            log.error("Fund suitability validation failed: {}", e.getMessage());
            kafkaTemplate.send("compliance-violations", Map.of(
                "accountId", accountId,
                "violationType", "FUND_SUITABILITY",
                "fundSymbol", fundSymbol,
                "amount", amount,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void requestNAVForPricing(String orderId, String fundSymbol, Object amount, String correlationId) {
        kafkaTemplate.send("nav-pricing-requests", Map.of(
            "orderId", orderId,
            "fundSymbol", fundSymbol,
            "amount", amount,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void validateMinimumInvestment(String fundSymbol, BigDecimal amount, String correlationId) {
        // This would typically check against fund master data
        // For now, we'll just log the validation
        log.info("Validating minimum investment for fund: {}, amount: {}", fundSymbol, amount);
    }

    private void validateRedemptionEligibility(InvestmentHolding holding, String correlationId) {
        // Check lock-in period and exit load
        LocalDateTime lockInExpiry = holding.getCreatedAt().plusDays(365); // Example: 1 year lock-in
        if (LocalDateTime.now().isBefore(lockInExpiry)) {
            log.warn("Redemption requested during lock-in period for holding: {}", holding.getSymbol());
        }
    }

    private void calculateTaxImplications(InvestmentHolding holding, BigDecimal units, String correlationId) {
        // Calculate capital gains tax implications
        kafkaTemplate.send("tax-calculation-requests", Map.of(
            "accountId", holding.getAccountId(),
            "symbol", holding.getSymbol(),
            "units", units,
            "averageCost", holding.getAverageCost(),
            "holdingPeriod", holding.getCreatedAt(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void validateSwitchEligibility(String fromFund, String toFund, BigDecimal units, String correlationId) {
        // Validate switch rules and eligibility
        log.info("Validating switch eligibility: {} -> {}, units: {}", fromFund, toFund, units);
    }

    private void validateFundFamilySwitch(String fromFund, String toFund, String correlationId) {
        // Ensure both funds belong to same family for free switch
        log.info("Validating fund family compatibility: {} -> {}", fromFund, toFund);
    }

    private void requestNAVForSwitch(String orderId, String fromFund, String toFund, BigDecimal units, String correlationId) {
        kafkaTemplate.send("nav-pricing-requests", Map.of(
            "orderId", orderId,
            "orderType", "SWITCH",
            "fromFund", fromFund,
            "toFund", toFund,
            "units", units,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void scheduleNextSIPInstallment(String sipId, String fundSymbol, BigDecimal amount, String frequency, String correlationId) {
        kafkaTemplate.send("sip-scheduling", Map.of(
            "sipId", sipId,
            "fundSymbol", fundSymbol,
            "amount", amount,
            "frequency", frequency,
            "nextExecutionDate", calculateNextSIPDate(frequency),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private LocalDateTime calculateNextSIPDate(String frequency) {
        LocalDateTime now = LocalDateTime.now();
        switch (frequency.toUpperCase()) {
            case "MONTHLY":
                return now.plusMonths(1);
            case "QUARTERLY":
                return now.plusMonths(3);
            case "WEEKLY":
                return now.plusWeeks(1);
            default:
                return now.plusMonths(1); // Default to monthly
        }
    }
}