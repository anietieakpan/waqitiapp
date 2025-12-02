package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sar_filings")
public class SARFiling {
    @Id
    private String id;
    private String eventId;
    private String referenceNumber;
    
    // Subject information
    private String subjectUserId;
    private String subjectType;
    private String subjectName;
    private String subjectSsn;
    private String subjectAddress;
    
    // Suspicious activity details
    private String activityType;
    private String suspiciousActivityDescription;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime activityStartDate;
    private LocalDateTime activityEndDate;
    private List<String> relatedTransactionIds;
    
    // Filing details
    private String filingType;
    private SARStatus status;
    private String filingReason;
    private String narrativeDescription;
    private List<String> supportingDocuments;
    
    // FINCEN details
    private String finCENId;
    private String bsaId;
    private LocalDateTime filedWithFINCEN;
    private String filingMethod;
    private String acknowledgmentNumber;
    
    // Investigation
    private String investigatorId;
    private LocalDateTime investigationStarted;
    private LocalDateTime investigationCompleted;
    private String investigationNotes;
    
    // Review and approval
    private String reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private boolean approved;
    private String approvalReason;
    private String rejectionReason;
    
    // Compliance officer
    private String complianceOfficerId;
    private LocalDateTime complianceReviewedAt;
    private boolean complianceApproved;
    
    // Follow-up
    private boolean followUpRequired;
    private LocalDateTime followUpDate;
    private String followUpNotes;
    private boolean lawEnforcementNotified;
    private LocalDateTime lawEnforcementNotificationDate;
    
    // Risk assessment
    private String riskLevel;
    private Integer riskScore;
    private List<String> riskFactors;
    
    // Metadata
    private String correlationId;
    private LocalDateTime detectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long processingTimeMs;
}

enum SARStatus {
    DETECTED,
    UNDER_REVIEW,
    INVESTIGATING,
    PENDING_APPROVAL,
    APPROVED,
    FILED,
    ACKNOWLEDGED,
    CLOSED,
    REJECTED,
    CANCELLED
}