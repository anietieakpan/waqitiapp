package com.waqiti.ledger.controller;

import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.service.AccountingService;
import com.waqiti.ledger.service.BalanceSheetService;
import com.waqiti.ledger.service.IncomeStatementService;
import com.waqiti.ledger.service.CashFlowStatementService;
import com.waqiti.ledger.service.FinancialDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Financial Dashboard Controller
 * 
 * Provides comprehensive financial reporting and dashboard endpoints
 * for management reporting and financial analysis.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Financial Dashboard", description = "Financial reporting and dashboard APIs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN', 'MANAGER', 'VIEWER')")
public class FinancialDashboardController {

    private final FinancialDashboardService dashboardService;
    private final BalanceSheetService balanceSheetService;
    private final IncomeStatementService incomeStatementService;
    private final CashFlowStatementService cashFlowService;
    private final AccountingService accountingService;

    @GetMapping("/overview")
    @Operation(summary = "Get financial overview", 
               description = "Retrieve high-level financial metrics and KPIs")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Financial overview retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<FinancialOverviewResponse> getFinancialOverview(
            @Parameter(description = "As of date for the overview (defaults to today)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        log.info("Retrieving financial overview as of: {}", effectiveDate);
        
        FinancialOverviewResponse overview = dashboardService.getFinancialOverview(effectiveDate);
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/balance-sheet")
    @Operation(summary = "Generate balance sheet", 
               description = "Generate balance sheet as of specified date")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance sheet generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<BalanceSheetResponse> getBalanceSheet(
            @Parameter(description = "As of date for balance sheet")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @Parameter(description = "Balance sheet format (STANDARD, COMPARATIVE, DETAILED)")
            @RequestParam(defaultValue = "STANDARD") String format) {
        
        log.info("Generating balance sheet as of: {} in format: {}", asOfDate, format);
        
        BalanceSheetService.BalanceSheetFormat sheetFormat = 
            BalanceSheetService.BalanceSheetFormat.valueOf(format.toUpperCase());
        
        BalanceSheetResponse balanceSheet = balanceSheetService.generateBalanceSheet(asOfDate, sheetFormat);
        return ResponseEntity.ok(balanceSheet);
    }

    @GetMapping("/income-statement")
    @Operation(summary = "Generate income statement", 
               description = "Generate income statement for specified period")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Income statement generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<IncomeStatementResponse> getIncomeStatement(
            @Parameter(description = "Start date of the period")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date of the period")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Income statement format (SINGLE_STEP, MULTI_STEP)")
            @RequestParam(defaultValue = "MULTI_STEP") String format) {
        
        log.info("Generating income statement from {} to {} in format: {}", startDate, endDate, format);
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        
        IncomeStatementService.IncomeStatementFormat statementFormat = 
            IncomeStatementService.IncomeStatementFormat.valueOf(format.toUpperCase());
        
        IncomeStatementResponse incomeStatement = 
            incomeStatementService.generateIncomeStatement(startDate, endDate, statementFormat);
        return ResponseEntity.ok(incomeStatement);
    }

    @GetMapping("/cash-flow")
    @Operation(summary = "Generate cash flow statement", 
               description = "Generate cash flow statement for specified period")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cash flow statement generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<CashFlowStatementResponse> getCashFlowStatement(
            @Parameter(description = "Start date of the period")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date of the period")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Cash flow method (DIRECT, INDIRECT)")
            @RequestParam(defaultValue = "INDIRECT") String method) {
        
        log.info("Generating cash flow statement from {} to {} using {} method", startDate, endDate, method);
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        
        CashFlowStatementService.CashFlowMethod flowMethod = 
            CashFlowStatementService.CashFlowMethod.valueOf(method.toUpperCase());
        
        CashFlowStatementResponse cashFlow = 
            cashFlowService.generateCashFlowStatement(startDate, endDate, flowMethod);
        return ResponseEntity.ok(cashFlow);
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Generate trial balance", 
               description = "Generate trial balance as of specified date")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trial balance generated successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<TrialBalanceResponse> getTrialBalance(
            @Parameter(description = "As of date for trial balance")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        
        log.info("Generating trial balance as of: {}", asOfDate);
        
        TrialBalanceResponse trialBalance = accountingService.generateTrialBalance(asOfDate);
        return ResponseEntity.ok(trialBalance);
    }

    @GetMapping("/financial-ratios")
    @Operation(summary = "Calculate financial ratios", 
               description = "Calculate key financial ratios and metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Financial ratios calculated successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<FinancialRatiosResponse> getFinancialRatios(
            @Parameter(description = "As of date for ratio calculations")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        
        log.info("Calculating financial ratios as of: {}", asOfDate);
        
        FinancialRatiosResponse ratios = dashboardService.calculateFinancialRatios(asOfDate);
        return ResponseEntity.ok(ratios);
    }

    @GetMapping("/performance-metrics")
    @Operation(summary = "Get performance metrics", 
               description = "Retrieve key performance indicators and metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<PerformanceMetricsResponse> getPerformanceMetrics(
            @Parameter(description = "Start date of the period")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date of the period")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Retrieving performance metrics from {} to {}", startDate, endDate);
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        
        PerformanceMetricsResponse metrics = dashboardService.getPerformanceMetrics(startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/trend-analysis")
    @Operation(summary = "Get trend analysis", 
               description = "Analyze financial trends over time")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trend analysis completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<TrendAnalysisResponse> getTrendAnalysis(
            @Parameter(description = "Start date for trend analysis")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for trend analysis")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Trend period (MONTHLY, QUARTERLY, YEARLY)")
            @RequestParam(defaultValue = "MONTHLY") String period) {
        
        log.info("Analyzing trends from {} to {} by {}", startDate, endDate, period);
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        
        TrendAnalysisResponse trends = dashboardService.analyzeTrends(startDate, endDate, period);
        return ResponseEntity.ok(trends);
    }
}