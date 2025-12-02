package com.waqiti.tax.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tax_returns", indexes = {
    @Index(name = "idx_tax_returns_user_id", columnList = "user_id"),
    @Index(name = "idx_tax_returns_tax_year", columnList = "tax_year"),
    @Index(name = "idx_tax_returns_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReturn {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "filing_status", nullable = false)
    private FilingStatus filingStatus;
    
    @Embedded
    private PersonalInfo personalInfo;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaxReturnStatus status = TaxReturnStatus.DRAFT;
    
    @Column(name = "estimated_refund", precision = 19, scale = 2)
    private BigDecimal estimatedRefund;
    
    @Column(name = "estimated_tax", precision = 19, scale = 2)
    private BigDecimal estimatedTax;
    
    @Column(name = "adjusted_gross_income", precision = 19, scale = 2)
    private BigDecimal adjustedGrossIncome;
    
    @Column(name = "total_income", precision = 19, scale = 2)
    private BigDecimal totalIncome;
    
    @Column(name = "federal_tax", precision = 19, scale = 2)
    private BigDecimal federalTax;
    
    @Column(name = "state_tax", precision = 19, scale = 2)
    private BigDecimal stateTax;
    
    @Column(name = "capital_gains", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal capitalGains = BigDecimal.ZERO;
    
    @Column(name = "deductions", precision = 19, scale = 2)
    private BigDecimal deductions;
    
    @Column(name = "tax_credits", precision = 19, scale = 2)
    private BigDecimal taxCredits;
    
    @Column(name = "total_withholdings", precision = 19, scale = 2)
    private BigDecimal totalWithholdings;
    
    @Column(name = "is_premium")
    @Builder.Default
    private Boolean isPremium = false;
    
    @Column(name = "include_crypto")
    @Builder.Default
    private Boolean includeCrypto = false;
    
    @Column(name = "include_investments")
    @Builder.Default
    private Boolean includeInvestments = false;
    
    @Column(name = "is_irs_authorized")
    @Builder.Default
    private Boolean isIrsAuthorized = false;
    
    @Column(name = "is_state_return_required")
    @Builder.Default
    private Boolean isStateReturnRequired = true;
    
    @Column(name = "irs_confirmation_number")
    private String irsConfirmationNumber;
    
    @Column(name = "state_confirmation_number")
    private String stateConfirmationNumber;
    
    @Column(name = "refund_received")
    @Builder.Default
    private Boolean refundReceived = false;
    
    @Column(name = "refund_received_date")
    private LocalDateTime refundReceivedDate;
    
    @Column(name = "filed_at")
    private LocalDateTime filedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "last_modified", nullable = false)
    private LocalDateTime lastModified;
    
    // Relationships
    @OneToMany(mappedBy = "taxReturn", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TaxDocument> documents;
    
    @OneToMany(mappedBy = "taxReturn", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TaxEstimate> estimates;
    
    @OneToMany(mappedBy = "taxReturn", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TaxForm> forms;
    
    public enum FilingStatus {
        SINGLE,
        MARRIED_FILING_JOINTLY,
        MARRIED_FILING_SEPARATELY,
        HEAD_OF_HOUSEHOLD,
        QUALIFYING_WIDOW
    }
    
    public enum TaxReturnStatus {
        DRAFT,
        IN_PROGRESS,
        READY_TO_FILE,
        FILED,
        ACCEPTED,
        REJECTED,
        AMENDED
    }
}