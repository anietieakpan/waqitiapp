package com.waqiti.frauddetection.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Transaction Block Response DTO
 *
 * Response from transaction service indicating block status.
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionBlockResponse {

    private String transactionId;

    private Boolean blocked;

    private String status;

    private String reason;

    private Boolean fallbackTriggered;

    private Boolean requiresManualReview;

    private String reviewQueueId;

    private String blockId;

    private LocalDateTime timestamp;

    private String errorMessage;
}
