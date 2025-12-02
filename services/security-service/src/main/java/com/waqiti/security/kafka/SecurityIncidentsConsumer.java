package com.waqiti.security.kafka;

import com.waqiti.common.events.SecurityIncidentEvent;
import com.waqiti.security.domain.SecurityIncident;
import com.waqiti.security.repository.SecurityIncidentRepository;
import com.waqiti.security.service.IncidentResponseService;
import com.waqiti.security.service.ThreatAnalysisService;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityIncidentsConsumer {
    
    private final SecurityIncidentRepository incidentRepository;
    private final IncidentResponseService responseService;
    private final ThreatAnalysisService threatAnalysisService;
    private final SecurityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"security-incidents", "security-alerts", "security-threats"},
        groupId = "security-incidents-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleSecurityIncident(
            @Payload SecurityIncidentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("incident-%s-p%d-o%d", 
            event.getIncidentId(), partition, offset);
        
        log.info("Processing security incident: id={}, type={}, severity={}",
            event.getIncidentId(), event.getIncidentType(), event.getSeverity());
        
        try {
            SecurityIncident incident = SecurityIncident.builder()
                .incidentId(event.getIncidentId())
                .incidentType(event.getIncidentType())
                .severity(event.getSeverity())
                .targetEntityId(event.getTargetEntityId())
                .targetEntityType(event.getTargetEntityType())
                .description(event.getDescription())
                .detectedAt(LocalDateTime.now())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .status("DETECTED")
                .correlationId(correlationId)
                .build();
            incidentRepository.save(incident);
            
            int threatScore = threatAnalysisService.calculateThreatScore(event);
            incident.setThreatScore(threatScore);
            incidentRepository.save(incident);
            
            if ("CRITICAL".equals(event.getSeverity()) || threatScore > 80) {
                responseService.initiateImmediateResponse(event.getIncidentId());
                
                notificationService.sendNotification("SECURITY_TEAM", "CRITICAL Security Incident",
                    String.format("Critical incident detected: %s (Score: %d)", 
                        event.getIncidentType(), threatScore),
                    correlationId);
                
                if ("DEVICE_COMPROMISED".equals(event.getIncidentType()) || 
                    "ACCOUNT_TAKEOVER".equals(event.getIncidentType())) {
                    responseService.lockdownAccount(event.getTargetEntityId());
                }
            }
            
            responseService.triggerAutomatedResponse(event.getIncidentId(), event.getIncidentType());
            
            metricsService.recordSecurityIncident(event.getIncidentType(), event.getSeverity());
            
            auditService.logSecurityEvent("SECURITY_INCIDENT_DETECTED", event.getIncidentId(),
                Map.of("type", event.getIncidentType(), "severity", event.getSeverity(),
                    "threatScore", threatScore, "correlationId", correlationId,
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
            log.warn("Security incident processed: id={}, type={}, severity={}, threatScore={}",
                event.getIncidentId(), event.getIncidentType(), event.getSeverity(), threatScore);
            
        } catch (Exception e) {
            log.error("Failed to process security incident: {}", e.getMessage(), e);
            kafkaTemplate.send("security-incidents-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
}