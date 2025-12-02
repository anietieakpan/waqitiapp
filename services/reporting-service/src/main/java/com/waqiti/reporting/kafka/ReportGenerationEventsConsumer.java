package com.waqiti.reporting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.reporting.service.ReportGenerationService;
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
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationEventsConsumer {
    
    private final ReportGenerationService reportGenerationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"report-generation-events", "report-requested", "report-completed"},
        groupId = "reporting-service-report-generation-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleReportGenerationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID reportId = null;
        UUID requestedBy = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            reportId = UUID.fromString((String) event.get("reportId"));
            requestedBy = UUID.fromString((String) event.get("requestedBy"));
            eventType = (String) event.get("eventType");
            String reportType = (String) event.get("reportType");
            String reportStatus = (String) event.get("reportStatus");
            LocalDateTime requestedDate = LocalDateTime.parse((String) event.get("requestedDate"));
            LocalDateTime completedDate = event.containsKey("completedDate") ? 
                    LocalDateTime.parse((String) event.get("completedDate")) : null;
            String reportFormat = (String) event.getOrDefault("reportFormat", "PDF");
            String reportPeriod = (String) event.getOrDefault("reportPeriod", "MONTHLY");
            
            log.info("Report generation event - ReportId: {}, RequestedBy: {}, Type: {}, Status: {}", 
                    reportId, requestedBy, reportType, reportStatus);
            
            reportGenerationService.processReportGeneration(reportId, requestedBy, eventType, 
                    reportType, reportStatus, requestedDate, completedDate, reportFormat, reportPeriod);
            
            auditService.auditFinancialEvent(
                    "REPORT_GENERATION_EVENT_PROCESSED",
                    requestedBy.toString(),
                    String.format("Report generation %s - Type: %s, Status: %s", 
                            eventType, reportType, reportStatus),
                    Map.of(
                            "reportId", reportId.toString(),
                            "requestedBy", requestedBy.toString(),
                            "eventType", eventType,
                            "reportType", reportType,
                            "reportStatus", reportStatus,
                            "reportFormat", reportFormat,
                            "reportPeriod", reportPeriod
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Report generation event processing failed - ReportId: {}, RequestedBy: {}, Error: {}", 
                    reportId, requestedBy, e.getMessage(), e);
            throw new RuntimeException("Report generation event processing failed", e);
        }
    }
}