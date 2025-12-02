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
 * Cryptocurrency payment request model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoPaymentRequest {
    
    @NotNull
    private String fromUserId;
    
    @NotNull
    private String toUserId;
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotNull
    private String cryptoCurrency; // BTC, ETH, USDC, etc.
    
    @NotNull
    private String walletAddress;
    
    @NotNull
    private String networkType; // mainnet, testnet, etc.
    
    private String description;
    private ProviderType providerType;
    private Map<String, Object> metadata;
    
    public PaymentRequest toPaymentRequest(PaymentType type) {
        return PaymentRequest.builder()
                .paymentId(UUID.randomUUID())
                .type(type)
                .providerType(providerType != null ? providerType : ProviderType.BITCOIN)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(amount)
                .metadata(Map.of(
                        "cryptoCurrency", cryptoCurrency,
                        "walletAddress", walletAddress,
                        "networkType", networkType,
                        "description", description != null ? description : "",
                        "currency", cryptoCurrency
                ))
                .build();
    }
}