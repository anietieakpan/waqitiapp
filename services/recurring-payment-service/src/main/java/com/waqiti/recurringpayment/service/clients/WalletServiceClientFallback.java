package com.waqiti.recurringpayment.service.clients;

import com.waqiti.recurringpayment.service.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fallback implementation for Wallet Service Client
 * 
 * CRITICAL: Wallet operations involve customer funds and balances.
 * Fallback must prevent overdrafts, double-spending, and fund locks.
 * Conservative approach: BLOCK all fund movements when wallet service is down.
 * 
 * Failure Strategy:
 * - BLOCK all fund holds and reservations
 * - RETURN null/zero balances to prevent overdrafts
 * - PRESERVE existing fund holds (don't release)
 * - LOG all failed operations for reconciliation
 * - PREVENT any transaction that could affect balances
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletServiceClientFallback implements WalletServiceClient {
    
    /**
     * CRITICAL: Get wallet balance
     * Strategy: Return null to indicate unavailable, prevent transactions
     */
    @Override
    public BigDecimal getBalance(String userId, String currency) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve wallet balance. " +
                 "UserId: {}, Currency: {}", userId, currency);
        
        // Return null to indicate balance unavailable
        // This should prevent any transaction from proceeding
        return null;
    }
    
    /**
     * Get all balances - return unavailable response
     */
    @Override
    public BalancesResponse getAllBalances(String userId) {
        log.error("FALLBACK: Cannot retrieve wallet balances. UserId: {}", userId);
        
        return BalancesResponse.builder()
                .userId(userId)
                .balances(null) // Null indicates unavailable
                .status("UNAVAILABLE")
                .message("Wallet service temporarily unavailable")
                .fallbackActivated(true)
                .build();
    }
    
    /**
     * CRITICAL: Hold funds for recurring payment
     * Strategy: BLOCK all fund holds to prevent overdrafts
     */
    @Override
    public HoldResult holdFunds(HoldRequest request) {
        log.error("FALLBACK: Cannot hold funds. Blocking transaction. " +
                 "UserId: {}, Amount: {}, Currency: {}",
                 request.getUserId(), request.getAmount(), request.getCurrency());
        
        return HoldResult.builder()
                .holdId(null)
                .success(false)
                .status("HOLD_BLOCKED")
                .message("Wallet service unavailable. Fund hold blocked for safety.")
                .requiresRetry(true)
                .fallbackActivated(true)
                .build();
    }
    
    /**
     * CRITICAL: Release held funds
     * Strategy: DO NOT release funds during outage to prevent double-spending
     */
    @Override
    public void releaseFunds(String holdId) {
        log.error("FALLBACK: Cannot release funds during wallet service outage. " +
                 "HoldId: {} - Preserving hold for safety", holdId);
        
        // Do not release funds during outage
        // This prevents potential double-spending if hold records are inconsistent
        // Funds will be released when service recovers and reconciliation runs
    }
    
    /**
     * CRITICAL: Reserve funds for future payment
     * Strategy: BLOCK all reservations during outage
     */
    @Override
    public ReservationResult reserveFunds(ReservationRequest request) {
        log.error("FALLBACK: Cannot reserve funds. Blocking reservation. " +
                 "UserId: {}, Amount: {}, ScheduledDate: {}",
                 request.getUserId(), request.getAmount(), request.getScheduledDate());
        
        return ReservationResult.builder()
                .reservationId(null)
                .success(false)
                .status("RESERVATION_BLOCKED")
                .message("Wallet service unavailable. Fund reservation blocked.")
                .requiresRetry(true)
                .fallbackActivated(true)
                .build();
    }
    
    /**
     * Get wallet limits - return conservative limits
     */
    @Override
    public WalletLimits getWalletLimits(String userId) {
        log.warn("FALLBACK: Cannot retrieve wallet limits. UserId: {}", userId);
        
        // Return zero limits to prevent any transactions
        return WalletLimits.builder()
                .userId(userId)
                .dailyLimit(BigDecimal.ZERO)
                .monthlyLimit(BigDecimal.ZERO)
                .transactionLimit(BigDecimal.ZERO)
                .availableDaily(BigDecimal.ZERO)
                .availableMonthly(BigDecimal.ZERO)
                .status("LIMITS_UNAVAILABLE")
                .message("Wallet limits unavailable. Transactions blocked for safety.")
                .fallbackActivated(true)
                .build();
    }
}