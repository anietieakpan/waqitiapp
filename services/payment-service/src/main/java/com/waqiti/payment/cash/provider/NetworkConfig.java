package com.waqiti.payment.cash.provider;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
public class NetworkConfig {
    private String networkId;
    private String networkType;
    private String endpointUrl;
    private String authType;
    private String apiKey;
    private String secretKey;
    private String clientCertificate;
    private BigDecimal maxTransactionAmount;
    private int processingTimeSeconds;
    private String feeStructure;
    private boolean isActive;
    private String acquiringBank;
    private boolean signatureRequired;
}
