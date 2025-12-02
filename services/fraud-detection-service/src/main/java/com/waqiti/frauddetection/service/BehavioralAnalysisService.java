package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.entity.UserBehaviorPattern;
import com.waqiti.frauddetection.repository.UserBehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Service for behavioral analysis fraud detection
 * Analyzes user behavior patterns to detect anomalies
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BehavioralAnalysisService {

    @Lazy
    private final BehavioralAnalysisService self;

    private final UserBehaviorRepository behaviorRepository;
    
    // Scoring weights
    private static final double TIMING_WEIGHT = 0.25;
    private static final double AMOUNT_WEIGHT = 0.30;
    private static final double FREQUENCY_WEIGHT = 0.20;
    private static final double DEVICE_WEIGHT = 0.15;
    private static final double LOCATION_WEIGHT = 0.10;
    
    /**
     * Analyze user behavior patterns for fraud detection
     */
    public double analyzeBehavior(FraudCheckRequest request) {
        log.debug("Analyzing behavior for user: {}, transaction: {}", 
            request.getUserId(), request.getTransactionId());
        
        try {
            // Get user's historical behavior patterns
            UserBehaviorPattern userPattern = self.getUserBehaviorPattern(request.getUserId());
            
            if (userPattern == null) {
                log.info("No behavior pattern found for new user: {}", request.getUserId());
                return calculateNewUserRiskScore(request);
            }
            
            double totalScore = 0.0;
            
            // Analyze timing patterns
            double timingScore = analyzeTimingAnomaly(request, userPattern);
            totalScore += timingScore * TIMING_WEIGHT;
            
            // Analyze transaction amounts
            double amountScore = analyzeAmountAnomaly(request, userPattern);
            totalScore += amountScore * AMOUNT_WEIGHT;
            
            // Analyze frequency patterns
            double frequencyScore = analyzeFrequencyAnomaly(request, userPattern);
            totalScore += frequencyScore * FREQUENCY_WEIGHT;
            
            // Analyze device patterns
            double deviceScore = analyzeDeviceAnomaly(request, userPattern);
            totalScore += deviceScore * DEVICE_WEIGHT;
            
            // Analyze location patterns
            double locationScore = analyzeLocationAnomaly(request, userPattern);
            totalScore += locationScore * LOCATION_WEIGHT;
            
            // Normalize score to 0-100
            double finalScore = Math.min(100.0, Math.max(0.0, totalScore));
            
            log.info("Behavioral analysis for user {}: timing={}, amount={}, frequency={}, device={}, location={}, final={}",
                request.getUserId(), timingScore, amountScore, frequencyScore, deviceScore, locationScore, finalScore);
            
            return finalScore;
            
        } catch (Exception e) {
            log.error("Error analyzing behavior for user: {}", request.getUserId(), e);
            return 50.0; // Default medium risk score
        }
    }
    
    /**
     * Check if behavior is anomalous
     */
    public boolean isAnomalousBehavior(FraudCheckRequest request) {
        double behaviorScore = analyzeBehavior(request);
        return behaviorScore > 60.0; // Threshold for anomalous behavior
    }
    
    /**
     * Analyze timing patterns for anomalies
     */
    private double analyzeTimingAnomaly(FraudCheckRequest request, UserBehaviorPattern pattern) {
        LocalTime transactionTime = request.getTransactionTime().toLocalTime();
        
        // Check if transaction is outside user's normal hours
        if (isOutsideNormalHours(transactionTime, pattern)) {
            log.debug("Transaction outside normal hours for user: {}", request.getUserId());
            return 70.0;
        }
        
        // Check if transaction is too soon after last transaction
        if (request.getLastTransactionTime() != null) {
            Duration timeSinceLastTransaction = Duration.between(
                request.getLastTransactionTime(), request.getTransactionTime());
            
            if (timeSinceLastTransaction.toMinutes() < 1) {
                log.debug("Transaction too soon after last transaction for user: {}", request.getUserId());
                return 80.0;
            }
        }
        
        // Check weekend/holiday patterns
        if (Boolean.TRUE.equals(request.getIsWeekend()) && !pattern.isWeekendActive()) {
            return 40.0;
        }
        
        return 10.0; // Normal timing pattern
    }
    
    /**
     * Analyze transaction amount patterns
     */
    private double analyzeAmountAnomaly(FraudCheckRequest request, UserBehaviorPattern pattern) {
        BigDecimal transactionAmount = request.getAmount();
        
        // Check if amount is significantly higher than usual
        if (transactionAmount.compareTo(pattern.getTypicalMaxAmount().multiply(BigDecimal.valueOf(3))) > 0) {
            log.debug("Transaction amount significantly higher than usual for user: {}", request.getUserId());
            return 90.0;
        }
        
        if (transactionAmount.compareTo(pattern.getTypicalMaxAmount().multiply(BigDecimal.valueOf(2))) > 0) {
            return 60.0;
        }
        
        // Check if amount is unusually low (could indicate testing)
        if (transactionAmount.compareTo(BigDecimal.ONE) < 0) {
            return 30.0;
        }
        
        // Check if amount matches typical fraudulent amounts
        if (isSuspiciousAmount(transactionAmount)) {
            return 50.0;
        }
        
        return 15.0; // Normal amount pattern
    }
    
    /**
     * Analyze transaction frequency patterns
     */
    private double analyzeFrequencyAnomaly(FraudCheckRequest request, UserBehaviorPattern pattern) {
        // Check velocity against user's normal patterns
        if (request.getTransactionCountLast24h() != null) {
            int normalDailyCount = pattern.getTypicalDailyTransactionCount();
            
            if (request.getTransactionCountLast24h() > normalDailyCount * 5) {
                log.debug("Transaction frequency significantly higher than usual for user: {}", request.getUserId());
                return 85.0;
            }
            
            if (request.getTransactionCountLast24h() > normalDailyCount * 3) {
                return 65.0;
            }
        }
        
        return 20.0; // Normal frequency pattern
    }
    
    /**
     * Analyze device patterns
     */
    private double analyzeDeviceAnomaly(FraudCheckRequest request, UserBehaviorPattern pattern) {
        if (Boolean.TRUE.equals(request.getIsNewDevice())) {
            // New device is always suspicious
            if (!pattern.isMultiDeviceUser()) {
                log.debug("New device for single-device user: {}", request.getUserId());
                return 75.0;
            }
            return 45.0;
        }
        
        // Check device fingerprint consistency
        if (request.getDeviceFingerprint() != null && 
            !request.getDeviceFingerprint().equals(pattern.getCommonDeviceFingerprint())) {
            return 35.0;
        }
        
        return 10.0; // Normal device pattern
    }
    
    /**
     * Analyze location patterns
     */
    private double analyzeLocationAnomaly(FraudCheckRequest request, UserBehaviorPattern pattern) {
        if (Boolean.TRUE.equals(request.getIsNewLocation())) {
            // Check distance from usual locations
            if (request.getLatitude() != null && request.getLongitude() != null) {
                double distance = calculateDistance(
                    request.getLatitude(), request.getLongitude(),
                    pattern.getTypicalLatitude(), pattern.getTypicalLongitude());
                
                if (distance > 1000) { // More than 1000 km
                    log.debug("Transaction from distant location for user: {}", request.getUserId());
                    return 70.0;
                }
                
                if (distance > 100) { // More than 100 km
                    return 40.0;
                }
            }
        }
        
        return 10.0; // Normal location pattern
    }
    
    /**
     * Calculate risk score for new users
     */
    private double calculateNewUserRiskScore(FraudCheckRequest request) {
        double score = 30.0; // Base score for new users
        
        // Higher risk for high-value first transactions
        if (request.isHighValueTransaction(BigDecimal.valueOf(1000))) {
            score += 40.0;
        } else if (request.isHighValueTransaction(BigDecimal.valueOf(100))) {
            score += 20.0;
        }
        
        // Higher risk for new devices
        if (Boolean.TRUE.equals(request.getIsNewDevice())) {
            score += 15.0;
        }
        
        // Higher risk for suspicious locations
        if (Boolean.TRUE.equals(request.getIsNewLocation())) {
            score += 10.0;
        }
        
        return Math.min(100.0, score);
    }
    
    /**
     * Get user behavior pattern from repository
     */
    @Cacheable(value = "userBehaviorPatterns", key = "#userId")
    public UserBehaviorPattern getUserBehaviorPattern(UUID userId) {
        if (userId == null) {
            log.warn("Null user ID provided for behavior pattern lookup");
            return createSafeDefaultBehaviorPattern(null);
        }
        
        try {
            Optional<UserBehaviorPattern> pattern = behaviorRepository.findByUserId(userId);
            if (pattern.isPresent()) {
                return pattern.get();
            } else {
                log.debug("No behavior pattern found for user: {} - this is normal for new users", userId);
                return null; // Returning null here is acceptable for new users
            }
        } catch (Exception e) {
            log.error("Error fetching behavior pattern for user: {} - using safe default", userId, e);
            return createSafeDefaultBehaviorPattern(userId);
        }
    }
    
    /**
     * Check if transaction is outside user's normal hours
     */
    private boolean isOutsideNormalHours(LocalTime transactionTime, UserBehaviorPattern pattern) {
        if (pattern.getTypicalStartHour() == null || pattern.getTypicalEndHour() == null) {
            return false;
        }
        
        LocalTime startTime = LocalTime.of(pattern.getTypicalStartHour(), 0);
        LocalTime endTime = LocalTime.of(pattern.getTypicalEndHour(), 0);
        
        return transactionTime.isBefore(startTime) || transactionTime.isAfter(endTime);
    }
    
    /**
     * Check if amount matches suspicious patterns
     */
    private boolean isSuspiciousAmount(BigDecimal amount) {
        // Common test amounts used by fraudsters
        BigDecimal[] suspiciousAmounts = {
            new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("10.00"),
            new BigDecimal("99.99"), new BigDecimal("100.00"), new BigDecimal("500.00")
        };
        
        for (BigDecimal suspiciousAmount : suspiciousAmounts) {
            if (amount.compareTo(suspiciousAmount) == 0) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculate distance between two coordinates in kilometers
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371; // Earth's radius in kilometers
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
    
    /**
     * Update user behavior pattern after successful transaction
     */
    public void updateBehaviorPattern(FraudCheckRequest request) {
        try {
            UserBehaviorPattern pattern = self.getUserBehaviorPattern(request.getUserId());
            
            if (pattern == null) {
                // Create new pattern for new user
                pattern = createNewBehaviorPattern(request);
            } else {
                // Update existing pattern
                updateExistingBehaviorPattern(pattern, request);
            }
            
            behaviorRepository.save(pattern);
            
        } catch (Exception e) {
            log.error("Error updating behavior pattern for user: {}", request.getUserId(), e);
        }
    }
    
    private UserBehaviorPattern createNewBehaviorPattern(FraudCheckRequest request) {
        return UserBehaviorPattern.builder()
            .userId(request.getUserId())
            .typicalStartHour(request.getTransactionTime().getHour())
            .typicalEndHour(request.getTransactionTime().getHour())
            .typicalMaxAmount(request.getAmount())
            .typicalDailyTransactionCount(1)
            .typicalLatitude(request.getLatitude())
            .typicalLongitude(request.getLongitude())
            .commonDeviceFingerprint(request.getDeviceFingerprint())
            .weekendActive(Boolean.TRUE.equals(request.getIsWeekend()))
            .multiDeviceUser(false)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    private void updateExistingBehaviorPattern(UserBehaviorPattern pattern, FraudCheckRequest request) {
        // Update patterns with weighted averages and new data
        // This is a simplified implementation - in production, use more sophisticated ML algorithms
        
        if (request.getAmount().compareTo(pattern.getTypicalMaxAmount()) > 0) {
            pattern.setTypicalMaxAmount(request.getAmount());
        }
        
        // Update device usage
        if (Boolean.TRUE.equals(request.getIsNewDevice())) {
            pattern.setMultiDeviceUser(true);
        }
        
        // Update location if significantly different
        if (request.getLatitude() != null && request.getLongitude() != null) {
            double distance = calculateDistance(
                request.getLatitude(), request.getLongitude(),
                pattern.getTypicalLatitude(), pattern.getTypicalLongitude());
            
            if (distance > 50) { // Update if more than 50km away
                pattern.setTypicalLatitude(request.getLatitude());
                pattern.setTypicalLongitude(request.getLongitude());
            }
        }
        
        pattern.setLastUpdated(LocalDateTime.now());
    }
    
    /**
     * Create a safe default behavior pattern when database access fails
     */
    private UserBehaviorPattern createSafeDefaultBehaviorPattern(UUID userId) {
        log.info("Creating safe default behavior pattern for user: {}", userId);
        
        return UserBehaviorPattern.builder()
            .userId(userId)
            .typicalStartHour(6) // Conservative early start
            .typicalEndHour(23) // Conservative late end
            .typicalMaxAmount(BigDecimal.valueOf(1000)) // Conservative max amount
            .typicalDailyTransactionCount(10) // Conservative daily count
            .typicalLatitude(0.0) // Neutral location
            .typicalLongitude(0.0) // Neutral location
            .commonDeviceFingerprint("unknown") // Unknown device
            .weekendActive(true) // Assume active on weekends (more permissive)
            .multiDeviceUser(true) // Assume multi-device user (more permissive)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * Get behavior pattern with fallback for critical security decisions
     */
    public UserBehaviorPattern getBehaviorPatternWithFallback(UUID userId) {
        UserBehaviorPattern pattern = self.getUserBehaviorPattern(userId);
        if (pattern == null) {
            log.debug("No behavior pattern found for user: {}, using conservative default", userId);
            return createSafeDefaultBehaviorPattern(userId);
        }
        return pattern;
    }
}