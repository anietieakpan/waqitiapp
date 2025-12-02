package com.waqiti.ledger.client;

import com.waqiti.ledger.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ledger Service Client
 * 
 * Feign client for inter-service communication with the ledger service
 */
@FeignClient(name = "ledger-service", path = "/api/v1")
public interface LedgerServiceClient {
    
    // Account operations
    @GetMapping("/accounts/{accountId}")
    ResponseEntity<LedgerAccountResponse> getAccount(@PathVariable UUID accountId);
    
    @PostMapping("/accounts")
    ResponseEntity<LedgerAccountResponse> createAccount(@RequestBody CreateAccountRequest request);
    
    @PutMapping("/accounts/{accountId}")
    ResponseEntity<LedgerAccountResponse> updateAccount(
        @PathVariable UUID accountId,
        @RequestBody UpdateAccountRequest request
    );
    
    @DeleteMapping("/accounts/{accountId}")
    ResponseEntity<Void> deleteAccount(@PathVariable UUID accountId);
    
    @GetMapping("/accounts")
    ResponseEntity<List<LedgerAccountResponse>> getAccounts(
        @RequestParam(required = false) String accountType,
        @RequestParam(required = false) Boolean active
    );
    
    // Journal entry operations
    @PostMapping("/journal-entries")
    ResponseEntity<JournalEntryResponse> createJournalEntry(@RequestBody CreateJournalEntryRequest request);
    
    @GetMapping("/journal-entries/{entryId}")
    ResponseEntity<JournalEntryResponse> getJournalEntry(@PathVariable UUID entryId);
    
    @PostMapping("/journal-entries/{entryId}/post")
    ResponseEntity<JournalEntryResponse> postJournalEntry(@PathVariable UUID entryId);
    
    @PostMapping("/journal-entries/{entryId}/void")
    ResponseEntity<Void> voidJournalEntry(@PathVariable UUID entryId);
    
    // Balance operations
    @GetMapping("/accounts/{accountId}/balance")
    ResponseEntity<AccountBalanceResponse> getAccountBalance(
        @PathVariable UUID accountId,
        @RequestParam(required = false) LocalDate asOfDate
    );
    
    @GetMapping("/accounts/{accountId}/balances")
    ResponseEntity<List<AccountBalanceResponse>> getAccountBalances(
        @PathVariable UUID accountId,
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate
    );
    
    // Trial balance
    @GetMapping("/reports/trial-balance")
    ResponseEntity<TrialBalanceResponse> getTrialBalance(
        @RequestParam(required = false) LocalDate asOfDate,
        @RequestParam(required = false) UUID companyId
    );
    
    // General ledger
    @GetMapping("/reports/general-ledger")
    ResponseEntity<GeneralLedgerReportResponse> getGeneralLedger(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) UUID companyId
    );
    
    // Financial statements
    @GetMapping("/reports/balance-sheet")
    ResponseEntity<BalanceSheetResponse> getBalanceSheet(
        @RequestParam LocalDate asOfDate,
        @RequestParam(required = false) UUID companyId
    );
    
    @GetMapping("/reports/income-statement")
    ResponseEntity<IncomeStatementResponse> getIncomeStatement(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false) UUID companyId
    );
    
    @GetMapping("/reports/cash-flow")
    ResponseEntity<CashFlowStatementResponse> getCashFlowStatement(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false) UUID companyId
    );
    
    // Reconciliation
    @PostMapping("/reconciliation/bank")
    ResponseEntity<ReconciliationResponse> reconcileBankAccount(
        @RequestBody BankReconciliationRequest request
    );
    
    @GetMapping("/reconciliation/status/{accountId}")
    ResponseEntity<ReconciliationStatusResponse> getReconciliationStatus(
        @PathVariable UUID accountId,
        @RequestParam(required = false) LocalDate asOfDate
    );
    
    // Validation
    @PostMapping("/validation/transaction")
    ResponseEntity<ValidationResponse> validateTransaction(
        @RequestBody TransactionValidationRequest request
    );
    
    @PostMapping("/validation/account")
    ResponseEntity<ValidationResponse> validateAccount(
        @RequestBody AccountValidationRequest request
    );
    
    // Bulk operations
    @PostMapping("/accounts/bulk")
    ResponseEntity<BulkCreateResponse> createAccountsBulk(
        @RequestBody List<CreateAccountRequest> requests
    );
    
    @PostMapping("/journal-entries/bulk")
    ResponseEntity<BulkCreateResponse> createJournalEntriesBulk(
        @RequestBody List<CreateJournalEntryRequest> requests
    );
}