package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for budget creation and retrieval operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetResponse {
    
    private Budget budget;
    private boolean success;
    private String message;
    private String errorCode;
    private LocalDateTime timestamp;
    
    public static BudgetResponse success(Budget budget, String message) {
        return BudgetResponse.builder()
            .budget(budget)
            .success(true)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static BudgetResponse failure(String message, String errorCode) {
        return BudgetResponse.builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .timestamp(LocalDateTime.now())
            .build();
    }
}