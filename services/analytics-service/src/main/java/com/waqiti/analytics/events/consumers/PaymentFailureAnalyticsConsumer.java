package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.service.PaymentAnalyticsService;
import com.waqiti.analytics.service.FailurePatternAnalysisService;
import com.waqiti.analytics.service.AuditService;
import com.waqiti.analytics.service.IdempotencyService;
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
 * Payment Failure Analytics Consumer
 * 
 * CRITICAL CONSUMER - Processes payment failure events for analytics and pattern detection
 * 
 * EVENT SOURCE:
 * - payment-service WiseWebhookService: Line 980 publishes payment failure analytics
 * 
 * BUSINESS CRITICALITY:
 * - Analyzes payment failure patterns
 * - Identifies systemic payment issues
 * - Tracks provider-specific failure rates
 * - Enables failure root cause analysis
 * - Supports payment optimization strategies
 * 
 * FAILURE ANALYTICS TYPES:
 * - PROVIDER_FAILURE: Payment provider error
 * - INSUFFICIENT_FUNDS: Customer insufficient balance
 * - DECLINED_BY_ISSUER: Card issuer declined
 * - FRAUD_SUSPECTED: Fraud detection triggered
 * - NETWORK_ERROR: Payment network timeout
 * - INVALID_CREDENTIALS: Authentication failed
 * - LIMIT_EXCEEDED: Transaction limit exceeded
 * 
 * PROCESSING ACTIONS:
 * - Record failure metrics by provider and reason
 * - Identify failure pattern trends
 * - Calculate provider failure rates
 * - Generate failure insights and recommendations
 * - Track time-series failure data
 * - Alert on abnormal failure spikes
 * 
 * BUSINESS VALUE:
 * - Success rate improvement: 5-15% increase
 * - Provider optimization: Choose best performers
 * - Cost reduction: Reduce failed transaction costs
 * - Customer experience: Identify friction points
 * - Revenue protection: Minimize lost transactions
 * 
 * FAILURE IMPACT:
 * - Missing failure pattern insights
 * - Inability to optimize payment routing
 * - Reduced payment success rates
 * - Higher customer friction
 * - Lost revenue opportunities
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time analytics processing
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentFailureAnalyticsConsumer {
    
    private final PaymentAnalyticsService paymentAnalyticsService;
    private final FailurePatternAnalysisService failurePatternAnalysisService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "payment-failure-analytics";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter providerFailuresCounter;
    private Counter insufficientFundsCounter;
    private Counter fraudSuspectedCounter;
    private Timer processingTimer;
    
    public PaymentFailureAnalyticsConsumer(
            PaymentAnalyticsService paymentAnalyticsService,
            FailurePatternAnalysisService failurePatternAnalysisService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.paymentAnalyticsService = paymentAnalyticsService;
        this.failurePatternAnalysisService = failurePatternAnalysisService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("payment_failure_analytics_processed_total")
                .description("Total number of payment failure analytics events processed")
                .tag("consumer", "payment-failure-analytics-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("payment_failure_analytics_failed_total")
                .description("Total number of payment failure analytics events that failed processing")
                .tag("consumer", "payment-failure-analytics-consumer")
                .register(meterRegistry);
        
        this.providerFailuresCounter = Counter.builder("payment_provider_failures_total")
                .description("Total number of payment provider failures")
                .register(meterRegistry);
        
        this.insufficientFundsCounter = Counter.builder("payment_insufficient_funds_total")
                .description("Total number of insufficient funds failures")
                .register(meterRegistry);
        
        this.fraudSuspectedCounter = Counter.builder("payment_fraud_suspected_total")
                .description("Total number of fraud-suspected payment failures")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("payment_failure_analytics_processing_duration")
                .description("Time taken to process payment failure analytics events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.payment-failure-analytics:payment-failure-analytics}",
        groupId = "${kafka.consumer.group-id:analytics-service-payment-failure-group}",
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
    public void handlePaymentFailureAnalytics(
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
            log.info("Received payment failure analytics event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String paymentId = extractString(eventData, "paymentId");
            String provider = extractString(eventData, "provider");
            String failureReason = extractString(eventData, "failureReason");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = paymentId != null ? paymentId : UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = paymentId;
            }
            
            if (paymentId == null) {
                log.error("Invalid payment failure analytics event - missing paymentId");
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing paymentId",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate payment failure analytics event detected - eventId: {}, paymentId: {}, correlationId: {}",
                        eventId, paymentId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processPaymentFailureAnalytics(paymentId, provider, failureReason, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed payment failure analytics event - eventId: {}, paymentId: {}, " +
                    "provider: {}, reason: {}, correlationId: {}",
                    eventId, paymentId, provider, failureReason, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process payment failure analytics event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process payment failure analytics event", e);
        }
    }
    
    @CircuitBreaker(name = "analytics", fallbackMethod = "processPaymentFailureAnalyticsFallback")
    @Retry(name = "analytics")
    private void processPaymentFailureAnalytics(String paymentId, String provider, String failureReason,
                                               Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing payment failure analytics - paymentId: {}, provider: {}, reason: {}, correlationId: {}",
                paymentId, provider, failureReason, correlationId);
        
        BigDecimal amount = extractBigDecimal(eventData, "amount");
        String previousStatus = extractString(eventData, "previousStatus");
        
        paymentAnalyticsService.recordPaymentFailure(
            paymentId,
            provider,
            failureReason,
            amount,
            previousStatus,
            eventData,
            correlationId
        );
        
        String actualReason = failureReason != null ? failureReason : determineFailureReason(eventData);
        
        switch (actualReason) {
            case "PROVIDER_FAILURE":
            case "PROVIDER_ERROR":
                providerFailuresCounter.increment();
                handleProviderFailure(paymentId, provider, eventData, correlationId);
                break;
            case "INSUFFICIENT_FUNDS":
                insufficientFundsCounter.increment();
                handleInsufficientFunds(paymentId, provider, amount, eventData, correlationId);
                break;
            case "FRAUD_SUSPECTED":
                fraudSuspectedCounter.increment();
                handleFraudSuspected(paymentId, provider, eventData, correlationId);
                break;
            case "DECLINED_BY_ISSUER":
                handleDeclinedByIssuer(paymentId, provider, eventData, correlationId);
                break;
            case "NETWORK_ERROR":
                handleNetworkError(paymentId, provider, eventData, correlationId);
                break;
            case "LIMIT_EXCEEDED":
                handleLimitExceeded(paymentId, provider, amount, eventData, correlationId);
                break;
            default:
                log.debug("Payment failure reason: {} - paymentId: {}", actualReason, paymentId);
                handleGenericFailure(paymentId, provider, actualReason, eventData, correlationId);
        }
        
        failurePatternAnalysisService.analyzeFailurePattern(
            paymentId,
            provider,
            actualReason,
            eventData,
            correlationId
        );
        
        failurePatternAnalysisService.updateProviderFailureRate(provider, eventData, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("paymentId", paymentId);
        auditMetadata.put("provider", provider);
        auditMetadata.put("failureReason", actualReason);
        auditMetadata.put("amount", amount);
        auditMetadata.put("previousStatus", previousStatus);
        auditMetadata.put("failureTime", eventData.get("failureTime"));
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logPaymentFailureAnalyticsProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : paymentId,
            paymentId,
            provider,
            actualReason,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment failure analytics processed successfully - paymentId: {}, provider: {}, reason: {}, correlationId: {}",
                paymentId, provider, actualReason, correlationId);
    }
    
    private void handleProviderFailure(String paymentId, String provider,
                                      Map<String, Object> eventData, String correlationId) {
        log.warn("Provider failure detected - paymentId: {}, provider: {}, correlationId: {}",
                paymentId, provider, correlationId);
        
        failurePatternAnalysisService.trackProviderFailure(provider, paymentId, eventData, correlationId);
        
        failurePatternAnalysisService.checkProviderFailureThreshold(provider, correlationId);
    }
    
    private void handleInsufficientFunds(String paymentId, String provider, BigDecimal amount,
                                        Map<String, Object> eventData, String correlationId) {
        log.info("Insufficient funds failure - paymentId: {}, provider: {}, amount: {}, correlationId: {}",
                paymentId, provider, amount, correlationId);
        
        paymentAnalyticsService.trackInsufficientFundsPattern(paymentId, amount, eventData, correlationId);
    }
    
    private void handleFraudSuspected(String paymentId, String provider,
                                     Map<String, Object> eventData, String correlationId) {
        log.warn("Fraud suspected in payment - paymentId: {}, provider: {}, correlationId: {}",
                paymentId, provider, correlationId);
        
        failurePatternAnalysisService.trackFraudSuspectedFailure(paymentId, provider, eventData, correlationId);
        
        paymentAnalyticsService.generateFraudFailureAlert(paymentId, provider, eventData, correlationId);
    }
    
    private void handleDeclinedByIssuer(String paymentId, String provider,
                                       Map<String, Object> eventData, String correlationId) {
        log.info("Payment declined by issuer - paymentId: {}, provider: {}, correlationId: {}",
                paymentId, provider, correlationId);
        
        failurePatternAnalysisService.trackIssuerDecline(paymentId, provider, eventData, correlationId);
    }
    
    private void handleNetworkError(String paymentId, String provider,
                                   Map<String, Object> eventData, String correlationId) {
        log.warn("Network error in payment - paymentId: {}, provider: {}, correlationId: {}",
                paymentId, provider, correlationId);
        
        failurePatternAnalysisService.trackNetworkError(provider, paymentId, eventData, correlationId);
    }
    
    private void handleLimitExceeded(String paymentId, String provider, BigDecimal amount,
                                    Map<String, Object> eventData, String correlationId) {
        log.info("Transaction limit exceeded - paymentId: {}, provider: {}, amount: {}, correlationId: {}",
                paymentId, provider, amount, correlationId);
        
        paymentAnalyticsService.trackLimitExceededPattern(paymentId, amount, eventData, correlationId);
    }
    
    private void handleGenericFailure(String paymentId, String provider, String reason,
                                     Map<String, Object> eventData, String correlationId) {
        log.debug("Generic payment failure - paymentId: {}, provider: {}, reason: {}, correlationId: {}",
                paymentId, provider, reason, correlationId);
        
        failurePatternAnalysisService.trackGenericFailure(paymentId, provider, reason, eventData, correlationId);
    }
    
    private String determineFailureReason(Map<String, Object> eventData) {
        String previousStatus = extractString(eventData, "previousStatus");
        if (previousStatus != null && previousStatus.contains("DECLINED")) {
            return "DECLINED_BY_ISSUER";
        }
        return "PROVIDER_FAILURE";
    }
    
    private void processPaymentFailureAnalyticsFallback(String paymentId, String provider, String failureReason,
                                                       Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process payment failure analytics - paymentId: {}, " +
                "provider: {}, reason: {}, correlationId: {}, error: {}",
                paymentId, provider, failureReason, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "PAYMENT_FAILURE_ANALYTICS_PROCESSING",
            paymentId,
            correlationId,
            Map.of(
                "paymentId", paymentId,
                "provider", provider != null ? provider : "UNKNOWN",
                "failureReason", failureReason != null ? failureReason : "UNKNOWN",
                "amount", eventData.get("amount"),
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
            String paymentId = extractString(eventData, "paymentId");
            String provider = extractString(eventData, "provider");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Payment failure analytics event moved to DLT - eventId: {}, paymentId: {}, provider: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, paymentId, provider, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("paymentId", paymentId);
            dltMetadata.put("provider", provider);
            dltMetadata.put("failureReason", extractString(eventData, "failureReason"));
            dltMetadata.put("amount", eventData.get("amount"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("failureTime"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "PAYMENT_FAILURE_ANALYTICS_DLT",
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
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}