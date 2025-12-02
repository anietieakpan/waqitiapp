package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.domain.TaxDocument;
import com.waqiti.investment.tax.domain.TaxTransaction;
import com.waqiti.investment.tax.enums.DocumentType;
import com.waqiti.investment.tax.enums.FilingStatus;
import com.waqiti.investment.tax.repository.TaxDocumentRepository;
import com.waqiti.investment.tax.repository.TaxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Form 1099-INT Service
 *
 * Generates IRS Form 1099-INT (Interest Income)
 * for reporting interest paid to account holders
 *
 * Compliance:
 * - IRC Section 6049 (interest income reporting requirements)
 * - IRS Publication 1220 (FIRE specifications)
 * - Reporting threshold: $10 or more in interest
 *
 * Form Structure:
 * - Box 1: Interest income
 * - Box 2: Early withdrawal penalty
 * - Box 3: Interest on U.S. Savings Bonds and Treasury obligations
 * - Box 4: Federal income tax withheld
 * - Box 5: Investment expenses
 * - Box 6: Foreign tax paid
 * - Box 7: Foreign country or U.S. possession
 * - Box 8: Tax-exempt interest
 * - Box 9: Specified private activity bond interest
 * - Box 10: Market discount
 * - Box 11: Bond premium
 * - Box 12: Bond premium on Treasury obligations
 * - Box 13: Bond premium on tax-exempt bond
 * - Box 14: Tax-exempt and tax credit bond CUSIP number
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form1099INTService {

    private final TaxDocumentRepository taxDocumentRepository;
    private final TaxTransactionRepository taxTransactionRepository;

    @Value("${waqiti.tax.payer.tin:XX-XXXXXXX}")
    private String payerTin;

    @Value("${waqiti.tax.payer.name:Waqiti Financial Services}")
    private String payerName;

    @Value("${waqiti.tax.payer.address:123 Waqiti Plaza, New York, NY 10001}")
    private String payerAddress;

    @Value("${waqiti.tax.1099-int-threshold:10.00}")
    private BigDecimal form1099IntThreshold;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Generate Form 1099-INT for a user and tax year
     *
     * @param userId User ID
     * @param investmentAccountId Investment account ID
     * @param taxYear Tax year (e.g., 2024)
     * @param taxpayerInfo Taxpayer information (TIN, name, address)
     * @return Generated TaxDocument
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaxDocument generateForm1099INT(
        UUID userId,
        String investmentAccountId,
        Integer taxYear,
        Form1099BService.TaxpayerInfo taxpayerInfo) {

        log.info("Generating Form 1099-INT for user={}, account={}, tax_year={}",
            userId, investmentAccountId, taxYear);

        // Check if document already exists
        boolean exists = taxDocumentRepository
            .existsByUserIdAndInvestmentAccountIdAndDocumentTypeAndTaxYearAndDeletedFalse(
                userId, investmentAccountId, DocumentType.FORM_1099_INT, taxYear);

        if (exists) {
            log.warn("Form 1099-INT already exists for user={}, tax_year={}. " +
                "Use correction workflow to update.", userId, taxYear);
            throw new IllegalStateException("Form 1099-INT already exists for this tax year");
        }

        // Get all interest-bearing transactions for the tax year
        List<TaxTransaction> interestTransactions = taxTransactionRepository
            .findInterestTransactionsByUserAndTaxYear(userId, taxYear);

        if (interestTransactions.isEmpty()) {
            log.warn("No interest transactions found for user={}, tax_year={}",
                userId, taxYear);
            throw new IllegalStateException("No reportable interest transactions for this tax year");
        }

        // Calculate aggregates
        Form1099INTAggregates aggregates = calculateInterestAggregates(interestTransactions);

        // Check threshold ($10 minimum)
        if (aggregates.getInterestIncome().compareTo(form1099IntThreshold) < 0) {
            log.info("Total interest income ${} below threshold ${} - skipping 1099-INT generation",
                aggregates.getInterestIncome(), form1099IntThreshold);
            throw new IllegalStateException("Interest income below reporting threshold");
        }

        log.info("Found {} interest transactions totaling ${} to report on Form 1099-INT",
            interestTransactions.size(), aggregates.getInterestIncome());

        // Build interest details JSONB
        Map<String, Object> interestDetails = buildInterestDetails(interestTransactions);

        // Generate document number
        String documentNumber = generateDocumentNumber(userId, taxYear, "1099INT");

        // Create Tax Document
        TaxDocument document = TaxDocument.builder()
            .userId(userId)
            .investmentAccountId(investmentAccountId)
            .documentType(DocumentType.FORM_1099_INT)
            .taxYear(taxYear)
            .documentNumber(documentNumber)
            .isCorrected(false)
            // Taxpayer information
            .taxpayerTin(taxpayerInfo.getTin()) // Should be encrypted
            .taxpayerName(taxpayerInfo.getName())
            .taxpayerAddressLine1(taxpayerInfo.getAddressLine1())
            .taxpayerAddressLine2(taxpayerInfo.getAddressLine2())
            .taxpayerCity(taxpayerInfo.getCity())
            .taxpayerState(taxpayerInfo.getState())
            .taxpayerZip(taxpayerInfo.getZip())
            // Payer information (Waqiti)
            .payerTin(payerTin)
            .payerName(payerName)
            .payerAddress(payerAddress)
            // Form 1099-INT specific fields
            .interestIncome(aggregates.getInterestIncome())
            .earlyWithdrawalPenalty(aggregates.getEarlyWithdrawalPenalty())
            .usSavingsBondsInterest(aggregates.getUsSavingsBondsInterest())
            .intFederalTaxWithheld(aggregates.getFederalTaxWithheld())
            .investmentExpenses(aggregates.getInvestmentExpenses())
            .intForeignTaxPaid(aggregates.getForeignTaxPaid())
            .intForeignCountry(aggregates.getForeignCountry())
            .taxExemptInterest(aggregates.getTaxExemptInterest())
            .privateActivityBondInterest(aggregates.getPrivateActivityBondInterest())
            .marketDiscount(aggregates.getMarketDiscount())
            .bondPremium(aggregates.getBondPremium())
            .bondPremiumTreasury(aggregates.getBondPremiumTreasury())
            .bondPremiumTaxExempt(aggregates.getBondPremiumTaxExempt())
            .cusipNumber(aggregates.getCusipNumber())
            .stateTaxWithheld(aggregates.getStateTaxWithheld())
            .stateCode(aggregates.getStateCode())
            .stateIncome(aggregates.getStateIncome())
            // Interest details
            .interestDetails(interestDetails)
            // Filing status
            .generatedAt(LocalDate.now())
            .filingStatus(FilingStatus.GENERATED)
            .build();

        // Save document
        document = taxDocumentRepository.save(document);

        // Mark all transactions as reported
        markTransactionsAsReported(interestTransactions, document.getId(), documentNumber);

        log.info("Form 1099-INT generated successfully: document_id={}, document_number={}, " +
                "interest_income={}",
            document.getId(), documentNumber, aggregates.getInterestIncome());

        return document;
    }

    /**
     * Calculate aggregate interest values for Form 1099-INT
     */
    private Form1099INTAggregates calculateInterestAggregates(List<TaxTransaction> transactions) {
        BigDecimal interestIncome = BigDecimal.ZERO;
        BigDecimal earlyWithdrawalPenalty = BigDecimal.ZERO;
        BigDecimal usSavingsBondsInterest = BigDecimal.ZERO;
        BigDecimal federalTaxWithheld = BigDecimal.ZERO;
        BigDecimal investmentExpenses = BigDecimal.ZERO;
        BigDecimal foreignTaxPaid = BigDecimal.ZERO;
        String foreignCountry = null;
        BigDecimal taxExemptInterest = BigDecimal.ZERO;
        BigDecimal privateActivityBondInterest = BigDecimal.ZERO;
        BigDecimal marketDiscount = BigDecimal.ZERO;
        BigDecimal bondPremium = BigDecimal.ZERO;
        BigDecimal bondPremiumTreasury = BigDecimal.ZERO;
        BigDecimal bondPremiumTaxExempt = BigDecimal.ZERO;
        String cusipNumber = null;
        BigDecimal stateTaxWithheld = BigDecimal.ZERO;
        String stateCode = null;
        BigDecimal stateIncome = BigDecimal.ZERO;

        for (TaxTransaction txn : transactions) {
            BigDecimal amount = txn.getInterestAmount() != null
                ? txn.getInterestAmount()
                : BigDecimal.ZERO;

            // Box 1 - Interest income
            interestIncome = interestIncome.add(amount);

            // Box 2 - Early withdrawal penalty
            if (txn.getEarlyWithdrawalPenalty() != null) {
                earlyWithdrawalPenalty = earlyWithdrawalPenalty.add(txn.getEarlyWithdrawalPenalty());
            }

            // Box 3 - U.S. Savings Bonds and Treasury obligations
            if (txn.getIsUsSavingsBond() || txn.getIsTreasuryObligation()) {
                usSavingsBondsInterest = usSavingsBondsInterest.add(amount);
            }

            // Box 4 - Federal tax withheld
            if (txn.getFederalTaxWithheld() != null) {
                federalTaxWithheld = federalTaxWithheld.add(txn.getFederalTaxWithheld());
            }

            // Box 5 - Investment expenses
            if (txn.getInvestmentExpenses() != null) {
                investmentExpenses = investmentExpenses.add(txn.getInvestmentExpenses());
            }

            // Box 6 - Foreign tax paid
            if (txn.getForeignTaxPaid() != null) {
                foreignTaxPaid = foreignTaxPaid.add(txn.getForeignTaxPaid());
                if (foreignCountry == null && txn.getForeignCountry() != null) {
                    foreignCountry = txn.getForeignCountry();
                }
            }

            // Box 8 - Tax-exempt interest (municipal bonds)
            if (txn.getIsTaxExempt()) {
                taxExemptInterest = taxExemptInterest.add(amount);
            }

            // Box 9 - Private activity bond interest
            if (txn.getIsPrivateActivityBond()) {
                privateActivityBondInterest = privateActivityBondInterest.add(amount);
            }

            // Box 10 - Market discount
            if (txn.getMarketDiscount() != null) {
                marketDiscount = marketDiscount.add(txn.getMarketDiscount());
            }

            // Box 11 - Bond premium
            if (txn.getBondPremium() != null) {
                bondPremium = bondPremium.add(txn.getBondPremium());
            }

            // Box 12 - Bond premium on Treasury obligations
            if (txn.getBondPremiumTreasury() != null) {
                bondPremiumTreasury = bondPremiumTreasury.add(txn.getBondPremiumTreasury());
            }

            // Box 13 - Bond premium on tax-exempt bond
            if (txn.getBondPremiumTaxExempt() != null) {
                bondPremiumTaxExempt = bondPremiumTaxExempt.add(txn.getBondPremiumTaxExempt());
            }

            // Box 14 - CUSIP number
            if (cusipNumber == null && txn.getCusip() != null) {
                cusipNumber = txn.getCusip();
            }

            // State tax withheld
            if (txn.getStateTaxWithheld() != null) {
                stateTaxWithheld = stateTaxWithheld.add(txn.getStateTaxWithheld());
                if (stateCode == null && txn.getStateCode() != null) {
                    stateCode = txn.getStateCode();
                }
            }

            // State income
            if (txn.getStateInterestIncome() != null) {
                stateIncome = stateIncome.add(txn.getStateInterestIncome());
            }
        }

        return Form1099INTAggregates.builder()
            .interestIncome(interestIncome)
            .earlyWithdrawalPenalty(earlyWithdrawalPenalty)
            .usSavingsBondsInterest(usSavingsBondsInterest)
            .federalTaxWithheld(federalTaxWithheld)
            .investmentExpenses(investmentExpenses)
            .foreignTaxPaid(foreignTaxPaid)
            .foreignCountry(foreignCountry)
            .taxExemptInterest(taxExemptInterest)
            .privateActivityBondInterest(privateActivityBondInterest)
            .marketDiscount(marketDiscount)
            .bondPremium(bondPremium)
            .bondPremiumTreasury(bondPremiumTreasury)
            .bondPremiumTaxExempt(bondPremiumTaxExempt)
            .cusipNumber(cusipNumber)
            .stateTaxWithheld(stateTaxWithheld)
            .stateCode(stateCode)
            .stateIncome(stateIncome)
            .build();
    }

    /**
     * Build interest details for JSONB storage
     */
    private Map<String, Object> buildInterestDetails(List<TaxTransaction> transactions) {
        // Group by security type
        Map<String, List<Map<String, Object>>> bySecurityType = new HashMap<>();

        for (TaxTransaction txn : transactions) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("transaction_id", txn.getId().toString());
            detail.put("symbol", txn.getSymbol());
            detail.put("security_name", txn.getSecurityName());
            detail.put("payment_date", txn.getInterestPaymentDate().toString());
            detail.put("amount", txn.getInterestAmount().toPlainString());
            detail.put("is_us_treasury", txn.getIsTreasuryObligation());
            detail.put("is_us_savings_bond", txn.getIsUsSavingsBond());
            detail.put("is_tax_exempt", txn.getIsTaxExempt());
            if (txn.getCusip() != null) {
                detail.put("cusip", txn.getCusip());
            }

            String securityType = determineSecurityType(txn);
            bySecurityType.computeIfAbsent(securityType, k -> new ArrayList<>()).add(detail);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("interest_by_type", bySecurityType);
        result.put("total_payment_count", transactions.size());
        result.put("security_types_count", bySecurityType.size());

        return result;
    }

    /**
     * Determine security type for grouping
     */
    private String determineSecurityType(TaxTransaction txn) {
        if (txn.getIsUsSavingsBond()) {
            return "U.S. Savings Bonds";
        } else if (txn.getIsTreasuryObligation()) {
            return "Treasury Obligations";
        } else if (txn.getIsTaxExempt()) {
            return "Municipal Bonds (Tax-Exempt)";
        } else if (txn.getIsPrivateActivityBond()) {
            return "Private Activity Bonds";
        } else {
            return "Taxable Interest";
        }
    }

    /**
     * Mark transactions as reported on Form 1099-INT
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markTransactionsAsReported(
        List<TaxTransaction> transactions,
        UUID documentId,
        String documentNumber) {

        for (TaxTransaction txn : transactions) {
            txn.markAsReported(documentId, documentNumber);
            taxTransactionRepository.save(txn);
        }

        log.info("Marked {} interest transactions as reported on Form 1099-INT document {}",
            transactions.size(), documentNumber);
    }

    /**
     * Generate unique document number
     */
    private String generateDocumentNumber(UUID userId, Integer taxYear, String formType) {
        return String.format("%s-%d-%s-%s",
            formType,
            taxYear,
            userId.toString().substring(0, 8).toUpperCase(),
            UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    }

    /**
     * Form 1099-INT Aggregates DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class Form1099INTAggregates {
        private BigDecimal interestIncome;
        private BigDecimal earlyWithdrawalPenalty;
        private BigDecimal usSavingsBondsInterest;
        private BigDecimal federalTaxWithheld;
        private BigDecimal investmentExpenses;
        private BigDecimal foreignTaxPaid;
        private String foreignCountry;
        private BigDecimal taxExemptInterest;
        private BigDecimal privateActivityBondInterest;
        private BigDecimal marketDiscount;
        private BigDecimal bondPremium;
        private BigDecimal bondPremiumTreasury;
        private BigDecimal bondPremiumTaxExempt;
        private String cusipNumber;
        private BigDecimal stateTaxWithheld;
        private String stateCode;
        private BigDecimal stateIncome;
    }
}
