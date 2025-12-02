package com.waqiti.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a payment completes successfully
 * 
 * This event triggers downstream processing:
 * - Rewards calculation (rewards-service)
 * - Analytics updates (analytics-service)
 * - Ledger finalization (ledger-service)
 * - Customer notifications (notification-service)
 * - Reporting updates (reporting-service)
 * 
 * KAFKA TOPIC: payment-completed
 * PRODUCER: payment-service
 * CONSUMERS: rewards-service, analytics-service, ledger-service, notification-service, reporting-service
 * 
 * IMPORTANCE: This is a CRITICAL event for financial operations
 * All consumers must be implemented and monitored
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique payment identifier
     */
    private UUID paymentId;
    
    /**
     * User who made the payment
     */
    private UUID userId;
    
    /**
     * Transaction correlation ID for end-to-end tracking
     */
    private String correlationId;
    
    /**
     * Payment amount
     */
    private BigDecimal amount;
    
    /**
     * Currency code (ISO 4217)
     */
    private String currency;
    
    /**
     * Fee amount charged
     */
    private BigDecimal feeAmount;
    
    /**
     * Net amount after fees
     */
    private BigDecimal netAmount;
    
    /**
     * Payment method used (CARD, BANK_TRANSFER, WALLET, etc.)
     */
    private String paymentMethod;
    
    /**
     * Payment provider (STRIPE, PAYPAL, etc.)
     */
    private String paymentProvider;
    
    /**
     * Provider transaction ID
     */
    private String providerTransactionId;
    
    /**
     * Merchant ID (if applicable)
     */
    private UUID merchantId;
    
    /**
     * Merchant name
     */
    private String merchantName;
    
    /**
     * Merchant category code (MCC)
     */
    private String merchantCategory;
    
    /**
     * Recipient user ID (for P2P payments)
     */
    private UUID recipientId;
    
    /**
     * Payment description
     */
    private String description;
    
    /**
     * Payment initiated timestamp
     */
    private Instant initiatedAt;
    
    /**
     * Payment completed timestamp
     */
    private Instant completedAt;
    
    /**
     * Event published timestamp
     */
    @Builder.Default
    private Instant publishedAt = Instant.now();
    
    /**
     * Event source service
     */
    @Builder.Default
    private String eventSource = "payment-service";
    
    /**
     * Event version for schema evolution
     */
    @Builder.Default
    private String eventVersion = "1.0.0";
    
    /**
     * Additional metadata
     */
    private String metadata;
}