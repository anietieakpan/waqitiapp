package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletRecoveryResponse {
    @NotNull
    private UUID recoveryId;
    @NotNull
    private UUID walletId;
    @NotNull
    private UUID userId;
    @NotNull
    private String status;
    private String recoveryMethod;
    private String verificationLevel;
    private boolean adminApprovalRequired;
    private boolean adminApproved;
    private UUID approvedBy;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String recoveryToken;
    private LocalDateTime tokenExpiresAt;
}
