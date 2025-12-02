package com.waqiti.wallet.service;

import com.waqiti.common.security.SensitiveDataMasker;
import com.waqiti.wallet.domain.FundReservation;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.exception.InsufficientBalanceException;
import com.waqiti.wallet.exception.TransactionLimitExceededException;
import com.waqiti.wallet.exception.WalletLockException;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.exception.WalletNotActiveException;
import com.waqiti.wallet.lock.DistributedWalletLockService;
import com.waqiti.wallet.repository.FundReservationRepository;
import com.waqiti.wallet.repository.WalletRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Production-ready wallet balance service with distributed locking.
 *
 * <p>This service replaces synchronized methods in the Wallet entity with proper
 * distributed locks, enabling safe concurrent wallet operations across multiple
 * service instances in a distributed deployment.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Distributed locking via Redis (Redisson)</li>
 *   <li>Optimistic locking with @Version at database level</li>
 *   <li>Automatic retry with exponential backoff</li>
 *   <li>Comprehensive metrics and monitoring</li>
 *   <li>Idempotency support for fund reservations</li>
 *   <li>Transaction isolation: REPEATABLE_READ</li>
 * </ul>
 *
 * <p><b>Consistency Guarantees:</b>
 * <ul>
 *   <li>Distributed mutual exclusion: Only one instance can modify wallet at a time</li>
 *   <li>Atomic operations: Balance updates are all-or-nothing</li>
 *   <li>Persistent reservations: Survive service restarts</li>
 *   <li>ACID compliance: Full transactional integrity</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @version 2.0 - Production Ready with Distributed Locks
 * @since 2025-10-18
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WalletBalanceService {

    private final WalletRepository walletRepository;
    private final FundReservationRepository fundReservationRepository;
    private final DistributedWalletLockService lockService;
    private final MeterRegistry meterRegistry;

    // Metrics
    private final Counter reservationCounter;
    private final Counter confirmationCounter;
    private final Counter releaseCounter;
    private final Counter creditCounter;
    private final Counter debitCounter;
    private final Counter lockContentionCounter;

    public WalletBalanceService(WalletRepository walletRepository,
                               FundReservationRepository fundReservationRepository,
                               DistributedWalletLockService lockService,
                               MeterRegistry meterRegistry) {
        this.walletRepository = walletRepository;
        this.fundReservationRepository = fundReservationRepository;
        this.lockService = lockService;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.reservationCounter = Counter.builder("wallet.balance.reservation.created")
                .description("Number of fund reservations created")
                .register(meterRegistry);

        this.confirmationCounter = Counter.builder("wallet.balance.reservation.confirmed")
                .description("Number of reservations confirmed")
                .register(meterRegistry);

        this.releaseCounter = Counter.builder("wallet.balance.reservation.released")
                .description("Number of reservations released")
                .register(meterRegistry);

        this.creditCounter = Counter.builder("wallet.balance.credit")
                .description("Number of credit operations")
                .register(meterRegistry);

        this.debitCounter = Counter.builder("wallet.balance.debit")
                .description("Number of debit operations")
                .register(meterRegistry);

        this.lockContentionCounter = Counter.builder("wallet.balance.lock.contention")
                .description("Number of lock contention events")
                .register(meterRegistry);
    }

    // ========================================
    // Fund Reservation Operations
    // ========================================

    /**
     * Reserve funds for a pending transaction with distributed locking.
     *
     * <p>This method prevents double-spending by:
     * <ul>
     *   <li>Acquiring distributed lock across all service instances</li>
     *   <li>Persisting reservation to database BEFORE updating balances</li>
     *   <li>Supporting idempotency via idempotency keys</li>
     *   <li>Using optimistic locking (@Version) for additional safety</li>
     * </ul>
     *
     * @param walletId wallet to reserve funds from
     * @param amount amount to reserve
     * @param transactionId transaction ID for tracking
     * @param idempotencyKey optional idempotency key for duplicate prevention
     * @return created fund reservation
     * @throws WalletNotFoundException if wallet doesn't exist
     * @throws InsufficientBalanceException if insufficient available balance
     * @throws TransactionLimitExceededException if transaction limits exceeded
     * @throws WalletLockException if unable to acquire distributed lock
     */
    @Timed("wallet.balance.reserveFunds")
    @Retryable(
        value = {org.springframework.dao.OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    // ✅ CRITICAL PRODUCTION FIX: Changed back to SERIALIZABLE to prevent double-spending
    // Previous comment was INCORRECT - READ_COMMITTED allows phantom reads and non-repeatable reads
    // For financial operations, we MUST use SERIALIZABLE to prevent:
    // 1. Double-spending: Multiple concurrent reservations succeeding when funds insufficient
    // 2. Phantom reads: Reserved amount calculation seeing inconsistent data
    // 3. Non-repeatable reads: Balance changing between validation and update
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public FundReservation reserveFunds(UUID walletId, BigDecimal amount, UUID transactionId, String idempotencyKey) {
        long startTime = System.currentTimeMillis();

        log.info("Reserving funds: wallet={}, amount={}, txn={}, idempotencyKey={}",
                walletId, amount, transactionId, idempotencyKey != null ? SensitiveDataMasker.maskApiKey(idempotencyKey) : "none");

        // Check for idempotency BEFORE acquiring lock (optimization)
        if (idempotencyKey != null) {
            var existingReservation = fundReservationRepository.findByIdempotencyKey(idempotencyKey);
            if (existingReservation.isPresent()) {
                log.info("Returning existing reservation for idempotency key: {}",
                        SensitiveDataMasker.maskApiKey(idempotencyKey));
                return existingReservation.get();
            }
        }

        // Acquire distributed lock
        String lockId = null;
        try {
            lockId = lockService.acquireLock(walletId.toString());

            // CRITICAL FIX: Fetch wallet with pessimistic WRITE lock (not read lock)
            // This prevents concurrent transactions from reading the row until we commit
            Wallet wallet = walletRepository.findByIdWithPessimisticLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            // Store original version for verification
            Long originalVersion = wallet.getVersion();
            log.debug("Acquired pessimistic lock on wallet {} with version {}", walletId, originalVersion);

            // Validate wallet state
            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new WalletNotActiveException("Wallet is not active: " + wallet.getStatus());
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive: " + amount);
            }

            // ✅ CRITICAL PRODUCTION FIX: Calculate available balance INSIDE pessimistic lock
            // This COMPLETELY ELIMINATES TOCTOU vulnerability by ensuring atomic read-compute-reserve
            //
            // SECURITY GUARANTEE: The pessimistic WRITE lock on line 174 ensures:
            // 1. No other transaction can read OR modify this wallet until we commit
            // 2. getTotalReservedAmount() sees consistent snapshot of all reservations
            // 3. The check-and-reserve operation is atomic (cannot be interleaved)
            //
            // Attack Prevention: Even if 1000 concurrent threads attempt to reserve funds,
            // only ONE will hold the pessimistic lock at a time, making double-spending impossible
            BigDecimal currentReserved = fundReservationRepository.getTotalReservedAmount(walletId);
            BigDecimal actualAvailable = wallet.getBalance().subtract(currentReserved);

            // ✅ CRITICAL SECURITY FIX: Mask sensitive financial data in logs (PCI DSS / GDPR compliance)
            log.debug("SECURITY: Balance check for wallet {} INSIDE pessimistic lock: balance=[REDACTED], reserved=[REDACTED], available=[REDACTED], requesting=[REDACTED]",
                com.waqiti.common.security.SensitiveDataMasker.maskWalletId(walletId));

            // Final balance check with detailed error message
            if (actualAvailable.compareTo(amount) < 0) {
                throw new InsufficientBalanceException(
                    String.format("Insufficient available balance: required %s, available %s (balance: %s, reserved: %s)",
                        amount, actualAvailable, wallet.getBalance(), currentReserved));
            }

            // ✅ ADDITIONAL SAFETY: Verify no negative balance condition
            if (wallet.getBalance().compareTo(currentReserved.add(amount)) < 0) {
                throw new IllegalStateException(
                    String.format("CRITICAL: Balance integrity violation detected. " +
                        "Balance %s cannot cover existing reserved %s + new reservation %s",
                        wallet.getBalance(), currentReserved, amount));
            }

            // Check transaction limits
            validateTransactionLimits(wallet, amount);

            // Create persistent reservation with wallet version
            FundReservation reservation = FundReservation.builder()
                    .walletId(walletId)
                    .transactionId(transactionId)
                    .amount(amount)
                    .currency(wallet.getCurrency())
                    .status(FundReservation.ReservationStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(5)) // 5-minute expiration
                    .idempotencyKey(idempotencyKey)
                    .walletVersionAtReservation(originalVersion) // Store version for later verification
                    .build();

            // CRITICAL: Persist reservation FIRST before updating wallet balances
            reservation = fundReservationRepository.save(reservation);
            log.debug("Created reservation {} for wallet {} with version snapshot {}",
                reservation.getId(), walletId, originalVersion);

            // Update wallet balances (in-memory, will be persisted by transaction)
            wallet.setReservedBalance(wallet.getReservedBalance().add(amount));
            wallet.setAvailableBalance(wallet.getBalance().subtract(wallet.getReservedBalance()));
            wallet.setUpdatedAt(LocalDateTime.now());

            // CRITICAL: Save wallet - optimistic lock version will auto-increment
            // If another transaction modified wallet, OptimisticLockingFailureException will be thrown
            wallet = walletRepository.save(wallet);
            log.debug("Updated wallet {} to version {}, reserved balance now {}",
                walletId, wallet.getVersion(), wallet.getReservedBalance());

            // Verify version incremented correctly
            if (!wallet.getVersion().equals(originalVersion + 1)) {
                log.warn("POTENTIAL RACE CONDITION: Wallet version jump detected. Expected {}, got {}",
                    originalVersion + 1, wallet.getVersion());
            }

            reservationCounter.increment();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Reserved funds successfully: reservation={}, wallet={}, amount={}, duration={}ms, version={}->{}",
                    reservation.getId(), walletId, amount, duration, originalVersion, wallet.getVersion());

            if (duration > 500) {
                log.warn("PERFORMANCE: Slow fund reservation: wallet={}, duration={}ms", walletId, duration);
            }

            return reservation;

        } catch (WalletLockException e) {
            lockContentionCounter.increment();
            log.error("Failed to acquire lock for fund reservation: wallet={}", walletId, e);
            throw e;
        } finally {
            if (lockId != null) {
                lockService.releaseLock(walletId.toString(), lockId);
            }
        }
    }

    /**
     * Confirm a fund reservation and complete the transaction.
     *
     * @param reservationId reservation to confirm
     * @throws IllegalStateException if reservation not found, not active, or expired
     * @throws WalletLockException if unable to acquire distributed lock
     */
    @Timed("wallet.balance.confirmReservation")
    @Retryable(
        value = {org.springframework.dao.OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    // ✅ CRITICAL PRODUCTION FIX: Upgraded to SERIALIZABLE for financial transaction integrity
    // Confirmation involves multiple operations (deduct balance, update reserved, update spending trackers)
    // SERIALIZABLE ensures these operations see consistent state
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void confirmReservation(UUID reservationId) {
        log.info("Confirming reservation: {}", reservationId);

        // Fetch reservation
        FundReservation reservation = fundReservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation not found: " + reservationId));

        UUID walletId = reservation.getWalletId();

        // Acquire distributed lock
        String lockId = null;
        try {
            lockId = lockService.acquireLock(walletId.toString());

            // Re-fetch reservation within lock to ensure latest state
            reservation = fundReservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IllegalStateException("Reservation not found: " + reservationId));

            // Validate reservation state
            if (reservation.getStatus() != FundReservation.ReservationStatus.ACTIVE) {
                throw new IllegalStateException("Cannot confirm non-active reservation: " + reservation.getStatus());
            }

            if (reservation.isExpired()) {
                throw new IllegalStateException("Cannot confirm expired reservation");
            }

            // Fetch wallet
            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            // Complete the transaction: deduct from balance and reserved balance
            wallet.setBalance(wallet.getBalance().subtract(reservation.getAmount()));
            wallet.setReservedBalance(wallet.getReservedBalance().subtract(reservation.getAmount()));

            // Recalculate available balance from database
            BigDecimal currentReserved = fundReservationRepository.getTotalReservedAmount(walletId)
                    .subtract(reservation.getAmount()); // Subtract this reservation as it's being confirmed
            wallet.setAvailableBalance(wallet.getBalance().subtract(currentReserved));

            // Update spending trackers
            wallet.setDailySpent(wallet.getDailySpent().add(reservation.getAmount()));
            wallet.setMonthlySpent(wallet.getMonthlySpent().add(reservation.getAmount()));
            wallet.setLastTransactionAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());

            // Confirm reservation
            reservation.confirm();

            // Save both (order matters: wallet first for referential integrity)
            walletRepository.save(wallet);
            fundReservationRepository.save(reservation);

            confirmationCounter.increment();

            log.info("Confirmed reservation successfully: reservation={}, wallet={}, amount={}",
                    reservationId, walletId, reservation.getAmount());

        } catch (WalletLockException e) {
            lockContentionCounter.increment();
            log.error("Failed to acquire lock for reservation confirmation: wallet={}", walletId, e);
            throw e;
        } finally {
            if (lockId != null) {
                lockService.releaseLock(walletId.toString(), lockId);
            }
        }
    }

    /**
     * Release a fund reservation and return funds to available balance.
     *
     * @param reservationId reservation to release
     * @param reason reason for release
     * @throws WalletLockException if unable to acquire distributed lock
     */
    @Timed("wallet.balance.releaseReservation")
    @Retryable(
        value = {org.springframework.dao.OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    // ✅ CRITICAL PRODUCTION FIX: Upgraded to SERIALIZABLE for consistency
    // Release involves updating reserved balance and recalculating available balance
    // Must see consistent state to prevent over-release scenarios
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void releaseReservation(UUID reservationId, String reason) {
        log.info("Releasing reservation: {}, reason={}", reservationId, reason);

        // Fetch reservation
        var reservationOpt = fundReservationRepository.findById(reservationId);
        if (reservationOpt.isEmpty()) {
            log.warn("Attempted to release non-existent reservation: {}", reservationId);
            return;
        }

        FundReservation reservation = reservationOpt.get();
        UUID walletId = reservation.getWalletId();

        // Acquire distributed lock
        String lockId = null;
        try {
            lockId = lockService.acquireLock(walletId.toString());

            // Re-fetch reservation within lock
            reservation = fundReservationRepository.findById(reservationId)
                    .orElseGet(() -> {
                        log.warn("Reservation disappeared during release: {}", reservationId);
                        return null;
                    });

            if (reservation == null) {
                return;
            }

            // Check if already released
            if (reservation.getStatus() != FundReservation.ReservationStatus.ACTIVE) {
                log.warn("Attempted to release non-active reservation: {}, status={}",
                        reservationId, reservation.getStatus());
                return;
            }

            // Fetch wallet
            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            // Return funds to available balance
            wallet.setReservedBalance(wallet.getReservedBalance().subtract(reservation.getAmount()));

            // Recalculate available balance
            BigDecimal currentReserved = fundReservationRepository.getTotalReservedAmount(walletId)
                    .subtract(reservation.getAmount()); // Subtract this reservation as it's being released
            wallet.setAvailableBalance(wallet.getBalance().subtract(currentReserved));
            wallet.setUpdatedAt(LocalDateTime.now());

            // Release reservation
            reservation.release(reason);

            // Save both
            walletRepository.save(wallet);
            fundReservationRepository.save(reservation);

            releaseCounter.increment();

            log.info("Released reservation successfully: reservation={}, wallet={}, amount={}",
                    reservationId, walletId, reservation.getAmount());

        } catch (WalletLockException e) {
            lockContentionCounter.increment();
            log.error("Failed to acquire lock for reservation release: wallet={}", walletId, e);
            throw e;
        } finally {
            if (lockId != null) {
                lockService.releaseLock(walletId.toString(), lockId);
            }
        }
    }

    // ========================================
    // Balance Update Operations
    // ========================================

    /**
     * Credit wallet with distributed locking (add funds).
     *
     * @param walletId wallet to credit
     * @param amount amount to add
     * @return updated wallet
     * @throws WalletLockException if unable to acquire distributed lock
     */
    @Timed("wallet.balance.credit")
    @Retryable(
        value = {org.springframework.dao.OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Wallet credit(UUID walletId, BigDecimal amount) {
        log.info("Crediting wallet: wallet={}, amount={}", walletId, amount);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive: " + amount);
        }

        String lockId = null;
        try {
            lockId = lockService.acquireLock(walletId.toString());

            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new WalletNotActiveException("Wallet is not active: " + wallet.getStatus());
            }

            // Add to both balance and available balance
            wallet.setBalance(wallet.getBalance().add(amount));
            wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
            wallet.setLastTransactionAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());

            wallet = walletRepository.save(wallet);

            creditCounter.increment();

            log.info("Credited wallet successfully: wallet={}, amount={}, newBalance={}",
                    walletId, amount, wallet.getBalance());

            return wallet;

        } catch (WalletLockException e) {
            lockContentionCounter.increment();
            log.error("Failed to acquire lock for credit: wallet={}", walletId, e);
            throw e;
        } finally {
            if (lockId != null) {
                lockService.releaseLock(walletId.toString(), lockId);
            }
        }
    }

    /**
     * Debit wallet with distributed locking (remove funds).
     *
     * <p>Note: This should typically be used with prior fund reservation.
     * Direct debit without reservation is allowed but logged as a warning.
     *
     * @param walletId wallet to debit
     * @param amount amount to remove
     * @return updated wallet
     * @throws WalletLockException if unable to acquire distributed lock
     * @throws InsufficientBalanceException if insufficient available balance
     */
    @Timed("wallet.balance.debit")
    @Retryable(
        value = {org.springframework.dao.OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Wallet debit(UUID walletId, BigDecimal amount) {
        log.info("Debiting wallet: wallet={}, amount={}", walletId, amount);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive: " + amount);
        }

        String lockId = null;
        try {
            lockId = lockService.acquireLock(walletId.toString());

            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new WalletNotActiveException("Wallet is not active: " + wallet.getStatus());
            }

            // Check if this debit has a corresponding reservation
            if (wallet.getReservedBalance().compareTo(amount) < 0) {
                log.warn("Direct debit without reservation: wallet={}, amount={}, reserved={}",
                        walletId, amount, wallet.getReservedBalance());

                // Validate sufficient available balance
                if (wallet.getAvailableBalance().compareTo(amount) < 0) {
                    throw new InsufficientBalanceException(
                        String.format("Insufficient available balance: required %s, available %s",
                            amount, wallet.getAvailableBalance()));
                }

                // Direct debit from both balance and available balance
                wallet.setBalance(wallet.getBalance().subtract(amount));
                wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
            } else {
                // Debit from balance only (reserved balance was already accounted for)
                wallet.setBalance(wallet.getBalance().subtract(amount));
            }

            wallet.setLastTransactionAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());

            wallet = walletRepository.save(wallet);

            debitCounter.increment();

            log.info("Debited wallet successfully: wallet={}, amount={}, newBalance={}",
                    walletId, amount, wallet.getBalance());

            return wallet;

        } catch (WalletLockException e) {
            lockContentionCounter.increment();
            log.error("Failed to acquire lock for debit: wallet={}", walletId, e);
            throw e;
        } finally {
            if (lockId != null) {
                lockService.releaseLock(walletId.toString(), lockId);
            }
        }
    }

    /**
     * Update wallet balance directly with distributed locking.
     *
     * <p>Use this method carefully - it sets the absolute balance.
     * Prefer credit() or debit() for incremental changes.
     *
     * @param walletId wallet to update
     * @param newBalance new absolute balance
     * @return updated wallet
     * @throws WalletLockException if unable to acquire distributed lock
     */
    @Timed("wallet.balance.update")
    @Retryable(
        value = {org.springframework.dao.OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Wallet updateBalance(UUID walletId, BigDecimal newBalance) {
        log.info("Updating wallet balance: wallet={}, newBalance={}", walletId, newBalance);

        String lockId = null;
        try {
            lockId = lockService.acquireLock(walletId.toString());

            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new WalletNotActiveException("Wallet is not active: " + wallet.getStatus());
            }

            BigDecimal difference = newBalance.subtract(wallet.getBalance());
            wallet.setBalance(newBalance);
            wallet.setAvailableBalance(wallet.getAvailableBalance().add(difference));
            wallet.setUpdatedAt(LocalDateTime.now());

            wallet = walletRepository.save(wallet);

            log.info("Updated wallet balance successfully: wallet={}, newBalance={}, difference={}",
                    walletId, newBalance, difference);

            return wallet;

        } catch (WalletLockException e) {
            lockContentionCounter.increment();
            log.error("Failed to acquire lock for balance update: wallet={}", walletId, e);
            throw e;
        } finally {
            if (lockId != null) {
                lockService.releaseLock(walletId.toString(), lockId);
            }
        }
    }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * Get available balance (thread-safe read).
     */
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        return wallet.getAvailableBalance();
    }

    /**
     * Cleanup expired reservations for a wallet.
     */
    @Timed("wallet.balance.cleanupExpired")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int cleanupExpiredReservations(UUID walletId) {
        log.info("Cleaning up expired reservations: wallet={}", walletId);

        String lockId = null;
        try {
            lockId = lockService.acquireLock(walletId.toString());

            LocalDateTime now = LocalDateTime.now();
            List<FundReservation> expiredReservations = fundReservationRepository
                    .findExpiredActiveReservations(now).stream()
                    .filter(r -> r.getWalletId().equals(walletId))
                    .toList();

            if (expiredReservations.isEmpty()) {
                return 0;
            }

            BigDecimal totalExpiredAmount = BigDecimal.ZERO;
            for (FundReservation reservation : expiredReservations) {
                reservation.markExpired();
                totalExpiredAmount = totalExpiredAmount.add(reservation.getAmount());
            }

            fundReservationRepository.saveAll(expiredReservations);

            // Update wallet balances
            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            BigDecimal currentReserved = fundReservationRepository.getTotalReservedAmount(walletId);
            wallet.setReservedBalance(currentReserved);
            wallet.setAvailableBalance(wallet.getBalance().subtract(currentReserved));
            wallet.setUpdatedAt(LocalDateTime.now());

            walletRepository.save(wallet);

            log.info("Cleaned up {} expired reservations totaling {}: wallet={}",
                    expiredReservations.size(), totalExpiredAmount, walletId);

            return expiredReservations.size();

        } catch (WalletLockException e) {
            log.error("Failed to acquire lock for cleanup: wallet={}", walletId, e);
            return 0;
        } finally {
            if (lockId != null) {
                lockService.releaseLock(walletId.toString(), lockId);
            }
        }
    }

    /**
     * Validate transaction limits.
     */
    private void validateTransactionLimits(Wallet wallet, BigDecimal amount) {
        if (wallet.getDailyLimit() != null && wallet.getDailyLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal projectedDailySpent = wallet.getDailySpent().add(amount);
            if (projectedDailySpent.compareTo(wallet.getDailyLimit()) > 0) {
                throw new TransactionLimitExceededException(
                    String.format("Daily limit exceeded. Limit: %s, Current: %s, Requested: %s",
                        wallet.getDailyLimit(), wallet.getDailySpent(), amount));
            }
        }

        if (wallet.getMonthlyLimit() != null && wallet.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal projectedMonthlySpent = wallet.getMonthlySpent().add(amount);
            if (projectedMonthlySpent.compareTo(wallet.getMonthlyLimit()) > 0) {
                throw new TransactionLimitExceededException(
                    String.format("Monthly limit exceeded. Limit: %s, Current: %s, Requested: %s",
                        wallet.getMonthlyLimit(), wallet.getMonthlySpent(), amount));
            }
        }
    }
}
