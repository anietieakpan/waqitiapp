package com.waqiti.common.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PagerDuty Incident DTO
 *
 * Represents a PagerDuty incident for tracking and management.
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagerDutyIncident {

    /**
     * Incident ID assigned by PagerDuty
     */
    private String incidentId;

    /**
     * Deduplication key
     */
    private String dedupKey;

    /**
     * Incident title
     */
    private String title;

    /**
     * Incident description
     */
    private String description;

    /**
     * Incident status: triggered, acknowledged, resolved
     */
    private String status;

    /**
     * Severity: critical, high, medium, low
     */
    private String severity;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Acknowledged timestamp
     */
    private LocalDateTime acknowledgedAt;

    /**
     * Resolved timestamp
     */
    private LocalDateTime resolvedAt;

    /**
     * Assigned user
     */
    private String assignedTo;

    /**
     * Incident URL in PagerDuty
     */
    private String incidentUrl;
}
