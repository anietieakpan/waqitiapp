package com.waqiti.payment.domain;

import com.waqiti.common.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ach_transactions", indexes = {
    @Index(name = "idx_ach_transaction_id", columnList = "ach_transaction_id", unique = true),
    @Index(name = "idx_ach_customer_id", columnList = "customer_id"),
    @Index(name = "idx_ach_status", columnList = "status"),
    @Index(name = "idx_ach_effective_date", columnList = "effective_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "ach_transaction_id", nullable = false, unique = true, length = 100)
    private String achTransactionId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "sec_code", length = 10)
    private String secCode;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "routing_number", length = 500)
    private String routingNumber; // PCI DSS Requirement: Encrypted with AES-256-GCM

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", length = 500)
    private String accountNumber; // PCI DSS Requirement: Encrypted with AES-256-GCM

    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "company_id", length = 50)
    private String companyId;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "originator_id", length = 50)
    private String originatorId;

    @Column(name = "receiver_id", length = 50)
    private String receiverId;

    @Column(name = "status", length = 50, nullable = false)
    @Builder.Default
    private String status = "PENDING";

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
