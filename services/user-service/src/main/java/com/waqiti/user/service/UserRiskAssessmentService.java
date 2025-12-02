package com.waqiti.user.service;

import com.waqiti.user.dto.security.UserRiskData;
import com.waqiti.user.dto.security.UserBehaviorProfile;
import com.waqiti.user.security.AdaptiveJwtTokenProvider.*;
import com.waqiti.user.security.DeviceFingerprintService;
import com.waqiti.user.security.LocationValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * User Risk Assessment Service
 * 
 * Provides comprehensive risk assessment for user authentication:
 * - Behavioral pattern analysis
 * - Device and location risk scoring
 * - Transaction pattern evaluation
 * - Multi-factor authentication triggers
 * - Adaptive security recommendations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserRiskAssessmentService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceFingerprintService deviceFingerprintService;
    private final LocationValidationService locationValidationService;
    
    private static final String USER_RISK_PREFIX = "user:risk:";
    private static final String USER_BEHAVIOR_PREFIX = "user:behavior:";
    private static final String SECURITY_EVENTS_PREFIX = "user:security:";
    
    private static final double HIGH_RISK_THRESHOLD = 0.8;
    private static final double MEDIUM_RISK_THRESHOLD = 0.5;

    /**
     * Assesses comprehensive user risk based on current context
     */
    public UserRiskProfile assessUserRisk(UUID userId, AuthenticationContext context) {
        try {
            log.debug("Assessing risk for user {} with context", userId);
            
            // Get baseline user risk
            UserRiskData baselineRisk = getUserBaselineRisk(userId);
            
            // Calculate component risk scores
            double deviceRisk = assessDeviceRisk(userId, context);
            double locationRisk = assessLocationRisk(userId, context);
            double behavioralRisk = assessBehavioralRisk(userId, context);
            double temporalRisk = assessTemporalRisk(userId, context);
            double securityEventRisk = assessSecurityEventRisk(userId);
            
            // Weighted risk calculation
            double overallRisk = calculateWeightedRisk(
                baselineRisk.getBaselineRiskScore(),
                deviceRisk, locationRisk, behavioralRisk, temporalRisk, securityEventRisk
            );
            
            // Determine risk level and requirements
            RiskLevel riskLevel = determineRiskLevel(overallRisk);
            boolean requiresMfa = shouldRequireMfa(riskLevel, overallRisk, context);
            boolean restrictedAccess = shouldRestrictAccess(riskLevel, overallRisk);
            
            // Generate security recommendations
            List<String> recommendations = generateSecurityRecommendations(
                riskLevel, deviceRisk, locationRisk, behavioralRisk, context);
            
            // Determine allowed operations
            Set<String> allowedOperations = determineAllowedOperations(riskLevel, restrictedAccess);
            
            // Update user risk profile
            updateUserRiskProfile(userId, overallRisk, riskLevel);
            
            UserRiskProfile profile = new UserRiskProfile(
                riskLevel, overallRisk, requiresMfa, restrictedAccess, 
                allowedOperations, recommendations
            );
            
            log.info("Risk assessment for user {}: level={}, score={:.3f}, MFA={}, restricted={}", 
                    userId, riskLevel, overallRisk, requiresMfa, restrictedAccess);
            
            return profile;
            
        } catch (Exception e) {
            log.error("Failed to assess user risk for {}", userId, e);
            // Return safe default in case of error
            return new UserRiskProfile(
                RiskLevel.HIGH, 0.9, true, true, 
                Set.of("READ"), List.of("Risk assessment failed - security review required")
            );
        }
    }

    /**
     * Detects behavioral anomalies for a user
     */
    public boolean detectBehavioralAnomaly(UUID userId, AuthenticationContext context) {
        try {
            UserBehaviorProfile behavior = getUserBehaviorProfile(userId);
            if (behavior == null) {
                // First-time user - not necessarily anomalous
                return false;
            }
            
            List<String> anomalies = new ArrayList<>();
            
            // Check login time patterns
            if (isUnusualLoginTime(behavior, LocalDateTime.now())) {
                anomalies.add("UNUSUAL_LOGIN_TIME");
            }
            
            // Check device patterns
            if (context.getDeviceFingerprint() != null) {
                if (!behavior.getKnownDevices().contains(context.getDeviceFingerprint())) {
                    anomalies.add("NEW_DEVICE");
                }
            }
            
            // Check location patterns
            if (context.getLocationData() != null) {
                if (!behavior.getKnownCountries().contains(context.getLocationData().getCountryCode())) {
                    anomalies.add("NEW_COUNTRY");
                }
            }
            
            // Check login frequency
            if (isUnusualLoginFrequency(behavior, userId)) {
                anomalies.add("UNUSUAL_FREQUENCY");
            }
            
            // Update behavior profile
            updateBehaviorProfile(userId, context);
            
            boolean hasAnomalies = !anomalies.isEmpty();
            if (hasAnomalies) {
                log.info("Behavioral anomalies detected for user {}: {}", userId, anomalies);
                recordSecurityEvent(userId, "BEHAVIORAL_ANOMALY", String.join(",", anomalies));
            }
            
            return hasAnomalies;
            
        } catch (Exception e) {
            log.error("Failed to detect behavioral anomaly for user {}", userId, e);
            return false;
        }
    }

    /**
     * Updates user risk profile based on security trigger
     */
    public void updateRiskProfile(UUID userId, SecurityTrigger trigger) {
        try {
            String riskKey = USER_RISK_PREFIX + userId.toString();
            UserRiskData currentRisk = (UserRiskData) redisTemplate.opsForValue().get(riskKey);
            
            if (currentRisk == null) {
                currentRisk = UserRiskData.builder()
                    .userId(userId.toString())
                    .overallRiskScore(0.3)
                    .riskLevel("MEDIUM")
                    .assessedAt(LocalDateTime.now())
                    .riskFactors(new ArrayList<>())
                    .build();
            }
            
            // Adjust risk based on trigger type
            double riskAdjustment = calculateRiskAdjustment(trigger);
            double newRiskScore = Math.max(0.0, Math.min(1.0, 
                currentRisk.getBaselineRiskScore() + riskAdjustment));
            
            currentRisk.setBaselineRiskScore(newRiskScore);
            currentRisk.setLastUpdated(LocalDateTime.now());
            currentRisk.addSecurityEvent(trigger);
            
            // Update risk level based on new score
            if (newRiskScore >= 0.8) {
                currentRisk.setRiskLevel("CRITICAL");
            } else if (newRiskScore >= 0.6) {
                currentRisk.setRiskLevel("HIGH");
            } else if (newRiskScore >= 0.4) {
                currentRisk.setRiskLevel("MEDIUM");
            } else {
                currentRisk.setRiskLevel("LOW");
            }
            
            // Keep only recent security events (last 30 days)
            List<UserRiskData.RiskAlert> recentAlerts = currentRisk.getSecurityEvents().stream()
                .filter(alert -> alert.getTriggeredAt() != null && 
                        alert.getTriggeredAt().isAfter(LocalDateTime.now().minusDays(30)))
                .collect(java.util.stream.Collectors.toList());
            currentRisk.setActiveAlerts(recentAlerts);
            
            redisTemplate.opsForValue().set(riskKey, currentRisk, 90, TimeUnit.DAYS);
            
            log.info("Updated risk profile for user {}: trigger={}, new_score={:.3f}", 
                    userId, trigger.getType(), newRiskScore);
            
        } catch (Exception e) {
            log.error("Failed to update risk profile for user {}", userId, e);
        }
    }

    // Private helper methods

    private double assessDeviceRisk(UUID userId, AuthenticationContext context) {
        try {
            if (context.getDeviceFingerprint() == null) {
                return 0.3; // Medium risk for unknown device
            }
            
            double deviceTrust = deviceFingerprintService.calculateDeviceTrustScore(
                userId, context.getDeviceFingerprint());
            
            // Invert trust to get risk (high trust = low risk)
            return 1.0 - deviceTrust;
            
        } catch (Exception e) {
            log.error("Failed to assess device risk", e);
            return 0.5; // Default medium risk
        }
    }

    private double assessLocationRisk(UUID userId, AuthenticationContext context) {
        try {
            if (context.getIpAddress() == null) {
                return 0.3;
            }
            
            double risk = 0.0;
            
            // Check if high-risk location
            if (locationValidationService.isHighRiskLocation(context.getIpAddress())) {
                risk += 0.6;
            }
            
            // Analyze location patterns
            var locationAnalysis = locationValidationService.analyzeUserLocationPattern(
                userId, context.getIpAddress());
            
            if (locationAnalysis.isAnomalyDetected()) {
                risk += 0.4;
            }
            
            risk += locationAnalysis.getRiskScore() * 0.3;
            
            return Math.min(risk, 1.0);
            
        } catch (Exception e) {
            log.error("Failed to assess location risk", e);
            return 0.3;
        }
    }

    private double assessBehavioralRisk(UUID userId, AuthenticationContext context) {
        try {
            UserBehaviorProfile behavior = getUserBehaviorProfile(userId);
            if (behavior == null) {
                return 0.2; // Lower risk for new users
            }
            
            double risk = 0.0;
            
            // Time-based patterns
            if (isUnusualLoginTime(behavior, LocalDateTime.now())) {
                risk += 0.2;
            }
            
            // Device consistency
            if (context.getDeviceFingerprint() != null &&
                !behavior.getKnownDevices().contains(context.getDeviceFingerprint())) {
                risk += 0.3;
            }
            
            // Location consistency
            if (context.getLocationData() != null &&
                !behavior.getKnownCountries().contains(context.getLocationData().getCountryCode())) {
                risk += 0.2;
            }
            
            // Login frequency anomalies
            if (isUnusualLoginFrequency(behavior, userId)) {
                risk += 0.3;
            }
            
            return Math.min(risk, 1.0);
            
        } catch (Exception e) {
            log.error("Failed to assess behavioral risk", e);
            return 0.3;
        }
    }

    private double assessTemporalRisk(UUID userId, AuthenticationContext context) {
        try {
            LocalDateTime now = LocalDateTime.now();
            double risk = 0.0;
            
            // Check if login during unusual hours (2 AM - 6 AM local time)
            int hour = now.getHour();
            if (hour >= 2 && hour <= 6) {
                risk += 0.2;
            }
            
            // Check for rapid successive logins
            String recentLoginsKey = "recent_logins:" + userId.toString();
            Long recentLoginCount = redisTemplate.opsForList().size(recentLoginsKey);
            
            if (recentLoginCount != null && recentLoginCount > 5) {
                risk += 0.3; // Multiple recent logins
            }
            
            // Record this login attempt
            redisTemplate.opsForList().leftPush(recentLoginsKey, now.toString());
            redisTemplate.opsForList().trim(recentLoginsKey, 0, 10); // Keep last 10
            redisTemplate.expire(recentLoginsKey, 1, TimeUnit.HOURS);
            
            return Math.min(risk, 1.0);
            
        } catch (Exception e) {
            log.error("Failed to assess temporal risk", e);
            return 0.1;
        }
    }

    private double assessSecurityEventRisk(UUID userId) {
        try {
            String securityEventsKey = SECURITY_EVENTS_PREFIX + userId.toString();
            List<Object> recentEvents = redisTemplate.opsForList().range(securityEventsKey, 0, 50);
            
            if (recentEvents == null || recentEvents.isEmpty()) {
                return 0.0;
            }
            
            double risk = 0.0;
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            
            for (Object eventObj : recentEvents) {
                // Events are stored as security trigger objects
                if (eventObj instanceof SecurityTrigger) {
                    SecurityTrigger trigger = 
                        (SecurityTrigger) eventObj;
                    // Assume recent events within the query window
                    risk += getEventRiskScore(trigger.getType().name());
                } else if (eventObj instanceof Map) {
                    // Handle events stored as maps
                    @SuppressWarnings("unchecked")
                    Map<String, Object> eventMap = (Map<String, Object>) eventObj;
                    String eventType = (String) eventMap.get("type");
                    if (eventType != null) {
                        risk += getEventRiskScore(eventType);
                    }
                } else if (eventObj instanceof String) {
                    // Handle events stored as strings
                    risk += getEventRiskScore((String) eventObj);
                }
            }
            
            return Math.min(risk, 1.0);
            
        } catch (Exception e) {
            log.error("Failed to assess security event risk", e);
            return 0.0;
        }
    }

    private double calculateWeightedRisk(double baseline, double device, double location, 
                                       double behavioral, double temporal, double securityEvent) {
        // Weighted combination of risk factors
        return (baseline * 0.2) + (device * 0.25) + (location * 0.2) + 
               (behavioral * 0.2) + (temporal * 0.1) + (securityEvent * 0.05);
    }

    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 0.9) return RiskLevel.CRITICAL;
        if (riskScore >= HIGH_RISK_THRESHOLD) return RiskLevel.HIGH;
        if (riskScore >= MEDIUM_RISK_THRESHOLD) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private boolean shouldRequireMfa(RiskLevel riskLevel, double riskScore, AuthenticationContext context) {
        // Always require MFA for high/critical risk
        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
            return true;
        }
        
        // Require MFA for medium risk with additional factors
        if (riskLevel == RiskLevel.MEDIUM) {
            return context.getDeviceFingerprint() == null || // Unknown device
                   (context.getLocationData() != null && 
                    locationValidationService.isHighRiskLocation(context.getIpAddress()));
        }
        
        return false;
    }

    private boolean shouldRestrictAccess(RiskLevel riskLevel, double riskScore) {
        return riskLevel == RiskLevel.CRITICAL || riskScore >= 0.95;
    }

    private List<String> generateSecurityRecommendations(RiskLevel riskLevel, double deviceRisk, 
                                                        double locationRisk, double behavioralRisk, 
                                                        AuthenticationContext context) {
        List<String> recommendations = new ArrayList<>();
        
        if (riskLevel == RiskLevel.CRITICAL) {
            recommendations.add("Immediate security review required");
            recommendations.add("Consider temporary account suspension");
        }
        
        if (riskLevel == RiskLevel.HIGH) {
            recommendations.add("Enable additional security measures");
            recommendations.add("Review recent account activity");
        }
        
        if (deviceRisk > 0.6) {
            recommendations.add("Verify device ownership");
            recommendations.add("Consider device registration");
        }
        
        if (locationRisk > 0.6) {
            recommendations.add("Verify current location");
            recommendations.add("Review travel patterns");
        }
        
        if (behavioralRisk > 0.6) {
            recommendations.add("Unusual behavior detected - verify identity");
        }
        
        if (context.getDeviceFingerprint() == null) {
            recommendations.add("Device fingerprinting recommended");
        }
        
        return recommendations;
    }

    private Set<String> determineAllowedOperations(RiskLevel riskLevel, boolean restrictedAccess) {
        if (restrictedAccess) {
            return Set.of("READ"); // Read-only access
        }
        
        return switch (riskLevel) {
            case CRITICAL -> Set.of("READ");
            case HIGH -> Set.of("READ", "BASIC_OPERATIONS");
            case MEDIUM -> Set.of("READ", "BASIC_OPERATIONS", "TRANSFER_LOW_AMOUNT");
            case LOW -> Set.of("READ", "BASIC_OPERATIONS", "TRANSFER_LOW_AMOUNT", "TRANSFER_HIGH_AMOUNT", "ADMIN_OPERATIONS");
        };
    }

    private UserRiskData getUserBaselineRisk(UUID userId) {
        try {
            String riskKey = USER_RISK_PREFIX + userId.toString();
            UserRiskData risk = (UserRiskData) redisTemplate.opsForValue().get(riskKey);
            
            if (risk == null) {
                // New user - start with moderate baseline risk
                risk = UserRiskData.builder()
                    .userId(userId.toString())
                    .overallRiskScore(0.3)
                    .riskLevel("MEDIUM")
                    .assessedAt(LocalDateTime.now())
                    .riskFactors(new ArrayList<>())
                    .build();
                redisTemplate.opsForValue().set(riskKey, risk, 90, TimeUnit.DAYS);
            }
            
            return risk;
            
        } catch (Exception e) {
            log.error("Failed to get baseline risk for user {}", userId, e);
            return UserRiskData.builder()
                .userId(userId.toString())
                .overallRiskScore(0.5)
                .riskLevel("MEDIUM")
                .assessedAt(LocalDateTime.now())
                .riskFactors(new ArrayList<>())
                .build();
        }
    }

    private void updateUserRiskProfile(UUID userId, double riskScore, RiskLevel riskLevel) {
        try {
            String riskKey = USER_RISK_PREFIX + userId.toString();
            UserRiskData risk = getUserBaselineRisk(userId);
            
            // Gradually adjust baseline risk (moving average)
            double adjustedBaseline = (risk.getBaselineRiskScore() * 0.8) + (riskScore * 0.2);
            risk.setBaselineRiskScore(adjustedBaseline);
            risk.setLastUpdated(LocalDateTime.now());
            
            redisTemplate.opsForValue().set(riskKey, risk, 90, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Failed to update risk profile for user {}", userId, e);
        }
    }

    private UserBehaviorProfile getUserBehaviorProfile(UUID userId) {
        try {
            String behaviorKey = USER_BEHAVIOR_PREFIX + userId.toString();
            UserBehaviorProfile profile = (UserBehaviorProfile) redisTemplate.opsForValue().get(behaviorKey);
            
            // Return default profile if none exists
            if (profile == null) {
                log.debug("No behavior profile found for user {}, creating default", userId);
                profile = UserBehaviorProfile.builder()
                    .userId(userId.toString())
                    .profileId(UUID.randomUUID().toString())
                    .profileCreatedAt(LocalDateTime.now())
                    .lastUpdatedAt(LocalDateTime.now())
                    .profileConfidence(0.0)
                    .dataPoints(0)
                    .profileQuality("NEW")
                    .typicalLoginTimes(new ArrayList<>())
                    .commonDevices(new HashSet<>())
                    .commonLocations(new HashSet<>())
                    .averageSessionDuration(0L)
                    .averageTransactionAmount(0.0)
                    .riskFlags(new HashSet<>())
                    .build();
            }
            
            return profile;
        } catch (Exception e) {
            log.error("Failed to get behavior profile for user {}", userId, e);
            // Return minimal default profile on error
            return UserBehaviorProfile.builder()
                .userId(userId.toString())
                .profileId(UUID.randomUUID().toString())
                .profileCreatedAt(LocalDateTime.now())
                .lastUpdatedAt(LocalDateTime.now())
                .profileConfidence(0.0)
                .dataPoints(0)
                .profileQuality("ERROR")
                .build();
        }
    }

    private void updateBehaviorProfile(UUID userId, AuthenticationContext context) {
        try {
            String behaviorKey = USER_BEHAVIOR_PREFIX + userId.toString();
            UserBehaviorProfile profile = getUserBehaviorProfile(userId);
            
            if (profile == null) {
                profile = UserBehaviorProfile.builder()
                    .userId(userId.toString())
                    .profileId(UUID.randomUUID().toString())
                    .profileCreatedAt(LocalDateTime.now())
                    .lastUpdatedAt(LocalDateTime.now())
                    .profileConfidence(0.0)
                    .dataPoints(0)
                    .profileQuality("INSUFFICIENT")
                    .build();
            }
            
            // Update behavior patterns
            profile.addLoginTime(LocalDateTime.now());
            
            if (context.getDeviceFingerprint() != null) {
                profile.addKnownDevice(context.getDeviceFingerprint());
            }
            
            if (context.getLocationData() != null) {
                profile.addKnownCountry(context.getLocationData().getCountryCode());
            }
            
            redisTemplate.opsForValue().set(behaviorKey, profile, 90, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Failed to update behavior profile for user {}", userId, e);
        }
    }

    private boolean isUnusualLoginTime(UserBehaviorProfile behavior, LocalDateTime loginTime) {
        // Check if login time is outside user's normal pattern
        Set<Integer> normalHours = behavior.getNormalLoginHours();
        return !normalHours.isEmpty() && !normalHours.contains(loginTime.getHour());
    }

    private boolean isUnusualLoginFrequency(UserBehaviorProfile behavior, UUID userId) {
        // Check recent login frequency against historical pattern
        try {
            String recentLoginsKey = "recent_logins:" + userId.toString();
            Long recentCount = redisTemplate.opsForList().size(recentLoginsKey);
            
            return recentCount != null && recentCount > behavior.getTypicalDailyLogins() * 3;
        } catch (Exception e) {
            return false;
        }
    }

    private double calculateRiskAdjustment(SecurityTrigger trigger) {
        return switch (trigger.getType()) {
            case SUSPICIOUS_LOGIN -> 0.3;
            case DEVICE_CHANGE -> 0.2;
            case LOCATION_CHANGE -> 0.2;
            case UNUSUAL_ACTIVITY -> 0.4;
            case SECURITY_BREACH -> 0.8;
            case PASSWORD_CHANGE -> -0.1; // Positive security action
            case MFA_SETUP -> -0.2; // Positive security action
        };
    }

    private double getEventRiskScore(String eventType) {
        return switch (eventType) {
            case "FAILED_LOGIN" -> 0.1;
            case "SUSPICIOUS_ACTIVITY" -> 0.3;
            case "SECURITY_VIOLATION" -> 0.5;
            case "FRAUD_ATTEMPT" -> 0.8;
            default -> 0.05;
        };
    }

    private void recordSecurityEvent(UUID userId, String eventType, String details) {
        try {
            String securityEventsKey = SECURITY_EVENTS_PREFIX + userId.toString();
            
            Map<String, Object> event = new HashMap<>();
            event.put("userId", userId.toString());
            event.put("type", eventType);
            event.put("details", details);
            event.put("timestamp", LocalDateTime.now().toString());
            
            redisTemplate.opsForList().leftPush(securityEventsKey, event);
            redisTemplate.opsForList().trim(securityEventsKey, 0, 100); // Keep last 100 events
            redisTemplate.expire(securityEventsKey, 30, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Failed to record security event for user {}", userId, e);
        }
    }

    // Data classes (simplified - would use Lombok @Data/@Builder)
    // UserRiskData, UserBehaviorProfile, SecurityEvent, etc.
}