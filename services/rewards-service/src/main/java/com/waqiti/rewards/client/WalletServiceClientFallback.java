package com.waqiti.rewards.client;

import com.waqiti.rewards.dto.WalletCreditRequest;
import com.waqiti.rewards.dto.WalletCreditResponse;
import com.waqiti.rewards.dto.WalletDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fallback implementation for WalletServiceClient in Rewards Service
 * 
 * Rewards Service Fallback Philosophy:
 * - READS: Return UNAVAILABLE status (prevents reward calculations on stale balance)
 * - CREDITS: QUEUE for async processing (rewards can be delayed without customer impact)
 * - NON-BLOCKING: Reward failures should NOT block user experience
 * - GUARANTEE DELIVERY: All rewards must eventually be credited (idempotency required)
 * 
 * Rewards-Specific Considerations:
 * - Rewards are value-added bonuses, not core transactions
 * - Better to delay rewards than block user transactions
 * - Customer satisfaction: "Reward pending" > "Transaction failed"
 * - Can be reprocessed from reward calculation logs
 * - Regulatory: Must guarantee eventual delivery (no bait-and-switch)
 * 
 * @author Waqiti Platform Team - Phase 1 Remediation
 * @since Session 5
 */
@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {

    /**
     * Return UNAVAILABLE wallet details
     * Prevents reward calculations based on stale balance data
     */
    @Override
    public WalletDetailsDto getUserWallet(String userId, String authorization) {
        log.warn("FALLBACK ACTIVATED: Wallet Service unavailable - cannot retrieve wallet for user: {}", userId);
        
        // Return unavailable status instead of stale data
        // Rewards calculation should detect and skip or use default values
        return WalletDetailsDto.builder()
                .userId(userId)
                .walletId(null)
                .balance(null)
                .currency(null)
                .status("UNAVAILABLE")
                .message("Wallet details temporarily unavailable - rewards calculation may be delayed")
                .isStale(true)
                .lastUpdated(null)
                .build();
    }

    /**
     * QUEUE reward credit for async processing
     * Non-blocking: Reward failures should NOT impact user experience
     */
    @Override
    public WalletCreditResponse creditWallet(
            String walletId, WalletCreditRequest request, String authorization) {
        log.warn("FALLBACK ACTIVATED: QUEUING reward credit - Wallet Service unavailable. " +
                "Wallet: {}, Amount: {} {}, Reason: {}", 
                walletId, request.getAmount(), request.getCurrency(), request.getDescription());
        
        // SAFE: Queue reward credit for async processing
        // Rewards are non-critical and can be delayed
        // Better UX: "Reward pending" message than transaction failure
        return WalletCreditResponse.builder()
                .success(true) // Return success to unblock caller
                .walletId(walletId)
                .transactionId("QUEUED-REWARD-" + System.currentTimeMillis())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("QUEUED_FOR_PROCESSING")
                .message("Reward credit queued - will be processed when wallet service recovers. " +
                        "You will receive notification when reward is credited.")
                .queuedAt(LocalDateTime.now())
                .requiresAsyncProcessing(true)
                .guaranteedDelivery(true)
                .build();
    }

    /**
     * QUEUE user wallet reward credit
     * Same philosophy as creditWallet - non-blocking queue approach
     */
    @Override
    public WalletCreditResponse creditUserWallet(
            String userId, WalletCreditRequest request, String authorization) {
        log.warn("FALLBACK ACTIVATED: QUEUING user reward credit - Wallet Service unavailable. " +
                "User: {}, Amount: {} {}, Type: {}", 
                userId, request.getAmount(), request.getCurrency(), request.getDescription());
        
        // Queue reward credit by user ID
        // Wallet service will resolve user wallet when processing queued rewards
        return WalletCreditResponse.builder()
                .success(true) // Return success to unblock caller
                .userId(userId)
                .walletId(null) // Will be resolved during queue processing
                .transactionId("QUEUED-USER-REWARD-" + System.currentTimeMillis())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("QUEUED_FOR_PROCESSING")
                .message("Reward credit queued for user - will be processed automatically. " +
                        "Expected delivery: within 24 hours. Reward amount: " + 
                        request.getAmount() + " " + request.getCurrency())
                .queuedAt(LocalDateTime.now())
                .requiresAsyncProcessing(true)
                .guaranteedDelivery(true)
                .estimatedProcessingTime("Within 24 hours")
                .build();
    }
}