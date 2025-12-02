/**
 * Transaction Risk Assessment Request DTO
 * Used for requesting risk assessment of transactions
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRiskAssessmentRequest {
    
    /**
     * Transaction ID to assess
     */
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    /**
     * User ID performing the transaction
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Recipient user ID if applicable
     */
    private String recipientId;
    
    /**
     * Transaction amount
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    /**
     * Transaction currency
     */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    /**
     * Transaction type (P2P, MERCHANT, WITHDRAWAL, etc.)
     */
    @NotBlank(message = "Transaction type is required")
    private String transactionType;
    
    /**
     * Payment method used
     */
    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
    
    /**
     * Payment method ID
     */
    private String paymentMethodId;
    
    /**
     * Device information
     */
    private Map<String, Object> deviceInfo;
    
    /**
     * Geographic information (IP, location)
     */
    private Map<String, Object> geographicInfo;
    
    /**
     * Behavioral patterns
     */
    private Map<String, Object> behavioralPatterns;
    
    /**
     * Transaction context (time of day, frequency, etc.)
     */
    private Map<String, Object> transactionContext;
    
    /**
     * Merchant information if applicable
     */
    private Map<String, Object> merchantInfo;
    
    /**
     * Session information
     */
    private Map<String, Object> sessionInfo;
    
    /**
     * Previous transaction history relevant to assessment
     */
    private Map<String, Object> transactionHistory;
    
    /**
     * When the transaction was initiated
     */
    private Instant transactionTime;
    
    /**
     * Request timestamp
     */
    private Instant requestTime;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Priority level for assessment (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priority;
    
    /**
     * Additional metadata for assessment
     */
    private Map<String, Object> metadata;
}