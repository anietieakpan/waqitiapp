package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive wallet event for tracking all wallet-related operations.
 * Supports wallet lifecycle, balance changes, limits, and compliance tracking.
 * Integrates with transaction processing, fraud detection, and regulatory reporting.
 * 
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"eventId"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletEvent implements DomainEvent {
    
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    @Builder.Default
    private String eventType = "Wallet.Event";
    
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    @Builder.Default
    private String topic = "wallet-events";
    
    private String aggregateId; // Wallet ID
    
    @Builder.Default
    private String aggregateType = "Wallet";
    
    private Long version;
    
    private String correlationId;
    
    private String userId;
    
    @Builder.Default
    private String sourceService = System.getProperty("spring.application.name", "wallet-service");
    
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    // Wallet identification
    private String walletId;
    private String accountNumber;
    private String ownerId;
    private WalletType walletType;
    private String currency;
    private String walletName;
    
    // Event details
    private WalletEventType walletEventType;
    private String eventDescription;
    private String referenceNumber;
    
    // Balance information
    private BigDecimal previousBalance;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal ledgerBalance;
    private BigDecimal reservedAmount;
    private BigDecimal creditLimit;
    
    // Transaction details
    private String transactionId;
    private TransactionType transactionType;
    private BigDecimal transactionAmount;
    private String transactionCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal fee;
    private BigDecimal tax;
    
    // Party information
    private String counterpartyId;
    private String counterpartyWalletId;
    private String counterpartyName;
    private String counterpartyAccountNumber;
    private String counterpartyBankCode;
    
    // Status and state
    private WalletStatus previousStatus;
    private WalletStatus currentStatus;
    private String statusChangeReason;
    private boolean active;
    private boolean frozen;
    private boolean suspended;
    
    // Limits and restrictions
    private BigDecimal dailyTransactionLimit;
    private BigDecimal monthlyTransactionLimit;
    private BigDecimal singleTransactionLimit;
    private BigDecimal minimumBalance;
    private BigDecimal maximumBalance;
    private Integer dailyTransactionCount;
    private Integer monthlyTransactionCount;
    
    // Compliance and regulatory
    private String kycStatus;
    private String amlStatus;
    private boolean pep; // Politically Exposed Person
    private String riskLevel;
    private String complianceNotes;
    private boolean sanctionScreeningPassed;
    private String reportingCategory;
    
    // Security
    private String ipAddress;
    private String deviceId;
    private String sessionId;
    private String authenticationMethod;
    private boolean twoFactorAuthenticated;
    private String securityCheckResult;
    
    // Fraud detection
    private BigDecimal fraudScore;
    private String fraudCheckResult;
    private boolean fraudAlert;
    private String[] fraudIndicators;
    private String fraudMitigationAction;
    
    // Integration details
    private String externalReferenceId;
    private String paymentProcessor;
    private String bankAccountId;
    private String cardId;
    private String channelCode;
    
    // Notification
    private boolean notificationSent;
    private String notificationChannel;
    private Instant notificationTimestamp;
    
    // Audit trail
    private String initiatedBy;
    private String approvedBy;
    private Instant approvalTimestamp;
    private String rejectedBy;
    private Instant rejectionTimestamp;
    private String rejectionReason;
    
    // Error handling
    private String errorCode;
    private String errorMessage;
    private String errorDetails;
    private Integer retryAttempt;
    private Instant nextRetryTime;
    
    // Settlement
    private String settlementId;
    private Instant settlementDate;
    private String settlementStatus;
    private BigDecimal settlementAmount;
    
    // Rewards and loyalty
    private BigDecimal pointsEarned;
    private BigDecimal pointsRedeemed;
    private BigDecimal cashbackAmount;
    private String promotionCode;
    
    /**
     * Types of wallet events
     */
    public enum WalletEventType {
        // Lifecycle events
        CREATED("Wallet created"),
        ACTIVATED("Wallet activated"),
        DEACTIVATED("Wallet deactivated"),
        SUSPENDED("Wallet suspended"),
        CLOSED("Wallet closed"),
        REOPENED("Wallet reopened"),
        
        // Balance events
        CREDITED("Wallet credited"),
        DEBITED("Wallet debited"),
        BALANCE_ADJUSTED("Balance adjusted"),
        BALANCE_INQUIRY("Balance checked"),
        
        // Transaction events
        TRANSFER_INITIATED("Transfer initiated"),
        TRANSFER_COMPLETED("Transfer completed"),
        TRANSFER_FAILED("Transfer failed"),
        TRANSFER_REVERSED("Transfer reversed"),
        
        // Payment events
        PAYMENT_RECEIVED("Payment received"),
        PAYMENT_SENT("Payment sent"),
        PAYMENT_PENDING("Payment pending"),
        PAYMENT_CONFIRMED("Payment confirmed"),
        PAYMENT_REJECTED("Payment rejected"),
        
        // Limit events
        LIMIT_UPDATED("Transaction limit updated"),
        LIMIT_EXCEEDED("Transaction limit exceeded"),
        LIMIT_RESET("Transaction limit reset"),
        
        // Hold events
        FUNDS_HELD("Funds placed on hold"),
        FUNDS_RELEASED("Funds released from hold"),
        
        // Fee events
        FEE_CHARGED("Fee charged"),
        FEE_WAIVED("Fee waived"),
        FEE_REFUNDED("Fee refunded"),
        
        // Compliance events
        KYC_UPDATED("KYC status updated"),
        AML_ALERT("AML alert triggered"),
        COMPLIANCE_BLOCK("Compliance block applied"),
        COMPLIANCE_CLEAR("Compliance cleared"),
        
        // Security events
        ACCESS_GRANTED("Wallet access granted"),
        ACCESS_DENIED("Wallet access denied"),
        PIN_CHANGED("PIN changed"),
        SECURITY_ALERT("Security alert raised"),
        
        // Integration events
        BANK_LINKED("Bank account linked"),
        BANK_UNLINKED("Bank account unlinked"),
        CARD_LINKED("Card linked"),
        CARD_UNLINKED("Card unlinked");
        
        private final String description;
        
        WalletEventType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Types of wallets
     */
    public enum WalletType {
        PERSONAL("Personal wallet"),
        BUSINESS("Business wallet"),
        MERCHANT("Merchant wallet"),
        SAVINGS("Savings wallet"),
        INVESTMENT("Investment wallet"),
        ESCROW("Escrow wallet"),
        VIRTUAL("Virtual card wallet"),
        JOINT("Joint wallet"),
        MINOR("Minor's wallet"),
        SYSTEM("System wallet");
        
        private final String description;
        
        WalletType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Transaction types
     */
    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER,
        PAYMENT,
        REFUND,
        FEE,
        INTEREST,
        CASHBACK,
        ADJUSTMENT,
        REVERSAL
    }
    
    /**
     * Wallet status
     */
    public enum WalletStatus {
        PENDING_ACTIVATION,
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        FROZEN,
        CLOSED,
        BLOCKED,
        RESTRICTED,
        UNDER_REVIEW
    }
    
    /**
     * Factory method for wallet creation event
     */
    public static WalletEvent created(String walletId, String ownerId, WalletType type, String currency) {
        return WalletEvent.builder()
                .eventType("Wallet.Created")
                .walletEventType(WalletEventType.CREATED)
                .walletId(walletId)
                .aggregateId(walletId)
                .ownerId(ownerId)
                .userId(ownerId)
                .walletType(type)
                .currency(currency)
                .currentStatus(WalletStatus.PENDING_ACTIVATION)
                .currentBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for wallet credit event
     */
    public static WalletEvent credited(String walletId, BigDecimal amount, BigDecimal newBalance, String transactionId) {
        return WalletEvent.builder()
                .eventType("Wallet.Credited")
                .walletEventType(WalletEventType.CREDITED)
                .walletId(walletId)
                .aggregateId(walletId)
                .transactionId(transactionId)
                .transactionAmount(amount)
                .previousBalance(newBalance.subtract(amount))
                .currentBalance(newBalance)
                .transactionType(TransactionType.DEPOSIT)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for wallet debit event
     */
    public static WalletEvent debited(String walletId, BigDecimal amount, BigDecimal newBalance, String transactionId) {
        return WalletEvent.builder()
                .eventType("Wallet.Debited")
                .walletEventType(WalletEventType.DEBITED)
                .walletId(walletId)
                .aggregateId(walletId)
                .transactionId(transactionId)
                .transactionAmount(amount)
                .previousBalance(newBalance.add(amount))
                .currentBalance(newBalance)
                .transactionType(TransactionType.WITHDRAWAL)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for wallet transfer event
     */
    public static WalletEvent transferred(String fromWalletId, String toWalletId, BigDecimal amount, 
                                         String transactionId, BigDecimal newBalance) {
        return WalletEvent.builder()
                .eventType("Wallet.Transfer")
                .walletEventType(WalletEventType.TRANSFER_COMPLETED)
                .walletId(fromWalletId)
                .aggregateId(fromWalletId)
                .counterpartyWalletId(toWalletId)
                .transactionId(transactionId)
                .transactionAmount(amount)
                .currentBalance(newBalance)
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Check if this is a high-value transaction requiring additional checks
     */
    public boolean isHighValueTransaction() {
        return transactionAmount != null && 
               transactionAmount.compareTo(new BigDecimal("10000")) > 0;
    }
    
    /**
     * Check if this event represents a failed operation
     */
    public boolean isFailed() {
        return walletEventType == WalletEventType.TRANSFER_FAILED ||
               errorCode != null ||
               Boolean.TRUE.equals(fraudAlert);
    }
    
    /**
     * Check if compliance review is required
     */
    public boolean requiresComplianceReview() {
        return isHighValueTransaction() ||
               Boolean.TRUE.equals(pep) ||
               "HIGH".equals(riskLevel) ||
               (fraudScore != null && fraudScore.compareTo(new BigDecimal("0.7")) > 0);
    }
    
    /**
     * Get event severity for monitoring
     */
    public String getSeverity() {
        if (isFailed()) return "ERROR";
        if (requiresComplianceReview()) return "WARNING";
        if (isHighValueTransaction()) return "INFO";
        return "DEBUG";
    }
    
    /**
     * Calculate effective balance after reservations
     */
    public BigDecimal getEffectiveBalance() {
        if (currentBalance == null) return BigDecimal.ZERO;
        if (reservedAmount == null) return currentBalance;
        return currentBalance.subtract(reservedAmount);
    }
    
    @Override
    public boolean isValid() {
        return eventId != null &&
               walletId != null &&
               walletEventType != null &&
               timestamp != null;
    }
    
    @Override
    public Integer getPriority() {
        if (walletEventType == WalletEventType.SECURITY_ALERT) return 10;
        if (walletEventType == WalletEventType.AML_ALERT) return 9;
        if (Boolean.TRUE.equals(fraudAlert)) return 9;
        if (isHighValueTransaction()) return 7;
        if (walletEventType == WalletEventType.TRANSFER_FAILED) return 6;
        return 5;
    }
    
    @Override
    public boolean containsSensitiveData() {
        return true; // Wallet events contain financial data
    }
    
    @Override
    public Long getTtlSeconds() {
        // Financial events need long retention for compliance
        return 94608000L; // 3 years
    }
    
    @Override
    public String getAggregateName() {
        return "Wallet";
    }
}