package com.waqiti.common.security.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive suspicious behavior analysis result
 * Contains detailed behavioral anomaly detection and risk assessment
 */
@Data
@Builder
@Jacksonized
public class SuspiciousBehaviorResult {
    
    private String userId;
    private String sessionId;
    private String behaviorAnalysisId;
    private boolean suspiciousActivityDetected;
    private double riskScore;
    private String riskLevel;
    private double confidence;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant analysisTimestamp;
    
    // Behavioral patterns detected
    private List<BehavioralAnomaly> behavioralAnomalies;
    
    // Account takeover indicators
    private AccountTakeoverIndicators accountTakeoverSignals;
    
    // Insider threat analysis
    private InsiderThreatAnalysis insiderThreatRisk;
    
    // Social engineering detection
    private SocialEngineeringAnalysis socialEngineeringRisk;
    
    // Automated bot detection
    private BotDetectionResult botDetectionResult;
    
    // Threat correlation
    private ThreatCorrelationResult threatCorrelation;

    /**
     * Factory method for normal behavior (no suspicious activity)
     */
    public static SuspiciousBehaviorResult normal() {
        return SuspiciousBehaviorResult.builder()
            .suspiciousActivityDetected(false)
            .riskScore(0.0)
            .riskLevel("LOW")
            .confidence(0.95)
            .analysisTimestamp(Instant.now())
            .build();
    }
    
    /**
     * Individual behavioral anomaly detection
     */
    @Data
    @Builder
    @Jacksonized
    public static class BehavioralAnomaly {
        private String anomalyType;
        private String severity;
        private String description;
        private double deviationScore;
        private double confidence;
        private Map<String, Object> evidence;
        private List<String> indicators;
        private String recommendation;
        private boolean requiresImmediateAction;
        
        public enum AnomalyType {
            LOGIN_PATTERN_ANOMALY,
            TRANSACTION_PATTERN_ANOMALY,
            NAVIGATION_ANOMALY,
            TIMING_ANOMALY,
            DEVICE_ANOMALY,
            LOCATION_ANOMALY,
            VELOCITY_ANOMALY,
            BEHAVIORAL_BIOMETRIC_ANOMALY,
            PRIVILEGE_ESCALATION_ATTEMPT,
            DATA_EXFILTRATION_PATTERN
        }
        
        public enum Severity {
            LOW, MEDIUM, HIGH, CRITICAL, EMERGENCY
        }
    }
    
    /**
     * Account takeover indicators
     */
    @Data
    @Builder
    @Jacksonized
    public static class AccountTakeoverIndicators {
        private boolean credentialStuffingDetected;
        private boolean unusualLoginLocation;
        private boolean deviceFingerprintMismatch;
        private boolean passwordChangeAttempts;
        private boolean emailChangeAttempts;
        private boolean phoneNumberChangeAttempts;
        private boolean multipleFailedLogins;
        private boolean immediateHighValueTransactions;
        private boolean settingsChangesPostLogin;
        private boolean contactInformationChanges;
        private double takeoverProbability;
        private List<String> evidenceItems;
        private String primaryIndicator;
    }
    
    /**
     * Insider threat analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class InsiderThreatAnalysis {
        private boolean privilegedUserActivity;
        private boolean afterHoursAccess;
        private boolean unusualDataAccess;
        private boolean excessiveDownloadActivity;
        private boolean systemAdministrationAnomalies;
        private boolean policyViolations;
        private boolean terminationRiskIndicators;
        private boolean financialStressIndicators;
        private double insiderThreatScore;
        private String riskCategory;
        private List<String> concerningBehaviors;
        private Map<String, Object> riskFactors;
    }
    
    /**
     * Social engineering attack detection
     */
    @Data
    @Builder
    @Jacksonized
    public static class SocialEngineeringAnalysis {
        private boolean phishingAttemptDetected;
        private boolean pretextingIndicators;
        private boolean urgencyManipulation;
        private boolean authorityImpersonation;
        private boolean informationHarvesting;
        private boolean trustExploitation;
        private boolean emotionalManipulation;
        private double manipulationScore;
        private List<String> tacticsList;
        private String attackVector;
        private Map<String, Object> attackDetails;
    }
    
    /**
     * Bot and automated activity detection
     */
    @Data
    @Builder
    @Jacksonized
    public static class BotDetectionResult {
        private boolean automatedActivityDetected;
        private String botType;
        private double botProbability;
        private List<String> automationIndicators;
        private Map<String, Double> behavioralScores;
        private boolean captchaChallengeRequired;
        private String detectionMethod;
        private Map<String, Object> fingerprinting;
        
        public enum BotType {
            CREDENTIAL_STUFFING_BOT,
            SCRAPING_BOT,
            TRANSACTION_BOT,
            ACCOUNT_CREATION_BOT,
            CLICK_FRAUD_BOT,
            SPAM_BOT,
            RECONNAISSANCE_BOT
        }
    }
    
    /**
     * Threat correlation across multiple indicators
     */
    @Data
    @Builder
    @Jacksonized
    public static class ThreatCorrelationResult {
        private List<String> correlatedThreats;
        private Map<String, Double> threatScores;
        private String primaryThreat;
        private boolean multiVectorAttack;
        private List<String> attackPhases;
        private String campaignAttribution;
        private Map<String, Object> correlationEvidence;
        private double overallThreatLevel;
    }
    
    /**
     * Calculates the comprehensive suspicious behavior score
     */
    public double calculateComprehensiveSuspicionScore() {
        double score = 0.0;
        
        // Base behavioral anomalies (0-40 points)
        if (behavioralAnomalies != null && !behavioralAnomalies.isEmpty()) {
            double anomalyScore = behavioralAnomalies.stream()
                .mapToDouble(anomaly -> {
                    switch (anomaly.severity) {
                        case "CRITICAL": return 10.0;
                        case "HIGH": return 7.0;
                        case "MEDIUM": return 4.0;
                        case "LOW": return 2.0;
                        default: return 1.0;
                    }
                })
                .sum();
            score += Math.min(anomalyScore, 40.0);
        }
        
        // Account takeover risk (0-25 points)
        if (accountTakeoverSignals != null) {
            score += accountTakeoverSignals.getTakeoverProbability() * 0.25;
        }
        
        // Insider threat risk (0-20 points)
        if (insiderThreatRisk != null) {
            score += insiderThreatRisk.getInsiderThreatScore() * 0.20;
        }
        
        // Bot detection (0-15 points)
        if (botDetectionResult != null && botDetectionResult.isAutomatedActivityDetected()) {
            score += botDetectionResult.getBotProbability() * 15;
        }
        
        return Math.min(score, 100.0);
    }
    
    /**
     * Determines if immediate security action is required
     */
    public boolean requiresImmediateSecurityAction() {
        return riskScore >= 85 ||
               (accountTakeoverSignals != null && accountTakeoverSignals.getTakeoverProbability() >= 0.8) ||
               (behavioralAnomalies != null && behavioralAnomalies.stream()
                   .anyMatch(anomaly -> anomaly.isRequiresImmediateAction())) ||
               (threatCorrelation != null && threatCorrelation.isMultiVectorAttack());
    }
    
    /**
     * Gets the most critical security concern
     */
    public String getPrimaryConcern() {
        if (accountTakeoverSignals != null && accountTakeoverSignals.getTakeoverProbability() >= 0.7) {
            return "ACCOUNT_TAKEOVER";
        }
        if (insiderThreatRisk != null && insiderThreatRisk.getInsiderThreatScore() >= 80) {
            return "INSIDER_THREAT";
        }
        if (botDetectionResult != null && botDetectionResult.isAutomatedActivityDetected()) {
            return "AUTOMATED_ATTACK";
        }
        if (socialEngineeringRisk != null && socialEngineeringRisk.getManipulationScore() >= 70) {
            return "SOCIAL_ENGINEERING";
        }
        if (behavioralAnomalies != null && !behavioralAnomalies.isEmpty()) {
            return "BEHAVIORAL_ANOMALY";
        }
        return "GENERAL_SUSPICIOUS_ACTIVITY";
    }
    
    /**
     * Gets recommended security actions
     */
    public List<String> getRecommendedActions() {
        List<String> actions = new java.util.ArrayList<>();
        
        if (requiresImmediateSecurityAction()) {
            actions.add("IMMEDIATE_ACCOUNT_REVIEW");
            actions.add("NOTIFY_SECURITY_TEAM");
        }
        
        if (accountTakeoverSignals != null && accountTakeoverSignals.getTakeoverProbability() >= 0.6) {
            actions.add("FORCE_PASSWORD_RESET");
            actions.add("REQUIRE_2FA_VERIFICATION");
            actions.add("TEMPORARY_ACCOUNT_LOCK");
        }
        
        if (botDetectionResult != null && botDetectionResult.isAutomatedActivityDetected()) {
            actions.add("CAPTCHA_CHALLENGE");
            actions.add("RATE_LIMIT_USER");
            actions.add("DEVICE_VERIFICATION");
        }
        
        if (insiderThreatRisk != null && insiderThreatRisk.getInsiderThreatScore() >= 60) {
            actions.add("ENHANCED_MONITORING");
            actions.add("ACCESS_LOG_REVIEW");
            actions.add("MANAGER_NOTIFICATION");
        }
        
        if (actions.isEmpty()) {
            actions.add("CONTINUE_MONITORING");
        }
        
        return actions;
    }
    
    /**
     * Determines monitoring duration based on risk level
     */
    public int getRecommendedMonitoringDays() {
        if (riskScore >= 90) return 90;
        if (riskScore >= 70) return 30;
        if (riskScore >= 50) return 14;
        if (riskScore >= 30) return 7;
        return 3;
    }
}