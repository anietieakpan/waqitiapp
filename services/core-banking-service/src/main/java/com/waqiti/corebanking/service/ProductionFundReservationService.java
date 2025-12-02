package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.FundReservation;
import com.waqiti.corebanking.repository.FundReservationRepository;
import com.waqiti.corebanking.exception.InsufficientFundsException;
import com.waqiti.corebanking.exception.ReservationNotFoundException;
import com.waqiti.corebanking.exception.ReservationExpiredException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PRODUCTION-GRADE Fund Reservation Service
 * Replaces in-memory reservation tracking with persistent, atomic operations
 * 
 * Features:
 * - Database-persistent reservations
 * - Atomic reservation operations with balance checks
 * - Automatic expiration handling
 * - Comprehensive monitoring and metrics
 * - Exception handling and recovery
 * - Scheduled cleanup of expired reservations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionFundReservationService {
    
    private final FundReservationRepository reservationRepository;
    private final MeterRegistry meterRegistry;
    
    private static final Duration DEFAULT_RESERVATION_DURATION = Duration.ofMinutes(30);
    
    /**
     * CRITICAL SECURITY: Atomically reserve funds with balance verification
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FundReservation reserveFunds(String accountId, String transactionId, 
                                      BigDecimal amount, String currency, 
                                      String reason, String reservedBy, 
                                      String service) {
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            log.info("Attempting to reserve {} {} for transaction {} in account {}", 
                amount, currency, transactionId, accountId);
            
            // Validate input parameters
            validateReservationParameters(accountId, transactionId, amount, currency, reason, reservedBy);
            
            // Check if reservation already exists for this transaction
            Optional<FundReservation> existingReservation = 
                reservationRepository.findActiveByTransactionId(transactionId);
            
            if (existingReservation.isPresent()) {
                log.warn("Reservation already exists for transaction: {}", transactionId);
                meterRegistry.counter("fund.reservations.duplicate").increment();
                return existingReservation.get();
            }
            
            LocalDateTime expiresAt = LocalDateTime.now().plus(DEFAULT_RESERVATION_DURATION);
            LocalDateTime createdAt = LocalDateTime.now();
            
            // Attempt atomic reservation with balance check
            int reservationCreated = reservationRepository.atomicReserveFunds(
                accountId, transactionId, amount, currency, reason, 
                expiresAt, reservedBy, service, createdAt);
            
            if (reservationCreated == 0) {
                log.warn("Insufficient funds for reservation: {} {} in account {}", 
                    amount, currency, accountId);
                meterRegistry.counter("fund.reservations.insufficient_funds").increment();
                throw new InsufficientFundsException(
                    "Insufficient available balance for reservation of " + amount + " " + currency);
            }
            
            // Retrieve the created reservation
            FundReservation reservation = reservationRepository.findActiveByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalStateException("Reservation created but not found"));
            
            log.info("Successfully reserved {} {} for transaction {} (Reservation ID: {})", 
                amount, currency, transactionId, reservation.getId());
            
            meterRegistry.counter("fund.reservations.created").increment();
            timer.stop(Timer.builder("fund.reservation.creation.time")
                .tag("status", "success")
                .register(meterRegistry));
            
            return reservation;
            
        } catch (InsufficientFundsException e) {
            timer.stop(Timer.builder("fund.reservation.creation.time")
                .tag("status", "insufficient_funds")
                .register(meterRegistry));
            throw e;
        } catch (Exception e) {
            timer.stop(Timer.builder("fund.reservation.creation.time")
                .tag("status", "error")
                .register(meterRegistry));
            log.error("Error creating fund reservation", e);
            throw new RuntimeException("Failed to create fund reservation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Release fund reservation
     */
    @Transactional
    public void releaseFunds(String transactionId, String releasedBy, String reason) {
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            log.info("Releasing fund reservation for transaction: {}", transactionId);
            
            int releasedCount = reservationRepository.releaseByTransactionId(
                transactionId, LocalDateTime.now(), releasedBy, reason);
            
            if (releasedCount == 0) {
                log.warn("No active reservation found for transaction: {}", transactionId);
                meterRegistry.counter("fund.reservations.release.not_found").increment();
                throw new ReservationNotFoundException("No active reservation found for transaction: " + transactionId);
            }
            
            log.info("Successfully released fund reservation for transaction: {}", transactionId);
            meterRegistry.counter("fund.reservations.released").increment();
            timer.stop(Timer.builder("fund.reservation.release.time")
                .tag("status", "success")
                .register(meterRegistry));
            
        } catch (Exception e) {
            timer.stop(Timer.builder("fund.reservation.release.time")
                .tag("status", "error")
                .register(meterRegistry));
            log.error("Error releasing fund reservation for transaction: {}", transactionId, e);
            throw new RuntimeException("Failed to release fund reservation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Use fund reservation (mark as used for completed transaction)
     */
    @Transactional
    public void useFunds(String transactionId, String usedBy) {
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            log.info("Using fund reservation for completed transaction: {}", transactionId);
            
            // Verify reservation exists and is active
            Optional<FundReservation> reservationOpt = 
                reservationRepository.findActiveByTransactionIdWithLock(transactionId);
            
            if (reservationOpt.isEmpty()) {
                log.warn("No active reservation found for transaction: {}", transactionId);
                meterRegistry.counter("fund.reservations.use.not_found").increment();
                throw new ReservationNotFoundException("No active reservation found for transaction: " + transactionId);
            }
            
            FundReservation reservation = reservationOpt.get();
            
            if (reservation.isExpired()) {
                log.warn("Cannot use expired reservation for transaction: {}", transactionId);
                meterRegistry.counter("fund.reservations.use.expired").increment();
                throw new ReservationExpiredException("Reservation for transaction " + transactionId + " has expired");
            }
            
            int usedCount = reservationRepository.useReservation(
                transactionId, LocalDateTime.now(), usedBy);
            
            if (usedCount == 0) {
                throw new IllegalStateException("Reservation exists but could not be marked as used");
            }
            
            log.info("Successfully used fund reservation for transaction: {}", transactionId);
            meterRegistry.counter("fund.reservations.used").increment();
            timer.stop(Timer.builder("fund.reservation.use.time")
                .tag("status", "success")
                .register(meterRegistry));
            
        } catch (Exception e) {
            timer.stop(Timer.builder("fund.reservation.use.time")
                .tag("status", "error")
                .register(meterRegistry));
            log.error("Error using fund reservation for transaction: {}", transactionId, e);
            throw new RuntimeException("Failed to use fund reservation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get total reserved amount for an account
     *
     * CRITICAL SECURITY FIX: Changed from fail-safe (return ZERO) to fail-closed (throw exception)
     * Returning ZERO on error could allow overdrafts if reservation system is down
     */
    public BigDecimal getTotalReservedAmount(String accountId) {
        try {
            BigDecimal totalReserved = reservationRepository.getTotalReservedAmount(accountId);
            log.debug("Total reserved amount for account {}: {}", accountId, totalReserved);
            return totalReserved != null ? totalReserved : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to get total reserved amount for account: {} - DENYING operation for safety",
                accountId, e);

            // FAIL CLOSED: Throw exception instead of returning ZERO
            // Returning ZERO could allow overdrafts if reservation data is temporarily unavailable
            throw new RuntimeException(
                "Fund reservation system error - cannot verify reserved funds for account: " + accountId +
                ". Operation denied for financial safety. Error: " + e.getMessage(), e);
        }
    }

    /**
     * Get reserved amount for specific transaction
     *
     * CRITICAL SECURITY FIX: Changed from fail-safe (return ZERO) to fail-closed (throw exception)
     * Returning ZERO on error could allow double-spending if we can't verify existing reservations
     */
    public BigDecimal getReservedAmountForTransaction(String transactionId) {
        try {
            BigDecimal reservedAmount = reservationRepository.getReservedAmountForTransaction(transactionId);
            return reservedAmount != null ? reservedAmount : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to get reserved amount for transaction: {} - DENYING operation for safety",
                transactionId, e);

            // FAIL CLOSED: Throw exception instead of returning ZERO
            // Returning ZERO could allow double-spending if reservation lookup fails
            throw new RuntimeException(
                "Fund reservation system error - cannot verify reservation for transaction: " + transactionId +
                ". Operation denied for financial safety. Error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extend reservation expiration time
     */
    @Transactional
    public void extendReservation(String transactionId, Duration additionalTime, String extendedBy) {
        try {
            Optional<FundReservation> reservationOpt = 
                reservationRepository.findActiveByTransactionIdWithLock(transactionId);
            
            if (reservationOpt.isEmpty()) {
                throw new ReservationNotFoundException("No active reservation found for transaction: " + transactionId);
            }
            
            FundReservation reservation = reservationOpt.get();
            LocalDateTime newExpiry = reservation.getExpiresAt().plus(additionalTime);
            
            reservation.extend(newExpiry, extendedBy);
            reservationRepository.save(reservation);
            
            log.info("Extended reservation for transaction {} until {}", transactionId, newExpiry);
            meterRegistry.counter("fund.reservations.extended").increment();
            
        } catch (Exception e) {
            log.error("Error extending reservation for transaction: {}", transactionId, e);
            throw new RuntimeException("Failed to extend reservation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Scheduled cleanup of expired reservations
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void cleanupExpiredReservations() {
        try {
            log.debug("Starting cleanup of expired reservations");
            
            LocalDateTime now = LocalDateTime.now();
            int expiredCount = reservationRepository.expireReservations(now);
            
            if (expiredCount > 0) {
                log.info("Expired {} fund reservations", expiredCount);
                meterRegistry.counter("fund.reservations.expired").increment(expiredCount);
            }
            
            meterRegistry.counter("fund.reservations.cleanup.runs").increment();
            
        } catch (Exception e) {
            log.error("Error during expired reservations cleanup", e);
            meterRegistry.counter("fund.reservations.cleanup.errors").increment();
        }
    }
    
    /**
     * Get reservation statistics for monitoring
     */
    public List<FundReservationRepository.ReservationStatistics> getReservationStatistics() {
        return reservationRepository.getReservationStatistics();
    }
    
    private void validateReservationParameters(String accountId, String transactionId, 
                                             BigDecimal amount, String currency, 
                                             String reason, String reservedBy) {
        if (accountId == null || accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }
        
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reservation reason cannot be null or empty");
        }
        
        if (reservedBy == null || reservedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Reserved by cannot be null or empty");
        }
        
        // Business rule validations
        if (amount.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("Reservation amount exceeds maximum limit");
        }
    }
}