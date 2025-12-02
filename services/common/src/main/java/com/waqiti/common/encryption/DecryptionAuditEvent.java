package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Audit event for decryption operations
 */
@Data
@Builder
public class DecryptionAuditEvent {
    private String dataType;
    private AdvancedEncryptionService.DataClassification dataClassification;
    private int keyVersion;
    private String algorithm;
    private String accessReason;
    private boolean success;
    private String failureReason;
    private String errorCode;
    private DataContext context;
}