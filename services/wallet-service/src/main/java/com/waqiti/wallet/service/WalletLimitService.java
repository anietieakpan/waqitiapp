package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing wallet limits and restrictions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletLimitService {
    
    private final WalletRepository walletRepository;
    
    /**
     * Update wallet daily limit
     *
     * P0-017 FIX: Added SERIALIZABLE isolation to prevent race conditions
     * during concurrent limit updates.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30)
    public void updateDailyLimit(UUID walletId, BigDecimal newLimit, String reason) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        BigDecimal oldLimit = wallet.getDailyLimit();
        wallet.setDailyLimit(newLimit);
        wallet.setUpdatedAt(LocalDateTime.now());
        
        walletRepository.save(wallet);
        
        log.info("Updated daily limit for wallet {}: {} -> {} (reason: {})",
            walletId, oldLimit, newLimit, reason);
    }
    
    /**
     * Update wallet monthly limit
     *
     * P0-017 FIX: Added SERIALIZABLE isolation to prevent race conditions.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30)
    public void updateMonthlyLimit(UUID walletId, BigDecimal newLimit, String reason) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        BigDecimal oldLimit = wallet.getMonthlyLimit();
        wallet.setMonthlyLimit(newLimit);
        wallet.setUpdatedAt(LocalDateTime.now());
        
        walletRepository.save(wallet);
        
        log.info("Updated monthly limit for wallet {}: {} -> {} (reason: {})",
            walletId, oldLimit, newLimit, reason);
    }
    
    /**
     * Update single transaction limit
     *
     * P0-017 FIX: Added SERIALIZABLE isolation to prevent race conditions.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30)
    public void updateSingleTransactionLimit(UUID walletId, BigDecimal newLimit, String reason) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        String oldLimit = (String) wallet.getMetadata().get("singleTransactionLimit");
        wallet.getMetadata().put("singleTransactionLimit", newLimit.toString());
        wallet.setUpdatedAt(LocalDateTime.now());
        
        walletRepository.save(wallet);
        
        log.info("Updated single transaction limit for wallet {}: {} -> {} (reason: {})",
            walletId, oldLimit, newLimit, reason);
    }
    
    /**
     * Update all wallet limits
     *
     * P0-017 FIX: Added SERIALIZABLE isolation to prevent race conditions.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30)
    public void updateAllLimits(UUID walletId, BigDecimal dailyLimit, BigDecimal monthlyLimit,
                               BigDecimal singleTransactionLimit, String reason) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        wallet.setDailyLimit(dailyLimit);
        wallet.setMonthlyLimit(monthlyLimit);
        wallet.getMetadata().put("singleTransactionLimit", singleTransactionLimit.toString());
        wallet.setUpdatedAt(LocalDateTime.now());
        
        walletRepository.save(wallet);
        
        log.info("Updated all limits for wallet {}: daily={}, monthly={}, single={} (reason: {})",
            walletId, dailyLimit, monthlyLimit, singleTransactionLimit, reason);
    }
    
    /**
     * Apply temporary limit restriction
     *
     * P0-017 FIX: Added SERIALIZABLE isolation to prevent race conditions.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30)
    public void applyTemporaryRestriction(UUID walletId, BigDecimal temporaryLimit,
                                        LocalDateTime until, String reason) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        // Store original limits for restoration
        wallet.getMetadata().put("originalDailyLimit", wallet.getDailyLimit().toString());
        wallet.getMetadata().put("originalMonthlyLimit", wallet.getMonthlyLimit().toString());
        wallet.getMetadata().put("restrictionUntil", until.toString());
        wallet.getMetadata().put("restrictionReason", reason);
        
        // Apply temporary limits
        wallet.setDailyLimit(temporaryLimit);
        wallet.setMonthlyLimit(temporaryLimit);
        wallet.setUpdatedAt(LocalDateTime.now());
        
        walletRepository.save(wallet);
        
        log.warn("Applied temporary restriction to wallet {}: limit={}, until={}, reason={}",
            walletId, temporaryLimit, until, reason);
    }
    
    /**
     * Remove temporary restrictions
     *
     * P0-017 FIX: Added SERIALIZABLE isolation to prevent race conditions.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30)
    public void removeTemporaryRestriction(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        // Restore original limits if they exist
        String originalDaily = (String) wallet.getMetadata().get("originalDailyLimit");
        String originalMonthly = (String) wallet.getMetadata().get("originalMonthlyLimit");
        
        if (originalDaily != null) {
            wallet.setDailyLimit(new BigDecimal(originalDaily));
            wallet.getMetadata().remove("originalDailyLimit");
        }
        
        if (originalMonthly != null) {
            wallet.setMonthlyLimit(new BigDecimal(originalMonthly));
            wallet.getMetadata().remove("originalMonthlyLimit");
        }
        
        // Clean up restriction metadata
        wallet.getMetadata().remove("restrictionUntil");
        wallet.getMetadata().remove("restrictionReason");
        wallet.setUpdatedAt(LocalDateTime.now());
        
        walletRepository.save(wallet);
        
        log.info("Removed temporary restriction from wallet {}", walletId);
    }
}