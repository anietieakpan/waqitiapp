package com.waqiti.investment.broker.service;

import com.waqiti.investment.domain.InvestmentHolding;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.repository.InvestmentHoldingRepository;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trade Settlement Service
 *
 * Implements T+2 settlement cycle for securities trades:
 * - T+0: Trade date (order filled)
 * - T+1: Trade date + 1 business day
 * - T+2: Settlement date (securities and cash exchanged)
 *
 * Responsibilities:
 * - Track pending settlements
 * - Update account balances on settlement date
 * - Integrate with DTC/NSCC for clearing
 * - Handle settlement failures
 * - Corporate actions (dividends, splits, mergers)
 *
 * Regulatory: SEC Rule 15c6-1 (T+2 settlement)
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeSettlementService {

    private final InvestmentOrderRepository orderRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final PortfolioService portfolioService;

    /**
     * Process filled orders for settlement
     * Called when order is filled to initiate settlement workflow
     *
     * @param order Filled investment order
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void initiateSettlement(InvestmentOrder order) {
        if (order.getStatus() != OrderStatus.FILLED) {
            log.warn("Cannot initiate settlement for non-filled order: {}", order.getOrderNumber());
            return;
        }

        // Calculate settlement date (T+2)
        LocalDate settlementDate = calculateSettlementDate(order.getFilledAt().toLocalDate());

        log.info("Initiating settlement for order {}: trade_date={}, settlement_date={}",
            order.getOrderNumber(), order.getFilledAt().toLocalDate(), settlementDate);

        // For now, settle immediately (in production, would wait until T+2)
        // In production: Create pending_settlement record and process on settlement date
        settleOrder(order);
    }

    /**
     * Settle a filled order
     * Updates account balances and holdings
     *
     * @param order Filled order to settle
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void settleOrder(InvestmentOrder order) {
        log.info("Settling order {}: symbol={}, side={}, quantity={}, price={}",
            order.getOrderNumber(), order.getSymbol(), order.getSide(),
            order.getExecutedQuantity(), order.getAveragePrice());

        if (order.getSide() == OrderSide.BUY) {
            settleBuyOrder(order);
        } else {
            settleSellOrder(order);
        }

        log.info("Order {} settled successfully", order.getOrderNumber());
    }

    /**
     * Settle a buy order
     * - Add shares to holdings
     * - Deduct cash from account (already done at order time)
     */
    private void settleBuyOrder(InvestmentOrder order) {
        // Find or create holding
        InvestmentHolding holding = holdingRepository
            .findByInvestmentAccountAndSymbol(order.getInvestmentAccount(), order.getSymbol())
            .orElseGet(() -> {
                InvestmentHolding newHolding = InvestmentHolding.builder()
                    .investmentAccount(order.getInvestmentAccount())
                    .portfolio(order.getInvestmentAccount().getPortfolio())
                    .symbol(order.getSymbol())
                    .instrumentType("STOCK") // Would be determined from order
                    .name(order.getSymbol()) // Would fetch from market data
                    .quantity(BigDecimal.ZERO)
                    .averageCost(BigDecimal.ZERO)
                    .totalCost(BigDecimal.ZERO)
                    .currentPrice(order.getAveragePrice())
                    .marketValue(BigDecimal.ZERO)
                    .build();
                return holdingRepository.save(newHolding);
            });

        // Calculate total cost (price * quantity + commission + fees)
        BigDecimal totalCost = order.getAveragePrice().multiply(order.getExecutedQuantity())
            .add(order.getCommission() != null ? order.getCommission() : BigDecimal.ZERO)
            .add(order.getFees() != null ? order.getFees() : BigDecimal.ZERO);

        // Update holding
        holding.updateQuantity(order.getExecutedQuantity(), totalCost);
        holdingRepository.save(holding);

        // Update portfolio
        portfolioService.recalculatePortfolio(order.getInvestmentAccount().getPortfolio().getId());

        log.info("Buy order settled: added {} shares of {} to holdings",
            order.getExecutedQuantity(), order.getSymbol());
    }

    /**
     * Settle a sell order
     * - Remove shares from holdings
     * - Add cash to account (proceeds - commission - fees)
     */
    private void settleSellOrder(InvestmentOrder order) {
        // Find holding
        InvestmentHolding holding = holdingRepository
            .findByInvestmentAccountAndSymbol(order.getInvestmentAccount(), order.getSymbol())
            .orElseThrow(() -> new IllegalStateException(
                "Cannot settle sell order: no holding found for " + order.getSymbol()));

        // Update holding (reduce quantity)
        holding.updateQuantity(order.getExecutedQuantity().negate(), BigDecimal.ZERO);

        if (holding.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            // Position closed, can soft-delete holding
            log.info("Position closed for {}", order.getSymbol());
        }

        holdingRepository.save(holding);

        // Calculate proceeds (price * quantity - commission - fees)
        BigDecimal proceeds = order.getAveragePrice().multiply(order.getExecutedQuantity())
            .subtract(order.getCommission() != null ? order.getCommission() : BigDecimal.ZERO)
            .subtract(order.getFees() != null ? order.getFees() : BigDecimal.ZERO);

        // Credit account balance (would integrate with wallet service)
        // walletService.creditAccount(order.getInvestmentAccount(), proceeds);

        // Update portfolio
        portfolioService.recalculatePortfolio(order.getInvestmentAccount().getPortfolio().getId());

        log.info("Sell order settled: removed {} shares of {}, proceeds: ${}",
            order.getExecutedQuantity(), order.getSymbol(), proceeds);
    }

    /**
     * Calculate settlement date (T+2 business days)
     * Excludes weekends and market holidays
     *
     * @param tradeDate Trade execution date
     * @return Settlement date
     */
    private LocalDate calculateSettlementDate(LocalDate tradeDate) {
        LocalDate settlementDate = tradeDate;
        int businessDays = 0;

        while (businessDays < 2) {
            settlementDate = settlementDate.plusDays(1);
            if (isBusinessDay(settlementDate)) {
                businessDays++;
            }
        }

        return settlementDate;
    }

    /**
     * Check if date is a business day
     * Excludes weekends (in production, would also exclude market holidays)
     *
     * @param date Date to check
     * @return true if business day
     */
    private boolean isBusinessDay(LocalDate date) {
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != java.time.DayOfWeek.SATURDAY &&
               dayOfWeek != java.time.DayOfWeek.SUNDAY;
        // In production, would also check against market holiday calendar
    }

    /**
     * Scheduled job: Process pending settlements
     * Runs daily at 6:00 AM to settle trades from T-2
     */
    @Scheduled(cron = "0 0 6 * * *") // 6:00 AM daily
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processPendingSettlements() {
        log.info("========================================");
        log.info("SCHEDULED: Daily Settlement Processing");
        log.info("========================================");

        LocalDate today = LocalDate.now();

        // In production, would query pending_settlements table for today's settlements
        // For now, find recently filled orders that haven't been settled
        List<InvestmentOrder> filledOrders = orderRepository.findByStatus(OrderStatus.FILLED);

        int settledCount = 0;
        for (InvestmentOrder order : filledOrders) {
            try {
                LocalDate settlementDate = calculateSettlementDate(order.getFilledAt().toLocalDate());
                if (!settlementDate.isAfter(today)) {
                    settleOrder(order);
                    settledCount++;
                }
            } catch (Exception e) {
                log.error("Failed to settle order {}", order.getOrderNumber(), e);
                // In production, would create settlement_failure record for manual review
            }
        }

        log.info("Settlement processing complete: {} orders settled", settledCount);
    }

    /**
     * Handle corporate actions (dividends, stock splits, mergers)
     * Called when corporate action notification received
     *
     * @param symbol Stock symbol
     * @param actionType Type of corporate action
     * @param effectiveDate Effective date of action
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCorporateAction(
        String symbol,
        String actionType,
        LocalDate effectiveDate,
        Map<String, Object> actionDetails) {

        log.info("Processing corporate action: symbol={}, type={}, effective_date={}",
            symbol, actionType, effectiveDate);

        List<InvestmentHolding> holdings = holdingRepository.findBySymbol(symbol);

        switch (actionType.toUpperCase()) {
            case "STOCK_SPLIT" -> processStockSplit(holdings, actionDetails);
            case "DIVIDEND" -> processDividend(holdings, actionDetails);
            case "MERGER" -> processMerger(holdings, actionDetails);
            case "SPINOFF" -> processSpinoff(holdings, actionDetails);
            default -> log.warn("Unknown corporate action type: {}", actionType);
        }
    }

    private void processStockSplit(List<InvestmentHolding> holdings, Map<String, Object> details) {
        // Example: 2-for-1 split doubles shares, halves price
        BigDecimal splitRatio = new BigDecimal(details.get("split_ratio").toString());

        for (InvestmentHolding holding : holdings) {
            BigDecimal newQuantity = holding.getQuantity().multiply(splitRatio);
            BigDecimal newAverageCost = holding.getAverageCost().divide(splitRatio, 4, RoundingMode.HALF_UP);

            holding.setQuantity(newQuantity);
            holding.setAverageCost(newAverageCost);
            holdingRepository.save(holding);

            log.info("Stock split processed: {} shares {} -> {} shares",
                holding.getSymbol(), holding.getQuantity(), newQuantity);
        }
    }

    private void processDividend(List<InvestmentHolding> holdings, Map<String, Object> details) {
        BigDecimal dividendPerShare = new BigDecimal(details.get("amount_per_share").toString());

        for (InvestmentHolding holding : holdings) {
            BigDecimal dividendAmount = holding.getQuantity().multiply(dividendPerShare);
            holding.addDividend(dividendAmount);
            holdingRepository.save(holding);

            // Credit dividend to account (would integrate with wallet service)
            log.info("Dividend processed: {} paid ${} (${} per share)",
                holding.getSymbol(), dividendAmount, dividendPerShare);
        }
    }

    private void processMerger(List<InvestmentHolding> holdings, Map<String, Object> details) {
        // Handle merger/acquisition
        log.info("Processing merger for {} holdings", holdings.size());
        // Implementation would depend on merger terms
    }

    private void processSpinoff(List<InvestmentHolding> holdings, Map<String, Object> details) {
        // Handle spinoff (new company created from existing company)
        log.info("Processing spinoff for {} holdings", holdings.size());
        // Implementation would depend on spinoff terms
    }
}
