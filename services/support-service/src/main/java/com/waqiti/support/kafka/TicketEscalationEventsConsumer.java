package com.waqiti.support.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.support.service.TicketEscalationService;
import com.waqiti.support.service.SupportNotificationService;
import com.waqiti.common.exception.SupportProcessingException;
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
 * Kafka Consumer for Ticket Escalation Events
 * Handles support ticket escalations, priority management, and SLA monitoring
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketEscalationEventsConsumer {
    
    private final TicketEscalationService escalationService;
    private final SupportNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"ticket-escalation-events", "ticket-escalated", "sla-breach-detected", "priority-changed"},
        groupId = "support-service-escalation-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleTicketEscalationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID ticketId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            ticketId = UUID.fromString((String) event.get("ticketId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String escalationReason = (String) event.get("escalationReason");
            String priority = (String) event.get("priority");
            String assignedAgent = (String) event.get("assignedAgent");
            String escalatedTo = (String) event.get("escalatedTo");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing ticket escalation event - TicketId: {}, CustomerId: {}, Type: {}, Priority: {}", 
                    ticketId, customerId, eventType, priority);
            
            switch (eventType) {
                case "TICKET_ESCALATED":
                    escalationService.processTicketEscalation(ticketId, customerId, escalationReason,
                            assignedAgent, escalatedTo, timestamp);
                    break;
                case "SLA_BREACH_DETECTED":
                    escalationService.processSLABreach(ticketId, customerId, 
                            (String) event.get("breachType"), timestamp);
                    break;
                case "PRIORITY_CHANGED":
                    escalationService.processPriorityChange(ticketId, priority,
                            (String) event.get("previousPriority"), timestamp);
                    break;
                default:
                    escalationService.processGenericEscalationEvent(ticketId, eventType, event, timestamp);
            }
            
            notificationService.sendEscalationNotification(ticketId, customerId, eventType,
                    escalationReason, priority, timestamp);
            
            auditService.auditFinancialEvent(
                    "TICKET_ESCALATION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Ticket escalation event processed - Type: %s, Priority: %s", eventType, priority),
                    Map.of(
                            "ticketId", ticketId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "priority", priority,
                            "escalationReason", escalationReason != null ? escalationReason : "N/A",
                            "assignedAgent", assignedAgent != null ? assignedAgent : "N/A"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed ticket escalation event - TicketId: {}, EventType: {}", 
                    ticketId, eventType);
            
        } catch (Exception e) {
            log.error("Ticket escalation event processing failed - TicketId: {}, CustomerId: {}, Error: {}", 
                    ticketId, customerId, e.getMessage(), e);
            throw new SupportProcessingException("Ticket escalation event processing failed", e);
        }
    }
}