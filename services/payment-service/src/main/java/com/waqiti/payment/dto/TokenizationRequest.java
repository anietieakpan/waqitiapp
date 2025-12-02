package com.waqiti.payment.dto;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

/**
 * CRITICAL: PCI DSS Compliant Tokenization Request DTO
 * 
 * This DTO handles tokenization requests with strict PCI compliance:
 * - Validates all required fields
 * - Ensures proper user identification
 * - Supports various tokenization options
 * - Maintains audit trail requirements
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TokenizationRequest {
    
    /**
     * User ID requesting tokenization
     * CRITICAL: Required for access control and data isolation
     */
    @NotNull(message = "User ID is required")
    @JsonProperty("userId")
    private UUID userId;
    
    /**
     * Card details to be tokenized
     * CRITICAL: Contains sensitive PAN data
     */
    @NotNull(message = "Card details are required")
    @Valid
    @JsonProperty("cardDetails")
    private CardDetails cardDetails;
    
    /**
     * Merchant ID (if applicable)
     * For merchant-specific tokenization
     */
    @JsonProperty("merchantId")
    private UUID merchantId;
    
    /**
     * Force creation of new token even if one exists
     * Default: false (reuse existing tokens)
     */
    @Builder.Default
    @JsonProperty("forceNewToken")
    private boolean forceNewToken = false;
    
    /**
     * Token type requested
     * Options: STANDARD, TEMPORARY, NETWORK, SINGLE_USE
     */
    @NotBlank(message = "Token type is required")
    @Pattern(regexp = "^(STANDARD|TEMPORARY|NETWORK|SINGLE_USE)$", 
             message = "Token type must be STANDARD, TEMPORARY, NETWORK, or SINGLE_USE")
    @Builder.Default
    @JsonProperty("tokenType")
    private String tokenType = "STANDARD";
    
    /**
     * Security level requested
     * Options: STANDARD, HIGH, CRITICAL
     */
    @Pattern(regexp = "^(STANDARD|HIGH|CRITICAL)$", 
             message = "Security level must be STANDARD, HIGH, or CRITICAL")
    @Builder.Default
    @JsonProperty("securityLevel")
    private String securityLevel = "STANDARD";
    
    /**
     * Purpose of tokenization
     * For audit and compliance tracking
     */
    @NotBlank(message = "Purpose is required")
    @Size(max = 100, message = "Purpose must not exceed 100 characters")
    @JsonProperty("purpose")
    private String purpose;
    
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
     * Device fingerprint for fraud prevention
     */
    @Size(max = 100, message = "Device fingerprint must not exceed 100 characters")
    @JsonProperty("deviceFingerprint")
    private String deviceFingerprint;
    
    /**
     * Geographic location (optional)
     */
    @Size(max = 100, message = "Location must not exceed 100 characters")
    @JsonProperty("location")
    private String location;
    
    /**
     * Network provider for network tokens
     * Required when tokenType is NETWORK
     */
    @JsonProperty("networkProvider")
    private String networkProvider;
    
    /**
     * Token expiry in minutes for temporary tokens
     * Required when tokenType is TEMPORARY
     */
    @Min(value = 1, message = "Token expiry must be at least 1 minute")
    @Max(value = 1440, message = "Token expiry must not exceed 1440 minutes (24 hours)")
    @JsonProperty("tokenExpiryMinutes")
    private Integer tokenExpiryMinutes;
    
    /**
     * Maximum usage count for single-use tokens
     * Required when tokenType is SINGLE_USE
     */
    @Min(value = 1, message = "Max usage count must be at least 1")
    @Max(value = 10, message = "Max usage count must not exceed 10")
    @JsonProperty("maxUsageCount")
    private Integer maxUsageCount;
    
    /**
     * Enable format-preserving tokenization
     * Maintains PAN structure in token
     */
    @Builder.Default
    @JsonProperty("formatPreserving")
    private boolean formatPreserving = true;
    
    /**
     * Enable Luhn compliance for format-preserving tokens
     */
    @Builder.Default
    @JsonProperty("luhnCompliant")
    private boolean luhnCompliant = true;
    
    /**
     * Custom metadata for business use cases
     */
    @Size(max = 1000, message = "Metadata must not exceed 1000 characters")
    @JsonProperty("metadata")
    private String metadata;
    
    /**
     * Compliance requirements
     */
    @Builder.Default
    @JsonProperty("pciCompliant")
    private boolean pciCompliant = true;
    
    /**
     * Audit all operations for this token
     */
    @Builder.Default
    @JsonProperty("auditAllOperations")
    private boolean auditAllOperations = true;
    
    /**
     * Environment (PRODUCTION, STAGING, SANDBOX)
     */
    @Pattern(regexp = "^(PRODUCTION|STAGING|SANDBOX)$", 
             message = "Environment must be PRODUCTION, STAGING, or SANDBOX")
    @Builder.Default
    @JsonProperty("environment")
    private String environment = "PRODUCTION";
    
    /**
     * Notification preferences for token events
     */
    @JsonProperty("notifyOnExpiry")
    @Builder.Default
    private boolean notifyOnExpiry = false;
    
    @JsonProperty("notifyOnUsage")
    @Builder.Default
    private boolean notifyOnUsage = false;
    
    /**
     * Validation methods
     */
    
    /**
     * Validate request for PCI compliance
     */
    public void validatePCICompliance() {
        // Ensure card details are valid
        if (cardDetails != null) {
            cardDetails.validatePCICompliance();
        }
        
        // Validate token type specific requirements
        validateTokenTypeRequirements();
        
        // Ensure compliance flag is set
        if (!pciCompliant) {
            throw new IllegalStateException("PCI compliance must be enabled for tokenization");
        }
    }
    
    /**
     * Validate token type specific requirements
     */
    private void validateTokenTypeRequirements() {
        switch (tokenType) {
            case "NETWORK":
                if (networkProvider == null || networkProvider.trim().isEmpty()) {
                    throw new IllegalArgumentException("Network provider is required for NETWORK token type");
                }
                break;
                
            case "TEMPORARY":
                if (tokenExpiryMinutes == null || tokenExpiryMinutes <= 0) {
                    throw new IllegalArgumentException("Token expiry minutes is required for TEMPORARY token type");
                }
                break;
                
            case "SINGLE_USE":
                if (maxUsageCount == null || maxUsageCount <= 0) {
                    throw new IllegalArgumentException("Max usage count is required for SINGLE_USE token type");
                }
                break;
        }
    }
    
    /**
     * Check if request is for high-value transaction tokenization
     */
    public boolean isHighValueTokenization() {
        return "HIGH".equals(securityLevel) || "CRITICAL".equals(securityLevel);
    }
    
    /**
     * Check if request requires enhanced security
     */
    public boolean requiresEnhancedSecurity() {
        return "CRITICAL".equals(securityLevel) || 
               "NETWORK".equals(tokenType) ||
               purpose.contains("HIGH_VALUE");
    }
    
    /**
     * Get display summary for logging
     */
    public String getDisplaySummary() {
        return String.format("TokenizationRequest{userId=%s, tokenType=%s, securityLevel=%s, purpose=%s}", 
            userId, tokenType, securityLevel, purpose);
    }
    
    /**
     * Create a safe copy for logging (no sensitive data)
     */
    public TokenizationRequest createSafeCopy() {
        return TokenizationRequest.builder()
            .userId(userId)
            .merchantId(merchantId)
            .forceNewToken(forceNewToken)
            .tokenType(tokenType)
            .securityLevel(securityLevel)
            .purpose(purpose)
            .clientIpAddress(clientIpAddress)
            .userAgent(userAgent)
            .deviceFingerprint(deviceFingerprint)
            .location(location)
            .networkProvider(networkProvider)
            .tokenExpiryMinutes(tokenExpiryMinutes)
            .maxUsageCount(maxUsageCount)
            .formatPreserving(formatPreserving)
            .luhnCompliant(luhnCompliant)
            .metadata(metadata)
            .pciCompliant(pciCompliant)
            .auditAllOperations(auditAllOperations)
            .environment(environment)
            .notifyOnExpiry(notifyOnExpiry)
            .notifyOnUsage(notifyOnUsage)
            // Exclude cardDetails to prevent sensitive data exposure
            .build();
    }
    
    /**
     * PCI DSS Compliant toString() - NO SENSITIVE DATA
     */
    @Override
    public String toString() {
        return String.format("TokenizationRequest{userId=%s, tokenType=%s, securityLevel=%s, purpose=%s, " +
                           "cardType=%s, environment=%s}", 
            userId, tokenType, securityLevel, purpose,
            cardDetails != null ? cardDetails.getCardType() : "UNKNOWN",
            environment);
    }
}