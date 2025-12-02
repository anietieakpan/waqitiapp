package com.waqiti.merchant.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "merchant_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class MerchantPayment {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "payment_id", unique = true, nullable = false, length = 50)
    private String paymentId;
    
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    
    @Column(name = "customer_id")
    private UUID customerId;
    
    @Column(name = "order_id", length = 100)
    private String orderId;
    
    @Column(name = "external_reference", length = 100)
    private String externalReference;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;
    
    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;
    
    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;
    
    @Column(name = "tip_amount", precision = 19, scale = 4)
    private BigDecimal tipAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Type(type = "jsonb")
    @Column(name = "line_items", columnDefinition = "jsonb")
    private Map<String, Object> lineItems;
    
    @Type(type = "jsonb")
    @Column(name = "customer_info", columnDefinition = "jsonb")
    private Map<String, Object> customerInfo;
    
    @Type(type = "jsonb")
    @Column(name = "payment_details", columnDefinition = "jsonb")
    private Map<String, Object> paymentDetails;
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "receipt_email", length = 100)
    private String receiptEmail;
    
    @Column(name = "receipt_phone", length = 20)
    private String receiptPhone;
    
    @Column(name = "receipt_url", length = 255)
    private String receiptUrl;
    
    @Column(name = "refund_policy", length = 50)
    private String refundPolicy = "STANDARD";
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;
    
    @Column(name = "captured_at")
    private LocalDateTime capturedAt;
    
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
    
    @Column(name = "failure_reason", length = 255)
    private String failureReason;
    
    @Column(name = "failure_code", length = 50)
    private String failureCode;
    
    @Column(name = "processor_transaction_id", length = 100)
    private String processorTransactionId;
    
    @Column(name = "authorization_code", length = 50)
    private String authorizationCode;
    
    @Column(name = "settlement_batch_id", length = 50)
    private String settlementBatchId;
    
    @Type(type = "jsonb")
    @Column(name = "risk_assessment", columnDefinition = "jsonb")
    private Map<String, Object> riskAssessment;
    
    @Column(name = "fraud_score")
    private Integer fraudScore;
    
    @Column(name = "chargeback_risk")
    private Integer chargebackRisk;
    
    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "location_data", columnDefinition = "TEXT")
    private String locationData;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (paymentId == null) {
            paymentId = "MP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set expiration time (default 1 hour)
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(1);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum PaymentMethod {
        CARD,
        BANK_TRANSFER,
        DIGITAL_WALLET,
        QR_CODE,
        NFC,
        CRYPTO,
        APPLE_PAY,
        GOOGLE_PAY,
        PAYPAL
    }
    
    public enum PaymentStatus {
        PENDING,
        AUTHORIZED,
        CAPTURED,
        SETTLED,
        FAILED,
        CANCELLED,
        REFUNDED,
        PARTIALLY_REFUNDED,
        DISPUTED,
        CHARGEBACK
    }
}