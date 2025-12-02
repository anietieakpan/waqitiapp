package com.waqiti.transaction.entity;

import com.waqiti.transaction.enums.ReceiptAuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Receipt audit log entity for database persistence
 */
@Entity
@Table(name = "receipt_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptAuditLogEntity {

    @Id
    private UUID id;

    @Version
    @Column(name = "opt_lock_version", nullable = false)
    private Long optLockVersion;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "receipt_id")
    private UUID receiptId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ReceiptAuditAction action;

    @Column(name = "action_description", length = 500)
    private String actionDescription;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "security_score")
    private Integer securityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "compliance_category")
    private String complianceCategory;

    @Column(name = "flagged_for_review", nullable = false)
    @Builder.Default
    private boolean flaggedForReview = false;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Column(name = "timestamp", nullable = false)
    @CreationTimestamp
    private LocalDateTime timestamp;

    // Indexes for performance
    @Table.Index(name = "idx_audit_transaction_id", columnList = "transaction_id")
    @Table.Index(name = "idx_audit_user_id", columnList = "user_id")
    @Table.Index(name = "idx_audit_timestamp", columnList = "timestamp")
    @Table.Index(name = "idx_audit_action", columnList = "action")
    @Table.Index(name = "idx_audit_flagged", columnList = "flagged_for_review")
    @Table.Index(name = "idx_audit_risk_level", columnList = "risk_level")
    public static class Indexes {}
}