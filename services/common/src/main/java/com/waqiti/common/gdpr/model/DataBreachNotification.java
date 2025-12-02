package com.waqiti.common.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GDPR Data Breach Notification Model
 *
 * GDPR Articles 33-34: Breach notification requirements
 * - To supervisory authority within 72 hours
 * - To data subjects if high risk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataBreachNotification {

    private UUID breachId;
    private String breachType; // UNAUTHORIZED_ACCESS, DATA_LOSS, RANSOMWARE, etc.
    private LocalDateTime detectedAt;
    private LocalDateTime reportedAt;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private List<UUID> affectedUserIds;
    private Integer affectedUserCount;
    private List<String> affectedDataCategories;
    private String description;
    private String impactAssessment;
    private String mitigationMeasures;
    private Boolean reportedToAuthority;
    private LocalDateTime authorityReportedAt;
    private String authorityReference;
    private Boolean notifiedToUsers;
    private LocalDateTime usersNotifiedAt;
    private String contactPerson;
    private String status; // DETECTED, INVESTIGATING, CONTAINED, REPORTED, RESOLVED
}
