package com.waqiti.account.kafka;

import com.waqiti.common.events.ReviewDeadlineEvent;
import com.waqiti.account.domain.ReviewDeadline;
import com.waqiti.account.repository.ReviewDeadlineRepository;
import com.waqiti.account.service.DeadlineManagementService;
import com.waqiti.account.service.EscalationService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewDeadlinesConsumer {
    
    private final ReviewDeadlineRepository deadlineRepository;
    private final DeadlineManagementService deadlineService;
    private final EscalationService escalationService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"review-deadlines", "deadline-alerts", "overdue-reviews"},
        groupId = "review-deadlines-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleReviewDeadline(
            @Payload ReviewDeadlineEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("deadline-%s-p%d-o%d", 
            event.getReviewId(), partition, offset);
        
        log.info("Processing review deadline: reviewId={}, status={}, dueDate={}",
            event.getReviewId(), event.getStatus(), event.getDueDate());
        
        try {
            switch (event.getStatus()) {
                case "APPROACHING_DEADLINE":
                    handleApproachingDeadline(event, correlationId);
                    break;
                    
                case "DEADLINE_REACHED":
                    handleDeadlineReached(event, correlationId);
                    break;
                    
                case "OVERDUE":
                    handleOverdue(event, correlationId);
                    break;
                    
                case "CRITICALLY_OVERDUE":
                    handleCriticallyOverdue(event, correlationId);
                    break;
                    
                case "DEADLINE_EXTENDED":
                    handleDeadlineExtension(event, correlationId);
                    break;
                    
                case "DEADLINE_MET":
                    handleDeadlineMet(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown deadline status: {}", event.getStatus());
                    break;
            }
            
            auditService.logAccountEvent("REVIEW_DEADLINE_PROCESSED", event.getReviewId(),
                Map.of("status", event.getStatus(), "dueDate", event.getDueDate(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process review deadline: {}", e.getMessage(), e);
            kafkaTemplate.send("review-deadlines-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void handleApproachingDeadline(ReviewDeadlineEvent event, String correlationId) {
        ReviewDeadline deadline = ReviewDeadline.builder()
            .reviewId(event.getReviewId())
            .dueDate(event.getDueDate())
            .status("APPROACHING_DEADLINE")
            .alertedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        deadlineRepository.save(deadline);
        
        Duration timeRemaining = Duration.between(LocalDateTime.now(), event.getDueDate());
        
        notificationService.sendNotification(event.getAssignedTo(), "Review Deadline Approaching",
            String.format("Review %s is due in %d hours. Please prioritize completion.", 
                event.getReviewId(), timeRemaining.toHours()),
            correlationId);
        
        metricsService.recordDeadlineApproaching(event.getReviewType());
        
        log.info("Deadline approaching alert sent: reviewId={}, hoursRemaining={}", 
            event.getReviewId(), timeRemaining.toHours());
    }
    
    private void handleDeadlineReached(ReviewDeadlineEvent event, String correlationId) {
        ReviewDeadline deadline = deadlineRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Deadline not found"));
        
        deadline.setStatus("DEADLINE_REACHED");
        deadline.setDeadlineReachedAt(LocalDateTime.now());
        deadlineRepository.save(deadline);
        
        notificationService.sendNotification(event.getAssignedTo(), "URGENT: Review Deadline Reached",
            String.format("Review %s deadline has been reached. Immediate action required.", event.getReviewId()),
            correlationId);
        
        notificationService.sendNotification(event.getManagerId(), "Review Deadline Reached",
            String.format("Review %s assigned to %s has reached deadline", event.getReviewId(), event.getAssignedTo()),
            correlationId);
        
        metricsService.recordDeadlineReached(event.getReviewType());
        
        log.warn("Deadline reached: reviewId={}, assignedTo={}", event.getReviewId(), event.getAssignedTo());
    }
    
    private void handleOverdue(ReviewDeadlineEvent event, String correlationId) {
        ReviewDeadline deadline = deadlineRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Deadline not found"));
        
        deadline.setStatus("OVERDUE");
        deadline.setOverdueAt(LocalDateTime.now());
        deadlineRepository.save(deadline);
        
        Duration overdueBy = Duration.between(event.getDueDate(), LocalDateTime.now());
        
        notificationService.sendNotification(event.getAssignedTo(), "CRITICAL: Review Overdue",
            String.format("Review %s is now %d hours overdue. Complete immediately.", 
                event.getReviewId(), overdueBy.toHours()),
            correlationId);
        
        notificationService.sendNotification(event.getManagerId(), "Review Overdue - Action Required",
            String.format("Review %s is overdue by %d hours. Intervention may be needed.", 
                event.getReviewId(), overdueBy.toHours()),
            correlationId);
        
        escalationService.flagOverdueReview(event.getReviewId(), overdueBy.toHours());
        
        metricsService.recordReviewOverdue(event.getReviewType(), overdueBy.toHours());
        
        log.error("Review overdue: reviewId={}, overdueHours={}", event.getReviewId(), overdueBy.toHours());
    }
    
    private void handleCriticallyOverdue(ReviewDeadlineEvent event, String correlationId) {
        ReviewDeadline deadline = deadlineRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Deadline not found"));
        
        deadline.setStatus("CRITICALLY_OVERDUE");
        deadline.setCriticalOverdueAt(LocalDateTime.now());
        deadlineRepository.save(deadline);
        
        Duration overdueBy = Duration.between(event.getDueDate(), LocalDateTime.now());
        
        kafkaTemplate.send("review-escalations", Map.of(
            "reviewId", event.getReviewId(),
            "escalationType", "CRITICAL_OVERDUE",
            "overdueHours", overdueBy.toHours(),
            "assignedTo", event.getAssignedTo(),
            "managerId", event.getManagerId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification("REVIEW_DIRECTOR", "CRITICAL: Review Critically Overdue",
            String.format("Review %s is critically overdue (%d hours). Immediate executive action required.", 
                event.getReviewId(), overdueBy.toHours()),
            correlationId);
        
        escalationService.escalateToCritical(event.getReviewId(), event.getManagerId());
        
        deadlineService.initiateReassignment(event.getReviewId());
        
        metricsService.recordCriticallyOverdue(event.getReviewType(), overdueBy.toHours());
        
        log.error("CRITICAL: Review critically overdue: reviewId={}, overdueHours={}", 
            event.getReviewId(), overdueBy.toHours());
    }
    
    private void handleDeadlineExtension(ReviewDeadlineEvent event, String correlationId) {
        ReviewDeadline deadline = deadlineRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Deadline not found"));
        
        LocalDateTime oldDeadline = deadline.getDueDate();
        deadline.setDueDate(event.getNewDueDate());
        deadline.setExtensionReason(event.getReason());
        deadline.setExtendedAt(LocalDateTime.now());
        deadline.setExtendedBy(event.getExtendedBy());
        deadlineRepository.save(deadline);
        
        Duration extension = Duration.between(oldDeadline, event.getNewDueDate());
        
        notificationService.sendNotification(event.getAssignedTo(), "Review Deadline Extended",
            String.format("Review %s deadline extended by %d hours. New deadline: %s. Reason: %s", 
                event.getReviewId(), extension.toHours(), event.getNewDueDate(), event.getReason()),
            correlationId);
        
        metricsService.recordDeadlineExtended(event.getReviewType(), extension.toHours());
        
        log.info("Deadline extended: reviewId={}, extensionHours={}, reason={}", 
            event.getReviewId(), extension.toHours(), event.getReason());
    }
    
    private void handleDeadlineMet(ReviewDeadlineEvent event, String correlationId) {
        ReviewDeadline deadline = deadlineRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Deadline not found"));
        
        deadline.setStatus("DEADLINE_MET");
        deadline.setCompletedAt(LocalDateTime.now());
        deadlineRepository.save(deadline);
        
        Duration completionTime = Duration.between(deadline.getAlertedAt(), LocalDateTime.now());
        
        metricsService.recordDeadlineMet(event.getReviewType(), completionTime.toHours());
        
        log.info("Deadline met: reviewId={}, completionTimeHours={}", 
            event.getReviewId(), completionTime.toHours());
    }
}