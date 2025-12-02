package com.waqiti.savings.repository;

import com.waqiti.savings.domain.SavingsContribution;
import com.waqiti.savings.domain.SavingsContribution.ContributionSource;
import com.waqiti.savings.domain.SavingsContribution.ContributionType;
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
 * Repository for SavingsContribution entity operations.
 * Tracks all contributions made to savings goals.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface SavingsContributionRepository extends JpaRepository<SavingsContribution, UUID> {

    /**
     * Find all contributions for a specific goal.
     *
     * @param goalId the savings goal UUID
     * @param pageable pagination parameters
     * @return paginated list of contributions
     */
    Page<SavingsContribution> findByGoalId(UUID goalId, Pageable pageable);

    /**
     * Find contributions for a goal ordered by contribution date.
     *
     * @param goalId the savings goal UUID
     * @return list of contributions ordered newest first
     */
    @Query("SELECT sc FROM SavingsContribution sc WHERE sc.goalId = :goalId ORDER BY sc.contributionDate DESC")
    List<SavingsContribution> findByGoalIdOrderByContributionDateDesc(@Param("goalId") UUID goalId);

    /**
     * Find all contributions by a user across all goals.
     *
     * @param userId the user's UUID
     * @param pageable pagination parameters
     * @return paginated list of contributions
     */
    Page<SavingsContribution> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find contributions within a date range for a goal.
     *
     * @param goalId the savings goal UUID
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of contributions in date range
     */
    @Query("SELECT sc FROM SavingsContribution sc WHERE sc.goalId = :goalId " +
           "AND sc.contributionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY sc.contributionDate DESC")
    List<SavingsContribution> findByGoalIdAndDateRange(
            @Param("goalId") UUID goalId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find contributions by type for a goal.
     *
     * @param goalId the savings goal UUID
     * @param type the contribution type (MANUAL, AUTO_SAVE, ROUND_UP, etc.)
     * @return list of contributions of specified type
     */
    List<SavingsContribution> findByGoalIdAndType(UUID goalId, ContributionType type);

    /**
     * Find contributions by source.
     *
     * @param goalId the savings goal UUID
     * @param source the contribution source (WALLET, BANK_TRANSFER, etc.)
     * @return list of contributions from specified source
     */
    List<SavingsContribution> findByGoalIdAndSource(UUID goalId, ContributionSource source);

    /**
     * Get total contributions for a goal.
     *
     * @param goalId the savings goal UUID
     * @return sum of all contribution amounts
     */
    @Query("SELECT COALESCE(SUM(sc.amount), 0) FROM SavingsContribution sc WHERE sc.goalId = :goalId")
    BigDecimal getTotalContributionsByGoalId(@Param("goalId") UUID goalId);

    /**
     * Get total contributions within a date range.
     *
     * @param goalId the savings goal UUID
     * @param startDate start of date range
     * @param endDate end of date range
     * @return sum of contributions in date range
     */
    @Query("SELECT COALESCE(SUM(sc.amount), 0) FROM SavingsContribution sc " +
           "WHERE sc.goalId = :goalId " +
           "AND sc.contributionDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalContributionsInDateRange(
            @Param("goalId") UUID goalId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count total contributions for a goal.
     *
     * @param goalId the savings goal UUID
     * @return number of contributions
     */
    Long countByGoalId(UUID goalId);

    /**
     * Find latest contribution for a goal.
     *
     * @param goalId the savings goal UUID
     * @return Optional containing most recent contribution
     */
    @Query("SELECT sc FROM SavingsContribution sc WHERE sc.goalId = :goalId " +
           "ORDER BY sc.contributionDate DESC LIMIT 1")
    Optional<SavingsContribution> findLatestContributionByGoalId(@Param("goalId") UUID goalId);

    /**
     * Get average contribution amount for a goal.
     *
     * @param goalId the savings goal UUID
     * @return average contribution amount
     */
    @Query("SELECT COALESCE(AVG(sc.amount), 0) FROM SavingsContribution sc WHERE sc.goalId = :goalId")
    BigDecimal getAverageContributionByGoalId(@Param("goalId") UUID goalId);

    /**
     * Find largest single contribution for a goal.
     *
     * @param goalId the savings goal UUID
     * @return Optional containing largest contribution
     */
    @Query("SELECT sc FROM SavingsContribution sc WHERE sc.goalId = :goalId " +
           "ORDER BY sc.amount DESC LIMIT 1")
    Optional<SavingsContribution> findLargestContributionByGoalId(@Param("goalId") UUID goalId);

    /**
     * Find contributions for monthly streak calculation.
     * Returns contributions grouped by month.
     *
     * @param goalId the savings goal UUID
     * @param monthsBack number of months to look back
     * @return list of monthly contribution data
     */
    @Query("SELECT NEW map(" +
           "FUNCTION('YEAR', sc.contributionDate) as year, " +
           "FUNCTION('MONTH', sc.contributionDate) as month, " +
           "COUNT(sc) as contributionCount, " +
           "SUM(sc.amount) as totalAmount) " +
           "FROM SavingsContribution sc " +
           "WHERE sc.goalId = :goalId " +
           "AND sc.contributionDate >= :sinceDate " +
           "GROUP BY FUNCTION('YEAR', sc.contributionDate), FUNCTION('MONTH', sc.contributionDate) " +
           "ORDER BY year DESC, month DESC")
    List<java.util.Map<String, Object>> getMonthlyContributions(
            @Param("goalId") UUID goalId,
            @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find all round-up contributions for a goal.
     * Used for tracking automated round-up savings.
     *
     * @param goalId the savings goal UUID
     * @return list of round-up contributions
     */
    @Query("SELECT sc FROM SavingsContribution sc WHERE sc.goalId = :goalId " +
           "AND sc.type = 'ROUND_UP' " +
           "ORDER BY sc.contributionDate DESC")
    List<SavingsContribution> findRoundUpContributions(@Param("goalId") UUID goalId);

    /**
     * Get contribution statistics by type for a goal.
     *
     * @param goalId the savings goal UUID
     * @return statistics grouped by contribution type
     */
    @Query("SELECT NEW map(" +
           "sc.type as contributionType, " +
           "COUNT(sc) as count, " +
           "COALESCE(SUM(sc.amount), 0) as totalAmount, " +
           "COALESCE(AVG(sc.amount), 0) as averageAmount) " +
           "FROM SavingsContribution sc " +
           "WHERE sc.goalId = :goalId " +
           "GROUP BY sc.type")
    List<java.util.Map<String, Object>> getContributionStatisticsByType(@Param("goalId") UUID goalId);

    /**
     * Find contributions for a user across all goals within date range.
     *
     * @param userId the user's UUID
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of contributions
     */
    @Query("SELECT sc FROM SavingsContribution sc WHERE sc.userId = :userId " +
           "AND sc.contributionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY sc.contributionDate DESC")
    List<SavingsContribution> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get total contributions by a user across all goals.
     *
     * @param userId the user's UUID
     * @return sum of all contributions
     */
    @Query("SELECT COALESCE(SUM(sc.amount), 0) FROM SavingsContribution sc WHERE sc.userId = :userId")
    BigDecimal getTotalContributionsByUserId(@Param("userId") UUID userId);

    /**
     * Delete all contributions for a goal (GDPR compliance).
     * NOTE: This is hard delete. Consider soft delete in production.
     *
     * @param goalId the savings goal UUID
     */
    void deleteByGoalId(UUID goalId);
}
