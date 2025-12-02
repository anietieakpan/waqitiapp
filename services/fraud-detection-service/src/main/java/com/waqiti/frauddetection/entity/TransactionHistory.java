package com.waqiti.frauddetection.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction History Entity for Fraud Detection Analysis
 * 
 * Comprehensive transaction record storage for ML model training and feature engineering:
 * - Complete transaction lifecycle tracking
 * - User behavior pattern analysis
 * - Merchant and recipient relationship mapping
 * - Geographic and temporal pattern recognition
 * - Channel and device usage tracking
 * - Risk scoring and fraud label storage
 * 
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 * @since 2025-01-17
 */
@Entity
@Table(name = "transaction_history", indexes = {
    @Index(name = "idx_user_id_timestamp", columnList = "user_id, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_recipient_id", columnList = "recipient_id"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    /**
     * Version for optimistic locking to prevent concurrent fraud score updates
     * Critical for ML model updates and fraud detection result modifications
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "transaction_id", unique = true)
    private String transactionId;
    
    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "transaction_type", nullable = false)
    private String transactionType;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    // Recipient Information
    @Column(name = "recipient_id")
    private String recipientId;
    
    @Column(name = "recipient_name")
    private String recipientName;
    
    @Column(name = "recipient_account")
    private String recipientAccount;
    
    // Merchant Information
    @Column(name = "merchant_id")
    private String merchantId;
    
    @Column(name = "merchant_name")
    private String merchantName;
    
    @Column(name = "merchant_category")
    private String merchantCategory;
    
    @Column(name = "merchant_country")
    private String merchantCountry;
    
    // Geographic Information
    @Column(name = "country")
    private String country;
    
    @Column(name = "city")
    private String city;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    // Device and Channel Information
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "channel")
    private String channel;
    
    @Column(name = "platform")
    private String platform;
    
    // Risk and Fraud Information
    @Column(name = "fraud_score")
    private Double fraudScore;
    
    @Column(name = "is_fraud")
    private Boolean isFraud;
    
    @Column(name = "fraud_type")
    private String fraudType;
    
    @Column(name = "risk_level")
    private String riskLevel;
    
    // Processing Information
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;
    
    @Column(name = "authorization_code")
    private String authorizationCode;
    
    // Additional Metadata
    @Column(name = "description")
    private String description;
    
    @Column(name = "reference_number")
    private String referenceNumber;
    
    @Column(name = "batch_id")
    private String batchId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Audit Fields
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "updated_by")
    private String updatedBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}