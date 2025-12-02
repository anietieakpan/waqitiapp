package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudAssessmentRequest;
import com.waqiti.frauddetection.dto.RiskScore;
import com.waqiti.common.math.MoneyMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Risk Scoring Service
 * 
 * CRITICAL IMPLEMENTATION: Calculates comprehensive risk scores
 * Replaces missing implementation identified in audit
 * 
 * @author Waqiti Security Team
 * @version 2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskScoringService {

    private final com.waqiti.frauddetection.repository.TransactionHistoryRepository transactionHistoryRepository;
    private final DeviceFingerprintService deviceFingerprintService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    /**
     * Calculate comprehensive risk score for transaction
     */
    public RiskScore calculateRiskScore(FraudAssessmentRequest request) {
        log.debug("Calculating risk score for user: {}", request.getUserId());
        
        double amountScore = calculateAmountRisk(request.getAmount());
        double frequencyScore = calculateFrequencyRisk(request.getUserId());
        double timeScore = calculateTimeRisk();
        double locationScore = calculateLocationRisk(request.getIpAddress());
        double deviceScore = calculateDeviceRisk(request.getDeviceFingerprint());
        
        // Weighted average
        double overallScore = (amountScore * 0.30) +
                            (frequencyScore * 0.25) +
                            (timeScore * 0.15) +
                            (locationScore * 0.20) +
                            (deviceScore * 0.10);
        
        return RiskScore.builder()
            .overallScore(overallScore)
            .amountScore(amountScore)
            .frequencyScore(frequencyScore)
            .timeScore(timeScore)
            .locationScore(locationScore)
            .deviceScore(deviceScore)
            .calculatedAt(LocalDateTime.now())
            .build();
    }

    private double calculateAmountRisk(BigDecimal amount) {
        // Higher amounts = higher risk
        // Use BigDecimal comparisons instead of double conversion
        BigDecimal threshold10k = new BigDecimal("10000");
        BigDecimal threshold5k = new BigDecimal("5000");
        BigDecimal threshold1k = new BigDecimal("1000");
        BigDecimal threshold500 = new BigDecimal("500");

        if (amount.compareTo(threshold10k) > 0) return 0.9;
        if (amount.compareTo(threshold5k) > 0) return 0.7;
        if (amount.compareTo(threshold1k) > 0) return 0.5;
        if (amount.compareTo(threshold500) > 0) return 0.3;
        return 0.1;
    }

    private double calculateFrequencyRisk(String userId) {
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minus(1, ChronoUnit.HOURS);
            LocalDateTime oneDayAgo = LocalDateTime.now().minus(1, ChronoUnit.DAYS);
            
            long hourlyCount = transactionHistoryRepository
                .countByUserIdAndTransactionDateAfter(userId, oneHourAgo);
            long dailyCount = transactionHistoryRepository
                .countByUserIdAndTransactionDateAfter(userId, oneDayAgo);
            
            if (hourlyCount > 10) return 0.9;
            if (hourlyCount > 5) return 0.7;
            if (dailyCount > 50) return 0.6;
            if (dailyCount > 20) return 0.4;
            
            return 0.2;
        } catch (Exception e) {
            log.error("Error calculating frequency risk for user {}: {}", userId, e.getMessage());
            return 0.3;
        }
    }

    private double calculateTimeRisk() {
        int hour = LocalDateTime.now().getHour();
        // Transactions during unusual hours are riskier
        if (hour < 6 || hour > 23) return 0.6;
        return 0.2;
    }

    private double calculateLocationRisk(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) return 0.5;
        
        try {
            String cacheKey = "ip:risk:" + ipAddress;
            Double cached = (Double) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
            
            double risk = 0.2;
            
            if (ipAddress.startsWith("10.") || 
                ipAddress.startsWith("192.168.") || 
                ipAddress.startsWith("172.")) {
                risk = 0.4;
            }
            
            if (ipAddress.startsWith("127.")) {
                risk = 0.6;
            }
            
            String countryCode = getCountryCodeFromIP(ipAddress);
            if (isHighRiskCountry(countryCode)) {
                risk += 0.3;
            }
            
            redisTemplate.opsForValue().set(cacheKey, Math.min(risk, 1.0), 
                java.time.Duration.ofHours(24));
            
            return Math.min(risk, 1.0);
        } catch (Exception e) {
            log.error("Error calculating location risk for IP {}: {}", ipAddress, e.getMessage());
            return 0.3;
        }
    }
    
    private String getCountryCodeFromIP(String ipAddress) {
        try {
            String cacheKey = "ip:country:" + ipAddress;
            String cached = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
            
            return "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    private boolean isHighRiskCountry(String countryCode) {
        Set<String> highRiskCountries = Set.of("PRK", "IRN", "SYR", "VEN", "CUB");
        return highRiskCountries.contains(countryCode);
    }

    private double calculateDeviceRisk(String deviceFingerprint) {
        if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
            return 0.5;
        }
        
        try {
            String cacheKey = "device:risk:" + deviceFingerprint;
            Double cached = (Double) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
            
            com.waqiti.frauddetection.dto.DeviceFingerprintResult result = 
                deviceFingerprintService.analyzeDeviceFingerprint("unknown", deviceFingerprint);
            
            double risk = result.getRiskScore();
            
            redisTemplate.opsForValue().set(cacheKey, risk, java.time.Duration.ofHours(1));
            
            return risk;
        } catch (Exception e) {
            log.error("Error calculating device risk: {}", e.getMessage());
            return 0.3;
        }
    }
}