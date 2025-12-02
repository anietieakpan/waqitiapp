package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * DTO for QR code payment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodePaymentRequest {
    
    @NotBlank
    private String qrCodeId;
    
    @NotBlank
    private String userId;
    
    private String pin;
    private String biometricData;
    private String deviceId;
    private String sessionId;
    
    private Map<String, Object> metadata;
}