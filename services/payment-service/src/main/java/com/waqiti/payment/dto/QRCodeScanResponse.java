package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for QR code scan responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodeScanResponse {
    
    private String qrCodeId;
    private String merchantName;
    private String merchantLogo;
    private BigDecimal amount;
    private String currency;
    private String description;
    private boolean requiresConfirmation;
    private boolean requiresPin;
    private boolean requiresBiometric;
    private BigDecimal fraudScore;
    private String riskLevel;
    private String status;
    private String message;
    private Map<String, Object> additionalData;
}