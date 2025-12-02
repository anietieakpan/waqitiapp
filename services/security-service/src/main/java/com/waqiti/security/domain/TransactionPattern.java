package com.waqiti.security.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing transaction patterns used in AML analysis
 */
@Entity
@Table(name = "transaction_patterns", indexes = {
    @Index(name = "idx_transaction_patterns_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_transaction_patterns_user_id", columnList = "user_id"),
    @Index(name = "idx_transaction_patterns_timestamp", columnList = "timestamp"),
    @Index(name = "idx_transaction_patterns_pattern_type", columnList = "pattern_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TransactionPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "recipient_wallet_id")
    private UUID recipientWalletId;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "status", nullable = false)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type")
    private PatternType patternType;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "is_cross_border")
    private Boolean isCrossBorder;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "merchant_category")
    private String merchantCategory;

    @Column(name = "risk_score")
    private Integer riskScore; // 1-100 scale

    @Column(name = "velocity_count") // Number of transactions in time window
    private Integer velocityCount;

    @Column(name = "cumulative_amount") // Running total for the day/period
    private BigDecimal cumulativeAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    public enum PatternType {
        NORMAL,
        SUSPICIOUS,
        HIGH_VELOCITY,
        STRUCTURING,
        ROUND_AMOUNT,
        OFF_HOURS,
        CROSS_BORDER,
        HIGH_RISK_COUNTRY,
        UNUSUAL_MERCHANT,
        ACCOUNT_TESTING,
        CIRCULAR_FLOW
    }
}