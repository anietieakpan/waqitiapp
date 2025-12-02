package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Transaction event model for event sourcing
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent extends FinancialEvent {

    private UUID transactionId;

    @Getter(AccessLevel.NONE)
    private UUID sourceWalletId;
    private UUID targetWalletId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String status;
    private String eventType;
    private Instant timestamp;
    private String description;
    private Map<String, Object> metadata;
    private UUID idempotencyKey;
    private String sourceSystem;
    private BigDecimal exchangeRate;
    private String targetCurrency;
    private BigDecimal fee;
    private String failureReason;
    private Integer retryCount;
    private UUID parentEventId;
    private String eventVersion;
    private String riskLevel;
    private String sagaId;

    // Override DomainEvent methods to convert UUID to String
    /**
     * Create transaction initiated event
     */
    public static TransactionEvent transactionInitiated(UUID transactionId, UUID userId, UUID sourceWalletId, UUID targetWalletId, BigDecimal amount, String currency, String transactionType) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .sourceWalletId(sourceWalletId)
            .targetWalletId(targetWalletId)
            .amount(amount)
            .currency(currency)
            .transactionType(transactionType)
            .status("INITIATED")
            .eventType("TRANSACTION_INITIATED")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create transaction completed event
     */
    public static TransactionEvent transactionCompleted(UUID transactionId, UUID userId, BigDecimal amount, String currency) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("COMPLETED")
            .eventType("TRANSACTION_COMPLETED")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create transaction failed event
     */
    public static TransactionEvent transactionFailed(UUID transactionId, UUID userId, BigDecimal amount, String currency, String failureReason) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("FAILED")
            .eventType("TRANSACTION_FAILED")
            .failureReason(failureReason)
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create transaction processing event
     */
    public static TransactionEvent transactionProcessing(UUID transactionId, UUID userId, BigDecimal amount, String currency) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("PROCESSING")
            .eventType("TRANSACTION_PROCESSING")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create transaction cancelled event
     */
    public static TransactionEvent transactionCancelled(UUID transactionId, UUID userId, BigDecimal amount, String currency, String reason) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("CANCELLED")
            .eventType("TRANSACTION_CANCELLED")
            .description(reason)
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create transfer event
     */
    public static TransactionEvent transferInitiated(UUID transactionId, UUID userId, UUID sourceWalletId, UUID targetWalletId, BigDecimal amount, String currency) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .sourceWalletId(sourceWalletId)
            .targetWalletId(targetWalletId)
            .amount(amount)
            .currency(currency)
            .transactionType("TRANSFER")
            .status("INITIATED")
            .eventType("TRANSFER_INITIATED")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create deposit event
     */
    public static TransactionEvent depositInitiated(UUID transactionId, UUID userId, UUID targetWalletId, BigDecimal amount, String currency) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .targetWalletId(targetWalletId)
            .amount(amount)
            .currency(currency)
            .transactionType("DEPOSIT")
            .status("INITIATED")
            .eventType("DEPOSIT_INITIATED")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create withdrawal event
     */
    public static TransactionEvent withdrawalInitiated(UUID transactionId, UUID userId, UUID sourceWalletId, BigDecimal amount, String currency) {
        return TransactionEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .sourceWalletId(sourceWalletId)
            .amount(amount)
            .currency(currency)
            .transactionType("WITHDRAWAL")
            .status("INITIATED")
            .eventType("WITHDRAWAL_INITIATED")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Check if transaction is in terminal state
     */
    public boolean isTerminalState() {
        return "COMPLETED".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELLED".equals(status);
    }
    
    /**
     * Check if transaction was successful
     */
    public boolean isSuccessful() {
        return "COMPLETED".equals(status);
    }
    
    /**
     * Check if transaction failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || "CANCELLED".equals(status);
    }
    
    /**
     * Check if transaction involves currency exchange
     */
    public boolean isCurrencyExchange() {
        return targetCurrency != null && !currency.equals(targetCurrency);
    }
    
    /**
     * Check if transaction has fees
     */
    public boolean hasFees() {
        return fee != null && fee.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
    
    /**
     * Check if transaction is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }
    
    /**
     * Get saga ID for distributed transaction tracking
     */
    public String getSagaId() {
        return sagaId;
    }
}