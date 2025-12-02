package com.waqiti.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    
    public void auditSecurityEvent(String eventType, String userId, String details) {
        log.info("AUDIT: event={}, user={}, details={}", eventType, userId, details);
    }
    
    public void auditEncryptionOperation(String operation, String keyId) {
        log.info("AUDIT_ENCRYPTION: operation={}, keyId={}", operation, keyId);
    }
}
