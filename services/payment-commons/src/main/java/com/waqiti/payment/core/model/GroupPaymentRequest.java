package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Group payment request model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentRequest {
    
    @NotNull
    private String fromUserId;
    
    @NotNull
    private List<String> participantIds;
    
    @NotNull
    @Positive
    private BigDecimal totalAmount;
    
    @NotNull
    private SplitType splitType;
    
    private String title;
    private String description;
    private ProviderType providerType;
    private Map<String, Object> metadata;
    
    public PaymentRequest toPaymentRequest(PaymentType type) {
        return PaymentRequest.builder()
                .paymentId(UUID.randomUUID())
                .type(type)
                .providerType(providerType != null ? providerType : ProviderType.INTERNAL)
                .fromUserId(fromUserId)
                .amount(totalAmount)
                .metadata(Map.of(
                        "title", title != null ? title : "",
                        "description", description != null ? description : "",
                        "participants", participantIds,
                        "splitType", splitType.toString(),
                        "participantCount", participantIds.size(),
                        "currency", "USD"
                ))
                .build();
    }
    
    public enum SplitType {
        EQUAL, WEIGHTED, CUSTOM
    }
}