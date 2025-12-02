package com.waqiti.crypto.trading;

import com.waqiti.crypto.dto.request.*;
import com.waqiti.crypto.dto.response.*;
import com.waqiti.crypto.entity.*;
import com.waqiti.crypto.exception.*;
import com.waqiti.crypto.repository.*;
import com.waqiti.crypto.pricing.CryptoPricingService;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.trading.execution.OrderMatchingEngine;
import com.waqiti.crypto.trading.risk.TradingRiskManager;
import com.waqiti.crypto.trading.algorithm.AlgorithmicTradingEngine;
import com.waqiti.crypto.trading.algorithm.TradingStrategy;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.financial.BigDecimalMath;
import com.waqiti.common.cache.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced Crypto Trading Service with sophisticated trading features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedCryptoTradingService {

    private final TradeOrderRepository tradeOrderRepository;
    private final TradePairRepository tradePairRepository;
    private final CryptoWalletService walletService;
    private final CryptoPricingService pricingService;
    private final OrderMatchingEngine matchingEngine;
    private final TradingRiskManager riskManager;
    private final AlgorithmicTradingEngine algoEngine;
    private final CacheService cacheService;
    private final DistributedLockService lockService;
    private final EventPublisher eventPublisher;

    /**
     * Execute DCA (Dollar Cost Averaging) strategy
     */
    @Transactional
    public DCAOrderResponse createDCAOrder(UUID userId, DCAOrderRequest request) {
        log.info("Creating DCA order for user: {} cryptocurrency: {}", userId, request.getCryptocurrency());

        String lockKey = "dca:user:" + userId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(5), Duration.ofSeconds(30), () -> {
            
            // Validate DCA parameters
            validateDCARequest(request);
            
            // Check available balance for entire DCA plan
            BigDecimal totalAmount = request.getInvestmentAmount().multiply(
                new BigDecimal(request.getNumberOfOrders()));
            validateSufficientBalance(userId, request.getBaseCurrency(), totalAmount);
            
            // Create master DCA order
            DCAOrder dcaOrder = DCAOrder.builder()
                    .userId(userId)
                    .cryptocurrency(request.getCryptocurrency())
                    .baseCurrency(request.getBaseCurrency())
                    .investmentAmount(request.getInvestmentAmount())
                    .frequency(request.getFrequency())
                    .numberOfOrders(request.getNumberOfOrders())
                    .completedOrders(0)
                    .totalInvested(BigDecimal.ZERO)
                    .totalCryptoReceived(BigDecimal.ZERO)
                    .averagePrice(BigDecimal.ZERO)
                    .status(DCAStatus.ACTIVE)
                    .nextExecutionTime(calculateNextExecution(request.getFrequency()))
                    .createdAt(LocalDateTime.now())
                    .build();
            
            dcaOrder = dcaOrderRepository.save(dcaOrder);
            
            // Reserve total funds
            walletService.reserveBalance(userId, request.getBaseCurrency(), totalAmount, dcaOrder.getId());
            
            // Schedule first execution if immediate start
            if (request.isStartImmediately()) {
                executeDCAOrder(dcaOrder);
            }
            
            // Publish event
            eventPublisher.publish(DCAOrderCreatedEvent.builder()
                    .dcaOrderId(dcaOrder.getId())
                    .userId(userId)
                    .cryptocurrency(request.getCryptocurrency())
                    .totalAmount(totalAmount)
                    .build());
            
            return mapToDCAOrderResponse(dcaOrder);
        });
    }

    /**
     * Execute algorithmic trading strategy
     */
    @Transactional
    public CompletableFuture<AlgorithmicTradeResult> executeAlgorithmicStrategy(
            UUID userId, AlgorithmicTradeRequest request) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Executing algorithmic strategy: {} for user: {}", 
                        request.getStrategyType(), userId);
                
                // Validate strategy parameters
                validateAlgorithmicRequest(request);
                
                // Get trading strategy implementation
                TradingStrategy strategy = algoEngine.getStrategy(request.getStrategyType());
                
                // Execute strategy
                AlgorithmicTradeResult result = strategy.execute(userId, request);
                
                // Publish result event
                eventPublisher.publish(AlgorithmicTradeExecutedEvent.builder()
                        .userId(userId)
                        .strategyType(request.getStrategyType())
                        .result(result)
                        .build());
                
                return result;
                
            } catch (Exception e) {
                log.error("Error executing algorithmic strategy for user: {}", userId, e);
                return AlgorithmicTradeResult.failed(e.getMessage());
            }
        });
    }

    /**
     * Create grid trading strategy
     */
    @Transactional
    public GridTradingResponse createGridTrading(UUID userId, GridTradingRequest request) {
        log.info("Creating grid trading strategy for user: {} pair: {}", 
                userId, request.getTradePair());

        String lockKey = "grid:user:" + userId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(5), Duration.ofSeconds(30), () -> {
            
            // Validate grid parameters
            validateGridTradingRequest(request);
            
            // Get current price
            BigDecimal currentPrice = pricingService.getCurrentPrice(request.getCryptocurrency());
            
            // Calculate grid levels
            List<GridLevel> gridLevels = calculateGridLevels(request, currentPrice);
            
            // Validate required balance
            BigDecimal requiredBalance = calculateRequiredBalance(gridLevels, request);
            validateSufficientBalance(userId, request.getBaseCurrency(), requiredBalance);
            
            // Create grid trading strategy
            GridTradingStrategy gridStrategy = GridTradingStrategy.builder()
                    .userId(userId)
                    .tradePair(request.getTradePair())
                    .cryptocurrency(request.getCryptocurrency())
                    .baseCurrency(request.getBaseCurrency())
                    .gridLevels(gridLevels.size())
                    .upperPrice(request.getUpperPrice())
                    .lowerPrice(request.getLowerPrice())
                    .investmentAmount(request.getInvestmentAmount())
                    .status(GridStatus.ACTIVE)
                    .currentPrice(currentPrice)
                    .totalProfit(BigDecimal.ZERO)
                    .completedTrades(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            gridStrategy = gridTradingRepository.save(gridStrategy);
            
            // Create initial grid orders
            List<TradeOrder> gridOrders = createGridOrders(userId, gridStrategy, gridLevels);
            
            // Submit orders to matching engine
            gridOrders.forEach(matchingEngine::submitOrder);
            
            // Reserve funds
            walletService.reserveBalance(userId, request.getBaseCurrency(), 
                    requiredBalance, gridStrategy.getId());
            
            return mapToGridTradingResponse(gridStrategy, gridOrders);
        });
    }

    /**
     * Execute arbitrage opportunity
     */
    @Transactional
    public ArbitrageResult executeArbitrage(UUID userId, ArbitrageRequest request) {
        log.info("Executing arbitrage for user: {} cryptocurrency: {}", 
                userId, request.getCryptocurrency());

        String lockKey = "arbitrage:user:" + userId;
        return lockService.executeWithLock(lockKey, Duration.ofSeconds(30), Duration.ofSeconds(10), () -> {
            
            // Validate arbitrage opportunity
            ArbitrageOpportunity opportunity = validateArbitrageOpportunity(request);
            
            if (opportunity.getProfitPercentage().compareTo(request.getMinProfitThreshold()) < 0) {
                throw new ArbitrageException("Profit margin below threshold");
            }
            
            // Calculate required amounts
            BigDecimal buyAmount = request.getAmount();
            BigDecimal sellAmount = buyAmount.multiply(opportunity.getExchangeRate());
            
            // Validate balances on both exchanges
            validateArbitrageBalances(userId, request, buyAmount, sellAmount);
            
            try {
                // Execute buy order on cheaper exchange
                TradeOrder buyOrder = executeBuyOrder(userId, request.getBuyExchange(), 
                        request.getCryptocurrency(), buyAmount, opportunity.getBuyPrice());
                
                // Execute sell order on expensive exchange
                TradeOrder sellOrder = executeSellOrder(userId, request.getSellExchange(), 
                        request.getCryptocurrency(), sellAmount, opportunity.getSellPrice());
                
                // Calculate actual profit
                BigDecimal profit = calculateArbitrageProfit(buyOrder, sellOrder);
                
                ArbitrageResult result = ArbitrageResult.builder()
                        .success(true)
                        .buyOrderId(buyOrder.getOrderId())
                        .sellOrderId(sellOrder.getOrderId())
                        .profit(profit)
                        .profitPercentage(profit.divide(buyAmount, 4, RoundingMode.HALF_UP))
                        .executionTime(LocalDateTime.now())
                        .build();
                
                // Record arbitrage execution
                recordArbitrageExecution(userId, request, result);
                
                return result;
                
            } catch (Exception e) {
                log.error("Arbitrage execution failed for user: {}", userId, e);
                return ArbitrageResult.failed(e.getMessage());
            }
        });
    }

    /**
     * Create stop-loss order with trailing capability
     */
    @Transactional
    public TrailingStopResponse createTrailingStop(UUID userId, TrailingStopRequest request) {
        log.info("Creating trailing stop for user: {} cryptocurrency: {}", 
                userId, request.getCryptocurrency());

        // Validate current position
        BigDecimal currentPosition = walletService.getAvailableCryptoBalance(
                userId, request.getCryptocurrency());
        
        if (currentPosition.compareTo(request.getQuantity()) < 0) {
            throw new InsufficientBalanceException("Insufficient cryptocurrency position");
        }
        
        // Get current price
        BigDecimal currentPrice = pricingService.getCurrentPrice(request.getCryptocurrency());
        
        // Calculate initial stop price
        BigDecimal initialStopPrice = calculateTrailingStopPrice(
                currentPrice, request.getTrailPercent(), "SELL");
        
        // Create trailing stop order
        TrailingStopOrder trailingStop = TrailingStopOrder.builder()
                .userId(userId)
                .cryptocurrency(request.getCryptocurrency())
                .quantity(request.getQuantity())
                .trailPercent(request.getTrailPercent())
                .currentPrice(currentPrice)
                .stopPrice(initialStopPrice)
                .highestPrice(currentPrice)
                .status(TrailingStopStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        trailingStop = trailingStopRepository.save(trailingStop);
        
        // Reserve cryptocurrency
        walletService.reserveCryptoBalance(userId, request.getCryptocurrency(), 
                request.getQuantity(), trailingStop.getId());
        
        // Start price monitoring
        startTrailingStopMonitoring(trailingStop);
        
        return mapToTrailingStopResponse(trailingStop);
    }

    /**
     * Get portfolio performance analytics
     */
    @Transactional(readOnly = true)
    public PortfolioAnalytics getPortfolioAnalytics(UUID userId, AnalyticsRequest request) {
        log.info("Generating portfolio analytics for user: {}", userId);

        // Get user's trade history
        List<TradeOrder> trades = tradeOrderRepository.findByUserIdAndStatusAndCreatedAtBetween(
                userId, TradeOrderStatus.FILLED, request.getFromDate(), request.getToDate());
        
        // Calculate metrics
        BigDecimal totalVolume = calculateTotalVolume(trades);
        BigDecimal totalPnL = calculateTotalPnL(trades);
        BigDecimal totalFees = calculateTotalFees(trades);
        Map<String, BigDecimal> assetAllocation = calculateAssetAllocation(userId);
        
        // Calculate risk metrics
        BigDecimal sharpeRatio = calculateSharpeRatio(trades);
        BigDecimal maxDrawdown = calculateMaxDrawdown(trades);
        BigDecimal volatility = calculateVolatility(trades);
        
        // Get top performing assets
        List<AssetPerformance> topPerformers = getTopPerformingAssets(trades, 5);
        
        return PortfolioAnalytics.builder()
                .userId(userId)
                .totalVolume(totalVolume)
                .totalPnL(totalPnL)
                .totalPnLPercentage(totalPnL.divide(totalVolume, 4, RoundingMode.HALF_UP))
                .totalFees(totalFees)
                .totalTrades(trades.size())
                .winRate(calculateWinRate(trades))
                .assetAllocation(assetAllocation)
                .sharpeRatio(sharpeRatio)
                .maxDrawdown(maxDrawdown)
                .volatility(volatility)
                .topPerformers(topPerformers)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // Helper methods

    private void validateDCARequest(DCAOrderRequest request) {
        if (request.getInvestmentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Investment amount must be positive");
        }
        if (request.getNumberOfOrders() <= 0) {
            throw new IllegalArgumentException("Number of orders must be positive");
        }
        if (request.getFrequency() == null) {
            throw new IllegalArgumentException("Frequency must be specified");
        }
    }

    private void validateAlgorithmicRequest(AlgorithmicTradeRequest request) {
        if (!algoEngine.isStrategySupported(request.getStrategyType())) {
            throw new UnsupportedStrategyException("Strategy not supported: " + request.getStrategyType());
        }
        // Additional validation based on strategy type
    }

    private void validateGridTradingRequest(GridTradingRequest request) {
        if (request.getUpperPrice().compareTo(request.getLowerPrice()) <= 0) {
            throw new IllegalArgumentException("Upper price must be greater than lower price");
        }
        if (request.getInvestmentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Investment amount must be positive");
        }
    }

    private LocalDateTime calculateNextExecution(DCAFrequency frequency) {
        LocalDateTime now = LocalDateTime.now();
        switch (frequency) {
            case DAILY:
                return now.plusDays(1);
            case WEEKLY:
                return now.plusWeeks(1);
            case MONTHLY:
                return now.plusMonths(1);
            default:
                throw new IllegalArgumentException("Unsupported frequency: " + frequency);
        }
    }

    private List<GridLevel> calculateGridLevels(GridTradingRequest request, BigDecimal currentPrice) {
        List<GridLevel> levels = new ArrayList<>();
        
        BigDecimal priceRange = request.getUpperPrice().subtract(request.getLowerPrice());
        BigDecimal levelSpacing = priceRange.divide(new BigDecimal(request.getGridLevels()), 8, RoundingMode.HALF_UP);
        
        for (int i = 0; i <= request.getGridLevels(); i++) {
            BigDecimal levelPrice = request.getLowerPrice().add(levelSpacing.multiply(new BigDecimal(i)));
            
            GridLevel level = GridLevel.builder()
                    .level(i)
                    .price(levelPrice)
                    .quantity(request.getInvestmentAmount().divide(new BigDecimal(request.getGridLevels()), 8, RoundingMode.HALF_UP))
                    .isActive(true)
                    .build();
            
            levels.add(level);
        }
        
        return levels;
    }

    private BigDecimal calculateRequiredBalance(List<GridLevel> gridLevels, GridTradingRequest request) {
        return gridLevels.stream()
                .map(level -> level.getPrice().multiply(level.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TradeOrder> createGridOrders(UUID userId, GridTradingStrategy strategy, List<GridLevel> levels) {
        List<TradeOrder> orders = new ArrayList<>();
        
        for (GridLevel level : levels) {
            // Create buy order below current price
            if (level.getPrice().compareTo(strategy.getCurrentPrice()) < 0) {
                TradeOrder buyOrder = createGridBuyOrder(userId, strategy, level);
                orders.add(tradeOrderRepository.save(buyOrder));
            }
            // Create sell order above current price
            else if (level.getPrice().compareTo(strategy.getCurrentPrice()) > 0) {
                TradeOrder sellOrder = createGridSellOrder(userId, strategy, level);
                orders.add(tradeOrderRepository.save(sellOrder));
            }
        }
        
        return orders;
    }

    private TradeOrder createGridBuyOrder(UUID userId, GridTradingStrategy strategy, GridLevel level) {
        return TradeOrder.builder()
                .userId(userId)
                .gridStrategyId(strategy.getId())
                .orderType(TradeOrderType.LIMIT)
                .side("BUY")
                .quantity(level.getQuantity())
                .price(level.getPrice())
                .status(TradeOrderStatus.PENDING)
                .timeInForce("GTC")
                .build();
    }

    private TradeOrder createGridSellOrder(UUID userId, GridTradingStrategy strategy, GridLevel level) {
        return TradeOrder.builder()
                .userId(userId)
                .gridStrategyId(strategy.getId())
                .orderType(TradeOrderType.LIMIT)
                .side("SELL")
                .quantity(level.getQuantity())
                .price(level.getPrice())
                .status(TradeOrderStatus.PENDING)
                .timeInForce("GTC")
                .build();
    }

    private ArbitrageOpportunity validateArbitrageOpportunity(ArbitrageRequest request) {
        BigDecimal buyPrice = pricingService.getExchangePrice(request.getBuyExchange(), request.getCryptocurrency());
        BigDecimal sellPrice = pricingService.getExchangePrice(request.getSellExchange(), request.getCryptocurrency());
        
        BigDecimal priceDiff = sellPrice.subtract(buyPrice);
        BigDecimal profitPercentage = priceDiff.divide(buyPrice, 4, RoundingMode.HALF_UP);
        
        return ArbitrageOpportunity.builder()
                .buyPrice(buyPrice)
                .sellPrice(sellPrice)
                .priceDifference(priceDiff)
                .profitPercentage(profitPercentage)
                .exchangeRate(sellPrice.divide(buyPrice, 8, RoundingMode.HALF_UP))
                .build();
    }

    private BigDecimal calculateTrailingStopPrice(BigDecimal currentPrice, BigDecimal trailPercent, String side) {
        BigDecimal trailAmount = currentPrice.multiply(trailPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        
        if ("SELL".equals(side)) {
            return currentPrice.subtract(trailAmount);
        } else {
            return currentPrice.add(trailAmount);
        }
    }

    private void startTrailingStopMonitoring(TrailingStopOrder trailingStop) {
        log.info("Starting trailing stop monitoring for order: {}", trailingStop.getId());
        
        CompletableFuture.runAsync(() -> {
            try {
                while (trailingStop.getStatus() == TrailingStopStatus.ACTIVE) {
                    BigDecimal currentPrice = pricingService.getCurrentPrice(trailingStop.getCryptocurrency());
                    
                    if (currentPrice.compareTo(trailingStop.getHighestPrice()) > 0) {
                        trailingStop.setHighestPrice(currentPrice);
                        BigDecimal newStopPrice = calculateTrailingStopPrice(
                                currentPrice, trailingStop.getTrailPercent(), "SELL");
                        trailingStop.setStopPrice(newStopPrice);
                        trailingStopRepository.save(trailingStop);
                        
                        log.info("Updated trailing stop - Order: {}, HighestPrice: {}, NewStopPrice: {}",
                                trailingStop.getId(), currentPrice, newStopPrice);
                    }
                    
                    if (currentPrice.compareTo(trailingStop.getStopPrice()) <= 0) {
                        log.warn("Trailing stop triggered - Order: {}, CurrentPrice: {}, StopPrice: {}",
                                trailingStop.getId(), currentPrice, trailingStop.getStopPrice());
                        
                        executeStopLossSale(trailingStop, currentPrice);
                        trailingStop.setStatus(TrailingStopStatus.TRIGGERED);
                        trailingStopRepository.save(trailingStop);
                        
                        eventPublisher.publish(TrailingStopTriggeredEvent.builder()
                                .orderId(trailingStop.getId())
                                .userId(trailingStop.getUserId())
                                .cryptocurrency(trailingStop.getCryptocurrency())
                                .quantity(trailingStop.getQuantity())
                                .executionPrice(currentPrice)
                                .build());
                        
                        break;
                    }
                    
                    Thread.sleep(5000);
                }
            } catch (InterruptedException e) {
                log.error("Trailing stop monitoring interrupted for order: {}", trailingStop.getId(), e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error in trailing stop monitoring for order: {}", trailingStop.getId(), e);
            }
        });
    }
    
    private void executeStopLossSale(TrailingStopOrder trailingStop, BigDecimal currentPrice) {
        try {
            log.info("Executing stop loss sale - Order: {}, Price: {}", trailingStop.getId(), currentPrice);
            
            TradeOrder sellOrder = TradeOrder.builder()
                    .userId(trailingStop.getUserId())
                    .trailingStopId(trailingStop.getId())
                    .orderType(TradeOrderType.MARKET)
                    .side("SELL")
                    .cryptocurrency(trailingStop.getCryptocurrency())
                    .quantity(trailingStop.getQuantity())
                    .price(currentPrice)
                    .status(TradeOrderStatus.PENDING)
                    .timeInForce("IOC")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            tradeOrderRepository.save(sellOrder);
            matchingEngine.submitOrder(sellOrder);
            
            walletService.releaseCryptoBalance(trailingStop.getUserId(), 
                    trailingStop.getCryptocurrency(), trailingStop.getId());
            
            log.info("Stop loss sale executed - Order: {}, SellOrder: {}", 
                    trailingStop.getId(), sellOrder.getOrderId());
            
        } catch (Exception e) {
            log.error("Failed to execute stop loss sale for order: {}", trailingStop.getId(), e);
            throw new RuntimeException("Stop loss execution failed", e);
        }
    }

    // Analytics calculation methods
    private BigDecimal calculateTotalVolume(List<TradeOrder> trades) {
        return trades.stream()
                .map(TradeOrder::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalPnL(List<TradeOrder> trades) {
        // Calculate realized P&L by matching buy/sell pairs using FIFO method
        Map<String, Queue<TradeOrder>> buyOrdersBySymbol = new HashMap<>();
        BigDecimal totalPnL = BigDecimal.ZERO;
        
        // Separate buy and sell orders
        for (TradeOrder trade : trades) {
            if (trade.getSide() == OrderSide.BUY) {
                buyOrdersBySymbol.computeIfAbsent(trade.getSymbol(), k -> new LinkedList<>()).add(trade);
            } else if (trade.getSide() == OrderSide.SELL) {
                totalPnL = totalPnL.add(calculatePnLForSell(trade, buyOrdersBySymbol.get(trade.getSymbol())));
            }
        }
        
        return totalPnL.setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculatePnLForSell(TradeOrder sellOrder, Queue<TradeOrder> buyOrders) {
        if (buyOrders == null || buyOrders.isEmpty()) {
            return BigDecimal.ZERO; // No matching buys
        }
        
        BigDecimal pnl = BigDecimal.ZERO;
        BigDecimal remainingQuantity = sellOrder.getQuantity();
        
        while (!buyOrders.isEmpty() && remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            TradeOrder buyOrder = buyOrders.peek();
            BigDecimal matchQuantity = remainingQuantity.min(buyOrder.getQuantity());
            
            // Calculate P&L for this match
            BigDecimal costBasis = buyOrder.getPrice().multiply(matchQuantity);
            BigDecimal saleValue = sellOrder.getPrice().multiply(matchQuantity);
            pnl = pnl.add(saleValue.subtract(costBasis));
            
            // Update remaining quantities
            remainingQuantity = remainingQuantity.subtract(matchQuantity);
            buyOrder.setQuantity(buyOrder.getQuantity().subtract(matchQuantity));
            
            if (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                buyOrders.poll(); // Remove fully matched buy order
            }
        }
        
        return pnl;
    }

    private BigDecimal calculateTotalFees(List<TradeOrder> trades) {
        return trades.stream()
                .map(TradeOrder::getTradingFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> calculateAssetAllocation(UUID userId) {
        // Get current balances for all cryptocurrencies
        return walletService.getAllCryptoBalances(userId).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue()
                ));
    }

    private BigDecimal calculateSharpeRatio(List<TradeOrder> trades) {
        if (trades.size() < 2) {
            return BigDecimal.ZERO; // Need at least 2 trades for calculation
        }
        
        // Calculate daily returns
        List<BigDecimal> dailyReturns = calculateDailyReturns(trades);
        if (dailyReturns.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Calculate average return
        BigDecimal avgReturn = dailyReturns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(dailyReturns.size()), 6, RoundingMode.HALF_UP);
        
        // Calculate standard deviation
        BigDecimal variance = dailyReturns.stream()
            .map(ret -> ret.subtract(avgReturn).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(dailyReturns.size() - 1), 6, RoundingMode.HALF_UP);
        
        BigDecimal stdDev = BigDecimalMath.sqrt(variance);
        
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // Avoid division by zero
        }
        
        // Risk-free rate assumption (2% annual = 0.0548% daily)
        BigDecimal riskFreeRate = new BigDecimal("0.000548");
        BigDecimal excessReturn = avgReturn.subtract(riskFreeRate);
        
        return excessReturn.divide(stdDev, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMaxDrawdown(List<TradeOrder> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Sort trades by timestamp to build equity curve
        List<TradeOrder> sortedTrades = trades.stream()
            .sorted(Comparator.comparing(TradeOrder::getCreatedAt))
            .collect(Collectors.toList());
        
        BigDecimal runningPnL = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        
        for (TradeOrder trade : sortedTrades) {
            // Add realized P&L from this trade
            runningPnL = runningPnL.add(calculateTradeReturns(trade));
            
            // Update peak if new high
            if (runningPnL.compareTo(peak) > 0) {
                peak = runningPnL;
            }
            
            // Calculate current drawdown
            BigDecimal currentDrawdown = peak.subtract(runningPnL);
            
            // Update max drawdown if current is larger
            if (currentDrawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = currentDrawdown;
            }
        }
        
        return maxDrawdown.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVolatility(List<TradeOrder> trades) {
        List<BigDecimal> dailyReturns = calculateDailyReturns(trades);
        
        if (dailyReturns.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Calculate mean return
        BigDecimal meanReturn = dailyReturns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(dailyReturns.size()), 6, RoundingMode.HALF_UP);
        
        // Calculate variance
        BigDecimal variance = dailyReturns.stream()
            .map(ret -> ret.subtract(meanReturn).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(dailyReturns.size() - 1), 6, RoundingMode.HALF_UP);
        
        // Return standard deviation (volatility) with high precision
        return BigDecimalMath.sqrt(variance)
            .setScale(4, RoundingMode.HALF_UP);
    }

    private List<AssetPerformance> getTopPerformingAssets(List<TradeOrder> trades, int limit) {
        // Group trades by cryptocurrency and calculate performance
        return trades.stream()
                .collect(Collectors.groupingBy(trade -> trade.getTradePair().getTradeCurrency().getSymbol()))
                .entrySet().stream()
                .map(entry -> calculateAssetPerformance(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> b.getReturnPercentage().compareTo(a.getReturnPercentage()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private AssetPerformance calculateAssetPerformance(String symbol, List<TradeOrder> trades) {
        if (trades.isEmpty()) {
            return AssetPerformance.builder()
                    .symbol(symbol)
                    .totalTrades(0)
                    .totalVolume(BigDecimal.ZERO)
                    .returnPercentage(BigDecimal.ZERO)
                    .build();
        }
        
        Queue<TradeOrder> buyQueue = new LinkedList<>();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalReturns = BigDecimal.ZERO;
        int profitableTrades = 0;
        
        for (TradeOrder trade : trades) {
            if (trade.getSide() == OrderSide.BUY) {
                buyQueue.add(trade);
                totalInvested = totalInvested.add(trade.getTotalValue());
            } else if (trade.getSide() == OrderSide.SELL && !buyQueue.isEmpty()) {
                TradeOrder matchedBuy = buyQueue.poll();
                BigDecimal costBasis = matchedBuy.getPrice().multiply(trade.getQuantity());
                BigDecimal saleValue = trade.getPrice().multiply(trade.getQuantity());
                BigDecimal tradeProfit = saleValue.subtract(costBasis);
                
                totalReturns = totalReturns.add(tradeProfit);
                if (tradeProfit.compareTo(BigDecimal.ZERO) > 0) {
                    profitableTrades++;
                }
            }
        }
        
        BigDecimal returnPercentage = totalInvested.compareTo(BigDecimal.ZERO) > 0 ?
                totalReturns.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
        
        BigDecimal winRate = trades.size() > 0 ?
                BigDecimal.valueOf(profitableTrades).divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
        
        return AssetPerformance.builder()
                .symbol(symbol)
                .totalTrades(trades.size())
                .totalVolume(calculateTotalVolume(trades))
                .returnPercentage(returnPercentage)
                .totalReturns(totalReturns)
                .winRate(winRate)
                .build();
    }

    private double calculateWinRate(List<TradeOrder> trades) {
        if (trades.isEmpty()) {
            return 0.0;
        }
        
        Map<String, Queue<TradeOrder>> buyOrdersBySymbol = new HashMap<>();
        int profitableTrades = 0;
        int totalCompletedTrades = 0;
        
        for (TradeOrder trade : trades) {
            if (trade.getSide() == OrderSide.BUY) {
                buyOrdersBySymbol.computeIfAbsent(trade.getSymbol(), k -> new LinkedList<>()).add(trade);
            } else if (trade.getSide() == OrderSide.SELL) {
                Queue<TradeOrder> buyQueue = buyOrdersBySymbol.get(trade.getSymbol());
                if (buyQueue != null && !buyQueue.isEmpty()) {
                    TradeOrder matchedBuy = buyQueue.poll();
                    BigDecimal costBasis = matchedBuy.getPrice().multiply(trade.getQuantity());
                    BigDecimal saleValue = trade.getPrice().multiply(trade.getQuantity());
                    
                    if (saleValue.compareTo(costBasis) > 0) {
                        profitableTrades++;
                    }
                    totalCompletedTrades++;
                }
            }
        }
        
        return totalCompletedTrades > 0 ? 
                (double) profitableTrades / totalCompletedTrades * 100.0 : 0.0;
    }

    // DTO mapping methods
    private DCAOrderResponse mapToDCAOrderResponse(DCAOrder dcaOrder) {
        return DCAOrderResponse.builder()
                .id(dcaOrder.getId())
                .cryptocurrency(dcaOrder.getCryptocurrency())
                .investmentAmount(dcaOrder.getInvestmentAmount())
                .frequency(dcaOrder.getFrequency())
                .numberOfOrders(dcaOrder.getNumberOfOrders())
                .completedOrders(dcaOrder.getCompletedOrders())
                .totalInvested(dcaOrder.getTotalInvested())
                .averagePrice(dcaOrder.getAveragePrice())
                .status(dcaOrder.getStatus())
                .nextExecutionTime(dcaOrder.getNextExecutionTime())
                .build();
    }

    private GridTradingResponse mapToGridTradingResponse(GridTradingStrategy strategy, List<TradeOrder> orders) {
        return GridTradingResponse.builder()
                .id(strategy.getId())
                .tradePair(strategy.getTradePair())
                .gridLevels(strategy.getGridLevels())
                .upperPrice(strategy.getUpperPrice())
                .lowerPrice(strategy.getLowerPrice())
                .investmentAmount(strategy.getInvestmentAmount())
                .status(strategy.getStatus())
                .totalProfit(strategy.getTotalProfit())
                .completedTrades(strategy.getCompletedTrades())
                .activeOrders(orders.size())
                .build();
    }

    private TrailingStopResponse mapToTrailingStopResponse(TrailingStopOrder trailingStop) {
        return TrailingStopResponse.builder()
                .id(trailingStop.getId())
                .cryptocurrency(trailingStop.getCryptocurrency())
                .quantity(trailingStop.getQuantity())
                .trailPercent(trailingStop.getTrailPercent())
                .currentPrice(trailingStop.getCurrentPrice())
                .stopPrice(trailingStop.getStopPrice())
                .status(trailingStop.getStatus())
                .build();
    }

    // Placeholder methods for complex operations
    private void executeDCAOrder(DCAOrder dcaOrder) {
        // Implementation for executing DCA order
    }

    private void validateSufficientBalance(UUID userId, String currency, BigDecimal amount) {
        // Implementation for balance validation
    }

    private void validateArbitrageBalances(UUID userId, ArbitrageRequest request, BigDecimal buyAmount, BigDecimal sellAmount) {
        // Implementation for arbitrage balance validation
    }

    private TradeOrder executeBuyOrder(UUID userId, String exchange, String cryptocurrency, BigDecimal amount, BigDecimal price) {
        // Implementation for executing buy order
        return new TradeOrder();
    }

    private TradeOrder executeSellOrder(UUID userId, String exchange, String cryptocurrency, BigDecimal amount, BigDecimal price) {
        // Implementation for executing sell order
        return new TradeOrder();
    }

    private BigDecimal calculateArbitrageProfit(TradeOrder buyOrder, TradeOrder sellOrder) {
        return sellOrder.getTotalValue().subtract(buyOrder.getTotalValue());
    }

    private void recordArbitrageExecution(UUID userId, ArbitrageRequest request, ArbitrageResult result) {
        // Implementation for recording arbitrage execution
    }

    // Repositories that would need to be created
    private DCAOrderRepository dcaOrderRepository;
    private GridTradingRepository gridTradingRepository;
    private TrailingStopRepository trailingStopRepository;
}