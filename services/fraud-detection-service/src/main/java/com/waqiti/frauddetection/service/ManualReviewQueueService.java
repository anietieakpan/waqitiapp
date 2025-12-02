package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudAssessmentResult;
import com.waqiti.frauddetection.entity.ManualReviewCase;
import com.waqiti.frauddetection.repository.ManualReviewCaseRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manual Review Queue Service
 *
 * Comprehensive service for managing manual fraud review queue with:
 * - Automated case creation and assignment
 * - SLA tracking and violation alerting
 * - Priority-based queue management
 * - Review analyst workload balancing
 * - Performance metrics and dashboards
 * - Case escalation workflows
 * - Automated reminders and notifications
 *
 * SLA Tiers:
 * - CRITICAL: 15 minutes
 * - HIGH: 1 hour
 * - MEDIUM: 4 hours
 * - LOW: 24 hours
 *
 * Features:
 * - Real-time SLA monitoring
 * - Automatic case escalation
 * - Analyst assignment optimization
 * - Queue statistics and reporting
 * - Integration with fraud alert system
 *
 * @author Waqiti Fraud Operations Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ManualReviewQueueService {

    private final ManualReviewCaseRepository reviewCaseRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${fraud.review.sla.critical-minutes:15}")
    private int criticalSlaPpMinutes;

    @Value("${fraud.review.sla.high-minutes:60}")
    private int highSlaMinutes;

    @Value("${fraud.review.sla.medium-minutes:240}")
    private int mediumSlaMinutes;

    @Value("${fraud.review.sla.low-minutes:1440}")
    private int lowSlaMinutes;

    @Value("${fraud.review.escalation.enabled:true}")
    private boolean escalationEnabled;

    @Value("${fraud.review.alert.topic:fraud-review-alerts}")
    private String reviewAlertTopic;

    // In-memory tracking for performance
    private final Map<String, ManualReviewCase> activeReviews = new ConcurrentHashMap<>();
    private final Map<String, Integer> analystWorkload = new ConcurrentHashMap<>();

    /**
     * Create a new manual review case from fraud assessment
     */
    @Transactional
    public String createReviewCase(FraudAssessmentResult assessment) {
        log.info("Creating manual review case for transaction: {}", assessment.getTransactionId());

        try {
            // Generate unique case ID
            String caseId = generateCaseId();

            // Determine priority
            ReviewPriority priority = determinePriority(assessment);

            // Calculate SLA deadline
            LocalDateTime slaDeadline = calculateSlaDeadline(priority);

            // Build review case
            ManualReviewCase reviewCase = ManualReviewCase.builder()
                .caseId(caseId)
                .transactionId(assessment.getTransactionId())
                .userId(assessment.getUserId())
                .priority(priority)
                .status(ReviewStatus.PENDING)
                .riskScore(assessment.getFinalScore())
                .mlScore(assessment.getMlScore())
                .ruleScore(assessment.getRuleBasedScore())
                .triggeredRules(extractRulesList(assessment))
                .reviewReason(buildReviewReason(assessment))
                .createdAt(LocalDateTime.now())
                .slaDeadline(slaDeadline)
                .isBlocked(assessment.isBlocked())
                .metadata(buildCaseMetadata(assessment))
                .build();

            // Save to repository
            ManualReviewCase savedCase = reviewCaseRepository.save(reviewCase);

            // Add to active reviews
            activeReviews.put(caseId, savedCase);

            // Try to assign to analyst
            assignToAnalyst(savedCase);

            // Publish to queue
            publishReviewCaseCreated(savedCase);

            // Record metrics
            recordCaseCreationMetrics(priority);

            log.info("Manual review case created: {} with priority: {} and SLA: {}",
                caseId, priority, slaDeadline);

            return caseId;

        } catch (Exception e) {
            log.error("Failed to create manual review case for transaction: {}",
                assessment.getTransactionId(), e);
            meterRegistry.counter("fraud.review.case.creation.errors").increment();
            throw new ReviewQueueException("Failed to create review case", e);
        }
    }

    /**
     * Assign case to analyst based on workload and expertise
     */
    @Transactional
    public void assignToAnalyst(ManualReviewCase reviewCase) {
        String assignedAnalyst = selectBestAnalyst(reviewCase);

        if (assignedAnalyst != null) {
            reviewCase.setAssignedTo(assignedAnalyst);
            reviewCase.setAssignedAt(LocalDateTime.now());
            reviewCase.setStatus(ReviewStatus.ASSIGNED);

            reviewCaseRepository.save(reviewCase);

            // Update workload tracking
            analystWorkload.merge(assignedAnalyst, 1, Integer::sum);

            log.info("Case {} assigned to analyst: {}", reviewCase.getCaseId(), assignedAnalyst);

            // Notify analyst
            notifyAnalystAssignment(reviewCase, assignedAnalyst);

            meterRegistry.counter("fraud.review.cases.assigned",
                "priority", reviewCase.getPriority().name()).increment();
        } else {
            log.warn("No available analyst for case: {}", reviewCase.getCaseId());
            meterRegistry.counter("fraud.review.cases.unassigned").increment();
        }
    }

    /**
     * Complete a review case with decision
     */
    @Transactional
    public void completeReview(String caseId, String reviewedBy, ReviewDecision decision, String notes) {
        log.info("Completing review for case: {} by: {} with decision: {}",
            caseId, reviewedBy, decision);

        reviewCaseRepository.findByCaseId(caseId).ifPresent(reviewCase -> {
            reviewCase.setStatus(ReviewStatus.COMPLETED);
            reviewCase.setReviewedBy(reviewedBy);
            reviewCase.setReviewedAt(LocalDateTime.now());
            reviewCase.setDecision(decision);
            reviewCase.setReviewNotes(notes);

            // Calculate review time
            long reviewTimeMinutes = ChronoUnit.MINUTES.between(
                reviewCase.getCreatedAt(), LocalDateTime.now());
            reviewCase.setReviewTimeMinutes(reviewTimeMinutes);

            // Check SLA compliance
            boolean slaViolated = LocalDateTime.now().isAfter(reviewCase.getSlaDeadline());
            reviewCase.setSlaViolated(slaViolated);

            reviewCaseRepository.save(reviewCase);

            // Remove from active reviews
            activeReviews.remove(caseId);

            // Update analyst workload
            if (reviewCase.getAssignedTo() != null) {
                analystWorkload.computeIfPresent(reviewCase.getAssignedTo(), (k, v) -> Math.max(0, v - 1));
            }

            // Record metrics
            recordReviewCompletionMetrics(reviewCase, slaViolated);

            // Publish completion event
            publishReviewCompleted(reviewCase);

            log.info("Review completed for case: {} in {} minutes (SLA violated: {})",
                caseId, reviewTimeMinutes, slaViolated);
        });
    }

    /**
     * Escalate case to higher priority
     */
    @Transactional
    public void escalateCase(String caseId, String reason) {
        log.warn("Escalating case: {} - Reason: {}", caseId, reason);

        reviewCaseRepository.findByCaseId(caseId).ifPresent(reviewCase -> {
            ReviewPriority oldPriority = reviewCase.getPriority();
            ReviewPriority newPriority = escalatePriority(oldPriority);

            reviewCase.setPriority(newPriority);
            reviewCase.setEscalated(true);
            reviewCase.setEscalationReason(reason);
            reviewCase.setEscalatedAt(LocalDateTime.now());

            // Recalculate SLA deadline
            reviewCase.setSlaDeadline(calculateSlaDeadline(newPriority));

            reviewCaseRepository.save(reviewCase);

            // Publish escalation alert
            publishCaseEscalated(reviewCase, oldPriority, reason);

            meterRegistry.counter("fraud.review.cases.escalated",
                "from", oldPriority.name(),
                "to", newPriority.name()).increment();

            log.info("Case {} escalated from {} to {}", caseId, oldPriority, newPriority);
        });
    }

    /**
     * Scheduled job to check SLA compliance and escalate if needed
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorSlaCompliance() {
        log.debug("Monitoring SLA compliance for {} active cases", activeReviews.size());

        LocalDateTime now = LocalDateTime.now();
        List<ManualReviewCase> violations = new ArrayList<>();

        for (ManualReviewCase reviewCase : activeReviews.values()) {
            if (now.isAfter(reviewCase.getSlaDeadline())) {
                violations.add(reviewCase);
            }
        }

        if (!violations.isEmpty()) {
            log.warn("Found {} SLA violations", violations.size());

            for (ManualReviewCase violation : violations) {
                handleSlaViolation(violation);
            }
        }
    }

    /**
     * Handle SLA violation
     */
    private void handleSlaViolation(ManualReviewCase reviewCase) {
        log.error("SLA VIOLATION: Case {} exceeded deadline: {}",
            reviewCase.getCaseId(), reviewCase.getSlaDeadline());

        // Mark as violated
        reviewCase.setSlaViolated(true);
        reviewCaseRepository.save(reviewCase);

        // Escalate if enabled
        if (escalationEnabled) {
            escalateCase(reviewCase.getCaseId(), "SLA deadline exceeded");
        }

        // Send alert
        publishSlaViolationAlert(reviewCase);

        meterRegistry.counter("fraud.review.sla.violations",
            "priority", reviewCase.getPriority().name()).increment();
    }

    /**
     * Get queue statistics
     */
    public QueueStatistics getQueueStatistics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);

        long totalPending = reviewCaseRepository.countByStatus(ReviewStatus.PENDING);
        long totalAssigned = reviewCaseRepository.countByStatus(ReviewStatus.ASSIGNED);
        long totalInProgress = reviewCaseRepository.countByStatus(ReviewStatus.IN_PROGRESS);

        long completed24h = reviewCaseRepository.countByStatusAndReviewedAtAfter(
            ReviewStatus.COMPLETED, last24Hours);

        long slaViolations24h = reviewCaseRepository.countBySlaViolatedTrueAndCreatedAtAfter(last24Hours);

        // Calculate average review time
        List<ManualReviewCase> recentCompleted = reviewCaseRepository
            .findByStatusAndReviewedAtAfter(ReviewStatus.COMPLETED, last24Hours);

        double avgReviewTime = recentCompleted.stream()
            .filter(c -> c.getReviewTimeMinutes() != null)
            .mapToLong(ManualReviewCase::getReviewTimeMinutes)
            .average()
            .orElse(0.0);

        return QueueStatistics.builder()
            .totalPending(totalPending)
            .totalAssigned(totalAssigned)
            .totalInProgress(totalInProgress)
            .completed24Hours(completed24h)
            .slaViolations24Hours(slaViolations24h)
            .averageReviewTimeMinutes(avgReviewTime)
            .activeAnalysts(analystWorkload.size())
            .timestamp(now)
            .build();
    }

    /**
     * Get analyst performance metrics
     */
    public Map<String, AnalystPerformance> getAnalystPerformance() {
        LocalDateTime last7Days = LocalDateTime.now().minusDays(7);

        List<ManualReviewCase> recentCases = reviewCaseRepository
            .findByReviewedAtAfter(last7Days);

        Map<String, List<ManualReviewCase>> casesByAnalyst = recentCases.stream()
            .filter(c -> c.getReviewedBy() != null)
            .collect(Collectors.groupingBy(ManualReviewCase::getReviewedBy));

        Map<String, AnalystPerformance> performance = new HashMap<>();

        for (Map.Entry<String, List<ManualReviewCase>> entry : casesByAnalyst.entrySet()) {
            String analyst = entry.getKey();
            List<ManualReviewCase> cases = entry.getValue();

            long totalReviewed = cases.size();
            double avgReviewTime = cases.stream()
                .filter(c -> c.getReviewTimeMinutes() != null)
                .mapToLong(ManualReviewCase::getReviewTimeMinutes)
                .average()
                .orElse(0.0);

            long slaViolations = cases.stream()
                .filter(ManualReviewCase::isSlaViolated)
                .count();

            double slaCompliance = totalReviewed > 0 ?
                ((double) (totalReviewed - slaViolations) / totalReviewed) * 100 : 0.0;

            performance.put(analyst, AnalystPerformance.builder()
                .analystId(analyst)
                .totalReviewed(totalReviewed)
                .averageReviewTimeMinutes(avgReviewTime)
                .slaViolations(slaViolations)
                .slaCompliancePercent(slaCompliance)
                .currentWorkload(analystWorkload.getOrDefault(analyst, 0))
                .build());
        }

        return performance;
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    private String generateCaseId() {
        return "MR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ReviewPriority determinePriority(FraudAssessmentResult assessment) {
        if (assessment.getFinalScore() >= 0.90 || assessment.isBlocked()) {
            return ReviewPriority.CRITICAL;
        } else if (assessment.getFinalScore() >= 0.75) {
            return ReviewPriority.HIGH;
        } else if (assessment.getFinalScore() >= 0.55) {
            return ReviewPriority.MEDIUM;
        } else {
            return ReviewPriority.LOW;
        }
    }

    private LocalDateTime calculateSlaDeadline(ReviewPriority priority) {
        int slaMinutes = switch (priority) {
            case CRITICAL -> criticalSlaMinutes;
            case HIGH -> highSlaMinutes;
            case MEDIUM -> mediumSlaMinutes;
            case LOW -> lowSlaMinutes;
        };

        return LocalDateTime.now().plusMinutes(slaMinutes);
    }

    private ReviewPriority escalatePriority(ReviewPriority current) {
        return switch (current) {
            case LOW -> ReviewPriority.MEDIUM;
            case MEDIUM -> ReviewPriority.HIGH;
            case HIGH, CRITICAL -> ReviewPriority.CRITICAL;
        };
    }

    private String buildReviewReason(FraudAssessmentResult assessment) {
        StringBuilder reason = new StringBuilder();
        reason.append("High risk transaction detected. ");

        if (assessment.isBlocked()) {
            reason.append("Transaction BLOCKED. ");
        }

        if (assessment.getTriggeredRules() != null && !assessment.getTriggeredRules().isEmpty()) {
            reason.append(String.format("Triggered %d fraud rules. ",
                assessment.getTriggeredRules().size()));
        }

        reason.append(String.format("Risk Score: %.2f", assessment.getFinalScore()));

        return reason.toString();
    }

    private List<String> extractRulesList(FraudAssessmentResult assessment) {
        if (assessment.getTriggeredRules() == null) {
            return List.of();
        }

        return assessment.getTriggeredRules().stream()
            .map(rule -> rule.getRuleCode() + ":" + rule.getRuleName())
            .collect(Collectors.toList());
    }

    private Map<String, Object> buildCaseMetadata(FraudAssessmentResult assessment) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("assessment_timestamp", assessment.getAssessmentTimestamp());
        metadata.put("processing_time_ms", assessment.getProcessingTimeMs());
        metadata.put("device_risk_score", assessment.getDeviceRiskScore());
        metadata.put("velocity_risk_score", assessment.getVelocityRiskScore());
        metadata.put("behavioral_risk_score", assessment.getBehavioralRiskScore());
        return metadata;
    }

    private String selectBestAnalyst(ManualReviewCase reviewCase) {
        // Simple load balancing - select analyst with lowest workload
        // In production, this would consider analyst expertise, shifts, etc.
        return analystWorkload.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private void notifyAnalystAssignment(ManualReviewCase reviewCase, String analyst) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("event_type", "CASE_ASSIGNED");
        notification.put("case_id", reviewCase.getCaseId());
        notification.put("analyst", analyst);
        notification.put("priority", reviewCase.getPriority());
        notification.put("sla_deadline", reviewCase.getSlaDeadline());

        kafkaTemplate.send(reviewAlertTopic, "assignment", notification);
    }

    private void publishReviewCaseCreated(ManualReviewCase reviewCase) {
        Map<String, Object> event = new HashMap<>();
        event.put("event_type", "CASE_CREATED");
        event.put("case_id", reviewCase.getCaseId());
        event.put("transaction_id", reviewCase.getTransactionId());
        event.put("priority", reviewCase.getPriority());
        event.put("sla_deadline", reviewCase.getSlaDeadline());
        event.put("created_at", reviewCase.getCreatedAt());

        kafkaTemplate.send(reviewAlertTopic, "created", event);
    }

    private void publishReviewCompleted(ManualReviewCase reviewCase) {
        Map<String, Object> event = new HashMap<>();
        event.put("event_type", "CASE_COMPLETED");
        event.put("case_id", reviewCase.getCaseId());
        event.put("decision", reviewCase.getDecision());
        event.put("review_time_minutes", reviewCase.getReviewTimeMinutes());
        event.put("sla_violated", reviewCase.isSlaViolated());

        kafkaTemplate.send(reviewAlertTopic, "completed", event);
    }

    private void publishCaseEscalated(ManualReviewCase reviewCase, ReviewPriority oldPriority, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("event_type", "CASE_ESCALATED");
        event.put("case_id", reviewCase.getCaseId());
        event.put("old_priority", oldPriority);
        event.put("new_priority", reviewCase.getPriority());
        event.put("reason", reason);
        event.put("new_sla_deadline", reviewCase.getSlaDeadline());

        kafkaTemplate.send(reviewAlertTopic, "escalated", event);
    }

    private void publishSlaViolationAlert(ManualReviewCase reviewCase) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("event_type", "SLA_VIOLATION");
        alert.put("case_id", reviewCase.getCaseId());
        alert.put("priority", reviewCase.getPriority());
        alert.put("sla_deadline", reviewCase.getSlaDeadline());
        alert.put("assigned_to", reviewCase.getAssignedTo());
        alert.put("created_at", reviewCase.getCreatedAt());

        kafkaTemplate.send(reviewAlertTopic, "sla_violation", alert);
    }

    private void recordCaseCreationMetrics(ReviewPriority priority) {
        meterRegistry.counter("fraud.review.cases.created",
            "priority", priority.name()).increment();
    }

    private void recordReviewCompletionMetrics(ManualReviewCase reviewCase, boolean slaViolated) {
        Timer.Sample sample = Timer.start(meterRegistry);

        meterRegistry.counter("fraud.review.cases.completed",
            "priority", reviewCase.getPriority().name(),
            "decision", reviewCase.getDecision().name(),
            "sla_violated", String.valueOf(slaViolated)
        ).increment();

        if (reviewCase.getReviewTimeMinutes() != null) {
            meterRegistry.timer("fraud.review.review_time",
                "priority", reviewCase.getPriority().name()
            ).record(Duration.ofMinutes(reviewCase.getReviewTimeMinutes()));
        }
    }

    // ============================================================================
    // ENUMS AND DATA CLASSES
    // ============================================================================

    public enum ReviewPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum ReviewStatus {
        PENDING,
        ASSIGNED,
        IN_PROGRESS,
        COMPLETED,
        ESCALATED,
        CANCELLED
    }

    public enum ReviewDecision {
        APPROVE,
        REJECT,
        ESCALATE,
        NEEDS_MORE_INFO,
        FALSE_POSITIVE,
        CONFIRMED_FRAUD
    }

    @lombok.Data
    @lombok.Builder
    public static class QueueStatistics {
        private long totalPending;
        private long totalAssigned;
        private long totalInProgress;
        private long completed24Hours;
        private long slaViolations24Hours;
        private double averageReviewTimeMinutes;
        private int activeAnalysts;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class AnalystPerformance {
        private String analystId;
        private long totalReviewed;
        private double averageReviewTimeMinutes;
        private long slaViolations;
        private double slaCompliancePercent;
        private int currentWorkload;
    }

    public static class ReviewQueueException extends RuntimeException {
        public ReviewQueueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
