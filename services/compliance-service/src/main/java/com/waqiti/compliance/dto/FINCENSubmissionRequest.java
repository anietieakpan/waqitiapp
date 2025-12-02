package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FINCEN SAR Submission Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FINCENSubmissionRequest {
    private String submissionId;
    private String institutionId;
    private String formType;
    private String batchType;
    private String xmlContent;
    private String checksum;
    private LocalDateTime timestamp;
    private String sarId;
    private String priority;
}