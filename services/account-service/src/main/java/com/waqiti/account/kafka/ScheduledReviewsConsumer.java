package com.waqiti.account.kafka;

import com.waqiti.common.events.ScheduledReviewEvent;
import com.waqiti.account.domain.ScheduledReview;
import com.waqiti.account.repository.ScheduledReviewRepository;
import com.waqiti.account.service.ReviewManagementService;
import com.waqiti.account.service.ReviewAssignmentService;
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
public class ScheduledReviewsConsumer {
    
    private final ScheduledReviewRepository reviewRepository;
    private final ReviewManagementService reviewService;
    private final ReviewAssignmentService assignmentService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"scheduled-reviews", "periodic-account-reviews", "compliance-reviews"},
        groupId = "scheduled-reviews-service-group",
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
    public void handleScheduledReview(
            @Payload ScheduledReviewEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("review-%s-p%d-o%d", 
            event.getReviewId(), partition, offset);
        
        log.info("Processing scheduled review: id={}, accountId={}, type={}, dueDate={}",
            event.getReviewId(), event.getAccountId(), event.getReviewType(), event.getDueDate());
        
        try {
            switch (event.getStatus()) {
                case "REVIEW_SCHEDULED":
                    scheduleReview(event, correlationId);
                    break;
                    
                case "REVIEW_DUE":
                    markReviewDue(event, correlationId);
                    break;
                    
                case "REVIEW_STARTED":
                    startReview(event, correlationId);
                    break;
                    
                case "REVIEW_COMPLETED":
                    completeReview(event, correlationId);
                    break;
                    
                case "REVIEW_ESCALATED":
                    escalateReview(event, correlationId);
                    break;
                    
                case "REVIEW_CANCELLED":
                    cancelReview(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown review status: {}", event.getStatus());
                    break;
            }
            
            auditService.logAccountEvent("SCHEDULED_REVIEW_PROCESSED", event.getAccountId(),
                Map.of("reviewId", event.getReviewId(), "reviewType", event.getReviewType(),
                    "status", event.getStatus(), "correlationId", correlationId,
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process scheduled review: {}", e.getMessage(), e);
            kafkaTemplate.send("scheduled-reviews-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void scheduleReview(ScheduledReviewEvent event, String correlationId) {
        ScheduledReview review = ScheduledReview.builder()
            .reviewId(event.getReviewId())
            .accountId(event.getAccountId())
            .reviewType(event.getReviewType())
            .priority(event.getPriority())
            .scheduledDate(event.getScheduledDate())
            .dueDate(event.getDueDate())
            .status("REVIEW_SCHEDULED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        reviewRepository.save(review);
        
        assignmentService.assignReviewer(event.getReviewId(), event.getReviewType());
        
        metricsService.recordReviewScheduled(event.getReviewType());
        
        log.info("Review scheduled: id={}, accountId={}, dueDate={}", 
            event.getReviewId(), event.getAccountId(), event.getDueDate());
    }
    
    private void markReviewDue(ScheduledReviewEvent event, String correlationId) {
        ScheduledReview review = reviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Review not found"));
        
        review.setStatus("REVIEW_DUE");
        review.setDueNotifiedAt(LocalDateTime.now());
        reviewRepository.save(review);
        
        notificationService.sendNotification(review.getAssignedTo(), "Review Due",
            String.format("Review %s is now due for account %s", event.getReviewId(), event.getAccountId()),
            correlationId);
        
        metricsService.recordReviewDue(event.getReviewType());
        
        log.info("Review marked as due: id={}", event.getReviewId());
    }
    
    private void startReview(ScheduledReviewEvent event, String correlationId) {
        ScheduledReview review = reviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Review not found"));
        
        review.setStatus("REVIEW_STARTED");
        review.setStartedAt(LocalDateTime.now());
        reviewRepository.save(review);
        
        reviewService.gatherReviewData(event.getReviewId(), event.getAccountId());
        
        metricsService.recordReviewStarted(event.getReviewType());
        
        log.info("Review started: id={}, accountId={}", event.getReviewId(), event.getAccountId());
    }
    
    private void completeReview(ScheduledReviewEvent event, String correlationId) {
        ScheduledReview review = reviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Review not found"));
        
        review.setStatus("REVIEW_COMPLETED");
        review.setCompletedAt(LocalDateTime.now());
        review.setOutcome(event.getOutcome());
        review.setFindings(event.getFindings());
        reviewRepository.save(review);
        
        reviewService.applyReviewOutcome(event.getAccountId(), event.getOutcome());
        
        if ("PASS".equals(event.getOutcome())) {
            reviewService.scheduleNextReview(event.getAccountId(), event.getReviewType());
        } else if ("FAIL".equals(event.getOutcome())) {
            kafkaTemplate.send("account-status-changes", Map.of(
                "accountId", event.getAccountId(),
                "status", "UNDER_REVIEW",
                "reason", "FAILED_PERIODIC_REVIEW",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordReviewCompleted(event.getReviewType(), event.getOutcome());
        
        log.info("Review completed: id={}, outcome={}", event.getReviewId(), event.getOutcome());
    }
    
    private void escalateReview(ScheduledReviewEvent event, String correlationId) {
        ScheduledReview review = reviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Review not found"));
        
        review.setStatus("REVIEW_ESCALATED");
        review.setEscalatedAt(LocalDateTime.now());
        review.setEscalationReason(event.getReason());
        reviewRepository.save(review);
        
        notificationService.sendNotification("REVIEW_MANAGER", "Review Escalated",
            String.format("Review %s has been escalated: %s", event.getReviewId(), event.getReason()),
            correlationId);
        
        metricsService.recordReviewEscalated(event.getReviewType());
        
        log.warn("Review escalated: id={}, reason={}", event.getReviewId(), event.getReason());
    }
    
    private void cancelReview(ScheduledReviewEvent event, String correlationId) {
        ScheduledReview review = reviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Review not found"));
        
        review.setStatus("REVIEW_CANCELLED");
        review.setCancelledAt(LocalDateTime.now());
        review.setCancellationReason(event.getReason());
        reviewRepository.save(review);
        
        metricsService.recordReviewCancelled(event.getReviewType());
        
        log.info("Review cancelled: id={}, reason={}", event.getReviewId(), event.getReason());
    }
}