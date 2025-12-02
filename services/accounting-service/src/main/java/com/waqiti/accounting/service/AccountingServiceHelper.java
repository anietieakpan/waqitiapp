package com.waqiti.accounting.service;

import com.waqiti.accounting.domain.*;
import com.waqiti.accounting.dto.response.FinancialStatementLine;
import com.waqiti.accounting.repository.AccountBalanceRepository;
import com.waqiti.accounting.repository.ChartOfAccountsRepository;
import com.waqiti.accounting.repository.GeneralLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Helper class for AccountingService to fix stub methods
 * Implements financial statement line generation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountingServiceHelper {

    private final GeneralLedgerRepository ledgerRepository;
    private final ChartOfAccountsRepository accountsRepository;
    private final AccountBalanceRepository balanceRepository;

    /**
     * Get account balances for financial statements (PRODUCTION FIX for stub method)
     */
    public List<FinancialStatementLine> getAccountBalances(
            AccountType type,
            FinancialPeriod period,
            String codeRangeStart,
            String codeRangeEnd) {

        log.debug("Getting account balances for type={}, period={}, range={}-{}",
            type, period.getName(), codeRangeStart, codeRangeEnd);

        // Get all accounts in the range
        List<ChartOfAccounts> accounts = accountsRepository
            .findByTypeAndCodeRange(type, codeRangeStart, codeRangeEnd);

        return accounts.stream()
            .map(account -> {
                // Sum debits and credits for this account in the period
                BigDecimal debits = ledgerRepository
                    .sumDebits(account.getCode(), period.getFiscalYear(),
                              period.getFiscalPeriod())
                    .orElse(BigDecimal.ZERO);

                BigDecimal credits = ledgerRepository
                    .sumCredits(account.getCode(), period.getFiscalYear(),
                               period.getFiscalPeriod())
                    .orElse(BigDecimal.ZERO);

                // Calculate ending balance based on normal balance
                BigDecimal balance;
                if (account.getNormalBalance() == NormalBalance.DEBIT) {
                    balance = debits.subtract(credits);
                } else {
                    balance = credits.subtract(debits);
                }

                // Only include accounts with non-zero balance
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    return FinancialStatementLine.builder()
                        .accountCode(account.getCode())
                        .accountName(account.getName())
                        .accountType(account.getType())
                        .amount(balance.abs())
                        .build();
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Get account balances as of a specific date (PRODUCTION FIX for stub method)
     */
    public List<FinancialStatementLine> getAccountBalancesAsOf(
            AccountType type,
            LocalDate asOfDate,
            String codeRangeStart,
            String codeRangeEnd) {

        log.debug("Getting account balances as of {} for type={}, range={}-{}",
            asOfDate, type, codeRangeStart, codeRangeEnd);

        // Get all accounts in the range
        List<ChartOfAccounts> accounts = accountsRepository
            .findByTypeAndCodeRange(type, codeRangeStart, codeRangeEnd);

        return accounts.stream()
            .map(account -> {
                // Sum debits and credits up to the specified date
                BigDecimal debits = ledgerRepository
                    .sumDebitsAsOf(account.getCode(), asOfDate)
                    .orElse(BigDecimal.ZERO);

                BigDecimal credits = ledgerRepository
                    .sumCreditsAsOf(account.getCode(), asOfDate)
                    .orElse(BigDecimal.ZERO);

                // Calculate balance based on normal balance
                BigDecimal balance;
                if (account.getNormalBalance() == NormalBalance.DEBIT) {
                    balance = debits.subtract(credits);
                } else {
                    balance = credits.subtract(debits);
                }

                // Only include accounts with non-zero balance
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    return FinancialStatementLine.builder()
                        .accountCode(account.getCode())
                        .accountName(account.getName())
                        .accountType(account.getType())
                        .amount(balance.abs())
                        .build();
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Get financial period by ID
     */
    public FinancialPeriod getFinancialPeriod(UUID periodId,
                                              com.waqiti.accounting.repository.FinancialPeriodRepository periodRepository) {
        return periodRepository.findById(periodId)
            .orElseThrow(() -> new com.waqiti.accounting.exception.AccountingException(
                "Financial period not found: " + periodId));
    }

    /**
     * Get current financial period
     */
    public FinancialPeriod getCurrentPeriod(
            com.waqiti.accounting.repository.FinancialPeriodRepository periodRepository) {
        return periodRepository.findCurrentPeriod(LocalDate.now())
            .orElseThrow(() -> new com.waqiti.accounting.exception.AccountingException(
                "No current financial period found. Please create a fiscal period."));
    }
}
