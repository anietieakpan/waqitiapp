package com.waqiti.investment.service;

import com.waqiti.investment.dto.MarketDataDto;
import com.waqiti.investment.dto.StockQuoteDto;
import com.waqiti.investment.dto.HistoricalDataDto;
import com.waqiti.investment.exception.MarketDataException;
import com.waqiti.common.financial.BigDecimalMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for fetching real-time and historical market data
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataService {
    
    @org.springframework.context.annotation.Lazy
    private final MarketDataService self;

    private static final int CACHE_TTL_SECONDS = 60; // 1 minute cache for real-time data
    private static final int HISTORICAL_CACHE_TTL_SECONDS = 3600; // 1 hour cache for historical data

    /**
     * Get real-time stock quote
     */
    @Cacheable(value = "stockQuotes", key = "#symbol", unless = "#result == null")
    public StockQuoteDto getStockQuote(String symbol) {
        try {
            Stock stock = YahooFinance.get(symbol.toUpperCase());
            if (stock == null || stock.getQuote() == null) {
                throw new MarketDataException("Stock not found: " + symbol);
            }

            return mapToStockQuoteDto(stock);
        } catch (IOException e) {
            log.error("Error fetching stock quote for symbol: {}", symbol, e);
            throw new MarketDataException("Failed to fetch stock quote for " + symbol, e)
                .withMetadata("symbol", symbol)
                .withMetadata("provider", "yahoo-finance");
        }
    }

    /**
     * Get multiple stock quotes in batch
     */
    @Cacheable(value = "batchStockQuotes", key = "#symbols.toString()", unless = "#result == null")
    public Map<String, StockQuoteDto> getBatchStockQuotes(List<String> symbols) {
        try {
            String[] symbolArray = symbols.stream()
                    .map(String::toUpperCase)
                    .toArray(String[]::new);
            
            Map<String, Stock> stocks = YahooFinance.get(symbolArray);
            
            return stocks.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue().getQuote() != null)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> mapToStockQuoteDto(entry.getValue())
                    ));
        } catch (IOException e) {
            log.error("Error fetching batch stock quotes", e);
            throw new MarketDataException("Failed to fetch batch stock quotes: " + e.getMessage());
        }
    }

    /**
     * Get historical stock data
     */
    @Cacheable(value = "historicalData", key = "#symbol + '_' + #from + '_' + #to + '_' + #interval", unless = "#result == null")
    public HistoricalDataDto getHistoricalData(String symbol, Calendar from, Calendar to, Interval interval) {
        try {
            Stock stock = YahooFinance.get(symbol.toUpperCase(), from, to, interval);
            if (stock == null) {
                throw new MarketDataException("Stock not found: " + symbol);
            }

            List<HistoricalQuote> history = stock.getHistory();
            if (history == null || history.isEmpty()) {
                throw new MarketDataException("No historical data found for: " + symbol);
            }

            return HistoricalDataDto.builder()
                    .symbol(symbol.toUpperCase())
                    .interval(interval.name())
                    .quotes(history.stream()
                            .map(this::mapToHistoricalQuote)
                            .collect(Collectors.toList()))
                    .build();
        } catch (IOException e) {
            log.error("Error fetching historical data for symbol: {}", symbol, e);
            throw new MarketDataException("Failed to fetch historical data: " + e.getMessage());
        }
    }

    /**
     * Get market indices (S&P 500, NASDAQ, DOW)
     */
    @Cacheable(value = "marketIndices", unless = "#result == null")
    public Map<String, MarketDataDto> getMarketIndices() {
        List<String> indices = Arrays.asList("^GSPC", "^IXIC", "^DJI");
        Map<String, String> indexNames = Map.of(
                "^GSPC", "S&P 500",
                "^IXIC", "NASDAQ",
                "^DJI", "DOW JONES"
        );

        try {
            Map<String, Stock> stocks = YahooFinance.get(indices.toArray(new String[0]));
            Map<String, MarketDataDto> result = new HashMap<>();

            for (Map.Entry<String, Stock> entry : stocks.entrySet()) {
                Stock stock = entry.getValue();
                if (stock != null && stock.getQuote() != null) {
                    MarketDataDto marketData = MarketDataDto.builder()
                            .symbol(entry.getKey())
                            .name(indexNames.get(entry.getKey()))
                            .price(stock.getQuote().getPrice())
                            .change(stock.getQuote().getChange())
                            .changePercent(stock.getQuote().getChangeInPercent())
                            .volume(stock.getQuote().getVolume())
                            .lastUpdated(LocalDateTime.now())
                            .build();
                    result.put(entry.getKey(), marketData);
                }
            }

            return result;
        } catch (IOException e) {
            log.error("Error fetching market indices", e);
            throw new MarketDataException("Failed to fetch market indices: " + e.getMessage());
        }
    }

    /**
     * Calculate technical indicators
     */
    public Map<String, BigDecimal> calculateTechnicalIndicators(String symbol, int period) {
        try {
            Calendar from = Calendar.getInstance();
            from.add(Calendar.DAY_OF_MONTH, -period * 2); // Get extra data for calculations
            Calendar to = Calendar.getInstance();

            Stock stock = YahooFinance.get(symbol.toUpperCase(), from, to, Interval.DAILY);
            List<HistoricalQuote> history = stock.getHistory();

            if (history.size() < period) {
                throw new MarketDataException("Insufficient historical data for technical analysis");
            }

            Map<String, BigDecimal> indicators = new HashMap<>();

            // Calculate Simple Moving Average (SMA)
            BigDecimal sma = calculateSMA(history, period);
            indicators.put("SMA_" + period, sma);

            // Calculate Exponential Moving Average (EMA)
            BigDecimal ema = calculateEMA(history, period);
            indicators.put("EMA_" + period, ema);

            // Calculate RSI (Relative Strength Index)
            BigDecimal rsi = calculateRSI(history, 14);
            indicators.put("RSI_14", rsi);

            // Calculate Bollinger Bands
            Map<String, BigDecimal> bollingerBands = calculateBollingerBands(history, 20);
            indicators.putAll(bollingerBands);

            return indicators;
        } catch (IOException e) {
            log.error("Error calculating technical indicators for symbol: {}", symbol, e);
            throw new MarketDataException("Failed to calculate technical indicators: " + e.getMessage());
        }
    }

    /**
     * Get top movers (gainers and losers)
     */
    @Cacheable(value = "topMovers", unless = "#result == null")
    public Map<String, List<StockQuoteDto>> getTopMovers() {
        // In a real implementation, this would fetch from a proper API
        // For now, we'll use a predefined list of popular stocks
        List<String> popularStocks = Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", 
                "FB", "NVDA", "JPM", "JNJ", "V"
        );

        Map<String, StockQuoteDto> quotes = self.getBatchStockQuotes(popularStocks);
        
        List<StockQuoteDto> gainers = quotes.values().stream()
                .filter(q -> q.getChangePercent() != null && q.getChangePercent().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getChangePercent().compareTo(a.getChangePercent()))
                .limit(5)
                .collect(Collectors.toList());

        List<StockQuoteDto> losers = quotes.values().stream()
                .filter(q -> q.getChangePercent() != null && q.getChangePercent().compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing(StockQuoteDto::getChangePercent))
                .limit(5)
                .collect(Collectors.toList());

        Map<String, List<StockQuoteDto>> result = new HashMap<>();
        result.put("gainers", gainers);
        result.put("losers", losers);
        
        return result;
    }

    /**
     * Search stocks by name or symbol
     */
    public CompletableFuture<List<StockQuoteDto>> searchStocks(String query) {
        return CompletableFuture.supplyAsync(() -> {
            // This is a simplified implementation
            // In production, you would use a proper search API
            List<String> commonStocks = Arrays.asList(
                    "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA",
                    "FB", "NVDA", "JPM", "JNJ", "V",
                    "MA", "PG", "HD", "DIS", "PYPL",
                    "NFLX", "ADBE", "CRM", "INTC", "CSCO"
            );

            List<String> matchingSymbols = commonStocks.stream()
                    .filter(symbol -> symbol.toLowerCase().contains(query.toLowerCase()))
                    .limit(10)
                    .collect(Collectors.toList());

            if (matchingSymbols.isEmpty()) {
                return Collections.emptyList();
            }

            return self.getBatchStockQuotes(matchingSymbols).values().stream()
                    .collect(Collectors.toList());
        });
    }

    // Helper methods

    private StockQuoteDto mapToStockQuoteDto(Stock stock) {
        return StockQuoteDto.builder()
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .price(stock.getQuote().getPrice())
                .previousClose(stock.getQuote().getPreviousClose())
                .open(stock.getQuote().getOpen())
                .dayHigh(stock.getQuote().getDayHigh())
                .dayLow(stock.getQuote().getDayLow())
                .volume(stock.getQuote().getVolume())
                .avgVolume(stock.getQuote().getAvgVolume())
                .marketCap(stock.getStats() != null ? stock.getStats().getMarketCap() : null)
                .peRatio(stock.getStats() != null ? stock.getStats().getPe() : null)
                .eps(stock.getStats() != null ? stock.getStats().getEps() : null)
                .change(stock.getQuote().getChange())
                .changePercent(stock.getQuote().getChangeInPercent())
                .yearHigh(stock.getQuote().getYearHigh())
                .yearLow(stock.getQuote().getYearLow())
                .lastTradeTime(stock.getQuote().getLastTradeTime() != null ? 
                        LocalDateTime.ofInstant(stock.getQuote().getLastTradeTime().toInstant(), ZoneId.systemDefault()) : 
                        LocalDateTime.now())
                .build();
    }

    private HistoricalDataDto.HistoricalQuote mapToHistoricalQuote(HistoricalQuote quote) {
        return HistoricalDataDto.HistoricalQuote.builder()
                .date(LocalDateTime.ofInstant(quote.getDate().toInstant(), ZoneId.systemDefault()))
                .open(quote.getOpen())
                .high(quote.getHigh())
                .low(quote.getLow())
                .close(quote.getClose())
                .adjClose(quote.getAdjClose())
                .volume(quote.getVolume())
                .build();
    }

    private BigDecimal calculateSMA(List<HistoricalQuote> history, int period) {
        if (history.size() < period) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(history.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateEMA(List<HistoricalQuote> history, int period) {
        if (history.size() < period) {
            return BigDecimal.ZERO;
        }

        BigDecimal multiplier = BigDecimal.valueOf(2).divide(
                BigDecimal.valueOf(period + 1), 4, RoundingMode.HALF_UP);

        // Start with SMA for initial EMA
        BigDecimal ema = calculateSMA(history.subList(history.size() - period, history.size()), period);

        // Calculate EMA for remaining data points
        for (int i = history.size() - period - 1; i >= 0; i--) {
            BigDecimal currentPrice = history.get(i).getClose();
            ema = currentPrice.multiply(multiplier)
                    .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }

        return ema.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRSI(List<HistoricalQuote> history, int period) {
        if (history.size() < period + 1) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;

        // Calculate initial average gain and loss
        for (int i = 1; i <= period; i++) {
            BigDecimal change = history.get(i - 1).getClose()
                    .subtract(history.get(i).getClose());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }

        avgGain = avgGain.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP)
        );

        return rsi;
    }

    private Map<String, BigDecimal> calculateBollingerBands(List<HistoricalQuote> history, int period) {
        Map<String, BigDecimal> bands = new HashMap<>();

        BigDecimal sma = calculateSMA(history, period);
        bands.put("BB_MIDDLE", sma);

        // Calculate standard deviation
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            BigDecimal diff = history.get(i).getClose().subtract(sma);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }

        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimalMath.sqrt(variance);

        bands.put("BB_UPPER", sma.add(stdDev.multiply(BigDecimal.valueOf(2))));
        bands.put("BB_LOWER", sma.subtract(stdDev.multiply(BigDecimal.valueOf(2))));

        return bands;
    }
}