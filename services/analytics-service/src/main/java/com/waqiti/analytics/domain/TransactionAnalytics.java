package com.waqiti.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transaction_analytics", indexes = {
        @Index(name = "idx_transaction_analytics_user_timestamp", columnList = "user_id, timestamp"),
        @Index(name = "idx_transaction_analytics_category", columnList = "category"),
        @Index(name = "idx_transaction_analytics_merchant", columnList = "merchant_id"),
        @Index(name = "idx_transaction_analytics_amount", columnList = "amount"),
        @Index(name = "idx_transaction_analytics_transaction_type", columnList = "transaction_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionAnalytics {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "transaction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    
    @Column(name = "category", nullable = false, length = 50)
    private String category;
    
    @Column(name = "subcategory", length = 50)
    private String subcategory;
    
    @Column(name = "merchant_id", length = 100)
    private String merchantId;
    
    @Column(name = "merchant_name", length = 200)
    private String merchantName;
    
    @Column(name = "merchant_category_code", length = 10)
    private String merchantCategoryCode;
    
    @Column(name = "location_lat", precision = 10, scale = 8)
    private BigDecimal locationLatitude;
    
    @Column(name = "location_lng", precision = 11, scale = 8)
    private BigDecimal locationLongitude;
    
    @Column(name = "location_city", length = 100)
    private String locationCity;
    
    @Column(name = "location_state", length = 50)
    private String locationState;
    
    @Column(name = "location_country", length = 50)
    private String locationCountry;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "day_of_week")
    private Integer dayOfWeek; // 1-7, Monday=1
    
    @Column(name = "hour_of_day")
    private Integer hourOfDay; // 0-23
    
    @Column(name = "is_weekend")
    private Boolean isWeekend;
    
    @Column(name = "is_holiday")
    private Boolean isHoliday;
    
    @Column(name = "payment_method", length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    
    @Column(name = "channel", length = 20)
    @Enumerated(EnumType.STRING)
    private TransactionChannel channel;
    
    @Column(name = "is_recurring")
    private Boolean isRecurring = false;
    
    @Column(name = "recurring_pattern", length = 20)
    private String recurringPattern; // DAILY, WEEKLY, MONTHLY, etc.
    
    @Column(name = "is_split_transaction")
    private Boolean isSplitTransaction = false;
    
    @Column(name = "original_transaction_id")
    private UUID originalTransactionId; // For split transactions
    
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // Comma-separated tags
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "reference", length = 100)
    private String reference;
    
    @Column(name = "balance_before", precision = 19, scale = 2)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;
    
    @Column(name = "fees", precision = 19, scale = 2)
    private BigDecimal fees = BigDecimal.ZERO;
    
    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;
    
    @Column(name = "original_amount", precision = 19, scale = 2)
    private BigDecimal originalAmount; // Before currency conversion
    
    @Column(name = "original_currency", length = 3)
    private String originalCurrency;
    
    // Risk and fraud indicators
    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;
    
    @Column(name = "anomaly_score", precision = 5, scale = 2)
    private BigDecimal anomalyScore;
    
    @Column(name = "is_flagged")
    private Boolean isFlagged = false;
    
    @Column(name = "fraud_indicators", columnDefinition = "TEXT")
    private String fraudIndicators; // JSON array of indicators
    
    // User behavior context
    @Column(name = "device_type", length = 20)
    private String deviceType;
    
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    // Social context
    @Column(name = "counterparty_user_id")
    private UUID counterpartyUserId; // For P2P transactions
    
    @Column(name = "social_context", length = 50)
    private String socialContext; // FRIEND, FAMILY, BUSINESS, etc.
    
    @Column(name = "group_transaction_id")
    private UUID groupTransactionId; // For group payments/splits
    
    // Analytics metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enrichment_data", columnDefinition = "jsonb")
    private Map<String, Object> enrichmentData; // Additional ML features
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "categorization_confidence", columnDefinition = "jsonb")
    private Map<String, Object> categorizationConfidence;
    
    @Column(name = "processed_for_analytics")
    private Boolean processedForAnalytics = false;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set derived fields
        if (timestamp != null) {
            dayOfWeek = timestamp.getDayOfWeek().getValue();
            hourOfDay = timestamp.getHour();
            isWeekend = dayOfWeek >= 6; // Saturday=6, Sunday=7
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean isIncome() {
        return transactionType == TransactionType.CREDIT || 
               transactionType == TransactionType.DEPOSIT ||
               transactionType == TransactionType.SALARY ||
               transactionType == TransactionType.REFUND;
    }
    
    public boolean isExpense() {
        return transactionType == TransactionType.DEBIT ||
               transactionType == TransactionType.PAYMENT ||
               transactionType == TransactionType.WITHDRAWAL ||
               transactionType == TransactionType.PURCHASE;
    }
    
    public boolean isTransfer() {
        return transactionType == TransactionType.TRANSFER ||
               transactionType == TransactionType.P2P_SENT ||
               transactionType == TransactionType.P2P_RECEIVED;
    }
    
    public boolean isHighValue() {
        // Consider transactions above $500 as high value (configurable)
        return amount.compareTo(BigDecimal.valueOf(500)) > 0;
    }
    
    public boolean isOffHours() {
        return hourOfDay != null && (hourOfDay < 6 || hourOfDay > 22);
    }
    
    public boolean isInternational() {
        return originalCurrency != null && !originalCurrency.equals(currency);
    }
    
    public boolean isContactless() {
        return paymentMethod == PaymentMethod.NFC ||
               paymentMethod == PaymentMethod.MOBILE_WALLET ||
               paymentMethod == PaymentMethod.QR_CODE;
    }
    
    public boolean isCashEquivalent() {
        return category != null && (
                category.toLowerCase().contains("cash") ||
                category.toLowerCase().contains("atm") ||
                category.toLowerCase().contains("withdrawal")
        );
    }
    
    public boolean isSubscriptionLike() {
        return isRecurring && (
                category != null && (
                        category.toLowerCase().contains("subscription") ||
                        category.toLowerCase().contains("streaming") ||
                        category.toLowerCase().contains("membership")
                )
        );
    }
    
    public boolean isEssentialCategory() {
        if (category == null) return false;
        String cat = category.toLowerCase();
        return cat.contains("grocery") || cat.contains("gas") || cat.contains("utilities") ||
               cat.contains("healthcare") || cat.contains("insurance") || cat.contains("rent") ||
               cat.contains("mortgage");
    }
    
    public boolean isDiscretionaryCategory() {
        if (category == null) return false;
        String cat = category.toLowerCase();
        return cat.contains("entertainment") || cat.contains("dining") || cat.contains("shopping") ||
               cat.contains("travel") || cat.contains("hobby") || cat.contains("luxury");
    }
    
    public String getTimeOfDayCategory() {
        if (hourOfDay == null) return "UNKNOWN";
        if (hourOfDay >= 5 && hourOfDay < 12) return "MORNING";
        if (hourOfDay >= 12 && hourOfDay < 17) return "AFTERNOON";
        if (hourOfDay >= 17 && hourOfDay < 21) return "EVENING";
        return "NIGHT";
    }
    
    public String getAmountCategory() {
        if (amount.compareTo(BigDecimal.valueOf(10)) <= 0) return "MICRO";
        if (amount.compareTo(BigDecimal.valueOf(50)) <= 0) return "SMALL";
        if (amount.compareTo(BigDecimal.valueOf(200)) <= 0) return "MEDIUM";
        if (amount.compareTo(BigDecimal.valueOf(1000)) <= 0) return "LARGE";
        return "VERY_LARGE";
    }
    
    // Enums
    public enum TransactionType {
        DEBIT, CREDIT, TRANSFER, PAYMENT, DEPOSIT, WITHDRAWAL, 
        P2P_SENT, P2P_RECEIVED, PURCHASE, REFUND, FEE, 
        SALARY, INVESTMENT, CRYPTO_BUY, CRYPTO_SELL, BILL_PAY,
        SUBSCRIPTION, LOAN_PAYMENT, INSURANCE, TAX_PAYMENT
    }
    
    public enum PaymentMethod {
        BANK_TRANSFER, CREDIT_CARD, DEBIT_CARD, DIGITAL_WALLET,
        MOBILE_WALLET, NFC, QR_CODE, CASH, CHECK, WIRE_TRANSFER,
        ACH, CRYPTOCURRENCY, PAYPAL, APPLE_PAY, GOOGLE_PAY,
        SAMSUNG_PAY, ZELLE, VENMO, CASHAPP
    }
    
    public enum TransactionChannel {
        MOBILE_APP, WEB, ATM, BRANCH, PHONE, SMS, EMAIL,
        MERCHANT_POS, ONLINE_MERCHANT, SUBSCRIPTION_SERVICE,
        GOVERNMENT, PEER_TO_PEER, MARKETPLACE
    }
}