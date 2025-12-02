package com.waqiti.tax.service;

import com.waqiti.tax.dto.*;
import com.waqiti.tax.entity.TaxDocument;
import com.waqiti.tax.entity.TaxableTransaction;
import com.waqiti.tax.repository.TaxDocumentRepository;
import com.waqiti.tax.repository.TaxableTransactionRepository;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.exception.TaxGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FORM 1099 GENERATION SERVICE
 *
 * Generates IRS Form 1099 tax documents for customers with taxable income.
 *
 * REGULATORY REQUIREMENT: IRS requires financial institutions to file 1099 forms
 * for customers who earn reportable income (interest, dividends, investment gains).
 *
 * DEADLINE: January 31st following the tax year
 * PENALTY: $50-$280 per form for late/missing filings
 *
 * SUPPORTED FORMS:
 * - 1099-INT: Interest Income (threshold: $10)
 * - 1099-DIV: Dividend Income (threshold: $10)
 * - 1099-B: Broker Transactions (all sales)
 * - 1099-MISC: Miscellaneous Income (threshold: $600)
 *
 * IRS FIRE System Integration:
 * https://www.irs.gov/e-file-providers/filing-information-returns-electronically-fire
 *
 * @author Waqiti Tax Team
 * @version 2.0.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form1099GenerationService {

    private final TaxableTransactionRepository transactionRepository;
    private final TaxDocumentRepository documentRepository;
    private final TaxCalculationService taxCalculationService;
    private final IRSIntegrationService irsIntegrationService;
    private final AuditService auditService;

    // IRS THRESHOLDS
    private static final BigDecimal INTEREST_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal DIVIDEND_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal MISC_THRESHOLD = new BigDecimal("600.00");

    /**
     * Generate all 1099 forms for a specific tax year
     *
     * @param taxYear The tax year (e.g., 2024)
     * @return Summary of generated forms
     */
    @Transactional
    public Form1099GenerationSummary generateAll1099Forms(int taxYear) {
        log.info("FORM1099: Starting generation for tax year {}", taxYear);

        Form1099GenerationSummary summary = new Form1099GenerationSummary();
        summary.setTaxYear(taxYear);
        summary.setGenerationDate(LocalDate.now());

        try {
            // Generate 1099-INT (Interest Income)
            List<Form1099INT> intForms = generate1099INT(taxYear);
            summary.setForm1099INTCount(intForms.size());
            summary.setTotalInterestReported(
                intForms.stream()
                    .map(Form1099INT::getInterestIncome)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            );

            // Generate 1099-DIV (Dividend Income)
            List<Form1099DIV> divForms = generate1099DIV(taxYear);
            summary.setForm1099DIVCount(divForms.size());
            summary.setTotalDividendsReported(
                divForms.stream()
                    .map(Form1099DIV::getTotalOrdinaryDividends)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            );

            // Generate 1099-B (Broker Transactions)
            List<Form1099B> brokerForms = generate1099B(taxYear);
            summary.setForm1099BCount(brokerForms.size());

            // Generate 1099-MISC (Miscellaneous Income)
            List<Form1099MISC> miscForms = generate1099MISC(taxYear);
            summary.setForm1099MISCCount(miscForms.size());
            summary.setTotalMiscIncomeReported(
                miscForms.stream()
                    .map(Form1099MISC::getMiscellaneousIncome)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            );

            summary.setTotalFormsGenerated(
                intForms.size() + divForms.size() + brokerForms.size() + miscForms.size()
            );

            // Save all forms to database
            saveFormsToDatabase(taxYear, intForms, divForms, brokerForms, miscForms);

            summary.setStatus("SUCCESS");
            log.info("FORM1099: Generation complete - {} total forms generated",
                summary.getTotalFormsGenerated());

            // AUDIT: Log form generation
            auditService.logTaxEvent(
                "FORM1099_GENERATION_COMPLETE",
                null,
                Map.of(
                    "taxYear", String.valueOf(taxYear),
                    "totalForms", String.valueOf(summary.getTotalFormsGenerated()),
                    "1099INT", String.valueOf(intForms.size()),
                    "1099DIV", String.valueOf(divForms.size()),
                    "1099B", String.valueOf(brokerForms.size()),
                    "1099MISC", String.valueOf(miscForms.size())
                )
            );

            return summary;

        } catch (Exception e) {
            log.error("FORM1099: Generation failed for tax year {}", taxYear, e);
            summary.setStatus("FAILED");
            summary.setErrorMessage(e.getMessage());

            // AUDIT: Log failure
            auditService.logCriticalError(
                "FORM1099_GENERATION_FAILED",
                "TAX_GENERATION_ERROR",
                "Form 1099 generation failed: " + e.getMessage(),
                Map.of("taxYear", String.valueOf(taxYear))
            );

            throw new TaxGenerationException("Failed to generate 1099 forms", e);
        }
    }

    /**
     * Generate 1099-INT forms (Interest Income)
     * Threshold: $10 or more in interest
     */
    private List<Form1099INT> generate1099INT(int taxYear) {
        log.info("FORM1099: Generating 1099-INT forms for {}", taxYear);

        LocalDate yearStart = LocalDate.of(taxYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(taxYear, 12, 31);

        // Get all interest transactions for the year
        List<TaxableTransaction> interestTransactions = transactionRepository
            .findByTypeAndDateBetween("INTEREST_EARNED", yearStart, yearEnd);

        // Group by customer and calculate total interest
        Map<UUID, BigDecimal> interestByCustomer = interestTransactions.stream()
            .collect(Collectors.groupingBy(
                TaxableTransaction::getCustomerId,
                Collectors.reducing(
                    BigDecimal.ZERO,
                    TaxableTransaction::getAmount,
                    BigDecimal::add
                )
            ));

        // Generate forms for customers meeting threshold
        List<Form1099INT> forms = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : interestByCustomer.entrySet()) {
            UUID customerId = entry.getKey();
            BigDecimal totalInterest = entry.getValue();

            if (totalInterest.compareTo(INTEREST_THRESHOLD) >= 0) {
                Form1099INT form = new Form1099INT();
                form.setTaxYear(taxYear);
                form.setCustomerId(customerId);
                form.setInterestIncome(totalInterest.setScale(2, RoundingMode.HALF_UP));
                form.setEarlyWithdrawalPenalty(BigDecimal.ZERO);
                form.setFederalIncomeTaxWithheld(BigDecimal.ZERO);
                form.setInvestmentExpenses(BigDecimal.ZERO);
                form.setFormId(UUID.randomUUID());
                form.setGeneratedDate(LocalDate.now());

                forms.add(form);

                log.debug("FORM1099-INT: Generated for customer {} - Interest: ${}",
                    customerId, totalInterest);
            }
        }

        log.info("FORM1099-INT: Generated {} forms (threshold: ${})",
            forms.size(), INTEREST_THRESHOLD);
        return forms;
    }

    /**
     * Generate 1099-DIV forms (Dividend Income)
     * Threshold: $10 or more in dividends
     */
    private List<Form1099DIV> generate1099DIV(int taxYear) {
        log.info("FORM1099: Generating 1099-DIV forms for {}", taxYear);

        LocalDate yearStart = LocalDate.of(taxYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(taxYear, 12, 31);

        // Get all dividend transactions
        List<TaxableTransaction> dividendTransactions = transactionRepository
            .findByTypeAndDateBetween("DIVIDEND_INCOME", yearStart, yearEnd);

        // Group by customer and calculate totals
        Map<UUID, DividendSummary> dividendsByCustomer = new HashMap<>();
        for (TaxableTransaction txn : dividendTransactions) {
            UUID customerId = txn.getCustomerId();
            DividendSummary summary = dividendsByCustomer.computeIfAbsent(
                customerId, k -> new DividendSummary()
            );

            // Classify dividend type
            if ("QUALIFIED".equals(txn.getSubType())) {
                summary.qualifiedDividends = summary.qualifiedDividends.add(txn.getAmount());
            }
            summary.ordinaryDividends = summary.ordinaryDividends.add(txn.getAmount());
        }

        // Generate forms for customers meeting threshold
        List<Form1099DIV> forms = new ArrayList<>();
        for (Map.Entry<UUID, DividendSummary> entry : dividendsByCustomer.entrySet()) {
            UUID customerId = entry.getKey();
            DividendSummary summary = entry.getValue();

            if (summary.ordinaryDividends.compareTo(DIVIDEND_THRESHOLD) >= 0) {
                Form1099DIV form = new Form1099DIV();
                form.setTaxYear(taxYear);
                form.setCustomerId(customerId);
                form.setTotalOrdinaryDividends(
                    summary.ordinaryDividends.setScale(2, RoundingMode.HALF_UP)
                );
                form.setQualifiedDividends(
                    summary.qualifiedDividends.setScale(2, RoundingMode.HALF_UP)
                );
                form.setCapitalGainDistributions(BigDecimal.ZERO);
                form.setFederalIncomeTaxWithheld(BigDecimal.ZERO);
                form.setFormId(UUID.randomUUID());
                form.setGeneratedDate(LocalDate.now());

                forms.add(form);

                log.debug("FORM1099-DIV: Generated for customer {} - Dividends: ${} (Qualified: ${})",
                    customerId, summary.ordinaryDividends, summary.qualifiedDividends);
            }
        }

        log.info("FORM1099-DIV: Generated {} forms (threshold: ${})",
            forms.size(), DIVIDEND_THRESHOLD);
        return forms;
    }

    /**
     * Generate 1099-B forms (Broker Transactions)
     * Required for ALL securities sales
     */
    private List<Form1099B> generate1099B(int taxYear) {
        log.info("FORM1099: Generating 1099-B forms for {}", taxYear);

        LocalDate yearStart = LocalDate.of(taxYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(taxYear, 12, 31);

        // Get all securities sale transactions
        List<TaxableTransaction> salesTransactions = transactionRepository
            .findByTypeAndDateBetween("SECURITY_SALE", yearStart, yearEnd);

        List<Form1099B> forms = new ArrayList<>();
        for (TaxableTransaction txn : salesTransactions) {
            Form1099B form = new Form1099B();
            form.setTaxYear(taxYear);
            form.setCustomerId(txn.getCustomerId());
            form.setSecurityDescription(txn.getDescription());
            form.setDateAcquired(txn.getAcquisitionDate());
            form.setDateSold(txn.getTransactionDate());
            form.setSaleProceeds(txn.getAmount().setScale(2, RoundingMode.HALF_UP));
            form.setCostBasis(txn.getCostBasis().setScale(2, RoundingMode.HALF_UP));
            form.setGainOrLoss(
                txn.getAmount().subtract(txn.getCostBasis()).setScale(2, RoundingMode.HALF_UP)
            );
            form.setShortTermOrLongTerm(determineHoldingPeriod(txn));
            form.setFederalIncomeTaxWithheld(BigDecimal.ZERO);
            form.setFormId(UUID.randomUUID());
            form.setGeneratedDate(LocalDate.now());

            forms.add(form);
        }

        log.info("FORM1099-B: Generated {} forms (all securities sales)", forms.size());
        return forms;
    }

    /**
     * Generate 1099-MISC forms (Miscellaneous Income)
     * Threshold: $600 or more
     */
    private List<Form1099MISC> generate1099MISC(int taxYear) {
        log.info("FORM1099: Generating 1099-MISC forms for {}", taxYear);

        LocalDate yearStart = LocalDate.of(taxYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(taxYear, 12, 31);

        // Get miscellaneous income (rewards, referral bonuses, etc.)
        List<TaxableTransaction> miscTransactions = transactionRepository
            .findByTypeAndDateBetween("MISC_INCOME", yearStart, yearEnd);

        // Group by customer
        Map<UUID, BigDecimal> miscByCustomer = miscTransactions.stream()
            .collect(Collectors.groupingBy(
                TaxableTransaction::getCustomerId,
                Collectors.reducing(
                    BigDecimal.ZERO,
                    TaxableTransaction::getAmount,
                    BigDecimal::add
                )
            ));

        // Generate forms for customers meeting threshold
        List<Form1099MISC> forms = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : miscByCustomer.entrySet()) {
            UUID customerId = entry.getKey();
            BigDecimal totalMisc = entry.getValue();

            if (totalMisc.compareTo(MISC_THRESHOLD) >= 0) {
                Form1099MISC form = new Form1099MISC();
                form.setTaxYear(taxYear);
                form.setCustomerId(customerId);
                form.setMiscellaneousIncome(totalMisc.setScale(2, RoundingMode.HALF_UP));
                form.setFederalIncomeTaxWithheld(BigDecimal.ZERO);
                form.setFormId(UUID.randomUUID());
                form.setGeneratedDate(LocalDate.now());

                forms.add(form);

                log.debug("FORM1099-MISC: Generated for customer {} - Misc Income: ${}",
                    customerId, totalMisc);
            }
        }

        log.info("FORM1099-MISC: Generated {} forms (threshold: ${})",
            forms.size(), MISC_THRESHOLD);
        return forms;
    }

    /**
     * Save all generated forms to database
     */
    @Transactional
    private void saveFormsToDatabase(
        int taxYear,
        List<Form1099INT> intForms,
        List<Form1099DIV> divForms,
        List<Form1099B> brokerForms,
        List<Form1099MISC> miscForms) {

        log.info("FORM1099: Saving forms to database - Tax Year {}", taxYear);

        int savedCount = 0;

        // Save 1099-INT forms
        for (Form1099INT form : intForms) {
            TaxDocument doc = convertToTaxDocument(form, "1099-INT");
            documentRepository.save(doc);
            savedCount++;
        }

        // Save 1099-DIV forms
        for (Form1099DIV form : divForms) {
            TaxDocument doc = convertToTaxDocument(form, "1099-DIV");
            documentRepository.save(doc);
            savedCount++;
        }

        // Save 1099-B forms
        for (Form1099B form : brokerForms) {
            TaxDocument doc = convertToTaxDocument(form, "1099-B");
            documentRepository.save(doc);
            savedCount++;
        }

        // Save 1099-MISC forms
        for (Form1099MISC form : miscForms) {
            TaxDocument doc = convertToTaxDocument(form, "1099-MISC");
            documentRepository.save(doc);
            savedCount++;
        }

        log.info("FORM1099: Saved {} tax documents to database", savedCount);
    }

    /**
     * Convert form DTO to TaxDocument entity
     */
    private TaxDocument convertToTaxDocument(Object form, String formType) {
        TaxDocument doc = new TaxDocument();
        doc.setFormType(formType);
        doc.setGeneratedDate(LocalDate.now());
        doc.setStatus("GENERATED");
        // Additional mapping logic here
        return doc;
    }

    /**
     * Determine if security was held short-term or long-term
     */
    private String determineHoldingPeriod(TaxableTransaction txn) {
        if (txn.getAcquisitionDate() == null) {
            return "UNKNOWN";
        }

        long daysHeld = java.time.temporal.ChronoUnit.DAYS.between(
            txn.getAcquisitionDate(), txn.getTransactionDate()
        );

        return daysHeld > 365 ? "LONG_TERM" : "SHORT_TERM";
    }

    /**
     * Helper class for dividend calculation
     */
    private static class DividendSummary {
        BigDecimal ordinaryDividends = BigDecimal.ZERO;
        BigDecimal qualifiedDividends = BigDecimal.ZERO;
    }
}
