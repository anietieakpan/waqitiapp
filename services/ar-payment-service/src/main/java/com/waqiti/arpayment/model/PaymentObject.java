package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a payment object in AR space
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentObject {
    private UUID id;
    private String type; // QR_CODE, NFC_TAG, VISUAL_MARKER, PRODUCT, etc.
    private String merchantId;
    private String merchantName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private Map<String, Double> position;
    private Map<String, Object> paymentData;
    private PaymentObjectStatus status;
    private boolean isInteractive;
    private InteractionMode interactionMode;
    
    public enum PaymentObjectStatus {
        ACTIVE,
        PENDING,
        COMPLETED,
        EXPIRED,
        INVALID
    }
    
    public enum InteractionMode {
        TAP,
        GESTURE,
        VOICE,
        AUTO,
        PROXIMITY
    }
}