package com.waqiti.reporting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class FinancialData {

    @Id
    private UUID dataId;

    @Column(nullable = false)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricCategory category;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal metricValue;

    @Column(length = 3)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String dataSource;

    @Column(nullable = false)
    private Boolean isAudited;

    @Column(nullable = false)
    private Boolean isEstimated;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String createdBy;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    // Aggregation fields for performance
    @Column(precision = 19, scale = 2)
    private BigDecimal dailyTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal weeklyTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal quarterlyTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal yearlyTotal;

    @PrePersist
    protected void onCreate() {
        if (dataId == null) {
            dataId = UUID.randomUUID();
        }
        if (isAudited == null) {
            isAudited = false;
        }
        if (isEstimated == null) {
            isEstimated = false;
        }
        if (currency == null) {
            currency = "USD";
        }
    }

    public enum DataType {
        REVENUE,
        EXPENSE,
        ASSET,
        LIABILITY,
        EQUITY,
        TRANSACTION_VOLUME,
        TRANSACTION_COUNT,
        CUSTOMER_METRIC,
        OPERATIONAL_METRIC,
        RISK_METRIC,
        COMPLIANCE_METRIC
    }

    public enum MetricCategory {
        // Revenue Categories
        TRANSACTION_FEES,
        INTERCHANGE_REVENUE,
        INTEREST_INCOME,
        SERVICE_CHARGES,
        INVESTMENT_INCOME,
        
        // Expense Categories
        OPERATING_EXPENSES,
        PERSONNEL_COSTS,
        TECHNOLOGY_COSTS,
        REGULATORY_COSTS,
        MARKETING_EXPENSES,
        
        // Asset Categories
        CASH_AND_EQUIVALENTS,
        CUSTOMER_DEPOSITS,
        LOANS_AND_ADVANCES,
        INVESTMENTS,
        FIXED_ASSETS,
        
        // Liability Categories
        CUSTOMER_LIABILITIES,
        BORROWINGS,
        ACCRUED_EXPENSES,
        REGULATORY_RESERVES,
        
        // Transaction Metrics
        PAYMENT_VOLUME,
        TRANSFER_VOLUME,
        CARD_TRANSACTIONS,
        INTERNATIONAL_TRANSFERS,
        
        // Customer Metrics
        ACTIVE_CUSTOMERS,
        NEW_CUSTOMERS,
        CUSTOMER_ACQUISITION_COST,
        CUSTOMER_LIFETIME_VALUE,
        
        // Operational Metrics
        TRANSACTION_SUCCESS_RATE,
        SYSTEM_UPTIME,
        API_RESPONSE_TIME,
        PROCESSING_TIME,
        
        // Risk Metrics
        FRAUD_RATE,
        CHARGEBACKS,
        CREDIT_LOSSES,
        OPERATIONAL_LOSSES,
        
        // Compliance Metrics
        KYC_COMPLETION_RATE,
        AML_ALERTS,
        REGULATORY_REPORTS,
        AUDIT_FINDINGS
    }
}