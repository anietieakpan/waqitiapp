package com.waqiti.expense.repository;

import com.waqiti.expense.domain.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Expense Category Repository - Production-ready with UUID types
 */
@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    // Find category by category ID (unique business key)
    Optional<ExpenseCategory> findByCategoryId(String categoryId);

    // Find all active categories
    @Query("SELECT ec FROM ExpenseCategory ec WHERE ec.isActive = true ORDER BY ec.name ASC")
    List<ExpenseCategory> findAllActive();

    // Find categories by parent category
    Optional<ExpenseCategory> findByParentCategory(ExpenseCategory parentCategory);

    // Find by category name
    Optional<ExpenseCategory> findByNameIgnoreCase(String name);

    // Check if category exists
    boolean existsByCategoryId(String categoryId);
}
