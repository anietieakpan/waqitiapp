package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARScanRequest {
    
    @NotBlank(message = "Scan type is required")
    private String scanType; // QR_CODE, AR_MARKER, OBJECT
    
    @NotBlank(message = "Scan data is required")
    private String scanData;
    
    private Map<String, Double> scanPosition; // x, y, z coordinates
    private Double confidence;
    private Map<String, Object> scanMetadata;
    private String arMarkerId;
    private Map<String, Object> objectAttributes;
}