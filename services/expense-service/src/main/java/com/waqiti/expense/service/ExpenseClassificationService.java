package com.waqiti.expense.service;

import com.waqiti.expense.domain.Expense;
import com.waqiti.expense.domain.ExpenseCategory;

/**
 * Service for automatic expense classification using ML
 */
public interface ExpenseClassificationService {

    /**
     * Classify expense and assign category
     */
    ExpenseCategory classifyExpense(Expense expense);

    /**
     * Get classification confidence score
     */
    double getClassificationConfidence(Expense expense);

    /**
     * Train classification model with historical data
     */
    void trainModel();

    /**
     * Get suggested categories for an expense
     */
    java.util.List<ExpenseCategory> getSuggestedCategories(Expense expense);
}
