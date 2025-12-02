package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Line Item Entity
 * Represents individual charges on a billing cycle
 */
@Entity
@Table(name = "line_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LineItem extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    // Pricing
    @Column(name = "quantity", precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "net_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal netAmount;

    // Period
    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    // Usage details
    @Column(name = "usage_type")
    private String usageType;

    @Column(name = "usage_count")
    private Integer usageCount;

    @Column(name = "usage_details", columnDefinition = "JSON")
    private String usageDetails;

    // Subscription details
    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "is_prorated")
    private Boolean isProrated = false;

    @Column(name = "proration_days")
    private Integer prorationDays;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ItemStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "base_currency_amount", precision = 19, scale = 4)
    private BigDecimal baseCurrencyAmount;

    // Metadata
    @Column(name = "category")
    private String category;

    @Column(name = "sub_category")
    private String subCategory;

    @Column(name = "tags")
    private String tags;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "external_reference")
    private String externalReference;

    /**
     * Line item types
     */
    public enum ItemType {
        SUBSCRIPTION,           // Recurring subscription charge
        USAGE,                 // Usage-based charge
        TRANSACTION_FEE,       // Per-transaction fee
        SERVICE_FEE,           // One-time service fee
        SETUP_FEE,             // Initial setup fee
        OVERAGE,               // Usage overage charge
        ADJUSTMENT,            // Manual adjustment
        CREDIT,                // Credit or refund
        DISCOUNT,              // Discount applied
        TAX,                   // Tax charge
        PENALTY,               // Late payment penalty
        INTEREST,              // Interest charge
        COMMISSION,            // Merchant commission
        CHARGEBACK,            // Chargeback fee
        OTHER                  // Other charges
    }

    /**
     * Line item status
     */
    public enum ItemStatus {
        PENDING,               // Not yet finalized
        CONFIRMED,             // Confirmed and included in invoice
        DISPUTED,              // Under dispute
        ADJUSTED,              // Adjusted after dispute
        CANCELLED,             // Cancelled
        REFUNDED              // Refunded
    }

    // Business methods
    
    public void calculateAmounts() {
        if (quantity != null && unitPrice != null) {
            grossAmount = quantity.multiply(unitPrice);
        }
        
        BigDecimal afterDiscount = grossAmount;
        if (discountAmount != null) {
            afterDiscount = grossAmount.subtract(discountAmount);
        } else if (discountPercentage != null) {
            discountAmount = grossAmount.multiply(discountPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            afterDiscount = grossAmount.subtract(discountAmount);
        }
        
        if (taxRate != null) {
            taxAmount = afterDiscount.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            netAmount = afterDiscount.add(taxAmount);
        } else {
            netAmount = afterDiscount;
        }
        
        // Calculate base currency amount if exchange rate is provided
        if (exchangeRate != null && netAmount != null) {
            baseCurrencyAmount = netAmount.multiply(exchangeRate);
        }
    }
    
    public boolean isCharge() {
        return itemType != ItemType.CREDIT && itemType != ItemType.DISCOUNT;
    }
    
    public boolean isCredit() {
        return itemType == ItemType.CREDIT || itemType == ItemType.DISCOUNT;
    }
}