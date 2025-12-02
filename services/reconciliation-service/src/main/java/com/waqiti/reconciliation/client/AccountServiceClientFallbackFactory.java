package com.waqiti.reconciliation.client;

import com.waqiti.common.feign.BaseFallbackFactory;
import com.waqiti.reconciliation.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback Factory for AccountServiceClient
 *
 * CRITICAL: Provides graceful degradation when account-service is unavailable
 *
 * FALLBACK STRATEGY:
 * - Circuit Breaker Open → Return empty lists/cached data
 * - Timeout → Return empty lists
 * - 4xx Client Error → Propagate exception
 * - 5xx Server Error → Return fallback responses
 *
 * CACHING STRATEGY:
 * - Account balances → Cache for 5 minutes
 * - Account details → Cache for 30 minutes
 * - Account lists → Cache for 10 minutes
 *
 * @author Waqiti Reconciliation Team
 * @version 3.0.0
 */
@Component
@Slf4j
public class AccountServiceClientFallbackFactory extends BaseFallbackFactory<AccountServiceClient> {

    private final com.waqiti.reconciliation.service.AccountCacheService cacheService;

    public AccountServiceClientFallbackFactory(
            com.waqiti.reconciliation.service.AccountCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    protected AccountServiceClient createFallback(Throwable cause) {
        return new AccountServiceClient() {

            @Override
            public AccountBalance getAccountBalance(UUID accountId, LocalDateTime asOfDate) {
                log.warn("AccountServiceClient.getAccountBalance fallback for accountId: {}", accountId);

                // If circuit breaker or timeout, return cached balance
                if (shouldUseCache(cause)) {
                    AccountBalance cached = cacheService.getCachedBalance(accountId, asOfDate);
                    if (cached != null) {
                        return cached;
                    }
                    return createZeroBalance(accountId);
                }

                // Propagate client errors (400, 401, 403)
                if (shouldPropagateException(cause)) {
                    throw rethrowWithMessage(cause, "Failed to get account balance");
                }

                // Return zero balance for server errors
                return createZeroBalance(accountId);
            }

            @Override
            public List<CustomerAccount> getAllActiveCustomerAccounts() {
                log.warn("AccountServiceClient.getAllActiveCustomerAccounts fallback");

                // Return cached list if available
                if (shouldUseCache(cause)) {
                    List<CustomerAccount> cached = cacheService.getCachedCustomerAccounts();
                    if (!cached.isEmpty()) {
                        log.info("Returning {} cached customer accounts", cached.size());
                        return cached;
                    }
                }

                return Collections.emptyList();
            }

            @Override
            public List<SystemAccount> getAllSystemAccounts() {
                log.warn("AccountServiceClient.getAllSystemAccounts fallback");

                // Return cached list if available
                if (shouldUseCache(cause)) {
                    List<SystemAccount> cached = cacheService.getCachedSystemAccounts();
                    if (!cached.isEmpty()) {
                        log.info("Returning {} cached system accounts", cached.size());
                        return cached;
                    }
                }

                return Collections.emptyList();
            }

            @Override
            public AccountDetails getAccountDetails(UUID accountId) {
                log.warn("AccountServiceClient.getAccountDetails fallback for accountId: {}", accountId);

                if (shouldPropagateException(cause)) {
                    throw rethrowWithMessage(cause, "Failed to get account details");
                }

                // Try cached details first
                if (shouldUseCache(cause)) {
                    AccountDetails cached = cacheService.getCachedAccountDetails(accountId);
                    if (cached != null) {
                        return cached;
                    }
                }

                // Return minimal account details
                return createMinimalAccountDetails(accountId);
            }

            @Override
            public List<AccountTransaction> getAccountTransactionHistory(
                    UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
                log.warn("AccountServiceClient.getAccountTransactionHistory fallback for accountId: {}", accountId);

                // Return empty transaction history
                // Reconciliation will be marked as incomplete
                return Collections.emptyList();
            }

            @Override
            public List<BalanceHistoryEntry> getAccountBalanceHistory(
                    UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
                log.warn("AccountServiceClient.getAccountBalanceHistory fallback for accountId: {}", accountId);

                // Return empty balance history
                return Collections.emptyList();
            }

            @Override
            public AccountStatusResponse getAccountStatus(UUID accountId) {
                log.warn("AccountServiceClient.getAccountStatus fallback for accountId: {}", accountId);

                if (shouldPropagateException(cause)) {
                    throw rethrowWithMessage(cause, "Failed to get account status");
                }

                // Return unknown status
                return new AccountStatusResponse(accountId, "UNKNOWN", "Service unavailable");
            }

            @Override
            public List<AccountSummary> getAccountsByType(String accountType) {
                log.warn("AccountServiceClient.getAccountsByType fallback for type: {}", accountType);

                return Collections.emptyList();
            }

            @Override
            public List<DormantAccount> getDormantAccounts(int inactiveDays) {
                log.warn("AccountServiceClient.getDormantAccounts fallback for inactiveDays: {}", inactiveDays);

                return Collections.emptyList();
            }

            @Override
            public ComplianceCheckResult checkAccountCompliance(UUID accountId, ComplianceCheckRequest request) {
                log.warn("AccountServiceClient.checkAccountCompliance fallback for accountId: {}", accountId);

                // Return inconclusive compliance check
                return new ComplianceCheckResult(accountId, "INCONCLUSIVE", "Compliance service unavailable");
            }

            @Override
            public AccountReconciliationData getAccountReconciliationData(UUID accountId, LocalDateTime asOfDate) {
                log.warn("AccountServiceClient.getAccountReconciliationData fallback for accountId: {}", accountId);

                // Return empty reconciliation data - reconciliation will fail gracefully
                return createEmptyReconciliationData(accountId, asOfDate);
            }

            @Override
            public AccountActionResult freezeAccount(UUID accountId, AccountFreezeRequest request) {
                log.error("AccountServiceClient.freezeAccount fallback for accountId: {} - CRITICAL OPERATION FAILED", accountId);

                // CRITICAL: Do NOT allow freeze operation in fallback
                // Throw exception to force manual intervention
                throw rethrowWithMessage(cause, "Account freeze operation unavailable - manual intervention required");
            }

            @Override
            public List<AccountAlert> getAccountAlerts(UUID accountId) {
                log.warn("AccountServiceClient.getAccountAlerts fallback for accountId: {}", accountId);

                return Collections.emptyList();
            }
        };
    }

    /**
     * Create zero balance for fallback
     */
    private AccountBalance createZeroBalance(UUID accountId) {
        return AccountBalance.builder()
            .accountId(accountId)
            .balance(java.math.BigDecimal.ZERO)
            .currency("USD")
            .fallback(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Create minimal account details for fallback
     */
    private AccountDetails createMinimalAccountDetails(UUID accountId) {
        return AccountDetails.builder()
            .accountId(accountId)
            .status("UNKNOWN")
            .fallback(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Create empty reconciliation data for fallback
     */
    private AccountReconciliationData createEmptyReconciliationData(UUID accountId, LocalDateTime asOfDate) {
        return AccountReconciliationData.builder()
            .accountId(accountId)
            .asOfDate(asOfDate)
            .balance(java.math.BigDecimal.ZERO)
            .transactionCount(0)
            .fallback(true)
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Override
    protected String getClientName() {
        return "AccountServiceClient";
    }
}
