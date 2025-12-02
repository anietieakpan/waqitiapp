package com.waqiti.security.kafka;

import com.waqiti.common.events.SecurityEscalationEvent;
import com.waqiti.security.domain.SecurityEscalation;
import com.waqiti.security.repository.SecurityEscalationRepository;
import com.waqiti.security.service.EscalationService;
import com.waqiti.security.service.IncidentCoordinationService;
import com.waqiti.security.metrics.SecurityMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityEscalationsConsumer {
    
    private final SecurityEscalationRepository escalationRepository;
    private final EscalationService escalationService;
    private final IncidentCoordinationService coordinationService;
    private final SecurityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"security-escalations", "escalation-cases", "critical-escalations"},
        groupId = "security-escalations-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "7"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleSecurityEscalation(
            @Payload SecurityEscalationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("escalation-%s-p%d-o%d", 
            event.getEscalationId(), partition, offset);
        
        log.warn("Processing security escalation: id={}, level={}, reason={}",
            event.getEscalationId(), event.getEscalationLevel(), event.getReason());
        
        try {
            SecurityEscalation escalation = SecurityEscalation.builder()
                .escalationId(event.getEscalationId())
                .sourceEntityId(event.getSourceEntityId())
                .sourceEntityType(event.getSourceEntityType())
                .escalationLevel(event.getEscalationLevel())
                .reason(event.getReason())
                .severity(event.getSeverity())
                .escalatedAt(LocalDateTime.now())
                .escalatedBy(event.getEscalatedBy())
                .status("ESCALATED")
                .correlationId(correlationId)
                .build();
            escalationRepository.save(escalation);
            
            List<String> escalationTeam = escalationService.getEscalationTeam(event.getEscalationLevel());
            
            for (String teamMember : escalationTeam) {
                notificationService.sendNotification(teamMember, 
                    String.format("URGENT: Security Escalation - Level %d", event.getEscalationLevel()),
                    String.format("Escalation ID: %s. Reason: %s. Severity: %s", 
                        event.getEscalationId(), event.getReason(), event.getSeverity()),
                    correlationId);
            }
            
            if (event.getEscalationLevel() >= 3) {
                coordinationService.triggerIncidentCommand(event.getEscalationId());
                
                notificationService.sendNotification("SECURITY_DIRECTOR", 
                    "CRITICAL Security Escalation",
                    String.format("Level %d escalation requires immediate attention: %s", 
                        event.getEscalationLevel(), event.getReason()),
                    correlationId);
            }
            
            escalationService.assignEscalationOwner(event.getEscalationId(), escalationTeam.get(0));
            
            metricsService.recordSecurityEscalation(event.getEscalationLevel(), event.getSeverity());
            
            auditService.logSecurityEvent("SECURITY_ESCALATED", event.getEscalationId(),
                Map.of("level", event.getEscalationLevel(), "reason", event.getReason(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
            log.error("Security escalation processed: id={}, level={}, assignedTo={}",
                event.getEscalationId(), event.getEscalationLevel(), escalationTeam.get(0));
            
        } catch (Exception e) {
            log.error("Failed to process security escalation: {}", e.getMessage(), e);
            kafkaTemplate.send("security-escalations-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
}