package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARScanResponse {
    
    private String experienceId;
    private String scanResult;
    private String paymentType;
    private UUID recipientId;
    private String recipientName;
    private BigDecimal amount;
    private String currency;
    private String merchantName;
    private Map<String, Object> productInfo;
    private Map<String, Object> visualizationData;
    private String nextAction;
    private boolean requiresConfirmation;
    private String message;
    private boolean success;
    
    public static ARScanResponse error(String message) {
        return ARScanResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}