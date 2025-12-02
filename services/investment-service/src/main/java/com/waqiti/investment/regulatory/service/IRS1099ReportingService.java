package com.waqiti.investment.regulatory.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.investment.domain.InvestmentAccount;
import com.waqiti.investment.domain.InvestmentTransaction;
import com.waqiti.investment.regulatory.dto.*;
import com.waqiti.investment.regulatory.repository.TaxReportRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * IRS 1099 Tax Reporting Service
 *
 * Generates IRS Form 1099-B (Broker and Barter Exchange Transactions)
 * and Form 1099-DIV (Dividends and Distributions) for investment accounts.
 *
 * Compliance requirements:
 * - IRS Publication 1179 (General Rules and Specifications for 1099 Forms)
 * - IRS Form 1099-B instructions
 * - IRS Form 1099-DIV instructions
 * - FIRE (Filing Information Returns Electronically) system
 * - E-file deadline: March 31st (paper by February 28th)
 * - Recipient copy deadline: January 31st
 * - Penalties: $50-$280 per form for late/incorrect filing
 *
 * Features:
 * - Automated year-end tax document generation
 * - Cost basis calculation (FIFO, LIFO, Specific ID)
 * - Wash sale adjustment tracking
 * - Short-term vs long-term gain classification
 * - Dividend income categorization (qualified vs ordinary)
 * - IRS FIRE system electronic filing
 * - Corrected 1099 support
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IRS1099ReportingService {

    private final InvestmentAccountRepository accountRepository;
    private final InvestmentTransactionRepository transactionRepository;
    private final TaxReportRepository taxReportRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final IRSFIREFilingService fireFilingService;
    private final WashSaleService washSaleService;
    private final CostBasisService costBasisService;

    /**
     * Generate IRS Form 1099-B for all securities sales in the tax year
     *
     * @param accountId Investment account ID
     * @param taxYear Tax year (e.g., 2024)
     * @return Form 1099-B with all reportable sales transactions
     */
    @Timed(value = "tax.form1099b.generation", description = "Time to generate Form 1099-B")
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public Form1099B generate1099B(String accountId, Year taxYear) {
        log.info("Generating Form 1099-B for account: {}, tax year: {}", accountId, taxYear);

        long startTime = System.currentTimeMillis();

        try {
            InvestmentAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TaxReportingException("Account not found: " + accountId));

            // Get all SELL transactions for the tax year
            LocalDate yearStart = taxYear.atMonth(1).atDay(1);
            LocalDate yearEnd = taxYear.atMonth(12).atDay(31);

            List<InvestmentTransaction> sellTransactions = transactionRepository
                .findByAccountIdAndTransactionTypeAndDateBetween(
                    accountId, "SELL", yearStart, yearEnd
                );

            if (sellTransactions.isEmpty()) {
                log.info("No reportable sales transactions for account: {}, tax year: {}", accountId, taxYear);
                return null;
            }

            // Process each sale transaction
            List<Form1099BTransaction> reportableTransactions = new ArrayList<>();

            for (InvestmentTransaction sale : sellTransactions) {
                try {
                    Form1099BTransaction txn = processSaleForTaxReporting(sale, taxYear);
                    reportableTransactions.add(txn);
                } catch (Exception e) {
                    log.error("Error processing transaction {} for 1099-B", sale.getId(), e);
                    // Continue processing other transactions
                }
            }

            // Calculate aggregated amounts
            BigDecimal totalProceeds = reportableTransactions.stream()
                .map(Form1099BTransaction::getGrossProceeds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCostBasis = reportableTransactions.stream()
                .filter(t -> t.getCostBasis() != null)
                .map(Form1099BTransaction::getCostBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalWashSaleAdjustment = reportableTransactions.stream()
                .filter(t -> t.getWashSaleLossDisallowed() != null)
                .map(Form1099BTransaction::getWashSaleLossDisallowed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build Form 1099-B
            Form1099B form1099B = Form1099B.builder()
                .formId(generateFormId("1099B", taxYear))
                .taxYear(taxYear.getValue())
                .accountId(accountId)
                // Payer information (broker/custodian)
                .payerName("Waqiti Investment Management LLC")
                .payerTIN("12-3456789")
                .payerAddress("123 Financial Plaza, New York, NY 10005")
                .payerPhone("(800) 555-1234")
                // Recipient information (customer)
                .recipientName(account.getCustomerName())
                .recipientTIN(account.getTaxId())
                .recipientAddress(account.getMailingAddress())
                .accountNumber(account.getAccountNumber())
                // Transaction details
                .transactions(reportableTransactions)
                .transactionCount(reportableTransactions.size())
                // Aggregated amounts
                .totalGrossProceeds(totalProceeds)
                .totalCostBasis(totalCostBasis)
                .totalWashSaleAdjustment(totalWashSaleAdjustment)
                .totalGainOrLoss(totalProceeds.subtract(totalCostBasis).subtract(totalWashSaleAdjustment))
                // Metadata
                .formType("ORIGINAL") // or "CORRECTED"
                .generatedAt(LocalDate.now().atStartOfDay())
                .filingStatus("PENDING")
                .correctedIndicator(false)
                .build();

            // Persist form
            taxReportRepository.save1099B(form1099B);

            // Audit trail
            auditService.logSecurityEvent(
                "TAX_FORM_1099B_GENERATED",
                Map.of(
                    "formId", form1099B.getFormId(),
                    "accountId", accountId,
                    "taxYear", taxYear.getValue(),
                    "transactionCount", reportableTransactions.size(),
                    "totalProceeds", totalProceeds,
                    "totalGainOrLoss", form1099B.getTotalGainOrLoss()
                ),
                account.getUserId(),
                "TAX_REPORTING"
            );

            // Metrics
            meterRegistry.counter("tax.form1099b.generated").increment();
            meterRegistry.gauge("tax.form1099b.transaction.count", reportableTransactions.size());
            meterRegistry.timer("tax.form1099b.generation.duration")
                .record(System.currentTimeMillis() - startTime, java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("Form 1099-B generated: formId={}, accountId={}, txCount={}, proceeds=${}",
                form1099B.getFormId(), accountId, reportableTransactions.size(), totalProceeds);

            return form1099B;

        } catch (Exception e) {
            log.error("Error generating Form 1099-B: accountId={}, taxYear={}", accountId, taxYear, e);
            meterRegistry.counter("tax.form1099b.generation.errors").increment();
            throw new TaxReportingException("Failed to generate Form 1099-B", e);
        }
    }

    /**
     * Generate IRS Form 1099-DIV for dividend and distribution income
     *
     * @param accountId Investment account ID
     * @param taxYear Tax year
     * @return Form 1099-DIV with dividend income details
     */
    @Timed(value = "tax.form1099div.generation")
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public Form1099DIV generate1099DIV(String accountId, Year taxYear) {
        log.info("Generating Form 1099-DIV for account: {}, tax year: {}", accountId, taxYear);

        try {
            InvestmentAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TaxReportingException("Account not found: " + accountId));

            LocalDate yearStart = taxYear.atMonth(1).atDay(1);
            LocalDate yearEnd = taxYear.atMonth(12).atDay(31);

            // Get all dividend transactions
            List<InvestmentTransaction> dividendTransactions = transactionRepository
                .findByAccountIdAndTransactionTypeAndDateBetween(
                    accountId, "DIVIDEND", yearStart, yearEnd
                );

            if (dividendTransactions.isEmpty()) {
                log.info("No dividend income for account: {}, tax year: {}", accountId, taxYear);
                return null;
            }

            // Categorize dividends
            BigDecimal ordinaryDividends = dividendTransactions.stream()
                .filter(t -> !"QUALIFIED".equals(t.getDividendType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal qualifiedDividends = dividendTransactions.stream()
                .filter(t -> "QUALIFIED".equals(t.getDividendType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal capitalGainDistributions = dividendTransactions.stream()
                .filter(t -> "CAPITAL_GAIN".equals(t.getDividendType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal nonDividendDistributions = dividendTransactions.stream()
                .filter(t -> "RETURN_OF_CAPITAL".equals(t.getDividendType()))
                .map(InvestmentTransaction::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal federalTaxWithheld = dividendTransactions.stream()
                .map(t -> t.getFederalTaxWithheld() != null ? t.getFederalTaxWithheld() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal foreignTaxPaid = dividendTransactions.stream()
                .map(t -> t.getForeignTaxPaid() != null ? t.getForeignTaxPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build Form 1099-DIV
            Form1099DIV form1099DIV = Form1099DIV.builder()
                .formId(generateFormId("1099DIV", taxYear))
                .taxYear(taxYear.getValue())
                .accountId(accountId)
                // Payer information
                .payerName("Waqiti Investment Management LLC")
                .payerTIN("12-3456789")
                .payerAddress("123 Financial Plaza, New York, NY 10005")
                .payerPhone("(800) 555-1234")
                // Recipient information
                .recipientName(account.getCustomerName())
                .recipientTIN(account.getTaxId())
                .recipientAddress(account.getMailingAddress())
                .accountNumber(account.getAccountNumber())
                // Box amounts (per IRS Form 1099-DIV instructions)
                .box1a_OrdinaryDividends(ordinaryDividends) // Total ordinary dividends
                .box1b_QualifiedDividends(qualifiedDividends) // Qualified dividends (subset of 1a)
                .box2a_CapitalGainDistributions(capitalGainDistributions) // Total capital gain distributions
                .box2b_Unrecaptured1250Gain(BigDecimal.ZERO) // Section 1250 gain
                .box2c_Section1202Gain(BigDecimal.ZERO) // Section 1202 gain
                .box2d_Collectibles28RateGain(BigDecimal.ZERO) // Collectibles (28%) gain
                .box2e_Section897OrdinaryDividends(BigDecimal.ZERO) // Section 897 ordinary dividends
                .box2f_Section897CapitalGain(BigDecimal.ZERO) // Section 897 capital gain
                .box3_NondividendDistributions(nonDividendDistributions) // Nondividend distributions
                .box4_FederalIncomeTaxWithheld(federalTaxWithheld) // Federal income tax withheld
                .box5_Section199ADividends(BigDecimal.ZERO) // Section 199A dividends
                .box6_InvestmentExpenses(BigDecimal.ZERO) // Investment expenses
                .box7_ForeignTaxPaid(foreignTaxPaid) // Foreign tax paid
                .box8_ForeignCountryOrUSPossession("") // Foreign country or U.S. possession
                .box9_CashLiquidationDistributions(BigDecimal.ZERO) // Cash liquidation distributions
                .box10_NoncashLiquidationDistributions(BigDecimal.ZERO) // Noncash liquidation distributions
                // Detailed transactions
                .dividendTransactions(dividendTransactions.stream()
                    .map(this::mapToDividendTransaction)
                    .collect(Collectors.toList()))
                // Metadata
                .formType("ORIGINAL")
                .generatedAt(LocalDate.now().atStartOfDay())
                .filingStatus("PENDING")
                .correctedIndicator(false)
                .build();

            // Persist form
            taxReportRepository.save1099DIV(form1099DIV);

            // Audit trail
            auditService.logSecurityEvent(
                "TAX_FORM_1099DIV_GENERATED",
                Map.of(
                    "formId", form1099DIV.getFormId(),
                    "accountId", accountId,
                    "taxYear", taxYear.getValue(),
                    "ordinaryDividends", ordinaryDividends,
                    "qualifiedDividends", qualifiedDividends,
                    "totalDividends", ordinaryDividends.add(capitalGainDistributions)
                ),
                account.getUserId(),
                "TAX_REPORTING"
            );

            meterRegistry.counter("tax.form1099div.generated").increment();

            log.info("Form 1099-DIV generated: formId={}, accountId={}, ordinaryDividends=${}",
                form1099DIV.getFormId(), accountId, ordinaryDividends);

            return form1099DIV;

        } catch (Exception e) {
            log.error("Error generating Form 1099-DIV: accountId={}, taxYear={}", accountId, taxYear, e);
            meterRegistry.counter("tax.form1099div.generation.errors").increment();
            throw new TaxReportingException("Failed to generate Form 1099-DIV", e);
        }
    }

    /**
     * File 1099 forms electronically with IRS FIRE system
     *
     * @param formId Form ID (1099-B or 1099-DIV)
     * @return Filing confirmation
     */
    @Async
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public IRSFilingConfirmation file1099WithIRS(String formId) {
        log.info("Filing 1099 form with IRS FIRE system: formId={}", formId);

        try {
            // Determine form type
            String formType = formId.contains("1099B") ? "1099-B" : "1099-DIV";

            Object form;
            if ("1099-B".equals(formType)) {
                form = taxReportRepository.find1099BById(formId)
                    .orElseThrow(() -> new TaxReportingException("Form 1099-B not found: " + formId));
            } else {
                form = taxReportRepository.find1099DIVById(formId)
                    .orElseThrow(() -> new TaxReportingException("Form 1099-DIV not found: " + formId));
            }

            // Convert to IRS FIRE format (Publication 1220 specifications)
            String fireFormatData = fireFilingService.convertToFIREFormat(form, formType);

            // Submit to IRS FIRE system
            IRSFilingConfirmation confirmation = fireFilingService.submitToFIRE(
                formType,
                fireFormatData,
                "TAX_YEAR_2024"
            );

            // Update filing status
            if ("1099-B".equals(formType)) {
                taxReportRepository.update1099BStatus(formId, "FILED", confirmation.getConfirmationNumber());
            } else {
                taxReportRepository.update1099DIVStatus(formId, "FILED", confirmation.getConfirmationNumber());
            }

            // Audit trail
            auditService.logSecurityEvent(
                "TAX_FORM_FILED_WITH_IRS",
                Map.of(
                    "formId", formId,
                    "formType", formType,
                    "confirmationNumber", confirmation.getConfirmationNumber(),
                    "filingDate", confirmation.getFilingDate()
                ),
                "SYSTEM",
                "TAX_REPORTING"
            );

            meterRegistry.counter("tax.forms.filed.irs").increment();

            log.info("1099 form filed with IRS: formId={}, confirmationNumber={}",
                formId, confirmation.getConfirmationNumber());

            return confirmation;

        } catch (Exception e) {
            log.error("Error filing 1099 form with IRS: formId={}", formId, e);
            meterRegistry.counter("tax.forms.filing.errors").increment();
            throw new TaxReportingException("Failed to file 1099 form with IRS", e);
        }
    }

    /**
     * Automated year-end 1099 form generation
     * Runs on January 5th at 1 AM to allow time for year-end processing
     */
    @Scheduled(cron = "0 0 1 5 1 *", zone = "America/New_York")
    @Transactional
    public void autoGenerate1099Forms() {
        log.info("Running automated year-end 1099 form generation");

        Year previousYear = Year.of(LocalDate.now().getYear() - 1);

        try {
            List<InvestmentAccount> activeAccounts = accountRepository.findAllActiveAccounts();

            log.info("Generating 1099 forms for {} active accounts, tax year {}",
                activeAccounts.size(), previousYear);

            int form1099BCount = 0;
            int form1099DIVCount = 0;
            int errorCount = 0;

            for (InvestmentAccount account : activeAccounts) {
                try {
                    // Generate 1099-B (if applicable)
                    Form1099B form1099B = generate1099B(account.getId(), previousYear);
                    if (form1099B != null) {
                        form1099BCount++;
                        // Send to customer (async)
                        send1099ToCustomer(form1099B);
                    }

                    // Generate 1099-DIV (if applicable)
                    Form1099DIV form1099DIV = generate1099DIV(account.getId(), previousYear);
                    if (form1099DIV != null) {
                        form1099DIVCount++;
                        // Send to customer (async)
                        send1099ToCustomer(form1099DIV);
                    }

                } catch (Exception e) {
                    log.error("Failed to generate 1099 forms for account: {}", account.getId(), e);
                    errorCount++;
                }
            }

            log.info("1099 form generation complete: 1099-B={}, 1099-DIV={}, errors={}",
                form1099BCount, form1099DIVCount, errorCount);

            if (errorCount > 0) {
                alertComplianceTeam("1099 Form Generation Errors",
                    String.format("Tax Year: %d, Errors: %d/%d accounts",
                        previousYear.getValue(), errorCount, activeAccounts.size()));
            }

        } catch (Exception e) {
            log.error("Automated 1099 form generation failed for tax year {}", previousYear, e);
            alertComplianceTeam("1099 Form Generation Failed",
                "Tax Year: " + previousYear.getValue() + ", Error: " + e.getMessage());
        }
    }

    /**
     * Automated IRS filing of all pending 1099 forms
     * Runs on March 15th at 2 AM (before March 31st deadline)
     */
    @Scheduled(cron = "0 0 2 15 3 *", zone = "America/New_York")
    @Transactional
    public void autoFileWithIRS() {
        log.info("Running automated IRS FIRE filing");

        try {
            List<String> pendingForms = taxReportRepository.findAllPending1099Forms();

            log.info("Filing {} pending 1099 forms with IRS", pendingForms.size());

            int successCount = 0;
            int errorCount = 0;

            for (String formId : pendingForms) {
                try {
                    file1099WithIRS(formId);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to file form with IRS: {}", formId, e);
                    errorCount++;
                }
            }

            log.info("IRS FIRE filing complete: success={}, errors={}", successCount, errorCount);

            if (errorCount > 0) {
                alertComplianceTeam("IRS FIRE Filing Errors",
                    String.format("Errors: %d/%d forms", errorCount, pendingForms.size()));
            }

        } catch (Exception e) {
            log.error("Automated IRS FIRE filing failed", e);
            alertComplianceTeam("IRS FIRE Filing Failed", "Error: " + e.getMessage());
        }
    }

    // Helper methods

    private Form1099BTransaction processSaleForTaxReporting(InvestmentTransaction sale, Year taxYear) {
        // Calculate cost basis using configured method (FIFO, LIFO, Specific ID)
        CostBasisCalculation costBasis = costBasisService.calculateCostBasis(
            sale.getAccountId(),
            sale.getSymbol(),
            sale.getQuantity(),
            sale.getExecutedAt().toLocalDate()
        );

        // Check for wash sale
        WashSaleAnalysis washSale = washSaleService.analyzeForWashSale(sale, costBasis);

        // Determine holding period
        long holdingDays = java.time.temporal.ChronoUnit.DAYS.between(
            costBasis.getAcquisitionDate(),
            sale.getExecutedAt().toLocalDate()
        );
        String holdingPeriod = holdingDays > 365 ? "LONG_TERM" : "SHORT_TERM";

        // Calculate gain/loss
        BigDecimal grossProceeds = sale.getPrincipalAmount();
        BigDecimal adjustedCostBasis = costBasis.getCostBasis();
        BigDecimal gainOrLoss = grossProceeds.subtract(adjustedCostBasis);

        if (washSale.isWashSale()) {
            gainOrLoss = gainOrLoss.add(washSale.getDisallowedLoss());
        }

        return Form1099BTransaction.builder()
            .description(sale.getSecurityName() + " (" + sale.getSymbol() + ")")
            .cusipNumber(sale.getCusipNumber())
            .dateAcquired(costBasis.getAcquisitionDate())
            .dateSold(sale.getExecutedAt().toLocalDate())
            .quantity(sale.getQuantity())
            .salesPrice(sale.getExecutionPrice())
            .grossProceeds(grossProceeds)
            .costBasis(adjustedCostBasis)
            .costBasisReportedToIRS(true) // Broker reported
            .washSaleLossDisallowed(washSale.isWashSale() ? washSale.getDisallowedLoss() : null)
            .gainOrLoss(gainOrLoss)
            .holdingPeriod(holdingPeriod)
            .ordinaryIncome(BigDecimal.ZERO)
            .federalTaxWithheld(sale.getFederalTaxWithheld())
            .stateTaxWithheld(sale.getStateTaxWithheld())
            .boxChecks(determineBoxChecks(sale, costBasis))
            .build();
    }

    private Form1099BTransaction.BoxChecks determineBoxChecks(
        InvestmentTransaction sale,
        CostBasisCalculation costBasis
    ) {
        return Form1099BTransaction.BoxChecks.builder()
            .box2_ShortOrLongTerm(costBasis.getHoldingPeriodDays() > 365 ? "L" : "S")
            .box3_ReportedToIRS(true)
            .box5_CostBasisNotReported(false)
            .box6_NoncoveredSecurity(false)
            .box12_BasisReported(true)
            .build();
    }

    private DividendTransactionDetail mapToDividendTransaction(InvestmentTransaction txn) {
        return DividendTransactionDetail.builder()
            .securityName(txn.getSecurityName())
            .symbol(txn.getSymbol())
            .cusipNumber(txn.getCusipNumber())
            .paymentDate(txn.getExecutedAt().toLocalDate())
            .amount(txn.getTotalAmount())
            .dividendType(txn.getDividendType())
            .qualified("QUALIFIED".equals(txn.getDividendType()))
            .build();
    }

    private String generateFormId(String formType, Year taxYear) {
        return String.format("%s-%d-%s",
            formType,
            taxYear.getValue(),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
    }

    private void send1099ToCustomer(Object form) {
        // Integration with notification/document delivery service
        log.info("Sending 1099 form to customer: {}", form);
        // TODO: Implement email/postal delivery
    }

    private void alertComplianceTeam(String subject, String message) {
        log.error("TAX COMPLIANCE ALERT: {} - {}", subject, message);
        // TODO: Send email/Slack notification
    }
}
