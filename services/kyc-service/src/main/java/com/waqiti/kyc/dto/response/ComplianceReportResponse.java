package com.waqiti.kyc.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for compliance report operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportResponse {
    
    private String reportId;
    
    private String reportType;
    
    private String status;
    
    private String format;
    
    private LocalDateTime generatedAt;
    
    private String downloadUrl;
    
    private Long fileSizeBytes;
    
    private Map<String, Object> data;
    
    private String errorMessage;
    
    private LocalDateTime expiresAt;
}