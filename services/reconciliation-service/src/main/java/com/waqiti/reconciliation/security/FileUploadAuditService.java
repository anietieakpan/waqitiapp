package com.waqiti.reconciliation.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Audit service for file upload security events.
 * Provides comprehensive logging and monitoring for file upload operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadAuditService {
    
    public void logSuccessfulValidation(String userId, String filename, String uploadId, long processingTime) {
        log.info("FILE_UPLOAD_AUDIT: Successful validation - User: {}, File: {}, UploadId: {}, ProcessingTime: {}ms",
                userId, filename, uploadId, processingTime);
    }
    
    public void logValidationFailure(String userId, String filename, String reason) {
        log.error("FILE_UPLOAD_SECURITY: Validation failed - User: {}, File: {}, Reason: {}",
                userId, filename, reason);
    }
    
    public void logMaliciousContent(String userId, String filename, String pattern) {
        log.error("FILE_UPLOAD_SECURITY: Malicious content detected - User: {}, File: {}, Pattern: {}",
                userId, filename, pattern);
    }
    
    public void logVirusDetected(String userId, String filename, String virusInfo) {
        log.error("FILE_UPLOAD_SECURITY: Virus detected - User: {}, File: {}, Virus: {}",
                userId, filename, virusInfo);
    }
    
    public void logRateLimitExceeded(String userId, String filename) {
        log.warn("FILE_UPLOAD_SECURITY: Rate limit exceeded - User: {}, File: {}",
                userId, filename);
    }
    
    public void logValidationError(String userId, String filename, String error) {
        log.error("FILE_UPLOAD_ERROR: Validation error - User: {}, File: {}, Error: {}",
                userId, filename, error);
    }
    
    public void logQuarantineAction(String userId, String filename, String reason) {
        log.error("FILE_UPLOAD_SECURITY: File quarantined - User: {}, File: {}, Reason: {}",
                userId, filename, reason);
    }
    
    public void logPathTraversalAttempt(String userId, String filename) {
        log.error("FILE_UPLOAD_SECURITY: Path traversal attempt - User: {}, File: {}, Timestamp: {}",
                userId, filename, Instant.now());
    }
    
    public void logSuspiciousExtension(String userId, String filename, String extension) {
        log.error("FILE_UPLOAD_SECURITY: Suspicious file extension - User: {}, File: {}, Extension: {}",
                userId, filename, extension);
    }
    
    public void logArchiveBombDetected(String userId, String filename, String details) {
        log.error("FILE_UPLOAD_SECURITY: Archive bomb detected - User: {}, File: {}, Details: {}",
                userId, filename, details);
    }
}