package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payment fraud evaluation request DTO
 * Comprehensive request for payment fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"securityContext", "deviceFingerprint"})
public class PaymentFraudRequest {
    
    @NotNull
    private UUID paymentId;
    
    @NotNull
    private UUID senderId;
    
    @NotNull
    private UUID recipientId;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    @NotNull
    @Size(min = 3, max = 3)
    private String currency;
    
    @NotNull
    private String paymentType;
    
    private String paymentMethod;
    
    private LocalDateTime initiatedAt;
    
    // Sender information
    private String senderAccountType;
    private String senderAccountId;
    private String senderCountryCode;
    private Integer senderAge;
    private LocalDateTime senderRegistrationDate;
    private Integer senderTransactionCount;
    private BigDecimal senderTotalVolume;
    
    // Recipient information
    private String recipientAccountType;
    private String recipientAccountId;
    private String recipientCountryCode;
    private boolean isFirstTimeRecipient;
    
    // Transaction context
    private String description;
    private boolean isRecurring;
    private UUID parentTransactionId;
    private String channel; // WEB, MOBILE, API
    
    // Security context
    private SecurityContext securityContext;
    
    // Device information
    private DeviceFingerprint deviceFingerprint;
    
    // Risk factors
    private Map<String, Object> additionalRiskFactors;
    
    // Business context
    private String merchantId;
    private String merchantCategory;
    private boolean isHighValueTransaction;
    private boolean isInternational;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityContext {
        private String ipAddress;
        private String userAgent;
        private String sessionId;
        private LocalDateTime sessionStartTime;
        private boolean isVpnDetected;
        private boolean isTorDetected;
        private String geolocation;
        private String timezone;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceFingerprint {
        private String deviceId;
        private String deviceType;
        private String operatingSystem;
        private String browserType;
        private String screenResolution;
        private String language;
        private boolean isMobile;
        private boolean isKnownDevice;
        private Integer riskScore;
    }
}