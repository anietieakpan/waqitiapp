package com.waqiti.customer.client.fallback;

import com.waqiti.customer.client.LedgerServiceClient;
import com.waqiti.customer.client.dto.PendingTransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Fallback implementation for LedgerServiceClient.
 * Provides circuit breaker pattern implementation with safe default values
 * when ledger-service is unavailable or experiencing issues.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Component
@Slf4j
public class LedgerServiceClientFallback implements LedgerServiceClient {

    @Override
    public List<PendingTransactionResponse> getPendingTransactions(String accountId) {
        log.error("LedgerServiceClient.getPendingTransactions fallback triggered for accountId: {}", accountId);
        return Collections.emptyList();
    }

    @Override
    public BigDecimal getPendingDebits(String accountId) {
        log.error("LedgerServiceClient.getPendingDebits fallback triggered for accountId: {}", accountId);
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getPendingCredits(String accountId) {
        log.error("LedgerServiceClient.getPendingCredits fallback triggered for accountId: {}", accountId);
        return BigDecimal.ZERO;
    }

    @Override
    public void freezeLedger(String accountId) {
        log.error("LedgerServiceClient.freezeLedger fallback triggered for accountId: {}. Ledger freeze operation failed.", accountId);
    }
}
