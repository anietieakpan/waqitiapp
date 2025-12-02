package com.waqiti.common.events.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Unified audit event model for compliance tracking and event streaming
 * Consolidates all audit logging requirements across the platform
 */
@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@Jacksonized
public class AuditEvent {
    // Core identification fields
    private String eventId;
    private String eventType;
    private String eventCategory;
    private String eventName;
    
    // User and authentication context
    private String userId;
    private List<String> userRoles;
    private Boolean authorized;
    
    // Resource information
    private String resource;
    private String resourceType;
    private String entityType;
    private String entityId;
    
    // Action and outcome
    private String action;
    private String outcome;
    private Boolean success;
    
    // Request context
    private String ipAddress;
    private String userAgent;
    private String requestMethod;
    private String requestUri;
    private String sessionId;
    
    // Service context
    private String serviceName;
    private String serviceVersion;
    
    // Geographic context
    private String location;
    private String country;
    private String city;
    private String deviceFingerprint;
    
    // Risk and compliance
    private Integer riskScore;
    private String riskLevel;
    private String complianceCategory;
    private String complianceRule;
    private boolean retainIndefinitely;
    
    // Performance metrics
    private Long duration;
    
    // Error information
    private String errorMessage;
    private String errorCode;
    
    // Additional context
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant timestamp;
    
    private Map<String, Object> details;
    private Map<String, Object> metadata;
    
    /**
     * Risk levels for audit events
     */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Get risk level based on risk score or explicit level
     */
    @JsonIgnore
    public RiskLevel getRiskLevelEnum() {
        if (riskLevel != null) {
            try {
                return RiskLevel.valueOf(riskLevel.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Fall through to calculate from score
            }
        }
        
        if (riskScore == null) return RiskLevel.LOW;
        if (riskScore >= 8) return RiskLevel.CRITICAL;
        if (riskScore >= 6) return RiskLevel.HIGH;
        if (riskScore >= 4) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
    
    /**
     * Check if operation was successful
     */
    @JsonIgnore
    public boolean isSuccess() {
        if (success != null) return success;
        if (authorized != null) return authorized;
        if (outcome != null) {
            return "SUCCESS".equalsIgnoreCase(outcome) || 
                   "COMPLETED".equalsIgnoreCase(outcome) ||
                   "ALLOWED".equalsIgnoreCase(outcome);
        }
        return true;
    }
    
    /**
     * Get resource type (backward compatibility)
     */
    @JsonIgnore
    public String getResourceType() {
        return resourceType != null ? resourceType : entityType;
    }
    
    /**
     * Get event category (backward compatibility)
     */
    @JsonIgnore
    public String getEventCategory() {
        return eventCategory != null ? eventCategory : 
               complianceCategory != null ? complianceCategory : 
               eventType;
    }
    
    // Explicit getters and setters for critical fields
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public List<String> getUserRoles() { return userRoles; }
    public void setUserRoles(List<String> userRoles) { this.userRoles = userRoles; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    // Additional explicit getters and setters for remaining fields
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public void setEventCategory(String eventCategory) { this.eventCategory = eventCategory; }
    
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    
    public Boolean getAuthorized() { return authorized; }
    public void setAuthorized(Boolean authorized) { this.authorized = authorized; }
    
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    
    public String getRequestUri() { return requestUri; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public String getServiceVersion() { return serviceVersion; }
    public void setServiceVersion(String serviceVersion) { this.serviceVersion = serviceVersion; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public String getComplianceCategory() { return complianceCategory; }
    public void setComplianceCategory(String complianceCategory) { this.complianceCategory = complianceCategory; }
    
    public String getComplianceRule() { return complianceRule; }
    public void setComplianceRule(String complianceRule) { this.complianceRule = complianceRule; }
    
    public boolean isRetainIndefinitely() { return retainIndefinitely; }
    public void setRetainIndefinitely(boolean retainIndefinitely) { this.retainIndefinitely = retainIndefinitely; }
    
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}