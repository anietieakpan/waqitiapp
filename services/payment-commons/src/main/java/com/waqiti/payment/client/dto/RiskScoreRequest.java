package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Risk score calculation request DTO
 * Generic request for calculating risk scores for various entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"sensitiveAttributes"})
public class RiskScoreRequest {
    
    @NotNull
    private UUID requestId;
    
    @NotNull
    private String entityId; // User ID, Merchant ID, Transaction ID, etc.
    
    @NotNull
    private EntityType entityType;
    
    @NotNull
    private RiskScoreType scoreType;
    
    private LocalDateTime requestedAt;
    
    // Context for risk calculation
    private RiskCalculationContext context;
    
    // Historical data to consider
    private HistoricalDataContext historicalData;
    
    // Real-time data for calculation
    private RealtimeDataContext realtimeData;
    
    // External data sources
    private ExternalDataContext externalData;
    
    // Calculation preferences
    private CalculationPreferences preferences;
    
    // Additional attributes for risk calculation
    private Map<String, Object> additionalAttributes;
    
    // Sensitive data (encrypted/masked)
    private Map<String, String> sensitiveAttributes;
    
    public enum EntityType {
        USER,
        MERCHANT,
        TRANSACTION,
        PAYMENT_METHOD,
        DEVICE,
        IP_ADDRESS,
        BUSINESS,
        ACCOUNT
    }
    
    public enum RiskScoreType {
        OVERALL,           // General risk score
        FRAUD,             // Fraud-specific risk
        CREDIT,            // Credit risk
        COMPLIANCE,        // Regulatory compliance risk
        OPERATIONAL,       // Operational risk
        REPUTATION,        // Reputation risk
        VELOCITY,          // Transaction velocity risk
        BEHAVIORAL,        // Behavioral anomaly risk
        GEOGRAPHIC,        // Geographic risk
        DEVICE,            // Device-based risk
        NETWORK            // Network-based risk
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskCalculationContext {
        private String calculationReason;
        private String triggeringEvent;
        private String businessContext;
        private String riskAssessmentType; // ONBOARDING, TRANSACTION, PERIODIC, TRIGGERED
        private LocalDateTime contextTimestamp;
        private Map<String, Object> contextualFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalDataContext {
        private Integer lookbackDays;
        private boolean includeTransactionHistory;
        private boolean includeBehavioralHistory;
        private boolean includeComplianceHistory;
        private boolean includeDeviceHistory;
        private boolean includeLocationHistory;
        private LocalDateTime dataFromDate;
        private LocalDateTime dataToDate;
        private Map<String, Object> historicalFilters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealtimeDataContext {
        private String currentLocation;
        private String currentDevice;
        private String currentIpAddress;
        private String currentSession;
        private LocalDateTime sessionStartTime;
        private String userAgent;
        private Map<String, Object> realtimeAttributes;
        private List<String> realtimeEvents;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalDataContext {
        private boolean includeCreditBureauData;
        private boolean includeIdentityVerification;
        private boolean includeSanctionsCheck;
        private boolean includePepCheck;
        private boolean includeMediaScreening;
        private boolean includeSocialMediaData;
        private boolean includePublicRecords;
        private boolean includeThirdPartyRiskData;
        @Builder.Default
        private List<String> externalDataSources = List.of();
        private Map<String, Object> externalDataPreferences;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalculationPreferences {
        private String calculationMethod; // ML_ENSEMBLE, RULE_BASED, HYBRID
        @Builder.Default
        private List<String> enabledModelTypes = List.of();
        private Map<String, Double> riskFactorWeights;
        private Double confidenceThreshold;
        private boolean enableExplainability;
        private boolean enableAuditTrail;
        private String outputFormat; // DETAILED, SUMMARY, MINIMAL
        private Integer maxProcessingTimeMs;
        private Map<String, Object> customParameters;
    }
    
    // Business logic methods
    public boolean requiresExternalData() {
        return externalData != null && 
               (externalData.includeCreditBureauData() ||
                externalData.includeIdentityVerification() ||
                externalData.includeSanctionsCheck() ||
                externalData.includePepCheck() ||
                externalData.includeMediaScreening());
    }
    
    public boolean isRealTimeRequest() {
        return realtimeData != null && 
               realtimeData.getCurrentSession() != null;
    }
    
    public boolean requiresHistoricalAnalysis() {
        return historicalData != null && 
               (historicalData.includeTransactionHistory() ||
                historicalData.includeBehavioralHistory() ||
                historicalData.includeComplianceHistory());
    }
    
    public boolean isHighPriorityRequest() {
        return context != null && 
               ("TRANSACTION".equals(context.getRiskAssessmentType()) ||
                "TRIGGERED".equals(context.getRiskAssessmentType()));
    }
    
    public boolean requiresMLModels() {
        return preferences != null &&
               ("ML_ENSEMBLE".equals(preferences.getCalculationMethod()) ||
                "HYBRID".equals(preferences.getCalculationMethod()));
    }
    
    public boolean requiresExplainability() {
        return preferences != null && 
               preferences.enableExplainability();
    }
    
    public boolean isComplianceRelated() {
        return scoreType == RiskScoreType.COMPLIANCE ||
               (externalData != null && 
                (externalData.includeSanctionsCheck() ||
                 externalData.includePepCheck()));
    }
    
    public Integer getEffectiveLookbackDays() {
        if (historicalData != null && historicalData.getLookbackDays() != null) {
            return historicalData.getLookbackDays();
        }
        
        // Default lookback based on entity type
        return switch (entityType) {
            case TRANSACTION -> 30;
            case USER -> 90;
            case MERCHANT -> 180;
            case BUSINESS -> 365;
            default -> 60;
        };
    }
    
    public Integer getEffectiveTimeoutMs() {
        if (preferences != null && preferences.getMaxProcessingTimeMs() != null) {
            return preferences.getMaxProcessingTimeMs();
        }
        
        // Default timeout based on priority
        return isHighPriorityRequest() ? 5000 : 30000;
    }
}