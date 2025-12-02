package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for QR code generation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodeGenerationResult {
    
    private String qrCodeId;
    private String qrData;
    private byte[] qrCodeImage;
    private String qrCodeImageBase64;
    private Instant expiresAt;
    private String merchantName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String displayUrl;
    
    /**
     * Get QR code image as base64 string
     */
    public String getQrCodeImageBase64() {
        if (qrCodeImageBase64 == null && qrCodeImage != null) {
            qrCodeImageBase64 = java.util.Base64.getEncoder().encodeToString(qrCodeImage);
        }
        return qrCodeImageBase64;
    }
}