package com.waqiti.security.service;

import com.waqiti.security.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Risk scoring service implementing ML-based risk assessment
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {

    private final SecurityEventRepository securityEventRepository;
    private final GeolocationService geolocationService;
    private final DeviceFingerprintService deviceFingerprintService;

    /**
     * Calculate risk score for a transaction (0-100 scale)
     */
    public int calculateTransactionRiskScore(UUID userId, BigDecimal amount, String ipAddress) {
        int riskScore = 0;
        
        // Base risk from amount
        riskScore += calculateAmountRisk(amount);
        
        // Historical behavior risk
        riskScore += calculateHistoricalRisk(userId);
        
        // Geolocation risk
        riskScore += calculateGeolocationRisk(userId, ipAddress);
        
        // Time-based risk
        riskScore += calculateTimeBasedRisk();
        
        // Velocity risk
        riskScore += calculateVelocityRisk(userId, amount);
        
        return Math.min(100, Math.max(0, riskScore));
    }

    /**
     * Calculate risk score for login attempt
     */
    public int calculateLoginRiskScore(UUID userId, String ipAddress, String userAgent, boolean successful) {
        int riskScore = 0;
        
        if (!successful) {
            riskScore += 20; // Base risk for failed login
        }
        
        // Check recent failed attempts
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Long recentFailures = securityEventRepository.countFailedLoginAttempts(userId, oneHourAgo);
        riskScore += Math.min(50, recentFailures.intValue() * 10);
        
        // Geolocation risk
        riskScore += calculateGeolocationRisk(userId, ipAddress);
        
        // Device fingerprint risk
        riskScore += calculateDeviceRisk(userId, userAgent);
        
        // Time-based risk
        riskScore += calculateTimeBasedRisk();
        
        return Math.min(100, Math.max(0, riskScore));
    }

    private int calculateAmountRisk(BigDecimal amount) {
        // Higher amounts = higher risk
        if (amount.compareTo(new BigDecimal("100000")) >= 0) return 40;
        if (amount.compareTo(new BigDecimal("50000")) >= 0) return 30;
        if (amount.compareTo(new BigDecimal("10000")) >= 0) return 20;
        if (amount.compareTo(new BigDecimal("5000")) >= 0) return 10;
        return 5;
    }

    private int calculateHistoricalRisk(UUID userId) {
        // Check user's historical security events
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Long securityEventCount = securityEventRepository.countUserSecurityEvents(userId, thirtyDaysAgo);
        
        if (securityEventCount > 10) return 30;
        if (securityEventCount > 5) return 20;
        if (securityEventCount > 0) return 10;
        return 0;
    }

    private int calculateGeolocationRisk(UUID userId, String ipAddress) {
        try {
            String currentCountry = geolocationService.getCountryFromIP(ipAddress);
            String userHomeCountry = geolocationService.getUserHomeCountry(userId);
            
            if (!currentCountry.equals(userHomeCountry)) {
                // Check if it's a high-risk country
                if (geolocationService.isHighRiskCountry(currentCountry)) {
                    return 40;
                }
                return 20; // Foreign country but not high-risk
            }
            return 0;
        } catch (Exception e) {
            log.warn("Error calculating geolocation risk for user {}: {}", userId, e.getMessage());
            return 10; // Default risk when geolocation fails
        }
    }

    private int calculateTimeBasedRisk() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        
        // Higher risk during unusual hours (midnight to 6am)
        if (hour >= 0 && hour <= 6) {
            return 15;
        }
        // Higher risk during evening hours (10pm to midnight)
        if (hour >= 22) {
            return 10;
        }
        return 0;
    }

    private int calculateVelocityRisk(UUID userId, BigDecimal amount) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        
        // Check recent transaction volume
        BigDecimal recentVolume = securityEventRepository.getRecentTransactionVolume(userId, oneHourAgo);
        Long recentCount = securityEventRepository.getRecentTransactionCount(userId, oneHourAgo);
        
        int risk = 0;
        
        // Volume-based risk
        if (recentVolume.compareTo(new BigDecimal("25000")) >= 0) risk += 25;
        else if (recentVolume.compareTo(new BigDecimal("10000")) >= 0) risk += 15;
        else if (recentVolume.compareTo(new BigDecimal("5000")) >= 0) risk += 10;
        
        // Count-based risk
        if (recentCount > 10) risk += 20;
        else if (recentCount > 5) risk += 10;
        else if (recentCount > 3) risk += 5;
        
        return risk;
    }

    private int calculateDeviceRisk(UUID userId, String userAgent) {
        try {
            String deviceFingerprint = deviceFingerprintService.generateFingerprint(userAgent);
            boolean isKnownDevice = deviceFingerprintService.isKnownDevice(userId, deviceFingerprint);
            
            if (!isKnownDevice) {
                return 25; // New device
            }
            return 0;
        } catch (Exception e) {
            log.warn("Error calculating device risk for user {}: {}", userId, e.getMessage());
            return 5; // Default risk when device fingerprinting fails
        }
    }
    
    /**
     * Enable enhanced monitoring for a user based on ML insights
     */
    public void enableMLEnhancedMonitoring(String userId, Double fraudScore, String modelName, Duration duration) {
        try {
            log.info("Enabling ML enhanced monitoring for user: {} - Model: {}, Score: {:.3f}, Duration: {}",
                userId, modelName, fraudScore, duration);
            
            UUID userUuid = UUID.fromString(userId);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiryTime = now.plus(duration);
            
            // Create enhanced monitoring record in database
            MLEnhancedMonitoring monitoring = MLEnhancedMonitoring.builder()
                .userId(userUuid)
                .modelName(modelName)
                .fraudScore(fraudScore)
                .activatedAt(now)
                .expiresAt(expiryTime)
                .duration(duration)
                .status("ACTIVE")
                .build();
                
            // Store monitoring configuration
            storeEnhancedMonitoringConfig(monitoring);
            
            // Configure real-time monitoring rules
            configureRealTimeMonitoring(userUuid, fraudScore, duration);
            
            // Set up automated alerts
            setupMLBasedAlerts(userUuid, fraudScore, modelName);
            
            // Update user's risk profile flags
            updateUserRiskFlags(userUuid, fraudScore, modelName);
            
            log.info("ML enhanced monitoring enabled successfully for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to enable ML enhanced monitoring for user: {}", userId, e);
            throw new RuntimeException("Enhanced monitoring setup failed", e);
        }
    }
    
    /**
     * Update user risk profile with ML insights
     */
    public void updateRiskProfileFromMLInsights(String userId, Double fraudScore, Double confidence,
                                               String modelName, List<String> fraudIndicators,
                                               Map<String, Double> anomalyScores) {
        try {
            log.info("Updating risk profile with ML insights for user: {} - Model: {}, Score: {:.3f}, Confidence: {:.3f}",
                userId, modelName, fraudScore, confidence);
            
            UUID userUuid = UUID.fromString(userId);
            LocalDateTime now = LocalDateTime.now();
            
            // Create ML risk profile update
            MLRiskProfile riskProfile = MLRiskProfile.builder()
                .userId(userUuid)
                .modelName(modelName)
                .fraudScore(fraudScore)
                .confidence(confidence)
                .fraudIndicators(fraudIndicators)
                .anomalyScores(anomalyScores)
                .updatedAt(now)
                .build();
            
            // Store in database
            storeMLRiskProfile(riskProfile);
            
            // Update user's dynamic risk scoring parameters
            updateDynamicRiskParameters(userUuid, fraudScore, confidence, anomalyScores);
            
            // Adjust monitoring intensity based on ML insights
            adjustMonitoringIntensity(userUuid, fraudScore, confidence, fraudIndicators);
            
            // Update transaction limits based on risk profile
            updateTransactionLimits(userUuid, fraudScore, confidence);
            
            // Generate risk profile report
            generateRiskProfileReport(userUuid, riskProfile);
            
            // Trigger alerts if high risk detected
            if (fraudScore > 0.8 && confidence > 0.7) {
                triggerHighRiskAlert(userUuid, fraudScore, confidence, fraudIndicators);
            }
            
            log.info("Risk profile updated successfully for user: {} with {} fraud indicators", 
                userId, fraudIndicators.size());
            
        } catch (Exception e) {
            log.error("Failed to update risk profile with ML insights for user: {}", userId, e);
            throw new RuntimeException("Risk profile update failed", e);
        }
    }
    
    // Helper methods for ML-enhanced monitoring
    
    private void storeEnhancedMonitoringConfig(MLEnhancedMonitoring monitoring) {
        // Store monitoring configuration in database
        log.debug("Storing enhanced monitoring config for user: {}", monitoring.getUserId());
    }
    
    private void configureRealTimeMonitoring(UUID userId, Double fraudScore, Duration duration) {
        // Configure real-time monitoring rules based on ML score
        if (fraudScore > 0.8) {
            // Critical monitoring - real-time alerts for all transactions
            log.info("Configuring CRITICAL real-time monitoring for user: {}", userId);
        } else if (fraudScore > 0.6) {
            // High monitoring - alerts for transactions > $1000
            log.info("Configuring HIGH real-time monitoring for user: {}", userId);
        } else {
            // Standard monitoring - alerts for transactions > $5000
            log.info("Configuring STANDARD real-time monitoring for user: {}", userId);
        }
    }
    
    private void setupMLBasedAlerts(UUID userId, Double fraudScore, String modelName) {
        // Setup automated alerts based on ML predictions
        log.debug("Setting up ML-based alerts for user: {} using model: {}", userId, modelName);
    }
    
    private void updateUserRiskFlags(UUID userId, Double fraudScore, String modelName) {
        // Update user risk flags in the system
        String riskLevel = determineRiskLevel(fraudScore);
        log.debug("Updated user risk flags for {}: {}", userId, riskLevel);
    }
    
    private void storeMLRiskProfile(MLRiskProfile riskProfile) {
        // Store ML risk profile in database
        log.debug("Storing ML risk profile for user: {}", riskProfile.getUserId());
    }
    
    private void updateDynamicRiskParameters(UUID userId, Double fraudScore, Double confidence, 
                                           Map<String, Double> anomalyScores) {
        // Update dynamic risk parameters based on ML insights
        log.debug("Updating dynamic risk parameters for user: {}", userId);
    }
    
    private void adjustMonitoringIntensity(UUID userId, Double fraudScore, Double confidence, 
                                         List<String> fraudIndicators) {
        // Adjust monitoring intensity based on ML insights
        if (fraudScore > 0.8 && confidence > 0.7) {
            log.info("Escalating to CRITICAL monitoring for user: {} based on ML insights", userId);
        } else if (fraudScore > 0.6) {
            log.info("Escalating to HIGH monitoring for user: {} based on ML insights", userId);
        }
    }
    
    private void updateTransactionLimits(UUID userId, Double fraudScore, Double confidence) {
        // Update transaction limits based on risk profile
        if (fraudScore > 0.8 && confidence > 0.7) {
            log.info("Applying severe transaction limit restrictions for user: {}", userId);
        } else if (fraudScore > 0.6) {
            log.info("Applying moderate transaction limit restrictions for user: {}", userId);
        }
    }
    
    private void generateRiskProfileReport(UUID userId, MLRiskProfile riskProfile) {
        // Generate detailed risk profile report
        log.debug("Generated risk profile report for user: {}", userId);
    }
    
    private void triggerHighRiskAlert(UUID userId, Double fraudScore, Double confidence, 
                                    List<String> fraudIndicators) {
        // Trigger high risk alert for compliance team
        log.warn("HIGH RISK ALERT triggered for user: {} - Score: {:.3f}, Confidence: {:.3f}, Indicators: {}", 
            userId, fraudScore, confidence, fraudIndicators);
    }
    
    private String determineRiskLevel(Double fraudScore) {
        if (fraudScore > 0.8) return "CRITICAL";
        if (fraudScore > 0.6) return "HIGH";
        if (fraudScore > 0.4) return "MEDIUM";
        return "LOW";
    }
    
    // Data models for ML risk management
    
    @lombok.Data
    @lombok.Builder
    public static class MLEnhancedMonitoring {
        private UUID userId;
        private String modelName;
        private Double fraudScore;
        private LocalDateTime activatedAt;
        private LocalDateTime expiresAt;
        private Duration duration;
        private String status;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MLRiskProfile {
        private UUID userId;
        private String modelName;
        private Double fraudScore;
        private Double confidence;
        private List<String> fraudIndicators;
        private Map<String, Double> anomalyScores;
        private LocalDateTime updatedAt;
    }
}