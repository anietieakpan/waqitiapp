package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.service.AlertNotificationService;
import com.waqiti.notification.service.EmergencyResponseService;
import com.waqiti.notification.service.DisasterRecoveryService;
import com.waqiti.notification.service.ExecutiveNotificationService;
import com.waqiti.notification.domain.Alert;
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
public class AlertsEmergencyConsumer {

    private final AlertNotificationService alertNotificationService;
    private final EmergencyResponseService emergencyResponseService;
    private final DisasterRecoveryService disasterRecoveryService;
    private final ExecutiveNotificationService executiveService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter emergencyAlertsProcessedCounter;
    private final Counter emergencyAlertsFailedCounter;
    private final Counter disasterRecoveryTriggeredCounter;
    private final Counter executiveNotificationsCounter;
    private final Timer emergencyAlertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AlertsEmergencyConsumer(
            AlertNotificationService alertNotificationService,
            EmergencyResponseService emergencyResponseService,
            DisasterRecoveryService disasterRecoveryService,
            ExecutiveNotificationService executiveService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.alertNotificationService = alertNotificationService;
        this.emergencyResponseService = emergencyResponseService;
        this.disasterRecoveryService = disasterRecoveryService;
        this.executiveService = executiveService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.emergencyAlertsProcessedCounter = Counter.builder("notification.alerts.emergency.processed")
            .description("Count of emergency alerts processed")
            .register(meterRegistry);
        
        this.emergencyAlertsFailedCounter = Counter.builder("notification.alerts.emergency.failed")
            .description("Count of emergency alert processing failures")
            .register(meterRegistry);
        
        this.disasterRecoveryTriggeredCounter = Counter.builder("notification.disaster.recovery.triggered")
            .description("Count of disaster recovery procedures triggered")
            .register(meterRegistry);
        
        this.executiveNotificationsCounter = Counter.builder("notification.executive.notifications")
            .description("Count of executive notifications sent")
            .register(meterRegistry);
        
        this.emergencyAlertProcessingTimer = Timer.builder("notification.alerts.emergency.processing.duration")
            .description("Time taken to process emergency alerts")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "alerts-emergency",
        groupId = "alerts-emergency-notification-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "alerts-emergency-notification-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleEmergencyAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.error("🚨 EMERGENCY ALERT RECEIVED 🚨 - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.error("Emergency alert event {} already processed, skipping", eventId);
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
            String affectedSystems = (String) eventData.get("affectedSystems");
            String businessImpact = (String) eventData.get("businessImpact");
            Boolean triggerDisasterRecovery = (Boolean) eventData.getOrDefault("triggerDisasterRecovery", false);
            Boolean notifyExecutives = (Boolean) eventData.getOrDefault("notifyExecutives", true);
            Boolean activateWarRoom = (Boolean) eventData.getOrDefault("activateWarRoom", true);
            
            String correlationId = String.format("emergency-alert-notification-%s-%d", 
                alertId, System.currentTimeMillis());
            
            log.error("🚨 EMERGENCY ALERT PROCESSING 🚨 - alertId: {}, type: {}, impact: {}, correlationId: {}", 
                alertId, alertType, businessImpact, correlationId);
            
            emergencyAlertsProcessedCounter.increment();
            
            processEmergencyAlert(alertId, alertType, severity, title, description, source,
                affectedSystems, businessImpact, triggerDisasterRecovery, notifyExecutives,
                activateWarRoom, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(emergencyAlertProcessingTimer);
            
            log.error("🚨 EMERGENCY ALERT PROCESSED 🚨 - eventId: {}, alertId: {}", eventId, alertId);
            
        } catch (Exception e) {
            log.error("🚨 CATASTROPHIC FAILURE: Failed to process emergency alert event {}: {}", eventId, e.getMessage(), e);
            emergencyAlertsFailedCounter.increment();
            throw new RuntimeException("Emergency alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "notification", fallbackMethod = "processEmergencyAlertFallback")
    @Retry(name = "notification")
    private void processEmergencyAlert(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedSystems,
            String businessImpact,
            Boolean triggerDisasterRecovery,
            Boolean notifyExecutives,
            Boolean activateWarRoom,
            Map<String, Object> eventData,
            String correlationId) {
        
        Alert alert = Alert.builder()
            .id(alertId)
            .type(alertType)
            .severity(AlertSeverity.EMERGENCY)
            .title(title)
            .description(description)
            .source(source)
            .affectedSystems(affectedSystems)
            .businessImpact(businessImpact)
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        // IMMEDIATE emergency notifications to all channels
        emergencyResponseService.sendImmediateEmergencyNotifications(alert, correlationId);
        
        // Notify executives immediately
        if (notifyExecutives) {
            executiveService.notifyExecutiveTeam(alert, correlationId);
            executiveNotificationsCounter.increment();
        }
        
        // Activate war room if required
        if (activateWarRoom) {
            emergencyResponseService.activateWarRoom(alert, correlationId);
        }
        
        // Trigger disaster recovery procedures if required
        if (triggerDisasterRecovery) {
            disasterRecoveryService.initiateDisasterRecoveryProcedures(alert, correlationId);
            disasterRecoveryTriggeredCounter.increment();
        }
        
        // Create major incident
        String incidentId = emergencyResponseService.createMajorIncident(alert, correlationId);
        
        // Start automatic escalation to regulatory bodies
        emergencyResponseService.initiateRegulatoryNotification(alert, incidentId, correlationId);
        
        // Publish emergency alert status update
        kafkaTemplate.send("emergency-alert-status-updates", Map.of(
            "alertId", alertId,
            "incidentId", incidentId,
            "status", "EMERGENCY_RESPONSE_ACTIVATED",
            "alertType", alertType,
            "businessImpact", businessImpact,
            "affectedSystems", affectedSystems,
            "warRoomActivated", activateWarRoom,
            "disasterRecoveryTriggered", triggerDisasterRecovery,
            "executivesNotified", notifyExecutives,
            "eventType", "EMERGENCY_ALERT_NOTIFIED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to all monitoring dashboards
        kafkaTemplate.send("all-dashboards-emergency-alerts", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "severity", "EMERGENCY",
            "title", title,
            "description", description,
            "source", source,
            "affectedSystems", affectedSystems,
            "businessImpact", businessImpact,
            "incidentId", incidentId,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to media/PR team for potential external communication
        kafkaTemplate.send("pr-team-emergency-alerts", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "title", title,
            "businessImpact", businessImpact,
            "requiresExternalCommunication", true,
            "incidentId", incidentId,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logEmergencyEvent(
            "EMERGENCY_ALERT_PROCESSED",
            alertId,
            Map.of(
                "alertType", alertType,
                "severity", "EMERGENCY",
                "title", title,
                "source", source,
                "affectedSystems", affectedSystems,
                "businessImpact", businessImpact,
                "incidentId", incidentId,
                "triggerDisasterRecovery", triggerDisasterRecovery,
                "notifyExecutives", notifyExecutives,
                "activateWarRoom", activateWarRoom,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        // Start immediate acknowledgment tracking - executives must respond within 2 minutes
        kafkaTemplate.send("emergency-alert-acknowledgment-tracking", Map.of(
            "alertId", alertId,
            "alertType", alertType,
            "severity", "EMERGENCY",
            "incidentId", incidentId,
            "maxResponseTimeMinutes", 2, // Emergency alerts must be acknowledged within 2 minutes
            "requiresExecutiveResponse", true,
            "escalationLevel", 1,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        log.error("🚨 EMERGENCY ALERT FULLY PROCESSED 🚨 - alertId: {}, incidentId: {}, impact: {}, correlationId: {}", 
            alertId, incidentId, businessImpact, correlationId);
    }

    private void processEmergencyAlertFallback(
            String alertId,
            String alertType,
            String severity,
            String title,
            String description,
            String source,
            String affectedSystems,
            String businessImpact,
            Boolean triggerDisasterRecovery,
            Boolean notifyExecutives,
            Boolean activateWarRoom,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("🚨 CIRCUIT BREAKER ACTIVATED FOR EMERGENCY ALERT 🚨 - alertId: {}, type: {}, correlationId: {}, error: {}", 
            alertId, alertType, correlationId, e.getMessage());
        
        // Even in fallback, try absolute emergency notification
        try {
            emergencyResponseService.sendAbsoluteEmergencyFallbackNotification(
                alertId, alertType, title, description, businessImpact, correlationId);
            
            // Manually trigger executive phone calls if needed
            if (notifyExecutives) {
                executiveService.triggerManualExecutivePhoneCalls(alertId, title, description);
            }
        } catch (Exception fallbackException) {
            log.error("🚨 ABSOLUTE EMERGENCY FALLBACK FAILED 🚨 - alertId: {}, error: {}", 
                alertId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("alertId", alertId);
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("severity", "EMERGENCY");
        fallbackEvent.put("title", title);
        fallbackEvent.put("source", source);
        fallbackEvent.put("businessImpact", businessImpact);
        fallbackEvent.put("affectedSystems", affectedSystems);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("emergency-alert-notification-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("🚨 CATASTROPHIC SYSTEM FAILURE: Emergency alert message sent to DLT 🚨 - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("emergency-alert-notification-processing-failures", dltEvent);
            
            // Send ultimate emergency notification about the system failure
            emergencyResponseService.sendUltimateCatastrophicFailureAlert(
                "Emergency Alert Processing System Catastrophic Failure",
                String.format("🚨 SYSTEM MELTDOWN: Emergency alert processing failed completely. This is a catastrophic system failure. Error: %s", exceptionMessage),
                dltEvent
            );
            
            // Manually trigger all emergency contacts
            emergencyResponseService.manuallyTriggerAllEmergencyContacts(
                "Emergency Notification System Failure",
                "The emergency notification system has completely failed. Manual intervention required immediately."
            );
            
        } catch (Exception e) {
            log.error("🚨 ULTIMATE SYSTEM CATASTROPHE 🚨 - Failed to process emergency alert DLT message: {}", e.getMessage(), e);
            
            // Last resort: Try to log to external system
            try {
                // This would typically be a webhook to external monitoring system
                System.err.println("ULTIMATE EMERGENCY: Emergency notification system has completely failed. Manual intervention required NOW.");
            } catch (Exception ignored) {
                // Absolute last resort - if even System.err fails, the system is beyond recovery
            }
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