package com.waqiti.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Regulatory Closure Request DTO
 *
 * Request to report regulatory wallet closure to regulators.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatoryClosureRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Wallet count is required")
    @Positive(message = "Wallet count must be positive")
    private Integer walletCount;

    private String regulationReference;
    private String deactivationReason;
    private String reportingAuthority;
    private String correlationId;
}
