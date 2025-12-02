package com.waqiti.payment.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for recording group payment events in the ledger service
 * 
 * This request captures all essential group payment information needed for:
 * - Double-entry bookkeeping
 * - Account balance updates
 * - Transaction reconciliation
 * - Financial audit trails
 * - Compliance reporting
 * 
 * CRITICAL IMPORTANCE:
 * - Ensures accurate financial record keeping
 * - Maintains account balance integrity
 * - Supports regulatory compliance
 * - Enables accurate reporting and analytics
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordGroupPaymentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the group payment
     */
    @NotBlank(message = "Group payment ID is required")
    @Size(max = 100, message = "Group payment ID must not exceed 100 characters")
    private String groupPaymentId;

    /**
     * Type of event that triggered this ledger record
     * Examples: GROUP_PAYMENT_CREATED, GROUP_PAYMENT_UPDATED, GROUP_PAYMENT_SETTLED
     */
    @NotBlank(message = "Event type is required")
    @Size(max = 50, message = "Event type must not exceed 50 characters")
    private String eventType;

    /**
     * User ID of the group payment creator
     */
    @NotBlank(message = "Created by user ID is required")
    @Size(max = 50, message = "Created by user ID must not exceed 50 characters")
    private String createdBy;

    /**
     * Total amount of the group payment
     */
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
    private BigDecimal totalAmount;

    /**
     * Currency code (ISO 4217 format: USD, EUR, etc.)
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    private String currency;

    /**
     * Current status of the group payment
     * Examples: PENDING, PARTIALLY_PAID, FULLY_PAID, SETTLED, CANCELLED
     */
    @NotBlank(message = "Status is required")
    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    /**
     * List of participants with their payment details
     * Contains user IDs, amounts owed, amounts paid, and payment status
     */
    private List<Map<String, Object>> participants;

    /**
     * Unified transaction ID for cross-service correlation
     */
    @Size(max = 100, message = "Unified transaction ID must not exceed 100 characters")
    private String unifiedTransactionId;

    /**
     * Additional metadata for the ledger entry
     * May include split type, category, receipt information, etc.
     */
    private Map<String, Object> metadata;

    /**
     * Correlation ID for tracing requests across services
     */
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private String correlationId;

    /**
     * Timestamp when the event occurred
     */
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    /**
     * Idempotency key to prevent duplicate ledger entries
     */
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;

    /**
     * Source system that originated this ledger request
     */
    @Builder.Default
    private String sourceSystem = "payment-service";

    /**
     * Version of the ledger entry format for future compatibility
     */
    @Builder.Default
    private String version = "1.0";
}