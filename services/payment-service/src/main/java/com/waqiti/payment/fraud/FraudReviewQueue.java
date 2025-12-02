package com.waqiti.payment.fraud;

import com.waqiti.payment.fraud.model.FraudReviewCase;
import com.waqiti.payment.fraud.model.FraudReviewStatus;
import com.waqiti.payment.fraud.model.FraudReviewDecision;
import com.waqiti.payment.fraud.repository.FraudReviewCaseRepository;
import com.waqiti.payment.service.PaymentProcessingService;
import com.waqiti.alerting.service.PagerDutyAlertService;
import com.waqiti.alerting.service.SlackAlertService;
import com.waqiti.common.notification.NotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Enterprise Fraud Review Queue System
 *
 * Manages manual fraud review workflow for medium-high risk transactions
 * that require human analyst intervention.
 *
 * Features:
 * - Priority-based queue management (0=Critical, 1=High, 2=Medium, 3=Low)
 * - SLA tracking (2-4 hour turnaround for high priority)
 * - Auto-escalation for overdue cases
 * - Real-time queue metrics and monitoring
 * - Case assignment to fraud analysts
 * - Decision audit trail for compliance
 * - Automatic notifications to users
 * - Redis-backed queue for high performance
 *
 * SLA Targets:
 * - Priority 0 (Critical): 30 min - 1 hour
 * - Priority 1 (High): 1-2 hours
 * - Priority 2 (Medium): 2-4 hours
 * - Priority 3 (Low): 4-8 hours
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 * @since 2025-10-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudReviewQueue {

    private final FraudReviewCaseRepository fraudReviewCaseRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PagerDutyAlertService pagerDutyAlertService;
    private final SlackAlertService slackAlertService;
    private final NotificationService notificationService;
    private final PaymentProcessingService paymentProcessingService;
    private final MeterRegistry meterRegistry;

    // In-memory queue metrics (for real-time monitoring)
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final Map<Integer, AtomicInteger> queueSizeByPriority = new ConcurrentHashMap<>();
    private final AtomicInteger overdueCount = new AtomicInteger(0);

    // Metrics
    private final Counter casesQueued;
    private final Counter casesApproved;
    private final Counter casesRejected;
    private final Counter casesEscalated;
    private final Counter slaViolations;

    public FraudReviewQueue(FraudReviewCaseRepository fraudReviewCaseRepository,
                           RedisTemplate<String, Object> redisTemplate,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.fraudReviewCaseRepository = fraudReviewCaseRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.casesQueued = Counter.builder("fraud.review.cases.queued")
            .description("Total fraud review cases queued")
            .register(meterRegistry);

        this.casesApproved = Counter.builder("fraud.review.cases.approved")
            .description("Total fraud review cases approved")
            .register(meterRegistry);

        this.casesRejected = Counter.builder("fraud.review.cases.rejected")
            .description("Total fraud review cases rejected")
            .register(meterRegistry);

        this.casesEscalated = Counter.builder("fraud.review.cases.escalated")
            .description("Total fraud review cases escalated")
            .register(meterRegistry);

        this.slaViolations = Counter.builder("fraud.review.sla.violations")
            .description("Total SLA violations in fraud review")
            .register(meterRegistry);

        // Initialize queue size gauges
        Gauge.builder("fraud.review.queue.size", queueSize, AtomicInteger::get)
            .description("Current fraud review queue size")
            .register(meterRegistry);

        Gauge.builder("fraud.review.queue.overdue", overdueCount, AtomicInteger::get)
            .description("Number of overdue fraud review cases")
            .register(meterRegistry);

        // Initialize priority-based queue size gauges
        for (int priority = 0; priority <= 3; priority++) {
            final int p = priority;
            queueSizeByPriority.put(p, new AtomicInteger(0));
            Gauge.builder("fraud.review.queue.size.priority." + p,
                    queueSizeByPriority.get(p), AtomicInteger::get)
                .description("Fraud review queue size for priority " + p)
                .register(meterRegistry);
        }

        // Initialize queue metrics on startup
        refreshQueueMetrics();
    }

    /**
     * Queue a transaction for manual fraud review
     *
     * @param reviewCase Fraud review case details
     * @return Created fraud review case with assigned review ID
     */
    @Transactional
    public FraudReviewCase queueForReview(FraudReviewCase reviewCase) {

        try {
            log.info("FRAUD REVIEW: Queueing case for review: paymentId={}, userId={}, priority={}, riskScore={}",
                reviewCase.getPaymentId(), reviewCase.getUserId(),
                reviewCase.getPriority(), reviewCase.getRiskScore());

            // Generate review ID
            String reviewId = generateReviewId();
            reviewCase.setReviewId(reviewId);

            // Set timestamps
            reviewCase.setQueuedAt(LocalDateTime.now());
            reviewCase.setStatus(FraudReviewStatus.PENDING);

            // Calculate SLA deadline
            LocalDateTime slaDeadline = calculateSLADeadline(reviewCase.getPriority());
            reviewCase.setSlaDeadline(slaDeadline);

            // Persist to database
            FraudReviewCase savedCase = fraudReviewCaseRepository.save(reviewCase);

            // Add to Redis queue (for fast retrieval)
            addToRedisQueue(savedCase);

            // Update metrics
            casesQueued.increment();
            queueSize.incrementAndGet();
            queueSizeByPriority.get(reviewCase.getPriority()).incrementAndGet();

            // Publish queue event
            publishQueueEvent(savedCase);

            // Send notification to fraud analysts
            notifyFraudAnalysts(savedCase);

            log.info("FRAUD REVIEW: Case queued successfully: reviewId={}, slaDeadline={}",
                reviewId, slaDeadline);

            return savedCase;

        } catch (Exception e) {
            log.error("FRAUD REVIEW: Failed to queue case: paymentId={}, userId={}",
                reviewCase.getPaymentId(), reviewCase.getUserId(), e);

            meterRegistry.counter("fraud.review.queue.errors").increment();
            throw new FraudReviewQueueException("Failed to queue fraud review case", e);
        }
    }

    /**
     * Get next case for review (priority-based)
     *
     * @param analystId Fraud analyst ID
     * @return Next case to review, or null if queue empty
     */
    @Transactional
    public FraudReviewCase getNextCase(String analystId) {

        try {
            log.debug("FRAUD REVIEW: Getting next case for analyst: {}", analystId);

            // Get highest priority pending case
            Optional<FraudReviewCase> nextCase = fraudReviewCaseRepository
                .findTopByStatusOrderByPriorityAscQueuedAtAsc(FraudReviewStatus.PENDING);

            if (nextCase.isEmpty()) {
                log.debug("FRAUD REVIEW: No pending cases in queue");
                return null;
            }

            FraudReviewCase reviewCase = nextCase.get();

            // Assign to analyst
            reviewCase.setStatus(FraudReviewStatus.IN_REVIEW);
            reviewCase.setAssignedAnalyst(analystId);
            reviewCase.setReviewStartedAt(LocalDateTime.now());

            fraudReviewCaseRepository.save(reviewCase);

            // Update Redis
            updateRedisQueue(reviewCase);

            // Update metrics
            queueSize.decrementAndGet();
            queueSizeByPriority.get(reviewCase.getPriority()).decrementAndGet();

            log.info("FRAUD REVIEW: Case assigned to analyst: reviewId={}, analystId={}, priority={}",
                reviewCase.getReviewId(), analystId, reviewCase.getPriority());

            return reviewCase;

        } catch (Exception e) {
            log.error("FRAUD REVIEW: Failed to get next case: analystId={}", analystId, e);
            throw new FraudReviewQueueException("Failed to get next review case", e);
        }
    }

    /**
     * Submit review decision
     *
     * @param reviewId Review case ID
     * @param decision Analyst decision (APPROVE/REJECT)
     * @param analystId Analyst making decision
     * @param notes Analyst notes
     * @return Updated fraud review case
     */
    @Transactional
    public FraudReviewCase submitDecision(String reviewId, FraudReviewDecision decision,
                                         String analystId, String notes) {

        try {
            log.info("FRAUD REVIEW: Submitting decision: reviewId={}, decision={}, analystId={}",
                reviewId, decision, analystId);

            // Get case
            FraudReviewCase reviewCase = fraudReviewCaseRepository.findByReviewId(reviewId)
                .orElseThrow(() -> new FraudReviewQueueException("Review case not found: " + reviewId));

            // Validate analyst
            if (!analystId.equals(reviewCase.getAssignedAnalyst())) {
                log.warn("FRAUD REVIEW: Analyst mismatch: reviewId={}, expected={}, actual={}",
                    reviewId, reviewCase.getAssignedAnalyst(), analystId);
                throw new FraudReviewQueueException("Analyst not assigned to this case");
            }

            // Update case
            reviewCase.setDecision(decision);
            reviewCase.setDecisionNotes(notes);
            reviewCase.setReviewCompletedAt(LocalDateTime.now());
            reviewCase.setStatus(FraudReviewStatus.COMPLETED);

            // Calculate review duration
            Duration reviewDuration = Duration.between(
                reviewCase.getReviewStartedAt(), reviewCase.getReviewCompletedAt());
            reviewCase.setReviewDurationMinutes(reviewDuration.toMinutes());

            // Check SLA compliance
            boolean slaViolation = LocalDateTime.now().isAfter(reviewCase.getSlaDeadline());
            reviewCase.setSlaViolation(slaViolation);

            if (slaViolation) {
                log.warn("FRAUD REVIEW: SLA violation: reviewId={}, deadline={}, completed={}",
                    reviewId, reviewCase.getSlaDeadline(), reviewCase.getReviewCompletedAt());
                slaViolations.increment();
            }

            // Save decision
            FraudReviewCase savedCase = fraudReviewCaseRepository.save(reviewCase);

            // Remove from Redis queue
            removeFromRedisQueue(reviewCase);

            // Update metrics
            if (decision == FraudReviewDecision.APPROVE) {
                casesApproved.increment();
            } else {
                casesRejected.increment();
            }

            // Track review duration
            meterRegistry.timer("fraud.review.duration",
                "decision", decision.name(),
                "priority", String.valueOf(reviewCase.getPriority()))
                .record(reviewDuration);

            // Publish decision event
            publishDecisionEvent(savedCase);

            // Notify user of decision
            notifyUser(savedCase);

            // Process payment based on decision
            processPaymentDecision(savedCase);

            log.info("FRAUD REVIEW: Decision submitted successfully: reviewId={}, decision={}, duration={}min, slaViolation={}",
                reviewId, decision, reviewCase.getReviewDurationMinutes(), slaViolation);

            return savedCase;

        } catch (Exception e) {
            log.error("FRAUD REVIEW: Failed to submit decision: reviewId={}, analystId={}",
                reviewId, analystId, e);
            throw new FraudReviewQueueException("Failed to submit review decision", e);
        }
    }

    /**
     * Escalate case to senior analyst
     */
    @Transactional
    public void escalateCase(String reviewId, String reason, String escalatedBy) {

        try {
            log.info("FRAUD REVIEW: Escalating case: reviewId={}, reason={}, escalatedBy={}",
                reviewId, reason, escalatedBy);

            FraudReviewCase reviewCase = fraudReviewCaseRepository.findByReviewId(reviewId)
                .orElseThrow(() -> new FraudReviewQueueException("Review case not found: " + reviewId));

            // Escalate
            reviewCase.setEscalated(true);
            reviewCase.setEscalationReason(reason);
            reviewCase.setEscalatedBy(escalatedBy);
            reviewCase.setEscalatedAt(LocalDateTime.now());

            // Increase priority (unless already highest)
            if (reviewCase.getPriority() > 0) {
                int oldPriority = reviewCase.getPriority();
                reviewCase.setPriority(oldPriority - 1);

                // Update priority metrics
                queueSizeByPriority.get(oldPriority).decrementAndGet();
                queueSizeByPriority.get(reviewCase.getPriority()).incrementAndGet();
            }

            // Reset to pending for senior analyst
            reviewCase.setStatus(FraudReviewStatus.PENDING);
            reviewCase.setAssignedAnalyst(null);
            reviewCase.setReviewStartedAt(null);

            fraudReviewCaseRepository.save(reviewCase);

            // Update Redis
            updateRedisQueue(reviewCase);

            // Update metrics
            casesEscalated.increment();
            queueSize.incrementAndGet();

            // Publish escalation event
            publishEscalationEvent(reviewCase);

            // Notify senior analysts
            notifySeniorAnalysts(reviewCase);

            log.info("FRAUD REVIEW: Case escalated successfully: reviewId={}, newPriority={}",
                reviewId, reviewCase.getPriority());

        } catch (Exception e) {
            log.error("FRAUD REVIEW: Failed to escalate case: reviewId={}", reviewId, e);
            throw new FraudReviewQueueException("Failed to escalate review case", e);
        }
    }

    /**
     * Get queue statistics
     */
    public FraudReviewQueueStats getQueueStats() {

        try {
            // Count by status
            long pendingCount = fraudReviewCaseRepository.countByStatus(FraudReviewStatus.PENDING);
            long inReviewCount = fraudReviewCaseRepository.countByStatus(FraudReviewStatus.IN_REVIEW);
            long completedToday = fraudReviewCaseRepository.countByStatusAndReviewCompletedAtAfter(
                FraudReviewStatus.COMPLETED, LocalDateTime.now().minusDays(1));

            // Count by priority
            Map<Integer, Long> countByPriority = new HashMap<>();
            for (int priority = 0; priority <= 3; priority++) {
                long count = fraudReviewCaseRepository.countByStatusAndPriority(
                    FraudReviewStatus.PENDING, priority);
                countByPriority.put(priority, count);
            }

            // Count overdue
            long overdueCount = fraudReviewCaseRepository.countByStatusAndSlaDeadlineBefore(
                FraudReviewStatus.PENDING, LocalDateTime.now());

            // Average review time
            Double avgReviewTimeMinutes = fraudReviewCaseRepository
                .getAverageReviewDurationMinutes(LocalDateTime.now().minusDays(7));

            // SLA compliance rate
            long totalCompleted = fraudReviewCaseRepository.countByStatusAndReviewCompletedAtAfter(
                FraudReviewStatus.COMPLETED, LocalDateTime.now().minusDays(7));
            long slaViolationsCount = fraudReviewCaseRepository.countBySlaViolationAndReviewCompletedAtAfter(
                true, LocalDateTime.now().minusDays(7));

            double slaComplianceRate = totalCompleted > 0 ?
                (double) (totalCompleted - slaViolationsCount) / totalCompleted * 100 : 100.0;

            return FraudReviewQueueStats.builder()
                .totalPending(pendingCount)
                .totalInReview(inReviewCount)
                .totalCompletedToday(completedToday)
                .countByPriority(countByPriority)
                .overdueCount(overdueCount)
                .averageReviewTimeMinutes(avgReviewTimeMinutes != null ? avgReviewTimeMinutes : 0.0)
                .slaComplianceRate(slaComplianceRate)
                .lastUpdated(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("FRAUD REVIEW: Failed to get queue stats", e);
            return FraudReviewQueueStats.builder()
                .totalPending(0L)
                .lastUpdated(LocalDateTime.now())
                .error("Failed to retrieve statistics")
                .build();
        }
    }

    /**
     * Get last review ID (for testing/debugging)
     */
    public String getLastReviewId() {
        return fraudReviewCaseRepository.findFirstByOrderByCreatedAtDesc()
            .map(FraudReviewCase::getReviewId)
            .orElse(null);
    }

    // ========== SCHEDULED TASKS ==========

    /**
     * Auto-escalate overdue cases (runs every 15 minutes)
     */
    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void autoEscalateOverdueCases() {

        try {
            log.debug("FRAUD REVIEW: Running auto-escalation check");

            List<FraudReviewCase> overdueCases = fraudReviewCaseRepository
                .findByStatusAndSlaDeadlineBefore(FraudReviewStatus.PENDING, LocalDateTime.now());

            if (!overdueCases.isEmpty()) {
                log.warn("FRAUD REVIEW: Found {} overdue cases for auto-escalation", overdueCases.size());

                for (FraudReviewCase reviewCase : overdueCases) {
                    try {
                        escalateCase(reviewCase.getReviewId(),
                            "Auto-escalated: SLA deadline exceeded",
                            "SYSTEM");
                    } catch (Exception e) {
                        log.error("FRAUD REVIEW: Failed to auto-escalate case: reviewId={}",
                            reviewCase.getReviewId(), e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("FRAUD REVIEW: Auto-escalation check failed", e);
        }
    }

    /**
     * Refresh queue metrics (runs every 5 minutes)
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void refreshQueueMetrics() {

        try {
            log.debug("FRAUD REVIEW: Refreshing queue metrics");

            // Update queue size
            long pendingCount = fraudReviewCaseRepository.countByStatus(FraudReviewStatus.PENDING);
            queueSize.set((int) pendingCount);

            // Update priority counts
            for (int priority = 0; priority <= 3; priority++) {
                long count = fraudReviewCaseRepository.countByStatusAndPriority(
                    FraudReviewStatus.PENDING, priority);
                queueSizeByPriority.get(priority).set((int) count);
            }

            // Update overdue count
            long overdue = fraudReviewCaseRepository.countByStatusAndSlaDeadlineBefore(
                FraudReviewStatus.PENDING, LocalDateTime.now());
            overdueCount.set((int) overdue);

            log.debug("FRAUD REVIEW: Metrics refreshed: pending={}, overdue={}",
                pendingCount, overdue);

        } catch (Exception e) {
            log.error("FRAUD REVIEW: Failed to refresh metrics", e);
        }
    }

    // ========== HELPER METHODS ==========

    private String generateReviewId() {
        return "FRQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private LocalDateTime calculateSLADeadline(int priority) {
        LocalDateTime now = LocalDateTime.now();
        return switch (priority) {
            case 0 -> now.plusMinutes(60);   // Critical: 1 hour
            case 1 -> now.plusHours(2);      // High: 2 hours
            case 2 -> now.plusHours(4);      // Medium: 4 hours
            default -> now.plusHours(8);     // Low: 8 hours
        };
    }

    private void addToRedisQueue(FraudReviewCase reviewCase) {
        try {
            String key = "fraud:review:queue:priority:" + reviewCase.getPriority();
            redisTemplate.opsForZSet().add(key,
                reviewCase.getReviewId(),
                reviewCase.getQueuedAt().toEpochSecond(java.time.ZoneOffset.UTC));
        } catch (Exception e) {
            log.warn("FRAUD REVIEW: Failed to add to Redis queue: reviewId={}",
                reviewCase.getReviewId(), e);
        }
    }

    private void updateRedisQueue(FraudReviewCase reviewCase) {
        try {
            // Remove from old priority queue
            for (int priority = 0; priority <= 3; priority++) {
                String key = "fraud:review:queue:priority:" + priority;
                redisTemplate.opsForZSet().remove(key, reviewCase.getReviewId());
            }

            // Add to new priority queue if still pending
            if (reviewCase.getStatus() == FraudReviewStatus.PENDING) {
                addToRedisQueue(reviewCase);
            }
        } catch (Exception e) {
            log.warn("FRAUD REVIEW: Failed to update Redis queue: reviewId={}",
                reviewCase.getReviewId(), e);
        }
    }

    private void removeFromRedisQueue(FraudReviewCase reviewCase) {
        try {
            String key = "fraud:review:queue:priority:" + reviewCase.getPriority();
            redisTemplate.opsForZSet().remove(key, reviewCase.getReviewId());
        } catch (Exception e) {
            log.warn("FRAUD REVIEW: Failed to remove from Redis queue: reviewId={}",
                reviewCase.getReviewId(), e);
        }
    }

    @Async
    private void publishQueueEvent(FraudReviewCase reviewCase) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "FRAUD_REVIEW_QUEUED",
                "reviewId", reviewCase.getReviewId(),
                "paymentId", reviewCase.getPaymentId(),
                "userId", reviewCase.getUserId(),
                "priority", reviewCase.getPriority(),
                "riskScore", reviewCase.getRiskScore(),
                "slaDeadline", reviewCase.getSlaDeadline().toString(),
                "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("fraud-review-events", reviewCase.getReviewId(), event);
        } catch (Exception e) {
            log.warn("FRAUD REVIEW: Failed to publish queue event: reviewId={}",
                reviewCase.getReviewId(), e);
        }
    }

    @Async
    private void publishDecisionEvent(FraudReviewCase reviewCase) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "FRAUD_REVIEW_DECISION",
                "reviewId", reviewCase.getReviewId(),
                "paymentId", reviewCase.getPaymentId(),
                "userId", reviewCase.getUserId(),
                "decision", reviewCase.getDecision().name(),
                "analystId", reviewCase.getAssignedAnalyst(),
                "reviewDurationMinutes", reviewCase.getReviewDurationMinutes(),
                "slaViolation", reviewCase.isSlaViolation(),
                "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("fraud-review-events", reviewCase.getReviewId(), event);
        } catch (Exception e) {
            log.warn("FRAUD REVIEW: Failed to publish decision event: reviewId={}",
                reviewCase.getReviewId(), e);
        }
    }

    @Async
    private void publishEscalationEvent(FraudReviewCase reviewCase) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "FRAUD_REVIEW_ESCALATED",
                "reviewId", reviewCase.getReviewId(),
                "paymentId", reviewCase.getPaymentId(),
                "escalationReason", reviewCase.getEscalationReason(),
                "escalatedBy", reviewCase.getEscalatedBy(),
                "newPriority", reviewCase.getPriority(),
                "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("fraud-review-events", reviewCase.getReviewId(), event);
        } catch (Exception e) {
            log.warn("FRAUD REVIEW: Failed to publish escalation event: reviewId={}",
                reviewCase.getReviewId(), e);
        }
    }

    @Async
    private void notifyFraudAnalysts(FraudReviewCase reviewCase) {
        log.info("FRAUD REVIEW: Notifying analysts of new case: reviewId={}, priority={}",
            reviewCase.getReviewId(), reviewCase.getPriority());

        try {
            // Extract transaction details for context
            Map<String, Object> transactionDetails = new HashMap<>();
            transactionDetails.put("reviewId", reviewCase.getReviewId());
            transactionDetails.put("userId", reviewCase.getUserId());
            transactionDetails.put("paymentId", reviewCase.getPaymentId());
            transactionDetails.put("amount", reviewCase.getAmount() != null ? reviewCase.getAmount().toString() : "N/A");
            transactionDetails.put("currency", reviewCase.getCurrency());
            transactionDetails.put("priority", reviewCase.getPriority());
            transactionDetails.put("riskScore", reviewCase.getRiskScore());
            transactionDetails.put("createdAt", reviewCase.getCreatedAt() != null ? reviewCase.getCreatedAt().toString() : "N/A");
            transactionDetails.put("slaDeadline", reviewCase.getSlaDeadline() != null ? reviewCase.getSlaDeadline().toString() : "N/A");

            // Build risk factors summary
            String riskFactors = buildRiskFactorsSummary(reviewCase);

            // Send Slack alert to fraud analysts channel (real-time notification)
            slackAlertService.sendFraudAlert(
                reviewCase.getUserId(),
                reviewCase.getPaymentId(),
                reviewCase.getRiskScore(),
                riskFactors,
                transactionDetails
            );

            // Send email notification to fraud analysts
            notificationService.sendEmail(
                getFraudAnalystEmail(reviewCase.getPriority()),
                "New Fraud Review Case - Priority " + reviewCase.getPriority(),
                buildFraudReviewEmailBody(reviewCase, riskFactors),
                true // HTML email
            );

            // For critical cases (Priority 0), also trigger PagerDuty
            if (reviewCase.getPriority() == 0) {
                pagerDutyAlertService.triggerFraudAlert(
                    reviewCase.getUserId(),
                    reviewCase.getPaymentId(),
                    reviewCase.getRiskScore(),
                    riskFactors
                );
            }

            log.info("Fraud analysts notified successfully for case: {}", reviewCase.getReviewId());

        } catch (Exception e) {
            log.error("Failed to notify fraud analysts for case: {}", reviewCase.getReviewId(), e);
            // Don't throw exception - notification failure shouldn't block fraud review
        }
    }

    @Async
    private void notifySeniorAnalysts(FraudReviewCase reviewCase) {
        log.info("FRAUD REVIEW: Notifying senior analysts of escalation: reviewId={}",
            reviewCase.getReviewId());

        try {
            // Build escalation context
            Map<String, Object> details = buildEscalationContext(reviewCase);

            // Send Slack alert to senior fraud team
            slackAlertService.sendComplianceAlert(
                reviewCase.getUserId(),
                reviewCase.getPaymentId(),
                "Fraud Case Escalation Required",
                buildEscalationDescription(reviewCase),
                com.waqiti.alerting.dto.AlertSeverity.HIGH
            );

            // Email senior analysts with high importance
            notificationService.sendEmail(
                "senior-fraud-analysts@example.com",
                String.format("[ESCALATION] Fraud Review Case %s - Priority %d",
                    reviewCase.getReviewId(), reviewCase.getPriority()),
                buildEscalationEmailHtml(reviewCase),
                true // HTML
            );

            // For critical escalations (Priority 0-1), also page senior team
            if (reviewCase.getPriority() <= 1) {
                pagerDutyAlertService.triggerAlert(
                    String.format("Fraud Escalation - Case %s", reviewCase.getReviewId()),
                    String.format("Senior analyst review required for high-risk case. " +
                        "Risk Score: %.1f%% | Amount: %s %s | User: %s",
                        reviewCase.getRiskScore() * 100,
                        reviewCase.getAmount(),
                        reviewCase.getCurrency(),
                        reviewCase.getUserId()),
                    com.waqiti.alerting.dto.AlertSeverity.HIGH,
                    com.waqiti.alerting.dto.AlertContext.builder()
                        .serviceName("fraud-detection-service")
                        .component("fraud-review-queue")
                        .userId(reviewCase.getUserId())
                        .transactionId(reviewCase.getPaymentId())
                        .riskScore((double) reviewCase.getRiskScore())
                        .additionalData(details)
                        .timestamp(java.time.Instant.now())
                        .build()
                );
            }

            // Log escalation in audit trail
            kafkaTemplate.send("audit.fraud-escalation",
                reviewCase.getReviewId(),
                buildEscalationAuditEvent(reviewCase)
            );

            log.info("Senior analysts notified successfully for escalation: {}", reviewCase.getReviewId());

        } catch (Exception e) {
            log.error("Failed to notify senior analysts for case: {}", reviewCase.getReviewId(), e);
        }
    }

    @Async
    private void notifyUser(FraudReviewCase reviewCase) {
        log.info("FRAUD REVIEW: Notifying user of decision: userId={}, decision={}",
            reviewCase.getUserId(), reviewCase.getDecision());

        try {
            String decision = reviewCase.getDecision() != null ?
                reviewCase.getDecision().toString() : "PENDING";

            // Send email notification
            notificationService.sendUserEmail(
                reviewCase.getUserId(),
                getEmailSubject(decision),
                buildUserNotificationEmailHtml(reviewCase, decision)
            );

            // Send push notification
            notificationService.sendPushNotification(
                reviewCase.getUserId(),
                getPushTitle(decision),
                getPushBody(reviewCase, decision)
            );

            // Send SMS for critical decisions (rejected transactions)
            if ("REJECTED".equals(decision)) {
                notificationService.sendSMS(
                    reviewCase.getUserId(),
                    String.format("Transaction %s declined due to security review. " +
                        "Please contact support at support@example.com or call 1-800-WAQITI.",
                        maskPaymentId(reviewCase.getPaymentId()))
                );
            }

            // Send in-app notification
            notificationService.sendInAppNotification(
                reviewCase.getUserId(),
                "Transaction Review Update",
                buildInAppMessage(reviewCase, decision),
                "/transactions/" + reviewCase.getPaymentId()
            );

            // Publish user notification event for analytics
            kafkaTemplate.send("user.notification.sent",
                reviewCase.getUserId(),
                buildUserNotificationEvent(reviewCase, decision)
            );

            log.info("User notified successfully: userId={}, decision={}, channels=email,push,sms,in-app",
                reviewCase.getUserId(), decision);

        } catch (Exception e) {
            log.error("Failed to notify user for case: {}", reviewCase.getReviewId(), e);
        }
    }

    @Async
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    private void processPaymentDecision(FraudReviewCase reviewCase) {
        log.info("FRAUD REVIEW: Processing payment decision: paymentId={}, decision={}",
            reviewCase.getPaymentId(), reviewCase.getDecision());

        try {
            if (reviewCase.getDecision() == null) {
                log.warn("No decision set for fraud review case: {}", reviewCase.getReviewId());
                return;
            }

            String reviewMetadata = String.format("FraudReview:%s|Analyst:%s|RiskScore:%.2f",
                reviewCase.getReviewId(),
                reviewCase.getReviewerId() != null ? reviewCase.getReviewerId() : "SYSTEM",
                reviewCase.getRiskScore());

            switch (reviewCase.getDecision()) {
                case APPROVED:
                    // Approve and release payment for processing
                    paymentProcessingService.approvePayment(
                        reviewCase.getPaymentId(),
                        String.format("Fraud review approved. Case: %s", reviewCase.getReviewId()),
                        reviewCase.getReviewerId()
                    );

                    // Publish approval event
                    kafkaTemplate.send("payment.fraud-review.approved",
                        reviewCase.getPaymentId(),
                        buildPaymentDecisionEvent(reviewCase, "APPROVED", reviewMetadata)
                    );

                    // Update fraud score for user (positive signal)
                    kafkaTemplate.send("user.fraud-score.update",
                        reviewCase.getUserId(),
                        Map.of(
                            "action", "DECREASE_RISK",
                            "reason", "LEGITIMATE_TRANSACTION_APPROVED",
                            "caseId", reviewCase.getReviewId()
                        )
                    );

                    log.info("Payment approved and released: paymentId={}, caseId={}",
                        reviewCase.getPaymentId(), reviewCase.getReviewId());
                    break;

                case REJECTED:
                    // Reject payment and refund if already captured
                    paymentProcessingService.rejectPayment(
                        reviewCase.getPaymentId(),
                        String.format("Fraud review rejected. Reason: %s",
                            reviewCase.getRejectionReason() != null ?
                                reviewCase.getRejectionReason() : "High fraud risk"),
                        reviewCase.getReviewerId()
                    );

                    // Publish rejection event
                    kafkaTemplate.send("payment.fraud-review.rejected",
                        reviewCase.getPaymentId(),
                        buildPaymentDecisionEvent(reviewCase, "REJECTED", reviewMetadata)
                    );

                    // Update fraud score for user (negative signal)
                    kafkaTemplate.send("user.fraud-score.update",
                        reviewCase.getUserId(),
                        Map.of(
                            "action", "INCREASE_RISK",
                            "reason", "FRAUDULENT_TRANSACTION_DETECTED",
                            "caseId", reviewCase.getReviewId(),
                            "severity", reviewCase.getPriority() == 0 ? "CRITICAL" : "HIGH"
                        )
                    );

                    // Consider account restrictions for severe cases
                    if (reviewCase.getPriority() == 0 || reviewCase.getRiskScore() > 0.95) {
                        kafkaTemplate.send("user.account.restrict",
                            reviewCase.getUserId(),
                            Map.of(
                                "restriction", "TRANSACTION_HOLD",
                                "duration", "24_HOURS",
                                "reason", "HIGH_FRAUD_RISK",
                                "caseId", reviewCase.getReviewId()
                            )
                        );
                    }

                    log.info("Payment rejected due to fraud: paymentId={}, reason={}, caseId={}",
                        reviewCase.getPaymentId(), reviewCase.getRejectionReason(),
                        reviewCase.getReviewId());
                    break;

                case REQUIRES_MORE_INFO:
                    // Hold payment pending additional information
                    paymentProcessingService.holdPayment(
                        reviewCase.getPaymentId(),
                        "Additional information required for fraud review"
                    );

                    // Request additional info from user
                    notificationService.sendUserEmail(
                        reviewCase.getUserId(),
                        "Additional Information Required for Transaction Review",
                        buildMoreInfoRequestEmail(reviewCase)
                    );

                    // Publish hold event
                    kafkaTemplate.send("payment.fraud-review.on-hold",
                        reviewCase.getPaymentId(),
                        buildPaymentDecisionEvent(reviewCase, "ON_HOLD", reviewMetadata)
                    );

                    log.info("Payment on hold, more info requested: paymentId={}, caseId={}",
                        reviewCase.getPaymentId(), reviewCase.getReviewId());
                    break;

                case ESCALATED:
                    // Already handled by escalation workflow, just log
                    log.info("Payment remains on hold pending escalation resolution: paymentId={}, caseId={}",
                        reviewCase.getPaymentId(), reviewCase.getReviewId());

                    // Publish escalation tracking event
                    kafkaTemplate.send("payment.fraud-review.escalated",
                        reviewCase.getPaymentId(),
                        buildPaymentDecisionEvent(reviewCase, "ESCALATED", reviewMetadata)
                    );
                    break;

                default:
                    log.warn("Unknown fraud review decision: {} for case: {}",
                        reviewCase.getDecision(), reviewCase.getReviewId());
            }

            // Update metrics
            updateDecisionMetrics(reviewCase.getDecision());

            log.info("Payment decision processed successfully: paymentId={}, decision={}, caseId={}",
                reviewCase.getPaymentId(), reviewCase.getDecision(), reviewCase.getReviewId());

        } catch (Exception e) {
            log.error("Failed to process payment decision for case: {}", reviewCase.getReviewId(), e);

            // Alert ops team of decision processing failure
            slackAlertService.sendDLQFailureAlert(
                "fraud-decision-processing",
                reviewCase.getReviewId(),
                String.format("Failed to process fraud decision: %s", e.getMessage()),
                Map.of(
                    "reviewId", reviewCase.getReviewId(),
                    "paymentId", reviewCase.getPaymentId(),
                    "decision", reviewCase.getDecision().toString()
                )
            );

            throw new FraudReviewQueueException("Payment decision processing failed: " +
                reviewCase.getReviewId(), e);
        }
    }

    /**
     * Custom exception for fraud review queue operations
     */
    public static class FraudReviewQueueException extends RuntimeException {
        public FraudReviewQueueException(String message) {
            super(message);
        }

        public FraudReviewQueueException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Fraud Review Queue Statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class FraudReviewQueueStats {
        private long totalPending;
        private long totalInReview;
        private long totalCompletedToday;
        private Map<Integer, Long> countByPriority;
        private long overdueCount;
        private double averageReviewTimeMinutes;
        private double slaComplianceRate;
        private LocalDateTime lastUpdated;
        private String error;
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> buildEscalationContext(FraudReviewCase reviewCase) {
        Map<String, Object> context = new HashMap<>();
        context.put("reviewId", reviewCase.getReviewId());
        context.put("userId", reviewCase.getUserId());
        context.put("paymentId", reviewCase.getPaymentId());
        context.put("amount", reviewCase.getAmount() != null ? reviewCase.getAmount().toString() : "N/A");
        context.put("currency", reviewCase.getCurrency());
        context.put("priority", reviewCase.getPriority());
        context.put("riskScore", reviewCase.getRiskScore());
        context.put("status", reviewCase.getStatus() != null ? reviewCase.getStatus().toString() : "PENDING");
        context.put("assignedTo", reviewCase.getReviewerId());
        context.put("slaDeadline", reviewCase.getSlaDeadline());
        return context;
    }

    private String buildEscalationDescription(FraudReviewCase reviewCase) {
        return String.format(
            "Case %s requires senior analyst review.\n\n" +
            "**Priority:** %d (Critical)\n" +
            "**Risk Score:** %.1f%%\n" +
            "**Amount:** %s %s\n" +
            "**SLA Deadline:** %s\n" +
            "**Status:** SLA breach imminent - requires immediate attention",
            reviewCase.getReviewId(),
            reviewCase.getPriority(),
            reviewCase.getRiskScore() * 100,
            reviewCase.getAmount(),
            reviewCase.getCurrency(),
            reviewCase.getSlaDeadline()
        );
    }

    private String buildEscalationEmailHtml(FraudReviewCase reviewCase) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif;">
            <div style="border-left: 4px solid #dc3545; padding-left: 20px;">
                <h2 style="color: #dc3545;">ESCALATION: Fraud Review Case %s</h2>
                <p><strong>This case requires immediate senior analyst review.</strong></p>
            </div>
            <table style="margin: 20px 0; border-collapse: collapse; width: 100%%;">
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Review ID:</strong></td>
                    <td style="padding: 8px;">%s</td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>User ID:</strong></td>
                    <td style="padding: 8px;">%s</td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Payment ID:</strong></td>
                    <td style="padding: 8px;">%s</td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Priority:</strong></td>
                    <td style="padding: 8px; color: #dc3545;"><strong>%d (Critical)</strong></td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Risk Score:</strong></td>
                    <td style="padding: 8px; color: #dc3545;"><strong>%.1f%%</strong></td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Amount:</strong></td>
                    <td style="padding: 8px;">%s %s</td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>SLA Deadline:</strong></td>
                    <td style="padding: 8px; color: #dc3545;">%s</td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Current Status:</strong></td>
                    <td style="padding: 8px;">%s</td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Assigned To:</strong></td>
                    <td style="padding: 8px;">%s</td></tr>
            </table>
            <div style="background: #fff3cd; border: 1px solid #ffc107; padding: 15px; margin: 20px 0;">
                <strong>⚠️ Action Required:</strong> This case is approaching SLA deadline and requires
                senior analyst review. Complex fraud pattern detected.
            </div>
            <p><a href="https://admin.example.com/fraud/cases/%s"
                  style="background: #dc3545; color: white; padding: 10px 20px; text-decoration: none; border-radius: 4px; display: inline-block;">
                Review Case Immediately
            </a></p>
            <p style="color: #6c757d; font-size: 12px; margin-top: 30px;">
                Generated: %s | System: Fraud Detection Service
            </p>
            </body>
            </html>
            """,
            reviewCase.getReviewId(),
            reviewCase.getReviewId(),
            reviewCase.getUserId(),
            reviewCase.getPaymentId(),
            reviewCase.getPriority(),
            reviewCase.getRiskScore() * 100,
            reviewCase.getAmount(),
            reviewCase.getCurrency(),
            reviewCase.getSlaDeadline(),
            reviewCase.getStatus(),
            reviewCase.getReviewerId() != null ? reviewCase.getReviewerId() : "Unassigned",
            reviewCase.getReviewId(),
            LocalDateTime.now()
        );
    }

    private Map<String, Object> buildEscalationAuditEvent(FraudReviewCase reviewCase) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "FRAUD_CASE_ESCALATED");
        event.put("reviewId", reviewCase.getReviewId());
        event.put("userId", reviewCase.getUserId());
        event.put("paymentId", reviewCase.getPaymentId());
        event.put("priority", reviewCase.getPriority());
        event.put("riskScore", reviewCase.getRiskScore());
        event.put("escalatedAt", LocalDateTime.now());
        event.put("escalatedBy", "SYSTEM");
        return event;
    }

    private String getEmailSubject(String decision) {
        return switch (decision) {
            case "APPROVED" -> "Transaction Approved - Processing Now";
            case "REJECTED" -> "Transaction Declined - Security Review";
            case "REQUIRES_MORE_INFO" -> "Action Required: Transaction Review";
            default -> "Transaction Review Update";
        };
    }

    private String getPushTitle(String decision) {
        return switch (decision) {
            case "APPROVED" -> "✅ Transaction Approved";
            case "REJECTED" -> "❌ Transaction Declined";
            case "REQUIRES_MORE_INFO" -> "⚠️ Action Required";
            default -> "Transaction Update";
        };
    }

    private String getPushBody(FraudReviewCase reviewCase, String decision) {
        return switch (decision) {
            case "APPROVED" -> String.format("Your transaction of %s %s has been approved and is being processed.",
                reviewCase.getAmount(), reviewCase.getCurrency());
            case "REJECTED" -> "Your transaction was declined due to security concerns. Please contact support.";
            case "REQUIRES_MORE_INFO" -> "We need additional information to complete your transaction review.";
            default -> "Your transaction review has been updated. Check your app for details.";
        };
    }

    private String buildUserNotificationEmailHtml(FraudReviewCase reviewCase, String decision) {
        if ("APPROVED".equals(decision)) {
            return String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                <div style="background: #d4edda; border-left: 4px solid #28a745; padding: 20px; margin-bottom: 20px;">
                    <h2 style="color: #28a745; margin: 0;">✅ Transaction Approved</h2>
                </div>
                <p>Great news! Your transaction has been approved and is now being processed.</p>
                <table style="margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Transaction ID:</strong></td>
                        <td style="padding: 8px;">%s</td></tr>
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Amount:</strong></td>
                        <td style="padding: 8px;">%s %s</td></tr>
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Status:</strong></td>
                        <td style="padding: 8px; color: #28a745;"><strong>Approved</strong></td></tr>
                </table>
                <p>Thank you for your patience during the security review.</p>
                <p style="color: #6c757d; font-size: 12px;">Waqiti Security Team</p>
                </body>
                </html>
                """,
                maskPaymentId(reviewCase.getPaymentId()),
                reviewCase.getAmount(),
                reviewCase.getCurrency()
            );
        } else if ("REJECTED".equals(decision)) {
            return String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                <div style="background: #f8d7da; border-left: 4px solid #dc3545; padding: 20px; margin-bottom: 20px;">
                    <h2 style="color: #dc3545; margin: 0;">❌ Transaction Declined</h2>
                </div>
                <p>We're sorry, but your transaction has been declined due to security concerns.</p>
                <table style="margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Transaction ID:</strong></td>
                        <td style="padding: 8px;">%s</td></tr>
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Amount:</strong></td>
                        <td style="padding: 8px;">%s %s</td></tr>
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Reason:</strong></td>
                        <td style="padding: 8px;">%s</td></tr>
                </table>
                <p><strong>What can you do?</strong></p>
                <ul>
                    <li>Contact our support team at <a href="mailto:support@example.com">support@example.com</a></li>
                    <li>Call us at 1-800-WAQITI</li>
                    <li>Review your account security settings</li>
                </ul>
                <p style="color: #6c757d; font-size: 12px;">Waqiti Security Team</p>
                </body>
                </html>
                """,
                maskPaymentId(reviewCase.getPaymentId()),
                reviewCase.getAmount(),
                reviewCase.getCurrency(),
                reviewCase.getRejectionReason() != null ? reviewCase.getRejectionReason() : "Security review failed"
            );
        } else {
            return String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                <div style="background: #fff3cd; border-left: 4px solid #ffc107; padding: 20px; margin-bottom: 20px;">
                    <h2 style="color: #856404; margin: 0;">⚠️ Transaction Under Review</h2>
                </div>
                <p>Your transaction is currently under security review.</p>
                <table style="margin: 20px 0; border-collapse: collapse;">
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Transaction ID:</strong></td>
                        <td style="padding: 8px;">%s</td></tr>
                    <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Amount:</strong></td>
                        <td style="padding: 8px;">%s %s</td></tr>
                </table>
                <p>We'll notify you once the review is complete (typically within 2-4 hours).</p>
                <p style="color: #6c757d; font-size: 12px;">Waqiti Security Team</p>
                </body>
                </html>
                """,
                maskPaymentId(reviewCase.getPaymentId()),
                reviewCase.getAmount(),
                reviewCase.getCurrency()
            );
        }
    }

    private String buildInAppMessage(FraudReviewCase reviewCase, String decision) {
        return switch (decision) {
            case "APPROVED" -> String.format("Your transaction of %s %s has been approved.",
                reviewCase.getAmount(), reviewCase.getCurrency());
            case "REJECTED" -> "Transaction declined. Contact support for details.";
            case "REQUIRES_MORE_INFO" -> "Additional information needed. Tap to provide details.";
            default -> "Transaction review updated.";
        };
    }

    private Map<String, Object> buildUserNotificationEvent(FraudReviewCase reviewCase, String decision) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_NOTIFIED");
        event.put("reviewId", reviewCase.getReviewId());
        event.put("userId", reviewCase.getUserId());
        event.put("paymentId", reviewCase.getPaymentId());
        event.put("decision", decision);
        event.put("notifiedAt", LocalDateTime.now());
        event.put("channels", Arrays.asList("EMAIL", "PUSH", "IN_APP", "SMS"));
        return event;
    }

    private Map<String, Object> buildPaymentDecisionEvent(FraudReviewCase reviewCase,
                                                          String decision, String metadata) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "FRAUD_REVIEW_DECISION");
        event.put("reviewId", reviewCase.getReviewId());
        event.put("paymentId", reviewCase.getPaymentId());
        event.put("userId", reviewCase.getUserId());
        event.put("decision", decision);
        event.put("priority", reviewCase.getPriority());
        event.put("riskScore", reviewCase.getRiskScore());
        event.put("reviewerId", reviewCase.getReviewerId());
        event.put("metadata", metadata);
        event.put("timestamp", LocalDateTime.now());
        return event;
    }

    private String buildMoreInfoRequestEmail(FraudReviewCase reviewCase) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif;">
            <h2>Additional Information Required</h2>
            <p>We need more information to complete your transaction review.</p>
            <table style="margin: 20px 0; border-collapse: collapse;">
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Transaction ID:</strong></td>
                    <td style="padding: 8px;">%s</td></tr>
                <tr><td style="padding: 8px; background: #f8f9fa;"><strong>Amount:</strong></td>
                    <td style="padding: 8px;">%s %s</td></tr>
            </table>
            <p>Please log in to your account and provide the requested information.</p>
            <p><a href="https://app.example.com/security/verify/%s"
                  style="background: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 4px; display: inline-block;">
                Provide Information
            </a></p>
            </body>
            </html>
            """,
            maskPaymentId(reviewCase.getPaymentId()),
            reviewCase.getAmount(),
            reviewCase.getCurrency(),
            reviewCase.getReviewId()
        );
    }

    private String maskPaymentId(String paymentId) {
        if (paymentId == null || paymentId.length() < 8) {
            return paymentId;
        }
        return paymentId.substring(0, 4) + "****" + paymentId.substring(paymentId.length() - 4);
    }

    private void updateDecisionMetrics(FraudReviewDecision decision) {
        try {
            Counter.builder("fraud.review.decisions")
                .tag("decision", decision.toString())
                .register(meterRegistry)
                .increment();
        } catch (Exception e) {
            log.warn("Failed to update decision metrics", e);
        }
    }
}
