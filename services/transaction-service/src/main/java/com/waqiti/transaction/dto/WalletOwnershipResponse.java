package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for wallet ownership validation.
 *
 * Contains information about wallet ownership and associated user details.
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletOwnershipResponse {

    /**
     * The wallet ID being checked.
     */
    private String walletId;

    /**
     * The user ID of the wallet owner.
     */
    private String userId;

    /**
     * Username being validated.
     */
    private String username;

    /**
     * Whether the user is the owner of the wallet.
     */
    private boolean isOwner;

    /**
     * Whether the user has any access rights to the wallet.
     * (Could be owner, delegate, or shared access)
     */
    private boolean hasAccess;

    /**
     * Type of access the user has.
     * Possible values: OWNER, DELEGATE, SHARED, READ_ONLY, NONE
     */
    private String accessType;

    /**
     * Wallet status.
     * Possible values: ACTIVE, SUSPENDED, CLOSED, FROZEN
     */
    private String walletStatus;

    /**
     * Whether the wallet is currently active and can be used for transactions.
     */
    private boolean isActive;

    /**
     * Timestamp when the ownership was last verified.
     */
    private LocalDateTime verifiedAt;

    /**
     * Additional metadata about the wallet.
     */
    private WalletMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletMetadata {
        /**
         * Wallet type (PERSONAL, BUSINESS, SAVINGS, etc.)
         */
        private String walletType;

        /**
         * Currency code (USD, EUR, etc.)
         */
        private String currency;

        /**
         * Whether the wallet requires KYC verification.
         */
        private boolean requiresKyc;

        /**
         * KYC verification status.
         */
        private String kycStatus;

        /**
         * User's tier/level in the system.
         */
        private String userTier;
    }
}
