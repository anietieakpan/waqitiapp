package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.service.AlertNotificationService;
import com.waqiti.notification.service.NotificationRoutingService;
import com.waqiti.notification.service.AlertAggregationService;
import com.waqiti.notification.domain.Alert;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.AlertSeverity;
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
public class AlertsMediumPriorityConsumer {

    private final AlertNotificationService alertNotificationService;
    private final NotificationRoutingService routingService;
    private final AlertAggregationService aggregationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter mediumPriorityAlertsProcessedCounter;
    private final Counter mediumPriorityAlertsFailedCounter;
    private final Counter aggregatedAlertsCounter;
    private final Timer mediumPriorityAlertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AlertsMediumPriorityConsumer(
            AlertNotificationService alertNotificationService,
            NotificationRoutingService routingService,
            AlertAggregationService aggregationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.alertNotificationService = alertNotificationService;
        this.routingService = routingService;
        this.aggregationService = aggregationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.mediumPriorityAlertsProcessedCounter = Counter.builder("notification.alerts.medium_priority.processed")
            .description("Count of medium priority alerts processed")
            .register(meterRegistry);
        
        this.mediumPriorityAlertsFailedCounter = Counter.builder("notification.alerts.medium_priority.failed")
            .description("Count of medium priority alert processing failures")
            .register(meterRegistry);
        
        this.aggregatedAlertsCounter = Counter.builder("notification.alerts.medium_priority.aggregated")
            .description("Count of medium priority alerts that were aggregated")
            .register(meterRegistry);
        
        this.mediumPriorityAlertProcessingTimer = Timer.builder("notification.alerts.medium_priority.processing.duration")
            .description("Time taken to process medium priority alerts")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "alerts-medium-priority",
        groupId = "alerts-medium-priority-notification-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "alerts-medium-priority-notification-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleMediumPriorityAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Medium priority alert received - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Medium priority alert event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String alertId = (String) eventData.get("alertId");
            String alertType = (String) eventData.get("alertType");
            String severity = (String) eventData.get("severity");
            String title = (String) eventData.get("title");
            String description = (String) eventData.get("description");
            String source = (String) eventData.get("source");
            String affectedService = (String) eventData.get("affectedService");
            String priority = (String) eventData.get("priority");
            Boolean allowAggregation = (Boolean) eventData.getOrDefault("allowAggregation", true);
            Boolean requiresAcknowledgment = (Boolean) eventData.getOrDefault("requiresAcknowledgment", false);
            
            String correlationId = String.format("medium-priority-alert-notification-%s-%d", 
                alertId, System.currentTimeMillis());
            
            log.info("Medium priority alert processing - alertId: {}, type: {}, priority: {}, correlationId: {}", 
                alertId, alertType, priority, correlationId);
            
            mediumPriorityAlertsProcessedCounter.increment();
            
            processMediumPriorityAlert(alertId, alertType, severity, title, description, source,
                affectedService, priority, allowAggregation, requiresAcknowledgment, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(mediumPriorityAlertProcessingTimer);
            
            log.info("Medium priority alert processed - eventId: {}, alertId: {}", eventId, alertId);
            
        } catch (Exception e) {
            log.error("Failed to process medium priority alert event {}: {}", eventId, e.getMessage(), e);
            mediumPriorityAlertsFailedCounter.increment();
            throw new RuntimeException("Medium priority alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "notification", fallbackMethod = "processMediumPriorityAlertFallback")
    @Retry(name = "notification")
    private void processMediumPriorityAlert(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String priority,
            Boolean allowAggregation,
            Boolean requiresAcknowledgment,
            Map<String, Object> eventData,
            String correlationId) {
        
        Alert alert = Alert.builder()
            .id(alertId)
            .type(alertType)
            .severity(AlertSeverity.MEDIUM)
            .title(title)
            .description(description)
            .source(source)
            .affectedService(affectedService)
            .priority(priority)
            .allowAggregation(allowAggregation)
            .requiresAcknowledgment(requiresAcknowledgment)
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        // Check if this alert should be aggregated with similar alerts
        boolean wasAggregated = false;
        if (allowAggregation) {
            wasAggregated = aggregationService.tryAggregateAlert(alert, correlationId);
            if (wasAggregated) {
                aggregatedAlertsCounter.increment();
                log.info("Medium priority alert aggregated - alertId: {}, correlationId: {}", alertId, correlationId);
            }
        }
        
        // Send notifications if not aggregated
        if (!wasAggregated) {
            var notificationChannels = routingService.getMediumPriorityChannels(alert);
            
            for (NotificationChannel channel : notificationChannels) {
                alertNotificationService.sendMediumPriorityAlert(alert, channel, correlationId);
            }
        }
        
        // Publish medium priority alert status update
        kafkaTemplate.send("medium-priority-alert-status-updates", Map.of(
            "alertId", alertId,
            "status", wasAggregated ? "AGGREGATED" : "NOTIFIED",
            "alertType", alertType,
            "priority", priority,
            "wasAggregated", wasAggregated,
            "eventType", "MEDIUM_PRIORITY_ALERT_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to monitoring dashboard
        kafkaTemplate.send("monitoring-dashboard-alerts", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "severity", "MEDIUM",
            "priority", priority,
            "title", title,
            "description", description,
            "source", source,
            "affectedService", affectedService,
            "wasAggregated", wasAggregated,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logNotificationEvent(
            "MEDIUM_PRIORITY_ALERT_PROCESSED",
            alertId,
            Map.of(
                "alertType", alertType,
                "severity", severity,
                "priority", priority,
                "title", title,
                "source", source,
                "affectedService", affectedService,
                "allowAggregation", allowAggregation,
                "wasAggregated", wasAggregated,
                "requiresAcknowledgment", requiresAcknowledgment,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        // Start acknowledgment tracking if required
        if (requiresAcknowledgment && !wasAggregated) {
            kafkaTemplate.send("medium-priority-alert-acknowledgment-tracking", Map.of(
                "alertId", alertId,
                "alertType", alertType,
                "severity", "MEDIUM",
                "priority", priority,
                "maxResponseTimeMinutes", 30, // Medium priority alerts must be acknowledged within 30 minutes
                "requiresResponse", true,
                "escalationLevel", 0, // No automatic escalation for medium priority
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        log.info("Medium priority alert fully processed - alertId: {}, priority: {}, aggregated: {}, correlationId: {}", 
            alertId, priority, wasAggregated, correlationId);
    }

    private void processMediumPriorityAlertFallback(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String priority,
            Boolean allowAggregation,
            Boolean requiresAcknowledgment,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for medium priority alert - alertId: {}, type: {}, correlationId: {}, error: {}", 
            alertId, alertType, correlationId, e.getMessage());
        
        // Try basic email notification as fallback for medium priority alerts
        try {
            alertNotificationService.sendBasicEmailFallback(
                alertId, alertType, title, description, priority, correlationId);
        } catch (Exception fallbackException) {
            log.error("Medium priority fallback failed - alertId: {}, error: {}", 
                alertId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("alertId", alertId);
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("severity", "MEDIUM");
        fallbackEvent.put("priority", priority);
        fallbackEvent.put("title", title);
        fallbackEvent.put("source", source);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("medium-priority-alert-notification-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("Medium priority alert message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("medium-priority-alert-notification-processing-failures", dltEvent);
            
            alertNotificationService.sendOperationalAlert(
                "Medium Priority Alert Processing Failed",
                String.format("NOTICE: Failed to process medium priority alert after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process medium priority alert DLT message: {}", e.getMessage(), e);
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