package com.waqiti.common.security.awareness.audit;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.repository.SecurityAwarenessAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAwarenessAuditEvent {
    private String eventType;
    private String entityType;
    private UUID entityId;
    private UUID employeeId;
    private String pciRequirement;
    private String complianceStatus;
    private Map<String, Object> eventData;
    private String ipAddress;
    private String userAgent;

    // Getters and setters
}