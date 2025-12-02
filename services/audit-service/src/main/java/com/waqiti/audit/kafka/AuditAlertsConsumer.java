package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditAlert;
import com.waqiti.audit.domain.AlertSeverity;
import com.waqiti.audit.domain.AlertStatus;
import com.waqiti.audit.repository.AuditAlertRepository;
import com.waqiti.audit.service.AuditAlertService;
import com.waqiti.audit.service.AuditNotificationService;
import com.waqiti.audit.service.ComplianceEscalationService;
import com.waqiti.audit.service.SIEMIntegrationService;
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
public class AuditAlertsConsumer {

    private final AuditAlertRepository alertRepository;
    private final AuditAlertService alertService;
    private final AuditNotificationService notificationService;
    private final ComplianceEscalationService escalationService;
    private final SIEMIntegrationService siemService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter auditAlertsCounter;
    private final Counter highSeverityAlertsCounter;
    private final Counter criticalAlertsCounter;
    private final Timer alertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AuditAlertsConsumer(
            AuditAlertRepository alertRepository,
            AuditAlertService alertService,
            AuditNotificationService notificationService,
            ComplianceEscalationService escalationService,
            SIEMIntegrationService siemService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.alertRepository = alertRepository;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.escalationService = escalationService;
        this.siemService = siemService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.auditAlertsCounter = Counter.builder("audit.alerts.events")
            .description("Count of audit alert events")
            .register(meterRegistry);
        
        this.highSeverityAlertsCounter = Counter.builder("audit.alerts.high.severity.events")
            .description("Count of high severity audit alerts")
            .register(meterRegistry);
        
        this.criticalAlertsCounter = Counter.builder("audit.alerts.critical.events")
            .description("Count of critical audit alerts")
            .register(meterRegistry);
        
        this.alertProcessingTimer = Timer.builder("audit.alerts.processing.duration")
            .description("Time taken to process audit alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "audit-alerts",
        groupId = "audit-alerts-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "audit-alerts-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAuditAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received audit alert event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String alertType = (String) eventData.get("alertType");
            String alertSeverity = (String) eventData.get("alertSeverity");
            String service = (String) eventData.get("service");
            String description = (String) eventData.get("description");
            String userId = (String) eventData.get("userId");
            String resourceId = (String) eventData.get("resourceId");
            Object timestampObj = eventData.get("timestamp");
            
            String correlationId = String.format("audit-alert-%s-%s-%d", 
                alertType, alertSeverity, System.currentTimeMillis());
            
            log.warn("Processing audit alert - type: {}, severity: {}, service: {}, correlationId: {}", 
                alertType, alertSeverity, service, correlationId);
            
            auditAlertsCounter.increment();
            
            processAuditAlert(alertType, alertSeverity, service, description, 
                userId, resourceId, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(alertProcessingTimer);
            
            log.info("Successfully processed audit alert event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process audit alert event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Audit alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "audit", fallbackMethod = "processAuditAlertFallback")
    @Retry(name = "audit")
    private void processAuditAlert(
            String alertType,
            String alertSeverity,
            String service,
            String description,
            String userId,
            String resourceId,
            Map<String, Object> eventData,
            String correlationId) {
        
        AlertSeverity severity = AlertSeverity.valueOf(alertSeverity);
        AlertStatus status = AlertStatus.OPEN;
        
        AuditAlert alert = AuditAlert.builder()
            .alertType(alertType)
            .severity(severity)
            .status(status)
            .service(service)
            .description(description)
            .userId(userId)
            .resourceId(resourceId)
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .metadata(eventData)
            .build();
        
        alertRepository.save(alert);
        
        alertService.processAuditAlert(alert, correlationId);
        
        // Update counters based on severity
        switch (severity) {
            case HIGH -> {
                highSeverityAlertsCounter.increment();
                handleHighSeverityAlert(alert, correlationId);
            }
            case CRITICAL -> {
                criticalAlertsCounter.increment();
                handleCriticalAlert(alert, correlationId);
            }
            case MEDIUM -> {
                handleMediumSeverityAlert(alert, correlationId);
            }
            case LOW -> {
                handleLowSeverityAlert(alert, correlationId);
            }
        }
        
        // Send to SIEM for all alerts
        siemService.sendAuditAlert(alert, correlationId);
        
        notificationService.sendAuditAlertNotification(
            alert,
            correlationId
        );
        
        kafkaTemplate.send("audit-alert-processed", Map.of(
            "alertId", alert.getId(),
            "alertType", alertType,
            "severity", severity.toString(),
            "status", status.toString(),
            "service", service,
            "userId", userId,
            "resourceId", resourceId,
            "eventType", "AUDIT_ALERT_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logAuditEvent(
            "AUDIT_ALERT_PROCESSED",
            alert.getId().toString(),
            Map.of(
                "alertType", alertType,
                "severity", severity.toString(),
                "status", status.toString(),
                "service", service,
                "description", description,
                "userId", userId,
                "resourceId", resourceId,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        if (severity == AlertSeverity.CRITICAL || severity == AlertSeverity.HIGH) {
            log.error("AUDIT ALERT: {} severity audit alert - type: {}, service: {}, correlationId: {}", 
                severity, alertType, service, correlationId);
        } else {
            log.warn("Audit alert processed - type: {}, severity: {}, service: {}, correlationId: {}", 
                alertType, severity, service, correlationId);
        }
    }

    private void handleCriticalAlert(AuditAlert alert, String correlationId) {
        log.error("CRITICAL AUDIT ALERT: {} - service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        // Immediate escalation for critical alerts
        escalationService.escalateCriticalAlert(alert, correlationId);
        
        kafkaTemplate.send("critical-audit-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "description", alert.getDescription(),
            "userId", alert.getUserId(),
            "resourceId", alert.getResourceId(),
            "severity", "CRITICAL",
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendCriticalAuditAlert(
            "Critical Audit Alert",
            String.format("Critical audit alert detected: %s in service %s", alert.getAlertType(), alert.getService()),
            Map.of(
                "alertId", alert.getId().toString(),
                "alertType", alert.getAlertType(),
                "service", alert.getService(),
                "correlationId", correlationId
            )
        );
    }

    private void handleHighSeverityAlert(AuditAlert alert, String correlationId) {
        log.error("HIGH SEVERITY AUDIT ALERT: {} - service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        escalationService.escalateHighSeverityAlert(alert, correlationId);
        
        kafkaTemplate.send("high-severity-audit-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "description", alert.getDescription(),
            "userId", alert.getUserId(),
            "resourceId", alert.getResourceId(),
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleMediumSeverityAlert(AuditAlert alert, String correlationId) {
        log.warn("Medium severity audit alert: {} - service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        kafkaTemplate.send("medium-severity-audit-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "description", alert.getDescription(),
            "severity", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleLowSeverityAlert(AuditAlert alert, String correlationId) {
        log.info("Low severity audit alert: {} - service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        kafkaTemplate.send("low-severity-audit-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "severity", "LOW",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processAuditAlertFallback(
            String alertType,
            String alertSeverity,
            String service,
            String description,
            String userId,
            String resourceId,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for audit alert - type: {}, severity: {}, correlationId: {}, error: {}", 
            alertType, alertSeverity, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("alertSeverity", alertSeverity);
        fallbackEvent.put("service", service);
        fallbackEvent.put("description", description);
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("resourceId", resourceId);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("audit-alert-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Audit alert event sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("audit-alert-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Audit Alert Processing Failed",
                String.format("CRITICAL: Failed to process audit alert after max retries. Error: %s", exceptionMessage),
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