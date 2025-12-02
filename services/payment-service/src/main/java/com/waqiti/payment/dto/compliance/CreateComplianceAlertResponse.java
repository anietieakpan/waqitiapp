/**
 * Create Compliance Alert Response DTO
 * Response for compliance alert creation operations
 */
package com.waqiti.payment.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateComplianceAlertResponse {
    
    /**
     * Whether the alert creation was successful
     */
    private Boolean success;
    
    /**
     * Generated compliance alert ID
     */
    private String complianceAlertId;
    
    /**
     * Original alert ID
     */
    private String originalAlertId;
    
    /**
     * User ID associated with the alert
     */
    private String userId;
    
    /**
     * Alert type created
     */
    private String alertType;
    
    /**
     * Assigned priority level
     */
    private String priority;
    
    /**
     * Current alert status
     */
    private String status;
    
    /**
     * Case number assigned if applicable
     */
    private String caseNumber;
    
    /**
     * Team assigned to handle the alert
     */
    private String assignedTeam;
    
    /**
     * Individual assignee if applicable
     */
    private String assignedTo;
    
    /**
     * Whether regulator notification was triggered
     */
    private Boolean regulatorNotificationTriggered;
    
    /**
     * Whether investigation was initiated
     */
    private Boolean investigationInitiated;
    
    /**
     * SLA deadline for response
     */
    private Instant slaDeadline;
    
    /**
     * Estimated processing time
     */
    private String estimatedProcessingTime;
    
    /**
     * Next actions required
     */
    private List<String> nextActions;
    
    /**
     * Related compliance alerts
     */
    private List<String> relatedAlerts;
    
    /**
     * Regulatory requirements triggered
     */
    private List<String> regulatoryRequirements;
    
    /**
     * When the alert was created
     */
    private Instant createdAt;
    
    /**
     * Alert reference number
     */
    private String referenceNumber;
    
    /**
     * Error message if creation failed
     */
    private String errorMessage;
    
    /**
     * Error code if applicable
     */
    private String errorCode;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}