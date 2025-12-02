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
 * GDPR Data Export Request Entity
 */
@Entity
@Table(name = "gdpr_export_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class GDPRDataExportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "request_date", nullable = false)
    private LocalDateTime requestDate;

    @Column(name = "request_reason", columnDefinition = "TEXT")
    private String requestReason;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private GDPRRequestStatus status;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @Column(name = "export_url", length = 500)
    private String exportUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}