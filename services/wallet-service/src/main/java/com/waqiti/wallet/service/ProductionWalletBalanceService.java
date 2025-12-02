package com.waqiti.wallet.service;

import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.wallet.domain.*;
import com.waqiti.wallet.dto.BalanceUpdateRequest;
import com.waqiti.wallet.dto.FundReservationRequest;
import com.waqiti.wallet.dto.FundReservationResponse;
import com.waqiti.wallet.exception.*;
import com.waqiti.wallet.repository.FundReservationRepository;
import com.waqiti.wallet.repository.WalletRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * PRODUCTION-GRADE WALLET BALANCE SERVICE
 *
 * CRITICAL SECURITY FIXES:
 * 1. Distributed Redis-based locking (prevents race conditions across multiple instances)
 * 2. Persistent fund reservations (survives service restarts - no double-spending)
 * 3. Optimistic locking with @Version (handles concurrent updates)
 * 4. Idempotency support (prevents duplicate reservations)
 * 5. Retry logic with exponential backoff (handles transient failures)
 * 6. Comprehensive error handling (fails safely)
 * 7. Prometheus metrics (observability)
 * 8. Audit logging (compliance)
 *
 * This service replaces the vulnerable JVM-local synchronization in Wallet.java
 * and provides production-grade distributed transaction safety.
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
public class ProductionWalletBalanceService {

    private final WalletRepository walletRepository;
    private final FundReservationRepository fundReservationRepository;
    private final DistributedLockService distributedLockService;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter reservationSuccessCounter;
    private Counter reservationFailureCounter;
    private Counter doubleSpendingPreventedCounter;
    private Counter optimisticLockRetryCounter;
    private Timer lockAcquisitionTimer;
    private Timer reservationDurationTimer;

    // Configuration
    private static final int DEFAULT_RESERVATION_TTL_MINUTES = 5;
    private static final int MAX_LOCK_WAIT_SECONDS = 10;
    private static final int LOCK_LEASE_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @PostConstruct
    public void initializeMetrics() {
        reservationSuccessCounter = Counter.builder("wallet.reservation.success")
                .description("Successful fund reservations")
                .tag("service", "wallet-balance")
                .register(meterRegistry);

        reservationFailureCounter = Counter.builder("wallet.reservation.failure")
                .description("Failed fund reservations")
                .tag("service", "wallet-balance")
                .register(meterRegistry);

        doubleSpendingPreventedCounter = Counter.builder("wallet.double_spending.prevented")
                .description("Double-spending attempts prevented by idempotency checks")
                .tag("service", "wallet-balance")
                .register(meterRegistry);

        optimisticLockRetryCounter = Counter.builder("wallet.optimistic_lock.retry")
                .description("Optimistic lock retries")
                .tag("service", "wallet-balance")
                .register(meterRegistry);

        lockAcquisitionTimer = Timer.builder("wallet.lock.acquisition")
                .description("Time to acquire distributed lock")
                .tag("service", "wallet-balance")
                .register(meterRegistry);

        reservationDurationTimer = Timer.builder("wallet.reservation.duration")
                .description("Total reservation operation duration")
                .tag("service", "wallet-balance")
                .register(meterRegistry);
    }

    /**
     * CRITICAL: Reserve funds with distributed locking and persistent storage
     *
     * This method provides production-grade fund reservation with:
     * - Distributed Redis locking (works across multiple service instances)
     * - Persistent storage (survives service restarts)
     * - Idempotency (prevents duplicate reservations)
     * - Optimistic locking (handles concurrent updates)
     * - Retry logic (handles transient failures)
     *
     * @param request Fund reservation request with amount, transactionId, walletId
     * @return FundReservationResponse with reservationId and status
     * @throws InsufficientBalanceException if wallet has insufficient available balance
     * @throws WalletNotFoundException if wallet doesn't exist
     * @throws WalletNotActiveException if wallet is not in ACTIVE status
     * @throws DuplicateReservationException if idempotency key already used
     * @throws WalletLockException if unable to acquire distributed lock
     */
    @Timed(value = "wallet.reserve_funds", description = "Time to reserve funds")
    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public FundReservationResponse reserveFunds(FundReservationRequest request) {
        Timer.Sample reservationSample = Timer.start(meterRegistry);

        try {
            log.info("Reserving funds - WalletId: {}, Amount: {}, TransactionId: {}, IdempotencyKey: {}",
                    request.getWalletId(), request.getAmount(), request.getTransactionId(),
                    request.getIdempotencyKey());

            // Input validation
            validateReservationRequest(request);

            // Check idempotency - prevent duplicate reservations
            if (request.getIdempotencyKey() != null) {
                Optional<FundReservation> existingReservation =
                        fundReservationRepository.findByIdempotencyKey(request.getIdempotencyKey());

                if (existingReservation.isPresent()) {
                    doubleSpendingPreventedCounter.increment();
                    log.warn("SECURITY: Double-spending attempt prevented - IdempotencyKey: {} already used",
                            request.getIdempotencyKey());
                    throw new DuplicateReservationException(
                            "Reservation already exists for idempotency key: " + request.getIdempotencyKey(),
                            existingReservation.get().getId()
                    );
                }
            }

            // Acquire distributed lock for wallet
            String lockKey = "wallet:lock:" + request.getWalletId();
            RLock lock = distributedLockService.getLock(lockKey);

            Timer.Sample lockSample = Timer.start(meterRegistry);
            boolean lockAcquired = false;

            try {
                // Try to acquire lock with timeout
                lockAcquired = lock.tryLock(MAX_LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
                lockSample.stop(lockAcquisitionTimer);

                if (!lockAcquired) {
                    log.error("Failed to acquire lock for wallet: {} after {} seconds",
                            request.getWalletId(), MAX_LOCK_WAIT_SECONDS);
                    throw new WalletLockException(
                            "Unable to acquire lock for wallet: " + request.getWalletId() +
                            ". System is under high load. Please retry."
                    );
                }

                log.debug("Acquired distributed lock for wallet: {}", request.getWalletId());

                // Load wallet with pessimistic lock
                Wallet wallet = walletRepository.findByIdWithPessimisticLock(request.getWalletId())
                        .orElseThrow(() -> new WalletNotFoundException(
                                "Wallet not found: " + request.getWalletId()
                        ));

                // Validate wallet status
                if (wallet.getStatus() != WalletStatus.ACTIVE) {
                    throw new WalletNotActiveException(
                            "Wallet is not active: " + wallet.getStatus()
                    );
                }

                // Check sufficient balance
                if (wallet.getAvailableBalance().compareTo(request.getAmount()) < 0) {
                    log.warn("Insufficient balance - WalletId: {}, Available: {}, Requested: {}",
                            wallet.getId(), wallet.getAvailableBalance(), request.getAmount());
                    throw new InsufficientBalanceException(
                            "Insufficient available balance. Available: " + wallet.getAvailableBalance() +
                            ", Requested: " + request.getAmount()
                    );
                }

                // Create persistent fund reservation
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expiresAt = now.plusMinutes(
                        request.getTtlMinutes() != null ? request.getTtlMinutes() : DEFAULT_RESERVATION_TTL_MINUTES
                );

                FundReservation reservation = FundReservation.builder()
                        .walletId(wallet.getId())
                        .transactionId(request.getTransactionId())
                        .amount(request.getAmount())
                        .currency(wallet.getCurrency())
                        .status(FundReservationStatus.ACTIVE)
                        .createdAt(now)
                        .expiresAt(expiresAt)
                        .reason(request.getReason())
                        .idempotencyKey(request.getIdempotencyKey())
                        .build();

                // Save reservation BEFORE updating wallet balance (for rollback safety)
                reservation = fundReservationRepository.save(reservation);

                // Update wallet balances atomically
                BigDecimal newAvailableBalance = wallet.getAvailableBalance().subtract(request.getAmount());
                BigDecimal newReservedBalance = wallet.getReservedBalance().add(request.getAmount());

                wallet.setAvailableBalance(newAvailableBalance);
                wallet.setReservedBalance(newReservedBalance);
                wallet.setUpdatedAt(now);
                wallet.setUpdatedBy("SYSTEM:FUND_RESERVATION");

                // Save wallet (optimistic locking will detect concurrent modifications via @Version)
                walletRepository.save(wallet);

                reservationSuccessCounter.increment();
                log.info("Successfully reserved funds - ReservationId: {}, WalletId: {}, Amount: {}",
                        reservation.getId(), wallet.getId(), request.getAmount());

                return FundReservationResponse.builder()
                        .reservationId(reservation.getId())
                        .walletId(wallet.getId())
                        .amount(request.getAmount())
                        .currency(wallet.getCurrency().toString())
                        .status(FundReservationStatus.ACTIVE.toString())
                        .expiresAt(expiresAt)
                        .createdAt(now)
                        .build();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted while waiting for lock - WalletId: {}", request.getWalletId(), e);
                throw new WalletLockException("Thread interrupted while acquiring wallet lock", e);
            } finally {
                // CRITICAL: Always release lock in finally block
                if (lockAcquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("Released distributed lock for wallet: {}", request.getWalletId());
                }
            }

        } catch (ObjectOptimisticLockingFailureException e) {
            // Optimistic locking failure - retry
            optimisticLockRetryCounter.increment();
            log.warn("Optimistic locking failure during fund reservation - WalletId: {}, retrying...",
                    request.getWalletId());
            throw e; // Will trigger @Retryable

        } catch (InsufficientBalanceException | WalletNotFoundException | WalletNotActiveException |
                 DuplicateReservationException | WalletLockException e) {
            // Expected business exceptions
            reservationFailureCounter.increment();
            throw e;

        } catch (Exception e) {
            reservationFailureCounter.increment();
            log.error("Unexpected error during fund reservation - WalletId: {}, Amount: {}",
                    request.getWalletId(), request.getAmount(), e);
            throw new FundReservationException("Failed to reserve funds: " + e.getMessage(), e);

        } finally {
            reservationSample.stop(reservationDurationTimer);
        }
    }

    /**
     * CRITICAL: Confirm fund reservation (complete transaction)
     *
     * Confirms a reservation and moves funds from reserved to balance.
     * This is called when a transaction completes successfully.
     *
     * @param reservationId Reservation ID to confirm
     * @throws ReservationNotFoundException if reservation doesn't exist
     * @throws ReservationExpiredException if reservation has expired
     * @throws InvalidReservationStateException if reservation is not in ACTIVE status
     */
    @Timed(value = "wallet.confirm_reservation", description = "Time to confirm reservation")
    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public void confirmReservation(UUID reservationId) {
        log.info("Confirming fund reservation: {}", reservationId);

        FundReservation reservation = fundReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + reservationId
                ));

        // Validate reservation state
        if (reservation.getStatus() != FundReservationStatus.ACTIVE) {
            throw new InvalidReservationStateException(
                    "Reservation is not active: " + reservation.getStatus()
            );
        }

        if (LocalDateTime.now().isAfter(reservation.getExpiresAt())) {
            throw new ReservationExpiredException(
                    "Reservation has expired: " + reservationId
            );
        }

        // Acquire distributed lock
        String lockKey = "wallet:lock:" + reservation.getWalletId();
        RLock lock = distributedLockService.getLock(lockKey);

        try {
            if (!lock.tryLock(MAX_LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new WalletLockException("Unable to acquire lock for wallet: " + reservation.getWalletId());
            }

            // Load wallet with pessimistic lock
            Wallet wallet = walletRepository.findByIdWithPessimisticLock(reservation.getWalletId())
                    .orElseThrow(() -> new WalletNotFoundException(
                            "Wallet not found: " + reservation.getWalletId()
                    ));

            // Update reservation status
            LocalDateTime now = LocalDateTime.now();
            reservation.setStatus(FundReservationStatus.CONFIRMED);
            reservation.setConfirmedAt(now);
            fundReservationRepository.save(reservation);

            // Update wallet balances - reserved funds become permanent debit
            BigDecimal newReservedBalance = wallet.getReservedBalance().subtract(reservation.getAmount());
            BigDecimal newBalance = wallet.getBalance().subtract(reservation.getAmount());

            wallet.setReservedBalance(newReservedBalance);
            wallet.setBalance(newBalance);
            wallet.setUpdatedAt(now);
            wallet.setUpdatedBy("SYSTEM:RESERVATION_CONFIRM");

            walletRepository.save(wallet);

            log.info("Successfully confirmed reservation - ReservationId: {}, WalletId: {}, Amount: {}",
                    reservationId, wallet.getId(), reservation.getAmount());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WalletLockException("Thread interrupted while acquiring lock", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * CRITICAL: Release fund reservation (rollback/cancel transaction)
     *
     * Releases a reservation and returns funds to available balance.
     * This is called when a transaction fails or is cancelled.
     *
     * @param reservationId Reservation ID to release
     * @param reason Reason for releasing reservation (for audit)
     */
    @Timed(value = "wallet.release_reservation", description = "Time to release reservation")
    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public void releaseReservation(UUID reservationId, String reason) {
        log.info("Releasing fund reservation: {} - Reason: {}", reservationId, reason);

        FundReservation reservation = fundReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + reservationId
                ));

        // Only release if in ACTIVE status
        if (reservation.getStatus() != FundReservationStatus.ACTIVE) {
            log.warn("Attempted to release non-active reservation: {} - Status: {}",
                    reservationId, reservation.getStatus());
            return; // Idempotent - already processed
        }

        // Acquire distributed lock
        String lockKey = "wallet:lock:" + reservation.getWalletId();
        RLock lock = distributedLockService.getLock(lockKey);

        try {
            if (!lock.tryLock(MAX_LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new WalletLockException("Unable to acquire lock for wallet: " + reservation.getWalletId());
            }

            // Load wallet with pessimistic lock
            Wallet wallet = walletRepository.findByIdWithPessimisticLock(reservation.getWalletId())
                    .orElseThrow(() -> new WalletNotFoundException(
                            "Wallet not found: " + reservation.getWalletId()
                    ));

            // Update reservation status
            LocalDateTime now = LocalDateTime.now();
            reservation.setStatus(FundReservationStatus.RELEASED);
            reservation.setReleasedAt(now);
            reservation.setReason(reason);
            fundReservationRepository.save(reservation);

            // Return reserved funds to available balance
            BigDecimal newAvailableBalance = wallet.getAvailableBalance().add(reservation.getAmount());
            BigDecimal newReservedBalance = wallet.getReservedBalance().subtract(reservation.getAmount());

            wallet.setAvailableBalance(newAvailableBalance);
            wallet.setReservedBalance(newReservedBalance);
            wallet.setUpdatedAt(now);
            wallet.setUpdatedBy("SYSTEM:RESERVATION_RELEASE");

            walletRepository.save(wallet);

            log.info("Successfully released reservation - ReservationId: {}, WalletId: {}, Amount: {}",
                    reservationId, wallet.getId(), reservation.getAmount());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WalletLockException("Thread interrupted while acquiring lock", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Cleanup expired reservations (scheduled job)
     *
     * This should be called periodically (e.g., every minute) to automatically
     * release expired reservations and return funds to available balance.
     */
    @Timed(value = "wallet.cleanup_expired_reservations", description = "Time to cleanup expired reservations")
    public int cleanupExpiredReservations() {
        log.info("Starting cleanup of expired reservations...");

        LocalDateTime now = LocalDateTime.now();
        List<FundReservation> expiredReservations = fundReservationRepository
                .findExpiredActiveReservations(now);

        int cleanedUp = 0;
        for (FundReservation reservation : expiredReservations) {
            try {
                releaseReservation(reservation.getId(), "AUTO_CLEANUP:EXPIRED");
                cleanedUp++;
            } catch (Exception e) {
                log.error("Failed to cleanup expired reservation: {}", reservation.getId(), e);
            }
        }

        log.info("Completed cleanup of expired reservations - Count: {}", cleanedUp);
        return cleanedUp;
    }

    /**
     * Validate fund reservation request
     */
    private void validateReservationRequest(FundReservationRequest request) {
        if (request.getWalletId() == null) {
            throw new IllegalArgumentException("WalletId is required");
        }
        if (request.getTransactionId() == null) {
            throw new IllegalArgumentException("TransactionId is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getTtlMinutes() != null && request.getTtlMinutes() <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
    }

    /**
     * Get active reservations for a wallet
     */
    @Transactional(readOnly = true)
    public List<FundReservation> getActiveReservations(UUID walletId) {
        return fundReservationRepository.findActiveByWalletId(walletId);
    }

    /**
     * Get total reserved amount for a wallet
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalReservedAmount(UUID walletId) {
        return fundReservationRepository.getTotalReservedAmount(walletId);
    }
}
