package com.waqiti.frauddetection.integration;

import org.springframework.stereotype.Service;

@Service
public class VpnDetectionClient {
    public boolean isVpn(String ipAddress) {
        return false;
    }
}
