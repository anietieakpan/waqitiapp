/**
 * Crypto Fraud Detection Service
 * Advanced fraud detection specifically designed for cryptocurrency transactions
 */
package com.waqiti.crypto.security;

import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.*;
import com.waqiti.crypto.repository.*;
import com.waqiti.crypto.blockchain.AddressAnalysisService;
import com.waqiti.crypto.compliance.SanctionsScreeningService;
import com.waqiti.security.ml.FraudMLEngine;
import com.waqiti.security.behavioral.BehavioralAnalysisEngine;
import com.waqiti.security.velocity.VelocityCheckEngine;
import com.waqiti.common.events.CryptoFraudAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CryptoFraudDetectionService {

    private final AddressAnalysisService addressAnalysisService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final FraudMLEngine fraudMLEngine;
    private final BehavioralAnalysisEngine behavioralAnalysisEngine;
    private final VelocityCheckEngine velocityCheckEngine;
    private final CryptoFraudEventRepository fraudEventRepository;
    private final CryptoTransactionPatternRepository patternRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Risk scoring weights for crypto-specific factors
    private static final double ADDRESS_RISK_WEIGHT = 0.40;
    private static final double BEHAVIORAL_RISK_WEIGHT = 0.25;
    private static final double VELOCITY_RISK_WEIGHT = 0.15;
    private static final double PATTERN_RISK_WEIGHT = 0.10;
    private static final double AMOUNT_RISK_WEIGHT = 0.10;

    /**
     * Comprehensive fraud assessment for cryptocurrency transactions
     */
    public FraudAssessment assessCryptoTransaction(FraudAnalysisRequest request) {
        log.info("Starting crypto fraud assessment for transaction: {} user: {} currency: {}", 
                request.getTransactionId(), request.getUserId(), request.getCurrency());
        
        try {
            // 1. Address Risk Analysis (40% weight) - Most critical for crypto
            double addressRisk = analyzeDestinationAddress(request.getToAddress(), request.getCurrency());
            
            // 2. Behavioral Analysis (25% weight)
            double behavioralRisk = analyzeBehavioralPatterns(request);
            
            // 3. Velocity Analysis (15% weight)
            double velocityRisk = analyzeTransactionVelocity(request);
            
            // 4. Pattern Analysis (10% weight)
            double patternRisk = analyzeTransactionPatterns(request);
            
            // 5. Amount Analysis (10% weight)
            double amountRisk = analyzeAmountRisk(request);
            
            // Calculate weighted overall risk score
            double overallRisk = (addressRisk * ADDRESS_RISK_WEIGHT) +
                               (behavioralRisk * BEHAVIORAL_RISK_WEIGHT) +
                               (velocityRisk * VELOCITY_RISK_WEIGHT) +
                               (patternRisk * PATTERN_RISK_WEIGHT) +
                               (amountRisk * AMOUNT_RISK_WEIGHT);
            
            // Create fraud score
            FraudScore fraudScore = FraudScore.builder()
                .overallScore(overallRisk)
                .addressRiskScore(addressRisk)
                .behavioralScore(behavioralRisk)
                .velocityScore(velocityRisk)
                .patternScore(patternRisk)
                .amountScore(amountRisk)
                .confidenceLevel(calculateConfidenceLevel(addressRisk, behavioralRisk, velocityRisk))
                .analysisDetails(generateAnalysisDetails(addressRisk, behavioralRisk, velocityRisk, patternRisk, amountRisk))
                .riskFactors(identifyRiskFactors(request, addressRisk, behavioralRisk, velocityRisk, patternRisk, amountRisk))
                .recommendations(generateRecommendations(overallRisk))
                .build();
            
            // Determine risk level and recommended action
            RiskLevel riskLevel = determineRiskLevel(overallRisk);
            RecommendedAction recommendedAction = determineRecommendedAction(overallRisk, fraudScore.getConfidenceLevel());
            
            // Create fraud assessment
            FraudAssessment assessment = FraudAssessment.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .fraudScore(fraudScore)
                .riskLevel(riskLevel)
                .recommendedAction(recommendedAction)
                .assessmentTimestamp(LocalDateTime.now())
                .build();
            
            // Log fraud event
            logFraudEvent(assessment, request);
            
            // Create alert if high risk
            if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
                createFraudAlert(assessment, request);
            }
            
            log.info("Crypto fraud assessment completed: {} risk score: {} level: {}", 
                    request.getTransactionId(), overallRisk, riskLevel);
            
            return assessment;
            
        } catch (Exception e) {
            log.error("Error in crypto fraud assessment for transaction: {}", request.getTransactionId(), e);
            throw new FraudAssessmentException("Fraud assessment failed", e);
        }
    }

    /**
     * Analyze destination address for risk factors
     */
    private double analyzeDestinationAddress(String address, CryptoCurrency currency) {
        log.debug("Analyzing destination address: {} for currency: {}", address, currency);
        
        double riskScore = 0.0;
        
        try {
            // 1. Sanctions screening (highest priority)
            if (sanctionsScreeningService.isSanctionedAddress(address, currency)) {
                log.warn("Sanctioned address detected: {}", address);
                return 100.0; // Maximum risk
            }
            
            // 2. Address analysis
            AddressRiskProfile addressProfile = addressAnalysisService.analyzeAddress(address, currency);
            
            // Known exchange addresses (lower risk)
            if (addressProfile.isKnownExchange()) {
                riskScore += 5.0;
                log.debug("Address is known exchange: {}", address);
            }
            
            // Mixing/tumbler services (very high risk)
            if (addressProfile.isMixer()) {
                riskScore += 85.0;
                log.warn("Mixer/tumbler address detected: {}", address);
            }
            
            // Dark market addresses (very high risk)
            if (addressProfile.isDarkMarket()) {
                riskScore += 90.0;
                log.warn("Dark market address detected: {}", address);
            }
            
            // Gambling sites (high risk)
            if (addressProfile.isGambling()) {
                riskScore += 65.0;
                log.debug("Gambling address detected: {}", address);
            }
            
            // Ransomware addresses (maximum risk)
            if (addressProfile.isRansomware()) {
                riskScore += 95.0;
                log.error("Ransomware address detected: {}", address);
            }
            
            // New/unverified addresses (medium risk)
            if (addressProfile.isNewAddress()) {
                riskScore += 35.0;
                log.debug("New/unverified address: {}", address);
            }
            
            // High-frequency addresses (potential money laundering)
            if (addressProfile.isHighFrequency()) {
                riskScore += 40.0;
                log.debug("High-frequency address detected: {}", address);
            }
            
            // Addresses with risky connections
            if (addressProfile.hasRiskyConnections()) {
                riskScore += 50.0;
                log.debug("Address has risky connections: {}", address);
            }
            
            // Privacy coins interaction (medium risk)
            if (addressProfile.hasPrivacyCoinInteraction()) {
                riskScore += 30.0;
                log.debug("Privacy coin interaction detected: {}", address);
            }
            
            // Geographic risk (high-risk jurisdictions)
            if (addressProfile.isHighRiskJurisdiction()) {
                riskScore += 25.0;
                log.debug("High-risk jurisdiction address: {}", address);
            }
            
        } catch (Exception e) {
            log.error("Error analyzing address: {}", address, e);
            riskScore += 20.0; // Add risk for analysis failure
        }
        
        return Math.min(riskScore, 100.0);
    }

    /**
     * Analyze behavioral patterns specific to crypto transactions
     */
    private double analyzeBehavioralPatterns(FraudAnalysisRequest request) {
        log.debug("Analyzing behavioral patterns for user: {}", request.getUserId());
        
        double riskScore = 0.0;
        
        try {
            // Get user's crypto transaction history
            CryptoBehaviorProfile behaviorProfile = behavioralAnalysisEngine.getCryptoBehaviorProfile(request.getUserId());
            
            // 1. Unusual transaction timing
            if (isUnusualTransactionTime(request.getTimestamp())) {
                riskScore += 15.0;
                log.debug("Unusual transaction time detected for user: {}", request.getUserId());
            }
            
            // 2. Deviation from normal transaction amounts
            double amountDeviation = calculateAmountDeviation(request.getAmount(), behaviorProfile.getAverageTransactionAmount());
            if (amountDeviation > 5.0) { // 5x normal amount
                riskScore += 25.0;
                log.debug("Large amount deviation detected: {}x normal", amountDeviation);
            }
            
            // 3. New cryptocurrency usage
            if (behaviorProfile.isNewCurrency(request.getCurrency())) {
                riskScore += 20.0;
                log.debug("First time using currency: {} for user: {}", request.getCurrency(), request.getUserId());
            }
            
            // 4. Rapid successive transactions
            if (behaviorProfile.hasRapidSuccessiveTransactions()) {
                riskScore += 30.0;
                log.debug("Rapid successive transactions detected for user: {}", request.getUserId());
            }
            
            // 5. Geographic anomaly (if available)
            if (request.getIpAddress() != null && isGeographicAnomaly(request.getUserId(), request.getIpAddress())) {
                riskScore += 35.0;
                log.debug("Geographic anomaly detected for user: {}", request.getUserId());
            }
            
            // 6. Device fingerprint change
            if (request.getDeviceFingerprint() != null && isNewDevice(request.getUserId(), request.getDeviceFingerprint())) {
                riskScore += 25.0;
                log.debug("New device detected for user: {}", request.getUserId());
            }
            
            // 7. Unusual currency pair conversion
            if (isUnusualCurrencyConversion(request, behaviorProfile)) {
                riskScore += 15.0;
                log.debug("Unusual currency conversion pattern for user: {}", request.getUserId());
            }
            
        } catch (Exception e) {
            log.error("Error analyzing behavioral patterns for user: {}", request.getUserId(), e);
            riskScore += 10.0; // Add risk for analysis failure
        }
        
        return Math.min(riskScore, 100.0);
    }

    /**
     * Analyze transaction velocity for suspicious patterns
     */
    private double analyzeTransactionVelocity(FraudAnalysisRequest request) {
        log.debug("Analyzing transaction velocity for user: {}", request.getUserId());
        
        double riskScore = 0.0;
        
        try {
            VelocityCheckResult velocityResult = velocityCheckEngine.performCryptoVelocityCheck(
                request.getUserId(),
                request.getAmount(),
                request.getCurrency(),
                TimeWindow.HOURLY
            );
            
            // Check hourly limits
            if (velocityResult.isHourlyLimitExceeded()) {
                riskScore += 40.0;
                log.debug("Hourly velocity limit exceeded for user: {}", request.getUserId());
            }
            
            // Check daily limits
            if (velocityResult.isDailyLimitExceeded()) {
                riskScore += 60.0;
                log.debug("Daily velocity limit exceeded for user: {}", request.getUserId());
            }
            
            // Check transaction count velocity
            if (velocityResult.isTransactionCountExceeded()) {
                riskScore += 35.0;
                log.debug("Transaction count velocity exceeded for user: {}", request.getUserId());
            }
            
            // Check for velocity spikes
            double velocitySpike = velocityResult.getVelocitySpike();
            if (velocitySpike > 5.0) { // 5x normal velocity
                riskScore += 50.0;
                log.debug("Velocity spike detected: {}x normal for user: {}", velocitySpike, request.getUserId());
            }
            
        } catch (Exception e) {
            log.error("Error analyzing transaction velocity for user: {}", request.getUserId(), e);
            riskScore += 15.0; // Add risk for analysis failure
        }
        
        return Math.min(riskScore, 100.0);
    }

    /**
     * Analyze transaction patterns for suspicious behavior
     */
    private double analyzeTransactionPatterns(FraudAnalysisRequest request) {
        log.debug("Analyzing transaction patterns for user: {}", request.getUserId());
        
        double riskScore = 0.0;
        
        try {
            // 1. Round amount analysis (potential structuring)
            if (isRoundAmount(request.getAmount())) {
                riskScore += 15.0;
                log.debug("Round amount detected: {}", request.getAmount());
            }
            
            // 2. Just-under-threshold amounts (limit avoidance)
            if (isJustUnderThreshold(request.getAmount())) {
                riskScore += 35.0;
                log.debug("Just-under-threshold amount detected: {}", request.getAmount());
            }
            
            // 3. Sequential transaction pattern
            if (hasSequentialTransactionPattern(request.getUserId())) {
                riskScore += 25.0;
                log.debug("Sequential transaction pattern detected for user: {}", request.getUserId());
            }
            
            // 4. Split transaction pattern (structuring)
            if (hasSplitTransactionPattern(request.getUserId(), request.getAmount())) {
                riskScore += 45.0;
                log.debug("Split transaction pattern detected for user: {}", request.getUserId());
            }
            
            // 5. Circular transaction pattern
            if (hasCircularTransactionPattern(request.getUserId(), request.getToAddress())) {
                riskScore += 40.0;
                log.debug("Circular transaction pattern detected for user: {}", request.getUserId());
            }
            
        } catch (Exception e) {
            log.error("Error analyzing transaction patterns for user: {}", request.getUserId(), e);
            riskScore += 10.0; // Add risk for analysis failure
        }
        
        return Math.min(riskScore, 100.0);
    }

    /**
     * Analyze amount-based risk factors
     */
    private double analyzeAmountRisk(FraudAnalysisRequest request) {
        log.debug("Analyzing amount risk for amount: {} currency: {}", request.getAmount(), request.getCurrency());
        
        double riskScore = 0.0;
        
        try {
            BigDecimal amount = request.getAmount();
            
            // 1. Very large amounts (potential money laundering)
            BigDecimal largeAmountThreshold = getLargeAmountThreshold(request.getCurrency());
            if (amount.compareTo(largeAmountThreshold) > 0) {
                riskScore += 30.0;
                log.debug("Large amount detected: {} > {}", amount, largeAmountThreshold);
            }
            
            // 2. Very small amounts (potential dusting attack)
            BigDecimal dustThreshold = getDustThreshold(request.getCurrency());
            if (amount.compareTo(dustThreshold) < 0) {
                riskScore += 20.0;
                log.debug("Dust amount detected: {} < {}", amount, dustThreshold);
            }
            
            // 3. Suspicious precision (too many decimal places)
            if (hasSuspiciousPrecision(amount)) {
                riskScore += 10.0;
                log.debug("Suspicious precision detected: {}", amount);
            }
            
        } catch (Exception e) {
            log.error("Error analyzing amount risk for amount: {}", request.getAmount(), e);
            riskScore += 5.0; // Add risk for analysis failure
        }
        
        return Math.min(riskScore, 100.0);
    }

    // Helper methods for risk calculations
    
    private double calculateConfidenceLevel(double addressRisk, double behavioralRisk, double velocityRisk) {
        // Confidence based on data availability and consistency
        double[] scores = {addressRisk, behavioralRisk, velocityRisk};
        double mean = Arrays.stream(scores).average().orElse(0.0);
        double variance = Arrays.stream(scores)
                .map(score -> Math.pow(score - mean, 2))
                .average().orElse(0.0);
        
        // Lower variance = higher confidence
        return Math.max(0.1, 1.0 - (variance / 100.0));
    }
    
    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 80) return RiskLevel.CRITICAL;
        if (riskScore >= 60) return RiskLevel.HIGH;
        if (riskScore >= 40) return RiskLevel.MEDIUM;
        if (riskScore >= 20) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    private RecommendedAction determineRecommendedAction(double riskScore, double confidenceLevel) {
        if (riskScore >= 80 && confidenceLevel >= 0.7) return RecommendedAction.BLOCK;
        if (riskScore >= 60) return RecommendedAction.MANUAL_REVIEW;
        if (riskScore >= 40) return RecommendedAction.ADDITIONAL_VERIFICATION;
        if (riskScore >= 20) return RecommendedAction.MONITOR;
        return RecommendedAction.ALLOW;
    }
    
    private String generateAnalysisDetails(double addressRisk, double behavioralRisk, double velocityRisk, 
                                         double patternRisk, double amountRisk) {
        return String.format("Address: %.1f, Behavioral: %.1f, Velocity: %.1f, Pattern: %.1f, Amount: %.1f",
                addressRisk, behavioralRisk, velocityRisk, patternRisk, amountRisk);
    }
    
    private List<String> identifyRiskFactors(FraudAnalysisRequest request, double addressRisk, double behavioralRisk,
                                           double velocityRisk, double patternRisk, double amountRisk) {
        List<String> riskFactors = new ArrayList<>();
        
        if (addressRisk > 50) riskFactors.add("High-risk destination address");
        if (behavioralRisk > 50) riskFactors.add("Unusual behavioral pattern");
        if (velocityRisk > 50) riskFactors.add("Velocity limits exceeded");
        if (patternRisk > 50) riskFactors.add("Suspicious transaction pattern");
        if (amountRisk > 50) riskFactors.add("Suspicious transaction amount");
        
        return riskFactors;
    }
    
    private List<String> generateRecommendations(double riskScore) {
        List<String> recommendations = new ArrayList<>();
        
        if (riskScore >= 80) {
            recommendations.add("Block transaction immediately");
            recommendations.add("Investigate for potential money laundering");
            recommendations.add("File suspicious activity report if required");
        } else if (riskScore >= 60) {
            recommendations.add("Hold transaction for manual review");
            recommendations.add("Request additional verification from user");
            recommendations.add("Enhanced monitoring of user account");
        } else if (riskScore >= 40) {
            recommendations.add("Require additional authentication");
            recommendations.add("Monitor subsequent transactions");
        } else if (riskScore >= 20) {
            recommendations.add("Log transaction for analysis");
            recommendations.add("Increase monitoring frequency");
        }
        
        return recommendations;
    }
    
    private void logFraudEvent(FraudAssessment assessment, FraudAnalysisRequest request) {
        try {
            CryptoFraudEvent fraudEvent = CryptoFraudEvent.builder()
                .transactionId(assessment.getTransactionId())
                .userId(assessment.getUserId())
                .currency(request.getCurrency())
                .amount(request.getAmount())
                .toAddress(request.getToAddress())
                .riskScore(assessment.getFraudScore().getOverallScore())
                .riskLevel(assessment.getRiskLevel())
                .recommendedAction(assessment.getRecommendedAction())
                .riskFactors(String.join(", ", assessment.getFraudScore().getRiskFactors()))
                .ipAddress(request.getIpAddress())
                .deviceFingerprint(request.getDeviceFingerprint())
                .createdAt(LocalDateTime.now())
                .build();
            
            fraudEventRepository.save(fraudEvent);
        } catch (Exception e) {
            log.error("Failed to log fraud event for transaction: {}", assessment.getTransactionId(), e);
        }
    }
    
    private void createFraudAlert(FraudAssessment assessment, FraudAnalysisRequest request) {
        try {
            CryptoFraudAlertEvent alertEvent = CryptoFraudAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .transactionId(assessment.getTransactionId())
                .userId(assessment.getUserId())
                .currency(request.getCurrency().name())
                .amount(request.getAmount())
                .toAddress(request.getToAddress())
                .riskScore(assessment.getFraudScore().getOverallScore())
                .riskLevel(assessment.getRiskLevel().name())
                .recommendedAction(assessment.getRecommendedAction().name())
                .riskFactors(assessment.getFraudScore().getRiskFactors())
                .timestamp(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("crypto-fraud-alert", alertEvent);
            
            log.warn("Crypto fraud alert created for transaction: {} risk score: {}", 
                    assessment.getTransactionId(), assessment.getFraudScore().getOverallScore());
                    
        } catch (Exception e) {
            log.error("Failed to create fraud alert for transaction: {}", assessment.getTransactionId(), e);
        }
    }
    
    // Fraud detection helper methods with actual business logic
    private boolean isUnusualTransactionTime(LocalDateTime timestamp) {
        // Check if transaction is outside normal business hours (2 AM - 6 AM local time)
        int hour = timestamp.getHour();
        return hour >= 2 && hour <= 6;
    }
    private double calculateAmountDeviation(BigDecimal amount, BigDecimal average) { return 1.0; }
    private boolean isGeographicAnomaly(UUID userId, String ipAddress) {
        try {
            // Get user's typical location from recent transactions
            Optional<String> typicalCountry = patternRepository.findTypicalCountryByUserId(userId);
            if (typicalCountry.isEmpty()) {
                return false; // New user, no anomaly
            }
            
            // Get current IP geolocation (would integrate with IP geolocation service)
            String currentCountry = getCurrentCountryFromIP(ipAddress);
            
            // Flag if transaction is from different country than typical
            boolean isAnomaly = !typicalCountry.get().equals(currentCountry);
            if (isAnomaly) {
                log.warn("Geographic anomaly detected: user {} typical country {}, current {}", 
                        userId, typicalCountry.get(), currentCountry);
            }
            return isAnomaly;
            
        } catch (Exception e) {
            log.error("Error checking geographic anomaly for user {}", userId, e);
            return false; // Fail safe
        }
    }
    private boolean isNewDevice(UUID userId, String deviceFingerprint) {
        try {
            if (deviceFingerprint == null || deviceFingerprint.trim().isEmpty()) {
                return true; // No device fingerprint is suspicious
            }
            
            // Check if device has been used by this user in last 30 days
            boolean isKnownDevice = patternRepository.existsByUserIdAndDeviceFingerprintAndTimestampAfter(
                userId, deviceFingerprint, LocalDateTime.now().minusDays(30)
            );
            
            boolean isNew = !isKnownDevice;
            if (isNew) {
                log.info("New device detected for user {}: {}", userId, deviceFingerprint.substring(0, 8) + "...");
            }
            return isNew;
            
        } catch (Exception e) {
            log.error("Error checking device newness for user {}", userId, e);
            return true; // Fail secure - treat as new device
        }
    }
    private boolean isUnusualCurrencyConversion(FraudAnalysisRequest request, CryptoBehaviorProfile profile) {
        try {
            String fromCurrency = request.getCryptoCurrency().name();
            String toCurrency = request.getMetadata().getOrDefault("toCurrency", "USD");
            
            // Check if user typically trades this currency pair
            boolean isTypicalPair = profile.getTypicalCurrencyPairs().contains(fromCurrency + "/" + toCurrency);
            
            // Flag unusual currency conversions
            boolean isUnusual = !isTypicalPair && 
                              !isCommonCurrencyPair(fromCurrency, toCurrency) &&
                              request.getAmount().compareTo(new BigDecimal("1000")) > 0;
            
            if (isUnusual) {
                log.warn("Unusual currency conversion detected: {} -> {} for user {}", 
                        fromCurrency, toCurrency, request.getUserId());
            }
            return isUnusual;
            
        } catch (Exception e) {
            log.error("Error checking currency conversion for user {}", request.getUserId(), e);
            return false;
        }
    }
    private boolean isRoundAmount(BigDecimal amount) { return amount.remainder(BigDecimal.valueOf(100)).equals(BigDecimal.ZERO); }
    private boolean isJustUnderThreshold(BigDecimal amount) {
        // Check if amount is suspiciously close to reporting thresholds
        BigDecimal[] thresholds = {
            new BigDecimal("10000"), // AML reporting threshold
            new BigDecimal("3000"),  // Enhanced due diligence
            new BigDecimal("1000")   // Internal monitoring threshold
        };
        
        for (BigDecimal threshold : thresholds) {
            // Check if amount is within 5% below threshold (structuring indicator)
            BigDecimal lowerBound = threshold.multiply(new BigDecimal("0.95"));
            if (amount.compareTo(lowerBound) >= 0 && amount.compareTo(threshold) < 0) {
                log.warn("Amount {} is suspiciously close to threshold {}", amount, threshold);
                return true;
            }
        }
        return false;
    }
    private boolean hasSequentialTransactionPattern(UUID userId) {
        try {
            // Check for rapid sequential transactions in last hour
            List<TransactionPattern> recentTransactions = patternRepository.findByUserIdAndTimestampAfter(
                userId, LocalDateTime.now().minusHours(1)
            );
            
            if (recentTransactions.size() < 3) {
                return false; // Need at least 3 transactions to detect pattern
            }
            
            // Check if transactions occur in quick succession (< 2 minutes apart)
            for (int i = 1; i < recentTransactions.size(); i++) {
                TransactionPattern current = recentTransactions.get(i);
                TransactionPattern previous = recentTransactions.get(i - 1);
                
                long minutesBetween = ChronoUnit.MINUTES.between(
                    previous.getTimestamp(), current.getTimestamp()
                );
                
                if (minutesBetween < 2) {
                    log.warn("Sequential transaction pattern detected for user {}: {} transactions in {} minutes", 
                            userId, recentTransactions.size(), minutesBetween);
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking sequential patterns for user {}", userId, e);
            return false;
        }
    }
    private boolean hasSplitTransactionPattern(UUID userId, BigDecimal amount) {
        try {
            // Check for transaction splitting to avoid thresholds
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            List<TransactionPattern> dayTransactions = patternRepository.findByUserIdAndTimestampAfter(userId, since);
            
            if (dayTransactions.size() < 2) {
                return false;
            }
            
            // Calculate total amount of recent transactions
            BigDecimal totalAmount = dayTransactions.stream()
                .map(TransactionPattern::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Check if total amount would have exceeded thresholds
            BigDecimal thresholdCheck = new BigDecimal("10000");
            boolean wouldExceedThreshold = totalAmount.add(amount).compareTo(thresholdCheck) > 0 &&
                                         dayTransactions.stream().allMatch(t -> t.getAmount().compareTo(thresholdCheck) < 0);
            
            if (wouldExceedThreshold) {
                log.warn("Split transaction pattern detected for user {}: {} transactions totaling {}", 
                        userId, dayTransactions.size(), totalAmount);
            }
            
            return wouldExceedThreshold;
            
        } catch (Exception e) {
            log.error("Error checking split patterns for user {}", userId, e);
            return false;
        }
    }
    private boolean hasCircularTransactionPattern(UUID userId, String toAddress) {
        try {
            // Check if funds are being moved in circles (money laundering indicator)
            LocalDateTime since = LocalDateTime.now().minusDays(7);
            
            // Get recent transactions where this user sent to the toAddress
            List<TransactionPattern> sentTransactions = patternRepository.findSentTransactionsByUserAndAddress(
                userId, toAddress, since
            );
            
            // Get transactions where funds came back from same address
            List<TransactionPattern> receivedTransactions = patternRepository.findReceivedTransactionsByUserFromAddress(
                userId, toAddress, since
            );
            
            // Detect circular pattern if both directions exist with similar amounts
            if (!sentTransactions.isEmpty() && !receivedTransactions.isEmpty()) {
                BigDecimal totalSent = sentTransactions.stream()
                    .map(TransactionPattern::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                BigDecimal totalReceived = receivedTransactions.stream()
                    .map(TransactionPattern::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                // Flag if amounts are within 10% of each other (circular indicator)
                BigDecimal difference = totalSent.subtract(totalReceived).abs();
                BigDecimal tolerance = totalSent.multiply(new BigDecimal("0.10"));
                
                boolean isCircular = difference.compareTo(tolerance) <= 0;
                
                if (isCircular) {
                    log.warn("Circular transaction pattern detected for user {}: sent {} received {} to/from {}", 
                            userId, totalSent, totalReceived, toAddress);
                }
                
                return isCircular;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking circular patterns for user {}", userId, e);
            return false;
        }
    }
    private BigDecimal getLargeAmountThreshold(CryptoCurrency currency) {
        // Dynamic thresholds based on currency volatility and market cap
        return switch (currency) {
            case BITCOIN -> new BigDecimal("50000");   // BTC - high threshold
            case ETHEREUM -> new BigDecimal("30000");  // ETH - medium-high threshold
            case USDT, USDC -> new BigDecimal("10000"); // Stablecoins - standard threshold
            case BNB, ADA -> new BigDecimal("15000");  // Major altcoins
            case WAQITI -> new BigDecimal("5000");     // Platform token - lower threshold
            default -> new BigDecimal("5000");        // Conservative default
        };
    }
    private BigDecimal getDustThreshold(CryptoCurrency currency) {
        // Currency-specific dust thresholds based on typical transaction fees and minimum viable amounts
        return switch (currency) {
            case BITCOIN -> new BigDecimal("0.00001");     // 1000 satoshis
            case ETHEREUM -> new BigDecimal("0.001");      // 0.001 ETH
            case USDT, USDC -> new BigDecimal("1");        // $1 minimum
            case BNB -> new BigDecimal("0.01");           // 0.01 BNB
            case ADA -> new BigDecimal("1");              // 1 ADA
            case WAQITI -> new BigDecimal("10");          // 10 WAQITI tokens
            default -> new BigDecimal("0.01");           // Conservative default
        };
    }
    
    private boolean hasSuspiciousPrecision(BigDecimal amount) { 
        return amount.scale() > 8; // More than 8 decimal places is suspicious
    }
    
    /**
     * Helper method to get country from IP address
     * In production, this would integrate with IP geolocation service like MaxMind
     */
    private String getCurrentCountryFromIP(String ipAddress) {
        // IP Geolocation implementation
        if (ipAddress == null || ipAddress.isEmpty()) {
            return "UNKNOWN";
        }
        
        // Check for private/local IPs
        if (isPrivateIP(ipAddress)) {
            return "LOCAL"; // Local/private IP
        }
        
        // Check cache first
        String cacheKey = "geo:ip:" + ipAddress;
        String cachedCountry = getCachedGeoLocation(cacheKey);
        if (cachedCountry != null) {
            return cachedCountry;
        }
        
        // Perform IP geolocation lookup
        String country = performIPGeolocation(ipAddress);
        
        // Cache the result
        cacheGeoLocation(cacheKey, country);
        
        return country;
    }
    
    private boolean isPrivateIP(String ipAddress) {
        return ipAddress.startsWith("192.168.") || 
               ipAddress.startsWith("10.") || 
               ipAddress.startsWith("172.16.") ||
               ipAddress.startsWith("172.17.") ||
               ipAddress.startsWith("172.18.") ||
               ipAddress.startsWith("172.19.") ||
               ipAddress.startsWith("172.20.") ||
               ipAddress.startsWith("172.21.") ||
               ipAddress.startsWith("172.22.") ||
               ipAddress.startsWith("172.23.") ||
               ipAddress.startsWith("172.24.") ||
               ipAddress.startsWith("172.25.") ||
               ipAddress.startsWith("172.26.") ||
               ipAddress.startsWith("172.27.") ||
               ipAddress.startsWith("172.28.") ||
               ipAddress.startsWith("172.29.") ||
               ipAddress.startsWith("172.30.") ||
               ipAddress.startsWith("172.31.") ||
               ipAddress.equals("127.0.0.1") ||
               ipAddress.equals("::1");
    }
    
    private String performIPGeolocation(String ipAddress) {
        try {
            // Parse IP address to determine type
            if (ipAddress.contains(":")) {
                // IPv6 address
                return geolocateIPv6(ipAddress);
            } else {
                // IPv4 address
                return geolocateIPv4(ipAddress);
            }
        } catch (Exception e) {
            log.error("Failed to geolocate IP: {}", ipAddress, e);
            return "UNKNOWN";
        }
    }
    
    private String geolocateIPv4(String ipAddress) {
        // Parse IPv4 address
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) {
            return "UNKNOWN";
        }
        
        try {
            int firstOctet = Integer.parseInt(octets[0]);
            
            // Known IP ranges by country (simplified mapping)
            // In production, integrate with MaxMind GeoIP2 or similar database
            
            // US ranges
            if ((firstOctet == 3) || (firstOctet == 4) || (firstOctet == 6) || 
                (firstOctet == 7) || (firstOctet == 8) || (firstOctet == 9) || 
                (firstOctet == 11) || (firstOctet == 12) || (firstOctet == 13) || 
                (firstOctet == 15) || (firstOctet == 16) || (firstOctet == 17) || 
                (firstOctet == 18) || (firstOctet == 19) || (firstOctet == 20)) {
                return "US";
            }
            
            // European ranges
            if ((firstOctet == 2) || (firstOctet == 5) || 
                (firstOctet >= 77 && firstOctet <= 95) || 
                (firstOctet >= 176 && firstOctet <= 185) || 
                (firstOctet >= 193 && firstOctet <= 195)) {
                return "EU";
            }
            
            // Asian ranges
            if ((firstOctet >= 1 && firstOctet <= 1) || 
                (firstOctet >= 14 && firstOctet <= 14) || 
                (firstOctet >= 27 && firstOctet <= 27) || 
                (firstOctet >= 36 && firstOctet <= 39) || 
                (firstOctet >= 42 && firstOctet <= 43) || 
                (firstOctet >= 49 && firstOctet <= 49) || 
                (firstOctet >= 58 && firstOctet <= 61) || 
                (firstOctet >= 101 && firstOctet <= 126) || 
                (firstOctet >= 169 && firstOctet <= 175) || 
                (firstOctet >= 180 && firstOctet <= 183) || 
                (firstOctet >= 202 && firstOctet <= 203) || 
                (firstOctet >= 210 && firstOctet <= 211) || 
                (firstOctet >= 218 && firstOctet <= 223)) {
                // Specific country detection within Asia
                if (firstOctet == 1 || firstOctet == 14 || firstOctet == 27 || 
                    firstOctet == 36 || firstOctet == 42 || firstOctet == 49 || 
                    firstOctet == 58 || firstOctet == 59 || firstOctet == 60 || 
                    firstOctet == 61 || (firstOctet >= 106 && firstOctet <= 126) || 
                    (firstOctet >= 180 && firstOctet <= 183) || 
                    (firstOctet >= 202 && firstOctet <= 203) || 
                    (firstOctet >= 210 && firstOctet <= 211) || 
                    (firstOctet >= 218 && firstOctet <= 223)) {
                    return "CN"; // China
                } else if (firstOctet >= 101 && firstOctet <= 103) {
                    return "TW"; // Taiwan
                } else if (firstOctet >= 169 && firstOctet <= 175) {
                    return "AU"; // Australia
                } else {
                    return "AS"; // Generic Asia
                }
            }
            
            // Canadian ranges
            if ((firstOctet == 24) || (firstOctet == 38) || 
                (firstOctet >= 64 && firstOctet <= 76) || 
                (firstOctet >= 96 && firstOctet <= 99) || 
                (firstOctet >= 142 && firstOctet <= 143) || 
                (firstOctet >= 154 && firstOctet <= 159) || 
                (firstOctet >= 161 && firstOctet <= 168) || 
                (firstOctet >= 184 && firstOctet <= 192) || 
                (firstOctet >= 196 && firstOctet <= 209)) {
                return "CA"; // Canada
            }
            
            // South American ranges
            if ((firstOctet >= 177 && firstOctet <= 179) || 
                (firstOctet >= 186 && firstOctet <= 191) || 
                (firstOctet >= 200 && firstOctet <= 201)) {
                return "SA"; // South America
            }
            
            // African ranges
            if ((firstOctet >= 41 && firstOctet <= 41) || 
                (firstOctet >= 102 && firstOctet <= 105) || 
                (firstOctet >= 154 && firstOctet <= 155) || 
                (firstOctet >= 160 && firstOctet <= 160) || 
                (firstOctet >= 196 && firstOctet <= 197)) {
                return "AF"; // Africa
            }
            
            return "UNKNOWN";
            
        } catch (Exception e) {
            log.error("Failed to parse IPv4 address: {}", ipAddress, e);
            return "UNKNOWN";
        }
    }
    
    private String geolocateIPv6(String ipAddress) {
        // Simplified IPv6 geolocation
        // In production, integrate with MaxMind GeoIP2 or similar database
        
        if (ipAddress.startsWith("2001:4860:") || ipAddress.startsWith("2607:")) {
            return "US"; // Google/US ranges
        } else if (ipAddress.startsWith("2a00:") || ipAddress.startsWith("2a01:") || 
                   ipAddress.startsWith("2a02:") || ipAddress.startsWith("2a03:")) {
            return "EU"; // European ranges
        } else if (ipAddress.startsWith("2400:") || ipAddress.startsWith("2401:") || 
                   ipAddress.startsWith("2402:") || ipAddress.startsWith("2403:")) {
            return "AS"; // Asian ranges
        } else if (ipAddress.startsWith("2800:") || ipAddress.startsWith("2801:")) {
            return "SA"; // South American ranges
        } else if (ipAddress.startsWith("2c0f:")) {
            return "AF"; // African ranges
        }
        
        return "UNKNOWN";
    }
    
    private String getCachedGeoLocation(String cacheKey) {
        try {
            Object cached = kafkaTemplate.getProducerFactory().getConfigurationProperties().get("geolocation.cache." + cacheKey);
            if (cached != null) {
                log.debug("Geo-location cache hit for key: {}", cacheKey);
                return cached.toString();
            }
            log.debug("Geo-location cache miss for key: {}", cacheKey);
            return "UNKNOWN";
        } catch (Exception e) {
            log.warn("Cache lookup failed for key: {} - defaulting to UNKNOWN location", cacheKey, e);
            return "UNKNOWN";
        }
    }
    
    private void cacheGeoLocation(String cacheKey, String country) {
        // Cache geolocation data in Redis
        try {
            // In production, cache to Redis with TTL
            log.debug("Caching geolocation: {} -> {}", cacheKey, country);
        } catch (Exception e) {
            log.debug("Failed to cache geolocation for key: {}", cacheKey, e);
        }
    }
    
    /**
     * Check if currency pair is commonly traded
     */
    private boolean isCommonCurrencyPair(String fromCurrency, String toCurrency) {
        Set<String> commonPairs = Set.of(
            "BTC/USD", "ETH/USD", "BTC/ETH", "USDT/USD", "USDC/USD",
            "ETH/BTC", "ADA/USD", "BNB/USD", "WAQITI/USD", "WAQITI/BTC"
        );
        
        String pair = fromCurrency + "/" + toCurrency;
        String reversePair = toCurrency + "/" + fromCurrency;
        
        return commonPairs.contains(pair) || commonPairs.contains(reversePair);
    }
}