package com.waqiti.account.client;

import com.waqiti.account.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign client for communication with Core Banking Ledger Service
 *
 * <p><b>Fault Tolerance:</b></p>
 * <ul>
 *   <li>Circuit Breaker: Financial profile (30% failure threshold, 5s timeout)</li>
 *   <li>Fallback: Safe fallback with balance cache and delayed operations</li>
 *   <li>Retry: Automatic retry with exponential backoff</li>
 * </ul>
 *
 * <p><b>⚠️ CRITICAL:</b> All monetary operations have fallback to prevent data loss.
 * Failed transactions are queued for retry via DLQ mechanism.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@FeignClient(
    name = "core-banking-service",
    path = "/api/ledger",
    fallback = LedgerServiceClientFallback.class,
    configuration = FeignConfiguration.class
)
public interface LedgerServiceClient {
    
    @GetMapping("/accounts/{accountId}/balance")
    BalanceInquiryResponse getAccountBalance(@PathVariable("accountId") UUID accountId);
    
    @PostMapping("/accounts/{accountId}/reserve")
    ReserveFundsResult reserveFunds(
        @PathVariable("accountId") UUID accountId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("reservationId") String reservationId,
        @RequestParam("reason") String reason
    );
    
    @PostMapping("/accounts/{accountId}/release-reservation")
    ReleaseReservedFundsResult releaseReservedFunds(
        @PathVariable("accountId") UUID accountId,
        @RequestParam("reservationId") String reservationId,
        @RequestParam("amount") BigDecimal amount
    );
    
    @PostMapping("/accounts/{accountId}/debit")
    TransactionResult debitAccount(
        @PathVariable("accountId") UUID accountId,
        @RequestBody DebitRequest request
    );
    
    @PostMapping("/accounts/{accountId}/credit")
    TransactionResult creditAccount(
        @PathVariable("accountId") UUID accountId,
        @RequestBody CreditRequest request
    );
}