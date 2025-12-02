package com.waqiti.customer.client;

import com.waqiti.customer.client.dto.AccountResponse;
import com.waqiti.customer.client.dto.AccountStatusResponse;
import com.waqiti.customer.client.dto.BalanceResponse;
import com.waqiti.customer.client.fallback.AccountServiceClientFallback;
import com.waqiti.customer.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign client for inter-service communication with account-service.
 * Provides methods to retrieve account information, balances, and manage account status.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@FeignClient(
    name = "account-service",
    configuration = FeignClientConfig.class,
    fallback = AccountServiceClientFallback.class
)
public interface AccountServiceClient {

    /**
     * Retrieves account information by account ID.
     *
     * @param accountId The unique account identifier
     * @return Account details
     */
    @GetMapping("/api/v1/accounts/{accountId}")
    AccountResponse getAccount(@PathVariable("accountId") String accountId);

    /**
     * Retrieves account balance by account ID.
     *
     * @param accountId The unique account identifier
     * @return Balance details including available and held amounts
     */
    @GetMapping("/api/v1/accounts/{accountId}/balance")
    BalanceResponse getBalance(@PathVariable("accountId") String accountId);

    /**
     * Retrieves account status by account ID.
     *
     * @param accountId The unique account identifier
     * @return Account status information
     */
    @GetMapping("/api/v1/accounts/{accountId}/status")
    AccountStatusResponse getAccountStatus(@PathVariable("accountId") String accountId);

    /**
     * Retrieves all accounts associated with a customer.
     *
     * @param customerId The unique customer identifier
     * @return List of accounts owned by the customer
     */
    @GetMapping("/api/v1/accounts/customer/{customerId}")
    List<AccountResponse> getAccountsByCustomerId(@PathVariable("customerId") String customerId);

    /**
     * Freezes an account, preventing transactions.
     *
     * @param accountId The unique account identifier
     */
    @PostMapping("/api/v1/accounts/{accountId}/freeze")
    void freezeAccount(@PathVariable("accountId") String accountId);

    /**
     * Retrieves the account creation date.
     *
     * @param accountId The unique account identifier
     * @return Account creation timestamp
     */
    @GetMapping("/api/v1/accounts/{accountId}/creation-date")
    LocalDateTime getAccountCreationDate(@PathVariable("accountId") String accountId);
}
