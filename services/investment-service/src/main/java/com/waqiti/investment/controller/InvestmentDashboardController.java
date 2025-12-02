package com.waqiti.investment.controller;

import com.waqiti.investment.dto.analytics.*;
import com.waqiti.investment.dto.response.*;
import com.waqiti.investment.service.AdvancedAnalyticsService;
import com.waqiti.investment.service.InvestmentService;
import com.waqiti.investment.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investment/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Investment Dashboard", description = "Advanced investment analytics and dashboard APIs")
public class InvestmentDashboardController {

    private final InvestmentService investmentService;
    private final AdvancedAnalyticsService analyticsService;
    private final MarketDataService marketDataService;

    @GetMapping("/overview/{accountId}")
    @Operation(summary = "Get investment dashboard overview", description = "Get comprehensive investment account overview")
    @ApiResponse(responseCode = "200", description = "Dashboard overview retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<InvestmentDashboardOverview> getDashboardOverview(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting dashboard overview for account: {}", accountId);
        
        // Get basic account info
        InvestmentAccountDto account = investmentService.getInvestmentAccount(accountId);
        PortfolioDto portfolio = investmentService.getPortfolio(accountId);
        List<PositionDto> positions = investmentService.getPositions(accountId);
        
        // Get advanced analytics
        AdvancedPortfolioAnalytics analytics = analyticsService.getAdvancedPortfolioAnalytics(accountId);
        
        // Get market data
        List<MarketMoversDto> topMovers = marketDataService.getTopMovers();
        MarketOverviewDto marketOverview = marketDataService.getMarketOverview();
        
        InvestmentDashboardOverview overview = InvestmentDashboardOverview.builder()
                .account(account)
                .portfolio(portfolio)
                .positions(positions)
                .analytics(analytics)
                .topMovers(topMovers)
                .marketOverview(marketOverview)
                .lastUpdated(analytics.getLastUpdated())
                .build();
        
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/analytics/{accountId}")
    @Operation(summary = "Get advanced portfolio analytics", description = "Get detailed portfolio analytics and risk metrics")
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AdvancedPortfolioAnalytics> getAdvancedAnalytics(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting advanced analytics for account: {}", accountId);
        
        AdvancedPortfolioAnalytics analytics = analyticsService.getAdvancedPortfolioAnalytics(accountId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/risk-assessment/{accountId}")
    @Operation(summary = "Get risk assessment report", description = "Get comprehensive risk assessment for portfolio")
    @ApiResponse(responseCode = "200", description = "Risk assessment retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RiskAssessmentReport> getRiskAssessment(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting risk assessment for account: {}", accountId);
        
        RiskAssessmentReport riskAssessment = analyticsService.generateRiskAssessment(accountId);
        return ResponseEntity.ok(riskAssessment);
    }

    @GetMapping("/optimization/{accountId}")
    @Operation(summary = "Get optimization recommendations", description = "Get portfolio optimization recommendations")
    @ApiResponse(responseCode = "200", description = "Optimization recommendations retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OptimizationRecommendations> getOptimizationRecommendations(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting optimization recommendations for account: {}", accountId);
        
        OptimizationRecommendations recommendations = analyticsService.getOptimizationRecommendations(accountId);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/benchmark/{accountId}")
    @Operation(summary = "Get benchmark analysis", description = "Compare portfolio performance against benchmark")
    @ApiResponse(responseCode = "200", description = "Benchmark analysis retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PerformanceBenchmarkAnalysis> getBenchmarkAnalysis(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId,
            @Parameter(description = "Benchmark symbol") @RequestParam(defaultValue = "SPY") String benchmark) {
        
        log.info("Getting benchmark analysis for account: {} vs {}", accountId, benchmark);
        
        PerformanceBenchmarkAnalysis analysis = analyticsService.benchmarkPerformance(accountId, benchmark);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/sentiment/{accountId}")
    @Operation(summary = "Get market sentiment analysis", description = "Get market sentiment analysis for portfolio holdings")
    @ApiResponse(responseCode = "200", description = "Sentiment analysis retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MarketSentimentAnalysis> getSentimentAnalysis(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting sentiment analysis for account: {}", accountId);
        
        MarketSentimentAnalysis sentiment = analyticsService.getMarketSentimentAnalysis(accountId);
        return ResponseEntity.ok(sentiment);
    }

    @GetMapping("/performance/{accountId}")
    @Operation(summary = "Get performance metrics", description = "Get detailed performance metrics for specified period")
    @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PerformanceDto> getPerformanceMetrics(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId,
            @Parameter(description = "Performance timeframe") @RequestParam(defaultValue = "ONE_MONTH") PerformanceTimeframe timeframe) {
        
        log.info("Getting performance metrics for account: {} timeframe: {}", accountId, timeframe);
        
        PerformanceDto performance = investmentService.getPerformance(accountId, timeframe);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/allocation/{accountId}")
    @Operation(summary = "Get allocation analysis", description = "Get detailed asset allocation breakdown")
    @ApiResponse(responseCode = "200", description = "Allocation analysis retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AllocationAnalysisDto> getAllocationAnalysis(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting allocation analysis for account: {}", accountId);
        
        AdvancedPortfolioAnalytics analytics = analyticsService.getAdvancedPortfolioAnalytics(accountId);
        
        AllocationAnalysisDto allocation = AllocationAnalysisDto.builder()
                .sectorAllocation(analytics.getSectorAllocation())
                .assetClassAllocation(analytics.getAssetClassAllocation())
                .geographicAllocation(analytics.getGeographicAllocation())
                .diversificationAnalysis(analytics.getDiversificationAnalysis())
                .lastUpdated(analytics.getLastUpdated())
                .build();
        
        return ResponseEntity.ok(allocation);
    }

    @GetMapping("/watchlist/{accountId}")
    @Operation(summary = "Get watchlist with analytics", description = "Get watchlist with price alerts and analytics")
    @ApiResponse(responseCode = "200", description = "Watchlist retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WatchlistAnalyticsDto> getWatchlistAnalytics(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting watchlist analytics for account: {}", accountId);
        
        List<WatchlistItemDto> watchlistItems = investmentService.getWatchlist(accountId);
        
        // Enhance watchlist items with current market data and analytics
        List<EnhancedWatchlistItemDto> enhancedItems = watchlistItems.stream()
                .map(item -> {
                    String symbol = item.getSymbol();
                    BigDecimal currentPrice = marketDataService.getCurrentPrice(symbol);
                    BigDecimal dayChange = marketDataService.getDayChange(symbol);
                    BigDecimal dayChangePercent = marketDataService.getDayChangePercent(symbol);
                    SentimentMetrics sentiment = marketDataService.getSentimentMetrics(symbol);
                    
                    return EnhancedWatchlistItemDto.builder()
                            .watchlistItem(item)
                            .currentPrice(currentPrice)
                            .dayChange(dayChange)
                            .dayChangePercent(dayChangePercent)
                            .sentiment(sentiment)
                            .priceAlert(item.getPriceTarget() != null && 
                                      currentPrice.compareTo(item.getPriceTarget()) >= 0)
                            .build();
                })
                .collect(Collectors.toList());
        
        WatchlistAnalyticsDto watchlistAnalytics = WatchlistAnalyticsDto.builder()
                .items(enhancedItems)
                .totalItems(enhancedItems.size())
                .priceAlerts(enhancedItems.stream().mapToInt(item -> item.isPriceAlert() ? 1 : 0).sum())
                .avgSentimentScore(enhancedItems.stream()
                        .map(item -> item.getSentiment().getOverallScore())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(enhancedItems.size()), 2, RoundingMode.HALF_UP))
                .build();
        
        return ResponseEntity.ok(watchlistAnalytics);
    }

    @GetMapping("/orders/{accountId}")
    @Operation(summary = "Get order history with analytics", description = "Get order history with execution analytics")
    @ApiResponse(responseCode = "200", description = "Order history retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderHistoryAnalyticsDto> getOrderHistoryAnalytics(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId,
            @Parameter(description = "Start date") @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date") @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Getting order history analytics for account: {}", accountId);
        
        List<InvestmentOrderDto> orders = investmentService.getOrderHistory(accountId, startDate, endDate);
        
        // Calculate order analytics
        OrderAnalytics analytics = calculateOrderAnalytics(orders);
        
        OrderHistoryAnalyticsDto orderAnalytics = OrderHistoryAnalyticsDto.builder()
                .orders(orders)
                .analytics(analytics)
                .totalOrders(orders.size())
                .executedOrders(orders.stream().mapToInt(o -> o.getStatus().name().equals("FILLED") ? 1 : 0).sum())
                .build();
        
        return ResponseEntity.ok(orderAnalytics);
    }

    @GetMapping("/dividends/{accountId}")
    @Operation(summary = "Get dividend analysis", description = "Get dividend income analysis and projections")
    @ApiResponse(responseCode = "200", description = "Dividend analysis retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DividendAnalysisDto> getDividendAnalysis(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId) {
        
        log.info("Getting dividend analysis for account: {}", accountId);
        
        DividendAnalysisDto dividendAnalysis = investmentService.getDividendAnalysis(accountId);
        return ResponseEntity.ok(dividendAnalysis);
    }

    @GetMapping("/tax-analysis/{accountId}")
    @Operation(summary = "Get tax analysis", description = "Get tax implications analysis for portfolio")
    @ApiResponse(responseCode = "200", description = "Tax analysis retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaxAnalysisDto> getTaxAnalysis(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId,
            @Parameter(description = "Tax year") @RequestParam(defaultValue = "2024") Integer taxYear) {
        
        log.info("Getting tax analysis for account: {} year: {}", accountId, taxYear);
        
        TaxAnalysisDto taxAnalysis = investmentService.getTaxAnalysis(accountId, taxYear);
        return ResponseEntity.ok(taxAnalysis);
    }

    @GetMapping("/market-insights")
    @Operation(summary = "Get market insights", description = "Get current market insights and analysis")
    @ApiResponse(responseCode = "200", description = "Market insights retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MarketInsightsDto> getMarketInsights() {
        log.info("Getting market insights");
        
        MarketInsightsDto insights = marketDataService.getMarketInsights();
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/research/{symbol}")
    @Operation(summary = "Get investment research", description = "Get comprehensive research data for a symbol")
    @ApiResponse(responseCode = "200", description = "Research data retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<InvestmentResearchDto> getInvestmentResearch(
            @Parameter(description = "Stock symbol") @PathVariable String symbol) {
        
        log.info("Getting investment research for symbol: {}", symbol);
        
        InvestmentResearchDto research = marketDataService.getInvestmentResearch(symbol);
        return ResponseEntity.ok(research);
    }

    @PostMapping("/simulate-trade/{accountId}")
    @Operation(summary = "Simulate trade impact", description = "Simulate the impact of a potential trade on portfolio")
    @ApiResponse(responseCode = "200", description = "Trade simulation completed successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TradeSimulationDto> simulateTradeImpact(
            @AuthenticationPrincipal Principal principal,
            @Parameter(description = "Investment account ID") @PathVariable String accountId,
            @RequestBody TradeSimulationRequest request) {
        
        log.info("Simulating trade impact for account: {} symbol: {} quantity: {}", 
                accountId, request.getSymbol(), request.getQuantity());
        
        TradeSimulationDto simulation = analyticsService.simulateTradeImpact(accountId, request);
        return ResponseEntity.ok(simulation);
    }

    @GetMapping("/portfolio-comparison")
    @Operation(summary = "Compare portfolios", description = "Compare performance of multiple portfolios")
    @ApiResponse(responseCode = "200", description = "Portfolio comparison completed successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PortfolioComparisonDto> comparePortfolios(
            @Parameter(description = "Account IDs") @RequestParam List<String> accountIds,
            @Parameter(description = "Comparison period") @RequestParam(defaultValue = "ONE_YEAR") PerformanceTimeframe period) {
        
        log.info("Comparing portfolios: {} for period: {}", accountIds, period);
        
        PortfolioComparisonDto comparison = analyticsService.comparePortfolios(accountIds, period);
        return ResponseEntity.ok(comparison);
    }

    // Helper methods
    private OrderAnalytics calculateOrderAnalytics(List<InvestmentOrderDto> orders) {
        // Calculate various order analytics metrics
        return OrderAnalytics.builder()
                .totalOrders(orders.size())
                .executedOrders(orders.stream().mapToInt(o -> o.getStatus().name().equals("FILLED") ? 1 : 0).sum())
                .avgExecutionTime(calculateAvgExecutionTime(orders))
                .totalVolume(calculateTotalVolume(orders))
                .avgOrderSize(calculateAvgOrderSize(orders))
                .executionRate(calculateExecutionRate(orders))
                .build();
    }
    
    private BigDecimal calculateAvgExecutionTime(List<InvestmentOrderDto> orders) {
        // Implementation for average execution time calculation
        return BigDecimal.ZERO;
    }
    
    private BigDecimal calculateTotalVolume(List<InvestmentOrderDto> orders) {
        return orders.stream()
                .filter(o -> o.getFilledValue() != null)
                .map(InvestmentOrderDto::getFilledValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateAvgOrderSize(List<InvestmentOrderDto> orders) {
        if (orders.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal totalValue = calculateTotalVolume(orders);
        return totalValue.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateExecutionRate(List<InvestmentOrderDto> orders) {
        if (orders.isEmpty()) return BigDecimal.ZERO;
        
        long executedCount = orders.stream().mapToLong(o -> o.getStatus().name().equals("FILLED") ? 1 : 0).sum();
        return BigDecimal.valueOf(executedCount)
                .divide(BigDecimal.valueOf(orders.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}