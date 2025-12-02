package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.FundReservation;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.repository.FundReservationRepository;
import com.waqiti.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CRITICAL SECURITY SERVICE: Manages persistent fund reservations
 * Fixes the double-spending vulnerability by ensuring reservations survive service restarts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundReservationService {
    
    private final FundReservationRepository fundReservationRepository;
    private final WalletRepository walletRepository;
    
    /**
     * Initialize wallet with fund reservation repository
     */
    @Transactional
    public void initializeWalletReservations(Wallet wallet) {
        wallet.setFundReservationRepository(fundReservationRepository);
        log.debug("SECURITY: Initialized wallet {} with persistent fund reservation repository", wallet.getId());
    }
    
    /**
     * SECURITY FIX: Perform dummy initialization for timing attack protection
     */
    public void performDummyInitialization() {
        try {
            // Simulate the computational work of fund reservation initialization
            UUID dummyWalletId = UUID.randomUUID();
            
            // Simulate database lookup (will not find anything)
            fundReservationRepository.findActiveReservationsByWalletId(dummyWalletId);
            
            // Simulate object creation
            FundReservation.builder()
                .walletId(dummyWalletId)
                .transactionId(UUID.randomUUID())
                .amount(java.math.BigDecimal.ZERO)
                .build();
                
            log.debug("SECURITY: Performed dummy fund reservation initialization for timing protection");
            
        } catch (Exception e) {
            log.debug("SECURITY: Dummy fund reservation initialization failed (expected for timing protection)", e);
        }
    }
    
    /**
     * Clean up expired reservations across all wallets (scheduled task)
     */
    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void cleanupExpiredReservations() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Mark expired reservations in database
            int expiredCount = fundReservationRepository.markExpiredReservations(now);
            
            if (expiredCount > 0) {
                log.info("SECURITY: Marked {} expired fund reservations as expired", expiredCount);
                
                // Update wallet balances for affected wallets
                updateWalletBalancesAfterExpiry();
            }
            
        } catch (Exception e) {
            log.error("SECURITY: Error during scheduled cleanup of expired reservations", e);
        }
    }
    
    /**
     * Clean up old completed reservations (keep for audit trail)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldReservations() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deletedCount = fundReservationRepository.deleteOldReservations(cutoff);
            
            if (deletedCount > 0) {
                log.info("SECURITY: Cleaned up {} old completed fund reservations", deletedCount);
            }
            
        } catch (Exception e) {
            log.error("SECURITY: Error during scheduled cleanup of old reservations", e);
        }
    }
    
    /**
     * Get active reservations for a wallet
     */
    @Transactional(readOnly = true)
    public List<FundReservation> getActiveReservations(UUID walletId) {
        return fundReservationRepository.findActiveReservationsByWalletId(walletId);
    }
    
    /**
     * Check if transaction has active reservation
     */
    @Transactional(readOnly = true)
    public boolean hasActiveReservation(UUID transactionId) {
        return fundReservationRepository.findByTransactionId(transactionId)
            .map(r -> r.getStatus() == FundReservation.ReservationStatus.ACTIVE && !r.isExpired())
            .orElse(false);
    }
    
    /**
     * Force release reservation (admin operation)
     */
    @Transactional
    public void forceReleaseReservation(UUID reservationId, String adminReason) {
        var reservationOpt = fundReservationRepository.findById(reservationId);
        if (reservationOpt.isEmpty()) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }
        
        FundReservation reservation = reservationOpt.get();
        if (reservation.getStatus() != FundReservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot release non-active reservation: " + reservation.getStatus());
        }
        
        // Release the reservation
        reservation.release("ADMIN: " + adminReason);
        fundReservationRepository.save(reservation);
        
        // Update wallet balance
        var walletOpt = walletRepository.findById(reservation.getWalletId());
        if (walletOpt.isPresent()) {
            Wallet wallet = walletOpt.get();
            initializeWalletReservations(wallet);
            
            // Recalculate balances
            wallet.cleanupExpiredReservations();
            walletRepository.save(wallet);
        }
        
        log.warn("SECURITY: Admin force-released reservation {} - Reason: {}", 
            reservationId, adminReason);
    }
    
    /**
     * Get reservation statistics for monitoring
     */
    @Transactional(readOnly = true)
    public ReservationStats getReservationStats() {
        List<FundReservation> activeReservations = fundReservationRepository.findActiveReservationsByWalletId(null);
        List<FundReservation> expiredReservations = fundReservationRepository.findExpiredActiveReservations(LocalDateTime.now());
        
        return new ReservationStats(
            activeReservations.size(),
            expiredReservations.size(),
            activeReservations.stream().map(FundReservation::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
        );
    }
    
    /**
     * Update wallet balances after reservation expiry
     */
    private void updateWalletBalancesAfterExpiry() {
        // Get all wallets that might be affected
        List<FundReservation> recentlyExpired = fundReservationRepository.findExpiredActiveReservations(
            LocalDateTime.now().minusMinutes(2));
        
        recentlyExpired.stream()
            .map(FundReservation::getWalletId)
            .distinct()
            .forEach(walletId -> {
                var walletOpt = walletRepository.findById(walletId);
                if (walletOpt.isPresent()) {
                    Wallet wallet = walletOpt.get();
                    initializeWalletReservations(wallet);
                    
                    // This will recalculate balances based on current reservations
                    wallet.cleanupExpiredReservations();
                    walletRepository.save(wallet);
                }
            });
    }
    
    /**
     * Statistics for monitoring
     */
    public static class ReservationStats {
        public final int activeCount;
        public final int expiredCount;
        public final java.math.BigDecimal totalActiveAmount;
        
        public ReservationStats(int activeCount, int expiredCount, java.math.BigDecimal totalActiveAmount) {
            this.activeCount = activeCount;
            this.expiredCount = expiredCount;
            this.totalActiveAmount = totalActiveAmount;
        }
    }
}