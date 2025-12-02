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
 * Split payment request model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitPaymentRequest {
    
    @NotNull
    private String fromUserId;
    
    @NotNull
    private List<SplitRecipient> recipients;
    
    @NotNull
    @Positive
    private BigDecimal totalAmount;
    
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
                        "description", description != null ? description : "",
                        "recipients", recipients,
                        "splitType", "CUSTOM",
                        "currency", "USD"
                ))
                .build();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitRecipient {
        private String userId;
        private BigDecimal amount;
        private String description;
    }
}