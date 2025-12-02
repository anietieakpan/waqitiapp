package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DeviceInfo {
    private String deviceId;
    private boolean trusted;
    private BigDecimal riskScore;
}
