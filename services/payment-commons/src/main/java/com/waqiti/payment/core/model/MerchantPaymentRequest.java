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
 * Merchant payment request model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantPaymentRequest {
    
    @NotNull
    private String fromUserId;
    
    @NotNull
    private String merchantId;
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotNull
    private String orderId;
    
    private String description;
    private ProviderType providerType;
    private Map<String, Object> metadata;
    
    public PaymentRequest toPaymentRequest(PaymentType type) {
        return PaymentRequest.builder()
                .paymentId(UUID.randomUUID())
                .type(type)
                .providerType(providerType != null ? providerType : ProviderType.INTERNAL)
                .fromUserId(fromUserId)
                .toUserId(merchantId)
                .amount(amount)
                .metadata(Map.of(
                        "merchantId", merchantId,
                        "orderId", orderId,
                        "description", description != null ? description : "",
                        "currency", "USD"
                ))
                .build();
    }
}