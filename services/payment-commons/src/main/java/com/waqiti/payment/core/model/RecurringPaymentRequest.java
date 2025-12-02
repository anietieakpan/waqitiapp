package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Recurring payment request model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPaymentRequest {
    
    @NotNull
    private String fromUserId;
    
    @NotNull
    private String toUserId;
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotNull
    private RecurrenceFrequency frequency;
    
    @NotNull
    private LocalDateTime startDate;
    
    private LocalDateTime endDate;
    private Integer maxExecutions;
    private String description;
    private ProviderType providerType;
    private Map<String, Object> metadata;
    
    public PaymentRequest toPaymentRequest(PaymentType type) {
        return PaymentRequest.builder()
                .paymentId(UUID.randomUUID())
                .type(type)
                .providerType(providerType != null ? providerType : ProviderType.INTERNAL)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(amount)
                .metadata(Map.of(
                        "frequency", frequency.toString(),
                        "startDate", startDate.toString(),
                        "endDate", endDate != null ? endDate.toString() : "",
                        "maxExecutions", maxExecutions != null ? maxExecutions.toString() : "0",
                        "description", description != null ? description : "",
                        "currency", "USD"
                ))
                .build();
    }
    
    public enum RecurrenceFrequency {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    }
}