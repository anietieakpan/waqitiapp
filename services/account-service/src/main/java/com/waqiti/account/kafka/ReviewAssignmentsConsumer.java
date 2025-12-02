package com.waqiti.account.kafka;

import com.waqiti.common.events.ReviewAssignmentEvent;
import com.waqiti.account.domain.ReviewAssignment;
import com.waqiti.account.repository.ReviewAssignmentRepository;
import com.waqiti.account.service.ReviewerWorkloadService;
import com.waqiti.account.service.ReviewRoutingService;
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
public class ReviewAssignmentsConsumer {
    
    private final ReviewAssignmentRepository assignmentRepository;
    private final ReviewerWorkloadService workloadService;
    private final ReviewRoutingService routingService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"review-assignments", "reviewer-workload-events"},
        groupId = "review-assignments-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleReviewAssignment(
            @Payload ReviewAssignmentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("assignment-%s-p%d-o%d", 
            event.getAssignmentId(), partition, offset);
        
        log.info("Processing review assignment: id={}, reviewerId={}, caseId={}",
            event.getAssignmentId(), event.getReviewerId(), event.getCaseId());
        
        try {
            ReviewAssignment assignment = ReviewAssignment.builder()
                .assignmentId(event.getAssignmentId())
                .caseId(event.getCaseId())
                .reviewerId(event.getReviewerId())
                .reviewType(event.getReviewType())
                .priority(event.getPriority())
                .assignedAt(LocalDateTime.now())
                .dueDate(event.getDueDate())
                .status("ASSIGNED")
                .correlationId(correlationId)
                .build();
            assignmentRepository.save(assignment);
            
            workloadService.updateReviewerWorkload(event.getReviewerId(), 1);
            
            notificationService.sendNotification(event.getReviewerId(), "New Review Assignment",
                String.format("You have been assigned %s review for case %s. Due: %s", 
                    event.getReviewType(), event.getCaseId(), event.getDueDate()),
                correlationId);
            
            if ("HIGH".equals(event.getPriority()) || "CRITICAL".equals(event.getPriority())) {
                notificationService.sendNotification(event.getReviewerId(), "URGENT Review Assignment",
                    String.format("PRIORITY %s: Case %s requires immediate attention", 
                        event.getPriority(), event.getCaseId()),
                    correlationId);
            }
            
            metricsService.recordReviewAssigned(event.getReviewType(), event.getReviewerId());
            
            auditService.logAccountEvent("REVIEW_ASSIGNED", event.getCaseId(),
                Map.of("assignmentId", event.getAssignmentId(), "reviewerId", event.getReviewerId(),
                    "reviewType", event.getReviewType(), "priority", event.getPriority(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
            log.info("Review assignment processed: id={}, reviewerId={}, caseId={}, priority={}",
                event.getAssignmentId(), event.getReviewerId(), event.getCaseId(), event.getPriority());
            
        } catch (Exception e) {
            log.error("Failed to process review assignment: {}", e.getMessage(), e);
            kafkaTemplate.send("review-assignments-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
}