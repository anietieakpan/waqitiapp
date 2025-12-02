package com.waqiti.atm.client;

import com.waqiti.atm.dto.TransactionSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Feign Client for Account Service
 * Provides account balance, transaction, and account management operations
 */
@FeignClient(name = "account-service", path = "/api/v1/accounts")
public interface AccountServiceClient {

    @GetMapping("/{accountId}/active")
    boolean isAccountActive(@PathVariable("accountId") UUID accountId);

    @GetMapping("/{accountId}/balance/available")
    BigDecimal getAvailableBalance(@PathVariable("accountId") UUID accountId);

    @GetMapping("/{accountId}/balance/current")
    BigDecimal getCurrentBalance(@PathVariable("accountId") UUID accountId);

    @PostMapping("/{accountId}/debit")
    void debitAccount(@PathVariable("accountId") UUID accountId,
                     @RequestParam("amount") BigDecimal amount,
                     @RequestParam("description") String description);

    @PostMapping("/{accountId}/credit")
    void creditAccount(@PathVariable("accountId") UUID accountId,
                      @RequestParam("amount") BigDecimal amount,
                      @RequestParam("description") String description);

    @PostMapping("/{accountId}/debit/reverse")
    void reverseDebit(@PathVariable("accountId") UUID accountId,
                     @RequestParam("amount") BigDecimal amount);

    @GetMapping("/{accountId}/transactions/recent")
    List<TransactionSummary> getRecentTransactions(@PathVariable("accountId") UUID accountId,
                                                   @RequestParam("limit") int limit);

    @GetMapping("/{accountId}/new-account")
    boolean isNewAccount(@PathVariable("accountId") UUID accountId,
                        @RequestParam("days") int days);
}
