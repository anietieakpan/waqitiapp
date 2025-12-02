package com.waqiti.crypto.trading;

import com.waqiti.common.exceptions.BusinessException;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.crypto.trading.dto.*;
import com.waqiti.crypto.trading.engine.OrderMatchingEngine;
import com.waqiti.crypto.trading.market.MarketDataService;
import com.waqiti.crypto.trading.liquidity.LiquidityProvider;
import com.waqiti.crypto.trading.repository.OrderRepository;
import com.waqiti.crypto.trading.repository.TradeRepository;
import com.waqiti.crypto.wallet.CryptoWalletService;
import com.waqiti.payment.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoTradingExchangeService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final OrderMatchingEngine matchingEngine;
    private final MarketDataService marketDataService;
    private final CryptoWalletService cryptoWalletService;
    private final WalletService fiatWalletService;
    private final LiquidityProvider liquidityProvider;
    private final PriceOracleService priceOracle;
    private final ComplianceService complianceService;
    private final RiskManagementService riskManagementService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final WebClient binanceClient;
    private final WebClient coinbaseClient;
    
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> haltedPairs = new ConcurrentHashMap<>();
    
    @Value("${crypto.trading.maker.fee:0.001}")
    private BigDecimal makerFeeRate;
    
    @Value("${crypto.trading.taker.fee:0.002}")
    private BigDecimal takerFeeRate;
    
    @Value("${crypto.trading.min.order:10}")
    private BigDecimal minOrderValue;
    
    @Value("${crypto.trading.max.slippage:0.05}")
    private BigDecimal maxSlippage;
    
    @Value("${crypto.trading.settlement.delay:2}")
    private int settlementDelaySeconds;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Order placeLimitOrder(LimitOrderRequest request) {
        log.info("Placing limit order: {} {} {} at {}", 
                request.getSide(), request.getQuantity(), request.getSymbol(), request.getPrice());
        
        // Validate trading pair
        validateTradingPair(request.getSymbol());
        
        // Check if trading is halted
        checkTradingHalted(request.getSymbol());
        
        // Validate order
        validateOrder(request);
        
        // Check user limits and compliance
        performComplianceCheck(request);
        
        // Check and lock funds
        lockFundsForOrder(request);
        
        // Create order
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(OrderType.LIMIT)
                .status(OrderStatus.PENDING)
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .timeInForce(request.getTimeInForce())
                .postOnly(request.isPostOnly())
                .reduceOnly(request.isReduceOnly())
                .createdAt(Instant.now())
                .build();
        
        // Calculate fees
        order.setEstimatedFee(calculateEstimatedFee(order, true));
        
        // Save order
        order = orderRepository.save(order);
        
        // Submit to matching engine
        submitToMatchingEngine(order);
        
        // Send confirmation
        notificationService.sendOrderPlacedNotification(order);
        
        log.info("Limit order placed: {}", order.getId());
        return order;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Order placeMarketOrder(MarketOrderRequest request) {
        log.info("Placing market order: {} {} {}", 
                request.getSide(), request.getQuantity(), request.getSymbol());
        
        // Validate trading pair
        validateTradingPair(request.getSymbol());
        
        // Check if trading is halted
        checkTradingHalted(request.getSymbol());
        
        // Get current market price
        BigDecimal marketPrice = getMarketPrice(request.getSymbol(), request.getSide());
        
        // Calculate slippage
        BigDecimal estimatedSlippage = calculateSlippage(request);
        if (estimatedSlippage.compareTo(maxSlippage) > 0) {
            throw new BusinessException("Estimated slippage too high: " + 
                    estimatedSlippage.multiply(BigDecimal.valueOf(100)) + "%");
        }
        
        // Create order
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(OrderType.MARKET)
                .status(OrderStatus.PENDING)
                .quantity(request.getQuantity())
                .remainingQuantity(request.getQuantity())
                .estimatedPrice(marketPrice)
                .maxSlippage(request.getMaxSlippage())
                .createdAt(Instant.now())
                .build();
        
        // Calculate required funds with slippage
        BigDecimal requiredFunds = calculateRequiredFunds(order, estimatedSlippage);
        
        // Lock funds
        lockFundsForMarketOrder(order, requiredFunds);
        
        // Save order
        order = orderRepository.save(order);
        
        // Execute immediately
        executeMarketOrder(order);
        
        log.info("Market order placed: {}", order.getId());
        return order;
    }

    @Transactional
    public Order placeStopLossOrder(StopLossOrderRequest request) {
        log.info("Placing stop-loss order: {} at {}", request.getSymbol(), request.getStopPrice());
        
        validateTradingPair(request.getSymbol());
        
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .symbol(request.getSymbol())
                .side(OrderSide.SELL)
                .type(OrderType.STOP_LOSS)
                .status(OrderStatus.PENDING)
                .quantity(request.getQuantity())
                .stopPrice(request.getStopPrice())
                .limitPrice(request.getLimitPrice())
                .remainingQuantity(request.getQuantity())
                .triggerable(true)
                .createdAt(Instant.now())
                .build();
        
        // Lock the crypto assets
        cryptoWalletService.lockForStopLoss(
                securityContext.getUserId(),
                getCryptoSymbol(request.getSymbol()),
                request.getQuantity()
        );
        
        order = orderRepository.save(order);
        
        // Add to monitoring
        addToStopLossMonitoring(order);
        
        return order;
    }

    @Transactional
    public SwapResult instantSwap(SwapRequest request) {
        log.info("Processing instant swap: {} {} to {}", 
                request.getFromAmount(), request.getFromCurrency(), request.getToCurrency());
        
        // Get swap quote
        SwapQuote quote = getSwapQuote(request);
        
        // Validate quote
        if (quote.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Quote expired");
        }
        
        // Check slippage
        if (request.getMaxSlippage() != null) {
            BigDecimal actualSlippage = calculateSwapSlippage(quote, request);
            if (actualSlippage.compareTo(request.getMaxSlippage()) > 0) {
                throw new BusinessException("Slippage exceeds maximum allowed");
            }
        }
        
        // Lock source funds
        lockSwapFunds(request.getFromCurrency(), request.getFromAmount());
        
        // Execute swap
        SwapResult result = SwapResult.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .fromCurrency(request.getFromCurrency())
                .fromAmount(request.getFromAmount())
                .toCurrency(request.getToCurrency())
                .toAmount(quote.getToAmount())
                .exchangeRate(quote.getRate())
                .fee(quote.getFee())
                .status(SwapStatus.PROCESSING)
                .createdAt(Instant.now())
                .build();
        
        // Process through liquidity provider
        processSwapThroughLiquidityProvider(result);
        
        // Update balances
        updateSwapBalances(result);
        
        result.setStatus(SwapStatus.COMPLETED);
        result.setCompletedAt(Instant.now());
        
        // Send confirmation
        notificationService.sendSwapConfirmation(result);
        
        log.info("Swap completed: {}", result.getId());
        return result;
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        log.info("Cancelling order: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found"));
        
        // Verify ownership
        if (!order.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to order");
        }
        
        // Check if order can be cancelled
        if (!canCancelOrder(order)) {
            throw new BusinessException("Order cannot be cancelled in status: " + order.getStatus());
        }
        
        // Remove from matching engine
        matchingEngine.removeOrder(order);
        
        // Release locked funds
        releaseFundsForOrder(order);
        
        // Update status
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        orderRepository.save(order);
        
        // Send notification
        notificationService.sendOrderCancelledNotification(order);
        
        log.info("Order cancelled: {}", orderId);
    }

    @Cacheable(value = "market-ticker", key = "#symbol")
    public MarketTicker getTicker(String symbol) {
        OrderBook orderBook = getOrderBook(symbol);
        
        BigDecimal lastPrice = getLastTradePrice(symbol);
        BigDecimal change24h = calculate24hChange(symbol);
        BigDecimal volume24h = calculate24hVolume(symbol);
        
        return MarketTicker.builder()
                .symbol(symbol)
                .lastPrice(lastPrice)
                .bid(orderBook.getBestBid())
                .ask(orderBook.getBestAsk())
                .change24h(change24h)
                .changePercent24h(calculateChangePercent(change24h, lastPrice))
                .volume24h(volume24h)
                .high24h(get24hHigh(symbol))
                .low24h(get24hLow(symbol))
                .timestamp(Instant.now())
                .build();
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, k -> {
            OrderBook book = new OrderBook(symbol);
            loadOrderBookFromDatabase(book);
            return book;
        });
    }

    public List<Trade> getRecentTrades(String symbol, int limit) {
        return tradeRepository.findRecentBySymbol(symbol, limit);
    }

    public TradingChart getCandlestickData(String symbol, Interval interval, int limit) {
        List<Candlestick> candles = marketDataService.getCandlesticks(symbol, interval, limit);
        
        return TradingChart.builder()
                .symbol(symbol)
                .interval(interval)
                .candlesticks(candles)
                .indicators(calculateIndicators(candles))
                .build();
    }

    public Portfolio getUserPortfolio() {
        UUID userId = securityContext.getUserId();
        
        // Get crypto holdings
        Map<String, CryptoBalance> cryptoBalances = cryptoWalletService.getUserBalances(userId);
        
        // Get fiat balances
        Map<String, BigDecimal> fiatBalances = fiatWalletService.getUserFiatBalances(userId);
        
        // Calculate total value
        BigDecimal totalValue = calculatePortfolioValue(cryptoBalances, fiatBalances);
        
        // Get P&L
        ProfitLoss pnl = calculateProfitLoss(userId);
        
        // Get allocation
        List<AssetAllocation> allocation = calculateAssetAllocation(cryptoBalances, fiatBalances);
        
        return Portfolio.builder()
                .userId(userId)
                .cryptoBalances(cryptoBalances)
                .fiatBalances(fiatBalances)
                .totalValue(totalValue)
                .totalValueUSD(convertToUSD(totalValue))
                .profitLoss(pnl)
                .allocation(allocation)
                .lastUpdated(Instant.now())
                .build();
    }

    @Transactional
    public StakingPosition stake(StakingRequest request) {
        log.info("Staking {} {}", request.getAmount(), request.getAsset());
        
        // Validate staking asset
        if (!isStakingSupported(request.getAsset())) {
            throw new BusinessException("Staking not supported for " + request.getAsset());
        }
        
        // Check minimum staking amount
        BigDecimal minStaking = getMinimumStakingAmount(request.getAsset());
        if (request.getAmount().compareTo(minStaking) < 0) {
            throw new BusinessException("Minimum staking amount is " + minStaking);
        }
        
        // Lock tokens for staking
        cryptoWalletService.lockForStaking(
                securityContext.getUserId(),
                request.getAsset(),
                request.getAmount()
        );
        
        // Create staking position
        StakingPosition position = StakingPosition.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .asset(request.getAsset())
                .amount(request.getAmount())
                .apy(getCurrentAPY(request.getAsset()))
                .lockPeriod(request.getLockPeriod())
                .status(StakingStatus.ACTIVE)
                .startedAt(Instant.now())
                .maturesAt(Instant.now().plus(Duration.ofDays(request.getLockPeriod())))
                .build();
        
        stakingRepository.save(position);
        
        // Schedule rewards calculation
        scheduleRewardsCalculation(position);
        
        log.info("Staking position created: {}", position.getId());
        return position;
    }

    @Transactional
    public LendingPosition lend(LendingRequest request) {
        log.info("Creating lending position: {} {} at {}% APR", 
                request.getAmount(), request.getAsset(), request.getInterestRate());
        
        // Validate lending parameters
        validateLendingRequest(request);
        
        // Check collateral if required
        if (request.isCollateralRequired()) {
            validateCollateral(request);
        }
        
        // Lock funds for lending
        lockFundsForLending(request);
        
        // Create lending position
        LendingPosition position = LendingPosition.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .asset(request.getAsset())
                .amount(request.getAmount())
                .interestRate(request.getInterestRate())
                .term(request.getTerm())
                .collateralAsset(request.getCollateralAsset())
                .collateralAmount(request.getCollateralAmount())
                .status(LendingStatus.ACTIVE)
                .nextPaymentDate(calculateNextPaymentDate(request.getTerm()))
                .maturityDate(calculateMaturityDate(request.getTerm()))
                .createdAt(Instant.now())
                .build();
        
        lendingRepository.save(position);
        
        // Match with borrowers
        matchWithBorrowers(position);
        
        return position;
    }

    @Scheduled(fixedDelay = 1000) // Run every second
    public void processOrderMatching() {
        for (String symbol : getActiveSymbols()) {
            try {
                OrderBook orderBook = getOrderBook(symbol);
                List<Trade> trades = matchingEngine.matchOrders(orderBook);
                
                for (Trade trade : trades) {
                    processTrade(trade);
                }
            } catch (Exception e) {
                log.error("Error processing order matching for {}", symbol, e);
            }
        }
    }

    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    public void updateMarketData() {
        for (String symbol : getActiveSymbols()) {
            try {
                // Fetch external market data
                MarketData externalData = fetchExternalMarketData(symbol);
                
                // Update internal price oracle
                priceOracle.updatePrice(symbol, externalData.getPrice());
                
                // Check for arbitrage opportunities
                checkArbitrageOpportunity(symbol, externalData);
                
            } catch (Exception e) {
                log.error("Error updating market data for {}", symbol, e);
            }
        }
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void calculateStakingRewards() {
        log.info("Calculating staking rewards");
        
        List<StakingPosition> activePositions = stakingRepository.findActivePositions();
        
        for (StakingPosition position : activePositions) {
            try {
                BigDecimal rewards = calculateRewards(position);
                
                if (rewards.compareTo(BigDecimal.ZERO) > 0) {
                    // Credit rewards
                    cryptoWalletService.creditStakingRewards(
                            position.getUserId(),
                            position.getAsset(),
                            rewards
                    );
                    
                    // Update position
                    position.setAccumulatedRewards(
                            position.getAccumulatedRewards().add(rewards)
                    );
                    position.setLastRewardAt(Instant.now());
                    stakingRepository.save(position);
                }
            } catch (Exception e) {
                log.error("Error calculating rewards for position {}", position.getId(), e);
            }
        }
    }

    private void submitToMatchingEngine(Order order) {
        OrderBook orderBook = getOrderBook(order.getSymbol());
        
        if (order.isPostOnly()) {
            // Check if order would match immediately
            if (wouldMatchImmediately(order, orderBook)) {
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Post-only order would match immediately");
                orderRepository.save(order);
                releaseFundsForOrder(order);
                return;
            }
        }
        
        // Add to order book
        orderBook.addOrder(order);
        
        // Trigger matching
        List<Trade> trades = matchingEngine.matchOrder(order, orderBook);
        
        for (Trade trade : trades) {
            processTrade(trade);
        }
        
        // Update order status
        if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus(OrderStatus.FILLED);
            order.setFilledAt(Instant.now());
        } else {
            order.setStatus(OrderStatus.OPEN);
        }
        
        orderRepository.save(order);
    }

    private void executeMarketOrder(Order order) {
        OrderBook orderBook = getOrderBook(order.getSymbol());
        BigDecimal remainingQuantity = order.getQuantity();
        BigDecimal totalExecutedValue = BigDecimal.ZERO;
        List<Trade> trades = new ArrayList<>();
        
        List<Order> counterOrders = order.getSide() == OrderSide.BUY ?
                orderBook.getAsks() : orderBook.getBids();
        
        for (Order counterOrder : counterOrders) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal tradeQuantity = remainingQuantity.min(counterOrder.getRemainingQuantity());
            BigDecimal tradePrice = counterOrder.getPrice();
            
            Trade trade = Trade.builder()
                    .id(UUID.randomUUID())
                    .symbol(order.getSymbol())
                    .buyOrderId(order.getSide() == OrderSide.BUY ? order.getId() : counterOrder.getId())
                    .sellOrderId(order.getSide() == OrderSide.SELL ? order.getId() : counterOrder.getId())
                    .price(tradePrice)
                    .quantity(tradeQuantity)
                    .buyerUserId(order.getSide() == OrderSide.BUY ? order.getUserId() : counterOrder.getUserId())
                    .sellerUserId(order.getSide() == OrderSide.SELL ? order.getUserId() : counterOrder.getUserId())
                    .timestamp(Instant.now())
                    .build();
            
            trades.add(trade);
            remainingQuantity = remainingQuantity.subtract(tradeQuantity);
            totalExecutedValue = totalExecutedValue.add(tradeQuantity.multiply(tradePrice));
            
            // Update counter order
            counterOrder.setRemainingQuantity(counterOrder.getRemainingQuantity().subtract(tradeQuantity));
            if (counterOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) == 0) {
                counterOrder.setStatus(OrderStatus.FILLED);
                counterOrder.setFilledAt(Instant.now());
            }
            orderRepository.save(counterOrder);
        }
        
        // Process all trades
        for (Trade trade : trades) {
            processTrade(trade);
        }
        
        // Update market order
        order.setRemainingQuantity(remainingQuantity);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus(OrderStatus.FILLED);
            order.setAveragePrice(totalExecutedValue.divide(order.getQuantity(), 8, RoundingMode.HALF_UP));
        } else {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
        order.setFilledAt(Instant.now());
        orderRepository.save(order);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    private void processTrade(Trade trade) {
        log.debug("Processing trade: {} {} at {}", trade.getQuantity(), trade.getSymbol(), trade.getPrice());
        
        // Save trade
        trade = tradeRepository.save(trade);
        
        // Calculate fees
        BigDecimal makerFee = trade.getQuantity().multiply(trade.getPrice()).multiply(makerFeeRate);
        BigDecimal takerFee = trade.getQuantity().multiply(trade.getPrice()).multiply(takerFeeRate);
        
        // Update buyer balance
        updateBuyerBalance(trade, takerFee);
        
        // Update seller balance
        updateSellerBalance(trade, makerFee);
        
        // Update market data
        updateMarketDataAfterTrade(trade);
        
        // Send notifications
        notifyTradeExecution(trade);
        
        // Trigger settlement
        scheduleSettlement(trade);
    }

    private void updateBuyerBalance(Trade trade, BigDecimal fee) {
        String baseCurrency = getBaseCurrency(trade.getSymbol());
        String quoteCurrency = getQuoteCurrency(trade.getSymbol());
        
        // Credit base currency
        cryptoWalletService.credit(
                trade.getBuyerUserId(),
                baseCurrency,
                trade.getQuantity(),
                "Trade buy: " + trade.getId()
        );
        
        // Debit quote currency + fee
        BigDecimal totalCost = trade.getQuantity().multiply(trade.getPrice()).add(fee);
        if (isFiatCurrency(quoteCurrency)) {
            fiatWalletService.debit(
                    trade.getBuyerUserId(),
                    totalCost,
                    quoteCurrency,
                    "Trade buy: " + trade.getId()
            );
        } else {
            cryptoWalletService.debit(
                    trade.getBuyerUserId(),
                    quoteCurrency,
                    totalCost,
                    "Trade buy: " + trade.getId()
            );
        }
    }

    private void updateSellerBalance(Trade trade, BigDecimal fee) {
        String baseCurrency = getBaseCurrency(trade.getSymbol());
        String quoteCurrency = getQuoteCurrency(trade.getSymbol());
        
        // Debit base currency
        cryptoWalletService.debit(
                trade.getSellerUserId(),
                baseCurrency,
                trade.getQuantity(),
                "Trade sell: " + trade.getId()
        );
        
        // Credit quote currency - fee
        BigDecimal totalRevenue = trade.getQuantity().multiply(trade.getPrice()).subtract(fee);
        if (isFiatCurrency(quoteCurrency)) {
            fiatWalletService.credit(
                    trade.getSellerUserId(),
                    totalRevenue,
                    quoteCurrency,
                    "Trade sell: " + trade.getId()
            );
        } else {
            cryptoWalletService.credit(
                    trade.getSellerUserId(),
                    quoteCurrency,
                    totalRevenue,
                    "Trade sell: " + trade.getId()
            );
        }
    }

    private void validateOrder(LimitOrderRequest request) {
        // Check minimum order value
        BigDecimal orderValue = request.getQuantity().multiply(request.getPrice());
        if (orderValue.compareTo(minOrderValue) < 0) {
            throw new BusinessException("Minimum order value is $" + minOrderValue);
        }
        
        // Check tick size
        if (!isValidPrice(request.getPrice(), request.getSymbol())) {
            throw new BusinessException("Invalid price for tick size");
        }
        
        // Check lot size
        if (!isValidQuantity(request.getQuantity(), request.getSymbol())) {
            throw new BusinessException("Invalid quantity for lot size");
        }
    }

    private void performComplianceCheck(LimitOrderRequest request) {
        ComplianceResult result = complianceService.checkTrading(
                securityContext.getUserId(),
                request.getSymbol(),
                request.getQuantity().multiply(request.getPrice())
        );
        
        if (!result.isApproved()) {
            throw new BusinessException("Compliance check failed: " + result.getReason());
        }
    }

    private void lockFundsForOrder(LimitOrderRequest request) {
        if (request.getSide() == OrderSide.BUY) {
            // Lock quote currency
            String quoteCurrency = getQuoteCurrency(request.getSymbol());
            BigDecimal requiredAmount = request.getQuantity().multiply(request.getPrice());
            
            if (isFiatCurrency(quoteCurrency)) {
                fiatWalletService.lock(
                        securityContext.getUserId(),
                        requiredAmount,
                        quoteCurrency
                );
            } else {
                cryptoWalletService.lock(
                        securityContext.getUserId(),
                        quoteCurrency,
                        requiredAmount
                );
            }
        } else {
            // Lock base currency
            String baseCurrency = getBaseCurrency(request.getSymbol());
            cryptoWalletService.lock(
                    securityContext.getUserId(),
                    baseCurrency,
                    request.getQuantity()
            );
        }
    }

    private void lockFundsForMarketOrder(Order order, BigDecimal requiredFunds) {
        if (order.getSide() == OrderSide.BUY) {
            String quoteCurrency = getQuoteCurrency(order.getSymbol());
            if (isFiatCurrency(quoteCurrency)) {
                fiatWalletService.lock(
                        order.getUserId(),
                        requiredFunds,
                        quoteCurrency
                );
            } else {
                cryptoWalletService.lock(
                        order.getUserId(),
                        quoteCurrency,
                        requiredFunds
                );
            }
        } else {
            String baseCurrency = getBaseCurrency(order.getSymbol());
            cryptoWalletService.lock(
                    order.getUserId(),
                    baseCurrency,
                    order.getQuantity()
            );
        }
    }

    private void releaseFundsForOrder(Order order) {
        if (order.getSide() == OrderSide.BUY) {
            String quoteCurrency = getQuoteCurrency(order.getSymbol());
            BigDecimal remainingAmount = order.getRemainingQuantity().multiply(order.getPrice());
            
            if (isFiatCurrency(quoteCurrency)) {
                fiatWalletService.unlock(
                        order.getUserId(),
                        remainingAmount,
                        quoteCurrency
                );
            } else {
                cryptoWalletService.unlock(
                        order.getUserId(),
                        quoteCurrency,
                        remainingAmount
                );
            }
        } else {
            String baseCurrency = getBaseCurrency(order.getSymbol());
            cryptoWalletService.unlock(
                    order.getUserId(),
                    baseCurrency,
                    order.getRemainingQuantity()
            );
        }
    }

    private BigDecimal getMarketPrice(String symbol, OrderSide side) {
        OrderBook orderBook = getOrderBook(symbol);
        
        if (side == OrderSide.BUY) {
            return orderBook.getBestAsk();
        } else {
            return orderBook.getBestBid();
        }
    }

    private BigDecimal calculateSlippage(MarketOrderRequest request) {
        OrderBook orderBook = getOrderBook(request.getSymbol());
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        
        List<Order> orders = request.getSide() == OrderSide.BUY ? 
                orderBook.getAsks() : orderBook.getBids();
        
        for (Order order : orders) {
            BigDecimal availableQuantity = order.getRemainingQuantity();
            BigDecimal neededQuantity = request.getQuantity().subtract(totalQuantity);
            BigDecimal tradeQuantity = availableQuantity.min(neededQuantity);
            
            totalQuantity = totalQuantity.add(tradeQuantity);
            totalValue = totalValue.add(tradeQuantity.multiply(order.getPrice()));
            
            if (totalQuantity.compareTo(request.getQuantity()) >= 0) {
                break;
            }
        }
        
        if (totalQuantity.compareTo(request.getQuantity()) < 0) {
            throw new BusinessException("Insufficient liquidity");
        }
        
        BigDecimal averagePrice = totalValue.divide(totalQuantity, 8, RoundingMode.HALF_UP);
        BigDecimal marketPrice = getMarketPrice(request.getSymbol(), request.getSide());
        
        return averagePrice.subtract(marketPrice).abs()
                .divide(marketPrice, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRequiredFunds(Order order, BigDecimal slippage) {
        BigDecimal baseAmount = order.getQuantity().multiply(order.getEstimatedPrice());
        BigDecimal slippageAmount = baseAmount.multiply(slippage);
        BigDecimal feeAmount = baseAmount.multiply(takerFeeRate);
        
        return baseAmount.add(slippageAmount).add(feeAmount);
    }

    private SwapQuote getSwapQuote(SwapRequest request) {
        // Get best rate from multiple sources
        List<SwapQuote> quotes = new ArrayList<>();
        
        // Internal liquidity pool
        quotes.add(liquidityProvider.getQuote(request));
        
        // External DEX aggregator
        quotes.add(getExternalQuote(request));
        
        // Select best quote
        return quotes.stream()
                .min(Comparator.comparing(SwapQuote::getFee))
                .orElseThrow(() -> new BusinessException("No swap quotes available"));
    }

    private void processSwapThroughLiquidityProvider(SwapResult result) {
        liquidityProvider.executeSwap(
                result.getFromCurrency(),
                result.getFromAmount(),
                result.getToCurrency(),
                result.getToAmount()
        );
    }

    private void updateSwapBalances(SwapResult result) {
        // Debit source
        if (isFiatCurrency(result.getFromCurrency())) {
            fiatWalletService.debit(
                    result.getUserId(),
                    result.getFromAmount(),
                    result.getFromCurrency(),
                    "Swap: " + result.getId()
            );
        } else {
            cryptoWalletService.debit(
                    result.getUserId(),
                    result.getFromCurrency(),
                    result.getFromAmount(),
                    "Swap: " + result.getId()
            );
        }
        
        // Credit destination
        if (isFiatCurrency(result.getToCurrency())) {
            fiatWalletService.credit(
                    result.getUserId(),
                    result.getToAmount().subtract(result.getFee()),
                    result.getToCurrency(),
                    "Swap: " + result.getId()
            );
        } else {
            cryptoWalletService.credit(
                    result.getUserId(),
                    result.getToCurrency(),
                    result.getToAmount().subtract(result.getFee()),
                    "Swap: " + result.getId()
            );
        }
    }

    private void lockSwapFunds(String currency, BigDecimal amount) {
        if (isFiatCurrency(currency)) {
            fiatWalletService.lock(
                    securityContext.getUserId(),
                    amount,
                    currency
            );
        } else {
            cryptoWalletService.lock(
                    securityContext.getUserId(),
                    currency,
                    amount
            );
        }
    }

    private void validateTradingPair(String symbol) {
        if (!isValidTradingPair(symbol)) {
            throw new BusinessException("Invalid trading pair: " + symbol);
        }
    }

    private void checkTradingHalted(String symbol) {
        if (haltedPairs.getOrDefault(symbol, new AtomicBoolean(false)).get()) {
            throw new BusinessException("Trading halted for " + symbol);
        }
    }

    private boolean canCancelOrder(Order order) {
        return order.getStatus() == OrderStatus.OPEN || 
               order.getStatus() == OrderStatus.PENDING ||
               order.getStatus() == OrderStatus.PARTIALLY_FILLED;
    }

    private void addToStopLossMonitoring(Order order) {
        stopLossMonitor.addOrder(order);
    }

    private boolean wouldMatchImmediately(Order order, OrderBook orderBook) {
        if (order.getSide() == OrderSide.BUY) {
            return orderBook.getBestAsk() != null && 
                   order.getPrice().compareTo(orderBook.getBestAsk()) >= 0;
        } else {
            return orderBook.getBestBid() != null && 
                   order.getPrice().compareTo(orderBook.getBestBid()) <= 0;
        }
    }

    private BigDecimal calculateEstimatedFee(Order order, boolean isMaker) {
        BigDecimal feeRate = isMaker ? makerFeeRate : takerFeeRate;
        return order.getQuantity().multiply(order.getPrice()).multiply(feeRate);
    }

    private void scheduleSettlement(Trade trade) {
        settlementScheduler.schedule(
                () -> settleTrade(trade),
                settlementDelaySeconds,
                TimeUnit.SECONDS
        );
    }

    private void settleTrade(Trade trade) {
        // Final settlement and clearing
        log.debug("Settling trade: {}", trade.getId());
        trade.setSettled(true);
        trade.setSettledAt(Instant.now());
        tradeRepository.save(trade);
    }

    private void notifyTradeExecution(Trade trade) {
        notificationService.sendTradeExecutedNotification(trade.getBuyerUserId(), trade);
        notificationService.sendTradeExecutedNotification(trade.getSellerUserId(), trade);
    }

    private List<String> getActiveSymbols() {
        return Arrays.asList("BTC/USD", "ETH/USD", "BTC/ETH", "SOL/USD", "MATIC/USD");
    }

    private String getBaseCurrency(String symbol) {
        return symbol.split("/")[0];
    }

    private String getQuoteCurrency(String symbol) {
        return symbol.split("/")[1];
    }

    private String getCryptoSymbol(String tradingPair) {
        return getBaseCurrency(tradingPair);
    }

    private boolean isFiatCurrency(String currency) {
        return Arrays.asList("USD", "EUR", "GBP", "JPY").contains(currency);
    }

    private boolean isValidTradingPair(String symbol) {
        return getActiveSymbols().contains(symbol);
    }

    private boolean isValidPrice(BigDecimal price, String symbol) {
        // Check tick size based on symbol
        return price.scale() <= 2;
    }

    private boolean isValidQuantity(BigDecimal quantity, String symbol) {
        // Check lot size based on symbol
        return quantity.scale() <= 8;
    }

    // Additional helper methods would continue...
}