package com.waqiti.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.infrastructure.service.SystemMaintenanceService;
import com.waqiti.infrastructure.service.InfrastructureNotificationService;
import com.waqiti.common.exception.InfrastructureProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for System Maintenance Events
 * Handles scheduled maintenance, system updates, and service availability
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemMaintenanceEventsConsumer {
    
    private final SystemMaintenanceService maintenanceService;
    private final InfrastructureNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"system-maintenance-events", "maintenance-scheduled", "maintenance-started", "maintenance-completed"},
        groupId = "infrastructure-service-maintenance-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleSystemMaintenanceEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID maintenanceId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            maintenanceId = UUID.fromString((String) event.get("maintenanceId"));
            eventType = (String) event.get("eventType");
            String serviceName = (String) event.get("serviceName");
            String maintenanceType = (String) event.get("maintenanceType");
            LocalDateTime scheduledTime = LocalDateTime.parse((String) event.get("scheduledTime"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing system maintenance event - MaintenanceId: {}, Type: {}, Service: {}", 
                    maintenanceId, eventType, serviceName);
            
            switch (eventType) {
                case "MAINTENANCE_SCHEDULED":
                    maintenanceService.scheduleMaintenanceWindow(maintenanceId, serviceName,
                            maintenanceType, scheduledTime, timestamp);
                    break;
                case "MAINTENANCE_STARTED":
                    maintenanceService.startMaintenance(maintenanceId, serviceName, timestamp);
                    break;
                case "MAINTENANCE_COMPLETED":
                    maintenanceService.completeMaintenance(maintenanceId, serviceName,
                            (String) event.get("status"), timestamp);
                    break;
                default:
                    maintenanceService.processGenericMaintenanceEvent(maintenanceId, eventType, event, timestamp);
            }
            
            notificationService.sendMaintenanceNotification(maintenanceId, serviceName,
                    eventType, maintenanceType, scheduledTime, timestamp);
            
            auditService.auditFinancialEvent(
                    "SYSTEM_MAINTENANCE_EVENT_PROCESSED",
                    serviceName,
                    String.format("System maintenance event processed - Type: %s, Service: %s", eventType, serviceName),
                    Map.of(
                            "maintenanceId", maintenanceId.toString(),
                            "eventType", eventType,
                            "serviceName", serviceName,
                            "maintenanceType", maintenanceType,
                            "scheduledTime", scheduledTime.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed system maintenance event - MaintenanceId: {}, EventType: {}", 
                    maintenanceId, eventType);
            
        } catch (Exception e) {
            log.error("System maintenance event processing failed - MaintenanceId: {}, Error: {}", 
                    maintenanceId, e.getMessage(), e);
            throw new InfrastructureProcessingException("System maintenance event processing failed", e);
        }
    }
}