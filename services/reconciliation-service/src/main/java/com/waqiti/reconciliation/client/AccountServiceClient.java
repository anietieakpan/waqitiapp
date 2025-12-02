package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "account-service",
    url = "${services.account-service.url:http://account-service:8080}",
    fallbackFactory = AccountServiceClientFallbackFactory.class
)
public interface AccountServiceClient {

    /**
     * Get account balance as of specific date
     */
    @GetMapping("/api/v1/accounts/{accountId}/balance")
    AccountBalance getAccountBalance(
        @PathVariable UUID accountId,
        @RequestParam("asOfDate") LocalDateTime asOfDate
    );

    /**
     * Get all active customer accounts
     */
    @GetMapping("/api/v1/accounts/customer/active")
    List<CustomerAccount> getAllActiveCustomerAccounts();

    /**
     * Get all system accounts
     */
    @GetMapping("/api/v1/accounts/system")
    List<SystemAccount> getAllSystemAccounts();

    /**
     * Get account details
     */
    @GetMapping("/api/v1/accounts/{accountId}")
    AccountDetails getAccountDetails(@PathVariable UUID accountId);

    /**
     * Get account transaction history
     */
    @GetMapping("/api/v1/accounts/{accountId}/transactions")
    List<AccountTransaction> getAccountTransactionHistory(
        @PathVariable UUID accountId,
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Get account balance history
     */
    @GetMapping("/api/v1/accounts/{accountId}/balance-history")
    List<BalanceHistoryEntry> getAccountBalanceHistory(
        @PathVariable UUID accountId,
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Validate account status
     */
    @GetMapping("/api/v1/accounts/{accountId}/status")
    AccountStatusResponse getAccountStatus(@PathVariable UUID accountId);

    /**
     * Get accounts by type
     */
    @GetMapping("/api/v1/accounts/by-type/{accountType}")
    List<AccountSummary> getAccountsByType(@PathVariable String accountType);

    /**
     * Get dormant accounts
     */
    @GetMapping("/api/v1/accounts/dormant")
    List<DormantAccount> getDormantAccounts(@RequestParam("inactiveDays") int inactiveDays);

    /**
     * Check account compliance
     */
    @PostMapping("/api/v1/accounts/{accountId}/compliance-check")
    ComplianceCheckResult checkAccountCompliance(
        @PathVariable UUID accountId,
        @RequestBody ComplianceCheckRequest request
    );

    /**
     * Get account reconciliation data
     */
    @GetMapping("/api/v1/accounts/{accountId}/reconciliation-data")
    AccountReconciliationData getAccountReconciliationData(
        @PathVariable UUID accountId,
        @RequestParam("asOfDate") LocalDateTime asOfDate
    );

    /**
     * Freeze/unfreeze account
     */
    @PostMapping("/api/v1/accounts/{accountId}/freeze")
    AccountActionResult freezeAccount(
        @PathVariable UUID accountId,
        @RequestBody AccountFreezeRequest request
    );

    /**
     * Get account alerts
     */
    @GetMapping("/api/v1/accounts/{accountId}/alerts")
    List<AccountAlert> getAccountAlerts(@PathVariable UUID accountId);
}