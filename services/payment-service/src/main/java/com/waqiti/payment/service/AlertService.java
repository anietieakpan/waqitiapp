package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing alerts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    public void suggestAutoReloadSetup(String userId, String accountId, String correlationId) {
        log.info("Suggesting auto-reload setup: userId={}, accountId={}, correlationId={}",
            userId, accountId, correlationId);
    }

    public void triggerFraudInvestigation(String accountId, double changeAmount, String correlationId) {
        log.error("Triggering fraud investigation: accountId={}, changeAmount=${}, correlationId={}",
            accountId, changeAmount, correlationId);
    }
}
