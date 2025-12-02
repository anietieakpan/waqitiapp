package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive behavioral fraud analysis using advanced behavioral biometrics
 * and machine learning to detect account takeover and fraudulent behavior patterns
 */
@Data
@Builder
@Jacksonized
public class BehavioralFraudAnalysis {
    
    private String userId;
    private String sessionId;
    private double riskScore;
    private String riskLevel;
    private double confidence;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant analysisTimestamp;

    /**
     * Timestamp field (alias for analysisTimestamp for builder compatibility)
     */
    private Instant timestamp;
    
    // Behavioral biometrics
    private TypingPatternAnalysis typingPatterns;
    private MouseMovementAnalysis mouseMovement;
    private NavigationPatternAnalysis navigationPatterns;
    private TouchPatternAnalysis touchPatterns; // For mobile devices

    // User behavior profile (for compatibility)
    private Object behaviorProfile; // Generic behavior profile for external integrations

    // Session behavior analysis
    private SessionBehaviorAnalysis sessionBehavior;
    
    // Temporal behavior analysis
    private TemporalBehaviorAnalysis temporalBehavior;
    
    // Cognitive behavior analysis
    private CognitiveBehaviorAnalysis cognitiveBehavior;
    
    // Device interaction patterns
    private DeviceInteractionAnalysis deviceInteraction;
    
    // Anomaly detection results
    private List<BehavioralAnomaly> anomalies;
    
    /**
     * Typing pattern analysis for keystroke dynamics
     */
    @Data
    @Builder
    @Jacksonized
    public static class TypingPatternAnalysis {
        private double averageTypingSpeed; // WPM
        private double keystrokeRhythm;
        private Map<String, Double> digramTimings; // Time between specific key pairs
        private Map<String, Double> dwellTimes; // Key press duration
        private double backspaceFrequency;
        private boolean copiedPastedDetected;
        private double typingConsistencyScore;
        private double deviationFromBaseline;
        private List<String> anomalousSequences;
        private boolean keyloggerIndicators;
        private double authenticityScore;
    }
    
    /**
     * Mouse movement and click pattern analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class MouseMovementAnalysis {
        private double averageVelocity;
        private double accelerationPatterns;
        private double clickFrequency;
        private Map<String, Double> movementVectors;
        private double tremor; // Hand stability indicator
        private double smoothness;
        private boolean scriptedMovement;
        private double humanlikeness;
        private List<String> suspiciousPatterns;
        private double curveAnalysis;
        private boolean automatedToolDetected;
        private double pressureSensitivity; // For supported devices
    }
    
    /**
     * Website/app navigation pattern analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class NavigationPatternAnalysis {
        private List<String> pageSequence;
        private Map<String, Long> pageTimings;
        private double navigationSpeed;
        private boolean skipsPagesNormally;
        private boolean familiarWithInterface;
        private List<String> unusualNavigationPaths;
        private double expertiseLevel;
        private boolean showsHesitation;
        private boolean directToTarget; // Unusually direct to sensitive functions
        private double entropyScore;
        private boolean followsNormalUserJourney;
        private List<String> shortcutUsage;
    }
    
    /**
     * Touch pattern analysis for mobile devices
     */
    @Data
    @Builder
    @Jacksonized
    public static class TouchPatternAnalysis {
        private double touchPressure;
        private double touchSize;
        private Map<String, Double> swipeVelocities;
        private double tapDuration;
        private boolean multiTouchPatterns;
        private double fingerTremor;
        private boolean leftRightHandedness;
        private List<String> gestures;
        private double touchAccuracy;
        private boolean roboticTouchDetected;
        private double naturalness;
        private String deviceOrientation;
    }
    
    /**
     * Overall session behavior analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class SessionBehaviorAnalysis {
        private long sessionDuration;
        private int totalInteractions;
        private double interactionFrequency;
        private List<String> activitySequence;
        private boolean showsPausesForThinking;
        private boolean rushesToCompletion;
        private double taskCompletionPattern;
        private boolean abandonsNormalFlow;
        private List<String> errorPatterns;
        private double concentrationLevel;
        private boolean multitaskingIndicators;
        private boolean showsFamiliarityWithSystem;
    }
    
    /**
     * Temporal behavior analysis (time-based patterns)
     */
    @Data
    @Builder
    @Jacksonized
    public static class TemporalBehaviorAnalysis {
        private Map<String, Integer> hourlyActivityPattern;
        private List<String> usualLoginTimes;
        private boolean outsideNormalHours;
        private String timezone;
        private boolean timezoneInconsistent;
        private double sessionSpacingPattern;
        private boolean burstyActivity;
        private boolean consistentWithWorkSchedule;
        private List<String> temporalAnomalies;
        private boolean weekdayWeekendDifferences;
        private double circadianRhythm;
        private boolean vacationModeDetected;
    }
    
    /**
     * Cognitive behavior analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class CognitiveBehaviorAnalysis {
        private double readingSpeed;
        private boolean readsInstructions;
        private double formFillingSpeed;
        private int errorsAndCorrections;
        private boolean showsConfusion;
        private double decisionMakingTime;
        private boolean performsDoubleChecks;
        private double memoryConsistency; // Remembers previous inputs
        private boolean learningCurvePresent;
        private double problemSolvingApproach;
        private boolean showsHumanCognitiveLimitations;
        private List<String> cognitiveFlags;
    }
    
    /**
     * Device interaction analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class DeviceInteractionAnalysis {
        private String deviceType;
        private Map<String, String> deviceCapabilities;
        private boolean usesDeviceAppropriately;
        private List<String> inputMethods;
        private boolean adaptsToDifferentScreenSizes;
        private double deviceFamiliarity;
        private boolean accessibilityFeaturesUsed;
        private List<String> hardwareInteractionPatterns;
        private boolean naturalDeviceOrientation;
        private double ergonomicConsistency;
    }
    
    /**
     * Individual behavioral anomaly
     */
    @Data
    @Builder
    @Jacksonized
    public static class BehavioralAnomaly {
        private String anomalyType;
        private String severity;
        private String description;
        private double deviationScore;
        private Map<String, Object> evidence;
        private double confidence;
        private String category;
        private List<String> indicators;
        private String recommendation;
        private boolean requiresImmediateAction;
        
        public enum AnomalyType {
            TYPING_SPEED_ANOMALY,
            MOUSE_MOVEMENT_UNNATURAL,
            NAVIGATION_TOO_EFFICIENT,
            TIME_PATTERN_INCONSISTENT,
            COGNITIVE_MISMATCH,
            DEVICE_INTERACTION_UNUSUAL,
            AUTOMATION_DETECTED,
            ACCOUNT_TAKEOVER_INDICATORS,
            SOCIAL_ENGINEERING_PATTERNS,
            INSIDER_THREAT_BEHAVIOR
        }
    }
    
    /**
     * Calculate comprehensive behavioral risk score
     */
    public double calculateBehavioralRiskScore() {
        double score = 0.0;
        
        // Typing patterns (0-25 points)
        if (typingPatterns != null) {
            score += (100 - typingPatterns.getAuthenticityScore()) * 0.25;
            if (typingPatterns.isCopiedPastedDetected()) score += 10;
            if (typingPatterns.isKeyloggerIndicators()) score += 15;
        }
        
        // Mouse movement (0-20 points)
        if (mouseMovement != null) {
            score += (100 - mouseMovement.getHumanlikeness()) * 0.20;
            if (mouseMovement.isScriptedMovement()) score += 15;
            if (mouseMovement.isAutomatedToolDetected()) score += 20;
        }
        
        // Navigation patterns (0-15 points)
        if (navigationPatterns != null) {
            if (navigationPatterns.isDirectToTarget() && !navigationPatterns.isFamiliarWithInterface()) {
                score += 12;
            }
            score += Math.max(0, 15 - navigationPatterns.getExpertiseLevel());
        }
        
        // Session behavior (0-20 points)
        if (sessionBehavior != null) {
            if (sessionBehavior.isRushesToCompletion()) score += 8;
            if (sessionBehavior.isAbandonsNormalFlow()) score += 10;
            if (!sessionBehavior.isShowsFamiliarityWithSystem()) score += 7;
        }
        
        // Temporal anomalies (0-10 points)
        if (temporalBehavior != null) {
            if (temporalBehavior.isOutsideNormalHours()) score += 5;
            if (temporalBehavior.isTimezoneInconsistent()) score += 8;
            if (temporalBehavior.isBurstyActivity()) score += 4;
        }
        
        // Cognitive behavior (0-10 points)
        if (cognitiveBehavior != null) {
            if (!cognitiveBehavior.isShowsHumanCognitiveLimitations()) score += 10;
            if (cognitiveBehavior.getMemoryConsistency() < 50) score += 6;
        }
        
        return Math.min(score, 100.0);
    }
    
    /**
     * Detect if behavior indicates account takeover
     */
    public boolean indicatesAccountTakeover() {
        return anomalies != null && anomalies.stream()
            .anyMatch(anomaly -> 
                anomaly.getAnomalyType().contains("ACCOUNT_TAKEOVER") ||
                anomaly.getSeverity().equals("CRITICAL"));
    }
    
    /**
     * Detect if behavior indicates automation/bot activity
     */
    public boolean indicatesAutomation() {
        return (mouseMovement != null && mouseMovement.isScriptedMovement()) ||
               (typingPatterns != null && typingPatterns.isKeyloggerIndicators()) ||
               (navigationPatterns != null && navigationPatterns.isDirectToTarget() && 
                navigationPatterns.getExpertiseLevel() == 0) ||
               (cognitiveBehavior != null && !cognitiveBehavior.isShowsHumanCognitiveLimitations());
    }
    
    /**
     * Get the most concerning behavioral indicators
     */
    public List<String> getCriticalIndicators() {
        List<String> indicators = new java.util.ArrayList<>();
        
        if (indicatesAutomation()) indicators.add("AUTOMATION_DETECTED");
        if (indicatesAccountTakeover()) indicators.add("ACCOUNT_TAKEOVER_RISK");
        
        if (typingPatterns != null && typingPatterns.getDeviationFromBaseline() > 80) {
            indicators.add("TYPING_PATTERN_HIGHLY_ANOMALOUS");
        }
        
        if (temporalBehavior != null && temporalBehavior.isTimezoneInconsistent()) {
            indicators.add("GEOGRAPHIC_INCONSISTENCY");
        }
        
        if (sessionBehavior != null && sessionBehavior.isRushesToCompletion() && 
            !sessionBehavior.isShowsFamiliarityWithSystem()) {
            indicators.add("SUSPICIOUS_URGENCY_PATTERN");
        }
        
        return indicators;
    }
    
    /**
     * Determine confidence level in behavioral analysis
     */
    public double calculateConfidenceLevel() {
        double baseConfidence = 70.0;

        // Increase confidence with more data points
        int dataPoints = 0;
        if (typingPatterns != null) dataPoints += 3;
        if (mouseMovement != null) dataPoints += 2;
        if (navigationPatterns != null) dataPoints += 2;
        if (sessionBehavior != null) dataPoints += 2;
        if (temporalBehavior != null) dataPoints += 1;

        double dataConfidenceBonus = Math.min(20.0, dataPoints * 2.0);

        return Math.min(baseConfidence + dataConfidenceBonus, 95.0);
    }

    /**
     * Get analysis timestamp
     */
    public Instant timestamp() {
        return analysisTimestamp;
    }
}