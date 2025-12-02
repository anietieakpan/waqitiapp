package com.waqiti.wallet.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import com.waqiti.wallet.exception.WalletExceptions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

// CRITICAL SECURITY IMPORTS for fixing fund reservation vulnerability
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.waqiti.wallet.repository.FundReservationRepository;
import com.waqiti.wallet.exception.InsufficientBalanceException;
import com.waqiti.wallet.exception.TransactionLimitExceededException;
import com.waqiti.wallet.exception.WalletNotActiveException;
// CRITICAL SECURITY: Field-level encryption for PCI DSS and GDPR compliance
import com.waqiti.common.security.converters.EncryptedFinancialConverter;
import com.waqiti.common.security.converters.EncryptedPIIConverter;

@Entity
@Table(name = "wallets", indexes = {
    @Index(name = "idx_wallet_user_id", columnList = "userId"),
    @Index(name = "idx_wallet_status", columnList = "status"),
    @Index(name = "idx_wallet_currency", columnList = "currency")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletType walletType;

    @Column(nullable = false)
    private String accountType; // "SAVINGS", "CHECKING", etc.

    // CRITICAL SECURITY: Encrypt financial balances for PCI DSS compliance
    // precision=19, scale=4 ensures we can store up to 999,999,999,999,999.9999
    // This supports transactions up to 1 quadrillion with 4 decimal places (for crypto/forex)
    @Column(nullable = false, precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal balance;

    // New fields for double-spending prevention - ENCRYPTED
    @Column(nullable = false, precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @Column(precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal pendingBalance = BigDecimal.ZERO;
    
    // Transaction limits
    @Column(precision = 19, scale = 4)
    private BigDecimal dailyLimit;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal monthlyLimit;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal dailySpent = BigDecimal.ZERO;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal monthlySpent = BigDecimal.ZERO;
    
    // Additional wallet features
    @Column
    private Boolean interestEnabled = false;
    
    @Column
    private Boolean overdraftEnabled = false;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal overdraftLimit = BigDecimal.ZERO;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate = BigDecimal.ZERO;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "wallet_metadata")
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    // Wallet freeze/closure tracking fields
    @Column(length = 500)
    private String frozenReason;

    @Column
    private LocalDateTime frozenAt;

    @Column
    private UUID closedBy;

    @Column
    private LocalDateTime closedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime lastTransactionAt;

    @Version
    private Long version;
    
    // CRITICAL SECURITY FIX: Remove vulnerable @Transient reservation storage
    // Fund reservations are now stored persistently in the database
    // This prevents double-spending attacks after service restarts
    
    @Transient
    private final Object balanceLock = new Object();
    
    // Repository injected for persistent fund reservation management
    @Transient
    private FundReservationRepository fundReservationRepository;

    // Audit fields
    @Setter
    private String createdBy;

    @Setter
    private String updatedBy;

    /**
     * Creates a new wallet
     */
    public static Wallet create(UUID userId, String externalId, WalletType walletType,
            String accountType, Currency currency) {
        Wallet wallet = new Wallet();
        wallet.userId = userId;
        wallet.externalId = externalId;
        wallet.walletType = walletType;
        wallet.accountType = accountType;
        wallet.balance = BigDecimal.ZERO;
        wallet.availableBalance = BigDecimal.ZERO;
        wallet.reservedBalance = BigDecimal.ZERO;
        wallet.pendingBalance = BigDecimal.ZERO;
        wallet.currency = currency;
        wallet.status = WalletStatus.ACTIVE;
        wallet.createdAt = LocalDateTime.now();
        wallet.updatedAt = LocalDateTime.now();
        return wallet;
    }
    
    /**
     * CRITICAL SECURITY FIX: Reserve funds with persistent storage to prevent double-spending
     * Fixes vulnerability where reservations were lost on service restart (@Transient)
     *
     * @deprecated Use WalletBalanceService.reserveFunds() instead for distributed lock support.
     *             This method only provides JVM-local synchronization and is unsafe in distributed deployments.
     *             DISABLED FOR SECURITY: This method has been disabled to prevent double-spending in distributed deployments.
     * @throws UnsupportedOperationException always - method is unsafe in production
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public FundReservation reserveFunds(BigDecimal amount, UUID transactionId, String idempotencyKey) {
        throw new UnsupportedOperationException(
            "This method is deprecated and unsafe in distributed deployments. " +
            "JVM-local synchronized(balanceLock) does NOT work across multiple service instances and WILL cause double-spending. " +
            "Use WalletBalanceService.reserveFunds() instead, which provides distributed Redis-based locking. " +
            "See: services/wallet-service/src/main/java/com/waqiti/wallet/service/WalletBalanceService.java"
        );
    }
    
    /**
     * CRITICAL SECURITY FIX: Confirm reservation with persistent storage
     *
     * @deprecated Use WalletBalanceService.confirmReservation() instead for distributed lock support.
     *             DISABLED FOR SECURITY: This method has been disabled to prevent double-spending in distributed deployments.
     * @throws UnsupportedOperationException always - method is unsafe in production
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void confirmReservation(UUID reservationId) {
        throw new UnsupportedOperationException(
            "This method is deprecated and unsafe in distributed deployments. " +
            "JVM-local synchronized(balanceLock) does NOT work across multiple service instances and WILL cause balance corruption. " +
            "Use WalletBalanceService.confirmReservation() instead, which provides distributed Redis-based locking. " +
            "See: services/wallet-service/src/main/java/com/waqiti/wallet/service/WalletBalanceService.java"
        );
    }
    
    /**
     * CRITICAL SECURITY FIX: Release reservation with persistent storage
     *
     * @deprecated Use WalletBalanceService.releaseReservation() instead for distributed lock support.
     *             DISABLED FOR SECURITY: This method has been disabled to prevent double-spending in distributed deployments.
     * @throws UnsupportedOperationException always - method is unsafe in production
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void releaseReservation(UUID reservationId, String reason) {
        throw new UnsupportedOperationException(
            "This method is deprecated and unsafe in distributed deployments. " +
            "JVM-local synchronized(balanceLock) does NOT work across multiple service instances and WILL cause balance corruption. " +
            "Use WalletBalanceService.releaseReservation() instead, which provides distributed Redis-based locking. " +
            "See: services/wallet-service/src/main/java/com/waqiti/wallet/service/WalletBalanceService.java"
        );
    }
    
    /**
     * Check if wallet has sufficient available balance
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }
    
    /**
     * Get available balance (total minus reserved)
     */
    public BigDecimal getAvailableBalance() {
        synchronized (balanceLock) {
            return availableBalance;
        }
    }
    
    /**
     * CRITICAL SECURITY FIX: Clean up expired reservations from persistent storage
     *
     * @deprecated Use WalletBalanceService.cleanupExpiredReservations() instead for distributed lock support.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @Transactional
    public void cleanupExpiredReservations() {
        synchronized (balanceLock) {
            if (fundReservationRepository == null) {
                log.warn("Fund reservation repository not available for cleanup");
                return;
            }
            
            LocalDateTime now = LocalDateTime.now();
            var expiredReservations = fundReservationRepository.findExpiredActiveReservations(now);
            
            BigDecimal totalExpiredAmount = BigDecimal.ZERO;
            for (FundReservation reservation : expiredReservations) {
                if (reservation.getWalletId().equals(this.id)) {
                    reservation.markExpired();
                    totalExpiredAmount = totalExpiredAmount.add(reservation.getAmount());
                    
                    log.warn("SECURITY: Expired persistent reservation {} for {} {}", 
                        reservation.getId(), reservation.getAmount(), currency);
                }
            }
            
            // Save all expired reservations
            if (!expiredReservations.isEmpty()) {
                fundReservationRepository.saveAll(expiredReservations);
                
                // Recalculate balances
                this.reservedBalance = getCurrentReservedAmount();
                this.availableBalance = this.balance.subtract(this.reservedBalance);
                this.updatedAt = LocalDateTime.now();
                
                log.info("SECURITY: Cleaned up {} expired reservations totaling {} {}", 
                    expiredReservations.size(), totalExpiredAmount, currency);
            }
        }
    }
    
    /**
     * CRITICAL SECURITY FIX: Get current reserved amount from persistent storage
     */
    private BigDecimal getCurrentReservedAmount() {
        if (fundReservationRepository != null) {
            return fundReservationRepository.getTotalReservedAmount(this.id);
        }
        return this.reservedBalance;
    }
    
    /**
     * SECURITY: Set the fund reservation repository (dependency injection)
     */
    public void setFundReservationRepository(FundReservationRepository repository) {
        this.fundReservationRepository = repository;
    }
    
    /**
     * SECURITY: Overloaded method for backward compatibility
     */
    public FundReservation reserveFunds(BigDecimal amount, UUID transactionId) {
        return reserveFunds(amount, transactionId, null);
    }
    
    /**
     * SECURITY: Overloaded method for backward compatibility
     */
    public void releaseReservation(UUID reservationId) {
        releaseReservation(reservationId, "Transaction cancelled");
    }
    
    /**
     * Check transaction limits
     */
    private void checkTransactionLimits(BigDecimal amount) {
        if (dailyLimit != null && dailyLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal projectedDailySpent = dailySpent.add(amount);
            if (projectedDailySpent.compareTo(dailyLimit) > 0) {
                throw new TransactionLimitExceededException(
                    String.format("Daily limit exceeded. Limit: %s, Current: %s, Requested: %s",
                        dailyLimit, dailySpent, amount));
            }
        }
        
        if (monthlyLimit != null && monthlyLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal projectedMonthlySpent = monthlySpent.add(amount);
            if (projectedMonthlySpent.compareTo(monthlyLimit) > 0) {
                throw new TransactionLimitExceededException(
                    String.format("Monthly limit exceeded. Limit: %s, Current: %s, Requested: %s",
                        monthlyLimit, monthlySpent, amount));
            }
        }
    }

    /**
     * Updates the wallet balance (synchronized with available balance)
     *
     * @deprecated Use WalletBalanceService.updateBalance() instead for distributed lock support.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void updateBalance(BigDecimal newBalance) {
        synchronized (balanceLock) {
            validateWalletActive();
            BigDecimal difference = newBalance.subtract(this.balance);
            this.balance = newBalance;
            this.availableBalance = this.availableBalance.add(difference);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Credit the wallet (add funds)
     *
     * @deprecated Use WalletBalanceService.credit() instead for distributed lock support.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void credit(BigDecimal amount) {
        synchronized (balanceLock) {
            validateWalletActive();
            validatePositiveAmount(amount);
            this.balance = this.balance.add(amount);
            this.availableBalance = this.availableBalance.add(amount);
            this.lastTransactionAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            log.info("Credited {} {} to wallet {}", amount, currency, id);
        }
    }

    /**
     * Debit the wallet (remove funds) - should only be used with prior reservation
     *
     * @deprecated Use WalletBalanceService.debit() instead for distributed lock support.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void debit(BigDecimal amount) {
        synchronized (balanceLock) {
            validateWalletActive();
            validatePositiveAmount(amount);
            // This method should typically be called after funds are reserved
            // Direct debit without reservation is allowed but logged as warning
            if (this.reservedBalance.compareTo(amount) < 0) {
                log.warn("Direct debit without reservation for {} {} in wallet {}", 
                    amount, currency, id);
                validateSufficientBalance(amount);
                this.balance = this.balance.subtract(amount);
                this.availableBalance = this.availableBalance.subtract(amount);
            } else {
                this.balance = this.balance.subtract(amount);
            }
            this.lastTransactionAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            log.info("Debited {} {} from wallet {}", amount, currency, id);
        }
    }

    /**
     * Freezes the wallet
     */
    public void freeze() {
        validateWalletActive();
        this.status = WalletStatus.FROZEN;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Unfreezes the wallet
     */
    public void unfreeze() {
        if (this.status != WalletStatus.FROZEN) {
            throw new IllegalStateException("Wallet is not frozen");
        }
        this.status = WalletStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Closes the wallet
     */
    public void close() {
        if (this.status == WalletStatus.CLOSED) {
            throw new IllegalStateException("Wallet is already closed");
        }

        if (this.balance.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot close wallet with positive balance");
        }

        this.status = WalletStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Validates that the wallet is active
     */
    private void validateWalletActive() {
        if (this.status != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException("Wallet is not active, current status: " + this.status);
        }
    }

    /**
     * Validates that the amount is positive
     */
    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    /**
     * Validates that the wallet has sufficient balance for a debit operation
     */
    private void validateSufficientBalance(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance: required %s %s, available %s %s",
                            amount, this.currency, this.balance, this.currency));
        }
    }
}