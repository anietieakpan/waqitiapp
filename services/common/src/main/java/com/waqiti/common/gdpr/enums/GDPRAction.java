package com.waqiti.common.gdpr.enums;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Actions and Rights
 *
 * Represents the actions data subjects can take under GDPR
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-19
 */
@Getter
public enum GDPRAction {
    /**
     * Article 15 - Right to Access
     * Data subject can request copy of their data
     */
    ACCESS_REQUEST("Right to Access", 15, 30),

    /**
     * Article 16 - Right to Rectification
     * Data subject can request correction of inaccurate data
     */
    RECTIFICATION("Right to Rectification", 16, 30),

    /**
     * Article 17 - Right to Erasure (Right to be Forgotten)
     * Data subject can request deletion of their data
     */
    ERASURE("Right to Erasure", 17, 30),

    /**
     * Article 18 - Right to Restriction of Processing
     * Data subject can request processing limitation
     */
    RESTRICTION("Right to Restriction", 18, 30),

    /**
     * Article 20 - Right to Data Portability
     * Data subject can request data in machine-readable format
     */
    DATA_PORTABILITY("Right to Data Portability", 20, 30),

    /**
     * Article 21 - Right to Object
     * Data subject can object to processing
     */
    OBJECTION("Right to Object", 21, 30),

    /**
     * Article 7(3) - Right to Withdraw Consent
     * Data subject can withdraw previously given consent
     */
    CONSENT_WITHDRAWAL("Withdraw Consent", 7, 1),

    /**
     * Article 77 - Right to Lodge Complaint
     * Data subject can complain to supervisory authority
     */
    COMPLAINT("Lodge Complaint", 77, 0),

    /**
     * Data Export Requested
     * Internal action tracking for data export requests
     */
    DATA_EXPORT_REQUESTED("Data Export Requested", 20, 30),

    /**
     * Data Export Completed
     */
    DATA_EXPORT_COMPLETED("Data Export Completed", 20, 0),

    /**
     * Data Export Failed
     */
    DATA_EXPORT_FAILED("Data Export Failed", 20, 0),

    /**
     * Data Deletion Requested
     */
    DATA_DELETION_REQUESTED("Data Deletion Requested", 17, 30),

    /**
     * Data Deletion Completed
     */
    DATA_DELETION_COMPLETED("Data Deletion Completed", 17, 0),

    /**
     * Data Deletion Failed
     */
    DATA_DELETION_FAILED("Data Deletion Failed", 17, 0),

    /**
     * Data Rectified
     */
    DATA_RECTIFIED("Data Rectified", 16, 0),

    /**
     * Processing Restricted
     */
    PROCESSING_RESTRICTED("Processing Restricted", 18, 0),

    /**
     * Data Portability Export
     */
    DATA_PORTABILITY_EXPORT("Data Portability Export", 20, 0),

    /**
     * Consent Granted
     */
    CONSENT_GRANTED("Consent Granted", 7, 0),

    /**
     * Consent Revoked
     */
    CONSENT_REVOKED("Consent Revoked", 7, 0),

    /**
     * Breach Notification Sent
     */
    BREACH_NOTIFICATION_SENT("Breach Notification Sent", 33, 0);

    private final String description;
    private final int articleNumber;
    private final int responseDays; // Days to respond per GDPR

    GDPRAction(String description, int articleNumber, int responseDays) {
        this.description = description;
        this.articleNumber = articleNumber;
        this.responseDays = responseDays;
    }

    public String getArticleReference() {
        return "GDPR Article " + articleNumber;
    }
}
