package com.waqiti.expense.service;

import com.waqiti.expense.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service interface for expense management operations
 */
public interface ExpenseService {

    /**
     * Create a new expense
     *
     * @param request expense creation request
     * @return created expense
     */
    ExpenseResponseDto createExpense(CreateExpenseRequestDto request);

    /**
     * Upload receipt for an expense
     *
     * @param expenseId expense ID
     * @param file receipt file
     * @param ocrEnabled whether to process receipt with OCR
     * @return receipt upload response
     */
    ReceiptUploadResponseDto uploadReceipt(UUID expenseId, MultipartFile file, boolean ocrEnabled);

    /**
     * Get user expenses with filters
     *
     * @param filter expense filter criteria
     * @param pageable pagination parameters
     * @return page of expenses
     */
    Page<ExpenseResponseDto> getUserExpenses(ExpenseFilterDto filter, Pageable pageable);

    /**
     * Get expense by ID
     *
     * @param expenseId expense ID
     * @return expense details
     */
    ExpenseResponseDto getExpenseById(UUID expenseId);

    /**
     * Update an existing expense
     *
     * @param expenseId expense ID
     * @param request update request
     * @return updated expense
     */
    ExpenseResponseDto updateExpense(UUID expenseId, UpdateExpenseRequestDto request);

    /**
     * Submit expense for approval
     *
     * @param expenseId expense ID
     * @return updated expense
     */
    ExpenseResponseDto submitExpense(UUID expenseId);

    /**
     * Approve or reject an expense
     *
     * @param expenseId expense ID
     * @param request approval decision
     * @return updated expense
     */
    ExpenseResponseDto approveExpense(UUID expenseId, ApproveExpenseRequestDto request);

    /**
     * Delete an expense
     *
     * @param expenseId expense ID
     */
    void deleteExpense(UUID expenseId);

    /**
     * Get expense summary for a period
     *
     * @param start period start
     * @param end period end
     * @return expense summary
     */
    ExpenseSummaryDto getExpenseSummary(LocalDateTime start, LocalDateTime end);

    /**
     * Get available expense categories
     *
     * @return list of category names
     */
    List<String> getExpenseCategories();

    /**
     * Get expense analytics
     *
     * @param months number of months to analyze
     * @return analytics data
     */
    ExpenseAnalyticsDto getExpenseAnalytics(int months);

    /**
     * Bulk import expenses from file
     *
     * @param file CSV file with expenses
     * @param skipValidation whether to skip validation
     * @return import results
     */
    BulkImportResponseDto bulkImportExpenses(MultipartFile file, boolean skipValidation);

    /**
     * Get expenses pending approval
     *
     * @param pageable pagination parameters
     * @return page of expenses
     */
    Page<ExpenseResponseDto> getExpensesPendingApproval(Pageable pageable);

    /**
     * Get team expense summary (for managers)
     *
     * @param start period start
     * @param end period end
     * @return team summary
     */
    Map<String, Object> getTeamExpenseSummary(LocalDateTime start, LocalDateTime end);

    /**
     * Calculate mileage expense
     *
     * @param distance distance traveled
     * @param unit distance unit (MILES, KILOMETERS)
     * @param vehicleType vehicle type
     * @return mileage calculation
     */
    MileageCalculationDto calculateMileageExpense(BigDecimal distance, String unit, String vehicleType);
}
