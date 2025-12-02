package com.waqiti.investment.service;

import com.waqiti.investment.domain.*;
import com.waqiti.investment.domain.enums.*;
import com.waqiti.investment.dto.*;
import com.waqiti.investment.dto.response.PortfolioDto;
import com.waqiti.investment.dto.response.PortfolioPerformanceDto;
import com.waqiti.investment.exception.*;
import com.waqiti.investment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing investment portfolios and holdings
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioService {

    @Lazy
    private final PortfolioService self;
    private final PortfolioRepository portfolioRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final InvestmentAccountRepository accountRepository;
    private final InvestmentTransactionRepository transactionRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final MarketDataService marketDataService;
    private final AdvancedAnalyticsService analyticsService;

    /**
     * Get portfolio by account ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "investmentPortfolios", key = "#accountId")
    public PortfolioDto getPortfolio(Long accountId) {
        InvestmentAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Investment account not found: " + accountId));

        Portfolio portfolio = account.getPortfolio();
        if (portfolio == null) {
            portfolio = createDefaultPortfolio(account);
        }

        return mapToPortfolioDto(portfolio);
    }

    /**
     * Get detailed portfolio with real-time market data
     */
    @Transactional(readOnly = true)
    public PortfolioDto getDetailedPortfolio(Long accountId) {
        PortfolioDto portfolio = self.getPortfolio(accountId);
        
        // Update with real-time market data
        List<String> symbols = portfolio.getHoldings().stream()
                .map(PortfolioDto.HoldingDto::getSymbol)
                .collect(Collectors.toList());

        if (!symbols.isEmpty()) {
            Map<String, StockQuoteDto> quotes = marketDataService.getBatchStockQuotes(symbols);
            updatePortfolioWithMarketData(portfolio, quotes);
        }

        return portfolio;
    }

    /**
     * Calculate portfolio performance metrics
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "portfolioPerformance", key = "#accountId + ':' + #startDate + ':' + #endDate")
    public PortfolioPerformanceDto calculatePortfolioPerformance(Long accountId, LocalDate startDate, LocalDate endDate) {
        Portfolio portfolio = self.getPortfolioEntity(accountId);
        
        BigDecimal initialValue = calculatePortfolioValueAtDate(portfolio, startDate);
        BigDecimal currentValue = portfolio.getTotalValue();
        BigDecimal totalReturn = currentValue.subtract(initialValue);
        BigDecimal returnPercentage = calculateReturnPercentage(initialValue, currentValue);

        // Calculate other metrics
        BigDecimal volatility = analyticsService.calculateVolatility(portfolio, startDate, endDate);
        BigDecimal sharpeRatio = analyticsService.calculateSharpeRatio(portfolio, startDate, endDate);
        BigDecimal beta = analyticsService.calculateBeta(portfolio, startDate, endDate);
        BigDecimal alpha = analyticsService.calculateAlpha(portfolio, startDate, endDate);

        return PortfolioPerformanceDto.builder()
                .accountId(accountId)
                .startDate(startDate)
                .endDate(endDate)
                .initialValue(initialValue)
                .currentValue(currentValue)
                .totalReturn(totalReturn)
                .returnPercentage(returnPercentage)
                .volatility(volatility)
                .sharpeRatio(sharpeRatio)
                .beta(beta)
                .alpha(alpha)
                .build();
    }

    /**
     * Get portfolio allocation breakdown
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getPortfolioAllocation(Long accountId) {
        Portfolio portfolio = self.getPortfolioEntity(accountId);
        
        Map<String, BigDecimal> allocation = new HashMap<>();
        BigDecimal totalValue = portfolio.getTotalValue();

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            return allocation;
        }

        // Asset class allocation
        Map<String, BigDecimal> assetClassAllocation = portfolio.getHoldings().stream()
                .collect(Collectors.groupingBy(
                        holding -> categorizeAssetClass(holding.getSymbol()),
                        Collectors.reducing(BigDecimal.ZERO, 
                                holding -> holding.getQuantity().multiply(holding.getCurrentPrice()),
                                BigDecimal::add)
                ));

        assetClassAllocation.forEach((assetClass, value) -> {
            BigDecimal percentage = value.divide(totalValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            allocation.put(assetClass, percentage);
        });

        return allocation;
    }

    /**
     * Get sector allocation
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getSectorAllocation(Long accountId) {
        Portfolio portfolio = self.getPortfolioEntity(accountId);
        
        Map<String, BigDecimal> sectorAllocation = new HashMap<>();
        BigDecimal totalValue = portfolio.getTotalValue();

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            return sectorAllocation;
        }

        // Group holdings by sector
        portfolio.getHoldings().forEach(holding -> {
            String sector = getSectorForSymbol(holding.getSymbol());
            BigDecimal value = holding.getQuantity().multiply(holding.getCurrentPrice());
            sectorAllocation.merge(sector, value, BigDecimal::add);
        });

        // Convert to percentages
        Map<String, BigDecimal> percentageAllocation = new HashMap<>();
        sectorAllocation.forEach((sector, value) -> {
            BigDecimal percentage = value.divide(totalValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            percentageAllocation.put(sector, percentage);
        });

        return percentageAllocation;
    }

    /**
     * Rebalance portfolio based on target allocation
     */
    @Transactional
    public List<RebalanceRecommendationDto> generateRebalanceRecommendations(
            Long accountId, 
            Map<String, BigDecimal> targetAllocation) {
        
        Portfolio portfolio = self.getPortfolioEntity(accountId);
        Map<String, BigDecimal> currentAllocation = self.getPortfolioAllocation(accountId);
        List<RebalanceRecommendationDto> recommendations = new ArrayList<>();

        BigDecimal totalValue = portfolio.getTotalValue();
        BigDecimal cashAvailable = portfolio.getAccount().getCashBalance();

        targetAllocation.forEach((assetClass, targetPercentage) -> {
            BigDecimal currentPercentage = currentAllocation.getOrDefault(assetClass, BigDecimal.ZERO);
            BigDecimal difference = targetPercentage.subtract(currentPercentage);

            if (Math.abs(difference.doubleValue()) > 1.0) { // 1% threshold
                BigDecimal targetValue = totalValue.multiply(targetPercentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal currentValue = totalValue.multiply(currentPercentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal adjustmentValue = targetValue.subtract(currentValue);

                RebalanceRecommendationDto recommendation = RebalanceRecommendationDto.builder()
                        .assetClass(assetClass)
                        .currentAllocation(currentPercentage)
                        .targetAllocation(targetPercentage)
                        .adjustmentValue(adjustmentValue)
                        .action(adjustmentValue.compareTo(BigDecimal.ZERO) > 0 ? "BUY" : "SELL")
                        .build();

                // Add specific stock recommendations based on asset class
                List<String> stocksInClass = getStocksForAssetClass(portfolio, assetClass);
                recommendation.setSpecificRecommendations(
                        generateSpecificStockRecommendations(stocksInClass, adjustmentValue)
                );

                recommendations.add(recommendation);
            }
        });

        return recommendations;
    }

    /**
     * Calculate portfolio risk metrics
     */
    @Transactional(readOnly = true)
    public PortfolioRiskMetricsDto calculateRiskMetrics(Long accountId) {
        Portfolio portfolio = self.getPortfolioEntity(accountId);
        
        // Calculate Value at Risk (VaR)
        BigDecimal var95 = analyticsService.calculateValueAtRisk(portfolio, 0.95, 1);
        BigDecimal var99 = analyticsService.calculateValueAtRisk(portfolio, 0.99, 1);

        // Calculate maximum drawdown
        BigDecimal maxDrawdown = analyticsService.calculateMaxDrawdown(portfolio, 
                LocalDate.now().minusYears(1), LocalDate.now());

        // Calculate concentration risk
        BigDecimal concentrationRisk = calculateConcentrationRisk(portfolio);

        // Calculate correlation matrix
        Map<String, Map<String, BigDecimal>> correlationMatrix = 
                analyticsService.calculateCorrelationMatrix(portfolio);

        return PortfolioRiskMetricsDto.builder()
                .accountId(accountId)
                .valueAtRisk95(var95)
                .valueAtRisk99(var99)
                .maxDrawdown(maxDrawdown)
                .concentrationRisk(concentrationRisk)
                .correlationMatrix(correlationMatrix)
                .riskScore(calculateOverallRiskScore(var95, maxDrawdown, concentrationRisk))
                .build();
    }

    /**
     * Get portfolio transactions history
     */
    @Transactional(readOnly = true)
    public Page<TransactionHistoryDto> getPortfolioTransactions(
            Long accountId, 
            LocalDateTime startDate, 
            LocalDateTime endDate,
            Pageable pageable) {
        
        Portfolio portfolio = self.getPortfolioEntity(accountId);
        
        try {
            // Query the investment transaction repository
            Page<InvestmentTransaction> transactions = transactionRepository.findByPortfolioIdAndTransactionDateBetween(
                portfolio.getId(), 
                startDate, 
                endDate, 
                pageable
            );
            
            // Map to DTOs
            List<TransactionHistoryDto> transactionDtos = transactions.getContent().stream()
                .map(this::mapToTransactionHistoryDto)
                .collect(Collectors.toList());
            
            return new PageImpl<>(transactionDtos, pageable, transactions.getTotalElements());
            
        } catch (Exception e) {
            log.error("Error retrieving portfolio transactions for account {}: {}", accountId, e.getMessage(), e);
            // Return empty page on error to prevent service failure
            return Page.empty(pageable);
        }
    }
    
    /**
     * Maps investment transaction to transaction history DTO
     */
    private TransactionHistoryDto mapToTransactionHistoryDto(InvestmentTransaction transaction) {
        return TransactionHistoryDto.builder()
            .transactionId(transaction.getId())
            .transactionDate(transaction.getTransactionDate())
            .transactionType(transaction.getTransactionType())
            .symbol(transaction.getSymbol())
            .quantity(transaction.getQuantity())
            .price(transaction.getPrice())
            .totalAmount(transaction.getTotalAmount())
            .fees(transaction.getFees())
            .settledAmount(transaction.getSettledAmount())
            .status(transaction.getStatus())
            .description(transaction.getDescription())
            .orderId(transaction.getOrderId())
            .executionId(transaction.getExecutionId())
            .counterparty(transaction.getCounterparty())
            .settlementDate(transaction.getSettlementDate())
            .createdAt(transaction.getCreatedAt())
            .updatedAt(transaction.getUpdatedAt())
            .build();
    }

    /**
     * Update holding with new market price
     */
    @Transactional
    public void updateHoldingPrice(Long holdingId, BigDecimal newPrice) {
        InvestmentHolding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new InvestmentException("Holding not found: " + holdingId));

        BigDecimal oldValue = holding.getQuantity().multiply(holding.getCurrentPrice());
        BigDecimal newValue = holding.getQuantity().multiply(newPrice);
        BigDecimal unrealizedGainLoss = newValue.subtract(holding.getTotalCost());

        holding.setCurrentPrice(newPrice);
        holding.setMarketValue(newValue);
        holding.setUnrealizedGainLoss(unrealizedGainLoss);
        holding.setLastUpdated(LocalDateTime.now());

        holdingRepository.save(holding);

        // Update portfolio totals
        updatePortfolioTotals(holding.getPortfolio());
    }

    /**
     * Add new holding to portfolio
     */
    @Transactional
    public InvestmentHolding addHolding(Portfolio portfolio, String symbol, BigDecimal quantity, BigDecimal purchasePrice) {
        // Check if holding already exists
        Optional<InvestmentHolding> existingHolding = portfolio.getHoldings().stream()
                .filter(h -> h.getSymbol().equals(symbol))
                .findFirst();

        if (existingHolding.isPresent()) {
            // Update existing holding
            InvestmentHolding holding = existingHolding.get();
            BigDecimal totalQuantity = holding.getQuantity().add(quantity);
            BigDecimal totalCost = holding.getTotalCost().add(quantity.multiply(purchasePrice));
            BigDecimal avgCost = totalCost.divide(totalQuantity, 2, RoundingMode.HALF_UP);

            holding.setQuantity(totalQuantity);
            holding.setAverageCost(avgCost);
            holding.setTotalCost(totalCost);
            holding.setMarketValue(totalQuantity.multiply(holding.getCurrentPrice()));
            holding.setUnrealizedGainLoss(holding.getMarketValue().subtract(totalCost));

            return holdingRepository.save(holding);
        } else {
            // Create new holding
            InvestmentHolding holding = InvestmentHolding.builder()
                    .portfolio(portfolio)
                    .symbol(symbol)
                    .quantity(quantity)
                    .averageCost(purchasePrice)
                    .totalCost(quantity.multiply(purchasePrice))
                    .currentPrice(purchasePrice)
                    .marketValue(quantity.multiply(purchasePrice))
                    .unrealizedGainLoss(BigDecimal.ZERO)
                    .lastUpdated(LocalDateTime.now())
                    .build();

            portfolio.getHoldings().add(holding);
            return holdingRepository.save(holding);
        }
    }

    /**
     * Remove or reduce holding
     */
    @Transactional
    public void removeHolding(Portfolio portfolio, String symbol, BigDecimal quantity) {
        InvestmentHolding holding = portfolio.getHoldings().stream()
                .filter(h -> h.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new InvestmentException("Holding not found: " + symbol));

        if (quantity.compareTo(holding.getQuantity()) >= 0) {
            // Remove entire holding
            portfolio.getHoldings().remove(holding);
            holdingRepository.delete(holding);
        } else {
            // Reduce holding
            BigDecimal remainingQuantity = holding.getQuantity().subtract(quantity);
            BigDecimal soldCost = holding.getAverageCost().multiply(quantity);
            BigDecimal remainingCost = holding.getTotalCost().subtract(soldCost);

            holding.setQuantity(remainingQuantity);
            holding.setTotalCost(remainingCost);
            holding.setMarketValue(remainingQuantity.multiply(holding.getCurrentPrice()));
            holding.setUnrealizedGainLoss(holding.getMarketValue().subtract(remainingCost));

            holdingRepository.save(holding);
        }

        updatePortfolioTotals(portfolio);
    }

    // Helper methods

    @Transactional(readOnly = true)
    public Portfolio getPortfolioEntity(Long accountId) {
        InvestmentAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Investment account not found: " + accountId));
        
        if (account.getPortfolio() == null) {
            return createDefaultPortfolio(account);
        }
        
        return account.getPortfolio();
    }

    private Portfolio createDefaultPortfolio(InvestmentAccount account) {
        Portfolio portfolio = Portfolio.builder()
                .account(account)
                .totalValue(BigDecimal.ZERO)
                .totalCost(BigDecimal.ZERO)
                .totalGainLoss(BigDecimal.ZERO)
                .dayGainLoss(BigDecimal.ZERO)
                .holdings(new ArrayList<>())
                .lastUpdated(LocalDateTime.now())
                .build();

        return portfolioRepository.save(portfolio);
    }

    private void updatePortfolioTotals(Portfolio portfolio) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalGainLoss = BigDecimal.ZERO;

        for (InvestmentHolding holding : portfolio.getHoldings()) {
            totalValue = totalValue.add(holding.getMarketValue());
            totalCost = totalCost.add(holding.getTotalCost());
            totalGainLoss = totalGainLoss.add(holding.getUnrealizedGainLoss());
        }

        portfolio.setTotalValue(totalValue);
        portfolio.setTotalCost(totalCost);
        portfolio.setTotalGainLoss(totalGainLoss);
        portfolio.setLastUpdated(LocalDateTime.now());

        portfolioRepository.save(portfolio);
    }

    private PortfolioDto mapToPortfolioDto(Portfolio portfolio) {
        List<PortfolioDto.HoldingDto> holdings = portfolio.getHoldings().stream()
                .map(this::mapToHoldingDto)
                .collect(Collectors.toList());

        return PortfolioDto.builder()
                .accountId(portfolio.getAccount().getId())
                .totalValue(portfolio.getTotalValue())
                .totalCost(portfolio.getTotalCost())
                .totalGainLoss(portfolio.getTotalGainLoss())
                .totalGainLossPercentage(calculateReturnPercentage(portfolio.getTotalCost(), portfolio.getTotalValue()))
                .dayGainLoss(portfolio.getDayGainLoss())
                .holdings(holdings)
                .lastUpdated(portfolio.getLastUpdated())
                .build();
    }

    private PortfolioDto.HoldingDto mapToHoldingDto(InvestmentHolding holding) {
        return PortfolioDto.HoldingDto.builder()
                .id(holding.getId())
                .symbol(holding.getSymbol())
                .quantity(holding.getQuantity())
                .averageCost(holding.getAverageCost())
                .currentPrice(holding.getCurrentPrice())
                .marketValue(holding.getMarketValue())
                .unrealizedGainLoss(holding.getUnrealizedGainLoss())
                .unrealizedGainLossPercentage(calculateReturnPercentage(holding.getTotalCost(), holding.getMarketValue()))
                .lastUpdated(holding.getLastUpdated())
                .build();
    }

    private void updatePortfolioWithMarketData(PortfolioDto portfolio, Map<String, StockQuoteDto> quotes) {
        BigDecimal dayGainLoss = BigDecimal.ZERO;

        for (PortfolioDto.HoldingDto holding : portfolio.getHoldings()) {
            StockQuoteDto quote = quotes.get(holding.getSymbol());
            if (quote != null) {
                holding.setCurrentPrice(quote.getPrice());
                holding.setMarketValue(holding.getQuantity().multiply(quote.getPrice()));
                
                BigDecimal holdingDayGainLoss = holding.getQuantity().multiply(quote.getChange());
                dayGainLoss = dayGainLoss.add(holdingDayGainLoss);
                
                holding.setDayGainLoss(holdingDayGainLoss);
                holding.setDayGainLossPercentage(quote.getChangePercent());
            }
        }

        portfolio.setDayGainLoss(dayGainLoss);
    }

    private BigDecimal calculateReturnPercentage(BigDecimal cost, BigDecimal value) {
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.subtract(cost)
                .divide(cost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private String categorizeAssetClass(String symbol) {
        // Simplified categorization - in production, this would use proper data sources
        if (symbol.matches("^[A-Z]{3,4}$")) {
            return "STOCKS";
        } else if (symbol.contains("BOND") || symbol.contains("TLT")) {
            return "BONDS";
        } else if (symbol.contains("GLD") || symbol.contains("SLV")) {
            return "COMMODITIES";
        } else if (symbol.contains("REIT")) {
            return "REAL_ESTATE";
        } else {
            return "OTHER";
        }
    }

    private String getSectorForSymbol(String symbol) {
        // Simplified sector mapping - in production, this would use proper data sources
        Map<String, String> sectorMap = Map.of(
                "AAPL", "Technology",
                "MSFT", "Technology",
                "JPM", "Financials",
                "JNJ", "Healthcare",
                "XOM", "Energy",
                "AMZN", "Consumer Discretionary",
                "WMT", "Consumer Staples"
        );
        
        return sectorMap.getOrDefault(symbol, "Other");
    }

    private BigDecimal calculatePortfolioValueAtDate(Portfolio portfolio, LocalDate date) {
        try {
            log.debug("Calculating portfolio value for date: {} for portfolio: {}", date, portfolio.getId());
            
            // If date is today, return current value
            if (date.equals(LocalDate.now())) {
                return portfolio.getTotalValue();
            }
            
            BigDecimal totalValue = BigDecimal.ZERO;
            
            for (InvestmentHolding holding : portfolio.getHoldings()) {
                BigDecimal historicalValue = calculateHoldingValueAtDate(holding, date);
                totalValue = totalValue.add(historicalValue);
            }
            
            // Add cash position at the specified date
            BigDecimal cashPositionAtDate = getCashPositionAtDate(portfolio, date);
            totalValue = totalValue.add(cashPositionAtDate);
            
            log.debug("Calculated historical portfolio value: {} for date: {}", totalValue, date);
            return totalValue;
            
        } catch (Exception e) {
            log.error("Error calculating portfolio value at date {}: {}", date, e.getMessage(), e);
            // Fallback to current value if historical calculation fails
            return portfolio.getTotalValue();
        }
    }
    
    /**
     * Calculate the value of a specific holding at a historical date
     */
    private BigDecimal calculateHoldingValueAtDate(InvestmentHolding holding, LocalDate date) {
        try {
            // Get quantity of holding at the specified date
            BigDecimal quantityAtDate = getQuantityAtDate(holding, date);
            
            if (quantityAtDate.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            
            // Get historical price for the symbol at the specified date
            Optional<HistoricalPrice> historicalPrice = marketDataService.getHistoricalPrice(holding.getSymbol(), date);
            
            if (historicalPrice.isPresent()) {
                BigDecimal priceAtDate = historicalPrice.get().getClosePrice();
                return quantityAtDate.multiply(priceAtDate);
            } else {
                // If no historical price is available, try to estimate using nearest available data
                Optional<HistoricalPrice> nearestPrice = marketDataService.getNearestHistoricalPrice(holding.getSymbol(), date);
                
                if (nearestPrice.isPresent()) {
                    log.warn("Using nearest available price for {} on {}: using price from {}", 
                        holding.getSymbol(), date, nearestPrice.get().getPriceDate());
                    return quantityAtDate.multiply(nearestPrice.get().getClosePrice());
                } else {
                    // Last resort: use current price
                    log.warn("No historical price available for {} on {}, using current price", holding.getSymbol(), date);
                    return quantityAtDate.multiply(holding.getCurrentPrice());
                }
            }
            
        } catch (Exception e) {
            log.error("Error calculating holding value at date for {}: {}", holding.getSymbol(), e.getMessage(), e);
            // Fallback to current value
            return holding.getMarketValue();
        }
    }
    
    /**
     * Get the quantity of a holding at a specific historical date
     */
    private BigDecimal getQuantityAtDate(InvestmentHolding holding, LocalDate date) {
        try {
            // Query transactions to calculate cumulative quantity up to the specified date
            List<InvestmentTransaction> transactionsUpToDate = transactionRepository
                .findBySymbolAndPortfolioIdAndTransactionDateBeforeOrderByTransactionDateAsc(
                    holding.getSymbol(), 
                    holding.getPortfolio().getId(), 
                    date.atTime(23, 59, 59)
                );
            
            BigDecimal cumulativeQuantity = BigDecimal.ZERO;
            
            for (InvestmentTransaction transaction : transactionsUpToDate) {
                switch (transaction.getTransactionType()) {
                    case BUY, DIVIDEND_REINVESTMENT, STOCK_SPLIT, BONUS_ISSUE -> 
                        cumulativeQuantity = cumulativeQuantity.add(transaction.getQuantity());
                    case SELL, STOCK_MERGE -> 
                        cumulativeQuantity = cumulativeQuantity.subtract(transaction.getQuantity());
                    case TRANSFER_IN -> 
                        cumulativeQuantity = cumulativeQuantity.add(transaction.getQuantity());
                    case TRANSFER_OUT -> 
                        cumulativeQuantity = cumulativeQuantity.subtract(transaction.getQuantity());
                }
            }
            
            return cumulativeQuantity.max(BigDecimal.ZERO); // Ensure non-negative
            
        } catch (Exception e) {
            log.error("Error calculating quantity at date for {}: {}", holding.getSymbol(), e.getMessage(), e);
            // Fallback to current quantity
            return holding.getQuantity();
        }
    }
    
    /**
     * Get cash position at a specific historical date
     */
    private BigDecimal getCashPositionAtDate(Portfolio portfolio, LocalDate date) {
        try {
            // Get all cash transactions up to the specified date
            List<CashTransaction> cashTransactions = cashTransactionRepository
                .findByPortfolioIdAndTransactionDateBeforeOrderByTransactionDateAsc(
                    portfolio.getId(),
                    date.atTime(23, 59, 59)
                );
            
            BigDecimal cumulativeCash = portfolio.getAccount().getInitialCashBalance(); // Starting balance
            
            for (CashTransaction transaction : cashTransactions) {
                switch (transaction.getTransactionType()) {
                    case DEPOSIT, DIVIDEND, INTEREST, SALE_PROCEEDS -> 
                        cumulativeCash = cumulativeCash.add(transaction.getAmount());
                    case WITHDRAWAL, PURCHASE, FEE -> 
                        cumulativeCash = cumulativeCash.subtract(transaction.getAmount());
                }
            }
            
            return cumulativeCash.max(BigDecimal.ZERO); // Ensure non-negative
            
        } catch (Exception e) {
            log.error("Error calculating cash position at date: {}", e.getMessage(), e);
            // Fallback to current cash balance
            return portfolio.getAccount().getCashBalance();
        }
    }

    private BigDecimal calculateConcentrationRisk(Portfolio portfolio) {
        if (portfolio.getHoldings().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalValue = portfolio.getTotalValue();
        BigDecimal largestHoldingValue = portfolio.getHoldings().stream()
                .map(InvestmentHolding::getMarketValue)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return largestHoldingValue.divide(totalValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateOverallRiskScore(BigDecimal var95, BigDecimal maxDrawdown, BigDecimal concentration) {
        // Simple risk scoring algorithm
        BigDecimal varScore = var95.abs().multiply(BigDecimal.valueOf(0.4));
        BigDecimal drawdownScore = maxDrawdown.abs().multiply(BigDecimal.valueOf(0.3));
        BigDecimal concentrationScore = concentration.multiply(BigDecimal.valueOf(0.3));
        
        return varScore.add(drawdownScore).add(concentrationScore)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private List<String> getStocksForAssetClass(Portfolio portfolio, String assetClass) {
        return portfolio.getHoldings().stream()
                .filter(holding -> categorizeAssetClass(holding.getSymbol()).equals(assetClass))
                .map(InvestmentHolding::getSymbol)
                .collect(Collectors.toList());
    }

    private List<RebalanceRecommendationDto.StockRecommendation> generateSpecificStockRecommendations(
            List<String> stocks, BigDecimal adjustmentValue) {
        
        List<RebalanceRecommendationDto.StockRecommendation> recommendations = new ArrayList<>();
        
        if (stocks.isEmpty()) {
            return recommendations;
        }

        // Distribute adjustment evenly across stocks
        BigDecimal perStockAdjustment = adjustmentValue.divide(
                BigDecimal.valueOf(stocks.size()), 2, RoundingMode.HALF_UP);

        for (String symbol : stocks) {
            recommendations.add(RebalanceRecommendationDto.StockRecommendation.builder()
                    .symbol(symbol)
                    .adjustmentValue(perStockAdjustment)
                    .build());
        }

        return recommendations;
    }
}