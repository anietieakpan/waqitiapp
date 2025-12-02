package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for sending fraud alert SMS messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertSmsRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotNull(message = "Transaction amount is required")
    private BigDecimal transactionAmount;
    
    @Builder.Default
    private String currency = "USD";
    
    @NotBlank(message = "Merchant name is required")
    private String merchantName;
    
    private String transactionLocation;
    
    @Builder.Default
    private LocalDateTime transactionTime = LocalDateTime.now();
    
    @Builder.Default
    private SmsPriority priority = SmsPriority.HIGH;
    
    @Builder.Default
    private SmsType type = SmsType.FRAUD_ALERT;
    
    /**
     * Fraud score (0.0 to 1.0)
     */
    private double fraudScore;
    
    /**
     * Risk factors identified
     */
    private String riskFactors;
    
    /**
     * Additional context for the alert
     */
    private Map<String, Object> context;
    
    /**
     * Custom message override (optional)
     */
    private String customMessage;
    
    /**
     * Whether this requires immediate action
     */
    @Builder.Default
    private boolean requiresImmediateAction = true;
    
    /**
     * Expiry time for the alert
     */
    private LocalDateTime expiryTime;
    
    /**
     * Language code for message localization
     */
    @Builder.Default
    private String languageCode = "en";
    
    /**
     * Timezone for displaying times
     */
    @Builder.Default
    private String timezone = "UTC";
    
    /**
     * Get amount (alias for transactionAmount)
     */
    public BigDecimal getAmount() {
        return transactionAmount;
    }
    
    /**
     * Get currency code
     */
    public String getCurrency() {
        return currency;
    }
}