package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO for QR code payment responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodePaymentResponse {
    
    private String transactionId;
    private String qrCodeId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String merchantName;
    private Instant completedAt;
    private String reference;
    private String receiptUrl;
    private Map<String, Object> metadata;
}