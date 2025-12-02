package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for dispute information from dispute-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {

    /**
     * Dispute identifier
     */
    @NotBlank(message = "Dispute ID is required")
    private String disputeId;

    /**
     * Customer identifier
     */
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    /**
     * Account identifier
     */
    @NotBlank(message = "Account ID is required")
    private String accountId;

    /**
     * Transaction identifier (if applicable)
     */
    private String transactionId;

    /**
     * Type of dispute (UNAUTHORIZED, FRAUD, ERROR, etc.)
     */
    @NotBlank(message = "Dispute type is required")
    private String disputeType;

    /**
     * Current status (OPEN, INVESTIGATING, RESOLVED, CLOSED, etc.)
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Disputed amount
     */
    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    /**
     * Currency code
     */
    @NotBlank(message = "Currency is required")
    private String currency;

    /**
     * Reason for dispute
     */
    @NotBlank(message = "Reason is required")
    private String reason;

    /**
     * Detailed description
     */
    private String description;

    /**
     * Resolution outcome (if resolved)
     */
    private String resolution;

    /**
     * Whether dispute was resolved in customer's favor
     */
    private Boolean resolvedInFavor;

    /**
     * Dispute creation timestamp
     */
    @NotNull(message = "Created date is required")
    private LocalDateTime createdAt;

    /**
     * Expected resolution date
     */
    private LocalDateTime expectedResolutionDate;

    /**
     * Actual resolution date
     */
    private LocalDateTime resolvedAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Assigned case handler
     */
    private String assignedTo;
}
