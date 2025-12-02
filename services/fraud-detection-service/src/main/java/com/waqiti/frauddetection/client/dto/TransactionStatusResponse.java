package com.waqiti.frauddetection.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Transaction Status Response DTO
 *
 * Current status of a transaction.
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusResponse {

    private String transactionId;

    /**
     * Status values: PENDING, PROCESSING, COMPLETED, BLOCKED, REVERSED, FAILED, FROZEN, UNKNOWN
     */
    private String status;

    private Boolean isBlocked;

    private String blockReason;

    private Boolean fallbackTriggered;

    private LocalDateTime timestamp;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
