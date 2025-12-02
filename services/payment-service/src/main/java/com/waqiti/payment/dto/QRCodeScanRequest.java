package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * DTO for QR code scan requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodeScanRequest {
    
    @NotBlank
    private String qrCodeId;
    
    @NotBlank
    private String qrData;
    
    @NotBlank
    private String userId;
    
    private String deviceId;
    private String deviceFingerprint;
    private String ipAddress;
    private String location;
    private String userAgent;
    
    private Map<String, Object> metadata;
}