package com.waqiti.crypto.trading.execution;

import com.waqiti.common.cache.CacheService;
import com.waqiti.common.cache.DistributedLockService;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.crypto.entity.TradeOrder;
import com.waqiti.crypto.entity.TradeOrderStatus;
import com.waqiti.crypto.entity.TradeOrderType;
import com.waqiti.crypto.event.OrderMatchedEvent;
import com.waqiti.crypto.event.TradeExecutedEvent;
import com.waqiti.crypto.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderMatchingEngine {

    private final TradeOrderRepository orderRepository;
    private final TradeExecutionEngine executionEngine;
    private final CacheService cacheService;
    private final DistributedLockService lockService;
    private final EventPublisher eventPublisher;

    // In-memory order books by trading pair
    private final Map<UUID, OrderBook> orderBooks = new ConcurrentHashMap<>();

    @Transactional
    public void submitOrder(TradeOrder order) {
        log.info("Submitting order to matching engine: {}", order.getOrderId());
        
        String lockKey = "matching:pair:" + order.getTradePairId();
        lockService.executeWithLock(lockKey, Duration.ofMinutes(1), Duration.ofSeconds(10), () -> {
            
            // Update order status
            order.setStatus(TradeOrderStatus.SUBMITTED);
            order.setSubmittedAt(LocalDateTime.now());
            orderRepository.save(order);
            
            // Get or create order book for trading pair
            OrderBook orderBook = orderBooks.computeIfAbsent(order.getTradePairId(), k -> new OrderBook());
            
            // Process order based on type
            List<TradeExecution> executions = new ArrayList<>();
            
            if (order.getOrderType() == TradeOrderType.MARKET) {
                executions = processMarketOrder(order, orderBook);
            } else {
                executions = processLimitOrder(order, orderBook);
            }
            
            // Return order match result with executions
            return OrderMatchResult.builder()
                    .orderId(order.getOrderId())
                    .tradePairId(order.getTradePairId())
                    .originalQuantity(order.getQuantity())
                    .executedQuantity(executions.stream()
                            .map(TradeExecution::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .remainingQuantity(order.getQuantity().subtract(
                            executions.stream()
                                    .map(TradeExecution::getQuantity)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)))
                    .averagePrice(calculateAveragePrice(executions))
                    .executions(executions)
                    .status(determineOrderStatus(order, executions))
                    .timestamp(LocalDateTime.now())
                    .build();
        });
    }

    @Transactional
    public void cancelOrder(TradeOrder order) {
        log.info("Cancelling order in matching engine: {}", order.getOrderId());
        
        OrderBook orderBook = orderBooks.get(order.getTradePairId());
        if (orderBook != null) {
            if ("BUY".equals(order.getSide())) {
                orderBook.buyOrders.removeIf(o -> o.getId().equals(order.getId()));
            } else {
                orderBook.sellOrders.removeIf(o -> o.getId().equals(order.getId()));
            }
        }
    }

    private List<TradeExecution> processMarketOrder(TradeOrder marketOrder, OrderBook orderBook) {
        log.info("Processing market order: {}", marketOrder.getOrderId());
        
        Queue<TradeOrder> oppositeOrders = "BUY".equals(marketOrder.getSide()) ? 
                orderBook.sellOrders : orderBook.buyOrders;
        
        BigDecimal remainingQuantity = marketOrder.getQuantity();
        BigDecimal totalValue = BigDecimal.ZERO;
        List<TradeExecution> executions = new ArrayList<>();
        
        while (remainingQuantity.compareTo(BigDecimal.ZERO) > 0 && !oppositeOrders.isEmpty()) {
            TradeOrder matchingOrder = oppositeOrders.poll();
            
            if (matchingOrder == null || !matchingOrder.isActive()) {
                continue;
            }
            
            BigDecimal matchQuantity = remainingQuantity.min(matchingOrder.getRemainingQuantity());
            BigDecimal matchPrice = matchingOrder.getPrice();
            
            // Create trade execution
            TradeExecution execution = executeMatch(marketOrder, matchingOrder, matchQuantity, matchPrice);
            executions.add(execution);
            
            totalValue = totalValue.add(matchQuantity.multiply(matchPrice));
            remainingQuantity = remainingQuantity.subtract(matchQuantity);
            
            // Update matching order
            updateOrderAfterMatch(matchingOrder, matchQuantity, matchPrice);
            
            // If matching order still has quantity, put it back
            if (matchingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                oppositeOrders.offer(matchingOrder);
            }
        }
        
        // Update market order
        BigDecimal filledQuantity = marketOrder.getQuantity().subtract(remainingQuantity);
        if (filledQuantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal averagePrice = totalValue.divide(filledQuantity, 8, RoundingMode.HALF_UP);
            updateOrderAfterMatch(marketOrder, filledQuantity, averagePrice);
        }
        
        // If market order couldn't be fully filled, cancel remaining
        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            marketOrder.setStatus(TradeOrderStatus.PARTIALLY_FILLED);
            log.warn("Market order {} partially filled. Remaining quantity: {}", 
                    marketOrder.getOrderId(), remainingQuantity);
        }
        
        // Process executions
        executions.forEach(this::processTradeExecution);
        
        return executions;
    }

    private List<TradeExecution> processLimitOrder(TradeOrder limitOrder, OrderBook orderBook) {
        log.info("Processing limit order: {}", limitOrder.getOrderId());
        
        Queue<TradeOrder> oppositeOrders = "BUY".equals(limitOrder.getSide()) ? 
                orderBook.sellOrders : orderBook.buyOrders;
        
        BigDecimal remainingQuantity = limitOrder.getQuantity();
        BigDecimal totalValue = BigDecimal.ZERO;
        List<TradeExecution> executions = new ArrayList<>();
        
        // Try to match with existing orders
        Iterator<TradeOrder> iterator = oppositeOrders.iterator();
        while (iterator.hasNext() && remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            TradeOrder matchingOrder = iterator.next();
            
            if (!canMatch(limitOrder, matchingOrder)) {
                break; // No more matching orders at better prices
            }
            
            BigDecimal matchQuantity = remainingQuantity.min(matchingOrder.getRemainingQuantity());
            BigDecimal matchPrice = matchingOrder.getPrice(); // Price improvement for taker
            
            // Create trade execution
            TradeExecution execution = executeMatch(limitOrder, matchingOrder, matchQuantity, matchPrice);
            executions.add(execution);
            
            totalValue = totalValue.add(matchQuantity.multiply(matchPrice));
            remainingQuantity = remainingQuantity.subtract(matchQuantity);
            
            // Update matching order
            updateOrderAfterMatch(matchingOrder, matchQuantity, matchPrice);
            
            // Remove if fully filled
            if (matchingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
                iterator.remove();
            }
        }
        
        // Update limit order if partially filled
        if (remainingQuantity.compareTo(limitOrder.getQuantity()) < 0) {
            BigDecimal filledQuantity = limitOrder.getQuantity().subtract(remainingQuantity);
            BigDecimal averagePrice = totalValue.divide(filledQuantity, 8, RoundingMode.HALF_UP);
            updateOrderAfterMatch(limitOrder, filledQuantity, averagePrice);
        }
        
        // Add remaining quantity to order book
        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            limitOrder.setStatus(TradeOrderStatus.ACKNOWLEDGED);
            limitOrder.setAcknowledgedAt(LocalDateTime.now());
            
            if ("BUY".equals(limitOrder.getSide())) {
                orderBook.buyOrders.offer(limitOrder);
            } else {
                orderBook.sellOrders.offer(limitOrder);
            }
        }
        
        // Process executions
        executions.forEach(this::processTradeExecution);
        
        return executions;
    }

    private boolean canMatch(TradeOrder takerOrder, TradeOrder makerOrder) {
        if ("BUY".equals(takerOrder.getSide())) {
            // Buy order can match if willing to pay >= maker's ask price
            return takerOrder.getPrice().compareTo(makerOrder.getPrice()) >= 0;
        } else {
            // Sell order can match if willing to accept <= maker's bid price
            return takerOrder.getPrice().compareTo(makerOrder.getPrice()) <= 0;
        }
    }

    private TradeExecution executeMatch(TradeOrder takerOrder, TradeOrder makerOrder, 
                                       BigDecimal quantity, BigDecimal price) {
        
        String executionId = "EXEC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        
        return TradeExecution.builder()
                .executionId(executionId)
                .tradePairId(takerOrder.getTradePairId())
                .takerOrderId(takerOrder.getId())
                .makerOrderId(makerOrder.getId())
                .quantity(quantity)
                .price(price)
                .value(quantity.multiply(price))
                .takerSide(takerOrder.getSide())
                .executedAt(LocalDateTime.now())
                .build();
    }

    private void updateOrderAfterMatch(TradeOrder order, BigDecimal filledQuantity, BigDecimal price) {
        BigDecimal previousFilled = order.getFilledQuantity();
        BigDecimal newFilled = previousFilled.add(filledQuantity);
        
        // Update filled quantity
        order.setFilledQuantity(newFilled);
        order.setRemainingQuantity(order.getQuantity().subtract(newFilled));
        
        // Update average price
        if (previousFilled.compareTo(BigDecimal.ZERO) == 0) {
            order.setAveragePrice(price);
        } else {
            BigDecimal previousValue = previousFilled.multiply(order.getAveragePrice());
            BigDecimal newValue = filledQuantity.multiply(price);
            BigDecimal totalValue = previousValue.add(newValue);
            order.setAveragePrice(totalValue.divide(newFilled, 8, RoundingMode.HALF_UP));
        }
        
        // Update timestamps
        if (order.getFirstFillAt() == null) {
            order.setFirstFillAt(LocalDateTime.now());
        }
        order.setLastFillAt(LocalDateTime.now());
        
        // Update status
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus(TradeOrderStatus.FILLED);
            order.setCompletedAt(LocalDateTime.now());
        } else {
            order.setStatus(TradeOrderStatus.PARTIALLY_FILLED);
        }
        
        orderRepository.save(order);
    }

    private void processTradeExecution(TradeExecution execution) {
        log.info("Processing trade execution: {} quantity: {} price: {}", 
                execution.getExecutionId(), execution.getQuantity(), execution.getPrice());
        
        // Publish events
        eventPublisher.publish(OrderMatchedEvent.builder()
                .executionId(execution.getExecutionId())
                .tradePairId(execution.getTradePairId())
                .takerOrderId(execution.getTakerOrderId())
                .makerOrderId(execution.getMakerOrderId())
                .quantity(execution.getQuantity())
                .price(execution.getPrice())
                .value(execution.getValue())
                .build());
        
        eventPublisher.publish(TradeExecutedEvent.builder()
                .executionId(execution.getExecutionId())
                .tradePairId(execution.getTradePairId())
                .quantity(execution.getQuantity())
                .price(execution.getPrice())
                .value(execution.getValue())
                .takerSide(execution.getTakerSide())
                .executedAt(execution.getExecutedAt())
                .build());
        
        // Delegate to execution engine for settlement
        executionEngine.settleTradeExecution(execution);
    }
    
    /**
     * Calculate average execution price from trade executions
     */
    private BigDecimal calculateAveragePrice(List<TradeExecution> executions) {
        if (executions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValue = executions.stream()
                .map(TradeExecution::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalQuantity = executions.stream()
                .map(TradeExecution::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalQuantity.equals(BigDecimal.ZERO)) {
            return BigDecimal.ZERO;
        }
        
        return totalValue.divide(totalQuantity, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Determine order status based on executions
     */
    private OrderStatus determineOrderStatus(TradeOrder order, List<TradeExecution> executions) {
        if (executions.isEmpty()) {
            return OrderStatus.OPEN;
        }
        
        BigDecimal executedQuantity = executions.stream()
                .map(TradeExecution::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (executedQuantity.equals(order.getQuantity())) {
            return OrderStatus.FILLED;
        } else if (executedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            return OrderStatus.PARTIALLY_FILLED;
        } else {
            return OrderStatus.OPEN;
        }
    }

    // Inner classes for order book management
    private static class OrderBook {
        // Buy orders sorted by price (highest first) then time priority
        final Queue<TradeOrder> buyOrders = new PriorityBlockingQueue<>(1000, 
                Comparator.comparing(TradeOrder::getPrice).reversed()
                         .thenComparing(TradeOrder::getCreatedAt));
        
        // Sell orders sorted by price (lowest first) then time priority  
        final Queue<TradeOrder> sellOrders = new PriorityBlockingQueue<>(1000,
                Comparator.comparing(TradeOrder::getPrice)
                         .thenComparing(TradeOrder::getCreatedAt));
    }

    @lombok.Data
    @lombok.Builder
    private static class TradeExecution {
        private String executionId;
        private UUID tradePairId;
        private UUID takerOrderId;
        private UUID makerOrderId;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal value;
        private String takerSide;
        private LocalDateTime executedAt;
    }
}