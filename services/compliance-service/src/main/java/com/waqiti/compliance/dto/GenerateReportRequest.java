package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateReportRequest {
    
    @NotNull
    private String reportType; // SAR, CTR, OFAC, KYC_SUMMARY, AML_QUARTERLY
    
    @NotNull
    private LocalDate startDate;
    
    @NotNull
    private LocalDate endDate;
    
    private String reportPeriod; // DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUAL
    private String format; // PDF, EXCEL, CSV, XML
    private Boolean includeDetails;
    private String recipientEmail;
}