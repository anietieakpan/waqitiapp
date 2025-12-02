package com.waqiti.security.events.producers;

import com.waqiti.common.events.security.AccountSuspendedEvent;
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
public class AccountSuspendedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topics.account-suspended:account-suspended}")
    private String topicName;

    private Counter eventsPublishedCounter;
    private Counter eventsFailedCounter;

    public AccountSuspendedEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsPublishedCounter = Counter.builder("account_suspended_events_published_total")
                .description("Total number of account suspended events published")
                .tag("producer", "account-suspended-producer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("account_suspended_events_failed_total")
                .description("Total number of account suspended events that failed to publish")
                .tag("producer", "account-suspended-producer")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishAccountSuspendedEventFallback")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishAccountSuspendedEvent(String userId, String suspensionReason,
                                            String suspensionType, String suspendedBy,
                                            LocalDateTime suspensionEndDate, String correlationId) {
        String eventId = UUID.randomUUID().toString();

        AccountSuspendedEvent event = buildAccountSuspendedEvent(
                userId, suspensionReason, suspensionType, suspendedBy,
                suspensionEndDate, eventId, correlationId
        );

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                userId,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                eventsPublishedCounter.increment();
                log.info("Account suspended event published successfully: eventId={}, userId={}, " +
                        "suspensionReason={}, suspensionType={}, correlationId={}",
                        eventId, userId, suspensionReason, suspensionType, correlationId);
            } else {
                eventsFailedCounter.increment();
                log.error("Failed to publish account suspended event: eventId={}, userId={}, correlationId={}, error={}",
                        eventId, userId, correlationId, ex.getMessage(), ex);
            }
        });

        Counter.builder("account_suspended_by_reason")
                .tag("suspension_reason", suspensionReason)
                .register(meterRegistry)
                .increment();
    }

    @CircuitBreaker(name = "kafkaProducer")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public CompletableFuture<SendResult<String, Object>> publishAccountSuspendedEventSync(
            String userId, String suspensionReason, String suspensionType,
            String suspendedBy, LocalDateTime suspensionEndDate, String correlationId) {
        
        String eventId = UUID.randomUUID().toString();

        AccountSuspendedEvent event = buildAccountSuspendedEvent(
                userId, suspensionReason, suspensionType, suspendedBy,
                suspensionEndDate, eventId, correlationId
        );

        log.debug("Publishing account suspended event synchronously: eventId={}, userId={}, correlationId={}",
                eventId, userId, correlationId);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                userId,
                event
        );

        future.thenAccept(result -> {
            eventsPublishedCounter.increment();
            log.info("Account suspended event published successfully (sync): eventId={}, userId={}, correlationId={}",
                    eventId, userId, correlationId);
        }).exceptionally(ex -> {
            eventsFailedCounter.increment();
            log.error("Failed to publish account suspended event (sync): eventId={}, userId={}, correlationId={}, error={}",
                    eventId, userId, correlationId, ex.getMessage());
            return null;
        });

        return future;
    }

    private AccountSuspendedEvent buildAccountSuspendedEvent(String userId, String suspensionReason,
                                                            String suspensionType, String suspendedBy,
                                                            LocalDateTime suspensionEndDate, String eventId,
                                                            String correlationId) {
        return AccountSuspendedEvent.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .eventVersion("1.0")
                .source("security-service")
                .userId(userId)
                .suspensionReason(suspensionReason)
                .suspensionType(suspensionType)
                .suspendedBy(suspendedBy)
                .suspendedAt(LocalDateTime.now())
                .suspensionEndDate(suspensionEndDate)
                .build();
    }

    private void publishAccountSuspendedEventFallback(String userId, String suspensionReason,
                                                     String suspensionType, String suspendedBy,
                                                     LocalDateTime suspensionEndDate, String correlationId,
                                                     Exception e) {
        eventsFailedCounter.increment();
        log.error("Circuit breaker fallback: Failed to publish account suspended event after retries - " +
                "userId={}, suspensionReason={}, correlationId={}, error={}",
                userId, suspensionReason, correlationId, e.getMessage());
    }
}