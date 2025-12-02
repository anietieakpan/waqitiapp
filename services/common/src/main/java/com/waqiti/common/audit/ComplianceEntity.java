package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for compliance audit entries
 */
@Entity
@Table(name = "compliance_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "compliance_framework")
    private String complianceFramework;
    
    @Column(name = "regulation_rule")
    private String regulationRule;
    
    @Column(name = "event_type")
    private String eventType;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "level")
    @Enumerated(EnumType.STRING)
    private ComplianceAuditLog.ComplianceLevel level;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ComplianceAuditLog.ComplianceStatus status;
    
    @Column(name = "violation_details", columnDefinition = "TEXT")
    private String violationDetails;
    
    @Column(name = "remediation_action", columnDefinition = "TEXT")
    private String remediationAction;
    
    @Column(name = "approver_user_id")
    private String approverUserId;
    
    @Column(name = "document_reference")
    private String documentReference;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    
    @Column(name = "due_date")
    private LocalDateTime dueDate;
    
    @ElementCollection
    @CollectionTable(name = "compliance_metadata", joinColumns = @JoinColumn(name = "compliance_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}