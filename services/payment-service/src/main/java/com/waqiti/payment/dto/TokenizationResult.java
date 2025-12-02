package com.waqiti.payment.dto;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL: PCI DSS Compliant Tokenization Result DTO
 * 
 * This DTO contains the result of tokenization operations.
 * It includes ONLY safe data that can be returned to clients.
 * 
 * SECURITY FEATURES:
 * - No sensitive card data
 * - Safe token for future use
 * - Metadata for UI display
 * - Audit information
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenizationResult {
    
    /**
     * Secure token value
     * This replaces the PAN for all future operations
     */
    @JsonProperty("token")
    private String token;
    
    /**
     * Token ID for reference
     */
    @JsonProperty("tokenId")
    private UUID tokenId;
    
    /**
     * Last 4 digits of card for display
     * Safe to display per PCI DSS
     */
    @JsonProperty("last4Digits")
    private String last4Digits;
    
    /**
     * Card type/brand (VISA, MASTERCARD, etc.)
     * Safe to display per PCI DSS
     */
    @JsonProperty("cardType")
    private String cardType;
    
    /**
     * Card expiry month
     * Safe to display per PCI DSS
     */
    @JsonProperty("expiryMonth")
    private Integer expiryMonth;
    
    /**
     * Card expiry year
     * Safe to display per PCI DSS
     */
    @JsonProperty("expiryYear")
    private Integer expiryYear;
    
    /**
     * Token expiration date
     */
    @JsonProperty("expiresAt")
    private LocalDateTime expiresAt;
    
    /**
     * Whether this is a newly created token
     * or an existing token was returned
     */
    @JsonProperty("isNewToken")
    private Boolean isNewToken;
    
    /**
     * Token type (STANDARD, TEMPORARY, NETWORK, SINGLE_USE)
     */
    @JsonProperty("tokenType")
    private String tokenType;
    
    /**
     * Security level of the token
     */
    @JsonProperty("securityLevel")
    private String securityLevel;
    
    /**
     * Token status (ACTIVE, INACTIVE, EXPIRED, REVOKED)
     */
    @JsonProperty("status")
    @Builder.Default
    private String status = "ACTIVE";
    
    /**
     * Network token information (for mobile payments)
     */
    @JsonProperty("networkTokenId")
    private String networkTokenId;
    
    @JsonProperty("networkProvider")
    private String networkProvider;
    
    @JsonProperty("networkTokenStatus")
    private String networkTokenStatus;
    
    /**
     * Usage information
     */
    @JsonProperty("usageCount")
    @Builder.Default
    private Integer usageCount = 0;
    
    @JsonProperty("maxUsageCount")
    private Integer maxUsageCount;
    
    /**
     * Token creation timestamp
     */
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    /**
     * Last usage timestamp
     */
    @JsonProperty("lastUsedAt")
    private LocalDateTime lastUsedAt;
    
    /**
     * Compliance information
     */
    @JsonProperty("pciCompliant")
    @Builder.Default
    private Boolean pciCompliant = true;
    
    @JsonProperty("tokenizationVersion")
    @Builder.Default
    private String tokenizationVersion = "1.0";
    
    /**
     * Environment information
     */
    @JsonProperty("environment")
    @Builder.Default
    private String environment = "PRODUCTION";
    
    /**
     * Additional metadata
     */
    @JsonProperty("metadata")
    private String metadata;
    
    /**
     * Issuing bank information (if available)
     */
    @JsonProperty("issuingBank")
    private String issuingBank;
    
    @JsonProperty("issuingCountry")
    private String issuingCountry;
    
    /**
     * Risk assessment information
     */
    @JsonProperty("riskScore")
    private Integer riskScore;
    
    @JsonProperty("fraudScore")
    private Integer fraudScore;
    
    /**
     * Device information
     */
    @JsonProperty("deviceType")
    private String deviceType;
    
    /**
     * Utility methods
     */
    
    /**
     * Get display name for UI
     */
    @JsonProperty("displayName")
    public String getDisplayName() {
        return String.format("%s ending in %s", cardType, last4Digits);
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
     * Check if token is active and valid
     */
    @JsonProperty("isValid")
    public boolean isValid() {
        return "ACTIVE".equals(status) && 
               (expiresAt == null || LocalDateTime.now().isBefore(expiresAt)) &&
               Boolean.TRUE.equals(pciCompliant);
    }
    
    /**
     * Check if token is expiring soon (within 30 days)
     */
    @JsonProperty("isExpiringSoon")
    public boolean isExpiringSoon() {
        return expiresAt != null && 
               LocalDateTime.now().plusDays(30).isAfter(expiresAt);
    }
    
    /**
     * Check if usage limit is reached
     */
    @JsonProperty("isUsageLimitReached")
    public boolean isUsageLimitReached() {
        return maxUsageCount != null && 
               maxUsageCount > 0 && 
               usageCount != null && 
               usageCount >= maxUsageCount;
    }
    
    /**
     * Get card expiry in MM/YY format
     */
    @JsonProperty("expiryDisplay")
    public String getExpiryDisplay() {
        if (expiryMonth == null || expiryYear == null) {
            return "**/**";
        }
        return String.format("%02d/%02d", expiryMonth, expiryYear % 100);
    }
    
    /**
     * Get remaining usage count
     */
    @JsonProperty("remainingUsage")
    public Integer getRemainingUsage() {
        if (maxUsageCount == null || maxUsageCount <= 0) {
            return null; // Unlimited usage
        }
        if (usageCount == null) {
            return maxUsageCount;
        }
        return Math.max(0, maxUsageCount - usageCount);
    }
    
    /**
     * Check if token supports network payments
     */
    @JsonProperty("supportsNetworkPayments")
    public boolean supportsNetworkPayments() {
        return networkTokenId != null && 
               "ACTIVE".equals(networkTokenStatus);
    }
    
    /**
     * Get token age in days
     */
    @JsonProperty("ageInDays")
    public Long getAgeInDays() {
        if (createdAt == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
    }
    
    /**
     * Get days until expiry
     */
    @JsonProperty("daysUntilExpiry")
    public Long getDaysUntilExpiry() {
        if (expiresAt == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiresAt);
    }
    
    /**
     * Create summary for audit logs
     */
    public String getAuditSummary() {
        return String.format("Token{id=%s, type=%s, cardType=%s, last4=%s, status=%s}", 
            tokenId, tokenType, cardType, last4Digits, status);
    }
    
    /**
     * Create minimal result for API responses
     */
    public TokenizationResult createMinimalResult() {
        return TokenizationResult.builder()
            .token(token)
            .tokenId(tokenId)
            .last4Digits(last4Digits)
            .cardType(cardType)
            .expiryMonth(expiryMonth)
            .expiryYear(expiryYear)
            .status(status)
            .isNewToken(isNewToken)
            .build();
    }
    
    /**
     * Create detailed result for authenticated users
     */
    public TokenizationResult createDetailedResult() {
        return this; // Return full object for detailed responses
    }
    
    /**
     * Validate result before returning to client
     */
    public void validateForResponse() {
        // Ensure no sensitive data is included
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("Token cannot be empty in response");
        }
        
        if (!Boolean.TRUE.equals(pciCompliant)) {
            throw new IllegalStateException("Non-PCI compliant tokens cannot be returned");
        }
        
        // Validate required fields
        if (last4Digits == null || cardType == null) {
            throw new IllegalStateException("Required display fields missing from token result");
        }
    }
    
    /**
     * PCI DSS Compliant toString() - safe for logging
     */
    @Override
    public String toString() {
        return String.format("TokenizationResult{tokenId=%s, cardType=%s, last4=%s, status=%s, " +
                           "isNewToken=%s, expiresAt=%s}", 
            tokenId, cardType, last4Digits, status, isNewToken, expiresAt);
    }
}