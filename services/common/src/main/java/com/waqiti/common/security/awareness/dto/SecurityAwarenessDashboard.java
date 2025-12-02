package com.waqiti.common.security.awareness.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import com.waqiti.common.security.awareness.validation.ValidQuarter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAwarenessDashboard {
    private PCIComplianceReport pciComplianceReport;
    private Integer overdueEmployeeCount;
    private Integer highRiskEmployeeCount;
    private List<PhishingCampaignReport> recentCampaigns;
    private List<AssessmentStatistics> quarterlyAssessments;
}