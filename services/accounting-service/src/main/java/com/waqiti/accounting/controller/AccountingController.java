package com.waqiti.accounting.controller;

import com.waqiti.accounting.domain.FinancialPeriod;
import com.waqiti.accounting.dto.request.PaymentTransactionRequest;
import com.waqiti.accounting.dto.request.ReconciliationRequest;
import com.waqiti.accounting.dto.response.*;
import com.waqiti.accounting.service.AccountingService;
import com.waqiti.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Main accounting controller for financial operations
 */
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Accounting", description = "Accounting and general ledger operations")
@SecurityRequirement(name = "bearer-jwt")
public class AccountingController {

    private final AccountingService accountingService;

    @PostMapping("/transactions/payment")
    @PreAuthorize("hasAnyRole('SYSTEM', 'PAYMENT_SERVICE')")
    @Operation(summary = "Process payment transaction accounting",
               description = "Creates journal entries for a payment transaction")
    public ResponseEntity<ApiResponse<PaymentAccountingResult>> processPaymentTransaction(
            @Valid @RequestBody PaymentTransactionRequest request) {

        log.info("Processing payment accounting: transactionId={}", request.getTransactionId());

        PaymentAccountingResult result = accountingService.processPaymentTransaction(request);

        return ApiResponse.success(result, "Payment accounting processed successfully")
            .toResponseEntity();
    }

    @GetMapping("/reports/trial-balance")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'CFO')")
    @Operation(summary = "Generate trial balance",
               description = "Generates trial balance for a financial period")
    public ResponseEntity<ApiResponse<TrialBalance>> getTrialBalance(
            @Parameter(description = "Financial period ID")
            @RequestParam UUID periodId) {

        log.info("Generating trial balance for period: {}", periodId);

        FinancialPeriod period = accountingService.getFinancialPeriod(periodId);
        TrialBalance trialBalance = accountingService.generateTrialBalance(period);

        return ApiResponse.success(trialBalance)
            .toResponseEntity();
    }

    @GetMapping("/reports/income-statement")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'CFO')")
    @Operation(summary = "Generate income statement",
               description = "Generates P&L statement for a period")
    public ResponseEntity<ApiResponse<IncomeStatement>> getIncomeStatement(
            @RequestParam UUID periodId) {

        log.info("Generating income statement for period: {}", periodId);

        FinancialPeriod period = accountingService.getFinancialPeriod(periodId);
        IncomeStatement statement = accountingService.generateIncomeStatement(period);

        return ApiResponse.success(statement)
            .toResponseEntity();
    }

    @GetMapping("/reports/balance-sheet")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'CFO')")
    @Operation(summary = "Generate balance sheet",
               description = "Generates balance sheet as of a date")
    public ResponseEntity<ApiResponse<BalanceSheet>> getBalanceSheet(
            @Parameter(description = "As of date (YYYY-MM-DD)")
            @RequestParam LocalDate asOfDate) {

        log.info("Generating balance sheet as of: {}", asOfDate);

        BalanceSheet balanceSheet = accountingService.generateBalanceSheet(asOfDate);

        return ApiResponse.success(balanceSheet)
            .toResponseEntity();
    }

    @PostMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
    @Operation(summary = "Perform account reconciliation",
               description = "Reconciles account balance with external system")
    public ResponseEntity<ApiResponse<ReconciliationResult>> reconcileAccount(
            @Valid @RequestBody ReconciliationRequest request) {

        log.info("Starting reconciliation for account: {}", request.getAccountCode());

        ReconciliationResult result = accountingService.reconcileAccount(request);

        return ApiResponse.success(result, "Reconciliation completed")
            .toResponseEntity();
    }

    @GetMapping("/periods/current")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'SYSTEM')")
    @Operation(summary = "Get current financial period",
               description = "Returns the currently active financial period")
    public ResponseEntity<ApiResponse<FinancialPeriod>> getCurrentPeriod() {

        log.info("Fetching current financial period");

        FinancialPeriod period = accountingService.getCurrentPeriod();

        return ApiResponse.success(period)
            .toResponseEntity();
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check accounting service health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ApiResponse.success("Accounting service is healthy")
            .toResponseEntity();
    }
}
