package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Suspicious Pattern
 * 
 * Represents a suspicious pattern detected in user or transaction behavior.
 * 
 * @author Waqiti Fraud Detection Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousPattern {
    
    /**
     * Pattern ID
     */
    private String patternId;
    
    /**
     * Pattern type
     */
    private PatternType patternType;
    
    /**
     * Pattern name/title
     */
    private String patternName;
    
    /**
     * Detailed description
     */
    private String description;
    
    /**
     * Severity level
     */
    private SeverityLevel severity;
    
    /**
     * Confidence score (0.0 to 1.0)
     */
    private Double confidence;
    
    /**
     * Risk score (0.0 to 1.0)
     */
    private Double riskScore;
    
    /**
     * Affected entities
     */
    private List<AffectedEntity> affectedEntities;
    
    /**
     * Pattern characteristics
     */
    private PatternCharacteristics characteristics;
    
    /**
     * Supporting evidence
     */
    private List<Evidence> evidence;
    
    /**
     * Pattern statistics
     */
    private PatternStatistics statistics;
    
    /**
     * Detection method
     */
    private DetectionMethod detectionMethod;
    
    /**
     * First occurrence
     */
    private LocalDateTime firstSeen;
    
    /**
     * Last occurrence
     */
    private LocalDateTime lastSeen;
    
    /**
     * Pattern status
     */
    private PatternStatus status;
    
    /**
     * Investigation status
     */
    private InvestigationStatus investigationStatus;
    
    /**
     * Assigned investigator
     */
    private String assignedTo;
    
    /**
     * Resolution details
     */
    private Resolution resolution;
    
    /**
     * Tags for categorization
     */
    private List<String> tags;
    
    /**
     * Related patterns
     */
    private List<String> relatedPatternIds;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;
    
    public enum PatternType {
        VELOCITY_ABUSE,
        AMOUNT_ANOMALY,
        TIME_ANOMALY,
        LOCATION_ANOMALY,
        DEVICE_ANOMALY,
        BEHAVIORAL_CHANGE,
        NETWORK_PATTERN,
        FREQUENCY_PATTERN,
        SEQUENCE_PATTERN,
        CORRELATION_PATTERN,
        CLUSTERING_PATTERN,
        OUTLIER_PATTERN
    }
    
    public enum SeverityLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum PatternStatus {
        ACTIVE,
        RESOLVED,
        FALSE_POSITIVE,
        MONITORING,
        ESCALATED,
        ARCHIVED
    }
    
    public enum InvestigationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        ON_HOLD,
        CANCELLED
    }
    
    public enum DetectionMethod {
        RULE_BASED,
        MACHINE_LEARNING,
        STATISTICAL_ANALYSIS,
        HYBRID,
        MANUAL_REVIEW,
        EXTERNAL_INTEL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AffectedEntity {
        private String entityType; // USER, MERCHANT, DEVICE, IP, etc.
        private String entityId;
        private String entityName;
        private Double impactScore;
        private Map<String, Object> entityDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternCharacteristics {
        private String frequency; // RARE, OCCASIONAL, FREQUENT, CONSTANT
        private String distribution; // CLUSTERED, SCATTERED, REGULAR
        private String trend; // INCREASING, DECREASING, STABLE, VOLATILE
        private Double persistence; // How long the pattern persists
        private Map<String, Object> additionalAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String evidenceType;
        private String description;
        private Double strength; // Evidence strength score
        private Map<String, Object> evidenceData;
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternStatistics {
        private Integer occurrenceCount;
        private Integer affectedEntityCount;
        private LocalDateTime timeSpanStart;
        private LocalDateTime timeSpanEnd;
        private Double avgFrequency;
        private Map<String, Integer> breakdownStats;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Resolution {
        private String resolutionType;
        private String description;
        private LocalDateTime resolvedAt;
        private String resolvedBy;
        private Boolean fraudConfirmed;
        private String actionTaken;
        private Map<String, Object> resolutionData;
    }
    
    /**
     * Check if pattern is critical
     */
    public boolean isCritical() {
        return severity == SeverityLevel.CRITICAL;
    }
    
    /**
     * Check if pattern is high confidence
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }
    
    /**
     * Check if pattern is active
     */
    public boolean isActive() {
        return status == PatternStatus.ACTIVE;
    }
    
    /**
     * Check if pattern needs investigation
     */
    public boolean needsInvestigation() {
        return (severity == SeverityLevel.HIGH || severity == SeverityLevel.CRITICAL) &&
               (investigationStatus == InvestigationStatus.PENDING || 
                investigationStatus == null);
    }
    
    /**
     * Check if pattern is recent (last 24 hours)
     */
    public boolean isRecent() {
        return lastSeen != null && 
               lastSeen.isAfter(LocalDateTime.now().minusHours(24));
    }
    
    /**
     * Get pattern duration in hours
     */
    public Long getPatternDurationHours() {
        if (firstSeen == null || lastSeen == null) {
            return null;
        }
        return java.time.Duration.between(firstSeen, lastSeen).toHours();
    }
    
    /**
     * Get overall threat level
     */
    public String getThreatLevel() {
        if (!isHighConfidence()) {
            return "LOW";
        }
        
        switch (severity) {
            case CRITICAL:
                return "CRITICAL";
            case HIGH:
                return "HIGH";
            case MEDIUM:
                return "MEDIUM";
            default:
                return "LOW";
        }
    }
    
    /**
     * Get pattern summary
     */
    public String getPatternSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(patternName != null ? patternName : patternType.name());
        summary.append(" (").append(severity).append(")");
        
        if (statistics != null && statistics.getOccurrenceCount() != null) {
            summary.append(" - ").append(statistics.getOccurrenceCount()).append(" occurrences");
        }
        
        if (statistics != null && statistics.getAffectedEntityCount() != null) {
            summary.append(", ").append(statistics.getAffectedEntityCount()).append(" entities affected");
        }
        
        return summary.toString();
    }
    
    /**
     * Check if pattern affects multiple entities
     */
    public boolean affectsMultipleEntities() {
        return affectedEntities != null && affectedEntities.size() > 1;
    }
    
    /**
     * Get primary affected entity
     */
    public AffectedEntity getPrimaryAffectedEntity() {
        if (affectedEntities == null || affectedEntities.isEmpty()) {
            return null;
        }
        
        return affectedEntities.stream()
                .max((e1, e2) -> Double.compare(
                    e1.getImpactScore() != null ? e1.getImpactScore() : 0.0,
                    e2.getImpactScore() != null ? e2.getImpactScore() : 0.0
                ))
                .orElse(affectedEntities.get(0));
    }
}