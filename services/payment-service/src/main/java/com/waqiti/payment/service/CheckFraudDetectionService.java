package com.waqiti.payment.service.check;

import com.waqiti.payment.client.FraudDetectionClient;
import com.waqiti.payment.dto.CheckDepositRequest;
import com.waqiti.payment.dto.CheckFraudAnalysisResult;
import com.waqiti.payment.entity.CheckDeposit;
import com.waqiti.payment.repository.CheckDepositRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Focused service for check fraud detection
 * Extracted from CheckDepositService.java (1,436 LOC)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CheckFraudDetectionService {

    private final FraudDetectionClient fraudDetectionClient;
    private final CheckDepositRepository checkDepositRepository;
    private final CheckDuplicateDetectionService duplicateDetectionService;

    /**
     * Perform comprehensive fraud analysis on check deposit
     */
    public CompletableFuture<CheckFraudAnalysisResult> analyzeFraud(CheckDepositRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Performing fraud analysis for check deposit: {}", request.getDepositId());
                
                CheckFraudAnalysisResult.Builder resultBuilder = CheckFraudAnalysisResult.builder()
                    .depositId(request.getDepositId())
                    .analysisTimestamp(LocalDateTime.now());
                
                // 1. Velocity check - frequency of deposits
                FraudCheckResult velocityCheck = performVelocityCheck(request);
                resultBuilder.velocityCheck(velocityCheck);
                
                // 2. Duplicate check detection
                FraudCheckResult duplicateCheck = duplicateDetectionService.checkForDuplicates(request);
                resultBuilder.duplicateCheck(duplicateCheck);
                
                // 3. Amount pattern analysis
                FraudCheckResult amountPatternCheck = analyzeAmountPatterns(request);
                resultBuilder.amountPatternCheck(amountPatternCheck);
                
                // 4. External fraud API check
                FraudCheckResult externalCheck = performExternalFraudCheck(request);
                resultBuilder.externalCheck(externalCheck);
                
                // 5. Account behavior analysis
                FraudCheckResult behaviorCheck = analyzeBehaviorPatterns(request);
                resultBuilder.behaviorCheck(behaviorCheck);
                
                // 6. Risk scoring
                FraudRiskScore riskScore = calculateRiskScore(
                    velocityCheck, duplicateCheck, amountPatternCheck, 
                    externalCheck, behaviorCheck);
                resultBuilder.riskScore(riskScore);
                
                // 7. Fraud decision
                FraudDecision decision = makeFraudDecision(riskScore);
                resultBuilder.decision(decision);
                
                CheckFraudAnalysisResult result = resultBuilder.build();
                
                log.info("Fraud analysis completed: depositId={}, riskScore={}, decision={}", 
                    request.getDepositId(), riskScore.getScore(), decision);
                
                return result;
                
            } catch (Exception e) {
                log.error("Fraud analysis failed: ", e);
                return CheckFraudAnalysisResult.failed(
                    request.getDepositId(), 
                    "Fraud analysis failed: " + e.getMessage()
                );
            }
        });
    }

    private FraudCheckResult performVelocityCheck(CheckDepositRequest request) {
        try {
            // Check deposits in last 24 hours
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            List<CheckDeposit> recentDeposits = checkDepositRepository
                .findByUserIdAndCreatedAtAfter(request.getUserId(), yesterday);
            
            int depositCount = recentDeposits.size();
            BigDecimal totalAmount = recentDeposits.stream()
                .map(CheckDeposit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Velocity thresholds
            int maxDailyDeposits = 5;
            BigDecimal maxDailyAmount = new BigDecimal("10000.00");
            
            boolean suspicious = depositCount >= maxDailyDeposits || 
                               totalAmount.compareTo(maxDailyAmount) > 0;
            
            Map<String, Object> details = new HashMap<>();
            details.put("depositCount", depositCount);
            details.put("totalAmount", totalAmount);
            details.put("maxDailyDeposits", maxDailyDeposits);
            details.put("maxDailyAmount", maxDailyAmount);
            
            return FraudCheckResult.builder()
                .checkType("VELOCITY_CHECK")
                .passed(!suspicious)
                .riskLevel(suspicious ? RiskLevel.HIGH : RiskLevel.LOW)
                .details(details)
                .message(suspicious ? "High velocity detected" : "Normal velocity")
                .build();
                
        } catch (Exception e) {
            log.error("Velocity check failed: ", e);
            return FraudCheckResult.error("VELOCITY_CHECK", e.getMessage());
        }
    }

    private FraudCheckResult analyzeAmountPatterns(CheckDepositRequest request) {
        try {
            // Get user's deposit history
            List<CheckDeposit> history = checkDepositRepository
                .findByUserIdOrderByCreatedAtDesc(request.getUserId())
                .stream()
                .limit(20)
                .toList();
            
            if (history.isEmpty()) {
                return FraudCheckResult.builder()
                    .checkType("AMOUNT_PATTERN")
                    .passed(true)
                    .riskLevel(RiskLevel.LOW)
                    .message("No deposit history for pattern analysis")
                    .build();
            }
            
            // Calculate statistics
            BigDecimal avgAmount = history.stream()
                .map(CheckDeposit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(history.size()), 2, RoundingMode.HALF_UP);
            
            BigDecimal maxAmount = history.stream()
                .map(CheckDeposit::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
            
            // Check for anomalies
            BigDecimal currentAmount = request.getAmount();
            boolean isAnomalous = currentAmount.compareTo(avgAmount.multiply(new BigDecimal("3"))) > 0 ||
                                currentAmount.compareTo(maxAmount.multiply(new BigDecimal("2"))) > 0;
            
            Map<String, Object> details = new HashMap<>();
            details.put("currentAmount", currentAmount);
            details.put("averageAmount", avgAmount);
            details.put("maxHistoricalAmount", maxAmount);
            details.put("historyCount", history.size());
            
            return FraudCheckResult.builder()
                .checkType("AMOUNT_PATTERN")
                .passed(!isAnomalous)
                .riskLevel(isAnomalous ? RiskLevel.MEDIUM : RiskLevel.LOW)
                .details(details)
                .message(isAnomalous ? "Amount significantly higher than usual" : "Normal amount pattern")
                .build();
                
        } catch (Exception e) {
            log.error("Amount pattern analysis failed: ", e);
            return FraudCheckResult.error("AMOUNT_PATTERN", e.getMessage());
        }
    }

    private FraudCheckResult performExternalFraudCheck(CheckDepositRequest request) {
        try {
            // Call external fraud detection service
            Map<String, Object> fraudRequest = new HashMap<>();
            fraudRequest.put("userId", request.getUserId());
            fraudRequest.put("amount", request.getAmount());
            fraudRequest.put("routingNumber", request.getRoutingNumber());
            fraudRequest.put("accountNumber", request.getAccountNumber());
            fraudRequest.put("checkNumber", request.getCheckNumber());
            
            Map<String, Object> fraudResponse = fraudDetectionClient.checkFraud(fraudRequest);
            
            boolean flagged = (Boolean) fraudResponse.getOrDefault("flagged", false);
            String riskLevelStr = (String) fraudResponse.getOrDefault("riskLevel", "LOW");
            String reason = (String) fraudResponse.getOrDefault("reason", "No issues detected");
            
            return FraudCheckResult.builder()
                .checkType("EXTERNAL_FRAUD_CHECK")
                .passed(!flagged)
                .riskLevel(RiskLevel.valueOf(riskLevelStr))
                .details(fraudResponse)
                .message(reason)
                .build();
                
        } catch (Exception e) {
            log.error("External fraud check failed: ", e);
            return FraudCheckResult.error("EXTERNAL_FRAUD_CHECK", e.getMessage());
        }
    }

    private FraudCheckResult analyzeBehaviorPatterns(CheckDepositRequest request) {
        try {
            Map<String, Object> details = new HashMap<>();
            List<String> suspiciousIndicators = new ArrayList<>();
            
            // Device fingerprinting and tracking
            String deviceId = request.getDeviceId();
            details.put("deviceId", deviceId);
            
            // Check if device has been used for fraudulent transactions
            boolean deviceFlagged = checkDeviceHistory(deviceId);
            if (deviceFlagged) {
                suspiciousIndicators.add("Device previously associated with fraud");
            }
            
            // IP address analysis
            String ipAddress = request.getIpAddress();
            details.put("ipAddress", ipAddress);
            
            // Check for VPN/Proxy usage
            boolean isVpnProxy = detectVpnOrProxy(ipAddress);
            if (isVpnProxy) {
                suspiciousIndicators.add("VPN/Proxy detected");
            }
            
            // Geolocation consistency check
            boolean locationMismatch = checkLocationConsistency(request.getUserId(), ipAddress);
            if (locationMismatch) {
                suspiciousIndicators.add("Location inconsistent with user profile");
            }
            
            // Time pattern analysis
            LocalDateTime timestamp = request.getTimestamp();
            details.put("timestamp", timestamp);
            
            // Check for unusual time patterns (e.g., middle of night deposits)
            boolean unusualTime = isUnusualDepositTime(request.getUserId(), timestamp);
            if (unusualTime) {
                suspiciousIndicators.add("Deposit at unusual time for user");
            }
            
            // Session behavior analysis
            SessionBehavior sessionBehavior = analyzeSessionBehavior(request);
            details.put("sessionBehavior", sessionBehavior);
            
            if (sessionBehavior.isRapidActions()) {
                suspiciousIndicators.add("Unusually rapid actions detected");
            }
            
            if (sessionBehavior.isFirstTimeDevice()) {
                suspiciousIndicators.add("First time using this device");
            }
            
            // User behavior profile comparison
            UserBehaviorScore behaviorScore = calculateUserBehaviorScore(request);
            details.put("behaviorScore", behaviorScore.getScore());
            
            // Determine risk level based on indicators
            RiskLevel riskLevel;
            boolean passed;
            
            if (suspiciousIndicators.size() >= 3 || behaviorScore.getScore() > 0.7) {
                riskLevel = RiskLevel.HIGH;
                passed = false;
            } else if (suspiciousIndicators.size() >= 2 || behaviorScore.getScore() > 0.5) {
                riskLevel = RiskLevel.MEDIUM;
                passed = false;
            } else if (suspiciousIndicators.size() >= 1 || behaviorScore.getScore() > 0.3) {
                riskLevel = RiskLevel.MEDIUM;
                passed = true; // Pass but flag for monitoring
            } else {
                riskLevel = RiskLevel.LOW;
                passed = true;
            }
            
            details.put("suspiciousIndicators", suspiciousIndicators);
            details.put("indicatorCount", suspiciousIndicators.size());
            
            String message = suspiciousIndicators.isEmpty() ? 
                "Behavior analysis passed - normal patterns detected" :
                "Suspicious behavior detected: " + String.join(", ", suspiciousIndicators);
            
            return FraudCheckResult.builder()
                .checkType("BEHAVIOR_ANALYSIS")
                .passed(passed)
                .riskLevel(riskLevel)
                .details(details)
                .message(message)
                .build();
                
        } catch (Exception e) {
            log.error("Behavior analysis failed: ", e);
            // On error, be conservative and flag as medium risk
            return FraudCheckResult.builder()
                .checkType("BEHAVIOR_ANALYSIS")
                .passed(false)
                .riskLevel(RiskLevel.MEDIUM)
                .details(new HashMap<>())
                .message("Behavior analysis error - flagged for review")
                .build();
        }
    }
    
    private boolean checkDeviceHistory(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return false;
        }
        // Check if device has been flagged in fraud database
        return checkDepositRepository.countByDeviceIdAndFraudulent(deviceId, true) > 0;
    }
    
    private boolean detectVpnOrProxy(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }
        // Check against known VPN/Proxy IP ranges
        // In production, this would use a VPN detection service
        return ipAddress.startsWith("10.") || ipAddress.startsWith("172.") || 
               ipAddress.startsWith("192.168.") || ipAddress.contains("vpn") ||
               ipAddress.contains("proxy");
    }
    
    private boolean checkLocationConsistency(String userId, String ipAddress) {
        try {
            // Get user's typical locations from history
            List<String> historicalIps = checkDepositRepository
                .findRecentByUserId(userId, 10)
                .stream()
                .map(CheckDeposit::getIpAddress)
                .filter(ip -> ip != null && !ip.isEmpty())
                .collect(Collectors.toList());
            
            if (historicalIps.isEmpty()) {
                return false; // No history to compare
            }
            
            // Simple check - in production would use GeoIP service
            String ipPrefix = ipAddress.substring(0, ipAddress.lastIndexOf('.'));
            boolean consistent = historicalIps.stream()
                .anyMatch(ip -> ip.startsWith(ipPrefix));
            
            return !consistent;
        } catch (Exception e) {
            log.warn("Location consistency check failed: ", e);
            return false;
        }
    }
    
    private boolean isUnusualDepositTime(String userId, LocalDateTime timestamp) {
        // Check if deposit is during unusual hours for this user
        int hour = timestamp.getHour();
        
        // Get user's typical deposit hours
        List<Integer> typicalHours = checkDepositRepository
            .findRecentByUserId(userId, 20)
            .stream()
            .map(d -> d.getCreatedAt().getHour())
            .collect(Collectors.toList());
        
        if (typicalHours.isEmpty()) {
            // No history, check against general unusual hours (2 AM - 5 AM)
            return hour >= 2 && hour <= 5;
        }
        
        // Calculate average deposit hour
        double avgHour = typicalHours.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(12);
        
        // Check if current hour is significantly different (>6 hours difference)
        return Math.abs(hour - avgHour) > 6;
    }
    
    private SessionBehavior analyzeSessionBehavior(CheckDepositRequest request) {
        SessionBehavior behavior = new SessionBehavior();
        
        // Check if this is the first time this device is being used
        boolean firstTimeDevice = checkDepositRepository
            .countByUserIdAndDeviceId(request.getUserId(), request.getDeviceId()) == 0;
        behavior.setFirstTimeDevice(firstTimeDevice);
        
        // Check for rapid actions (multiple deposits in short time)
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        long recentActions = checkDepositRepository
            .countByUserIdAndCreatedAtAfter(request.getUserId(), fiveMinutesAgo);
        behavior.setRapidActions(recentActions > 1);
        
        return behavior;
    }
    
    private UserBehaviorScore calculateUserBehaviorScore(CheckDepositRequest request) {
        double score = 0.0;
        
        // Factor 1: Device trust (0-0.3)
        if (request.getDeviceId() != null) {
            long deviceUsageCount = checkDepositRepository
                .countByUserIdAndDeviceId(request.getUserId(), request.getDeviceId());
            if (deviceUsageCount == 0) {
                score += 0.3; // New device
            } else if (deviceUsageCount < 5) {
                score += 0.15; // Relatively new device
            }
        }
        
        // Factor 2: Amount deviation (0-0.3)
        BigDecimal avgAmount = checkDepositRepository
            .findAverageAmountByUserId(request.getUserId());
        if (avgAmount != null && avgAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal deviation = request.getAmount().subtract(avgAmount).abs();
            BigDecimal deviationPercent = deviation.divide(avgAmount, 2, RoundingMode.HALF_UP);
            if (deviationPercent.compareTo(new BigDecimal("2")) > 0) {
                score += 0.3; // More than 200% deviation
            } else if (deviationPercent.compareTo(new BigDecimal("1")) > 0) {
                score += 0.15; // More than 100% deviation
            }
        }
        
        // Factor 3: Time pattern deviation (0-0.2)
        if (isUnusualDepositTime(request.getUserId(), request.getTimestamp())) {
            score += 0.2;
        }
        
        // Factor 4: Location change (0-0.2)
        if (checkLocationConsistency(request.getUserId(), request.getIpAddress())) {
            score += 0.2;
        }
        
        return new UserBehaviorScore(Math.min(score, 1.0));
    }
    
    // Inner classes for behavior analysis
    private static class SessionBehavior {
        private boolean firstTimeDevice;
        private boolean rapidActions;
        
        // Getters and setters
        public boolean isFirstTimeDevice() { return firstTimeDevice; }
        public void setFirstTimeDevice(boolean firstTimeDevice) { this.firstTimeDevice = firstTimeDevice; }
        public boolean isRapidActions() { return rapidActions; }
        public void setRapidActions(boolean rapidActions) { this.rapidActions = rapidActions; }
    }
    
    private static class UserBehaviorScore {
        private final double score;
        
        public UserBehaviorScore(double score) {
            this.score = score;
        }
        
        public double getScore() { return score; }
    }

    private FraudRiskScore calculateRiskScore(FraudCheckResult... checks) {
        int totalScore = 0;
        int maxScore = 0;
        
        for (FraudCheckResult check : checks) {
            if (check.isPassed()) {
                totalScore += check.getRiskLevel().getScore();
            } else {
                totalScore += check.getRiskLevel().getScore() * 2; // Penalty for failed checks
            }
            maxScore += RiskLevel.CRITICAL.getScore();
        }
        
        double riskPercentage = (double) totalScore / maxScore * 100;
        
        RiskLevel overallRisk;
        if (riskPercentage > 75) {
            overallRisk = RiskLevel.CRITICAL;
        } else if (riskPercentage > 50) {
            overallRisk = RiskLevel.HIGH;
        } else if (riskPercentage > 25) {
            overallRisk = RiskLevel.MEDIUM;
        } else {
            overallRisk = RiskLevel.LOW;
        }
        
        return FraudRiskScore.builder()
            .score(riskPercentage)
            .level(overallRisk)
            .calculatedAt(LocalDateTime.now())
            .build();
    }

    private FraudDecision makeFraudDecision(FraudRiskScore riskScore) {
        return switch (riskScore.getLevel()) {
            case LOW -> FraudDecision.APPROVE;
            case MEDIUM -> FraudDecision.MANUAL_REVIEW;
            case HIGH, CRITICAL -> FraudDecision.REJECT;
        };
    }
    
    // Enums and supporting classes
    public enum RiskLevel {
        LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);
        
        private final int score;
        
        RiskLevel(int score) {
            this.score = score;
        }
        
        public int getScore() {
            return score;
        }
    }
    
    public enum FraudDecision {
        APPROVE, MANUAL_REVIEW, REJECT
    }
}