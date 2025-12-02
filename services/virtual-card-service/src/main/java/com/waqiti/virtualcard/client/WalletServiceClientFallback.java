package com.waqiti.virtualcard.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fallback implementation for WalletServiceClient
 *
 * Provides graceful degradation when wallet service is unavailable
 */
@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {

    @Override
    public BigDecimal getBalance(String userId, String currency) {
        log.error("Wallet service unavailable - falling back for getBalance. userId={}, currency={}",
            userId, currency);
        // Return zero balance to prevent operations (fail-safe)
        return BigDecimal.ZERO;
    }

    @Override
    public WalletTransactionResponse debit(String userId, WalletDebitRequest request) {
        log.error("Wallet service unavailable - falling back for debit. userId={}, amount={}",
            userId, request.getAmount());

        return WalletTransactionResponse.builder()
            .success(false)
            .errorCode("SERVICE_UNAVAILABLE")
            .message("Wallet service is temporarily unavailable. Please try again later.")
            .build();
    }

    @Override
    public WalletTransactionResponse credit(String userId, WalletCreditRequest request) {
        log.error("Wallet service unavailable - falling back for credit. userId={}, amount={}",
            userId, request.getAmount());

        return WalletTransactionResponse.builder()
            .success(false)
            .errorCode("SERVICE_UNAVAILABLE")
            .message("Wallet service is temporarily unavailable. Please try again later.")
            .build();
    }

    @Override
    public boolean hasSufficientBalance(String userId, BigDecimal amount, String currency) {
        log.error("Wallet service unavailable - falling back for hasSufficientBalance. userId={}, amount={}",
            userId, amount);
        // Return false to prevent operations (fail-safe)
        return false;
    }

    @Override
    public WalletDetails getWalletDetails(String userId) {
        log.error("Wallet service unavailable - falling back for getWalletDetails. userId={}", userId);

        return WalletDetails.builder()
            .userId(userId)
            .balance(BigDecimal.ZERO)
            .status("UNAVAILABLE")
            .build();
    }

    @Override
    public FundReservationResponse reserveFunds(String userId, FundReservationRequest request) {
        log.error("Wallet service unavailable - falling back for reserveFunds. userId={}, amount={}",
            userId, request.getAmount());

        return FundReservationResponse.builder()
            .success(false)
            .errorCode("SERVICE_UNAVAILABLE")
            .build();
    }

    @Override
    public void releaseReservedFunds(String userId, String reservationId) {
        log.error("Wallet service unavailable - falling back for releaseReservedFunds. userId={}, reservationId={}",
            userId, reservationId);
        // Log but don't throw exception - best effort cleanup
    }
}
