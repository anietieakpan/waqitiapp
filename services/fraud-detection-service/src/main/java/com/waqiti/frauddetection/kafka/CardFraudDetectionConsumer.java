package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.math.MoneyMath;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for card fraud detection events
 * Handles card fraud pattern detection with real-time transaction analysis,
 * card blocking, and merchant risk assessment
 * 
 * Critical for: Card fraud prevention, transaction security, financial protection
 * SLA: Must process card fraud events within 3 seconds for real-time blocking
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CardFraudDetectionConsumer {

    private final CardFraudDetectionService cardFraudDetectionService;
    private final CardSecurityService cardSecurityService;
    private final TransactionAnalysisService transactionAnalysisService;
    private final MerchantRiskService merchantRiskService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter cardFraudCounter = Counter.builder("card_fraud_events_processed_total")
            .description("Total number of card fraud events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter fraudBlockedCounter = Counter.builder("card_fraud_blocked_total")
            .description("Total number of card fraud events blocked")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("card_fraud_processing_duration")
            .description("Time taken to process card fraud events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"card-fraud-detection"},
        groupId = "fraud-service-card-fraud-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "card-fraud-detection-processor", fallbackMethod = "handleCardFraudFailure")
    @Retry(name = "card-fraud-detection-processor")
    public void processCardFraudEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.info("Processing card fraud event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Card fraud event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate card fraud data
            CardFraudData fraudData = extractCardFraudData(event.getPayload());
            validateCardFraudData(fraudData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process card fraud detection
            processCardFraud(fraudData, event);

            // Record successful processing metrics
            cardFraudCounter.increment();
            
            // Audit the fraud processing
            auditCardFraudProcessing(fraudData, event, "SUCCESS");

            log.info("Successfully processed card fraud: {} for card: {} - risk: {} amount: {}", 
                    eventId, fraudData.getCardNumber().substring(fraudData.getCardNumber().length() - 4), 
                    fraudData.getRiskScore(), fraudData.getTransactionAmount());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid card fraud event data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process card fraud event: {}", eventId, e);
            auditCardFraudProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("Card fraud event processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private CardFraudData extractCardFraudData(Map<String, Object> payload) {
        return CardFraudData.builder()
                .transactionId(extractString(payload, "transactionId"))
                .cardNumber(extractString(payload, "cardNumber"))
                .accountId(extractString(payload, "accountId"))
                .merchantId(extractString(payload, "merchantId"))
                .merchantName(extractString(payload, "merchantName"))
                .transactionAmount(extractBigDecimal(payload, "transactionAmount"))
                .currency(extractString(payload, "currency"))
                .transactionType(extractString(payload, "transactionType"))
                .location(extractMap(payload, "location"))
                .deviceInfo(extractMap(payload, "deviceInfo"))
                .fraudIndicators(extractMap(payload, "fraudIndicators"))
                .riskScore(extractDouble(payload, "riskScore"))
                .fraudType(extractString(payload, "fraudType"))
                .detectionMethod(extractString(payload, "detectionMethod"))
                .transactionTime(extractInstant(payload, "transactionTime"))
                .detectionTime(extractInstant(payload, "detectionTime"))
                .build();
    }

    private void validateCardFraudData(CardFraudData fraudData) {
        if (fraudData.getTransactionId() == null || fraudData.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (fraudData.getCardNumber() == null || fraudData.getCardNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Card number is required");
        }
        
        if (fraudData.getTransactionAmount() == null || fraudData.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid transaction amount is required");
        }
        
        if (fraudData.getRiskScore() == null || fraudData.getRiskScore() < 0.0 || fraudData.getRiskScore() > 1.0) {
            throw new IllegalArgumentException("Risk score must be between 0.0 and 1.0");
        }
    }

    private void processCardFraud(CardFraudData fraudData, GenericKafkaEvent event) {
        log.info("Processing card fraud - TransactionId: {}, Card: ****{}, Amount: {} {}, Risk: {}, Type: {}", 
                fraudData.getTransactionId(), 
                fraudData.getCardNumber().substring(fraudData.getCardNumber().length() - 4),
                fraudData.getTransactionAmount(), fraudData.getCurrency(),
                fraudData.getRiskScore(), fraudData.getFraudType());

        try {
            // Analyze transaction patterns
            TransactionAnalysisResult analysis = analyzeTransaction(fraudData);

            // Assess merchant risk
            MerchantRiskAssessment merchantRisk = assessMerchantRisk(fraudData);

            // Determine fraud response
            FraudResponse response = determineFraudResponse(fraudData, analysis, merchantRisk);

            // Execute fraud response
            executeFraudResponse(fraudData, response);

            // Record fraud detection
            recordFraudDetection(fraudData, analysis, response);

            // Send notifications
            sendFraudNotifications(fraudData, response);

            if (response.isBlocked()) {
                fraudBlockedCounter.increment();
            }

            log.info("Card fraud processed - Response: {}, Blocked: {}, Action: {}", 
                    response.getResponseType(), response.isBlocked(), response.getAction());

        } catch (Exception e) {
            log.error("Failed to process card fraud for transaction: {}", fraudData.getTransactionId(), e);
            
            // Apply emergency card blocking if high risk
            if (fraudData.getRiskScore() > 0.8) {
                cardSecurityService.emergencyBlockCard(fraudData.getCardNumber(), "Processing failure with high risk");
            }
            
            throw new RuntimeException("Card fraud processing failed", e);
        }
    }

    private TransactionAnalysisResult analyzeTransaction(CardFraudData fraudData) {
        return transactionAnalysisService.analyzeTransactionForFraud(
                fraudData.getTransactionId(),
                fraudData.getCardNumber(),
                fraudData.getTransactionAmount(),
                fraudData.getLocation(),
                fraudData.getDeviceInfo(),
                fraudData.getFraudIndicators()
        );
    }

    private MerchantRiskAssessment assessMerchantRisk(CardFraudData fraudData) {
        return merchantRiskService.assessMerchantRisk(
                fraudData.getMerchantId(),
                fraudData.getMerchantName(),
                fraudData.getTransactionAmount(),
                fraudData.getTransactionType()
        );
    }

    private FraudResponse determineFraudResponse(CardFraudData fraudData, 
                                               TransactionAnalysisResult analysis,
                                               MerchantRiskAssessment merchantRisk) {
        double combinedRisk = calculateCombinedRisk(fraudData.getRiskScore(), analysis, merchantRisk);
        
        if (combinedRisk >= 0.9) {
            return FraudResponse.builder()
                    .responseType("BLOCK_IMMEDIATELY")
                    .blocked(true)
                    .action("BLOCK_CARD_AND_TRANSACTION")
                    .reason("High fraud risk detected")
                    .build();
        } else if (combinedRisk >= 0.7) {
            return FraudResponse.builder()
                    .responseType("REQUIRE_VERIFICATION")
                    .blocked(false)
                    .action("STEP_UP_AUTHENTICATION")
                    .reason("Medium fraud risk - verification required")
                    .build();
        } else {
            return FraudResponse.builder()
                    .responseType("MONITOR")
                    .blocked(false)
                    .action("ENHANCED_MONITORING")
                    .reason("Low to medium risk - monitor closely")
                    .build();
        }
    }

    private double calculateCombinedRisk(double baseRisk, 
                                       TransactionAnalysisResult analysis,
                                       MerchantRiskAssessment merchantRisk) {
        double analysisRisk = analysis.getRiskScore() * 0.4;
        double merchantRiskScore = merchantRisk.getRiskScore() * 0.3;
        double baseRiskScore = baseRisk * 0.3;
        
        return Math.min(1.0, analysisRisk + merchantRiskScore + baseRiskScore);
    }

    private void executeFraudResponse(CardFraudData fraudData, FraudResponse response) {
        switch (response.getAction()) {
            case "BLOCK_CARD_AND_TRANSACTION":
                cardSecurityService.blockCard(fraudData.getCardNumber(), response.getReason());
                cardFraudDetectionService.blockTransaction(fraudData.getTransactionId(), response.getReason());
                break;
                
            case "STEP_UP_AUTHENTICATION":
                cardSecurityService.requireStepUpAuth(fraudData.getCardNumber(), fraudData.getTransactionId());
                break;
                
            case "ENHANCED_MONITORING":
                cardSecurityService.enableEnhancedMonitoring(fraudData.getCardNumber());
                break;
                
            default:
                log.debug("No specific action required for response: {}", response.getAction());
        }
    }

    private void recordFraudDetection(CardFraudData fraudData, 
                                    TransactionAnalysisResult analysis,
                                    FraudResponse response) {
        cardFraudDetectionService.recordFraudDetection(
                fraudData.getTransactionId(),
                fraudData.getCardNumber(),
                fraudData.getFraudType(),
                fraudData.getRiskScore(),
                analysis.getRiskScore(),
                response.getResponseType()
        );
    }

    private void sendFraudNotifications(CardFraudData fraudData, FraudResponse response) {
        if (response.isBlocked()) {
            fraudNotificationService.sendCardBlockedAlert(
                    "Card Blocked Due to Fraud",
                    fraudData,
                    response
            );
            
            // Send customer notification
            fraudNotificationService.sendCustomerCardBlockedNotification(
                    fraudData.getAccountId(),
                    fraudData.getCardNumber(),
                    response.getReason()
            );
        } else if ("REQUIRE_VERIFICATION".equals(response.getResponseType())) {
            fraudNotificationService.sendVerificationRequiredAlert(
                    fraudData.getAccountId(),
                    fraudData.getTransactionId()
            );
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("Card fraud validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "CARD_FRAUD_VALIDATION_ERROR",
                null,
                "Card fraud validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditCardFraudProcessing(CardFraudData fraudData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "CARD_FRAUD_PROCESSED",
                    fraudData != null ? fraudData.getAccountId() : null,
                    String.format("Card fraud processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "transactionId", fraudData != null ? fraudData.getTransactionId() : "unknown",
                            "accountId", fraudData != null ? fraudData.getAccountId() : "unknown",
                            "fraudType", fraudData != null ? fraudData.getFraudType() : "unknown",
                            "riskScore", fraudData != null ? fraudData.getRiskScore() : 0.0,
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit card fraud processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Card fraud event sent to DLT - EventId: {}", event.getEventId());
        
        try {
            CardFraudData fraudData = extractCardFraudData(event.getPayload());
            
            // Apply emergency protection for high-risk DLT events
            if (fraudData.getRiskScore() > 0.7) {
                cardSecurityService.emergencyBlockCard(fraudData.getCardNumber(), "DLT high risk event");
            }
            
        } catch (Exception e) {
            log.error("Failed to handle card fraud DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleCardFraudFailure(GenericKafkaEvent event, String topic, int partition,
                                      long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for card fraud processing - EventId: {}", event.getEventId(), e);
        
        try {
            CardFraudData fraudData = extractCardFraudData(event.getPayload());
            
            // Emergency card protection
            if (fraudData.getRiskScore() > 0.8) {
                cardSecurityService.emergencyBlockCard(fraudData.getCardNumber(), "Circuit breaker activation");
            }
            
        } catch (Exception ex) {
            log.error("Failed to handle card fraud circuit breaker fallback", ex);
        }
        
        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;

        // Handle BigDecimal with MoneyMath for precision
        if (value instanceof BigDecimal) {
            return (double) MoneyMath.toMLFeature((BigDecimal) value);
        }

        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class CardFraudData {
        private String transactionId;
        private String cardNumber;
        private String accountId;
        private String merchantId;
        private String merchantName;
        private BigDecimal transactionAmount;
        private String currency;
        private String transactionType;
        private Map<String, Object> location;
        private Map<String, Object> deviceInfo;
        private Map<String, Object> fraudIndicators;
        private Double riskScore;
        private String fraudType;
        private String detectionMethod;
        private Instant transactionTime;
        private Instant detectionTime;
    }

    @lombok.Data
    @lombok.Builder
    public static class TransactionAnalysisResult {
        private Double riskScore;
        private String analysisResult;
        private Map<String, Object> patterns;
    }

    @lombok.Data
    @lombok.Builder
    public static class MerchantRiskAssessment {
        private Double riskScore;
        private String riskLevel;
        private String merchantStatus;
    }

    @lombok.Data
    @lombok.Builder
    public static class FraudResponse {
        private String responseType;
        private boolean blocked;
        private String action;
        private String reason;
    }
}