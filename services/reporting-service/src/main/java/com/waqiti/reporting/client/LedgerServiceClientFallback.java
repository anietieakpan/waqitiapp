package com.waqiti.reporting.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.waqiti.reporting.client.LedgerServiceClient.*;

@Slf4j
@Component
public class LedgerServiceClientFallback implements LedgerServiceClient {

    @Override
    public BalanceHistoryResponse getBalanceHistory(UUID accountId, LocalDate fromDate, LocalDate toDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve balance history - Ledger Service unavailable. " +
                "AccountId: {}, DateRange: {} to {}", accountId, fromDate, toDate);
        
        return BalanceHistoryResponse.builder()
                .accountId(accountId)
                .fromDate(fromDate)
                .toDate(toDate)
                .balances(Collections.emptyList())
                .status("UNAVAILABLE")
                .message("Balance history temporarily unavailable - report generation delayed")
                .isStale(true)
                .build();
    }

    @Override
    public BigDecimal getTotalAssetBalance(LocalDate date) {
        log.error("FALLBACK ACTIVATED: BLOCKING financial report - Cannot retrieve total assets. Date: {}", date);
        return null;
    }

    @Override
    public BigDecimal getTotalLiabilityBalance(LocalDate date) {
        log.error("FALLBACK ACTIVATED: BLOCKING financial report - Cannot retrieve total liabilities. Date: {}", date);
        return null;
    }

    @Override
    public BalanceSheetData getBalanceSheetData(LocalDate date) {
        log.error("FALLBACK ACTIVATED: BLOCKING balance sheet generation - Ledger Service unavailable. Date: {}", date);
        
        return new BalanceSheetData(
                date,
                null, null, null,
                null, null, null,
                null, null
        );
    }

    @Override
    public List<CategoryBalance> getBalancesByCategory(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve category balances - Ledger Service unavailable. Date: {}", date);
        return Collections.emptyList();
    }

    @Override
    public CashFlowData getCashFlowData(LocalDate fromDate, LocalDate toDate) {
        log.error("FALLBACK ACTIVATED: BLOCKING cash flow report - Ledger Service unavailable. DateRange: {} to {}", 
                fromDate, toDate);
        
        return new CashFlowData(
                fromDate, toDate,
                null, null, null, null, null, null
        );
    }

    @Override
    public List<LedgerEntry> getLedgerEntries(LocalDate fromDate, LocalDate toDate, String accountType) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve ledger entries - Ledger Service unavailable. " +
                "DateRange: {} to {}, AccountType: {}", fromDate, toDate, accountType);
        return Collections.emptyList();
    }

    @Override
    public TrialBalance getTrialBalance(LocalDate date) {
        log.error("FALLBACK ACTIVATED: BLOCKING trial balance - Ledger Service unavailable. Date: {}", date);
        
        return new TrialBalance(
                date,
                Collections.emptyList(),
                null,
                null,
                false
        );
    }

    @Override
    public List<AgingBucket> getAccountAgingAnalysis(LocalDate date) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve aging analysis - Ledger Service unavailable. Date: {}", date);
        return Collections.emptyList();
    }

    @Override
    public FinancialRatios getFinancialRatios(LocalDate date) {
        log.error("FALLBACK ACTIVATED: BLOCKING financial ratios calculation - Ledger Service unavailable. Date: {}", date);
        
        return new FinancialRatios(
                date,
                null, null, null,
                null, null, null, null
        );
    }
}