package com.waqiti.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event published when a payment is successfully completed.
 * This event is consumed by various services for downstream processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    
    /**
     * Unique identifier for the transaction
     */
    private UUID transactionId;
    
    /**
     * Type of payment (TRANSFER, DEPOSIT, WITHDRAWAL, PAYMENT, FEE)
     */
    private String paymentType;
    
    /**
     * Payment amount
     */
    private BigDecimal amount;
    
    /**
     * Currency code (e.g., USD, EUR, GBP)
     */
    private String currency;
    
    /**
     * Source user ID (payer)
     */
    private UUID fromUserId;
    
    /**
     * Source wallet ID
     */
    private UUID fromWalletId;
    
    /**
     * Destination user ID (payee)
     */
    private UUID toUserId;
    
    /**
     * Destination wallet ID
     */
    private UUID toWalletId;
    
    /**
     * Merchant ID for merchant payments
     */
    private UUID merchantId;
    
    /**
     * Payment method used
     */
    private UUID paymentMethodId;
    
    /**
     * Transaction fee amount
     */
    private BigDecimal feeAmount;
    
    /**
     * Fee currency (if different from transaction currency)
     */
    private String feeCurrency;
    
    /**
     * Payment description
     */
    private String description;
    
    /**
     * Payment reference number
     */
    private String referenceNumber;
    
    /**
     * External transaction ID from payment processor
     */
    private String externalTransactionId;
    
    /**
     * Payment processor name (e.g., STRIPE, PAYPAL, SQUARE)
     */
    private String paymentProcessor;
    
    /**
     * Transaction status
     */
    private String status;
    
    /**
     * Timestamp when the payment was completed
     */
    private LocalDateTime timestamp;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Correlation ID for tracing
     */
    private String correlationId;
    
    /**
     * Event version for compatibility
     */
    @Builder.Default
    private String eventVersion = "1.0";
}