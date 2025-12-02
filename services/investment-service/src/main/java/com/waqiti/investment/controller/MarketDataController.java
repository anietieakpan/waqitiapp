package com.waqiti.investment.controller;

import com.waqiti.investment.dto.*;
import com.waqiti.investment.service.MarketDataService;
import com.waqiti.investment.websocket.MarketDataWebSocketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import yahoofinance.histquotes.Interval;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for market data operations
 */
@RestController
@RequestMapping("/api/v1/market-data")
@Tag(name = "Market Data", description = "Market data and stock quotes API")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {
    "${cors.allowed-origins:http://localhost:3000,https://app.example.com,https://admin.example.com}"
})
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final MarketDataWebSocketService webSocketService;

    @GetMapping("/quote/{symbol}")
    @Operation(summary = "Get stock quote", description = "Get real-time quote for a stock symbol")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StockQuoteDto> getStockQuote(
            @Parameter(description = "Stock symbol", required = true)
            @PathVariable String symbol) {
        
        log.info("Getting stock quote for symbol: {}", symbol);
        StockQuoteDto quote = marketDataService.getStockQuote(symbol);
        return ResponseEntity.ok(quote);
    }

    @PostMapping("/quotes/batch")
    @Operation(summary = "Get batch stock quotes", description = "Get real-time quotes for multiple stock symbols")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, StockQuoteDto>> getBatchStockQuotes(
            @Parameter(description = "List of stock symbols", required = true)
            @RequestBody @Valid List<String> symbols) {
        
        log.info("Getting batch stock quotes for {} symbols", symbols.size());
        Map<String, StockQuoteDto> quotes = marketDataService.getBatchStockQuotes(symbols);
        return ResponseEntity.ok(quotes);
    }

    @GetMapping("/historical/{symbol}")
    @Operation(summary = "Get historical data", description = "Get historical price data for a stock")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<HistoricalDataDto> getHistoricalData(
            @Parameter(description = "Stock symbol", required = true)
            @PathVariable String symbol,
            @Parameter(description = "Start date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Data interval")
            @RequestParam(defaultValue = "DAILY") String interval) {
        
        log.info("Getting historical data for {} from {} to {}", symbol, from, to);
        
        Calendar fromCal = Calendar.getInstance();
        fromCal.set(from.getYear(), from.getMonthValue() - 1, from.getDayOfMonth());
        
        Calendar toCal = Calendar.getInstance();
        toCal.set(to.getYear(), to.getMonthValue() - 1, to.getDayOfMonth());
        
        Interval dataInterval = Interval.valueOf(interval);
        
        HistoricalDataDto historicalData = marketDataService.getHistoricalData(symbol, fromCal, toCal, dataInterval);
        return ResponseEntity.ok(historicalData);
    }

    @GetMapping("/indices")
    @Operation(summary = "Get market indices", description = "Get current values for major market indices")
    public ResponseEntity<Map<String, MarketDataDto>> getMarketIndices() {
        log.info("Getting market indices");
        Map<String, MarketDataDto> indices = marketDataService.getMarketIndices();
        return ResponseEntity.ok(indices);
    }

    @GetMapping("/technical-indicators/{symbol}")
    @Operation(summary = "Get technical indicators", description = "Calculate technical indicators for a stock")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, BigDecimal>> getTechnicalIndicators(
            @Parameter(description = "Stock symbol", required = true)
            @PathVariable String symbol,
            @Parameter(description = "Period for calculations")
            @RequestParam(defaultValue = "20") int period) {
        
        log.info("Calculating technical indicators for {} with period {}", symbol, period);
        Map<String, BigDecimal> indicators = marketDataService.calculateTechnicalIndicators(symbol, period);
        return ResponseEntity.ok(indicators);
    }

    @GetMapping("/top-movers")
    @Operation(summary = "Get top movers", description = "Get top gainers and losers")
    public ResponseEntity<Map<String, List<StockQuoteDto>>> getTopMovers() {
        log.info("Getting top movers");
        Map<String, List<StockQuoteDto>> topMovers = marketDataService.getTopMovers();
        return ResponseEntity.ok(topMovers);
    }

    @GetMapping("/search")
    @Operation(summary = "Search stocks", description = "Search for stocks by symbol or name")
    @PreAuthorize("hasRole('USER')")
    public CompletableFuture<ResponseEntity<List<StockQuoteDto>>> searchStocks(
            @Parameter(description = "Search query", required = true)
            @RequestParam String query) {
        
        log.info("Searching stocks with query: {}", query);
        return marketDataService.searchStocks(query)
                .thenApply(ResponseEntity::ok);
    }

    // WebSocket endpoints

    @MessageMapping("/subscribe-symbols")
    public void subscribeToSymbols(
            @Payload SubscribeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} subscribing to symbols: {}", userDetails.getUsername(), request.getSymbols());
        webSocketService.subscribeToSymbols(userDetails.getUsername(), request.getSymbols());
    }

    @MessageMapping("/unsubscribe-symbols")
    public void unsubscribeFromSymbols(
            @Payload SubscribeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} unsubscribing from symbols: {}", userDetails.getUsername(), request.getSymbols());
        webSocketService.unsubscribeFromSymbols(userDetails.getUsername(), request.getSymbols());
    }

    // Request DTOs
    @lombok.Data
    public static class SubscribeRequest {
        private List<String> symbols;
    }
}