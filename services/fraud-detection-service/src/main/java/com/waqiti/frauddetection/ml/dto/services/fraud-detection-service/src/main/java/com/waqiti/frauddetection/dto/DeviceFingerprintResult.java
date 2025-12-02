package com.waqiti.frauddetection.dto;
import lombok.*;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFingerprintResult {
    private String fingerprintId;
    private boolean isKnownDevice;
    private double trustScore;
    private Map<String, String> deviceAttributes;
}
