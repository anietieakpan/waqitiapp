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
 * Request DTO for balance reservation operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceReservationRequest {

    @NotBlank
    private String customerId;
    
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    @NotBlank
    private String reservationReason;
    
    private Integer expirationTimeoutSeconds;
    
    private String transactionId;
    
    private String reference;
    
    private Map<String, String> metadata;
    
    private Instant requestedAt;
    
    @Builder.Default
    private boolean allowOverdraft = false;
    
    private String priority;
    
    /**
     * Creates a basic reservation request
     */
    public static BalanceReservationRequest basic(String customerId, BigDecimal amount, String currency, String reason) {
        return BalanceReservationRequest.builder()
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .reservationReason(reason)
                .requestedAt(Instant.now())
                .build();
    }
}