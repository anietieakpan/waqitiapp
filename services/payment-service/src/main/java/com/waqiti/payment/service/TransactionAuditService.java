package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionAuditService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Map<String, List<AuditEntry>> auditLog = new ConcurrentHashMap<>();
    
    public void auditTransaction(String transactionId, String action, String userId, 
                                String details, Map<String, Object> metadata) {
        try {
            log.debug("Auditing transaction: transactionId={}, action={}, userId={}", 
                transactionId, action, userId);
            
            AuditEntry entry = AuditEntry.builder()
                .transactionId(transactionId)
                .action(action)
                .userId(userId)
                .details(details)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .ipAddress(metadata != null ? (String) metadata.get("ipAddress") : null)
                .userAgent(metadata != null ? (String) metadata.get("userAgent") : null)
                .build();
            
            auditLog.computeIfAbsent(transactionId, k -> new ArrayList<>()).add(entry);
            
            publishAuditEvent(entry);
            
            log.info("Transaction audited successfully: transactionId={}, action={}", transactionId, action);
            
        } catch (Exception e) {
            log.error("Failed to audit transaction: transactionId={}", transactionId, e);
        }
    }
    
    public void auditPaymentCreation(String transactionId, String userId, String amount, String currency) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("amount", amount);
        metadata.put("currency", currency);
        
        auditTransaction(transactionId, "PAYMENT_CREATED", userId, 
            "Payment created", metadata);
    }
    
    public void auditPaymentApproval(String transactionId, String userId, String approvedBy) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("approvedBy", approvedBy);
        
        auditTransaction(transactionId, "PAYMENT_APPROVED", userId, 
            "Payment approved", metadata);
    }
    
    public void auditPaymentRejection(String transactionId, String userId, String reason) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reason", reason);
        
        auditTransaction(transactionId, "PAYMENT_REJECTED", userId, 
            "Payment rejected", metadata);
    }
    
    public void auditFraudCheck(String transactionId, String userId, String riskLevel, Double riskScore) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("riskLevel", riskLevel);
        metadata.put("riskScore", riskScore);
        
        auditTransaction(transactionId, "FRAUD_CHECK", userId, 
            "Fraud check performed", metadata);
    }
    
    public List<AuditEntry> getTransactionAuditTrail(String transactionId) {
        return auditLog.getOrDefault(transactionId, new ArrayList<>());
    }
    
    public List<AuditEntry> getUserAuditTrail(String userId) {
        return auditLog.values().stream()
            .flatMap(List::stream)
            .filter(entry -> entry.getUserId().equals(userId))
            .sorted(Comparator.comparing(AuditEntry::getTimestamp).reversed())
            .toList();
    }
    
    private void publishAuditEvent(AuditEntry entry) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("transactionId", entry.getTransactionId());
            event.put("action", entry.getAction());
            event.put("userId", entry.getUserId());
            event.put("details", entry.getDetails());
            event.put("metadata", entry.getMetadata());
            event.put("timestamp", entry.getTimestamp().toString());
            
            kafkaTemplate.send("audit-events", entry.getTransactionId(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish audit event", e);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AuditEntry {
        private String transactionId;
        private String action;
        private String userId;
        private String details;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private String ipAddress;
        private String userAgent;
    }
}