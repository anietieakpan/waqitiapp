package com.waqiti.common.client;

import com.waqiti.common.api.StandardApiResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Security Service Client
 * 
 * Provides standardized communication with the Security Service
 */
@Component
@Slf4j
public class SecurityServiceClient extends ServiceClient {

    public SecurityServiceClient(RestTemplate restTemplate, 
                               @Value("${services.security-service.url}") String baseUrl) {
        super(restTemplate, baseUrl, "security-service");
    }

    /**
     * Analyze transaction for fraud
     */
    public CompletableFuture<ServiceResponse<FraudAnalysisResultDTO>> analyzeFraud(FraudAnalysisRequest request) {
        return post("/api/v1/fraud/analyze", request, FraudAnalysisResultDTO.class);
    }

    /**
     * Get fraud score for user
     */
    public CompletableFuture<ServiceResponse<UserFraudScoreDTO>> getUserFraudScore(UUID userId) {
        return get("/api/v1/fraud/users/" + userId + "/score", UserFraudScoreDTO.class, null);
    }

    /**
     * Report fraud incident
     */
    public CompletableFuture<ServiceResponse<FraudIncidentDTO>> reportFraud(FraudReportRequest request) {
        return post("/api/v1/fraud/report", request, FraudIncidentDTO.class);
    }

    /**
     * Update fraud rules
     */
    public CompletableFuture<ServiceResponse<FraudRuleDTO>> updateFraudRule(UUID ruleId, FraudRuleUpdateRequest request) {
        return put("/api/v1/fraud/rules/" + ruleId, request, FraudRuleDTO.class);
    }

    /**
     * Get active fraud rules
     */
    public CompletableFuture<ServiceResponse<List<FraudRuleDTO>>> getActiveFraudRules() {
        return getList("/api/v1/fraud/rules/active", 
            new ParameterizedTypeReference<StandardApiResponse<List<FraudRuleDTO>>>() {}, 
            null);
    }

    /**
     * Validate device fingerprint
     */
    public CompletableFuture<ServiceResponse<DeviceValidationResultDTO>> validateDevice(DeviceValidationRequest request) {
        return post("/api/v1/device/validate", request, DeviceValidationResultDTO.class);
    }

    /**
     * Register device
     */
    public CompletableFuture<ServiceResponse<DeviceRegistrationDTO>> registerDevice(DeviceRegistrationRequest request) {
        return post("/api/v1/device/register", request, DeviceRegistrationDTO.class);
    }

    /**
     * Get user devices
     */
    public CompletableFuture<ServiceResponse<List<UserDeviceDTO>>> getUserDevices(UUID userId) {
        return getList("/api/v1/device/users/" + userId + "/devices", 
            new ParameterizedTypeReference<StandardApiResponse<List<UserDeviceDTO>>>() {}, 
            null);
    }

    /**
     * Block device
     */
    public CompletableFuture<ServiceResponse<Void>> blockDevice(UUID deviceId, BlockDeviceRequest request) {
        return post("/api/v1/device/" + deviceId + "/block", request, Void.class);
    }

    /**
     * Validate location
     */
    public CompletableFuture<ServiceResponse<LocationValidationResultDTO>> validateLocation(LocationValidationRequest request) {
        return post("/api/v1/location/validate", request, LocationValidationResultDTO.class);
    }

    /**
     * Get location risk score
     */
    public CompletableFuture<ServiceResponse<LocationRiskDTO>> getLocationRisk(String ipAddress, String country) {
        Map<String, Object> queryParams = Map.of(
            "ip", ipAddress,
            "country", country
        );
        return get("/api/v1/location/risk", LocationRiskDTO.class, queryParams);
    }

    /**
     * Check velocity limits
     */
    public CompletableFuture<ServiceResponse<VelocityCheckResultDTO>> checkVelocity(VelocityCheckRequest request) {
        return post("/api/v1/velocity/check", request, VelocityCheckResultDTO.class);
    }

    /**
     * Update velocity limits
     */
    public CompletableFuture<ServiceResponse<VelocityLimitsDTO>> updateVelocityLimits(UUID userId, VelocityLimitsUpdateRequest request) {
        return put("/api/v1/velocity/users/" + userId + "/limits", request, VelocityLimitsDTO.class);
    }

    /**
     * Get security alerts for user
     */
    public CompletableFuture<ServiceResponse<List<SecurityAlertDTO>>> getSecurityAlerts(UUID userId, SecurityAlertRequest request) {
        Map<String, Object> queryParams = Map.of(
            "severity", request.getSeverity() != null ? request.getSeverity() : "",
            "type", request.getType() != null ? request.getType() : "",
            "fromDate", request.getFromDate() != null ? request.getFromDate().toString() : "",
            "toDate", request.getToDate() != null ? request.getToDate().toString() : "",
            "page", request.getPage(),
            "size", request.getSize()
        );
        return getList("/api/v1/alerts/users/" + userId, 
            new ParameterizedTypeReference<StandardApiResponse<List<SecurityAlertDTO>>>() {}, 
            queryParams);
    }

    /**
     * Create security alert
     */
    public CompletableFuture<ServiceResponse<SecurityAlertDTO>> createSecurityAlert(CreateSecurityAlertRequest request) {
        return post("/api/v1/alerts", request, SecurityAlertDTO.class);
    }

    /**
     * Acknowledge security alert
     */
    public CompletableFuture<ServiceResponse<Void>> acknowledgeAlert(UUID alertId, AlertAcknowledgmentRequest request) {
        return post("/api/v1/alerts/" + alertId + "/acknowledge", request, Void.class);
    }

    /**
     * Perform risk assessment
     */
    public CompletableFuture<ServiceResponse<RiskAssessmentResultDTO>> performRiskAssessment(RiskAssessmentRequest request) {
        return post("/api/v1/risk/assess", request, RiskAssessmentResultDTO.class);
    }

    /**
     * Get user risk profile
     */
    public CompletableFuture<ServiceResponse<UserRiskProfileDTO>> getUserRiskProfile(UUID userId) {
        return get("/api/v1/risk/users/" + userId + "/profile", UserRiskProfileDTO.class, null);
    }

    /**
     * Update risk profile
     */
    public CompletableFuture<ServiceResponse<UserRiskProfileDTO>> updateRiskProfile(UUID userId, RiskProfileUpdateRequest request) {
        return put("/api/v1/risk/users/" + userId + "/profile", request, UserRiskProfileDTO.class);
    }

    /**
     * Check compliance rules
     */
    public CompletableFuture<ServiceResponse<ComplianceCheckResultDTO>> checkCompliance(ComplianceCheckRequest request) {
        return post("/api/v1/compliance/check", request, ComplianceCheckResultDTO.class);
    }

    /**
     * Get compliance status
     */
    public CompletableFuture<ServiceResponse<ComplianceStatusDTO>> getComplianceStatus(UUID userId) {
        return get("/api/v1/compliance/users/" + userId + "/status", ComplianceStatusDTO.class, null);
    }

    /**
     * Generate security report
     */
    public CompletableFuture<ServiceResponse<SecurityReportDTO>> generateSecurityReport(SecurityReportRequest request) {
        return post("/api/v1/reports/security", request, SecurityReportDTO.class);
    }

    /**
     * Validate authentication token
     */
    public CompletableFuture<ServiceResponse<TokenValidationResultDTO>> validateToken(String token) {
        Map<String, Object> request = Map.of("token", token);
        return post("/api/v1/auth/validate", request, TokenValidationResultDTO.class);
    }

    /**
     * Revoke authentication token
     */
    public CompletableFuture<ServiceResponse<Void>> revokeToken(String token, String reason) {
        Map<String, Object> request = Map.of(
            "token", token,
            "reason", reason
        );
        return post("/api/v1/auth/revoke", request, Void.class);
    }

    @Override
    protected String getCurrentCorrelationId() {
        return org.slf4j.MDC.get("correlationId");
    }

    @Override
    protected String getCurrentAuthToken() {
        return org.springframework.security.core.context.SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getCredentials()
            .toString();
    }
    
    /**
     * Verify user token - helper method for AccountService
     */
    public boolean verifyUserToken(String userId, String token) {
        try {
            TokenValidationResultDTO result = validateToken(token).get().getData();
            return result != null && result.isValid() && userId.equals(result.getUserId().toString());
        } catch (Exception e) {
            log.error("Error verifying token for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Verify security answers - helper method for AccountService
     */
    public boolean verifySecurityAnswers(com.waqiti.common.security.model.UserContext userContext, Map<String, String> answers) {
        // This would typically make a call to the security service to verify answers
        // For now, returning placeholder implementation
        return answers != null && !answers.isEmpty();
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudAnalysisResultDTO {
        private UUID analysisId;
        private UUID transactionId;
        private BigDecimal fraudScore;
        private String riskLevel;
        private String decision;
        private List<String> triggeredRules;
        private List<String> riskFactors;
        private String explanation;
        private Map<String, Object> details;
        private LocalDateTime analyzedAt;
        private String modelVersion;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserFraudScoreDTO {
        private UUID userId;
        private BigDecimal score;
        private String riskLevel;
        private LocalDateTime lastCalculated;
        private Map<String, BigDecimal> scoreFactors;
        private List<String> riskIndicators;
        private int transactionCount;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudIncidentDTO {
        private UUID id;
        private UUID userId;
        private UUID transactionId;
        private String type;
        private String severity;
        private String status;
        private String description;
        private LocalDateTime reportedAt;
        private LocalDateTime resolvedAt;
        private String resolution;
        private Map<String, Object> evidence;
        private String reportedBy;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudRuleDTO {
        private UUID id;
        private String name;
        private String description;
        private String condition;
        private String action;
        private BigDecimal threshold;
        private boolean active;
        private int priority;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Map<String, Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeviceValidationResultDTO {
        private String deviceId;
        private boolean valid;
        private String trustLevel;
        private BigDecimal riskScore;
        private List<String> riskFactors;
        private boolean isNewDevice;
        private boolean isCompromised;
        private LocalDateTime lastSeen;
        private Map<String, Object> deviceInfo;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeviceRegistrationDTO {
        private UUID id;
        private String deviceId;
        private UUID userId;
        private String deviceType;
        private String trustLevel;
        private boolean verified;
        private LocalDateTime registeredAt;
        private LocalDateTime lastUsed;
        private Map<String, Object> deviceInfo;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserDeviceDTO {
        private UUID id;
        private String deviceId;
        private String deviceType;
        private String name;
        private String trustLevel;
        private boolean active;
        private boolean blocked;
        private LocalDateTime registeredAt;
        private LocalDateTime lastUsed;
        private String ipAddress;
        private String location;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LocationValidationResultDTO {
        private String ipAddress;
        private String country;
        private String region;
        private String city;
        private boolean valid;
        private BigDecimal riskScore;
        private String riskLevel;
        private List<String> riskFactors;
        private boolean isVpn;
        private boolean isProxy;
        private Map<String, Object> locationData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LocationRiskDTO {
        private String location;
        private BigDecimal riskScore;
        private String riskLevel;
        private List<String> riskFactors;
        private boolean isHighRisk;
        private boolean isSanctioned;
        private Map<String, Object> riskData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VelocityCheckResultDTO {
        private UUID userId;
        private String checkType;
        private boolean withinLimits;
        private BigDecimal currentAmount;
        private BigDecimal limitAmount;
        private int currentCount;
        private int limitCount;
        private String timeWindow;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
        private List<String> violations;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VelocityLimitsDTO {
        private UUID userId;
        private BigDecimal dailyAmountLimit;
        private BigDecimal weeklyAmountLimit;
        private BigDecimal monthlyAmountLimit;
        private int dailyCountLimit;
        private int weeklyCountLimit;
        private int monthlyCountLimit;
        private LocalDateTime lastUpdated;
        private String updatedBy;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecurityAlertDTO {
        private UUID id;
        private UUID userId;
        private String type;
        private String severity;
        private String status;
        private String title;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime acknowledgedAt;
        private String acknowledgedBy;
        private Map<String, Object> details;
        private List<String> actions;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RiskAssessmentResultDTO {
        private UUID assessmentId;
        private UUID userId;
        private BigDecimal overallRiskScore;
        private String riskLevel;
        private Map<String, BigDecimal> categoryScores;
        private List<String> riskFactors;
        private List<String> mitigationRecommendations;
        private LocalDateTime assessedAt;
        private String assessmentType;
        private Map<String, Object> details;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserRiskProfileDTO {
        private UUID userId;
        private BigDecimal riskScore;
        private String riskLevel;
        private String category;
        private LocalDateTime lastAssessed;
        private LocalDateTime nextReview;
        private Map<String, BigDecimal> riskFactors;
        private List<String> mitigationMeasures;
        private boolean requiresEnhancedDueDiligence;
        private Map<String, Object> profile;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceCheckResultDTO {
        private UUID checkId;
        private UUID userId;
        private boolean compliant;
        private String status;
        private List<String> violations;
        private List<String> warnings;
        private Map<String, Object> checkResults;
        private LocalDateTime checkedAt;
        private String checkedBy;
        private List<String> requiredActions;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceStatusDTO {
        private UUID userId;
        private String overallStatus;
        private Map<String, String> regulationStatus;
        private LocalDateTime lastChecked;
        private LocalDateTime nextReview;
        private List<String> pendingActions;
        private List<String> warnings;
        private Map<String, Object> details;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecurityReportDTO {
        private UUID reportId;
        private String type;
        private String period;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private Map<String, Object> metrics;
        private List<String> findings;
        private List<String> recommendations;
        private LocalDateTime generatedAt;
        private String generatedBy;
        private byte[] reportData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TokenValidationResultDTO {
        private boolean valid;
        private String status;
        private UUID userId;
        private List<String> scopes;
        private LocalDateTime issuedAt;
        private LocalDateTime expiresAt;
        private String issuer;
        private Map<String, Object> claims;
    }

    // Request DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudAnalysisRequest {
        private UUID transactionId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String type;
        private String deviceId;
        private String ipAddress;
        private String location;
        private Map<String, Object> transactionData;
        private Map<String, Object> userContext;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudReportRequest {
        private UUID userId;
        private UUID transactionId;
        private String type;
        private String severity;
        private String description;
        private Map<String, Object> evidence;
        private String reportedBy;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudRuleUpdateRequest {
        private String name;
        private String description;
        private String condition;
        private String action;
        private BigDecimal threshold;
        private boolean active;
        private int priority;
        private Map<String, Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeviceValidationRequest {
        private String deviceId;
        private UUID userId;
        private String deviceType;
        private String userAgent;
        private String ipAddress;
        private Map<String, Object> deviceInfo;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeviceRegistrationRequest {
        private String deviceId;
        private UUID userId;
        private String deviceType;
        private String deviceName;
        private String userAgent;
        private String ipAddress;
        private Map<String, Object> deviceInfo;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BlockDeviceRequest {
        private String reason;
        private String notes;
        private boolean permanent;
        private LocalDateTime blockedUntil;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LocationValidationRequest {
        private String ipAddress;
        private String country;
        private String region;
        private String city;
        private UUID userId;
        private Map<String, Object> context;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VelocityCheckRequest {
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String checkType;
        private String timeWindow;
        private Map<String, Object> context;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VelocityLimitsUpdateRequest {
        private BigDecimal dailyAmountLimit;
        private BigDecimal weeklyAmountLimit;
        private BigDecimal monthlyAmountLimit;
        private int dailyCountLimit;
        private int weeklyCountLimit;
        private int monthlyCountLimit;
        private String reason;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecurityAlertRequest {
        private String severity;
        private String type;
        private String status;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 20;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateSecurityAlertRequest {
        private UUID userId;
        private String type;
        private String severity;
        private String title;
        private String description;
        private Map<String, Object> details;
        private List<String> actions;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertAcknowledgmentRequest {
        private String acknowledgedBy;
        private String notes;
        private String action;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RiskAssessmentRequest {
        private UUID userId;
        private String assessmentType;
        private Map<String, Object> context;
        private List<String> factors;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RiskProfileUpdateRequest {
        private String riskLevel;
        private String category;
        private Map<String, BigDecimal> riskFactors;
        private List<String> mitigationMeasures;
        private boolean requiresEnhancedDueDiligence;
        private String updatedBy;
        private String reason;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceCheckRequest {
        private UUID userId;
        private String checkType;
        private List<String> regulations;
        private Map<String, Object> context;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecurityReportRequest {
        private String type;
        private String period;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private List<String> metrics;
        private String format;
        private Map<String, Object> filters;
    }
}