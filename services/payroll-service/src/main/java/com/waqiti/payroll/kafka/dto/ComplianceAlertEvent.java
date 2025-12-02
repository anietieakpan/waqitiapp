package com.waqiti.payroll.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Kafka Event: Compliance Violation Alert
 * Topic: compliance-alert-events
 * Producer: ComplianceService
 * Consumer: Compliance Dashboard, Alert Service, Legal Team Notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAlertEvent {

    // Event metadata
    private String eventId;
    private String eventType; // "COMPLIANCE_VIOLATION", "COMPLIANCE_WARNING"

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    private String correlationId;

    // Company information
    private String companyId;
    private String companyName;
    private String payrollBatchId;

    // Violation details
    private String violationType; // MINIMUM_WAGE, OVERTIME, PAY_EQUITY, AML, CHILD_LABOR, etc.
    private String severity; // INFO, WARNING, HIGH, CRITICAL
    private String regulation; // "FLSA Section 6", "Equal Pay Act", etc.

    private String violationDescription;
    private String employeeId; // If applicable
    private String employeeName; // If applicable

    // Violation specifics
    private List<ViolationDetail> violations;

    // Action required
    private boolean actionRequired;
    private String suggestedAction;
    private String dueDate;

    // Notification targets
    private List<String> notifyEmails;
    private List<String> notifyUserIds;

    // Additional metadata
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViolationDetail {
        private String field;
        private String expectedValue;
        private String actualValue;
        private String reason;
    }
}
