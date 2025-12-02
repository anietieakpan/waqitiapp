package com.waqiti.expense.repository;

import com.waqiti.expense.domain.ExpenseAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Expense Attachment Repository
 */
@Repository
public interface ExpenseAttachmentRepository extends JpaRepository<ExpenseAttachment, UUID> {

    List<ExpenseAttachment> findByExpenseId(UUID expenseId);

    List<ExpenseAttachment> findByExpenseIdOrderByUploadedAtDesc(UUID expenseId);

    void deleteByExpenseId(UUID expenseId);

    boolean existsByExpenseId(UUID expenseId);
}
