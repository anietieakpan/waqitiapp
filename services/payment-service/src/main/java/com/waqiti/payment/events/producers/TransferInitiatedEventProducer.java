package com.waqiti.payment.events.producers;

import com.waqiti.common.events.transfer.TransferInitiatedEvent;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferInitiatedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topics.transfer-initiated:transfer-initiated}")
    private String topicName;

    private Counter eventsPublishedCounter;
    private Counter eventsFailedCounter;

    public TransferInitiatedEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsPublishedCounter = Counter.builder("transfer_initiated_events_published_total")
                .description("Total number of transfer initiated events published")
                .tag("producer", "transfer-initiated-producer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("transfer_initiated_events_failed_total")
                .description("Total number of transfer initiated events that failed to publish")
                .tag("producer", "transfer-initiated-producer")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishTransferInitiatedEventFallback")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishTransferInitiatedEvent(String transferId, String userId, String transferType,
                                             BigDecimal amount, String currency, String sourceAccount,
                                             String destinationAccount, String correlationId) {
        String eventId = UUID.randomUUID().toString();

        TransferInitiatedEvent event = buildTransferInitiatedEvent(
                transferId, userId, transferType, amount, currency, sourceAccount,
                destinationAccount, eventId, correlationId
        );

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                transferId,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                eventsPublishedCounter.increment();
                log.info("Transfer initiated event published successfully: eventId={}, transferId={}, userId={}, " +
                        "transferType={}, amount={} {}, correlationId={}",
                        eventId, transferId, userId, transferType, amount, currency, correlationId);
            } else {
                eventsFailedCounter.increment();
                log.error("Failed to publish transfer initiated event: eventId={}, transferId={}, correlationId={}, error={}",
                        eventId, transferId, correlationId, ex.getMessage(), ex);
            }
        });

        Counter.builder("transfer_initiated_by_type")
                .tag("transfer_type", transferType)
                .register(meterRegistry)
                .increment();
    }

    @CircuitBreaker(name = "kafkaProducer")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public CompletableFuture<SendResult<String, Object>> publishTransferInitiatedEventSync(
            String transferId, String userId, String transferType, BigDecimal amount,
            String currency, String sourceAccount, String destinationAccount,
            String correlationId) {
        
        String eventId = UUID.randomUUID().toString();

        TransferInitiatedEvent event = buildTransferInitiatedEvent(
                transferId, userId, transferType, amount, currency, sourceAccount,
                destinationAccount, eventId, correlationId
        );

        log.debug("Publishing transfer initiated event synchronously: eventId={}, transferId={}, correlationId={}",
                eventId, transferId, correlationId);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                transferId,
                event
        );

        future.thenAccept(result -> {
            eventsPublishedCounter.increment();
            log.info("Transfer initiated event published successfully (sync): eventId={}, transferId={}, correlationId={}",
                    eventId, transferId, correlationId);
        }).exceptionally(ex -> {
            eventsFailedCounter.increment();
            log.error("Failed to publish transfer initiated event (sync): eventId={}, transferId={}, correlationId={}, error={}",
                    eventId, transferId, correlationId, ex.getMessage());
            return null;
        });

        return future;
    }

    private TransferInitiatedEvent buildTransferInitiatedEvent(String transferId, String userId,
                                                               String transferType, BigDecimal amount,
                                                               String currency, String sourceAccount,
                                                               String destinationAccount, String eventId,
                                                               String correlationId) {
        return TransferInitiatedEvent.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .eventVersion("1.0")
                .source("payment-service")
                .transferId(transferId)
                .userId(userId)
                .transferType(transferType)
                .amount(amount)
                .currency(currency)
                .sourceAccount(maskAccountNumber(sourceAccount))
                .destinationAccount(maskAccountNumber(destinationAccount))
                .initiatedAt(LocalDateTime.now())
                .build();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private void publishTransferInitiatedEventFallback(String transferId, String userId,
                                                      String transferType, BigDecimal amount,
                                                      String currency, String sourceAccount,
                                                      String destinationAccount, String correlationId,
                                                      Exception e) {
        eventsFailedCounter.increment();
        log.error("Circuit breaker fallback: Failed to publish transfer initiated event after retries - " +
                "transferId={}, userId={}, correlationId={}, error={}",
                transferId, userId, correlationId, e.getMessage());
    }
}