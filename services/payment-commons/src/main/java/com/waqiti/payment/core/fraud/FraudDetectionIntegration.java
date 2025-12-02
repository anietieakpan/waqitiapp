package com.waqiti.payment.core.fraud;

import com.waqiti.payment.client.FraudDetectionClient;
import com.waqiti.payment.client.dto.FraudScore;
import com.waqiti.payment.core.integration.PaymentProcessingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fraud detection integration service
 * Orchestrates fraud detection across payment processing pipeline
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionIntegration {
    
    private final FraudDetectionClient fraudDetectionClient;
    
    /**
     * Perform comprehensive fraud evaluation
     */
    public FraudEvaluationResult evaluatePayment(PaymentProcessingRequest request) {
        log.info("Starting fraud evaluation for request: {}", request.getRequestId());
        
        try {
            // Perform instant transfer evaluation
            FraudScore fraudScore = fraudDetectionClient.evaluateInstantTransfer(
                request.getSenderId(),
                request.getRecipientId(),
                request.getAmount(),
                request.getPaymentType().name()
            ).getBody();
            
            // Build comprehensive result
            FraudEvaluationResult result = buildFraudEvaluationResult(fraudScore, request);
            
            log.info("Fraud evaluation completed for {}: risk={}, shouldBlock={}", 
                request.getRequestId(), result.getRiskLevel(), result.isShouldBlock());
            
            return result;
            
        } catch (Exception e) {
            log.error("Fraud evaluation failed for request: {}", request.getRequestId(), e);
            return createFallbackResult(request, e);
        }
    }
    
    /**
     * Async fraud evaluation
     */
    public CompletableFuture<FraudEvaluationResult> evaluatePaymentAsync(PaymentProcessingRequest request) {
        return CompletableFuture.supplyAsync(() -> evaluatePayment(request));
    }
    
    /**
     * Real-time fraud monitoring
     */
    public MonitoringResult monitorTransaction(PaymentProcessingRequest request) {
        log.debug("Monitoring transaction for fraud: {}", request.getRequestId());
        
        try {
            // Create monitoring request
            Map<String, Object> monitoringData = createMonitoringRequest(request);
            
            // Call fraud detection service
            Object result = fraudDetectionClient.monitorTransaction(monitoringData).getBody();
            
            return MonitoringResult.builder()
                .transactionId(request.getRequestId())
                .monitoringStatus(MonitoringStatus.ACTIVE)
                .alertsGenerated(0)
                .riskFactors(extractRiskFactors(result))
                .monitoredAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Transaction monitoring failed: {}", request.getRequestId(), e);
            return createFallbackMonitoringResult(request);
        }
    }
    
    /**
     * Get user risk profile
     */
    public UserRiskProfile getUserRiskProfile(UUID userId) {
        log.debug("Getting risk profile for user: {}", userId);
        
        try {
            Object userRiskScore = fraudDetectionClient.getUserRiskScore(userId).getBody();
            
            return UserRiskProfile.builder()
                .userId(userId)
                .riskScore(extractRiskScore(userRiskScore))
                .riskLevel(extractRiskLevel(userRiskScore))
                .lastUpdated(LocalDateTime.now())
                .factors(extractUserRiskFactors(userRiskScore))
                .build();
            
        } catch (Exception e) {
            log.error("Failed to get user risk profile: {}", userId, e);
            return createFallbackUserRiskProfile(userId);
        }
    }
    
    /**
     * Update user risk score after transaction
     */
    public void updateUserRiskScore(UUID userId, PaymentProcessingRequest request, boolean successful) {
        try {
            Map<String, Object> updateData = createRiskUpdateData(request, successful);
            fraudDetectionClient.updateUserRiskScore(userId, updateData);
            
            log.debug("Updated risk score for user {} after transaction {}", userId, request.getRequestId());
            
        } catch (Exception e) {
            log.error("Failed to update user risk score: {}", userId, e);
        }
    }
    
    /**
     * Check velocity limits
     */
    public VelocityCheckResult checkVelocityLimits(PaymentProcessingRequest request) {
        log.debug("Checking velocity limits for: {}", request.getRequestId());
        
        try {
            Map<String, Object> velocityRequest = createVelocityRequest(request);
            Object result = fraudDetectionClient.performVelocityCheck(velocityRequest).getBody();
            
            return VelocityCheckResult.builder()
                .passed(extractVelocityPassed(result))
                .dailyCount(extractDailyCount(result))
                .dailyAmount(extractDailyAmount(result))
                .weeklyCount(extractWeeklyCount(result))
                .weeklyAmount(extractWeeklyAmount(result))
                .monthlyCount(extractMonthlyCount(result))
                .monthlyAmount(extractMonthlyAmount(result))
                .violatedLimits(extractViolatedLimits(result))
                .build();
            
        } catch (Exception e) {
            log.error("Velocity check failed: {}", request.getRequestId(), e);
            return createFallbackVelocityResult();
        }
    }
    
    /**
     * Analyze geographic risk
     */
    public GeographicRiskResult analyzeGeographicRisk(PaymentProcessingRequest request) {
        if (request.getSecurityContext() == null || request.getSecurityContext().getIpAddress() == null) {
            return GeographicRiskResult.builder()
                .riskLevel("UNKNOWN")
                .reason("No location data available")
                .build();
        }
        
        try {
            Map<String, Object> geoRequest = createGeographicRequest(request);
            Object result = fraudDetectionClient.analyzeGeographicRisk(geoRequest).getBody();
            
            return GeographicRiskResult.builder()
                .riskLevel(extractGeoRiskLevel(result))
                .countryCode(extractCountryCode(result))
                .cityName(extractCityName(result))
                .distanceFromUsual(extractDistance(result))
                .isHighRiskCountry(extractHighRiskCountry(result))
                .reason(extractGeoReason(result))
                .build();
            
        } catch (Exception e) {
            log.error("Geographic risk analysis failed: {}", request.getRequestId(), e);
            return createFallbackGeographicResult();
        }
    }
    
    private FraudEvaluationResult buildFraudEvaluationResult(FraudScore fraudScore, PaymentProcessingRequest request) {
        return FraudEvaluationResult.builder()
            .requestId(request.getRequestId())
            .riskScore(fraudScore.getScore())
            .riskLevel(fraudScore.getRiskLevel())
            .shouldBlock(fraudScore.isShouldBlock())
            .reason(fraudScore.getReason())
            .confidence(calculateConfidence(fraudScore))
            .evaluatedAt(LocalDateTime.now())
            .details(fraudScore.getDetails())
            .recommendations(generateRecommendations(fraudScore))
            .build();
    }
    
    private FraudEvaluationResult createFallbackResult(PaymentProcessingRequest request, Exception error) {
        return FraudEvaluationResult.builder()
            .requestId(request.getRequestId())
            .riskScore(new BigDecimal("50.0")) // Conservative medium risk
            .riskLevel("MEDIUM")
            .shouldBlock(false) // Don't block on service failure
            .reason("Fraud service unavailable - conservative evaluation applied")
            .confidence(0.3) // Low confidence
            .evaluatedAt(LocalDateTime.now())
            .serviceError(error.getMessage())
            .fallback(true)
            .build();
    }
    
    private MonitoringResult createFallbackMonitoringResult(PaymentProcessingRequest request) {
        return MonitoringResult.builder()
            .transactionId(request.getRequestId())
            .monitoringStatus(MonitoringStatus.DEGRADED)
            .alertsGenerated(0)
            .monitoredAt(LocalDateTime.now())
            .error("Monitoring service unavailable")
            .build();
    }
    
    private UserRiskProfile createFallbackUserRiskProfile(UUID userId) {
        return UserRiskProfile.builder()
            .userId(userId)
            .riskScore(new BigDecimal("50.0"))
            .riskLevel("MEDIUM")
            .lastUpdated(LocalDateTime.now())
            .fallback(true)
            .build();
    }
    
    private VelocityCheckResult createFallbackVelocityResult() {
        return VelocityCheckResult.builder()
            .passed(true) // Allow transaction when service is down
            .fallback(true)
            .build();
    }
    
    private GeographicRiskResult createFallbackGeographicResult() {
        return GeographicRiskResult.builder()
            .riskLevel("MEDIUM")
            .reason("Geographic service unavailable")
            .fallback(true)
            .build();
    }
    
    // Helper methods for data extraction
    private Map<String, Object> createMonitoringRequest(PaymentProcessingRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", request.getRequestId());
        data.put("senderId", request.getSenderId());
        data.put("recipientId", request.getRecipientId());
        data.put("amount", request.getAmount());
        data.put("currency", request.getCurrency());
        data.put("paymentType", request.getPaymentType());
        
        if (request.getSecurityContext() != null) {
            data.put("securityContext", request.getSecurityContext());
        }
        
        return data;
    }
    
    private Map<String, Object> createRiskUpdateData(PaymentProcessingRequest request, boolean successful) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", request.getRequestId());
        data.put("amount", request.getAmount());
        data.put("successful", successful);
        data.put("timestamp", LocalDateTime.now());
        data.put("paymentType", request.getPaymentType());
        return data;
    }
    
    private Map<String, Object> createVelocityRequest(PaymentProcessingRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", request.getSenderId());
        data.put("amount", request.getAmount());
        data.put("currency", request.getCurrency());
        data.put("paymentType", request.getPaymentType());
        data.put("timestamp", LocalDateTime.now());
        return data;
    }
    
    private Map<String, Object> createGeographicRequest(PaymentProcessingRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", request.getSenderId());
        data.put("ipAddress", request.getSecurityContext().getIpAddress());
        data.put("amount", request.getAmount());
        data.put("timestamp", LocalDateTime.now());
        return data;
    }
    
    // Data extraction helper methods
    private Double calculateConfidence(FraudScore fraudScore) {
        if (fraudScore.getDetails() != null && fraudScore.getDetails().containsKey("confidence")) {
            return (Double) fraudScore.getDetails().get("confidence");
        }
        return 0.8; // Default confidence
    }
    
    private Map<String, String> generateRecommendations(FraudScore fraudScore) {
        Map<String, String> recommendations = new HashMap<>();
        
        if (fraudScore.isHighRisk()) {
            recommendations.put("action", "REQUIRE_ADDITIONAL_VERIFICATION");
            recommendations.put("method", "TWO_FACTOR_AUTHENTICATION");
        } else if ("MEDIUM".equals(fraudScore.getRiskLevel())) {
            recommendations.put("action", "MONITOR_CLOSELY");
            recommendations.put("method", "ENHANCED_MONITORING");
        } else {
            recommendations.put("action", "PROCEED");
            recommendations.put("method", "STANDARD_PROCESSING");
        }
        
        return recommendations;
    }
    
    // Extract methods for handling dynamic response objects
    private Map<String, Object> extractRiskFactors(Object result) {
        if (result instanceof Map) {
            return (Map<String, Object>) ((Map<?, ?>) result).get("riskFactors");
        }
        return new HashMap<>();
    }
    
    private BigDecimal extractRiskScore(Object userRiskScore) {
        if (userRiskScore instanceof Map) {
            Object score = ((Map<?, ?>) userRiskScore).get("riskScore");
            if (score instanceof Number) {
                return new BigDecimal(score.toString());
            }
        }
        return new BigDecimal("50.0");
    }
    
    private String extractRiskLevel(Object userRiskScore) {
        if (userRiskScore instanceof Map) {
            Object level = ((Map<?, ?>) userRiskScore).get("riskLevel");
            if (level instanceof String) {
                return (String) level;
            }
        }
        return "MEDIUM";
    }
    
    private Map<String, Object> extractUserRiskFactors(Object userRiskScore) {
        if (userRiskScore instanceof Map) {
            Object factors = ((Map<?, ?>) userRiskScore).get("factors");
            if (factors instanceof Map) {
                return (Map<String, Object>) factors;
            }
        }
        return new HashMap<>();
    }
    
    // Additional extraction methods for velocity and geographic data
    private Boolean extractVelocityPassed(Object result) {
        return extractBoolean(result, "velocityCheckPassed", true);
    }
    
    private Integer extractDailyCount(Object result) {
        return extractInteger(result, "dailyCount", 0);
    }
    
    private BigDecimal extractDailyAmount(Object result) {
        return extractBigDecimal(result, "dailyAmount", BigDecimal.ZERO);
    }
    
    private Integer extractWeeklyCount(Object result) {
        return extractInteger(result, "weeklyCount", 0);
    }
    
    private BigDecimal extractWeeklyAmount(Object result) {
        return extractBigDecimal(result, "weeklyAmount", BigDecimal.ZERO);
    }
    
    private Integer extractMonthlyCount(Object result) {
        return extractInteger(result, "monthlyCount", 0);
    }
    
    private BigDecimal extractMonthlyAmount(Object result) {
        return extractBigDecimal(result, "monthlyAmount", BigDecimal.ZERO);
    }
    
    private Map<String, String> extractViolatedLimits(Object result) {
        if (result instanceof Map) {
            Object limits = ((Map<?, ?>) result).get("violatedLimits");
            if (limits instanceof Map) {
                return (Map<String, String>) limits;
            }
        }
        return new HashMap<>();
    }
    
    private String extractGeoRiskLevel(Object result) {
        return extractString(result, "riskLevel", "MEDIUM");
    }
    
    private String extractCountryCode(Object result) {
        return extractString(result, "countryCode", "UNKNOWN");
    }
    
    private String extractCityName(Object result) {
        return extractString(result, "cityName", "UNKNOWN");
    }
    
    private Double extractDistance(Object result) {
        return extractDouble(result, "distanceFromUsual", 0.0);
    }
    
    private Boolean extractHighRiskCountry(Object result) {
        return extractBoolean(result, "isHighRiskCountry", false);
    }
    
    private String extractGeoReason(Object result) {
        return extractString(result, "reason", "No additional information");
    }
    
    // Generic extraction utility methods
    private Boolean extractBoolean(Object result, String key, Boolean defaultValue) {
        if (result instanceof Map) {
            Object value = ((Map<?, ?>) result).get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        return defaultValue;
    }
    
    private Integer extractInteger(Object result, String key, Integer defaultValue) {
        if (result instanceof Map) {
            Object value = ((Map<?, ?>) result).get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return defaultValue;
    }
    
    private Double extractDouble(Object result, String key, Double defaultValue) {
        if (result instanceof Map) {
            Object value = ((Map<?, ?>) result).get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return defaultValue;
    }
    
    private String extractString(Object result, String key, String defaultValue) {
        if (result instanceof Map) {
            Object value = ((Map<?, ?>) result).get(key);
            if (value instanceof String) {
                return (String) value;
            }
        }
        return defaultValue;
    }
    
    private BigDecimal extractBigDecimal(Object result, String key, BigDecimal defaultValue) {
        if (result instanceof Map) {
            Object value = ((Map<?, ?>) result).get(key);
            if (value instanceof Number) {
                return new BigDecimal(value.toString());
            }
        }
        return defaultValue;
    }
    
    // Result classes
    @lombok.Data
    @lombok.Builder
    public static class FraudEvaluationResult {
        private UUID requestId;
        private BigDecimal riskScore;
        private String riskLevel;
        private boolean shouldBlock;
        private String reason;
        private Double confidence;
        private LocalDateTime evaluatedAt;
        private Map<String, Object> details;
        private Map<String, String> recommendations;
        private String serviceError;
        private boolean fallback;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MonitoringResult {
        private UUID transactionId;
        private MonitoringStatus monitoringStatus;
        private int alertsGenerated;
        private Map<String, Object> riskFactors;
        private LocalDateTime monitoredAt;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class UserRiskProfile {
        private UUID userId;
        private BigDecimal riskScore;
        private String riskLevel;
        private LocalDateTime lastUpdated;
        private Map<String, Object> factors;
        private boolean fallback;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VelocityCheckResult {
        private boolean passed;
        private Integer dailyCount;
        private BigDecimal dailyAmount;
        private Integer weeklyCount;
        private BigDecimal weeklyAmount;
        private Integer monthlyCount;
        private BigDecimal monthlyAmount;
        private Map<String, String> violatedLimits;
        private boolean fallback;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class GeographicRiskResult {
        private String riskLevel;
        private String countryCode;
        private String cityName;
        private Double distanceFromUsual;
        private Boolean isHighRiskCountry;
        private String reason;
        private boolean fallback;
    }
    
    public enum MonitoringStatus {
        ACTIVE,
        DEGRADED,
        INACTIVE,
        ERROR
    }
}