package com.waqiti.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Audit Alert Repository Service - acts as a service wrapper for alert operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditAlertRepository {
    
    public void saveAlert(Object alert) {
        log.debug("Saving audit alert through service wrapper");
        // Service wrapper for alert repository operations
    }
}