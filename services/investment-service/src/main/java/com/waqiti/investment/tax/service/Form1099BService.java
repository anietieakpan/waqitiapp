package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.domain.TaxDocument;
import com.waqiti.investment.tax.domain.TaxTransaction;
import com.waqiti.investment.tax.enums.DocumentType;
import com.waqiti.investment.tax.enums.FilingStatus;
import com.waqiti.investment.tax.enums.HoldingPeriodType;
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
 * Form 1099-B Service
 *
 * Generates IRS Form 1099-B (Proceeds from Broker and Barter Exchange Transactions)
 * for reporting securities sales
 *
 * Compliance:
 * - IRC Section 6045 (broker reporting requirements)
 * - IRS Publication 1220 (FIRE specifications)
 * - Cost basis reporting (effective 2011+ for covered securities)
 *
 * Form Structure:
 * - Box 1a: Description of property
 * - Box 1b: Date acquired
 * - Box 1c: Date sold
 * - Box 1d: Proceeds (gross)
 * - Box 1e: Cost or other basis
 * - Box 1f: Realized gain/loss
 * - Box 1g: Wash sale loss disallowed
 * - Box 2: Short-term or long-term
 * - Box 3: Covered/non-covered security
 * - Box 4: Federal income tax withheld
 * - Box 5: Check if applicable (ordinary income, collectibles, etc.)
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form1099BService {

    private final TaxDocumentRepository taxDocumentRepository;
    private final TaxTransactionRepository taxTransactionRepository;
    private final WashSaleDetectionService washSaleDetectionService;

    @Value("${waqiti.tax.payer.tin:XX-XXXXXXX}")
    private String payerTin;

    @Value("${waqiti.tax.payer.name:Waqiti Financial Services}")
    private String payerName;

    @Value("${waqiti.tax.payer.address:123 Waqiti Plaza, New York, NY 10001}")
    private String payerAddress;

    /**
     * Generate Form 1099-B for a user and tax year
     *
     * @param userId User ID
     * @param investmentAccountId Investment account ID
     * @param taxYear Tax year (e.g., 2024)
     * @param taxpayerInfo Taxpayer information (TIN, name, address)
     * @return Generated TaxDocument
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaxDocument generateForm1099B(
        UUID userId,
        String investmentAccountId,
        Integer taxYear,
        TaxpayerInfo taxpayerInfo) {

        log.info("Generating Form 1099-B for user={}, account={}, tax_year={}",
            userId, investmentAccountId, taxYear);

        // Check if document already exists
        boolean exists = taxDocumentRepository
            .existsByUserIdAndInvestmentAccountIdAndDocumentTypeAndTaxYearAndDeletedFalse(
                userId, investmentAccountId, DocumentType.FORM_1099_B, taxYear);

        if (exists) {
            log.warn("Form 1099-B already exists for user={}, tax_year={}. " +
                "Use correction workflow to update.", userId, taxYear);
            throw new IllegalStateException("Form 1099-B already exists for this tax year");
        }

        // Run wash sale detection first
        log.info("Running wash sale detection before generating Form 1099-B");
        washSaleDetectionService.detectWashSalesForTaxYear(userId, taxYear);

        // Get all unreported sale transactions for the tax year
        List<TaxTransaction> salesTransactions = taxTransactionRepository
            .findUnreportedTransactionsByUserAndTaxYear(userId, taxYear).stream()
            .filter(TaxTransaction::isSale)
            .collect(Collectors.toList());

        if (salesTransactions.isEmpty()) {
            log.warn("No reportable sale transactions found for user={}, tax_year={}",
                userId, taxYear);
            throw new IllegalStateException("No reportable transactions for this tax year");
        }

        log.info("Found {} sale transactions to report on Form 1099-B", salesTransactions.size());

        // Calculate aggregates
        Form1099BAggregates aggregates = calculateAggregates(salesTransactions);

        // Build transaction details JSONB
        Map<String, Object> transactionDetails = buildTransactionDetails(salesTransactions);

        // Generate document number
        String documentNumber = generateDocumentNumber(userId, taxYear, "1099B");

        // Create Tax Document
        TaxDocument document = TaxDocument.builder()
            .userId(userId)
            .investmentAccountId(investmentAccountId)
            .documentType(DocumentType.FORM_1099_B)
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
            // Form 1099-B specific fields
            .proceedsFromSales(aggregates.getTotalProceeds())
            .costBasis(aggregates.getTotalCostBasis())
            .washSaleLossDisallowed(aggregates.getTotalWashSaleLoss())
            .federalTaxWithheld(BigDecimal.ZERO) // Typically not withheld for regular accounts
            .shortTermCovered(aggregates.hasShortTermCovered())
            .shortTermNotCovered(aggregates.hasShortTermNotCovered())
            .longTermCovered(aggregates.hasLongTermCovered())
            .longTermNotCovered(aggregates.hasLongTermNotCovered())
            .isOrdinaryIncome(aggregates.hasOrdinaryIncome())
            .aggregateProfitLoss(aggregates.getTotalGainLoss())
            // Transaction details
            .transactionDetails(transactionDetails)
            // Filing status
            .generatedAt(LocalDate.now())
            .filingStatus(FilingStatus.GENERATED)
            .build();

        // Save document
        document = taxDocumentRepository.save(document);

        // Mark all transactions as reported
        markTransactionsAsReported(salesTransactions, document.getId(), documentNumber);

        log.info("Form 1099-B generated successfully: document_id={}, document_number={}, " +
                "total_proceeds={}, total_cost_basis={}, aggregate_gain_loss={}",
            document.getId(), documentNumber, aggregates.getTotalProceeds(),
            aggregates.getTotalCostBasis(), aggregates.getTotalGainLoss());

        return document;
    }

    /**
     * Calculate aggregate values for Form 1099-B
     */
    private Form1099BAggregates calculateAggregates(List<TaxTransaction> transactions) {
        BigDecimal totalProceeds = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalGainLoss = BigDecimal.ZERO;
        BigDecimal totalWashSaleLoss = BigDecimal.ZERO;

        boolean hasShortTermCovered = false;
        boolean hasShortTermNotCovered = false;
        boolean hasLongTermCovered = false;
        boolean hasLongTermNotCovered = false;
        boolean hasOrdinaryIncome = false;

        for (TaxTransaction txn : transactions) {
            totalProceeds = totalProceeds.add(
                txn.getNetProceeds() != null ? txn.getNetProceeds() : BigDecimal.ZERO);
            totalCostBasis = totalCostBasis.add(
                txn.getAdjustedCostBasis() != null ? txn.getAdjustedCostBasis() : BigDecimal.ZERO);
            totalGainLoss = totalGainLoss.add(
                txn.getGainLoss() != null ? txn.getGainLoss() : BigDecimal.ZERO);

            if (txn.getIsWashSale() && txn.getWashSaleLossDisallowed() != null) {
                totalWashSaleLoss = totalWashSaleLoss.add(txn.getWashSaleLossDisallowed());
            }

            // Categorize by holding period and coverage
            if (HoldingPeriodType.SHORT_TERM.equals(txn.getHoldingPeriodType())) {
                if (txn.getIsCoveredSecurity()) {
                    hasShortTermCovered = true;
                } else {
                    hasShortTermNotCovered = true;
                }
            } else if (HoldingPeriodType.LONG_TERM.equals(txn.getHoldingPeriodType())) {
                if (txn.getIsCoveredSecurity()) {
                    hasLongTermCovered = true;
                } else {
                    hasLongTermNotCovered = true;
                }
            }

            if (txn.getIsOrdinaryIncome()) {
                hasOrdinaryIncome = true;
            }
        }

        return Form1099BAggregates.builder()
            .totalProceeds(totalProceeds)
            .totalCostBasis(totalCostBasis)
            .totalGainLoss(totalGainLoss)
            .totalWashSaleLoss(totalWashSaleLoss)
            .hasShortTermCovered(hasShortTermCovered)
            .hasShortTermNotCovered(hasShortTermNotCovered)
            .hasLongTermCovered(hasLongTermCovered)
            .hasLongTermNotCovered(hasLongTermNotCovered)
            .hasOrdinaryIncome(hasOrdinaryIncome)
            .build();
    }

    /**
     * Build transaction details for JSONB storage
     */
    private Map<String, Object> buildTransactionDetails(List<TaxTransaction> transactions) {
        List<Map<String, Object>> details = transactions.stream()
            .map(txn -> {
                Map<String, Object> detail = new HashMap<>();
                detail.put("transaction_id", txn.getId().toString());
                detail.put("symbol", txn.getSymbol());
                detail.put("security_name", txn.getSecurityName());
                detail.put("cusip", txn.getCusip());
                detail.put("acquisition_date", txn.getAcquisitionDate().toString());
                detail.put("sale_date", txn.getSaleDate().toString());
                detail.put("quantity", txn.getQuantity().toPlainString());
                detail.put("proceeds", txn.getNetProceeds().toPlainString());
                detail.put("cost_basis", txn.getAdjustedCostBasis().toPlainString());
                detail.put("gain_loss", txn.getGainLoss().toPlainString());
                detail.put("holding_period", txn.getHoldingPeriodType().name());
                detail.put("is_covered", txn.getIsCoveredSecurity());
                detail.put("is_wash_sale", txn.getIsWashSale());
                if (txn.getIsWashSale()) {
                    detail.put("wash_sale_loss_disallowed",
                        txn.getWashSaleLossDisallowed().toPlainString());
                }
                return detail;
            })
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("transactions", details);
        result.put("transaction_count", details.size());

        return result;
    }

    /**
     * Mark transactions as reported on Form 1099-B
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

        log.info("Marked {} transactions as reported on Form 1099-B document {}",
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
     * Taxpayer Information DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class TaxpayerInfo {
        private String tin; // SSN or EIN (should be encrypted)
        private String name;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String zip;
    }

    /**
     * Form 1099-B Aggregates DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class Form1099BAggregates {
        private BigDecimal totalProceeds;
        private BigDecimal totalCostBasis;
        private BigDecimal totalGainLoss;
        private BigDecimal totalWashSaleLoss;
        private boolean hasShortTermCovered;
        private boolean hasShortTermNotCovered;
        private boolean hasLongTermCovered;
        private boolean hasLongTermNotCovered;
        private boolean hasOrdinaryIncome;
    }
}
