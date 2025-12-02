package com.waqiti.compliance.kafka;

import com.waqiti.common.events.FATCAReportingEvent;
import com.waqiti.compliance.domain.FATCAReport;
import com.waqiti.compliance.repository.FATCAReportRepository;
import com.waqiti.compliance.service.FATCAService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class FATCAReportingEventsConsumer {
    
    private final FATCAReportRepository reportRepository;
    private final FATCAService fatcaService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    
    @KafkaListener(
        topics = {"fatca-reporting-events", "foreign-account-reporting"},
        groupId = "compliance-fatca-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional
    public void handleFATCAReportingEvent(
            @Payload FATCAReportingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Processing FATCA event: accountId={}, eventType={}", 
            event.getAccountId(), event.getEventType());
        
        try {
            FATCAReport report = FATCAReport.builder()
                .id(UUID.randomUUID().toString())
                .accountId(event.getAccountId())
                .reportingYear(event.getReportingYear())
                .accountBalance(event.getAccountBalance())
                .usPersonIndicator(event.isUSPerson())
                .foreignTaxIdNumber(event.getForeignTaxIdNumber())
                .createdAt(LocalDateTime.now())
                .build();
            
            reportRepository.save(report);
            fatcaService.processReport(report);
            
            metricsService.recordFATCAReport();
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process FATCA event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}