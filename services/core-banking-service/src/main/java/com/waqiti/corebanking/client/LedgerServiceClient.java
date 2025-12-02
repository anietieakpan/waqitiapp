package com.waqiti.corebanking.client;

import com.waqiti.corebanking.dto.LedgerEntryDto;
import com.waqiti.corebanking.dto.AccountBalanceDto;
import com.waqiti.corebanking.dto.JournalEntryDto;
import com.waqiti.corebanking.dto.TransactionLedgerDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Feign client for Ledger Service integration
 * Handles all communication with the ledger microservice
 * CRITICAL: Implements circuit breaker and retry for financial resilience
 */
@FeignClient(
    name = "ledger-service",
    url = "${services.ledger.url:http://ledger-service:8080}",
    configuration = FeignClientConfiguration.class,
    fallback = LedgerServiceClientFallback.class
)
public interface LedgerServiceClient {

    @PostMapping("/api/v1/ledger/entries")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "createLedgerEntryFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<LedgerEntryDto> createLedgerEntry(@RequestBody LedgerEntryDto entry);

    @PostMapping("/api/v1/ledger/journal")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "createJournalEntryFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<JournalEntryDto> createJournalEntry(@RequestBody JournalEntryDto journalEntry);

    @GetMapping("/api/v1/ledger/balance/{accountId}")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getAccountBalanceFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<AccountBalanceDto> getAccountBalance(@PathVariable String accountId);

    @GetMapping("/api/v1/ledger/entries/{accountId}")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getAccountEntriesFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<List<LedgerEntryDto>> getAccountEntries(
        @PathVariable String accountId,
        @RequestParam(required = false) Integer limit
    );

    @PostMapping("/api/v1/ledger/reconcile/{accountId}")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "reconcileAccountFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<Boolean> reconcileAccount(@PathVariable String accountId);

    @GetMapping("/api/v1/ledger/transactions/{transactionId}")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getTransactionLedgerFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<TransactionLedgerDto> getTransactionLedger(@PathVariable UUID transactionId);

    @PostMapping("/api/v1/ledger/reverse/{transactionId}")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "reverseTransactionFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<JournalEntryDto> reverseTransaction(
        @PathVariable UUID transactionId,
        @RequestParam String reason
    );

    @GetMapping("/api/v1/ledger/trial-balance")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getTrialBalanceFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<TrialBalanceDto> getTrialBalance(
        @RequestParam(required = false) String date
    );

    @GetMapping("/api/v1/ledger/accounts/{accountNumber}/statement")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getAccountStatementFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<AccountStatementDto> getAccountStatement(
        @PathVariable String accountNumber,
        @RequestParam String fromDate,
        @RequestParam String toDate
    );

    @PostMapping("/api/v1/ledger/validate")
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "validateLedgerEntryFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    ResponseEntity<ValidationResultDto> validateLedgerEntry(@RequestBody LedgerEntryDto entry);

    /**
     * DTO classes for Ledger Service responses
     */
    
    record TrialBalanceDto(
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal difference,
        boolean balanced,
        String balanceDate,
        List<AccountBalance> accountBalances
    ) {}

    record AccountStatementDto(
        String accountNumber,
        String accountName,
        String fromDate,
        String toDate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        List<StatementEntry> entries
    ) {}

    record StatementEntry(
        String date,
        String description,
        String reference,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance
    ) {}

    record ValidationResultDto(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {}

    record AccountBalance(
        String accountNumber,
        String accountName,
        BigDecimal debitBalance,
        BigDecimal creditBalance
    ) {}
}