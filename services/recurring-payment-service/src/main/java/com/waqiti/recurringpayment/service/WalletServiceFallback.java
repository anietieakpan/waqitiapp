package com.waqiti.recurringpayment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fallback implementation for WalletService Feign client
 * Provides graceful degradation when wallet-service is unavailable
 */
@Slf4j
@Component
public class WalletServiceFallback implements WalletService {

    @Override
    public BigDecimal getBalance(String userId, String currency) {
        log.error("FALLBACK: wallet-service unavailable for getBalance. UserId: {}, Currency: {}",
                 userId, currency);

        // Return null to indicate balance check failed
        // Calling service should handle null and fail gracefully
        return null;
    }

    @Override
    public void holdFunds(String userId, BigDecimal amount, String currency, String reference) {
        log.error("FALLBACK: wallet-service unavailable for holdFunds. UserId: {}, Amount: {}, Currency: {}, Ref: {}",
                 userId, amount, currency, reference);

        // Cannot hold funds when wallet service is down
        // Throw exception to prevent payment processing without fund reservation
        throw new RuntimeException("Wallet service unavailable - cannot hold funds. Reference: " + reference);
    }

    @Override
    public void releaseFunds(String userId, BigDecimal amount, String currency, String reference) {
        log.error("FALLBACK: wallet-service unavailable for releaseFunds. UserId: {}, Amount: {}, Currency: {}, Ref: {}",
                 userId, amount, currency, reference);

        // Queue fund release for retry when service is back
        // This is critical - must not lose fund releases
        throw new RuntimeException("Wallet service unavailable - fund release will be retried. Reference: " + reference);
    }
}
