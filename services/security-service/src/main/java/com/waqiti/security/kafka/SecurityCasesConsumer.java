package com.waqiti.security.kafka;

import com.waqiti.common.events.SecurityCaseEvent;
import com.waqiti.security.domain.SecurityCase;
import com.waqiti.security.repository.SecurityCaseRepository;
import com.waqiti.security.service.CaseManagementService;
import com.waqiti.security.service.InvestigationService;
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
public class SecurityCasesConsumer {
    
    private final SecurityCaseRepository caseRepository;
    private final CaseManagementService caseService;
    private final InvestigationService investigationService;
    private final SecurityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"security-cases", "investigation-cases", "security-review-cases"},
        groupId = "security-cases-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleSecurityCase(
            @Payload SecurityCaseEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("case-%s-p%d-o%d", 
            event.getCaseId(), partition, offset);
        
        log.info("Processing security case: id={}, type={}, priority={}",
            event.getCaseId(), event.getCaseType(), event.getPriority());
        
        try {
            switch (event.getStatus()) {
                case "CASE_CREATED":
                    createCase(event, correlationId);
                    break;
                    
                case "ASSIGNED":
                    assignCase(event, correlationId);
                    break;
                    
                case "UNDER_INVESTIGATION":
                    investigateCase(event, correlationId);
                    break;
                    
                case "EVIDENCE_GATHERED":
                    gatherEvidence(event, correlationId);
                    break;
                    
                case "RESOLVED":
                    resolveCase(event, correlationId);
                    break;
                    
                case "ESCALATED":
                    escalateCase(event, correlationId);
                    break;
                    
                case "CLOSED":
                    closeCase(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown case status: {}", event.getStatus());
                    break;
            }
            
            auditService.logSecurityEvent("SECURITY_CASE_PROCESSED", event.getCaseId(),
                Map.of("caseType", event.getCaseType(), "status", event.getStatus(),
                    "priority", event.getPriority(), "correlationId", correlationId,
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process security case: {}", e.getMessage(), e);
            kafkaTemplate.send("security-cases-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void createCase(SecurityCaseEvent event, String correlationId) {
        SecurityCase securityCase = SecurityCase.builder()
            .caseId(event.getCaseId())
            .caseType(event.getCaseType())
            .priority(event.getPriority())
            .accountId(event.getAccountId())
            .description(event.getDescription())
            .status("CASE_CREATED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        caseRepository.save(securityCase);
        
        caseService.routeCase(event.getCaseId(), event.getCaseType(), event.getPriority());
        
        metricsService.recordCaseCreated(event.getCaseType(), event.getPriority());
        
        log.info("Security case created: id={}, type={}", event.getCaseId(), event.getCaseType());
    }
    
    private void assignCase(SecurityCaseEvent event, String correlationId) {
        SecurityCase securityCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Security case not found"));
        
        securityCase.setStatus("ASSIGNED");
        securityCase.setAssignedTo(event.getAssignedTo());
        securityCase.setAssignedAt(LocalDateTime.now());
        caseRepository.save(securityCase);
        
        notificationService.sendNotification(event.getAssignedTo(), "New Security Case Assigned",
            String.format("You have been assigned case %s: %s", event.getCaseId(), event.getCaseType()),
            correlationId);
        
        metricsService.recordCaseAssigned(event.getCaseType());
        
        log.info("Case assigned: id={}, assignedTo={}", event.getCaseId(), event.getAssignedTo());
    }
    
    private void investigateCase(SecurityCaseEvent event, String correlationId) {
        SecurityCase securityCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Security case not found"));
        
        securityCase.setStatus("UNDER_INVESTIGATION");
        securityCase.setInvestigationStartedAt(LocalDateTime.now());
        caseRepository.save(securityCase);
        
        investigationService.startInvestigation(event.getCaseId(), event.getCaseType());
        
        metricsService.recordCaseInvestigation(event.getCaseType());
        
        log.info("Investigation started: caseId={}", event.getCaseId());
    }
    
    private void gatherEvidence(SecurityCaseEvent event, String correlationId) {
        SecurityCase securityCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Security case not found"));
        
        securityCase.setStatus("EVIDENCE_GATHERED");
        securityCase.setEvidenceCollectedAt(LocalDateTime.now());
        caseRepository.save(securityCase);
        
        investigationService.documentEvidence(event.getCaseId(), event.getEvidence());
        
        metricsService.recordEvidenceGathered(event.getCaseType());
        
        log.info("Evidence gathered: caseId={}", event.getCaseId());
    }
    
    private void resolveCase(SecurityCaseEvent event, String correlationId) {
        SecurityCase securityCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Security case not found"));
        
        securityCase.setStatus("RESOLVED");
        securityCase.setResolvedAt(LocalDateTime.now());
        securityCase.setResolution(event.getResolution());
        caseRepository.save(securityCase);
        
        caseService.applyResolution(event.getCaseId(), event.getResolution());
        
        metricsService.recordCaseResolved(event.getCaseType());
        
        log.info("Case resolved: id={}, resolution={}", event.getCaseId(), event.getResolution());
    }
    
    private void escalateCase(SecurityCaseEvent event, String correlationId) {
        SecurityCase securityCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Security case not found"));
        
        securityCase.setStatus("ESCALATED");
        securityCase.setEscalatedAt(LocalDateTime.now());
        securityCase.setEscalationReason(event.getReason());
        caseRepository.save(securityCase);
        
        notificationService.sendNotification("SECURITY_MANAGER", "Case Escalated",
            String.format("Security case %s has been escalated: %s", event.getCaseId(), event.getReason()),
            correlationId);
        
        metricsService.recordCaseEscalated(event.getCaseType(), event.getReason());
        
        log.warn("Case escalated: id={}, reason={}", event.getCaseId(), event.getReason());
    }
    
    private void closeCase(SecurityCaseEvent event, String correlationId) {
        SecurityCase securityCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Security case not found"));
        
        securityCase.setStatus("CLOSED");
        securityCase.setClosedAt(LocalDateTime.now());
        caseRepository.save(securityCase);
        
        caseService.archiveCase(event.getCaseId());
        
        metricsService.recordCaseClosed(event.getCaseType());
        
        log.info("Case closed: id={}", event.getCaseId());
    }
}