package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Audit event for key management operations
 */
@Data
@Builder
public class KeyManagementAuditEvent {
    private EncryptionAuditService.KeyManagementOperation operation;
    private int keyVersion;
    private int previousVersion;
    private String reason;
    private boolean success;
    private String description;
}