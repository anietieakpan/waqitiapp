package com.waqiti.investment.execution.model;

import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.enums.OrderSide;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * Order Book - Price-time priority order book implementation.
 *
 * Features:
 * - Bids sorted by price (descending) and time (ascending)
 * - Asks sorted by price (ascending) and time (ascending)
 * - Thread-safe concurrent operations
 * - Real-time best bid/ask tracking
 * - Spread calculation
 * - Depth aggregation
 * - Stop order tracking
 *
 * Data Structure:
 * - ConcurrentSkipListSet for O(log n) insertion/deletion
 * - Maintains price-time priority automatically
 * - Supports high-frequency updates
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@Slf4j
public class OrderBook {

    private final String symbol;

    // Bids (buy orders) - sorted by price DESC, then time ASC
    private final ConcurrentSkipListSet<OrderBookEntry> bids;

    // Asks (sell orders) - sorted by price ASC, then time ASC
    private final ConcurrentSkipListSet<OrderBookEntry> asks;

    // Stop orders waiting to be triggered
    private final List<InvestmentOrder> stopOrders;
    private final List<InvestmentOrder> stopLimitOrders;

    // Order book statistics
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal spread;
    private BigDecimal midPrice;
    private int bidDepth;
    private int askDepth;
    private LocalDateTime lastUpdateTime;

    public OrderBook(String symbol) {
        this.symbol = symbol;

        // Bids: Highest price first, then earliest time
        this.bids = new ConcurrentSkipListSet<>((a, b) -> {
            int priceCompare = b.getPrice().compareTo(a.getPrice()); // Descending price
            if (priceCompare != 0) return priceCompare;
            return a.getTimestamp().compareTo(b.getTimestamp()); // Ascending time
        });

        // Asks: Lowest price first, then earliest time
        this.asks = new ConcurrentSkipListSet<>((a, b) -> {
            int priceCompare = a.getPrice().compareTo(b.getPrice()); // Ascending price
            if (priceCompare != 0) return priceCompare;
            return a.getTimestamp().compareTo(b.getTimestamp()); // Ascending time
        });

        this.stopOrders = Collections.synchronizedList(new ArrayList<>());
        this.stopLimitOrders = Collections.synchronizedList(new ArrayList<>());

        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * Add bid (buy order) to order book.
     */
    public void addBid(OrderBookEntry entry) {
        bids.add(entry);
        updateStatistics();
        log.debug("OrderBook[{}]: Added bid @ {} x {}", symbol, entry.getPrice(), entry.getQuantity());
    }

    /**
     * Add ask (sell order) to order book.
     */
    public void addAsk(OrderBookEntry entry) {
        asks.add(entry);
        updateStatistics();
        log.debug("OrderBook[{}]: Added ask @ {} x {}", symbol, entry.getPrice(), entry.getQuantity());
    }

    /**
     * Remove order from order book.
     */
    public boolean removeOrder(String orderId) {
        boolean removed = bids.removeIf(entry -> entry.getOrder().getId().equals(orderId));
        if (!removed) {
            removed = asks.removeIf(entry -> entry.getOrder().getId().equals(orderId));
        }
        if (removed) {
            updateStatistics();
            log.debug("OrderBook[{}]: Removed order {}", symbol, orderId);
        }
        return removed;
    }

    /**
     * Add stop order to watch list.
     */
    public void addStopOrder(InvestmentOrder order) {
        stopOrders.add(order);
        log.debug("OrderBook[{}]: Added stop order {} @ trigger {}",
            symbol, order.getId(), order.getStopPrice());
    }

    /**
     * Add stop-limit order to watch list.
     */
    public void addStopLimitOrder(InvestmentOrder order) {
        stopLimitOrders.add(order);
        log.debug("OrderBook[{}]: Added stop-limit order {} @ trigger {} / limit {}",
            symbol, order.getId(), order.getStopPrice(), order.getLimitPrice());
    }

    /**
     * Check and trigger stop orders based on current price.
     */
    public List<InvestmentOrder> checkStopOrders(BigDecimal currentPrice) {
        List<InvestmentOrder> triggered = new ArrayList<>();

        synchronized (stopOrders) {
            Iterator<InvestmentOrder> iterator = stopOrders.iterator();
            while (iterator.hasNext()) {
                InvestmentOrder order = iterator.next();

                boolean shouldTrigger = order.getSide() == OrderSide.BUY
                    ? currentPrice.compareTo(order.getStopPrice()) >= 0 // Stop-buy triggers when price rises
                    : currentPrice.compareTo(order.getStopPrice()) <= 0; // Stop-sell triggers when price falls

                if (shouldTrigger) {
                    triggered.add(order);
                    iterator.remove();
                    log.info("OrderBook[{}]: Stop order {} triggered at price {}",
                        symbol, order.getId(), currentPrice);
                }
            }
        }

        return triggered;
    }

    /**
     * Check and trigger stop-limit orders.
     */
    public List<InvestmentOrder> checkStopLimitOrders(BigDecimal currentPrice) {
        List<InvestmentOrder> triggered = new ArrayList<>();

        synchronized (stopLimitOrders) {
            Iterator<InvestmentOrder> iterator = stopLimitOrders.iterator();
            while (iterator.hasNext()) {
                InvestmentOrder order = iterator.next();

                boolean shouldTrigger = order.getSide() == OrderSide.BUY
                    ? currentPrice.compareTo(order.getStopPrice()) >= 0
                    : currentPrice.compareTo(order.getStopPrice()) <= 0;

                if (shouldTrigger) {
                    triggered.add(order);
                    iterator.remove();
                    log.info("OrderBook[{}]: Stop-limit order {} triggered at price {}",
                        symbol, order.getId(), currentPrice);
                }
            }
        }

        return triggered;
    }

    /**
     * Update order book statistics.
     */
    public void updateStatistics() {
        this.bestBid = bids.isEmpty() ? null : bids.first().getPrice();
        this.bestAsk = asks.isEmpty() ? null : asks.first().getPrice();

        if (bestBid != null && bestAsk != null) {
            this.spread = bestAsk.subtract(bestBid);
            this.midPrice = bestBid.add(bestAsk)
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        } else {
            this.spread = null;
            this.midPrice = null;
        }

        this.bidDepth = bids.size();
        this.askDepth = asks.size();
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * Get top N levels of order book (depth).
     */
    public OrderBookDepth getDepth(int levels) {
        List<PriceLevel> bidLevels = aggregatePriceLevels(bids, levels);
        List<PriceLevel> askLevels = aggregatePriceLevels(asks, levels);

        return OrderBookDepth.builder()
            .symbol(symbol)
            .bids(bidLevels)
            .asks(askLevels)
            .bestBid(bestBid)
            .bestAsk(bestAsk)
            .spread(spread)
            .midPrice(midPrice)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Aggregate orders by price level.
     */
    private List<PriceLevel> aggregatePriceLevels(Set<OrderBookEntry> orders, int maxLevels) {
        Map<BigDecimal, BigDecimal> priceMap = new LinkedHashMap<>();

        for (OrderBookEntry entry : orders) {
            if (priceMap.size() >= maxLevels) break;

            priceMap.merge(entry.getPrice(), entry.getQuantity(), BigDecimal::add);
        }

        return priceMap.entrySet().stream()
            .map(e -> new PriceLevel(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * Get all bids as list (for iteration).
     */
    public List<OrderBookEntry> getBids() {
        return new ArrayList<>(bids);
    }

    /**
     * Get all asks as list (for iteration).
     */
    public List<OrderBookEntry> getAsks() {
        return new ArrayList<>(asks);
    }

    /**
     * Get order book snapshot.
     */
    public OrderBookSnapshot getSnapshot() {
        return OrderBookSnapshot.builder()
            .symbol(symbol)
            .bestBid(bestBid)
            .bestAsk(bestAsk)
            .spread(spread)
            .midPrice(midPrice)
            .bidDepth(bidDepth)
            .askDepth(askDepth)
            .totalBidVolume(calculateTotalVolume(bids))
            .totalAskVolume(calculateTotalVolume(asks))
            .timestamp(lastUpdateTime)
            .build();
    }

    /**
     * Calculate total volume at all price levels.
     */
    private BigDecimal calculateTotalVolume(Set<OrderBookEntry> orders) {
        return orders.stream()
            .map(OrderBookEntry::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Clear order book (for market close, etc.).
     */
    public void clear() {
        bids.clear();
        asks.clear();
        stopOrders.clear();
        stopLimitOrders.clear();
        updateStatistics();
        log.info("OrderBook[{}]: Cleared all orders", symbol);
    }

    /**
     * Get order count.
     */
    public int getTotalOrderCount() {
        return bids.size() + asks.size() + stopOrders.size() + stopLimitOrders.size();
    }
}
