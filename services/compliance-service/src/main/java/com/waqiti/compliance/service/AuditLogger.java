package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Audit Logger for compliance events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {
    
    public void logAuditEvent(String eventType, String entityId, Map<String, Object> details) {
        log.info("Audit Event: type={} entityId={} timestamp={} details={}", 
                eventType, entityId, LocalDateTime.now(), details);
    }
    
    public void logComplianceAction(String action, String userId, String details) {
        log.info("Compliance Action: action={} userId={} details={} timestamp={}", 
                action, userId, details, LocalDateTime.now());
    }
    
    public void logRegulatoryEvent(String eventType, String jurisdiction, Map<String, Object> data) {
        log.info("Regulatory Event: type={} jurisdiction={} timestamp={} data={}", 
                eventType, jurisdiction, LocalDateTime.now(), data);
    }
}