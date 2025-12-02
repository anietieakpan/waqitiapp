package com.waqiti.transaction.service;

import com.waqiti.common.client.AuditServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditServiceClient auditServiceClient;
    
    @CircuitBreaker(name = "audit-service", fallbackMethod = "auditTransactionBlockFallback")
    @Retry(name = "audit-service")
    public void auditTransactionBlock(String blockId, String operation, String blockType, 
                                    String targetType, String targetId, String blockReason,
                                    String status, String requestedBy, String eventId) {
        log.debug("Auditing transaction block: {} operation: {} type: {} target: {}", 
                blockId, operation, blockType, targetId);
        
        try {
            Map<String, Object> metadata = Map.of(
                    "blockId", blockId,
                    "operation", operation,
                    "blockType", blockType,
                    "targetType", targetType,
                    "targetId", targetId,
                    "blockReason", blockReason != null ? blockReason : "N/A",
                    "status", status,
                    "requestedBy", requestedBy,
                    "eventId", eventId
            );
            
            auditServiceClient.createAuditEvent(
                    "TRANSACTION_BLOCK", 
                    blockId, 
                    operation + "_" + blockType, 
                    metadata
            );
            
            log.debug("Successfully audited transaction block: {}", blockId);
            
        } catch (Exception e) {
            log.error("Failed to audit transaction block: {}", blockId, e);
            auditTransactionBlockFallback(blockId, operation, blockType, targetType, 
                    targetId, blockReason, status, requestedBy, eventId, e);
        }
    }
    
    @CircuitBreaker(name = "audit-service", fallbackMethod = "logValidationErrorFallback")
    @Retry(name = "audit-service")
    public void logValidationError(String eventId, String errorMessage) {
        log.debug("Logging validation error for event: {} message: {}", eventId, errorMessage);
        
        try {
            Map<String, Object> metadata = Map.of(
                    "eventId", eventId,
                    "errorMessage", errorMessage,
                    "errorType", "VALIDATION_ERROR"
            );
            
            auditServiceClient.createAuditEvent(
                    "TRANSACTION_BLOCK_VALIDATION_ERROR", 
                    eventId, 
                    "VALIDATION_FAILED", 
                    metadata
            );
            
            log.debug("Successfully logged validation error: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to log validation error: {}", eventId, e);
            logValidationErrorFallback(eventId, errorMessage, e);
        }
    }
    
    private void auditTransactionBlockFallback(String blockId, String operation, String blockType, 
                                             String targetType, String targetId, String blockReason,
                                             String status, String requestedBy, String eventId, 
                                             Exception e) {
        log.warn("Audit service unavailable - transaction block audit not recorded: {} (fallback)", blockId);
    }
    
    private void logValidationErrorFallback(String eventId, String errorMessage, Exception e) {
        log.warn("Audit service unavailable - validation error not logged: {} (fallback)", eventId);
    }
}