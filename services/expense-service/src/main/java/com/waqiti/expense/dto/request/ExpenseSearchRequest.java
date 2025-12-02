package com.waqiti.expense.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Expense search/filter request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSearchRequest {

    private UUID userId;

    private List<String> categories;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private String merchantName;

    private List<String> tags;

    private String searchTerm;

    private String paymentMethod;

    private Boolean isRecurring;

    private Integer page;

    private Integer size;

    private String sortBy;

    private String sortDirection;
}
