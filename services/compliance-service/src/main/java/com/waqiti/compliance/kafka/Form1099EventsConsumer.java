package com.waqiti.compliance.kafka;

import com.waqiti.common.events.Form1099Event;
import com.waqiti.compliance.domain.Form1099Report;
import com.waqiti.compliance.domain.ReportStatus;
import com.waqiti.compliance.repository.Form1099ReportRepository;
import com.waqiti.compliance.service.Form1099Service;
import com.waqiti.compliance.service.IRSFilingService;
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
public class Form1099EventsConsumer {
    
    private final Form1099ReportRepository reportRepository;
    private final Form1099Service form1099Service;
    private final IRSFilingService irsFilingService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal REPORTING_THRESHOLD_1099K = new BigDecimal("600.00");
    
    @KafkaListener(
        topics = {"form-1099-events", "tax-reporting-events", "1099k-filing-events"},
        groupId = "compliance-form1099-service-group",
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
    public void handleForm1099Event(
            @Payload Form1099Event event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("form1099-%s-p%d-o%d", 
            event.getPayeeId(), partition, offset);
        
        log.info("Processing Form 1099 event: payeeId={}, type={}, amount={}", 
            event.getPayeeId(), event.getForm1099Type(), event.getTotalAmount());
        
        try {
            switch (event.getEventType()) {
                case THRESHOLD_REACHED:
                    processThresholdReached(event, correlationId);
                    break;
                case ANNUAL_REPORT_GENERATED:
                    processAnnualReportGenerated(event, correlationId);
                    break;
                case REPORT_SENT_TO_PAYEE:
                    processReportSentToPayee(event, correlationId);
                    break;
                case REPORT_FILED_WITH_IRS:
                    processReportFiledWithIRS(event, correlationId);
                    break;
                default:
                    log.warn("Unknown Form 1099 event type: {}", event.getEventType());
                    break;
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process Form 1099 event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void processThresholdReached(Form1099Event event, String correlationId) {
        log.info("1099 threshold reached: payeeId={}, amount={}", 
            event.getPayeeId(), event.getTotalAmount());
        
        Form1099Report report = Form1099Report.builder()
            .id(UUID.randomUUID().toString())
            .taxYear(event.getTaxYear())
            .form1099Type(event.getForm1099Type())
            .payeeId(event.getPayeeId())
            .payeeName(event.getPayeeName())
            .payeeTIN(event.getPayeeTIN())
            .payeeAddress(event.getPayeeAddress())
            .payerEIN(event.getPayerEIN())
            .totalAmount(event.getTotalAmount())
            .transactionCount(event.getTransactionCount())
            .status(ReportStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        reportRepository.save(report);
        metricsService.recordForm1099ThresholdReached(event.getForm1099Type());
    }
    
    private void processAnnualReportGenerated(Form1099Event event, String correlationId) {
        log.info("Annual 1099 report generated: reportId={}, taxYear={}", 
            event.getReportId(), event.getTaxYear());
        
        Form1099Report report = reportRepository.findById(event.getReportId())
            .orElseThrow();
        
        report.setStatus(ReportStatus.GENERATED);
        report.setGeneratedAt(LocalDateTime.now());
        reportRepository.save(report);
        
        metricsService.recordForm1099Generated(event.getForm1099Type());
    }
    
    private void processReportSentToPayee(Form1099Event event, String correlationId) {
        log.info("1099 report sent to payee: reportId={}", event.getReportId());
        
        Form1099Report report = reportRepository.findById(event.getReportId())
            .orElseThrow();
        
        report.setSentToPayeeAt(LocalDateTime.now());
        reportRepository.save(report);
        
        metricsService.recordForm1099SentToPayee();
    }
    
    private void processReportFiledWithIRS(Form1099Event event, String correlationId) {
        log.info("1099 report filed with IRS: reportId={}", event.getReportId());
        
        Form1099Report report = reportRepository.findById(event.getReportId())
            .orElseThrow();
        
        report.setStatus(ReportStatus.FILED);
        report.setFiledAt(LocalDateTime.now());
        report.setConfirmationNumber(event.getConfirmationNumber());
        reportRepository.save(report);
        
        metricsService.recordForm1099Filed(event.getForm1099Type());
    }
}