package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Usage Record Entity
 *
 * Tracks consumption-based billing metrics for metered services.
 *
 * CRITICAL BUSINESS USE CASES:
 * - API call metering (per-request billing)
 * - Storage usage (GB-hours)
 * - Bandwidth consumption (GB transferred)
 * - Transaction volume (per-transaction fees)
 * - Compute hours (serverless/container billing)
 *
 * BILLING MODELS SUPPORTED:
 * - Pay-as-you-go (usage * unit price)
 * - Tiered pricing (volume discounts)
 * - Committed usage discounts
 * - Overage charges
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Entity
@Table(name = "usage_records", indexes = {
    @Index(name = "idx_usage_account", columnList = "account_id"),
    @Index(name = "idx_usage_subscription", columnList = "subscription_id"),
    @Index(name = "idx_usage_timestamp", columnList = "usage_timestamp DESC"),
    @Index(name = "idx_usage_metric", columnList = "metric_name, usage_timestamp"),
    @Index(name = "idx_usage_billing_period", columnList = "billing_period_start, billing_period_end"),
    @Index(name = "idx_usage_idempotency", columnList = "idempotency_key", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UsageRecord extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "billing_cycle_id")
    private UUID billingCycleId;

    // Metric details
    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;  // e.g., "api_calls", "storage_gb_hours", "bandwidth_gb"

    @Column(name = "metric_category", length = 50)
    @Enumerated(EnumType.STRING)
    private MetricCategory metricCategory;

    @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", length = 50)
    private String unit;  // e.g., "requests", "GB", "hours"

    // Pricing
    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;  // quantity * unitPrice

    @Column(name = "currency", length = 3)
    private String currency;

    // Timing
    @Column(name = "usage_timestamp", nullable = false)
    private LocalDateTime usageTimestamp;

    @Column(name = "billing_period_start")
    private LocalDateTime billingPeriodStart;

    @Column(name = "billing_period_end")
    private LocalDateTime billingPeriodEnd;

    // Status tracking
    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private UsageStatus status;

    @Column(name = "billed")
    private Boolean billed;

    @Column(name = "billed_at")
    private LocalDateTime billedAt;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    // Idempotency
    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;

    // Metadata
    @Column(name = "source", length = 100)
    private String source;  // e.g., "api-gateway", "storage-service"

    @Column(name = "resource_id", length = 255)
    private String resourceId;  // e.g., API endpoint, storage bucket

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;  // JSON metadata for filtering/grouping

    @Version
    private Long version;

    public enum MetricCategory {
        API_USAGE,          // API calls, requests
        STORAGE,            // GB-hours, object count
        BANDWIDTH,          // Data transfer
        COMPUTE,            // CPU/GPU hours
        TRANSACTIONS,       // Payment processing
        MESSAGING,          // SMS, email, push notifications
        DATABASE,           // Query count, storage
        CUSTOM              // Custom metrics
    }

    public enum UsageStatus {
        RECORDED,           // Usage captured
        VALIDATED,          // Passed validation
        AGGREGATED,         // Rolled up for billing
        BILLED,             // Included in invoice
        DISPUTED            // Customer disputed usage
    }

    /**
     * Calculates total amount (quantity * unitPrice)
     */
    public void calculateTotalAmount() {
        if (quantity != null && unitPrice != null) {
            totalAmount = quantity.multiply(unitPrice);
        }
    }

    /**
     * Marks usage as billed
     */
    public void markAsBilled(UUID invoiceId) {
        this.billed = true;
        this.billedAt = LocalDateTime.now();
        this.invoiceId = invoiceId;
        this.status = UsageStatus.BILLED;
    }
}
