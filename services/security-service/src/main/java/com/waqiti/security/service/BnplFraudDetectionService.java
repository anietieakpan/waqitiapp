package com.waqiti.security.service;

import com.waqiti.security.domain.SecurityEvent;
import com.waqiti.security.domain.SecurityEventType;
import com.waqiti.security.domain.SecuritySeverity;
import com.waqiti.security.domain.SecurityAction;
import com.waqiti.security.dto.FraudAnalysisRequest;
import com.waqiti.security.dto.FraudAnalysisResponse;
import com.waqiti.security.dto.BnplFraudAnalysisRequest;
import com.waqiti.security.dto.BnplFraudAnalysisResponse;
import com.waqiti.security.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BNPL-specific Fraud Detection Service
 * Extends the core fraud detection with BNPL-specific rules and risk factors
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BnplFraudDetectionService {

    private final FraudDetectionService coreFraudService;
    private final SecurityEventRepository securityEventRepository;
    private final RiskScoringService riskScoringService;

    // BNPL-specific thresholds
    private static final int MAX_BNPL_APPLICATIONS_PER_DAY = 3;
    private static final int MAX_BNPL_APPLICATIONS_PER_WEEK = 7;
    private static final BigDecimal BNPL_HIGH_VALUE_THRESHOLD = new BigDecimal("2000.00");
    private static final BigDecimal MAX_TOTAL_BNPL_EXPOSURE = new BigDecimal("10000.00");

    /**
     * Comprehensive BNPL application fraud analysis
     */
    public BnplFraudAnalysisResponse analyzeBnplApplication(BnplFraudAnalysisRequest request) {
        log.info("Starting BNPL fraud analysis for user: {} amount: {}", 
                request.getUserId(), request.getPurchaseAmount());

        try {
            // 1. Perform core fraud analysis using existing infrastructure
            FraudAnalysisRequest coreRequest = buildCoreFraudRequest(request);
            FraudAnalysisResponse coreAnalysis = coreFraudService.analyzeTransactionAdvanced(coreRequest);

            // 2. Perform BNPL-specific analysis
            BnplRiskFactors bnplFactors = performBnplSpecificAnalysis(request);

            // 3. Combine core and BNPL scores
            double finalRiskScore = calculateCombinedBnplRiskScore(coreAnalysis, bnplFactors);

            // 4. Create comprehensive BNPL response
            BnplFraudAnalysisResponse response = BnplFraudAnalysisResponse.builder()
                    .applicationId(request.getApplicationId())
                    .userId(request.getUserId())
                    .merchantId(request.getMerchantId())
                    .purchaseAmount(request.getPurchaseAmount())
                    .financedAmount(request.getFinancedAmount())
                    
                    // Core fraud scores
                    .coreRiskScore(coreAnalysis.getRiskScore())
                    .mlScore(coreAnalysis.getMlScore())
                    .velocityScore(coreAnalysis.getVelocityScore())
                    .behavioralScore(coreAnalysis.getBehavioralScore())
                    .geolocationScore(coreAnalysis.getGeolocationScore())
                    .deviceScore(coreAnalysis.getDeviceScore())
                    
                    // BNPL-specific scores
                    .bnplVelocityScore(bnplFactors.getVelocityScore())
                    .creditCorrelationScore(bnplFactors.getCreditCorrelationScore())
                    .merchantRiskScore(bnplFactors.getMerchantRiskScore())
                    .amountPatternScore(bnplFactors.getAmountPatternScore())
                    .applicationPatternScore(bnplFactors.getApplicationPatternScore())
                    
                    // Final assessment
                    .finalRiskScore(finalRiskScore)
                    .riskLevel(determineRiskLevel(finalRiskScore))
                    .recommendedAction(determineRecommendedAction(finalRiskScore, bnplFactors))
                    .confidenceLevel(calculateConfidenceLevel(coreAnalysis, bnplFactors))
                    
                    // Risk factors and recommendations
                    .riskFactors(combineRiskFactors(coreAnalysis.getRiskFactors(), bnplFactors.getRiskFactors()))
                    .recommendations(generateBnplRecommendations(finalRiskScore, bnplFactors))
                    .analysisDetails(generateBnplAnalysisDetails(coreAnalysis, bnplFactors))
                    
                    .timestamp(LocalDateTime.now())
                    .build();

            // 5. Create security event for high-risk applications
            if (finalRiskScore >= 75) {
                createBnplSecurityEvent(request, response, bnplFactors);
            }

            // 6. Log analysis completion
            log.info("BNPL fraud analysis completed for user: {} - Final Risk Score: {} Action: {}", 
                    request.getUserId(), finalRiskScore, response.getRecommendedAction());

            return response;

        } catch (Exception e) {
            log.error("Error in BNPL fraud analysis for user: {}", request.getUserId(), e);
            throw new RuntimeException("BNPL fraud analysis failed", e);
        }
    }

    /**
     * Quick BNPL velocity check for real-time application processing
     */
    public boolean checkBnplVelocityRules(UUID userId, BigDecimal amount) {
        log.debug("Checking BNPL velocity rules for user: {} amount: {}", userId, amount);

        LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);

        // Check daily application limit
        long dailyApplications = securityEventRepository.countBnplApplicationsToday(userId, dayStart);
        if (dailyApplications >= MAX_BNPL_APPLICATIONS_PER_DAY) {
            createBnplVelocityViolation(userId, "Daily BNPL application limit exceeded");
            return false;
        }

        // Check weekly application limit
        long weeklyApplications = securityEventRepository.countBnplApplicationsThisWeek(userId, weekStart);
        if (weeklyApplications >= MAX_BNPL_APPLICATIONS_PER_WEEK) {
            createBnplVelocityViolation(userId, "Weekly BNPL application limit exceeded");
            return false;
        }

        // Check total BNPL exposure
        BigDecimal totalExposure = securityEventRepository.getTotalBnplExposure(userId);
        if (totalExposure != null && totalExposure.add(amount).compareTo(MAX_TOTAL_BNPL_EXPOSURE) > 0) {
            createBnplVelocityViolation(userId, "Maximum BNPL exposure limit exceeded");
            return false;
        }

        return true;
    }

    /**
     * Perform BNPL-specific risk analysis
     */
    private BnplRiskFactors performBnplSpecificAnalysis(BnplFraudAnalysisRequest request) {
        BnplRiskFactors.BnplRiskFactorsBuilder builder = BnplRiskFactors.builder();
        List<String> riskFactors = new ArrayList<>();

        // 1. BNPL Velocity Analysis
        double velocityScore = analyzeBnplVelocity(request.getUserId(), riskFactors);
        builder.velocityScore(velocityScore);

        // 2. Credit Assessment Correlation
        double creditScore = analyzeCreditCorrelation(request, riskFactors);
        builder.creditCorrelationScore(creditScore);

        // 3. Merchant Risk Analysis
        double merchantScore = analyzeMerchantRisk(request.getMerchantId(), riskFactors);
        builder.merchantRiskScore(merchantScore);

        // 4. Amount Pattern Analysis
        double amountScore = analyzeAmountPatterns(request, riskFactors);
        builder.amountPatternScore(amountScore);

        // 5. Application Pattern Analysis
        double patternScore = analyzeApplicationPatterns(request, riskFactors);
        builder.applicationPatternScore(patternScore);

        builder.riskFactors(riskFactors);
        return builder.build();
    }

    private double analyzeBnplVelocity(UUID userId, List<String> riskFactors) {
        double score = 0.0;

        LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        LocalDateTime monthStart = LocalDateTime.now().minusDays(30);

        // Daily velocity
        long dailyApps = securityEventRepository.countBnplApplicationsToday(userId, dayStart);
        if (dailyApps >= 2) {
            score += 25;
            riskFactors.add("HIGH_BNPL_DAILY_VELOCITY: " + dailyApps + " applications today");
        }

        // Weekly velocity
        long weeklyApps = securityEventRepository.countBnplApplicationsThisWeek(userId, weekStart);
        if (weeklyApps >= 5) {
            score += 20;
            riskFactors.add("HIGH_BNPL_WEEKLY_VELOCITY: " + weeklyApps + " applications this week");
        }

        // Monthly velocity
        long monthlyApps = securityEventRepository.countBnplApplicationsThisMonth(userId, monthStart);
        if (monthlyApps >= 10) {
            score += 15;
            riskFactors.add("HIGH_BNPL_MONTHLY_VELOCITY: " + monthlyApps + " applications this month");
        }

        // Time between applications
        LocalDateTime lastApplication = securityEventRepository.getLastBnplApplicationTime(userId);
        if (lastApplication != null && lastApplication.isAfter(LocalDateTime.now().minusHours(1))) {
            score += 30;
            riskFactors.add("RAPID_BNPL_APPLICATIONS: Less than 1 hour since last application");
        }

        return Math.min(score, 100.0);
    }

    private double analyzeCreditCorrelation(BnplFraudAnalysisRequest request, List<String> riskFactors) {
        double score = 0.0;

        // Analyze credit score vs amount requested
        if (request.getCreditScore() != null) {
            if (request.getCreditScore() < 500 && request.getFinancedAmount().compareTo(new BigDecimal("1000")) > 0) {
                score += 40;
                riskFactors.add("HIGH_AMOUNT_LOW_CREDIT: Large amount with low credit score");
            }

            if (request.getCreditScore() < 300) {
                score += 50;
                riskFactors.add("EXTREMELY_LOW_CREDIT_SCORE: Potential synthetic identity");
            }
        }

        // Check for thin credit file
        if (request.isThinCreditFile()) {
            score += 25;
            riskFactors.add("THIN_CREDIT_FILE: Limited credit history");
        }

        // Check credit utilization patterns
        if (request.getCreditUtilization() != null && request.getCreditUtilization() > 0.9) {
            score += 30;
            riskFactors.add("HIGH_CREDIT_UTILIZATION: Over 90% utilization");
        }

        return Math.min(score, 100.0);
    }

    private double analyzeMerchantRisk(String merchantId, List<String> riskFactors) {
        double score = 0.0;

        // Check merchant risk category
        String merchantCategory = getMerchantCategory(merchantId);
        if (isHighRiskMerchantCategory(merchantCategory)) {
            score += 35;
            riskFactors.add("HIGH_RISK_MERCHANT_CATEGORY: " + merchantCategory);
        }

        // Check if new merchant
        if (isNewMerchant(merchantId)) {
            score += 15;
            riskFactors.add("NEW_MERCHANT: Recently onboarded merchant");
        }

        // Check merchant fraud history
        if (hasMerchantFraudHistory(merchantId)) {
            score += 40;
            riskFactors.add("MERCHANT_FRAUD_HISTORY: Previous fraud incidents");
        }

        return Math.min(score, 100.0);
    }

    private double analyzeAmountPatterns(BnplFraudAnalysisRequest request, List<String> riskFactors) {
        double score = 0.0;

        BigDecimal amount = request.getPurchaseAmount();
        BigDecimal financed = request.getFinancedAmount();

        // Check for round amounts (potential testing)
        if (amount.remainder(new BigDecimal("100")).equals(BigDecimal.ZERO) && 
            amount.compareTo(new BigDecimal("500")) >= 0) {
            score += 10;
            riskFactors.add("ROUND_AMOUNT_PATTERN: Suspicious round number");
        }

        // Check for very high down payment (potential money laundering)
        if (request.getDownPayment() != null) {
            BigDecimal downPaymentRatio = request.getDownPayment().divide(amount, 2, java.math.RoundingMode.HALF_UP);
            if (downPaymentRatio.compareTo(new BigDecimal("0.9")) > 0) {
                score += 25;
                riskFactors.add("EXCESSIVE_DOWN_PAYMENT: Over 90% down payment");
            }
        }

        // Check amount vs user's historical pattern
        BigDecimal avgAmount = getAverageBnplAmount(request.getUserId());
        if (avgAmount != null && amount.compareTo(avgAmount.multiply(new BigDecimal("5"))) > 0) {
            score += 20;
            riskFactors.add("AMOUNT_DEVIATION: Significantly higher than usual");
        }

        return Math.min(score, 100.0);
    }

    private double analyzeApplicationPatterns(BnplFraudAnalysisRequest request, List<String> riskFactors) {
        double score = 0.0;

        // Check application timing patterns
        int hour = LocalDateTime.now().getHour();
        if (hour < 6 || hour > 23) {
            score += 10;
            riskFactors.add("UNUSUAL_APPLICATION_TIME: Late night/early morning");
        }

        // Check for weekend applications
        if (LocalDateTime.now().getDayOfWeek().getValue() > 5) {
            score += 5;
            riskFactors.add("WEEKEND_APPLICATION: Applied during weekend");
        }

        // Check device/IP consistency
        if (request.getDeviceFingerprint() != null) {
            long deviceUsage = securityEventRepository.countDeviceUsageAcrossUsers(request.getDeviceFingerprint());
            if (deviceUsage > 10) {
                score += 30;
                riskFactors.add("SHARED_DEVICE: Used by multiple users");
            }
        }

        return Math.min(score, 100.0);
    }

    private double calculateCombinedBnplRiskScore(FraudAnalysisResponse coreAnalysis, BnplRiskFactors bnplFactors) {
        // Weight core fraud score (60%) and BNPL-specific factors (40%)
        double coreWeight = 0.6;
        double bnplWeight = 0.4;

        // Calculate BNPL-specific weighted score
        double bnplScore = (bnplFactors.getVelocityScore() * 0.3) +
                          (bnplFactors.getCreditCorrelationScore() * 0.25) +
                          (bnplFactors.getMerchantRiskScore() * 0.2) +
                          (bnplFactors.getAmountPatternScore() * 0.15) +
                          (bnplFactors.getApplicationPatternScore() * 0.1);

        return (coreAnalysis.getRiskScore() * coreWeight) + (bnplScore * bnplWeight);
    }

    // Helper methods for building requests and responses
    private FraudAnalysisRequest buildCoreFraudRequest(BnplFraudAnalysisRequest request) {
        return FraudAnalysisRequest.builder()
                .transactionId(request.getApplicationId())
                .userId(request.getUserId())
                .amount(request.getPurchaseAmount())
                .ipAddress(request.getIpAddress())
                .deviceFingerprint(request.getDeviceFingerprint())
                .userAgent(request.getUserAgent())
                .transactionType("BNPL_APPLICATION")
                .channelType("MOBILE_APP")
                .merchantId(request.getMerchantId())
                .geolocation(request.getGeolocation())
                .build();
    }

    private void createBnplSecurityEvent(BnplFraudAnalysisRequest request, 
                                       BnplFraudAnalysisResponse response, 
                                       BnplRiskFactors factors) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(request.getUserId())
                .eventType(SecurityEventType.SUSPICIOUS_BNPL_APPLICATION)
                .severity(mapScoreToSeverity(response.getFinalRiskScore()))
                .ipAddress(request.getIpAddress())
                .userAgent(request.getUserAgent())
                .transactionId(request.getApplicationId())
                .amount(request.getPurchaseAmount())
                .riskScore((int) response.getFinalRiskScore())
                .description(generateBnplEventDescription(request, response))
                .actionTaken(mapActionToSecurityAction(response.getRecommendedAction()))
                .resolved(false)
                .metadata(generateEventMetadata(request, factors))
                .build();

        securityEventRepository.save(event);
    }

    private void createBnplVelocityViolation(UUID userId, String description) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .eventType(SecurityEventType.BNPL_VELOCITY_VIOLATION)
                .severity(SecuritySeverity.HIGH)
                .description(description)
                .actionTaken(SecurityAction.APPLICATION_BLOCKED)
                .resolved(false)
                .build();

        securityEventRepository.save(event);
    }

    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 90) return "CRITICAL";
        if (riskScore >= 75) return "HIGH";
        if (riskScore >= 50) return "MEDIUM";
        if (riskScore >= 25) return "LOW";
        return "MINIMAL";
    }

    private String determineRecommendedAction(double riskScore, BnplRiskFactors factors) {
        if (riskScore >= 90) return "BLOCK_APPLICATION";
        if (riskScore >= 75) return "MANUAL_REVIEW_REQUIRED";
        if (riskScore >= 50) return "ENHANCED_VERIFICATION";
        if (riskScore >= 25) return "MONITOR_CLOSELY";
        return "APPROVE";
    }

    private double calculateConfidenceLevel(FraudAnalysisResponse coreAnalysis, BnplRiskFactors factors) {
        // Combine confidence from core analysis with BNPL-specific confidence
        double coreConfidence = coreAnalysis.getConfidenceLevel();
        double bnplConfidence = Math.max(0.1, 1.0 - (factors.getRiskFactors().size() * 0.1));
        return (coreConfidence + bnplConfidence) / 2.0;
    }

    // Placeholder methods for external integrations
    private String getMerchantCategory(String merchantId) { return "RETAIL"; }
    private boolean isHighRiskMerchantCategory(String category) { return false; }
    private boolean isNewMerchant(String merchantId) { return false; }
    private boolean hasMerchantFraudHistory(String merchantId) { return false; }
    private BigDecimal getAverageBnplAmount(UUID userId) { 
        try {
            // Query user's historical BNPL transactions from the last 6 months
            Instant sixMonthsAgo = Instant.now().minus(Duration.ofDays(180));
            
            List<BigDecimal> amounts = transactionRepository
                .findByUserIdAndTypeAndCreatedAtAfter(userId, "BNPL", sixMonthsAgo)
                .stream()
                .map(transaction -> transaction.getAmount())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            if (amounts.isEmpty()) {
                // No historical data, return default average
                return new BigDecimal("250.00");
            }
            
            // Calculate average
            BigDecimal sum = amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return sum.divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.warn("Failed to calculate average BNPL amount for user {}: {}", userId, e.getMessage());
            // Return conservative default on error
            return new BigDecimal("200.00");
        }
    }

    private SecuritySeverity mapScoreToSeverity(double score) {
        if (score >= 90) return SecuritySeverity.CRITICAL;
        if (score >= 75) return SecuritySeverity.HIGH;
        if (score >= 50) return SecuritySeverity.MEDIUM;
        return SecuritySeverity.LOW;
    }

    private SecurityAction mapActionToSecurityAction(String action) {
        switch (action) {
            case "BLOCK_APPLICATION": return SecurityAction.APPLICATION_BLOCKED;
            case "MANUAL_REVIEW_REQUIRED": return SecurityAction.MANUAL_REVIEW_REQUIRED;
            case "ENHANCED_VERIFICATION": return SecurityAction.ADDITIONAL_VERIFICATION_REQUIRED;
            default: return SecurityAction.LOG_ONLY;
        }
    }

    private List<String> combineRiskFactors(List<String> coreFactors, List<String> bnplFactors) {
        List<String> combined = new ArrayList<>(coreFactors);
        combined.addAll(bnplFactors);
        return combined;
    }

    private List<String> generateBnplRecommendations(double riskScore, BnplRiskFactors factors) {
        List<String> recommendations = new ArrayList<>();
        
        if (riskScore >= 90) {
            recommendations.add("Block application immediately");
            recommendations.add("Flag user account for investigation");
        } else if (riskScore >= 75) {
            recommendations.add("Hold application for manual review");
            recommendations.add("Verify user identity and employment");
        } else if (riskScore >= 50) {
            recommendations.add("Require additional documentation");
            recommendations.add("Lower initial credit limit");
        }
        
        return recommendations;
    }

    private String generateBnplAnalysisDetails(FraudAnalysisResponse coreAnalysis, BnplRiskFactors factors) {
        return String.format("Core: %.1f, BNPL Velocity: %.1f, Credit: %.1f, Merchant: %.1f, Amount: %.1f, Pattern: %.1f",
                coreAnalysis.getRiskScore(), factors.getVelocityScore(), factors.getCreditCorrelationScore(),
                factors.getMerchantRiskScore(), factors.getAmountPatternScore(), factors.getApplicationPatternScore());
    }

    private String generateBnplEventDescription(BnplFraudAnalysisRequest request, BnplFraudAnalysisResponse response) {
        return String.format("High-risk BNPL application detected (Score: %.1f) for user %s, amount: $%s",
                response.getFinalRiskScore(), request.getUserId(), request.getPurchaseAmount());
    }

    private String generateEventMetadata(BnplFraudAnalysisRequest request, BnplRiskFactors factors) {
        return String.format("merchantId=%s,financedAmount=%s,creditScore=%s,factors=%d",
                request.getMerchantId(), request.getFinancedAmount(), 
                request.getCreditScore(), factors.getRiskFactors().size());
    }

    @lombok.Data
    @lombok.Builder
    private static class BnplRiskFactors {
        private double velocityScore;
        private double creditCorrelationScore;
        private double merchantRiskScore;
        private double amountPatternScore;
        private double applicationPatternScore;
        private List<String> riskFactors;
    }
}