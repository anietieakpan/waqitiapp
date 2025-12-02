package com.waqiti.expense.repository;

import com.waqiti.expense.domain.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Recurring Expense Repository
 */
@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    List<RecurringExpense> findByUserIdAndIsActive(UUID userId, boolean isActive);

    @Query("SELECT re FROM RecurringExpense re WHERE re.nextExecutionDate <= :date " +
           "AND re.isActive = true")
    List<RecurringExpense> findDueRecurringExpenses(@Param("date") LocalDateTime date);

    @Query("SELECT re FROM RecurringExpense re WHERE re.userId = :userId " +
           "AND re.isActive = true ORDER BY re.nextExecutionDate ASC")
    List<RecurringExpense> findActiveByUserId(@Param("userId") UUID userId);

    List<RecurringExpense> findByUserIdAndCategory(UUID userId, String category);
}
