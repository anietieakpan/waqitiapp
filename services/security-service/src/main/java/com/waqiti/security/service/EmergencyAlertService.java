package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyAlertService {
    
    public void createEmergencyAlert(String alertType, Object payload, String message) {
        log.error("EMERGENCY ALERT - Type: {} Message: {}", alertType, message);
    }
}