package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.customer.service.CustomerFeedbackService;
import com.waqiti.customer.service.SentimentAnalysisService;
import com.waqiti.customer.service.CustomerRetentionService;
import com.waqiti.customer.service.EscalationService;
import com.waqiti.customer.service.ServiceRecoveryService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerFeedback;
import com.waqiti.customer.domain.FeedbackSentiment;
import com.waqiti.customer.domain.EscalationLevel;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CustomerFeedbackNegativeConsumer {

    private final CustomerFeedbackService feedbackService;
    private final SentimentAnalysisService sentimentService;
    private final CustomerRetentionService retentionService;
    private final EscalationService escalationService;
    private final ServiceRecoveryService serviceRecoveryService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter negativeFeedbackProcessedCounter;
    private final Counter negativeFeedbackEscalatedCounter;
    private final Counter serviceRecoveryTriggeredCounter;
    private final Counter customerRetentionActionsCounter;
    private final Timer negativeFeedbackProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public CustomerFeedbackNegativeConsumer(
            CustomerFeedbackService feedbackService,
            SentimentAnalysisService sentimentService,
            CustomerRetentionService retentionService,
            EscalationService escalationService,
            ServiceRecoveryService serviceRecoveryService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.feedbackService = feedbackService;
        this.sentimentService = sentimentService;
        this.retentionService = retentionService;
        this.escalationService = escalationService;
        this.serviceRecoveryService = serviceRecoveryService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.negativeFeedbackProcessedCounter = Counter.builder("customer.feedback.negative.processed")
            .description("Count of negative feedback events processed")
            .register(meterRegistry);
        
        this.negativeFeedbackEscalatedCounter = Counter.builder("customer.feedback.negative.escalated")
            .description("Count of negative feedback events escalated")
            .register(meterRegistry);
        
        this.serviceRecoveryTriggeredCounter = Counter.builder("customer.service_recovery.triggered")
            .description("Count of service recovery actions triggered")
            .register(meterRegistry);
        
        this.customerRetentionActionsCounter = Counter.builder("customer.retention.actions.triggered")
            .description("Count of customer retention actions triggered")
            .register(meterRegistry);
        
        this.negativeFeedbackProcessingTimer = Timer.builder("customer.feedback.negative.processing.duration")
            .description("Time taken to process negative feedback")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "customer-feedback-negative",
        groupId = "customer-feedback-negative-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "customer-feedback-negative-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleNegativeFeedbackEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.warn("NEGATIVE FEEDBACK RECEIVED - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Negative feedback event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String feedbackId = (String) eventData.get("feedbackId");
            String customerId = (String) eventData.get("customerId");
            String feedbackType = (String) eventData.get("feedbackType");
            String feedbackText = (String) eventData.get("feedbackText");
            Double sentimentScore = (Double) eventData.get("sentimentScore");
            Integer rating = (Integer) eventData.get("rating");
            String category = (String) eventData.get("category");
            String channel = (String) eventData.get("channel");
            String serviceArea = (String) eventData.get("serviceArea");
            Boolean requiresImmediateAction = (Boolean) eventData.getOrDefault("requiresImmediateAction", false);
            
            String correlationId = String.format("negative-feedback-%s-%d", 
                feedbackId, System.currentTimeMillis());
            
            log.warn("PROCESSING NEGATIVE FEEDBACK - feedbackId: {}, customerId: {}, rating: {}, sentiment: {}, correlationId: {}", 
                feedbackId, customerId, rating, sentimentScore, correlationId);
            
            negativeFeedbackProcessedCounter.increment();
            
            processNegativeFeedback(feedbackId, customerId, feedbackType, feedbackText,
                sentimentScore, rating, category, channel, serviceArea, requiresImmediateAction,
                eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(negativeFeedbackProcessingTimer);
            
            log.warn("NEGATIVE FEEDBACK PROCESSED - eventId: {}, feedbackId: {}", eventId, feedbackId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process negative feedback event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Negative feedback processing failed", e);
        }
    }

    @CircuitBreaker(name = "customer", fallbackMethod = "processNegativeFeedbackFallback")
    @Retry(name = "customer")
    private void processNegativeFeedback(
            String feedbackId,
            String customerId,
            String feedbackType,
            String feedbackText,
            Double sentimentScore,
            Integer rating,
            String category,
            String channel,
            String serviceArea,
            Boolean requiresImmediateAction,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Get customer details
        Customer customer = feedbackService.getCustomerById(customerId);
        if (customer == null) {
            throw new RuntimeException("Customer not found: " + customerId);
        }
        
        // Create feedback record
        CustomerFeedback feedback = CustomerFeedback.builder()
            .id(feedbackId)
            .customerId(customerId)
            .feedbackType(feedbackType)
            .feedbackText(feedbackText)
            .sentimentScore(sentimentScore)
            .rating(rating)
            .category(category)
            .channel(channel)
            .serviceArea(serviceArea)
            .sentiment(FeedbackSentiment.NEGATIVE)
            .requiresImmediateAction(requiresImmediateAction)
            .receivedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        feedbackService.saveFeedback(feedback);
        
        // Perform detailed sentiment analysis
        var detailedSentiment = sentimentService.analyzeDetailedSentiment(feedbackText, category);
        feedback.setSentimentDetails(detailedSentiment);
        
        // Determine severity and escalation level
        EscalationLevel escalationLevel = determineEscalationLevel(rating, sentimentScore, 
            customer.getCustomerTier(), requiresImmediateAction);
        
        // Update customer satisfaction metrics
        feedbackService.updateCustomerSatisfactionMetrics(customer, feedback, correlationId);
        
        // Check if customer is at risk of churn
        boolean isChurnRisk = retentionService.assessChurnRisk(customer, feedback, correlationId);
        
        // Trigger service recovery if needed
        if (shouldTriggerServiceRecovery(feedback, customer, escalationLevel)) {
            serviceRecoveryService.initiateServiceRecovery(customer, feedback, correlationId);
            serviceRecoveryTriggeredCounter.increment();
        }
        
        // Trigger retention actions if customer is at risk
        if (isChurnRisk) {
            retentionService.triggerRetentionActions(customer, feedback, correlationId);
            customerRetentionActionsCounter.increment();
        }
        
        // Escalate if required
        if (escalationLevel != EscalationLevel.NONE) {
            escalationService.escalateNegativeFeedback(customer, feedback, escalationLevel, correlationId);
            negativeFeedbackEscalatedCounter.increment();
        }
        
        // Schedule follow-up actions
        feedbackService.scheduleFollowUpActions(customer, feedback, correlationId);
        
        // Update customer experience scores
        feedbackService.updateCustomerExperienceScores(customer, feedback, correlationId);
        
        // Publish negative feedback alert
        kafkaTemplate.send("customer-satisfaction-alerts", Map.of(
            "alertType", "NEGATIVE_FEEDBACK_RECEIVED",
            "feedbackId", feedbackId,
            "customerId", customerId,
            "customerTier", customer.getCustomerTier(),
            "rating", rating,
            "sentimentScore", sentimentScore,
            "category", category,
            "serviceArea", serviceArea,
            "escalationLevel", escalationLevel.toString(),
            "isChurnRisk", isChurnRisk,
            "requiresImmediateAction", requiresImmediateAction,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to customer service team
        kafkaTemplate.send("customer-service-escalations", Map.of(
            "escalationType", "NEGATIVE_FEEDBACK",
            "feedbackId", feedbackId,
            "customerId", customerId,
            "customerName", customer.getFullName(),
            "customerTier", customer.getCustomerTier(),
            "rating", rating,
            "category", category,
            "serviceArea", serviceArea,
            "escalationLevel", escalationLevel.toString(),
            "priority", escalationLevel == EscalationLevel.URGENT ? "HIGH" : "MEDIUM",
            "assignedTo", escalationService.getAssignee(escalationLevel, serviceArea),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Update customer journey analytics
        kafkaTemplate.send("customer-journey-analytics", Map.of(
            "eventType", "NEGATIVE_FEEDBACK",
            "customerId", customerId,
            "touchpoint", channel,
            "sentiment", "NEGATIVE",
            "rating", rating,
            "category", category,
            "serviceArea", serviceArea,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to product team for improvement insights
        kafkaTemplate.send("product-improvement-insights", Map.of(
            "insightType", "NEGATIVE_FEEDBACK",
            "feedbackId", feedbackId,
            "category", category,
            "serviceArea", serviceArea,
            "feedbackText", feedbackText,
            "sentimentScore", sentimentScore,
            "rating", rating,
            "customerSegment", customer.getCustomerSegment(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logCustomerEvent(
            "NEGATIVE_FEEDBACK_PROCESSED",
            customerId,
            Map.of(
                "feedbackId", feedbackId,
                "feedbackType", feedbackType,
                "rating", rating,
                "sentimentScore", sentimentScore,
                "category", category,
                "serviceArea", serviceArea,
                "escalationLevel", escalationLevel.toString(),
                "isChurnRisk", isChurnRisk,
                "serviceRecoveryTriggered", shouldTriggerServiceRecovery(feedback, customer, escalationLevel),
                "requiresImmediateAction", requiresImmediateAction,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.warn("NEGATIVE FEEDBACK FULLY PROCESSED - feedbackId: {}, customerId: {}, escalation: {}, churnRisk: {}, correlationId: {}", 
            feedbackId, customerId, escalationLevel, isChurnRisk, correlationId);
    }

    private EscalationLevel determineEscalationLevel(Integer rating, Double sentimentScore, 
            String customerTier, Boolean requiresImmediateAction) {
        
        if (requiresImmediateAction || rating <= 1 || sentimentScore <= -0.8) {
            return EscalationLevel.URGENT;
        }
        
        if (rating <= 2 || sentimentScore <= -0.6) {
            return "PREMIUM".equals(customerTier) || "VIP".equals(customerTier) ? 
                EscalationLevel.HIGH : EscalationLevel.MEDIUM;
        }
        
        if (rating <= 3 || sentimentScore <= -0.4) {
            return EscalationLevel.MEDIUM;
        }
        
        return EscalationLevel.LOW;
    }

    private boolean shouldTriggerServiceRecovery(CustomerFeedback feedback, Customer customer, 
            EscalationLevel escalationLevel) {
        
        return feedback.getRating() <= 2 || 
               feedback.getSentimentScore() <= -0.6 ||
               escalationLevel == EscalationLevel.URGENT ||
               "PREMIUM".equals(customer.getCustomerTier()) ||
               "VIP".equals(customer.getCustomerTier());
    }

    private void processNegativeFeedbackFallback(
            String feedbackId,
            String customerId,
            String feedbackType,
            String feedbackText,
            Double sentimentScore,
            Integer rating,
            String category,
            String channel,
            String serviceArea,
            Boolean requiresImmediateAction,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for negative feedback processing - feedbackId: {}, customerId: {}, correlationId: {}, error: {}", 
            feedbackId, customerId, correlationId, e.getMessage());
        
        // Try emergency escalation notification
        try {
            escalationService.sendEmergencyNegativeFeedbackAlert(
                feedbackId, customerId, rating, category, correlationId);
        } catch (Exception fallbackException) {
            log.error("Emergency escalation failed - feedbackId: {}, error: {}", 
                feedbackId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("feedbackId", feedbackId);
        fallbackEvent.put("customerId", customerId);
        fallbackEvent.put("feedbackType", feedbackType);
        fallbackEvent.put("rating", rating);
        fallbackEvent.put("category", category);
        fallbackEvent.put("serviceArea", serviceArea);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("negative-feedback-processing-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Negative feedback message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("negative-feedback-processing-failures", dltEvent);
            
            escalationService.sendCriticalOperationalAlert(
                "Negative Feedback Processing Failed",
                String.format("CRITICAL: Failed to process negative customer feedback after max retries. Customer satisfaction may be at risk. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process negative feedback DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}