/**
 * Crypto Balance Entity
 * JPA entity representing cryptocurrency balances for wallets
 */
package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "crypto_balances", indexes = {
    @Index(name = "idx_crypto_balance_wallet_currency", columnList = "walletId,currency", unique = true),
    @Index(name = "idx_crypto_balance_updated", columnList = "lastUpdated")
})
@EntityListeners(AuditingEntityListener.class)
@Audited
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, unique = true)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CryptoCurrency currency;

    @Column(name = "available_balance", nullable = false, precision = 36, scale = 18)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "pending_balance", nullable = false, precision = 36, scale = 18)
    @Builder.Default
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(name = "staked_balance", nullable = false, precision = 36, scale = 18)
    @Builder.Default
    private BigDecimal stakedBalance = BigDecimal.ZERO;

    @Column(name = "total_balance", nullable = false, precision = 36, scale = 18)
    @Builder.Default
    private BigDecimal totalBalance = BigDecimal.ZERO;

    @Column(name = "reserved_balance", nullable = false, precision = 36, scale = 18)
    @Builder.Default
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    /**
     * CRITICAL FIX: Optimistic locking version field
     * Prevents race conditions in concurrent crypto transactions
     */
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * CRITICAL FIX: Audit trail fields for compliance
     * Required for cryptocurrency transaction auditing
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "last_updated_by", length = 100)
    private String lastUpdatedBy;

    @PreUpdate
    @PrePersist
    protected void updateTotalBalance() {
        // CRITICAL FIX: Update total balance including reserved balance
        totalBalance = availableBalance.add(pendingBalance).add(stakedBalance).add(reservedBalance);
        
        // CRITICAL VALIDATION: Prevent negative balances
        validateBalances();
    }
    
    /**
     * CRITICAL SECURITY: Validates balance integrity before database operations
     * Prevents cryptocurrency theft through balance manipulation
     */
    private void validateBalances() {
        if (availableBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Available balance cannot be negative: " + availableBalance);
        }
        if (pendingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Pending balance cannot be negative: " + pendingBalance);
        }
        if (stakedBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Staked balance cannot be negative: " + stakedBalance);
        }
        if (reservedBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Reserved balance cannot be negative: " + reservedBalance);
        }
        
        // Validate balance equation
        BigDecimal calculatedTotal = availableBalance.add(pendingBalance).add(stakedBalance).add(reservedBalance);
        if (totalBalance.compareTo(calculatedTotal) != 0) {
            throw new IllegalStateException(String.format(
                "Balance equation violation: total=%s, calculated=%s (available=%s + pending=%s + staked=%s + reserved=%s)", 
                totalBalance, calculatedTotal, availableBalance, pendingBalance, stakedBalance, reservedBalance));
        }
    }

    // Helper methods for balance operations
    public void addAvailableBalance(BigDecimal amount) {
        this.availableBalance = this.availableBalance.add(amount);
        updateTotalBalance();
    }

    public void subtractAvailableBalance(BigDecimal amount) {
        this.availableBalance = this.availableBalance.subtract(amount);
        updateTotalBalance();
    }

    public void addPendingBalance(BigDecimal amount) {
        this.pendingBalance = this.pendingBalance.add(amount);
        updateTotalBalance();
    }

    public void subtractPendingBalance(BigDecimal amount) {
        this.pendingBalance = this.pendingBalance.subtract(amount);
        updateTotalBalance();
    }

    public void movePendingToAvailable(BigDecimal amount) {
        this.pendingBalance = this.pendingBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
        updateTotalBalance();
    }

    public void addStakedBalance(BigDecimal amount) {
        this.stakedBalance = this.stakedBalance.add(amount);
        updateTotalBalance();
    }

    public void subtractStakedBalance(BigDecimal amount) {
        this.stakedBalance = this.stakedBalance.subtract(amount);
        updateTotalBalance();
    }
}