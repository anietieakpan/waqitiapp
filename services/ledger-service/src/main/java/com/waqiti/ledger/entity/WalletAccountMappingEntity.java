package com.waqiti.ledger.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Wallet Account Mapping Entity
 *
 * Maps wallets to their corresponding ledger accounts in the Chart of Accounts.
 * This replaces the previous hardcoded UUID.nameUUIDFromBytes() approach with
 * proper database-backed account resolution for regulatory compliance.
 *
 * Compliance Requirements:
 * - GAAP: Proper chart of accounts integration
 * - SOX: Auditable account assignments
 * - PCI DSS: Traceable financial operations
 *
 * Each wallet has:
 * - A liability account (tracks customer balances owed)
 * - Per currency (multi-currency support)
 * - Linked to actual accounts in chart of accounts
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Entity
@Table(name = "wallet_account_mappings", indexes = {
    @Index(name = "idx_wallet_currency", columnList = "walletId,currency", unique = true),
    @Index(name = "idx_account_id", columnList = "accountId"),
    @Index(name = "idx_mapping_type", columnList = "mappingType"),
    @Index(name = "idx_active_mappings", columnList = "isActive")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletAccountMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "mapping_id")
    private UUID mappingId;

    /**
     * Wallet ID from wallet-service
     */
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    /**
     * Currency code (ISO 4217: USD, EUR, GBP, etc.)
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Ledger account ID from chart of accounts
     */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /**
     * Type of mapping:
     * - WALLET_LIABILITY: Customer wallet balance (credit account)
     * - CASH_CLEARING: Cash in transit account (debit account)
     * - FEE_REVENUE: Transaction fee revenue account
     * - INTEREST_EXPENSE: Interest paid to customers
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false)
    private MappingType mappingType;

    /**
     * User ID for audit trail
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * Active status - allows soft delete of mappings
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Audit fields
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Notes for auditing and documentation
     */
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Mapping types for wallet-account relationships
     */
    public enum MappingType {
        /**
         * Wallet liability account - tracks customer balances owed
         * This is a CREDIT account (increases with credits, decreases with debits)
         * Example: "Customer Wallet Liabilities - USD"
         */
        WALLET_LIABILITY,

        /**
         * Cash clearing account - tracks cash in transit
         * This is a DEBIT account (increases with debits, decreases with credits)
         * Example: "Cash Clearing - USD"
         */
        CASH_CLEARING,

        /**
         * Fee revenue account - tracks transaction fees earned
         * This is a CREDIT account (revenue)
         * Example: "Transaction Fee Revenue - USD"
         */
        FEE_REVENUE,

        /**
         * Interest expense account - tracks interest paid to customers
         * This is a DEBIT account (expense)
         * Example: "Interest Expense - USD"
         */
        INTEREST_EXPENSE
    }
}
