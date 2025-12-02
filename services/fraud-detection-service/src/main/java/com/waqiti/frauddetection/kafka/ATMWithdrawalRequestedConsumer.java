package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.service.RiskScoringService;
import com.waqiti.frauddetection.service.VelocityCheckService;
import com.waqiti.frauddetection.entity.FraudCheck;
import com.waqiti.frauddetection.entity.ATMFraudResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for ATMWithdrawalRequested events
 * Performs real-time fraud detection on ATM withdrawal requests
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ATMWithdrawalRequestedConsumer extends BaseKafkaConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final RiskScoringService riskScoringService;
    private final VelocityCheckService velocityCheckService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "atm-withdrawal-requested", groupId = "fraud-detection-group")
    @CircuitBreaker(name = "atm-fraud-consumer")
    @Retry(name = "atm-fraud-consumer")
    @Transactional
    public void handleATMWithdrawalRequested(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "atm-withdrawal-requested");
        
        try {
            log.info("Processing ATM withdrawal fraud check: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            
            String withdrawalId = eventData.path("withdrawalId").asText();
            String userId = eventData.path("userId").asText();
            String cardNumber = eventData.path("cardNumber").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String atmId = eventData.path("atmId").asText();
            String atmLocation = eventData.path("atmLocation").asText();
            double latitude = eventData.path("latitude").asDouble();
            double longitude = eventData.path("longitude").asDouble();
            LocalDateTime requestTime = LocalDateTime.parse(eventData.path("requestTime").asText());
            String pinVerified = eventData.path("pinVerified").asText("true");
            String cardPresent = eventData.path("cardPresent").asText("true");
            
            log.info("Running fraud checks for ATM withdrawal: withdrawalId={}, userId={}, amount={}, location={}", 
                    withdrawalId, userId, amount, atmLocation);
            
            // Perform comprehensive fraud detection
            ATMFraudResult fraudResult = performATMFraudDetection(withdrawalId, userId, cardNumber, 
                    amount, atmId, atmLocation, latitude, longitude, requestTime, 
                    Boolean.parseBoolean(pinVerified), Boolean.parseBoolean(cardPresent));
            
            // Send fraud result back to ATM service
            publishFraudResult(withdrawalId, fraudResult);
            
            ack.acknowledge();
            log.info("Completed ATM withdrawal fraud check: withdrawalId={}, riskScore={}, approved={}", 
                    withdrawalId, fraudResult.getRiskScore(), fraudResult.isApproved());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse ATM withdrawal event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing ATM withdrawal event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private ATMFraudResult performATMFraudDetection(String withdrawalId, String userId, String cardNumber,
                                                   BigDecimal amount, String atmId, String atmLocation,
                                                   double latitude, double longitude, LocalDateTime requestTime,
                                                   boolean pinVerified, boolean cardPresent) {
        
        try {
            log.debug("Starting fraud detection analysis for withdrawal: {}", withdrawalId);
            
            // Initialize fraud check record
            FraudCheck fraudCheck = FraudCheck.builder()
                    .id(UUID.randomUUID())
                    .transactionId(withdrawalId)
                    .transactionType("ATM_WITHDRAWAL")
                    .userId(userId)
                    .amount(amount)
                    .location(atmLocation)
                    .latitude(latitude)
                    .longitude(longitude)
                    .timestamp(requestTime)
                    .build();
            
            double totalRiskScore = 0.0;
            StringBuilder riskFactors = new StringBuilder();
            
            // 1. Velocity checks (30% weight)
            double velocityRisk = performVelocityChecks(userId, amount, requestTime);
            totalRiskScore += velocityRisk * 0.30;
            if (velocityRisk > 0.5) {
                riskFactors.append("High transaction velocity; ");
            }
            
            // 2. Location risk analysis (25% weight)
            double locationRisk = analyzeLocationRisk(userId, latitude, longitude, atmLocation, requestTime);
            totalRiskScore += locationRisk * 0.25;
            if (locationRisk > 0.6) {
                riskFactors.append("Unusual location pattern; ");
            }
            
            // 3. Time-based risk analysis (15% weight)
            double timeRisk = analyzeTimeRisk(userId, requestTime);
            totalRiskScore += timeRisk * 0.15;
            if (timeRisk > 0.7) {
                riskFactors.append("Unusual time pattern; ");
            }
            
            // 4. Amount risk analysis (20% weight)
            double amountRisk = analyzeAmountRisk(userId, amount);
            totalRiskScore += amountRisk * 0.20;
            if (amountRisk > 0.6) {
                riskFactors.append("Unusual amount pattern; ");
            }
            
            // 5. Authentication risk (10% weight)
            double authRisk = analyzeAuthenticationRisk(pinVerified, cardPresent, cardNumber);
            totalRiskScore += authRisk * 0.10;
            if (authRisk > 0.5) {
                riskFactors.append("Authentication concerns; ");
            }
            
            // Determine approval based on risk score
            boolean approved = totalRiskScore <= 0.7; // 70% threshold
            String decision = approved ? "APPROVED" : "DECLINED";
            String decisionReason = approved ? "Low fraud risk" : 
                    "High fraud risk: " + riskFactors.toString();
            
            // Create fraud result
            ATMFraudResult result = ATMFraudResult.builder()
                    .withdrawalId(withdrawalId)
                    .userId(userId)
                    .riskScore(totalRiskScore)
                    .approved(approved)
                    .decision(decision)
                    .decisionReason(decisionReason)
                    .riskFactors(riskFactors.toString())
                    .velocityRisk(velocityRisk)
                    .locationRisk(locationRisk)
                    .timeRisk(timeRisk)
                    .amountRisk(amountRisk)
                    .authenticationRisk(authRisk)
                    .processingTime(System.currentTimeMillis())
                    .recommendedAction(approved ? "ALLOW" : "BLOCK")
                    .build();
            
            // Save fraud check results
            fraudCheck.setRiskScore(totalRiskScore);
            fraudCheck.setDecision(decision);
            fraudCheck.setDecisionReason(decisionReason);
            fraudCheck.setProcessedAt(LocalDateTime.now());
            
            fraudDetectionService.saveFraudCheck(fraudCheck);
            
            log.info("ATM fraud analysis completed: withdrawalId={}, riskScore={:.3f}, decision={}", 
                    withdrawalId, totalRiskScore, decision);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in ATM fraud detection: withdrawalId={}, error={}", withdrawalId, e.getMessage(), e);
            
            // Return safe default - block transaction on error
            return ATMFraudResult.builder()
                    .withdrawalId(withdrawalId)
                    .userId(userId)
                    .riskScore(1.0)
                    .approved(false)
                    .decision("DECLINED")
                    .decisionReason("Fraud detection system error - transaction blocked for safety")
                    .recommendedAction("BLOCK")
                    .build();
        }
    }
    
    private double performVelocityChecks(String userId, BigDecimal amount, LocalDateTime requestTime) {
        try {
            // Check hourly velocity
            int hourlyCount = velocityCheckService.getTransactionCount(userId, "ATM_WITHDRAWAL", 
                    requestTime.minusHours(1), requestTime);
            BigDecimal hourlyAmount = velocityCheckService.getTransactionAmount(userId, "ATM_WITHDRAWAL", 
                    requestTime.minusHours(1), requestTime);
            
            // Check daily velocity
            int dailyCount = velocityCheckService.getTransactionCount(userId, "ATM_WITHDRAWAL", 
                    requestTime.minusHours(24), requestTime);
            BigDecimal dailyAmount = velocityCheckService.getTransactionAmount(userId, "ATM_WITHDRAWAL", 
                    requestTime.minusHours(24), requestTime);
            
            double riskScore = 0.0;
            
            // Hourly limits: 3 transactions or $500
            if (hourlyCount >= 3) riskScore += 0.4;
            if (hourlyAmount.add(amount).compareTo(BigDecimal.valueOf(500)) > 0) riskScore += 0.3;
            
            // Daily limits: 10 transactions or $2000
            if (dailyCount >= 10) riskScore += 0.3;
            if (dailyAmount.add(amount).compareTo(BigDecimal.valueOf(2000)) > 0) riskScore += 0.4;
            
            return Math.min(riskScore, 1.0);
            
        } catch (Exception e) {
            log.error("Error in velocity check: userId={}, error={}", userId, e.getMessage(), e);
            return 0.5; // Medium risk on error
        }
    }
    
    private double analyzeLocationRisk(String userId, double latitude, double longitude, 
                                       String atmLocation, LocalDateTime requestTime) {
        try {
            // Get user's recent transaction locations
            var recentLocations = fraudDetectionService.getRecentTransactionLocations(userId, 30);
            
            double riskScore = 0.0;
            
            // Check if location is completely new
            boolean isNewLocation = recentLocations.stream()
                    .noneMatch(loc -> calculateDistance(latitude, longitude, loc.getLatitude(), loc.getLongitude()) < 5.0);
            
            if (isNewLocation) {
                riskScore += 0.4;
            }
            
            // Check distance from user's usual locations
            double minDistance = recentLocations.stream()
                    .mapToDouble(loc -> calculateDistance(latitude, longitude, loc.getLatitude(), loc.getLongitude()))
                    .min()
                    .orElse(0.0);
            
            if (minDistance > 100) { // More than 100 km from usual locations
                riskScore += 0.5;
            } else if (minDistance > 50) { // More than 50 km
                riskScore += 0.3;
            }
            
            // Check for rapid location changes (impossible travel)
            var lastLocation = fraudDetectionService.getLastTransactionLocation(userId);
            if (lastLocation != null) {
                double distanceFromLast = calculateDistance(latitude, longitude, 
                        lastLocation.getLatitude(), lastLocation.getLongitude());
                long timeDiffMinutes = java.time.Duration.between(lastLocation.getTimestamp(), requestTime).toMinutes();
                
                // Check if travel speed is impossible (more than 200 km/h)
                if (timeDiffMinutes > 0 && distanceFromLast / (timeDiffMinutes / 60.0) > 200) {
                    riskScore += 0.7;
                }
            }
            
            return Math.min(riskScore, 1.0);
            
        } catch (Exception e) {
            log.error("Error in location risk analysis: userId={}, error={}", userId, e.getMessage(), e);
            return 0.3; // Low-medium risk on error
        }
    }
    
    private double analyzeTimeRisk(String userId, LocalDateTime requestTime) {
        try {
            LocalTime time = requestTime.toLocalTime();
            int hour = time.getHour();
            
            // Get user's typical transaction hours
            var typicalHours = fraudDetectionService.getUserTypicalTransactionHours(userId);
            
            double riskScore = 0.0;
            
            // Very early morning hours (2 AM - 6 AM) are higher risk
            if (hour >= 2 && hour <= 6) {
                riskScore += 0.6;
            }
            
            // Late night hours (11 PM - 2 AM) are medium risk
            if (hour >= 23 || hour <= 2) {
                riskScore += 0.3;
            }
            
            // Check if this is outside user's normal pattern
            if (!typicalHours.isEmpty() && !typicalHours.contains(hour)) {
                riskScore += 0.4;
            }
            
            // Weekend vs weekday pattern
            boolean isWeekend = requestTime.getDayOfWeek().getValue() >= 6;
            boolean userTypicallyUsesWeekends = fraudDetectionService.userTypicallyTransactsOnWeekends(userId);
            
            if (isWeekend && !userTypicallyUsesWeekends) {
                riskScore += 0.2;
            }
            
            return Math.min(riskScore, 1.0);
            
        } catch (Exception e) {
            log.error("Error in time risk analysis: userId={}, error={}", userId, e.getMessage(), e);
            return 0.2; // Low risk on error
        }
    }
    
    private double analyzeAmountRisk(String userId, BigDecimal amount) {
        try {
            // Get user's typical transaction amounts
            BigDecimal avgAmount = fraudDetectionService.getUserAverageTransactionAmount(userId, "ATM_WITHDRAWAL", 30);
            BigDecimal maxAmount = fraudDetectionService.getUserMaxTransactionAmount(userId, "ATM_WITHDRAWAL", 30);
            
            double riskScore = 0.0;
            
            // Check against daily withdrawal limits
            if (amount.compareTo(BigDecimal.valueOf(1000)) > 0) {
                riskScore += 0.5;
            }
            
            // Check against user's typical amounts
            if (avgAmount != null && amount.compareTo(avgAmount.multiply(BigDecimal.valueOf(3))) > 0) {
                riskScore += 0.4;
            }
            
            // Check against user's historical maximum
            if (maxAmount != null && amount.compareTo(maxAmount.multiply(BigDecimal.valueOf(1.5))) > 0) {
                riskScore += 0.3;
            }
            
            // Very small amounts can also be suspicious (testing)
            if (amount.compareTo(BigDecimal.valueOf(10)) < 0) {
                riskScore += 0.2;
            }
            
            return Math.min(riskScore, 1.0);
            
        } catch (Exception e) {
            log.error("Error in amount risk analysis: userId={}, error={}", userId, e.getMessage(), e);
            return 0.2; // Low risk on error
        }
    }
    
    private double analyzeAuthenticationRisk(boolean pinVerified, boolean cardPresent, String cardNumber) {
        double riskScore = 0.0;
        
        // PIN not verified is high risk
        if (!pinVerified) {
            riskScore += 0.8;
        }
        
        // Card not present is high risk
        if (!cardPresent) {
            riskScore += 0.7;
        }
        
        // Check if card is reported as compromised
        if (fraudDetectionService.isCardCompromised(cardNumber)) {
            riskScore = 1.0; // Maximum risk
        }
        
        // Check for recent PIN failures
        int recentPinFailures = fraudDetectionService.getRecentPinFailures(cardNumber, 24);
        if (recentPinFailures >= 3) {
            riskScore += 0.5;
        }
        
        return Math.min(riskScore, 1.0);
    }
    
    private void publishFraudResult(String withdrawalId, ATMFraudResult result) {
        try {
            // In production, this would publish to a Kafka topic for the ATM service
            fraudDetectionService.publishATMFraudResult(withdrawalId, result);
            
            log.debug("Published fraud result for withdrawal: {}", withdrawalId);
        } catch (Exception e) {
            log.error("Error publishing fraud result: withdrawalId={}, error={}", withdrawalId, e.getMessage(), e);
        }
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula for calculating distance between two points
        final int EARTH_RADIUS = 6371; // Earth's radius in kilometers
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
}