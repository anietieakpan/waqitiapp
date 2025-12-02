package com.waqiti.expense.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expense creation request DTO
 * Production-ready with comprehensive validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Description is required")
    private String description;

    private LocalDateTime expenseDate;

    private String merchantName;

    private String merchantCategory;

    private Map<String, String> metadata;

    private List<String> tags;

    private UUID receiptId;

    private String paymentMethod;

    private String currency;

    private String notes;

    private Boolean isRecurring;

    private String location;
}
