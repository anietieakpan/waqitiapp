package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-grade AML screening result model for comprehensive transaction analysis.
 * Supports high-throughput screening with detailed risk assessment and audit trail.
 * 
 * Compliant with:
 * - FATF recommendations
 * - FinCEN requirements 
 * - AUSTRAC guidelines
 * - EU AML directives
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLScreeningResult {
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "User ID is required") 
    private String userId;
    
    @NotNull(message = "Screening result status is required")
    private boolean passed;
    
    @NotBlank(message = "Risk level is required")
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    @NotNull(message = "Risk score is required")
    private BigDecimal riskScore; // 0-100 scale
    
    @NotNull(message = "Screening timestamp is required")
    private LocalDateTime screenedAt;
    
    @Builder.Default
    private List<String> flags = new ArrayList<>(); // Risk flags identified
    
    @Builder.Default
    private List<WatchlistMatch> watchlistMatches = new ArrayList<>();
    
    @Builder.Default
    private List<AMLAlert> alerts = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Object> details = new HashMap<>(); // Additional screening details
    
    private String screeningEngine; // Which engine performed screening
    private String screeningVersion; // Version of screening rules
    private Long processingTimeMs; // Performance metrics
    private String correlationId; // For tracing across services
    
    // Regulatory compliance fields
    private boolean requiresManualReview;
    private boolean requiresSAR; // Suspicious Activity Report
    private boolean requiresCTR; // Currency Transaction Report
    private String complianceReason;
    private List<String> regulatoryFlags;
    
    // Sanctions and PEP screening
    private SanctionsScreeningResult sanctionsResult;
    private PEPScreeningResult pepResult;
    private AdverseMediaResult adverseMediaResult;
    
    // Transaction pattern analysis
    private VelocityAnalysis velocityAnalysis;
    private PatternAnalysis patternAnalysis;
    private GeographicAnalysis geographicAnalysis;
    
    // Audit and monitoring
    private String auditTrailId;
    private Map<String, String> auditMetadata;
    private LocalDateTime expiresAt; // When this result expires
    
    /**
     * Factory method for passed screening
     */
    public static AMLScreeningResult passed(String transactionId, String userId) {
        return AMLScreeningResult.builder()
                .transactionId(transactionId)
                .userId(userId)
                .passed(true)
                .riskLevel("LOW")
                .riskScore(BigDecimal.ZERO)
                .screenedAt(LocalDateTime.now())
                .flags(new ArrayList<>())
                .details(new HashMap<>())
                .requiresManualReview(false)
                .requiresSAR(false)
                .requiresCTR(false)
                .build();
    }
    
    /**
     * Factory method for failed screening
     */
    public static AMLScreeningResult failed(String transactionId, String userId, String reason) {
        return AMLScreeningResult.builder()
                .transactionId(transactionId)
                .userId(userId)
                .passed(false)
                .riskLevel("HIGH")
                .riskScore(BigDecimal.valueOf(75))
                .screenedAt(LocalDateTime.now())
                .flags(List.of("FAILED_SCREENING"))
                .details(Map.of("failureReason", reason))
                .requiresManualReview(true)
                .complianceReason(reason)
                .build();
    }
    
    /**
     * Add a risk flag
     */
    public void addFlag(String flag) {
        if (flags == null) {
            flags = new ArrayList<>();
        }
        if (!flags.contains(flag)) {
            flags.add(flag);
        }
    }
    
    /**
     * Add multiple risk flags
     */
    public void addFlags(List<String> newFlags) {
        if (newFlags != null) {
            newFlags.forEach(this::addFlag);
        }
    }
    
    /**
     * Add a watchlist match
     */
    public void addWatchlistMatch(WatchlistMatch match) {
        if (watchlistMatches == null) {
            watchlistMatches = new ArrayList<>();
        }
        watchlistMatches.add(match);
    }
    
    /**
     * Add an AML alert
     */
    public void addAlert(AMLAlert alert) {
        if (alerts == null) {
            alerts = new ArrayList<>();
        }
        alerts.add(alert);
    }
    
    /**
     * Add screening detail
     */
    public void addDetail(String key, Object value) {
        if (details == null) {
            details = new HashMap<>();
        }
        details.put(key, value);
    }
    
    /**
     * Check if screening has high risk indicators
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel) ||
               (riskScore != null && riskScore.compareTo(BigDecimal.valueOf(70)) >= 0);
    }
    
    /**
     * Check if any watchlist matches exist
     */
    public boolean hasWatchlistMatches() {
        return watchlistMatches != null && !watchlistMatches.isEmpty();
    }
    
    /**
     * Check if any alerts were generated
     */
    public boolean hasAlerts() {
        return alerts != null && !alerts.isEmpty();
    }
    
    /**
     * Check if any risk flags exist
     */
    public boolean hasFlags() {
        return flags != null && !flags.isEmpty();
    }
    
    /**
     * Get the highest priority flag
     */
    public String getHighestPriorityFlag() {
        if (!hasFlags()) {
            return null;
        }
        
        // Priority order for flags
        String[] priorityOrder = {
            "SANCTIONS_MATCH", "PEP_MATCH", "TERRORIST_WATCH", 
            "HIGH_RISK_COUNTRY", "STRUCTURING", "VELOCITY_EXCEEDED",
            "ROUND_AMOUNT", "OFF_HOURS", "UNUSUAL_PATTERN"
        };
        
        for (String priority : priorityOrder) {
            if (flags.contains(priority)) {
                return priority;
            }
        }
        
        return flags.get(0); // Return first flag if no priority match
    }
    
    /**
     * Calculate overall risk based on multiple factors
     */
    public void calculateRisk() {
        BigDecimal calculatedScore = BigDecimal.ZERO;
        
        // Base risk from flags
        if (hasFlags()) {
            calculatedScore = calculatedScore.add(BigDecimal.valueOf(flags.size() * 10));
        }
        
        // Watchlist matches add significant risk
        if (hasWatchlistMatches()) {
            calculatedScore = calculatedScore.add(BigDecimal.valueOf(30));
        }
        
        // Sanctions matches are critical
        if (sanctionsResult != null && sanctionsResult.isMatch()) {
            calculatedScore = calculatedScore.add(BigDecimal.valueOf(50));
        }
        
        // PEP matches add moderate risk
        if (pepResult != null && pepResult.isMatch()) {
            calculatedScore = calculatedScore.add(BigDecimal.valueOf(20));
        }
        
        // Velocity violations
        if (velocityAnalysis != null && velocityAnalysis.isViolation()) {
            calculatedScore = calculatedScore.add(BigDecimal.valueOf(15));
        }
        
        // Pattern violations
        if (patternAnalysis != null && patternAnalysis.isSuspicious()) {
            calculatedScore = calculatedScore.add(BigDecimal.valueOf(25));
        }
        
        // Geographic risk
        if (geographicAnalysis != null && geographicAnalysis.isHighRisk()) {
            calculatedScore = calculatedScore.add(BigDecimal.valueOf(20));
        }
        
        // Cap at 100
        this.riskScore = calculatedScore.min(BigDecimal.valueOf(100));
        
        // Determine risk level based on score
        if (riskScore.compareTo(BigDecimal.valueOf(80)) >= 0) {
            this.riskLevel = "CRITICAL";
            this.requiresManualReview = true;
            this.requiresSAR = true;
        } else if (riskScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            this.riskLevel = "HIGH";
            this.requiresManualReview = true;
        } else if (riskScore.compareTo(BigDecimal.valueOf(30)) >= 0) {
            this.riskLevel = "MEDIUM";
        } else {
            this.riskLevel = "LOW";
        }
        
        // Update pass/fail status
        this.passed = riskScore.compareTo(BigDecimal.valueOf(70)) < 0;
    }
    
    /**
     * Generate summary for reporting
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("AML Screening Result: ").append(passed ? "PASSED" : "FAILED").append("\n");
        summary.append("Risk Level: ").append(riskLevel).append(" (Score: ").append(riskScore).append(")\n");
        
        if (hasFlags()) {
            summary.append("Risk Flags: ").append(String.join(", ", flags)).append("\n");
        }
        
        if (hasWatchlistMatches()) {
            summary.append("Watchlist Matches: ").append(watchlistMatches.size()).append("\n");
        }
        
        if (requiresManualReview) {
            summary.append("Manual Review Required: YES\n");
        }
        
        if (requiresSAR) {
            summary.append("SAR Filing Required: YES\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Validate the screening result
     */
    public void validate() {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (riskLevel == null || riskLevel.trim().isEmpty()) {
            throw new IllegalArgumentException("Risk level is required");
        }
        
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(riskLevel)) {
            throw new IllegalArgumentException("Invalid risk level: " + riskLevel);
        }
        
        if (riskScore == null) {
            throw new IllegalArgumentException("Risk score is required");
        }
        
        if (riskScore.compareTo(BigDecimal.ZERO) < 0 || riskScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        
        if (screenedAt == null) {
            throw new IllegalArgumentException("Screening timestamp is required");
        }
    }
    
    /**
     * Create a copy for audit purposes (without sensitive data)
     */
    public AMLScreeningResult createAuditCopy() {
        return AMLScreeningResult.builder()
                .transactionId(transactionId)
                .userId("***") // Masked for audit
                .passed(passed)
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .screenedAt(screenedAt)
                .flags(new ArrayList<>(flags))
                .requiresManualReview(requiresManualReview)
                .requiresSAR(requiresSAR)
                .requiresCTR(requiresCTR)
                .auditTrailId(auditTrailId)
                .build();
    }
    
    // Supporting nested classes
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WatchlistMatch {
        private String listName;
        private String matchedName;
        private double matchScore;
        private String reason;
        private Map<String, Object> details;
        
        public boolean isHighConfidence() {
            return matchScore >= 0.8;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AMLAlert {
        private String alertType;
        private String severity;
        private String description;
        private Map<String, Object> details;
        private LocalDateTime createdAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsScreeningResult {
        private boolean match;
        private String listName;
        private String matchedName;
        private double confidence;
        private String reason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PEPScreeningResult {
        private boolean match;
        private String matchedName;
        private String position;
        private String country;
        private double confidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdverseMediaResult {
        private boolean match;
        private String source;
        private String headline;
        private LocalDateTime publicationDate;
        private String riskCategory;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityAnalysis {
        private boolean violation;
        private int transactionCount;
        private BigDecimal totalAmount;
        private String timeframe;
        private String violationType;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternAnalysis {
        private boolean suspicious;
        private String patternType;
        private String description;
        private double confidence;
        private Map<String, Object> evidence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicAnalysis {
        private boolean highRisk;
        private String sourceCountry;
        private String destinationCountry;
        private String riskReason;
        private List<String> riskFactors;
    }
}