package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Device spoofing detection result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSpoofingResult {
    
    private String deviceId;
    private boolean isSpoofed;
    private List<String> spoofingIndicators;
    private double spoofingScore;
    private double confidence;
}