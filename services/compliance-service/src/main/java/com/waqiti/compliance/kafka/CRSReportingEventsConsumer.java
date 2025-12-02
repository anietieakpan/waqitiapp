package com.waqiti.compliance.kafka;

import com.waqiti.common.events.CRSReportingEvent;
import com.waqiti.compliance.domain.CRSReport;
import com.waqiti.compliance.repository.CRSReportRepository;
import com.waqiti.compliance.service.CRSService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
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
public class CRSReportingEventsConsumer {
    
    private final CRSReportRepository reportRepository;
    private final CRSService crsService;
    private final ComplianceMetricsService metricsService;
    
    @KafkaListener(
        topics = {"crs-reporting-events", "common-reporting-standard-events"},
        groupId = "compliance-crs-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional
    public void handleCRSReportingEvent(
            @Payload CRSReportingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Processing CRS event: accountId={}, jurisdiction={}", 
            event.getAccountId(), event.getReportingJurisdiction());
        
        try {
            CRSReport report = CRSReport.builder()
                .id(UUID.randomUUID().toString())
                .accountId(event.getAccountId())
                .reportingYear(event.getReportingYear())
                .reportingJurisdiction(event.getReportingJurisdiction())
                .residenceJurisdiction(event.getResidenceJurisdiction())
                .accountBalance(event.getAccountBalance())
                .taxIdNumber(event.getTaxIdNumber())
                .createdAt(LocalDateTime.now())
                .build();
            
            reportRepository.save(report);
            crsService.processReport(report);
            
            metricsService.recordCRSReport(event.getReportingJurisdiction());
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process CRS event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}