package com.waqiti.investment.service;

import com.waqiti.investment.domain.*;
import com.waqiti.investment.dto.*;
import com.waqiti.investment.repository.*;
import com.waqiti.investment.provider.MarketDataProvider;
import com.waqiti.investment.exception.*;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.StockTradingEvent;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.InvestmentDataException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.social.service.SocialFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Stock Trading Service - CashApp-style simple stock trading with social features
 * 
 * Features:
 * - Fractional share investing starting from $1
 * - Social trading and sharing
 * - Simple market orders with instant execution
 * - Real-time price tracking
 * - Auto-invest with round-ups
 * - Popular stock discovery
 * - Investment insights and education
 * - Tax-loss harvesting
 * - Boosts and rewards for investing
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StockTradingService {
    
    @Lazy
    private final StockTradingService self;
    
    private final InvestmentAccountRepository accountRepository;
    private final StockOrderRepository orderRepository;
    private final StockHoldingRepository holdingRepository;
    private final StockWatchlistRepository watchlistRepository;
    private final StockBoostRepository boostRepository;
    private final TradingInsightRepository insightRepository;
    private final MarketDataProvider marketDataProvider;
    private final InvestmentService investmentService;
    private final SocialFeedService socialFeedService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final EncryptionService encryptionService;
    
    @Value("${stock.min-investment-amount:1.00}")
    private BigDecimal minInvestmentAmount;
    
    @Value("${stock.max-daily-investment:50000.00}")
    private BigDecimal maxDailyInvestment;
    
    @Value("${stock.social-sharing-enabled:true}")
    private boolean socialSharingEnabled;
    
    @Value("${stock.auto-invest-round-ups:true}")
    private boolean autoInvestRoundUpsEnabled;
    
    /**
     * Get popular stocks trending on the platform
     */
    @Cacheable(value = "popularStocks", expiry = 300) // 5 minutes
    public List<PopularStockDto> getPopularStocks(int limit) {
        log.debug("Getting popular stocks with limit: {}", limit);
        
        try {
            // Get most traded stocks on platform
            List<StockActivity> recentActivity = orderRepository.findRecentTradingActivity(
                Instant.now().minusHours(24), 
                limit * 3
            );
            
            Map<String, Integer> symbolCounts = recentActivity.stream()
                .collect(Collectors.groupingBy(
                    StockActivity::getSymbol,
                    Collectors.summingInt(activity -> 1)
                ));
            
            // Get top symbols and enrich with market data
            List<PopularStockDto> popularStocks = symbolCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    String symbol = entry.getKey();
                    int tradingCount = entry.getValue();
                    
                    try {
                        StockQuote quote = marketDataProvider.getRealTimeQuote(symbol);
                        CompanyProfile profile = marketDataProvider.getCompanyProfile(symbol);
                        
                        return PopularStockDto.builder()
                            .symbol(symbol)
                            .companyName(profile.getName())
                            .currentPrice(quote.getPrice())
                            .changePercent(quote.getChangePercent())
                            .volume(quote.getVolume())
                            .marketCap(profile.getMarketCap())
                            .tradingCount(tradingCount)
                            .logoUrl(profile.getLogoUrl())
                            .sector(profile.getSector())
                            .isTrending(tradingCount > 10)
                            .build();
                            
                    } catch (Exception e) {
                        log.error("CRITICAL: Failed to get stock data for symbol: {} - this affects investment recommendations", symbol, e);
                        throw new InvestmentDataException("Failed to retrieve stock data", e, symbol);
                    }
                })
                .collect(Collectors.toList());
            
            // Add some default popular stocks if not enough activity
            if (popularStocks.size() < limit) {
                List<String> defaultPopular = Arrays.asList(
                    "AAPL", "GOOGL", "AMZN", "TSLA", "MSFT", "NVDA", "META", "NFLX"
                );
                
                for (String symbol : defaultPopular) {
                    if (popularStocks.size() >= limit) break;
                    
                    if (popularStocks.stream().noneMatch(stock -> stock.getSymbol().equals(symbol))) {
                        try {
                            StockQuote quote = marketDataProvider.getRealTimeQuote(symbol);
                            CompanyProfile profile = marketDataProvider.getCompanyProfile(symbol);
                            
                            popularStocks.add(PopularStockDto.builder()
                                .symbol(symbol)
                                .companyName(profile.getName())
                                .currentPrice(quote.getPrice())
                                .changePercent(quote.getChangePercent())
                                .volume(quote.getVolume())
                                .marketCap(profile.getMarketCap())
                                .tradingCount(0)
                                .logoUrl(profile.getLogoUrl())
                                .sector(profile.getSector())
                                .isTrending(false)
                                .build());
                                
                        } catch (Exception e) {
                            log.warn("Failed to get default stock data for: {}", symbol, e);
                        }
                    }
                }
            }
            
            log.info("Retrieved {} popular stocks", popularStocks.size());
            return popularStocks;
            
        } catch (Exception e) {
            log.error("Error getting popular stocks", e);
            throw new BusinessException("Failed to get popular stocks");
        }
    }
    
    /**
     * Buy stock with fractional shares - CashApp style
     * CRITICAL FIX: Added @Transactional to ensure atomic financial operations
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public StockOrderDto buyStock(UUID userId, BuyStockRequest request) {
        log.info("User {} buying ${} of {}", userId, request.getAmount(), request.getSymbol());
        
        try {
            // Validate request
            validateBuyRequest(request);
            
            // Get user's investment account
            InvestmentAccount account = getOrCreateInvestmentAccount(userId);
            
            // Check buying power
            if (request.getAmount().compareTo(account.getBuyingPower()) > 0) {
                throw new InsufficientFundsException("Insufficient buying power");
            }
            
            // Get current stock quote
            StockQuote quote = marketDataProvider.getRealTimeQuote(request.getSymbol());
            
            // Calculate fractional shares
            BigDecimal shares = request.getAmount()
                .divide(quote.getPrice(), 6, RoundingMode.HALF_UP);
            
            // Create market order
            StockOrder order = StockOrder.builder()
                .userId(userId)
                .accountId(account.getId())
                .symbol(request.getSymbol())
                .orderType(StockOrderType.BUY)
                .amount(request.getAmount())
                .shares(shares)
                .estimatedPrice(quote.getPrice())
                .status(StockOrderStatus.PENDING)
                .isFractional(true)
                .socialMessage(request.getSocialMessage())
                .isPrivate(request.isPrivate())
                .createdAt(Instant.now())
                .build();
            
            order = orderRepository.save(order);
            
            // Execute order immediately (market order)
            executeStockOrder(order, quote);
            
            // Update account buying power
            account.setBuyingPower(account.getBuyingPower().subtract(request.getAmount()));
            accountRepository.save(account);
            
            // Create social activity if enabled
            if (socialSharingEnabled && !request.isPrivate()) {
                createSocialTradingActivity(userId, order, "bought");
            }
            
            // Send push notification
            notificationService.sendStockPurchaseNotification(userId, order);
            
            // Check for investment insights
            generateInvestmentInsight(userId, order);
            
            // Apply any active boosts
            applyStockBoosts(userId, order);
            
            log.info("Stock purchase completed - Order: {}", order.getId());
            
            return toStockOrderDto(order);
            
        } catch (Exception e) {
            log.error("Error buying stock for user: {}", userId, e);
            throw new BusinessException("Failed to buy stock: " + e.getMessage());
        }
    }
    
    /**
     * Sell stock position
     * CRITICAL FIX: Added @Transactional to ensure atomic financial operations
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public StockOrderDto sellStock(UUID userId, SellStockRequest request) {
        log.info("User {} selling ${} of {}", userId, request.getAmount(), request.getSymbol());
        
        try {
            // Get user's investment account
            InvestmentAccount account = getOrCreateInvestmentAccount(userId);
            
            // Get current holding
            StockHolding holding = holdingRepository.findByUserIdAndSymbol(userId, request.getSymbol())
                .orElseThrow(() -> new BusinessException("No position found for " + request.getSymbol()));
            
            // Get current stock quote
            StockQuote quote = marketDataProvider.getRealTimeQuote(request.getSymbol());
            BigDecimal currentValue = holding.getShares().multiply(quote.getPrice());
            
            // Validate sell amount
            if (request.getAmount().compareTo(currentValue) > 0) {
                throw new BusinessException("Cannot sell more than current position value");
            }
            
            // Calculate shares to sell
            BigDecimal sharesToSell = request.getAmount()
                .divide(quote.getPrice(), 6, RoundingMode.HALF_UP);
            
            if (sharesToSell.compareTo(holding.getShares()) > 0) {
                sharesToSell = holding.getShares(); // Sell all if amount exceeds position
            }
            
            // Create sell order
            StockOrder order = StockOrder.builder()
                .userId(userId)
                .accountId(account.getId())
                .symbol(request.getSymbol())
                .orderType(StockOrderType.SELL)
                .amount(request.getAmount())
                .shares(sharesToSell)
                .estimatedPrice(quote.getPrice())
                .status(StockOrderStatus.PENDING)
                .isFractional(true)
                .socialMessage(request.getSocialMessage())
                .isPrivate(request.isPrivate())
                .createdAt(Instant.now())
                .build();
            
            order = orderRepository.save(order);
            
            // Execute order immediately
            executeStockOrder(order, quote);
            
            // Update holding
            updateHoldingAfterSell(holding, order);
            
            // Update account cash balance
            BigDecimal proceeds = order.getExecutedShares().multiply(order.getExecutedPrice());
            account.setCashBalance(account.getCashBalance().add(proceeds));
            account.setBuyingPower(account.getBuyingPower().add(proceeds));
            accountRepository.save(account);
            
            // Create social activity
            if (socialSharingEnabled && !request.isPrivate()) {
                createSocialTradingActivity(userId, order, "sold");
            }
            
            // Send notification
            notificationService.sendStockSaleNotification(userId, order);
            
            // Check for tax-loss harvesting opportunity
            checkTaxLossHarvesting(userId, order);
            
            log.info("Stock sale completed - Order: {}", order.getId());
            
            return toStockOrderDto(order);
            
        } catch (Exception e) {
            log.error("Error selling stock for user: {}", userId, e);
            throw new BusinessException("Failed to sell stock: " + e.getMessage());
        }
    }
    
    /**
     * Get user's stock portfolio with current values
     */
    public StockPortfolioDto getStockPortfolio(UUID userId) {
        log.debug("Getting stock portfolio for user: {}", userId);
        
        try {
            InvestmentAccount account = getOrCreateInvestmentAccount(userId);
            List<StockHolding> holdings = holdingRepository.findByUserId(userId);
            
            BigDecimal totalValue = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal dayChange = BigDecimal.ZERO;
            
            List<StockPositionDto> positions = new ArrayList<>();
            
            for (StockHolding holding : holdings) {
                if (holding.getShares().compareTo(BigDecimal.ZERO) > 0) {
                    StockQuote quote = marketDataProvider.getRealTimeQuote(holding.getSymbol());
                    BigDecimal currentValue = holding.getShares().multiply(quote.getPrice());
                    BigDecimal gainLoss = currentValue.subtract(holding.getTotalCost());
                    BigDecimal gainLossPercent = holding.getTotalCost().compareTo(BigDecimal.ZERO) > 0 ?
                        gainLoss.divide(holding.getTotalCost(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
                    
                    // Calculate day change
                    BigDecimal dayChangeAmount = holding.getShares().multiply(quote.getDayChange());
                    
                    positions.add(StockPositionDto.builder()
                        .symbol(holding.getSymbol())
                        .companyName(holding.getCompanyName())
                        .shares(holding.getShares())
                        .averageCost(holding.getAverageCost())
                        .totalCost(holding.getTotalCost())
                        .currentValue(currentValue)
                        .currentPrice(quote.getPrice())
                        .gainLoss(gainLoss)
                        .gainLossPercent(gainLossPercent)
                        .dayChange(dayChangeAmount)
                        .dayChangePercent(quote.getChangePercent())
                        .logoUrl(holding.getLogoUrl())
                        .sector(holding.getSector())
                        .build());
                    
                    totalValue = totalValue.add(currentValue);
                    totalCost = totalCost.add(holding.getTotalCost());
                    dayChange = dayChange.add(dayChangeAmount);
                }
            }
            
            BigDecimal totalGainLoss = totalValue.subtract(totalCost);
            BigDecimal totalGainLossPercent = totalCost.compareTo(BigDecimal.ZERO) > 0 ?
                totalGainLoss.divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            
            BigDecimal dayChangePercent = totalValue.subtract(dayChange).compareTo(BigDecimal.ZERO) > 0 ?
                dayChange.divide(totalValue.subtract(dayChange), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            
            return StockPortfolioDto.builder()
                .accountId(account.getId())
                .totalValue(totalValue)
                .totalCost(totalCost)
                .cashBalance(account.getCashBalance())
                .buyingPower(account.getBuyingPower())
                .totalGainLoss(totalGainLoss)
                .totalGainLossPercent(totalGainLossPercent)
                .dayChange(dayChange)
                .dayChangePercent(dayChangePercent)
                .positions(positions)
                .positionCount(positions.size())
                .lastUpdated(Instant.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error getting stock portfolio for user: {}", userId, e);
            throw new BusinessException("Failed to get stock portfolio");
        }
    }
    
    /**
     * Get real-time stock quote with additional data
     */
    @Cacheable(value = "stockQuote", key = "#symbol", expiry = 30) // 30 seconds
    public EnhancedStockQuoteDto getEnhancedStockQuote(String symbol) {
        log.debug("Getting enhanced quote for: {}", symbol);
        
        try {
            StockQuote quote = marketDataProvider.getRealTimeQuote(symbol);
            CompanyProfile profile = marketDataProvider.getCompanyProfile(symbol);
            StockChart chart = marketDataProvider.getIntradayChart(symbol);
            
            // Get analyst recommendations
            List<AnalystRating> ratings = marketDataProvider.getAnalystRatings(symbol);
            String consensusRating = calculateConsensusRating(ratings);
            
            // Get key metrics
            StockMetrics metrics = marketDataProvider.getKeyMetrics(symbol);
            
            // Check if user's friends are trading this stock
            int friendsTrading = getFriendsTrading(symbol);
            
            return EnhancedStockQuoteDto.builder()
                .symbol(symbol)
                .companyName(profile.getName())
                .currentPrice(quote.getPrice())
                .changeAmount(quote.getDayChange())
                .changePercent(quote.getChangePercent())
                .volume(quote.getVolume())
                .avgVolume(quote.getAvgVolume())
                .marketCap(profile.getMarketCap())
                .peRatio(metrics.getPeRatio())
                .week52High(quote.getWeek52High())
                .week52Low(quote.getWeek52Low())
                .dividendYield(metrics.getDividendYield())
                .logoUrl(profile.getLogoUrl())
                .sector(profile.getSector())
                .industry(profile.getIndustry())
                .description(profile.getDescription())
                .intradayChart(chart.getDataPoints())
                .consensusRating(consensusRating)
                .analystPriceTarget(calculateAvgPriceTarget(ratings).orElse(null))
                .friendsTrading(friendsTrading)
                .isPopular(isPopularStock(symbol))
                .riskLevel(calculateRiskLevel(metrics))
                .build();
                
        } catch (Exception e) {
            log.error("Error getting enhanced quote for: {}", symbol, e);
            throw new BusinessException("Failed to get stock quote");
        }
    }
    
    /**
     * Search stocks with intelligent suggestions
     */
    public List<StockSearchResultDto> searchStocks(String query, int limit) {
        log.debug("Searching stocks for query: {}", query);
        
        try {
            List<StockSearchResult> results = marketDataProvider.searchStocks(query, limit);
            
            return results.stream()
                .map(result -> {
                    try {
                        StockQuote quote = marketDataProvider.getRealTimeQuote(result.getSymbol());
                        
                        return StockSearchResultDto.builder()
                            .symbol(result.getSymbol())
                            .companyName(result.getCompanyName())
                            .currentPrice(quote.getPrice())
                            .changePercent(quote.getChangePercent())
                            .marketCap(result.getMarketCap())
                            .logoUrl(result.getLogoUrl())
                            .sector(result.getSector())
                            .isPopular(isPopularStock(result.getSymbol()))
                            .isTradable(result.isTradable())
                            .build();
                            
                    } catch (Exception e) {
                        log.warn("Failed to enrich search result for: {}", result.getSymbol(), e);
                        
                        return StockSearchResultDto.builder()
                            .symbol(result.getSymbol())
                            .companyName(result.getCompanyName())
                            .marketCap(result.getMarketCap())
                            .sector(result.getSector())
                            .isTradable(result.isTradable())
                            .build();
                    }
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error searching stocks", e);
            throw new BusinessException("Failed to search stocks");
        }
    }
    
    /**
     * Set up automatic investing (round-ups, recurring)
     */
    public AutoInvestConfigDto setupAutoInvest(UUID userId, SetupAutoInvestRequest request) {
        log.info("Setting up auto-invest for user: {}", userId);
        
        try {
            InvestmentAccount account = getOrCreateInvestmentAccount(userId);
            
            // Validate request
            validateAutoInvestRequest(request);
            
            // Create auto-invest configuration
            AutoInvestConfig config = AutoInvestConfig.builder()
                .userId(userId)
                .accountId(account.getId())
                .roundUpEnabled(request.isRoundUpEnabled())
                .roundUpMultiplier(request.getRoundUpMultiplier())
                .recurringAmount(request.getRecurringAmount())
                .recurringFrequency(request.getRecurringFrequency())
                .targetStocks(request.getTargetStocks())
                .allocationPercentages(request.getAllocationPercentages())
                .maxDailyInvestment(request.getMaxDailyInvestment())
                .enabled(true)
                .createdAt(Instant.now())
                .build();
            
            config = autoInvestRepository.save(config);
            
            // Send notification
            notificationService.sendAutoInvestSetupNotification(userId, config);
            
            log.info("Auto-invest setup completed for user: {}", userId);
            
            return toAutoInvestConfigDto(config);
            
        } catch (Exception e) {
            log.error("Error setting up auto-invest for user: {}", userId, e);
            throw new BusinessException("Failed to setup auto-invest");
        }
    }
    
    /**
     * Process round-up investments from transactions
     */
    @Async
    public CompletableFuture<Void> processRoundUpInvestment(UUID userId, BigDecimal transactionAmount) {
        log.debug("Processing round-up investment for user: {}, amount: {}", userId, transactionAmount);
        
        try {
            Optional<AutoInvestConfig> configOpt = autoInvestRepository.findByUserIdAndRoundUpEnabled(userId, true);
            
            if (configOpt.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            
            AutoInvestConfig config = configOpt.get();
            
            // Calculate round-up amount
            BigDecimal roundUp = calculateRoundUp(transactionAmount, config.getRoundUpMultiplier());
            
            if (roundUp.compareTo(BigDecimal.ZERO) > 0) {
                // Check daily limit
                BigDecimal todayInvested = getRoundUpInvestedToday(userId);
                
                if (todayInvested.add(roundUp).compareTo(config.getMaxDailyInvestment()) <= 0) {
                    // Execute round-up investment
                    executeRoundUpInvestment(userId, config, roundUp);
                }
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error processing round-up investment", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Get stock trading history with analytics
     */
    public Page<StockOrderDto> getTradingHistory(UUID userId, Pageable pageable) {
        log.debug("Getting trading history for user: {}", userId);
        
        Page<StockOrder> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        return orders.map(this::toStockOrderDto);
    }
    
    /**
     * Get investment insights and recommendations
     */
    public List<InvestmentInsightDto> getInvestmentInsights(UUID userId) {
        log.debug("Getting investment insights for user: {}", userId);
        
        try {
            List<TradingInsight> insights = insightRepository.findByUserIdAndActiveTrue(userId);
            
            // Generate new insights if none exist or they're old
            if (insights.isEmpty() || isInsightsStale(insights)) {
                insights = generateFreshInsights(userId);
            }
            
            return insights.stream()
                .map(this::toInvestmentInsightDto)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting investment insights", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Apply stock boost rewards
     */
    public Optional<StockBoostDto> applyStockBoost(UUID userId, String symbol, BigDecimal investmentAmount) {
        log.info("Applying stock boost for user: {}, symbol: {}, amount: {}", userId, symbol, investmentAmount);
        
        try {
            // Find applicable boosts
            List<StockBoost> activeBoosts = boostRepository.findActiveBoostsByUserAndSymbol(userId, symbol);
            
            BigDecimal totalBoostAmount = BigDecimal.ZERO;
            
            for (StockBoost boost : activeBoosts) {
                if (boost.isApplicable(investmentAmount)) {
                    BigDecimal boostAmount = boost.calculateBoostAmount(investmentAmount);
                    
                    // Apply the boost (credit to account)
                    InvestmentAccount account = getOrCreateInvestmentAccount(userId);
                    account.setCashBalance(account.getCashBalance().add(boostAmount));
                    account.setBuyingPower(account.getBuyingPower().add(boostAmount));
                    accountRepository.save(account);
                    
                    // Record boost application
                    boost.recordApplication(investmentAmount, boostAmount);
                    boostRepository.save(boost);
                    
                    totalBoostAmount = totalBoostAmount.add(boostAmount);
                    
                    log.info("Applied boost: {} - Amount: ${}", boost.getName(), boostAmount);
                }
            }
            
            if (totalBoostAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Send notification
                notificationService.sendStockBoostNotification(userId, totalBoostAmount);
                
                // Create boost DTO
                return Optional.of(StockBoostDto.builder()
                    .totalBoostAmount(totalBoostAmount)
                    .appliedBoosts(activeBoosts.stream()
                        .map(this::toSimpleBoostDto)
                        .collect(Collectors.toList()))
                    .message("You earned $" + totalBoostAmount + " in stock boosts!")
                    .build());
            }
            
            log.debug("No stock boosts available to apply");
            return StockBoostResult.builder()
                .applied(false)
                .message("No active stock boosts available")
                .build();
            
        } catch (Exception e) {
            log.error("CRITICAL: Error applying stock boost - investment calculations may be incorrect", e);
            throw new RuntimeException("Failed to apply stock boost", e);
        }
    }
    
    // Helper methods
    
    private void executeStockOrder(StockOrder order, StockQuote quote) {
        // Simulate immediate execution for market orders
        order.setStatus(StockOrderStatus.EXECUTED);
        order.setExecutedPrice(quote.getPrice());
        order.setExecutedShares(order.getShares());
        order.setExecutedAmount(order.getShares().multiply(quote.getPrice()));
        order.setExecutedAt(Instant.now());
        
        orderRepository.save(order);
        
        // Update or create holding
        if (order.getOrderType() == StockOrderType.BUY) {
            updateHoldingAfterBuy(order, quote);
        }
        
        // Publish event
        eventPublisher.publish(StockTradingEvent.orderExecuted(order));
        
        log.info("Executed stock order: {} - {} shares at ${}", 
            order.getId(), order.getExecutedShares(), order.getExecutedPrice());
    }
    
    private void updateHoldingAfterBuy(StockOrder order, StockQuote quote) {
        Optional<StockHolding> existingHolding = holdingRepository
            .findByUserIdAndSymbol(order.getUserId(), order.getSymbol());
        
        if (existingHolding.isPresent()) {
            StockHolding holding = existingHolding.get();
            
            // Update existing holding
            BigDecimal totalCost = holding.getTotalCost().add(order.getExecutedAmount());
            BigDecimal totalShares = holding.getShares().add(order.getExecutedShares());
            BigDecimal newAvgCost = totalCost.divide(totalShares, 4, RoundingMode.HALF_UP);
            
            holding.setShares(totalShares);
            holding.setTotalCost(totalCost);
            holding.setAverageCost(newAvgCost);
            holding.setLastUpdated(Instant.now());
            
            holdingRepository.save(holding);
            
        } else {
            // Create new holding
            CompanyProfile profile = marketDataProvider.getCompanyProfile(order.getSymbol());
            
            StockHolding holding = StockHolding.builder()
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .companyName(profile.getName())
                .shares(order.getExecutedShares())
                .averageCost(order.getExecutedPrice())
                .totalCost(order.getExecutedAmount())
                .logoUrl(profile.getLogoUrl())
                .sector(profile.getSector())
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
            
            holdingRepository.save(holding);
        }
    }
    
    private void updateHoldingAfterSell(StockHolding holding, StockOrder order) {
        BigDecimal remainingShares = holding.getShares().subtract(order.getExecutedShares());
        
        if (remainingShares.compareTo(BigDecimal.ZERO) <= 0) {
            // Position fully closed
            holdingRepository.delete(holding);
        } else {
            // Partial sale - reduce position
            BigDecimal proportionSold = order.getExecutedShares().divide(holding.getShares(), 6, RoundingMode.HALF_UP);
            BigDecimal costReduction = holding.getTotalCost().multiply(proportionSold);
            
            holding.setShares(remainingShares);
            holding.setTotalCost(holding.getTotalCost().subtract(costReduction));
            holding.setLastUpdated(Instant.now());
            
            holdingRepository.save(holding);
        }
    }
    
    private void createSocialTradingActivity(UUID userId, StockOrder order, String action) {
        if (!order.isPrivate() && order.getSocialMessage() != null) {
            try {
                SocialTradingActivity activity = SocialTradingActivity.builder()
                    .userId(userId)
                    .symbol(order.getSymbol())
                    .action(action)
                    .amount(order.getAmount())
                    .shares(order.getExecutedShares())
                    .price(order.getExecutedPrice())
                    .message(order.getSocialMessage())
                    .createdAt(Instant.now())
                    .build();
                
                // This would integrate with the social feed service
                socialFeedService.createTradingActivity(activity);
                
            } catch (Exception e) {
                log.warn("Failed to create social trading activity", e);
            }
        }
    }
    
    private void generateInvestmentInsight(UUID userId, StockOrder order) {
        // Generate insights based on trading patterns
        try {
            List<String> insights = new ArrayList<>();
            
            // Check diversification
            List<StockHolding> holdings = holdingRepository.findByUserId(userId);
            if (holdings.size() == 1) {
                insights.add("Consider diversifying with different stocks to reduce risk");
            }
            
            // Check sector concentration
            Map<String, Long> sectorCounts = holdings.stream()
                .collect(Collectors.groupingBy(
                    StockHolding::getSector,
                    Collectors.counting()
                ));
            
            if (sectorCounts.size() == 1) {
                insights.add("You're concentrated in one sector. Consider adding stocks from other industries");
            }
            
            // Save insights
            for (String insightText : insights) {
                TradingInsight insight = TradingInsight.builder()
                    .userId(userId)
                    .type(InsightType.DIVERSIFICATION)
                    .title("Investment Tip")
                    .message(insightText)
                    .priority(InsightPriority.MEDIUM)
                    .active(true)
                    .createdAt(Instant.now())
                    .build();
                
                insightRepository.save(insight);
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate investment insight", e);
        }
    }
    
    private void applyStockBoosts(UUID userId, StockOrder order) {
        try {
            Optional<StockBoostDto> boost = self.applyStockBoost(userId, order.getSymbol(), order.getAmount());
            if (boost.isPresent()) {
                log.info("Applied stock boosts totaling: ${}", boost.get().getTotalBoostAmount());
            }
        } catch (Exception e) {
            log.warn("Failed to apply stock boosts", e);
        }
    }
    
    private void validateBuyRequest(BuyStockRequest request) {
        if (request.getAmount().compareTo(minInvestmentAmount) < 0) {
            throw new BusinessException("Minimum investment amount is $" + minInvestmentAmount);
        }
        
        if (request.getAmount().compareTo(maxDailyInvestment) > 0) {
            throw new BusinessException("Daily investment limit is $" + maxDailyInvestment);
        }
        
        if (!marketDataProvider.isValidSymbol(request.getSymbol())) {
            throw new BusinessException("Invalid stock symbol: " + request.getSymbol());
        }
    }
    
    private InvestmentAccount getOrCreateInvestmentAccount(UUID userId) {
        return accountRepository.findByUserId(userId.toString())
            .orElseGet(() -> {
                // Auto-create investment account
                return investmentService.createInvestmentAccount(
                    CreateAccountRequest.builder()
                        .accountType(AccountType.INDIVIDUAL)
                        .build()
                );
            });
    }
    
    private BigDecimal calculateRoundUp(BigDecimal amount, BigDecimal multiplier) {
        BigDecimal roundedUp = amount.setScale(0, RoundingMode.CEILING);
        BigDecimal roundUp = roundedUp.subtract(amount);
        return roundUp.multiply(multiplier != null ? multiplier : BigDecimal.ONE);
    }
    
    private void executeRoundUpInvestment(UUID userId, AutoInvestConfig config, BigDecimal roundUpAmount) {
        try {
            // Distribute round-up amount across target stocks
            for (Map.Entry<String, BigDecimal> entry : config.getAllocationPercentages().entrySet()) {
                String symbol = entry.getKey();
                BigDecimal percentage = entry.getValue();
                BigDecimal investAmount = roundUpAmount
                    .multiply(percentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                
                if (investAmount.compareTo(BigDecimal.valueOf(0.01)) >= 0) { // Minimum 1 cent
                    BuyStockRequest buyRequest = BuyStockRequest.builder()
                        .symbol(symbol)
                        .amount(investAmount)
                        .isPrivate(true)
                        .build();
                    
                    self.buyStock(userId, buyRequest);
                }
            }
            
            log.info("Executed round-up investment: ${} for user: {}", roundUpAmount, userId);
            
        } catch (Exception e) {
            log.error("Failed to execute round-up investment", e);
        }
    }
    
    private BigDecimal getRoundUpInvestedToday(UUID userId) {
        LocalDate today = LocalDate.now();
        return orderRepository.getTotalRoundUpInvestmentForDate(userId, today);
    }
    
    private String calculateConsensusRating(List<AnalystRating> ratings) {
        if (ratings.isEmpty()) return "N/A";
        
        Map<String, Long> ratingCounts = ratings.stream()
            .collect(Collectors.groupingBy(
                AnalystRating::getRating,
                Collectors.counting()
            ));
        
        return ratingCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("N/A");
    }
    
    private Optional<BigDecimal> calculateAvgPriceTarget(List<AnalystRating> ratings) {
        if (ratings.isEmpty()) return Optional.empty();
        
        return Optional.of(ratings.stream()
            .filter(r -> r.getPriceTarget() != null)
            .map(AnalystRating::getPriceTarget)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP));
    }
    
    private int getFriendsTrading(String symbol) {
        try {
            String socialKey = "invest:social:trading:" + symbol;
            Set<Object> tradingUsers = redisTemplate.opsForSet().members(socialKey);
            return tradingUsers != null ? tradingUsers.size() : 0;
        } catch (Exception e) {
            log.debug("Failed to get friends trading count for symbol: {}", symbol, e);
            return 0;
        }
    }
    
    private boolean isPopularStock(String symbol) {
        return Arrays.asList("AAPL", "GOOGL", "AMZN", "TSLA", "MSFT").contains(symbol);
    }
    
    private String calculateRiskLevel(StockMetrics metrics) {
        // Simple risk calculation based on volatility and beta
        if (metrics.getBeta() != null && metrics.getBeta().compareTo(BigDecimal.valueOf(1.5)) > 0) {
            return "HIGH";
        } else if (metrics.getBeta() != null && metrics.getBeta().compareTo(BigDecimal.valueOf(0.8)) < 0) {
            return "LOW";
        }
        return "MEDIUM";
    }
    
    // DTO conversion methods
    
    private StockOrderDto toStockOrderDto(StockOrder order) {
        return StockOrderDto.builder()
            .id(order.getId())
            .symbol(order.getSymbol())
            .orderType(order.getOrderType())
            .amount(order.getAmount())
            .shares(order.getShares())
            .estimatedPrice(order.getEstimatedPrice())
            .executedPrice(order.getExecutedPrice())
            .executedShares(order.getExecutedShares())
            .executedAmount(order.getExecutedAmount())
            .status(order.getStatus())
            .isFractional(order.isFractional())
            .socialMessage(order.getSocialMessage())
            .isPrivate(order.isPrivate())
            .createdAt(order.getCreatedAt())
            .executedAt(order.getExecutedAt())
            .build();
    }
    
    private AutoInvestConfigDto toAutoInvestConfigDto(AutoInvestConfig config) {
        return AutoInvestConfigDto.builder()
            .id(config.getId())
            .roundUpEnabled(config.isRoundUpEnabled())
            .roundUpMultiplier(config.getRoundUpMultiplier())
            .recurringAmount(config.getRecurringAmount())
            .recurringFrequency(config.getRecurringFrequency())
            .targetStocks(config.getTargetStocks())
            .allocationPercentages(config.getAllocationPercentages())
            .maxDailyInvestment(config.getMaxDailyInvestment())
            .enabled(config.isEnabled())
            .createdAt(config.getCreatedAt())
            .build();
    }
    
    private InvestmentInsightDto toInvestmentInsightDto(TradingInsight insight) {
        return InvestmentInsightDto.builder()
            .id(insight.getId())
            .type(insight.getType())
            .title(insight.getTitle())
            .message(insight.getMessage())
            .priority(insight.getPriority())
            .actionUrl(insight.getActionUrl())
            .createdAt(insight.getCreatedAt())
            .build();
    }
    
    private SimpleBoostDto toSimpleBoostDto(StockBoost boost) {
        return SimpleBoostDto.builder()
            .name(boost.getName())
            .description(boost.getDescription())
            .boostPercent(boost.getBoostPercent())
            .build();
    }
    
    private void validateAutoInvestRequest(SetupAutoInvestRequest request) {
        // Validation logic
        if (request.getAllocationPercentages().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BusinessException("Allocation percentages must sum to 100%");
        }
    }
    
    private void checkTaxLossHarvesting(UUID userId, StockOrder sellOrder) {
        // Check if the sale resulted in a loss and suggest tax-loss harvesting
        // This is a placeholder for tax optimization logic
    }
    
    private List<TradingInsight> generateFreshInsights(UUID userId) {
        try {
            log.info("Generating fresh trading insights for user: {}", userId);
            
            List<TradingInsight> insights = new ArrayList<>();
            
            // Get user's portfolio and trading history
            List<StockHolding> holdings = holdingRepository.findByUserId(userId);
            List<StockOrder> recentOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().limit(50).collect(Collectors.toList());
            
            // 1. Portfolio Diversification Analysis
            insights.addAll(generateDiversificationInsights(userId, holdings));
            
            // 2. Performance Analysis
            insights.addAll(generatePerformanceInsights(userId, holdings));
            
            // 3. Market Opportunities
            insights.addAll(generateMarketOpportunityInsights(userId, holdings));
            
            // 4. Risk Management Insights
            insights.addAll(generateRiskInsights(userId, holdings));
            
            // 5. Sector Analysis
            insights.addAll(generateSectorInsights(userId, holdings));
            
            // 6. Technical Analysis Insights
            insights.addAll(generateTechnicalInsights(userId, holdings));
            
            // 7. News and Event-Based Insights
            insights.addAll(generateNewsBasedInsights(userId, holdings));
            
            // 8. Tax Optimization Insights
            insights.addAll(generateTaxOptimizationInsights(userId, holdings, recentOrders));
            
            // Save insights to database
            insightRepository.saveAll(insights);
            
            log.info("Generated {} fresh trading insights for user: {}", insights.size(), userId);
            return insights;
            
        } catch (Exception e) {
            log.error("Failed to generate fresh insights for user: {}", userId, e);
            return Collections.emptyList();
        }
    }
    
    private List<TradingInsight> generateDiversificationInsights(UUID userId, List<StockHolding> holdings) {
        List<TradingInsight> insights = new ArrayList<>();
        
        if (holdings.isEmpty()) {
            return insights;
        }
        
        // Calculate portfolio concentration
        Map<String, BigDecimal> sectorWeights = holdings.stream()
            .collect(Collectors.groupingBy(
                StockHolding::getSector,
                Collectors.reducing(BigDecimal.ZERO, StockHolding::getTotalValue, BigDecimal::add)
            ));
        
        BigDecimal totalValue = holdings.stream()
            .map(StockHolding::getTotalValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Check for over-concentration in single sector
        sectorWeights.entrySet().stream()
            .filter(entry -> entry.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(0.4)) > 0) // > 40%
            .forEach(entry -> {
                TradingInsight insight = TradingInsight.builder()
                    .userId(userId)
                    .type("DIVERSIFICATION")
                    .title("High Sector Concentration")
                    .description(String.format("Your portfolio is %d%% concentrated in %s. " +
                        "Consider diversifying into other sectors to reduce risk.",
                        entry.getValue().divide(totalValue, 2, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).intValue(),
                        entry.getKey()))
                    .priority("MEDIUM")
                    .actionable(true)
                    .createdAt(Instant.now())
                    .build();
                insights.add(insight);
            });
        
        // Check for insufficient diversification (< 5 positions)
        if (holdings.size() < 5) {
            TradingInsight insight = TradingInsight.builder()
                .userId(userId)
                .type("DIVERSIFICATION")
                .title("Consider More Diversification")
                .description(String.format("You have %d positions. Consider adding 2-3 more " +
                    "stocks from different sectors to improve diversification.", holdings.size()))
                .priority("LOW")
                .actionable(true)
                .createdAt(Instant.now())
                .build();
            insights.add(insight);
        }
        
        return insights;
    }
    
    private List<TradingInsight> generatePerformanceInsights(UUID userId, List<StockHolding> holdings) {
        List<TradingInsight> insights = new ArrayList<>();
        
        for (StockHolding holding : holdings) {
            try {
                StockQuote currentQuote = marketDataProvider.getQuote(holding.getSymbol());
                BigDecimal currentValue = currentQuote.getPrice().multiply(holding.getShares());
                BigDecimal gainLoss = currentValue.subtract(holding.getTotalCost());
                BigDecimal gainLossPercent = gainLoss.divide(holding.getTotalCost(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                // Significant gains
                if (gainLossPercent.compareTo(BigDecimal.valueOf(20)) > 0) {
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("PERFORMANCE")
                        .symbol(holding.getSymbol())
                        .title("Strong Performer")
                        .description(String.format("%s is up %.2f%%. Consider taking partial profits " +
                            "or reviewing position size.", holding.getSymbol(), gainLossPercent))
                        .priority("MEDIUM")
                        .actionable(true)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                }
                
                // Significant losses
                if (gainLossPercent.compareTo(BigDecimal.valueOf(-15)) < 0) {
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("PERFORMANCE")
                        .symbol(holding.getSymbol())
                        .title("Underperforming Position")
                        .description(String.format("%s is down %.2f%%. Review your thesis " +
                            "and consider stop-loss or averaging down.", holding.getSymbol(), gainLossPercent))
                        .priority("HIGH")
                        .actionable(true)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                }
                
            } catch (Exception e) {
                log.warn("Failed to generate performance insight for {}: {}", holding.getSymbol(), e.getMessage());
            }
        }
        
        return insights;
    }
    
    private List<TradingInsight> generateMarketOpportunityInsights(UUID userId, List<StockHolding> holdings) {
        List<TradingInsight> insights = new ArrayList<>();
        
        try {
            // Get trending stocks and market movers
            List<String> trendingSymbols = marketDataProvider.getTrendingStocks();
            List<String> currentSymbols = holdings.stream()
                .map(StockHolding::getSymbol)
                .collect(Collectors.toList());
            
            // Find trending stocks not in portfolio
            List<String> opportunities = trendingSymbols.stream()
                .filter(symbol -> !currentSymbols.contains(symbol))
                .limit(3)
                .collect(Collectors.toList());
            
            for (String symbol : opportunities) {
                try {
                    StockQuote quote = marketDataProvider.getQuote(symbol);
                    CompanyProfile profile = marketDataProvider.getCompanyProfile(symbol);
                    
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("OPPORTUNITY")
                        .symbol(symbol)
                        .title("Trending Stock Opportunity")
                        .description(String.format("%s (%s) is trending. Current price: $%.2f. " +
                            "Consider researching for potential addition to portfolio.",
                            profile.getName(), symbol, quote.getPrice()))
                        .priority("LOW")
                        .actionable(true)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                    
                } catch (Exception e) {
                    log.warn("Failed to create opportunity insight for {}: {}", symbol, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate market opportunity insights: {}", e.getMessage());
        }
        
        return insights;
    }
    
    private List<TradingInsight> generateRiskInsights(UUID userId, List<StockHolding> holdings) {
        List<TradingInsight> insights = new ArrayList<>();
        
        // Calculate portfolio beta and risk metrics
        try {
            double portfolioBeta = holdings.stream()
                .mapToDouble(holding -> {
                    try {
                        StockMetrics metrics = marketDataProvider.getStockMetrics(holding.getSymbol());
                        return metrics.getBeta() != null ? metrics.getBeta().doubleValue() : 1.0;
                    } catch (Exception e) {
                        return 1.0; // Default beta
                    }
                })
                .average()
                .orElse(1.0);
            
            if (portfolioBeta > 1.3) {
                TradingInsight insight = TradingInsight.builder()
                    .userId(userId)
                    .type("RISK")
                    .title("High Portfolio Beta")
                    .description(String.format("Your portfolio beta is %.2f, indicating higher " +
                        "volatility than the market. Consider adding defensive stocks.", portfolioBeta))
                    .priority("MEDIUM")
                    .actionable(true)
                    .createdAt(Instant.now())
                    .build();
                insights.add(insight);
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate risk insights: {}", e.getMessage());
        }
        
        return insights;
    }
    
    private List<TradingInsight> generateSectorInsights(UUID userId, List<StockHolding> holdings) {
        List<TradingInsight> insights = new ArrayList<>();
        
        // Get sector performance data and generate insights
        try {
            Map<String, List<StockHolding>> sectorGroups = holdings.stream()
                .collect(Collectors.groupingBy(StockHolding::getSector));
            
            for (Map.Entry<String, List<StockHolding>> entry : sectorGroups.entrySet()) {
                String sector = entry.getKey();
                List<StockHolding> sectorHoldings = entry.getValue();
                
                // Generate sector-specific insights
                SectorPerformance performance = marketDataProvider.getSectorPerformance(sector);
                if (performance != null && performance.isOutperforming()) {
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("SECTOR")
                        .title(sector + " Sector Strength")
                        .description(String.format("The %s sector is outperforming the market. " +
                            "Your %d positions in this sector may benefit from continued strength.",
                            sector, sectorHoldings.size()))
                        .priority("LOW")
                        .actionable(false)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate sector insights: {}", e.getMessage());
        }
        
        return insights;
    }
    
    private List<TradingInsight> generateTechnicalInsights(UUID userId, List<StockHolding> holdings) {
        List<TradingInsight> insights = new ArrayList<>();
        
        for (StockHolding holding : holdings) {
            try {
                TechnicalIndicators indicators = marketDataProvider.getTechnicalIndicators(holding.getSymbol());
                
                // RSI overbought/oversold
                if (indicators.getRsi() > 70) {
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("TECHNICAL")
                        .symbol(holding.getSymbol())
                        .title("Overbought Signal")
                        .description(String.format("%s RSI is %.1f, indicating potential " +
                            "overbought conditions. Consider taking profits.",
                            holding.getSymbol(), indicators.getRsi()))
                        .priority("MEDIUM")
                        .actionable(true)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                } else if (indicators.getRsi() < 30) {
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("TECHNICAL")
                        .symbol(holding.getSymbol())
                        .title("Oversold Signal")
                        .description(String.format("%s RSI is %.1f, indicating potential " +
                            "oversold conditions. May be a buying opportunity.",
                            holding.getSymbol(), indicators.getRsi()))
                        .priority("MEDIUM")
                        .actionable(true)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                }
                
            } catch (Exception e) {
                log.warn("Failed to generate technical insight for {}: {}", holding.getSymbol(), e.getMessage());
            }
        }
        
        return insights;
    }
    
    private List<TradingInsight> generateNewsBasedInsights(UUID userId, List<StockHolding> holdings) {
        List<TradingInsight> insights = new ArrayList<>();
        
        for (StockHolding holding : holdings) {
            try {
                List<NewsArticle> recentNews = marketDataProvider.getRecentNews(holding.getSymbol(), 7);
                
                // Check for earnings announcements
                boolean hasEarningsNews = recentNews.stream()
                    .anyMatch(article -> article.getTitle().toLowerCase().contains("earnings") ||
                                       article.getTitle().toLowerCase().contains("quarterly results"));
                
                if (hasEarningsNews) {
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("NEWS")
                        .symbol(holding.getSymbol())
                        .title("Recent Earnings Announcement")
                        .description(String.format("%s has recent earnings news. " +
                            "Review results and guidance to assess impact on your position.",
                            holding.getSymbol()))
                        .priority("MEDIUM")
                        .actionable(true)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                }
                
            } catch (Exception e) {
                log.warn("Failed to generate news insight for {}: {}", holding.getSymbol(), e.getMessage());
            }
        }
        
        return insights;
    }
    
    private List<TradingInsight> generateTaxOptimizationInsights(UUID userId, List<StockHolding> holdings, List<StockOrder> recentOrders) {
        List<TradingInsight> insights = new ArrayList<>();
        
        // Tax loss harvesting opportunities
        for (StockHolding holding : holdings) {
            try {
                StockQuote quote = marketDataProvider.getQuote(holding.getSymbol());
                BigDecimal currentValue = quote.getPrice().multiply(holding.getShares());
                BigDecimal unrealizedLoss = holding.getTotalCost().subtract(currentValue);
                
                if (unrealizedLoss.compareTo(BigDecimal.valueOf(1000)) > 0) {
                    TradingInsight insight = TradingInsight.builder()
                        .userId(userId)
                        .type("TAX")
                        .symbol(holding.getSymbol())
                        .title("Tax Loss Harvesting Opportunity")
                        .description(String.format("%s has unrealized losses of $%.2f. " +
                            "Consider harvesting losses for tax benefits if it aligns with your strategy.",
                            holding.getSymbol(), unrealizedLoss))
                        .priority("LOW")
                        .actionable(true)
                        .createdAt(Instant.now())
                        .build();
                    insights.add(insight);
                }
                
            } catch (Exception e) {
                log.warn("Failed to generate tax insight for {}: {}", holding.getSymbol(), e.getMessage());
            }
        }
        
        return insights;
    }
    
    private boolean isInsightsStale(List<TradingInsight> insights) {
        return insights.stream()
            .anyMatch(insight -> insight.getCreatedAt().isBefore(Instant.now().minusSeconds(86400))); // 24 hours
    }
}