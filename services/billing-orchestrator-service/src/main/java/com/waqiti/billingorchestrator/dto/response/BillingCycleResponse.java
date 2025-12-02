package com.waqiti.billingorchestrator.dto.response;

import com.waqiti.billingorchestrator.entity.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for Billing Cycle
 *
 * Comprehensive billing cycle information for API consumers
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Billing cycle response with complete details")
public class BillingCycleResponse {

    @Schema(description = "Billing cycle UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Customer UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc")
    private UUID customerId;

    @Schema(description = "Account UUID", example = "456e7890-e89b-12d3-a456-426614174222")
    private UUID accountId;

    @Schema(description = "Customer type", example = "BUSINESS")
    private BillingCycle.CustomerType customerType;

    @Schema(description = "Cycle start date", example = "2025-01-01")
    private LocalDate cycleStartDate;

    @Schema(description = "Cycle end date", example = "2025-01-31")
    private LocalDate cycleEndDate;

    @Schema(description = "Current status", example = "INVOICED")
    private BillingCycle.CycleStatus status;

    @Schema(description = "Billing frequency", example = "MONTHLY")
    private BillingCycle.BillingFrequency billingFrequency;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    // Financial amounts
    @Schema(description = "Subscription charges", example = "99.99")
    private BigDecimal subscriptionCharges;

    @Schema(description = "Usage charges", example = "50.00")
    private BigDecimal usageCharges;

    @Schema(description = "Transaction fees", example = "2.50")
    private BigDecimal transactionFees;

    @Schema(description = "Adjustments (can be negative)", example = "-5.00")
    private BigDecimal adjustments;

    @Schema(description = "Credits applied", example = "10.00")
    private BigDecimal credits;

    @Schema(description = "Tax amount", example = "14.25")
    private BigDecimal taxAmount;

    @Schema(description = "Total amount", example = "151.74")
    private BigDecimal totalAmount;

    @Schema(description = "Amount paid", example = "100.00")
    private BigDecimal paidAmount;

    @Schema(description = "Balance due", example = "51.74")
    private BigDecimal balanceDue;

    // Dates
    @Schema(description = "Invoice date", example = "2025-02-01")
    private LocalDate invoiceDate;

    @Schema(description = "Payment due date", example = "2025-02-15")
    private LocalDate dueDate;

    @Schema(description = "Grace period end date", example = "2025-02-20")
    private LocalDate gracePeriodEndDate;

    @Schema(description = "Cycle closed timestamp", example = "2025-02-01T00:00:00")
    private LocalDateTime closedAt;

    // Invoice information
    @Schema(description = "Invoice UUID", example = "789e0123-e89b-12d3-a456-426614174444")
    private UUID invoiceId;

    @Schema(description = "Invoice number", example = "INV-2025-0001")
    private String invoiceNumber;

    @Schema(description = "Invoice generated flag", example = "true")
    private Boolean invoiceGenerated;

    @Schema(description = "Invoice sent flag", example = "true")
    private Boolean invoiceSent;

    @Schema(description = "Invoice sent timestamp", example = "2025-02-01T10:30:00")
    private LocalDateTime invoiceSentAt;

    // Payment information
    @Schema(description = "Auto-pay enabled", example = "true")
    private Boolean autoPayEnabled;

    @Schema(description = "Payment method UUID", example = "321e9876-e89b-12d3-a456-426614174666")
    private UUID paymentMethodId;

    @Schema(description = "Number of payment attempts", example = "1")
    private Integer paymentAttempts;

    @Schema(description = "Last payment attempt timestamp", example = "2025-02-15T14:30:00")
    private LocalDateTime lastPaymentAttemptAt;

    @Schema(description = "Payment completed timestamp", example = "2025-02-15T14:31:00")
    private LocalDateTime paidAt;

    // Dunning
    @Schema(description = "Current dunning level (0 = none)", example = "0")
    private Integer dunningLevel;

    @Schema(description = "Last dunning action timestamp", example = "2025-02-20T09:00:00")
    private LocalDateTime lastDunningActionAt;

    // Metadata
    @Schema(description = "Additional notes", example = "Q1 2025 billing cycle")
    private String notes;

    @Schema(description = "Custom metadata", example = "{\"source\": \"api\", \"campaign\": \"Q1_2025\"}")
    private Map<String, String> metadata;

    // Audit fields
    @Schema(description = "Created timestamp", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last updated timestamp", example = "2025-02-15T14:31:00")
    private LocalDateTime updatedAt;

    @Schema(description = "Optimistic lock version", example = "5")
    private Long version;

    // Related entities (optional - can be loaded on demand)
    @Schema(description = "Line items (optional)")
    private List<LineItemResponse> lineItems;

    @Schema(description = "Payment history (optional)")
    private List<PaymentSummary> payments;

    @Schema(description = "Recent events (optional)")
    private List<BillingEventSummary> recentEvents;

    // Calculated/derived fields
    @Schema(description = "Is past due", example = "false")
    private Boolean isPastDue;

    @Schema(description = "Is in grace period", example = "false")
    private Boolean isInGracePeriod;

    @Schema(description = "Days overdue", example = "0")
    private Integer daysOverdue;

    @Schema(description = "Payment URL", example = "https://billing.example.com/pay/inv_123")
    private String paymentUrl;

    /**
     * Nested DTO for line item summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemResponse {
        private UUID id;
        private String description;
        private BigDecimal amount;
        private String itemType;
    }

    /**
     * Nested DTO for payment summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        private UUID id;
        private BigDecimal amount;
        private String status;
        private LocalDateTime paymentDate;
        private String paymentMethod;
    }

    /**
     * Nested DTO for event summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingEventSummary {
        private UUID id;
        private String eventType;
        private String description;
        private LocalDateTime eventTimestamp;
    }
}
