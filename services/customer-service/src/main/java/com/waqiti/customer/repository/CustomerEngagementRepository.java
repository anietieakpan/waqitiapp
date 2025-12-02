package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerEngagement;
import com.waqiti.customer.entity.CustomerEngagement.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerEngagement entity
 *
 * Provides data access methods for customer engagement tracking.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerEngagementRepository extends JpaRepository<CustomerEngagement, UUID> {

    /**
     * Find engagement by engagement ID
     *
     * @param engagementId the unique engagement identifier
     * @return Optional containing the engagement if found
     */
    Optional<CustomerEngagement> findByEngagementId(String engagementId);

    /**
     * Find engagement by customer ID
     *
     * @param customerId the customer ID
     * @return Optional containing the engagement if found
     */
    Optional<CustomerEngagement> findByCustomerId(String customerId);

    /**
     * Find engagements by engagement tier
     *
     * @param engagementTier the engagement tier
     * @param pageable pagination information
     * @return page of engagements
     */
    Page<CustomerEngagement> findByEngagementTier(EngagementTier engagementTier, Pageable pageable);

    /**
     * Find highly engaged customers
     *
     * @param pageable pagination information
     * @return page of highly engaged customers
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.engagementTier = 'HIGH' ORDER BY e.engagementScore DESC")
    Page<CustomerEngagement> findHighlyEngagedCustomers(Pageable pageable);

    /**
     * Find low engagement customers
     *
     * @param pageable pagination information
     * @return page of low engagement customers
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.engagementTier = 'LOW' ORDER BY e.engagementScore ASC")
    Page<CustomerEngagement> findLowEngagementCustomers(Pageable pageable);

    /**
     * Find engagements by type
     *
     * @param engagementType the engagement type
     * @param pageable pagination information
     * @return page of engagements
     */
    Page<CustomerEngagement> findByEngagementType(EngagementType engagementType, Pageable pageable);

    /**
     * Find engagements by channel
     *
     * @param engagementChannel the engagement channel
     * @param pageable pagination information
     * @return page of engagements
     */
    Page<CustomerEngagement> findByEngagementChannel(EngagementChannel engagementChannel, Pageable pageable);

    /**
     * Find engagements by frequency
     *
     * @param engagementFrequency the engagement frequency
     * @param pageable pagination information
     * @return page of engagements
     */
    Page<CustomerEngagement> findByEngagementFrequency(EngagementFrequency engagementFrequency, Pageable pageable);

    /**
     * Find inactive customers
     *
     * @param pageable pagination information
     * @return page of inactive customers
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.engagementFrequency = 'INACTIVE' " +
           "ORDER BY e.lastEngagementDate ASC NULLS LAST")
    Page<CustomerEngagement> findInactiveCustomers(Pageable pageable);

    /**
     * Find customers by engagement score range
     *
     * @param minScore minimum engagement score
     * @param maxScore maximum engagement score
     * @param pageable pagination information
     * @return page of engagements
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.engagementScore BETWEEN :minScore AND :maxScore " +
           "ORDER BY e.engagementScore DESC")
    Page<CustomerEngagement> findByEngagementScoreRange(
        @Param("minScore") BigDecimal minScore,
        @Param("maxScore") BigDecimal maxScore,
        Pageable pageable
    );

    /**
     * Find customers with high response rate
     *
     * @param threshold the response rate threshold
     * @param pageable pagination information
     * @return page of engagements
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.responseRate >= :threshold ORDER BY e.responseRate DESC")
    Page<CustomerEngagement> findHighResponseRate(@Param("threshold") BigDecimal threshold, Pageable pageable);

    /**
     * Find customers with high click-through rate
     *
     * @param threshold the click-through rate threshold
     * @param pageable pagination information
     * @return page of engagements
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.clickThroughRate >= :threshold " +
           "ORDER BY e.clickThroughRate DESC")
    Page<CustomerEngagement> findHighClickThroughRate(@Param("threshold") BigDecimal threshold, Pageable pageable);

    /**
     * Find customers with high conversion rate
     *
     * @param threshold the conversion rate threshold
     * @param pageable pagination information
     * @return page of engagements
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.conversionRate >= :threshold ORDER BY e.conversionRate DESC")
    Page<CustomerEngagement> findHighConversionRate(@Param("threshold") BigDecimal threshold, Pageable pageable);

    /**
     * Find recent engagements (last N days)
     *
     * @param since the date threshold
     * @param pageable pagination information
     * @return page of recent engagements
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.lastEngagementDate >= :since " +
           "ORDER BY e.lastEngagementDate DESC")
    Page<CustomerEngagement> findRecentEngagements(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Find customers with no recent engagement
     *
     * @param since the date threshold
     * @param pageable pagination information
     * @return page of engagements
     */
    @Query("SELECT e FROM CustomerEngagement e WHERE e.lastEngagementDate < :since OR e.lastEngagementDate IS NULL " +
           "ORDER BY e.lastEngagementDate ASC NULLS FIRST")
    Page<CustomerEngagement> findNoRecentEngagement(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Find top engaged customers by interaction count
     *
     * @param pageable pagination information
     * @return page of engagements
     */
    @Query("SELECT e FROM CustomerEngagement e ORDER BY e.interactionCount DESC")
    Page<CustomerEngagement> findTopByInteractionCount(Pageable pageable);

    /**
     * Count engagements by tier
     *
     * @param engagementTier the engagement tier
     * @return count of engagements
     */
    long countByEngagementTier(EngagementTier engagementTier);

    /**
     * Count engagements by frequency
     *
     * @param engagementFrequency the engagement frequency
     * @return count of engagements
     */
    long countByEngagementFrequency(EngagementFrequency engagementFrequency);

    /**
     * Count highly engaged customers
     *
     * @return count of highly engaged customers
     */
    @Query("SELECT COUNT(e) FROM CustomerEngagement e WHERE e.engagementTier = 'HIGH'")
    long countHighlyEngaged();

    /**
     * Count low engagement customers
     *
     * @return count of low engagement customers
     */
    @Query("SELECT COUNT(e) FROM CustomerEngagement e WHERE e.engagementTier = 'LOW'")
    long countLowEngagement();

    /**
     * Count inactive customers
     *
     * @return count of inactive customers
     */
    @Query("SELECT COUNT(e) FROM CustomerEngagement e WHERE e.engagementFrequency = 'INACTIVE'")
    long countInactive();

    /**
     * Get average engagement score
     *
     * @return average engagement score
     */
    @Query("SELECT AVG(e.engagementScore) FROM CustomerEngagement e WHERE e.engagementScore IS NOT NULL")
    BigDecimal getAverageEngagementScore();

    /**
     * Get average response rate
     *
     * @return average response rate
     */
    @Query("SELECT AVG(e.responseRate) FROM CustomerEngagement e WHERE e.responseRate IS NOT NULL")
    BigDecimal getAverageResponseRate();

    /**
     * Get average click-through rate
     *
     * @return average click-through rate
     */
    @Query("SELECT AVG(e.clickThroughRate) FROM CustomerEngagement e WHERE e.clickThroughRate IS NOT NULL")
    BigDecimal getAverageClickThroughRate();

    /**
     * Get average conversion rate
     *
     * @return average conversion rate
     */
    @Query("SELECT AVG(e.conversionRate) FROM CustomerEngagement e WHERE e.conversionRate IS NOT NULL")
    BigDecimal getAverageConversionRate();

    /**
     * Get total interaction count
     *
     * @return total interaction count
     */
    @Query("SELECT COALESCE(SUM(e.interactionCount), 0) FROM CustomerEngagement e")
    Long getTotalInteractionCount();
}
