package com.waqiti.currency.repository;

import com.waqiti.currency.model.ReviewStatus;
import com.waqiti.currency.model.TreasuryOperationsReview;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Treasury Operations Review Repository
 *
 * Stores failed currency conversions for treasury team review
 * In production, this would persist to a database
 */
@Slf4j
@Repository
public class TreasuryOperationsReviewRepository {

    // In-memory storage (in production, use JPA/database)
    private final Map<String, TreasuryOperationsReview> reviewStore = new ConcurrentHashMap<>();

    /**
     * Save review item
     */
    public void save(TreasuryOperationsReview review) {
        log.info("Saving treasury operations review: conversionId={} status={} priority={} correlationId={}",
                review.getConversionId(), review.getStatus(), review.getPriority(),
                review.getCorrelationId());

        reviewStore.put(review.getConversionId(), review);

        log.debug("Treasury review saved: conversionId={} total_reviews={}",
                review.getConversionId(), reviewStore.size());
    }

    /**
     * Find review by conversion ID
     */
    public TreasuryOperationsReview findByConversionId(String conversionId, String correlationId) {
        log.debug("Finding treasury review: conversionId={} correlationId={}",
                conversionId, correlationId);

        TreasuryOperationsReview review = reviewStore.get(conversionId);

        if (review != null) {
            log.debug("Treasury review found: conversionId={} status={} correlationId={}",
                    conversionId, review.getStatus(), correlationId);
        } else {
            log.debug("Treasury review not found: conversionId={} correlationId={}",
                    conversionId, correlationId);
        }

        return review;
    }

    /**
     * Find all pending reviews
     */
    public List<TreasuryOperationsReview> findPendingReviews(String correlationId) {
        log.debug("Finding pending treasury reviews: correlationId={}", correlationId);

        List<TreasuryOperationsReview> pendingReviews = reviewStore.values().stream()
                .filter(review -> review.getStatus() == ReviewStatus.PENDING_TREASURY_TEAM)
                .collect(Collectors.toList());

        log.debug("Found {} pending treasury reviews: correlationId={}",
                pendingReviews.size(), correlationId);

        return pendingReviews;
    }

    /**
     * Find reviews by status
     */
    public List<TreasuryOperationsReview> findByStatus(ReviewStatus status, String correlationId) {
        log.debug("Finding treasury reviews by status: status={} correlationId={}",
                status, correlationId);

        List<TreasuryOperationsReview> reviews = reviewStore.values().stream()
                .filter(review -> review.getStatus() == status)
                .collect(Collectors.toList());

        log.debug("Found {} treasury reviews with status {}: correlationId={}",
                reviews.size(), status, correlationId);

        return reviews;
    }

    /**
     * Find reviews by customer ID
     */
    public List<TreasuryOperationsReview> findByCustomerId(String customerId, String correlationId) {
        log.debug("Finding treasury reviews for customer: customerId={} correlationId={}",
                customerId, correlationId);

        List<TreasuryOperationsReview> reviews = reviewStore.values().stream()
                .filter(review -> customerId.equals(review.getCustomerId()))
                .collect(Collectors.toList());

        log.debug("Found {} treasury reviews for customer: customerId={} correlationId={}",
                reviews.size(), customerId, correlationId);

        return reviews;
    }

    /**
     * Update review status
     */
    public void updateStatus(String conversionId, ReviewStatus newStatus, String correlationId) {
        log.info("Updating treasury review status: conversionId={} newStatus={} correlationId={}",
                conversionId, newStatus, correlationId);

        TreasuryOperationsReview review = reviewStore.get(conversionId);

        if (review != null) {
            ReviewStatus oldStatus = review.getStatus();
            review.setStatus(newStatus);
            review.setReviewedAt(java.time.Instant.now());

            reviewStore.put(conversionId, review);

            log.info("Treasury review status updated: conversionId={} {}→{} correlationId={}",
                    conversionId, oldStatus, newStatus, correlationId);
        } else {
            log.warn("Cannot update status - review not found: conversionId={} correlationId={}",
                    conversionId, correlationId);
        }
    }

    /**
     * Delete review
     */
    public void delete(String conversionId, String correlationId) {
        log.info("Deleting treasury review: conversionId={} correlationId={}",
                conversionId, correlationId);

        TreasuryOperationsReview removed = reviewStore.remove(conversionId);

        if (removed != null) {
            log.info("Treasury review deleted: conversionId={} correlationId={}",
                    conversionId, correlationId);
        } else {
            log.warn("Cannot delete - review not found: conversionId={} correlationId={}",
                    conversionId, correlationId);
        }
    }

    /**
     * Get all reviews
     */
    public List<TreasuryOperationsReview> findAll(String correlationId) {
        log.debug("Finding all treasury reviews: correlationId={}", correlationId);

        List<TreasuryOperationsReview> allReviews = new ArrayList<>(reviewStore.values());

        log.debug("Found {} total treasury reviews: correlationId={}",
                allReviews.size(), correlationId);

        return allReviews;
    }

    /**
     * Count reviews by status
     */
    public long countByStatus(ReviewStatus status) {
        return reviewStore.values().stream()
                .filter(review -> review.getStatus() == status)
                .count();
    }

    /**
     * Get repository statistics
     */
    public RepositoryStats getStats() {
        Map<ReviewStatus, Long> statusCounts = reviewStore.values().stream()
                .collect(Collectors.groupingBy(
                    TreasuryOperationsReview::getStatus,
                    Collectors.counting()
                ));

        return new RepositoryStats(
                reviewStore.size(),
                statusCounts.getOrDefault(ReviewStatus.PENDING_TREASURY_TEAM, 0L),
                statusCounts.getOrDefault(ReviewStatus.IN_REVIEW, 0L),
                statusCounts.getOrDefault(ReviewStatus.RESOLVED, 0L),
                statusCounts.getOrDefault(ReviewStatus.ESCALATED, 0L)
        );
    }

    /**
     * Repository statistics record
     */
    public record RepositoryStats(
            int totalReviews,
            long pendingCount,
            long inReviewCount,
            long resolvedCount,
            long escalatedCount
    ) {}
}
