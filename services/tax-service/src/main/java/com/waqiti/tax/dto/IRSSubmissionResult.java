package com.waqiti.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IRSSubmissionResult {
    private boolean success;
    private String confirmationNumber;
    private String message;
    private LocalDate estimatedRefundDate;
    private LocalDateTime submittedAt;
    private String errorCode;
    private String errorDetails;
}
