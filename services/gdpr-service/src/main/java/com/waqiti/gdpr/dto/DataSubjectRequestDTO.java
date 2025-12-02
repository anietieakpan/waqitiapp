package com.waqiti.gdpr.dto;

import com.waqiti.gdpr.domain.ExportFormat;
import com.waqiti.gdpr.domain.RequestStatus;
import com.waqiti.gdpr.domain.RequestType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DataSubjectRequestDTO {
    private String id;
    private String userId;
    private RequestType requestType;
    private RequestStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deadline;
    private List<String> dataCategories;
    private ExportFormat exportFormat;
    private String exportUrl;
    private LocalDateTime exportExpiresAt;
    private String rejectionReason;
    private String notes;
    private boolean isOverdue;
    private List<AuditLogDTO> auditLogs;
}