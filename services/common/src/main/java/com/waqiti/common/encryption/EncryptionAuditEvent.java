package com.waqiti.common.encryption;

import com.waqiti.common.audit.AuditSeverity;
import lombok.Builder;
import lombok.Data;

/**
 * Audit event for encryption operations
 */
@Data
@Builder
public class EncryptionAuditEvent {
    private EncryptionAuditService.EncryptionEventType eventType;
    private String fieldName;
    private AdvancedEncryptionService.DataClassification dataClassification;
    private AdvancedEncryptionService.EncryptionMethod encryptionMethod;
    private int keyVersion;
    private String algorithm;
    private int dataSize;
    private AuditSeverity severity;
    private String description;
    private DataContext context;
}