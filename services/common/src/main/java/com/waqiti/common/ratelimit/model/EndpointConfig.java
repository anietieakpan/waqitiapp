package com.waqiti.common.ratelimit.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Endpoint-specific rate limiting configuration model.
 * Supports multiple rate limiting strategies and detailed configuration options.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointConfig {
    
    /**
     * Unique identifier for this endpoint configuration.
     */
    @NotBlank(message = "Endpoint ID cannot be blank")
    private String endpointId;
    
    /**
     * Human-readable description of this endpoint.
     */
    private String description;
    
    /**
     * Maximum capacity of the token bucket.
     */
    @Min(value = 1, message = "Capacity must be at least 1")
    @Builder.Default
    private long capacity = 100;
    
    /**
     * Number of tokens to refill in each refill period.
     */
    @Min(value = 1, message = "Refill tokens must be at least 1")
    @Builder.Default
    private long refillTokens = 10;
    
    /**
     * Refill period in seconds.
     */
    @Min(value = 1, message = "Refill period must be at least 1 second")
    @Builder.Default
    private long refillPeriodSeconds = 60;
    
    /**
     * Maximum requests allowed per minute.
     */
    @Min(value = 1, message = "Max requests per minute must be at least 1")
    @Builder.Default
    private int maxRequestsPerMinute = 60;
    
    /**
     * Maximum requests allowed per hour.
     */
    @Min(value = 1, message = "Max requests per hour must be at least 1")
    @Builder.Default
    private int maxRequestsPerHour = 1000;
    
    /**
     * Maximum requests allowed per day.
     */
    @Min(value = 1, message = "Max requests per day must be at least 1")
    @Builder.Default
    private int maxRequestsPerDay = 10000;
    
    /**
     * Whether to enable burst mode for temporary traffic spikes.
     */
    @Builder.Default
    private boolean enableBurstMode = true;
    
    /**
     * Multiplier for burst capacity (e.g., 1.5 means 50% more capacity during bursts).
     */
    @DecimalMin(value = "1.0", message = "Burst multiplier must be at least 1.0")
    @DecimalMax(value = "10.0", message = "Burst multiplier cannot exceed 10.0")
    @Builder.Default
    private double burstMultiplier = 1.5;
    
    /**
     * Rate limiting strategy to use.
     * Options: TOKEN_BUCKET, SLIDING_WINDOW, FIXED_WINDOW, ADAPTIVE
     */
    @NotNull(message = "Strategy cannot be null")
    @Builder.Default
    private RateLimitStrategy strategy = RateLimitStrategy.TOKEN_BUCKET;
    
    /**
     * Legacy algorithm field for backward compatibility.
     */
    @Deprecated
    @Builder.Default
    private String algorithm = "SLIDING_WINDOW";
    
    /**
     * Window size in milliseconds for window-based algorithms.
     */
    @Min(value = 1000, message = "Window size must be at least 1000ms")
    @Builder.Default
    private long windowSizeMs = 60000;
    
    /**
     * Whether to enable per-user rate limiting.
     */
    @Builder.Default
    private boolean enableUserRateLimit = true;
    
    /**
     * Whether to enable per-IP rate limiting.
     */
    @Builder.Default
    private boolean enableIpRateLimit = true;
    
    /**
     * Whether to enable global rate limiting for this endpoint.
     */
    @Builder.Default
    private boolean enableGlobalRateLimit = false;
    
    /**
     * Priority level for this endpoint (higher numbers = higher priority).
     */
    @Builder.Default
    private int priority = 0;
    
    /**
     * Weight for weighted rate limiting algorithms.
     */
    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1")
    @DecimalMax(value = "10.0", message = "Weight cannot exceed 10.0")
    @Builder.Default
    private double weight = 1.0;
    
    /**
     * Whether to fail open (allow requests) when rate limiter fails.
     */
    @Builder.Default
    private boolean failOpen = true;
    
    /**
     * Action to take when rate limiter fails.
     */
    @NotNull(message = "Fallback action cannot be null")
    @Builder.Default
    private FallbackAction fallbackAction = FallbackAction.ALLOW;
    
    /**
     * Whether this configuration is currently active.
     */
    @Builder.Default
    private boolean active = true;
    
    /**
     * Custom tags for this endpoint configuration.
     */
    @Builder.Default
    private java.util.Map<String, String> tags = new java.util.HashMap<>();
    
    /**
     * Rate limiting strategies supported by the system.
     */
    public enum RateLimitStrategy {
        /**
         * Token bucket algorithm - smooth rate limiting with burst capability.
         */
        TOKEN_BUCKET,
        
        /**
         * Sliding window algorithm - precise rate limiting over time windows.
         */
        SLIDING_WINDOW,
        
        /**
         * Fixed window algorithm - simple rate limiting with fixed time periods.
         */
        FIXED_WINDOW,
        
        /**
         * Adaptive algorithm - adjusts limits based on system load and response times.
         */
        ADAPTIVE,
        
        /**
         * Weighted algorithm - applies different limits based on user/request weights.
         */
        WEIGHTED
    }
    
    /**
     * Fallback actions when rate limiting fails.
     */
    public enum FallbackAction {
        /**
         * Allow the request to proceed.
         */
        ALLOW,
        
        /**
         * Deny the request immediately.
         */
        DENY,
        
        /**
         * Apply throttling with reduced limits.
         */
        THROTTLE,
        
        /**
         * Queue the request for later processing.
         */
        QUEUE
    }
    
    /**
     * Validates the configuration for consistency and logical constraints.
     *
     * @return true if configuration is valid
     * @throws IllegalArgumentException if configuration is invalid
     */
    public boolean validateConfiguration() {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        
        if (refillTokens > capacity) {
            throw new IllegalArgumentException("Refill tokens cannot exceed capacity");
        }
        
        if (maxRequestsPerMinute > maxRequestsPerHour) {
            throw new IllegalArgumentException("Per-minute limit cannot exceed per-hour limit");
        }
        
        if (maxRequestsPerHour > maxRequestsPerDay) {
            throw new IllegalArgumentException("Per-hour limit cannot exceed per-day limit");
        }
        
        if (burstMultiplier < 1.0) {
            throw new IllegalArgumentException("Burst multiplier must be at least 1.0");
        }
        
        return true;
    }
    
    /**
     * Creates a copy of this configuration with specified modifications.
     *
     * @return a new EndpointConfig instance
     */
    public EndpointConfig copy() {
        return EndpointConfig.builder()
                .endpointId(this.endpointId)
                .description(this.description)
                .capacity(this.capacity)
                .refillTokens(this.refillTokens)
                .refillPeriodSeconds(this.refillPeriodSeconds)
                .maxRequestsPerMinute(this.maxRequestsPerMinute)
                .maxRequestsPerHour(this.maxRequestsPerHour)
                .maxRequestsPerDay(this.maxRequestsPerDay)
                .enableBurstMode(this.enableBurstMode)
                .burstMultiplier(this.burstMultiplier)
                .strategy(this.strategy)
                .algorithm(this.algorithm)
                .windowSizeMs(this.windowSizeMs)
                .enableUserRateLimit(this.enableUserRateLimit)
                .enableIpRateLimit(this.enableIpRateLimit)
                .enableGlobalRateLimit(this.enableGlobalRateLimit)
                .priority(this.priority)
                .weight(this.weight)
                .failOpen(this.failOpen)
                .fallbackAction(this.fallbackAction)
                .active(this.active)
                .tags(new java.util.HashMap<>(this.tags))
                .build();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointConfig that = (EndpointConfig) o;
        return capacity == that.capacity &&
               refillTokens == that.refillTokens &&
               refillPeriodSeconds == that.refillPeriodSeconds &&
               maxRequestsPerMinute == that.maxRequestsPerMinute &&
               maxRequestsPerHour == that.maxRequestsPerHour &&
               maxRequestsPerDay == that.maxRequestsPerDay &&
               enableBurstMode == that.enableBurstMode &&
               Double.compare(that.burstMultiplier, burstMultiplier) == 0 &&
               windowSizeMs == that.windowSizeMs &&
               enableUserRateLimit == that.enableUserRateLimit &&
               enableIpRateLimit == that.enableIpRateLimit &&
               enableGlobalRateLimit == that.enableGlobalRateLimit &&
               priority == that.priority &&
               Double.compare(that.weight, weight) == 0 &&
               failOpen == that.failOpen &&
               active == that.active &&
               Objects.equals(endpointId, that.endpointId) &&
               Objects.equals(description, that.description) &&
               strategy == that.strategy &&
               Objects.equals(algorithm, that.algorithm) &&
               fallbackAction == that.fallbackAction &&
               Objects.equals(tags, that.tags);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(endpointId, description, capacity, refillTokens, refillPeriodSeconds,
                          maxRequestsPerMinute, maxRequestsPerHour, maxRequestsPerDay, enableBurstMode,
                          burstMultiplier, strategy, algorithm, windowSizeMs, enableUserRateLimit, 
                          enableIpRateLimit, enableGlobalRateLimit, priority, weight, failOpen, 
                          fallbackAction, active, tags);
    }
    
    @Override
    public String toString() {
        return "EndpointConfig{" +
               "endpointId='" + endpointId + '\'' +
               ", description='" + description + '\'' +
               ", capacity=" + capacity +
               ", refillTokens=" + refillTokens +
               ", refillPeriodSeconds=" + refillPeriodSeconds +
               ", strategy=" + strategy +
               ", priority=" + priority +
               ", weight=" + weight +
               ", active=" + active +
               '}';
    }
}