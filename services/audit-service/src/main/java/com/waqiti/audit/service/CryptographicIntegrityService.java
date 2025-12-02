package com.waqiti.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.TreeMap;

/**
 * Service for ensuring cryptographic integrity of audit logs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CryptographicIntegrityService {
    
    private final ObjectMapper objectMapper;
    
    @Value("${audit.integrity.algorithm:SHA-256}")
    private String hashAlgorithm;
    
    @Value("${audit.integrity.hmac.algorithm:HmacSHA256}")
    private String hmacAlgorithm;
    
    @Value("${audit.integrity.hmac.secret:${AUDIT_HMAC_SECRET:default-secret-key}}")
    private String hmacSecret;
    
    @Value("${audit.integrity.include.timestamp:true}")
    private boolean includeTimestamp;
    
    /**
     * Generate integrity hash for audit log
     */
    public String generateIntegrityHash(AuditLog auditLog) {
        try {
            // Create canonical representation of audit log
            String canonicalData = createCanonicalRepresentation(auditLog);
            
            // Generate HMAC for integrity
            String hmac = generateHMAC(canonicalData);
            
            // Combine with SHA hash for additional security
            String combinedHash = generateCombinedHash(canonicalData, hmac);
            
            log.debug("Generated integrity hash for audit log: {}", auditLog.getAuditId());
            return combinedHash;
            
        } catch (Exception e) {
            log.error("Failed to generate integrity hash for audit log", e);
            throw new RuntimeException("Integrity hash generation failed", e);
        }
    }
    
    /**
     * Verify integrity of audit log
     */
    public boolean verifyIntegrity(AuditLog auditLog, String providedHash) {
        try {
            String calculatedHash = generateIntegrityHash(auditLog);
            boolean isValid = calculatedHash.equals(providedHash);
            
            if (!isValid) {
                log.warn("Integrity verification failed for audit log: {}", auditLog.getAuditId());
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Failed to verify integrity for audit log", e);
            return false;
        }
    }
    
    /**
     * Create canonical representation of audit log for hashing
     */
    private String createCanonicalRepresentation(AuditLog auditLog) {
        TreeMap<String, String> canonicalMap = new TreeMap<>();
        
        // Add core fields in deterministic order
        canonicalMap.put("auditId", String.valueOf(auditLog.getAuditId()));
        canonicalMap.put("eventType", auditLog.getEventType());
        canonicalMap.put("entityType", auditLog.getEntityType());
        canonicalMap.put("entityId", auditLog.getEntityId());
        canonicalMap.put("action", auditLog.getAction());
        canonicalMap.put("userId", auditLog.getUserId());
        canonicalMap.put("sourceIpAddress", auditLog.getSourceIpAddress());
        
        if (includeTimestamp && auditLog.getTimestamp() != null) {
            canonicalMap.put("timestamp", 
                auditLog.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        // Add state information if present
        if (auditLog.getBeforeState() != null) {
            canonicalMap.put("beforeState", auditLog.getBeforeState());
        }
        if (auditLog.getAfterState() != null) {
            canonicalMap.put("afterState", auditLog.getAfterState());
        }
        
        // Add risk and compliance information
        if (auditLog.getRiskLevel() != null) {
            canonicalMap.put("riskLevel", auditLog.getRiskLevel());
        }
        
        // Convert to canonical string
        StringBuilder canonical = new StringBuilder();
        canonicalMap.forEach((key, value) -> {
            if (value != null) {
                canonical.append(key).append("=").append(value).append("|");
            }
        });
        
        return canonical.toString();
    }
    
    /**
     * Generate HMAC for data
     */
    private String generateHMAC(String data) throws Exception {
        Mac mac = Mac.getInstance(hmacAlgorithm);
        SecretKeySpec secretKey = new SecretKeySpec(
            hmacSecret.getBytes(StandardCharsets.UTF_8), 
            hmacAlgorithm
        );
        mac.init(secretKey);
        
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
    
    /**
     * Generate SHA hash
     */
    private String generateSHA(String data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
        byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }
    
    /**
     * Generate combined hash for enhanced security
     */
    private String generateCombinedHash(String data, String hmac) throws NoSuchAlgorithmException {
        String combined = data + "|" + hmac;
        return generateSHA(combined);
    }
    
    /**
     * Convert bytes to hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Generate chain hash for linked audit logs
     */
    public String generateChainHash(String previousHash, AuditLog currentLog) {
        try {
            String currentHash = generateIntegrityHash(currentLog);
            String chainData = previousHash + "|" + currentHash;
            return generateSHA(chainData);
        } catch (Exception e) {
            log.error("Failed to generate chain hash", e);
            throw new RuntimeException("Chain hash generation failed", e);
        }
    }
    
    /**
     * Verify chain integrity
     */
    public boolean verifyChainIntegrity(String previousHash, AuditLog currentLog, String expectedChainHash) {
        try {
            String calculatedChainHash = generateChainHash(previousHash, currentLog);
            return calculatedChainHash.equals(expectedChainHash);
        } catch (Exception e) {
            log.error("Failed to verify chain integrity", e);
            return false;
        }
    }
}