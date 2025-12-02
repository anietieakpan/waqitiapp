package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.FraudInvestigationEvent;
import com.waqiti.frauddetection.domain.FraudInvestigation;
import com.waqiti.frauddetection.repository.FraudInvestigationRepository;
import com.waqiti.frauddetection.service.InvestigationService;
import com.waqiti.frauddetection.service.EvidenceCollectionService;
import com.waqiti.frauddetection.service.FraudAnalyticsService;
import com.waqiti.frauddetection.metrics.FraudMetricsService;
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
public class FraudInvestigationsConsumer {
    
    private final FraudInvestigationRepository investigationRepository;
    private final InvestigationService investigationService;
    private final EvidenceCollectionService evidenceService;
    private final FraudAnalyticsService analyticsService;
    private final FraudMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final long MAX_INVESTIGATION_DAYS = 30;
    
    @KafkaListener(
        topics = {"fraud-investigations", "fraud-case-management", "fraud-review-queue"},
        groupId = "fraud-investigations-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleFraudInvestigation(
            @Payload FraudInvestigationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("fraud-inv-%s-p%d-o%d", 
            event.getInvestigationId(), partition, offset);
        
        log.info("Processing fraud investigation: id={}, accountId={}, status={}, fraudScore={}",
            event.getInvestigationId(), event.getAccountId(), event.getStatus(), event.getFraudScore());
        
        try {
            switch (event.getStatus()) {
                case "INVESTIGATION_OPENED":
                    openInvestigation(event, correlationId);
                    break;
                    
                case "EVIDENCE_COLLECTION":
                    collectEvidence(event, correlationId);
                    break;
                    
                case "ANALYSIS_IN_PROGRESS":
                    analyzeEvidence(event, correlationId);
                    break;
                    
                case "PRELIMINARY_FINDINGS":
                    documentFindings(event, correlationId);
                    break;
                    
                case "CUSTOMER_CONTACTED":
                    contactCustomer(event, correlationId);
                    break;
                    
                case "CONFIRMED_FRAUD":
                    confirmFraud(event, correlationId);
                    break;
                    
                case "NO_FRAUD_DETECTED":
                    clearFraud(event, correlationId);
                    break;
                    
                case "INVESTIGATION_CLOSED":
                    closeInvestigation(event, correlationId);
                    break;
                    
                case "ESCALATED_TO_LAW_ENFORCEMENT":
                    escalateToLawEnforcement(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown investigation status: {}", event.getStatus());
                    break;
            }
            
            auditService.logFraudEvent("FRAUD_INVESTIGATION_PROCESSED", event.getInvestigationId(),
                Map.of("accountId", event.getAccountId(), "status", event.getStatus(),
                    "fraudScore", event.getFraudScore(), "correlationId", correlationId,
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process fraud investigation: {}", e.getMessage(), e);
            kafkaTemplate.send("fraud-investigations-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void openInvestigation(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = FraudInvestigation.builder()
            .investigationId(event.getInvestigationId())
            .accountId(event.getAccountId())
            .fraudScore(event.getFraudScore())
            .fraudType(event.getFraudType())
            .status("INVESTIGATION_OPENED")
            .priority(calculatePriority(event.getFraudScore()))
            .openedAt(LocalDateTime.now())
            .dueDate(LocalDateTime.now().plusDays(MAX_INVESTIGATION_DAYS))
            .correlationId(correlationId)
            .build();
        investigationRepository.save(investigation);
        
        investigationService.assignInvestigator(event.getInvestigationId(), event.getFraudType());
        
        if (event.getFraudScore() > 80) {
            investigationService.freezeAccount(event.getAccountId());
            
            notificationService.sendNotification(event.getAccountId(), "Account Security Review",
                "Your account is under security review. Transactions may be temporarily restricted.",
                correlationId);
        }
        
        metricsService.recordInvestigationOpened(event.getFraudType(), event.getFraudScore());
        
        log.warn("Fraud investigation opened: id={}, accountId={}, fraudScore={}", 
            event.getInvestigationId(), event.getAccountId(), event.getFraudScore());
    }
    
    private void collectEvidence(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("EVIDENCE_COLLECTION");
        investigation.setEvidenceCollectionStartedAt(LocalDateTime.now());
        investigationRepository.save(investigation);
        
        Map<String, Object> evidence = evidenceService.collectTransactionEvidence(event.getAccountId());
        evidenceService.collectBehaviorEvidence(event.getAccountId());
        evidenceService.collectDeviceEvidence(event.getAccountId());
        
        investigation.setEvidenceCount(evidence.size());
        investigationRepository.save(investigation);
        
        metricsService.recordEvidenceCollected(event.getFraudType(), evidence.size());
        
        log.info("Evidence collection started: investigationId={}, evidenceCount={}", 
            event.getInvestigationId(), evidence.size());
    }
    
    private void analyzeEvidence(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("ANALYSIS_IN_PROGRESS");
        investigation.setAnalysisStartedAt(LocalDateTime.now());
        investigationRepository.save(investigation);
        
        Map<String, Object> analysisResult = analyticsService.performFraudAnalysis(
            event.getInvestigationId(),
            event.getAccountId()
        );
        
        int updatedFraudScore = (int) analysisResult.get("fraudScore");
        investigation.setFraudScore(updatedFraudScore);
        investigation.setRiskIndicators((List<String>) analysisResult.get("riskIndicators"));
        investigationRepository.save(investigation);
        
        metricsService.recordAnalysisCompleted(event.getFraudType(), updatedFraudScore);
        
        log.info("Evidence analysis completed: investigationId={}, updatedScore={}", 
            event.getInvestigationId(), updatedFraudScore);
    }
    
    private void documentFindings(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("PRELIMINARY_FINDINGS");
        investigation.setFindings(event.getFindings());
        investigation.setFindingsDocumentedAt(LocalDateTime.now());
        investigationRepository.save(investigation);
        
        if (investigation.getFraudScore() > 70) {
            notificationService.sendNotification("FRAUD_TEAM", "High-Confidence Fraud Detected",
                String.format("Investigation %s shows high probability of fraud (Score: %d)", 
                    event.getInvestigationId(), investigation.getFraudScore()),
                correlationId);
        }
        
        metricsService.recordFindingsDocumented(event.getFraudType());
        
        log.info("Preliminary findings documented: investigationId={}", event.getInvestigationId());
    }
    
    private void contactCustomer(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("CUSTOMER_CONTACTED");
        investigation.setCustomerContactedAt(LocalDateTime.now());
        investigationRepository.save(investigation);
        
        notificationService.sendNotification(event.getAccountId(), "Fraud Investigation Contact Required",
            "We need to verify recent activity on your account. Please contact us within 48 hours.",
            correlationId);
        
        metricsService.recordCustomerContacted(event.getFraudType());
        
        log.info("Customer contacted for investigation: investigationId={}, accountId={}", 
            event.getInvestigationId(), event.getAccountId());
    }
    
    private void confirmFraud(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("CONFIRMED_FRAUD");
        investigation.setConfirmedAt(LocalDateTime.now());
        investigation.setConfirmedBy(event.getInvestigator());
        investigationRepository.save(investigation);
        
        investigationService.permanentlyFreezeAccount(event.getAccountId());
        investigationService.blockFraudulentTransactions(event.getAccountId());
        investigationService.flagRelatedAccounts(event.getAccountId());
        
        kafkaTemplate.send("sar-filing-queue", Map.of(
            "investigationId", event.getInvestigationId(),
            "accountId", event.getAccountId(),
            "fraudType", event.getFraudType(),
            "fraudScore", investigation.getFraudScore(),
            "amount", event.getAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification(event.getAccountId(), "Account Suspended - Fraud Confirmed",
            "Your account has been suspended due to confirmed fraudulent activity. Our team will contact you.",
            correlationId);
        
        notificationService.sendNotification("FRAUD_TEAM", "Fraud Confirmed",
            String.format("Investigation %s confirmed fraud. SAR filing initiated.", event.getInvestigationId()),
            correlationId);
        
        metricsService.recordFraudConfirmed(event.getFraudType(), event.getAmount());
        
        log.error("Fraud confirmed: investigationId={}, accountId={}, amount={}", 
            event.getInvestigationId(), event.getAccountId(), event.getAmount());
    }
    
    private void clearFraud(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("NO_FRAUD_DETECTED");
        investigation.setClearedAt(LocalDateTime.now());
        investigationRepository.save(investigation);
        
        investigationService.unfreezeAccount(event.getAccountId());
        investigationService.restoreAccountAccess(event.getAccountId());
        
        notificationService.sendNotification(event.getAccountId(), "Account Review Complete",
            "Our security review is complete. No fraudulent activity was detected. Your account access has been restored.",
            correlationId);
        
        metricsService.recordFraudCleared(event.getFraudType());
        
        log.info("Fraud investigation cleared: investigationId={}, accountId={}", 
            event.getInvestigationId(), event.getAccountId());
    }
    
    private void closeInvestigation(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("INVESTIGATION_CLOSED");
        investigation.setClosedAt(LocalDateTime.now());
        investigation.setClosureReason(event.getClosureReason());
        investigationRepository.save(investigation);
        
        investigationService.archiveInvestigation(event.getInvestigationId());
        
        metricsService.recordInvestigationClosed(event.getFraudType());
        
        log.info("Investigation closed: id={}, reason={}", 
            event.getInvestigationId(), event.getClosureReason());
    }
    
    private void escalateToLawEnforcement(FraudInvestigationEvent event, String correlationId) {
        FraudInvestigation investigation = investigationRepository.findByInvestigationId(event.getInvestigationId())
            .orElseThrow(() -> new RuntimeException("Investigation not found"));
        
        investigation.setStatus("ESCALATED_TO_LAW_ENFORCEMENT");
        investigation.setLawEnforcementEscalatedAt(LocalDateTime.now());
        investigationRepository.save(investigation);
        
        notificationService.sendNotification("LEGAL_TEAM", "Law Enforcement Escalation Required",
            String.format("Investigation %s requires law enforcement involvement. Fraud amount: %s", 
                event.getInvestigationId(), event.getAmount()),
            correlationId);
        
        metricsService.recordLawEnforcementEscalation(event.getFraudType(), event.getAmount());
        
        log.error("Investigation escalated to law enforcement: id={}, amount={}", 
            event.getInvestigationId(), event.getAmount());
    }
    
    private String calculatePriority(int fraudScore) {
        if (fraudScore >= 90) return "CRITICAL";
        if (fraudScore >= 70) return "HIGH";
        if (fraudScore >= 50) return "MEDIUM";
        return "LOW";
    }
}