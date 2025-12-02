package com.waqiti.account.kafka;

import com.waqiti.common.events.ReviewCaseEvent;
import com.waqiti.account.domain.ReviewCase;
import com.waqiti.account.repository.ReviewCaseRepository;
import com.waqiti.account.service.CaseReviewService;
import com.waqiti.account.service.CaseWorkflowService;
import com.waqiti.account.metrics.AccountMetricsService;
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
public class ReviewCasesConsumer {
    
    private final ReviewCaseRepository caseRepository;
    private final CaseReviewService reviewService;
    private final CaseWorkflowService workflowService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"review-cases", "account-review-cases", "manual-review-cases"},
        groupId = "review-cases-service-group",
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
    public void handleReviewCase(
            @Payload ReviewCaseEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("case-%s-p%d-o%d", 
            event.getCaseId(), partition, offset);
        
        log.info("Processing review case: id={}, accountId={}, caseType={}, priority={}",
            event.getCaseId(), event.getAccountId(), event.getCaseType(), event.getPriority());
        
        try {
            switch (event.getStatus()) {
                case "CASE_OPENED":
                    openCase(event, correlationId);
                    break;
                    
                case "ASSIGNED_TO_REVIEWER":
                    assignReviewer(event, correlationId);
                    break;
                    
                case "UNDER_REVIEW":
                    processReview(event, correlationId);
                    break;
                    
                case "ADDITIONAL_INFO_REQUESTED":
                    requestAdditionalInfo(event, correlationId);
                    break;
                    
                case "APPROVED":
                    approveCase(event, correlationId);
                    break;
                    
                case "REJECTED":
                    rejectCase(event, correlationId);
                    break;
                    
                case "CLOSED":
                    closeCase(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown case status: {}", event.getStatus());
                    break;
            }
            
            auditService.logAccountEvent("REVIEW_CASE_PROCESSED", event.getAccountId(),
                Map.of("caseId", event.getCaseId(), "caseType", event.getCaseType(),
                    "status", event.getStatus(), "correlationId", correlationId,
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process review case: {}", e.getMessage(), e);
            kafkaTemplate.send("review-cases-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void openCase(ReviewCaseEvent event, String correlationId) {
        ReviewCase reviewCase = ReviewCase.builder()
            .caseId(event.getCaseId())
            .accountId(event.getAccountId())
            .caseType(event.getCaseType())
            .priority(event.getPriority())
            .description(event.getDescription())
            .status("CASE_OPENED")
            .openedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        caseRepository.save(reviewCase);
        
        workflowService.initiateCaseWorkflow(event.getCaseId(), event.getCaseType());
        
        metricsService.recordCaseOpened(event.getCaseType(), event.getPriority());
        
        log.info("Review case opened: id={}, accountId={}, type={}", 
            event.getCaseId(), event.getAccountId(), event.getCaseType());
    }
    
    private void assignReviewer(ReviewCaseEvent event, String correlationId) {
        ReviewCase reviewCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Review case not found"));
        
        reviewCase.setStatus("ASSIGNED_TO_REVIEWER");
        reviewCase.setAssignedTo(event.getAssignedTo());
        reviewCase.setAssignedAt(LocalDateTime.now());
        caseRepository.save(reviewCase);
        
        notificationService.sendNotification(event.getAssignedTo(), "Review Case Assigned",
            String.format("You have been assigned case %s: %s", event.getCaseId(), event.getCaseType()),
            correlationId);
        
        metricsService.recordCaseAssigned(event.getCaseType());
        
        log.info("Case assigned: id={}, assignedTo={}", event.getCaseId(), event.getAssignedTo());
    }
    
    private void processReview(ReviewCaseEvent event, String correlationId) {
        ReviewCase reviewCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Review case not found"));
        
        reviewCase.setStatus("UNDER_REVIEW");
        reviewCase.setReviewStartedAt(LocalDateTime.now());
        caseRepository.save(reviewCase);
        
        reviewService.gatherCaseEvidence(event.getCaseId(), event.getAccountId());
        
        metricsService.recordCaseUnderReview(event.getCaseType());
        
        log.info("Case under review: id={}", event.getCaseId());
    }
    
    private void requestAdditionalInfo(ReviewCaseEvent event, String correlationId) {
        ReviewCase reviewCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Review case not found"));
        
        reviewCase.setStatus("ADDITIONAL_INFO_REQUESTED");
        reviewCase.setInfoRequestedAt(LocalDateTime.now());
        reviewCase.setInfoRequested(event.getInfoRequested());
        caseRepository.save(reviewCase);
        
        notificationService.sendNotification(event.getAccountId(), "Additional Information Required",
            String.format("We need additional information for case %s. Please provide: %s", 
                event.getCaseId(), event.getInfoRequested()),
            correlationId);
        
        metricsService.recordAdditionalInfoRequested(event.getCaseType());
        
        log.info("Additional info requested: caseId={}, info={}", 
            event.getCaseId(), event.getInfoRequested());
    }
    
    private void approveCase(ReviewCaseEvent event, String correlationId) {
        ReviewCase reviewCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Review case not found"));
        
        reviewCase.setStatus("APPROVED");
        reviewCase.setApprovedAt(LocalDateTime.now());
        reviewCase.setApprovedBy(event.getReviewedBy());
        reviewCase.setDecisionNotes(event.getDecisionNotes());
        caseRepository.save(reviewCase);
        
        workflowService.applyApprovalActions(event.getCaseId(), event.getAccountId());
        
        notificationService.sendNotification(event.getAccountId(), "Case Approved",
            String.format("Your case %s has been approved", event.getCaseId()),
            correlationId);
        
        metricsService.recordCaseApproved(event.getCaseType());
        
        log.info("Case approved: id={}, approvedBy={}", event.getCaseId(), event.getReviewedBy());
    }
    
    private void rejectCase(ReviewCaseEvent event, String correlationId) {
        ReviewCase reviewCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Review case not found"));
        
        reviewCase.setStatus("REJECTED");
        reviewCase.setRejectedAt(LocalDateTime.now());
        reviewCase.setRejectedBy(event.getReviewedBy());
        reviewCase.setRejectionReason(event.getRejectionReason());
        caseRepository.save(reviewCase);
        
        workflowService.applyRejectionActions(event.getCaseId(), event.getAccountId());
        
        notificationService.sendNotification(event.getAccountId(), "Case Rejected",
            String.format("Your case %s has been rejected: %s", event.getCaseId(), event.getRejectionReason()),
            correlationId);
        
        metricsService.recordCaseRejected(event.getCaseType(), event.getRejectionReason());
        
        log.warn("Case rejected: id={}, reason={}", event.getCaseId(), event.getRejectionReason());
    }
    
    private void closeCase(ReviewCaseEvent event, String correlationId) {
        ReviewCase reviewCase = caseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Review case not found"));
        
        reviewCase.setStatus("CLOSED");
        reviewCase.setClosedAt(LocalDateTime.now());
        reviewCase.setClosureNotes(event.getClosureNotes());
        caseRepository.save(reviewCase);
        
        workflowService.archiveCase(event.getCaseId());
        
        metricsService.recordCaseClosed(event.getCaseType());
        
        log.info("Case closed: id={}", event.getCaseId());
    }
}