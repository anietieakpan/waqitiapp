package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.service.RiskAnalysisService;
import com.waqiti.frauddetection.service.AuditService;
import com.waqiti.frauddetection.service.IdempotencyService;
import com.waqiti.common.math.MoneyMath;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Risk Scores Events Consumer
 * 
 * CRITICAL CONSUMER - Processes risk scoring results from risk-service
 * 
 * EVENT SOURCE:
 * - risk-service RiskScoringConsumer: Line 1286 publishes risk scores
 * 
 * BUSINESS CRITICALITY:
 * - Enables real-time fraud detection based on risk scores
 * - Updates fraud detection models with risk intelligence
 * - Triggers automated fraud prevention actions
 * - Supports compliance monitoring for high-risk entities
 * - Provides risk-based authentication decisioning
 * 
 * PROCESSING ACTIONS:
 * - Record risk scores in fraud detection system
 * - Update user/transaction risk profiles
 * - Trigger enhanced monitoring for high-risk entities
 * - Feed risk scores into ML fraud models
 * - Generate alerts for critical risk scores (>0.90)
 * - Audit all risk score updates for compliance
 * 
 * BUSINESS VALUE:
 * - Fraud prevention: Real-time risk-based decisioning
 * - Compliance: Risk scoring audit trail
 * - Customer protection: Proactive fraud detection
 * - Operational efficiency: Automated risk-based actions
 * 
 * FAILURE IMPACT:
 * - Loss of real-time fraud detection capability
 * - Missing risk intelligence for transactions
 * - Inability to trigger automated fraud prevention
 * - Compliance gaps in risk monitoring
 * - Increased fraud losses
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time metrics tracking
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RiskScoresEventsConsumer {
    
    private final FraudDetectionService fraudDetectionService;
    private final RiskAnalysisService riskAnalysisService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "risk-scores";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final BigDecimal CRITICAL_THRESHOLD = new BigDecimal("0.90");
    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("0.70");
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter criticalRiskCounter;
    private Counter highRiskCounter;
    private Timer processingTimer;
    
    public RiskScoresEventsConsumer(
            FraudDetectionService fraudDetectionService,
            RiskAnalysisService riskAnalysisService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.fraudDetectionService = fraudDetectionService;
        this.riskAnalysisService = riskAnalysisService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("risk_scores_events_processed_total")
                .description("Total number of risk score events processed")
                .tag("consumer", "risk-scores-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("risk_scores_events_failed_total")
                .description("Total number of risk score events that failed processing")
                .tag("consumer", "risk-scores-consumer")
                .register(meterRegistry);
        
        this.criticalRiskCounter = Counter.builder("critical_risk_scores_total")
                .description("Total number of critical risk scores received")
                .register(meterRegistry);
        
        this.highRiskCounter = Counter.builder("high_risk_scores_total")
                .description("Total number of high risk scores received")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("risk_scores_event_processing_duration")
                .description("Time taken to process risk score events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.risk-scores:risk-scores}",
        groupId = "${kafka.consumer.group-id:fraud-detection-service-risk-scores-group}",
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
    public void handleRiskScoreEvent(
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
            log.info("Received risk score event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String scoringId = extractString(eventData, "scoringId");
            correlationId = extractString(eventData, "correlationId");
            String entityId = extractString(eventData, "entityId");
            String entityType = extractString(eventData, "entityType");
            BigDecimal overallRiskScore = extractBigDecimal(eventData, "overallRiskScore");
            String riskLevel = extractString(eventData, "riskLevel");
            String decision = extractString(eventData, "decision");
            
            if (eventId == null) {
                eventId = UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            if (scoringId == null || entityId == null || overallRiskScore == null) {
                log.error("Invalid risk score event - missing required fields: scoringId={}, entityId={}, score={}",
                        scoringId, entityId, overallRiskScore);
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing required fields",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate risk score event detected - eventId: {}, scoringId: {}, correlationId: {}",
                        eventId, scoringId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processRiskScore(scoringId, entityId, entityType, overallRiskScore, riskLevel, 
                    decision, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed risk score event - eventId: {}, scoringId: {}, " +
                    "entityId: {}, score: {}, level: {}, correlationId: {}",
                    eventId, scoringId, entityId, overallRiskScore, riskLevel, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process risk score event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process risk score event", e);
        }
    }
    
    @CircuitBreaker(name = "fraudDetection", fallbackMethod = "processRiskScoreFallback")
    @Retry(name = "fraudDetection")
    @TimeLimiter(name = "fraudDetection")
    private void processRiskScore(String scoringId, String entityId, String entityType,
                                  BigDecimal overallRiskScore, String riskLevel, String decision,
                                  Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing risk score - scoringId: {}, entityId: {}, entityType: {}, " +
                "score: {}, level: {}, decision: {}, correlationId: {}",
                scoringId, entityId, entityType, overallRiskScore, riskLevel, decision, correlationId);
        
        riskAnalysisService.recordRiskScore(
            scoringId,
            entityId,
            entityType,
            overallRiskScore,
            riskLevel,
            eventData
        );
        
        if (overallRiskScore.compareTo(CRITICAL_THRESHOLD) >= 0) {
            criticalRiskCounter.increment();
            handleCriticalRisk(scoringId, entityId, entityType, overallRiskScore, eventData, correlationId);
        } else if (overallRiskScore.compareTo(HIGH_THRESHOLD) >= 0) {
            highRiskCounter.increment();
            handleHighRisk(scoringId, entityId, entityType, overallRiskScore, eventData, correlationId);
        }
        
        fraudDetectionService.updateFraudModelsWithRiskScore(
            entityId,
            entityType,
            overallRiskScore,
            riskLevel,
            eventData
        );
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("scoringId", scoringId);
        auditMetadata.put("entityId", entityId);
        auditMetadata.put("entityType", entityType);
        auditMetadata.put("overallRiskScore", overallRiskScore);
        auditMetadata.put("riskLevel", riskLevel);
        auditMetadata.put("decision", decision);
        auditMetadata.put("factorScores", eventData.get("factorScores"));
        
        auditService.logRiskScoreProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : scoringId,
            scoringId,
            entityId,
            entityType,
            overallRiskScore,
            riskLevel,
            decision,
            correlationId,
            auditMetadata
        );
        
        log.info("Risk score processed successfully - scoringId: {}, entityId: {}, correlationId: {}",
                scoringId, entityId, correlationId);
    }
    
    private void handleCriticalRisk(String scoringId, String entityId, String entityType,
                                   BigDecimal riskScore, Map<String, Object> eventData,
                                   String correlationId) {
        log.warn("CRITICAL RISK DETECTED - scoringId: {}, entityId: {}, entityType: {}, score: {}",
                scoringId, entityId, entityType, riskScore);
        
        fraudDetectionService.triggerEnhancedMonitoring(entityId, entityType, riskScore, correlationId);
        
        auditService.logCriticalRiskAlert(
            scoringId,
            entityId,
            entityType,
            riskScore,
            correlationId,
            eventData
        );
    }
    
    private void handleHighRisk(String scoringId, String entityId, String entityType,
                               BigDecimal riskScore, Map<String, Object> eventData,
                               String correlationId) {
        log.info("HIGH RISK DETECTED - scoringId: {}, entityId: {}, entityType: {}, score: {}",
                scoringId, entityId, entityType, riskScore);
        
        fraudDetectionService.enableRiskBasedAuthentication(entityId, entityType, riskScore, correlationId);
    }
    
    private void processRiskScoreFallback(String scoringId, String entityId, String entityType,
                                         BigDecimal overallRiskScore, String riskLevel, String decision,
                                         Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process risk score - scoringId: {}, " +
                "entityId: {}, correlationId: {}, error: {}",
                scoringId, entityId, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "RISK_SCORE_PROCESSING",
            scoringId,
            correlationId,
            Map.of(
                "entityId", entityId,
                "entityType", entityType,
                "overallRiskScore", overallRiskScore,
                "riskLevel", riskLevel,
                "error", e.getMessage()
            )
        );
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        try {
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String eventId = extractString(eventData, "eventId");
            String scoringId = extractString(eventData, "scoringId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Risk score event moved to DLT - eventId: {}, scoringId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, scoringId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("scoringId", scoringId);
            dltMetadata.put("entityId", extractString(eventData, "entityId"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "RISK_SCORE_DLT",
                exceptionMessage,
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
    
    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) {
            // Use MoneyMath.fromDouble for safe conversion
            return MoneyMath.fromDouble(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}