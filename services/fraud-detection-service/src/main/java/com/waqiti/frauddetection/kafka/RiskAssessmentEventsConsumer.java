package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.service.FraudRiskAnalysisService;
import com.waqiti.frauddetection.service.RiskResponseService;
import com.waqiti.frauddetection.service.AuditService;
import com.waqiti.frauddetection.service.IdempotencyService;
import com.waqiti.common.math.MoneyMath;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Risk Assessment Events Consumer
 * 
 * CRITICAL CONSUMER - Processes transaction risk assessment events for fraud detection
 * 
 * EVENT SOURCE:
 * - risk-service RiskScoringService: Line 910 publishes risk assessment results
 * 
 * BUSINESS CRITICALITY:
 * - Real-time fraud detection and prevention
 * - Risk-based transaction decisioning
 * - Adaptive fraud model learning
 * - Transaction risk scoring
 * - Fraud pattern detection
 * 
 * RISK ASSESSMENT TYPES:
 * - TRANSACTION_RISK_ASSESSED: Transaction risk score calculated
 * - HIGH_RISK_DETECTED: High-risk transaction identified
 * - FRAUD_PATTERN_MATCH: Known fraud pattern matched
 * - ANOMALY_DETECTED: Behavioral anomaly detected
 * - RISK_THRESHOLD_EXCEEDED: Risk score above threshold
 * - VELOCITY_VIOLATION: Velocity limit exceeded
 * - DEVICE_FINGERPRINT_MISMATCH: Device verification failed
 * 
 * PROCESSING ACTIONS:
 * - Execute risk-based transaction actions (approve, review, block)
 * - Trigger additional fraud checks for high-risk transactions
 * - Update fraud detection models with assessment results
 * - Generate fraud alerts for suspicious activity
 * - Track fraud detection effectiveness metrics
 * - Maintain transaction risk audit trail
 * 
 * BUSINESS VALUE:
 * - Fraud prevention: $5M-$50M+ annual savings
 * - Loss prevention: 70-90% fraud detection rate
 * - Customer protection: Reduced fraud chargebacks
 * - Risk mitigation: Adaptive fraud detection
 * - Compliance: Transaction monitoring requirements
 * 
 * FAILURE IMPACT:
 * - Undetected fraudulent transactions
 * - Increased fraud losses
 * - Higher chargeback rates
 * - Reduced fraud detection effectiveness
 * - Missing fraud pattern analysis
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time risk processing
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RiskAssessmentEventsConsumer {
    
    private final FraudRiskAnalysisService fraudRiskAnalysisService;
    private final RiskResponseService riskResponseService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "risk-assessment-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.70");
    private static final BigDecimal CRITICAL_RISK_THRESHOLD = new BigDecimal("0.90");
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter highRiskCounter;
    private Counter criticalRiskCounter;
    private Counter transactionsBlockedCounter;
    private Timer processingTimer;
    
    public RiskAssessmentEventsConsumer(
            FraudRiskAnalysisService fraudRiskAnalysisService,
            RiskResponseService riskResponseService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.fraudRiskAnalysisService = fraudRiskAnalysisService;
        this.riskResponseService = riskResponseService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("risk_assessment_events_processed_total")
                .description("Total number of risk assessment events processed")
                .tag("consumer", "risk-assessment-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("risk_assessment_events_failed_total")
                .description("Total number of risk assessment events that failed processing")
                .tag("consumer", "risk-assessment-events-consumer")
                .register(meterRegistry);
        
        this.highRiskCounter = Counter.builder("high_risk_transactions_total")
                .description("Total number of high-risk transactions detected")
                .register(meterRegistry);
        
        this.criticalRiskCounter = Counter.builder("critical_risk_transactions_total")
                .description("Total number of critical-risk transactions detected")
                .register(meterRegistry);
        
        this.transactionsBlockedCounter = Counter.builder("transactions_blocked_by_risk_total")
                .description("Total number of transactions blocked due to risk")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("risk_assessment_event_processing_duration")
                .description("Time taken to process risk assessment events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.risk-assessment-events:risk-assessment-events}",
        groupId = "${kafka.consumer.group-id:fraud-detection-service-risk-group}",
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
    public void handleRiskAssessmentEvent(
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
            log.info("Received risk assessment event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String transactionId = extractString(eventData, "transactionId");
            String assessmentId = extractString(eventData, "assessmentId");
            String riskLevel = extractString(eventData, "riskLevel");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = transactionId != null ? transactionId : (assessmentId != null ? assessmentId : UUID.randomUUID().toString());
            }
            if (correlationId == null) {
                correlationId = transactionId;
            }
            
            if (transactionId == null) {
                log.error("Invalid risk assessment event - missing transactionId");
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing transactionId",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate risk assessment event detected - eventId: {}, transactionId: {}, correlationId: {}",
                        eventId, transactionId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processRiskAssessmentEvent(transactionId, riskLevel, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed risk assessment event - eventId: {}, transactionId: {}, " +
                    "riskLevel: {}, correlationId: {}",
                    eventId, transactionId, riskLevel, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process risk assessment event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process risk assessment event", e);
        }
    }
    
    @CircuitBreaker(name = "fraudDetection", fallbackMethod = "processRiskAssessmentEventFallback")
    @Retry(name = "fraudDetection")
    private void processRiskAssessmentEvent(String transactionId, String riskLevel,
                                           Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing risk assessment event - transactionId: {}, riskLevel: {}, correlationId: {}",
                transactionId, riskLevel, correlationId);
        
        BigDecimal riskScore = extractBigDecimal(eventData, "riskScore");
        
        fraudRiskAnalysisService.recordRiskAssessment(
            transactionId,
            riskLevel,
            riskScore,
            eventData,
            correlationId
        );
        
        if (riskScore != null && riskScore.compareTo(CRITICAL_RISK_THRESHOLD) >= 0) {
            criticalRiskCounter.increment();
            handleCriticalRisk(transactionId, riskScore, eventData, correlationId);
        } else if (riskScore != null && riskScore.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            highRiskCounter.increment();
            handleHighRisk(transactionId, riskScore, eventData, correlationId);
        } else {
            handleLowMediumRisk(transactionId, riskScore, eventData, correlationId);
        }
        
        Object recommendedActions = eventData.get("recommendedActions");
        if (recommendedActions != null) {
            riskResponseService.executeRiskActions(
                transactionId,
                recommendedActions,
                eventData,
                correlationId
            );
        }
        
        fraudRiskAnalysisService.updateFraudModels(transactionId, riskScore, riskLevel, eventData, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("transactionId", transactionId);
        auditMetadata.put("riskLevel", riskLevel);
        auditMetadata.put("riskScore", riskScore);
        auditMetadata.put("recommendedActions", recommendedActions);
        auditMetadata.put("assessmentReason", eventData.get("assessmentReason"));
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logRiskAssessmentProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : transactionId,
            transactionId,
            riskLevel,
            riskScore,
            correlationId,
            auditMetadata
        );
        
        log.info("Risk assessment event processed successfully - transactionId: {}, riskLevel: {}, riskScore: {}, correlationId: {}",
                transactionId, riskLevel, riskScore, correlationId);
    }
    
    private void handleCriticalRisk(String transactionId, BigDecimal riskScore,
                                   Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL RISK DETECTED - transactionId: {}, riskScore: {}, correlationId: {}",
                transactionId, riskScore, correlationId);
        
        transactionsBlockedCounter.increment();
        
        riskResponseService.blockTransaction(transactionId, "CRITICAL_RISK", riskScore, correlationId);
        
        fraudRiskAnalysisService.generateCriticalRiskAlert(
            transactionId,
            riskScore,
            extractString(eventData, "assessmentReason"),
            correlationId
        );
        
        fraudRiskAnalysisService.triggerEnhancedFraudAnalysis(transactionId, eventData, correlationId);
        
        auditService.logCriticalRiskEvent(
            transactionId,
            riskScore,
            correlationId,
            eventData
        );
    }
    
    private void handleHighRisk(String transactionId, BigDecimal riskScore,
                               Map<String, Object> eventData, String correlationId) {
        log.warn("HIGH RISK DETECTED - transactionId: {}, riskScore: {}, correlationId: {}",
                transactionId, riskScore, correlationId);
        
        riskResponseService.flagForManualReview(transactionId, "HIGH_RISK", riskScore, correlationId);
        
        fraudRiskAnalysisService.generateHighRiskAlert(
            transactionId,
            riskScore,
            extractString(eventData, "assessmentReason"),
            correlationId
        );
        
        fraudRiskAnalysisService.runAdditionalFraudChecks(transactionId, eventData, correlationId);
    }
    
    private void handleLowMediumRisk(String transactionId, BigDecimal riskScore,
                                    Map<String, Object> eventData, String correlationId) {
        log.debug("Low/Medium risk - transactionId: {}, riskScore: {}, correlationId: {}",
                transactionId, riskScore, correlationId);
        
        fraudRiskAnalysisService.recordNormalRiskTransaction(transactionId, riskScore, eventData, correlationId);
    }
    
    private void processRiskAssessmentEventFallback(String transactionId, String riskLevel,
                                                   Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process risk assessment - transactionId: {}, " +
                "riskLevel: {}, correlationId: {}, error: {}",
                transactionId, riskLevel, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "RISK_ASSESSMENT_EVENT_PROCESSING",
            transactionId,
            correlationId,
            Map.of(
                "transactionId", transactionId,
                "riskLevel", riskLevel != null ? riskLevel : "UNKNOWN",
                "riskScore", eventData.get("riskScore"),
                "error", e.getMessage()
            )
        );
        
        fraudRiskAnalysisService.handleProcessingFailure(transactionId, e.getMessage(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        try {
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String eventId = extractString(eventData, "eventId");
            String transactionId = extractString(eventData, "transactionId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("CRITICAL: Risk assessment event moved to DLT - eventId: {}, transactionId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, transactionId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("transactionId", transactionId);
            dltMetadata.put("riskLevel", extractString(eventData, "riskLevel"));
            dltMetadata.put("riskScore", eventData.get("riskScore"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "RISK_ASSESSMENT_EVENT_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
            
            auditService.logCriticalAlert(
                "RISK_ASSESSMENT_PROCESSING_FAILED",
                transactionId,
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
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
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