package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * User behavioral patterns and biometrics
 * Used for behavioral analysis and anomaly detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior {

    private String userId;
    private String sessionId;
    private Instant capturedAt;

    // Typing dynamics
    private Integer typingSpeed; // characters per second
    private Double averageKeyHoldTime; // milliseconds
    private Double averageKeyInterval; // milliseconds between keys
    private Map<String, Double> keystrokeDynamics; // key pair -> timing
    private Boolean typingPatternMatches;

    // Mouse/Touch dynamics
    private Integer mouseMovements;
    private Double averageMouseSpeed;
    private List<Double> mouseAcceleration;
    private Integer clickCount;
    private Double clickPrecision;
    private Boolean mousePatternMatches;

    // Touch dynamics (mobile)
    private Integer touchPoints;
    private Double averageTouchPressure;
    private Double averageTouchDuration;
    private Double touchAreaVariance;
    private Boolean touchPatternMatches;

    // Navigation patterns
    private List<String> pageSequence;
    private Integer pageVisitCount;
    private Integer backButtonClicks;
    private Integer formResubmissions;
    private Double sessionDuration; // seconds
    private Boolean navigationPatternMatches;

    // Device interaction
    private String deviceOrientation; // PORTRAIT, LANDSCAPE
    private Integer deviceOrientationChanges;
    private Boolean copyPasteDetected;
    private Boolean autofillUsed;
    private Boolean screenRecordingDetected;

    // Time patterns
    private LocalTime usualLoginTime;
    private List<Integer> usualActivityHours;
    private Boolean outsideUsualHours;
    private List<Integer> usualActivityDays;
    private Boolean outsideUsualDays;

    // Biometric data (if available)
    private Boolean biometricAuthenticated;
    private String biometricType; // FINGERPRINT, FACE, IRIS, VOICE
    private Double biometricConfidence;

    // Behavioral score
    private Double behavioralScore; // 0.0 (anomalous) to 1.0 (normal)
    private List<String> anomalies; // Detected behavioral anomalies
    private Double overallSimilarity; // To historical behavior

    private Map<String, Object> rawBehaviorData;
}
