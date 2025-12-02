package com.waqiti.common.retention;

import com.waqiti.common.retention.model.RetentionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Data Retention Audit Service
 *
 * PRODUCTION FIX: Created to support DataRetentionService audit requirements
 */
@Slf4j
@Service
public class DataRetentionAuditService {

    public void auditRetentionEnforcement(RetentionResult result) {
        log.info("DATA RETENTION AUDIT: Policy: {}, Records Deleted: {}, Status: {}",
            result.getPolicyName(),
            result.getRecordsDeleted(),
            result.getStatus());
    }

    public void auditRetentionFailure(DataRetentionPolicy policy, Exception e) {
        log.error("DATA RETENTION FAILURE: Policy: {}, Error: {}",
            policy.getDataType(),
            e.getMessage(),
            e);
    }

    public void auditSystemFailure(String operation, Exception e) {
        log.error("DATA RETENTION SYSTEM FAILURE: Operation: {}, Error: {}",
            operation,
            e.getMessage(),
            e);
    }

    public void auditLegalHold(String entityType, String entityId, String reason) {
        log.info("LEGAL HOLD APPLIED: Entity: {}, ID: {}, Reason: {}",
            entityType,
            entityId,
            reason);
    }

    public void auditLegalHoldRelease(String entityType, String entityId, String reason) {
        log.info("LEGAL HOLD RELEASED: Entity: {}, ID: {}, Reason: {}",
            entityType,
            entityId,
            reason);
    }
}
