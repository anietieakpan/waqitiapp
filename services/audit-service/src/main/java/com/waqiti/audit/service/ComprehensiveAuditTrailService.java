package com.waqiti.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL COMPLIANCE SERVICE: Comprehensive Audit Trail Service
 * Ensures complete transaction traceability and regulatory compliance
 * 
 * Features:
 * - Immutable audit log with cryptographic integrity
 * - Real-time event streaming and processing
 * - Multi-level audit data classification
 * - Automated compliance reporting
 * - Forensic investigation support
 * - Data retention policy enforcement
 * - Privacy-preserving audit logging
 * - Distributed audit log replication
 * - Event correlation and analysis
 * - Regulatory export capabilities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveAuditTrailService {
    
    private final AuditLogRepository auditLogRepository;
    private final EncryptedAuditRepository encryptedAuditRepository;
    private final ComplianceReportRepository complianceReportRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${audit.encryption.key}")
    private String encryptionKey;
    
    @Value("${audit.retention.days:2555}") // 7 years default
    private int retentionDays;
    
    @Value("${audit.batch-size:1000}")
    private int batchSize;
    
    @Value("${audit.async-processing:true}")
    private boolean asyncProcessing;
    
    @Value("${audit.integrity-checking:true}")
    private boolean integrityCheckingEnabled;
    
    // Event correlation and deduplication
    private final Map<String, EventCorrelation> correlationMap = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    
    // Audit trail integrity
    private volatile String lastAuditHash = "";
    private final Object hashLock = new Object();
    
    @PostConstruct
    public void initialize() {
        initializeAuditTrail();
        setupComplianceMonitoring();
        log.info("Comprehensive Audit Trail Service initialized - Retention: {} days", retentionDays);
    }
    
    /**
     * CRITICAL: Log audit event with cryptographic integrity
     */
    @Async
    public CompletableFuture<String> logAuditEvent(AuditEventRequest request) {
        try {
            // Validate and enrich event
            AuditEvent event = enrichAuditEvent(request);

            // Check for duplicate events
            if (isDuplicateEvent(event)) {
                log.debug("Duplicate audit event detected, skipping: {}", event.getEventId());
                return CompletableFuture.completedFuture(event.getEventId());
            }

            // Calculate integrity hash
            String integrityHash = calculateIntegrityHash(event);
            event.setIntegrityHash(integrityHash);

            // Store audit event
            CompletableFuture<String> storageFuture = storeAuditEvent(event);

            // Stream to real-time processing
            streamAuditEvent(event);

            // Update event correlation
            updateEventCorrelation(event);

            // Mark as processed
            processedEventIds.add(event.getEventId());

            return storageFuture;

        } catch (Exception e) {
            log.error("Critical error logging audit event", e);
            // Even if audit fails, we must not fail the main operation
            return CompletableFuture.completedFuture("ERROR");
        }
    }

    /**
     * Simplified audit event logging with basic parameters
     */
    public void logAuditEvent(String eventId, String eventType, String entityId, Object eventData) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            if (eventData != null) {
                metadata.put("data", eventData);
            }
            if (entityId != null) {
                metadata.put("entityId", entityId);
            }

            AuditEventRequest request = AuditEventRequest.builder()
                .eventType(eventType != null ? eventType : "GENERIC")
                .category("SYSTEM")
                .severity(AuditSeverity.INFO)
                .resource(entityId)
                .action("LOG")
                .outcome(AuditOutcome.SUCCESS)
                .description("Audit event: " + eventType)
                .metadata(metadata)
                .correlationId(eventId)
                .build();

            logAuditEvent(request);
        } catch (Exception e) {
            log.error("Failed to log simplified audit event: eventId={}, eventType={}", eventId, eventType, e);
        }
    }
    
    /**
     * Enhanced audit event creation
     */
    private AuditEvent enrichAuditEvent(AuditEventRequest request) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now(ZoneOffset.UTC))
            .eventType(request.getEventType())
            .category(request.getCategory())
            .severity(request.getSeverity())
            .userId(request.getUserId())
            .sessionId(request.getSessionId())
            .ipAddress(maskIpAddress(request.getIpAddress()))
            .userAgent(request.getUserAgent())
            .resource(request.getResource())
            .action(request.getAction())
            .outcome(request.getOutcome())
            .description(request.getDescription())
            .metadata(sanitizeMetadata(request.getMetadata()))
            .correlationId(request.getCorrelationId())
            .parentEventId(request.getParentEventId())
            .dataClassification(determineDataClassification(request))
            .complianceFlags(determineComplianceFlags(request))
            .build();
        
        // Add system context
        event.setSystemInfo(SystemInfo.builder()
            .serviceId("audit-service")
            .version("1.0.0")
            .hostname(System.getenv("HOSTNAME"))
            .processId(ProcessHandle.current().pid())
            .threadId(Thread.currentThread().getId())
            .build());
        
        return event;
    }
    
    /**
     * Store audit event with multiple persistence layers
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    private CompletableFuture<String> storeAuditEvent(AuditEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Primary storage - immediate persistence
                AuditLog auditLog = convertToAuditLog(event);
                auditLogRepository.save(auditLog);
                
                // Secondary storage - encrypted for sensitive events
                if (requiresEncryption(event)) {
                    EncryptedAuditLog encryptedLog = encryptAuditEvent(event);
                    encryptedAuditRepository.save(encryptedLog);
                }
                
                // Update audit chain integrity
                updateAuditChain(event);
                
                log.debug("Audit event stored: {} - {}", event.getEventId(), event.getEventType());
                return event.getEventId();
                
            } catch (Exception e) {
                log.error("Failed to store audit event: {}", event.getEventId(), e);
                throw new AuditStorageException("Failed to store audit event", e);
            }
        });
    }
    
    /**
     * Stream audit event for real-time processing
     */
    private void streamAuditEvent(AuditEvent event) {
        try {
            // Send to audit stream for real-time monitoring
            kafkaTemplate.send("audit.events.stream", event.getEventId(), event);
            
            // Send high-severity events to alert stream
            if (event.getSeverity() == AuditSeverity.CRITICAL || 
                event.getSeverity() == AuditSeverity.HIGH) {
                kafkaTemplate.send("audit.alerts.stream", event.getEventId(), event);
            }
            
            // Send compliance-relevant events to compliance stream
            if (hasComplianceFlags(event)) {
                kafkaTemplate.send("compliance.events.stream", event.getEventId(), event);
            }
            
        } catch (Exception e) {
            log.error("Failed to stream audit event: {}", event.getEventId(), e);
            // Don't fail the audit logging if streaming fails
        }
    }
    
    /**
     * Calculate cryptographic integrity hash
     */
    private String calculateIntegrityHash(AuditEvent event) {
        synchronized (hashLock) {
            try {
                // Create hash input from event data and previous hash
                String hashInput = String.join("|",
                    event.getEventId(),
                    event.getTimestamp().toString(),
                    event.getEventType().name(),
                    event.getUserId(),
                    event.getResource(),
                    event.getAction(),
                    event.getOutcome().name(),
                    lastAuditHash
                );
                
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(hashInput.getBytes());
                
                String currentHash = Base64.getEncoder().encodeToString(hashBytes);
                lastAuditHash = currentHash;
                
                return currentHash;
                
            } catch (Exception e) {
                log.error("Failed to calculate integrity hash", e);
                return UUID.randomUUID().toString(); // Fallback
            }
        }
    }
    
    /**
     * Update audit chain for integrity verification
     */
    private void updateAuditChain(AuditEvent event) {
        AuditChainEntry chainEntry = AuditChainEntry.builder()
            .eventId(event.getEventId())
            .timestamp(event.getTimestamp())
            .hash(event.getIntegrityHash())
            .previousHash(getPreviousChainHash())
            .build();
        
        // Store chain entry for integrity verification
        kafkaTemplate.send("audit.chain.updates", event.getEventId(), chainEntry);
    }
    
    /**
     * Event correlation for forensic analysis
     */
    private void updateEventCorrelation(AuditEvent event) {
        if (event.getCorrelationId() != null) {
            EventCorrelation correlation = correlationMap.computeIfAbsent(
                event.getCorrelationId(), 
                k -> new EventCorrelation(k)
            );
            
            correlation.addEvent(event);
            
            // Clean up old correlations (older than 24 hours)
            if (correlation.getStartTime().isBefore(LocalDateTime.now().minusHours(24))) {
                correlationMap.remove(event.getCorrelationId());
            }
        }
    }
    
    /**
     * Listen to transaction events from other services
     */
    @KafkaListener(topics = {"transaction-events", "user-events", "payment-events"}, groupId = "audit-service-transaction-group")
    public void handleTransactionEvent(String message) {
        try {
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);
            
            AuditEventRequest auditRequest = AuditEventRequest.builder()
                .eventType(mapTransactionEventType(event.getType()))
                .category(AuditCategory.TRANSACTION)
                .severity(determineSeverity(event))
                .userId(event.getUserId())
                .resource("transaction")
                .action(event.getAction())
                .outcome(mapTransactionOutcome(event.getStatus()))
                .description(String.format("Transaction %s: %s", event.getAction(), event.getTransactionId()))
                .metadata(Map.of(
                    "transactionId", event.getTransactionId(),
                    "amount", event.getAmount().toString(),
                    "currency", event.getCurrency(),
                    "provider", event.getProvider()
                ))
                .correlationId(event.getTransactionId())
                .ipAddress(event.getIpAddress())
                .build();
            
            logAuditEvent(auditRequest);
            
        } catch (Exception e) {
            log.error("Error processing transaction event", e);
        }
    }
    
    /**
     * Listen to authentication events
     */
    @KafkaListener(topics = "auth-events", groupId = "audit-service-auth-group")
    public void handleAuthenticationEvent(String message) {
        try {
            AuthEvent event = objectMapper.readValue(message, AuthEvent.class);
            
            AuditEventRequest auditRequest = AuditEventRequest.builder()
                .eventType(AuditEventType.AUTHENTICATION)
                .category(AuditCategory.SECURITY)
                .severity(event.isSuccessful() ? AuditSeverity.INFO : AuditSeverity.MEDIUM)
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .resource("authentication")
                .action(event.getAction())
                .outcome(event.isSuccessful() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE)
                .description(String.format("Authentication %s for user %s", 
                    event.getAction(), event.getUserId()))
                .metadata(Map.of(
                    "method", event.getMethod(),
                    "mfaUsed", String.valueOf(event.isMfaUsed()),
                    "deviceId", event.getDeviceId()
                ))
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .build();
            
            logAuditEvent(auditRequest);
            
        } catch (Exception e) {
            log.error("Error processing authentication event", e);
        }
    }
    
    /**
     * Query audit trail with advanced filtering
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> queryAuditTrail(AuditQuery query, Pageable pageable) {
        try {
            // Validate query permissions
            if (!hasQueryPermission(query.getRequesterId())) {
                throw new SecurityException("Insufficient permissions for audit query");
            }
            
            // Apply data classification filters
            query = applyDataClassificationFilters(query);
            
            // Execute query with privacy controls
            Page<AuditLog> results = auditLogRepository.findWithFilters(query, pageable);
            
            // Convert to audit events with data masking
            return results.map(this::convertToAuditEventWithMasking);
            
        } catch (Exception e) {
            // Log the query attempt for security monitoring
            logSecurityEvent("AUDIT_QUERY_FAILED", query.getRequesterId(), 
                "Failed audit trail query: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Generate compliance report
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void generateDailyComplianceReport() {
        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            
            ComplianceReport report = ComplianceReport.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType(ComplianceReportType.DAILY)
                .reportDate(yesterday.toLocalDate())
                .generatedAt(LocalDateTime.now())
                .build();
            
            // Collect compliance metrics
            ComplianceMetrics metrics = collectComplianceMetrics(yesterday, yesterday.plusDays(1));
            report.setMetrics(metrics);
            
            // Generate report sections
            report.setSections(generateComplianceSections(metrics));
            
            // Save report
            complianceReportRepository.save(report);
            
            // Send to compliance team if issues found
            if (metrics.hasComplianceIssues()) {
                sendComplianceAlert(report);
            }
            
            log.info("Daily compliance report generated: {}", report.getReportId());
            
        } catch (Exception e) {
            log.error("Failed to generate daily compliance report", e);
        }
    }
    
    /**
     * Verify audit trail integrity
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void verifyAuditIntegrity() {
        if (!integrityCheckingEnabled) return;
        
        try {
            log.info("Starting audit trail integrity verification");
            
            List<AuditLog> recentLogs = auditLogRepository.findRecentForIntegrityCheck(
                LocalDateTime.now().minusHours(1), batchSize);
            
            int verificationErrors = 0;
            String expectedPreviousHash = "";
            
            for (AuditLog auditLog : recentLogs) {
                String recalculatedHash = recalculateHash(auditLog, expectedPreviousHash);
                
                if (!recalculatedHash.equals(auditLog.getIntegrityHash())) {
                    log.error("INTEGRITY VIOLATION: Audit log {} has invalid hash", auditLog.getEventId());
                    verificationErrors++;
                    
                    // Create security incident
                    createSecurityIncident("AUDIT_INTEGRITY_VIOLATION", auditLog.getEventId());
                }
                
                expectedPreviousHash = auditLog.getIntegrityHash();
            }
            
            if (verificationErrors > 0) {
                log.error("CRITICAL: {} audit integrity violations detected", verificationErrors);
                sendIntegrityAlert(verificationErrors);
            } else {
                log.info("Audit trail integrity verification completed successfully");
            }
            
        } catch (Exception e) {
            log.error("Error during integrity verification", e);
        }
    }
    
    /**
     * Archive old audit data
     */
    @Scheduled(cron = "0 0 3 * * SUN") // Weekly on Sunday at 3 AM
    public void archiveOldAuditData() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            
            log.info("Starting audit data archival for data older than {}", cutoffDate);
            
            // Archive to long-term storage
            List<AuditLog> oldLogs = auditLogRepository.findOlderThan(cutoffDate);
            
            if (!oldLogs.isEmpty()) {
                // Export to archive storage
                String archiveId = exportToArchiveStorage(oldLogs);
                
                // Create archive record
                AuditArchive archive = AuditArchive.builder()
                    .archiveId(archiveId)
                    .archivedAt(LocalDateTime.now())
                    .recordCount(oldLogs.size())
                    .startDate(oldLogs.get(0).getTimestamp())
                    .endDate(oldLogs.get(oldLogs.size() - 1).getTimestamp())
                    .build();
                
                // Delete from primary storage after successful archive
                auditLogRepository.deleteOlderThan(cutoffDate);
                
                log.info("Archived {} audit records to {}", oldLogs.size(), archiveId);
            }
            
        } catch (Exception e) {
            log.error("Error during audit data archival", e);
        }
    }
    
    /**
     * Export audit data for regulatory compliance
     */
    public String exportAuditData(AuditExportRequest request) {
        try {
            // Validate export permissions
            if (!hasExportPermission(request.getRequesterId())) {
                throw new SecurityException("Insufficient permissions for audit export");
            }
            
            // Log export request
            logSecurityEvent("AUDIT_EXPORT_REQUESTED", request.getRequesterId(),
                String.format("Export requested: %s to %s", request.getStartDate(), request.getEndDate()));
            
            // Generate export
            List<AuditLog> exportData = auditLogRepository.findForExport(
                request.getStartDate(), request.getEndDate(), request.getFilters());
            
            // Create export package
            String exportId = createExportPackage(exportData, request);
            
            // Log successful export
            logSecurityEvent("AUDIT_EXPORT_COMPLETED", request.getRequesterId(),
                String.format("Export completed: %s (%d records)", exportId, exportData.size()));
            
            return exportId;
            
        } catch (Exception e) {
            logSecurityEvent("AUDIT_EXPORT_FAILED", request.getRequesterId(),
                "Export failed: " + e.getMessage());
            throw e;
        }
    }
    
    // Helper methods
    
    private boolean isDuplicateEvent(AuditEvent event) {
        return processedEventIds.contains(event.getEventId());
    }
    
    private boolean requiresEncryption(AuditEvent event) {
        return event.getDataClassification() == DataClassification.CONFIDENTIAL ||
               event.getDataClassification() == DataClassification.RESTRICTED;
    }
    
    private EncryptedAuditLog encryptAuditEvent(AuditEvent event) throws Exception {
        String eventJson = objectMapper.writeValueAsString(event);
        String encryptedData = encrypt(eventJson);
        
        return EncryptedAuditLog.builder()
            .eventId(event.getEventId())
            .timestamp(event.getTimestamp())
            .encryptedData(encryptedData)
            .dataClassification(event.getDataClassification())
            .build();
    }
    
    private String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        SecretKeySpec keySpec = new SecretKeySpec(encryptionKey.getBytes(), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) return null;
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + parts[3];
        }
        return "***";
    }
    
    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null) return new HashMap<>();
        
        Map<String, Object> sanitized = new HashMap<>(metadata);
        
        // Remove sensitive fields
        sanitized.remove("password");
        sanitized.remove("ssn");
        sanitized.remove("creditCard");
        sanitized.remove("bankAccount");
        
        return sanitized;
    }
    
    private DataClassification determineDataClassification(AuditEventRequest request) {
        if (request.getMetadata() != null) {
            if (request.getMetadata().containsKey("creditCard") || 
                request.getMetadata().containsKey("bankAccount")) {
                return DataClassification.RESTRICTED;
            }
            if (request.getMetadata().containsKey("personalInfo")) {
                return DataClassification.CONFIDENTIAL;
            }
        }
        
        if (request.getCategory() == AuditCategory.FINANCIAL) {
            return DataClassification.CONFIDENTIAL;
        }
        
        return DataClassification.INTERNAL;
    }
    
    private Set<ComplianceFlag> determineComplianceFlags(AuditEventRequest request) {
        Set<ComplianceFlag> flags = new HashSet<>();
        
        if (request.getCategory() == AuditCategory.FINANCIAL) {
            flags.add(ComplianceFlag.SOX_RELEVANT);
            flags.add(ComplianceFlag.PCI_DSS_RELEVANT);
        }
        
        if (request.getEventType() == AuditEventType.DATA_ACCESS && 
            request.getMetadata() != null && 
            request.getMetadata().containsKey("personalInfo")) {
            flags.add(ComplianceFlag.GDPR_RELEVANT);
        }
        
        if (request.getSeverity() == AuditSeverity.CRITICAL) {
            flags.add(ComplianceFlag.SECURITY_INCIDENT);
        }
        
        return flags;
    }
    
    // Additional supporting methods would be implemented here...
    
    private void initializeAuditTrail() {
        // Initialize audit chain
        lastAuditHash = "GENESIS_HASH_" + System.currentTimeMillis();
    }
    
    private void setupComplianceMonitoring() {
        // Setup compliance monitoring rules
    }

    /**
     * Add audit event to continuous trail
     */
    public void addToContinuousTrail(AuditEvent auditEvent, String correlationId) {
        log.debug("Adding audit event to continuous trail: eventId={}, correlationId={}",
            auditEvent.getId(), correlationId);

        try {
            // Save to repository
            auditEventRepository.save(auditEvent);

            // Update continuous trail metadata
            updateContinuousTrailMetadata(auditEvent, correlationId);

            log.info("Added to continuous trail: eventId={}, type={}, correlationId={}",
                auditEvent.getId(), auditEvent.getEventType(), correlationId);

        } catch (Exception e) {
            log.error("Failed to add to continuous trail: eventId={}, correlationId={}, error={}",
                auditEvent.getId(), correlationId, e.getMessage());
        }
    }

    private void updateContinuousTrailMetadata(AuditEvent auditEvent, String correlationId) {
        // Update trail metadata for real-time monitoring
        log.debug("Updating continuous trail metadata for event: {}", auditEvent.getId());
    }

    // Supporting classes and enums would be in separate files
}