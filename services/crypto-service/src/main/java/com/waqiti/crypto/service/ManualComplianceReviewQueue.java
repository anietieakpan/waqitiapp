package com.waqiti.crypto.service;

import com.waqiti.crypto.entity.ManualComplianceReview;
import com.waqiti.crypto.repository.ManualComplianceReviewRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manual Compliance Review Queue Service
 * Database-backed queue for transactions requiring manual compliance review
 * Supports priority-based review workflows with SLA tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualComplianceReviewQueue {

    private final ManualComplianceReviewRepository reviewRepository;
    private final MeterRegistry meterRegistry;

    private Counter reviewsQueued;
    private Counter reviewsCompleted;
    private Counter reviewsApproved;
    private Counter reviewsRejected;

    @javax.annotation.PostConstruct
    public void initMetrics() {
        reviewsQueued = Counter.builder("manual_compliance_reviews_queued_total")
                .description("Total manual reviews queued")
                .register(meterRegistry);
        reviewsCompleted = Counter.builder("manual_compliance_reviews_completed_total")
                .description("Total manual reviews completed")
                .register(meterRegistry);
        reviewsApproved = Counter.builder("manual_compliance_reviews_approved_total")
                .description("Total manual reviews approved")
                .register(meterRegistry);
        reviewsRejected = Counter.builder("manual_compliance_reviews_rejected_total")
                .description("Total manual reviews rejected")
                .register(meterRegistry);
    }

    /**
     * Queue transaction for manual compliance review
     */
    @Transactional
    public ManualComplianceReview queueForReview(
            String transactionId,
            String customerId,
            String reviewReason,
            String riskScore,
            String priority,
            String correlationId) {

        log.warn("Queueing transaction for manual compliance review: transaction={} customer={} reason={} priority={} correlationId={}",
                transactionId, customerId, reviewReason, priority, correlationId);

        // Check if already queued
        List<ManualComplianceReview> existing = reviewRepository
                .findByTransactionIdAndStatus(transactionId, "PENDING");

        if (!existing.isEmpty()) {
            log.warn("Transaction already queued for review: {} existing review: {} correlationId={}",
                    transactionId, existing.get(0).getId(), correlationId);
            return existing.get(0);
        }

        // Calculate SLA deadline based on priority
        Instant slaDeadline = calculateSlaDeadline(priority);

        ManualComplianceReview review = ManualComplianceReview.builder()
                .transactionId(transactionId)
                .customerId(customerId)
                .reviewReason(reviewReason)
                .riskScore(riskScore)
                .priority(priority)
                .status("PENDING")
                .correlationId(correlationId)
                .queuedAt(Instant.now())
                .slaDeadline(slaDeadline)
                .build();

        review = reviewRepository.save(review);

        reviewsQueued.increment();

        log.warn("Transaction queued for manual review: {} review ID: {} SLA deadline: {} correlationId: {}",
                transactionId, review.getId(), slaDeadline, correlationId);

        return review;
    }

    /**
     * Approve transaction after manual review
     */
    @Transactional
    public ManualComplianceReview approveReview(
            String reviewId,
            String reviewerId,
            String approvalComments,
            String correlationId) {

        log.info("Approving manual compliance review: {} reviewer: {} correlationId: {}",
                reviewId, reviewerId, correlationId);

        UUID id = UUID.fromString(reviewId);
        ManualComplianceReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        if (!"PENDING".equals(review.getStatus())) {
            throw new IllegalStateException("Review already completed: " + reviewId);
        }

        review.setStatus("APPROVED");
        review.setReviewedBy(reviewerId);
        review.setReviewedAt(Instant.now());
        review.setReviewComments(approvalComments);
        review.setDecision("APPROVED");

        review = reviewRepository.save(review);

        reviewsCompleted.increment();
        reviewsApproved.increment();

        log.info("Manual compliance review approved: {} transaction: {} reviewer: {} correlationId: {}",
                reviewId, review.getTransactionId(), reviewerId, correlationId);

        return review;
    }

    /**
     * Reject transaction after manual review
     */
    @Transactional
    public ManualComplianceReview rejectReview(
            String reviewId,
            String reviewerId,
            String rejectionReason,
            String correlationId) {

        log.warn("Rejecting manual compliance review: {} reviewer: {} reason: {} correlationId: {}",
                reviewId, reviewerId, rejectionReason, correlationId);

        UUID id = UUID.fromString(reviewId);
        ManualComplianceReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        if (!"PENDING".equals(review.getStatus())) {
            throw new IllegalStateException("Review already completed: " + reviewId);
        }

        review.setStatus("REJECTED");
        review.setReviewedBy(reviewerId);
        review.setReviewedAt(Instant.now());
        review.setReviewComments(rejectionReason);
        review.setDecision("REJECTED");

        review = reviewRepository.save(review);

        reviewsCompleted.increment();
        reviewsRejected.increment();

        log.warn("Manual compliance review rejected: {} transaction: {} reviewer: {} correlationId: {}",
                reviewId, review.getTransactionId(), reviewerId, correlationId);

        return review;
    }

    /**
     * Get pending reviews (for compliance team dashboard)
     */
    @Transactional(readOnly = true)
    public List<ManualComplianceReview> getPendingReviews() {
        return reviewRepository.findByStatus("PENDING");
    }

    /**
     * Get high-priority pending reviews
     */
    @Transactional(readOnly = true)
    public List<ManualComplianceReview> getHighPriorityReviews() {
        return reviewRepository.findByPriorityAndStatus("HIGH", "PENDING");
    }

    /**
     * Get overdue reviews (past SLA deadline)
     */
    @Transactional(readOnly = true)
    public List<ManualComplianceReview> getOverdueReviews() {
        return reviewRepository.findOverdueReviews(Instant.now());
    }

    /**
     * Get reviews for specific customer
     */
    @Transactional(readOnly = true)
    public List<ManualComplianceReview> getCustomerReviews(String customerId) {
        return reviewRepository.findByCustomerId(customerId);
    }

    /**
     * Get reviews for specific transaction
     */
    @Transactional(readOnly = true)
    public List<ManualComplianceReview> getTransactionReviews(String transactionId) {
        return reviewRepository.findByTransactionId(transactionId);
    }

    /**
     * Calculate SLA deadline based on priority
     */
    private Instant calculateSlaDeadline(String priority) {
        return switch (priority) {
            case "CRITICAL", "HIGH" -> Instant.now().plusSeconds(4 * 3600); // 4 hours
            case "MEDIUM" -> Instant.now().plusSeconds(24 * 3600); // 24 hours
            case "LOW" -> Instant.now().plusSeconds(72 * 3600); // 72 hours
            default -> Instant.now().plusSeconds(24 * 3600); // Default 24 hours
        };
    }

    /**
     * Assign review to specific reviewer
     */
    @Transactional
    public ManualComplianceReview assignReview(
            String reviewId,
            String reviewerId,
            String correlationId) {

        log.info("Assigning manual compliance review: {} to reviewer: {} correlationId: {}",
                reviewId, reviewerId, correlationId);

        UUID id = UUID.fromString(reviewId);
        ManualComplianceReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        if (!"PENDING".equals(review.getStatus())) {
            throw new IllegalStateException("Review already completed: " + reviewId);
        }

        review.setAssignedTo(reviewerId);
        review.setAssignedAt(Instant.now());

        review = reviewRepository.save(review);

        log.info("Review assigned: {} to reviewer: {} correlationId: {}",
                reviewId, reviewerId, correlationId);

        return review;
    }

    /**
     * Escalate overdue review
     */
    @Transactional
    public void escalateOverdueReview(String reviewId, String escalationReason) {
        log.warn("Escalating overdue compliance review: {} reason: {}", reviewId, escalationReason);

        UUID id = UUID.fromString(reviewId);
        ManualComplianceReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        review.setEscalated(true);
        review.setEscalatedAt(Instant.now());
        review.setEscalationReason(escalationReason);

        // Upgrade priority if not already critical
        if (!"CRITICAL".equals(review.getPriority())) {
            review.setPriority("CRITICAL");
        }

        reviewRepository.save(review);

        log.warn("Review escalated: {} transaction: {} reason: {}",
                reviewId, review.getTransactionId(), escalationReason);
    }
}
