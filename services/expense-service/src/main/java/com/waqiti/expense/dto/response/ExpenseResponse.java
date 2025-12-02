package com.waqiti.expense.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expense response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private UUID id;

    private UUID userId;

    private BigDecimal amount;

    private String category;

    private String description;

    private LocalDateTime expenseDate;

    private String merchantName;

    private String merchantCategory;

    private ExpenseStatus status;

    private List<String> tags;

    private Map<String, String> metadata;

    private UUID receiptId;

    private String paymentMethod;

    private String currency;

    private String notes;

    private Boolean isRecurring;

    private String location;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum ExpenseStatus {
        PENDING,
        APPROVED,
        REJECTED,
        REIMBURSED
    }
}
