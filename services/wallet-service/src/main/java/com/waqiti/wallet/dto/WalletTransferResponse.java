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
public class WalletTransferResponse {
    @NotNull
    private UUID transferId;
    @NotNull
    private UUID fromWalletId;
    @NotNull
    private UUID toWalletId;
    @NotNull
    private UUID fromUserId;
    @NotNull
    private UUID toUserId;
    @NotNull
    private BigDecimal amount;
    @NotNull
    private String currency;
    @NotNull
    private String status;
    private String reference;
    private BigDecimal fee;
    private BigDecimal fromWalletNewBalance;
    private BigDecimal toWalletNewBalance;
    private LocalDateTime transferTimestamp;
    private LocalDateTime completedAt;
}
