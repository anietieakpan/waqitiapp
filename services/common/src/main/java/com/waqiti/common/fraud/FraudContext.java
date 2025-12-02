package com.waqiti.common.fraud;

import com.waqiti.common.fraud.ml.MLPredictionResult;
import com.waqiti.common.fraud.rules.RuleEvaluationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.annotation.concurrent.NotThreadSafe;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.waqiti.common.fraud.model.FraudScore;

/**
 * Comprehensive fraud analysis context containing all relevant information
 * for fraud detection processing, including transaction data, user profile,
 * ML predictions, and rule evaluation results.
 *
 * <h2>Thread Safety</h2>
 * <p><strong>NOT THREAD-SAFE</strong>. This class is mutable and not designed
 * for concurrent access. Each transaction should create its own {@code FraudContext}
 * instance. Do not share instances across threads without external synchronization.
 * </p>
 *
 * <h2>Usage Guidelines</h2>
 * <ul>
 *   <li><strong>Per-transaction</strong>: Create new instance for each transaction</li>
 *   <li><strong>Single-threaded</strong>: Use in single thread only</li>
 *   <li><strong>Async processing</strong>: Create defensive copy before passing to async methods</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@NotThreadSafe
public class FraudContext {
    
    /**
     * Unique context identifier for tracking
     */
    private String contextId;

    /**
     * Transaction ID (convenience field for direct access)
     */
    private String transactionId;

    /**
     * User ID (convenience field for direct access)
     */
    private String userId;

    /**
     * Transaction being analyzed
     */
    private TransactionInfo transaction;
    
    /**
     * User information and profile
     */
    private UserProfile userProfile;
    
    /**
     * Merchant information
     */
    private MerchantProfile merchantProfile;
    
    /**
     * Device and session information
     */
    private DeviceSessionInfo deviceSession;

    /**
     * Device information map (for compatibility)
     */
    private Map<String, Object> deviceInfo;

    /**
     * Geographic and location data
     */
    private LocationInfo locationInfo;
    
    /**
     * Machine learning prediction results
     */
    private MLPredictionResult mlPrediction;
    
    /**
     * Fraud rule evaluation results
     */
    private List<RuleEvaluationResult> ruleResults;
    
    /**
     * Overall fraud score calculation
     */
    private FraudScore fraudScore;
    
    /**
     * Risk assessment details
     */
    private RiskAssessment riskAssessment;
    
    /**
     * Historical context and patterns
     */
    private HistoricalContext historicalContext;
    
    /**
     * Behavioral analysis results
     */
    private BehavioralAnalysis behavioralAnalysis;
    
    /**
     * External data sources and enrichment
     */
    private ExternalDataContext externalData;
    
    /**
     * Processing metadata
     */
    private ProcessingMetadata processingMetadata;
    
    /**
     * Compliance and regulatory context
     */
    private ComplianceContext complianceContext;
    
    /**
     * Real-time alerts generated
     */
    private List<FraudAlert> alerts;
    
    /**
     * Recommended actions
     */
    private List<String> recommendedActions;
    
    /**
     * Context creation timestamp
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Context expiration timestamp
     */
    private LocalDateTime expiresAt;

    /**
     * Last enrichment timestamp
     */
    private LocalDateTime lastEnriched;

    /**
     * Rule violations detected
     */
    private List<Object> ruleViolations;

    /**
     * Fraud alerts generated
     */
    private List<Object> fraudAlerts;

    /**
     * Archive timestamp
     */
    private LocalDateTime archivedAt;

    /**
     * Merchant ID
     */
    private String merchantId;

    /**
     * Account age in days
     */
    private Integer accountAge;

    /**
     * Transaction amount (convenience field)
     */
    private BigDecimal amount;

    /**
     * Transaction currency (convenience field)
     */
    private String currency;

    /**
     * Transaction timestamp (convenience field)
     */
    private LocalDateTime timestamp;

    /**
     * Transaction data map (for additional transaction details)
     */
    private Map<String, Object> transactionData;

    /**
     * Historical data map (for user/transaction history)
     */
    private Map<String, Object> historicalData;

    /**
     * Metadata map (for processing metadata)
     */
    private Map<String, Object> metadata;

    /**
     * Check if ML prediction indicates fraud
     */
    public boolean isMlFraudPredicted() {
        return mlPrediction != null && mlPrediction.isFraud();
    }
    
    /**
     * Check if any rules were triggered
     */
    public boolean areRulesTriggered() {
        return ruleResults != null && ruleResults.stream().anyMatch(RuleEvaluationResult::isTriggered);
    }
    
    /**
     * Get highest triggered rule severity
     */
    public com.waqiti.common.fraud.rules.FraudRule.RuleSeverity getHighestRuleSeverity() {
        if (ruleResults == null) {
            return null;
        }
        
        return ruleResults.stream()
                .filter(RuleEvaluationResult::isTriggered)
                .map(RuleEvaluationResult::getSeverity)
                .max(Enum::compareTo)
                .orElse(null);
    }
    
    /**
     * Get overall fraud probability
     */
    public double getOverallFraudProbability() {
        if (fraudScore != null) {
            return fraudScore.getOverallScore();
        }
        if (mlPrediction != null) {
            return mlPrediction.getFraudProbability();
        }
        return 0.0;
    }
    
    /**
     * Get overall confidence level
     */
    public double getOverallConfidence() {
        if (fraudScore != null) {
            return fraudScore.getConfidence();
        }
        if (mlPrediction != null) {
            return mlPrediction.getConfidence();
        }
        return 0.0;
    }
    
    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlockTransaction() {
        double probability = getOverallFraudProbability();
        double confidence = getOverallConfidence();
        
        return (probability >= 0.8 && confidence >= 0.7) ||
               (probability >= 0.9) ||
               getHighestRuleSeverity() == com.waqiti.common.fraud.rules.FraudRule.RuleSeverity.CRITICAL;
    }
    
    /**
     * Check if additional authentication is required
     */
    public boolean requiresAdditionalAuth() {
        double probability = getOverallFraudProbability();
        return probability >= 0.5 && probability < 0.8;
    }
    
    /**
     * Get risk level classification
     */
    public RiskLevel getRiskLevel() {
        double probability = getOverallFraudProbability();
        
        if (probability >= 0.8) {
            return RiskLevel.CRITICAL;
        } else if (probability >= 0.6) {
            return RiskLevel.HIGH;
        } else if (probability >= 0.4) {
            return RiskLevel.MEDIUM;
        } else if (probability >= 0.2) {
            return RiskLevel.LOW;
        } else {
            return RiskLevel.MINIMAL;
        }
    }
    
    /**
     * Get top risk factors across all analysis
     */
    public List<String> getTopRiskFactors() {
        List<String> riskFactors = new java.util.ArrayList<>();
        
        // Add ML risk factors
        if (mlPrediction != null && mlPrediction.getRiskFactors() != null) {
            riskFactors.addAll(mlPrediction.getRiskFactors());
        }
        
        // Add triggered rule names
        if (ruleResults != null) {
            ruleResults.stream()
                    .filter(RuleEvaluationResult::isTriggered)
                    .map(RuleEvaluationResult::getRuleName)
                    .forEach(riskFactors::add);
        }
        
        // Add behavioral risk factors
        if (behavioralAnalysis != null && behavioralAnalysis.getAnomalies() != null) {
            riskFactors.addAll(behavioralAnalysis.getAnomalies());
        }
        
        return riskFactors.stream().distinct().limit(10).collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Create fraud analysis summary
     */
    public String createAnalysisSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("=== FRAUD ANALYSIS SUMMARY ===\n");
        summary.append(String.format("Context ID: %s\n", contextId));
        summary.append(String.format("Transaction: %s\n", transaction != null ? transaction.getTransactionId() : "N/A"));
        summary.append(String.format("Fraud Probability: %.3f (%.1f%%)\n", getOverallFraudProbability(), getOverallFraudProbability() * 100));
        summary.append(String.format("Confidence: %.3f (%.1f%%)\n", getOverallConfidence(), getOverallConfidence() * 100));
        summary.append(String.format("Risk Level: %s\n", getRiskLevel()));
        summary.append(String.format("ML Fraud Predicted: %s\n", isMlFraudPredicted()));
        summary.append(String.format("Rules Triggered: %s\n", areRulesTriggered()));
        summary.append(String.format("Should Block: %s\n", shouldBlockTransaction()));
        summary.append(String.format("Requires Additional Auth: %s\n", requiresAdditionalAuth()));
        
        List<String> topRiskFactors = getTopRiskFactors();
        if (!topRiskFactors.isEmpty()) {
            summary.append(String.format("Top Risk Factors: %s\n", String.join(", ", topRiskFactors)));
        }
        
        if (recommendedActions != null && !recommendedActions.isEmpty()) {
            summary.append(String.format("Recommended Actions: %s\n", String.join(", ", recommendedActions)));
        }
        
        summary.append(String.format("Analysis Time: %s\n", createdAt));
        
        return summary.toString();
    }
    
    /**
     * Check if context is still valid (not expired)
     */
    public boolean isValid() {
        return expiresAt == null || LocalDateTime.now().isBefore(expiresAt);
    }
    
    /**
     * Get processing duration if available
     */
    public Long getProcessingDurationMs() {
        if (processingMetadata != null) {
            return processingMetadata.getTotalProcessingTimeMs();
        }
        return null;
    }

    /**
     * PRODUCTION FIX: Convenience getter for transaction amount
     */
    public java.math.BigDecimal getAmount() {
        return transaction != null ? transaction.getAmount() : java.math.BigDecimal.ZERO;
    }

    /**
     * PRODUCTION FIX: Convenience getter for currency
     */
    public String getCurrency() {
        return transaction != null ? transaction.getCurrency() : null;
    }

    /**
     * PRODUCTION FIX: Convenience getter for timestamp
     */
    public LocalDateTime getTimestamp() {
        return transaction != null ? transaction.getTimestamp() : createdAt;
    }

    /**
     * PRODUCTION FIX: Convenience getter for location (domain model)
     * Returns canonical model.Location with full geospatial features
     */
    public com.waqiti.common.fraud.model.Location getLocationModel() {
        if (locationInfo != null && locationInfo.getLatitude() != null && locationInfo.getLongitude() != null) {
            return com.waqiti.common.fraud.model.Location.builder()
                .latitude(locationInfo.getLatitude())
                .longitude(locationInfo.getLongitude())
                .city(locationInfo.getCity())
                .country(locationInfo.getCountry())
                .countryCode(locationInfo.getCountryCode())
                .region(locationInfo.getRegion())
                .postalCode(locationInfo.getPostalCode())
                .ipAddress(locationInfo.getIpAddress())
                .build();
        }
        return null;
    }

    /**
     * PRODUCTION FIX: Convenience getter for user risk profile
     * Returns canonical profiling.UserRiskProfile with full behavioral data
     */
    public com.waqiti.common.fraud.profiling.UserRiskProfile getUserRiskProfileDomain() {
        if (userProfile != null) {
            return userProfile.toUserRiskProfile();
        }
        return null;
    }

    /**
     * PRODUCTION FIX: Get overall risk score (0-100)
     */
    public double getOverallRiskScore() {
        return getOverallFraudProbability() * 100.0;
    }

    /**
     * PRODUCTION FIX: Set overall risk score
     */
    public void setOverallRiskScore(double score) {
        // Update fraud score if available
        if (fraudScore != null) {
            fraudScore.setOverallScore(score / 100.0);
        }
    }

    /**
     * PRODUCTION FIX: Get historical data context
     */
    public ExternalDataContext getHistoricalData() {
        if (historicalContext != null && historicalContext.getHistoricalTransactions() != null) {
            // Convert historical context to external data format
            ExternalDataContext historical = new ExternalDataContext();
            historical.put("transactions", historicalContext.getHistoricalTransactions());
            historical.put("patterns", historicalContext.getPatterns());
            return historical;
        }
        return externalData != null ? externalData : new ExternalDataContext();
    }

    /**
     * PRODUCTION FIX: Get device info
     */
    public DeviceSessionInfo getDeviceInfo() {
        return deviceSession != null ? deviceSession : new DeviceSessionInfo();
    }

    /**
     * PRODUCTION FIX: Custom builder extension for amount and currency
     */
    public static class FraudContextBuilder {
        /**
         * Set transaction amount (convenience method)
         */
        public FraudContextBuilder amount(BigDecimal amount) {
            if (this.transaction == null) {
                this.transaction = TransactionInfo.builder().amount(amount).build();
            } else {
                this.transaction.setAmount(amount);
            }
            return this;
        }

        /**
         * Set transaction currency (convenience method)
         */
        public FraudContextBuilder currency(String currency) {
            if (this.transaction == null) {
                this.transaction = TransactionInfo.builder().currency(currency).build();
            } else {
                this.transaction.setCurrency(currency);
            }
            return this;
        }

        /**
         * Set transaction timestamp (convenience method)
         */
        public FraudContextBuilder timestamp(LocalDateTime timestamp) {
            if (this.transaction == null) {
                this.transaction = TransactionInfo.builder().timestamp(timestamp).build();
            } else {
                this.transaction.setTimestamp(timestamp);
            }
            return this;
        }

        /**
         * Set transaction additional data (convenience method)
         */
        public FraudContextBuilder transactionData(Map<String, Object> data) {
            if (this.transaction == null) {
                this.transaction = TransactionInfo.builder().additionalData(data).build();
            } else {
                this.transaction.setAdditionalData(data);
            }
            return this;
        }
    }

    // Supporting classes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionInfo {
        private String transactionId;
        /** Transaction amount - BigDecimal for precision */
        private BigDecimal amount;
        private String currency;
        private String transactionType;
        private LocalDateTime timestamp;
        private String paymentMethod;
        private String channel;
        private Map<String, Object> additionalData;

        public String getSummary() {
            return String.format("%s: %.2f %s via %s", transactionId, amount, currency, paymentMethod);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfile {
        private String userId;
        private String email;
        private int accountAgeDays;
        private double riskScore;
        private String customerSegment;
        private Map<String, Object> profileData;
        private List<String> riskFlags;

        public boolean isHighRisk() {
            return riskScore >= 0.7;
        }

        public com.waqiti.common.fraud.profiling.UserRiskProfile toUserRiskProfile() {
            return com.waqiti.common.fraud.profiling.UserRiskProfile.builder()
                .userId(userId)
                .riskScore(riskScore)
                .build();
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantProfile {
        private String merchantId;
        private String name;
        private String category;
        private double riskScore;
        private String country;
        private boolean isHighRisk;
        private Map<String, Object> profileData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceSessionInfo {
        private String deviceId;
        private String deviceType;
        private String browser;
        private String operatingSystem;
        private boolean isNewDevice;
        private double deviceRiskScore;
        private String sessionId;
        private double sessionDurationMinutes;
        private String ipAddress;
        private boolean isProxyVPN;

        /**
         * Add all key-value pairs from map to device session info
         */
        public void putAll(Map<String, Object> data) {
            if (data != null) {
                if (data.containsKey("deviceId")) this.deviceId = (String) data.get("deviceId");
                if (data.containsKey("deviceType")) this.deviceType = (String) data.get("deviceType");
                if (data.containsKey("browser")) this.browser = (String) data.get("browser");
                if (data.containsKey("operatingSystem")) this.operatingSystem = (String) data.get("operatingSystem");
                if (data.containsKey("sessionId")) this.sessionId = (String) data.get("sessionId");
                if (data.containsKey("ipAddress")) this.ipAddress = (String) data.get("ipAddress");
            }
        }

        /**
         * Get number of populated fields
         */
        public int size() {
            int count = 0;
            if (deviceId != null) count++;
            if (deviceType != null) count++;
            if (browser != null) count++;
            if (operatingSystem != null) count++;
            if (sessionId != null) count++;
            if (ipAddress != null) count++;
            return count;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private String postalCode;
        private String ipAddress;
        private Double latitude;
        private Double longitude;
        private double distanceFromHomeKm;
        private boolean isHighRiskLocation;
        private double velocityKmH;

        public com.waqiti.common.fraud.model.Location toLocation() {
            return com.waqiti.common.fraud.model.Location.builder()
                .country(country)
                .region(region)
                .city(city)
                .latitude(latitude)
                .longitude(longitude)
                .build();
        }

        /**
         * PRODUCTION FIX: Add all location data from a map
         */
        public void putAll(Map<String, Object> locationData) {
            if (locationData != null) {
                if (locationData.containsKey("country")) this.country = (String) locationData.get("country");
                if (locationData.containsKey("region")) this.region = (String) locationData.get("region");
                if (locationData.containsKey("city")) this.city = (String) locationData.get("city");
                if (locationData.containsKey("latitude")) this.latitude = ((Number) locationData.get("latitude")).doubleValue();
                if (locationData.containsKey("longitude")) this.longitude = ((Number) locationData.get("longitude")).doubleValue();
            }
        }

        /**
         * PRODUCTION FIX: Get size (number of fields populated)
         */
        public int size() {
            int count = 0;
            if (country != null) count++;
            if (region != null) count++;
            if (city != null) count++;
            if (latitude != null) count++;
            if (longitude != null) count++;
            return count;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private double amountRisk;
        private double velocityRisk;
        private double geographicRisk;
        private double deviceRisk;
        private double behavioralRisk;
        private double overallRisk;
        private String riskExplanation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalContext {
        private int transactionsLast24Hours;
        private double totalAmountLast24Hours;
        private int uniqueMerchantsLastMonth;
        private double avgTransactionAmount;
        @Builder.Default
        private List<String> recentPatterns = new java.util.ArrayList<>();
        private boolean hasHistoricalFraud;
        @Builder.Default
        private List<com.waqiti.common.fraud.dto.TransactionSummary> recentTransactions = new java.util.ArrayList<>();
        @Builder.Default
        private List<Object> historicalTransactions = new java.util.ArrayList<>();

        public List<com.waqiti.common.fraud.dto.TransactionSummary> getRecentTransactions() {
            return recentTransactions != null ? recentTransactions : java.util.Collections.emptyList();
        }

        /**
         * PRODUCTION FIX: Get historical transactions
         */
        public List<Object> getHistoricalTransactions() {
            return historicalTransactions != null ? historicalTransactions : java.util.Collections.emptyList();
        }

        /**
         * PRODUCTION FIX: Get patterns
         */
        public List<String> getPatterns() {
            return recentPatterns != null ? recentPatterns : java.util.Collections.emptyList();
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralAnalysis {
        private double anomalyScore;
        private List<String> anomalies;
        private double deviationFromNormal;
        private Map<String, Double> behaviorMetrics;
        private String behaviorProfile;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalDataContext {
        @Builder.Default
        private Map<String, Object> thirdPartyData = new java.util.HashMap<>();
        private List<String> watchlistMatches;
        private Map<String, String> externalScores;
        private boolean hasExternalAlerts;

        /**
         * PRODUCTION FIX: Add key-value pair to context
         */
        public void put(String key, Object value) {
            if (thirdPartyData == null) {
                thirdPartyData = new java.util.HashMap<>();
            }
            thirdPartyData.put(key, value);
        }

        /**
         * PRODUCTION FIX: Check if context is empty
         */
        public boolean isEmpty() {
            return (thirdPartyData == null || thirdPartyData.isEmpty()) &&
                   (watchlistMatches == null || watchlistMatches.isEmpty()) &&
                   (externalScores == null || externalScores.isEmpty());
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingMetadata {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long totalProcessingTimeMs;
        private long mlProcessingTimeMs;
        private long rulesProcessingTimeMs;
        private String processingVersion;
        private Map<String, Object> debugInfo;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceContext {
        private List<String> applicableRegulations;
        private Map<String, String> complianceChecks;
        private boolean requiresCompliance;
        private List<String> complianceViolations;
    }
    
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }

    // Convenience getter methods for backward compatibility and simplified access

    public String getDeviceId() {
        return deviceSession != null ? deviceSession.getDeviceId() : null;
    }

    public List<com.waqiti.common.fraud.dto.TransactionSummary> getRecentTransactions() {
        return historicalContext != null ? historicalContext.getRecentTransactions() : java.util.Collections.emptyList();
    }

    /**
     * Get merchant ID
     */
    public String getMerchantId() {
        return merchantId != null ? merchantId : (merchantProfile != null ? merchantProfile.getMerchantId() : null);
    }

    /**
     * Get account age
     */
    public Integer getAccountAge() {
        return accountAge != null ? accountAge : (userProfile != null ? userProfile.getAccountAgeDays() : null);
    }

    /**
     * PRODUCTION FIX: Get user risk profile for FraudScoringEngine compatibility
     * Returns the dto.UserRiskProfile for scoring calculations
     */
    public com.waqiti.common.fraud.profiling.UserRiskProfile getUserRiskProfile() {
        if (userProfile != null) {
            return com.waqiti.common.fraud.profiling.UserRiskProfile.builder()
                .userId(userProfile.getUserId())
                .email(userProfile.getEmail())
                .accountAgeDays(userProfile.getAccountAgeDays())
                .riskScore(userProfile.getRiskScore())
                .customerSegment(userProfile.getCustomerSegment())
                .riskFlags(userProfile.getRiskFlags() != null ? userProfile.getRiskFlags() : new java.util.ArrayList<>())
                .typicalTransactionAmount(BigDecimal.ZERO)
                .typicalActiveHours(new java.util.HashSet<>())
                .typicalLocations(new java.util.ArrayList<>())
                .knownDevices(new java.util.HashSet<>())
                .typicalDailyTransactions(0)
                .overallRiskScore(userProfile.getRiskScore())
                .lastUpdated(java.time.LocalDateTime.now())
                .build();
        }
        return com.waqiti.common.fraud.profiling.UserRiskProfile.builder()
            .userId(userId)
            .typicalTransactionAmount(BigDecimal.ZERO)
            .typicalDailyTransactions(0)
            .overallRiskScore(0.0)
            .build();
    }

    /**
     * PRODUCTION FIX: Get location for compatibility
     */
    public com.waqiti.common.fraud.model.Location getLocation() {
        if (locationInfo != null) {
            return com.waqiti.common.fraud.model.Location.builder()
                .country(locationInfo.getCountry())
                .countryCode(locationInfo.getCountryCode())
                .region(locationInfo.getRegion())
                .city(locationInfo.getCity())
                .latitude(locationInfo.getLatitude())
                .longitude(locationInfo.getLongitude())
                .build();
        }
        return null;
    }
}