package com.waqiti.kyc.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request DTO for generating compliance reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportRequest {
    
    @NotBlank(message = "Organization ID is required")
    private String organizationId;
    
    @NotBlank(message = "Report type is required")
    private String reportType;
    
    @NotBlank(message = "Requested by is required")
    private String requestedBy;
    
    @NotBlank(message = "Format is required")
    private String format = "PDF";
    
    @NotNull(message = "Parameters are required")
    private Map<String, String> parameters;
    
    private boolean recurring = false;
    
    private String schedule;
    
    private String callbackUrl;
    
    private boolean includeRawData = false;
    
    private String language = "en";
}