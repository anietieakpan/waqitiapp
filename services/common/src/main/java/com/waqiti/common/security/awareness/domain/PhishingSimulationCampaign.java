package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phishing Simulation Campaign
 *
 * Represents a phishing simulation campaign sent to employees
 * to test their security awareness.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "phishing_simulation_campaigns", indexes = {
        @Index(name = "idx_campaign_status", columnList = "status"),
        @Index(name = "idx_campaign_scheduled", columnList = "scheduled_date"),
        @Index(name = "idx_campaign_completed", columnList = "completed_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhishingSimulationCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "campaign_id")
    private UUID id;

    @Column(name = "campaign_name", nullable = false, length = 255)
    private String campaignName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 50)
    private PhishingTemplateType templateType;

    @Column(name = "subject_line", length = 500)
    private String subjectLine;

    @Column(name = "sender_email", length = 255)
    private String senderEmail;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    @Column(name = "difficulty_level", length = 50)
    private String difficultyLevel;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "started_date")
    private LocalDateTime startedDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "target_audience", columnDefinition = "TEXT")
    private String targetAudience;

    @Column(name = "target_employees_count")
    @Builder.Default
    private Integer targetEmployeesCount = 0;

    @Column(name = "total_targeted")
    @Builder.Default
    private Integer totalTargeted = 0;

    @Column(name = "total_delivered")
    @Builder.Default
    private Integer totalDelivered = 0;

    @Column(name = "emails_sent")
    @Builder.Default
    private Integer emailsSent = 0;

    @Column(name = "emails_opened")
    @Builder.Default
    private Integer emailsOpened = 0;

    @Column(name = "links_clicked")
    @Builder.Default
    private Integer linksClicked = 0;

    @Column(name = "data_submitted")
    @Builder.Default
    private Integer dataSubmitted = 0;

    @Column(name = "reported_as_phishing")
    @Builder.Default
    private Integer reportedAsPhishing = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Convenience getters for compatibility
    public Integer getTotalTargeted() {
        return targetEmployeesCount != null ? targetEmployeesCount : totalTargeted;
    }

    public Integer getTotalDelivered() {
        return emailsSent != null ? emailsSent : totalDelivered;
    }

    public Integer getTotalOpened() {
        return emailsOpened != null ? emailsOpened : 0;
    }

    public Integer getTotalClicked() {
        return linksClicked != null ? linksClicked : 0;
    }

    public Integer getTotalSubmittedData() {
        return dataSubmitted != null ? dataSubmitted : 0;
    }

    public Integer getTotalReported() {
        return reportedAsPhishing != null ? reportedAsPhishing : 0;
    }

    public Integer getTotalEmailsSent() {
        return emailsSent != null ? emailsSent : totalDelivered;
    }

    // Convenience setters for compatibility
    public void setTotalTargeted(Integer value) {
        this.targetEmployeesCount = value;
        this.totalTargeted = value;
    }

    public void setTotalDelivered(Integer value) {
        this.emailsSent = value;
        this.totalDelivered = value;
    }

    public void setTotalOpened(Integer value) {
        this.emailsOpened = value;
    }

    public void setTotalClicked(Integer value) {
        this.linksClicked = value;
    }

    public void setTotalSubmittedData(Integer value) {
        this.dataSubmitted = value;
    }

    public void setTotalReported(Integer value) {
        this.reportedAsPhishing = value;
    }

    @Version
    @Column(name = "version")
    private Long version;

    public enum PhishingTemplateType {
        CEO_FRAUD,
        PAYROLL_SCAM,
        IT_SUPPORT_SCAM,
        PACKAGE_DELIVERY,
        PASSWORD_RESET,
        INVOICE_PAYMENT,
        TAX_REFUND,
        ACCOUNT_SUSPENSION,
        SECURITY_ALERT,
        GIFT_CARD_REQUEST
    }

    public enum CampaignStatus {
        DRAFT,
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    /**
     * Calculate failure rate (clicked + submitted)
     */
    public double getFailureRate() {
        if (emailsSent == 0) {
            return 0.0;
        }
        return ((double) (linksClicked + dataSubmitted) / emailsSent) * 100;
    }

    /**
     * Calculate success rate (reported as phishing)
     */
    public double getSuccessRate() {
        if (emailsSent == 0) {
            return 0.0;
        }
        return ((double) reportedAsPhishing / emailsSent) * 100;
    }

    /**
     * Start campaign
     */
    public void startCampaign() {
        this.status = CampaignStatus.IN_PROGRESS;
        this.startedDate = LocalDateTime.now();
    }

    /**
     * Complete campaign
     */
    public void completeCampaign() {
        this.status = CampaignStatus.COMPLETED;
        this.completedDate = LocalDateTime.now();
    }
}