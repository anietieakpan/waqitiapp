/**
 * Crypto Price Controller
 * REST API endpoints for cryptocurrency pricing
 */
package com.waqiti.crypto.controller;

import com.waqiti.crypto.dto.response.CryptoPriceResponse;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.service.CryptoPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/prices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Crypto Prices", description = "Cryptocurrency pricing endpoints")
public class CryptoPriceController {
    
    private final CryptoPricingService pricingService;
    
    @GetMapping("/{currency}")
    @Operation(summary = "Get current price", description = "Retrieves current price for a cryptocurrency")
    @Cacheable(value = "crypto-prices", key = "#currency")
    public ResponseEntity<CryptoPriceResponse> getCurrentPrice(
            @PathVariable CryptoCurrency currency) {
        
        log.info("Fetching current price for {}", currency);
        CryptoPriceResponse price = pricingService.getCurrentPrice(currency);
        return ResponseEntity.ok(price);
    }
    
    @GetMapping
    @Operation(summary = "Get all prices", description = "Retrieves current prices for all supported cryptocurrencies")
    @Cacheable(value = "crypto-prices-all")
    public ResponseEntity<Map<CryptoCurrency, CryptoPriceResponse>> getAllPrices() {
        
        log.info("Fetching all cryptocurrency prices");
        Map<CryptoCurrency, CryptoPriceResponse> prices = pricingService.getAllPrices();
        return ResponseEntity.ok(prices);
    }
    
    @GetMapping("/{currency}/history")
    @Operation(summary = "Get price history", description = "Retrieves historical price data")
    public ResponseEntity<List<PricePoint>> getPriceHistory(
            @PathVariable CryptoCurrency currency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "1H") String interval) {
        
        log.info("Fetching price history for {} from {} to {}", currency, startDate, endDate);
        List<PricePoint> history = pricingService.getPriceHistory(currency, startDate, endDate, interval);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/convert")
    @Operation(summary = "Convert currency", description = "Converts amount between cryptocurrencies or to/from fiat")
    public ResponseEntity<ConversionResult> convertCurrency(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        
        log.info("Converting {} {} to {}", amount, from, to);
        ConversionResult result = pricingService.convertCurrency(from, to, amount);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/market-stats")
    @Operation(summary = "Get market statistics", description = "Retrieves market statistics for cryptocurrencies")
    public ResponseEntity<MarketStats> getMarketStats() {
        
        log.info("Fetching market statistics");
        MarketStats stats = pricingService.getMarketStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * SECURITY CRITICAL: User-specific price alert endpoints
     * These endpoints MUST use JWT authentication to prevent user impersonation
     */
    @PostMapping("/alerts")
    @Operation(summary = "Set price alert", description = "Creates a price alert for a cryptocurrency")
    @PreAuthorize("hasAuthority('SCOPE_crypto:write') or hasRole('USER')")
    public ResponseEntity<PriceAlert> setPriceAlert(
            @RequestBody PriceAlertRequest request) {
        
        // SECURITY: Extract authenticated user ID from JWT token
        UUID userId = getAuthenticatedUserId();
        
        log.info("AUDIT: Setting price alert for authenticated user: {} on {} at {}", 
            userId, request.getCurrency(), request.getTargetPrice());
        
        PriceAlert alert = pricingService.setPriceAlert(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }
    
    @GetMapping("/alerts")
    @Operation(summary = "Get price alerts", description = "Retrieves user's price alerts")
    @PreAuthorize("hasAuthority('SCOPE_crypto:read') or hasRole('USER')")
    public ResponseEntity<List<PriceAlert>> getPriceAlerts() {
        
        // SECURITY: Extract authenticated user ID from JWT token
        UUID userId = getAuthenticatedUserId();
        
        log.info("AUDIT: Fetching price alerts for authenticated user: {}", userId);
        List<PriceAlert> alerts = pricingService.getUserAlerts(userId);
        return ResponseEntity.ok(alerts);
    }
    
    @DeleteMapping("/alerts/{alertId}")
    @Operation(summary = "Delete price alert", description = "Removes a price alert")
    @PreAuthorize("hasAuthority('SCOPE_crypto:write') or hasRole('USER')")
    public ResponseEntity<Void> deletePriceAlert(@PathVariable UUID alertId) {
        
        // SECURITY: Extract authenticated user ID from JWT token
        UUID userId = getAuthenticatedUserId();
        
        log.info("AUDIT: Deleting price alert: {} for authenticated user: {}", alertId, userId);
        
        // SECURITY: Verify alert ownership before deletion
        pricingService.deletePriceAlert(alertId, userId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * SECURITY: Extract authenticated user ID from JWT token
     * This prevents user impersonation attacks via X-User-Id header manipulation
     */
    private UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("No authenticated user found");
        }
        
        // Extract user ID from JWT claims
        Object userIdClaim = authentication.getDetails();
        if (userIdClaim instanceof UUID) {
            return (UUID) userIdClaim;
        }
        
        // Fallback: extract from principal name (should be user ID)
        String principalName = authentication.getName();
        try {
            return UUID.fromString(principalName);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid user ID in authentication token");
        }
    }
    
    @Data
    @Builder
    public static class PricePoint {
        private LocalDateTime timestamp;
        private BigDecimal price;
        private BigDecimal volume;
        private BigDecimal high;
        private BigDecimal low;
    }
    
    @Data
    @Builder
    public static class ConversionResult {
        private String from;
        private String to;
        private BigDecimal fromAmount;
        private BigDecimal toAmount;
        private BigDecimal exchangeRate;
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    public static class MarketStats {
        private BigDecimal totalMarketCap;
        private BigDecimal total24hVolume;
        private Integer activeCurrencies;
        private BigDecimal btcDominance;
        private BigDecimal ethDominance;
        private LocalDateTime lastUpdated;
    }
    
    @Data
    @Builder
    public static class PriceAlertRequest {
        private CryptoCurrency currency;
        private BigDecimal targetPrice;
        private AlertType alertType;
        private boolean recurring;
    }
    
    @Data
    @Builder
    public static class PriceAlert {
        private UUID id;
        private UUID userId;
        private CryptoCurrency currency;
        private BigDecimal targetPrice;
        private AlertType alertType;
        private boolean active;
        private LocalDateTime createdAt;
    }
    
    public enum AlertType {
        ABOVE, BELOW, CHANGE_PERCENT
    }
}