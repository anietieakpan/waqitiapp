package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.customer.service.CustomerSatisfactionService;
import com.waqiti.customer.service.CustomerRetentionService;
import com.waqiti.customer.service.CustomerExperienceService;
import com.waqiti.customer.service.EscalationService;
import com.waqiti.customer.service.SatisfactionAnalyticsService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.SatisfactionAlert;
import com.waqiti.customer.domain.AlertSeverity;
import com.waqiti.customer.domain.SatisfactionTrend;
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
public class CustomerSatisfactionAlertsConsumer {

    private final CustomerSatisfactionService satisfactionService;
    private final CustomerRetentionService retentionService;
    private final CustomerExperienceService experienceService;
    private final EscalationService escalationService;
    private final SatisfactionAnalyticsService analyticsService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter satisfactionAlertsProcessedCounter;
    private final Counter criticalSatisfactionAlertsCounter;
    private final Counter retentionActionsTriggeredCounter;
    private final Counter experienceImprovementActionsCounter;
    private final Timer satisfactionAlertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public CustomerSatisfactionAlertsConsumer(
            CustomerSatisfactionService satisfactionService,
            CustomerRetentionService retentionService,
            CustomerExperienceService experienceService,
            EscalationService escalationService,
            SatisfactionAnalyticsService analyticsService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.satisfactionService = satisfactionService;
        this.retentionService = retentionService;
        this.experienceService = experienceService;
        this.escalationService = escalationService;
        this.analyticsService = analyticsService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.satisfactionAlertsProcessedCounter = Counter.builder("customer.satisfaction.alerts.processed")
            .description("Count of customer satisfaction alerts processed")
            .register(meterRegistry);
        
        this.criticalSatisfactionAlertsCounter = Counter.builder("customer.satisfaction.alerts.critical")
            .description("Count of critical customer satisfaction alerts")
            .register(meterRegistry);
        
        this.retentionActionsTriggeredCounter = Counter.builder("customer.satisfaction.retention_actions.triggered")
            .description("Count of retention actions triggered by satisfaction alerts")
            .register(meterRegistry);
        
        this.experienceImprovementActionsCounter = Counter.builder("customer.satisfaction.experience_actions.triggered")
            .description("Count of experience improvement actions triggered")
            .register(meterRegistry);
        
        this.satisfactionAlertProcessingTimer = Timer.builder("customer.satisfaction.alerts.processing.duration")
            .description("Time taken to process customer satisfaction alerts")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "customer-satisfaction-alerts",
        groupId = "customer-satisfaction-alerts-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "customer-satisfaction-alerts-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCustomerSatisfactionAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.warn("CUSTOMER SATISFACTION ALERT RECEIVED - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Customer satisfaction alert event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String alertId = (String) eventData.get("alertId");
            String customerId = (String) eventData.get("customerId");
            String alertType = (String) eventData.get("alertType");
            String severity = (String) eventData.get("severity");
            Double currentScore = (Double) eventData.get("currentScore");
            Double previousScore = (Double) eventData.get("previousScore");
            String trendDirection = (String) eventData.get("trendDirection");
            String triggerEvent = (String) eventData.get("triggerEvent");
            String affectedArea = (String) eventData.get("affectedArea");
            Boolean requiresImmediateAction = (Boolean) eventData.getOrDefault("requiresImmediateAction", false);
            
            String correlationId = String.format("satisfaction-alert-%s-%d", 
                alertId, System.currentTimeMillis());
            
            log.warn("PROCESSING SATISFACTION ALERT - alertId: {}, customerId: {}, type: {}, severity: {}, score: {}, correlationId: {}", 
                alertId, customerId, alertType, severity, currentScore, correlationId);
            
            satisfactionAlertsProcessedCounter.increment();
            if ("CRITICAL".equals(severity)) {
                criticalSatisfactionAlertsCounter.increment();
            }
            
            processCustomerSatisfactionAlert(alertId, customerId, alertType, severity,
                currentScore, previousScore, trendDirection, triggerEvent, affectedArea,
                requiresImmediateAction, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(satisfactionAlertProcessingTimer);
            
            log.warn("SATISFACTION ALERT PROCESSED - eventId: {}, alertId: {}", eventId, alertId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process customer satisfaction alert event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Customer satisfaction alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "customer", fallbackMethod = "processCustomerSatisfactionAlertFallback")
    @Retry(name = "customer")
    private void processCustomerSatisfactionAlert(
            String alertId,
            String customerId,
            String alertType,
            String severity,
            Double currentScore,
            Double previousScore,
            String trendDirection,
            String triggerEvent,
            String affectedArea,
            Boolean requiresImmediateAction,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Get customer details
        Customer customer = satisfactionService.getCustomerById(customerId);
        if (customer == null) {
            throw new RuntimeException("Customer not found: " + customerId);
        }
        
        // Create satisfaction alert record
        SatisfactionAlert alert = SatisfactionAlert.builder()
            .id(alertId)
            .customerId(customerId)
            .alertType(alertType)
            .severity(AlertSeverity.valueOf(severity))
            .currentScore(currentScore)
            .previousScore(previousScore)
            .trendDirection(SatisfactionTrend.valueOf(trendDirection))
            .triggerEvent(triggerEvent)
            .affectedArea(affectedArea)
            .requiresImmediateAction(requiresImmediateAction)
            .alertTime(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        satisfactionService.saveSatisfactionAlert(alert);
        
        // Update customer satisfaction metrics
        satisfactionService.updateCustomerSatisfactionMetrics(customer, alert, correlationId);
        
        // Analyze satisfaction trends
        var satisfactionAnalysis = analyticsService.analyzeSatisfactionTrends(customer, alert, correlationId);
        
        // Determine if customer is at churn risk
        boolean isChurnRisk = retentionService.assessChurnRiskFromSatisfaction(customer, alert, correlationId);
        
        // Trigger retention actions if needed
        if (isChurnRisk || currentScore < 6.0) {
            retentionService.triggerSatisfactionBasedRetention(customer, alert, correlationId);
            retentionActionsTriggeredCounter.increment();
        }
        
        // Trigger experience improvement actions
        if (shouldTriggerExperienceImprovement(alert, customer)) {
            experienceService.triggerExperienceImprovementActions(customer, alert, correlationId);
            experienceImprovementActionsCounter.increment();
        }
        
        // Escalate critical satisfaction issues
        if (AlertSeverity.CRITICAL.equals(alert.getSeverity()) || requiresImmediateAction) {
            escalationService.escalateSatisfactionAlert(customer, alert, correlationId);
        }
        
        // Update customer journey analytics
        satisfactionService.updateCustomerJourneyAnalytics(customer, alert, correlationId);
        
        // Trigger personalized recovery actions
        if (currentScore < 7.0 || SatisfactionTrend.DECLINING.equals(alert.getTrendDirection())) {
            satisfactionService.triggerPersonalizedRecoveryActions(customer, alert, correlationId);
        }
        
        // Publish satisfaction status update
        kafkaTemplate.send("customer-satisfaction-status-updates", Map.of(
            "alertId", alertId,
            "customerId", customerId,
            "customerTier", customer.getCustomerTier(),
            "alertType", alertType,
            "severity", severity,
            "currentScore", currentScore,
            "previousScore", previousScore,
            "trendDirection", trendDirection,
            "affectedArea", affectedArea,
            "isChurnRisk", isChurnRisk,
            "actionsTriggered", true,
            "eventType", "SATISFACTION_ALERT_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to customer success team
        kafkaTemplate.send("customer-success-team-alerts", Map.of(
            "alertType", "SATISFACTION_DECLINE",
            "alertId", alertId,
            "customerId", customerId,
            "customerName", customer.getFullName(),
            "customerTier", customer.getCustomerTier(),
            "currentScore", currentScore,
            "previousScore", previousScore,
            "trendDirection", trendDirection,
            "affectedArea", affectedArea,
            "severity", severity,
            "isChurnRisk", isChurnRisk,
            "requiresImmediateAction", requiresImmediateAction,
            "assignedTo", escalationService.getCustomerSuccessAssignee(customer),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to product team for insight
        kafkaTemplate.send("product-satisfaction-insights", Map.of(
            "insightType", "SATISFACTION_ALERT",
            "alertId", alertId,
            "affectedArea", affectedArea,
            "triggerEvent", triggerEvent,
            "currentScore", currentScore,
            "trendDirection", trendDirection,
            "customerSegment", customer.getCustomerSegment(),
            "customerTier", customer.getCustomerTier(),
            "analysis", satisfactionAnalysis.toString(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Update NPS and CSAT tracking
        kafkaTemplate.send("nps-csat-tracking-updates", Map.of(
            "customerId", customerId,
            "metricType", alertType.contains("NPS") ? "NPS" : "CSAT",
            "currentScore", currentScore,
            "previousScore", previousScore,
            "trendDirection", trendDirection,
            "affectedArea", affectedArea,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to analytics for trend analysis
        kafkaTemplate.send("satisfaction-analytics", Map.of(
            "eventType", "SATISFACTION_ALERT",
            "customerId", customerId,
            "customerSegment", customer.getCustomerSegment(),
            "customerTier", customer.getCustomerTier(),
            "alertType", alertType,
            "currentScore", currentScore,
            "trendDirection", trendDirection,
            "affectedArea", affectedArea,
            "triggerEvent", triggerEvent,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logCustomerEvent(
            "CUSTOMER_SATISFACTION_ALERT_PROCESSED",
            customerId,
            Map.of(
                "alertId", alertId,
                "alertType", alertType,
                "severity", severity,
                "currentScore", currentScore,
                "previousScore", previousScore,
                "trendDirection", trendDirection,
                "triggerEvent", triggerEvent,
                "affectedArea", affectedArea,
                "isChurnRisk", isChurnRisk,
                "requiresImmediateAction", requiresImmediateAction,
                "retentionActionsTriggered", isChurnRisk || currentScore < 6.0,
                "experienceActionsTriggered", shouldTriggerExperienceImprovement(alert, customer),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.warn("SATISFACTION ALERT FULLY PROCESSED - alertId: {}, customerId: {}, score: {}, churnRisk: {}, correlationId: {}", 
            alertId, customerId, currentScore, isChurnRisk, correlationId);
    }

    private boolean shouldTriggerExperienceImprovement(SatisfactionAlert alert, Customer customer) {
        return alert.getCurrentScore() < 7.0 || 
               SatisfactionTrend.DECLINING.equals(alert.getTrendDirection()) ||
               AlertSeverity.HIGH.equals(alert.getSeverity()) ||
               AlertSeverity.CRITICAL.equals(alert.getSeverity()) ||
               "PREMIUM".equals(customer.getCustomerTier()) ||
               "VIP".equals(customer.getCustomerTier());
    }

    private void processCustomerSatisfactionAlertFallback(
            String alertId,
            String customerId,
            String alertType,
            String severity,
            Double currentScore,
            Double previousScore,
            String trendDirection,
            String triggerEvent,
            String affectedArea,
            Boolean requiresImmediateAction,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for satisfaction alert processing - alertId: {}, customerId: {}, correlationId: {}, error: {}", 
            alertId, customerId, correlationId, e.getMessage());
        
        // Try emergency satisfaction alert
        try {
            escalationService.sendEmergencySatisfactionAlert(
                alertId, customerId, alertType, currentScore, severity, correlationId);
        } catch (Exception fallbackException) {
            log.error("Emergency satisfaction alert failed - alertId: {}, error: {}", 
                alertId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("alertId", alertId);
        fallbackEvent.put("customerId", customerId);
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("severity", severity);
        fallbackEvent.put("currentScore", currentScore);
        fallbackEvent.put("affectedArea", affectedArea);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("satisfaction-alert-processing-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Customer satisfaction alert message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("satisfaction-alert-processing-failures", dltEvent);
            
            escalationService.sendCriticalOperationalAlert(
                "Customer Satisfaction Alert Processing Failed",
                String.format("CRITICAL: Failed to process customer satisfaction alert after max retries. Customer retention may be at risk. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process satisfaction alert DLT message: {}", e.getMessage(), e);
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