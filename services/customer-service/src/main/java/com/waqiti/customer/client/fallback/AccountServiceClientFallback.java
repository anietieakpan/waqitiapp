package com.waqiti.customer.client.fallback;

import com.waqiti.customer.client.AccountServiceClient;
import com.waqiti.customer.client.dto.AccountResponse;
import com.waqiti.customer.client.dto.AccountStatusResponse;
import com.waqiti.customer.client.dto.BalanceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Fallback implementation for AccountServiceClient.
 * Provides circuit breaker pattern implementation with safe default values
 * when account-service is unavailable or experiencing issues.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Component
@Slf4j
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override
    public AccountResponse getAccount(String accountId) {
        log.error("AccountServiceClient.getAccount fallback triggered for accountId: {}", accountId);
        return null;
    }

    @Override
    public BalanceResponse getBalance(String accountId) {
        log.error("AccountServiceClient.getBalance fallback triggered for accountId: {}", accountId);
        return null;
    }

    @Override
    public AccountStatusResponse getAccountStatus(String accountId) {
        log.error("AccountServiceClient.getAccountStatus fallback triggered for accountId: {}", accountId);
        return null;
    }

    @Override
    public List<AccountResponse> getAccountsByCustomerId(String customerId) {
        log.error("AccountServiceClient.getAccountsByCustomerId fallback triggered for customerId: {}", customerId);
        return Collections.emptyList();
    }

    @Override
    public void freezeAccount(String accountId) {
        log.error("AccountServiceClient.freezeAccount fallback triggered for accountId: {}. Account freeze operation failed.", accountId);
    }

    @Override
    public LocalDateTime getAccountCreationDate(String accountId) {
        log.error("AccountServiceClient.getAccountCreationDate fallback triggered for accountId: {}", accountId);
        return null;
    }
}
