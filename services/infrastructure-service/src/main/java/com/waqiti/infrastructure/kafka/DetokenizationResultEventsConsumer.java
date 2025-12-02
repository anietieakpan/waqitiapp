package com.waqiti.infrastructure.kafka;

import com.waqiti.common.events.DetokenizationResultEvent;
import com.waqiti.infrastructure.domain.TokenizationResult;
import com.waqiti.infrastructure.repository.TokenizationResultRepository;
import com.waqiti.infrastructure.service.DetokenizationService;
import com.waqiti.infrastructure.service.SecurityIncidentService;
import com.waqiti.infrastructure.service.CryptoAuditService;
import com.waqiti.infrastructure.metrics.InfrastructureMetricsService;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DetokenizationResultEventsConsumer {

    private final TokenizationResultRepository tokenizationResultRepository;
    private final DetokenizationService detokenizationService;
    private final SecurityIncidentService securityIncidentService;
    private final CryptoAuditService cryptoAuditService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("detokenization_result_processed_total")
            .description("Total number of successfully processed detokenization result events")
            .register(meterRegistry);
        errorCounter = Counter.builder("detokenization_result_errors_total")
            .description("Total number of detokenization result processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("detokenization_result_processing_duration")
            .description("Time taken to process detokenization result events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"detokenization-result-events"},
        groupId = "detokenization-result-service-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "detokenization-result", fallbackMethod = "handleDetokenizationResultEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleDetokenizationResultEvent(
            @Payload DetokenizationResultEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("detokenization-%s-p%d-o%d", event.getTokenId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTokenId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing detokenization result: tokenId={}, eventType={}, status={}",
                event.getTokenId(), event.getEventType(), event.getStatus());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case DETOKENIZATION_SUCCESS:
                    processDetokenizationSuccess(event, correlationId);
                    break;

                case DETOKENIZATION_FAILED:
                    processDetokenizationFailed(event, correlationId);
                    break;

                case DETOKENIZATION_INVALID_TOKEN:
                    processDetokenizationInvalidToken(event, correlationId);
                    break;

                case DETOKENIZATION_EXPIRED_TOKEN:
                    processDetokenizationExpiredToken(event, correlationId);
                    break;

                case DETOKENIZATION_SECURITY_VIOLATION:
                    processDetokenizationSecurityViolation(event, correlationId);
                    break;

                case DETOKENIZATION_RATE_LIMITED:
                    processDetokenizationRateLimited(event, correlationId);
                    break;

                case DETOKENIZATION_BATCH_COMPLETED:
                    processDetokenizationBatchCompleted(event, correlationId);
                    break;

                case DETOKENIZATION_AUDIT_REQUIRED:
                    processDetokenizationAuditRequired(event, correlationId);
                    break;

                default:
                    log.warn("Unknown detokenization result event type: {}", event.getEventType());
                    processUnknownDetokenizationEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logInfrastructureEvent("DETOKENIZATION_RESULT_EVENT_PROCESSED", event.getTokenId(),
                Map.of("eventType", event.getEventType(), "status", event.getStatus(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process detokenization result event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("detokenization-result-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDetokenizationResultEventFallback(
            DetokenizationResultEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("detokenization-fallback-%s-p%d-o%d", event.getTokenId(), partition, offset);

        log.error("Circuit breaker fallback triggered for detokenization result: tokenId={}, error={}",
            event.getTokenId(), ex.getMessage());

        // Create security incident for circuit breaker
        securityIncidentService.createIncident(
            "DETOKENIZATION_RESULT_CIRCUIT_BREAKER",
            String.format("Detokenization result circuit breaker triggered for token %s", event.getTokenId()),
            "HIGH",
            Map.of("tokenId", event.getTokenId(), "eventType", event.getEventType(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("detokenization-result-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Detokenization Result Circuit Breaker",
                String.format("Token %s detokenization result processing failed: %s",
                    event.getTokenId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDetokenizationResultEvent(
            @Payload DetokenizationResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-detokenization-%s-%d", event.getTokenId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Detokenization result permanently failed: tokenId={}, topic={}, error={}",
            event.getTokenId(), topic, exceptionMessage);

        // Create critical security incident
        securityIncidentService.createCriticalIncident(
            "DETOKENIZATION_RESULT_DLT_EVENT",
            String.format("Detokenization result sent to DLT for token %s", event.getTokenId()),
            Map.of("tokenId", event.getTokenId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "correlationId", correlationId,
                "requiresImmediateAction", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logCriticalInfrastructureEvent("DETOKENIZATION_RESULT_DLT_EVENT", event.getTokenId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Detokenization Result Dead Letter Event",
                String.format("Token %s detokenization result sent to DLT: %s",
                    event.getTokenId(), exceptionMessage),
                Map.of("tokenId", event.getTokenId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void processDetokenizationSuccess(DetokenizationResultEvent event, String correlationId) {
        // Update tokenization result record
        TokenizationResult result = tokenizationResultRepository.findByTokenId(event.getTokenId())
            .orElse(new TokenizationResult());

        result.setTokenId(event.getTokenId());
        result.setStatus("DETOKENIZED_SUCCESS");
        result.setDetokenizedValue(event.getDetokenizedValue());
        result.setDetokenizedAt(LocalDateTime.now());
        result.setProcessingTimeMs(event.getProcessingTimeMs());
        result.setCorrelationId(correlationId);

        tokenizationResultRepository.save(result);

        // Log successful detokenization
        cryptoAuditService.logDetokenizationSuccess(event.getTokenId(), event.getDetokenizedValue(),
            event.getRequesterId(), correlationId);

        // Notify requesting service
        kafkaTemplate.send("tokenization-responses", Map.of(
            "tokenId", event.getTokenId(),
            "status", "DETOKENIZATION_SUCCESS",
            "detokenizedValue", event.getDetokenizedValue(),
            "requesterId", event.getRequesterId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordDetokenizationSuccess(event.getTokenType());

        log.info("Detokenization successful: tokenId={}, processingTime={}ms",
            event.getTokenId(), event.getProcessingTimeMs());
    }

    private void processDetokenizationFailed(DetokenizationResultEvent event, String correlationId) {
        // Update tokenization result record
        TokenizationResult result = tokenizationResultRepository.findByTokenId(event.getTokenId())
            .orElse(new TokenizationResult());

        result.setTokenId(event.getTokenId());
        result.setStatus("DETOKENIZATION_FAILED");
        result.setErrorMessage(event.getErrorMessage());
        result.setFailedAt(LocalDateTime.now());
        result.setCorrelationId(correlationId);

        tokenizationResultRepository.save(result);

        // Log detokenization failure
        cryptoAuditService.logDetokenizationFailure(event.getTokenId(), event.getErrorMessage(),
            event.getRequesterId(), correlationId);

        // Create incident for failure
        securityIncidentService.createIncident(
            "DETOKENIZATION_FAILED",
            String.format("Detokenization failed for token %s", event.getTokenId()),
            "MEDIUM",
            Map.of("tokenId", event.getTokenId(), "errorMessage", event.getErrorMessage(),
                "requesterId", event.getRequesterId(), "correlationId", correlationId)
        );

        // Notify requesting service of failure
        kafkaTemplate.send("tokenization-responses", Map.of(
            "tokenId", event.getTokenId(),
            "status", "DETOKENIZATION_FAILED",
            "errorMessage", event.getErrorMessage(),
            "requesterId", event.getRequesterId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordDetokenizationFailure(event.getTokenType(), event.getErrorCode());

        log.warn("Detokenization failed: tokenId={}, error={}",
            event.getTokenId(), event.getErrorMessage());
    }

    private void processDetokenizationInvalidToken(DetokenizationResultEvent event, String correlationId) {
        // Log invalid token attempt
        cryptoAuditService.logInvalidTokenAttempt(event.getTokenId(), event.getRequesterId(), correlationId);

        // Create security incident for invalid token
        securityIncidentService.createIncident(
            "DETOKENIZATION_INVALID_TOKEN",
            String.format("Invalid token detokenization attempt: %s", event.getTokenId()),
            "HIGH",
            Map.of("tokenId", event.getTokenId(), "requesterId", event.getRequesterId(),
                "sourceIp", event.getSourceIp(), "correlationId", correlationId)
        );

        // Check for potential attack patterns
        detokenizationService.analyzeInvalidTokenPatterns(event.getRequesterId(), event.getSourceIp());

        // Send security alert if suspicious
        if (detokenizationService.isSuspiciousActivity(event.getRequesterId(), event.getSourceIp())) {
            notificationService.sendSecurityAlert(
                "Suspicious Detokenization Activity",
                String.format("Multiple invalid token attempts from requester %s", event.getRequesterId()),
                "HIGH"
            );
        }

        metricsService.recordInvalidToken(event.getTokenType());

        log.warn("Invalid token detokenization attempt: tokenId={}, requesterId={}, sourceIp={}",
            event.getTokenId(), event.getRequesterId(), event.getSourceIp());
    }

    private void processDetokenizationExpiredToken(DetokenizationResultEvent event, String correlationId) {
        // Log expired token attempt
        cryptoAuditService.logExpiredTokenAttempt(event.getTokenId(), event.getRequesterId(),
            event.getExpirationTime(), correlationId);

        // Update token status
        detokenizationService.markTokenAsExpired(event.getTokenId(), event.getExpirationTime());

        // Notify requesting service
        kafkaTemplate.send("tokenization-responses", Map.of(
            "tokenId", event.getTokenId(),
            "status", "TOKEN_EXPIRED",
            "expirationTime", event.getExpirationTime(),
            "requesterId", event.getRequesterId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordExpiredToken(event.getTokenType());

        log.info("Expired token detokenization attempt: tokenId={}, expiredAt={}",
            event.getTokenId(), event.getExpirationTime());
    }

    private void processDetokenizationSecurityViolation(DetokenizationResultEvent event, String correlationId) {
        // Create critical security incident
        securityIncidentService.createCriticalIncident(
            "DETOKENIZATION_SECURITY_VIOLATION",
            String.format("Security violation in detokenization for token %s", event.getTokenId()),
            Map.of("tokenId", event.getTokenId(), "violationType", event.getViolationType(),
                "requesterId", event.getRequesterId(), "sourceIp", event.getSourceIp(),
                "correlationId", correlationId)
        );

        // Log security violation
        cryptoAuditService.logSecurityViolation(event.getTokenId(), event.getViolationType(),
            event.getRequesterId(), event.getSourceIp(), correlationId);

        // Block requester temporarily
        detokenizationService.blockRequester(event.getRequesterId(), "SECURITY_VIOLATION",
            event.getViolationType());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Detokenization Security Violation",
            String.format("Security violation detected: %s for token %s",
                event.getViolationType(), event.getTokenId()),
            "CRITICAL"
        );

        metricsService.recordSecurityViolation(event.getViolationType());

        log.error("Detokenization security violation: tokenId={}, violationType={}, requesterId={}",
            event.getTokenId(), event.getViolationType(), event.getRequesterId());
    }

    private void processDetokenizationRateLimited(DetokenizationResultEvent event, String correlationId) {
        // Log rate limiting
        cryptoAuditService.logRateLimitExceeded(event.getTokenId(), event.getRequesterId(),
            event.getCurrentRate(), event.getRateLimit(), correlationId);

        // Update rate limit status
        detokenizationService.updateRateLimitStatus(event.getRequesterId(), event.getCurrentRate(),
            event.getRateLimit());

        // Notify requesting service
        kafkaTemplate.send("tokenization-responses", Map.of(
            "tokenId", event.getTokenId(),
            "status", "RATE_LIMITED",
            "currentRate", event.getCurrentRate(),
            "rateLimit", event.getRateLimit(),
            "retryAfter", event.getRetryAfter(),
            "requesterId", event.getRequesterId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordRateLimitExceeded(event.getRequesterId());

        log.warn("Detokenization rate limited: tokenId={}, requesterId={}, rate={}/{}",
            event.getTokenId(), event.getRequesterId(), event.getCurrentRate(), event.getRateLimit());
    }

    private void processDetokenizationBatchCompleted(DetokenizationResultEvent event, String correlationId) {
        // Update batch processing status
        detokenizationService.updateBatchStatus(event.getBatchId(), "COMPLETED",
            event.getProcessedCount(), event.getFailedCount());

        // Log batch completion
        cryptoAuditService.logBatchCompletion(event.getBatchId(), event.getProcessedCount(),
            event.getFailedCount(), event.getProcessingTimeMs(), correlationId);

        // Notify batch requester
        kafkaTemplate.send("batch-processing-results", Map.of(
            "batchId", event.getBatchId(),
            "status", "COMPLETED",
            "processedCount", event.getProcessedCount(),
            "failedCount", event.getFailedCount(),
            "processingTimeMs", event.getProcessingTimeMs(),
            "requesterId", event.getRequesterId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordBatchProcessingCompleted(event.getProcessedCount(), event.getFailedCount(),
            event.getProcessingTimeMs());

        log.info("Detokenization batch completed: batchId={}, processed={}, failed={}, time={}ms",
            event.getBatchId(), event.getProcessedCount(), event.getFailedCount(), event.getProcessingTimeMs());
    }

    private void processDetokenizationAuditRequired(DetokenizationResultEvent event, String correlationId) {
        // Create audit requirement record
        cryptoAuditService.createAuditRequirement(event.getTokenId(), event.getAuditReason(),
            event.getRequesterId(), correlationId);

        // Queue for security review
        detokenizationService.queueForSecurityReview(event.getTokenId(), event.getAuditReason(),
            event.getRequesterId());

        // Send notification to compliance team
        notificationService.sendComplianceAlert(
            "Detokenization Audit Required",
            String.format("Audit required for token %s: %s", event.getTokenId(), event.getAuditReason()),
            Map.of("tokenId", event.getTokenId(), "auditReason", event.getAuditReason(),
                "requesterId", event.getRequesterId(), "correlationId", correlationId)
        );

        metricsService.recordAuditRequired(event.getAuditReason());

        log.info("Detokenization audit required: tokenId={}, reason={}, requesterId={}",
            event.getTokenId(), event.getAuditReason(), event.getRequesterId());
    }

    private void processUnknownDetokenizationEvent(DetokenizationResultEvent event, String correlationId) {
        // Create incident for unknown event type
        securityIncidentService.createIncident(
            "UNKNOWN_DETOKENIZATION_RESULT_EVENT",
            String.format("Unknown detokenization result event type %s for token %s",
                event.getEventType(), event.getTokenId()),
            "MEDIUM",
            Map.of("tokenId", event.getTokenId(), "unknownEventType", event.getEventType(),
                "requesterId", event.getRequesterId(), "correlationId", correlationId)
        );

        log.warn("Unknown detokenization result event: tokenId={}, eventType={}, requesterId={}",
            event.getTokenId(), event.getEventType(), event.getRequesterId());
    }
}