package com.waqiti.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL SECURITY: Transaction Audit Service for regulatory compliance
 * Records all financial operations for audit trail, regulatory reporting, and fraud detection
 * Implements immutable audit logs with cryptographic integrity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionAuditService {
    
    private final TransactionAuditRepository auditRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityContextService securityContextService;
    private final AuditEventEncryptionService encryptionService;
    
    private static final String AUDIT_TOPIC = "financial.audit.events";
    
    /**
     * Records a financial transaction audit event
     */
    @Transactional
    public void auditTransaction(TransactionAuditEvent event) {
        try {
            // Enrich with security context
            enrichWithSecurityContext(event);
            
            // Generate cryptographic hash for integrity
            event.setIntegrityHash(generateIntegrityHash(event));
            
            // Persist to database for compliance
            TransactionAuditRecord record = createAuditRecord(event);
            auditRepository.save(record);
            
            // Send to Kafka for real-time processing
            publishAuditEvent(event);
            
            log.debug("SECURITY: Transaction audit recorded: {} for amount: {}", 
                    event.getTransactionType(), event.getAmount());
                    
        } catch (Exception e) {
            log.error("CRITICAL: Failed to record transaction audit: {}", event.getTransactionId(), e);
            // Never fail the transaction due to audit logging
            // But record the audit failure for investigation
            recordAuditFailure(event, e);
        }
    }
    
    /**
     * Records wallet balance changes
     */
    public void auditWalletOperation(UUID walletId, String operation, BigDecimal amount, 
                                   BigDecimal previousBalance, BigDecimal newBalance, String transactionId) {
        
        TransactionAuditEvent event = TransactionAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(transactionId)
                .transactionType("WALLET_" + operation.toUpperCase())
                .sourceEntityId(walletId.toString())
                .sourceEntityType("WALLET")
                .amount(amount)
                .currency("USD") // Default currency, should be configurable
                .previousBalance(previousBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .timestamp(Instant.now())
                .build();
                
        auditTransaction(event);
    }
    
    /**
     * Records payment processing events
     */
    public void auditPaymentOperation(String paymentId, String paymentMethod, String operation,
                                    BigDecimal amount, String currency, String status, 
                                    String providerId, Map<String, Object> metadata) {
        
        TransactionAuditEvent event = TransactionAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(paymentId)
                .transactionType("PAYMENT_" + operation.toUpperCase())
                .sourceEntityId(paymentId)
                .sourceEntityType("PAYMENT")
                .amount(amount)
                .currency(currency)
                .status(status)
                .paymentMethod(paymentMethod)
                .providerId(providerId)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
                
        auditTransaction(event);
    }
    
    /**
     * Records money transfer events
     */
    public void auditTransferOperation(String transferId, UUID sourceWalletId, UUID targetWalletId,
                                     BigDecimal amount, String currency, String status,
                                     Map<String, Object> transferDetails) {
        
        TransactionAuditEvent event = TransactionAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(transferId)
                .transactionType("TRANSFER")
                .sourceEntityId(sourceWalletId.toString())
                .sourceEntityType("WALLET")
                .targetEntityId(targetWalletId.toString())
                .targetEntityType("WALLET")
                .amount(amount)
                .currency(currency)
                .status(status)
                .metadata(transferDetails)
                .timestamp(Instant.now())
                .build();
                
        auditTransaction(event);
    }
    
    /**
     * Records KYC/AML compliance events
     */
    public void auditComplianceEvent(String userId, String eventType, String status,
                                   String riskLevel, Map<String, Object> complianceData) {
        
        TransactionAuditEvent event = TransactionAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID().toString()) // Generate for compliance events
                .transactionType("COMPLIANCE_" + eventType.toUpperCase())
                .sourceEntityId(userId)
                .sourceEntityType("USER")
                .status(status)
                .riskLevel(riskLevel)
                .metadata(complianceData)
                .timestamp(Instant.now())
                .build();
                
        auditTransaction(event);
    }
    
    /**
     * Records fraud detection events
     */
    public void auditFraudEvent(String transactionId, String fraudType, double riskScore,
                              String decision, Map<String, Object> fraudDetails) {
        
        TransactionAuditEvent event = TransactionAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(transactionId)
                .transactionType("FRAUD_" + fraudType.toUpperCase())
                .sourceEntityId(transactionId)
                .sourceEntityType("TRANSACTION")
                .riskScore(riskScore)
                .status(decision)
                .metadata(fraudDetails)
                .timestamp(Instant.now())
                .build();
                
        auditTransaction(event);
    }
    
    /**
     * Records authentication and authorization events
     */
    public void auditSecurityEvent(String userId, String eventType, String status,
                                 String ipAddress, String userAgent, Map<String, Object> details) {
        
        TransactionAuditEvent event = TransactionAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID().toString())
                .transactionType("SECURITY_" + eventType.toUpperCase())
                .sourceEntityId(userId)
                .sourceEntityType("USER")
                .status(status)
                .clientIpAddress(ipAddress)
                .userAgent(userAgent)
                .metadata(details)
                .timestamp(Instant.now())
                .build();
                
        auditTransaction(event);
    }
    
    /**
     * Enriches audit event with current security context
     */
    private void enrichWithSecurityContext(TransactionAuditEvent event) {
        try {
            if (event.getUserId() == null) {
                event.setUserId(securityContextService.getCurrentUserId());
            }
            if (event.getSessionId() == null) {
                event.setSessionId(securityContextService.getCurrentSessionId());
            }
            if (event.getCorrelationId() == null) {
                event.setCorrelationId(securityContextService.getCorrelationId());
            }
            if (event.getClientIpAddress() == null) {
                event.setClientIpAddress(securityContextService.getClientIpAddress());
            }
            if (event.getUserAgent() == null) {
                event.setUserAgent(securityContextService.getUserAgent());
            }
        } catch (Exception e) {
            log.warn("Failed to enrich audit event with security context", e);
        }
    }
    
    /**
     * Generates cryptographic hash for audit event integrity
     */
    private String generateIntegrityHash(TransactionAuditEvent event) {
        try {
            // Create normalized representation for hashing
            String normalizedData = String.format("%s|%s|%s|%s|%s|%s|%s",
                    event.getTransactionId(),
                    event.getTransactionType(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getStatus(),
                    event.getTimestamp(),
                    event.getUserId());
                    
            return encryptionService.generateIntegrityHash(normalizedData);
        } catch (Exception e) {
            log.error("Failed to generate integrity hash for audit event", e);
            return "HASH_GENERATION_FAILED_" + Instant.now().toEpochMilli();
        }
    }
    
    /**
     * Creates persistent audit record from event
     */
    private TransactionAuditRecord createAuditRecord(TransactionAuditEvent event) {
        return TransactionAuditRecord.builder()
                .eventId(event.getEventId())
                .transactionId(event.getTransactionId())
                .transactionType(event.getTransactionType())
                .sourceEntityId(event.getSourceEntityId())
                .sourceEntityType(event.getSourceEntityType())
                .targetEntityId(event.getTargetEntityId())
                .targetEntityType(event.getTargetEntityType())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .previousBalance(event.getPreviousBalance())
                .newBalance(event.getNewBalance())
                .status(event.getStatus())
                .paymentMethod(event.getPaymentMethod())
                .providerId(event.getProviderId())
                .riskScore(event.getRiskScore())
                .riskLevel(event.getRiskLevel())
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .correlationId(event.getCorrelationId())
                .clientIpAddress(event.getClientIpAddress())
                .userAgent(event.getUserAgent())
                .deviceFingerprint(event.getDeviceFingerprint())
                .metadataJson(serializeMetadata(event.getMetadata()))
                .integrityHash(event.getIntegrityHash())
                .timestamp(event.getTimestamp())
                .build();
    }
    
    /**
     * Publishes audit event to Kafka for real-time processing
     */
    private void publishAuditEvent(TransactionAuditEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(AUDIT_TOPIC, event.getTransactionId(), eventJson);
            
            log.debug("SECURITY: Published audit event to Kafka: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to publish audit event to Kafka: {}", event.getEventId(), e);
            // Store failed publication for retry
            recordKafkaPublicationFailure(event, e);
        }
    }
    
    /**
     * Serializes metadata to JSON
     */
    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}"; // Return empty JSON object instead of null for audit compliance
        }
        
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit metadata", e);
            return "{\"serialization_error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * Records audit logging failures for investigation
     */
    private void recordAuditFailure(TransactionAuditEvent event, Exception error) {
        try {
            AuditFailureRecord failure = AuditFailureRecord.builder()
                    .failureId(UUID.randomUUID().toString())
                    .originalEventId(event.getEventId().toString())
                    .transactionId(event.getTransactionId())
                    .failureType("AUDIT_LOGGING_FAILURE")
                    .errorMessage(error.getMessage())
                    .errorStackTrace(getStackTrace(error))
                    .originalEventData(objectMapper.writeValueAsString(event))
                    .failureTimestamp(Instant.now())
                    .build();
                    
            // Store failure record for manual investigation
            auditRepository.saveAuditFailure(failure);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to record audit failure - complete audit system breakdown", e);
        }
    }
    
    /**
     * Records Kafka publication failures for retry
     */
    private void recordKafkaPublicationFailure(TransactionAuditEvent event, Exception error) {
        try {
            KafkaPublicationFailure failure = KafkaPublicationFailure.builder()
                    .failureId(UUID.randomUUID().toString())
                    .eventId(event.getEventId().toString())
                    .topic(AUDIT_TOPIC)
                    .partitionKey(event.getTransactionId())
                    .eventData(objectMapper.writeValueAsString(event))
                    .errorMessage(error.getMessage())
                    .failureTimestamp(Instant.now())
                    .retryCount(0)
                    .build();
                    
            auditRepository.saveKafkaFailure(failure);
            
        } catch (Exception e) {
            log.error("Failed to record Kafka publication failure", e);
        }
    }
    
    /**
     * Gets stack trace as string (PCI DSS compliant)
     */
    private String getStackTrace(Exception e) {
        try {
            // PCI DSS FIX: Build stack trace without printStackTrace()
            StringBuilder sb = new StringBuilder();
            sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");

            for (StackTraceElement element : e.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }

            Throwable cause = e.getCause();
            if (cause != null) {
                sb.append("Caused by: ").append(cause.getClass().getName())
                  .append(": ").append(cause.getMessage()).append("\n");
            }

            return sb.toString();
        } catch (Exception ex) {
            return "Stack trace unavailable: " + ex.getMessage();
        }
    }
    
    /**
     * Utility methods for common audit scenarios
     */
    public static class AuditUtilities {
        
        public static Map<String, Object> createTransferMetadata(String transferType, String description,
                                                               String reference, Map<String, String> additionalData) {
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("transferType", transferType);
            metadata.put("description", description);
            metadata.put("reference", reference);
            if (additionalData != null) {
                metadata.putAll(additionalData);
            }
            return metadata;
        }
        
        public static Map<String, Object> createPaymentMetadata(String merchantId, String orderId,
                                                              String productId, Map<String, String> additionalData) {
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("merchantId", merchantId);
            metadata.put("orderId", orderId);
            metadata.put("productId", productId);
            if (additionalData != null) {
                metadata.putAll(additionalData);
            }
            return metadata;
        }
        
        public static Map<String, Object> createComplianceMetadata(String verificationLevel, String documentType,
                                                                 String sanctionsStatus, Map<String, String> additionalData) {
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("verificationLevel", verificationLevel);
            metadata.put("documentType", documentType);
            metadata.put("sanctionsStatus", sanctionsStatus);
            if (additionalData != null) {
                metadata.putAll(additionalData);
            }
            return metadata;
        }
    }
}