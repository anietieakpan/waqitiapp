package com.waqiti.common.gdpr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Data Breach Record Entity
 */
@Entity
@Table(name = "gdpr_data_breaches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class GDPRDataBreach {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "breach_date")
    private LocalDateTime breachDate;

    @Column(name = "discovery_date")
    private LocalDateTime discoveryDate;

    @Column(name = "affected_users", columnDefinition = "JSONB")
    private String affectedUsers;

    @Column(name = "data_categories", columnDefinition = "JSONB")
    private String dataCategories;

    @Column(name = "breach_description", columnDefinition = "TEXT")
    private String breachDescription;

    @Column(name = "mitigation_actions", columnDefinition = "TEXT")
    private String mitigationActions;

    @Column(name = "notified_supervisory_authority")
    private Boolean notifiedSupervisoryAuthority;

    @Column(name = "supervisory_authority_notification_date")
    private LocalDateTime supervisoryAuthorityNotificationDate;

    @Column(name = "notified_users")
    private Boolean notifiedUsers;

    @Column(name = "user_notification_date")
    private LocalDateTime userNotificationDate;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}