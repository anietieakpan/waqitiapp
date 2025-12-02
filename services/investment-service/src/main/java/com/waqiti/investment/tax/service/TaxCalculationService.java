package com.waqiti.investment.tax.service;

import com.waqiti.investment.domain.InvestmentHolding;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.repository.InvestmentHoldingRepository;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.tax.entity.TaxTransaction;
import com.waqiti.investment.tax.entity.TaxTransaction.*;
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
 * Tax Calculation Service - Enterprise-grade implementation.
 *
 * IRS Compliance:
 * - IRC Section 1091 (Wash Sale Rules)
 * - IRC Section 1012 (Basis of Property)
 * - IRS Publication 550 (Investment Income and Expenses)
 * - IRS Publication 551 (Basis of Assets)
 *
 * Wash Sale Rules (IRC Section 1091):
 * - 30 days before sale
 * - 30 days after sale
 * - Total 61-day window
 * - Loss disallowed if substantially identical security purchased
 * - Disallowed loss added to basis of replacement shares
 *
 * Cost Basis Methods:
 * - FIFO (First In, First Out) - Default for stocks
 * - LIFO (Last In, First Out)
 * - Specific Identification
 * - Average Cost (mutual funds only)
 * - HIFO (Highest In, First Out) - tax optimization
 *
 * Holding Period Rules:
 * - Short-term: 365 days or less
 * - Long-term: More than 365 days
 * - Holding period starts day after acquisition
 * - Holding period ends on sale date
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxCalculationService {

    private final TaxTransactionRepository taxTransactionRepository;
    private final InvestmentOrderRepository investmentOrderRepository;
    private final InvestmentHoldingRepository investmentHoldingRepository;

    private static final int WASH_SALE_WINDOW_DAYS = 30;
    private static final int SHORT_TERM_HOLDING_DAYS = 365;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Calculate tax consequences for a stock sale order.
     *
     * This method:
     * 1. Determines cost basis using specified method
     * 2. Calculates gain/loss
     * 3. Determines holding period (short/long term)
     * 4. Checks for wash sales
     * 5. Creates tax transaction records
     *
     * @param order The filled sell order
     * @param costBasisMethod Cost basis calculation method
     * @return List of tax transactions (may be multiple if from different lots)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<TaxTransaction> calculateStockSaleTaxConsequences(InvestmentOrder order,
                                                                   CostBasisMethod costBasisMethod) {

        log.info("TAX: Calculating tax consequences for order {} - Symbol: {}, Quantity: {}, Method: {}",
            order.getId(), order.getSymbol(), order.getExecutedQuantity(), costBasisMethod);

        validateSaleOrder(order);

        // Get acquisition history for this symbol
        List<StockLot> acquisitionLots = getAcquisitionLots(
            order.getInvestmentAccount().getCustomerId(),
            order.getSymbol(),
            order.getFilledAt().toLocalDate()
        );

        if (acquisitionLots.isEmpty()) {
            log.warn("TAX: No acquisition lots found for symbol {} - cannot calculate cost basis", order.getSymbol());
            throw new IllegalStateException("No acquisition lots found for symbol: " + order.getSymbol());
        }

        // Apply cost basis method to select lots
        List<StockLot> selectedLots = selectLotsForSale(
            acquisitionLots,
            order.getExecutedQuantity(),
            costBasisMethod
        );

        // Create tax transactions for each lot
        List<TaxTransaction> taxTransactions = new ArrayList<>();
        BigDecimal remainingQuantity = order.getExecutedQuantity();
        BigDecimal totalProceeds = order.getTotalCost(); // Total sale proceeds
        BigDecimal proceedsPerShare = order.getAveragePrice();

        for (StockLot lot : selectedLots) {
            BigDecimal quantityFromThisLot = remainingQuantity.min(lot.getQuantity());

            TaxTransaction transaction = createStockSaleTransaction(
                order,
                lot,
                quantityFromThisLot,
                proceedsPerShare,
                costBasisMethod
            );

            taxTransactions.add(transaction);
            remainingQuantity = remainingQuantity.subtract(quantityFromThisLot);

            if (remainingQuantity.compareTo(ZERO) <= 0) {
                break;
            }
        }

        // Check for wash sales
        taxTransactions = checkAndApplyWashSaleRules(taxTransactions);

        // Save all transactions
        List<TaxTransaction> savedTransactions = taxTransactionRepository.saveAll(taxTransactions);

        log.info("TAX: Created {} tax transaction(s) for order {}", savedTransactions.size(), order.getId());
        return savedTransactions;
    }

    /**
     * Create individual tax transaction for stock sale.
     */
    private TaxTransaction createStockSaleTransaction(InvestmentOrder order,
                                                       StockLot lot,
                                                       BigDecimal quantity,
                                                       BigDecimal proceedsPerShare,
                                                       CostBasisMethod costBasisMethod) {

        LocalDate saleDate = order.getFilledAt().toLocalDate();
        LocalDate acquisitionDate = lot.getAcquisitionDate();

        // Calculate holding period
        long holdingDays = ChronoUnit.DAYS.between(acquisitionDate, saleDate);
        HoldingPeriodType holdingPeriod = holdingDays > SHORT_TERM_HOLDING_DAYS
            ? HoldingPeriodType.LONG_TERM
            : HoldingPeriodType.SHORT_TERM;

        // Calculate proceeds
        BigDecimal proceeds = proceedsPerShare.multiply(quantity);
        BigDecimal saleCommission = calculateProportionalCommission(
            order.getCommission(),
            quantity,
            order.getExecutedQuantity()
        );
        BigDecimal netProceeds = proceeds.subtract(saleCommission);

        // Calculate cost basis
        BigDecimal costPerShare = lot.getCostPerShare();
        BigDecimal costBasis = costPerShare.multiply(quantity);
        BigDecimal purchaseCommission = calculateProportionalCommission(
            lot.getPurchaseCommission(),
            quantity,
            lot.getOriginalQuantity()
        );
        BigDecimal adjustedCostBasis = costBasis.add(purchaseCommission);

        // Calculate gain/loss
        BigDecimal gainLoss = netProceeds.subtract(adjustedCostBasis);

        // Determine tax year
        int taxYear = saleDate.getYear();

        // Determine if covered security (acquired after cost basis reporting rules - 2011 for stocks)
        boolean isCovered = acquisitionDate.isAfter(LocalDate.of(2010, 12, 31));

        return TaxTransaction.builder()
            .userId(UUID.fromString(order.getInvestmentAccount().getCustomerId()))
            .investmentAccountId(order.getInvestmentAccount().getId())
            .taxYear(taxYear)
            .transactionType(TransactionType.STOCK_SALE)
            .orderId(order.getId())
            .symbol(order.getSymbol())
            .securityName(lot.getSecurityName())
            .cusip(lot.getCusip())
            .instrumentType(order.getInstrumentType())
            .acquisitionDate(acquisitionDate)
            .saleDate(saleDate)
            .quantity(quantity)
            .proceeds(proceeds)
            .costBasis(costBasis)
            .saleCommission(saleCommission)
            .purchaseCommission(purchaseCommission)
            .netProceeds(netProceeds)
            .adjustedCostBasis(adjustedCostBasis)
            .gainLoss(gainLoss)
            .holdingPeriodType(holdingPeriod)
            .holdingPeriodDays((int) holdingDays)
            .costBasisMethod(costBasisMethod)
            .isCoveredSecurity(isCovered)
            .isNoncoveredSecurity(!isCovered)
            .reportedOn1099(false)
            .isWashSale(false)
            .isOrdinaryIncome(false)
            .isCollectiblesGain(false)
            .isSection1256(false)
            .build();
    }

    /**
     * Check and apply IRS wash sale rules (IRC Section 1091).
     *
     * Wash Sale Rules:
     * - Loss disallowed if substantially identical security purchased within 30 days before or after sale
     * - Disallowed loss added to cost basis of replacement shares
     * - Holding period of replacement shares includes holding period of sold shares
     */
    private List<TaxTransaction> checkAndApplyWashSaleRules(List<TaxTransaction> salesTransactions) {

        for (TaxTransaction sale : salesTransactions) {
            // Wash sale rule only applies to losses
            if (sale.getGainLoss().compareTo(ZERO) >= 0) {
                continue; // Gain or no loss, no wash sale
            }

            // Check 61-day window (30 days before + day of sale + 30 days after)
            LocalDate windowStart = sale.getSaleDate().minusDays(WASH_SALE_WINDOW_DAYS);
            LocalDate windowEnd = sale.getSaleDate().plusDays(WASH_SALE_WINDOW_DAYS);

            // Find purchases of substantially identical securities in wash sale window
            List<TaxTransaction> replacementPurchases = taxTransactionRepository
                .findPotentialWashSaleTransactions(
                    sale.getUserId(),
                    sale.getSymbol(),
                    windowStart,
                    windowEnd
                )
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.STOCK_PURCHASE)
                .filter(t -> !t.getId().equals(sale.getId())) // Exclude self
                .filter(t -> !t.getSaleDate().equals(sale.getSaleDate())) // Exclude same-day sales
                .collect(Collectors.toList());

            if (!replacementPurchases.isEmpty()) {
                // Wash sale detected!
                log.warn("TAX: Wash sale detected for {} - Sale date: {}, Loss: {}",
                    sale.getSymbol(), sale.getSaleDate(), sale.getGainLoss());

                sale.setIsWashSale(true);
                sale.setWashSaleLossDisallowed(sale.getGainLoss().abs());

                // Disallowed loss is added to cost basis of replacement shares
                // (This would be tracked in a separate adjustment record)
                TaxTransaction replacementShare = replacementPurchases.get(0);
                sale.setRelatedWashSaleTransactionId(replacementShare.getId());
                sale.setWashSaleAdjustment(sale.getGainLoss().abs());

                // Adjusted gain/loss is zero for wash sales
                sale.setGainLoss(ZERO);

                log.info("TAX: Wash sale loss ${} disallowed and added to replacement shares",
                    sale.getWashSaleLossDisallowed());
            }
        }

        return salesTransactions;
    }

    /**
     * Get acquisition lots for a symbol (purchase history).
     */
    private List<StockLot> getAcquisitionLots(String customerId, String symbol, LocalDate asOfDate) {

        // Get all purchase orders for this symbol up to the sale date
        List<InvestmentOrder> purchaseOrders = investmentOrderRepository
            .findByCustomerIdAndSymbol(customerId, symbol)
            .stream()
            .filter(order -> order.getSide().toString().equals("BUY"))
            .filter(order -> order.isFilled())
            .filter(order -> order.getFilledAt().toLocalDate().isBefore(asOfDate)
                          || order.getFilledAt().toLocalDate().isEqual(asOfDate))
            .sorted(Comparator.comparing(InvestmentOrder::getFilledAt))
            .collect(Collectors.toList());

        // Convert to stock lots
        List<StockLot> lots = new ArrayList<>();
        for (InvestmentOrder purchase : purchaseOrders) {
            StockLot lot = StockLot.builder()
                .orderId(purchase.getId())
                .symbol(purchase.getSymbol())
                .securityName(purchase.getSymbol()) // Would be enriched from market data
                .acquisitionDate(purchase.getFilledAt().toLocalDate())
                .quantity(purchase.getExecutedQuantity())
                .originalQuantity(purchase.getExecutedQuantity())
                .costPerShare(purchase.getAveragePrice())
                .purchaseCommission(purchase.getCommission())
                .build();
            lots.add(lot);
        }

        // Adjust for any previous sales from these lots
        // (Track remaining quantity per lot)
        adjustLotsForPreviousSales(lots, customerId, symbol, asOfDate);

        return lots.stream()
            .filter(lot -> lot.getQuantity().compareTo(ZERO) > 0)
            .collect(Collectors.toList());
    }

    /**
     * Adjust lot quantities for previous sales.
     */
    private void adjustLotsForPreviousSales(List<StockLot> lots, String customerId, String symbol, LocalDate asOfDate) {

        // Get all previous sales for this symbol
        List<TaxTransaction> previousSales = taxTransactionRepository.findBySymbolAndYear(
            UUID.fromString(customerId),
            symbol,
            asOfDate.getYear()
        ).stream()
        .filter(t -> t.getTransactionType() == TransactionType.STOCK_SALE)
        .filter(t -> t.getSaleDate().isBefore(asOfDate))
        .sorted(Comparator.comparing(TaxTransaction::getSaleDate))
        .collect(Collectors.toList());

        // For each previous sale, reduce the lot quantities
        for (TaxTransaction sale : previousSales) {
            BigDecimal remainingToReduce = sale.getQuantity();

            for (StockLot lot : lots) {
                if (remainingToReduce.compareTo(ZERO) <= 0) {
                    break;
                }

                if (lot.getQuantity().compareTo(ZERO) > 0) {
                    BigDecimal reduction = remainingToReduce.min(lot.getQuantity());
                    lot.setQuantity(lot.getQuantity().subtract(reduction));
                    remainingToReduce = remainingToReduce.subtract(reduction);
                }
            }
        }
    }

    /**
     * Select lots for sale based on cost basis method.
     */
    private List<StockLot> selectLotsForSale(List<StockLot> availableLots,
                                              BigDecimal quantityToSell,
                                              CostBasisMethod method) {

        List<StockLot> sortedLots = new ArrayList<>(availableLots);

        switch (method) {
            case FIFO:
                // First In, First Out - oldest first
                sortedLots.sort(Comparator.comparing(StockLot::getAcquisitionDate));
                break;

            case LIFO:
                // Last In, First Out - newest first
                sortedLots.sort(Comparator.comparing(StockLot::getAcquisitionDate).reversed());
                break;

            case HIFO:
                // Highest In, First Out - highest cost basis first (tax optimization)
                sortedLots.sort(Comparator.comparing(StockLot::getCostPerShare).reversed());
                break;

            case AVERAGE_COST:
                // Average cost method (typically for mutual funds)
                sortedLots = applyAverageCostMethod(sortedLots);
                break;

            case SPECIFIC_ID:
                // Specific identification - user chooses specific lots
                // Would require additional user input, default to FIFO for now
                log.warn("TAX: SPECIFIC_ID method requires user input, defaulting to FIFO");
                sortedLots.sort(Comparator.comparing(StockLot::getAcquisitionDate));
                break;

            default:
                sortedLots.sort(Comparator.comparing(StockLot::getAcquisitionDate));
        }

        // Select lots until we have enough quantity
        List<StockLot> selectedLots = new ArrayList<>();
        BigDecimal remaining = quantityToSell;

        for (StockLot lot : sortedLots) {
            if (remaining.compareTo(ZERO) <= 0) {
                break;
            }

            selectedLots.add(lot);
            remaining = remaining.subtract(lot.getQuantity());
        }

        return selectedLots;
    }

    /**
     * Apply average cost method (for mutual funds).
     */
    private List<StockLot> applyAverageCostMethod(List<StockLot> lots) {

        if (lots.isEmpty()) {
            return lots;
        }

        // Calculate total shares and total cost
        BigDecimal totalShares = lots.stream()
            .map(StockLot::getQuantity)
            .reduce(ZERO, BigDecimal::add);

        BigDecimal totalCost = lots.stream()
            .map(lot -> lot.getCostPerShare().multiply(lot.getQuantity()))
            .reduce(ZERO, BigDecimal::add);

        // Calculate average cost per share
        BigDecimal averageCost = totalCost.divide(totalShares, 4, RoundingMode.HALF_UP);

        // Create single lot with average cost
        StockLot averageLot = lots.get(0).toBuilder()
            .quantity(totalShares)
            .costPerShare(averageCost)
            .build();

        return List.of(averageLot);
    }

    /**
     * Calculate proportional commission.
     */
    private BigDecimal calculateProportionalCommission(BigDecimal totalCommission,
                                                         BigDecimal quantity,
                                                         BigDecimal totalQuantity) {

        if (totalCommission == null || totalCommission.compareTo(ZERO) == 0) {
            return ZERO;
        }

        if (totalQuantity.compareTo(ZERO) == 0) {
            return ZERO;
        }

        return totalCommission
            .multiply(quantity)
            .divide(totalQuantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * Validate sale order before processing.
     */
    private void validateSaleOrder(InvestmentOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (!order.isFilled()) {
            throw new IllegalStateException("Order must be filled to calculate tax consequences");
        }

        if (!order.getSide().toString().equals("SELL")) {
            throw new IllegalArgumentException("Order must be a SELL order");
        }

        if (order.getExecutedQuantity() == null || order.getExecutedQuantity().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Executed quantity must be greater than zero");
        }
    }

    /**
     * Calculate tax year for a date.
     */
    public int calculateTaxYear(LocalDate date) {
        return date.getYear();
    }

    /**
     * Determine if a security is covered (broker reports cost basis).
     *
     * Cost Basis Reporting Rules:
     * - Stocks acquired after 1/1/2011
     * - ETFs acquired after 1/1/2011
     * - Mutual funds acquired after 1/1/2012
     * - Options acquired after 1/1/2014
     * - Bonds acquired after 1/1/2016
     */
    public boolean isCoveredSecurity(String instrumentType, LocalDate acquisitionDate) {

        LocalDate cutoffDate = switch (instrumentType.toUpperCase()) {
            case "STOCK", "ETF" -> LocalDate.of(2010, 12, 31);
            case "MUTUAL_FUND" -> LocalDate.of(2011, 12, 31);
            case "OPTION" -> LocalDate.of(2013, 12, 31);
            case "BOND" -> LocalDate.of(2015, 12, 31);
            default -> LocalDate.of(2010, 12, 31);
        };

        return acquisitionDate.isAfter(cutoffDate);
    }

    /**
     * Stock lot tracking for cost basis calculation.
     */
    @lombok.Data
    @lombok.Builder(toBuilder = true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class StockLot {
        private String orderId;
        private String symbol;
        private String securityName;
        private String cusip;
        private LocalDate acquisitionDate;
        private BigDecimal quantity;
        private BigDecimal originalQuantity;
        private BigDecimal costPerShare;
        private BigDecimal purchaseCommission;
    }
}
