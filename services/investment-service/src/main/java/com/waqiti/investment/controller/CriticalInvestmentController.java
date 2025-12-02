package com.waqiti.investment.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.investment.dto.*;
import com.waqiti.investment.service.InvestmentOrderService;
import com.waqiti.investment.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL INVESTMENT OPERATIONS CONTROLLER
 * 
 * Handles high-risk investment trading operations with strict rate limiting
 * to prevent market manipulation, excessive speculation, and trading abuse:
 * 
 * - Investment orders: 30 requests/minute per user
 * - Order cancellations: 10 requests/minute per user
 * - Portfolio operations: Protected with additional limits
 * 
 * Rate limits designed to prevent high-frequency trading abuse while allowing
 * legitimate investment activities according to SEC regulations.
 */
@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Critical Investment Operations", description = "High-security investment operations with strict rate limiting")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('USER')")
public class CriticalInvestmentController {

    private final InvestmentOrderService orderService;
    private final PortfolioService portfolioService;

    /**
     * CRITICAL ENDPOINT: Investment Order Placement
     * Rate Limited: 30 requests/minute per user
     * Priority: HIGH
     */
    @PostMapping("/orders")
    @RateLimit(
        requests = 30,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        burstCapacity = 10,
        alertThreshold = 0.8,
        description = "Investment order endpoint - HFT protection",
        errorMessage = "Investment order rate limit exceeded. Maximum 30 orders per minute allowed to prevent excessive speculation."
    )
    @Operation(
        summary = "Place investment order",
        description = "Places an investment order (buy/sell stocks, bonds, ETFs). Limited to 30 orders per minute per user to prevent high-frequency trading abuse."
    )
    @PreAuthorize("hasAuthority('INVESTMENT_TRADE') and @investmentValidator.canPlaceOrder(authentication.name, #request)")
    public ResponseEntity<ApiResponse<InvestmentOrderResponse>> placeOrder(
            @Valid @RequestBody InvestmentOrderRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Trading-Session", required = false) String tradingSession) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRITICAL_INVESTMENT_ORDER: User {} placing {} order for {} shares of {} at {} - Session: {}", 
                userId, request.getOrderType(), request.getQuantity(), request.getSymbol(), 
                request.getLimitPrice(), tradingSession);
        
        try {
            // Additional validation for large orders
            if (request.getQuantity().multiply(request.getLimitPrice()).compareTo(new java.math.BigDecimal("100000")) >= 0) {
                log.warn("LARGE_INVESTMENT_ORDER: User {} placing large order for ${} - REQUIRES REVIEW", 
                        userId, request.getQuantity().multiply(request.getLimitPrice()));
            }
            
            // Check for potential day trading violations (Pattern Day Trader rules)
            if (orderService.isPotentialDayTradingViolation(userId, request)) {
                log.error("DAY_TRADING_VIOLATION: Order blocked for user {} - PDT rule violation", userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Order blocked: Pattern Day Trading rule violation. Minimum $25,000 account balance required."));
            }
            
            // Check for wash sale violations
            if (orderService.isPotentialWashSale(userId, request)) {
                log.warn("POTENTIAL_WASH_SALE: Order may trigger wash sale rule for user {} - Symbol: {}", 
                        userId, request.getSymbol());
            }
            
            InvestmentOrderResponse response = orderService.placeOrder(request, userId, requestId, tradingSession);
            
            log.info("CRITICAL_INVESTMENT_ORDER_SUCCESS: Order {} placed for user {} - {} {} shares at {}", 
                    response.getOrderId(), userId, request.getOrderType(), request.getQuantity(), request.getLimitPrice());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Investment order placed successfully"));
            
        } catch (Exception e) {
            log.error("CRITICAL_INVESTMENT_ORDER_ERROR: Order error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Investment order service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Cancel Investment Order
     * Rate Limited: 10 requests/minute per user
     * Priority: MEDIUM
     */
    @PostMapping("/cancel")
    @RateLimit(
        requests = 10,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.MEDIUM,
        burstCapacity = 3,
        alertThreshold = 0.7,
        description = "Investment order cancellation endpoint",
        errorMessage = "Order cancellation rate limit exceeded. Maximum 10 cancellations per minute allowed."
    )
    @Operation(
        summary = "Cancel investment order",
        description = "Cancels a pending investment order. Limited to 10 cancellations per minute per user to prevent order manipulation."
    )
    @PreAuthorize("@investmentOrderValidator.canCancelOrder(authentication.name, #request.orderId)")
    public ResponseEntity<ApiResponse<InvestmentOrderCancellationResponse>> cancelOrder(
            @Valid @RequestBody InvestmentOrderCancellationRequest request,
            @RequestHeader("X-Request-ID") String requestId) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("INVESTMENT_ORDER_CANCEL: User {} cancelling order {} - Reason: {}", 
                userId, request.getOrderId(), request.getCancellationReason());
        
        try {
            // Check if order can still be cancelled (not filled)
            if (!orderService.canCancelOrder(request.getOrderId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Order cannot be cancelled - already filled or partially filled"));
            }
            
            InvestmentOrderCancellationResponse response = orderService.cancelOrder(
                request.getOrderId(), userId, request.getCancellationReason(), requestId);
            
            log.info("INVESTMENT_ORDER_CANCELLED: Order {} cancelled by user {} - Reason: {}", 
                    request.getOrderId(), userId, request.getCancellationReason());
            
            return ResponseEntity.ok(ApiResponse.success(response, "Investment order cancelled successfully"));
            
        } catch (Exception e) {
            log.error("INVESTMENT_ORDER_CANCEL_ERROR: Cancel error for order {} user {} - Error: {}", 
                    request.getOrderId(), userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Order cancellation service temporarily unavailable."));
        }
    }

    /**
     * ENDPOINT: Get Investment Portfolio
     * Rate Limited: 60 requests/minute per user
     * Priority: LOW
     */
    @GetMapping("/portfolio")
    @RateLimit(
        requests = 60,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        burstCapacity = 20,
        description = "Investment portfolio endpoint"
    )
    @Operation(
        summary = "Get investment portfolio",
        description = "Retrieves user's investment portfolio with current positions and performance. Limited to 60 requests per minute per user."
    )
    public ResponseEntity<ApiResponse<InvestmentPortfolioResponse>> getPortfolio(
            @RequestHeader("X-Request-ID") String requestId,
            @RequestParam(required = false, defaultValue = "false") boolean includePerformance) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        try {
            InvestmentPortfolioResponse response = portfolioService.getPortfolio(userId, includePerformance, requestId);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Portfolio retrieved successfully"));
            
        } catch (Exception e) {
            log.error("INVESTMENT_PORTFOLIO_ERROR: Portfolio error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Portfolio service temporarily unavailable."));
        }
    }

    /**
     * ENDPOINT: Get Order History
     * Rate Limited: 30 requests/minute per user
     * Priority: LOW
     */
    @GetMapping("/orders/history")
    @RateLimit(
        requests = 30,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        burstCapacity = 10,
        description = "Investment order history endpoint"
    )
    @Operation(
        summary = "Get investment order history",
        description = "Retrieves user's investment order history. Limited to 30 requests per minute per user."
    )
    public ResponseEntity<ApiResponse<InvestmentOrderHistoryResponse>> getOrderHistory(
            @RequestHeader("X-Request-ID") String requestId,
            @RequestParam(required = false, defaultValue = "30") int days,
            @RequestParam(required = false) String symbol) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        try {
            InvestmentOrderHistoryResponse response = orderService.getOrderHistory(userId, days, symbol, requestId);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Order history retrieved successfully"));
            
        } catch (Exception e) {
            log.error("INVESTMENT_ORDER_HISTORY_ERROR: History error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Order history service temporarily unavailable."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Execute Fractional Share Purchase
     * Rate Limited: 20 requests/minute per user
     * Priority: MEDIUM
     */
    @PostMapping("/fractional")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.MEDIUM,
        burstCapacity = 5,
        description = "Fractional share purchase endpoint"
    )
    @Operation(
        summary = "Purchase fractional shares",
        description = "Purchases fractional shares for high-priced securities. Limited to 20 purchases per minute per user."
    )
    @PreAuthorize("hasAuthority('INVESTMENT_FRACTIONAL') and @investmentValidator.canPurchaseFractional(authentication.name, #request)")
    public ResponseEntity<ApiResponse<FractionalShareResponse>> purchaseFractionalShares(
            @Valid @RequestBody FractionalShareRequest request,
            @RequestHeader("X-Request-ID") String requestId) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("FRACTIONAL_SHARE_PURCHASE: User {} purchasing ${} of {} - Session: {}", 
                userId, request.getDollarAmount(), request.getSymbol(), requestId);
        
        try {
            FractionalShareResponse response = orderService.purchaseFractionalShares(request, userId, requestId);
            
            log.info("FRACTIONAL_SHARE_SUCCESS: Purchase {} completed for user {} - {} shares of {}", 
                    response.getPurchaseId(), userId, response.getSharesAcquired(), request.getSymbol());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Fractional shares purchased successfully"));
            
        } catch (Exception e) {
            log.error("FRACTIONAL_SHARE_ERROR: Purchase error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Fractional share service temporarily unavailable."));
        }
    }

    /**
     * Monitoring endpoint for investment rate limits
     */
    @GetMapping("/rate-limit/status")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        description = "Investment rate limit status check"
    )
    @Operation(summary = "Check investment trading rate limit status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInvestmentRateLimitStatus() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Map<String, Object> status = Map.of(
            "userId", userId,
            "service", "investment-service",
            "endpoint", "trading",
            "timestamp", System.currentTimeMillis(),
            "message", "Investment trading rate limit status check completed"
        );
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}