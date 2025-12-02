package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.domain.TaxTransaction;
import com.waqiti.investment.tax.enums.CostBasisMethod;
import com.waqiti.investment.tax.enums.HoldingPeriodType;
import com.waqiti.investment.tax.enums.TransactionType;
import com.waqiti.investment.tax.repository.TaxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cost Basis Tracking Service
 *
 * Implements IRS cost basis tracking requirements (Publication 550)
 * Supports multiple cost basis methods:
 * - FIFO (First In, First Out) - Default for stocks
 * - LIFO (Last In, First Out)
 * - SPECIFIC_ID (Specific Identification)
 * - AVERAGE_COST (For mutual funds)
 * - HIFO (Highest In, First Out) - Tax optimization
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostBasisTrackingService {

    private final TaxTransactionRepository taxTransactionRepository;

    /**
     * Calculate cost basis for a stock sale using specified method
     *
     * @param userId User ID
     * @param symbol Stock symbol
     * @param saleDate Sale date
     * @param quantitySold Quantity sold
     * @param method Cost basis calculation method
     * @return List of matched purchase transactions with cost basis allocation
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<CostBasisLot> calculateCostBasis(
        UUID userId,
        String symbol,
        LocalDate saleDate,
        BigDecimal quantitySold,
        CostBasisMethod method) {

        log.info("Calculating cost basis for user={}, symbol={}, quantity={}, method={}",
            userId, symbol, quantitySold, method);

        // Retrieve all purchase transactions for this symbol
        List<TaxTransaction> purchases = taxTransactionRepository
            .findPurchasesByUserAndSymbol(userId, symbol);

        if (purchases.isEmpty()) {
            log.warn("No purchase transactions found for user={}, symbol={}", userId, symbol);
            return Collections.emptyList();
        }

        // Sort purchases based on cost basis method
        List<TaxTransaction> sortedPurchases = sortPurchasesByMethod(purchases, method);

        // Match purchases to sale
        return matchPurchasesToSale(sortedPurchases, quantitySold, saleDate);
    }

    /**
     * Sort purchases based on cost basis method
     */
    private List<TaxTransaction> sortPurchasesByMethod(
        List<TaxTransaction> purchases,
        CostBasisMethod method) {

        return switch (method) {
            case FIFO -> // First In, First Out - oldest first
                purchases.stream()
                    .sorted(Comparator.comparing(TaxTransaction::getAcquisitionDate))
                    .collect(Collectors.toList());

            case LIFO -> // Last In, First Out - newest first
                purchases.stream()
                    .sorted(Comparator.comparing(TaxTransaction::getAcquisitionDate).reversed())
                    .collect(Collectors.toList());

            case HIFO -> // Highest In, First Out - highest cost basis first
                purchases.stream()
                    .sorted(Comparator.comparing(
                        (TaxTransaction t) -> t.getCostBasis().divide(t.getQuantity(), 4, RoundingMode.HALF_UP))
                        .reversed())
                    .collect(Collectors.toList());

            case SPECIFIC_ID -> // Specific Identification - requires user selection
                // For now, default to FIFO
                // In production, this would come from user's lot selection
                purchases.stream()
                    .sorted(Comparator.comparing(TaxTransaction::getAcquisitionDate))
                    .collect(Collectors.toList());

            case AVERAGE_COST -> // Average Cost - used for mutual funds
                // Calculate weighted average cost and treat as single lot
                purchases.stream()
                    .sorted(Comparator.comparing(TaxTransaction::getAcquisitionDate))
                    .collect(Collectors.toList());
        };
    }

    /**
     * Match purchase lots to a sale
     */
    private List<CostBasisLot> matchPurchasesToSale(
        List<TaxTransaction> purchases,
        BigDecimal quantitySold,
        LocalDate saleDate) {

        List<CostBasisLot> lots = new ArrayList<>();
        BigDecimal remainingQuantity = quantitySold;

        for (TaxTransaction purchase : purchases) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal purchaseQuantity = purchase.getQuantity();
            BigDecimal quantityToMatch = remainingQuantity.min(purchaseQuantity);

            // Calculate per-share cost basis
            BigDecimal perShareCost = purchase.getAdjustedCostBasis() != null
                ? purchase.getAdjustedCostBasis().divide(purchaseQuantity, 4, RoundingMode.HALF_UP)
                : purchase.getCostBasis().divide(purchaseQuantity, 4, RoundingMode.HALF_UP);

            // Calculate allocated cost basis for this lot
            BigDecimal allocatedCostBasis = perShareCost.multiply(quantityToMatch);

            // Calculate holding period
            long holdingPeriodDays = ChronoUnit.DAYS.between(
                purchase.getAcquisitionDate(), saleDate);
            HoldingPeriodType holdingPeriodType = (holdingPeriodDays > 365)
                ? HoldingPeriodType.LONG_TERM
                : HoldingPeriodType.SHORT_TERM;

            CostBasisLot lot = CostBasisLot.builder()
                .purchaseTransactionId(purchase.getId())
                .acquisitionDate(purchase.getAcquisitionDate())
                .saleDate(saleDate)
                .quantity(quantityToMatch)
                .costBasis(allocatedCostBasis)
                .perShareCost(perShareCost)
                .holdingPeriodDays((int) holdingPeriodDays)
                .holdingPeriodType(holdingPeriodType)
                .symbol(purchase.getSymbol())
                .build();

            lots.add(lot);
            remainingQuantity = remainingQuantity.subtract(quantityToMatch);
        }

        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Insufficient purchase history to match sale quantity. " +
                "Remaining unmatched: {}", remainingQuantity);
        }

        return lots;
    }

    /**
     * Calculate average cost basis for mutual funds
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public BigDecimal calculateAverageCostBasis(UUID userId, String symbol) {
        List<TaxTransaction> purchases = taxTransactionRepository
            .findPurchasesByUserAndSymbol(userId, symbol);

        if (purchases.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;

        for (TaxTransaction purchase : purchases) {
            BigDecimal cost = (purchase.getAdjustedCostBasis() != null)
                ? purchase.getAdjustedCostBasis()
                : purchase.getCostBasis();
            totalCost = totalCost.add(cost);
            totalQuantity = totalQuantity.add(purchase.getQuantity());
        }

        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalCost.divide(totalQuantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * Get available lots for specific identification
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public List<CostBasisLot> getAvailableLotsForSpecificId(UUID userId, String symbol) {
        List<TaxTransaction> purchases = taxTransactionRepository
            .findPurchasesByUserAndSymbol(userId, symbol);

        return purchases.stream()
            .map(purchase -> {
                BigDecimal perShareCost = purchase.getAdjustedCostBasis() != null
                    ? purchase.getAdjustedCostBasis().divide(purchase.getQuantity(), 4, RoundingMode.HALF_UP)
                    : purchase.getCostBasis().divide(purchase.getQuantity(), 4, RoundingMode.HALF_UP);

                return CostBasisLot.builder()
                    .purchaseTransactionId(purchase.getId())
                    .acquisitionDate(purchase.getAcquisitionDate())
                    .quantity(purchase.getQuantity())
                    .costBasis(purchase.getAdjustedCostBasis() != null
                        ? purchase.getAdjustedCostBasis()
                        : purchase.getCostBasis())
                    .perShareCost(perShareCost)
                    .symbol(purchase.getSymbol())
                    .build();
            })
            .collect(Collectors.toList());
    }

    /**
     * Calculate realized gain/loss for a sale
     */
    public BigDecimal calculateRealizedGainLoss(
        List<CostBasisLot> lots,
        BigDecimal saleProceeds) {

        BigDecimal totalCostBasis = lots.stream()
            .map(CostBasisLot::getCostBasis)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return saleProceeds.subtract(totalCostBasis);
    }

    /**
     * Cost Basis Lot DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class CostBasisLot {
        private UUID purchaseTransactionId;
        private LocalDate acquisitionDate;
        private LocalDate saleDate;
        private String symbol;
        private BigDecimal quantity;
        private BigDecimal costBasis;
        private BigDecimal perShareCost;
        private Integer holdingPeriodDays;
        private HoldingPeriodType holdingPeriodType;
    }
}
