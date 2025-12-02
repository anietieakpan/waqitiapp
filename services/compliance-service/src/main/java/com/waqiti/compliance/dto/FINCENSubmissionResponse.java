package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FINCEN SAR Submission Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FINCENSubmissionResponse {
    private String bsaId;
    private String submissionId;
    private String status;
    private String confirmationNumber;
    private LocalDateTime submittedAt;
    private LocalDateTime processedAt;
    private String message;
    private String warnings;
    private String errors;
    
    public String getBsaId() {
        return bsaId;
    }
    
    public String getSubmissionId() {
        return submissionId;
    }