package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    public void logEvent(String eventType, String userId, Map<String, Object> eventData) {
        log.info("Audit event: type={}, userId={}, data={}", eventType, userId, eventData);
    }

    public void logEvent(String eventType, String userId, String message, Map<String, Object> eventData) {
        log.info("Audit event: type={}, userId={}, message={}, data={}", eventType, userId, message, eventData);
    }

    public void auditFinancialEvent(String eventType, String userId, String message, Map<String, Object> eventData) {
        log.info("Financial audit event: type={}, userId={}, message={}, data={}", eventType, userId, message, eventData);
    }
}
