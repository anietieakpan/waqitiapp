package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.service.AlertNotificationService;
import com.waqiti.notification.service.NotificationRoutingService;
import com.waqiti.notification.service.EscalationService;
import com.waqiti.notification.service.IncidentManagementService;
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
public class AlertsHighPriorityConsumer {

    private final AlertNotificationService alertNotificationService;
    private final NotificationRoutingService routingService;
    private final EscalationService escalationService;
    private final IncidentManagementService incidentService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter highPriorityAlertsProcessedCounter;
    private final Counter highPriorityAlertsFailedCounter;
    private final Counter escalatedHighPriorityAlertsCounter;
    private final Timer highPriorityAlertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AlertsHighPriorityConsumer(
            AlertNotificationService alertNotificationService,
            NotificationRoutingService routingService,
            EscalationService escalationService,
            IncidentManagementService incidentService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.alertNotificationService = alertNotificationService;
        this.routingService = routingService;
        this.escalationService = escalationService;
        this.incidentService = incidentService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.highPriorityAlertsProcessedCounter = Counter.builder("notification.alerts.high_priority.processed")
            .description("Count of high priority alerts processed")
            .register(meterRegistry);
        
        this.highPriorityAlertsFailedCounter = Counter.builder("notification.alerts.high_priority.failed")
            .description("Count of high priority alert processing failures")
            .register(meterRegistry);
        
        this.escalatedHighPriorityAlertsCounter = Counter.builder("notification.alerts.high_priority.escalated")
            .description("Count of high priority alerts that were escalated")
            .register(meterRegistry);
        
        this.highPriorityAlertProcessingTimer = Timer.builder("notification.alerts.high_priority.processing.duration")
            .description("Time taken to process high priority alerts")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "alerts-high-priority",
        groupId = "alerts-high-priority-notification-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "alerts-high-priority-notification-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleHighPriorityAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.warn("HIGH PRIORITY ALERT RECEIVED - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("High priority alert event {} already processed, skipping", eventId);
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
            Boolean requiresEscalation = (Boolean) eventData.getOrDefault("requiresEscalation", true);
            Boolean createIncident = (Boolean) eventData.getOrDefault("createIncident", true);
            
            String correlationId = String.format("high-priority-alert-notification-%s-%d", 
                alertId, System.currentTimeMillis());
            
            log.warn("HIGH PRIORITY ALERT PROCESSING - alertId: {}, type: {}, priority: {}, correlationId: {}", 
                alertId, alertType, priority, correlationId);
            
            highPriorityAlertsProcessedCounter.increment();
            
            processHighPriorityAlert(alertId, alertType, severity, title, description, source,
                affectedService, priority, requiresEscalation, createIncident, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(highPriorityAlertProcessingTimer);
            
            log.warn("HIGH PRIORITY ALERT PROCESSED - eventId: {}, alertId: {}", eventId, alertId);
            
        } catch (Exception e) {
            log.error("HIGH PRIORITY FAILURE: Failed to process high priority alert event {}: {}", eventId, e.getMessage(), e);
            highPriorityAlertsFailedCounter.increment();
            throw new RuntimeException("High priority alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "notification", fallbackMethod = "processHighPriorityAlertFallback")
    @Retry(name = "notification")
    private void processHighPriorityAlert(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String priority,
            Boolean requiresEscalation,
            Boolean createIncident,
            Map<String, Object> eventData,
            String correlationId) {
        
        Alert alert = Alert.builder()
            .id(alertId)
            .type(alertType)
            .severity(AlertSeverity.HIGH)
            .title(title)
            .description(description)
            .source(source)
            .affectedService(affectedService)
            .priority(priority)
            .requiresEscalation(requiresEscalation)
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        // Send high priority notifications (SMS, Email, Slack, PagerDuty)
        var notificationChannels = routingService.getHighPriorityChannels(alert);
        
        for (NotificationChannel channel : notificationChannels) {
            alertNotificationService.sendHighPriorityAlert(alert, channel, correlationId);
        }
        
        // Create incident if required
        String incidentId = null;
        if (createIncident) {
            incidentId = incidentService.createIncident(alert, correlationId);
        }
        
        // Start escalation process if required
        if (requiresEscalation) {
            escalationService.startHighPriorityEscalation(alert, correlationId);
            escalatedHighPriorityAlertsCounter.increment();
        }
        
        // Publish high priority alert status update
        kafkaTemplate.send("high-priority-alert-status-updates", Map.of(
            "alertId", alertId,
            "incidentId", incidentId != null ? incidentId : "",
            "status", "HIGH_PRIORITY_NOTIFIED",
            "alertType", alertType,
            "priority", priority,
            "notificationChannels", notificationChannels.toString(),
            "escalationStarted", requiresEscalation,
            "eventType", "HIGH_PRIORITY_ALERT_NOTIFIED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to operations dashboard
        kafkaTemplate.send("operations-dashboard-alerts", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "severity", "HIGH",
            "priority", priority,
            "title", title,
            "description", description,
            "source", source,
            "affectedService", affectedService,
            "incidentId", incidentId != null ? incidentId : "",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logNotificationEvent(
            "HIGH_PRIORITY_ALERT_PROCESSED",
            alertId,
            Map.of(
                "alertType", alertType,
                "severity", severity,
                "priority", priority,
                "title", title,
                "source", source,
                "affectedService", affectedService,
                "incidentId", incidentId != null ? incidentId : "",
                "notificationChannels", notificationChannels.toString(),
                "requiresEscalation", requiresEscalation,
                "createIncident", createIncident,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        // Start acknowledgment tracking with shorter timeout for high priority
        kafkaTemplate.send("high-priority-alert-acknowledgment-tracking", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "severity", "HIGH",
            "priority", priority,
            "incidentId", incidentId != null ? incidentId : "",
            "maxResponseTimeMinutes", 10, // High priority alerts must be acknowledged within 10 minutes
            "requiresResponse", true,
            "escalationLevel", 1,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        log.warn("HIGH PRIORITY ALERT FULLY PROCESSED - alertId: {}, incidentId: {}, priority: {}, correlationId: {}", 
            alertId, incidentId, priority, correlationId);
    }

    private void processHighPriorityAlertFallback(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String priority,
            Boolean requiresEscalation,
            Boolean createIncident,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("CIRCUIT BREAKER ACTIVATED FOR HIGH PRIORITY ALERT - alertId: {}, type: {}, correlationId: {}, error: {}", 
            alertId, alertType, correlationId, e.getMessage());
        
        // Try fallback emergency notification for high priority alerts
        try {
            alertNotificationService.sendHighPriorityFallbackNotification(
                alertId, alertType, title, description, priority, correlationId);
        } catch (Exception fallbackException) {
            log.error("HIGH PRIORITY FALLBACK FAILED - alertId: {}, error: {}", 
                alertId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("alertId", alertId);
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("severity", "HIGH");
        fallbackEvent.put("priority", priority);
        fallbackEvent.put("title", title);
        fallbackEvent.put("source", source);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("high-priority-alert-notification-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("HIGH PRIORITY SYSTEM FAILURE: High priority alert message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("high-priority-alert-notification-processing-failures", dltEvent);
            
            alertNotificationService.sendCriticalOperationalAlert(
                "High Priority Alert Processing Failed",
                String.format("HIGH PRIORITY FAILURE: Failed to process high priority alert after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process high priority alert DLT message: {}", e.getMessage(), e);
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