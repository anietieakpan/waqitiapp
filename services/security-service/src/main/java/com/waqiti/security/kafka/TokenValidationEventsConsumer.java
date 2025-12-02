package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.TokenSecurityService;
import com.waqiti.security.service.TokenAnomalyDetectionService;
import com.waqiti.security.service.AuditService;
import com.waqiti.security.service.IdempotencyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Token Validation Events Consumer
 * 
 * CRITICAL CONSUMER - Processes payment token validation results for security monitoring
 * 
 * EVENT SOURCE:
 * - payment-service PaymentTokenizationEventsConsumer: Line 926 publishes token validation results
 * 
 * BUSINESS CRITICALITY:
 * - Monitors payment token security
 * - Detects token validation anomalies
 * - Tracks token failure patterns
 * - Prevents token-based fraud
 * - Ensures PCI DSS tokenization compliance
 * 
 * TOKEN VALIDATION TYPES:
 * - TOKEN_VALID: Token successfully validated
 * - TOKEN_INVALID: Token validation failed
 * - TOKEN_EXPIRED: Token past expiration
 * - TOKEN_SUSPENDED: Token suspended due to security
 * - TOKEN_TAMPERED: Token tampering detected
 * - TOKEN_MISMATCH: Token data mismatch
 * - TOKEN_REVOKED: Token revoked by issuer
 * 
 * PROCESSING ACTIONS:
 * - Track token validation success/failure rates
 * - Detect abnormal token validation patterns
 * - Generate alerts for suspicious token activity
 * - Monitor token lifecycle health
 * - Track PCI DSS tokenization compliance
 * - Analyze token security metrics
 * 
 * BUSINESS VALUE:
 * - Fraud prevention: Detect compromised tokens
 * - Security: Real-time token monitoring
 * - PCI compliance: Token security audit trail
 * - Risk mitigation: Early token fraud detection
 * - Customer protection: Prevent unauthorized use
 * 
 * FAILURE IMPACT:
 * - Undetected token security issues
 * - Increased token-based fraud
 * - PCI DSS compliance violations
 * - Missing token anomaly detection
 * - Reduced payment security
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time security monitoring
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenValidationEventsConsumer {
    
    private final TokenSecurityService tokenSecurityService;
    private final TokenAnomalyDetectionService tokenAnomalyDetectionService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "token-validation-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter validTokensCounter;
    private Counter invalidTokensCounter;
    private Counter suspendedTokensCounter;
    private Counter tamperedTokensCounter;
    private Timer processingTimer;
    
    public TokenValidationEventsConsumer(
            TokenSecurityService tokenSecurityService,
            TokenAnomalyDetectionService tokenAnomalyDetectionService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.tokenSecurityService = tokenSecurityService;
        this.tokenAnomalyDetectionService = tokenAnomalyDetectionService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("token_validation_events_processed_total")
                .description("Total number of token validation events processed")
                .tag("consumer", "token-validation-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("token_validation_events_failed_total")
                .description("Total number of token validation events that failed processing")
                .tag("consumer", "token-validation-events-consumer")
                .register(meterRegistry);
        
        this.validTokensCounter = Counter.builder("valid_tokens_total")
                .description("Total number of valid token validations")
                .register(meterRegistry);
        
        this.invalidTokensCounter = Counter.builder("invalid_tokens_total")
                .description("Total number of invalid token validations")
                .register(meterRegistry);
        
        this.suspendedTokensCounter = Counter.builder("suspended_tokens_total")
                .description("Total number of suspended tokens detected")
                .register(meterRegistry);
        
        this.tamperedTokensCounter = Counter.builder("tampered_tokens_total")
                .description("Total number of tampered tokens detected")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("token_validation_event_processing_duration")
                .description("Time taken to process token validation events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.token-validation-events:token-validation-events}",
        groupId = "${kafka.consumer.group-id:security-service-token-validation-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 30)
    public void handleTokenValidationEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = null;
        String correlationId = null;
        
        try {
            log.info("Received token validation event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String tokenId = extractString(eventData, "tokenId");
            Boolean valid = extractBoolean(eventData, "valid");
            String failureReason = extractString(eventData, "failureReason");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = tokenId != null ? tokenId : UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = tokenId;
            }
            
            if (tokenId == null) {
                log.error("Invalid token validation event - missing tokenId");
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing tokenId",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate token validation event detected - eventId: {}, tokenId: {}, correlationId: {}",
                        eventId, tokenId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processTokenValidationEvent(tokenId, valid, failureReason, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed token validation event - eventId: {}, tokenId: {}, " +
                    "valid: {}, correlationId: {}",
                    eventId, tokenId, valid, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process token validation event - eventId: {}, correlationId: {}, error: {}",
                    eventId, correlationId, e.getMessage(), e);
            
            auditService.logEventProcessingFailure(
                eventId,
                TOPIC_NAME,
                "PROCESSING_FAILED",
                e.getMessage(),
                correlationId,
                Map.of(
                    "error", e.getClass().getName(),
                    "errorMessage", e.getMessage()
                )
            );
            
            throw new RuntimeException("Failed to process token validation event", e);
        }
    }
    
    @CircuitBreaker(name = "security", fallbackMethod = "processTokenValidationEventFallback")
    @Retry(name = "security")
    private void processTokenValidationEvent(String tokenId, Boolean valid, String failureReason,
                                            Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing token validation event - tokenId: {}, valid: {}, reason: {}, correlationId: {}",
                tokenId, valid, failureReason, correlationId);
        
        tokenSecurityService.recordTokenValidation(
            tokenId,
            valid != null ? valid : false,
            failureReason,
            eventData,
            correlationId
        );
        
        if (valid != null && valid) {
            validTokensCounter.increment();
            handleValidToken(tokenId, eventData, correlationId);
        } else {
            invalidTokensCounter.increment();
            handleInvalidToken(tokenId, failureReason, eventData, correlationId);
        }
        
        tokenAnomalyDetectionService.analyzeTokenValidationPattern(tokenId, valid, eventData, correlationId);
        
        tokenSecurityService.updateTokenHealthMetrics(tokenId, valid, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("tokenId", tokenId);
        auditMetadata.put("valid", valid);
        auditMetadata.put("failureReason", failureReason);
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logTokenValidationProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : tokenId,
            tokenId,
            valid != null ? valid : false,
            failureReason,
            correlationId,
            auditMetadata
        );
        
        log.info("Token validation event processed successfully - tokenId: {}, valid: {}, correlationId: {}",
                tokenId, valid, correlationId);
    }
    
    private void handleValidToken(String tokenId, Map<String, Object> eventData, String correlationId) {
        log.debug("Valid token - tokenId: {}, correlationId: {}", tokenId, correlationId);
        
        tokenSecurityService.recordSuccessfulValidation(tokenId, eventData, correlationId);
    }
    
    private void handleInvalidToken(String tokenId, String failureReason,
                                   Map<String, Object> eventData, String correlationId) {
        log.warn("Invalid token detected - tokenId: {}, reason: {}, correlationId: {}",
                tokenId, failureReason, correlationId);
        
        if (failureReason == null) {
            failureReason = "UNKNOWN";
        }
        
        switch (failureReason) {
            case "TOKEN_EXPIRED":
                handleExpiredToken(tokenId, eventData, correlationId);
                break;
            case "TOKEN_SUSPENDED":
                suspendedTokensCounter.increment();
                handleSuspendedToken(tokenId, eventData, correlationId);
                break;
            case "TOKEN_TAMPERED":
                tamperedTokensCounter.increment();
                handleTamperedToken(tokenId, eventData, correlationId);
                break;
            case "TOKEN_MISMATCH":
                handleTokenMismatch(tokenId, eventData, correlationId);
                break;
            case "TOKEN_REVOKED":
                handleRevokedToken(tokenId, eventData, correlationId);
                break;
            default:
                handleGenericInvalidToken(tokenId, failureReason, eventData, correlationId);
        }
        
        tokenAnomalyDetectionService.checkInvalidTokenThreshold(tokenId, failureReason, correlationId);
    }
    
    private void handleExpiredToken(String tokenId, Map<String, Object> eventData, String correlationId) {
        log.info("Expired token - tokenId: {}, correlationId: {}", tokenId, correlationId);
        
        tokenSecurityService.handleExpiredToken(tokenId, eventData, correlationId);
    }
    
    private void handleSuspendedToken(String tokenId, Map<String, Object> eventData, String correlationId) {
        log.warn("Suspended token detected - tokenId: {}, correlationId: {}", tokenId, correlationId);
        
        tokenSecurityService.handleSuspendedToken(tokenId, eventData, correlationId);
        
        tokenAnomalyDetectionService.generateSuspendedTokenAlert(tokenId, eventData, correlationId);
    }
    
    private void handleTamperedToken(String tokenId, Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL: Tampered token detected - tokenId: {}, correlationId: {}", tokenId, correlationId);
        
        tokenSecurityService.handleTamperedToken(tokenId, eventData, correlationId);
        
        tokenAnomalyDetectionService.generateCriticalTokenSecurityAlert(
            tokenId,
            "TOKEN_TAMPERED",
            eventData,
            correlationId
        );
        
        auditService.logCriticalSecurityEvent(
            "TOKEN_TAMPERED",
            tokenId,
            correlationId,
            eventData
        );
    }
    
    private void handleTokenMismatch(String tokenId, Map<String, Object> eventData, String correlationId) {
        log.warn("Token data mismatch - tokenId: {}, correlationId: {}", tokenId, correlationId);
        
        tokenSecurityService.handleTokenMismatch(tokenId, eventData, correlationId);
    }
    
    private void handleRevokedToken(String tokenId, Map<String, Object> eventData, String correlationId) {
        log.info("Revoked token - tokenId: {}, correlationId: {}", tokenId, correlationId);
        
        tokenSecurityService.handleRevokedToken(tokenId, eventData, correlationId);
    }
    
    private void handleGenericInvalidToken(String tokenId, String failureReason,
                                          Map<String, Object> eventData, String correlationId) {
        log.info("Invalid token - tokenId: {}, reason: {}, correlationId: {}",
                tokenId, failureReason, correlationId);
        
        tokenSecurityService.recordInvalidToken(tokenId, failureReason, eventData, correlationId);
    }
    
    private void processTokenValidationEventFallback(String tokenId, Boolean valid, String failureReason,
                                                    Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process token validation - tokenId: {}, " +
                "valid: {}, reason: {}, correlationId: {}, error: {}",
                tokenId, valid, failureReason, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "TOKEN_VALIDATION_EVENT_PROCESSING",
            tokenId,
            correlationId,
            Map.of(
                "tokenId", tokenId,
                "valid", valid != null ? valid : false,
                "failureReason", failureReason != null ? failureReason : "UNKNOWN",
                "error", e.getMessage()
            )
        );
        
        tokenSecurityService.handleProcessingFailure(tokenId, e.getMessage(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        try {
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String eventId = extractString(eventData, "eventId");
            String tokenId = extractString(eventData, "tokenId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("CRITICAL: Token validation event moved to DLT - eventId: {}, tokenId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, tokenId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("tokenId", tokenId);
            dltMetadata.put("valid", eventData.get("valid"));
            dltMetadata.put("failureReason", extractString(eventData, "failureReason"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "TOKEN_VALIDATION_EVENT_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
            
            auditService.logCriticalAlert(
                "TOKEN_VALIDATION_PROCESSING_FAILED",
                tokenId,
                correlationId,
                dltMetadata
            );
        } catch (Exception e) {
            log.error("Failed to process DLT event: {}", e.getMessage(), e);
        }
    }
    
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private Boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
}