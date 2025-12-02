package com.waqiti.notification.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache wrapper for email templates with usage tracking
 */
@Data
@Slf4j
public class TemplateCache {
    
    private final EmailTemplate template;
    private final AtomicLong usageCount = new AtomicLong(0);
    private final LocalDateTime cachedAt;
    private LocalDateTime lastUsed;
    private LocalDateTime lastUpdated;
    private boolean hot = false; // Indicates frequently used template
    
    public TemplateCache(EmailTemplate template) {
        this.template = template;
        this.cachedAt = LocalDateTime.now();
        this.lastUsed = LocalDateTime.now();
        this.lastUpdated = template.getUpdatedAt();
    }
    
    public void recordUsage() {
        long count = usageCount.incrementAndGet();
        lastUsed = LocalDateTime.now();
        
        // Mark as hot if used more than 100 times
        if (count > 100) {
            hot = true;
        }
        
        log.debug("Template {} used. Total usage: {}", template.getName(), count);
    }
    
    public boolean isStale() {
        // Template is stale if it hasn't been used in the last hour
        // or if the template was updated after caching
        return lastUsed.isBefore(LocalDateTime.now().minusHours(1)) ||
               (template.getUpdatedAt() != null && 
                template.getUpdatedAt().isAfter(cachedAt));
    }
    
    public boolean isExpired() {
        // Cache expires after 24 hours for non-hot templates
        // Hot templates expire after 7 days
        if (hot) {
            return cachedAt.isBefore(LocalDateTime.now().minusDays(7));
        } else {
            return cachedAt.isBefore(LocalDateTime.now().minusHours(24));
        }
    }
    
    public long getUsageCount() {
        return usageCount.get();
    }
    
    public double getUsageRate() {
        long hoursActive = java.time.Duration.between(cachedAt, LocalDateTime.now()).toHours();
        if (hoursActive == 0) return usageCount.get();
        return (double) usageCount.get() / hoursActive;
    }
    
    public String getCacheStatus() {
        if (isExpired()) return "EXPIRED";
        if (isStale()) return "STALE";
        if (hot) return "HOT";
        return "ACTIVE";
    }
    
    public CacheMetrics getMetrics() {
        return CacheMetrics.builder()
            .templateName(template.getName())
            .templateId(template.getId())
            .usageCount(usageCount.get())
            .usageRate(getUsageRate())
            .cachedAt(cachedAt)
            .lastUsed(lastUsed)
            .hot(hot)
            .stale(isStale())
            .expired(isExpired())
            .status(getCacheStatus())
            .build();
    }
    
    @lombok.Builder
    @lombok.Data
    public static class CacheMetrics {
        private String templateName;
        private String templateId;
        private long usageCount;
        private double usageRate;
        private LocalDateTime cachedAt;
        private LocalDateTime lastUsed;
        private boolean hot;
        private boolean stale;
        private boolean expired;
        private String status;
    }
}