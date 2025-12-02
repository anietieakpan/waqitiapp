package com.waqiti.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet Closure Notification Request DTO
 *
 * Request to send wallet closure notification to user.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletClosureNotificationRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Wallet count is required")
    @Positive(message = "Wallet count must be positive")
    private Integer walletCount;

    @NotNull(message = "Deactivation type is required")
    private String deactivationType; // TEMPORARY, PERMANENT, SUSPENDED, COMPLIANCE

    private String deactivationReason;
    private LocalDateTime scheduledReactivation;
    private String correlationId;
}
