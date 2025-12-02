package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.domain.TaxDocument;
import com.waqiti.investment.tax.domain.TaxTransaction;
import com.waqiti.investment.tax.enums.DividendType;
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
import java.util.stream.Collectors;

/**
 * Form 1099-DIV Service
 *
 * Generates IRS Form 1099-DIV (Dividends and Distributions)
 * for reporting dividend income
 *
 * Compliance:
 * - IRC Section 6042 (dividend reporting requirements)
 * - IRC Section 6045 (capital gain distributions)
 * - IRS Publication 1220 (FIRE specifications)
 *
 * Form Structure:
 * - Box 1a: Total ordinary dividends
 * - Box 1b: Qualified dividends (preferential tax rate)
 * - Box 2a: Total capital gain distributions
 * - Box 2b: Unrecaptured Section 1250 gain
 * - Box 2c: Section 1202 gain
 * - Box 2d: Collectibles (28%) gain
 * - Box 2e: Section 897 ordinary dividends
 * - Box 2f: Section 897 capital gain
 * - Box 3: Nondividend distributions (return of capital)
 * - Box 4: Federal income tax withheld
 * - Box 5: Section 199A dividends
 * - Box 6: Investment expenses
 * - Box 7: Foreign tax paid
 * - Box 8: Foreign country
 * - Box 9: Cash liquidation distributions
 * - Box 10: Noncash liquidation distributions
 * - Box 11: Exempt-interest dividends
 * - Box 12: Private activity bond interest dividends
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form1099DIVService {

    private final TaxDocumentRepository taxDocumentRepository;
    private final TaxTransactionRepository taxTransactionRepository;

    @Value("${waqiti.tax.payer.tin:XX-XXXXXXX}")
    private String payerTin;

    @Value("${waqiti.tax.payer.name:Waqiti Financial Services}")
    private String payerName;

    @Value("${waqiti.tax.payer.address:123 Waqiti Plaza, New York, NY 10001}")
    private String payerAddress;

    /**
     * Generate Form 1099-DIV for a user and tax year
     *
     * @param userId User ID
     * @param investmentAccountId Investment account ID
     * @param taxYear Tax year (e.g., 2024)
     * @param taxpayerInfo Taxpayer information (TIN, name, address)
     * @return Generated TaxDocument
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaxDocument generateForm1099DIV(
        UUID userId,
        String investmentAccountId,
        Integer taxYear,
        Form1099BService.TaxpayerInfo taxpayerInfo) {

        log.info("Generating Form 1099-DIV for user={}, account={}, tax_year={}",
            userId, investmentAccountId, taxYear);

        // Check if document already exists
        boolean exists = taxDocumentRepository
            .existsByUserIdAndInvestmentAccountIdAndDocumentTypeAndTaxYearAndDeletedFalse(
                userId, investmentAccountId, DocumentType.FORM_1099_DIV, taxYear);

        if (exists) {
            log.warn("Form 1099-DIV already exists for user={}, tax_year={}. " +
                "Use correction workflow to update.", userId, taxYear);
            throw new IllegalStateException("Form 1099-DIV already exists for this tax year");
        }

        // Get all dividend transactions for the tax year
        List<TaxTransaction> dividendTransactions = taxTransactionRepository
            .findDividendTransactionsByUserAndTaxYear(userId, taxYear);

        if (dividendTransactions.isEmpty()) {
            log.warn("No dividend transactions found for user={}, tax_year={}",
                userId, taxYear);
            throw new IllegalStateException("No reportable dividend transactions for this tax year");
        }

        log.info("Found {} dividend transactions to report on Form 1099-DIV",
            dividendTransactions.size());

        // Calculate aggregates
        Form1099DIVAggregates aggregates = calculateDividendAggregates(dividendTransactions);

        // Build dividend details JSONB
        Map<String, Object> dividendDetails = buildDividendDetails(dividendTransactions);

        // Generate document number
        String documentNumber = generateDocumentNumber(userId, taxYear, "1099DIV");

        // Create Tax Document
        TaxDocument document = TaxDocument.builder()
            .userId(userId)
            .investmentAccountId(investmentAccountId)
            .documentType(DocumentType.FORM_1099_DIV)
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
            // Form 1099-DIV specific fields
            .totalOrdinaryDividends(aggregates.getTotalOrdinaryDividends())
            .qualifiedDividends(aggregates.getQualifiedDividends())
            .totalCapitalGainDistributions(aggregates.getTotalCapitalGainDistributions())
            .section1250Gain(aggregates.getSection1250Gain())
            .section1202Gain(aggregates.getSection1202Gain())
            .collectiblesGain(aggregates.getCollectiblesGain())
            .section897Dividends(aggregates.getSection897Dividends())
            .section897CapitalGain(aggregates.getSection897CapitalGain())
            .nondividendDistributions(aggregates.getNondividendDistributions())
            .divFederalTaxWithheld(BigDecimal.ZERO) // Typically not withheld for U.S. citizens
            .section199aDividends(aggregates.getSection199aDividends())
            .investmentExpenses(aggregates.getInvestmentExpenses())
            .foreignTaxPaid(aggregates.getForeignTaxPaid())
            .foreignCountry(aggregates.getForeignCountry())
            .cashLiquidationDistributions(aggregates.getCashLiquidationDistributions())
            .noncashLiquidationDistributions(aggregates.getNoncashLiquidationDistributions())
            .exemptInterestDividends(aggregates.getExemptInterestDividends())
            .privateActivityBondDividends(aggregates.getPrivateActivityBondDividends())
            // Dividend details
            .dividendDetails(dividendDetails)
            // Filing status
            .generatedAt(LocalDate.now())
            .filingStatus(FilingStatus.GENERATED)
            .build();

        // Save document
        document = taxDocumentRepository.save(document);

        // Mark all transactions as reported
        markTransactionsAsReported(dividendTransactions, document.getId(), documentNumber);

        log.info("Form 1099-DIV generated successfully: document_id={}, document_number={}, " +
                "total_ordinary_dividends={}, qualified_dividends={}, capital_gain_distributions={}",
            document.getId(), documentNumber, aggregates.getTotalOrdinaryDividends(),
            aggregates.getQualifiedDividends(), aggregates.getTotalCapitalGainDistributions());

        return document;
    }

    /**
     * Calculate aggregate dividend values for Form 1099-DIV
     */
    private Form1099DIVAggregates calculateDividendAggregates(List<TaxTransaction> transactions) {
        BigDecimal totalOrdinaryDividends = BigDecimal.ZERO;
        BigDecimal qualifiedDividends = BigDecimal.ZERO;
        BigDecimal totalCapitalGainDistributions = BigDecimal.ZERO;
        BigDecimal nondividendDistributions = BigDecimal.ZERO;
        BigDecimal foreignTaxPaid = BigDecimal.ZERO;
        String foreignCountry = null;

        for (TaxTransaction txn : transactions) {
            BigDecimal amount = txn.getDividendAmount() != null
                ? txn.getDividendAmount()
                : BigDecimal.ZERO;

            if (DividendType.ORDINARY.equals(txn.getDividendType())) {
                totalOrdinaryDividends = totalOrdinaryDividends.add(amount);
                // Qualified dividends are a subset of ordinary dividends
                if (txn.getIsQualifiedDividend()) {
                    qualifiedDividends = qualifiedDividends.add(amount);
                }
            } else if (DividendType.QUALIFIED.equals(txn.getDividendType())) {
                totalOrdinaryDividends = totalOrdinaryDividends.add(amount);
                qualifiedDividends = qualifiedDividends.add(amount);
            } else if (DividendType.CAPITAL_GAIN.equals(txn.getDividendType())) {
                totalCapitalGainDistributions = totalCapitalGainDistributions.add(amount);
            } else if (DividendType.RETURN_OF_CAPITAL.equals(txn.getDividendType())) {
                BigDecimal roc = txn.getReturnOfCapital() != null
                    ? txn.getReturnOfCapital()
                    : amount;
                nondividendDistributions = nondividendDistributions.add(roc);
            }

            // Foreign tax paid
            if (txn.getForeignTaxPaid() != null) {
                foreignTaxPaid = foreignTaxPaid.add(txn.getForeignTaxPaid());
                if (foreignCountry == null && txn.getForeignCountry() != null) {
                    foreignCountry = txn.getForeignCountry();
                }
            }
        }

        return Form1099DIVAggregates.builder()
            .totalOrdinaryDividends(totalOrdinaryDividends)
            .qualifiedDividends(qualifiedDividends)
            .totalCapitalGainDistributions(totalCapitalGainDistributions)
            .section1250Gain(BigDecimal.ZERO) // Would need specific transaction data
            .section1202Gain(BigDecimal.ZERO) // Would need specific transaction data
            .collectiblesGain(BigDecimal.ZERO) // Would need specific transaction data
            .section897Dividends(BigDecimal.ZERO) // REIT/RIC foreign investment dividends
            .section897CapitalGain(BigDecimal.ZERO) // REIT/RIC foreign investment gains
            .nondividendDistributions(nondividendDistributions)
            .section199aDividends(BigDecimal.ZERO) // Qualified business income dividends
            .investmentExpenses(BigDecimal.ZERO) // Investment expenses from funds
            .foreignTaxPaid(foreignTaxPaid)
            .foreignCountry(foreignCountry)
            .cashLiquidationDistributions(BigDecimal.ZERO) // Liquidating distributions
            .noncashLiquidationDistributions(BigDecimal.ZERO) // Liquidating distributions
            .exemptInterestDividends(BigDecimal.ZERO) // Municipal bond fund dividends
            .privateActivityBondDividends(BigDecimal.ZERO) // Private activity bonds
            .build();
    }

    /**
     * Build dividend details for JSONB storage
     */
    private Map<String, Object> buildDividendDetails(List<TaxTransaction> transactions) {
        // Group by symbol for better organization
        Map<String, List<Map<String, Object>>> bySymbol = new HashMap<>();

        for (TaxTransaction txn : transactions) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("transaction_id", txn.getId().toString());
            detail.put("symbol", txn.getSymbol());
            detail.put("security_name", txn.getSecurityName());
            detail.put("payment_date", txn.getDividendPaymentDate().toString());
            detail.put("ex_date", txn.getDividendExDate() != null
                ? txn.getDividendExDate().toString() : null);
            detail.put("amount", txn.getDividendAmount().toPlainString());
            detail.put("dividend_type", txn.getDividendType().name());
            detail.put("is_qualified", txn.getIsQualifiedDividend());
            if (txn.getForeignTaxPaid() != null) {
                detail.put("foreign_tax_paid", txn.getForeignTaxPaid().toPlainString());
                detail.put("foreign_country", txn.getForeignCountry());
            }

            bySymbol.computeIfAbsent(txn.getSymbol(), k -> new ArrayList<>()).add(detail);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dividends_by_symbol", bySymbol);
        result.put("total_dividend_count", transactions.size());
        result.put("symbols_count", bySymbol.size());

        return result;
    }

    /**
     * Mark transactions as reported on Form 1099-DIV
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

        log.info("Marked {} dividend transactions as reported on Form 1099-DIV document {}",
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
     * Form 1099-DIV Aggregates DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class Form1099DIVAggregates {
        private BigDecimal totalOrdinaryDividends;
        private BigDecimal qualifiedDividends;
        private BigDecimal totalCapitalGainDistributions;
        private BigDecimal section1250Gain;
        private BigDecimal section1202Gain;
        private BigDecimal collectiblesGain;
        private BigDecimal section897Dividends;
        private BigDecimal section897CapitalGain;
        private BigDecimal nondividendDistributions;
        private BigDecimal section199aDividends;
        private BigDecimal investmentExpenses;
        private BigDecimal foreignTaxPaid;
        private String foreignCountry;
        private BigDecimal cashLiquidationDistributions;
        private BigDecimal noncashLiquidationDistributions;
        private BigDecimal exemptInterestDividends;
        private BigDecimal privateActivityBondDividends;
    }
}
