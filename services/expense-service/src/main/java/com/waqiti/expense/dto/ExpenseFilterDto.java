package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Filter DTO for querying expenses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseFilterDto {

    private String category;
    private String status;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private String merchantName;
    private String paymentMethod;
    private Boolean isRecurring;
    private Boolean isReimbursable;
    private Boolean needsReview;
    private String searchText;
}
