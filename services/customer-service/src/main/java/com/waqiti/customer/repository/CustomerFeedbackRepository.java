package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerFeedback;
import com.waqiti.customer.entity.CustomerFeedback.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerFeedback entity
 *
 * Provides data access methods for customer feedback management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, UUID> {

    /**
     * Find feedback by feedback ID
     *
     * @param feedbackId the unique feedback identifier
     * @return Optional containing the feedback if found
     */
    Optional<CustomerFeedback> findByFeedbackId(String feedbackId);

    /**
     * Find all feedback for a customer
     *
     * @param customerId the customer ID
     * @return list of feedback
     */
    List<CustomerFeedback> findByCustomerId(String customerId);

    /**
     * Find feedback by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of feedback
     */
    Page<CustomerFeedback> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find feedback by rating
     *
     * @param rating the rating (1-5)
     * @param pageable pagination information
     * @return page of feedback
     */
    Page<CustomerFeedback> findByRating(Integer rating, Pageable pageable);

    /**
     * Find feedback by rating range
     *
     * @param minRating minimum rating
     * @param maxRating maximum rating
     * @param pageable pagination information
     * @return page of feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.rating BETWEEN :minRating AND :maxRating " +
           "ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findByRatingRange(
        @Param("minRating") Integer minRating,
        @Param("maxRating") Integer maxRating,
        Pageable pageable
    );

    /**
     * Find high rating feedback (4-5)
     *
     * @param pageable pagination information
     * @return page of high rating feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.rating >= 4 ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findHighRatingFeedback(Pageable pageable);

    /**
     * Find low rating feedback (1-2)
     *
     * @param pageable pagination information
     * @return page of low rating feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.rating <= 2 ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findLowRatingFeedback(Pageable pageable);

    /**
     * Find feedback by sentiment
     *
     * @param sentiment the sentiment
     * @param pageable pagination information
     * @return page of feedback
     */
    Page<CustomerFeedback> findBySentiment(Sentiment sentiment, Pageable pageable);

    /**
     * Find positive feedback
     *
     * @param pageable pagination information
     * @return page of positive feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.sentiment = 'POSITIVE' ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findPositiveFeedback(Pageable pageable);

    /**
     * Find negative feedback
     *
     * @param pageable pagination information
     * @return page of negative feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.sentiment = 'NEGATIVE' ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findNegativeFeedback(Pageable pageable);

    /**
     * Find negative feedback for a customer
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of negative feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.customerId = :customerId AND f.sentiment = 'NEGATIVE' " +
           "ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findNegativeFeedbackByCustomer(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find feedback by type
     *
     * @param feedbackType the feedback type
     * @param pageable pagination information
     * @return page of feedback
     */
    Page<CustomerFeedback> findByFeedbackType(FeedbackType feedbackType, Pageable pageable);

    /**
     * Find feedback by source
     *
     * @param feedbackSource the feedback source
     * @param pageable pagination information
     * @return page of feedback
     */
    Page<CustomerFeedback> findByFeedbackSource(FeedbackSource feedbackSource, Pageable pageable);

    /**
     * Find feedback by category
     *
     * @param feedbackCategory the feedback category
     * @param pageable pagination information
     * @return page of feedback
     */
    Page<CustomerFeedback> findByFeedbackCategory(FeedbackCategory feedbackCategory, Pageable pageable);

    /**
     * Find NPS promoters (score 9-10)
     *
     * @param pageable pagination information
     * @return page of promoter feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.npsScore >= 9 ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findPromoters(Pageable pageable);

    /**
     * Find NPS detractors (score 0-6)
     *
     * @param pageable pagination information
     * @return page of detractor feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.npsScore <= 6 ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findDetractors(Pageable pageable);

    /**
     * Find NPS passives (score 7-8)
     *
     * @param pageable pagination information
     * @return page of passive feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.npsScore BETWEEN 7 AND 8 ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findPassives(Pageable pageable);

    /**
     * Find unresponded feedback
     *
     * @param pageable pagination information
     * @return page of unresponded feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.respondedAt IS NULL ORDER BY f.submittedAt ASC")
    Page<CustomerFeedback> findUnrespondedFeedback(Pageable pageable);

    /**
     * Find responded feedback
     *
     * @param pageable pagination information
     * @return page of responded feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.respondedAt IS NOT NULL ORDER BY f.respondedAt DESC")
    Page<CustomerFeedback> findRespondedFeedback(Pageable pageable);

    /**
     * Find feedback without action taken
     *
     * @param pageable pagination information
     * @return page of feedback without action
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.actionTaken IS NULL OR f.actionTaken = '' " +
           "ORDER BY f.submittedAt ASC")
    Page<CustomerFeedback> findWithoutAction(Pageable pageable);

    /**
     * Find feedback submitted within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE f.submittedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY f.submittedAt DESC")
    Page<CustomerFeedback> findBySubmittedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Search feedback by text
     *
     * @param searchTerm the search term
     * @param pageable pagination information
     * @return page of matching feedback
     */
    @Query("SELECT f FROM CustomerFeedback f WHERE " +
           "LOWER(f.feedbackText) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.responseText) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<CustomerFeedback> searchFeedback(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Count feedback by customer ID
     *
     * @param customerId the customer ID
     * @return count of feedback
     */
    long countByCustomerId(String customerId);

    /**
     * Count feedback by rating
     *
     * @param rating the rating
     * @return count of feedback
     */
    long countByRating(Integer rating);

    /**
     * Count feedback by sentiment
     *
     * @param sentiment the sentiment
     * @return count of feedback
     */
    long countBySentiment(Sentiment sentiment);

    /**
     * Count promoters
     *
     * @return count of promoters
     */
    @Query("SELECT COUNT(f) FROM CustomerFeedback f WHERE f.npsScore >= 9")
    long countPromoters();

    /**
     * Count detractors
     *
     * @return count of detractors
     */
    @Query("SELECT COUNT(f) FROM CustomerFeedback f WHERE f.npsScore <= 6")
    long countDetractors();

    /**
     * Count passives
     *
     * @return count of passives
     */
    @Query("SELECT COUNT(f) FROM CustomerFeedback f WHERE f.npsScore BETWEEN 7 AND 8")
    long countPassives();

    /**
     * Count unresponded feedback
     *
     * @return count of unresponded feedback
     */
    @Query("SELECT COUNT(f) FROM CustomerFeedback f WHERE f.respondedAt IS NULL")
    long countUnresponded();

    /**
     * Get average rating
     *
     * @return average rating
     */
    @Query("SELECT AVG(f.rating) FROM CustomerFeedback f WHERE f.rating IS NOT NULL")
    Double getAverageRating();

    /**
     * Get average NPS score
     *
     * @return average NPS score
     */
    @Query("SELECT AVG(f.npsScore) FROM CustomerFeedback f WHERE f.npsScore IS NOT NULL")
    Double getAverageNpsScore();

    /**
     * Calculate NPS (Net Promoter Score)
     *
     * @return NPS percentage
     */
    @Query("SELECT (CAST(SUM(CASE WHEN f.npsScore >= 9 THEN 1 ELSE 0 END) AS double) / COUNT(f) * 100) - " +
           "(CAST(SUM(CASE WHEN f.npsScore <= 6 THEN 1 ELSE 0 END) AS double) / COUNT(f) * 100) " +
           "FROM CustomerFeedback f WHERE f.npsScore IS NOT NULL")
    Double calculateNPS();
}
