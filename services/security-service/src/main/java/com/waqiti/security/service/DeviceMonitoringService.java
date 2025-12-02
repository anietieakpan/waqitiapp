package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMonitoringService {
    
    public void updateDeviceMetrics(String deviceId, Object result) {
        log.debug("Updating device metrics for deviceId: {}", deviceId);
    }
}