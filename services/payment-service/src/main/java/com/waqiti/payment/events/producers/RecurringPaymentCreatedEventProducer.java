package com.waqiti.payment.events.producers;

import com.waqiti.common.events.payment.RecurringPaymentCreatedEvent;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringPaymentCreatedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topics.recurring-payment-created:recurring-payment-created}")
    private String topicName;

    private Counter eventsPublishedCounter;
    private Counter eventsFailedCounter;

    public RecurringPaymentCreatedEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsPublishedCounter = Counter.builder("recurring_payment_created_events_published_total")
                .description("Total number of recurring payment created events published")
                .tag("producer", "recurring-payment-created-producer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("recurring_payment_created_events_failed_total")
                .description("Total number of recurring payment created events that failed to publish")
                .tag("producer", "recurring-payment-created-producer")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishRecurringPaymentCreatedEventFallback")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishRecurringPaymentCreatedEvent(String recurringPaymentId, String userId,
                                                    String paymentType, BigDecimal amount,
                                                    String currency, String frequency,
                                                    LocalDate startDate, LocalDate nextPaymentDate,
                                                    String correlationId) {
        String eventId = UUID.randomUUID().toString();

        RecurringPaymentCreatedEvent event = buildRecurringPaymentCreatedEvent(
                recurringPaymentId, userId, paymentType, amount, currency, frequency,
                startDate, nextPaymentDate, eventId, correlationId
        );

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                recurringPaymentId,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                eventsPublishedCounter.increment();
                log.info("Recurring payment created event published successfully: eventId={}, recurringPaymentId={}, " +
                        "userId={}, paymentType={}, amount={} {}, frequency={}, correlationId={}",
                        eventId, recurringPaymentId, userId, paymentType, amount, currency, frequency, correlationId);
            } else {
                eventsFailedCounter.increment();
                log.error("Failed to publish recurring payment created event: eventId={}, recurringPaymentId={}, " +
                        "correlationId={}, error={}",
                        eventId, recurringPaymentId, correlationId, ex.getMessage(), ex);
            }
        });

        Counter.builder("recurring_payment_created_by_frequency")
                .tag("frequency", frequency)
                .register(meterRegistry)
                .increment();
    }

    @CircuitBreaker(name = "kafkaProducer")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public CompletableFuture<SendResult<String, Object>> publishRecurringPaymentCreatedEventSync(
            String recurringPaymentId, String userId, String paymentType, BigDecimal amount,
            String currency, String frequency, LocalDate startDate, LocalDate nextPaymentDate,
            String correlationId) {
        
        String eventId = UUID.randomUUID().toString();

        RecurringPaymentCreatedEvent event = buildRecurringPaymentCreatedEvent(
                recurringPaymentId, userId, paymentType, amount, currency, frequency,
                startDate, nextPaymentDate, eventId, correlationId
        );

        log.debug("Publishing recurring payment created event synchronously: eventId={}, recurringPaymentId={}, correlationId={}",
                eventId, recurringPaymentId, correlationId);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                recurringPaymentId,
                event
        );

        future.thenAccept(result -> {
            eventsPublishedCounter.increment();
            log.info("Recurring payment created event published successfully (sync): eventId={}, recurringPaymentId={}, correlationId={}",
                    eventId, recurringPaymentId, correlationId);
        }).exceptionally(ex -> {
            eventsFailedCounter.increment();
            log.error("Failed to publish recurring payment created event (sync): eventId={}, recurringPaymentId={}, " +
                    "correlationId={}, error={}",
                    eventId, recurringPaymentId, correlationId, ex.getMessage());
            return null;
        });

        return future;
    }

    private RecurringPaymentCreatedEvent buildRecurringPaymentCreatedEvent(
            String recurringPaymentId, String userId, String paymentType, BigDecimal amount,
            String currency, String frequency, LocalDate startDate, LocalDate nextPaymentDate,
            String eventId, String correlationId) {
        
        return RecurringPaymentCreatedEvent.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .eventVersion("1.0")
                .source("payment-service")
                .recurringPaymentId(recurringPaymentId)
                .userId(userId)
                .paymentType(paymentType)
                .amount(amount)
                .currency(currency)
                .frequency(frequency)
                .startDate(startDate)
                .nextPaymentDate(nextPaymentDate)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void publishRecurringPaymentCreatedEventFallback(String recurringPaymentId, String userId,
                                                            String paymentType, BigDecimal amount,
                                                            String currency, String frequency,
                                                            LocalDate startDate, LocalDate nextPaymentDate,
                                                            String correlationId, Exception e) {
        eventsFailedCounter.increment();
        log.error("Circuit breaker fallback: Failed to publish recurring payment created event after retries - " +
                "recurringPaymentId={}, userId={}, correlationId={}, error={}",
                recurringPaymentId, userId, correlationId, e.getMessage());
    }
}