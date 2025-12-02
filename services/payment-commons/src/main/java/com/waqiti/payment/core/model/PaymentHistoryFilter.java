package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment History Filter Model
 * 
 * Comprehensive filtering options for payment history queries
 * with support for date ranges, amounts, statuses, and metadata.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryFilter {
    
    // Date filtering
    private Instant startDate;
    private Instant endDate;
    
    // Amount filtering
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    
    // Status filtering
    private PaymentStatus status;
    private List<PaymentStatus> statuses;
    
    // Type filtering
    private PaymentType paymentType;
    private List<PaymentType> paymentTypes;
    
    // Provider filtering
    private ProviderType providerType;
    private List<ProviderType> providerTypes;
    
    // Currency filtering
    private String currency;
    private List<String> currencies;
    
    // User filtering
    private String senderId;
    private String recipientId;
    private List<String> userIds;
    
    // Merchant filtering
    private String merchantId;
    private List<String> merchantIds;
    
    // Reference filtering
    private String referenceId;
    private String transactionId;
    private String externalId;
    
    // Pagination
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int limit = 50;
    @Builder.Default
    private String sortField = "createdAt";
    @Builder.Default
    private SortDirection sortDirection = SortDirection.DESC;
    
    // Text search
    private String searchQuery;
    private List<String> searchFields;
    
    // Risk and compliance
    private Integer minRiskScore;
    private Integer maxRiskScore;
    private Boolean flaggedTransactions;
    private Boolean complianceReviewed;
    
    // Metadata filtering
    private String metadataKey;
    private String metadataValue;
    
    // Tags
    private List<String> tags;
    
    // Channels
    private String channel;
    private List<String> channels;
    
    // Geographical filtering
    private String country;
    private String region;
    private String city;
    
    // Device filtering
    private String deviceType;
    private String platform;
    
    // Special flags
    private Boolean recurringOnly;
    private Boolean refundedOnly;
    private Boolean disputedOnly;
    private Boolean failedOnly;
    private Boolean internationalOnly;
    
    // Amount ranges (predefined)
    private AmountRange amountRange;
    
    // Time ranges (predefined)
    private TimeRange timeRange;
    
    // Group filtering
    private String groupId;
    private Boolean groupPaymentsOnly;
    
    // Legacy field for backward compatibility
    private String searchTerm;
    
    // Enums
    public enum SortDirection {
        ASC, DESC
    }
    
    public enum AmountRange {
        MICRO(BigDecimal.ZERO, new BigDecimal("10")),
        SMALL(new BigDecimal("10"), new BigDecimal("100")),
        MEDIUM(new BigDecimal("100"), new BigDecimal("1000")),
        LARGE(new BigDecimal("1000"), new BigDecimal("10000")),
        ENTERPRISE(new BigDecimal("10000"), new BigDecimal("1000000"));
        
        private final BigDecimal min;
        private final BigDecimal max;
        
        AmountRange(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }
        
        public BigDecimal getMin() { return min; }
        public BigDecimal getMax() { return max; }
    }
    
    public enum TimeRange {
        TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS,
        LAST_90_DAYS, LAST_YEAR, THIS_MONTH, LAST_MONTH,
        THIS_QUARTER, LAST_QUARTER, CUSTOM
    }
    
    // Helper methods
    public boolean hasDateFilter() {
        return startDate != null || endDate != null || timeRange != null;
    }
    
    public boolean hasAmountFilter() {
        return minAmount != null || maxAmount != null || amountRange != null;
    }
    
    public boolean hasStatusFilter() {
        return status != null || (statuses != null && !statuses.isEmpty());
    }
    
    public boolean hasUserFilter() {
        return senderId != null || recipientId != null || 
               (userIds != null && !userIds.isEmpty());
    }
    
    public void applyAmountRange() {
        if (amountRange != null) {
            this.minAmount = amountRange.getMin();
            this.maxAmount = amountRange.getMax();
        }
    }
    
    public void applyTimeRange() {
        if (timeRange != null && timeRange != TimeRange.CUSTOM) {
            Instant now = Instant.now();
            switch (timeRange) {
                case TODAY:
                    this.startDate = now.minus(java.time.Duration.ofDays(1));
                    this.endDate = now;
                    break;
                case LAST_7_DAYS:
                    this.startDate = now.minus(java.time.Duration.ofDays(7));
                    this.endDate = now;
                    break;
                case LAST_30_DAYS:
                    this.startDate = now.minus(java.time.Duration.ofDays(30));
                    this.endDate = now;
                    break;
            }
        }
    }
    
    // Validation
    public void validate() {
        if (limit > 1000) {
            throw new IllegalArgumentException("Limit cannot exceed 1000");
        }
        
        if (page < 0) {
            throw new IllegalArgumentException("Page cannot be negative");
        }
        
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        
        if (minAmount != null && maxAmount != null && 
            minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("Min amount cannot be greater than max amount");
        }
    }
    
    // Legacy method for backward compatibility
    public boolean matches(PaymentResult payment) {
        if (status != null && !status.equals(payment.getStatus())) {
            return false;
        }
        
        if (paymentType != null && !paymentType.equals(payment.getPaymentType())) {
            return false;
        }
        
        if (providerType != null && !providerType.equals(payment.getProviderType())) {
            return false;
        }
        
        if (minAmount != null && payment.getAmount().compareTo(minAmount) < 0) {
            return false;
        }
        
        if (maxAmount != null && payment.getAmount().compareTo(maxAmount) > 0) {
            return false;
        }
        
        if (searchTerm != null && !searchTerm.isEmpty()) {
            String term = searchTerm.toLowerCase();
            return payment.getTransactionId() != null && 
                   payment.getTransactionId().toLowerCase().contains(term);
        }
        
        return true;
    }
}