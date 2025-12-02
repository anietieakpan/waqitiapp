package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.notification.service.AlertNotificationService;
import com.waqiti.notification.service.CriticalAlertService;
import com.waqiti.notification.service.EmergencyResponseService;
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
public class AlertsCriticalConsumer {

    private final AlertNotificationService alertNotificationService;
    private final CriticalAlertService criticalAlertService;
    private final EmergencyResponseService emergencyResponseService;
    private final IncidentManagementService incidentService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler universalDLQHandler;
    
    private final Counter criticalAlertsProcessedCounter;
    private final Counter criticalAlertsFailedCounter;
    private final Counter emergencyResponsesTriggeredCounter;
    private final Timer criticalAlertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AlertsCriticalConsumer(
            AlertNotificationService alertNotificationService,
            CriticalAlertService criticalAlertService,
            EmergencyResponseService emergencyResponseService,
            IncidentManagementService incidentService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            UniversalDLQHandler universalDLQHandler,
            MeterRegistry meterRegistry) {
        
        this.alertNotificationService = alertNotificationService;
        this.criticalAlertService = criticalAlertService;
        this.emergencyResponseService = emergencyResponseService;
        this.incidentService = incidentService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.universalDLQHandler = universalDLQHandler;
        
        this.criticalAlertsProcessedCounter = Counter.builder("notification.alerts.critical.processed")
            .description("Count of critical alerts processed")
            .register(meterRegistry);
        
        this.criticalAlertsFailedCounter = Counter.builder("notification.alerts.critical.failed")
            .description("Count of critical alert processing failures")
            .register(meterRegistry);
        
        this.emergencyResponsesTriggeredCounter = Counter.builder("notification.emergency.responses.triggered")
            .description("Count of emergency responses triggered")
            .register(meterRegistry);
        
        this.criticalAlertProcessingTimer = Timer.builder("notification.alerts.critical.processing.duration")
            .description("Time taken to process critical alerts")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "alerts-critical",
        groupId = "alerts-critical-notification-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "alerts-critical-notification-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCriticalAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.error("CRITICAL ALERT RECEIVED - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Critical alert event {} already processed, skipping", eventId);
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
            String impactLevel = (String) eventData.get("impactLevel");
            Boolean requiresImmediateResponse = (Boolean) eventData.getOrDefault("requiresImmediateResponse", true);
            Boolean triggerEmergencyProtocol = (Boolean) eventData.getOrDefault("triggerEmergencyProtocol", false);
            
            String correlationId = String.format("critical-alert-notification-%s-%d", 
                alertId, System.currentTimeMillis());
            
            log.error("CRITICAL ALERT PROCESSING - alertId: {}, type: {}, impact: {}, correlationId: {}", 
                alertId, alertType, impactLevel, correlationId);
            
            criticalAlertsProcessedCounter.increment();
            
            processCriticalAlert(alertId, alertType, severity, title, description, source,
                affectedService, impactLevel, requiresImmediateResponse, triggerEmergencyProtocol,
                eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(criticalAlertProcessingTimer);
            
            log.error("CRITICAL ALERT PROCESSED - eventId: {}, alertId: {}", eventId, alertId);
            
        } catch (Exception e) {
            log.error("CRITICAL FAILURE: Failed to process critical alert event {}: {}", eventId, e.getMessage(), e);
            criticalAlertsFailedCounter.increment();

            // Use UniversalDLQHandler for enhanced DLQ routing
            universalDLQHandler.sendToDLQ(
                message,
                topic,
                partition,
                offset,
                e,
                Map.of(
                    "consumerGroup", "alerts-critical-notification-processor-group",
                    "errorType", e.getClass().getSimpleName(),
                    "eventId", eventId
                )
            );

            throw new RuntimeException("Critical alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "notification", fallbackMethod = "processCriticalAlertFallback")
    @Retry(name = "notification")
    private void processCriticalAlert(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String impactLevel,
            Boolean requiresImmediateResponse,
            Boolean triggerEmergencyProtocol,
            Map<String, Object> eventData,
            String correlationId) {
        
        Alert alert = Alert.builder()
            .id(alertId)
            .type(alertType)
            .severity(AlertSeverity.CRITICAL)
            .title(title)
            .description(description)
            .source(source)
            .affectedService(affectedService)
            .impactLevel(impactLevel)
            .requiresImmediateResponse(requiresImmediateResponse)
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        // Immediately notify all critical channels (SMS, Phone, PagerDuty, Email)
        criticalAlertService.sendImmediateCriticalNotifications(alert, correlationId);
        
        // Create incident if not exists
        String incidentId = incidentService.createOrUpdateIncident(alert, correlationId);
        
        // Trigger emergency protocol if required
        if (triggerEmergencyProtocol) {
            emergencyResponseService.triggerEmergencyProtocol(alert, incidentId, correlationId);
            emergencyResponsesTriggeredCounter.increment();
        }
        
        // Start escalation timer for immediate response
        if (requiresImmediateResponse) {
            criticalAlertService.startEscalationTimer(alert, correlationId);
        }
        
        // Publish critical alert status update
        kafkaTemplate.send("critical-alert-status-updates", Map.of(
            "alertId", alertId,
            "incidentId", incidentId,
            "status", "CRITICAL_NOTIFIED",
            "alertType", alertType,
            "impactLevel", impactLevel,
            "emergencyProtocolTriggered", triggerEmergencyProtocol,
            "eventType", "CRITICAL_ALERT_NOTIFIED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to operations dashboard
        kafkaTemplate.send("operations-dashboard-alerts", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "severity", "CRITICAL",
            "title", title,
            "description", description,
            "source", source,
            "affectedService", affectedService,
            "impactLevel", impactLevel,
            "incidentId", incidentId,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logCriticalEvent(
            "CRITICAL_ALERT_PROCESSED",
            alertId,
            Map.of(
                "alertType", alertType,
                "severity", severity,
                "title", title,
                "source", source,
                "affectedService", affectedService,
                "impactLevel", impactLevel,
                "incidentId", incidentId,
                "requiresImmediateResponse", requiresImmediateResponse,
                "triggerEmergencyProtocol", triggerEmergencyProtocol,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        // Start monitoring for acknowledgment
        kafkaTemplate.send("critical-alert-acknowledgment-tracking", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "severity", "CRITICAL",
            "incidentId", incidentId,
            "maxResponseTimeMinutes", 5, // Critical alerts must be acknowledged within 5 minutes
            "requiresResponse", true,
            "escalationLevel", 1,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        log.error("CRITICAL ALERT FULLY PROCESSED - alertId: {}, incidentId: {}, impact: {}, correlationId: {}", 
            alertId, incidentId, impactLevel, correlationId);
    }

    private void processCriticalAlertFallback(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedService,
            String impactLevel,
            Boolean requiresImmediateResponse,
            Boolean triggerEmergencyProtocol,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("CIRCUIT BREAKER ACTIVATED FOR CRITICAL ALERT - alertId: {}, type: {}, correlationId: {}, error: {}", 
            alertId, alertType, correlationId, e.getMessage());
        
        // Even in fallback, try alternative emergency notification
        try {
            emergencyResponseService.sendEmergencyFallbackNotification(
                alertId, alertType, title, description, correlationId);
        } catch (Exception fallbackException) {
            log.error("EMERGENCY FALLBACK FAILED - alertId: {}, error: {}", 
                alertId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("alertId", alertId);
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("severity", "CRITICAL");
        fallbackEvent.put("title", title);
        fallbackEvent.put("source", source);
        fallbackEvent.put("impactLevel", impactLevel);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("critical-alert-notification-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL SYSTEM FAILURE: Critical alert message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("critical-alert-notification-processing-failures", dltEvent);
            
            // Send emergency notification about the failure
            emergencyResponseService.sendCriticalSystemFailureAlert(
                "Critical Alert Processing System Failure",
                String.format("EMERGENCY: Critical alert processing failed completely. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("SYSTEM CATASTROPHIC FAILURE: Failed to process critical alert DLT message: {}", e.getMessage(), e);
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