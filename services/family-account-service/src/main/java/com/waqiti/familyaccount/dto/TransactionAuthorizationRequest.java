package com.waqiti.familyaccount.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Transaction Authorization Request DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAuthorizationRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Transaction amount is required")
    private BigDecimal transactionAmount;

    @NotBlank(message = "Merchant name is required")
    private String merchantName;

    private String merchantCategory;

    private String description;
}
