package com.waqiti.account.entity;

import com.waqiti.common.entity.BaseEntity;
import com.waqiti.common.encryption.annotation.Encrypted;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Payment method entity for account payment options
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Entity
@Table(name = "payment_methods", indexes = {
    @Index(name = "idx_payment_method_account", columnList = "account_id"),
    @Index(name = "idx_payment_method_type", columnList = "method_type"),
    @Index(name = "idx_payment_method_status", columnList = "status")
})
@Getter
@Setter
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class PaymentMethod extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 30)
    @ToString.Include
    private PaymentMethodType methodType;
    
    @Column(name = "card_number_masked", length = 20)
    private String cardNumberMasked;
    
    @Column(name = "card_holder_name", length = 100)
    private String cardHolderName;
    
    @Column(name = "card_brand", length = 20)
    private String cardBrand;
    
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    
    @Column(name = "bank_name", length = 100)
    private String bankName;
    
    @Encrypted(fieldType = "BANK_ACCOUNT", highlySensitive = true)
    @Column(name = "bank_account_number", length = 500) // Larger length for encrypted data
    private String bankAccountNumber;

    @Encrypted(fieldType = "ROUTING_NUMBER", highlySensitive = true)
    @Column(name = "routing_number", length = 500) // PCI DSS: Encrypted routing number
    private String routingNumber;
    
    @Column(name = "wallet_provider", length = 50)
    private String walletProvider;
    
    @Column(name = "wallet_id", length = 100)
    private String walletId;
    
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentMethodStatus status;
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @Column(name = "billing_address", columnDefinition = "jsonb")
    private String billingAddress;
    
    @PrePersist
    @Override
    protected void onPrePersist() {
        super.onPrePersist();
        
        if (isDefault == null) {
            isDefault = false;
        }
        
        if (status == null) {
            status = PaymentMethodStatus.PENDING_VERIFICATION;
        }
    }
    
    public enum PaymentMethodType {
        DEBIT_CARD,
        CREDIT_CARD,
        BANK_ACCOUNT,
        DIGITAL_WALLET,
        VIRTUAL_CARD,
        PREPAID_CARD
    }
    
    public enum PaymentMethodStatus {
        PENDING_VERIFICATION,
        VERIFIED,
        EXPIRED,
        BLOCKED,
        INACTIVE
    }
}