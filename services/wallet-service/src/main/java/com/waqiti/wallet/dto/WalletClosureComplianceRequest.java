package com.waqiti.wallet.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Wallet Closure Compliance Request DTO
 *
 * Request to report wallet closure to compliance service.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletClosureComplianceRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotEmpty(message = "At least one wallet ID is required")
    private List<UUID> walletIds;

    @NotNull(message = "Deactivation type is required")
    private String deactivationType;

    private String deactivationReason;
    private String deactivatedBy;
    private String ticketId;
    private String correlationId;
}
