package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet Fraud Check Request DTO
 *
 * Request payload for real-time fraud detection on wallet transfers
 *
 * @author Waqiti Security Team - P0 Production Fix
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletFraudCheckRequest {

    @NotNull(message = "From user ID is required")
    private UUID fromUserId;

    @NotNull(message = "To user ID is required")
    private UUID toUserId;

    @NotNull(message = "From wallet ID is required")
    private UUID fromWalletId;

    @NotNull(message = "To wallet ID is required")
    private UUID toWalletId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    private String transactionType; // P2P, TRANSFER, WITHDRAWAL

    private String correlationId;

    private Map<String, Object> metadata;

    private LocalDateTime timestamp;
}
