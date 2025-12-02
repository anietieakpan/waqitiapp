package com.waqiti.payment.core.integration;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-specific payment result
 * Raw result from payment provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"rawResponse"})
public class ProviderPaymentResult {
    
    private UUID resultId;
    private UUID requestId;
    private String provider;
    private ProviderStatus status;
    private String providerTransactionId;
    private String providerReferenceId;
    private BigDecimal processedAmount;
    private String currency;
    private BigDecimal providerFee;
    private LocalDateTime processedAt;
    private String authorizationCode;
    private String responseCode;
    private String responseMessage;
    private Map<String, Object> providerMetadata;
    private Map<String, Object> rawResponse;
    private ProviderError error;
    private LocalDateTime responseTimestamp;
    private Long responseTimeMs;
    
    public enum ProviderStatus {
        SUCCESS,
        PENDING,
        PROCESSING,
        FAILED,
        DECLINED,
        CANCELLED,
        TIMEOUT,
        ERROR,
        UNKNOWN
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderError {
        private String errorCode;
        private String errorMessage;
        private String errorCategory;
        private boolean retryable;
        private Map<String, Object> errorDetails;
    }
    
    public boolean isSuccessful() {
        return status == ProviderStatus.SUCCESS;
    }
    
    public boolean isPending() {
        return status == ProviderStatus.PENDING || status == ProviderStatus.PROCESSING;
    }
    
    public boolean hasError() {
        return error != null || status == ProviderStatus.FAILED || 
               status == ProviderStatus.ERROR || status == ProviderStatus.DECLINED;
    }
}