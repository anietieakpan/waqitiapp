package com.waqiti.payment.dispute.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dispute Result
 */
@Data
@Builder
public class DisputeResult {
    private boolean success;
    private String disputeId;
    private DisputeStatus status;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime dueDate;
    private String errorCode;
    private String errorMessage;
}
