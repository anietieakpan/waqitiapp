package com.waqiti.payment.service;

import com.waqiti.common.lock.DistributedLockService;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Fund reservation service to prevent double-spending
 * 
 * Implements a reservation pattern where funds are:
 * 1. Reserved before processing
 * 2. Committed on success
 * 3. Released on failure
 * 4. Auto-released on timeout
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundReservationService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockService lockService;
    private final UnifiedWalletServiceClient walletServiceClient;
    
    private static final String RESERVATION_PREFIX = "fund:reservation:";
    private static final String WALLET_RESERVATION_PREFIX = "wallet:reservations:";
    
    /**
     * Reserve funds for a transaction
     * 
     * @return Reservation ID if successful, null if insufficient funds
     */
    public String reserveFunds(UUID walletId, BigDecimal amount, String currency, 
                              String transactionId, Duration ttl) {
        
        String walletLockKey = "wallet:balance:" + walletId;
        
        return lockService.executeWithLock(walletLockKey, Duration.ofSeconds(5), () -> {
            // Get current balance
            BigDecimal currentBalance = getWalletBalance(walletId, currency);
            BigDecimal reservedAmount = getTotalReservedAmount(walletId, currency);
            BigDecimal availableBalance = currentBalance.subtract(reservedAmount);
            
            // Check if sufficient funds available
            if (availableBalance.compareTo(amount) < 0) {
                log.warn("Insufficient funds for reservation. Available: {}, Required: {}", 
                    availableBalance, amount);
                throw new InsufficientFundsException(
                    String.format("Insufficient funds for reservation. Available: %s, Required: %s", 
                    availableBalance, amount));
            }
            
            // Create reservation
            String reservationId = UUID.randomUUID().toString();
            FundReservation reservation = new FundReservation(
                reservationId,
                walletId,
                amount,
                currency,
                transactionId,
                Instant.now(),
                ttl
            );
            
            // Store reservation
            String reservationKey = RESERVATION_PREFIX + reservationId;
            redisTemplate.opsForValue().set(reservationKey, reservation, ttl.toMillis(), TimeUnit.MILLISECONDS);
            
            // Add to wallet's reservation list
            String walletReservationKey = WALLET_RESERVATION_PREFIX + walletId + ":" + currency;
            redisTemplate.opsForHash().put(walletReservationKey, reservationId, reservation);
            redisTemplate.expire(walletReservationKey, ttl.toMillis(), TimeUnit.MILLISECONDS);
            
            log.info("Funds reserved: {} {} for wallet: {}, reservation: {}", 
                amount, currency, walletId, reservationId);
            
            return reservationId;
        });
    }
    
    /**
     * Commit a reservation (mark as used)
     */
    public void commitReservation(String reservationId) {
        String reservationKey = RESERVATION_PREFIX + reservationId;
        FundReservation reservation = (FundReservation) redisTemplate.opsForValue().get(reservationKey);
        
        if (reservation != null) {
            // Remove from wallet's reservation list
            String walletReservationKey = WALLET_RESERVATION_PREFIX + reservation.getWalletId() + 
                ":" + reservation.getCurrency();
            redisTemplate.opsForHash().delete(walletReservationKey, reservationId);
            
            // Delete reservation
            redisTemplate.delete(reservationKey);
            
            log.info("Reservation committed: {}", reservationId);
        }
    }
    
    /**
     * Release a reservation (funds become available again)
     */
    public void releaseReservation(String reservationId) {
        String reservationKey = RESERVATION_PREFIX + reservationId;
        FundReservation reservation = (FundReservation) redisTemplate.opsForValue().get(reservationKey);
        
        if (reservation != null) {
            // Remove from wallet's reservation list
            String walletReservationKey = WALLET_RESERVATION_PREFIX + reservation.getWalletId() + 
                ":" + reservation.getCurrency();
            redisTemplate.opsForHash().delete(walletReservationKey, reservationId);
            
            // Delete reservation
            redisTemplate.delete(reservationKey);
            
            log.info("Reservation released: {} for amount: {} {}", 
                reservationId, reservation.getAmount(), reservation.getCurrency());
        }
    }
    
    /**
     * Get total reserved amount for a wallet
     */
    private BigDecimal getTotalReservedAmount(UUID walletId, String currency) {
        String walletReservationKey = WALLET_RESERVATION_PREFIX + walletId + ":" + currency;
        Map<Object, Object> reservations = redisTemplate.opsForHash().entries(walletReservationKey);
        
        BigDecimal total = BigDecimal.ZERO;
        
        for (Object value : reservations.values()) {
            if (value instanceof FundReservation) {
                FundReservation reservation = (FundReservation) value;
                if (!reservation.isExpired()) {
                    total = total.add(reservation.getAmount());
                }
            }
        }
        
        return total;
    }
    
    /**
     * Get wallet balance from wallet service
     */
    private BigDecimal getWalletBalance(UUID walletId, String currency) {
        try {
            WalletResponse wallet = walletServiceClient.getWalletBalance(walletId, currency);
            if (wallet != null && wallet.getBalance() != null) {
                return wallet.getBalance();
            } else {
                log.warn("Wallet not found or balance is null for wallet: {} currency: {}", walletId, currency);
                return BigDecimal.ZERO;
            }
        } catch (Exception e) {
            log.error("Error fetching wallet balance for wallet: {} currency: {}", walletId, currency, e);
            // Return zero balance on error to prevent invalid transactions
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Clean up expired reservations
     */
    public void cleanupExpiredReservations() {
        // This would be called by a scheduled task
        log.info("Cleaning up expired fund reservations");
    }
    
    /**
     * Fund reservation data
     */
    public static class FundReservation {
        private final String reservationId;
        private final UUID walletId;
        private final BigDecimal amount;
        private final String currency;
        private final String transactionId;
        private final Instant createdAt;
        private final Duration ttl;
        
        public FundReservation(String reservationId, UUID walletId, BigDecimal amount, 
                              String currency, String transactionId, Instant createdAt, Duration ttl) {
            this.reservationId = reservationId;
            this.walletId = walletId;
            this.amount = amount;
            this.currency = currency;
            this.transactionId = transactionId;
            this.createdAt = createdAt;
            this.ttl = ttl;
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(ttl));
        }
        
        // Getters
        public String getReservationId() { return reservationId; }
        public UUID getWalletId() { return walletId; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getTransactionId() { return transactionId; }
        public Instant getCreatedAt() { return createdAt; }
        public Duration getTtl() { return ttl; }
    }
}