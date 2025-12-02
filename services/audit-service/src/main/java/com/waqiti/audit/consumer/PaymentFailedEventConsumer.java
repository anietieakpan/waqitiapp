package com.waqiti.audit.consumer;

import com.waqiti.common.events.PaymentFailedEvent;
import com.waqiti.audit.service.AuditTrailService;
import com.waqiti.audit.service.ComplianceAuditService;
import com.waqiti.audit.service.FailureAnalysisService;
import com.waqiti.audit.repository.ProcessedEventRepository;
import com.waqiti.audit.model.ProcessedEvent;
import com.waqiti.audit.model.AuditRecord;
import com.waqiti.audit.model.FailurePattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;

/**
 * Audit Service Consumer for PaymentFailedEvent
 * Creates comprehensive audit trail for payment failures
 * CRITICAL: All payment failures must be audited for compliance
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentFailedEventConsumer {
    
    private final AuditTrailService auditTrailService;
    private final ComplianceAuditService complianceAuditService;
    private final FailureAnalysisService failureAnalysisService;
    private final ProcessedEventRepository processedEventRepository;
    
    @KafkaListener(
        topics = "payment.failed",
        groupId = "audit-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Creating audit trail for payment failure: {}", event.getPaymentId());
        
        // IDEMPOTENCY CHECK
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Payment failure already audited for event: {}", event.getEventId());
            return;
        }
        
        try {
            // STEP 1: Create comprehensive audit record
            AuditRecord auditRecord = createFailureAuditRecord(event);
            auditTrailService.recordPaymentFailure(auditRecord);
            
            // STEP 2: Compliance audit for high-value failures
            if (requiresComplianceAudit(event)) {
                complianceAuditService.auditHighValuePaymentFailure(event);
            }
            
            // STEP 3: Pattern analysis for fraud detection
            FailurePattern pattern = failureAnalysisService.analyzeFailurePattern(event);
            if (pattern.isSuspicious()) {
                escalateToFraudTeam(event, pattern);
            }
            
            // STEP 4: Regulatory reporting if required
            if (requiresRegulatoryReporting(event)) {
                generateRegulatoryReport(event);
            }
            
            // STEP 5: Record processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("PaymentFailedEvent")
                .processedAt(Instant.now())
                .auditRecordId(auditRecord.getId())
                .complianceRequired(requiresComplianceAudit(event))
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully created audit trail for payment failure: {}", 
                event.getPaymentId());
                
        } catch (Exception e) {
            log.error("Failed to create audit trail for payment failure: {}", 
                event.getPaymentId(), e);
            throw new RuntimeException("Payment failure audit failed", e);
        }
    }
    
    private AuditRecord createFailureAuditRecord(PaymentFailedEvent event) {
        return AuditRecord.builder()
            .eventType("PAYMENT_FAILURE")
            .paymentId(event.getPaymentId())
            .userId(event.getSenderUserId())
            .accountFrom(event.getFromAccount())
            .accountTo(event.getToAccount())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .failureReason(event.getFailureReason())
            .failureCode(event.getFailureCode())
            .paymentProvider(event.getPaymentProvider())
            .ipAddress(event.getIpAddress())
            .deviceId(event.getDeviceId())
            .timestamp(event.getTimestamp())
            .processingTime(calculateProcessingTime(event))
            .feesCharged(event.getFeesCharged())
            .fundsDebited(event.getFundsDebited())
            .compensationRequired(true)
            .severity(determineSeverity(event))
            .build();
    }
    
    private boolean requiresComplianceAudit(PaymentFailedEvent event) {
        // High-value transactions
        if (event.getAmount().compareTo(new BigDecimal("3000")) >= 0) {
            return true;
        }
        
        // International transfers
        if (isInternationalTransfer(event)) {
            return true;
        }
        
        // Suspicious failure patterns
        if (event.getFailureCode().contains("FRAUD") || 
            event.getFailureCode().contains("SUSPICIOUS")) {
            return true;
        }
        
        // Multiple failures from same user
        if (failureAnalysisService.hasMultipleRecentFailures(event.getSenderUserId())) {
            return true;
        }
        
        return false;
    }
    
    private void escalateToFraudTeam(PaymentFailedEvent event, FailurePattern pattern) {
        log.warn("Escalating suspicious payment failure to fraud team: {}", 
            event.getPaymentId());
        
        fraudAlertService.createSuspiciousActivityAlert(
            event.getSenderUserId(),
            event.getPaymentId(),
            pattern.getSuspiciousIndicators(),
            "Payment failure shows suspicious patterns"
        );
        
        // Temporarily restrict user account for investigation
        userSecurityService.applyTemporaryRestrictions(
            event.getSenderUserId(),
            "Suspicious payment failure pattern detected",
            Duration.ofHours(24)
        );
    }
    
    private boolean requiresRegulatoryReporting(PaymentFailedEvent event) {
        // SAR (Suspicious Activity Report) thresholds
        if (event.getAmount().compareTo(new BigDecimal("5000")) >= 0 && 
            event.getFailureCode().contains("AML")) {
            return true;
        }
        
        // CTR (Currency Transaction Report) for certain failure types
        if (event.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
            return true;
        }
        
        return false;
    }
    
    private void generateRegulatoryReport(PaymentFailedEvent event) {
        log.info("Generating regulatory report for payment failure: {}", 
            event.getPaymentId());
        
        RegulatoryReport report = RegulatoryReport.builder()
            .reportType(determineReportType(event))
            .paymentId(event.getPaymentId())
            .userId(event.getSenderUserId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .failureReason(event.getFailureReason())
            .timestamp(event.getTimestamp())
            .requiresFilingWithinDays(30)
            .build();
            
        regulatoryReportingService.scheduleReport(report);
    }
    
    private String determineSeverity(PaymentFailedEvent event) {
        // Critical failures
        if (event.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
            return "CRITICAL";
        }
        
        // High severity for fraud-related failures
        if (event.getFailureCode().contains("FRAUD") || 
            event.getFailureCode().contains("BLOCKED")) {
            return "HIGH";
        }
        
        // Medium for technical failures
        if (event.getFailureCode().contains("TECHNICAL") || 
            event.getFailureCode().contains("TIMEOUT")) {
            return "MEDIUM";
        }
        
        return "LOW";
    }
    
    private long calculateProcessingTime(PaymentFailedEvent event) {
        if (event.getInitiatedAt() != null && event.getTimestamp() != null) {
            return event.getTimestamp().toEpochMilli() - event.getInitiatedAt().toEpochMilli();
        }
        return 0;
    }
    
    private boolean isInternationalTransfer(PaymentFailedEvent event) {
        // Implementation to check if transfer crosses borders
        return event.getFromAccount().startsWith("INT") || 
               event.getToAccount().startsWith("INT");
    }
    
    private String determineReportType(PaymentFailedEvent event) {
        if (event.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
            return "CTR"; // Currency Transaction Report
        }
        
        if (event.getFailureCode().contains("AML") || 
            event.getFailureCode().contains("SUSPICIOUS")) {
            return "SAR"; // Suspicious Activity Report
        }
        
        return "GENERAL";
    }
}