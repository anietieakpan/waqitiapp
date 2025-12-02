package com.waqiti.billingorchestrator.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Refund
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Refund response")
public class RefundResponse {

    @Schema(description = "Refund UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Original payment UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc")
    private UUID paymentId;

    @Schema(description = "Refund transaction ID", example = "re_1234567890abcdef")
    private String refundTransactionId;

    @Schema(description = "Refund status", example = "COMPLETED")
    private String status;

    @Schema(description = "Refund amount", example = "50.00")
    private BigDecimal amount;

    @Schema(description = "Currency", example = "USD")
    private String currency;

    @Schema(description = "Refund type", example = "PARTIAL")
    private String refundType;

    @Schema(description = "Reason for refund", example = "Customer requested refund due to service issue")
    private String reason;

    @Schema(description = "Initiated by", example = "admin@example.com")
    private String initiatedBy;

    @Schema(description = "Refund initiated timestamp", example = "2025-02-20T10:00:00")
    private LocalDateTime initiatedAt;

    @Schema(description = "Refund completed timestamp", example = "2025-02-20T10:05:00")
    private LocalDateTime completedAt;

    @Schema(description = "Expected processing days", example = "5")
    private Integer expectedProcessingDays;

    @Schema(description = "Processing fee refunded", example = "true")
    private Boolean processingFeeRefunded;

    @Schema(description = "Customer notified", example = "true")
    private Boolean customerNotified;

    @Schema(description = "Failure reason (if failed)", example = null)
    private String failureReason;
}
