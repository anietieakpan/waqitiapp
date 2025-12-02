package com.waqiti.common.domain.valueobjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PaymentId Value Object - Immutable representation of payment identifiers
 * Encapsulates payment ID validation and formatting rules
 */
@EqualsAndHashCode
public class PaymentId {
    
    private static final Pattern PAYMENT_ID_PATTERN = Pattern.compile("^(pay_|txn_)[a-zA-Z0-9]{16}$");
    private static final String PAY_PREFIX = "pay_";
    private static final String TXN_PREFIX = "txn_";
    
    private final String value;
    
    @JsonCreator
    public PaymentId(String paymentId) {
        this.value = validateAndNormalize(paymentId);
    }
    
    public static PaymentId of(String paymentId) {
        return new PaymentId(paymentId);
    }
    
    public static PaymentId generatePayment() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return new PaymentId(PAY_PREFIX + uuid.substring(0, 16));
    }
    
    public static PaymentId generateTransaction() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return new PaymentId(TXN_PREFIX + uuid.substring(0, 16));
    }
    
    public static PaymentId fromUUID(UUID uuid, PaymentType type) {
        String uuidString = uuid.toString().replace("-", "");
        String prefix = type == PaymentType.PAYMENT ? PAY_PREFIX : TXN_PREFIX;
        return new PaymentId(prefix + uuidString.substring(0, 16));
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public String getShortId() {
        return value.substring(4); // Remove prefix
    }
    
    public PaymentType getType() {
        if (value.startsWith(PAY_PREFIX)) {
            return PaymentType.PAYMENT;
        } else if (value.startsWith(TXN_PREFIX)) {
            return PaymentType.TRANSACTION;
        }
        throw new IllegalStateException("Unknown payment ID type: " + value);
    }
    
    public boolean isPayment() {
        return value.startsWith(PAY_PREFIX);
    }
    
    public boolean isTransaction() {
        return value.startsWith(TXN_PREFIX);
    }
    
    public boolean isValid() {
        return PAYMENT_ID_PATTERN.matcher(value).matches();
    }
    
    public PaymentId maskForLogging() {
        String prefix = value.substring(0, 4);
        String shortId = getShortId();
        
        if (shortId.length() <= 4) {
            return new PaymentId(prefix + "****");
        }
        
        String masked = shortId.substring(0, 2) + "*".repeat(shortId.length() - 4) + shortId.substring(shortId.length() - 2);
        return new PaymentId(prefix + masked);
    }
    
    public String toTrackingCode() {
        // Generate human-readable tracking code from payment ID
        String shortId = getShortId().toUpperCase();
        return shortId.substring(0, 4) + "-" + shortId.substring(4, 8) + "-" + shortId.substring(8, 12);
    }
    
    private String validateAndNormalize(String paymentId) {
        Objects.requireNonNull(paymentId, "PaymentId cannot be null");
        
        String trimmed = paymentId.trim();
        
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("PaymentId cannot be empty");
        }
        
        if (!trimmed.startsWith(PAY_PREFIX) && !trimmed.startsWith(TXN_PREFIX)) {
            throw new IllegalArgumentException("PaymentId must start with 'pay_' or 'txn_' prefix");
        }
        
        if (!PAYMENT_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid PaymentId format. Expected: pay_[16 chars] or txn_[16 chars]");
        }
        
        return trimmed;
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    public enum PaymentType {
        PAYMENT,
        TRANSACTION
    }
}