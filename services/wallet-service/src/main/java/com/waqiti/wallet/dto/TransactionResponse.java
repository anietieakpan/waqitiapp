package com.waqiti.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response for transaction operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private String externalId;
    private UUID sourceWalletId;
    private UUID targetWalletId;
    private BigDecimal amount;
    private String currency;
    private String type;
    private String status;
    private String description;
    private LocalDateTime createdAt;
}