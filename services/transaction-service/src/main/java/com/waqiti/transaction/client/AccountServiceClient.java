package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.AccountBalanceResponse;
import com.waqiti.transaction.dto.ReserveFundsRequest;
import com.waqiti.transaction.dto.ReserveFundsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@FeignClient(
    name = "account-service",
    path = "/api/accounts",
    fallback = AccountServiceClientFallback.class,
    configuration = AccountServiceClientConfiguration.class
)
public interface AccountServiceClient {

    @GetMapping("/{accountId}/balance")
    AccountBalanceResponse getAccountBalance(@PathVariable String accountId);

    @PostMapping("/{accountId}/reserve")
    ReserveFundsResponse reserveFunds(@PathVariable String accountId, @RequestBody ReserveFundsRequest request);

    @PostMapping("/{accountId}/release")
    void releaseFunds(@PathVariable String accountId, @RequestParam String reservationId);

    @PostMapping("/{accountId}/debit")
    void debitAccount(@PathVariable String accountId, @RequestParam BigDecimal amount, @RequestParam String transactionId);

    @PostMapping("/{accountId}/credit")
    void creditAccount(@PathVariable String accountId, @RequestParam BigDecimal amount, @RequestParam String transactionId);

    @GetMapping("/{accountId}/status")
    String getAccountStatus(@PathVariable String accountId);
}