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
 * JPA entity for security audit log entries
 */
@Entity
@Table(name = "security_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "event_type")
    private String eventType;
    
    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private SecurityAuditLog.SecurityEventCategory category;
    
    @Column(name = "severity")
    @Enumerated(EnumType.STRING)
    private SecurityAuditLog.SecuritySeverity severity;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "resource")
    private String resource;
    
    @Column(name = "action")
    private String action;
    
    @Column(name = "outcome")
    @Enumerated(EnumType.STRING)
    private SecurityAuditLog.SecurityOutcome outcome;
    
    @Column(name = "threat_level")
    private String threatLevel;
    
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    
    @Column(name = "source_service")
    private String sourceService;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "detection_method")
    private String detectionMethod;
    
    @Column(name = "mitigation_action")
    private String mitigationAction;
    
    @Column(name = "requires_alert")
    private Boolean requiresAlert;
    
    @ElementCollection
    @CollectionTable(name = "security_audit_metadata", joinColumns = @JoinColumn(name = "security_audit_id"))
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