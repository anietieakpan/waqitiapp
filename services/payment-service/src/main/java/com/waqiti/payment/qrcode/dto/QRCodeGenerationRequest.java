package com.waqiti.payment.qrcode.dto;

import com.waqiti.payment.qrcode.model.QRCodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * QR Code Generation Request DTO
 * 
 * Comprehensive request model for generating QR codes with support for
 * various payment types, security features, and customization options.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QRCodeGenerationRequest {
    
    @NotNull(message = "QR code type is required")
    private QRCodeType type;
    
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum limit")
    private BigDecimal amount;
    
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    @Builder.Default
    private String currency = "USD";
    
    @NotNull(message = "Recipient ID is required")
    private String recipientId;
    
    @Size(max = 100, message = "Recipient name too long")
    private String recipientName;
    
    private String merchantId;
    
    @Size(max = 100, message = "Merchant name too long")
    private String merchantName;
    
    @Size(max = 500, message = "Description too long")
    private String description;
    
    private Instant expiresAt;
    
    @Builder.Default
    private boolean isStatic = false;
    
    @Builder.Default
    private boolean requireSignature = true;
    
    @Builder.Default
    private boolean encryptSensitiveData = true;
    
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    // Display customization
    @Size(max = 100, message = "Logo URL too long")
    private String logoUrl;
    
    @Size(max = 50, message = "Brand name too long")
    private String brandName;
    
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Invalid color format")
    private String primaryColor;
    
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Invalid color format")
    private String secondaryColor;
    
    // Payment options
    private boolean allowTips;
    
    @DecimalMin(value = "0", message = "Tip percentage cannot be negative")
    @DecimalMax(value = "100", message = "Tip percentage cannot exceed 100")
    private BigDecimal defaultTipPercentage;
    
    private boolean allowPartialPayment;
    
    @DecimalMin(value = "0.01", message = "Minimum payment must be greater than 0")
    private BigDecimal minimumPaymentAmount;
    
    // Security features
    private boolean requirePin;
    
    private boolean requireBiometric;
    
    private boolean requireLocationVerification;
    
    @Max(value = 10, message = "Max scan attempts cannot exceed 10")
    private Integer maxScanAttempts;
    
    private boolean oneTimeUse;
    
    // Notification settings
    private boolean notifyOnScan;
    
    private boolean notifyOnPayment;
    
    private String notificationEmail;
    
    private String notificationPhone;
    
    private String webhookUrl;
    
    // Reference information
    private String referenceNumber;
    
    private String invoiceNumber;
    
    private String orderNumber;
    
    private String customerReference;
    
    // Recurring payment settings
    private boolean isRecurring;
    
    private String recurringFrequency;
    
    private Integer recurringCount;
    
    private Instant recurringStartDate;
    
    private Instant recurringEndDate;
    
    // Split payment settings
    private boolean allowSplitPayment;
    
    private Integer maxSplitParticipants;
    
    private SplitType splitType;
    
    // Loyalty program integration
    private String loyaltyProgramId;
    
    private BigDecimal loyaltyPointsMultiplier;
    
    private boolean allowLoyaltyRedemption;
    
    // Tax and fees
    private BigDecimal taxRate;
    
    private BigDecimal serviceFee;
    
    private BigDecimal convenienceFee;
    
    private boolean includeTaxInAmount;
    
    // Geographic restrictions
    private String allowedCountries;
    
    private String blockedCountries;
    
    private String allowedRegions;
    
    // Payment method restrictions
    private String allowedPaymentMethods;
    
    private String blockedPaymentMethods;
    
    private boolean creditCardOnly;
    
    private boolean debitCardOnly;
    
    // Analytics tracking
    private String campaignId;
    
    private String sourceChannel;
    
    private String marketingCode;
    
    private Map<String, String> analyticsMetadata;
    
    // Compliance
    private boolean requireKYC;
    
    private String complianceLevel;
    
    private boolean amlCheckRequired;
    
    private boolean sanctionsCheckRequired;
    
    // User context
    @NotNull(message = "User ID is required")
    private String userId;
    
    private String deviceId;
    
    private String ipAddress;
    
    private String userAgent;
    
    private String sessionId;
    
    // Custom validation
    public void validate() {
        // Validate static QR codes don't have expiry
        if (isStatic && expiresAt != null) {
            throw new IllegalArgumentException("Static QR codes cannot have expiry date");
        }
        
        // Validate one-time use QR codes are not static
        if (oneTimeUse && isStatic) {
            throw new IllegalArgumentException("One-time use QR codes cannot be static");
        }
        
        // Validate amount for certain types
        if (type == QRCodeType.REQUEST_MONEY && amount == null) {
            throw new IllegalArgumentException("Amount is required for money request QR codes");
        }
        
        // Validate merchant fields
        if (type == QRCodeType.MERCHANT || type == QRCodeType.STATIC_MERCHANT) {
            if (merchantId == null || merchantName == null) {
                throw new IllegalArgumentException("Merchant details required for merchant QR codes");
            }
        }
        
        // Validate recurring settings
        if (isRecurring) {
            if (recurringFrequency == null || recurringStartDate == null) {
                throw new IllegalArgumentException("Recurring frequency and start date required for recurring payments");
            }
        }
        
        // Validate split payment settings
        if (allowSplitPayment && maxSplitParticipants != null && maxSplitParticipants < 2) {
            throw new IllegalArgumentException("Split payment requires at least 2 participants");
        }
    }
    
    // Enums
    public enum SplitType {
        EQUAL,
        PERCENTAGE,
        CUSTOM,
        PROPORTIONAL
    }
    
    // Builder defaults
    public static class QRCodeGenerationRequestBuilder {
        private Map<String, String> metadata = new HashMap<>();
        private Map<String, String> analyticsMetadata = new HashMap<>();
    }
}