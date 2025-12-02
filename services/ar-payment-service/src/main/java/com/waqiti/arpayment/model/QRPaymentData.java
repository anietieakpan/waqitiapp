package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents payment data encoded in a QR code
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QRPaymentData {
    private UUID paymentId;
    private UUID recipientId;
    private String recipientName;
    private String recipientType; // USER, MERCHANT, ATM, VENDING_MACHINE
    private BigDecimal amount;
    private String currency;
    private String description;
    private String reference;
    private QRCodeType qrType;
    private Instant validUntil;
    private boolean isReusable;
    private int maxUses;
    private int currentUses;
    private Map<String, Object> additionalData;
    private SecurityInfo security;
    
    public enum QRCodeType {
        STATIC_PAYMENT,
        DYNAMIC_PAYMENT,
        INVOICE,
        DONATION,
        SUBSCRIPTION,
        SPLIT_BILL,
        REQUEST_PAYMENT,
        MERCHANT_CHECKOUT
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityInfo {
        private String signature;
        private String encryptionMethod;
        private boolean requiresPIN;
        private boolean requiresBiometric;
        private String challengeToken;
    }
    
    public UUID getRecipientId() {
        return recipientId != null ? recipientId : UUID.randomUUID();
    }
    
    public BigDecimal getAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}