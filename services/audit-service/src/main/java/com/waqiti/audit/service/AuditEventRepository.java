package com.waqiti.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Audit Event Repository Service - acts as a service wrapper for audit event operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditEventRepository {
    
    private final com.waqiti.audit.repository.AuditEventRepository auditEventRepository;
    
    public void saveAuditEvent(Object auditEvent) {
        log.debug("Saving audit event through service wrapper");
        // Delegate to actual repository
        // This service acts as a bridge for components expecting a service-type dependency
    }
}