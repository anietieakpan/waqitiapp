package com.waqiti.payment.core.integration;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-specific payment request
 * Transformed request ready for provider processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"providerCredentials", "securityData"})
public class ProviderPaymentRequest {
    
    private UUID requestId;
    private String provider;
    private String providerEndpoint;
    private ProviderRequestType requestType;
    private BigDecimal amount;
    private String currency;
    private String senderAccount;
    private String recipientAccount;
    private String description;
    private Map<String, Object> providerSpecificFields;
    private Map<String, String> providerCredentials;
    private Map<String, Object> securityData;
    private String idempotencyKey;
    private LocalDateTime requestTimestamp;
    private Integer timeoutSeconds;
    private boolean testMode;
    private Map<String, Object> metadata;
    
    public enum ProviderRequestType {
        PAYMENT,
        TRANSFER,
        AUTHORIZATION,
        CAPTURE,
        REFUND,
        VOID,
        QUERY,
        SETTLEMENT
    }
}