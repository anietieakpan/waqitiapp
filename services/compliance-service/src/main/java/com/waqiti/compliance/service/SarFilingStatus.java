package com.waqiti.compliance.service;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SAR Filing Status
 * 
 * Represents the current status and metadata of a SAR filing.
 * Used for status tracking and reporting purposes.
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Data
@Builder
public class SarFilingStatus {
    
    private String sarId;
    private String status;
    private LocalDateTime submittedAt;
    private String confirmationId;
    private LocalDateTime detectedAt;
    private String priority;
    private boolean isOverdue;
    private long daysUntilDeadline;
    private String regulatoryBody;
    private boolean expeditedFiling;
}