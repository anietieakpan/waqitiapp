package com.waqiti.ledger.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.service.AccountingService;
import com.waqiti.ledger.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ledger Management", description = "Double-entry bookkeeping and accounting operations")
@Validated
public class LedgerController {

    private final LedgerService ledgerService;
    private final AccountingService accountingService;
    private final ReconciliationService reconciliationService;

    // Journal Entry Endpoints
    @PostMapping("/journal-entries")
    @Operation(summary = "Create journal entry")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> createJournalEntry(
            @Valid @RequestBody CreateJournalEntryRequest request) {
        log.info("Creating journal entry: {}", request.getDescription());
        
        JournalEntryResponse response = ledgerService.createJournalEntry(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/journal-entries/batch")
    @Operation(summary = "Create batch journal entries")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BatchJournalEntryResponse>> createBatchJournalEntries(
            @Valid @RequestBody BatchJournalEntryRequest request) {
        log.info("Creating batch journal entries: {} entries", request.getEntries().size());
        
        BatchJournalEntryResponse response = ledgerService.createBatchJournalEntries(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/journal-entries")
    @Operation(summary = "Get journal entries")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<Page<JournalEntryResponse>>> getJournalEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false) String reference,
            Pageable pageable) {
        
        JournalEntryFilter filter = JournalEntryFilter.builder()
                .startDate(startDate)
                .endDate(endDate)
                .accountCode(accountCode)
                .reference(reference)
                .build();
        
        Page<JournalEntryResponse> entries = ledgerService.getJournalEntries(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    @GetMapping("/journal-entries/{entryId}")
    @Operation(summary = "Get journal entry details")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<JournalEntryDetailResponse>> getJournalEntryDetails(
            @PathVariable UUID entryId) {
        
        JournalEntryDetailResponse entry = ledgerService.getJournalEntryDetails(entryId);
        return ResponseEntity.ok(ApiResponse.success(entry));
    }

    @PostMapping("/journal-entries/{entryId}/reverse")
    @Operation(summary = "Reverse journal entry")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> reverseJournalEntry(
            @PathVariable UUID entryId,
            @Valid @RequestBody ReverseJournalEntryRequest request) {
        log.info("Reversing journal entry: {}", entryId);
        
        JournalEntryResponse response = ledgerService.reverseJournalEntry(entryId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Chart of Accounts Endpoints
    @GetMapping("/accounts")
    @Operation(summary = "Get chart of accounts",
               description = "Retrieves paginated list of accounts with optional filtering by type and active status")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<Page<LedgerAccountResponse>>> getChartOfAccounts(
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) Boolean activeOnly,
            Pageable pageable) {

        Page<LedgerAccountResponse> accounts = accountingService.getChartOfAccounts(accountType, activeOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @PostMapping("/accounts")
    @Operation(summary = "Create ledger account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LedgerAccountResponse>> createLedgerAccount(
            @Valid @RequestBody CreateLedgerAccountRequest request) {
        log.info("Creating ledger account: {}", request.getAccountName());
        
        LedgerAccountResponse response = accountingService.createLedgerAccount(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountCode}")
    @Operation(summary = "Get account details")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<LedgerAccountDetailResponse>> getAccountDetails(
            @PathVariable String accountCode) {
        
        LedgerAccountDetailResponse account = accountingService.getAccountDetails(accountCode);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @GetMapping("/accounts/{accountCode}/balance")
    @Operation(summary = "Get account balance")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<AccountBalanceResponse>> getAccountBalance(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        
        AccountBalanceResponse balance = accountingService.getAccountBalance(accountCode, asOfDate);
        return ResponseEntity.ok(ApiResponse.success(balance));
    }

    @GetMapping("/accounts/{accountCode}/transactions")
    @Operation(summary = "Get account transactions")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<Page<LedgerEntryResponse>>> getAccountTransactions(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        
        Page<LedgerEntryResponse> transactions = accountingService.getAccountTransactions(
                accountCode, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    // Financial Reports
    @GetMapping("/reports/trial-balance")
    @Operation(summary = "Generate trial balance")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<TrialBalanceResponse>> generateTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        log.info("Generating trial balance as of: {}", asOfDate);
        
        TrialBalanceResponse trialBalance = accountingService.generateTrialBalance(asOfDate);
        return ResponseEntity.ok(ApiResponse.success(trialBalance));
    }

    @GetMapping("/reports/balance-sheet")
    @Operation(summary = "Generate balance sheet")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<BalanceSheetResponse>> generateBalanceSheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        log.info("Generating balance sheet as of: {}", asOfDate);
        
        BalanceSheetResponse balanceSheet = accountingService.generateBalanceSheet(asOfDate);
        return ResponseEntity.ok(ApiResponse.success(balanceSheet));
    }

    @GetMapping("/reports/income-statement")
    @Operation(summary = "Generate income statement")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<IncomeStatementResponse>> generateIncomeStatement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Generating income statement from {} to {}", startDate, endDate);
        
        IncomeStatementResponse incomeStatement = accountingService.generateIncomeStatement(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(incomeStatement));
    }

    @GetMapping("/reports/general-ledger")
    @Operation(summary = "Generate general ledger report")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<GeneralLedgerReportResponse>> generateGeneralLedgerReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<String> accountCodes) {
        log.info("Generating general ledger report from {} to {}", startDate, endDate);
        
        GeneralLedgerReportResponse report = accountingService.generateGeneralLedgerReport(
                startDate, endDate, accountCodes);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * P1-2 CRITICAL FIX: Get wallet balance from ledger for reconciliation
     *
     * Returns the authoritative wallet balance calculated from ledger entries.
     * This is the "source of truth" used by wallet-service reconciliation to detect discrepancies.
     *
     * RECONCILIATION FLOW:
     * 1. Wallet-service calls this endpoint hourly for all active wallets
     * 2. Ledger calculates balance by summing all transactions in wallet's liability account
     * 3. Wallet-service compares ledger balance vs cached balance
     * 4. Discrepancies trigger auto-correction or manual review alerts
     *
     * @param walletId Wallet UUID
     * @return Wallet balance from ledger
     */
    @GetMapping("/wallets/{walletId}/balance")
    @Operation(summary = "Get wallet balance from ledger (for reconciliation)")
    @PreAuthorize("hasRole('WALLET_SERVICE') or hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getWalletBalance(
            @PathVariable UUID walletId) {
        log.debug("P1-2: Getting wallet balance from ledger for reconciliation: walletId={}", walletId);

        WalletBalanceResponse balance = ledgerService.getWalletBalance(walletId);
        return ResponseEntity.ok(ApiResponse.success(balance));
    }

    // Reconciliation Endpoints
    @PostMapping("/reconciliation/bank")
    @Operation(summary = "Perform bank reconciliation")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> performBankReconciliation(
            @Valid @RequestBody BankReconciliationRequest request) {
        log.info("Performing bank reconciliation for account: {}", request.getBankAccountCode());

        ReconciliationResponse response = reconciliationService.performBankReconciliation(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reconciliation/discrepancies")
    @Operation(summary = "Get reconciliation discrepancies")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReconciliationDiscrepancyResponse>>> getReconciliationDiscrepancies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accountCode,
            Pageable pageable) {
        
        Page<ReconciliationDiscrepancyResponse> discrepancies = 
                reconciliationService.getDiscrepancies(status, accountCode, pageable);
        return ResponseEntity.ok(ApiResponse.success(discrepancies));
    }

    @PostMapping("/reconciliation/discrepancies/{discrepancyId}/resolve")
    @Operation(summary = "Resolve reconciliation discrepancy")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReconciliationDiscrepancyResponse>> resolveDiscrepancy(
            @PathVariable UUID discrepancyId,
            @Valid @RequestBody ResolveDiscrepancyRequest request) {
        log.info("Resolving reconciliation discrepancy: {}", discrepancyId);
        
        ReconciliationDiscrepancyResponse response = 
                reconciliationService.resolveDiscrepancy(discrepancyId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Period End Operations
    @PostMapping("/period-end/close")
    @Operation(summary = "Close accounting period")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PeriodCloseResponse>> closeAccountingPeriod(
            @Valid @RequestBody ClosePeriodRequest request) {
        log.info("Closing accounting period: {}", request.getPeriodEndDate());
        
        PeriodCloseResponse response = accountingService.closeAccountingPeriod(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/period-end/status")
    @Operation(summary = "Get period end status")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PeriodStatusResponse>> getPeriodEndStatus() {
        
        PeriodStatusResponse status = accountingService.getPeriodEndStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // Audit Trail
    @GetMapping("/audit-trail")
    @Operation(summary = "Get ledger audit trail")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<LedgerAuditTrailResponse>>> getLedgerAuditTrail(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        
        Page<LedgerAuditTrailResponse> auditTrail = ledgerService.getAuditTrail(
                entityType, entityId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(auditTrail));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Ledger service is healthy"));
    }
}