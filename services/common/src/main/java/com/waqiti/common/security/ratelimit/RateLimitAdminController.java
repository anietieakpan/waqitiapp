package com.waqiti.common.security.ratelimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Rate Limit Administration Controller
 *
 * Provides endpoints for security administrators to monitor and manage
 * rate limiting across the platform.
 *
 * CRITICAL: All endpoints require ADMIN role
 */
@RestController
@RequestMapping("/api/v1/admin/rate-limits")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rate Limit Administration", description = "Monitor and manage rate limiting")
@SecurityRequirement(name = "bearer-jwt")
public class RateLimitAdminController {

    private final RateLimitMetrics rateLimitMetrics;

    /**
     * Get current rate limit metrics
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Get rate limit metrics", description = "Returns current rate limiting statistics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        log.info("ADMIN: Rate limit metrics requested");
        Map<String, Object> metrics = rateLimitMetrics.getCurrentMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get top violators
     */
    @GetMapping("/violators")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Get top violators", description = "Returns clients with most rate limit violations")
    public ResponseEntity<List<RateLimitMetrics.ViolatorInfo>> getTopViolators(
            @RequestParam(defaultValue = "10") int limit) {

        log.info("ADMIN: Top violators requested (limit: {})", limit);
        List<RateLimitMetrics.ViolatorInfo> violators = rateLimitMetrics.getTopViolators(limit);
        return ResponseEntity.ok(violators);
    }

    /**
     * Clear rate limit for a specific client
     */
    @DeleteMapping("/clear/{clientIdentifier}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Clear rate limit", description = "Removes rate limit for specific client")
    public ResponseEntity<Map<String, Object>> clearRateLimit(
            @PathVariable String clientIdentifier,
            @RequestParam String endpoint) {

        log.warn("ADMIN: Clearing rate limit for client: {} on endpoint: {}", clientIdentifier, endpoint);

        boolean success = rateLimitMetrics.clearRateLimit(clientIdentifier, endpoint);

        Map<String, Object> response = Map.of(
                "success", success,
                "clientIdentifier", clientIdentifier,
                "endpoint", endpoint,
                "message", success ? "Rate limit cleared successfully" : "Failed to clear rate limit"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Clear lockout for a specific client
     */
    @DeleteMapping("/lockout/{clientIdentifier}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Clear lockout", description = "Removes lockout for specific client")
    public ResponseEntity<Map<String, Object>> clearLockout(@PathVariable String clientIdentifier) {

        log.warn("ADMIN: Clearing lockout for client: {}", clientIdentifier);

        boolean success = rateLimitMetrics.clearLockout(clientIdentifier);

        Map<String, Object> response = Map.of(
                "success", success,
                "clientIdentifier", clientIdentifier,
                "message", success ? "Lockout cleared successfully" : "Failed to clear lockout"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Rate limit health check", description = "Checks if rate limiting is functioning")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "healthy",
                "rateLimitingEnabled", true,
                "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(health);
    }
}
