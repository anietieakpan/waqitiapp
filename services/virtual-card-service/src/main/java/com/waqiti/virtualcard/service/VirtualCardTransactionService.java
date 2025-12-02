package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.domain.VirtualCard;
import com.waqiti.virtualcard.repository.VirtualCardRepository;
import com.waqiti.common.service.OptimisticLockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Virtual Card Transaction Service with Optimistic Locking
 * 
 * Demonstrates proper usage of optimistic locking for financial operations
 * that update card spending totals and usage counts concurrently.
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualCardTransactionService {
    
    private final VirtualCardRepository virtualCardRepository;
    private final OptimisticLockingService optimisticLockingService;
    
    /**
     * Process card transaction with optimistic locking
     * 
     * @param cardId Card ID
     * @param transactionAmount Transaction amount
     * @return Updated card
     * @throws OptimisticLockingFailureException if concurrent modification detected
     */
    @Transactional
    public VirtualCard processCardTransaction(String cardId, BigDecimal transactionAmount) {
        return optimisticLockingService.executeWithOptimisticLocking(() -> {
            
            // Fetch card with current version
            VirtualCard card = virtualCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
            
            // Verify card can process transaction
            if (!card.canProcessTransaction()) {
                throw new IllegalStateException("Card cannot process transactions: " + cardId);
            }
            
            // Update financial totals (critical section)
            BigDecimal newTotalSpent = card.getTotalSpent().add(transactionAmount);
            Long newUsageCount = card.getUsageCount() + 1;
            
            card.setTotalSpent(newTotalSpent);
            card.setUsageCount(newUsageCount);
            card.setLastUsedAt(LocalDateTime.now());
            
            log.info("CARD_TRANSACTION: Processing transaction - cardId: {}, amount: {}, newTotal: {}, usageCount: {}", 
                cardId, transactionAmount, newTotalSpent, newUsageCount);
            
            // Save with version check
            return virtualCardRepository.save(card);
            
        }, "VirtualCard[" + cardId + "]");
    }
    
    /**
     * Process card refund with optimistic locking
     * 
     * @param cardId Card ID
     * @param refundAmount Refund amount
     * @return Updated card
     */
    @Transactional
    public VirtualCard processCardRefund(String cardId, BigDecimal refundAmount) {
        return optimisticLockingService.executeCriticalFinancialOperation(() -> {
            
            VirtualCard card = virtualCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
            
            // Validate refund amount
            if (refundAmount.compareTo(card.getTotalSpent()) > 0) {
                throw new IllegalArgumentException("Refund amount exceeds total spent");
            }
            
            // Update financial totals
            BigDecimal newTotalSpent = card.getTotalSpent().subtract(refundAmount);
            card.setTotalSpent(newTotalSpent);
            
            log.info("CARD_REFUND: Processing refund - cardId: {}, refundAmount: {}, newTotal: {}", 
                cardId, refundAmount, newTotalSpent);
            
            return virtualCardRepository.save(card);
            
        }, "VirtualCard[" + cardId + "]");
    }
    
    /**
     * Lock card with optimistic locking
     * 
     * @param cardId Card ID
     * @param lockReason Reason for locking
     * @return Updated card
     */
    @Transactional
    public VirtualCard lockCard(String cardId, String lockReason) {
        return optimisticLockingService.executeWithOptimisticLocking(() -> {
            
            VirtualCard card = virtualCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
            
            if (card.isLocked()) {
                throw new IllegalStateException("Card is already locked: " + cardId);
            }
            
            // Update lock status
            card.setLocked(true);
            card.setLockReason(lockReason);
            card.setLockedAt(LocalDateTime.now());
            
            log.warn("CARD_LOCK: Locking card - cardId: {}, reason: {}", cardId, lockReason);
            
            return virtualCardRepository.save(card);
            
        }, "VirtualCard[" + cardId + "]");
    }
    
    /**
     * Unlock card with optimistic locking
     * 
     * @param cardId Card ID
     * @return Updated card
     */
    @Transactional
    public VirtualCard unlockCard(String cardId) {
        return optimisticLockingService.executeWithOptimisticLocking(() -> {
            
            VirtualCard card = virtualCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
            
            if (!card.isLocked()) {
                throw new IllegalStateException("Card is not locked: " + cardId);
            }
            
            // Update lock status
            card.setLocked(false);
            card.setLockReason(null);
            card.setLockedAt(null);
            
            log.info("CARD_UNLOCK: Unlocking card - cardId: {}", cardId);
            
            return virtualCardRepository.save(card);
            
        }, "VirtualCard[" + cardId + "]");
    }
    
    /**
     * Reset card spending with optimistic locking
     * Used for monthly/daily limit resets
     * 
     * @param cardId Card ID
     * @return Updated card
     */
    @Transactional
    public VirtualCard resetCardSpending(String cardId) {
        return optimisticLockingService.executeWithOptimisticLocking(() -> {
            
            VirtualCard card = virtualCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
            
            // Reset spending totals
            card.setTotalSpent(BigDecimal.ZERO);
            card.setUsageCount(0L);
            
            log.info("CARD_RESET: Resetting card spending - cardId: {}", cardId);
            
            return virtualCardRepository.save(card);
            
        }, "VirtualCard[" + cardId + "]");
    }
}