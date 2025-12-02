package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.domain.TaxTransaction;
import com.waqiti.investment.tax.enums.TransactionType;
import com.waqiti.investment.tax.repository.TaxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wash Sale Detection Service
 *
 * Implements IRS IRC Section 1091 Wash Sale Rule:
 * "A wash sale occurs when you sell or trade stock or securities at a loss
 * and within 30 days before or after the sale you:
 * 1. Buy substantially identical stock or securities,
 * 2. Acquire substantially identical stock or securities in a fully taxable trade,
 * 3. Acquire a contract or option to buy substantially identical stock or securities, or
 * 4. Acquire substantially identical stock for your individual retirement account (IRA)"
 *
 * The wash sale rule applies to the 61-day period: 30 days before + sale day + 30 days after
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WashSaleDetectionService {

    private final TaxTransactionRepository taxTransactionRepository;

    private static final int WASH_SALE_WINDOW_DAYS = 30; // 30 days before and after

    /**
     * Detect wash sales for a specific stock sale transaction
     *
     * @param saleTransaction The sale transaction to check
     * @return WashSaleResult indicating if wash sale occurred
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WashSaleResult detectWashSale(TaxTransaction saleTransaction) {
        // Wash sale rule only applies to losses, not gains
        if (!saleTransaction.hasDisallowableLoss()) {
            log.debug("Transaction {} is not a loss, wash sale rule does not apply",
                saleTransaction.getId());
            return WashSaleResult.noWashSale();
        }

        LocalDate saleDate = saleTransaction.getSaleDate();
        LocalDate windowStart = saleDate.minusDays(WASH_SALE_WINDOW_DAYS);
        LocalDate windowEnd = saleDate.plusDays(WASH_SALE_WINDOW_DAYS);

        log.info("Checking wash sale for transaction {} (symbol={}, sale_date={}, loss={}). " +
                "Window: {} to {}",
            saleTransaction.getId(), saleTransaction.getSymbol(), saleDate,
            saleTransaction.getGainLoss(), windowStart, windowEnd);

        // Find all transactions within the wash sale window
        List<TaxTransaction> windowTransactions = taxTransactionRepository
            .findTransactionsInWashSaleWindow(
                saleTransaction.getUserId(),
                saleTransaction.getSymbol(),
                windowStart,
                windowEnd);

        // Find replacement purchases (purchases after or shortly before the sale)
        List<TaxTransaction> replacementPurchases = new ArrayList<>();

        for (TaxTransaction txn : windowTransactions) {
            // Skip the sale transaction itself
            if (txn.getId().equals(saleTransaction.getId())) {
                continue;
            }

            // Only consider purchases
            if (!txn.isPurchase()) {
                continue;
            }

            // Purchase must be within 30 days before or after the sale
            LocalDate purchaseDate = txn.getAcquisitionDate();
            if (purchaseDate != null &&
                !purchaseDate.isBefore(windowStart) &&
                !purchaseDate.isAfter(windowEnd)) {
                replacementPurchases.add(txn);
            }
        }

        if (replacementPurchases.isEmpty()) {
            log.debug("No replacement purchases found within wash sale window");
            return WashSaleResult.noWashSale();
        }

        // Wash sale detected - calculate disallowed loss
        log.warn("WASH SALE DETECTED for transaction {} - {} replacement purchases found",
            saleTransaction.getId(), replacementPurchases.size());

        return calculateWashSaleLoss(saleTransaction, replacementPurchases);
    }

    /**
     * Calculate the disallowed loss and identify replacement shares
     */
    private WashSaleResult calculateWashSaleLoss(
        TaxTransaction saleTransaction,
        List<TaxTransaction> replacementPurchases) {

        BigDecimal lossAmount = saleTransaction.getGainLoss().abs(); // Loss is negative
        BigDecimal soldQuantity = saleTransaction.getQuantity();

        // Calculate total replacement quantity
        BigDecimal totalReplacementQuantity = replacementPurchases.stream()
            .map(TaxTransaction::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // The disallowed loss is limited to the lesser of:
        // 1. The actual loss
        // 2. The loss proportional to replacement shares
        BigDecimal disallowedLoss;
        if (totalReplacementQuantity.compareTo(soldQuantity) >= 0) {
            // Full replacement - entire loss is disallowed
            disallowedLoss = lossAmount;
        } else {
            // Partial replacement - proportional loss is disallowed
            disallowedLoss = lossAmount
                .multiply(totalReplacementQuantity)
                .divide(soldQuantity, 4, RoundingMode.HALF_UP);
        }

        log.info("Wash sale loss calculation: sold_qty={}, replacement_qty={}, " +
                "loss={}, disallowed_loss={}",
            soldQuantity, totalReplacementQuantity, lossAmount, disallowedLoss);

        return WashSaleResult.builder()
            .isWashSale(true)
            .disallowedLoss(disallowedLoss)
            .replacementTransactions(replacementPurchases)
            .build();
    }

    /**
     * Apply wash sale adjustment to transactions
     * - Disallow loss on the sale transaction
     * - Add disallowed loss to cost basis of replacement shares
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void applyWashSaleAdjustment(
        TaxTransaction saleTransaction,
        WashSaleResult washSaleResult) {

        if (!washSaleResult.isWashSale()) {
            return;
        }

        // Mark sale transaction as wash sale
        TaxTransaction replacementTxn = washSaleResult.getReplacementTransactions().get(0);
        saleTransaction.applyWashSaleRule(
            washSaleResult.getDisallowedLoss(),
            replacementTxn.getId());

        // Recalculate gain/loss (loss is now reduced/eliminated)
        BigDecimal originalGainLoss = saleTransaction.getGainLoss();
        BigDecimal adjustedGainLoss = originalGainLoss.add(washSaleResult.getDisallowedLoss());
        saleTransaction.setGainLoss(adjustedGainLoss);

        taxTransactionRepository.save(saleTransaction);

        // Adjust cost basis of replacement shares
        // The disallowed loss is added to the cost basis of replacement shares
        BigDecimal disallowedLossPerShare = washSaleResult.getDisallowedLoss()
            .divide(replacementTxn.getQuantity(), 4, RoundingMode.HALF_UP);

        for (TaxTransaction replacement : washSaleResult.getReplacementTransactions()) {
            BigDecimal adjustmentAmount = disallowedLossPerShare
                .multiply(replacement.getQuantity());

            replacement.setWashSaleAdjustment(adjustmentAmount);
            replacement.calculateAdjustedCostBasis(); // Recalculate with adjustment

            taxTransactionRepository.save(replacement);

            log.info("Applied wash sale adjustment to replacement transaction {}: " +
                    "adjustment={}, new_adjusted_basis={}",
                replacement.getId(), adjustmentAmount, replacement.getAdjustedCostBasis());
        }

        log.info("Wash sale adjustment complete for sale transaction {}. " +
                "Disallowed loss: {}, Adjusted gain/loss: {}",
            saleTransaction.getId(), washSaleResult.getDisallowedLoss(), adjustedGainLoss);
    }

    /**
     * Batch detect wash sales for all sales in a tax year
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<WashSaleResult> detectWashSalesForTaxYear(UUID userId, Integer taxYear) {
        log.info("Running wash sale detection for user={}, tax_year={}", userId, taxYear);

        // Get all sale transactions for the tax year
        List<TaxTransaction> sales = taxTransactionRepository
            .findByUserIdAndTaxYearAndDeletedFalse(userId, taxYear).stream()
            .filter(TaxTransaction::isSale)
            .filter(TaxTransaction::hasDisallowableLoss) // Only losses
            .toList();

        log.info("Found {} sales with losses to check for wash sales", sales.size());

        List<WashSaleResult> results = new ArrayList<>();

        for (TaxTransaction sale : sales) {
            WashSaleResult result = detectWashSale(sale);
            if (result.isWashSale()) {
                applyWashSaleAdjustment(sale, result);
                results.add(result);
            }
        }

        log.info("Wash sale detection complete. Found {} wash sales out of {} sales",
            results.size(), sales.size());

        return results;
    }

    /**
     * Wash Sale Result DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class WashSaleResult {
        private boolean isWashSale;
        private BigDecimal disallowedLoss;
        private List<TaxTransaction> replacementTransactions;

        public static WashSaleResult noWashSale() {
            return WashSaleResult.builder()
                .isWashSale(false)
                .disallowedLoss(BigDecimal.ZERO)
                .replacementTransactions(List.of())
                .build();
        }
    }
}
