package com.waqiti.ledger.controller;

import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.service.AccountingService;
import com.waqiti.ledger.service.ChartOfAccountsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Account Controller
 * 
 * REST API endpoints for account management and chart of accounts operations.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Accounts", description = "Account management and chart of accounts operations")
public class AccountController {

    private final AccountingService accountingService;
    private final ChartOfAccountsService chartOfAccountsService;

    @GetMapping
    @Operation(summary = "Get chart of accounts",
               description = "Retrieves paginated chart of accounts with optional filtering. " +
                           "Use pagination to handle large account hierarchies efficiently.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved accounts"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<LedgerAccountResponse>> getChartOfAccounts(
            @RequestParam(required = false) String accountType,
            @RequestParam(defaultValue = "true") Boolean activeOnly,
            Pageable pageable) {

        log.info("Getting chart of accounts - type: {}, activeOnly: {}, page: {}, size: {}",
                accountType, activeOnly, pageable.getPageNumber(), pageable.getPageSize());
        Page<LedgerAccountResponse> accounts = accountingService.getChartOfAccounts(accountType, activeOnly, pageable);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountCode}")
    @Operation(summary = "Get account details", description = "Retrieves detailed information for a specific account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<LedgerAccountDetailResponse> getAccountDetails(
            @PathVariable String accountCode) {
        
        log.info("Getting account details for: {}", accountCode);
        LedgerAccountDetailResponse accountDetails = accountingService.getAccountDetails(accountCode);
        return ResponseEntity.ok(accountDetails);
    }

    @PostMapping
    @Operation(summary = "Create new account", description = "Creates a new ledger account in the chart of accounts")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "409", description = "Account already exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LedgerAccountResponse> createAccount(
            @Valid @RequestBody CreateLedgerAccountRequest request) {
        
        log.info("Creating new account: {} - {}", request.getAccountCode(), request.getAccountName());
        LedgerAccountResponse account = accountingService.createLedgerAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @PutMapping("/{accountCode}")
    @Operation(summary = "Update account", description = "Updates an existing ledger account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UpdateAccountResponse> updateAccount(
            @PathVariable String accountCode,
            @Valid @RequestBody UpdateAccountRequest request) {
        
        log.info("Updating account: {}", accountCode);
        UpdateAccountResponse response = chartOfAccountsService.updateAccount(accountCode, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{accountCode}")
    @Operation(summary = "Deactivate account", description = "Deactivates a ledger account (soft delete)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deactivated successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "409", description = "Account has transactions and cannot be deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateAccount(@PathVariable String accountCode) {
        log.info("Deactivating account: {}", accountCode);
        chartOfAccountsService.deactivateAccount(accountCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountCode}/balance")
    @Operation(summary = "Get account balance", description = "Retrieves the current balance for an account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        
        log.info("Getting balance for account: {} as of {}", accountCode, asOfDate);
        AccountBalanceResponse balance = accountingService.getAccountBalance(accountCode, asOfDate);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/{accountCode}/transactions")
    @Operation(summary = "Get account transactions", description = "Retrieves transactions for a specific account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<LedgerEntryResponse>> getAccountTransactions(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        
        log.info("Getting transactions for account: {} from {} to {}", accountCode, startDate, endDate);
        Page<LedgerEntryResponse> transactions = accountingService.getAccountTransactions(
            accountCode, startDate, endDate, pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/search")
    @Operation(summary = "Search accounts", description = "Search for accounts by code or name")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<Page<LedgerAccountResponse>> searchAccounts(
            @RequestParam String query,
            Pageable pageable) {
        
        log.info("Searching accounts with query: {}", query);
        Page<LedgerAccountResponse> results = chartOfAccountsService.searchAccounts(query, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/types")
    @Operation(summary = "Get account types", description = "Retrieves all available account types")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account types retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<String>> getAccountTypes() {
        List<String> accountTypes = chartOfAccountsService.getAccountTypes();
        return ResponseEntity.ok(accountTypes);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate account", description = "Validates account details without creating")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Validation completed"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public ResponseEntity<ValidationResponse> validateAccount(
            @Valid @RequestBody CreateLedgerAccountRequest request) {
        
        log.info("Validating account: {}", request.getAccountCode());
        ValidationResponse validation = chartOfAccountsService.validateAccount(request);
        return ResponseEntity.ok(validation);
    }

    @GetMapping("/hierarchy")
    @Operation(summary = "Get account hierarchy", description = "Retrieves the hierarchical structure of accounts")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Hierarchy retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'VIEWER')")
    public ResponseEntity<AccountHierarchyResponse> getAccountHierarchy(
            @RequestParam(required = false) UUID parentAccountId) {
        
        log.info("Getting account hierarchy for parent: {}", parentAccountId);
        AccountHierarchyResponse hierarchy = chartOfAccountsService.getAccountHierarchy(parentAccountId);
        return ResponseEntity.ok(hierarchy);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk create accounts", description = "Creates multiple accounts in a single operation")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Accounts created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "207", description = "Partial success"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkCreateResponse> bulkCreateAccounts(
            @Valid @RequestBody List<CreateLedgerAccountRequest> requests) {
        
        log.info("Bulk creating {} accounts", requests.size());
        BulkCreateResponse response = chartOfAccountsService.bulkCreateAccounts(requests);
        
        if (response.hasErrors()) {
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/export")
    @Operation(summary = "Export chart of accounts", description = "Exports the chart of accounts to various formats")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    public ResponseEntity<byte[]> exportChartOfAccounts(
            @RequestParam(defaultValue = "CSV") String format) {
        
        log.info("Exporting chart of accounts in format: {}", format);
        byte[] exportData = chartOfAccountsService.exportChartOfAccounts(format);
        
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=chart-of-accounts." + format.toLowerCase())
            .body(exportData);
    }
}