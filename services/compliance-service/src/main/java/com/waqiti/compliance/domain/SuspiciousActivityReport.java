package com.waqiti.compliance.domain;

import com.waqiti.compliance.dto.ReportingInstitutionInfo;
import com.waqiti.compliance.dto.SARStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousActivityReport {

    private UUID sarId;
    private UUID customerId;
    private List<SuspiciousActivity> suspiciousActivities;
    private String narrativeDescription;
    private ReportingInstitutionInfo reportingInstitution;
    private LocalDateTime filingDate;
    private String reportedBy;
    private SARStatus status;
    private String submissionReference;
    private LocalDateTime submittedAt;

    public UUID getSarId() {
        return sarId;
    }

    public void setStatus(SARStatus status) {
        this.status = status;
    }

    public void setSubmissionReference(String submissionReference) {
        this.submissionReference = submissionReference;
    }
}
