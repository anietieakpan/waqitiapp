package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an external biller (utility company, service provider)
 * This is the entity that issues bills to be paid
 */
@Entity
@Table(name = "billers", indexes = {
        @Index(name = "idx_biller_name", columnList = "name"),
        @Index(name = "idx_biller_category", columnList = "category"),
        @Index(name = "idx_biller_status", columnList = "status"),
        @Index(name = "idx_biller_external_id", columnList = "external_biller_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Biller {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private BillCategory category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "customer_service_phone", length = 20)
    private String customerServicePhone;

    @Column(name = "customer_service_email", length = 100)
    private String customerServiceEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillerStatus status;

    @Column(name = "external_biller_id", unique = true, length = 100)
    private String externalBillerId;

    @Column(name = "supports_auto_pay", nullable = false)
    private Boolean supportsAutoPay = false;

    @Column(name = "supports_direct_payment", nullable = false)
    private Boolean supportsDirectPayment = false;

    @Column(name = "supports_bill_import", nullable = false)
    private Boolean supportsBillImport = false;

    @Column(name = "supports_ebill", nullable = false)
    private Boolean supportsEbill = false;

    @Column(name = "average_processing_time_hours")
    private Integer averageProcessingTimeHours;

    @Column(name = "processing_cutoff_time", length = 10)
    private String processingCutoffTime;

    @Column(name = "payment_fee_percentage", precision = 5, scale = 4)
    private java.math.BigDecimal paymentFeePercentage;

    @Column(name = "payment_fee_fixed", precision = 19, scale = 4)
    private java.math.BigDecimal paymentFeeFixed;

    @Column(name = "minimum_payment_amount", precision = 19, scale = 4)
    private java.math.BigDecimal minimumPaymentAmount;

    @Column(name = "maximum_payment_amount", precision = 19, scale = 4)
    private java.math.BigDecimal maximumPaymentAmount;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "state_code", length = 10)
    private String stateCode;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Business logic methods

    public boolean isActive() {
        return status == BillerStatus.ACTIVE;
    }

    public boolean supportsFeature(String feature) {
        return switch (feature.toLowerCase()) {
            case "auto_pay" -> supportsAutoPay;
            case "direct_payment" -> supportsDirectPayment;
            case "bill_import" -> supportsBillImport;
            case "ebill" -> supportsEbill;
            default -> false;
        };
    }

    public java.math.BigDecimal calculatePaymentFee(java.math.BigDecimal amount) {
        java.math.BigDecimal fee = java.math.BigDecimal.ZERO;

        if (paymentFeePercentage != null) {
            fee = fee.add(amount.multiply(paymentFeePercentage));
        }

        if (paymentFeeFixed != null) {
            fee = fee.add(paymentFeeFixed);
        }

        return fee;
    }

    @PrePersist
    protected void onCreate() {
        if (supportsAutoPay == null) {
            supportsAutoPay = false;
        }
        if (supportsDirectPayment == null) {
            supportsDirectPayment = false;
        }
        if (supportsBillImport == null) {
            supportsBillImport = false;
        }
        if (supportsEbill == null) {
            supportsEbill = false;
        }
        if (countryCode == null) {
            countryCode = "USA";
        }
    }
}
