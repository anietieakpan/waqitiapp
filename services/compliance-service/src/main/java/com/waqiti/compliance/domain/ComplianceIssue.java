package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceIssue {

    private UUID issueId;
    private String issueType;
    private String description;
    private Severity severity;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getIssueType() {
        return issueType;
    }
}
