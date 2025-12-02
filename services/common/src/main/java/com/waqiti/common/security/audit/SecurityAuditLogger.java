package com.waqiti.common.security.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Security audit logger for tracking security-related events.
 * Provides comprehensive logging and metrics for security operations.
 */
@Slf4j
@Component
public class SecurityAuditLogger {
    
    private final Counter securityEventCounter;
    private final Counter securityViolationCounter;
    private final Counter authenticationFailureCounter;
    
    public SecurityAuditLogger(MeterRegistry meterRegistry) {
        this.securityEventCounter = Counter.builder("security.events.total")
                .description("Total number of security events")
                .register(meterRegistry);
        this.securityViolationCounter = Counter.builder("security.violations.total")
                .description("Total number of security violations")
                .register(meterRegistry);
        this.authenticationFailureCounter = Counter.builder("authentication.failures.total")
                .description("Total number of authentication failures")
                .register(meterRegistry);
    }
    
    /**
     * Log a security event with contextual information
     */
    public void logSecurityEvent(String event, String userId, String details) {
        logSecurityEvent(event, userId, details, new HashMap<>());
    }
    
    /**
     * Log a security event with additional context
     */
    public void logSecurityEvent(String event, String userId, String details, Map<String, Object> context) {
        securityEventCounter.increment();
        
        log.info("SECURITY_EVENT: {} | User: {} | Details: {} | Context: {} | Timestamp: {}", 
                event, userId, details, context, Instant.now());
    }
    
    /**
     * Log a security violation
     */
    public void logSecurityViolation(String violation, String userId, String details) {
        logSecurityViolation(violation, userId, details, new HashMap<>());
    }
    
    /**
     * Log a security violation with additional context
     */
    public void logSecurityViolation(String violation, String userId, String details, Map<String, Object> context) {
        securityViolationCounter.increment();
        
        log.warn("SECURITY_VIOLATION: {} | User: {} | Details: {} | Context: {} | Timestamp: {}", 
                violation, userId, details, context, Instant.now());
    }
    
    /**
     * Log authentication failure
     */
    public void logAuthenticationFailure(String username, String reason, String ipAddress) {
        authenticationFailureCounter.increment();
        
        Map<String, Object> context = new HashMap<>();
        context.put("ipAddress", ipAddress);
        context.put("reason", reason);
        
        log.warn("AUTHENTICATION_FAILURE: User: {} | Reason: {} | IP: {} | Timestamp: {}", 
                username, reason, ipAddress, Instant.now());
    }
    
    /**
     * Log data access event
     */
    public void logDataAccess(String userId, String resource, String operation) {
        logDataAccess(userId, resource, operation, new HashMap<>());
    }
    
    /**
     * Log data access event with additional context
     */
    public void logDataAccess(String userId, String resource, String operation, Map<String, Object> context) {
        Map<String, Object> auditContext = new HashMap<>(context);
        auditContext.put("resource", resource);
        auditContext.put("operation", operation);
        
        logSecurityEvent("DATA_ACCESS", userId, 
                String.format("Accessed %s with operation %s", resource, operation), auditContext);
    }
    
    /**
     * Log credential scan results
     */
    public void logCredentialScan(String userId, boolean credentialsFound, String details) {
        Map<String, Object> context = new HashMap<>();
        context.put("credentialsFound", credentialsFound);
        
        if (credentialsFound) {
            logSecurityViolation("CREDENTIAL_EXPOSURE", userId, details, context);
        } else {
            logSecurityEvent("CREDENTIAL_SCAN_CLEAN", userId, details, context);
        }
    }
    
    /**
     * Log secret management operations
     */
    public void logSecretOperation(String userId, String operation, String secretId) {
        Map<String, Object> context = new HashMap<>();
        context.put("secretId", secretId);
        context.put("operation", operation);
        
        logSecurityEvent("SECRET_OPERATION", userId, 
                String.format("Secret operation %s for %s", operation, secretId), context);
    }
    
    /**
     * Log input validation failure
     */
    public void logInputValidationFailure(String userId, String input, String violation) {
        Map<String, Object> context = new HashMap<>();
        context.put("inputType", input);
        context.put("violation", violation);
        
        logSecurityViolation("INPUT_VALIDATION_FAILURE", userId, 
                String.format("Input validation failed for %s: %s", input, violation), context);
    }
    
    /**
     * Log repository access security events
     */
    public void logRepositoryAccess(String userId, String repository, String operation, boolean authorized) {
        Map<String, Object> context = new HashMap<>();
        context.put("repository", repository);
        context.put("operation", operation);
        context.put("authorized", authorized);
        
        if (authorized) {
            logSecurityEvent("REPOSITORY_ACCESS", userId, 
                    String.format("Repository %s accessed with operation %s", repository, operation), context);
        } else {
            logSecurityViolation("UNAUTHORIZED_REPOSITORY_ACCESS", userId, 
                    String.format("Unauthorized access to repository %s with operation %s", repository, operation), context);
        }
    }
    
    /**
     * Log when credential violations are found during scanning
     */
    public void logCredentialViolationsFound(int violationCount) {
        Map<String, Object> context = new HashMap<>();
        context.put("violationCount", violationCount);
        context.put("scanTime", Instant.now());
        
        logSecurityViolation("CREDENTIAL_VIOLATIONS_DETECTED", "SYSTEM", 
                String.format("Found %d credential violations in codebase scan", violationCount), context);
    }
    
    /**
     * Log individual credential violation details
     */
    public void logCredentialViolation(String filePath, int lineNumber, String patternName, String severity) {
        Map<String, Object> context = new HashMap<>();
        context.put("filePath", filePath);
        context.put("lineNumber", lineNumber);
        context.put("patternName", patternName);
        context.put("severity", severity);
        
        logSecurityViolation("CREDENTIAL_VIOLATION", "SCANNER", 
                String.format("Credential violation in %s at line %d: %s (Severity: %s)", 
                        filePath, lineNumber, patternName, severity), context);
    }
    
    /**
     * Log credential remediation results
     */
    public void logCredentialRemediation(int totalViolations, int remediatedCount, int failedCount) {
        Map<String, Object> context = new HashMap<>();
        context.put("totalViolations", totalViolations);
        context.put("remediatedCount", remediatedCount);
        context.put("failedCount", failedCount);
        context.put("successRate", totalViolations > 0 ? (double) remediatedCount / totalViolations * 100 : 0);
        
        String event = failedCount > 0 ? "CREDENTIAL_REMEDIATION_PARTIAL" : "CREDENTIAL_REMEDIATION_COMPLETE";
        logSecurityEvent(event, "SYSTEM", 
                String.format("Remediated %d of %d credential violations (%d failed)", 
                        remediatedCount, totalViolations, failedCount), context);
    }
    
    /**
     * Log credential replacement
     */
    public void logCredentialReplacement(String filePath, int occurrences, String newCredential) {
        Map<String, Object> context = new HashMap<>();
        context.put("filePath", filePath);
        context.put("occurrences", occurrences);
        logSecurityEvent("CREDENTIAL_REPLACEMENT", "SYSTEM", 
                String.format("Replaced %d occurrences in %s", occurrences, filePath), context);
    }
    
    /**
     * Log credential rollback
     */
    public void logCredentialRollback(String filePath) {
        logSecurityEvent("CREDENTIAL_ROLLBACK", "SYSTEM", 
                String.format("Rolled back changes in %s", filePath), new HashMap<>());
    }
    
    /**
     * Log secret access
     */
    public void logSecretAccess(String secretKey, String userId) {
        Map<String, Object> context = new HashMap<>();
        context.put("secretKey", secretKey);
        logSecurityEvent("SECRET_ACCESS", userId, 
                String.format("Accessed secret: %s", secretKey), context);
    }
    
    /**
     * Log secret access failure
     */
    public void logSecretAccessFailure(String secretKey, String reason) {
        Map<String, Object> context = new HashMap<>();
        context.put("secretKey", secretKey);
        context.put("reason", reason);
        logSecurityViolation("SECRET_ACCESS_FAILURE", "SYSTEM", 
                String.format("Failed to access secret %s: %s", secretKey, reason), context);
    }
    
    /**
     * Log secret update
     */
    public void logSecretUpdate(String secretKey) {
        logSecurityEvent("SECRET_UPDATE", "SYSTEM", 
                String.format("Updated secret: %s", secretKey), new HashMap<>());
    }
    
    /**
     * Log secret creation
     */
    public void logSecretCreation(String secretKey) {
        logSecurityEvent("SECRET_CREATION", "SYSTEM", 
                String.format("Created secret: %s", secretKey), new HashMap<>());
    }
    
    /**
     * Log secret rotation
     */
    public void logSecretRotation(String secretKey, String newVersion) {
        Map<String, Object> context = new HashMap<>();
        context.put("secretKey", secretKey);
        context.put("newVersion", newVersion);
        logSecurityEvent("SECRET_ROTATION", "SYSTEM", 
                String.format("Rotated secret %s to version %s", secretKey, newVersion), context);
    }
    
    /**
     * Log secret deletion
     */
    public void logSecretDeletion(String secretKey, int recoveryWindowDays) {
        Map<String, Object> context = new HashMap<>();
        context.put("secretKey", secretKey);
        context.put("recoveryWindowDays", recoveryWindowDays);
        logSecurityEvent("SECRET_DELETION", "SYSTEM",
                String.format("Deleted secret %s with %d days recovery window", secretKey, recoveryWindowDays), context);
    }

    /**
     * Log IDOR (Insecure Direct Object Reference) attack attempt
     * CRITICAL SECURITY: Tracks unauthorized access attempts to resources
     */
    public void logIDORAttempt(java.util.UUID userId, String resourceType, java.util.UUID resourceId, String reason) {
        Map<String, Object> context = new HashMap<>();
        context.put("userId", userId.toString());
        context.put("resourceType", resourceType);
        context.put("resourceId", resourceId.toString());
        context.put("reason", reason);
        context.put("severity", "CRITICAL");
        context.put("attackType", "IDOR");

        securityViolationCounter.increment();

        log.error("IDOR_ATTACK_ATTEMPT: User {} attempted to access {} {} without ownership | Reason: {} | Timestamp: {}",
                userId, resourceType, resourceId, reason, Instant.now());

        logSecurityViolation("IDOR_ATTACK", userId.toString(),
                String.format("Unauthorized access attempt to %s %s", resourceType, resourceId), context);
    }

    /**
     * Log permission denied event (user owns resource but lacks required permission)
     */
    public void logPermissionDenied(java.util.UUID userId, String resourceType, java.util.UUID resourceId, String requiredPermission) {
        Map<String, Object> context = new HashMap<>();
        context.put("userId", userId.toString());
        context.put("resourceType", resourceType);
        context.put("resourceId", resourceId.toString());
        context.put("requiredPermission", requiredPermission);
        context.put("severity", "HIGH");

        securityViolationCounter.increment();

        log.warn("PERMISSION_DENIED: User {} lacks {} permission on {} {} | Timestamp: {}",
                userId, requiredPermission, resourceType, resourceId, Instant.now());

        logSecurityViolation("PERMISSION_DENIED", userId.toString(),
                String.format("User lacks %s permission on %s %s", requiredPermission, resourceType, resourceId), context);
    }

    /**
     * Log successful ownership validation
     */
    public void logOwnershipValidated(java.util.UUID userId, String resourceType, java.util.UUID resourceId) {
        Map<String, Object> context = new HashMap<>();
        context.put("userId", userId.toString());
        context.put("resourceType", resourceType);
        context.put("resourceId", resourceId.toString());

        log.debug("OWNERSHIP_VALIDATED: User {} owns {} {} | Timestamp: {}",
                userId, resourceType, resourceId, Instant.now());
    }

    /**
     * Log admin bypass of ownership check
     */
    public void logAdminOwnershipBypass(java.util.UUID adminUserId, String resourceType, java.util.UUID resourceId) {
        Map<String, Object> context = new HashMap<>();
        context.put("adminUserId", adminUserId.toString());
        context.put("resourceType", resourceType);
        context.put("resourceId", resourceId.toString());
        context.put("bypassReason", "ADMIN_ROLE");

        log.info("ADMIN_OWNERSHIP_BYPASS: Admin {} bypassed ownership check for {} {} | Timestamp: {}",
                adminUserId, resourceType, resourceId, Instant.now());

        logSecurityEvent("ADMIN_OWNERSHIP_BYPASS", adminUserId.toString(),
                String.format("Admin bypassed ownership check for %s %s", resourceType, resourceId), context);
    }

    /**
     * Log bulk ownership validation attempt
     */
    public void logBulkOwnershipValidation(java.util.UUID userId, String resourceType, int resourceCount, int failedCount) {
        Map<String, Object> context = new HashMap<>();
        context.put("userId", userId.toString());
        context.put("resourceType", resourceType);
        context.put("totalResources", resourceCount);
        context.put("failedResources", failedCount);
        context.put("successRate", resourceCount > 0 ? (double) (resourceCount - failedCount) / resourceCount * 100 : 0);

        if (failedCount > 0) {
            log.warn("BULK_OWNERSHIP_VALIDATION_FAILED: User {} failed to validate ownership for {} of {} {} resources | Timestamp: {}",
                    userId, failedCount, resourceCount, resourceType, Instant.now());

            logSecurityViolation("BULK_OWNERSHIP_VALIDATION_FAILED", userId.toString(),
                    String.format("Failed to validate ownership for %d of %d %s resources", failedCount, resourceCount, resourceType), context);
        } else {
            log.debug("BULK_OWNERSHIP_VALIDATION_SUCCESS: User {} validated ownership for all {} {} resources | Timestamp: {}",
                    userId, resourceCount, resourceType, Instant.now());
        }
    }

    // Database query logging methods
    public void logDatabaseQuery(String operation, int rowCount, int durationMs) {
        log.debug("DATABASE_QUERY: Operation: {}, Rows: {}, Duration: {}ms", operation, rowCount, durationMs);
    }

    public void logDatabaseUpdate(String operation, int rowsAffected, int durationMs) {
        log.info("DATABASE_UPDATE: Operation: {}, RowsAffected: {}, Duration: {}ms", operation, rowsAffected, durationMs);
    }

    public void logNativeQuery(String query, int rowCount, int durationMs) {
        log.warn("NATIVE_QUERY: Query: {}, Rows: {}, Duration: {}ms", query, rowCount, durationMs);
    }

    public void logJdbcQuery(String query, int rowCount, int durationMs) {
        log.warn("JDBC_QUERY: Query: {}, Rows: {}, Duration: {}ms", query, rowCount, durationMs);
    }

    public void logBatchOperation(String operation, int batchSize) {
        log.info("BATCH_OPERATION: Operation: {}, BatchSize: {}", operation, batchSize);
    }

    public void logDatabaseError(String operation, String error) {
        log.error("DATABASE_ERROR: Operation: {}, Error: {}", operation, error);
    }

    public void logSecurityThreat(String threatType, String details, String userId, String ipAddress) {
        log.error("SECURITY_THREAT: Type: {}, Details: {}, UserId: {}, IP: {}", threatType, details, userId, ipAddress);
        securityViolationCounter.increment();
    }

    // Input validation logging
    public void logRateLimitExceeded(String endpoint, String userId) {
        log.warn("RATE_LIMIT_EXCEEDED: Endpoint: {}, UserId: {}", endpoint, userId);
    }

    public void logInputValidationSuccess(String input, int length) {
        log.debug("INPUT_VALIDATION_SUCCESS: Input length: {}", length);
    }

    public void logInputValidationError(String input, String error) {
        log.error("INPUT_VALIDATION_ERROR: Error: {}", error);
    }

    /**
     * Log idempotency event for audit trail
     * @param idempotencyKey The idempotency key
     * @param operation The operation being performed
     * @param status Status of the idempotency check
     * @param userId User performing the operation
     * @param context Operation context
     */
    public void logIdempotencyEvent(String idempotencyKey, String operation, String status,
                                     String userId, Object context) {
        log.info("IDEMPOTENCY_EVENT: key={}, operation={}, status={}, user={}",
                idempotencyKey, operation, status, userId);
        if (context != null) {
            log.debug("IDEMPOTENCY_CONTEXT: {}", context);
        }
    }
}