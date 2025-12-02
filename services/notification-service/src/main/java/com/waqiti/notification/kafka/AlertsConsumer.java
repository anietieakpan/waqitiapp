package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.service.AlertNotificationService;
import com.waqiti.notification.service.NotificationRoutingService;
import com.waqiti.notification.service.EscalationService;
import com.waqiti.notification.service.AlertCorrelationService;
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
public class AlertsConsumer {

    private final AlertNotificationService alertNotificationService;
    private final NotificationRoutingService routingService;
    private final EscalationService escalationService;
    private final AlertCorrelationService correlationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter alertsProcessedCounter;
    private final Counter alertsFailedCounter;
    private final Counter escalatedAlertsCounter;
    private final Timer alertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AlertsConsumer(
            AlertNotificationService alertNotificationService,
            NotificationRoutingService routingService,
            EscalationService escalationService,
            AlertCorrelationService correlationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.alertNotificationService = alertNotificationService;
        this.routingService = routingService;
        this.escalationService = escalationService;
        this.correlationService = correlationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.alertsProcessedCounter = Counter.builder("notification.alerts.processed")
            .description("Count of general alerts processed")
            .register(meterRegistry);
        
        this.alertsFailedCounter = Counter.builder("notification.alerts.failed")
            .description("Count of general alert processing failures")
            .register(meterRegistry);
        
        this.escalatedAlertsCounter = Counter.builder("notification.alerts.escalated")
            .description("Count of alerts that were escalated")
            .register(meterRegistry);
        
        this.alertProcessingTimer = Timer.builder("notification.alerts.processing.duration")
            .description("Time taken to process general alerts")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "alerts",
        groupId = "alerts-notification-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "alerts-notification-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received general alert event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
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
            String category = (String) eventData.get("category");
            Boolean requiresAcknowledgment = (Boolean) eventData.getOrDefault("requiresAcknowledgment", false);
            
            String correlationId = String.format("alert-notification-%s-%d", 
                alertId, System.currentTimeMillis());
            
            log.info("Processing general alert - alertId: {}, type: {}, severity: {}, correlationId: {}", 
                alertId, alertType, severity, correlationId);
            
            alertsProcessedCounter.increment();
            
            processAlert(alertId, alertType, severity, title, description, source,
                affectedService, category, requiresAcknowledgment, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(alertProcessingTimer);
            
            log.info("Successfully processed general alert event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process general alert event {}: {}", eventId, e.getMessage(), e);
            alertsFailedCounter.increment();
            throw new RuntimeException("General alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "notification", fallbackMethod = "processAlertFallback")
    @Retry(name = "notification")
    private void processAlert(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String category,
            Boolean requiresAcknowledgment,
            Map<String, Object> eventData,
            String correlationId) {
        
        Alert alert = Alert.builder()
            .id(alertId)
            .type(alertType)
            .severity(AlertSeverity.valueOf(severity))
            .title(title)
            .description(description)
            .source(source)
            .affectedService(affectedService)
            .category(category)
            .requiresAcknowledgment(requiresAcknowledgment)
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        // Determine notification channels based on severity and type
        var notificationChannels = routingService.determineChannels(alert);
        
        // Send notifications through appropriate channels
        for (NotificationChannel channel : notificationChannels) {
            alertNotificationService.sendAlert(alert, channel, correlationId);
        }
        
        // Check for correlation with other alerts
        correlationService.checkAlertCorrelation(alert, correlationId);
        
        // Handle escalation if required
        if (shouldEscalateAlert(alert)) {
            escalationService.escalateAlert(alert, correlationId);
            escalatedAlertsCounter.increment();
        }
        
        // Publish alert status update
        kafkaTemplate.send("alert-status-updates", Map.of(
            "alertId", alertId,
            "status", "NOTIFIED",
            "alertType", alertType,
            "severity", severity,
            "notificationChannels", notificationChannels.toString(),
            "eventType", "ALERT_NOTIFIED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logNotificationEvent(
            "GENERAL_ALERT_PROCESSED",
            alertId,
            Map.of(
                "alertType", alertType,
                "severity", severity,
                "title", title,
                "source", source,
                "affectedService", affectedService,
                "category", category,
                "notificationChannels", notificationChannels.toString(),
                "requiresAcknowledgment", requiresAcknowledgment,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        if (requiresAcknowledgment) {
            kafkaTemplate.send("alert-acknowledgment-tracking", Map.of(
                "alertId", alertId,
                "alertType", alertType,
                "severity", severity,
                "requiresResponse", true,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        log.info("General alert processed and notifications sent - alertId: {}, type: {}, severity: {}, correlationId: {}", 
            alertId, alertType, severity, correlationId);
    }

    private boolean shouldEscalateAlert(Alert alert) {
        return alert.getSeverity() == AlertSeverity.HIGH || 
               alert.getSeverity() == AlertSeverity.CRITICAL ||
               alert.getRequiresAcknowledgment();
    }

    private void processAlertFallback(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String category,
            Boolean requiresAcknowledgment,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for general alert - alertId: {}, type: {}, correlationId: {}, error: {}", 
            alertId, alertType, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("alertId", alertId);
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("severity", severity);
        fallbackEvent.put("title", title);
        fallbackEvent.put("source", source);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("alert-notification-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: General alert message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("alert-notification-processing-failures", dltEvent);
            
            alertNotificationService.sendCriticalOperationalAlert(
                "General Alert Processing Failed",
                String.format("CRITICAL: Failed to process general alert after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
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