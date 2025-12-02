package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Request DTO for verifying bank accounts using micro-deposits in core-banking-service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountVerificationRequest {
    
    @NotNull(message = "First micro-deposit amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
    @DecimalMax(value = "0.99", message = "Deposit amount must be less than 1.00")
    @Digits(integer = 1, fraction = 2, message = "Invalid deposit amount format")
    private BigDecimal microDepositAmount1;
    
    @NotNull(message = "Second micro-deposit amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
    @DecimalMax(value = "0.99", message = "Deposit amount must be less than 1.00")
    @Digits(integer = 1, fraction = 2, message = "Invalid deposit amount format")
    private BigDecimal microDepositAmount2;
    
    private String verificationMethod;
    
    private boolean instantVerification;
}