package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Universal payment request model
 * Consolidates all payment types into single interface
 *
 * SECURITY FIX: Replaced @Data with @Getter/@Setter to prevent sensitive data exposure
 * Sensitive fields: accountNumber, signature, encryptedData, ipAddress
 */
@lombok.Getter
@lombok.Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull
    private UUID paymentId;

    @NotNull
    private PaymentType type;

    @NotNull
    private ProviderType providerType;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    private String currency;

    @NotNull
    private String fromUserId;

    @NotNull
    private String toUserId;

    private String description;
    private String reference;

    // Payment method details
    private String paymentMethodId;
    private Map<String, Object> paymentMethodDetails;

    // Request metadata
    private LocalDateTime timestamp;
    private String deviceId;
    private String ipAddress; // GDPR: Must mask in logs
    private String sessionId;

    // Routing information
    private String routingNumber;
    private String accountNumber; // PCI DSS: Must mask in logs
    private String checkNumber;

    // Additional metadata
    private Map<String, Object> metadata;

    // Security - CRITICAL: Never log these fields
    private String encryptedData; // Contains sensitive encrypted payload
    private String signature; // Authentication credential

    // Geolocation
    private Double latitude;
    private Double longitude;

    // Special payment type data
    private SplitPaymentData splitData;
    private RecurringPaymentData recurringData;
    private GroupPaymentData groupData;
    private BnplPaymentData bnplData;
    private MerchantPaymentData merchantData;
    private CryptoPaymentData cryptoData;

    /**
     * Custom toString() that masks sensitive fields per PCI DSS & GDPR
     */
    @Override
    public String toString() {
        return "PaymentRequest{" +
            "paymentId=" + paymentId +
            ", type=" + type +
            ", providerType=" + providerType +
            ", amount=" + amount +
            ", fromUserId='" + fromUserId + '\'' +
            ", toUserId='" + toUserId + '\'' +
            ", description='" + description + '\'' +
            ", reference='" + reference + '\'' +
            ", paymentMethodId='" + paymentMethodId + '\'' +
            ", timestamp=" + timestamp +
            ", deviceId='" + deviceId + '\'' +
            ", ipAddress='[REDACTED-GDPR]'" +
            ", sessionId='" + sessionId + '\'' +
            ", routingNumber='" + routingNumber + '\'' +
            ", accountNumber='[REDACTED-PCI-DSS]'" +
            ", checkNumber='" + checkNumber + '\'' +
            ", encryptedData='[REDACTED]'" +
            ", signature='[REDACTED]'" +
            ", latitude=" + latitude +
            ", longitude=" + longitude +
            ", splitData=" + splitData +
            ", recurringData=" + recurringData +
            ", groupData=" + groupData +
            ", bnplData=" + bnplData +
            ", merchantData=" + merchantData +
            ", cryptoData=" + cryptoData +
            '}';
    }
    
    public static PaymentRequest createP2P(String fromUserId, String toUserId, BigDecimal amount) {
        return PaymentRequest.builder()
            .paymentId(UUID.randomUUID())
            .type(PaymentType.P2P)
            .providerType(ProviderType.INTERNAL)
            .amount(amount)
            .fromUserId(fromUserId)
            .toUserId(toUserId)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static PaymentRequest createMerchant(String userId, String merchantId, BigDecimal amount) {
        return PaymentRequest.builder()
            .paymentId(UUID.randomUUID())
            .type(PaymentType.MERCHANT)
            .providerType(ProviderType.STRIPE)
            .amount(amount)
            .fromUserId(userId)
            .toUserId(merchantId)
            .timestamp(LocalDateTime.now())
            .build();
    }
}