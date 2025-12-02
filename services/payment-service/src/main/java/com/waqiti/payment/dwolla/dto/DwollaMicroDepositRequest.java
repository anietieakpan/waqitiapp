package com.waqiti.payment.dwolla.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Dwolla Micro Deposit Request DTO
 * 
 * Request object for micro-deposit verification.
 */
@Data
@Builder
public class DwollaMicroDepositRequest {
    private BigDecimal amount1;
    private BigDecimal amount2;
}