package com.waqiti.expense.dto.request;

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
 * Expense update request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseUpdateRequest {

    private BigDecimal amount;

    private String category;

    private String description;

    private LocalDateTime expenseDate;

    private String merchantName;

    private String merchantCategory;

    private Map<String, String> metadata;

    private List<String> tags;

    private UUID receiptId;

    private String paymentMethod;

    private String notes;

    private Boolean isRecurring;

    private String location;
}
