package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerLifecycle;
import com.waqiti.customer.entity.CustomerLifecycle.*;
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
 * Repository interface for CustomerLifecycle entity
 *
 * Provides data access methods for customer lifecycle management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerLifecycleRepository extends JpaRepository<CustomerLifecycle, UUID> {

    /**
     * Find lifecycle by lifecycle ID
     *
     * @param lifecycleId the unique lifecycle identifier
     * @return Optional containing the lifecycle if found
     */
    Optional<CustomerLifecycle> findByLifecycleId(String lifecycleId);

    /**
     * Find lifecycle by customer ID
     *
     * @param customerId the customer ID
     * @return Optional containing the lifecycle if found
     */
    Optional<CustomerLifecycle> findByCustomerId(String customerId);

    /**
     * Find customers by lifecycle stage
     *
     * @param lifecycleStage the lifecycle stage
     * @param pageable pagination information
     * @return page of lifecycles
     */
    Page<CustomerLifecycle> findByLifecycleStage(LifecycleStage lifecycleStage, Pageable pageable);

    /**
     * Find customers at risk
     *
     * @param pageable pagination information
     * @return page of at-risk customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.lifecycleStage = 'AT_RISK' ORDER BY l.churnProbability DESC")
    Page<CustomerLifecycle> findAtRiskCustomers(Pageable pageable);

    /**
     * Find dormant customers
     *
     * @param pageable pagination information
     * @return page of dormant customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.lifecycleStage = 'DORMANT' ORDER BY l.stageEnteredAt ASC")
    Page<CustomerLifecycle> findDormantCustomers(Pageable pageable);

    /**
     * Find churned customers
     *
     * @param pageable pagination information
     * @return page of churned customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.lifecycleStage = 'CHURNED' ORDER BY l.stageEnteredAt DESC")
    Page<CustomerLifecycle> findChurnedCustomers(Pageable pageable);

    /**
     * Find active customers
     *
     * @param pageable pagination information
     * @return page of active customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.lifecycleStage IN ('ACTIVE', 'REACTIVATED')")
    Page<CustomerLifecycle> findActiveCustomers(Pageable pageable);

    /**
     * Find customers by engagement level
     *
     * @param engagementLevel the engagement level
     * @param pageable pagination information
     * @return page of lifecycles
     */
    Page<CustomerLifecycle> findByEngagementLevel(EngagementLevel engagementLevel, Pageable pageable);

    /**
     * Find customers by retention priority
     *
     * @param retentionPriority the retention priority
     * @param pageable pagination information
     * @return page of lifecycles
     */
    Page<CustomerLifecycle> findByRetentionPriority(RetentionPriority retentionPriority, Pageable pageable);

    /**
     * Find customers with high churn risk
     *
     * @param threshold the churn probability threshold
     * @param pageable pagination information
     * @return page of high-risk customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.churnProbability >= :threshold ORDER BY l.churnProbability DESC")
    Page<CustomerLifecycle> findHighChurnRisk(@Param("threshold") BigDecimal threshold, Pageable pageable);

    /**
     * Find customers with critical retention priority
     *
     * @param pageable pagination information
     * @return page of critical retention customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.retentionPriority = 'CRITICAL' " +
           "ORDER BY l.churnProbability DESC")
    Page<CustomerLifecycle> findCriticalRetention(Pageable pageable);

    /**
     * Find customers in stage for longer than specified days
     *
     * @param lifecycleStage the lifecycle stage
     * @param daysThreshold the days threshold
     * @param pageable pagination information
     * @return page of lifecycles
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.lifecycleStage = :stage " +
           "AND l.stageDurationDays >= :daysThreshold ORDER BY l.stageDurationDays DESC")
    Page<CustomerLifecycle> findInStageForDays(
        @Param("stage") LifecycleStage lifecycleStage,
        @Param("daysThreshold") Integer daysThreshold,
        Pageable pageable
    );

    /**
     * Find customers who entered stage within date range
     *
     * @param lifecycleStage the lifecycle stage
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of lifecycles
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.lifecycleStage = :stage " +
           "AND l.stageEnteredAt BETWEEN :startDate AND :endDate")
    Page<CustomerLifecycle> findEnteredStageBetween(
        @Param("stage") LifecycleStage lifecycleStage,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find customers who transitioned from one stage to another
     *
     * @param fromStage the previous stage
     * @param toStage the current stage
     * @param pageable pagination information
     * @return page of lifecycles
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.previousStage = :fromStage AND l.lifecycleStage = :toStage")
    Page<CustomerLifecycle> findTransitionedFromTo(
        @Param("fromStage") LifecycleStage fromStage,
        @Param("toStage") LifecycleStage toStage,
        Pageable pageable
    );

    /**
     * Find low engagement customers
     *
     * @param pageable pagination information
     * @return page of low engagement customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.engagementLevel IN ('LOW', 'VERY_LOW', 'NONE') " +
           "ORDER BY l.engagementLevel DESC, l.stageEnteredAt ASC")
    Page<CustomerLifecycle> findLowEngagementCustomers(Pageable pageable);

    /**
     * Find high engagement customers
     *
     * @param pageable pagination information
     * @return page of high engagement customers
     */
    @Query("SELECT l FROM CustomerLifecycle l WHERE l.engagementLevel IN ('HIGH', 'VERY_HIGH') " +
           "ORDER BY l.engagementLevel ASC, l.lifecycleScore DESC")
    Page<CustomerLifecycle> findHighEngagementCustomers(Pageable pageable);

    /**
     * Count customers by lifecycle stage
     *
     * @param lifecycleStage the lifecycle stage
     * @return count of customers
     */
    long countByLifecycleStage(LifecycleStage lifecycleStage);

    /**
     * Count customers by engagement level
     *
     * @param engagementLevel the engagement level
     * @return count of customers
     */
    long countByEngagementLevel(EngagementLevel engagementLevel);

    /**
     * Count customers by retention priority
     *
     * @param retentionPriority the retention priority
     * @return count of customers
     */
    long countByRetentionPriority(RetentionPriority retentionPriority);

    /**
     * Count at-risk customers
     *
     * @return count of at-risk customers
     */
    @Query("SELECT COUNT(l) FROM CustomerLifecycle l WHERE l.lifecycleStage = 'AT_RISK'")
    long countAtRisk();

    /**
     * Count high churn risk customers
     *
     * @param threshold the churn probability threshold
     * @return count of high-risk customers
     */
    @Query("SELECT COUNT(l) FROM CustomerLifecycle l WHERE l.churnProbability >= :threshold")
    long countHighChurnRisk(@Param("threshold") BigDecimal threshold);

    /**
     * Get average lifecycle score
     *
     * @return average lifecycle score
     */
    @Query("SELECT AVG(l.lifecycleScore) FROM CustomerLifecycle l WHERE l.lifecycleScore IS NOT NULL")
    BigDecimal getAverageLifecycleScore();

    /**
     * Get average churn probability
     *
     * @return average churn probability
     */
    @Query("SELECT AVG(l.churnProbability) FROM CustomerLifecycle l WHERE l.churnProbability IS NOT NULL")
    BigDecimal getAverageChurnProbability();
}
