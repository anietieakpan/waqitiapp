package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Response DTO for account status from account-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusResponse {

    /**
     * Account identifier
     */
    @NotBlank(message = "Account ID is required")
    private String accountId;

    /**
     * Current status (ACTIVE, FROZEN, CLOSED, SUSPENDED, etc.)
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Whether account is active
     */
    @NotNull(message = "Active flag is required")
    private Boolean active;

    /**
     * Whether account is frozen
     */
    @NotNull(message = "Frozen flag is required")
    private Boolean frozen;

    /**
     * Whether account is closed
     */
    @NotNull(message = "Closed flag is required")
    private Boolean closed;

    /**
     * Whether account is suspended
     */
    @NotNull(message = "Suspended flag is required")
    private Boolean suspended;

    /**
     * Whether account is dormant
     */
    private Boolean dormant;

    /**
     * Reason for current status
     */
    private String statusReason;

    /**
     * Timestamp of last status change
     */
    private LocalDateTime statusChangedAt;

    /**
     * User who changed the status
     */
    private String changedBy;
}
