package com.waqiti.payment.qrcode.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for QR Code based payments
 */
@Entity
@Table(name = "qr_code_payments", indexes = {
    @Index(name = "idx_qr_code_id", columnList = "qr_code_id", unique = true),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QRCodePayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "qr_code_id", nullable = false, unique = true, length = 50)
    private String qrCodeId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "merchant_id")
    private String merchantId;
    
    @Column(name = "merchant_name")
    private String merchantName;
    
    @Column(name = "payer_user_id")
    private String payerUserId;
    
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "final_amount", precision = 19, scale = 4)
    private BigDecimal finalAmount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private QRCodeType type;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "payment_data", columnDefinition = "TEXT")
    private String paymentData;
    
    @Column(name = "transaction_id")
    private String transactionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;
    
    @ElementCollection
    @CollectionTable(name = "qr_code_metadata", 
                     joinColumns = @JoinColumn(name = "qr_code_payment_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Version
    private Long version;
    
    public enum Status {
        ACTIVE,
        PROCESSING,
        COMPLETED,
        CANCELLED,
        EXPIRED,
        FAILED
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}