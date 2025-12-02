package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionResponse {
    @NotNull
    private UUID transactionId;
    @NotNull
    private UUID walletId;
    @NotNull
    private UUID userId;
    @NotNull
    private String transactionType;
    @NotNull
    private BigDecimal amount;
    @NotNull
    private String currency;
    @NotNull
    private BigDecimal newBalance;
    private BigDecimal previousBalance;
    private String reference;
    private String description;
    private String status;
    private LocalDateTime transactionTimestamp;
    private String metadata;
}
