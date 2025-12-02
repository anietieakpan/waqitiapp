package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "data_subject_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSubjectRequest {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "request_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestType requestType;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "deadline")
    private LocalDateTime deadline;
    
    @Column(name = "verification_token")
    private String verificationToken;
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @Column(name = "processed_by")
    private String processedBy;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "request_data_categories",
        joinColumns = @JoinColumn(name = "request_id")
    )
    @Column(name = "category")
    private List<String> dataCategories = new ArrayList<>();
    
    @Column(name = "export_format")
    @Enumerated(EnumType.STRING)
    private ExportFormat exportFormat;
    
    @Column(name = "export_url")
    private String exportUrl;
    
    @Column(name = "export_expires_at")
    private LocalDateTime exportExpiresAt;
    
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RequestAuditLog> auditLogs = new ArrayList<>();
    
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
    
    @Column(name = "notes", length = 2000)
    private String notes;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
        status = RequestStatus.PENDING_VERIFICATION;
        
        // Set deadline based on request type (GDPR requires 30 days)
        deadline = submittedAt.plusDays(30);
    }
    
    public void addAuditLog(RequestAuditLog log) {
        auditLogs.add(log);
        log.setRequest(this);
    }
    
    public boolean isOverdue() {
        return status != RequestStatus.COMPLETED && 
               status != RequestStatus.REJECTED &&
               LocalDateTime.now().isAfter(deadline);
    }
}

enum RequestType {
    ACCESS,           // Right to access personal data
    PORTABILITY,      // Right to data portability
    RECTIFICATION,    // Right to rectification
    ERASURE,          // Right to erasure (right to be forgotten)
    RESTRICTION,      // Right to restriction of processing
    OBJECTION        // Right to object to processing
}

enum RequestStatus {
    PENDING_VERIFICATION,
    VERIFIED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    EXPIRED
}

enum ExportFormat {
    JSON,
    CSV,
    PDF,
    EXCEL
}