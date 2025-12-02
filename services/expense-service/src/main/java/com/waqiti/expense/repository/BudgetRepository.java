package com.waqiti.expense.repository;

import com.waqiti.expense.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Budget Repository - Production-ready with UUID types
 */
@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    // Find active budgets by user ID
    @Query("SELECT b FROM Budget b WHERE b.userId = :userId AND b.status = 'ACTIVE'")
    List<Budget> findActiveByUserId(@Param("userId") UUID userId);

    // Find budgets by user ID and category (for checking budget coverage)
    @Query("SELECT b FROM Budget b JOIN b.categories c WHERE b.userId = :userId " +
           "AND c.categoryId = :categoryId AND b.status = 'ACTIVE'")
    List<Budget> findByUserIdAndCategoryId(@Param("userId") UUID userId,
                                           @Param("categoryId") String categoryId);

    // Find active budgets for a specific period
    @Query("SELECT b FROM Budget b WHERE b.userId = :userId " +
           "AND b.periodStart <= :date AND b.periodEnd >= :date " +
           "AND b.status = 'ACTIVE'")
    List<Budget> findActiveByUserIdAndPeriod(@Param("userId") UUID userId,
                                              @Param("date") LocalDate date);

    // Find expired recurring budgets that need renewal
    @Query("SELECT b FROM Budget b WHERE b.periodEnd < :date " +
           "AND b.isRecurring = true AND b.autoRenew = true " +
           "AND b.status = 'ACTIVE'")
    List<Budget> findExpiredRecurringBudgets(@Param("date") LocalDate date);

    // Find all budgets by user (for GDPR export)
    List<Budget> findByUserId(UUID userId);

    // Check if user has active budget for category
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
           "FROM Budget b JOIN b.categories c WHERE b.userId = :userId " +
           "AND c.categoryId = :categoryId AND b.status = 'ACTIVE'")
    boolean existsByUserIdAndCategoryId(@Param("userId") UUID userId,
                                        @Param("categoryId") String categoryId);
}
