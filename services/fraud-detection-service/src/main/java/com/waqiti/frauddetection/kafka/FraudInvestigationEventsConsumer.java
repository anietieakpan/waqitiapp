package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.FraudInvestigationEvent;
import com.waqiti.frauddetection.domain.FraudInvestigation;
import com.waqiti.frauddetection.domain.InvestigationStatus;
import com.waqiti.frauddetection.repository.FraudInvestigationRepository;
import com.waqiti.frauddetection.service.FraudInvestigationService;
import com.waqiti.frauddetection.service.CaseManagementService;
import com.waqiti.frauddetection.service.EvidenceCollectionService;
import com.waqiti.frauddetection.metrics.InvestigationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class FraudInvestigationEventsConsumer {
    
    private final FraudInvestigationRepository investigationRepository;
    private final FraudInvestigationService investigationService;
    private final CaseManagementService caseManagementService;
    private final EvidenceCollectionService evidenceCollectionService;
    private final InvestigationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"fraud-investigation-events", "case-management-events", "investigation-updates"},
        groupId = "fraud-investigation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleFraudInvestigationEvent(
            @Payload FraudInvestigationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("investigation-%s-p%d-o%d", 
            event.getInvestigationId(), partition, offset);
        
        log.info("Processing investigation event: investigationId={}, eventType={}, correlation={}",
            event.getInvestigationId(), event.getEventType(), correlationId);
        
        try {
            switch (event.getEventType()) {
                case INVESTIGATION_OPENED:
                    processInvestigationOpened(event, correlationId);
                    break;
                case EVIDENCE_COLLECTED:
                    processEvidenceCollected(event, correlationId);
                    break;
                case INVESTIGATION_ESCALATED:
                    processInvestigationEscalated(event, correlationId);
                    break;
                case INVESTIGATION_CLOSED:
                    processInvestigationClosed(event, correlationId);
                    break;
                case DECISION_MADE:
                    processDecisionMade(event, correlationId);
                    break;
                case RESTITUTION_INITIATED:
                    processRestitutionInitiated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown investigation event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logSecurityEvent(
                "INVESTIGATION_EVENT_PROCESSED",
                event.getInvestigationId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "caseId", event.getCaseId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process investigation event: investigationId={}, error={}",
                event.getInvestigationId(), e.getMessage(), e);
            handleInvestigationEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processInvestigationOpened(FraudInvestigationEvent event, String correlationId) {
        log.info("Processing investigation opened: investigationId={}, subject={}, priority={}", 
            event.getInvestigationId(), event.getSubjectId(), event.getPriority());
        
        FraudInvestigation investigation = FraudInvestigation.builder()
            .id(event.getInvestigationId())
            .caseId(event.getCaseId())
            .subjectId(event.getSubjectId())
            .subjectType(event.getSubjectType())
            .investigationType(event.getInvestigationType())
            .priority(event.getPriority())
            .status(InvestigationStatus.OPEN)
            .openedAt(LocalDateTime.now())
            .openedBy(event.getOpenedBy())
            .assignedTo(event.getAssignedTo())
            .description(event.getDescription())
            .estimatedLoss(event.getEstimatedLoss())
            .correlationId(correlationId)
            .build();
        
        investigationRepository.save(investigation);
        
        caseManagementService.createCase(investigation);
        evidenceCollectionService.initializeEvidenceCollection(event.getInvestigationId());
        
        notificationService.sendNotification(
            event.getAssignedTo(),
            "New Fraud Investigation Assigned",
            String.format("Investigation %s has been assigned to you. Priority: %s",
                event.getInvestigationId(), event.getPriority()),
            event.getInvestigationId()
        );
        
        metricsService.recordInvestigationOpened(event.getInvestigationType(), event.getPriority());
    }
    
    private void processEvidenceCollected(FraudInvestigationEvent event, String correlationId) {
        log.info("Processing evidence collected: investigationId={}, evidenceType={}", 
            event.getInvestigationId(), event.getEvidenceType());
        
        FraudInvestigation investigation = investigationRepository
            .findById(event.getInvestigationId())
            .orElseThrow();
        
        evidenceCollectionService.addEvidence(
            event.getInvestigationId(),
            event.getEvidenceType(),
            event.getEvidenceData(),
            event.getCollectedBy()
        );
        
        investigation.setEvidenceCount(investigation.getEvidenceCount() + 1);
        investigation.setLastUpdatedAt(LocalDateTime.now());
        investigationRepository.save(investigation);
        
        metricsService.recordEvidenceCollected(event.getEvidenceType());
    }
    
    private void processInvestigationEscalated(FraudInvestigationEvent event, String correlationId) {
        log.warn("Processing investigation escalation: investigationId={}, reason={}", 
            event.getInvestigationId(), event.getEscalationReason());
        
        FraudInvestigation investigation = investigationRepository
            .findById(event.getInvestigationId())
            .orElseThrow();
        
        investigation.setStatus(InvestigationStatus.ESCALATED);
        investigation.setEscalatedAt(LocalDateTime.now());
        investigation.setEscalationReason(event.getEscalationReason());
        investigation.setEscalatedTo(event.getEscalatedTo());
        investigationRepository.save(investigation);
        
        notificationService.sendNotification(
            event.getEscalatedTo(),
            "Investigation Escalated",
            String.format("Investigation %s has been escalated to you. Reason: %s",
                event.getInvestigationId(), event.getEscalationReason()),
            event.getInvestigationId()
        );
        
        metricsService.recordInvestigationEscalated(event.getEscalationReason());
    }
    
    private void processInvestigationClosed(FraudInvestigationEvent event, String correlationId) {
        log.info("Processing investigation closed: investigationId={}, outcome={}", 
            event.getInvestigationId(), event.getOutcome());
        
        FraudInvestigation investigation = investigationRepository
            .findById(event.getInvestigationId())
            .orElseThrow();
        
        investigation.setStatus(InvestigationStatus.CLOSED);
        investigation.setClosedAt(LocalDateTime.now());
        investigation.setClosedBy(event.getClosedBy());
        investigation.setOutcome(event.getOutcome());
        investigation.setResolutionNotes(event.getResolutionNotes());
        investigationRepository.save(investigation);
        
        caseManagementService.closeCase(event.getCaseId(), event.getOutcome());
        
        metricsService.recordInvestigationClosed(
            event.getOutcome(),
            investigation.getOpenedAt(),
            investigation.getClosedAt()
        );
    }
    
    private void processDecisionMade(FraudInvestigationEvent event, String correlationId) {
        log.info("Processing investigation decision: investigationId={}, decision={}", 
            event.getInvestigationId(), event.getDecision());
        
        FraudInvestigation investigation = investigationRepository
            .findById(event.getInvestigationId())
            .orElseThrow();
        
        investigation.setDecision(event.getDecision());
        investigation.setDecisionMadeAt(LocalDateTime.now());
        investigation.setDecisionMadeBy(event.getDecisionMadeBy());
        investigation.setDecisionReasoning(event.getDecisionReasoning());
        investigationRepository.save(investigation);
        
        if ("FRAUD_CONFIRMED".equals(event.getDecision())) {
            investigationService.processFraudConfirmed(investigation);
        }
        
        metricsService.recordDecisionMade(event.getDecision());
    }
    
    private void processRestitutionInitiated(FraudInvestigationEvent event, String correlationId) {
        log.info("Processing restitution: investigationId={}, amount={}", 
            event.getInvestigationId(), event.getRestitutionAmount());
        
        investigationService.initiateRestitution(
            event.getInvestigationId(),
            event.getRestitutionAmount(),
            event.getRestitutionMethod()
        );
        
        metricsService.recordRestitutionInitiated(event.getRestitutionAmount());
    }
    
    private void handleInvestigationEventError(FraudInvestigationEvent event, Exception error, 
            String correlationId) {
        kafkaTemplate.send("fraud-investigation-events-dlq", Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.incrementInvestigationEventError(event.getEventType());
    }
}