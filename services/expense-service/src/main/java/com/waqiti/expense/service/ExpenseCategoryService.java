package com.waqiti.expense.service;

import com.waqiti.expense.domain.ExpenseCategory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing expense categories
 */
public interface ExpenseCategoryService {

    /**
     * Get all active categories
     */
    List<ExpenseCategory> getAllActiveCategories();

    /**
     * Get category by ID
     */
    Optional<ExpenseCategory> getCategoryById(String categoryId);

    /**
     * Create new category
     */
    ExpenseCategory createCategory(ExpenseCategory category);

    /**
     * Update category
     */
    ExpenseCategory updateCategory(String categoryId, ExpenseCategory category);

    /**
     * Delete category (soft delete)
     */
    void deleteCategory(String categoryId);

    /**
     * Get category hierarchy
     */
    List<ExpenseCategory> getCategoryHierarchy();
}
