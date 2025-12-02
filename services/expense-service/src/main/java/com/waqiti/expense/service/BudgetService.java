package com.waqiti.expense.service;

import com.waqiti.expense.domain.Budget;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for budget management and monitoring
 */
public interface BudgetService {

    /**
     * Create a new budget
     */
    Budget createBudget(Budget budget);

    /**
     * Update existing budget
     */
    Budget updateBudget(UUID budgetId, Budget budget);

    /**
     * Get budget by ID
     */
    Budget getBudgetById(UUID budgetId);

    /**
     * Get user's active budgets
     */
    List<Budget> getUserActiveBudgets(UUID userId);

    /**
     * Add expense amount to budget
     */
    void addExpenseToBudget(UUID budgetId, BigDecimal amount);

    /**
     * Remove expense amount from budget
     */
    void removeExpenseFromBudget(UUID budgetId, BigDecimal amount);

    /**
     * Check budget alerts and send notifications
     */
    void checkBudgetAlertsAndNotify(UUID budgetId);

    /**
     * Get budgets by category
     */
    List<Budget> getBudgetsByCategory(UUID userId, String categoryId);

    /**
     * Delete budget
     */
    void deleteBudget(UUID budgetId);

    /**
     * Renew recurring budgets
     */
    void renewRecurringBudgets();
}
