package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Banking API Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankingAPIResponse {
    private boolean successful;
    private String referenceId;
    private String message;
    private String errorMessage;
    private LocalDate expectedCompletionDate;
}