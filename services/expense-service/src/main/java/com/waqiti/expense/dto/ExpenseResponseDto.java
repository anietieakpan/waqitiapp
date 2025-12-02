package com.waqiti.expense.dto;

import com.waqiti.expense.domain.enums.ExpenseStatus;
import com.waqiti.expense.domain.enums.ExpenseType;
import com.waqiti.expense.domain.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for expense data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponseDto {

    private String id;
    private String userId;
    private String transactionId;
    private String description;
    private BigDecimal amount;
    private String currency;
    private LocalDate expenseDate;

    // Category and Budget
    private String categoryId;
    private String categoryName;
    private String budgetId;
    private String budgetName;

    // Type and Status
    private ExpenseType expenseType;
    private ExpenseStatus status;
    private PaymentMethod paymentMethod;

    // Merchant and Location
    private String merchantName;
    private String merchantCategory;
    private String merchantId;
    private String locationCity;
    private String locationCountry;
    private Double latitude;
    private Double longitude;

    // Flags
    private Boolean isRecurring;
    private String recurringFrequency;
    private String parentExpenseId;
    private Boolean isReimbursable;
    private String reimbursementStatus;
    private Boolean isBusinessExpense;
    private Boolean taxDeductible;

    // Classification
    private Boolean autoCategorized;
    private Double confidenceScore;
    private Boolean needsReview;
    private String reviewReason;

    // Attachments and Tags
    private String notes;
    private List<String> attachmentUrls;
    private List<String> tags;
    private Map<String, String> metadata;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime processedAt;

    // Computed fields
    private Boolean isOverBudget;
    private Boolean isRecent;
    private String displayDescription;
}
