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
@Table(name = "suspicious_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SuspiciousActivity {

    @Id
    private UUID sarId;

    @Column(unique = true, nullable = false)
    private String sarNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SARStatus status;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String subjectType; // INDIVIDUAL, ORGANIZATION

    private String suspectInformation;

    @Column(nullable = false)
    private LocalDate incidentDate;

    private LocalDate reportingDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String suspiciousActivityDescription;

    @Column(columnDefinition = "TEXT")
    private String narrative;

    @Column(precision = 19, scale = 2)
    private BigDecimal amountInvolved;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @ElementCollection
    @CollectionTable(name = "sar_transaction_ids", joinColumns = @JoinColumn(name = "sar_id"))
    @Column(name = "transaction_id")
    private List<String> involvedTransactions;

    @ElementCollection
    @CollectionTable(name = "sar_account_ids", joinColumns = @JoinColumn(name = "sar_id"))
    @Column(name = "account_id")
    private List<String> involvedAccounts;

    private String investigatingOfficer;

    private String complianceOfficer;

    private String approvedBy;

    private LocalDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Column(nullable = false)
    private Boolean requiresImmediateAttention;

    // Filing information
    private String filingInstitutionName;

    private String filingInstitutionCode;

    private String regulatoryFilingNumber;

    private LocalDateTime filedAt;

    private String filedBy;

    @Enumerated(EnumType.STRING)
    private FilingStatus filingStatus;

    // Documentation
    @ElementCollection
    @CollectionTable(name = "sar_attachments", joinColumns = @JoinColumn(name = "sar_id"))
    @Column(name = "attachment_path")
    private List<String> attachments;

    @ElementCollection
    @CollectionTable(name = "sar_evidence", joinColumns = @JoinColumn(name = "sar_id"))
    @Column(name = "evidence_description")
    private List<String> evidence;

    // Risk assessment
    private Double riskScore;

    private String riskLevel;

    @ElementCollection
    @CollectionTable(name = "sar_risk_factors", joinColumns = @JoinColumn(name = "sar_id"))
    @Column(name = "risk_factor")
    private List<String> riskFactors;

    // Follow-up actions
    @Column(columnDefinition = "TEXT")
    private String recommendedActions;

    @Column(columnDefinition = "TEXT")
    private String followUpNotes;

    private LocalDateTime followUpDate;

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
        if (sarId == null) {
            sarId = UUID.randomUUID();
        }
        if (status == null) {
            status = SARStatus.DRAFT;
        }
        if (priority == null) {
            priority = Priority.MEDIUM;
        }
        if (requiresImmediateAttention == null) {
            requiresImmediateAttention = false;
        }
        if (filingStatus == null) {
            filingStatus = FilingStatus.NOT_FILED;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (sarNumber == null) {
            sarNumber = generateSARNumber();
        }
    }

    private String generateSARNumber() {
        // Generate SAR number based on year and sequence
        return "SAR-" + LocalDate.now().getYear() + "-" + System.currentTimeMillis();
    }

    public enum SARStatus {
        DRAFT,
        UNDER_REVIEW,
        APPROVED,
        FILED,
        ACKNOWLEDGED,
        CLOSED
    }

    public enum ActivityType {
        STRUCTURING,
        MONEY_LAUNDERING,
        TERRORIST_FINANCING,
        FRAUD,
        BRIBERY_CORRUPTION,
        IDENTITY_THEFT,
        CHECK_KITING,
        ELDER_ABUSE,
        HUMAN_TRAFFICKING,
        CYBER_CRIME,
        OTHER
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    public enum FilingStatus {
        NOT_FILED,
        PENDING_FILING,
        FILED,
        ACKNOWLEDGED,
        REJECTED,
        RESUBMITTED
    }
}