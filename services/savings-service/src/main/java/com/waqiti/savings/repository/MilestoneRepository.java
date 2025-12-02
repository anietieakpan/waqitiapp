package com.waqiti.savings.repository;

import com.waqiti.savings.domain.Milestone;
import com.waqiti.savings.domain.Milestone.MilestoneStatus;
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
 * Repository for Milestone entity operations.
 * Manages goal milestones and achievement tracking.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    /**
     * Find all milestones for a specific savings goal.
     *
     * @param goalId the savings goal UUID
     * @return list of milestones ordered by target percentage
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId ORDER BY m.targetPercentage ASC")
    List<Milestone> findByGoalId(@Param("goalId") UUID goalId);

    /**
     * Find milestones by status for a goal.
     *
     * @param goalId the savings goal UUID
     * @param status the milestone status
     * @return list of milestones with specified status
     */
    List<Milestone> findByGoalIdAndStatus(UUID goalId, MilestoneStatus status);

    /**
     * Find pending (not achieved) milestones for a goal.
     *
     * @param goalId the savings goal UUID
     * @return list of pending milestones ordered by target percentage
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.status = 'PENDING' " +
           "ORDER BY m.targetPercentage ASC")
    List<Milestone> findPendingMilestones(@Param("goalId") UUID goalId);

    /**
     * Find achieved milestones for a goal.
     *
     * @param goalId the savings goal UUID
     * @return list of achieved milestones ordered by achievement date
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.status = 'ACHIEVED' " +
           "ORDER BY m.achievedAt DESC")
    List<Milestone> findAchievedMilestones(@Param("goalId") UUID goalId);

    /**
     * Find next pending milestone for a goal.
     * Returns the lowest percentage pending milestone.
     *
     * @param goalId the savings goal UUID
     * @return Optional containing next milestone to achieve
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.status = 'PENDING' " +
           "ORDER BY m.targetPercentage ASC LIMIT 1")
    Optional<Milestone> findNextPendingMilestone(@Param("goalId") UUID goalId);

    /**
     * Find milestones that should be marked as achieved.
     * Returns pending milestones where current progress >= target percentage.
     *
     * @param goalId the savings goal UUID
     * @param currentPercentage current goal progress percentage
     * @return list of milestones to mark as achieved
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.status = 'PENDING' " +
           "AND m.targetPercentage <= :currentPercentage")
    List<Milestone> findMilestonesReadyForAchievement(
            @Param("goalId") UUID goalId,
            @Param("currentPercentage") BigDecimal currentPercentage);

    /**
     * Count total milestones for a goal.
     *
     * @param goalId the savings goal UUID
     * @return number of milestones
     */
    Long countByGoalId(UUID goalId);

    /**
     * Count achieved milestones for a goal.
     *
     * @param goalId the savings goal UUID
     * @return number of achieved milestones
     */
    Long countByGoalIdAndStatus(UUID goalId, MilestoneStatus status);

    /**
     * Get completion percentage for a goal's milestones.
     * Returns ratio of achieved to total milestones.
     *
     * @param goalId the savings goal UUID
     * @return milestone completion statistics
     */
    @Query("SELECT NEW map(" +
           "COUNT(m) as totalMilestones, " +
           "SUM(CASE WHEN m.status = 'ACHIEVED' THEN 1 ELSE 0 END) as achievedMilestones, " +
           "SUM(CASE WHEN m.status = 'PENDING' THEN 1 ELSE 0 END) as pendingMilestones) " +
           "FROM Milestone m WHERE m.goalId = :goalId")
    Optional<java.util.Map<String, Object>> getMilestoneStatistics(@Param("goalId") UUID goalId);

    /**
     * Find most recently achieved milestone for a goal.
     *
     * @param goalId the savings goal UUID
     * @return Optional containing latest achieved milestone
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.status = 'ACHIEVED' " +
           "ORDER BY m.achievedAt DESC LIMIT 1")
    Optional<Milestone> findLatestAchievedMilestone(@Param("goalId") UUID goalId);

    /**
     * Find milestones achieved within a date range.
     * Useful for achievement notifications and rewards.
     *
     * @param goalId the savings goal UUID
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of milestones achieved in date range
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.status = 'ACHIEVED' " +
           "AND m.achievedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY m.achievedAt DESC")
    List<Milestone> findMilestonesAchievedBetween(
            @Param("goalId") UUID goalId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find all milestones for a user across all goals.
     *
     * @param userId the user's UUID
     * @return list of milestones
     */
    @Query("SELECT m FROM Milestone m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<Milestone> findByUserId(@Param("userId") UUID userId);

    /**
     * Find recently achieved milestones for a user (last 30 days).
     * Used for achievement feed and notifications.
     *
     * @param userId the user's UUID
     * @param since date threshold
     * @return list of recent achievements
     */
    @Query("SELECT m FROM Milestone m WHERE m.userId = :userId " +
           "AND m.status = 'ACHIEVED' " +
           "AND m.achievedAt >= :since " +
           "ORDER BY m.achievedAt DESC")
    List<Milestone> findRecentAchievements(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    /**
     * Get total achievement amount for a user.
     * Sum of all achievement amounts across achieved milestones.
     *
     * @param userId the user's UUID
     * @return total achievement value
     */
    @Query("SELECT COALESCE(SUM(m.achievementAmount), 0) FROM Milestone m " +
           "WHERE m.userId = :userId AND m.status = 'ACHIEVED'")
    BigDecimal getTotalAchievementAmountByUserId(@Param("userId") UUID userId);

    /**
     * Find custom (user-created) milestones for a goal.
     *
     * @param goalId the savings goal UUID
     * @return list of custom milestones
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.isCustom = true " +
           "ORDER BY m.targetPercentage ASC")
    List<Milestone> findCustomMilestones(@Param("goalId") UUID goalId);

    /**
     * Find default (system-generated) milestones for a goal.
     *
     * @param goalId the savings goal UUID
     * @return list of default milestones
     */
    @Query("SELECT m FROM Milestone m WHERE m.goalId = :goalId " +
           "AND m.isCustom = false " +
           "ORDER BY m.targetPercentage ASC")
    List<Milestone> findDefaultMilestones(@Param("goalId") UUID goalId);

    /**
     * Check if a milestone at specific percentage exists for a goal.
     * Used to prevent duplicate milestone creation.
     *
     * @param goalId the savings goal UUID
     * @param targetPercentage the target percentage
     * @return true if milestone exists
     */
    boolean existsByGoalIdAndTargetPercentage(UUID goalId, BigDecimal targetPercentage);

    /**
     * Delete all milestones for a goal.
     *
     * @param goalId the savings goal UUID
     */
    void deleteByGoalId(UUID goalId);

    /**
     * Delete all milestones for a user (GDPR compliance).
     *
     * @param userId the user's UUID
     */
    void deleteByUserId(UUID userId);
}
