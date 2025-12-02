package com.waqiti.payment.controller;

import com.waqiti.payment.service.PaymentProviderFallbackService;
import com.waqiti.payment.service.PaymentProviderFallbackService.FallbackStatistics;
import com.waqiti.common.ratelimit.RateLimited;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for payment provider fallback monitoring and management
 */
@RestController
@RequestMapping("/api/v1/payment/providers/fallback")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Provider Fallback", description = "Payment provider fallback monitoring and management")
public class PaymentProviderFallbackController {

    private final PaymentProviderFallbackService fallbackService;

    /**
     * Get fallback statistics and provider health status
     */
    @GetMapping("/statistics")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYMENT_OPERATOR')")
    @Operation(summary = "Get fallback statistics", 
               description = "Retrieve current fallback statistics and provider health status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fallback statistics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - insufficient permissions")
    })
    public ResponseEntity<FallbackStatistics> getFallbackStatistics() {
        log.debug("Retrieving payment provider fallback statistics");
        
        FallbackStatistics statistics = fallbackService.getFallbackStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * Health check endpoint for payment provider fallback system
     */
    @GetMapping("/health")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYMENT_OPERATOR') or hasRole('SYSTEM')")
    @Operation(summary = "Fallback system health check", 
               description = "Check if the payment provider fallback system is operational")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fallback system is healthy"),
        @ApiResponse(responseCode = "503", description = "Fallback system is degraded")
    })
    public ResponseEntity<HealthStatus> getHealth() {
        log.debug("Checking payment provider fallback system health");
        
        try {
            FallbackStatistics stats = fallbackService.getFallbackStatistics();
            
            boolean isHealthy = stats.getHealthyProviders() > 0;
            boolean isDegraded = stats.getHealthyProviders() < 2;
            
            HealthStatus health = HealthStatus.builder()
                .healthy(isHealthy)
                .degraded(isDegraded)
                .totalProviders(stats.getTotalProviders())
                .healthyProviders(stats.getHealthyProviders())
                .fallbackEnabled(stats.isFallbackEnabled())
                .message(isHealthy ? 
                    (isDegraded ? "Fallback system operational but degraded" : "Fallback system fully operational") :
                    "Fallback system unavailable - no healthy providers")
                .build();
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Error checking fallback system health: {}", e.getMessage(), e);
            
            HealthStatus errorHealth = HealthStatus.builder()
                .healthy(false)
                .degraded(true)
                .message("Error checking fallback system: " + e.getMessage())
                .build();
                
            return ResponseEntity.status(503).body(errorHealth);
        }
    }

    /**
     * Health status DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HealthStatus {
        private boolean healthy;
        private boolean degraded;
        private Integer totalProviders;
        private Integer healthyProviders;
        private Boolean fallbackEnabled;
        private String message;
    }
}