package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO for balance debit requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceDebitRequest {
    
    @NotBlank
    private String customerId;
    
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    @NotBlank
    private String reference;
    
    @NotBlank
    private String description;
    
    private String transactionId;
    private String reservationId;
    
    @Builder.Default
    private boolean allowOverdraft = false;
    
    private String source;
    private String debitType;
    private Map<String, String> metadata;
    
    private Instant processedAt;
    
    @Builder.Default
    private boolean sendNotification = true;
    
    private String notificationTemplate;
    
    /**
     * Creates a basic debit request
     */
    public static BalanceDebitRequest basic(String customerId, BigDecimal amount, String currency, 
                                          String reference, String description) {
        return BalanceDebitRequest.builder()
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .reference(reference)
                .description(description)
                .processedAt(Instant.now())
                .build();
    }
}