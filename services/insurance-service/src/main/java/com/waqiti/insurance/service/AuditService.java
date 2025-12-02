package com.waqiti.insurance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    public void logEvent(String eventType, String entityId, String action, String details) {
        log.info("AUDIT: type={}, entity={}, action={}, details={}", eventType, entityId, action, details);
    }
}
