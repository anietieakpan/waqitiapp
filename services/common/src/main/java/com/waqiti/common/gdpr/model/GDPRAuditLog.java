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
 * GDPR Audit Log Entity
 *
 * @deprecated This shared GDPR module is deprecated. Use the dedicated GDPR Service instead.
 * @see com.waqiti.gdpr.service.GDPRComplianceService
 */
@Deprecated(since = "1.0-SNAPSHOT", forRemoval = true)
@Entity
@Table(name = "gdpr_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDPRAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action")
    @Enumerated(EnumType.STRING)
    private com.waqiti.common.gdpr.enums.GDPRAction action;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "ip_address")
    private String ipAddress;
}