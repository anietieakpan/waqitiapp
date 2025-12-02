package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.AccountBalanceResponse;
import com.waqiti.transaction.dto.ReserveFundsRequest;
import com.waqiti.transaction.dto.ReserveFundsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fallback implementation for Account Service Client
 * 
 * CRITICAL: Account operations are foundational to all transactions.
 * Any account service failure must prevent financial movements to maintain
 * data integrity and prevent overdrafts or double-spending.
 * 
 * Failure Strategy:
 * - BLOCK all debits and credits during outage
 * - RETURN null/unavailable for balance inquiries
 * - FAIL all fund reservations to prevent overdrafts
 * - PRESERVE existing reservations (no releases)
 * - LOG all attempts for reconciliation
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountServiceClientFallback implements AccountServiceClient {
    
    /**
     * CRITICAL: Get account balance
     * Strategy: Return null to indicate unavailable and block transactions
     */
    @Override
    public AccountBalanceResponse getAccountBalance(String accountId) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve account balance. " +
                 "AccountId: {} - Blocking all transactions", accountId);

        return AccountBalanceResponse.builder()
                .accountId(accountId)
                .currentBalance(null)
                .availableBalance(null)
                .status("UNAVAILABLE")
                .isActive(false)
                .canDebit(false)
                .canCredit(false)
                .build();
    }
    
    /**
     * CRITICAL: Reserve funds for transaction
     * Strategy: BLOCK all reservations to prevent overdrafts
     */
    @Override
    public ReserveFundsResponse reserveFunds(String accountId, ReserveFundsRequest request) {
        log.error("FALLBACK: Cannot reserve funds. Blocking reservation. " +
                 "AccountId: {}, Amount: {}, TransactionId: {}",
                 accountId, request.getAmount(), request.getTransactionId());

        return ReserveFundsResponse.builder()
                .reservationId(null)
                .transactionId(request.getTransactionId())
                .accountId(accountId)
                .status("FAILED")
                .reservationStatus(ReserveFundsResponse.ReservationStatus.FAILED)
                .message("Account service unavailable. Fund reservation blocked for safety.")
                .build();
    }
    
    /**
     * CRITICAL: Release reserved funds
     * Strategy: DO NOT release during outage to prevent double-spending
     */
    @Override
    public void releaseFunds(String accountId, String reservationId) {
        log.error("FALLBACK: Cannot release funds during account service outage. " +
                 "AccountId: {}, ReservationId: {} - Preserving reservation for safety",
                 accountId, reservationId);
        // Do not release funds - preserve reservation until service recovers
    }
    
    /**
     * CRITICAL: Debit account
     * Strategy: BLOCK all debits during outage
     */
    @Override
    public void debitAccount(String accountId, BigDecimal amount, String transactionId) {
        log.error("FALLBACK: CRITICAL - Blocking debit operation. " +
                 "AccountId: {}, Amount: {}, TransactionId: {}",
                 accountId, amount, transactionId);
        
        throw new RuntimeException(
            "Account service unavailable. Debit operation blocked to prevent overdraft. " +
            "TransactionId: " + transactionId);
    }
    
    /**
     * CRITICAL: Credit account
     * Strategy: BLOCK credits during outage to maintain consistency
     */
    @Override
    public void creditAccount(String accountId, BigDecimal amount, String transactionId) {
        log.error("FALLBACK: CRITICAL - Blocking credit operation. " +
                 "AccountId: {}, Amount: {}, TransactionId: {}",
                 accountId, amount, transactionId);
        
        throw new RuntimeException(
            "Account service unavailable. Credit operation blocked to maintain consistency. " +
            "TransactionId: " + transactionId);
    }
    
    /**
     * Get account status - return unavailable
     */
    @Override
    public String getAccountStatus(String accountId) {
        log.warn("FALLBACK: Cannot retrieve account status. AccountId: {}", accountId);
        return "STATUS_UNAVAILABLE";
    }
}