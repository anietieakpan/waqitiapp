package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "request_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestAuditLog {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private DataSubjectRequest request;
    
    @Column(name = "action", nullable = false)
    private String action;
    
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "performed_by", nullable = false)
    private String performedBy;
    
    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (performedAt == null) {
            performedAt = LocalDateTime.now();
        }
    }
}