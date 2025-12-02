package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * CRITICAL P0 FIX: Merchant QR Code Response DTO
 *
 * Response DTO for merchant QR code generation containing
 * merchant-specific metadata and analytics.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing merchant QR code details")
public class MerchantQRCodeResponse {

    @Schema(description = "Unique identifier for the QR code", example = "QR1234567890ABCDEF", required = true)
    private String qrCodeId;

    @Schema(description = "Base64 encoded QR code image (PNG format)")
    private String qrCodeImage;

    @Schema(description = "QR code image URL", example = "https://api.example.com/qr/merchant/QR1234567890ABCDEF.png")
    private String qrCodeImageUrl;

    @Schema(description = "High-resolution printable QR code URL")
    private String printableImageUrl;

    @Schema(description = "Payment deep link URL", example = "waqiti://pay/merchant/QR1234567890ABCDEF")
    private String paymentUrl;

    @Schema(description = "Web payment URL", example = "https://pay.example.com/merchant/QR1234567890ABCDEF")
    private String webPaymentUrl;

    @Schema(description = "Short shareable link", example = "https://waq.it/m/ABC123")
    private String shortUrl;

    @Schema(description = "Merchant ID", example = "mch_789012", required = true)
    private String merchantId;

    @Schema(description = "Merchant name", example = "Coffee Shop Inc.")
    private String merchantName;

    @Schema(description = "Store ID", example = "store_001")
    private String storeId;

    @Schema(description = "Store name", example = "Downtown Branch")
    private String storeName;

    @Schema(description = "Terminal ID", example = "pos_001")
    private String terminalId;

    @Schema(description = "Fixed payment amount (if applicable)", example = "99.99")
    private BigDecimal amount;

    @Schema(description = "Minimum payment amount", example = "1.00")
    private BigDecimal minimumAmount;

    @Schema(description = "Maximum payment amount", example = "10000.00")
    private BigDecimal maximumAmount;

    @Schema(description = "Currency code", example = "USD", required = true)
    private String currency;

    @Schema(description = "QR code type", example = "MERCHANT_STATIC", required = true)
    private String type;

    @Schema(description = "Payment description", example = "Store checkout payment")
    private String description;

    @Schema(description = "Merchant reference", example = "INVOICE-2024-001")
    private String reference;

    @Schema(description = "QR code status", example = "ACTIVE")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code creation timestamp")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code expiry timestamp")
    private LocalDateTime expiresAt;

    @Schema(description = "Indicates if QR code is static (reusable)", example = "true")
    private Boolean isStatic;

    @Schema(description = "Tips allowed", example = "true")
    private Boolean allowTips;

    @Schema(description = "Suggested tip percentage", example = "15.0")
    private BigDecimal suggestedTipPercentage;

    @Schema(description = "Merchant logo URL", example = "https://cdn.merchant.com/logo.png")
    private String logoUrl;

    @Schema(description = "Callback webhook URL")
    private String callbackUrl;

    @Schema(description = "Business category", example = "retail")
    private String category;

    @Schema(description = "Cashier or staff ID", example = "cashier_42")
    private String cashierId;

    @Schema(description = "Campaign ID", example = "campaign_2024_q1")
    private String campaignId;

    @Schema(description = "Analytics tracking enabled", example = "true")
    private Boolean analyticsEnabled;

    @Schema(description = "Offline payment capable", example = "false")
    private Boolean offlineCapable;

    @Schema(description = "QR code data payload for offline processing")
    private String qrDataPayload;

    @Schema(description = "Digital signature for verification")
    private String signature;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Usage statistics")
    private UsageStatistics usageStatistics;

    @Schema(description = "Security configuration")
    private SecurityConfig securityConfig;

    @Schema(description = "Generation processing time (ms)", example = "120")
    private Long processingTimeMs;

    /**
     * Usage statistics for the QR code
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "QR code usage statistics")
    public static class UsageStatistics {

        @Schema(description = "Total number of scans", example = "150")
        private Integer totalScans;

        @Schema(description = "Successful payments count", example = "95")
        private Integer successfulPayments;

        @Schema(description = "Failed payments count", example = "5")
        private Integer failedPayments;

        @Schema(description = "Total payment volume", example = "9500.00")
        private BigDecimal totalVolume;

        @Schema(description = "Average payment amount", example = "100.00")
        private BigDecimal averageAmount;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Last scan timestamp")
        private LocalDateTime lastScannedAt;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Last successful payment timestamp")
        private LocalDateTime lastPaymentAt;
    }

    /**
     * Security configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Security configuration for merchant QR code")
    public static class SecurityConfig {

        @Schema(description = "Fraud detection level", example = "MEDIUM")
        private String fraudDetectionLevel;

        @Schema(description = "Geolocation verification enabled", example = "false")
        private Boolean geoLocationVerification;

        @Schema(description = "Device binding enforced", example = "false")
        private Boolean deviceBindingEnforced;

        @Schema(description = "Maximum scan attempts per hour", example = "100")
        private Integer maxScansPerHour;

        @Schema(description = "IP whitelist enabled", example = "false")
        private Boolean ipWhitelistEnabled;
    }

    // Helper methods
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status) && !isExpired();
    }
}
