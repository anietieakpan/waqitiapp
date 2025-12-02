package com.waqiti.payment.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity class for NFC transactions
 */
@Entity
@Table(name = "nfc_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Version for optimistic locking - prevents race conditions
     * CRITICAL for financial transactions to prevent double-processing
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "transaction_id", unique = true, nullable = false, length = 64)
    private String transactionId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "transfer_id", length = 64)
    private String transferId;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType; // MERCHANT_PAYMENT, P2P_TRANSFER, CONTACT_EXCHANGE

    @Column(name = "status", nullable = false, length = 32)
    private String status; // PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "processing_fee", precision = 19, scale = 2)
    private BigDecimal processingFee;

    @Column(name = "net_amount", precision = 19, scale = 2)
    private BigDecimal netAmount;

    // Participant information
    @Column(name = "sender_id", length = 64)
    private String senderId;

    @Column(name = "recipient_id", length = 64)
    private String recipientId;

    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    // NFC specific fields
    @Column(name = "nfc_session_id", length = 64)
    private String nfcSessionId;

    @Column(name = "nfc_protocol_version", length = 32)
    private String nfcProtocolVersion;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "sender_device_id", length = 128)
    private String senderDeviceId;

    @Column(name = "recipient_device_id", length = 128)
    private String recipientDeviceId;

    @Column(name = "secure_element_used")
    private Boolean secureElementUsed;

    // Security and fraud detection
    @Column(name = "signature", length = 1024)
    private String signature;

    @Column(name = "security_level", length = 32)
    private String securityLevel;

    @Column(name = "fraud_check_passed")
    private Boolean fraudCheckPassed;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "device_fingerprint", length = 512)
    private String deviceFingerprint;

    // Location data
    @Column(name = "latitude", precision = 10, scale = 8)
    private Double latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private Double longitude;

    @Column(name = "location_accuracy", length = 10)
    private String locationAccuracy;

    // Processing details
    @Column(name = "processing_method", length = 64)
    private String processingMethod;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "authorization_code", length = 64)
    private String authorizationCode;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Settlement information
    @Column(name = "estimated_settlement")
    private Instant estimatedSettlement;

    @Column(name = "settlement_method", length = 64)
    private String settlementMethod;

    @Column(name = "settlement_status", length = 32)
    private String settlementStatus;

    // Error information
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    // Additional information
    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "message", length = 255)
    private String message;

    @Column(name = "order_id", length = 64)
    private String orderId;

    // Receipt and documentation
    @Column(name = "receipt_url", length = 512)
    private String receiptUrl;

    @Column(name = "invoice_url", length = 512)
    private String invoiceUrl;

    @Column(name = "confirmation_code", length = 64)
    private String confirmationCode;

    // Blockchain/audit trail
    @Column(name = "blockchain_tx_hash", length = 128)
    private String blockchainTxHash;

    @Column(name = "audit_trail_id", length = 64)
    private String auditTrailId;

    // Client information
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    // Additional metadata
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // Indexes
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        
        if (transactionId == null) {
            transactionId = generateTransactionId();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Generates a unique transaction ID
     */
    private String generateTransactionId() {
        return String.format("NFC_TXN_%d_%s", 
                System.currentTimeMillis(), 
                Long.toHexString(System.nanoTime()));
    }

    /**
     * Checks if the transaction is in a final state
     */
    public boolean isFinalState() {
        return "SUCCESS".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELLED".equals(status);
    }

    /**
     * Checks if the transaction is still processing
     */
    public boolean isProcessing() {
        return "PENDING".equals(status) || "PROCESSING".equals(status);
    }

    /**
     * Checks if the transaction was successful
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(status);
    }

    /**
     * Gets the processing duration in milliseconds
     */
    public long getProcessingDurationMs() {
        if (createdAt == null) {
            return 0L;
        }
        
        Instant endTime = completedAt != null ? completedAt : Instant.now();
        return endTime.toEpochMilli() - createdAt.toEpochMilli();
    }

    /**
     * Determines if this is a merchant payment transaction
     */
    public boolean isMerchantPayment() {
        return "MERCHANT_PAYMENT".equals(transactionType);
    }

    /**
     * Determines if this is a P2P transfer transaction
     */
    public boolean isP2PTransfer() {
        return "P2P_TRANSFER".equals(transactionType);
    }

    /**
     * Determines if this is a contact exchange transaction
     */
    public boolean isContactExchange() {
        return "CONTACT_EXCHANGE".equals(transactionType);
    }
}