package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Refund Response DTO
 *
 * Response returned after initiating a refund request.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    private String refundId;
    private boolean success;
    private String refundStatus; // INITIATED, PROCESSING, COMPLETED, FAILED
    private String message;
    private String failureReason;
    private LocalDateTime estimatedCompletionDate;
    private String trackingNumber;
}
