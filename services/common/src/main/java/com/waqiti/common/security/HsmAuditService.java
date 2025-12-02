package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Comprehensive audit service for HSM operations.
 * Provides detailed logging for compliance and security monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HsmAuditService {
    
    public void logHsmInitialization(String provider, String partition, Instant timestamp) {
        log.info("HSM_AUDIT: HSM initialized - Provider: {}, Partition: {}, Timestamp: {}", 
                provider, partition, timestamp);
    }
    
    public void logHsmInitializationFailure(String error, Instant timestamp) {
        log.error("HSM_AUDIT: HSM initialization failed - Error: {}, Timestamp: {}", 
                error, timestamp);
    }
    
    public void logKeyExistenceCheck(String keyId, boolean exists, Instant timestamp) {
        log.info("HSM_AUDIT: Key existence check - KeyId: {}, Exists: {}, Timestamp: {}", 
                keyId, exists, timestamp);
    }
    
    public void logKeyGeneration(String keyId, String algorithm, int keySize, Instant timestamp) {
        log.info("HSM_AUDIT: Key generated in HSM - KeyId: {}, Algorithm: {}, Size: {}, Timestamp: {}", 
                keyId, algorithm, keySize, timestamp);
    }
    
    public void logKeyGenerationFailure(String keyId, String error, Instant timestamp) {
        log.error("HSM_AUDIT: HSM key generation failed - KeyId: {}, Error: {}, Timestamp: {}", 
                keyId, error, timestamp);
    }
    
    public void logKeyRetrieval(String keyId, Instant timestamp) {
        log.info("HSM_AUDIT: Key retrieved from HSM - KeyId: {}, Timestamp: {}", 
                keyId, timestamp);
    }
    
    public void logKeyRetrievalFailure(String keyId, String error, Instant timestamp) {
        log.error("HSM_AUDIT: HSM key retrieval failed - KeyId: {}, Error: {}, Timestamp: {}", 
                keyId, error, timestamp);
    }
    
    public void logEncryptionOperation(String keyId, int dataLength, Instant timestamp) {
        log.info("HSM_AUDIT: Encryption operation - KeyId: {}, DataLength: {}, Timestamp: {}", 
                keyId, dataLength, timestamp);
    }
    
    public void logDecryptionOperation(String keyId, int dataLength, Instant timestamp) {
        log.info("HSM_AUDIT: Decryption operation - KeyId: {}, DataLength: {}, Timestamp: {}", 
                keyId, dataLength, timestamp);
    }
    
    public void logKeyDeletion(String keyId, Instant timestamp) {
        log.info("HSM_AUDIT: Key deleted from HSM - KeyId: {}, Timestamp: {}", 
                keyId, timestamp);
    }
    
    public void logKeyDeletionFailure(String keyId, String error, Instant timestamp) {
        log.error("HSM_AUDIT: HSM key deletion failed - KeyId: {}, Error: {}, Timestamp: {}", 
                keyId, error, timestamp);
    }
    
    public void logHsmOperationFailure(String operation, String keyId, String error, Instant timestamp) {
        log.error("HSM_AUDIT: HSM operation failed - Operation: {}, KeyId: {}, Error: {}, Timestamp: {}", 
                operation, keyId, error, timestamp);
    }
}