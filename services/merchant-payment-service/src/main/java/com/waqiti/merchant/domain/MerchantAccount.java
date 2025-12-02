package com.waqiti.merchant.domain;

import com.waqiti.common.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "merchant_accounts", indexes = {
    @Index(name = "idx_merchant_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_merchant_status", columnList = "status"),
    @Index(name = "idx_merchant_business_name", columnList = "business_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
public class MerchantAccount {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
    
    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;
    
    @Column(name = "business_type", length = 50)
    private String businessType;
    
    @Column(name = "industry", length = 100)
    private String industry;
    
    @Column(name = "mcc", length = 10)
    private String mcc; // Merchant Category Code
    
    @Embedded
    private BusinessAddress businessAddress;
    
    @Embedded
    private ContactInfo contactInfo;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", length = 500)
    private String taxId; // PCI DSS: Encrypted EIN/Tax ID
    
    @Column(name = "business_license", length = 100)
    private String businessLicense;
    
    @Column(name = "website", length = 255)
    private String website;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING_VERIFICATION;
    
    @Embedded
    private MerchantSettings settings;
    
    @Column(name = "balance", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(name = "total_volume", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalVolume = BigDecimal.ZERO;
    
    @Column(name = "total_transactions")
    @Builder.Default
    private Long totalTransactions = 0L;
    
    @Column(name = "api_key", length = 255)
    private String apiKey;
    
    @Column(name = "api_secret", length = 255)
    private String apiSecret;
    
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;
    
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
    
    @Column(name = "verified_at")
    private Instant verifiedAt;
    
    @Column(name = "last_payment_at")
    private Instant lastPaymentAt;
    
    @ElementCollection
    @CollectionTable(name = "merchant_metadata", 
        joinColumns = @JoinColumn(name = "merchant_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    private Long version;
    
    public enum MerchantStatus {
        PENDING_VERIFICATION,
        UNDER_REVIEW,
        ACTIVE,
        SUSPENDED,
        CLOSED,
        REJECTED
    }
    
    public boolean isActive() {
        return status == MerchantStatus.ACTIVE;
    }
    
    public void updateBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    
    public void incrementTransactionCount() {
        this.totalTransactions++;
        this.lastPaymentAt = Instant.now();
    }
}