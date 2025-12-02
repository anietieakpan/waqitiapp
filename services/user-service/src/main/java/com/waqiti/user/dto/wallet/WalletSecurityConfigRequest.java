package com.waqiti.user.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSecurityConfigRequest {
    private boolean enableMfa;
    private boolean enableBiometric;
    private boolean enableGeofencing;
    private List<String> allowedCountries;
    private BigDecimal mfaThreshold;
    private Duration sessionTimeout;
}