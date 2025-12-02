package com.waqiti.crypto.controller;

import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.crypto.dto.request.BuyCryptocurrencyRequest;
import com.waqiti.crypto.dto.request.SellCryptocurrencyRequest;
import com.waqiti.crypto.dto.request.OrderModificationRequest;
import com.waqiti.crypto.dto.response.TradeResponse;
import com.waqiti.crypto.dto.response.TradingOrderResponse;
import com.waqiti.crypto.dto.response.TradePairResponse;
import com.waqiti.crypto.entity.TradePair;
import com.waqiti.crypto.trading.CryptoTradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crypto/trading")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Crypto Trading", description = "Cryptocurrency trading and order management APIs")
public class CryptoTradingController {

    private final CryptoTradingService tradingService;

    @PostMapping("/orders/buy")
    @Operation(summary = "Place buy order", description = "Place a buy order for cryptocurrency")
    @ApiResponse(responseCode = "201", description = "Buy order placed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid order parameters")
    @ApiResponse(responseCode = "402", description = "Insufficient balance")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TradingOrderResponse> placeBuyOrder(
            @Valid @RequestBody BuyCryptocurrencyRequest request) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Placing buy order for user: {} cryptocurrency: {}", userId, request.getCryptocurrency());

        TradingOrderResponse response = tradingService.placeBuyOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/orders/sell")
    @Operation(summary = "Place sell order", description = "Place a sell order for cryptocurrency")
    @ApiResponse(responseCode = "201", description = "Sell order placed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid order parameters")
    @ApiResponse(responseCode = "402", description = "Insufficient cryptocurrency balance")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TradingOrderResponse> placeSellOrder(
            @Valid @RequestBody SellCryptocurrencyRequest request) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Placing sell order for user: {} cryptocurrency: {}", userId, request.getCryptocurrency());

        TradingOrderResponse response = tradingService.placeSellOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/orders/{orderId}")
    @Operation(summary = "Cancel order", description = "Cancel an active trading order")
    @ApiResponse(responseCode = "204", description = "Order cancelled successfully")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "400", description = "Order cannot be cancelled")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> cancelOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Cancelling order: {} for user: {}", orderId, userId);

        tradingService.cancelOrder(userId, orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get order details", description = "Retrieve details of a specific trading order")
    @ApiResponse(responseCode = "200", description = "Order details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TradingOrderResponse> getOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Getting order: {} for user: {}", orderId, userId);

        TradingOrderResponse response = tradingService.getOrder(userId, orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    @Operation(summary = "Get user orders", description = "Retrieve user's trading orders with optional status filter")
    @ApiResponse(responseCode = "200", description = "Orders retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<TradingOrderResponse>> getUserOrders(
            @Parameter(description = "Order status filter") @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Getting orders for user: {} with status: {}", userId, status);
        
        Page<TradingOrderResponse> response = tradingService.getUserOrders(userId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trades")
    @Operation(summary = "Get user trades", description = "Retrieve user's executed trades")
    @ApiResponse(responseCode = "200", description = "Trades retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TradeResponse>> getUserTrades(
            @Parameter(description = "Cryptocurrency filter") @RequestParam(required = false) String cryptocurrency,
            @PageableDefault(size = 50) Pageable pageable) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Getting trades for user: {} cryptocurrency: {}", userId, cryptocurrency);

        List<TradeResponse> response = tradingService.getUserTrades(userId, cryptocurrency, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pairs")
    @Operation(summary = "Get trading pairs", description = "Retrieve all available trading pairs")
    @ApiResponse(responseCode = "200", description = "Trading pairs retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TradePairResponse>> getTradingPairs() {
        log.info("Getting available trading pairs");
        
        List<TradePair> tradePairs = tradingService.getAvailableTradePairs();
        List<TradePairResponse> response = tradePairs.stream()
                .map(this::mapToTradePairResponse)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/fee")
    @Operation(summary = "Get trading fee", description = "Calculate trading fee for a potential trade")
    @ApiResponse(responseCode = "200", description = "Trading fee calculated successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BigDecimal> getTradingFee(
            @Parameter(description = "Cryptocurrency symbol") @RequestParam String cryptocurrency,
            @Parameter(description = "Trade amount") @RequestParam BigDecimal amount) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Calculating trading fee for user: {} cryptocurrency: {} amount: {}",
                userId, cryptocurrency, amount);

        BigDecimal fee = tradingService.getTradingFee(userId, cryptocurrency, amount);
        return ResponseEntity.ok(fee);
    }

    @GetMapping("/orderbook/{tradePair}")
    @Operation(summary = "Get order book", description = "Retrieve order book for a trading pair")
    @ApiResponse(responseCode = "200", description = "Order book retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @Parameter(description = "Trading pair (e.g., BTCUSD)") @PathVariable String tradePair,
            @Parameter(description = "Depth limit") @RequestParam(defaultValue = "20") Integer limit) {
        
        log.info("Getting order book for trading pair: {} limit: {}", tradePair, limit);
        
        // This would be implemented to return real-time order book data
        OrderBookResponse response = OrderBookResponse.builder()
                .tradePair(tradePair)
                .lastUpdateId(System.currentTimeMillis())
                .bids(List.of())
                .asks(List.of())
                .build();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ticker/{tradePair}")
    @Operation(summary = "Get 24h ticker", description = "Get 24-hour trading statistics for a pair")
    @ApiResponse(responseCode = "200", description = "Ticker data retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TickerResponse> getTicker(
            @Parameter(description = "Trading pair (e.g., BTCUSD)") @PathVariable String tradePair) {
        
        log.info("Getting ticker data for trading pair: {}", tradePair);
        
        // This would be implemented to return real-time ticker data
        TickerResponse response = TickerResponse.builder()
                .tradePair(tradePair)
                .lastPrice(BigDecimal.ZERO)
                .priceChange(BigDecimal.ZERO)
                .priceChangePercent(BigDecimal.ZERO)
                .volume(BigDecimal.ZERO)
                .quoteVolume(BigDecimal.ZERO)
                .openPrice(BigDecimal.ZERO)
                .highPrice(BigDecimal.ZERO)
                .lowPrice(BigDecimal.ZERO)
                .count(0L)
                .build();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/klines/{tradePair}")
    @Operation(summary = "Get kline/candlestick data", description = "Get kline data for technical analysis")
    @ApiResponse(responseCode = "200", description = "Kline data retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<KlineResponse>> getKlines(
            @Parameter(description = "Trading pair") @PathVariable String tradePair,
            @Parameter(description = "Time interval") @RequestParam String interval,
            @Parameter(description = "Start time") @RequestParam(required = false) Long startTime,
            @Parameter(description = "End time") @RequestParam(required = false) Long endTime,
            @Parameter(description = "Limit") @RequestParam(defaultValue = "500") Integer limit) {
        
        log.info("Getting kline data for pair: {} interval: {}", tradePair, interval);
        
        // This would be implemented to return historical price data
        List<KlineResponse> response = List.of();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{orderId}/modify")
    @Operation(summary = "Modify order", description = "Modify an existing order (price/quantity)")
    @ApiResponse(responseCode = "200", description = "Order modified successfully")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "400", description = "Order cannot be modified")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TradingOrderResponse> modifyOrder(
            @Parameter(description = "Order ID") @PathVariable String orderId,
            @Valid @RequestBody OrderModificationRequest request) {

        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        log.info("Modifying order: {} for user: {}", orderId, userId);
        
        try {
            TradingOrderResponse response = tradingService.modifyOrder(userId, orderId, request);
            log.info("Successfully modified order: {} for user: {}", orderId, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to modify order: {} for user: {}, error: {}", orderId, userId, e.getMessage());
            throw e;
        }
    }

    // Helper method to convert TradePair entity to response DTO
    private TradePairResponse mapToTradePairResponse(TradePair tradePair) {
        return TradePairResponse.builder()
                .symbol(tradePair.getSymbol())
                .displayName(tradePair.getDisplayName())
                .tradeCurrency(tradePair.getTradeCurrency().getSymbol())
                .baseCurrency(tradePair.getBaseCurrency().getSymbol())
                .active(tradePair.getActive())
                .tradingEnabled(tradePair.getTradingEnabled())
                .minTradeAmount(tradePair.getMinTradeAmount())
                .maxTradeAmount(tradePair.getMaxTradeAmount())
                .minPrice(tradePair.getMinPrice())
                .maxPrice(tradePair.getMaxPrice())
                .tickSize(tradePair.getTickSize())
                .lotSize(tradePair.getLotSize())
                .makerFee(tradePair.getMakerFee())
                .takerFee(tradePair.getTakerFee())
                .pricePrecision(tradePair.getPricePrecision())
                .quantityPrecision(tradePair.getQuantityPrecision())
                .orderTypes(tradePair.getOrderTypes())
                .timeInForce(tradePair.getTimeInForce())
                .icebergAllowed(tradePair.getIcebergAllowed())
                .ocoAllowed(tradePair.getOcoAllowed())
                .isSpotTradingAllowed(tradePair.getIsSpotTradingAllowed())
                .isMarginTradingAllowed(tradePair.getIsMarginTradingAllowed())
                .build();
    }
}

// Response DTOs for order book, ticker, and kline data would be defined here
// These are simplified for brevity