package com.waqiti.investment.kafka;

import com.waqiti.common.events.investment.InvestmentExecutedEvent;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.InvestmentExecution;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.repository.InvestmentExecutionRepository;
import com.waqiti.investment.service.InvestmentService;
import com.waqiti.investment.service.OrderExecutionService;
import com.waqiti.investment.service.PortfolioService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class InvestmentExecutedConsumer {

    private final InvestmentOrderRepository orderRepository;
    private final InvestmentExecutionRepository executionRepository;
    private final InvestmentService investmentService;
    private final OrderExecutionService orderExecutionService;
    private final PortfolioService portfolioService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService; // CRITICAL: Persistent idempotency

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
        successCounter = Counter.builder("investment_executed_processed_total")
            .description("Total number of successfully processed investment execution events")
            .register(meterRegistry);
        errorCounter = Counter.builder("investment_executed_errors_total")
            .description("Total number of investment execution processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("investment_executed_processing_duration")
            .description("Time taken to process investment execution events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"investment-executed", "investment-settlement", "trade-confirmations"},
        groupId = "investment-execution-service-group",
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
    @CircuitBreaker(name = "investment-executed", fallbackMethod = "handleInvestmentExecutedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInvestmentExecutedEvent(
            @Payload InvestmentExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("exec-%s-p%d-o%d", event.getOrderId(), partition, offset);

        // CRITICAL SECURITY: Enhanced idempotency key with all unique identifiers
        String idempotencyKey = String.format("investment-execution:%s:%s:%s",
            event.getOrderId(), event.getSymbol(), event.getExecutedAt());
        UUID operationId = UUID.randomUUID();

        try {
            // CRITICAL SECURITY: Persistent idempotency check (survives service restarts)
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate investment execution event ignored - already processed: orderId={}, symbol={}, idempotencyKey={}",
                        event.getOrderId(), event.getSymbol(), idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("SECURITY: Processing new investment execution with persistent idempotency: orderId={}, symbol={}, quantity={}, executedPrice={}, idempotencyKey={}",
                event.getOrderId(), event.getSymbol(), event.getQuantity(), event.getExecutedPrice(), idempotencyKey);

            // Process investment execution
            processInvestmentExecution(event, correlationId);

            // Update portfolio holdings
            updatePortfolioHoldings(event, correlationId);

            // Process settlement workflow
            initiateSettlementWorkflow(event, correlationId);

            // Generate trade confirmation
            generateTradeConfirmation(event, correlationId);

            // Perform regulatory compliance checks
            performComplianceChecks(event, correlationId);

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("orderId", event.getOrderId(), "executionId", correlationId,
                       "status", "COMPLETED", "timestamp", Instant.now()),
                Duration.ofDays(7));

            auditService.logAccountEvent("INVESTMENT_EXECUTED_EVENT_PROCESSED", event.getAccountId(),
                Map.of("orderId", event.getOrderId(), "symbol", event.getSymbol(),
                    "quantity", event.getQuantity(), "executedPrice", event.getExecutedPrice(),
                    "totalAmount", event.getTotalAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("SECURITY: Failed to process investment execution event: {}", e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed in persistent storage for retry logic
            idempotencyService.failOperation(idempotencyKey, operationId,
                String.format("Investment execution failed: %s", e.getMessage()));

            // Send fallback event
            kafkaTemplate.send("investment-executed-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "idempotencyKey", idempotencyKey,
                "timestamp", Instant.now(), "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInvestmentExecutedEventFallback(
            InvestmentExecutedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("exec-fallback-%s-p%d-o%d", event.getOrderId(), partition, offset);

        log.error("Circuit breaker fallback triggered for investment execution: orderId={}, error={}",
            event.getOrderId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("investment-executed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Investment Execution Circuit Breaker Triggered",
                String.format("Investment execution for order %s failed: %s", event.getOrderId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInvestmentExecutedEvent(
            @Payload InvestmentExecutedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-exec-%s-%d", event.getOrderId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Investment execution permanently failed: orderId={}, topic={}, error={}",
            event.getOrderId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("INVESTMENT_EXECUTED_DLT_EVENT", event.getAccountId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "orderId", event.getOrderId(), "symbol", event.getSymbol(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Investment Execution Dead Letter Event",
                String.format("Investment execution for order %s sent to DLT: %s", event.getOrderId(), exceptionMessage),
                Map.of("orderId", event.getOrderId(), "symbol", event.getSymbol(),
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

    private void processInvestmentExecution(InvestmentExecutedEvent event, String correlationId) {
        // Find the original order
        InvestmentOrder order = orderRepository.findByOrderId(event.getOrderId())
            .orElseThrow(() -> new RuntimeException("Investment order not found: " + event.getOrderId()));

        // Create execution record
        InvestmentExecution execution = InvestmentExecution.builder()
            .executionId(UUID.randomUUID().toString())
            .orderId(event.getOrderId())
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .symbol(event.getSymbol())
            .quantity(event.getQuantity())
            .executedPrice(event.getExecutedPrice())
            .totalAmount(event.getTotalAmount())
            .commission(event.getCommission())
            .currency(event.getCurrency())
            .executedAt(event.getExecutedAt())
            .assetType(event.getAssetType())
            .exchange(event.getExchange())
            .status("EXECUTED")
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        executionRepository.save(execution);

        // Update order status
        order.setStatus("EXECUTED");
        order.setExecutedQuantity(event.getQuantity());
        order.setExecutedPrice(event.getExecutedPrice());
        order.setExecutedAt(event.getExecutedAt());
        order.setLastModifiedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Notify user of execution
        notificationService.sendNotification(event.getUserId(), "Investment Executed",
            String.format("Your %s order for %s shares of %s has been executed at $%s",
                order.getOrderSide(), event.getQuantity(), event.getSymbol(), event.getExecutedPrice()),
            correlationId);

        log.info("Investment execution processed: orderId={}, executionId={}",
            event.getOrderId(), execution.getExecutionId());
    }

    private void updatePortfolioHoldings(InvestmentExecutedEvent event, String correlationId) {
        try {
            portfolioService.updateHoldingsFromExecution(
                event.getAccountId(),
                event.getSymbol(),
                event.getOrderSide(),
                event.getQuantity(),
                event.getExecutedPrice(),
                event.getCommission()
            );

            log.info("Portfolio holdings updated for execution: orderId={}, symbol={}",
                event.getOrderId(), event.getSymbol());

        } catch (Exception e) {
            log.error("Failed to update portfolio holdings: {}", e.getMessage(), e);
            // Send event for manual reconciliation
            kafkaTemplate.send("portfolio-reconciliation-required", Map.of(
                "orderId", event.getOrderId(),
                "accountId", event.getAccountId(),
                "symbol", event.getSymbol(),
                "correlationId", correlationId,
                "error", e.getMessage(),
                "timestamp", Instant.now()
            ));
        }
    }

    private void initiateSettlementWorkflow(InvestmentExecutedEvent event, String correlationId) {
        // Send settlement initiation event
        kafkaTemplate.send("investment-settlement-workflow", Map.of(
            "orderId", event.getOrderId(),
            "executionId", correlationId,
            "accountId", event.getAccountId(),
            "symbol", event.getSymbol(),
            "quantity", event.getQuantity(),
            "totalAmount", event.getTotalAmount(),
            "currency", event.getCurrency(),
            "settlementDate", calculateSettlementDate(event.getAssetType()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Settlement workflow initiated for execution: orderId={}", event.getOrderId());
    }

    private void generateTradeConfirmation(InvestmentExecutedEvent event, String correlationId) {
        // Send trade confirmation generation event
        kafkaTemplate.send("trade-confirmation-generation", Map.of(
            "orderId", event.getOrderId(),
            "accountId", event.getAccountId(),
            "userId", event.getUserId(),
            "symbol", event.getSymbol(),
            "quantity", event.getQuantity(),
            "executedPrice", event.getExecutedPrice(),
            "totalAmount", event.getTotalAmount(),
            "commission", event.getCommission(),
            "executedAt", event.getExecutedAt(),
            "confirmationType", "EXECUTION_CONFIRMATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Trade confirmation generated for execution: orderId={}", event.getOrderId());
    }

    private void performComplianceChecks(InvestmentExecutedEvent event, String correlationId) {
        try {
            // Best execution analysis
            orderExecutionService.validateBestExecution(
                event.getOrderId(),
                event.getSymbol(),
                event.getExecutedPrice(),
                event.getExecutedAt()
            );

            // Suitability validation
            investmentService.validateInvestmentSuitability(
                event.getAccountId(),
                event.getSymbol(),
                event.getTotalAmount()
            );

            // Position limit checks
            portfolioService.validatePositionLimits(
                event.getAccountId(),
                event.getSymbol(),
                event.getQuantity()
            );

            log.info("Compliance checks passed for execution: orderId={}", event.getOrderId());

        } catch (Exception e) {
            log.error("Compliance check failed for execution: {}", e.getMessage(), e);

            // Send compliance violation event
            kafkaTemplate.send("compliance-violations", Map.of(
                "orderId", event.getOrderId(),
                "accountId", event.getAccountId(),
                "violationType", "EXECUTION_COMPLIANCE",
                "description", e.getMessage(),
                "severity", "HIGH",
                "correlationId", correlationId,
                "requiresReview", true,
                "timestamp", Instant.now()
            ));
        }
    }

    private LocalDateTime calculateSettlementDate(String assetType) {
        // T+2 for stocks, T+1 for bonds, T+0 for money market
        LocalDateTime now = LocalDateTime.now();
        switch (assetType != null ? assetType.toUpperCase() : "STOCK") {
            case "BOND":
                return now.plusDays(1);
            case "MONEY_MARKET":
                return now;
            case "STOCK":
            default:
                return now.plusDays(2);
        }
    }
}