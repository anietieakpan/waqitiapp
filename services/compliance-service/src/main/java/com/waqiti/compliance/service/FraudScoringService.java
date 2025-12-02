package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fraud Scoring Service
 * Calculates fraud risk scores for transactions and users
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FraudScoringService {
    
    private final AMLComplianceService amlComplianceService;
    
    public double calculateFraudScore(String userId, String transactionId, BigDecimal amount, Map<String, Object> context) {
        log.debug("Calculating fraud score for userId={}, transactionId={}", userId, transactionId);
        
        double score = 0.0;
        
        try {
            score += calculateAmountRiskScore(amount);
            score += calculateVelocityScore(userId, context);
            score += calculateBehaviorScore(userId, context);
            score += calculateDeviceScore(context);
            score += calculateLocationScore(context);
            
            log.info("Fraud score calculated: userId={}, score={}", userId, score);
            
        } catch (Exception e) {
            log.error("Error calculating fraud score", e);
            score = 50.0;
        }
        
        return Math.min(100.0, Math.max(0.0, score));
    }
    
    private double calculateAmountRiskScore(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return 30.0;
        } else if (amount.compareTo(new BigDecimal("5000")) > 0) {
            return 20.0;
        } else if (amount.compareTo(new BigDecimal("1000")) > 0) {
            return 10.0;
        }
        return 0.0;
    }
    
    private double calculateVelocityScore(String userId, Map<String, Object> context) {
        Integer recentTransactions = (Integer) context.getOrDefault("recentTransactionCount", 0);
        if (recentTransactions > 10) {
            return 25.0;
        } else if (recentTransactions > 5) {
            return 15.0;
        }
        return 0.0;
    }
    
    private double calculateBehaviorScore(String userId, Map<String, Object> context) {
        Boolean unusualPattern = (Boolean) context.getOrDefault("unusualPattern", false);
        return unusualPattern ? 20.0 : 0.0;
    }
    
    private double calculateDeviceScore(Map<String, Object> context) {
        Boolean newDevice = (Boolean) context.getOrDefault("newDevice", false);
        return newDevice ? 10.0 : 0.0;
    }
    
    private double calculateLocationScore(Map<String, Object> context) {
        Boolean unusualLocation = (Boolean) context.getOrDefault("unusualLocation", false);
        return unusualLocation ? 15.0 : 0.0;
    }
    
    public String getRiskLevel(double score) {
        if (score >= 75) {
            return "CRITICAL";
        } else if (score >= 50) {
            return "HIGH";
        } else if (score >= 25) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}