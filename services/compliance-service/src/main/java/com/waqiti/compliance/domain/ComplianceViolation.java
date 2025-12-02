package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceViolation {

    private String violationType;
    private String details;
    private Severity severity;
    private LocalDateTime detectedAt;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public String getViolationType() {
        return violationType;
    }

    public String getDetails() {
        return details;
    }

    public Severity getSeverity() {
        return severity;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }
}
