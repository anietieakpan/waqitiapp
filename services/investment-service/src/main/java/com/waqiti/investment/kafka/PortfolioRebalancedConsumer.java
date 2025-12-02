package com.waqiti.investment.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.investment.service.InvestmentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CRITICAL FIX #44: PortfolioRebalancedConsumer
 * Notifies investors when portfolios are automatically rebalanced
 * Impact: Investment transparency, regulatory compliance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioRebalancedConsumer {
    private final InvestmentNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "investment.portfolio.rebalanced", groupId = "investment-portfolio-rebalanced")
    public void handle(PortfolioRebalancedEvent event, Acknowledgment ack) {
        try {
            log.info("⚖️ PORTFOLIO REBALANCED: userId={}, portfolioId={}, tradesExecuted={}, totalValue=${}",
                event.getUserId(), event.getPortfolioId(), event.getTradesExecuted(), event.getPortfolioValue());

            String key = "portfolio:rebalanced:" + event.getRebalanceId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                ⚖️ Portfolio Automatically Rebalanced

                Your investment portfolio has been rebalanced to maintain your target allocation.

                Portfolio Details:
                - Portfolio: %s
                - Total Value: $%s
                - Rebalanced: %s
                - Rebalance Type: %s
                - Trades Executed: %d

                Target vs Actual Allocation:
                %s

                Trades Executed:
                %s

                Performance Impact:
                - Estimated Cost: $%s (fees + spread)
                - Tax Considerations: %s

                Why We Rebalanced:
                %s

                Portfolio Performance:
                - Current Value: $%s
                - %s
                - Year-to-Date Return: %.2f%%
                - Since Inception Return: %.2f%%

                What This Means:
                • Your portfolio allocations have been adjusted
                • This helps maintain your risk profile
                • Rebalancing is part of disciplined investing
                • No action needed from you

                Rebalancing Strategy:
                %s

                View Full Details:
                • Portfolio dashboard: https://example.com/investments/portfolio/%s
                • Trade confirmations: https://example.com/investments/trades
                • Tax reports: https://example.com/investments/tax-reports

                Investment Philosophy:
                💡 Why We Rebalance:
                • Maintains target risk level
                • Prevents portfolio drift
                • Captures gains from winning investments
                • Buys underperforming assets at lower prices
                • Follows proven investment discipline

                💡 Rebalancing Frequency:
                %s

                Questions? Contact investment advisory:
                Email: advisory@example.com
                Phone: 1-800-WAQITI-INV
                Reference: Rebalance ID %s

                Important Disclosures:
                %s
                """,
                event.getPortfolioName(),
                event.getPortfolioValue(),
                event.getRebalancedAt(),
                event.getRebalanceType(),
                event.getTradesExecuted(),
                formatAllocationComparison(event.getAllocationChanges()),
                formatTradesExecuted(event.getTradesSummary()),
                event.getRebalanceCost(),
                event.hasTaxImplications() ? "May generate taxable events - see tax report" : "No immediate tax impact",
                getRebalanceReason(event.getRebalanceReason(), event.getRebalanceTrigger()),
                event.getPortfolioValue(),
                event.getValueChange().compareTo(BigDecimal.ZERO) >= 0
                    ? String.format("Up $%s since last rebalance", event.getValueChange())
                    : String.format("Down $%s since last rebalance", event.getValueChange().abs()),
                event.getYtdReturn().doubleValue(),
                event.getSinceInceptionReturn().doubleValue(),
                getRebalancingStrategy(event.getRebalanceType()),
                event.getPortfolioId(),
                getRebalancingFrequency(event.getRebalanceType()),
                event.getRebalanceId(),
                getDisclosures());

            notificationService.sendPortfolioRebalancedNotification(
                event.getUserId(), event.getPortfolioId(), event.getPortfolioName(),
                event.getPortfolioValue(), message);

            metricsCollector.incrementCounter("investment.portfolio.rebalanced.notification.sent");
            metricsCollector.recordGauge("portfolio.value", event.getPortfolioValue().doubleValue());
            metricsCollector.recordHistogram("portfolio.trades.executed", event.getTradesExecuted());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process portfolio rebalanced event", e);
            dlqHandler.sendToDLQ("investment.portfolio.rebalanced", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String formatAllocationComparison(List<AllocationChange> changes) {
        StringBuilder sb = new StringBuilder();
        for (AllocationChange change : changes) {
            String arrow = change.getActualAllocation().compareTo(change.getTargetAllocation()) > 0 ? "▼" : "▲";
            sb.append(String.format("• %s: %.1f%% → %.1f%% (target: %.1f%%) %s\n",
                change.getAssetClass(),
                change.getBeforeAllocation().doubleValue(),
                change.getActualAllocation().doubleValue(),
                change.getTargetAllocation().doubleValue(),
                arrow));
        }
        return sb.toString();
    }

    private String formatTradesExecuted(List<TradeSummary> trades) {
        StringBuilder sb = new StringBuilder();
        for (TradeSummary trade : trades) {
            sb.append(String.format("• %s %s shares of %s at $%s (total: $%s)\n",
                trade.getAction(),
                trade.getShares(),
                trade.getSymbol(),
                trade.getPrice(),
                trade.getTotalValue()));
        }
        return sb.toString();
    }

    private String getRebalanceReason(String reason, String trigger) {
        return switch (trigger.toLowerCase()) {
            case "drift_threshold" -> String.format("""
                Your portfolio drifted beyond the %s threshold.
                Automatic rebalancing helps maintain your target allocation.
                """, reason);
            case "scheduled" -> """
                This was a scheduled quarterly rebalance.
                Regular rebalancing is part of your investment strategy.
                """;
            case "deposit" -> """
                New funds were deposited into your portfolio.
                We rebalanced to invest the funds according to your target allocation.
                """;
            case "market_volatility" -> """
                Market movements caused significant allocation drift.
                Rebalancing helps manage risk during volatile periods.
                """;
            default -> "Your portfolio was rebalanced to maintain your target allocation.";
        };
    }

    private String getRebalancingStrategy(String rebalanceType) {
        return switch (rebalanceType.toLowerCase()) {
            case "threshold" -> """
                Threshold-Based Rebalancing:
                • Triggered when any asset class drifts 5%+ from target
                • Minimizes trading while maintaining discipline
                • Reduces transaction costs
                """;
            case "calendar" -> """
                Calendar-Based Rebalancing:
                • Quarterly automatic rebalancing
                • Predictable and consistent
                • Simple and disciplined approach
                """;
            case "hybrid" -> """
                Hybrid Rebalancing:
                • Combines threshold and calendar approaches
                • Quarterly review with 5%+ drift trigger
                • Optimal balance of discipline and cost
                """;
            default -> "Your portfolio follows a systematic rebalancing strategy.";
        };
    }

    private String getRebalancingFrequency(String rebalanceType) {
        return switch (rebalanceType.toLowerCase()) {
            case "threshold" -> "As needed (when drift exceeds 5%)";
            case "calendar" -> "Quarterly (January, April, July, October)";
            case "hybrid" -> "Quarterly, or sooner if drift exceeds 5%";
            default -> "According to your strategy";
        };
    }

    private String getDisclosures() {
        return """
            Past performance does not guarantee future results. All investments carry risk, including possible loss of principal.
            Rebalancing may generate taxable events. Consult a tax advisor for your situation.
            Investment advisory services provided by Waqiti Investment Advisors, a registered investment advisor.
            """;
    }

    private static class PortfolioRebalancedEvent {
        private UUID userId, portfolioId, rebalanceId;
        private String portfolioName, rebalanceType, rebalanceReason, rebalanceTrigger;
        private BigDecimal portfolioValue, rebalanceCost, valueChange, ytdReturn, sinceInceptionReturn;
        private int tradesExecuted;
        private LocalDateTime rebalancedAt;
        private boolean taxImplications;
        private List<AllocationChange> allocationChanges;
        private List<TradeSummary> tradesSummary;

        public UUID getUserId() { return userId; }
        public UUID getPortfolioId() { return portfolioId; }
        public UUID getRebalanceId() { return rebalanceId; }
        public String getPortfolioName() { return portfolioName; }
        public String getRebalanceType() { return rebalanceType; }
        public String getRebalanceReason() { return rebalanceReason; }
        public String getRebalanceTrigger() { return rebalanceTrigger; }
        public BigDecimal getPortfolioValue() { return portfolioValue; }
        public BigDecimal getRebalanceCost() { return rebalanceCost; }
        public BigDecimal getValueChange() { return valueChange; }
        public BigDecimal getYtdReturn() { return ytdReturn; }
        public BigDecimal getSinceInceptionReturn() { return sinceInceptionReturn; }
        public int getTradesExecuted() { return tradesExecuted; }
        public LocalDateTime getRebalancedAt() { return rebalancedAt; }
        public boolean hasTaxImplications() { return taxImplications; }
        public List<AllocationChange> getAllocationChanges() { return allocationChanges; }
        public List<TradeSummary> getTradesSummary() { return tradesSummary; }
    }

    private static class AllocationChange {
        private String assetClass;
        private BigDecimal beforeAllocation, actualAllocation, targetAllocation;

        public String getAssetClass() { return assetClass; }
        public BigDecimal getBeforeAllocation() { return beforeAllocation; }
        public BigDecimal getActualAllocation() { return actualAllocation; }
        public BigDecimal getTargetAllocation() { return targetAllocation; }
    }

    private static class TradeSummary {
        private String action, symbol;
        private BigDecimal shares, price, totalValue;

        public String getAction() { return action; }
        public String getSymbol() { return symbol; }
        public BigDecimal getShares() { return shares; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getTotalValue() { return totalValue; }
    }
}
