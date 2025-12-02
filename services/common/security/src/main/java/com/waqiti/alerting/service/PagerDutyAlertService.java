package com.waqiti.alerting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagerDutyAlertService {
    
    public void sendCriticalAlert(String title, String description) {
        log.error("CRITICAL ALERT: {} - {}", title, description);
        // In production: integrate with PagerDuty API
    }
    
    public void sendAlert(String severity, String message) {
        log.warn("ALERT[{}]: {}", severity, message);
    }
}
