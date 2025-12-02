package com.waqiti.business.domain;

import com.waqiti.common.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "business_sub_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BusinessSubAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "main_account_id", nullable = false)
    private UUID mainAccountId;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "spending_limit", precision = 19, scale = 2)
    private BigDecimal spendingLimit;

    @Column(name = "current_balance", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "description", length = 1000)
    private String description;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", unique = true, length = 500) // PCI DSS: Encrypted account number
    private String accountNumber;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @PrePersist
    private void prePersist() {
        if (accountNumber == null) {
            accountNumber = generateAccountNumber();
        }
    }

    private String generateAccountNumber() {
        return "SUB-" + mainAccountId.toString().substring(0, 8).toUpperCase() + 
               "-" + System.currentTimeMillis() % 10000;
    }
}