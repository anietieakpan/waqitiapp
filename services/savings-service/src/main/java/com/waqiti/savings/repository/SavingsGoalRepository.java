package com.waqiti.savings.repository;

import com.waqiti.savings.domain.SavingsGoal;
import com.waqiti.savings.domain.SavingsGoal.Category;
import com.waqiti.savings.domain.SavingsGoal.Priority;
import com.waqiti.savings.domain.SavingsGoal.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SavingsGoal entity operations.
 * Provides comprehensive query methods for goal management, tracking, and analytics.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID>,
                                                JpaSpecificationExecutor<SavingsGoal> {

    /**
     * Find all goals for a specific user.
     *
     * @param userId the user's UUID
     * @return list of savings goals ordered by priority and creation date
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId " +
           "ORDER BY sg.priority DESC, sg.createdAt DESC")
    List<SavingsGoal> findByUserId(@Param("userId") UUID userId);

    /**
     * Find goals by user ID with pagination.
     *
     * @param userId the user's UUID
     * @param pageable pagination parameters
     * @return paginated list of savings goals
     */
    Page<SavingsGoal> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find goals by user ID and status.
     *
     * @param userId the user's UUID
     * @param status the goal status (ACTIVE, COMPLETED, PAUSED, etc.)
     * @return list of goals matching criteria
     */
    List<SavingsGoal> findByUserIdAndStatus(UUID userId, Status status);

    /**
     * Find active goals for a user.
     * Most commonly used query for displaying user's current goals.
     *
     * @param userId the user's UUID
     * @return list of active savings goals ordered by priority
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId AND sg.status = 'ACTIVE' " +
           "ORDER BY sg.priority DESC, sg.targetDate ASC NULLS LAST")
    List<SavingsGoal> findActiveGoalsByUserId(@Param("userId") UUID userId);

    /**
     * Find goal with pessimistic write lock for concurrent update protection.
     * Use when making contributions to prevent race conditions.
     *
     * @param id the goal UUID
     * @return Optional containing the locked goal
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.id = :id")
    Optional<SavingsGoal> findByIdWithLock(@Param("id") UUID id);

    /**
     * Find goals by category.
     *
     * @param userId the user's UUID
     * @param category the goal category (VACATION, EMERGENCY, etc.)
     * @return list of goals in specified category
     */
    List<SavingsGoal> findByUserIdAndCategory(UUID userId, Category category);

    /**
     * Find goals by priority.
     *
     * @param userId the user's UUID
     * @param priority the goal priority (LOW, MEDIUM, HIGH, CRITICAL)
     * @return list of goals with specified priority
     */
    List<SavingsGoal> findByUserIdAndPriority(UUID userId, Priority priority);

    /**
     * Find completed goals within a date range.
     * Useful for achievement tracking and analytics.
     *
     * @param userId the user's UUID
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of completed goals
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId " +
           "AND sg.status = 'COMPLETED' " +
           "AND sg.completedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY sg.completedAt DESC")
    List<SavingsGoal> findCompletedGoalsBetween(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find goals that have reached their target amount.
     * Used for auto-completion processing.
     *
     * @return list of goals at or above target amount
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.status = 'ACTIVE' " +
           "AND sg.currentAmount >= sg.targetAmount")
    List<SavingsGoal> findGoalsReachedTarget();

    /**
     * Find overdue goals (past target date but not completed).
     * Useful for sending reminder notifications.
     *
     * @param now current timestamp
     * @return list of overdue goals
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.status = 'ACTIVE' " +
           "AND sg.targetDate < :now")
    List<SavingsGoal> findOverdueGoals(@Param("now") LocalDateTime now);

    /**
     * Find goals nearing target date (within specified days).
     * Used for sending approaching deadline notifications.
     *
     * @param daysAhead number of days to look ahead
     * @return list of goals nearing deadline
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.status = 'ACTIVE' " +
           "AND sg.targetDate BETWEEN CURRENT_TIMESTAMP AND :deadlineDate " +
           "AND sg.notificationsEnabled = true")
    List<SavingsGoal> findGoalsNearingDeadline(@Param("deadlineDate") LocalDateTime deadlineDate);

    /**
     * Find goals that need reminders.
     * Returns goals where next reminder date has passed.
     *
     * @param now current timestamp
     * @return list of goals needing reminders
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.status = 'ACTIVE' " +
           "AND sg.notificationsEnabled = true " +
           "AND sg.nextReminderAt IS NOT NULL " +
           "AND sg.nextReminderAt <= :now")
    List<SavingsGoal> findGoalsNeedingReminder(@Param("now") LocalDateTime now);

    /**
     * Find goals with auto-save enabled.
     *
     * @param userId the user's UUID
     * @return list of goals with automatic savings
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId " +
           "AND sg.status = 'ACTIVE' " +
           "AND sg.autoSaveEnabled = true")
    List<SavingsGoal> findGoalsWithAutoSave(@Param("userId") UUID userId);

    /**
     * Find high-priority goals that are on track.
     * "On track" means average contribution >= required monthly saving.
     *
     * @param userId the user's UUID
     * @return list of high-priority goals making good progress
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId " +
           "AND sg.status = 'ACTIVE' " +
           "AND sg.priority = 'HIGH' " +
           "AND sg.averageMonthlyContribution >= sg.requiredMonthlySaving")
    List<SavingsGoal> findHighPriorityGoalsOnTrack(@Param("userId") UUID userId);

    /**
     * Find goals behind schedule.
     * "Behind" means average contribution < required monthly saving.
     *
     * @param userId the user's UUID
     * @return list of goals behind schedule
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId " +
           "AND sg.status = 'ACTIVE' " +
           "AND sg.requiredMonthlySaving IS NOT NULL " +
           "AND (sg.averageMonthlyContribution IS NULL OR sg.averageMonthlyContribution < sg.requiredMonthlySaving)")
    List<SavingsGoal> findGoalsBehindSchedule(@Param("userId") UUID userId);

    /**
     * Count total goals for a user.
     * Used for enforcing max goals per user limit.
     *
     * @param userId the user's UUID
     * @return count of goals
     */
    Long countByUserId(UUID userId);

    /**
     * Count goals by status for a user.
     *
     * @param userId the user's UUID
     * @param status the goal status
     * @return count of goals with specified status
     */
    Long countByUserIdAndStatus(UUID userId, Status status);

    /**
     * Get total amount saved across all goals for a user.
     *
     * @param userId the user's UUID
     * @return sum of current amounts across all active goals
     */
    @Query("SELECT COALESCE(SUM(sg.currentAmount), 0) FROM SavingsGoal sg " +
           "WHERE sg.userId = :userId AND sg.status = 'ACTIVE'")
    BigDecimal getTotalSavedByUserId(@Param("userId") UUID userId);

    /**
     * Get total target amount across all active goals.
     *
     * @param userId the user's UUID
     * @return sum of target amounts
     */
    @Query("SELECT COALESCE(SUM(sg.targetAmount), 0) FROM SavingsGoal sg " +
           "WHERE sg.userId = :userId AND sg.status = 'ACTIVE'")
    BigDecimal getTotalTargetByUserId(@Param("userId") UUID userId);

    /**
     * Get total interest earned across all goals.
     *
     * @param userId the user's UUID
     * @return sum of interest earned
     */
    @Query("SELECT COALESCE(SUM(sg.interestEarned), 0) FROM SavingsGoal sg " +
           "WHERE sg.userId = :userId")
    BigDecimal getTotalInterestEarnedByUserId(@Param("userId") UUID userId);

    /**
     * Find shared goals (public visibility).
     * Used for social features and goal discovery.
     *
     * @param pageable pagination parameters
     * @return paginated list of public goals
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.visibility = 'PUBLIC' " +
           "AND sg.status IN ('ACTIVE', 'COMPLETED') " +
           "ORDER BY sg.progressPercentage DESC")
    Page<SavingsGoal> findPublicGoals(Pageable pageable);

    /**
     * Find goals shared with a specific user.
     *
     * @param sharedWithUserId the user UUID who has access
     * @return list of shared goals
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.isShared = true " +
           "AND FUNCTION('jsonb_exists', sg.sharedWith, CAST(:sharedWithUserId AS string)) = true")
    List<SavingsGoal> findGoalsSharedWithUser(@Param("sharedWithUserId") UUID sharedWithUserId);

    /**
     * Find goals by share code.
     * Used for goal sharing via unique code.
     *
     * @param shareCode the unique share code
     * @return Optional containing the goal if found
     */
    Optional<SavingsGoal> findByShareCode(String shareCode);

    /**
     * Get goal statistics by category for a user.
     * Returns count and total saved per category.
     *
     * @param userId the user's UUID
     * @return list of category statistics
     */
    @Query("SELECT NEW map(" +
           "sg.category as category, " +
           "COUNT(sg) as goalCount, " +
           "COALESCE(SUM(sg.currentAmount), 0) as totalSaved, " +
           "COALESCE(SUM(sg.targetAmount), 0) as totalTarget) " +
           "FROM SavingsGoal sg " +
           "WHERE sg.userId = :userId AND sg.status = 'ACTIVE' " +
           "GROUP BY sg.category")
    List<java.util.Map<String, Object>> getGoalStatisticsByCategory(@Param("userId") UUID userId);

    /**
     * Get overall goal progress metrics for a user.
     *
     * @param userId the user's UUID
     * @return goal progress statistics
     */
    @Query("SELECT NEW map(" +
           "COUNT(sg) as totalGoals, " +
           "SUM(CASE WHEN sg.status = 'ACTIVE' THEN 1 ELSE 0 END) as activeGoals, " +
           "SUM(CASE WHEN sg.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedGoals, " +
           "SUM(CASE WHEN sg.status = 'PAUSED' THEN 1 ELSE 0 END) as pausedGoals, " +
           "COALESCE(AVG(sg.progressPercentage), 0) as averageProgress, " +
           "COALESCE(SUM(sg.currentAmount), 0) as totalSaved, " +
           "COALESCE(SUM(sg.targetAmount), 0) as totalTarget) " +
           "FROM SavingsGoal sg WHERE sg.userId = :userId")
    Optional<java.util.Map<String, Object>> getGoalProgressMetrics(@Param("userId") UUID userId);

    /**
     * Find goals with longest active streak.
     * Used for gamification and achievement features.
     *
     * @param userId the user's UUID
     * @param limit maximum number of results
     * @return list of goals ordered by streak length
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId " +
           "AND sg.currentStreak > 0 " +
           "ORDER BY sg.currentStreak DESC")
    List<SavingsGoal> findGoalsWithLongestStreak(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find paused goals that can be resumed.
     *
     * @param userId the user's UUID
     * @return list of paused goals
     */
    @Query("SELECT sg FROM SavingsGoal sg WHERE sg.userId = :userId AND sg.status = 'PAUSED'")
    List<SavingsGoal> findPausedGoals(@Param("userId") UUID userId);

    /**
     * Soft delete all goals for a user (GDPR compliance).
     *
     * @param userId the user's UUID
     */
    @Query("UPDATE SavingsGoal sg SET sg.status = 'ARCHIVED', sg.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE sg.userId = :userId")
    void softDeleteAllByUserId(@Param("userId") UUID userId);
}
