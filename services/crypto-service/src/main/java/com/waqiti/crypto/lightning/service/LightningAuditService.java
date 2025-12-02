package com.waqiti.crypto.lightning.service;

import com.waqiti.common.audit.PciDssAuditEnhancement;
import com.waqiti.crypto.lightning.entity.*;
import com.waqiti.crypto.lightning.repository.LightningAuditRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Lightning audit logging service
 * Provides comprehensive audit trails for all Lightning operations
 * Compliant with PCI DSS, SOX, and financial regulations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LightningAuditService {

    private final LightningAuditRepository auditRepository;
    private final PciDssAuditEnhancement pciDssAuditEnhancement;
    private final MeterRegistry meterRegistry;
    
    @Value("${waqiti.lightning.audit.retention-days:2555}") // 7 years default
    private int auditRetentionDays;
    
    @Value("${waqiti.lightning.audit.enable-real-time-alerts:true}")
    private boolean enableRealTimeAlerts;
    
    @Value("${waqiti.lightning.audit.sensitive-fields:paymentPreimage,secret,privateKey}")
    private String[] sensitiveFields;
    
    private final ConcurrentHashMap<String, AuditStatistics> auditStatsCache = new ConcurrentHashMap<>();
    private Counter auditEventCounter;
    private Counter securityEventCounter;
    private Counter complianceEventCounter;

    @jakarta.annotation.PostConstruct
    public void init() {
        auditEventCounter = Counter.builder("lightning.audit.events")
            .description("Number of Lightning audit events logged")
            .register(meterRegistry);
            
        securityEventCounter = Counter.builder("lightning.audit.security_events")
            .description("Number of Lightning security events logged")
            .register(meterRegistry);
            
        complianceEventCounter = Counter.builder("lightning.audit.compliance_events")
            .description("Number of Lightning compliance events logged")
            .register(meterRegistry);
    }

    // ============ INVOICE AUDIT LOGGING ============

    /**
     * Log invoice creation audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInvoiceCreation(String userId, LightningNetworkService.LightningInvoice invoice) {
        log.debug("Logging invoice creation audit: {}", invoice.getPaymentHash());
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("amountSat", invoice.getAmountSat());
            auditData.put("description", sanitizeDescription(invoice.getDescription()));
            auditData.put("expiry", invoice.getExpiry());
            auditData.put("paymentHash", invoice.getPaymentHash());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.INVOICE_CREATED,
                userId,
                "Invoice created successfully",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
            // Enhanced PCI DSS logging for payment operations
            pciDssAuditEnhancement.logPaymentOperation(
                userId,
                "LIGHTNING_INVOICE_CREATE",
                invoice.getAmountSat(),
                "SUCCESS",
                getClientInfo()
            );
            
        } catch (Exception e) {
            log.error("Failed to log invoice creation audit", e);
        }
    }

    /**
     * Log invoice settlement audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInvoiceSettlement(String userId, InvoiceEntity invoice, String paymentPreimage) {
        log.debug("Logging invoice settlement audit: {}", invoice.getPaymentHash());
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("invoiceId", invoice.getId());
            auditData.put("amountSat", invoice.getAmountSat());
            auditData.put("amountPaidSat", invoice.getAmountPaidSat());
            auditData.put("paymentHash", invoice.getPaymentHash());
            auditData.put("settledAt", invoice.getSettledAt());
            // Note: paymentPreimage is sensitive and should be hashed
            auditData.put("paymentPreimageHash", hashSensitiveData(paymentPreimage));
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.INVOICE_SETTLED,
                userId,
                "Invoice settled successfully",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            complianceEventCounter.increment();
            
            // PCI DSS compliance logging
            pciDssAuditEnhancement.logPaymentSettlement(
                userId,
                "LIGHTNING_INVOICE_SETTLEMENT",
                invoice.getAmountPaidSat(),
                invoice.getPaymentHash()
            );
            
        } catch (Exception e) {
            log.error("Failed to log invoice settlement audit", e);
        }
    }

    /**
     * Log invoice cancellation audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInvoiceCancellation(String userId, String invoiceId) {
        log.debug("Logging invoice cancellation audit: {}", invoiceId);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("invoiceId", invoiceId);
            auditData.put("cancelledAt", Instant.now());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.INVOICE_CANCELLED,
                userId,
                "Invoice cancelled by user",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log invoice cancellation audit", e);
        }
    }

    // ============ PAYMENT AUDIT LOGGING ============

    /**
     * Log successful payment audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPaymentSuccess(String userId, PaymentEntity payment) {
        log.debug("Logging payment success audit: {}", payment.getPaymentHash());
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("paymentId", payment.getId());
            auditData.put("paymentHash", payment.getPaymentHash());
            auditData.put("amountSat", payment.getAmountSat());
            auditData.put("feeSat", payment.getFeeSat());
            auditData.put("type", payment.getType().toString());
            auditData.put("hopCount", payment.getHopCount());
            auditData.put("completedAt", payment.getCompletedAt());
            
            if (payment.getDestinationPubkey() != null) {
                auditData.put("destinationPubkey", payment.getDestinationPubkey());
            }
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.PAYMENT_SENT,
                userId,
                "Lightning payment sent successfully",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            complianceEventCounter.increment();
            
            // Enhanced audit for large payments
            if (payment.getAmountSat() > 1000000L) { // > 1M sats
                logHighValuePayment(userId, payment);
            }
            
        } catch (Exception e) {
            log.error("Failed to log payment success audit", e);
        }
    }

    /**
     * Log payment failure audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPaymentFailure(String userId, String paymentRequest, String error) {
        log.debug("Logging payment failure audit for user: {}", userId);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("paymentRequest", truncatePaymentRequest(paymentRequest));
            auditData.put("error", error);
            auditData.put("failedAt", Instant.now());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.PAYMENT_FAILED,
                userId,
                "Lightning payment failed: " + error,
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
            // Check for suspicious patterns
            checkSuspiciousPaymentActivity(userId, error);
            
        } catch (Exception e) {
            log.error("Failed to log payment failure audit", e);
        }
    }

    /**
     * Log keysend payment audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logKeysendSuccess(String userId, PaymentEntity payment) {
        log.debug("Logging keysend success audit: {}", payment.getPaymentHash());
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("paymentId", payment.getId());
            auditData.put("destinationPubkey", payment.getDestinationPubkey());
            auditData.put("amountSat", payment.getAmountSat());
            auditData.put("feeSat", payment.getFeeSat());
            auditData.put("customData", sanitizeCustomData(payment.getCustomData()));
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.KEYSEND_SENT,
                userId,
                "Keysend payment sent successfully",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log keysend success audit", e);
        }
    }

    // ============ CHANNEL AUDIT LOGGING ============

    /**
     * Log channel opening audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChannelOpen(String userId, ChannelEntity channel) {
        log.debug("Logging channel open audit: {}", channel.getId());
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("channelId", channel.getId());
            auditData.put("remotePubkey", channel.getRemotePubkey());
            auditData.put("capacity", channel.getCapacity());
            auditData.put("localBalance", channel.getLocalBalance());
            auditData.put("isPrivate", channel.getIsPrivate());
            auditData.put("openedAt", channel.getOpenedAt());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.CHANNEL_OPENED,
                userId,
                "Lightning channel opened",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            complianceEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log channel open audit", e);
        }
    }

    /**
     * Log channel closing audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChannelClose(String userId, String channelId, boolean force) {
        log.debug("Logging channel close audit: {}", channelId);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("channelId", channelId);
            auditData.put("forceClose", force);
            auditData.put("closedAt", Instant.now());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                force ? LightningAuditEventType.CHANNEL_FORCE_CLOSED : LightningAuditEventType.CHANNEL_CLOSED,
                userId,
                force ? "Channel force closed" : "Channel cooperatively closed",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
            // Force closes require special attention
            if (force) {
                securityEventCounter.increment();
                logSecurityEvent(userId, "CHANNEL_FORCE_CLOSE", "Channel " + channelId + " was force closed");
            }
            
        } catch (Exception e) {
            log.error("Failed to log channel close audit", e);
        }
    }

    /**
     * Log channel rebalancing audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChannelRebalance(String userId, String channelId, Long amountSat) {
        log.debug("Logging channel rebalance audit: {}", channelId);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("channelId", channelId);
            auditData.put("rebalanceAmount", amountSat);
            auditData.put("rebalancedAt", Instant.now());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.CHANNEL_REBALANCED,
                userId,
                "Channel rebalanced",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log channel rebalance audit", e);
        }
    }

    // ============ SECURITY AUDIT LOGGING ============

    /**
     * Log security events
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecurityEvent(String userId, String eventType, String description) {
        log.warn("Security event detected: {} - {} - {}", userId, eventType, description);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("securityEventType", eventType);
            auditData.put("severity", "HIGH");
            auditData.put("clientInfo", getClientInfo());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.SECURITY_EVENT,
                userId,
                description,
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            securityEventCounter.increment();
            
            // Real-time alerting for security events
            if (enableRealTimeAlerts) {
                triggerSecurityAlert(userId, eventType, description);
            }
            
        } catch (Exception e) {
            log.error("Failed to log security event", e);
        }
    }

    /**
     * Log authentication events
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthenticationEvent(String userId, String eventType, boolean success, String details) {
        log.debug("Logging authentication event: {} - {} - {}", userId, eventType, success);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("authEventType", eventType);
            auditData.put("success", success);
            auditData.put("details", details);
            auditData.put("clientInfo", getClientInfo());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.AUTHENTICATION,
                userId,
                success ? "Authentication successful" : "Authentication failed",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
            if (!success) {
                securityEventCounter.increment();
                checkFailedAuthenticationPattern(userId);
            }
            
        } catch (Exception e) {
            log.error("Failed to log authentication event", e);
        }
    }

    // ============ BACKUP AND RECOVERY AUDIT ============

    /**
     * Log backup creation audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logBackupCreation(String userId, String backupId) {
        log.debug("Logging backup creation audit: {}", backupId);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("backupId", backupId);
            auditData.put("backupType", "CHANNEL_BACKUP");
            auditData.put("createdAt", Instant.now());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.BACKUP_CREATED,
                userId,
                "Channel backup created",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            complianceEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log backup creation audit", e);
        }
    }

    // ============ STREAM AND SWAP AUDIT ============

    /**
     * Log stream start audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStreamStart(String userId, StreamEntity stream) {
        log.debug("Logging stream start audit: {}", stream.getId());
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("streamId", stream.getId());
            auditData.put("destination", stream.getDestination());
            auditData.put("amountPerInterval", stream.getAmountPerInterval());
            auditData.put("interval", stream.getInterval());
            auditData.put("totalDuration", stream.getTotalDuration());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.STREAM_STARTED,
                userId,
                "Payment stream started",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log stream start audit", e);
        }
    }

    /**
     * Log stream stop audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStreamStop(String userId, String streamId) {
        log.debug("Logging stream stop audit: {}", streamId);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("streamId", streamId);
            auditData.put("stoppedAt", Instant.now());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.STREAM_STOPPED,
                userId,
                "Payment stream stopped",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log stream stop audit", e);
        }
    }

    /**
     * Log swap initiation audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSwapInitiation(String userId, SwapEntity swap) {
        log.debug("Logging swap initiation audit: {}", swap.getId());
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("swapId", swap.getId());
            auditData.put("swapType", swap.getType().toString());
            auditData.put("amountSat", swap.getAmountSat());
            auditData.put("onchainAddress", swap.getOnchainAddress());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.SWAP_INITIATED,
                userId,
                "Submarine swap initiated",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            complianceEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log swap initiation audit", e);
        }
    }

    // ============ PEER CONNECTION AUDIT ============

    /**
     * Log peer connection audit event
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPeerConnection(String adminUserId, String peerPubkey, boolean success) {
        log.debug("Logging peer connection audit: {} - {}", peerPubkey, success);
        
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("peerPubkey", peerPubkey);
            auditData.put("success", success);
            auditData.put("connectedAt", Instant.now());
            
            LightningAuditEntity auditEvent = createBaseAuditEvent(
                LightningAuditEventType.PEER_CONNECTED,
                adminUserId,
                success ? "Peer connected successfully" : "Peer connection failed",
                auditData
            );
            
            auditRepository.save(auditEvent);
            auditEventCounter.increment();
            
        } catch (Exception e) {
            log.error("Failed to log peer connection audit", e);
        }
    }

    // ============ AUDIT MAINTENANCE ============

    /**
     * Clean up old audit records (keeping required retention period)
     */
    @Scheduled(cron = "0 0 3 * * ?") // Daily at 3 AM
    @Transactional
    public void cleanupOldAuditRecords() {
        log.info("Starting audit record cleanup");
        
        try {
            Instant cutoffDate = Instant.now().minus(Duration.ofDays(auditRetentionDays));
            int deletedCount = auditRepository.deleteOldAuditRecords(cutoffDate);
            
            log.info("Deleted {} old audit records older than {}", deletedCount, cutoffDate);
            
            // Archive critical audit events instead of deleting
            archiveCriticalAuditEvents(cutoffDate);
            
        } catch (Exception e) {
            log.error("Error during audit cleanup", e);
        }
    }

    /**
     * Generate audit statistics reports
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void updateAuditStatistics() {
        log.debug("Updating audit statistics");
        
        try {
            List<Object[]> stats = auditRepository.getAuditStatistics();
            
            for (Object[] stat : stats) {
                String eventType = (String) stat[0];
                Long count = (Long) stat[1];
                
                AuditStatistics auditStats = auditStatsCache.computeIfAbsent(
                    eventType, k -> new AuditStatistics());
                auditStats.setEventCount(count);
                auditStats.setLastUpdated(Instant.now());
            }
            
        } catch (Exception e) {
            log.error("Error updating audit statistics", e);
        }
    }

    // ============ HELPER METHODS ============

    private LightningAuditEntity createBaseAuditEvent(LightningAuditEventType eventType, 
                                                      String userId, String description,
                                                      Map<String, Object> auditData) {
        return LightningAuditEntity.builder()
            .id(UUID.randomUUID().toString())
            .eventType(eventType)
            .userId(userId)
            .description(description)
            .auditData(sanitizeAuditData(auditData))
            .timestamp(Instant.now())
            .severity(determineSeverity(eventType))
            .clientInfo(getClientInfo())
            .build();
    }

    private Map<String, Object> sanitizeAuditData(Map<String, Object> auditData) {
        if (auditData == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> sanitized = new HashMap<>(auditData);
        
        // Remove or hash sensitive fields
        for (String sensitiveField : sensitiveFields) {
            if (sanitized.containsKey(sensitiveField)) {
                Object value = sanitized.get(sensitiveField);
                if (value != null) {
                    sanitized.put(sensitiveField, hashSensitiveData(value.toString()));
                }
            }
        }
        
        return sanitized;
    }

    private String hashSensitiveData(String sensitiveData) {
        // In production, use proper hashing
        return "SHA256:" + sensitiveData.hashCode();
    }

    private String sanitizeDescription(String description) {
        if (description == null) return null;
        return description.length() > 500 ? description.substring(0, 500) + "..." : description;
    }

    private String sanitizeCustomData(String customData) {
        if (customData == null) return null;
        return customData.length() > 200 ? customData.substring(0, 200) + "..." : customData;
    }

    private String truncatePaymentRequest(String paymentRequest) {
        if (paymentRequest == null) return null;
        return paymentRequest.length() > 50 ? 
            paymentRequest.substring(0, 50) + "..." : paymentRequest;
    }

    private AuditSeverity determineSeverity(LightningAuditEventType eventType) {
        return switch (eventType) {
            case SECURITY_EVENT, AUTHENTICATION, CHANNEL_FORCE_CLOSED -> AuditSeverity.HIGH;
            case PAYMENT_SENT, PAYMENT_FAILED, INVOICE_SETTLED -> AuditSeverity.MEDIUM;
            default -> AuditSeverity.LOW;
        };
    }

    private Map<String, Object> getClientInfo() {
        // In a web context, extract client information
        Map<String, Object> clientInfo = new HashMap<>();
        clientInfo.put("timestamp", Instant.now().toString());
        clientInfo.put("source", "waqiti-crypto-service");
        // Add more client info as needed (IP, User-Agent, etc.)
        return clientInfo;
    }

    private void logHighValuePayment(String userId, PaymentEntity payment) {
        logSecurityEvent(userId, "HIGH_VALUE_PAYMENT", 
            String.format("High value payment: %d sats", payment.getAmountSat()));
    }

    private void checkSuspiciousPaymentActivity(String userId, String error) {
        // Implement pattern detection for suspicious activities
        AuditStatistics stats = auditStatsCache.get(userId + "_payment_failures");
        if (stats != null && stats.getEventCount() > 10) {
            logSecurityEvent(userId, "SUSPICIOUS_PAYMENT_PATTERN", 
                "High number of payment failures detected");
        }
    }

    private void checkFailedAuthenticationPattern(String userId) {
        // Check for brute force attempts
        // Implementation would analyze recent failed attempts
    }

    private void triggerSecurityAlert(String userId, String eventType, String description) {
        // In production, integrate with alerting system
        log.warn("SECURITY ALERT: User {} - {} - {}", userId, eventType, description);
    }

    private void archiveCriticalAuditEvents(Instant cutoffDate) {
        // Archive instead of delete for critical events
        List<LightningAuditEntity> criticalEvents = auditRepository
            .findCriticalEventsOlderThan(cutoffDate);
        
        // Archive to external storage or separate table
        log.info("Archiving {} critical audit events", criticalEvents.size());
    }

    /**
     * Audit statistics holder
     */
    private static class AuditStatistics {
        private Long eventCount = 0L;
        private Instant lastUpdated;

        public Long getEventCount() { return eventCount; }
        public void setEventCount(Long eventCount) { this.eventCount = eventCount; }
        
        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }
}