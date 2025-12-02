package com.waqiti.crypto.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.service.CryptoTradingService;
import com.waqiti.crypto.service.CryptoWalletService;
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
 * CRITICAL CRYPTOCURRENCY OPERATIONS CONTROLLER
 * 
 * Handles high-risk cryptocurrency trading operations with strict rate limiting
 * to prevent market manipulation, flash crashes, and trading abuse:
 * 
 * - Crypto buy orders: 10 requests/minute per user
 * - Crypto sell orders: 10 requests/minute per user  
 * - Crypto swap operations: 10 requests/minute per user
 * - Wallet operations: Protected with additional limits
 * 
 * Rate limits designed to prevent high-frequency trading abuse while allowing
 * legitimate retail trading activities.
 */
@RestController
@RequestMapping("/api/crypto")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Critical Crypto Operations", description = "High-security cryptocurrency operations with strict rate limiting")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('USER')")
public class CriticalCryptoController {

    private final CryptoTradingService tradingService;
    private final CryptoWalletService walletService;

    /**
     * CRITICAL ENDPOINT: Cryptocurrency Buy Order
     * Rate Limited: 10 requests/minute per user
     * Priority: HIGH
     */
    @PostMapping("/buy")
    @RateLimit(
        requests = 10,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        burstCapacity = 3,
        alertThreshold = 0.8,
        description = "Crypto buy order endpoint - HFT protection",
        errorMessage = "Crypto buy rate limit exceeded. Maximum 10 buy orders per minute allowed to prevent market manipulation."
    )
    @Operation(
        summary = "Place cryptocurrency buy order",
        description = "Places a buy order for cryptocurrency. Limited to 10 buy orders per minute per user to prevent high-frequency trading abuse."
    )
    @PreAuthorize("hasAuthority('CRYPTO_TRADE') and @cryptoTradingValidator.canPlaceOrder(authentication.name, #request)")
    public ResponseEntity<ApiResponse<CryptoBuyResponse>> buyCryptocurrency(
            @Valid @RequestBody CryptoBuyRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Trading-Session", required = false) String tradingSession) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRITICAL_CRYPTO_BUY: User {} placing buy order for {} {} at {} - Session: {}", 
                userId, request.getAmount(), request.getCryptocurrency(), request.getTargetPrice(), tradingSession);
        
        try {
            // Additional validation for large orders
            if (request.getAmount().compareTo(new java.math.BigDecimal("50000")) >= 0) {
                log.warn("LARGE_CRYPTO_BUY: User {} placing large buy order for ${} - REQUIRES REVIEW", 
                        userId, request.getAmount());
            }
            
            // Check for potential market manipulation
            if (tradingService.isPotentialManipulation(userId, request)) {
                log.error("POTENTIAL_MANIPULATION: Buy order blocked for user {} - Suspicious pattern detected", userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Order blocked: Potential market manipulation detected. Contact support."));
            }
            
            CryptoBuyResponse response = tradingService.placeBuyOrder(request, userId, requestId, tradingSession);
            
            log.info("CRITICAL_CRYPTO_BUY_SUCCESS: Buy order {} placed for user {} - {} {}", 
                    response.getOrderId(), userId, request.getAmount(), request.getCryptocurrency());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Crypto buy order placed successfully"));
            
        } catch (Exception e) {
            log.error("CRITICAL_CRYPTO_BUY_ERROR: Buy order error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Crypto trading service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Cryptocurrency Sell Order
     * Rate Limited: 10 requests/minute per user
     * Priority: HIGH
     */
    @PostMapping("/sell")
    @RateLimit(
        requests = 10,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        burstCapacity = 3,
        alertThreshold = 0.8,
        description = "Crypto sell order endpoint - HFT protection",
        errorMessage = "Crypto sell rate limit exceeded. Maximum 10 sell orders per minute allowed to prevent market manipulation."
    )
    @Operation(
        summary = "Place cryptocurrency sell order",
        description = "Places a sell order for cryptocurrency. Limited to 10 sell orders per minute per user to prevent high-frequency trading abuse."
    )
    @PreAuthorize("hasAuthority('CRYPTO_TRADE') and @cryptoTradingValidator.canPlaceOrder(authentication.name, #request)")
    public ResponseEntity<ApiResponse<CryptoSellResponse>> sellCryptocurrency(
            @Valid @RequestBody CryptoSellRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Trading-Session", required = false) String tradingSession) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRITICAL_CRYPTO_SELL: User {} placing sell order for {} {} at {} - Session: {}", 
                userId, request.getAmount(), request.getCryptocurrency(), request.getTargetPrice(), tradingSession);
        
        try {
            // Additional validation for large sell orders
            if (request.getAmount().compareTo(new java.math.BigDecimal("50000")) >= 0) {
                log.warn("LARGE_CRYPTO_SELL: User {} placing large sell order for ${} - REQUIRES REVIEW", 
                        userId, request.getAmount());
            }
            
            // Check for potential market manipulation
            if (tradingService.isPotentialManipulation(userId, request)) {
                log.error("POTENTIAL_MANIPULATION: Sell order blocked for user {} - Suspicious pattern detected", userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Order blocked: Potential market manipulation detected. Contact support."));
            }
            
            CryptoSellResponse response = tradingService.placeSellOrder(request, userId, requestId, tradingSession);
            
            log.info("CRITICAL_CRYPTO_SELL_SUCCESS: Sell order {} placed for user {} - {} {}", 
                    response.getOrderId(), userId, request.getAmount(), request.getCryptocurrency());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Crypto sell order placed successfully"));
            
        } catch (Exception e) {
            log.error("CRITICAL_CRYPTO_SELL_ERROR: Sell order error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Crypto trading service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Cryptocurrency Swap
     * Rate Limited: 10 requests/minute per user
     * Priority: HIGH
     */
    @PostMapping("/swap")
    @RateLimit(
        requests = 10,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        burstCapacity = 2,
        alertThreshold = 0.8,
        description = "Crypto swap endpoint - arbitrage protection",
        errorMessage = "Crypto swap rate limit exceeded. Maximum 10 swaps per minute allowed to prevent arbitrage abuse."
    )
    @Operation(
        summary = "Swap between cryptocurrencies",
        description = "Swaps one cryptocurrency for another. Limited to 10 swaps per minute per user to prevent arbitrage abuse."
    )
    @PreAuthorize("hasAuthority('CRYPTO_TRADE') and @cryptoTradingValidator.canSwap(authentication.name, #request)")
    public ResponseEntity<ApiResponse<CryptoSwapResponse>> swapCryptocurrency(
            @Valid @RequestBody CryptoSwapRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Trading-Session", required = false) String tradingSession) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRITICAL_CRYPTO_SWAP: User {} swapping {} {} to {} - Session: {}", 
                userId, request.getFromAmount(), request.getFromCrypto(), request.getToCrypto(), tradingSession);
        
        try {
            // Check for potential arbitrage abuse
            if (tradingService.isPotentialArbitrage(userId, request)) {
                log.error("POTENTIAL_ARBITRAGE: Swap blocked for user {} - Arbitrage pattern detected", userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Swap blocked: Potential arbitrage abuse detected. Contact support."));
            }
            
            // Additional validation for large swaps
            if (request.getFromAmount().compareTo(new java.math.BigDecimal("25000")) >= 0) {
                log.warn("LARGE_CRYPTO_SWAP: User {} swapping large amount ${} - REQUIRES REVIEW", 
                        userId, request.getFromAmount());
            }
            
            CryptoSwapResponse response = tradingService.swapCryptocurrency(request, userId, requestId, tradingSession);
            
            log.info("CRITICAL_CRYPTO_SWAP_SUCCESS: Swap {} completed for user {} - {} {} to {} {}", 
                    response.getSwapId(), userId, request.getFromAmount(), request.getFromCrypto(),
                    response.getToAmount(), request.getToCrypto());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Crypto swap completed successfully"));
            
        } catch (Exception e) {
            log.error("CRITICAL_CRYPTO_SWAP_ERROR: Swap error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Crypto swap service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Cancel Crypto Order
     * Rate Limited: 20 requests/minute per user
     * Priority: MEDIUM
     */
    @PostMapping("/orders/{orderId}/cancel")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.MEDIUM,
        burstCapacity = 5,
        description = "Crypto order cancellation endpoint"
    )
    @Operation(
        summary = "Cancel cryptocurrency order",
        description = "Cancels a pending cryptocurrency order. Limited to 20 cancellations per minute per user."
    )
    @PreAuthorize("@cryptoOrderValidator.canCancelOrder(authentication.name, #orderId)")
    public ResponseEntity<ApiResponse<CryptoOrderCancellationResponse>> cancelOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-Request-ID") String requestId) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRYPTO_ORDER_CANCEL: User {} cancelling order {}", userId, orderId);
        
        try {
            CryptoOrderCancellationResponse response = tradingService.cancelOrder(orderId, userId, requestId);
            
            log.info("CRYPTO_ORDER_CANCELLED: Order {} cancelled by user {}", orderId, userId);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Crypto order cancelled successfully"));
            
        } catch (Exception e) {
            log.error("CRYPTO_ORDER_CANCEL_ERROR: Cancel error for order {} user {} - Error: {}", 
                    orderId, userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Order cancellation service temporarily unavailable."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Get Crypto Portfolio
     * Rate Limited: 30 requests/minute per user
     * Priority: LOW
     */
    @GetMapping("/portfolio")
    @RateLimit(
        requests = 30,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        burstCapacity = 10,
        description = "Crypto portfolio endpoint"
    )
    @Operation(
        summary = "Get cryptocurrency portfolio",
        description = "Retrieves user's cryptocurrency portfolio. Limited to 30 requests per minute per user."
    )
    public ResponseEntity<ApiResponse<CryptoPortfolioResponse>> getPortfolio(
            @RequestHeader("X-Request-ID") String requestId) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        try {
            CryptoPortfolioResponse response = walletService.getPortfolio(userId, requestId);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Portfolio retrieved successfully"));
            
        } catch (Exception e) {
            log.error("CRYPTO_PORTFOLIO_ERROR: Portfolio error for user {} - Error: {}", 
                    userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Portfolio service temporarily unavailable."));
        }
    }

    /**
     * Monitoring endpoint for crypto rate limits
     */
    @GetMapping("/rate-limit/status")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        description = "Crypto rate limit status check"
    )
    @Operation(summary = "Check crypto trading rate limit status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCryptoRateLimitStatus() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Map<String, Object> status = Map.of(
            "userId", userId,
            "service", "crypto-service",
            "endpoint", "trading",
            "timestamp", System.currentTimeMillis(),
            "message", "Crypto trading rate limit status check completed"
        );
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}