package com.waqiti.payment.core.service;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.entity.PaymentAuditLog;
import com.waqiti.payment.core.entity.PaymentTransaction;
import com.waqiti.payment.core.repository.PaymentAuditRepository;
import com.waqiti.payment.core.repository.PaymentTransactionRepository;
import com.waqiti.common.audit.PciDssAuditEnhancement;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Industrial-strength payment audit service with comprehensive tracking
 * 
 * Features:
 * - Immutable audit logs with cryptographic integrity
 * - PCI DSS compliant data handling
 * - Forensic analysis capabilities
 * - Compliance reporting (SOX, GDPR, AML)
 * - Real-time analytics and metrics
 * - Anomaly detection
 * - Audit trail search and export
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentAuditService {

    private final PaymentAuditRepository auditRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PciDssAuditEnhancement pciAuditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Value("${audit.encryption.key:#{null}}")
    private String encryptionKey;

    @jakarta.annotation.PostConstruct
    private void validateConfiguration() {
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: audit.encryption.key must be configured via environment variable. " +
                "Never use default encryption keys in production. " +
                "Set AUDIT_ENCRYPTION_KEY environment variable with a cryptographically secure 32-byte key."
            );
        }
        if (encryptionKey.length() < 32) {
            throw new IllegalStateException(
                "SECURITY ERROR: audit.encryption.key must be at least 32 bytes (256 bits) for AES-256 encryption"
            );
        }
        log.info("PaymentAuditService encryption key validated successfully");
    }
    
    @Value("${audit.retention.days:2555}")
    private int retentionDays; // 7 years for financial records
    
    @Value("${audit.enable-forensics:true}")
    private boolean enableForensics;
    
    // Cache for recent audits
    private final Map<String, PaymentAuditLog> recentAudits = new ConcurrentHashMap<>();
    private final Map<String, PaymentAnalytics> analyticsCache = new ConcurrentHashMap<>();

    /**
     * Audits payment with comprehensive tracking
     */
    @Transactional
    @Async
    public void auditPayment(PaymentRequest request, PaymentResult result) {
        try {
            log.info("Auditing payment: paymentId={}, status={}, amount={}", 
                    request.getPaymentId(), result.getStatus(), request.getAmount());
            
            // Create immutable audit log
            PaymentAuditLog auditLog = createAuditLog(request, result);
            
            // Generate cryptographic hash for integrity
            auditLog.setIntegrityHash(generateIntegrityHash(auditLog));
            
            // Encrypt sensitive data
            if (shouldEncryptSensitiveData(request)) {
                auditLog.setEncryptedData(encryptSensitiveData(request));
            }
            
            // Store audit log
            auditRepository.save(auditLog);
            
            // Store transaction record
            PaymentTransaction transaction = createTransactionRecord(request, result);
            transactionRepository.save(transaction);
            
            // Update caches
            updateCaches(auditLog, transaction);
            
            // PCI DSS audit
            pciAuditService.auditPaymentOperation(
                request.getPaymentId().toString(),
                "PAYMENT_PROCESSED",
                request.getFromUserId()
            );
            
            // Detect anomalies
            detectAnomalies(request, result);
            
            // Update metrics
            updateMetrics(request, result);
            
            // Compliance checks
            performComplianceChecks(request, result);
            
        } catch (Exception e) {
            log.error("Failed to audit payment: ", e);
            // Audit failures must not affect payment processing
            // Send to backup audit queue
            sendToBackupAuditQueue(request, result, e);
        }
    }
    
    private PaymentAuditLog createAuditLog(PaymentRequest request, PaymentResult result) {
        return PaymentAuditLog.builder()
            .auditId(UUID.randomUUID())
            .paymentId(request.getPaymentId())
            .transactionId(result.getTransactionId())
            .eventType("PAYMENT_" + result.getStatus())
            .eventTimestamp(LocalDateTime.now())
            .userId(request.getFromUserId())
            .recipientId(request.getToUserId())
            .amount(request.getAmount())
            .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
            .paymentType(request.getType().toString())
            .providerType(request.getProviderType().toString())
            .status(result.getStatus().toString())
            .errorMessage(result.getErrorMessage())
            .processingTime(calculateProcessingTime(request, result))
            .ipAddress(extractIpAddress(request))
            .userAgent(extractUserAgent(request))
            .deviceId(extractDeviceId(request))
            .sessionId(extractSessionId(request))
            .metadata(serializeMetadata(request.getMetadata()))
            .providerResponse(serializeMetadata(result.getProviderResponse()))
            .build();
    }

    public void auditRefund(RefundRequest request, PaymentResult result) {
        try {
            log.info("Auditing refund: refundId={}, originalPaymentId={}, amount={}", 
                    request.getRefundId(), request.getOriginalPaymentId(), request.getAmount());
            
            // Store refund result
            paymentHistory.put(request.getRefundId().toString(), result);
            
            logRefundAuditEntry(request, result);
            
        } catch (Exception e) {
            log.error("Failed to audit refund: ", e);
        }
    }

    /**
     * Retrieves payment by ID with audit trail
     */
    @Transactional(readOnly = true)
    public PaymentResult getPaymentById(String paymentId) {
        // Log access for audit
        logDataAccess("PAYMENT_RETRIEVAL", paymentId);
        
        // Check cache first
        PaymentAuditLog cached = recentAudits.get(paymentId);
        if (cached != null) {
            return convertToPaymentResult(cached);
        }
        
        // Query from database
        Optional<PaymentTransaction> transaction = 
            transactionRepository.findByPaymentId(UUID.fromString(paymentId));
        
        return transaction.map(this::convertToPaymentResult).orElse(null);
    }

    /**
     * Retrieves payment history with filtering and pagination
     */
    @Transactional(readOnly = true)
    public List<PaymentResult> getPaymentHistory(String userId, PaymentHistoryFilter filter) {
        // Log access for compliance
        logDataAccess("PAYMENT_HISTORY_RETRIEVAL", userId);
        
        // Build query with filters
        PageRequest pageRequest = PageRequest.of(
            filter != null ? filter.getPage() : 0,
            filter != null ? filter.getSize() : 100,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        Page<PaymentTransaction> transactions;
        
        if (filter != null && filter.hasDateRange()) {
            transactions = transactionRepository.findByUserIdAndDateRange(
                userId,
                filter.getStartDate(),
                filter.getEndDate(),
                pageRequest
            );
        } else {
            transactions = transactionRepository.findByUserId(userId, pageRequest);
        }
        
        return transactions.stream()
            .map(this::convertToPaymentResult)
            .filter(payment -> filter == null || filter.matches(payment))
            .collect(Collectors.toList());
    }

    /**
     * Generates comprehensive payment analytics
     */
    @Transactional(readOnly = true)
    public PaymentAnalytics getPaymentAnalytics(String userId, AnalyticsFilter filter) {
        // Check cache
        String cacheKey = userId + ":" + (filter != null ? filter.hashCode() : "all");
        PaymentAnalytics cached = analyticsCache.get(cacheKey);
        if (cached != null && !isCacheExpired(cached)) {
            return cached;
        }
        
        // Generate analytics
        LocalDateTime startDate = filter != null ? filter.getStartDate() : 
            LocalDateTime.now().minus(90, ChronoUnit.DAYS);
        LocalDateTime endDate = filter != null ? filter.getEndDate() : 
            LocalDateTime.now();
        
        // Query aggregated data
        Map<String, Object> stats = transactionRepository.getAggregatedStats(
            userId, startDate, endDate
        );
        
        PaymentAnalytics analytics = PaymentAnalytics.builder()
            .userId(userId)
            .periodStart(startDate)
            .periodEnd(endDate)
            .totalPayments((Long) stats.get("totalCount"))
            .successfulPayments((Long) stats.get("successCount"))
            .failedPayments((Long) stats.get("failedCount"))
            .pendingPayments((Long) stats.get("pendingCount"))
            .totalAmount((BigDecimal) stats.get("totalAmount"))
            .averageAmount((BigDecimal) stats.get("averageAmount"))
            .medianAmount((BigDecimal) stats.get("medianAmount"))
            .largestPayment((BigDecimal) stats.get("maxAmount"))
            .smallestPayment((BigDecimal) stats.get("minAmount"))
            .totalFees((BigDecimal) stats.get("totalFees"))
            .averageProcessingTime((Long) stats.get("avgProcessingTime"))
            .paymentsByType(getPaymentsByType(userId, startDate, endDate))
            .paymentsByStatus(getPaymentsByStatus(userId, startDate, endDate))
            .dailyVolume(getDailyVolume(userId, startDate, endDate))
            .hourlyDistribution(getHourlyDistribution(userId, startDate, endDate))
            .topRecipients(getTopRecipients(userId, startDate, endDate))
            .failureReasons(getFailureReasons(userId, startDate, endDate))
            .fraudMetrics(getFraudMetrics(userId, startDate, endDate))
            .complianceMetrics(getComplianceMetrics(userId, startDate, endDate))
            .generatedAt(LocalDateTime.now())
            .build();
        
        // Cache the result
        analyticsCache.put(cacheKey, analytics);
        
        return analytics;
    }

    /**
     * Performs forensic analysis on payment patterns
     */
    public ForensicAnalysisResult performForensicAnalysis(String userId, 
                                                          LocalDateTime startDate,
                                                          LocalDateTime endDate) {
        if (!enableForensics) {
            return ForensicAnalysisResult.notEnabled();
        }
        
        log.info("Performing forensic analysis for user: {}", userId);
        
        List<PaymentAuditLog> auditLogs = auditRepository.findByUserIdAndDateRange(
            userId, startDate, endDate
        );
        
        return ForensicAnalysisResult.builder()
            .userId(userId)
            .analysisId(UUID.randomUUID().toString())
            .startDate(startDate)
            .endDate(endDate)
            .totalTransactions(auditLogs.size())
            .suspiciousPatterns(detectSuspiciousPatterns(auditLogs))
            .velocityAnomalies(detectVelocityAnomalies(auditLogs))
            .amountAnomalies(detectAmountAnomalies(auditLogs))
            .geographicAnomalies(detectGeographicAnomalies(auditLogs))
            .deviceAnomalies(detectDeviceAnomalies(auditLogs))
            .timeAnomalies(detectTimeAnomalies(auditLogs))
            .relatedAccounts(findRelatedAccounts(auditLogs))
            .riskScore(calculateForensicRiskScore(auditLogs))
            .recommendations(generateForensicRecommendations(auditLogs))
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generates compliance reports
     */
    public ComplianceReport generateComplianceReport(ComplianceType type,
                                                     LocalDateTime startDate,
                                                     LocalDateTime endDate) {
        log.info("Generating {} compliance report from {} to {}", type, startDate, endDate);
        
        return switch (type) {
            case SOX -> generateSOXReport(startDate, endDate);
            case GDPR -> generateGDPRReport(startDate, endDate);
            case PCI_DSS -> generatePCIDSSReport(startDate, endDate);
            case AML_BSA -> generateAMLBSAReport(startDate, endDate);
            case FATF -> generateFATFReport(startDate, endDate);
            default -> throw new IllegalArgumentException("Unsupported compliance type: " + type);
        };
    }
    
    /**
     * Exports audit trail for regulatory review
     */
    public AuditTrailExport exportAuditTrail(ExportRequest request) {
        log.info("Exporting audit trail: {}", request);
        
        List<PaymentAuditLog> auditLogs = auditRepository.findByFilters(
            request.getUserIds(),
            request.getStartDate(),
            request.getEndDate(),
            request.getEventTypes(),
            request.getStatuses()
        );
        
        // Verify integrity of audit logs
        List<PaymentAuditLog> verifiedLogs = auditLogs.stream()
            .filter(this::verifyIntegrity)
            .collect(Collectors.toList());
        
        if (verifiedLogs.size() < auditLogs.size()) {
            log.error("INTEGRITY WARNING: {} audit logs failed integrity check", 
                auditLogs.size() - verifiedLogs.size());
        }
        
        return AuditTrailExport.builder()
            .exportId(UUID.randomUUID().toString())
            .requestedBy(request.getRequestedBy())
            .exportDate(LocalDateTime.now())
            .recordCount(verifiedLogs.size())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .format(request.getFormat())
            .data(formatExportData(verifiedLogs, request.getFormat()))
            .checksum(generateExportChecksum(verifiedLogs))
            .build();
    }
    
    // Helper methods
    
    private String generateIntegrityHash(PaymentAuditLog auditLog) {
        try {
            String data = auditLog.getPaymentId() + auditLog.getTransactionId() + 
                         auditLog.getAmount() + auditLog.getEventTimestamp();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate integrity hash: ", e);
            return null;
        }
    }
    
    private String encryptSensitiveData(PaymentRequest request) {
        try {
            Map<String, Object> sensitiveData = new HashMap<>();
            if (request.getPaymentMethodId() != null) {
                sensitiveData.put("paymentMethodId", request.getPaymentMethodId());
            }
            if (request.getMetadata() != null && request.getMetadata().containsKey("cardNumber")) {
                sensitiveData.put("cardNumber", maskCardNumber(
                    (String) request.getMetadata().get("cardNumber")));
            }
            
            String json = objectMapper.writeValueAsString(sensitiveData);
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(json.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Failed to encrypt sensitive data: ", e);
            return null;
        }
    }
    
    private boolean verifyIntegrity(PaymentAuditLog auditLog) {
        String expectedHash = generateIntegrityHash(auditLog);
        return expectedHash != null && expectedHash.equals(auditLog.getIntegrityHash());
    }
    
    private void detectAnomalies(PaymentRequest request, PaymentResult result) {
        // Detect unusual patterns
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            log.warn("HIGH_VALUE_TRANSACTION: paymentId={}, amount={}", 
                request.getPaymentId(), request.getAmount());
        }
        
        // Check for rapid succession
        List<PaymentAuditLog> recentPayments = auditRepository.findRecentByUserId(
            request.getFromUserId(), LocalDateTime.now().minus(1, ChronoUnit.MINUTES)
        );
        
        if (recentPayments.size() > 3) {
            log.warn("RAPID_TRANSACTIONS: userId={}, count={}", 
                request.getFromUserId(), recentPayments.size());
        }
    }
    
    private void performComplianceChecks(PaymentRequest request, PaymentResult result) {
        // AML check for large transactions
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            log.info("AML_CHECK: Large transaction flagged for review: {}", 
                request.getPaymentId());
        }
        
        // GDPR data retention check
        cleanupOldAuditLogs();
    }
    
    @Async
    private void cleanupOldAuditLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = auditRepository.deleteOldAuditLogs(cutoffDate);
        if (deleted > 0) {
            log.info("Cleaned up {} old audit logs", deleted);
        }
    }
    
    private void updateMetrics(PaymentRequest request, PaymentResult result) {
        Counter.builder("payment.audit.logged")
            .tag("type", request.getType().toString())
            .tag("status", result.getStatus().toString())
            .register(meterRegistry)
            .increment();
    }
    
    private void logDataAccess(String accessType, String resourceId) {
        pciAuditService.auditDataAccess(accessType, resourceId, getCurrentUser());
    }
    
    private String getCurrentUser() {
        // Get from security context
        return "system"; // Placeholder
    }
    
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }
    
    private boolean shouldEncryptSensitiveData(PaymentRequest request) {
        return request.getPaymentMethodId() != null || 
               (request.getMetadata() != null && 
                request.getMetadata().containsKey("cardNumber"));
    }
    
    private boolean isCacheExpired(PaymentAnalytics analytics) {
        return ChronoUnit.MINUTES.between(
            analytics.getGeneratedAt(), LocalDateTime.now()) > 5;
    }
}