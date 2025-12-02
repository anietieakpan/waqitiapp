package com.waqiti.payment.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payment_methods")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PaymentMethod {
    
    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Optimistic locking - prevents concurrent update conflicts
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "method_id", unique = true, nullable = false, length = 50)
    private String methodId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    @NotNull
    private PaymentMethodType methodType;
    
    @Column(nullable = false, length = 50)
    private String provider;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodStatus status = PaymentMethodStatus.ACTIVE;
    
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
    
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    @Column(name = "masked_details", length = 100)
    private String maskedDetails;
    
    @Column(name = "encrypted_details", columnDefinition = "TEXT")
    private String encryptedDetails;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
    
    @Type(type = "jsonb")
    @Column(name = "verification_data", columnDefinition = "jsonb")
    private Map<String, Object> verificationData;
    
    @Column(name = "expires_at")
    private LocalDate expiresAt;
    
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (methodId == null) {
            methodId = "PM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum PaymentMethodType {
        BANK_ACCOUNT,
        CREDIT_CARD,
        DEBIT_CARD,
        DIGITAL_WALLET,
        CRYPTOCURRENCY
    }
    
    public enum PaymentMethodStatus {
        ACTIVE,
        INACTIVE,
        EXPIRED,
        BLOCKED
    }
    
    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        FAILED
    }
}