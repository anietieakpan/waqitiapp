package com.waqiti.common.security.awareness.audit;

import com.waqiti.common.security.awareness.domain.SecurityAwarenessAuditLog;
import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.repository.SecurityAwarenessAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAwarenessAuditEventPublisher {

    private final SecurityAwarenessAuditRepository auditRepository;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAuditEvent(SecurityAwarenessAuditEvent event) {
        try {
            SecurityAwarenessAuditLog auditLog = SecurityAwarenessAuditLog.builder()
                    .eventType(event.getEventType())
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId())
                    .employeeId(event.getEmployeeId())
                    .pciRequirement(event.getPciRequirement())
                    .complianceStatus(event.getComplianceStatus())
                    .eventData(event.getEventData())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .build();

            auditRepository.save(auditLog);

            log.debug("Audit event logged: {}", event.getEventType());

        } catch (Exception e) {
            log.error("Failed to log audit event: {}", event.getEventType(), e);
            // Don't throw - audit logging should not break business logic
        }
    }
}