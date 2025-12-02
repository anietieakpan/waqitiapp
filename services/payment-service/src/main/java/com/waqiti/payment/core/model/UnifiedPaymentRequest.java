package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ENHANCED Unified payment request model that standardizes payment requests
 * across different payment types and providers with enterprise features
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedPaymentRequest {
    
    @NotNull(message = "Payment ID is required")
    @Builder.Default
    private String paymentId = UUID.randomUUID().toString();
    
    @NotNull(message = "Request ID is required")
    @Builder.Default
    private String requestId = UUID.randomUUID().toString();
    
    @NotNull(message = "User ID is required")
    private String userId; // Sender ID
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum limit")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    @Builder.Default
    private String currency = "USD";
    
    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;
    
    private String recipientId;
    private List<String> recipients; // For group payments
    
    @Size(max = 500, message = "Description too long")
    private String description;
    
    private Map<String, Object> metadata;
    
    private String paymentMethod;
    
    private ProviderType providerType;
    
    private LocalDateTime scheduledAt;
    
    private boolean recurring;
    private String recurringPeriod;
    
    // Enhanced fields for enterprise features
    private String senderId; // Alias for userId
    private String idempotencyKey;
    private SecurityLevel securityLevel;
    private boolean requireMFA;
    private boolean complianceCheckRequired;
    private boolean skipFraudCheck;
    private boolean skipComplianceCheck;
    private boolean urgentProcessing;
    
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();
    
    private String sourceApplication;
    private String sourceIpAddress;
    private String deviceFingerprint;
    
    // Getters for compatibility
    public String getSenderId() {
        return senderId != null ? senderId : userId;
    }
    
    public ProviderType getProviderType() {
        if (providerType != null) return providerType;
        // Map provider string to enum
        if (provider != null) {
            try {
                return ProviderType.valueOf(provider.toUpperCase());
            } catch (Exception e) {
                return ProviderType.INTERNAL;
            }
        }
        return ProviderType.INTERNAL;
    }
    
    // Legacy field support
    private String provider;
    
    public enum PaymentType {
        P2P,
        GROUP,
        MERCHANT,
        BILL,
        REQUEST,
        INTERNATIONAL,
        CRYPTO,
        SPLIT,
        RECURRING,
        INSTANT,
        BNPL,
        STANDARD,
        WIRE
    }
    
    public enum SecurityLevel {
        STANDARD, ENHANCED, MAXIMUM
    }
}