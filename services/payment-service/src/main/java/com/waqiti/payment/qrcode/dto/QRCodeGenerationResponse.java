package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.payment.qrcode.model.QRCodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for QR code generation
 * Contains the generated QR code data and metadata
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing generated QR code details")
public class QRCodeGenerationResponse {
    
    @Schema(description = "Unique identifier for the QR code", example = "QR1234567890ABCDEF")
    private String qrCodeId;
    
    @Schema(description = "Base64 encoded QR code image (PNG format)")
    private String qrCodeImage;
    
    @Schema(description = "Alternative QR code image URL for direct access", example = "https://api.example.com/qr/QR1234567890ABCDEF.png")
    private String qrCodeImageUrl;
    
    @Schema(description = "Deep link URL for mobile app integration", example = "waqiti://pay/QR1234567890ABCDEF")
    private String paymentUrl;
    
    @Schema(description = "Web URL for browser-based payments", example = "https://pay.example.com/qr/QR1234567890ABCDEF")
    private String webPaymentUrl;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code expiry timestamp")
    private LocalDateTime expiresAt;
    
    @Schema(description = "Fixed payment amount (if applicable)", example = "99.99")
    private BigDecimal amount;
    
    @Schema(description = "Minimum payment amount for dynamic QR", example = "1.00")
    private BigDecimal minimumAmount;
    
    @Schema(description = "Maximum payment amount for dynamic QR", example = "1000.00")
    private BigDecimal maximumAmount;
    
    @Schema(description = "Currency code", example = "USD")
    private String currency;
    
    @Schema(description = "QR code type", example = "USER_DYNAMIC")
    private String type;
    
    @Schema(description = "QR code type enum for strong typing")
    private QRCodeType qrCodeType;
    
    @Schema(description = "Payment description", example = "Coffee payment")
    private String description;
    
    @Schema(description = "External reference ID", example = "INV-2024-001")
    private String reference;
    
    @Schema(description = "Merchant details if applicable")
    private MerchantInfo merchantInfo;
    
    @Schema(description = "QR code display text for UI", example = "Scan to pay $99.99")
    private String displayText;
    
    @Schema(description = "Short shareable link", example = "https://waq.it/p/ABC123")
    private String shortUrl;
    
    @Schema(description = "QR code data payload (for offline processing)")
    private String qrDataPayload;
    
    @Schema(description = "Digital signature for verification")
    private String signature;
    
    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
    
    @Schema(description = "Security features enabled")
    private SecurityFeatures securityFeatures;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Timestamp when QR code was created")
    private LocalDateTime createdAt;
    
    @Schema(description = "Estimated processing time in seconds", example = "0.5")
    private Double processingTime;
    
    // Additional enterprise fields
    @Schema(description = "QR code status")
    private GenerationStatus status;
    
    @Schema(description = "Status message")
    private String statusMessage;
    
    @Schema(description = "Payment identifier if QR code has been used")
    private String paymentId;
    
    @Schema(description = "Indicates if QR code has been used")
    private boolean isUsed;
    
    @Schema(description = "Indicates if QR code is static (reusable)")
    private boolean isStatic;
    
    @Schema(description = "Scan count for this QR code")
    private Integer scanCount;
    
    @Schema(description = "Last scanned timestamp")
    private Instant lastScannedAt;
    
    @Schema(description = "Validation timestamp")
    private Instant validFrom;
    
    @Schema(description = "Validation end timestamp")
    private Instant validUntil;
    
    @Schema(description = "Webhook URL for notifications")
    private String webhookUrl;
    
    @Schema(description = "Campaign tracking ID")
    private String campaignId;
    
    @Schema(description = "Analytics tracking ID")
    private String trackingId;
    
    @Schema(description = "Compliance status")
    private String complianceStatus;
    
    @Schema(description = "Error code if generation failed")
    private String errorCode;
    
    @Schema(description = "Error details if generation failed")
    private String errorDetails;
    
    @Schema(description = "Warning message for partial failures")
    private String warningMessage;
    
    @Schema(description = "Rate limit information - remaining generations")
    private Integer remainingGenerations;
    
    @Schema(description = "Rate limit reset timestamp")
    private Instant rateLimitResetAt;
    
    // Helper methods
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isActive() {
        return status == GenerationStatus.ACTIVE && !isExpired() && !isUsed;
    }
    
    public long getTimeUntilExpiry() {
        if (expiresAt == null) {
            return Long.MAX_VALUE;
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0, seconds);
    }
    
    // Status enumeration
    public enum GenerationStatus {
        ACTIVE,
        EXPIRED,
        USED,
        CANCELLED,
        PENDING_APPROVAL,
        FAILED,
        SUSPENDED
    }
    
    // Builder convenience methods
    public static QRCodeGenerationResponse success(String qrCodeId, String qrCodeImage) {
        return QRCodeGenerationResponse.builder()
            .qrCodeId(qrCodeId)
            .qrCodeImage(qrCodeImage)
            .status(GenerationStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    public static QRCodeGenerationResponse error(String errorCode, String errorDetails) {
        return QRCodeGenerationResponse.builder()
            .status(GenerationStatus.FAILED)
            .errorCode(errorCode)
            .errorDetails(errorDetails)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Merchant information for merchant QR codes
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Merchant details for merchant QR codes")
    public static class MerchantInfo {
        
        @Schema(description = "Merchant ID", example = "mch_789012")
        private String merchantId;
        
        @Schema(description = "Merchant name", example = "Coffee Shop")
        private String merchantName;
        
        @Schema(description = "Merchant category", example = "retail")
        private String category;
        
        @Schema(description = "Store ID", example = "store_001")
        private String storeId;
        
        @Schema(description = "Terminal ID", example = "pos_001")
        private String terminalId;
        
        @Schema(description = "Merchant logo URL")
        private String logoUrl;
        
        @Schema(description = "Store address")
        private String address;
        
        @Schema(description = "Contact phone", example = "+1234567890")
        private String phone;
    }
    
    /**
     * Security features configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Security features enabled for this QR code")
    public static class SecurityFeatures {
        
        @Schema(description = "PIN required for payment", example = "false")
        private Boolean requirePin;
        
        @Schema(description = "Biometric authentication required", example = "true")
        private Boolean requireBiometric;
        
        @Schema(description = "Confirmation required before processing", example = "true")
        private Boolean requireConfirmation;
        
        @Schema(description = "Device binding enforced", example = "false")
        private Boolean deviceBindingEnforced;
        
        @Schema(description = "Geolocation verification enabled", example = "false")
        private Boolean geoLocationVerification;
        
        @Schema(description = "Maximum scan attempts allowed", example = "3")
        private Integer maxScanAttempts;
        
        @Schema(description = "Fraud detection level", example = "MEDIUM")
        private String fraudDetectionLevel;
    }
}