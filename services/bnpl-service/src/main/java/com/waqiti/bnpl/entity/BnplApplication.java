/**
 * BNPL Application Entity
 * Represents a Buy Now Pay Later application
 */
package com.waqiti.bnpl.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bnpl_applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplApplication {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates in concurrent BNPL application processing
     * Applications may be updated by credit checks, merchant approvals, payment processing
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "merchant_id")
    private UUID merchantId;
    
    @Column(name = "merchant_name")
    private String merchantName;
    
    @Column(name = "order_id")
    private String orderId;
    
    @Column(name = "application_number", unique = true, nullable = false)
    private String applicationNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;
    
    @Column(name = "purchase_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchaseAmount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "down_payment", precision = 19, scale = 4)
    private BigDecimal downPayment;

    @Column(name = "financed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal financedAmount;

    @Column(name = "installment_count", nullable = false)
    private Integer installmentCount;

    @Column(name = "installment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal installmentAmount;

    @Column(name = "interest_rate", precision = 5, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;
    
    @Column(name = "application_date", nullable = false)
    private LocalDateTime applicationDate;
    
    @Column(name = "approval_date")
    private LocalDateTime approvalDate;
    
    @Column(name = "first_payment_date")
    private LocalDate firstPaymentDate;
    
    @Column(name = "final_payment_date")
    private LocalDate finalPaymentDate;
    
    @Column(name = "credit_score")
    private Integer creditScore;
    
    @Column(name = "risk_tier")
    private String riskTier;
    
    @Type(JsonType.class)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private JsonNode riskFactors;
    
    @Column(name = "decision")
    private String decision;
    
    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;
    
    @Column(name = "decision_date")
    private LocalDateTime decisionDate;
    
    @Column(name = "decision_by")
    private String decisionBy;
    
    @Column(name = "application_source")
    private String applicationSource;
    
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BnplInstallment> installments;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (applicationDate == null) {
            applicationDate = LocalDateTime.now();
        }
        if (status == null) {
            status = ApplicationStatus.PENDING;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (downPayment == null) {
            downPayment = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isApproved() {
        return status == ApplicationStatus.APPROVED;
    }
    
    public boolean isPending() {
        return status == ApplicationStatus.PENDING;
    }
    
    public boolean isRejected() {
        return status == ApplicationStatus.REJECTED;
    }
    
    public boolean isActive() {
        return status == ApplicationStatus.APPROVED || status == ApplicationStatus.ACTIVE;
    }
    
    public enum ApplicationStatus {
        PENDING,
        APPROVED,
        REJECTED,
        ACTIVE,
        COMPLETED,
        DEFAULTED,
        CANCELLED
    }
}