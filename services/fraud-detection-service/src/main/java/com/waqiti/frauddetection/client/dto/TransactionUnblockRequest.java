package com.waqiti.frauddetection.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * Transaction Unblock Request DTO
 *
 * Used to unblock transactions that were false positives.
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionUnblockRequest {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "Unblock reason is required")
    private String unblockReason;

    private String reviewedBy;

    private String reviewNotes;

    private Boolean falsePositive;
}
