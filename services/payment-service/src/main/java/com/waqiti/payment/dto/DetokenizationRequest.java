package com.waqiti.payment.dto;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;
import java.util.UUID;

/**
 * CRITICAL: PCI DSS Compliant Detokenization Request DTO
 * 
 * This DTO handles detokenization requests with strict access controls:
 * - Validates authorized purposes only
 * - Enforces user access control
 * - Maintains audit trail
 * - Supports compliance requirements
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DetokenizationRequest {
    
    /**
     * Token to be detokenized
     * CRITICAL: Must be valid and owned by the requesting user
     */
    @NotBlank(message = "Token is required")
    @Size(min = 10, max = 50, message = "Token must be between 10 and 50 characters")
    @JsonProperty("token")
    private String token;
    
    /**
     * User ID requesting detokenization
     * CRITICAL: Required for access control
     */
    @NotNull(message = "User ID is required")
    @JsonProperty("userId")
    private UUID userId;
    
    /**
     * Purpose of detokenization
     * CRITICAL: Must be from authorized list for compliance
     */
    @NotBlank(message = "Purpose is required")
    @Pattern(regexp = "^(PAYMENT_PROCESSING|FRAUD_INVESTIGATION|COMPLIANCE_AUDIT|DISPUTE_RESOLUTION|REGULATORY_REPORTING)$",
             message = "Purpose must be one of: PAYMENT_PROCESSING, FRAUD_INVESTIGATION, COMPLIANCE_AUDIT, DISPUTE_RESOLUTION, REGULATORY_REPORTING")
    @JsonProperty("purpose")
    private String purpose;
    
    /**
     * Requester ID (system or user performing the detokenization)
     * For audit trail and access control
     */
    @NotNull(message = "Requester ID is required")
    @JsonProperty("requesterId")
    private UUID requesterId;
    
    /**
     * Request source system
     * For tracking which system is requesting detokenization
     */
    @NotBlank(message = "Source system is required")
    @Size(max = 50, message = "Source system must not exceed 50 characters")
    @JsonProperty("sourceSystem")
    private String sourceSystem;
    
    /**
     * Transaction ID (if applicable)
     * Links detokenization to specific transaction
     */
    @JsonProperty("transactionId")
    private UUID transactionId;
    
    /**
     * Payment ID (if applicable)
     * Links detokenization to specific payment
     */
    @JsonProperty("paymentId")
    private UUID paymentId;
    
    /**
     * Client IP address for security tracking
     */
    @JsonProperty("clientIpAddress")
    private String clientIpAddress;
    
    /**
     * User agent for device tracking
     */
    @Size(max = 500, message = "User agent must not exceed 500 characters")
    @JsonProperty("userAgent")
    private String userAgent;
    
    /**
     * Session ID for correlation
     */
    @JsonProperty("sessionId")
    private String sessionId;
    
    /**
     * Authorization token/session for elevated access
     * Required for sensitive purposes like fraud investigation
     */
    @JsonProperty("authorizationToken")
    private String authorizationToken;
    
    /**
     * Urgency level
     * For prioritizing fraud investigations and compliance requests
     */
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|CRITICAL)$",
             message = "Urgency must be LOW, MEDIUM, HIGH, or CRITICAL")
    @Builder.Default
    @JsonProperty("urgency")
    private String urgency = "MEDIUM";
    
    /**
     * Additional context for the request
     */
    @Size(max = 500, message = "Context must not exceed 500 characters")
    @JsonProperty("context")
    private String context;
    
    /**
     * Compliance flags
     */
    @Builder.Default
    @JsonProperty("auditRequired")
    private boolean auditRequired = true;
    
    @Builder.Default
    @JsonProperty("pciCompliant")
    private boolean pciCompliant = true;
    
    /**
     * Time constraints
     */
    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Max(value = 300, message = "Timeout must not exceed 300 seconds")
    @Builder.Default
    @JsonProperty("timeoutSeconds")
    private Integer timeoutSeconds = 30;
    
    /**
     * Environment
     */
    @Pattern(regexp = "^(PRODUCTION|STAGING|SANDBOX)$",
             message = "Environment must be PRODUCTION, STAGING, or SANDBOX")
    @Builder.Default
    @JsonProperty("environment")
    private String environment = "PRODUCTION";
    
    /**
     * Additional metadata
     */
    @Size(max = 1000, message = "Metadata must not exceed 1000 characters")
    @JsonProperty("metadata")
    private String metadata;
    
    /**
     * Validation methods
     */
    
    /**
     * Validate request for PCI compliance
     */
    public void validatePCICompliance() {
        if (!pciCompliant) {
            throw new IllegalStateException("PCI compliance must be enabled for detokenization");
        }
        
        validatePurposeAuthorization();
        validateRequesterAccess();
    }
    
    /**
     * Validate purpose authorization
     */
    private void validatePurposeAuthorization() {
        // High-security purposes require additional validation
        if (isHighSecurityPurpose() && authorizationToken == null) {
            throw new IllegalArgumentException("Authorization token required for " + purpose);
        }
        
        // Compliance purposes must have audit enabled
        if (isCompliancePurpose() && !auditRequired) {
            throw new IllegalArgumentException("Audit required for compliance purpose: " + purpose);
        }
    }
    
    /**
     * Validate requester access permissions
     */
    private void validateRequesterAccess() {
        if (requesterId == null) {
            throw new IllegalArgumentException("Requester ID is required for access control");
        }
        
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("Source system is required for audit trail");
        }
    }
    
    /**
     * Check if this is a high-security purpose
     */
    public boolean isHighSecurityPurpose() {
        return "FRAUD_INVESTIGATION".equals(purpose) || 
               "COMPLIANCE_AUDIT".equals(purpose) ||
               "REGULATORY_REPORTING".equals(purpose);
    }
    
    /**
     * Check if this is a compliance-related purpose
     */
    public boolean isCompliancePurpose() {
        return "COMPLIANCE_AUDIT".equals(purpose) || 
               "REGULATORY_REPORTING".equals(purpose) ||
               "DISPUTE_RESOLUTION".equals(purpose);
    }
    
    /**
     * Check if this is an urgent request
     */
    public boolean isUrgentRequest() {
        return "HIGH".equals(urgency) || "CRITICAL".equals(urgency);
    }
    
    /**
     * Check if this is a payment processing request
     */
    public boolean isPaymentProcessing() {
        return "PAYMENT_PROCESSING".equals(purpose);
    }
    
    /**
     * Get priority score for processing queue
     */
    public int getPriorityScore() {
        int score = 0;
        
        // Base priority by purpose
        switch (purpose) {
            case "PAYMENT_PROCESSING":
                score += 100;
                break;
            case "FRAUD_INVESTIGATION":
                score += 80;
                break;
            case "COMPLIANCE_AUDIT":
                score += 60;
                break;
            case "DISPUTE_RESOLUTION":
                score += 40;
                break;
            case "REGULATORY_REPORTING":
                score += 20;
                break;
        }
        
        // Urgency modifier
        switch (urgency) {
            case "CRITICAL":
                score += 50;
                break;
            case "HIGH":
                score += 30;
                break;
            case "MEDIUM":
                score += 10;
                break;
            case "LOW":
                score += 0;
                break;
        }
        
        return score;
    }
    
    /**
     * Get display summary for logging
     */
    public String getDisplaySummary() {
        return String.format("DetokenizationRequest{userId=%s, purpose=%s, urgency=%s, source=%s}", 
            userId, purpose, urgency, sourceSystem);
    }
    
    /**
     * Get masked token for logging
     */
    public String getMaskedToken() {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
    
    /**
     * Create audit summary
     */
    public String getAuditSummary() {
        return String.format("Detokenization{token=%s, userId=%s, purpose=%s, requester=%s, source=%s}", 
            getMaskedToken(), userId, purpose, requesterId, sourceSystem);
    }
    
    /**
     * Create a safe copy for logging (no sensitive data)
     */
    public DetokenizationRequest createSafeCopy() {
        return DetokenizationRequest.builder()
            .userId(userId)
            .purpose(purpose)
            .requesterId(requesterId)
            .sourceSystem(sourceSystem)
            .transactionId(transactionId)
            .paymentId(paymentId)
            .clientIpAddress(clientIpAddress)
            .userAgent(userAgent)
            .sessionId(sessionId)
            .urgency(urgency)
            .context(context)
            .auditRequired(auditRequired)
            .pciCompliant(pciCompliant)
            .timeoutSeconds(timeoutSeconds)
            .environment(environment)
            .metadata(metadata)
            // Exclude token and authorizationToken
            .build();
    }
    
    /**
     * PCI DSS Compliant toString() - NO SENSITIVE DATA
     */
    @Override
    public String toString() {
        return String.format("DetokenizationRequest{userId=%s, purpose=%s, urgency=%s, " +
                           "source=%s, token=%s}", 
            userId, purpose, urgency, sourceSystem, getMaskedToken());
    }
}