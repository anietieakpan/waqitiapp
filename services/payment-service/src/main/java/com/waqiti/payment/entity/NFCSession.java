package com.waqiti.payment.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;

/**
 * Entity class for NFC sessions
 */
@Entity
@Table(name = "nfc_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "session_id", unique = true, nullable = false, length = 64)
    private String sessionId;

    @Column(name = "session_token", unique = true, nullable = false, length = 128)
    private String sessionToken;

    @Column(name = "session_type", nullable = false, length = 32)
    private String sessionType; // MERCHANT, P2P, CONTACT_EXCHANGE

    @Column(name = "status", nullable = false, length = 32)
    private String status; // ACTIVE, EXPIRED, CANCELLED, COMPLETED

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    // Session configuration
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "order_id", length = 64)
    private String orderId;

    // NFC protocol information
    @Column(name = "nfc_protocol_version", length = 32)
    private String nfcProtocolVersion;

    @Column(name = "encryption_algorithm", length = 32)
    private String encryptionAlgorithm;

    // Security settings
    @Column(name = "security_level", length = 32)
    private String securityLevel; // LOW, MEDIUM, HIGH

    @Column(name = "require_biometric")
    private Boolean requireBiometric;

    @Column(name = "require_pin")
    private Boolean requirePin;

    // Session limits
    @Column(name = "max_transaction_amount", precision = 19, scale = 2)
    private BigDecimal maxTransactionAmount;

    @Column(name = "max_transaction_count")
    private Integer maxTransactionCount;

    @Column(name = "remaining_transactions")
    private Integer remainingTransactions;

    // Cryptographic keys (stored as byte arrays)
    @Column(name = "public_key", columnDefinition = "BLOB")
    private byte[] publicKeyBytes;

    @Column(name = "private_key", columnDefinition = "BLOB")
    private byte[] privateKeyBytes;

    // Transient fields for actual key objects
    @Transient
    private PublicKey publicKey;

    @Transient
    private PrivateKey privateKey;

    // Location data
    @Column(name = "latitude", precision = 10, scale = 8)
    private Double latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private Double longitude;

    @Column(name = "location_accuracy", length = 10)
    private String locationAccuracy;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // WebSocket information for real-time updates
    @Column(name = "websocket_url", length = 512)
    private String websocketUrl;

    @Column(name = "websocket_token", length = 128)
    private String websocketToken;

    // QR code for backup payment method
    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "deep_link_url", length = 512)
    private String deepLinkUrl;

    // Usage statistics
    @Column(name = "transaction_count")
    private Integer transactionCount;

    @Column(name = "total_amount_processed", precision = 19, scale = 2)
    private BigDecimal totalAmountProcessed;

    @Column(name = "last_transaction_id", length = 64)
    private String lastTransactionId;

    // Additional metadata
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        lastActivityAt = Instant.now();
        transactionCount = 0;
        totalAmountProcessed = BigDecimal.ZERO;
        
        if (remainingTransactions == null && maxTransactionCount != null) {
            remainingTransactions = maxTransactionCount;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        lastActivityAt = Instant.now();
    }

    /**
     * Checks if the session is active and not expired
     */
    public boolean isActive() {
        return "ACTIVE".equals(status) && 
               expiresAt != null && 
               Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if the session has expired
     */
    public boolean isExpired() {
        return "EXPIRED".equals(status) || 
               (expiresAt != null && Instant.now().isAfter(expiresAt));
    }

    /**
     * Gets remaining session time in seconds
     */
    public long getRemainingTimeSeconds() {
        if (expiresAt == null) {
            return 0L;
        }
        
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0L, remaining);
    }

    /**
     * Checks if the session can accept more transactions
     */
    public boolean canAcceptTransaction() {
        return isActive() && 
               (remainingTransactions == null || remainingTransactions > 0);
    }

    /**
     * Decrements the remaining transaction count
     */
    public void consumeTransaction() {
        if (remainingTransactions != null && remainingTransactions > 0) {
            remainingTransactions--;
        }
        
        if (transactionCount == null) {
            transactionCount = 1;
        } else {
            transactionCount++;
        }
    }

    /**
     * Adds to the total amount processed
     */
    public void addProcessedAmount(BigDecimal amount) {
        if (totalAmountProcessed == null) {
            totalAmountProcessed = amount;
        } else {
            totalAmountProcessed = totalAmountProcessed.add(amount);
        }
    }

    /**
     * Checks if this is a merchant session
     */
    public boolean isMerchantSession() {
        return "MERCHANT".equals(sessionType);
    }

    /**
     * Checks if this is a P2P session
     */
    public boolean isP2PSession() {
        return "P2P".equals(sessionType);
    }

    /**
     * Checks if this is a contact exchange session
     */
    public boolean isContactExchangeSession() {
        return "CONTACT_EXCHANGE".equals(sessionType);
    }

    /**
     * Expires the session
     */
    public void expire() {
        status = "EXPIRED";
        completedAt = Instant.now();
    }

    /**
     * Completes the session
     */
    public void complete() {
        status = "COMPLETED";
        completedAt = Instant.now();
    }

    /**
     * Cancels the session
     */
    public void cancel() {
        status = "CANCELLED";
        completedAt = Instant.now();
    }

    /**
     * Updates last activity timestamp
     */
    public void updateActivity() {
        lastActivityAt = Instant.now();
    }

    /**
     * Checks if the session requires high security
     */
    public boolean requiresHighSecurity() {
        return "HIGH".equals(securityLevel) || requireBiometric || requirePin;
    }
}