package com.waqiti.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Search criteria for advanced account querying
 *
 * Supports multiple filter combinations for flexible account search:
 * - By user, account number, or type
 * - By status or currency
 * - By balance range
 * - By date range
 * - By KYC/tier level
 *
 * @author Production Readiness Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSearchCriteria {

    /**
     * Filter by account number (exact match or partial)
     */
    private String accountNumber;

    /**
     * Filter by user ID (exact match)
     */
    private UUID userId;

    /**
     * Filter by account type
     * Values: SAVINGS, CHECKING, INVESTMENT, CREDIT, LOAN, WALLET
     */
    private String accountType;

    /**
     * Filter by account status
     * Values: PENDING_ACTIVATION, ACTIVE, INACTIVE, SUSPENDED, FROZEN, CLOSED
     */
    private String status;

    /**
     * Filter by currency (ISO 4217 code)
     */
    private String currency;

    /**
     * Minimum balance filter (inclusive)
     */
    private BigDecimal minBalance;

    /**
     * Maximum balance filter (inclusive)
     */
    private BigDecimal maxBalance;

    /**
     * Filter by account category
     * Values: PERSONAL, BUSINESS, JOINT, TRUST, CORPORATE
     */
    private String accountCategory;

    /**
     * Filter by tier level
     * Values: BASIC, STANDARD, PREMIUM, VIP, PLATINUM
     */
    private String tierLevel;

    /**
     * Filter by KYC level
     * Values: LEVEL_0, LEVEL_1, LEVEL_2, LEVEL_3
     */
    private String kycLevel;

    /**
     * Filter by frozen status
     */
    private Boolean frozen;

    /**
     * Filter by international transactions enabled
     */
    private Boolean internationalEnabled;

    /**
     * Created after this date (inclusive)
     */
    private LocalDateTime createdAfter;

    /**
     * Created before this date (inclusive)
     */
    private LocalDateTime createdBefore;

    /**
     * Last transaction after this date
     */
    private LocalDateTime lastTransactionAfter;

    /**
     * Include deleted accounts in search
     * Default: false (only active accounts)
     */
    @Builder.Default
    private Boolean includeDeleted = false;

    /**
     * Filter by parent account (for sub-accounts)
     */
    private UUID parentAccountId;

    /**
     * Filter by business ID (for business accounts)
     */
    private UUID businessId;

    /**
     * Filter by risk score range
     */
    private Integer minRiskScore;
    private Integer maxRiskScore;

    /**
     * Search in account name (fuzzy search)
     */
    private String accountNameContains;

    /**
     * Helper method to check if any filter is applied
     */
    public boolean hasFilters() {
        return accountNumber != null || userId != null || accountType != null
            || status != null || currency != null || minBalance != null
            || maxBalance != null || accountCategory != null || tierLevel != null
            || kycLevel != null || frozen != null || internationalEnabled != null
            || createdAfter != null || createdBefore != null
            || lastTransactionAfter != null || parentAccountId != null
            || businessId != null || minRiskScore != null || maxRiskScore != null
            || accountNameContains != null;
    }
}
