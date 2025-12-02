package com.waqiti.reporting.client;

import com.waqiti.reporting.dto.BalanceHistoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "ledger-service", 
    path = "/api/ledger",
    fallback = LedgerServiceClientFallback.class
)
public interface LedgerServiceClient {

    /**
     * Get balance history for account statements
     */
    @GetMapping("/accounts/{accountId}/balance-history")
    BalanceHistoryResponse getBalanceHistory(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get total asset balance for financial reporting
     */
    @GetMapping("/balances/assets/total")
    BigDecimal getTotalAssetBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get total liability balance
     */
    @GetMapping("/balances/liabilities/total")
    BigDecimal getTotalLiabilityBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get balance sheet data
     */
    @GetMapping("/balances/balance-sheet")
    BalanceSheetData getBalanceSheetData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get account balances by category
     */
    @GetMapping("/balances/by-category")
    List<CategoryBalance> getBalancesByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get cash flow data
     */
    @GetMapping("/cash-flow")
    CashFlowData getCashFlowData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get ledger entries for audit trail
     */
    @GetMapping("/entries")
    List<LedgerEntry> getLedgerEntries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String accountType
    );

    /**
     * Get trial balance
     */
    @GetMapping("/trial-balance")
    TrialBalance getTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get account aging analysis
     */
    @GetMapping("/aging-analysis")
    List<AgingBucket> getAccountAgingAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get financial ratios
     */
    @GetMapping("/ratios")
    FinancialRatios getFinancialRatios(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    // Supporting DTOs
    record BalanceSheetData(
            LocalDate asOfDate,
            BigDecimal totalAssets,
            BigDecimal currentAssets,
            BigDecimal nonCurrentAssets,
            BigDecimal totalLiabilities,
            BigDecimal currentLiabilities,
            BigDecimal longTermLiabilities,
            BigDecimal totalEquity,
            BigDecimal retainedEarnings
    ) {}

    record CategoryBalance(
            String category,
            String accountType,
            BigDecimal balance,
            String currency
    ) {}

    record CashFlowData(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal operatingCashFlow,
            BigDecimal investingCashFlow,
            BigDecimal financingCashFlow,
            BigDecimal netCashFlow,
            BigDecimal beginningCash,
            BigDecimal endingCash
    ) {}

    record LedgerEntry(
            String entryId,
            LocalDate entryDate,
            String accountCode,
            String accountName,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String description,
            String reference,
            String entryType
    ) {}

    record TrialBalance(
            LocalDate asOfDate,
            List<TrialBalanceAccount> accounts,
            BigDecimal totalDebits,
            BigDecimal totalCredits,
            Boolean balanced
    ) {}

    record TrialBalanceAccount(
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal debitBalance,
            BigDecimal creditBalance
    ) {}

    record AgingBucket(
            String bucketName,
            Integer daysRange,
            BigDecimal amount,
            Long accountCount
    ) {}

    record FinancialRatios(
            LocalDate calculationDate,
            Double currentRatio,
            Double quickRatio,
            Double debtToEquityRatio,
            Double returnOnAssets,
            Double returnOnEquity,
            Double assetTurnover,
            Double equityMultiplier
    ) {}
}