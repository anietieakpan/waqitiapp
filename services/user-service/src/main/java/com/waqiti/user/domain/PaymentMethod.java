package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment Method Entity
 */
@Entity
@Table(name = "payment_methods", indexes = {
    @Index(name = "idx_payment_methods_user_id", columnList = "user_id"),
    @Index(name = "idx_payment_methods_type", columnList = "payment_type"),
    @Index(name = "idx_payment_methods_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "payment_type", length = 50, nullable = false)
    private String paymentType;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "account_identifier", length = 255)
    private String accountIdentifier;

    @Column(name = "last_four", length = 4)
    private String lastFour;

    @Column(name = "expiry_month")
    private Integer expiryMonth;

    @Column(name = "expiry_year")
    private Integer expiryYear;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "nickname", length = 100)
    private String nickname;
    
    @Column(name = "external_account_id", length = 255)
    private String externalAccountId;
    
    @Column(name = "tokenized_card_number", length = 255)
    private String tokenizedCardNumber;
    
    @Column(name = "deleted")
    @Builder.Default
    private Boolean deleted = false;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "deletion_reason")
    private String deletionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Version
    @Column(name = "version")
    private Long version;
}
