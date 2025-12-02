package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.CriticalAuditAlert;
import com.waqiti.audit.domain.AlertSeverity;
import com.waqiti.audit.domain.AlertStatus;
import com.waqiti.audit.domain.EscalationLevel;
import com.waqiti.audit.repository.CriticalAuditAlertRepository;
import com.waqiti.audit.service.CriticalAuditAlertService;
import com.waqiti.audit.service.EmergencyNotificationService;
import com.waqiti.audit.service.IncidentManagementService;
import com.waqiti.audit.service.SIEMIntegrationService;
import com.waqiti.audit.service.RegulatoryEscalationService;
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
public class AuditAlertsCriticalConsumer {

    private final CriticalAuditAlertRepository criticalAlertRepository;
    private final CriticalAuditAlertService criticalAlertService;
    private final EmergencyNotificationService emergencyNotificationService;
    private final IncidentManagementService incidentService;
    private final SIEMIntegrationService siemService;
    private final RegulatoryEscalationService regulatoryEscalationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter criticalAlertsCounter;
    private final Counter securityIncidentCounter;
    private final Counter complianceViolationCounter;
    private final Counter emergencyEscalationCounter;
    private final Timer criticalAlertProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AuditAlertsCriticalConsumer(
            CriticalAuditAlertRepository criticalAlertRepository,
            CriticalAuditAlertService criticalAlertService,
            EmergencyNotificationService emergencyNotificationService,
            IncidentManagementService incidentService,
            SIEMIntegrationService siemService,
            RegulatoryEscalationService regulatoryEscalationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.criticalAlertRepository = criticalAlertRepository;
        this.criticalAlertService = criticalAlertService;
        this.emergencyNotificationService = emergencyNotificationService;
        this.incidentService = incidentService;
        this.siemService = siemService;
        this.regulatoryEscalationService = regulatoryEscalationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.criticalAlertsCounter = Counter.builder("audit.alerts.critical.events")
            .description("Count of critical audit alert events")
            .register(meterRegistry);
        
        this.securityIncidentCounter = Counter.builder("audit.alerts.security.incident.events")
            .description("Count of security incident alerts")
            .register(meterRegistry);
        
        this.complianceViolationCounter = Counter.builder("audit.alerts.compliance.violation.events")
            .description("Count of compliance violation alerts")
            .register(meterRegistry);
        
        this.emergencyEscalationCounter = Counter.builder("audit.alerts.emergency.escalation.events")
            .description("Count of emergency escalation alerts")
            .register(meterRegistry);
        
        this.criticalAlertProcessingTimer = Timer.builder("audit.alerts.critical.processing.duration")
            .description("Time taken to process critical audit alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "audit-alerts-critical",
        groupId = "audit-alerts-critical-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "audit-alerts-critical-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCriticalAuditAlertEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.error("CRITICAL: Received critical audit alert event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String id = (String) eventData.get("id");
            String alertType = (String) eventData.get("alertType");
            String service = (String) eventData.get("service");
            String description = (String) eventData.get("description");
            String userId = (String) eventData.get("userId");
            String resourceId = (String) eventData.get("resourceId");
            String incidentCategory = (String) eventData.get("incidentCategory");
            String escalationLevel = (String) eventData.getOrDefault("escalationLevel", "EMERGENCY");
            Object timestampObj = eventData.get("timestamp");
            
            String correlationId = String.format("critical-audit-alert-%s-%s-%d", 
                alertType, escalationLevel, System.currentTimeMillis());
            
            log.error("CRITICAL: Processing critical audit alert - type: {}, service: {}, category: {}, correlationId: {}", 
                alertType, service, incidentCategory, correlationId);
            
            criticalAlertsCounter.increment();
            
            processCriticalAuditAlert(id, alertType, service, description, userId, 
                resourceId, incidentCategory, escalationLevel, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(criticalAlertProcessingTimer);
            
            log.error("CRITICAL: Successfully processed critical audit alert event: {}", eventId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process critical audit alert event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Critical audit alert processing failed", e);
        }
    }

    @CircuitBreaker(name = "audit", fallbackMethod = "processCriticalAuditAlertFallback")
    @Retry(name = "audit")
    private void processCriticalAuditAlert(
            String id,
            String alertType,
            String service,
            String description,
            String userId,
            String resourceId,
            String incidentCategory,
            String escalationLevel,
            Map<String, Object> eventData,
            String correlationId) {
        
        EscalationLevel escalation = EscalationLevel.valueOf(escalationLevel);
        AlertStatus status = AlertStatus.CRITICAL_OPEN;
        
        CriticalAuditAlert criticalAlert = CriticalAuditAlert.builder()
            .originalAlertId(id)
            .alertType(alertType)
            .severity(AlertSeverity.CRITICAL)
            .status(status)
            .service(service)
            .description(description)
            .userId(userId)
            .resourceId(resourceId)
            .incidentCategory(incidentCategory)
            .escalationLevel(escalation)
            .detectedAt(LocalDateTime.now())
            .escalatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .metadata(eventData)
            .requiresImmediateAction(true)
            .build();
        
        criticalAlertRepository.save(criticalAlert);
        
        criticalAlertService.processCriticalAlert(criticalAlert, correlationId);
        
        // Handle based on incident category
        switch (incidentCategory) {
            case "SECURITY_BREACH" -> {
                securityIncidentCounter.increment();
                handleSecurityIncident(criticalAlert, correlationId);
            }
            case "COMPLIANCE_VIOLATION" -> {
                complianceViolationCounter.increment();
                handleComplianceViolation(criticalAlert, correlationId);
            }
            case "DATA_BREACH" -> {
                handleDataBreach(criticalAlert, correlationId);
            }
            case "SYSTEM_COMPROMISE" -> {
                handleSystemCompromise(criticalAlert, correlationId);
            }
            case "FRAUD_DETECTION" -> {
                handleFraudDetection(criticalAlert, correlationId);
            }
            default -> {
                handleGenericCriticalAlert(criticalAlert, correlationId);
            }
        }
        
        // Always escalate based on escalation level
        if (escalation == EscalationLevel.EMERGENCY) {
            emergencyEscalationCounter.increment();
            handleEmergencyEscalation(criticalAlert, correlationId);
        }
        
        // Send to SIEM immediately
        siemService.sendCriticalAlert(criticalAlert, correlationId);
        
        // Create incident record
        incidentService.createCriticalIncident(criticalAlert, correlationId);
        
        emergencyNotificationService.sendCriticalAlertNotification(
            criticalAlert,
            correlationId
        );
        
        kafkaTemplate.send("critical-audit-alert-processed", Map.of(
            "alertId", criticalAlert.getId(),
            "originalAlertId", id,
            "alertType", alertType,
            "severity", "CRITICAL",
            "status", status.toString(),
            "service", service,
            "incidentCategory", incidentCategory,
            "escalationLevel", escalation.toString(),
            "userId", userId,
            "resourceId", resourceId,
            "eventType", "CRITICAL_AUDIT_ALERT_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logCriticalAuditEvent(
            "CRITICAL_AUDIT_ALERT_PROCESSED",
            criticalAlert.getId().toString(),
            Map.of(
                "originalAlertId", id,
                "alertType", alertType,
                "severity", "CRITICAL",
                "status", status.toString(),
                "service", service,
                "incidentCategory", incidentCategory,
                "escalationLevel", escalation.toString(),
                "description", description,
                "userId", userId,
                "resourceId", resourceId,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.error("CRITICAL ALERT PROCESSED: type: {}, service: {}, category: {}, escalation: {}, correlationId: {}", 
            alertType, service, incidentCategory, escalation, correlationId);
    }

    private void handleSecurityIncident(CriticalAuditAlert alert, String correlationId) {
        log.error("SECURITY INCIDENT: Critical security alert - type: {}, service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        incidentService.escalateSecurityIncident(alert, correlationId);
        
        kafkaTemplate.send("security-incident-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "description", alert.getDescription(),
            "userId", alert.getUserId(),
            "resourceId", alert.getResourceId(),
            "severity", "CRITICAL",
            "incidentType", "SECURITY_BREACH",
            "requiresImmediateResponse", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleComplianceViolation(CriticalAuditAlert alert, String correlationId) {
        log.error("COMPLIANCE VIOLATION: Critical compliance alert - type: {}, service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        regulatoryEscalationService.escalateComplianceViolation(alert, correlationId);
        
        kafkaTemplate.send("compliance-violation-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "description", alert.getDescription(),
            "severity", "CRITICAL",
            "violationType", "COMPLIANCE_BREACH",
            "requiresRegulatoryNotification", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleDataBreach(CriticalAuditAlert alert, String correlationId) {
        log.error("DATA BREACH: Critical data breach alert - type: {}, service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        incidentService.initiateDataBreachProtocol(alert, correlationId);
        
        kafkaTemplate.send("data-breach-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "severity", "CRITICAL",
            "breachType", "DATA_EXPOSURE",
            "requiresCustomerNotification", true,
            "requiresRegulatoryReporting", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleSystemCompromise(CriticalAuditAlert alert, String correlationId) {
        log.error("SYSTEM COMPROMISE: Critical system compromise alert - type: {}, service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        incidentService.initiateSystemLockdown(alert, correlationId);
        
        kafkaTemplate.send("system-compromise-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "severity", "CRITICAL",
            "compromiseType", "SYSTEM_BREACH",
            "requiresSystemLockdown", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleFraudDetection(CriticalAuditAlert alert, String correlationId) {
        log.error("FRAUD DETECTION: Critical fraud alert - type: {}, service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        kafkaTemplate.send("critical-fraud-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "userId", alert.getUserId(),
            "severity", "CRITICAL",
            "fraudType", "CRITICAL_FRAUD",
            "requiresAccountFreeze", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleGenericCriticalAlert(CriticalAuditAlert alert, String correlationId) {
        log.error("GENERIC CRITICAL ALERT: type: {}, service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        kafkaTemplate.send("generic-critical-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleEmergencyEscalation(CriticalAuditAlert alert, String correlationId) {
        log.error("EMERGENCY ESCALATION: Emergency critical alert - type: {}, service: {}, correlationId: {}", 
            alert.getAlertType(), alert.getService(), correlationId);
        
        emergencyNotificationService.sendEmergencyEscalation(alert, correlationId);
        
        kafkaTemplate.send("emergency-escalation-alerts", Map.of(
            "alertId", alert.getId(),
            "alertType", alert.getAlertType(),
            "service", alert.getService(),
            "description", alert.getDescription(),
            "severity", "CRITICAL",
            "escalationLevel", "EMERGENCY",
            "requiresC_LevelNotification", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processCriticalAuditAlertFallback(
            String id,
            String alertType,
            String service,
            String description,
            String userId,
            String resourceId,
            String incidentCategory,
            String escalationLevel,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("CRITICAL: Circuit breaker activated for critical audit alert - type: {}, service: {}, correlationId: {}, error: {}", 
            alertType, service, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("id", id);
        fallbackEvent.put("alertType", alertType);
        fallbackEvent.put("service", service);
        fallbackEvent.put("description", description);
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("resourceId", resourceId);
        fallbackEvent.put("incidentCategory", incidentCategory);
        fallbackEvent.put("escalationLevel", escalationLevel);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("critical-audit-alert-fallback-events", fallbackEvent);
        
        // Send emergency notification about the fallback
        emergencyNotificationService.sendFallbackAlert(
            "Critical Audit Alert Processing Failed",
            String.format("EMERGENCY: Failed to process critical audit alert: %s", alertType),
            fallbackEvent
        );
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("EMERGENCY: Critical audit alert event sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("critical-audit-alert-processing-failures", dltEvent);
            
            emergencyNotificationService.sendEmergencyAlert(
                "EMERGENCY: Critical Audit Alert Processing Failed",
                String.format("EMERGENCY: Failed to process critical audit alert after max retries. This requires immediate manual intervention. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("EMERGENCY: Failed to process DLT message for critical audit alert: {}", e.getMessage(), e);
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