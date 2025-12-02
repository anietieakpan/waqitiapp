package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_risk_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CustomerRiskProfile {

    @Id
    private UUID profileId;

    @Column(unique = true, nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel overallRiskLevel;

    @Column(nullable = false)
    private Double riskScore;

    @Column(nullable = false)
    private LocalDate assessmentDate;

    private LocalDate nextReviewDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerType customerType;

    @Column(nullable = false)
    private String customerName;

    private String customerCategory;

    // Geographic risk factors
    private String residenceCountry;

    private String citizenshipCountry;

    private String businessCountry;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "customer_high_risk_countries", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "country_code")
    private List<String> highRiskCountries;

    // Business/occupation risk factors
    private String occupation;

    private String industry;

    private String businessNature;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "customer_risk_factors", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "risk_factor")
    private List<String> riskFactors;

    // Financial profile
    @Column(precision = 19, scale = 2)
    private BigDecimal expectedMonthlyVolume;

    @Column(precision = 19, scale = 2)
    private BigDecimal actualMonthlyVolume;

    @Column(precision = 19, scale = 2)
    private BigDecimal averageTransactionAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal largestTransactionAmount;

    private Integer expectedTransactionFrequency;

    private Integer actualTransactionFrequency;

    // PEP and sanctions screening
    @Column(nullable = false)
    private Boolean isPEP;

    @Column(nullable = false)
    private Boolean isSanctioned;

    @Column(nullable = false)
    private Boolean hasAdverseMedia;

    private String pepDetails;

    private String sanctionsDetails;

    private String adverseMediaDetails;

    // Transaction patterns
    @ElementCollection
    @CollectionTable(name = "customer_transaction_patterns", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "pattern_description")
    private List<String> suspiciousPatterns;

    @ElementCollection
    @CollectionTable(name = "customer_preferred_countries", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "country_code")
    private List<String> preferredCountries;

    @ElementCollection
    @CollectionTable(name = "customer_payment_methods", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "payment_method")
    private List<String> preferredPaymentMethods;

    // Compliance history
    private Integer alertCount;

    private Integer sarCount;

    private Integer ctrCount;

    private LocalDateTime lastAlertDate;

    private LocalDateTime lastSarDate;

    private LocalDateTime lastCtrDate;

    // Due diligence
    @Enumerated(EnumType.STRING)
    private DueDiligenceLevel dueDiligenceLevel;

    private LocalDate lastDueDiligenceDate;

    private LocalDate nextDueDiligenceDate;

    @Column(columnDefinition = "TEXT")
    private String dueDiligenceNotes;

    // Monitoring settings
    @Column(nullable = false)
    private Boolean enhancedMonitoring;

    @Column(nullable = false)
    private Boolean autoAlerts;

    @Column(precision = 19, scale = 2)
    private BigDecimal alertThreshold;

    private Integer monitoringFrequency; // days

    // Assessment details
    private String assessedBy;

    private String approvedBy;

    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String assessmentNotes;

    @Column(columnDefinition = "TEXT")
    private String recommendedActions;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private String createdBy;

    private String updatedBy;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (profileId == null) {
            profileId = UUID.randomUUID();
        }
        if (overallRiskLevel == null) {
            overallRiskLevel = RiskLevel.MEDIUM;
        }
        if (riskScore == null) {
            riskScore = 0.5; // Default medium risk
        }
        if (assessmentDate == null) {
            assessmentDate = LocalDate.now();
        }
        if (isPEP == null) {
            isPEP = false;
        }
        if (isSanctioned == null) {
            isSanctioned = false;
        }
        if (hasAdverseMedia == null) {
            hasAdverseMedia = false;
        }
        if (enhancedMonitoring == null) {
            enhancedMonitoring = false;
        }
        if (autoAlerts == null) {
            autoAlerts = true;
        }
        if (alertCount == null) {
            alertCount = 0;
        }
        if (sarCount == null) {
            sarCount = 0;
        }
        if (ctrCount == null) {
            ctrCount = 0;
        }
        if (dueDiligenceLevel == null) {
            dueDiligenceLevel = DueDiligenceLevel.STANDARD;
        }
        if (monitoringFrequency == null) {
            monitoringFrequency = 30; // 30 days
        }
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        EXTREME
    }

    public enum CustomerType {
        INDIVIDUAL,
        BUSINESS,
        GOVERNMENT,
        NON_PROFIT,
        FINANCIAL_INSTITUTION,
        MONEY_SERVICE_BUSINESS,
        TRUST,
        ESTATE
    }

    public enum DueDiligenceLevel {
        SIMPLIFIED,
        STANDARD,
        ENHANCED,
        ONGOING
    }
}