package com.waqiti.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing external provider transactions for reconciliation
 */
@Entity
@Table(name = "provider_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderTransaction {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_transaction_id", nullable = false, length = 100)
    private String providerTransactionId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "matched", nullable = false)
    @Builder.Default
    private Boolean matched = false;

    @Column(name = "matched_transaction_id", length = 50)
    private String matchedTransactionId;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "merchant_id", length = 50)
    private String merchantId;

    @Column(name = "customer_id", length = 50)
    private String customerId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    @Column(name = "settlement_status", length = 20)
    @Builder.Default
    private String settlementStatus = "PENDING";

    @Column(name = "webhook_id", length = 100)
    private String webhookId;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Utility methods for reconciliation
    public boolean isMatchable() {
        return !matched && isSuccessfulStatus();
    }

    public boolean isSuccessfulStatus() {
        return "SUCCESS".equalsIgnoreCase(status) || 
               "COMPLETED".equalsIgnoreCase(status) || 
               "SETTLED".equalsIgnoreCase(status);
    }

    public boolean isFailedStatus() {
        return "FAILED".equalsIgnoreCase(status) || 
               "REJECTED".equalsIgnoreCase(status) || 
               "CANCELLED".equalsIgnoreCase(status);
    }

    public boolean requiresHighPriorityMatching() {
        return amount.compareTo(new BigDecimal("10000")) > 0;
    }

    public String getUniqueIdentifier() {
        return provider + "-" + providerTransactionId + "-" + amount;
    }

    public long getAgeInHours() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toHours();
    }
}