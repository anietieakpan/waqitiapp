package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for QR code generation requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodeGenerationRequest {
    
    @NotBlank
    private String merchantId;
    
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    private String description;
    private String terminalId;
    private String location;
    private String posReference;
    
    private Integer expiryMinutes;
    
    private Map<String, Object> metadata;
}