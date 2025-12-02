package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Data Breach Record - GDPR Articles 33-34
 *
 * Implements comprehensive data breach tracking and notification requirements
 * under GDPR Articles 33 (notification to supervisory authority) and 34
 * (communication to data subjects).
 *
 * Article 33 requires notification within 72 hours of becoming aware unless
 * unlikely to result in risk to rights and freedoms.
 *
 * Article 34 requires notification to individuals without undue delay if
 * likely to result in high risk to rights and freedoms.
 */
@Entity
@Table(name = "data_breaches",
       indexes = {
           @Index(name = "idx_breach_discovered_at", columnList = "discovered_at"),
           @Index(name = "idx_breach_status", columnList = "status"),
           @Index(name = "idx_breach_severity", columnList = "severity"),
           @Index(name = "idx_breach_regulatory_deadline", columnList = "regulatory_notification_deadline")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataBreach {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "id", length = 36)
    private String id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "breach_type", length = 50, nullable = false)
    private BreachType breachType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private BreachSeverity severity;

    @NotBlank
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @NotNull
    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt;

    @Column(name = "breach_occurred_at")
    private LocalDateTime breachOccurredAt;

    @NotBlank
    @Column(name = "reported_by", length = 255, nullable = false)
    private String reportedBy;

    @Positive
    @Column(name = "affected_user_count")
    private Integer affectedUserCount;

    @ElementCollection
    @CollectionTable(name = "breach_affected_data_categories",
                     joinColumns = @JoinColumn(name = "breach_id"))
    @Column(name = "data_category", length = 100)
    @Builder.Default
    private Set<String> affectedDataCategories = new HashSet<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private BreachStatus status;

    // Risk Assessment
    @Embedded
    private RiskAssessment riskAssessment;

    @Column(name = "likely_consequences", columnDefinition = "TEXT")
    private String likelyConsequences;

    @Column(name = "mitigation_measures", columnDefinition = "TEXT")
    private String mitigationMeasures;

    // Regulatory Notification (Article 33)
    @Column(name = "requires_regulatory_notification", nullable = false)
    @Builder.Default
    private boolean requiresRegulatoryNotification = true;

    @Column(name = "regulatory_notification_deadline")
    private LocalDateTime regulatoryNotificationDeadline; // 72 hours from discovery

    @Column(name = "regulatory_notified_at")
    private LocalDateTime regulatoryNotifiedAt;

    @Column(name = "regulatory_notification_reference", length = 255)
    private String regulatoryNotificationReference;

    @Column(name = "regulatory_exemption_reason", columnDefinition = "TEXT")
    private String regulatoryExemptionReason; // If notification not required

    // User Notification (Article 34)
    @Column(name = "requires_user_notification", nullable = false)
    @Builder.Default
    private boolean requiresUserNotification = false;

    @Column(name = "user_notification_deadline")
    private LocalDateTime userNotificationDeadline;

    @Column(name = "users_notified_at")
    private LocalDateTime usersNotifiedAt;

    @Column(name = "user_notification_count")
    private Integer userNotificationCount;

    @Column(name = "user_notification_method", length = 100)
    private String userNotificationMethod; // EMAIL, SMS, IN_APP, POSTAL

    @Column(name = "user_notification_exemption_reason", columnDefinition = "TEXT")
    private String userNotificationExemptionReason;

    // Technical Details
    @Column(name = "attack_vector", length = 100)
    private String attackVector;

    @Column(name = "vulnerability_exploited", columnDefinition = "TEXT")
    private String vulnerabilityExploited;

    @Column(name = "systems_affected", columnDefinition = "TEXT")
    private String systemsAffected;

    @Column(name = "data_compromised", columnDefinition = "TEXT")
    private String dataCompromised;

    // Containment and Recovery
    @Column(name = "contained_at")
    private LocalDateTime containedAt;

    @Column(name = "containment_actions", columnDefinition = "TEXT")
    private String containmentActions;

    @Column(name = "recovery_completed_at")
    private LocalDateTime recoveryCompletedAt;

    @Column(name = "recovery_actions", columnDefinition = "TEXT")
    private String recoveryActions;

    // Investigation
    @Column(name = "investigation_status", length = 50)
    private String investigationStatus;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "investigation_completed_at")
    private LocalDateTime investigationCompletedAt;

    @Column(name = "investigation_report_url", length = 500)
    private String investigationReportUrl;

    // Legal and Compliance
    @Column(name = "dpo_notified_at")
    private LocalDateTime dpoNotifiedAt;

    @Column(name = "legal_team_notified_at")
    private LocalDateTime legalTeamNotifiedAt;

    @Column(name = "insurance_notified_at")
    private LocalDateTime insuranceNotifiedAt;

    @Column(name = "law_enforcement_notified_at")
    private LocalDateTime lawEnforcementNotifiedAt;

    @Column(name = "law_enforcement_reference", length = 255)
    private String lawEnforcementReference;

    // Follow-up Actions
    @Column(name = "lessons_learned", columnDefinition = "TEXT")
    private String lessonsLearned;

    @Column(name = "preventive_measures", columnDefinition = "TEXT")
    private String preventiveMeasures;

    @Column(name = "policy_changes_required", columnDefinition = "TEXT")
    private String policyChangesRequired;

    // Metadata
    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // Set deadlines if not already set
        if (requiresRegulatoryNotification && regulatoryNotificationDeadline == null) {
            regulatoryNotificationDeadline = discoveredAt.plusHours(72);
        }

        if (requiresUserNotification && userNotificationDeadline == null) {
            // Without undue delay - typically 24-48 hours
            userNotificationDeadline = discoveredAt.plusHours(24);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if regulatory notification deadline has been breached
     */
    public boolean isRegulatoryDeadlineBreached() {
        if (!requiresRegulatoryNotification || regulatoryNotifiedAt != null) {
            return false;
        }
        return LocalDateTime.now().isAfter(regulatoryNotificationDeadline);
    }

    /**
     * Check if user notification deadline has been breached
     */
    public boolean isUserNotificationDeadlineBreached() {
        if (!requiresUserNotification || usersNotifiedAt != null) {
            return false;
        }
        return LocalDateTime.now().isAfter(userNotificationDeadline);
    }

    /**
     * Mark regulatory notification as sent
     */
    public void markRegulatoryNotified(String reference) {
        this.regulatoryNotifiedAt = LocalDateTime.now();
        this.regulatoryNotificationReference = reference;
    }

    /**
     * Mark users as notified
     */
    public void markUsersNotified(int count, String method) {
        this.usersNotifiedAt = LocalDateTime.now();
        this.userNotificationCount = count;
        this.userNotificationMethod = method;
    }

    /**
     * Mark breach as contained
     */
    public void markContained(String actions) {
        this.containedAt = LocalDateTime.now();
        this.containmentActions = actions;
        this.status = BreachStatus.CONTAINED;
    }

    /**
     * Mark breach as resolved
     */
    public void markResolved(String recoveryActions) {
        this.recoveryCompletedAt = LocalDateTime.now();
        this.recoveryActions = recoveryActions;
        this.status = BreachStatus.RESOLVED;
        this.closedAt = LocalDateTime.now();
    }
}
