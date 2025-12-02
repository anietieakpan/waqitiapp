package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.waqiti.validation.annotation.ValidMoneyAmount;
import com.waqiti.validation.annotation.ValidCurrency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for balance transfer operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceTransferRequest {
    
    @NotBlank
    private String fromCustomerId;
    
    @NotBlank
    private String toCustomerId;
    
    @NotNull(message = "Transfer amount is required")
    @ValidMoneyAmount(
        min = 0.01,
        max = 500000.00,
        scale = 2,
        transactionType = ValidMoneyAmount.TransactionType.TRANSFER,
        checkFraudLimits = true,
        userTier = ValidMoneyAmount.UserTier.STANDARD,
        message = "Invalid transfer amount"
    )
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @ValidCurrency(
        supportedOnly = true,
        allowCrypto = false,
        transactionType = ValidCurrency.TransactionType.TRANSFER,
        checkActiveStatus = true,
        message = "Invalid or unsupported currency for transfer"
    )
    private String currency;
    
    @NotBlank
    private String reference;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    private String transferType;
    private Map<String, String> metadata;
    
    @Builder.Default
    private boolean sendNotification = true;
}