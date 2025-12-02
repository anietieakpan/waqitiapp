package com.waqiti.compliance.contracts.dto.aml;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response after creating/updating an AML case
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLCaseResponse {

    /**
     * Case identifier
     */
    private String caseId;

    /**
     * Case number (for display/reporting)
     */
    private String caseNumber;

    /**
     * Case status
     */
    private AMLCaseStatus status;

    /**
     * Case type
     */
    private AMLCaseType caseType;

    /**
     * Priority
     */
    private AMLPriority priority;

    /**
     * Subject ID
     */
    private String subjectId;

    /**
     * Assigned to
     */
    private String assignedTo;

    /**
     * Risk score
     */
    private Double riskScore;

    /**
     * Created timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * Due date
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueDate;

    /**
     * Resolution timestamp (if closed)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resolvedAt;

    /**
     * Action taken (if resolved)
     */
    private String actionTaken;

    /**
     * Related case IDs
     */
    private List<String> relatedCaseIds;

    /**
     * Tags
     */
    private List<String> tags;

    /**
     * Success indicator
     */
    private Boolean success;

    /**
     * Message
     */
    private String message;

    /**
     * Error details (if failed)
     */
    private String errorDetails;
}
