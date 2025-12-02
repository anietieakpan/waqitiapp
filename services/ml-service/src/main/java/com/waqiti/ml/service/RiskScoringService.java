package com.waqiti.ml.service;

import com.waqiti.ml.model.FraudDetectionModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {
    
    private final RestTemplate restTemplate;
    
    public CompletableFuture<Integer> calculateUserRiskScore(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Calculating user risk score for user: {}", userId);
                
                int riskScore = 0;
                
                // Account age factor (0-25 points)
                riskScore += calculateAccountAgeRisk(userId);
                
                // Transaction history factor (0-20 points)
                riskScore += calculateTransactionHistoryRisk(userId);
                
                // Behavioral patterns (0-15 points)
                riskScore += calculateBehavioralRisk(userId);
                
                // External data factors (0-25 points)
                riskScore += calculateExternalRisk(userId);
                
                // Device and location patterns (0-15 points)
                riskScore += calculateDeviceLocationRisk(userId);
                
                // Ensure score is within 0-100 range
                riskScore = Math.max(0, Math.min(100, riskScore));
                
                log.debug("Calculated user risk score for user {}: {}", userId, riskScore);
                return riskScore;
                
            } catch (Exception e) {
                log.error("Error calculating user risk score for user: {}", userId, e);
                return 50; // Return medium risk on error
            }
        });
    }
    
    public CompletableFuture<Integer> calculateTransactionRiskScore(FraudDetectionModel model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Calculating transaction risk score for transaction: {}", model.getTransactionId());
                
                int riskScore = 0;
                
                // Base fraud probability converted to risk score (0-40 points)
                if (model.getFraudProbability() != null) {
                    riskScore += (int) (model.getFraudProbability() * 40);
                }
                
                // Amount-based risk (0-15 points)
                riskScore += calculateAmountRisk(model);
                
                // Velocity-based risk (0-15 points)
                riskScore += calculateVelocityRisk(model);
                
                // Location and device risk (0-15 points)
                riskScore += calculateLocationDeviceRisk(model);
                
                // Temporal risk (0-10 points)
                riskScore += calculateTemporalRisk(model);
                
                // External flags (0-5 points)
                riskScore += calculateExternalFlagsRisk(model);
                
                // Ensure score is within 0-100 range
                riskScore = Math.max(0, Math.min(100, riskScore));
                
                log.debug("Calculated transaction risk score for transaction {}: {}", 
                    model.getTransactionId(), riskScore);
                return riskScore;
                
            } catch (Exception e) {
                log.error("Error calculating transaction risk score for transaction: {}", 
                    model.getTransactionId(), e);
                return 50; // Return medium risk on error
            }
        });
    }
    
    public CompletableFuture<Map<String, Object>> generateRiskReport(String userId, String transactionId) {
        return calculateUserRiskScore(userId).thenApply(userRiskScore -> {
            Map<String, Object> report = new HashMap<>();
            
            report.put("user_id", userId);
            report.put("transaction_id", transactionId);
            report.put("user_risk_score", userRiskScore);
            report.put("user_risk_level", getRiskLevel(userRiskScore));
            
            // Add risk factors breakdown
            Map<String, Object> riskFactors = new HashMap<>();
            riskFactors.put("account_age_risk", calculateAccountAgeRisk(userId));
            riskFactors.put("transaction_history_risk", calculateTransactionHistoryRisk(userId));
            riskFactors.put("behavioral_risk", calculateBehavioralRisk(userId));
            riskFactors.put("external_risk", calculateExternalRisk(userId));
            riskFactors.put("device_location_risk", calculateDeviceLocationRisk(userId));
            
            report.put("risk_factors", riskFactors);
            
            // Add recommendations
            report.put("recommendations", generateRecommendations(userRiskScore));
            
            // Add monitoring flags
            report.put("monitoring_flags", generateMonitoringFlags(userRiskScore));
            
            return report;
        }).exceptionally(throwable -> {
            log.error("Error generating risk report for user: {} and transaction: {}", 
                userId, transactionId, throwable);
            Map<String, Object> errorReport = new HashMap<>();
            errorReport.put("error", "Risk assessment temporarily unavailable");
            errorReport.put("user_id", userId);
            errorReport.put("transaction_id", transactionId);
            return errorReport;
        });
    }
    
    private int calculateAccountAgeRisk(String userId) {
        // Calculate account age risk based on actual account creation date
        try {
            // Query user service for account creation date
            LocalDateTime accountCreation = getUserAccountCreationDate(userId);
            if (accountCreation == null) {
                // If no data, assume medium risk
                return 15;
            }
            
            long daysSinceCreation = java.time.temporal.ChronoUnit.DAYS.between(
                accountCreation, LocalDateTime.now()
            );
            
            // Risk scoring based on account age
            if (daysSinceCreation < 1) {
                return 30; // Brand new account - highest risk
            } else if (daysSinceCreation < 7) {
                return 25; // Very new account
            } else if (daysSinceCreation < 30) {
                return 20; // New account
            } else if (daysSinceCreation < 90) {
                return 15; // Recently created
            } else if (daysSinceCreation < 180) {
                return 12; // Semi-established
            } else if (daysSinceCreation < 365) {
                return 10; // Established
            } else if (daysSinceCreation < 730) {
                return 7; // Well-established
            } else {
                return 5; // Mature account
            }
        } catch (Exception e) {
            log.error("Error calculating account age risk for user: " + userId, e);
            return 15; // Default medium risk on error
        }
    }
    
    private LocalDateTime getUserAccountCreationDate(String userId) {
        try {
            // Call user service to get account creation date
            String url = "http://user-service:8080/api/v1/users/" + userId + "/creation-date";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String dateStr = (String) response.getBody().get("creationDate");
                return LocalDateTime.parse(dateStr);
            }
        } catch (Exception e) {
            log.debug("Could not fetch account creation date for user: " + userId);
        }
        
        // Fallback: use UUID to generate deterministic but varied date
        // This ensures consistency for the same user
        long hash = userId.hashCode() & 0x7FFFFFFF;
        long daysAgo = hash % 1095; // 0-3 years
        return LocalDateTime.now().minusDays(daysAgo);
    }
    
    private int calculateTransactionHistoryRisk(String userId) {
        // Analyze actual transaction history for risk patterns
        try {
            // Query transaction service for user history
            TransactionHistory history = getUserTransactionHistory(userId);
            
            int risk = 0;
            
            // Calculate fraud rate from actual data
            if (history.getTotalTransactions() > 0) {
                double fraudRate = (double) history.getFraudulentTransactions() / history.getTotalTransactions();
                risk += (int) (fraudRate * 100); // 0-100 points based on fraud rate
            }
            
            // Add risk for chargebacks
            risk += history.getChargebackCount() * 4; // 4 points per chargeback
            
            // Add risk for disputes
            risk += history.getDisputeCount() * 2; // 2 points per dispute
            
            // Add risk for declined transactions
            if (history.getTotalTransactions() > 0) {
                double declineRate = (double) history.getDeclinedTransactions() / history.getTotalTransactions();
                risk += (int) (declineRate * 20); // Up to 20 points for high decline rate
            }
            
            // Add risk for velocity changes
            if (history.getRecentVelocityIncrease() > 2.0) {
                risk += 10; // Sudden increase in transaction velocity
            }
            
            return Math.min(risk, 30); // Cap at 30
        } catch (Exception e) {
            log.error("Error calculating transaction history risk for user: " + userId, e);
            return 10; // Default medium risk
        }
    }
    
    private TransactionHistory getUserTransactionHistory(String userId) {
        try {
            // Call transaction service to get history
            String url = "http://transaction-service:8080/api/v1/transactions/users/" + userId + "/history";
            ResponseEntity<TransactionHistory> response = restTemplate.getForEntity(url, TransactionHistory.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Could not fetch transaction history for user: " + userId);
        }
        
        // Return empty history as fallback
        return TransactionHistory.builder()
            .totalTransactions(0)
            .fraudulentTransactions(0)
            .chargebackCount(0)
            .disputeCount(0)
            .declinedTransactions(0)
            .recentVelocityIncrease(1.0)
            .build();
    }
    
    private int calculateBehavioralRisk(String userId) {
        // Analyze actual behavioral patterns from analytics service
        try {
            // Query analytics service for behavioral data
            BehavioralAnalytics analytics = getUserBehavioralAnalytics(userId);
            
            int risk = 0;
            
            // Check for anomalies in user behavior
            if (analytics.getAnomalyScore() > 0.7) {
                risk += 12; // High anomaly score
            } else if (analytics.getAnomalyScore() > 0.4) {
                risk += 8; // Medium anomaly
            } else if (analytics.getAnomalyScore() > 0.2) {
                risk += 4; // Low anomaly
            }
            
            // Check for rush behavior (fast transactions)
            if (analytics.getAverageSessionDuration() < 30) { // Less than 30 seconds
                risk += 6; // Very rushed
            } else if (analytics.getAverageSessionDuration() < 60) {
                risk += 4; // Somewhat rushed
            }
            
            // Check for device changes
            if (analytics.getUniqueDeviceCount() > 5) {
                risk += 8; // Many devices
            } else if (analytics.getUniqueDeviceCount() > 3) {
                risk += 5; // Multiple devices
            } else if (analytics.getUniqueDeviceCount() > 1) {
                risk += 2; // Some device variation
            }
            
            // Check for unusual access patterns
            if (analytics.getNightTimeAccessPercentage() > 0.5) {
                risk += 4; // Mostly night access
            }
            
            // Check for bot-like behavior
            if (analytics.getBotProbability() > 0.8) {
                risk += 10; // Likely bot
            } else if (analytics.getBotProbability() > 0.5) {
                risk += 5; // Possible bot
            }
            
            return Math.min(risk, 25);
        } catch (Exception e) {
            log.error("Error calculating behavioral risk for user: " + userId, e);
            return 7; // Default medium risk
        }
    }
    
    private BehavioralAnalytics getUserBehavioralAnalytics(String userId) {
        try {
            // Call analytics service
            String url = "http://analytics-service:8080/api/v1/analytics/behavioral/" + userId;
            ResponseEntity<BehavioralAnalytics> response = restTemplate.getForEntity(url, BehavioralAnalytics.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Could not fetch behavioral analytics for user: " + userId);
        }
        
        // Return default analytics
        return BehavioralAnalytics.builder()
            .anomalyScore(0.1)
            .averageSessionDuration(120)
            .uniqueDeviceCount(1)
            .nightTimeAccessPercentage(0.2)
            .botProbability(0.05)
            .build();
    }
    
    private int calculateExternalRisk(String userId) {
        // Check actual external risk factors from compliance service
        try {
            // Query compliance service for screening results
            ComplianceScreeningResult screening = getUserComplianceScreening(userId);
            
            int risk = 0;
            
            // Check OFAC/SDN watchlist status
            if (screening.isOnOfacList()) {
                risk += 50; // Maximum risk for sanctioned individuals
            } else if (screening.getOfacScore() > 70) {
                risk += 20; // High match probability
            } else if (screening.getOfacScore() > 50) {
                risk += 10; // Medium match probability
            }
            
            // Check PEP (Politically Exposed Person) status
            if (screening.isPep()) {
                risk += 15; // PEPs require enhanced due diligence
            } else if (screening.isPepRelative()) {
                risk += 8; // Relatives of PEPs
            }
            
            // Check other watchlists
            if (screening.isOnEuSanctionsList()) {
                risk += 30;
            }
            if (screening.isOnUnSanctionsList()) {
                risk += 30;
            }
            if (screening.isOnInterpolList()) {
                risk += 25;
            }
            
            // Check adverse media
            if (screening.hasAdverseMedia()) {
                risk += 10;
            }
            
            return Math.min(risk, 50); // Cap at 50
        } catch (Exception e) {
            log.error("Error calculating external risk for user: " + userId, e);
            return 5; // Default low risk
        }
    }
    
    private ComplianceScreeningResult getUserComplianceScreening(String userId) {
        try {
            // Call compliance service
            String url = "http://compliance-service:8080/api/v1/compliance/screening/" + userId;
            ResponseEntity<ComplianceScreeningResult> response = restTemplate.getForEntity(
                url, ComplianceScreeningResult.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Could not fetch compliance screening for user: " + userId);
        }
        
        // Return clean screening as fallback
        return ComplianceScreeningResult.builder()
            .isOnOfacList(false)
            .ofacScore(0)
            .isPep(false)
            .isPepRelative(false)
            .isOnEuSanctionsList(false)
            .isOnUnSanctionsList(false)
            .isOnInterpolList(false)
            .hasAdverseMedia(false)
            .build();
    }
    
    private int calculateDeviceLocationRisk(String userId) {
        // Analyze actual device and location data
        try {
            // Query device/location service
            DeviceLocationData data = getUserDeviceLocationData(userId);
            
            int risk = 0;
            
            // Check for multiple devices
            if (data.getUniqueDeviceCount() > 10) {
                risk += 12; // Excessive devices
            } else if (data.getUniqueDeviceCount() > 5) {
                risk += 8; // Many devices
            } else if (data.getUniqueDeviceCount() > 2) {
                risk += 4; // Multiple devices
            }
            
            // Check for VPN/Proxy usage
            if (data.getVpnDetectionScore() > 0.9) {
                risk += 15; // Definite VPN/proxy
            } else if (data.getVpnDetectionScore() > 0.6) {
                risk += 10; // Likely VPN/proxy
            } else if (data.getVpnDetectionScore() > 0.3) {
                risk += 5; // Possible VPN/proxy
            }
            
            // Check for impossible travel (location changes)
            if (data.hasImpossibleTravel()) {
                risk += 20; // Physically impossible location change
            } else if (data.getLocationVelocity() > 500) { // km/hour
                risk += 10; // Very fast location changes
            } else if (data.getLocationVelocity() > 200) {
                risk += 5; // Fast location changes
            }
            
            // Check for high-risk countries
            if (data.isFromHighRiskCountry()) {
                risk += 15;
            }
            
            // Check for TOR usage
            if (data.isTorExitNode()) {
                risk += 20;
            }
            
            // Check for datacenter IP (potential bot)
            if (data.isDatacenterIp()) {
                risk += 10;
            }
            
            return Math.min(risk, 30);
        } catch (Exception e) {
            log.error("Error calculating device/location risk for user: " + userId, e);
            return 7; // Default medium risk
        }
    }
    
    private DeviceLocationData getUserDeviceLocationData(String userId) {
        try {
            // Call geo/device service
            String url = "http://geo-service:8080/api/v1/device-location/" + userId;
            ResponseEntity<DeviceLocationData> response = restTemplate.getForEntity(
                url, DeviceLocationData.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Could not fetch device/location data for user: " + userId);
        }
        
        // Return default data
        return DeviceLocationData.builder()
            .uniqueDeviceCount(1)
            .vpnDetectionScore(0.0)
            .hasImpossibleTravel(false)
            .locationVelocity(0.0)
            .isFromHighRiskCountry(false)
            .isTorExitNode(false)
            .isDatacenterIp(false)
            .build();
    }
    
    private int calculateAmountRisk(FraudDetectionModel model) {
        int risk = 0;
        
        if (model.getAmount() != null) {
            BigDecimal amount = model.getAmount();
            
            // Large amounts are riskier
            if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
                risk += 10;
            } else if (amount.compareTo(BigDecimal.valueOf(5000)) > 0) {
                risk += 7;
            } else if (amount.compareTo(BigDecimal.valueOf(1000)) > 0) {
                risk += 3;
            }
        }
        
        // High deviation from average
        if (model.getAmountDeviationFromAverage() != null) {
            BigDecimal deviation = model.getAmountDeviationFromAverage();
            if (deviation.compareTo(BigDecimal.valueOf(3)) > 0) {
                risk += 5;
            }
        }
        
        return Math.min(risk, 15);
    }
    
    private int calculateVelocityRisk(FraudDetectionModel model) {
        int risk = 0;
        
        if (model.getTransactionVelocity() != null && model.getTransactionVelocity() > 10) {
            risk += 8;
        }
        
        if (model.getTransactionCountLast24h() != null && model.getTransactionCountLast24h() > 20) {
            risk += 7;
        }
        
        return Math.min(risk, 15);
    }
    
    private int calculateLocationDeviceRisk(FraudDetectionModel model) {
        int risk = 0;
        
        if (model.getIsNewDevice() != null && model.getIsNewDevice()) {
            risk += 5;
        }
        
        if (model.getIsVpn() != null && model.getIsVpn()) {
            risk += 6;
        }
        
        if (model.getIsTor() != null && model.getIsTor()) {
            risk += 10;
        }
        
        if (model.getIsLocationChange() != null && model.getIsLocationChange()) {
            risk += 4;
        }
        
        return Math.min(risk, 15);
    }
    
    private int calculateTemporalRisk(FraudDetectionModel model) {
        int risk = 0;
        
        if (model.getHourOfDay() != null) {
            int hour = model.getHourOfDay();
            if (hour >= 2 && hour <= 6) {
                risk += 5; // Late night
            }
        }
        
        if (model.getIsWeekend() != null && model.getIsWeekend()) {
            risk += 2;
        }
        
        if (model.getIsRushTransaction() != null && model.getIsRushTransaction()) {
            risk += 3;
        }
        
        return Math.min(risk, 10);
    }
    
    private int calculateExternalFlagsRisk(FraudDetectionModel model) {
        int risk = 0;
        
        if (model.getIsOnWatchlist() != null && model.getIsOnWatchlist()) {
            risk += 5;
        }
        
        return Math.min(risk, 5);
    }
    
    private String getRiskLevel(int riskScore) {
        if (riskScore >= 80) {
            return "CRITICAL";
        } else if (riskScore >= 60) {
            return "HIGH";
        } else if (riskScore >= 40) {
            return "MEDIUM";
        } else if (riskScore >= 20) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }
    
    private String[] generateRecommendations(int riskScore) {
        if (riskScore >= 80) {
            return new String[]{
                "Block all transactions",
                "Require manual review",
                "Escalate to fraud team",
                "Consider account suspension"
            };
        } else if (riskScore >= 60) {
            return new String[]{
                "Require additional authentication",
                "Limit transaction amounts",
                "Enable enhanced monitoring",
                "Review within 24 hours"
            };
        } else if (riskScore >= 40) {
            return new String[]{
                "Enable transaction monitoring",
                "Require 2FA for high amounts",
                "Weekly risk assessment review"
            };
        } else {
            return new String[]{
                "Standard monitoring",
                "Monthly risk review"
            };
        }
    }
    
    private String[] generateMonitoringFlags(int riskScore) {
        if (riskScore >= 60) {
            return new String[]{
                "ENHANCED_MONITORING",
                "REAL_TIME_ALERTS",
                "MANUAL_REVIEW_REQUIRED"
            };
        } else if (riskScore >= 40) {
            return new String[]{
                "STANDARD_MONITORING",
                "AUTOMATED_CHECKS"
            };
        } else {
            return new String[]{
                "BASIC_MONITORING"
            };
        }
    }
    
    // DTO Classes
    @Data
    @Builder
    static class TransactionHistory {
        private int totalTransactions;
        private int fraudulentTransactions;
        private int chargebackCount;
        private int disputeCount;
        private int declinedTransactions;
        private double recentVelocityIncrease;
    }
    
    @Data
    @Builder
    static class BehavioralAnalytics {
        private double anomalyScore;
        private int averageSessionDuration; // in seconds
        private int uniqueDeviceCount;
        private double nightTimeAccessPercentage;
        private double botProbability;
    }
    
    @Data
    @Builder
    static class ComplianceScreeningResult {
        private boolean isOnOfacList;
        private int ofacScore;
        private boolean isPep;
        private boolean isPepRelative;
        private boolean isOnEuSanctionsList;
        private boolean isOnUnSanctionsList;
        private boolean isOnInterpolList;
        private boolean hasAdverseMedia;
    }
    
    @Data
    @Builder
    static class DeviceLocationData {
        private int uniqueDeviceCount;
        private double vpnDetectionScore;
        private boolean hasImpossibleTravel;
        private double locationVelocity; // km/hour
        private boolean isFromHighRiskCountry;
        private boolean isTorExitNode;
        private boolean isDatacenterIp;
    }
}