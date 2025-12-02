package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phishing Test Result
 *
 * Tracks individual employee responses to phishing simulation campaigns.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "phishing_test_results", indexes = {
        @Index(name = "idx_test_result_employee", columnList = "employee_id"),
        @Index(name = "idx_test_result_campaign", columnList = "campaign_id"),
        @Index(name = "idx_test_result_action", columnList = "action_taken")
})
@Data
@Builder(builderMethodName = "internalBuilder")
@NoArgsConstructor
@AllArgsConstructor
public class PhishingTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "result_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private PhishingSimulationCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "email_sent")
    @Builder.Default
    private Boolean emailSent = false;

    @Column(name = "email_opened")
    @Builder.Default
    private Boolean emailOpened = false;

    @Column(name = "link_clicked")
    @Builder.Default
    private Boolean linkClicked = false;

    @Column(name = "data_submitted")
    @Builder.Default
    private Boolean dataSubmitted = false;

    @Column(name = "reported_as_phishing")
    @Builder.Default
    private Boolean reportedAsPhishing = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", length = 50)
    private ActionTaken actionTaken;

    @Column(name = "time_to_click_minutes")
    private Integer timeToClickMinutes;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "tracking_token", unique = true, length = 100)
    private String trackingToken;

    @Column(name = "email_opened_at")
    private LocalDateTime emailOpenedAt;

    @Column(name = "link_clicked_at")
    private LocalDateTime linkClickedAt;

    @Column(name = "link_clicked_ip_address", length = 45)
    private String linkClickedIpAddress;

    @Column(name = "link_clicked_user_agent", columnDefinition = "TEXT")
    private String linkClickedUserAgent;

    @Column(name = "data_submitted_at")
    private LocalDateTime dataSubmittedAt;

    @Column(name = "data_submitted_ip_address", length = 45)
    private String dataSubmittedIpAddress;

    @Column(name = "remedial_training_required")
    @Builder.Default
    private Boolean remedialTrainingRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private com.waqiti.common.security.awareness.model.PhishingResult result;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum ActionTaken {
        NO_ACTION,
        OPENED_EMAIL,
        CLICKED_LINK,
        SUBMITTED_DATA,
        REPORTED_PHISHING
    }

    /**
     * Custom builder that accepts campaign and employee IDs
     */
    public static PhishingTestResultBuilder builder() {
        return internalBuilder();
    }

    public static class PhishingTestResultBuilder {
        public PhishingTestResultBuilder campaignId(UUID campaignId) {
            PhishingSimulationCampaign camp = new PhishingSimulationCampaign();
            camp.setId(campaignId);
            this.campaign = camp;
            return this;
        }

        public PhishingTestResultBuilder employeeId(UUID employeeId) {
            Employee emp = new Employee();
            emp.setId(employeeId);
            this.employee = emp;
            return this;
        }
    }

    /**
     * Get campaign ID from relationship
     */
    public UUID getCampaignId() {
        return campaign != null ? campaign.getId() : null;
    }

    /**
     * Get employee ID from relationship
     */
    public UUID getEmployeeId() {
        return employee != null ? employee.getId() : null;
    }

    /**
     * Determine if employee passed the test
     */
    public boolean isPassed() {
        return reportedAsPhishing ||
                (!linkClicked && !dataSubmitted);
    }

    /**
     * Determine if employee failed the test
     */
    public boolean isFailed() {
        return linkClicked || dataSubmitted;
    }

    /**
     * Get highest risk action taken
     */
    public ActionTaken determineHighestRiskAction() {
        if (reportedAsPhishing) {
            return ActionTaken.REPORTED_PHISHING;
        } else if (dataSubmitted) {
            return ActionTaken.SUBMITTED_DATA;
        } else if (linkClicked) {
            return ActionTaken.CLICKED_LINK;
        } else if (emailOpened) {
            return ActionTaken.OPENED_EMAIL;
        } else {
            return ActionTaken.NO_ACTION;
        }
    }
}