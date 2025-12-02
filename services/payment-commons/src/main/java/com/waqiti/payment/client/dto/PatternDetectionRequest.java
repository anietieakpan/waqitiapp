package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pattern Detection Request
 * 
 * Request to analyze data for fraud patterns and suspicious behaviors.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternDetectionRequest {
    
    /**
     * Analysis ID for tracking
     */
    private String analysisId;
    
    /**
     * Pattern detection scope
     */
    private DetectionScope scope;
    
    /**
     * Time range for analysis
     */
    private TimeRange timeRange;
    
    /**
     * Entities to analyze
     */
    private List<AnalysisEntity> entities;
    
    /**
     * Pattern types to detect
     */
    private List<PatternType> patternTypes;
    
    /**
     * Analysis parameters
     */
    private AnalysisParameters parameters;
    
    /**
     * Data sources to include
     */
    private List<String> dataSources;
    
    /**
     * Filters to apply
     */
    private AnalysisFilters filters;
    
    /**
     * Analysis priority
     */
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    /**
     * Whether to run in real-time
     */
    @Builder.Default
    private Boolean realTime = false;
    
    /**
     * Whether to include historical patterns
     */
    @Builder.Default
    private Boolean includeHistorical = true;
    
    /**
     * Confidence threshold for pattern detection
     */
    @Builder.Default
    private Double confidenceThreshold = 0.7;
    
    /**
     * Maximum analysis duration in minutes
     */
    @Builder.Default
    private Integer maxDurationMinutes = 30;
    
    /**
     * Notification settings
     */
    private NotificationConfig notifications;
    
    /**
     * Request metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Request timestamp
     */
    private LocalDateTime requestedAt;
    
    /**
     * User requesting analysis
     */
    private String requestedBy;
    
    /**
     * Source system
     */
    private String requestSource;
    
    public enum DetectionScope {
        SINGLE_USER,
        SINGLE_MERCHANT,
        USER_GROUP,
        MERCHANT_GROUP,
        CROSS_USER,
        CROSS_MERCHANT,
        GLOBAL
    }
    
    public enum PatternType {
        VELOCITY_PATTERNS,
        AMOUNT_PATTERNS,
        TIME_PATTERNS,
        LOCATION_PATTERNS,
        DEVICE_PATTERNS,
        BEHAVIORAL_PATTERNS,
        NETWORK_PATTERNS,
        FREQUENCY_PATTERNS,
        SEQUENCE_PATTERNS,
        ANOMALY_PATTERNS,
        CORRELATION_PATTERNS,
        CLUSTERING_PATTERNS
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeRange {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String timeWindow; // "1h", "1d", "1w", "1m"
        private Boolean rolling;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisEntity {
        private String entityType; // USER, MERCHANT, DEVICE, IP, etc.
        private String entityId;
        private Map<String, Object> entityAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisParameters {
        private Double sensitivityLevel;
        private Integer minPatternSize;
        private Integer maxPatternSize;
        private Double similarityThreshold;
        private Boolean enableMLModels;
        private List<String> mlModelIds;
        private Map<String, Object> customParameters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisFilters {
        private AmountFilter amountFilter;
        private LocationFilter locationFilter;
        private StatusFilter statusFilter;
        private TypeFilter typeFilter;
        private Map<String, Object> customFilters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountFilter {
        private Double minAmount;
        private Double maxAmount;
        private String currency;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationFilter {
        private List<String> countries;
        private List<String> regions;
        private List<String> cities;
        private Boolean excludeVpn;
        private Boolean excludeProxy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusFilter {
        private List<String> transactionStatuses;
        private List<String> userStatuses;
        private List<String> riskLevels;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeFilter {
        private List<String> transactionTypes;
        private List<String> paymentMethods;
        private List<String> deviceTypes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationConfig {
        private Boolean notifyOnCompletion;
        private Boolean notifyOnPatternFound;
        private List<String> emailRecipients;
        private String webhookUrl;
        private String slackChannel;
    }
    
    /**
     * Check if this is a high priority request
     */
    public boolean isHighPriority() {
        return priority == Priority.HIGH || priority == Priority.CRITICAL;
    }
    
    /**
     * Check if real-time analysis is requested
     */
    public boolean isRealTimeAnalysis() {
        return realTime != null && realTime;
    }
    
    /**
     * Get effective time range in hours
     */
    public Long getTimeRangeHours() {
        if (timeRange == null) {
            return 24L; // Default 24 hours
        }
        
        if (timeRange.getStartTime() != null && timeRange.getEndTime() != null) {
            return java.time.Duration.between(timeRange.getStartTime(), timeRange.getEndTime()).toHours();
        }
        
        if (timeRange.getTimeWindow() != null) {
            String window = timeRange.getTimeWindow().toLowerCase();
            if (window.endsWith("h")) {
                return Long.parseLong(window.substring(0, window.length() - 1));
            } else if (window.endsWith("d")) {
                return Long.parseLong(window.substring(0, window.length() - 1)) * 24;
            } else if (window.endsWith("w")) {
                return Long.parseLong(window.substring(0, window.length() - 1)) * 24 * 7;
            }
        }
        
        return 24L;
    }
    
    /**
     * Validate required fields
     */
    public boolean isValid() {
        return scope != null &&
               patternTypes != null && !patternTypes.isEmpty() &&
               (entities != null && !entities.isEmpty() || scope == DetectionScope.GLOBAL);
    }
    
    /**
     * Get analysis complexity score
     */
    public int getComplexityScore() {
        int score = 0;
        
        // Scope complexity
        switch (scope) {
            case SINGLE_USER:
            case SINGLE_MERCHANT:
                score += 1;
                break;
            case USER_GROUP:
            case MERCHANT_GROUP:
                score += 3;
                break;
            case CROSS_USER:
            case CROSS_MERCHANT:
                score += 5;
                break;
            case GLOBAL:
                score += 10;
                break;
        }
        
        // Pattern types complexity
        score += patternTypes.size();
        
        // Time range complexity
        Long hours = getTimeRangeHours();
        if (hours > 168) { // More than a week
            score += 3;
        } else if (hours > 24) { // More than a day
            score += 2;
        } else {
            score += 1;
        }
        
        // Entity count complexity
        if (entities != null) {
            if (entities.size() > 100) {
                score += 5;
            } else if (entities.size() > 10) {
                score += 3;
            } else {
                score += 1;
            }
        }
        
        return score;
    }
}