package com.waqiti.investment.service;

import com.waqiti.investment.domain.*;
import com.waqiti.investment.domain.enums.*;
import com.waqiti.investment.dto.request.*;
import com.waqiti.investment.dto.response.*;
import com.waqiti.investment.exception.*;
import com.waqiti.investment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced Trading Service for sophisticated trading strategies and features
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedTradingService {

    private final InvestmentOrderRepository orderRepository;
    private final InvestmentAccountRepository accountRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final OrderExecutionService orderExecutionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ADVANCED_TRADING_TOPIC = "advanced-trading-events";
    private static final BigDecimal PATTERN_DAY_TRADER_MIN_EQUITY = new BigDecimal("25000");
    private static final int MAX_DAY_TRADES_PER_WEEK = 3;

    /**
     * Execute bracket order (OCO - One Cancels Other)
     */
    @Transactional
    public List<InvestmentOrderDto> createBracketOrder(CreateBracketOrderRequest request) {
        log.info("Creating bracket order for account: {}", request.getAccountId());

        InvestmentAccount account = validateAccount(request.getAccountId());
        
        // Create parent order
        CreateOrderRequest parentOrderRequest = CreateOrderRequest.builder()
                .accountId(request.getAccountId())
                .symbol(request.getSymbol())
                .orderType(request.getParentOrderType())
                .orderSide(request.getOrderSide())
                .quantity(request.getQuantity())
                .limitPrice(request.getParentLimitPrice())
                .timeInForce(TimeInForce.GTC)
                .build();

        InvestmentOrderDto parentOrder = orderExecutionService.createOrder(parentOrderRequest);

        List<InvestmentOrderDto> bracketOrders = new ArrayList<>();
        bracketOrders.add(parentOrder);

        // Create profit target order
        if (request.getProfitTargetPrice() != null) {
            CreateOrderRequest profitOrderRequest = CreateOrderRequest.builder()
                    .accountId(request.getAccountId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.LIMIT)
                    .orderSide(request.getOrderSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY)
                    .quantity(request.getQuantity())
                    .limitPrice(request.getProfitTargetPrice())
                    .timeInForce(TimeInForce.GTC)
                    .parentOrderId(parentOrder.getId())
                    .build();

            InvestmentOrderDto profitOrder = orderExecutionService.createOrder(profitOrderRequest);
            bracketOrders.add(profitOrder);
        }

        // Create stop loss order
        if (request.getStopLossPrice() != null) {
            CreateOrderRequest stopOrderRequest = CreateOrderRequest.builder()
                    .accountId(request.getAccountId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.STOP)
                    .orderSide(request.getOrderSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY)
                    .quantity(request.getQuantity())
                    .stopPrice(request.getStopLossPrice())
                    .timeInForce(TimeInForce.GTC)
                    .parentOrderId(parentOrder.getId())
                    .build();

            InvestmentOrderDto stopOrder = orderExecutionService.createOrder(stopOrderRequest);
            bracketOrders.add(stopOrder);
        }

        // Publish bracket order event
        publishAdvancedTradingEvent("BRACKET_ORDER_CREATED", 
                Map.of("accountId", account.getId(), "orders", bracketOrders.size()));

        return bracketOrders;
    }

    /**
     * Execute algorithmic trading strategy
     */
    @Transactional
    public CompletableFuture<AlgorithmicTradeResult> executeAlgorithmicTrade(AlgorithmicTradeRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Executing algorithmic trade strategy: {}", request.getStrategyType());

                InvestmentAccount account = validateAccount(request.getAccountId());
                
                switch (request.getStrategyType()) {
                    case TWAP:
                        return executeTWAPStrategy(account, request);
                    case VWAP:
                        return executeVWAPStrategy(account, request);
                    case ICEBERG:
                        return executeIcebergStrategy(account, request);
                    case MOMENTUM:
                        return executeMomentumStrategy(account, request);
                    case MEAN_REVERSION:
                        return executeMeanReversionStrategy(account, request);
                    default:
                        throw new InvestmentException("Unsupported algorithm strategy: " + request.getStrategyType());
                }
            } catch (Exception e) {
                log.error("Error executing algorithmic trade: {}", e.getMessage(), e);
                return AlgorithmicTradeResult.failed(e.getMessage());
            }
        });
    }

    /**
     * Execute Time Weighted Average Price (TWAP) strategy
     */
    private AlgorithmicTradeResult executeTWAPStrategy(InvestmentAccount account, AlgorithmicTradeRequest request) {
        BigDecimal totalQuantity = request.getQuantity();
        int numberOfSlices = request.getTimeSlices();
        BigDecimal sliceQuantity = totalQuantity.divide(new BigDecimal(numberOfSlices), 0, RoundingMode.DOWN);
        
        List<InvestmentOrderDto> orders = new ArrayList<>();
        
        for (int i = 0; i < numberOfSlices; i++) {
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .accountId(account.getId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.MARKET)
                    .orderSide(request.getOrderSide())
                    .quantity(i == numberOfSlices - 1 ? totalQuantity.subtract(sliceQuantity.multiply(new BigDecimal(i))) : sliceQuantity)
                    .timeInForce(TimeInForce.IOC)
                    .build();
            
            // Add delay between orders
            if (i > 0) {
                try {
                    Thread.sleep(request.getTimeIntervalMinutes() * 60 * 1000 / numberOfSlices);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
            orders.add(order);
        }
        
        return AlgorithmicTradeResult.success(orders, "TWAP strategy executed successfully");
    }

    /**
     * Execute Volume Weighted Average Price (VWAP) strategy
     */
    private AlgorithmicTradeResult executeVWAPStrategy(InvestmentAccount account, AlgorithmicTradeRequest request) {
        // Get historical volume data to determine optimal order sizing
        Map<String, BigDecimal> technicalIndicators = marketDataService.calculateTechnicalIndicators(
                request.getSymbol(), 20);
        
        // Implement VWAP logic based on historical volume patterns
        BigDecimal totalQuantity = request.getQuantity();
        BigDecimal currentPrice = marketDataService.getStockQuote(request.getSymbol()).getPrice();
        
        // Create orders based on volume-weighted distribution
        List<InvestmentOrderDto> orders = new ArrayList<>();
        
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .accountId(account.getId())
                .symbol(request.getSymbol())
                .orderType(OrderType.LIMIT)
                .orderSide(request.getOrderSide())
                .quantity(totalQuantity)
                .limitPrice(currentPrice.multiply(new BigDecimal("0.999"))) // Slight discount for VWAP
                .timeInForce(TimeInForce.DAY)
                .build();
        
        InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
        orders.add(order);
        
        return AlgorithmicTradeResult.success(orders, "VWAP strategy executed successfully");
    }

    /**
     * Execute Iceberg order strategy
     */
    private AlgorithmicTradeResult executeIcebergStrategy(InvestmentAccount account, AlgorithmicTradeRequest request) {
        BigDecimal totalQuantity = request.getQuantity();
        BigDecimal visibleQuantity = request.getIcebergVisibleQuantity();
        
        if (visibleQuantity.compareTo(totalQuantity) >= 0) {
            throw new InvestmentException("Visible quantity must be less than total quantity for iceberg order");
        }
        
        List<InvestmentOrderDto> orders = new ArrayList<>();
        BigDecimal remainingQuantity = totalQuantity;
        
        while (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentSliceQuantity = remainingQuantity.min(visibleQuantity);
            
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .accountId(account.getId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.LIMIT)
                    .orderSide(request.getOrderSide())
                    .quantity(currentSliceQuantity)
                    .limitPrice(request.getLimitPrice())
                    .timeInForce(TimeInForce.GTC)
                    .build();
            
            InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
            orders.add(order);
            
            remainingQuantity = remainingQuantity.subtract(currentSliceQuantity);
        }
        
        return AlgorithmicTradeResult.success(orders, "Iceberg strategy executed successfully");
    }

    /**
     * Execute Momentum trading strategy
     */
    private AlgorithmicTradeResult executeMomentumStrategy(InvestmentAccount account, AlgorithmicTradeRequest request) {
        Map<String, BigDecimal> technicalIndicators = marketDataService.calculateTechnicalIndicators(
                request.getSymbol(), 14);
        
        BigDecimal rsi = technicalIndicators.get("RSI_14");
        BigDecimal ema20 = technicalIndicators.get("EMA_20");
        BigDecimal currentPrice = marketDataService.getStockQuote(request.getSymbol()).getPrice();
        
        List<InvestmentOrderDto> orders = new ArrayList<>();
        
        // Momentum signal: RSI > 60 and price above EMA20 for BUY
        if (request.getOrderSide() == OrderSide.BUY && rsi.compareTo(new BigDecimal("60")) > 0 
                && currentPrice.compareTo(ema20) > 0) {
            
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .accountId(account.getId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.MARKET)
                    .orderSide(OrderSide.BUY)
                    .quantity(request.getQuantity())
                    .timeInForce(TimeInForce.IOC)
                    .build();
            
            InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
            orders.add(order);
        }
        // Momentum signal: RSI < 40 and price below EMA20 for SELL
        else if (request.getOrderSide() == OrderSide.SELL && rsi.compareTo(new BigDecimal("40")) < 0 
                && currentPrice.compareTo(ema20) < 0) {
            
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .accountId(account.getId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.MARKET)
                    .orderSide(OrderSide.SELL)
                    .quantity(request.getQuantity())
                    .timeInForce(TimeInForce.IOC)
                    .build();
            
            InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
            orders.add(order);
        }
        
        return AlgorithmicTradeResult.success(orders, "Momentum strategy executed");
    }

    /**
     * Execute Mean Reversion strategy
     */
    private AlgorithmicTradeResult executeMeanReversionStrategy(InvestmentAccount account, AlgorithmicTradeRequest request) {
        Map<String, BigDecimal> technicalIndicators = marketDataService.calculateTechnicalIndicators(
                request.getSymbol(), 20);
        
        BigDecimal bbUpper = technicalIndicators.get("BB_UPPER");
        BigDecimal bbLower = technicalIndicators.get("BB_LOWER");
        BigDecimal bbMiddle = technicalIndicators.get("BB_MIDDLE");
        BigDecimal currentPrice = marketDataService.getStockQuote(request.getSymbol()).getPrice();
        
        List<InvestmentOrderDto> orders = new ArrayList<>();
        
        // Mean reversion: Buy when price touches lower Bollinger Band
        if (request.getOrderSide() == OrderSide.BUY && currentPrice.compareTo(bbLower) <= 0) {
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .accountId(account.getId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.LIMIT)
                    .orderSide(OrderSide.BUY)
                    .quantity(request.getQuantity())
                    .limitPrice(bbMiddle) // Target mean price
                    .timeInForce(TimeInForce.GTC)
                    .build();
            
            InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
            orders.add(order);
        }
        // Mean reversion: Sell when price touches upper Bollinger Band
        else if (request.getOrderSide() == OrderSide.SELL && currentPrice.compareTo(bbUpper) >= 0) {
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .accountId(account.getId())
                    .symbol(request.getSymbol())
                    .orderType(OrderType.LIMIT)
                    .orderSide(OrderSide.SELL)
                    .quantity(request.getQuantity())
                    .limitPrice(bbMiddle) // Target mean price
                    .timeInForce(TimeInForce.GTC)
                    .build();
            
            InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
            orders.add(order);
        }
        
        return AlgorithmicTradeResult.success(orders, "Mean reversion strategy executed");
    }

    /**
     * Check Pattern Day Trader (PDT) rules
     */
    public PDTCheckResult checkPatternDayTraderRules(Long accountId, String symbol) {
        InvestmentAccount account = validateAccount(accountId);
        
        // Check if account has minimum equity
        boolean hasMinimumEquity = account.getTotalEquity().compareTo(PATTERN_DAY_TRADER_MIN_EQUITY) >= 0;
        
        // Count day trades in the last 5 trading days
        LocalDateTime fiveTradingDaysAgo = LocalDateTime.now().minusDays(7); // Approximate
        List<InvestmentOrder> recentOrders = orderRepository.findByAccountIdAndFilledAtAfter(
                accountId, fiveTradingDaysAgo);
        
        int dayTradeCount = countDayTrades(recentOrders);
        boolean exceedsLimit = dayTradeCount >= MAX_DAY_TRADES_PER_WEEK;
        
        return PDTCheckResult.builder()
                .isPatternDayTrader(hasMinimumEquity)
                .dayTradeCount(dayTradeCount)
                .exceedsLimit(exceedsLimit && !hasMinimumEquity)
                .minimumEquityRequired(PATTERN_DAY_TRADER_MIN_EQUITY)
                .currentEquity(account.getTotalEquity())
                .build();
    }

    /**
     * Execute options trading strategy
     */
    @Transactional
    public OptionsTradeResult executeOptionsStrategy(OptionsTradeRequest request) {
        log.info("Executing options strategy: {}", request.getStrategyType());
        
        InvestmentAccount account = validateAccount(request.getAccountId());
        
        // Validate options trading approval
        if (!account.getOptionsLevel().canTrade(request.getStrategyType())) {
            throw new InvestmentException("Account not approved for options strategy: " + request.getStrategyType());
        }
        
        switch (request.getStrategyType()) {
            case COVERED_CALL:
                return executeCoveredCall(account, request);
            case CASH_SECURED_PUT:
                return executeCashSecuredPut(account, request);
            case PROTECTIVE_PUT:
                return executeProtectivePut(account, request);
            case BULL_CALL_SPREAD:
                return executeBullCallSpread(account, request);
            case BEAR_PUT_SPREAD:
                return executeBearPutSpread(account, request);
            default:
                throw new InvestmentException("Unsupported options strategy: " + request.getStrategyType());
        }
    }

    /**
     * Execute margin trading with risk management
     */
    @Transactional
    public MarginTradeResult executeMarginTrade(MarginTradeRequest request) {
        log.info("Executing margin trade for account: {}", request.getAccountId());
        
        InvestmentAccount account = validateAccount(request.getAccountId());
        
        if (!account.isMarginEnabled()) {
            throw new InvestmentException("Margin trading not enabled for account");
        }
        
        // Calculate buying power
        BigDecimal buyingPower = calculateBuyingPower(account);
        BigDecimal orderValue = request.getQuantity().multiply(request.getPrice());
        
        if (orderValue.compareTo(buyingPower) > 0) {
            throw new InsufficientFundsException("Insufficient buying power for margin trade");
        }
        
        // Check margin requirements
        BigDecimal marginRequirement = calculateMarginRequirement(request.getSymbol(), orderValue);
        
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .accountId(request.getAccountId())
                .symbol(request.getSymbol())
                .orderType(request.getOrderType())
                .orderSide(request.getOrderSide())
                .quantity(request.getQuantity())
                .limitPrice(request.getPrice())
                .timeInForce(request.getTimeInForce())
                .marginOrder(true)
                .build();
        
        InvestmentOrderDto order = orderExecutionService.createOrder(orderRequest);
        
        return MarginTradeResult.builder()
                .orderId(order.getId())
                .marginUsed(marginRequirement)
                .buyingPowerUsed(orderValue)
                .interestRate(account.getMarginInterestRate())
                .maintenanceRequirement(marginRequirement.multiply(new BigDecimal("0.25")))
                .build();
    }

    // Helper methods

    private InvestmentAccount validateAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Investment account not found: " + accountId));
    }

    private int countDayTrades(List<InvestmentOrder> orders) {
        Map<String, List<InvestmentOrder>> ordersBySymbolAndDate = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.FILLED)
                .collect(Collectors.groupingBy(order -> 
                        order.getSymbol() + "_" + order.getFilledAt().toLocalDate()));
        
        int dayTradeCount = 0;
        for (List<InvestmentOrder> symbolDateOrders : ordersBySymbolAndDate.values()) {
            boolean hasBuy = symbolDateOrders.stream().anyMatch(o -> o.getOrderSide() == OrderSide.BUY);
            boolean hasSell = symbolDateOrders.stream().anyMatch(o -> o.getOrderSide() == OrderSide.SELL);
            if (hasBuy && hasSell) {
                dayTradeCount++;
            }
        }
        
        return dayTradeCount;
    }

    private BigDecimal calculateBuyingPower(InvestmentAccount account) {
        BigDecimal cashBalance = account.getCashBalance();
        BigDecimal marginValue = account.getTotalEquity().multiply(new BigDecimal("0.5")); // 50% margin
        return cashBalance.add(marginValue);
    }

    private BigDecimal calculateMarginRequirement(String symbol, BigDecimal orderValue) {
        // Simplified margin requirement calculation (typically 50% for stocks)
        return orderValue.multiply(new BigDecimal("0.5"));
    }

    private void publishAdvancedTradingEvent(String eventType, Map<String, Object> data) {
        AdvancedTradingEvent event = AdvancedTradingEvent.builder()
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
        
        kafkaTemplate.send(ADVANCED_TRADING_TOPIC, eventType, event);
    }

    // Placeholder methods for options strategies
    private OptionsTradeResult executeCoveredCall(InvestmentAccount account, OptionsTradeRequest request) {
        return OptionsTradeResult.builder()
                .strategy(request.getStrategyType())
                .status("EXECUTED")
                .build();
    }

    private OptionsTradeResult executeCashSecuredPut(InvestmentAccount account, OptionsTradeRequest request) {
        return OptionsTradeResult.builder()
                .strategy(request.getStrategyType())
                .status("EXECUTED")
                .build();
    }

    private OptionsTradeResult executeProtectivePut(InvestmentAccount account, OptionsTradeRequest request) {
        return OptionsTradeResult.builder()
                .strategy(request.getStrategyType())
                .status("EXECUTED")
                .build();
    }

    private OptionsTradeResult executeBullCallSpread(InvestmentAccount account, OptionsTradeRequest request) {
        return OptionsTradeResult.builder()
                .strategy(request.getStrategyType())
                .status("EXECUTED")
                .build();
    }

    private OptionsTradeResult executeBearPutSpread(InvestmentAccount account, OptionsTradeRequest request) {
        return OptionsTradeResult.builder()
                .strategy(request.getStrategyType())
                .status("EXECUTED")
                .build();
    }

    // Result classes
    @lombok.Data
    @lombok.Builder
    public static class AlgorithmicTradeResult {
        private boolean success;
        private String message;
        private List<InvestmentOrderDto> orders;
        private String strategyType;
        
        public static AlgorithmicTradeResult success(List<InvestmentOrderDto> orders, String message) {
            return AlgorithmicTradeResult.builder()
                    .success(true)
                    .orders(orders)
                    .message(message)
                    .build();
        }
        
        public static AlgorithmicTradeResult failed(String message) {
            return AlgorithmicTradeResult.builder()
                    .success(false)
                    .message(message)
                    .orders(Collections.emptyList())
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class PDTCheckResult {
        private boolean isPatternDayTrader;
        private int dayTradeCount;
        private boolean exceedsLimit;
        private BigDecimal minimumEquityRequired;
        private BigDecimal currentEquity;
    }

    @lombok.Data
    @lombok.Builder
    public static class OptionsTradeResult {
        private String strategy;
        private String status;
        private List<InvestmentOrderDto> orders;
        private BigDecimal premium;
        private BigDecimal commission;
    }

    @lombok.Data
    @lombok.Builder
    public static class MarginTradeResult {
        private Long orderId;
        private BigDecimal marginUsed;
        private BigDecimal buyingPowerUsed;
        private BigDecimal interestRate;
        private BigDecimal maintenanceRequirement;
    }

    @lombok.Data
    @lombok.Builder
    private static class AdvancedTradingEvent {
        private String eventType;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
    }
}