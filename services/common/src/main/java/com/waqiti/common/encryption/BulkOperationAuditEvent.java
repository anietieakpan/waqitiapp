package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Audit event for bulk encryption/decryption operations
 */
@Data
@Builder
public class BulkOperationAuditEvent {
    private String operationType;
    private int recordCount;
    private int successCount;
    private int failureCount;
    private long duration; // milliseconds
    private String description;
    private DataContext context;
}