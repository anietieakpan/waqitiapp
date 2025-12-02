package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Peer-to-Peer payment request model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class P2PPaymentRequest {
    
    @NotNull
    private String fromUserId;
    
    @NotNull
    private String toUserId;
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
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
                        "description", description != null ? description : "",
                        "currency", "USD"
                ))
                .build();
    }
}