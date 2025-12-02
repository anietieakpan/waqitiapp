package com.waqiti.payment.entity;

import com.waqiti.common.entity.BaseEntity;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a QR code payment
 */
@Entity
@Table(name = "qr_code_payments",
        indexes = {
                @Index(name = "idx_qr_code_id", columnList = "qr_code_id", unique = true),
                @Index(name = "idx_merchant_id", columnList = "merchant_id"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_expires_at", columnList = "expires_at"),
                @Index(name = "idx_scanned_by", columnList = "scanned_by"),
                @Index(name = "idx_transaction_id", columnList = "transaction_id")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class QRCodePayment extends BaseEntity {

    @Column(name = "qr_code_id", nullable = false, unique = true, length = 50)
    private String qrCodeId;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QRCodePaymentStatus status;

    @Column(name = "qr_data", columnDefinition = "TEXT")
    private String qrData;

    @Lob
    @Column(name = "qr_code_image")
    private byte[] qrCodeImage;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "scanned_by")
    private String scannedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "terminal_id")
    private String terminalId;

    @Column(name = "location")
    private String location;

    @Column(name = "pos_reference")
    private String posReference;

    // Fraud and security
    @Column(name = "fraud_score")
    private BigDecimal fraudScore;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "ip_address")
    private String ipAddress;

    // Statistics
    @Column(name = "scan_attempts")
    private Integer scanAttempts;

    @Column(name = "last_scan_attempt")
    private Instant lastScanAttempt;

    // Metadata
    @Type(type = "json")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Callbacks
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = QRCodePaymentStatus.PENDING;
        }
        if (scanAttempts == null) {
            scanAttempts = 0;
        }
    }

    /**
     * Check if QR code is expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Check if QR code can be scanned
     */
    public boolean canBeScanned() {
        return status == QRCodePaymentStatus.PENDING && !isExpired();
    }

    /**
     * Check if QR code can be processed
     */
    public boolean canBeProcessed() {
        return status == QRCodePaymentStatus.SCANNED && !isExpired();
    }

    /**
     * Increment scan attempts
     */
    public void incrementScanAttempts() {
        if (scanAttempts == null) {
            scanAttempts = 0;
        }
        scanAttempts++;
        lastScanAttempt = Instant.now();
    }
}