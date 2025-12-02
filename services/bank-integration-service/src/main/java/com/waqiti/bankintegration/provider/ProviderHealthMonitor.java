package com.waqiti.bankintegration.provider;

import com.waqiti.bankintegration.domain.ProviderHealthStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors health status of payment providers.
 * 
 * Provides real-time health monitoring, circuit breaker functionality,
 * and automatic recovery detection for payment providers.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Component
@Slf4j
public class ProviderHealthMonitor {
    
    private final ConcurrentHashMap<String, ProviderHealthInfo> healthCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 30;
    private static final long HEALTH_CACHE_TTL_SECONDS = 60;
    
    /**
     * Check if provider is healthy
     */
    public boolean isProviderHealthy(PaymentProvider provider) {
        ProviderHealthInfo healthInfo = healthCache.get(provider.getName());
        
        // If no cached info or expired, check now
        if (healthInfo == null || isHealthInfoExpired(healthInfo)) {
            checkProviderHealth(provider);
            healthInfo = healthCache.get(provider.getName());
        }
        
        return healthInfo != null && healthInfo.isHealthy();
    }
    
    /**
     * Check provider health and update cache
     */
    public void checkProviderHealth(PaymentProvider provider) {
        try {
            ProviderHealthStatus status = provider.getHealthStatus();
            
            ProviderHealthInfo healthInfo = ProviderHealthInfo.builder()
                .providerName(provider.getName())
                .isHealthy(status.isHealthy())
                .responseTime(status.getResponseTimeMs())
                .errorRate(status.getErrorRate())
                .lastCheckTime(LocalDateTime.now())
                .lastError(status.getLastError())
                .consecutiveFailures(status.isHealthy() ? 0 : 
                    getCurrentConsecutiveFailures(provider.getName()) + 1)
                .build();
            
            healthCache.put(provider.getName(), healthInfo);
            
            log.debug("Health check completed for provider {}: healthy={}, responseTime={}ms", 
                    provider.getName(), status.isHealthy(), status.getResponseTimeMs());
                    
        } catch (Exception e) {
            log.error("Failed to check health for provider: {}", provider.getName(), e);
            
            ProviderHealthInfo unhealthyInfo = ProviderHealthInfo.builder()
                .providerName(provider.getName())
                .isHealthy(false)
                .responseTime(-1L)
                .errorRate(1.0)
                .lastCheckTime(LocalDateTime.now())
                .lastError(e.getMessage())
                .consecutiveFailures(getCurrentConsecutiveFailures(provider.getName()) + 1)
                .build();
            
            healthCache.put(provider.getName(), unhealthyInfo);
        }
    }
    
    /**
     * Start periodic health monitoring
     */
    public void startPeriodicHealthChecks(PaymentProvider provider) {
        scheduler.scheduleAtFixedRate(
            () -> checkProviderHealth(provider),
            0,
            HEALTH_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        log.info("Started periodic health checks for provider: {}", provider.getName());
    }
    
    /**
     * Stop health monitoring for provider
     */
    public void stopHealthChecks(String providerName) {
        healthCache.remove(providerName);
        log.info("Stopped health checks for provider: {}", providerName);
    }
    
    /**
     * Get current health info
     */
    public ProviderHealthInfo getHealthInfo(String providerName) {
        return healthCache.get(providerName);
    }
    
    /**
     * Mark provider as unhealthy
     */
    public void markProviderUnhealthy(String providerName, String reason) {
        ProviderHealthInfo currentInfo = healthCache.get(providerName);
        
        ProviderHealthInfo unhealthyInfo = ProviderHealthInfo.builder()
            .providerName(providerName)
            .isHealthy(false)
            .responseTime(currentInfo != null ? currentInfo.getResponseTime() : -1L)
            .errorRate(1.0)
            .lastCheckTime(LocalDateTime.now())
            .lastError(reason)
            .consecutiveFailures(getCurrentConsecutiveFailures(providerName) + 1)
            .build();
        
        healthCache.put(providerName, unhealthyInfo);
        
        log.warn("Marked provider {} as unhealthy: {}", providerName, reason);
    }
    
    /**
     * Force refresh health status
     */
    public void forceHealthCheck(PaymentProvider provider) {
        checkProviderHealth(provider);
    }
    
    /**
     * Get all provider health statuses
     */
    public ConcurrentHashMap<String, ProviderHealthInfo> getAllHealthStatuses() {
        return new ConcurrentHashMap<>(healthCache);
    }
    
    /**
     * Clear health cache
     */
    public void clearHealthCache() {
        healthCache.clear();
        log.info("Health cache cleared");
    }
    
    /**
     * Shutdown health monitor
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        healthCache.clear();
        log.info("Provider health monitor shutdown completed");
    }
    
    private boolean isHealthInfoExpired(ProviderHealthInfo healthInfo) {
        return LocalDateTime.now().isAfter(
            healthInfo.getLastCheckTime().plusSeconds(HEALTH_CACHE_TTL_SECONDS)
        );
    }
    
    private int getCurrentConsecutiveFailures(String providerName) {
        ProviderHealthInfo healthInfo = healthCache.get(providerName);
        return healthInfo != null ? healthInfo.getConsecutiveFailures() : 0;
    }
    
    /**
     * Inner class to hold provider health information
     */
    public static class ProviderHealthInfo {
        private final String providerName;
        private final boolean healthy;
        private final long responseTime;
        private final double errorRate;
        private final LocalDateTime lastCheckTime;
        private final String lastError;
        private final int consecutiveFailures;
        
        private ProviderHealthInfo(Builder builder) {
            this.providerName = builder.providerName;
            this.healthy = builder.healthy;
            this.responseTime = builder.responseTime;
            this.errorRate = builder.errorRate;
            this.lastCheckTime = builder.lastCheckTime;
            this.lastError = builder.lastError;
            this.consecutiveFailures = builder.consecutiveFailures;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getProviderName() { return providerName; }
        public boolean isHealthy() { return healthy; }
        public long getResponseTime() { return responseTime; }
        public double getErrorRate() { return errorRate; }
        public LocalDateTime getLastCheckTime() { return lastCheckTime; }
        public String getLastError() { return lastError; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        
        public static class Builder {
            private String providerName;
            private boolean healthy;
            private long responseTime;
            private double errorRate;
            private LocalDateTime lastCheckTime;
            private String lastError;
            private int consecutiveFailures;
            
            public Builder providerName(String providerName) {
                this.providerName = providerName;
                return this;
            }
            
            public Builder isHealthy(boolean healthy) {
                this.healthy = healthy;
                return this;
            }
            
            public Builder responseTime(long responseTime) {
                this.responseTime = responseTime;
                return this;
            }
            
            public Builder errorRate(double errorRate) {
                this.errorRate = errorRate;
                return this;
            }
            
            public Builder lastCheckTime(LocalDateTime lastCheckTime) {
                this.lastCheckTime = lastCheckTime;
                return this;
            }
            
            public Builder lastError(String lastError) {
                this.lastError = lastError;
                return this;
            }
            
            public Builder consecutiveFailures(int consecutiveFailures) {
                this.consecutiveFailures = consecutiveFailures;
                return this;
            }
            
            public ProviderHealthInfo build() {
                return new ProviderHealthInfo(this);
            }
        }
    }
}