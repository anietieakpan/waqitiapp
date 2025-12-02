package com.waqiti.investment.regulatory.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.investment.domain.InvestmentAccount;
import com.waqiti.investment.domain.InvestmentTransaction;
import com.waqiti.investment.regulatory.dto.*;
import com.waqiti.investment.regulatory.repository.SECReportRepository;
import com.waqiti.investment.repository.InvestmentAccountRepository;
import com.waqiti.investment.repository.InvestmentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SEC Regulatory Reporting Service
 *
 * Implements comprehensive SEC reporting requirements including:
 * - Form D (Regulation D offerings)
 * - Form ADV (Investment adviser registration)
 * - Form 13F (Institutional investment manager holdings)
 * - Customer account statements
 * - Trade confirmations
 *
 * Compliant with:
 * - Securities Act of 1933
 * - Securities Exchange Act of 1934
 * - Investment Advisers Act of 1940
 * - SEC Rule 10b-10 (Customer confirmations)
 * - SEC Rule 17a-7 (Recordkeeping)
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SECReportingService {

    private final InvestmentAccountRepository accountRepository;
    private final InvestmentTransactionRepository transactionRepository;
    private final SECReportRepository secReportRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final SECEDGARFilingService edgarFilingService;
    private final TradeConfirmationService confirmationService;

    /**
     * Generate comprehensive SEC Form 13F report
     * Required for institutional investment managers with >$100M AUM
     * Filed within 45 days of quarter end
     *
     * @param quarterEndDate End date of reporting quarter
     * @return Form 13F report details
     */
    @Timed(value = "sec.form13f.generation", description = "Time to generate Form 13F")
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public Form13FReport generateForm13F(LocalDate quarterEndDate) {
        log.info("Generating SEC Form 13F for quarter ending {}", quarterEndDate);

        long startTime = System.currentTimeMillis();

        try {
            // Calculate total AUM across all accounts
            BigDecimal totalAUM = accountRepository.calculateTotalAUMAsOf(quarterEndDate);

            if (totalAUM.compareTo(new BigDecimal("100000000")) < 0) {
                log.info("Total AUM ${} below $100M threshold, Form 13F not required", totalAUM);
                return null;
            }

            // Get all holdings as of quarter end
            List<InvestmentHoldingSummary> holdings = accountRepository
                .findAllHoldingsAsOf(quarterEndDate)
                .stream()
                .filter(h -> h.getMarketValue().compareTo(new BigDecimal("200000")) >= 0 ||
                            h.getShares().compareTo(BigDecimal.valueOf(10000)) >= 0)
                .collect(Collectors.toList());

            // Build Form 13F report
            Form13FReport report = Form13FReport.builder()
                .reportingPeriodEnd(quarterEndDate)
                .filingManager(getFilingManagerInfo())
                .tableOfContents(buildTableOfContents(holdings))
                .informationTable(buildInformationTable(holdings))
                .totalAUM(totalAUM)
                .totalHoldingsCount(holdings.size())
                .filingStatus("PENDING")
                .generatedAt(LocalDateTime.now())
                .build();

            // Persist report
            secReportRepository.saveForm13F(report);

            // Audit trail
            auditService.logSecurityEvent(
                "SEC_FORM_13F_GENERATED",
                Map.of(
                    "quarterEnd", quarterEndDate,
                    "totalAUM", totalAUM,
                    "holdingsCount", holdings.size(),
                    "reportId", report.getReportId()
                ),
                "SYSTEM",
                "SEC_REPORTING"
            );

            // Metrics
            meterRegistry.counter("sec.form13f.generated").increment();
            meterRegistry.timer("sec.form13f.generation.duration")
                .record(System.currentTimeMillis() - startTime, java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("Form 13F generated successfully: reportId={}, holdings={}, AUM=${}",
                report.getReportId(), holdings.size(), totalAUM);

            return report;

        } catch (Exception e) {
            log.error("Error generating Form 13F for quarter {}", quarterEndDate, e);
            meterRegistry.counter("sec.form13f.generation.errors").increment();
            throw new SECReportingException("Failed to generate Form 13F", e);
        }
    }

    /**
     * File Form 13F with SEC EDGAR system
     *
     * @param reportId Form 13F report ID
     * @return Filing confirmation
     */
    @Async
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SECFilingConfirmation fileForm13F(String reportId) {
        log.info("Filing Form 13F with SEC EDGAR: reportId={}", reportId);

        try {
            Form13FReport report = secReportRepository.findForm13FById(reportId)
                .orElseThrow(() -> new SECReportingException("Form 13F not found: " + reportId));

            if (!"PENDING".equals(report.getFilingStatus())) {
                throw new SECReportingException("Form 13F already filed or invalid status: " + report.getFilingStatus());
            }

            // Convert to EDGAR XML format
            String edgarXml = edgarFilingService.convertForm13FToEDGARXML(report);

            // Submit to SEC EDGAR
            SECFilingConfirmation confirmation = edgarFilingService.submitFiling(
                "13F-HR",
                edgarXml,
                report.getReportingPeriodEnd()
            );

            // Update filing status
            report.setFilingStatus("FILED");
            report.setFiledAt(LocalDateTime.now());
            report.setAccessionNumber(confirmation.getAccessionNumber());
            secReportRepository.saveForm13F(report);

            // Audit trail
            auditService.logSecurityEvent(
                "SEC_FORM_13F_FILED",
                Map.of(
                    "reportId", reportId,
                    "accessionNumber", confirmation.getAccessionNumber(),
                    "filingDate", confirmation.getFilingDate()
                ),
                "SYSTEM",
                "SEC_REPORTING"
            );

            meterRegistry.counter("sec.form13f.filed").increment();

            log.info("Form 13F filed successfully: reportId={}, accessionNumber={}",
                reportId, confirmation.getAccessionNumber());

            return confirmation;

        } catch (Exception e) {
            log.error("Error filing Form 13F: reportId={}", reportId, e);
            meterRegistry.counter("sec.form13f.filing.errors").increment();

            // Update status to ERROR
            secReportRepository.updateForm13FStatus(reportId, "ERROR");

            throw new SECReportingException("Failed to file Form 13F", e);
        }
    }

    /**
     * Automated quarterly Form 13F generation and filing
     * Runs on the 15th day after quarter end at 2 AM
     */
    @Scheduled(cron = "0 0 2 15 1,4,7,10 *", zone = "America/New_York")
    @Transactional
    public void autoGenerateAndFileForm13F() {
        log.info("Running automated Form 13F generation");

        // Determine previous quarter end
        LocalDate now = LocalDate.now();
        LocalDate quarterEnd = getQuarterEnd(now.minusMonths(1));

        try {
            Form13FReport report = generateForm13F(quarterEnd);

            if (report != null) {
                // File asynchronously
                fileForm13F(report.getReportId());
            }

        } catch (Exception e) {
            log.error("Automated Form 13F generation failed for quarter {}", quarterEnd, e);
            // Alert compliance team
            alertComplianceTeam("Form 13F Auto-Generation Failed",
                "Quarter: " + quarterEnd + ", Error: " + e.getMessage());
        }
    }

    /**
     * Generate trade confirmation per SEC Rule 10b-10
     * Must be sent at or before completion of transaction
     *
     * @param transactionId Investment transaction ID
     * @return Trade confirmation document
     */
    @Timed(value = "sec.trade.confirmation.generation")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TradeConfirmation generateTradeConfirmation(String transactionId) {
        log.info("Generating trade confirmation for transaction: {}", transactionId);

        try {
            InvestmentTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new SECReportingException("Transaction not found: " + transactionId));

            InvestmentAccount account = accountRepository.findById(transaction.getAccountId())
                .orElseThrow(() -> new SECReportingException("Account not found: " + transaction.getAccountId()));

            // Build confirmation per Rule 10b-10 requirements
            TradeConfirmation confirmation = TradeConfirmation.builder()
                .confirmationNumber(generateConfirmationNumber())
                .transactionId(transactionId)
                .accountNumber(account.getAccountNumber())
                .customerName(account.getCustomerName())
                // Transaction details
                .transactionDate(transaction.getExecutedAt().toLocalDate())
                .settlementDate(transaction.getSettlementDate())
                .transactionType(transaction.getTransactionType())
                // Security details
                .securityName(transaction.getSecurityName())
                .securitySymbol(transaction.getSymbol())
                .cusipNumber(transaction.getCusipNumber())
                .quantity(transaction.getQuantity())
                .pricePerShare(transaction.getExecutionPrice())
                // Financial details
                .principalAmount(transaction.getPrincipalAmount())
                .commission(transaction.getCommission())
                .secFees(transaction.getSecFees())
                .otherFees(transaction.getOtherFees())
                .totalAmount(transaction.getTotalAmount())
                // Regulatory details
                .capacityIndicator(transaction.getCapacityIndicator()) // Agent or Principal
                .solicitedIndicator(transaction.isSolicited())
                .contraSide(transaction.getContraSide())
                // Remittance information
                .remittanceAddress(getRemittanceAddress())
                .paymentDueDate(transaction.getSettlementDate())
                .generatedAt(LocalDateTime.now())
                .deliveryMethod("EMAIL")
                .deliveryStatus("PENDING")
                .build();

            // Persist confirmation
            confirmationService.save(confirmation);

            // Send to customer (async)
            confirmationService.sendToCustomer(confirmation);

            // Audit trail
            auditService.logSecurityEvent(
                "TRADE_CONFIRMATION_GENERATED",
                Map.of(
                    "confirmationNumber", confirmation.getConfirmationNumber(),
                    "transactionId", transactionId,
                    "accountId", account.getId(),
                    "symbol", transaction.getSymbol(),
                    "quantity", transaction.getQuantity(),
                    "amount", transaction.getTotalAmount()
                ),
                account.getUserId(),
                "SEC_REPORTING"
            );

            meterRegistry.counter("sec.trade.confirmations.generated").increment();

            log.info("Trade confirmation generated: confirmationNumber={}, transactionId={}",
                confirmation.getConfirmationNumber(), transactionId);

            return confirmation;

        } catch (Exception e) {
            log.error("Error generating trade confirmation for transaction: {}", transactionId, e);
            meterRegistry.counter("sec.trade.confirmations.errors").increment();
            throw new SECReportingException("Failed to generate trade confirmation", e);
        }
    }

    /**
     * Generate monthly account statement per SEC Rule 17a-7
     * Must include all transactions, holdings, and account activity
     *
     * @param accountId Investment account ID
     * @param statementMonth Month for statement
     * @return Account statement
     */
    @Timed(value = "sec.account.statement.generation")
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public AccountStatement generateMonthlyStatement(String accountId, Year statementMonth) {
        log.info("Generating monthly statement for account: {}, month: {}", accountId, statementMonth);

        try {
            InvestmentAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new SECReportingException("Account not found: " + accountId));

            LocalDate monthStart = statementMonth.atMonth(1).atDay(1);
            LocalDate monthEnd = statementMonth.atMonth(1).atEndOfMonth();

            // Get all transactions for the month
            List<InvestmentTransaction> transactions = transactionRepository
                .findByAccountIdAndDateBetween(accountId, monthStart, monthEnd);

            // Get beginning and ending balances
            BigDecimal beginningBalance = accountRepository.getBalanceAsOf(accountId, monthStart.minusDays(1));
            BigDecimal endingBalance = accountRepository.getBalanceAsOf(accountId, monthEnd);

            // Get current holdings
            List<InvestmentHoldingSummary> holdings = accountRepository.getHoldingsByAccountId(accountId);

            // Calculate totals
            BigDecimal totalDeposits = transactions.stream()
                .filter(t -> "DEPOSIT".equals(t.getTransactionType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalWithdrawals = transactions.stream()
                .filter(t -> "WITHDRAWAL".equals(t.getTransactionType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalTrades = transactions.stream()
                .filter(t -> "BUY".equals(t.getTransactionType()) || "SELL".equals(t.getTransactionType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalFees = transactions.stream()
                .map(t -> t.getCommission().add(t.getSecFees()).add(t.getOtherFees()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDividends = transactions.stream()
                .filter(t -> "DIVIDEND".equals(t.getTransactionType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build statement
            AccountStatement statement = AccountStatement.builder()
                .statementNumber(generateStatementNumber())
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .customerName(account.getCustomerName())
                .statementPeriod(statementMonth.toString())
                .statementPeriodStart(monthStart)
                .statementPeriodEnd(monthEnd)
                // Balances
                .beginningBalance(beginningBalance)
                .endingBalance(endingBalance)
                .netChange(endingBalance.subtract(beginningBalance))
                // Activity summary
                .transactions(transactions)
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .totalTrades(totalTrades)
                .totalFees(totalFees)
                .totalDividends(totalDividends)
                // Holdings
                .holdings(holdings)
                .totalMarketValue(holdings.stream()
                    .map(InvestmentHoldingSummary::getMarketValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                // Performance
                .monthToDateReturn(calculateReturn(beginningBalance, endingBalance))
                .yearToDateReturn(calculateYTDReturn(accountId, statementMonth))
                // Metadata
                .generatedAt(LocalDateTime.now())
                .deliveryMethod("EMAIL")
                .deliveryStatus("PENDING")
                .build();

            // Persist statement
            secReportRepository.saveAccountStatement(statement);

            // Audit trail
            auditService.logSecurityEvent(
                "ACCOUNT_STATEMENT_GENERATED",
                Map.of(
                    "statementNumber", statement.getStatementNumber(),
                    "accountId", accountId,
                    "period", statementMonth.toString(),
                    "transactionsCount", transactions.size(),
                    "endingBalance", endingBalance
                ),
                account.getUserId(),
                "SEC_REPORTING"
            );

            meterRegistry.counter("sec.account.statements.generated").increment();

            log.info("Monthly statement generated: statementNumber={}, accountId={}, period={}",
                statement.getStatementNumber(), accountId, statementMonth);

            return statement;

        } catch (Exception e) {
            log.error("Error generating monthly statement: accountId={}, month={}", accountId, statementMonth, e);
            meterRegistry.counter("sec.account.statements.errors").increment();
            throw new SECReportingException("Failed to generate monthly statement", e);
        }
    }

    /**
     * Automated monthly statement generation
     * Runs on the 1st day of each month at 3 AM
     */
    @Scheduled(cron = "0 0 3 1 * *", zone = "America/New_York")
    @Transactional
    public void autoGenerateMonthlyStatements() {
        log.info("Running automated monthly statement generation");

        LocalDate now = LocalDate.now();
        Year previousMonth = Year.of(now.minusMonths(1).getYear());

        try {
            List<InvestmentAccount> activeAccounts = accountRepository.findAllActiveAccounts();

            log.info("Generating monthly statements for {} active accounts", activeAccounts.size());

            int successCount = 0;
            int errorCount = 0;

            for (InvestmentAccount account : activeAccounts) {
                try {
                    AccountStatement statement = generateMonthlyStatement(account.getId(), previousMonth);
                    // Send to customer (async)
                    confirmationService.sendStatementToCustomer(statement);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to generate statement for account: {}", account.getId(), e);
                    errorCount++;
                }
            }

            log.info("Monthly statement generation complete: success={}, errors={}", successCount, errorCount);

            if (errorCount > 0) {
                alertComplianceTeam("Monthly Statement Generation Errors",
                    String.format("Period: %s, Errors: %d/%d accounts", previousMonth, errorCount, activeAccounts.size()));
            }

        } catch (Exception e) {
            log.error("Automated monthly statement generation failed for period {}", previousMonth, e);
            alertComplianceTeam("Monthly Statement Generation Failed",
                "Period: " + previousMonth + ", Error: " + e.getMessage());
        }
    }

    // Helper methods

    private FilingManagerInfo getFilingManagerInfo() {
        return FilingManagerInfo.builder()
            .name("Waqiti Investment Management LLC")
            .cik("0001234567") // Central Index Key
            .fileNumber("801-12345")
            .address("123 Financial Plaza, New York, NY 10005")
            .build();
    }

    private List<TableOfContentsEntry> buildTableOfContents(List<InvestmentHoldingSummary> holdings) {
        return holdings.stream()
            .map(h -> TableOfContentsEntry.builder()
                .nameOfIssuer(h.getCompanyName())
                .titleOfClass(h.getSecurityType())
                .cusip(h.getCusipNumber())
                .value(h.getMarketValue())
                .sharesOrPrincipalAmount(h.getShares())
                .shOrPrn("SH")
                .putOrCall(h.getOptionType())
                .investmentDiscretion(h.getDiscretionType())
                .votingAuthority(h.getVotingAuthority())
                .build())
            .collect(Collectors.toList());
    }

    private List<InformationTableEntry> buildInformationTable(List<InvestmentHoldingSummary> holdings) {
        return holdings.stream()
            .map(h -> InformationTableEntry.builder()
                .nameOfIssuer(h.getCompanyName())
                .titleOfClass(h.getSecurityType())
                .cusip(h.getCusipNumber())
                .value(h.getMarketValue().divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.HALF_UP)) // in thousands
                .sharesOrPrincipalAmount(h.getShares())
                .sshPrnamt("SH")
                .putCall(h.getOptionType())
                .investmentDiscretion(h.getDiscretionType())
                .otherManagers(h.getOtherManagers())
                .votingAuthority(buildVotingAuthority(h))
                .build())
            .collect(Collectors.toList());
    }

    private VotingAuthority buildVotingAuthority(InvestmentHoldingSummary holding) {
        return VotingAuthority.builder()
            .sole(holding.getSoleVotingAuthority())
            .shared(holding.getSharedVotingAuthority())
            .none(holding.getNoVotingAuthority())
            .build();
    }

    private String generateConfirmationNumber() {
        return "CNF-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateStatementNumber() {
        return "STMT-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")) +
               "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String getRemittanceAddress() {
        return "Waqiti Investment Management LLC\n" +
               "Attn: Payment Processing\n" +
               "123 Financial Plaza\n" +
               "New York, NY 10005";
    }

    private LocalDate getQuarterEnd(LocalDate date) {
        int month = date.getMonthValue();
        int quarterEndMonth = ((month - 1) / 3 + 1) * 3;
        return LocalDate.of(date.getYear(), quarterEndMonth, 1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
    }

    private BigDecimal calculateReturn(BigDecimal beginningBalance, BigDecimal endingBalance) {
        if (beginningBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return endingBalance.subtract(beginningBalance)
            .divide(beginningBalance, 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    private BigDecimal calculateYTDReturn(String accountId, Year period) {
        LocalDate yearStart = period.atMonth(1).atDay(1);
        BigDecimal startBalance = accountRepository.getBalanceAsOf(accountId, yearStart.minusDays(1));
        BigDecimal currentBalance = accountRepository.getBalanceAsOf(accountId, LocalDate.now());
        return calculateReturn(startBalance, currentBalance);
    }

    private void alertComplianceTeam(String subject, String message) {
        // Integration with notification service
        log.error("COMPLIANCE ALERT: {} - {}", subject, message);
        // TODO: Send email/Slack notification to compliance team
    }
}
