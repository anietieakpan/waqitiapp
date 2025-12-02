package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRequest {
    private String transactionId;
    private String userId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String sourceIpAddress;
    private String deviceId;
    private String deviceFingerprint;
    private String userAgent;
    private String geolocation;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    private String merchantId;
    private String beneficiaryId;
    private Boolean knownDevice;
    private Boolean trustedLocation;
    private Integer failedAttempts;
    private String paymentMethod;
    private Map<String, Object> transactionContext;
}