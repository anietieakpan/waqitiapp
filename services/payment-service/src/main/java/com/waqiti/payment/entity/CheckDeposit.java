package com.waqiti.payment.entity;

import com.waqiti.common.encryption.EncryptedStringConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a mobile check deposit
 */
@Entity
@Table(name = "check_deposits",
    indexes = {
        @Index(name = "idx_check_user_id", columnList = "user_id"),
        @Index(name = "idx_check_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_check_status", columnList = "status"),
        @Index(name = "idx_check_created_at", columnList = "created_at"),
        @Index(name = "idx_check_micr_account", columnList = "micr_account_number"),
        @Index(name = "idx_check_duplicate", columnList = "micr_routing_number, micr_account_number, check_number, amount"),
        @Index(name = "idx_check_idempotency", columnList = "idempotency_key", unique = true)
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CheckDeposit {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CheckDepositStatus status;
    
    // Check images
    @Column(name = "front_image_url", nullable = false, length = 500)
    private String frontImageUrl; // Encrypted S3 URL
    
    @Column(name = "back_image_url", nullable = false, length = 500)
    private String backImageUrl; // Encrypted S3 URL
    
    @Column(name = "front_image_hash", nullable = false, length = 64)
    private String frontImageHash; // SHA-256 hash for duplicate detection
    
    @Column(name = "back_image_hash", nullable = false, length = 64)
    private String backImageHash; // SHA-256 hash for duplicate detection
    
    // MICR data (extracted from check)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "micr_routing_number", length = 500)
    private String micrRoutingNumber; // PCI DSS: Encrypted routing number

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "micr_account_number", length = 500)
    private String micrAccountNumber; // PCI DSS: Encrypted account number
    
    @Column(name = "check_number", length = 20)
    private String checkNumber;
    
    @Column(name = "micr_raw_data", length = 500)
    private String micrRawData; // Encrypted raw MICR line
    
    // Check details
    @Column(name = "payee_name", length = 100)
    private String payeeName;
    
    @Column(name = "payor_name", length = 100)
    private String payorName;
    
    @Column(name = "check_date")
    private LocalDate checkDate;
    
    @Column(name = "memo", length = 255)
    private String memo;
    
    // Amount extraction
    @Column(name = "extracted_amount", precision = 19, scale = 4)
    private BigDecimal extractedAmount; // OCR extracted amount
    
    @Column(name = "amount_confidence", precision = 5, scale = 4)
    private BigDecimal amountConfidence; // 0-1 confidence score
    
    @Column(name = "manual_review_required", nullable = false)
    private boolean manualReviewRequired = false;
    
    // Hold information
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_type", length = 30)
    private CheckHoldType holdType;
    
    @Column(name = "hold_release_date")
    private LocalDate holdReleaseDate;
    
    @Column(name = "funds_available_date")
    private LocalDate fundsAvailableDate;
    
    @Column(name = "partial_availability_amount", precision = 19, scale = 4)
    private BigDecimal partialAvailabilityAmount;
    
    // Risk and fraud detection
    @Column(name = "risk_score", precision = 5, scale = 4)
    private BigDecimal riskScore; // 0-1 risk score
    
    @Column(name = "fraud_indicators", columnDefinition = "TEXT")
    private String fraudIndicators; // JSON array of fraud indicators
    
    @Column(name = "verification_status", length = 50)
    private String verificationStatus;
    
    // External processing
    @Column(name = "external_processor_id", length = 100)
    private String externalProcessorId;
    
    @Column(name = "external_reference_id", length = 100)
    private String externalReferenceId;
    
    @Column(name = "processor_response", columnDefinition = "TEXT")
    private String processorResponse; // JSON response from processor
    
    // Return/rejection information
    @Column(name = "return_code", length = 10)
    private String returnCode;
    
    @Column(name = "return_reason", length = 255)
    private String returnReason;
    
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
    
    // Device and location info
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "device_type", length = 50)
    private String deviceType;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;
    
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;
    
    // Idempotency
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;
    
    // Timestamps
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "deposited_at")
    private LocalDateTime depositedAt;
    
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;
    
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        submittedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}