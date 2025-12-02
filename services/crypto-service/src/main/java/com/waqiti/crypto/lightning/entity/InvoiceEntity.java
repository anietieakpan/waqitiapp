package com.waqiti.crypto.lightning.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a Lightning Network invoice
 */
@Entity
@Table(name = "lightning_invoices", indexes = {
    @Index(name = "idx_invoice_user", columnList = "userId"),
    @Index(name = "idx_invoice_hash", columnList = "paymentHash", unique = true),
    @Index(name = "idx_invoice_status", columnList = "status"),
    @Index(name = "idx_invoice_created", columnList = "createdAt"),
    @Index(name = "idx_invoice_expires", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"paymentRequest", "paymentSecret"})
public class InvoiceEntity extends BaseEntity {

    @Column(nullable = false)
    private String userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String paymentRequest;

    @Column(nullable = false, unique = true, length = 64)
    private String paymentHash;

    @Column(length = 64)
    private String paymentSecret;

    @Column(length = 64)
    private String paymentPreimage;

    @Column(nullable = false)
    private Long amountSat;

    @Column
    private Long amountPaidSat;

    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant settledAt;

    @Column
    private Instant cancelledAt;

    @ElementCollection
    @CollectionTable(name = "invoice_metadata", joinColumns = @JoinColumn(name = "invoice_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @Column
    private String webhookUrl;

    @Column
    private Integer webhookAttempts;

    @Column
    private Instant lastWebhookAttempt;

    @Column
    private String lightningAddress;

    @Column
    private String lnurlPayCode;

    @Column
    private Boolean isRecurring;

    @Column
    private String recurringSchedule;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = InvoiceStatus.PENDING;
        }
        if (webhookAttempts == null) {
            webhookAttempts = 0;
        }
        if (isRecurring == null) {
            isRecurring = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Check if invoice is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if invoice is payable
     */
    public boolean isPayable() {
        return status == InvoiceStatus.PENDING && !isExpired();
    }

    /**
     * Calculate remaining time until expiry in seconds
     */
    public long getRemainingSeconds() {
        if (expiresAt == null) {
            return 0;
        }
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}