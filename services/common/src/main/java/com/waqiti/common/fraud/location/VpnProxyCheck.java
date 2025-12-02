package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * VPN/Proxy check result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VpnProxyCheck {
    private boolean isVpn;
    private boolean isProxy;
    private boolean isTor;
    private double confidence;
    private double riskScore;
    private String ipAddress;
}
