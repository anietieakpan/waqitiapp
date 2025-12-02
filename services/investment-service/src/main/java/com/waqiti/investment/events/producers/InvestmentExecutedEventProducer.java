package com.waqiti.investment.events.producers;

import com.waqiti.common.events.investment.InvestmentExecutedEvent;
import com.waqiti.investment.domain.InvestmentOrder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentExecutedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topics.investment-executed:investment-executed}")
    private String topicName;

    private Counter eventsPublishedCounter;
    private Counter eventsFailedCounter;

    public InvestmentExecutedEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsPublishedCounter = Counter.builder("investment_executed_events_published_total")
                .description("Total number of investment executed events published")
                .tag("producer", "investment-executed-producer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("investment_executed_events_failed_total")
                .description("Total number of investment executed events that failed to publish")
                .tag("producer", "investment-executed-producer")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishInvestmentExecutedEventFallback")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishInvestmentExecutedEvent(InvestmentOrder order, String correlationId) {
        String eventId = UUID.randomUUID().toString();

        InvestmentExecutedEvent event = buildInvestmentExecutedEvent(order, eventId, correlationId);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                order.getId().toString(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                eventsPublishedCounter.increment();
                log.info("Investment executed event published successfully: eventId={}, orderId={}, symbol={}, " +
                        "side={}, quantity={}, price={}, correlationId={}",
                        eventId, order.getId(), order.getSymbol(), order.getOrderSide(), 
                        order.getExecutedQuantity(), order.getExecutedPrice(), correlationId);
            } else {
                eventsFailedCounter.increment();
                log.error("Failed to publish investment executed event: eventId={}, orderId={}, correlationId={}, error={}",
                        eventId, order.getId(), correlationId, ex.getMessage(), ex);
            }
        });

        Counter.builder("investment_executed_by_side")
                .tag("side", order.getOrderSide().toString())
                .register(meterRegistry)
                .increment();
    }

    @CircuitBreaker(name = "kafkaProducer")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public CompletableFuture<SendResult<String, Object>> publishInvestmentExecutedEventSync(
            InvestmentOrder order, String correlationId) {
        
        String eventId = UUID.randomUUID().toString();

        InvestmentExecutedEvent event = buildInvestmentExecutedEvent(order, eventId, correlationId);

        log.debug("Publishing investment executed event synchronously: eventId={}, orderId={}, correlationId={}",
                eventId, order.getId(), correlationId);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                order.getId().toString(),
                event
        );

        future.thenAccept(result -> {
            eventsPublishedCounter.increment();
            log.info("Investment executed event published successfully (sync): eventId={}, orderId={}, correlationId={}",
                    eventId, order.getId(), correlationId);
        }).exceptionally(ex -> {
            eventsFailedCounter.increment();
            log.error("Failed to publish investment executed event (sync): eventId={}, orderId={}, correlationId={}, error={}",
                    eventId, order.getId(), correlationId, ex.getMessage());
            return null;
        });

        return future;
    }

    private InvestmentExecutedEvent buildInvestmentExecutedEvent(InvestmentOrder order, 
                                                                 String eventId, String correlationId) {
        return InvestmentExecutedEvent.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .eventVersion("1.0")
                .source("investment-service")
                .orderId(order.getId().toString())
                .accountId(order.getAccountId().toString())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .companyName(order.getCompanyName())
                .orderType(order.getOrderType().toString())
                .orderSide(order.getOrderSide().toString())
                .quantity(order.getExecutedQuantity())
                .executedPrice(order.getExecutedPrice())
                .totalAmount(order.getExecutedQuantity().multiply(order.getExecutedPrice()))
                .commission(order.getCommission())
                .currency(order.getCurrency() != null ? order.getCurrency() : "USD")
                .executedAt(order.getFilledAt() != null ? order.getFilledAt() : LocalDateTime.now())
                .assetType(order.getAssetType() != null ? order.getAssetType().toString() : "STOCK")
                .exchange(order.getExchange())
                .build();
    }

    private void publishInvestmentExecutedEventFallback(InvestmentOrder order, String correlationId, Exception e) {
        eventsFailedCounter.increment();
        log.error("Circuit breaker fallback: Failed to publish investment executed event after retries - " +
                "orderId={}, correlationId={}, error={}",
                order.getId(), correlationId, e.getMessage());
    }
}