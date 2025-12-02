package com.waqiti.compliance.events.producers;

import com.waqiti.common.events.compliance.KYCVerifiedEvent;
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
public class KYCVerifiedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topics.kyc-verified:kyc-verified}")
    private String topicName;

    private Counter eventsPublishedCounter;
    private Counter eventsFailedCounter;

    public KYCVerifiedEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsPublishedCounter = Counter.builder("kyc_verified_events_published_total")
                .description("Total number of KYC verified events published")
                .tag("producer", "kyc-verified-producer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("kyc_verified_events_failed_total")
                .description("Total number of KYC verified events that failed to publish")
                .tag("producer", "kyc-verified-producer")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishKYCVerifiedEventFallback")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishKYCVerifiedEvent(String verificationId, String userId, String kycLevel,
                                       String verificationType, String verifiedBy,
                                       String correlationId) {
        String eventId = UUID.randomUUID().toString();

        KYCVerifiedEvent event = buildKYCVerifiedEvent(
                verificationId, userId, kycLevel, verificationType, verifiedBy,
                eventId, correlationId
        );

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                userId,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                eventsPublishedCounter.increment();
                log.info("KYC verified event published successfully: eventId={}, verificationId={}, userId={}, " +
                        "kycLevel={}, correlationId={}",
                        eventId, verificationId, userId, kycLevel, correlationId);
            } else {
                eventsFailedCounter.increment();
                log.error("Failed to publish KYC verified event: eventId={}, verificationId={}, correlationId={}, error={}",
                        eventId, verificationId, correlationId, ex.getMessage(), ex);
            }
        });

        Counter.builder("kyc_verified_by_level")
                .tag("kyc_level", kycLevel)
                .register(meterRegistry)
                .increment();
    }

    @CircuitBreaker(name = "kafkaProducer")
    @Retry(name = "kafkaProducer")
    @Transactional(propagation = Propagation.REQUIRED)
    public CompletableFuture<SendResult<String, Object>> publishKYCVerifiedEventSync(
            String verificationId, String userId, String kycLevel, String verificationType,
            String verifiedBy, String correlationId) {
        
        String eventId = UUID.randomUUID().toString();

        KYCVerifiedEvent event = buildKYCVerifiedEvent(
                verificationId, userId, kycLevel, verificationType, verifiedBy,
                eventId, correlationId
        );

        log.debug("Publishing KYC verified event synchronously: eventId={}, verificationId={}, correlationId={}",
                eventId, verificationId, correlationId);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                userId,
                event
        );

        future.thenAccept(result -> {
            eventsPublishedCounter.increment();
            log.info("KYC verified event published successfully (sync): eventId={}, verificationId={}, correlationId={}",
                    eventId, verificationId, correlationId);
        }).exceptionally(ex -> {
            eventsFailedCounter.increment();
            log.error("Failed to publish KYC verified event (sync): eventId={}, verificationId={}, correlationId={}, error={}",
                    eventId, verificationId, correlationId, ex.getMessage());
            return null;
        });

        return future;
    }

    private KYCVerifiedEvent buildKYCVerifiedEvent(String verificationId, String userId,
                                                   String kycLevel, String verificationType,
                                                   String verifiedBy, String eventId,
                                                   String correlationId) {
        return KYCVerifiedEvent.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .eventVersion("1.0")
                .source("compliance-service")
                .verificationId(verificationId)
                .userId(userId)
                .kycLevel(kycLevel)
                .verificationType(verificationType != null ? verificationType : "STANDARD")
                .verifiedBy(verifiedBy)
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    private void publishKYCVerifiedEventFallback(String verificationId, String userId,
                                                String kycLevel, String verificationType,
                                                String verifiedBy, String correlationId,
                                                Exception e) {
        eventsFailedCounter.increment();
        log.error("Circuit breaker fallback: Failed to publish KYC verified event after retries - " +
                "verificationId={}, userId={}, correlationId={}, error={}",
                verificationId, userId, correlationId, e.getMessage());
    }
}