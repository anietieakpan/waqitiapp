package com.waqiti.compliance.kafka;

import com.waqiti.common.events.Form8300Event;
import com.waqiti.compliance.domain.Form8300Report;
import com.waqiti.compliance.domain.ReportStatus;
import com.waqiti.compliance.repository.Form8300ReportRepository;
import com.waqiti.compliance.service.Form8300Service;
import com.waqiti.compliance.service.IRSFilingService;
import com.waqiti.compliance.service.ComplianceValidationService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class Form8300EventsConsumer {
    
    private final Form8300ReportRepository reportRepository;
    private final Form8300Service form8300Service;
    private final IRSFilingService irsFilingService;
    private final ComplianceValidationService validationService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal REPORTING_THRESHOLD = new BigDecimal("10000.00");
    
    @KafkaListener(
        topics = {"form-8300-events", "cash-transaction-reporting", "irs-8300-filing"},
        groupId = "compliance-form8300-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleForm8300Event(
            @Payload Form8300Event event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("form8300-%s-p%d-o%d", 
            event.getTransactionId(), partition, offset);
        
        log.info("Processing Form 8300 event: txId={}, amount={}, eventType={}", 
            event.getTransactionId(), event.getAmount(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case THRESHOLD_EXCEEDED:
                    processThresholdExceeded(event, correlationId);
                    break;
                case REPORT_GENERATED:
                    processReportGenerated(event, correlationId);
                    break;
                case REPORT_FILED:
                    processReportFiled(event, correlationId);
                    break;
                case FILING_FAILED:
                    processFilingFailed(event, correlationId);
                    break;
                case AMENDED_REPORT:
                    processAmendedReport(event, correlationId);
                    break;
                default:
                    log.warn("Unknown Form 8300 event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logComplianceEvent(
                "FORM_8300_EVENT_PROCESSED",
                event.getTransactionId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "amount", event.getAmount(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process Form 8300 event: txId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
            kafkaTemplate.send("form-8300-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processThresholdExceeded(Form8300Event event, String correlationId) {
        log.warn("Cash transaction threshold exceeded: txId={}, amount={}", 
            event.getTransactionId(), event.getAmount());
        
        if (event.getAmount().compareTo(REPORTING_THRESHOLD) >= 0) {
            Form8300Report report = Form8300Report.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .reportingBusinessName(event.getBusinessName())
                .reportingBusinessEIN(event.getBusinessEIN())
                .customerName(event.getCustomerName())
                .customerTIN(event.getCustomerTIN())
                .customerAddress(event.getCustomerAddress())
                .transactionAmount(event.getAmount())
                .transactionDate(event.getTransactionDate())
                .cashReceived(event.getAmount())
                .transactionType(event.getTransactionType())
                .status(ReportStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusDays(15))
                .correlationId(correlationId)
                .build();
            
            reportRepository.save(report);
            
            form8300Service.scheduleReportGeneration(report.getId());
            
            notificationService.sendComplianceAlert(
                "Form 8300 Reporting Required",
                String.format("Cash transaction of %s requires Form 8300 filing. Due: %s",
                    event.getAmount(), report.getDueDate()),
                NotificationService.Priority.HIGH
            );
            
            metricsService.recordForm8300Triggered(event.getAmount());
        }
    }
    
    private void processReportGenerated(Form8300Event event, String correlationId) {
        log.info("Form 8300 report generated: reportId={}", event.getReportId());
        
        Form8300Report report = reportRepository.findById(event.getReportId())
            .orElseThrow(() -> new IllegalStateException("Report not found: " + event.getReportId()));
        
        report.setStatus(ReportStatus.GENERATED);
        report.setGeneratedAt(LocalDateTime.now());
        report.setReportData(event.getReportData());
        reportRepository.save(report);
        
        if (validationService.validateForm8300(report)) {
            irsFilingService.scheduleIRSFiling(report.getId());
        } else {
            report.setStatus(ReportStatus.VALIDATION_FAILED);
            report.setValidationErrors(validationService.getValidationErrors(report));
            reportRepository.save(report);
        }
        
        metricsService.recordForm8300Generated();
    }
    
    private void processReportFiled(Form8300Event event, String correlationId) {
        log.info("Form 8300 report filed: reportId={}, confirmationNumber={}", 
            event.getReportId(), event.getConfirmationNumber());
        
        Form8300Report report = reportRepository.findById(event.getReportId())
            .orElseThrow(() -> new IllegalStateException("Report not found: " + event.getReportId()));
        
        report.setStatus(ReportStatus.FILED);
        report.setFiledAt(LocalDateTime.now());
        report.setConfirmationNumber(event.getConfirmationNumber());
        reportRepository.save(report);
        
        metricsService.recordForm8300Filed();
    }
    
    private void processFilingFailed(Form8300Event event, String correlationId) {
        log.error("Form 8300 filing failed: reportId={}, reason={}", 
            event.getReportId(), event.getFailureReason());
        
        Form8300Report report = reportRepository.findById(event.getReportId())
            .orElseThrow(() -> new IllegalStateException("Report not found: " + event.getReportId()));
        
        report.setStatus(ReportStatus.FILING_FAILED);
        report.setFailureReason(event.getFailureReason());
        reportRepository.save(report);
        
        notificationService.sendComplianceAlert(
            "Form 8300 Filing Failed",
            String.format("Failed to file Form 8300 report %s: %s",
                event.getReportId(), event.getFailureReason()),
            NotificationService.Priority.CRITICAL
        );
        
        metricsService.recordForm8300FilingFailed(event.getFailureReason());
    }
    
    private void processAmendedReport(Form8300Event event, String correlationId) {
        log.info("Processing amended Form 8300: originalReportId={}", event.getOriginalReportId());
        
        Form8300Report amendedReport = form8300Service.createAmendedReport(
            event.getOriginalReportId(),
            event.getAmendmentReason(),
            event.getReportData()
        );
        
        reportRepository.save(amendedReport);
        metricsService.recordForm8300Amended();
    }
}