package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditEvent;
import com.waqiti.audit.repository.AuditEventRepository;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.ComplianceReportingService;
import com.waqiti.audit.service.AuditAnalyticsEngine;
import com.waqiti.audit.service.AuditArchiveService;
import com.waqiti.audit.service.AuditNotificationService;
import com.waqiti.common.audit.AuditService as CommonAuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditReportsConsumer {

    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final ComplianceReportingService complianceReportingService;
    private final AuditAnalyticsEngine auditAnalyticsEngine;
    private final AuditArchiveService auditArchiveService;
    private final AuditNotificationService auditNotificationService;
    private final CommonAuditService commonAuditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter reportRequestCounter;
    private Counter complianceReportCounter;
    private Counter analyticsReportCounter;
    private Counter scheduledReportCounter;
    private Counter adhocReportCounter;
    private Counter reportGenerationFailureCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_reports_processed_total")
            .description("Total number of successfully processed audit report events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_reports_errors_total")
            .description("Total number of audit report processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_reports_processing_duration")
            .description("Time taken to process audit report events")
            .register(meterRegistry);
        reportRequestCounter = Counter.builder("audit_report_requests_total")
            .description("Total number of audit report requests")
            .register(meterRegistry);
        complianceReportCounter = Counter.builder("audit_compliance_reports_total")
            .description("Total number of compliance audit reports generated")
            .register(meterRegistry);
        analyticsReportCounter = Counter.builder("audit_analytics_reports_total")
            .description("Total number of analytics audit reports generated")
            .register(meterRegistry);
        scheduledReportCounter = Counter.builder("audit_scheduled_reports_total")
            .description("Total number of scheduled audit reports processed")
            .register(meterRegistry);
        adhocReportCounter = Counter.builder("audit_adhoc_reports_total")
            .description("Total number of ad-hoc audit reports processed")
            .register(meterRegistry);
        reportGenerationFailureCounter = Counter.builder("audit_report_generation_failures_total")
            .description("Total number of audit report generation failures")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit-reports"},
        groupId = "audit-reports-processor-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "audit-reports", fallbackMethod = "handleAuditReportsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditReportsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-report-%d-p%d-o%d", System.currentTimeMillis(), partition, offset);
        String eventKey = String.format("report-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing audit report event: partition={}, offset={}, correlationId={}",
                partition, offset, correlationId);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            String reportType = (String) eventData.get("reportType");
            String reportFormat = (String) eventData.get("reportFormat");
            String reportScope = (String) eventData.get("reportScope");
            String requestedBy = (String) eventData.get("requestedBy");
            String reportPeriod = (String) eventData.get("reportPeriod");
            String startDate = (String) eventData.get("startDate");
            String endDate = (String) eventData.get("endDate");
            List<String> complianceStandards = (List<String>) eventData.get("complianceStandards");
            Map<String, Object> reportParameters = (Map<String, Object>) eventData.get("reportParameters");
            List<String> includedServices = (List<String>) eventData.get("includedServices");
            Boolean isScheduled = (Boolean) eventData.getOrDefault("isScheduled", false);
            String scheduleFrequency = (String) eventData.get("scheduleFrequency");
            Integer priority = eventData.get("priority") != null ?
                Integer.valueOf(eventData.get("priority").toString()) : 3; // Default medium priority

            reportRequestCounter.increment();
            if (isScheduled) {
                scheduledReportCounter.increment();
            } else {
                adhocReportCounter.increment();
            }

            // Process audit report request
            processAuditReportRequest(eventData, reportType, reportFormat, reportScope, requestedBy,
                reportPeriod, startDate, endDate, complianceStandards, reportParameters,
                includedServices, isScheduled, scheduleFrequency, priority, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            commonAuditService.logAuditEvent("AUDIT_REPORT_REQUEST_PROCESSED", correlationId,
                Map.of("reportType", reportType, "reportScope", reportScope, "requestedBy", requestedBy,
                    "isScheduled", isScheduled, "priority", priority, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit report event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("audit-reports-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditReportsEventFallback(
            String message,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-report-fallback-%d-p%d-o%d",
            System.currentTimeMillis(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit reports: partition={}, offset={}, error={}",
            partition, offset, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("audit-reports-dlq", Map.of(
            "originalMessage", message,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Audit Reports Circuit Breaker Triggered",
                String.format("Audit reports processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditReportsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-report-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Audit report permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        commonAuditService.logAuditEvent("AUDIT_REPORT_DLT_EVENT", correlationId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Audit Report Dead Letter Event",
                String.format("Audit report processing sent to DLT: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processAuditReportRequest(Map<String, Object> eventData, String reportType,
            String reportFormat, String reportScope, String requestedBy, String reportPeriod,
            String startDate, String endDate, List<String> complianceStandards,
            Map<String, Object> reportParameters, List<String> includedServices,
            Boolean isScheduled, String scheduleFrequency, Integer priority, String correlationId) {

        log.info("Processing audit report request: type={}, scope={}, requestedBy={}, scheduled={}, correlationId={}",
            reportType, reportScope, requestedBy, isScheduled, correlationId);

        // Create audit event for the report request
        AuditEvent auditEvent = AuditEvent.builder()
            .eventType("AUDIT_REPORT_REQUEST")
            .serviceName("audit-service")
            .userId(requestedBy)
            .resourceType("AUDIT_REPORT")
            .action(reportType)
            .description(String.format("Audit report request: %s for scope %s", reportType, reportScope))
            .result(AuditEvent.AuditResult.SUCCESS)
            .severity(mapPriorityToSeverity(priority))
            .correlationId(correlationId)
            .metadata(convertToStringMap(eventData))
            .complianceTags(String.join(",", complianceStandards != null ? complianceStandards : Collections.emptyList()))
            .build();

        auditEventRepository.save(auditEvent);

        // Process based on report type
        switch (reportType.toUpperCase()) {
            case "COMPLIANCE_REPORT":
                processComplianceReport(reportScope, startDate, endDate, complianceStandards,
                    reportParameters, includedServices, requestedBy, correlationId);
                complianceReportCounter.increment();
                break;
            case "AUDIT_TRAIL_REPORT":
                processAuditTrailReport(reportScope, startDate, endDate, reportParameters,
                    includedServices, requestedBy, correlationId);
                break;
            case "USER_ACTIVITY_REPORT":
                processUserActivityReport(reportScope, startDate, endDate, reportParameters,
                    requestedBy, correlationId);
                break;
            case "SECURITY_EVENTS_REPORT":
                processSecurityEventsReport(reportScope, startDate, endDate, reportParameters,
                    includedServices, requestedBy, correlationId);
                break;
            case "TRANSACTION_AUDIT_REPORT":
                processTransactionAuditReport(reportScope, startDate, endDate, reportParameters,
                    includedServices, requestedBy, correlationId);
                break;
            case "SYSTEM_HEALTH_REPORT":
                processSystemHealthReport(reportScope, startDate, endDate, reportParameters,
                    includedServices, requestedBy, correlationId);
                break;
            case "ANALYTICS_REPORT":
                processAnalyticsReport(reportScope, startDate, endDate, reportParameters,
                    includedServices, requestedBy, correlationId);
                analyticsReportCounter.increment();
                break;
            case "EXECUTIVE_SUMMARY":
                processExecutiveSummaryReport(reportScope, startDate, endDate, complianceStandards,
                    reportParameters, requestedBy, correlationId);
                break;
            default:
                processGenericReport(reportType, reportScope, startDate, endDate, reportParameters,
                    requestedBy, correlationId);
        }

        // Handle scheduled reports
        if (isScheduled && scheduleFrequency != null) {
            handleScheduledReport(reportType, reportScope, scheduleFrequency, reportParameters,
                requestedBy, correlationId);
        }

        // Generate and deliver report
        generateAndDeliverReport(reportType, reportFormat, reportScope, startDate, endDate,
            reportParameters, requestedBy, priority, correlationId);

        // Send downstream events
        kafkaTemplate.send("audit-report-processed", Map.of(
            "eventId", auditEvent.getId(),
            "reportType", reportType,
            "reportScope", reportScope,
            "requestedBy", requestedBy,
            "isScheduled", isScheduled,
            "priority", priority,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Completed audit report processing: type={}, scope={}, correlationId={}",
            reportType, reportScope, correlationId);
    }

    private void processComplianceReport(String reportScope, String startDate, String endDate,
            List<String> complianceStandards, Map<String, Object> reportParameters,
            List<String> includedServices, String requestedBy, String correlationId) {

        log.info("Processing compliance report: scope={}, standards={}, correlationId={}",
            reportScope, complianceStandards, correlationId);

        try {
            // Generate compliance report
            Map<String, Object> reportData = complianceReportingService.generateComplianceReport(
                reportScope, startDate, endDate, complianceStandards, reportParameters,
                includedServices, correlationId
            );

            // Send report data to storage
            kafkaTemplate.send("compliance-report-generated", Map.of(
                "reportScope", reportScope,
                "complianceStandards", complianceStandards != null ? complianceStandards : Collections.emptyList(),
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Compliance report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate compliance report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("COMPLIANCE_REPORT", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processAuditTrailReport(String reportScope, String startDate, String endDate,
            Map<String, Object> reportParameters, List<String> includedServices,
            String requestedBy, String correlationId) {

        log.info("Processing audit trail report: scope={}, services={}, correlationId={}",
            reportScope, includedServices, correlationId);

        try {
            // Generate audit trail report
            Map<String, Object> reportData = auditService.generateAuditTrailReport(
                reportScope, startDate, endDate, reportParameters, includedServices, correlationId
            );

            kafkaTemplate.send("audit-trail-report-generated", Map.of(
                "reportScope", reportScope,
                "includedServices", includedServices != null ? includedServices : Collections.emptyList(),
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Audit trail report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate audit trail report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("AUDIT_TRAIL_REPORT", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processUserActivityReport(String reportScope, String startDate, String endDate,
            Map<String, Object> reportParameters, String requestedBy, String correlationId) {

        log.info("Processing user activity report: scope={}, correlationId={}",
            reportScope, correlationId);

        try {
            // Generate user activity report
            Map<String, Object> reportData = auditAnalyticsEngine.generateUserActivityReport(
                reportScope, startDate, endDate, reportParameters, correlationId
            );

            kafkaTemplate.send("user-activity-report-generated", Map.of(
                "reportScope", reportScope,
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("User activity report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate user activity report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("USER_ACTIVITY_REPORT", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processSecurityEventsReport(String reportScope, String startDate, String endDate,
            Map<String, Object> reportParameters, List<String> includedServices,
            String requestedBy, String correlationId) {

        log.info("Processing security events report: scope={}, services={}, correlationId={}",
            reportScope, includedServices, correlationId);

        try {
            // Generate security events report
            Map<String, Object> reportData = auditAnalyticsEngine.generateSecurityEventsReport(
                reportScope, startDate, endDate, reportParameters, includedServices, correlationId
            );

            kafkaTemplate.send("security-events-report-generated", Map.of(
                "reportScope", reportScope,
                "includedServices", includedServices != null ? includedServices : Collections.emptyList(),
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Security events report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate security events report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("SECURITY_EVENTS_REPORT", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processTransactionAuditReport(String reportScope, String startDate, String endDate,
            Map<String, Object> reportParameters, List<String> includedServices,
            String requestedBy, String correlationId) {

        log.info("Processing transaction audit report: scope={}, services={}, correlationId={}",
            reportScope, includedServices, correlationId);

        try {
            // Generate transaction audit report
            Map<String, Object> reportData = auditAnalyticsEngine.generateTransactionAuditReport(
                reportScope, startDate, endDate, reportParameters, includedServices, correlationId
            );

            kafkaTemplate.send("transaction-audit-report-generated", Map.of(
                "reportScope", reportScope,
                "includedServices", includedServices != null ? includedServices : Collections.emptyList(),
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Transaction audit report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate transaction audit report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("TRANSACTION_AUDIT_REPORT", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processSystemHealthReport(String reportScope, String startDate, String endDate,
            Map<String, Object> reportParameters, List<String> includedServices,
            String requestedBy, String correlationId) {

        log.info("Processing system health report: scope={}, services={}, correlationId={}",
            reportScope, includedServices, correlationId);

        try {
            // Generate system health report
            Map<String, Object> reportData = auditAnalyticsEngine.generateSystemHealthReport(
                reportScope, startDate, endDate, reportParameters, includedServices, correlationId
            );

            kafkaTemplate.send("system-health-report-generated", Map.of(
                "reportScope", reportScope,
                "includedServices", includedServices != null ? includedServices : Collections.emptyList(),
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("System health report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate system health report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("SYSTEM_HEALTH_REPORT", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processAnalyticsReport(String reportScope, String startDate, String endDate,
            Map<String, Object> reportParameters, List<String> includedServices,
            String requestedBy, String correlationId) {

        log.info("Processing analytics report: scope={}, services={}, correlationId={}",
            reportScope, includedServices, correlationId);

        try {
            // Generate analytics report
            Map<String, Object> reportData = auditAnalyticsEngine.generateAdvancedAnalyticsReport(
                reportScope, startDate, endDate, reportParameters, includedServices, correlationId
            );

            kafkaTemplate.send("analytics-report-generated", Map.of(
                "reportScope", reportScope,
                "includedServices", includedServices != null ? includedServices : Collections.emptyList(),
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Analytics report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate analytics report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("ANALYTICS_REPORT", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processExecutiveSummaryReport(String reportScope, String startDate, String endDate,
            List<String> complianceStandards, Map<String, Object> reportParameters,
            String requestedBy, String correlationId) {

        log.info("Processing executive summary report: scope={}, standards={}, correlationId={}",
            reportScope, complianceStandards, correlationId);

        try {
            // Generate executive summary report
            Map<String, Object> reportData = complianceReportingService.generateExecutiveSummary(
                reportScope, startDate, endDate, complianceStandards, reportParameters, correlationId
            );

            kafkaTemplate.send("executive-summary-report-generated", Map.of(
                "reportScope", reportScope,
                "complianceStandards", complianceStandards != null ? complianceStandards : Collections.emptyList(),
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Executive summary report generated successfully: scope={}, correlationId={}",
                reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate executive summary report: scope={}, correlationId={}, error={}",
                reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure("EXECUTIVE_SUMMARY", reportScope, requestedBy, e, correlationId);
        }
    }

    private void processGenericReport(String reportType, String reportScope, String startDate,
            String endDate, Map<String, Object> reportParameters, String requestedBy, String correlationId) {

        log.info("Processing generic report: type={}, scope={}, correlationId={}",
            reportType, reportScope, correlationId);

        try {
            // Generate generic report
            Map<String, Object> reportData = auditService.generateGenericReport(
                reportType, reportScope, startDate, endDate, reportParameters, correlationId
            );

            kafkaTemplate.send("generic-report-generated", Map.of(
                "reportType", reportType,
                "reportScope", reportScope,
                "reportData", reportData,
                "requestedBy", requestedBy,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Generic report generated successfully: type={}, scope={}, correlationId={}",
                reportType, reportScope, correlationId);

        } catch (Exception e) {
            log.error("Failed to generate generic report: type={}, scope={}, correlationId={}, error={}",
                reportType, reportScope, correlationId, e.getMessage());
            handleReportGenerationFailure(reportType, reportScope, requestedBy, e, correlationId);
        }
    }

    private void handleScheduledReport(String reportType, String reportScope, String scheduleFrequency,
            Map<String, Object> reportParameters, String requestedBy, String correlationId) {

        log.info("Setting up scheduled report: type={}, scope={}, frequency={}, correlationId={}",
            reportType, reportScope, scheduleFrequency, correlationId);

        // Send to scheduling service
        kafkaTemplate.send("audit-report-scheduling", Map.of(
            "reportType", reportType,
            "reportScope", reportScope,
            "scheduleFrequency", scheduleFrequency,
            "reportParameters", reportParameters != null ? reportParameters : new HashMap<>(),
            "requestedBy", requestedBy,
            "schedulingAction", "CREATE_SCHEDULE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void generateAndDeliverReport(String reportType, String reportFormat, String reportScope,
            String startDate, String endDate, Map<String, Object> reportParameters,
            String requestedBy, Integer priority, String correlationId) {

        log.info("Generating and delivering report: type={}, format={}, scope={}, priority={}, correlationId={}",
            reportType, reportFormat, reportScope, priority, correlationId);

        // Send to report generation and delivery service
        kafkaTemplate.send("audit-report-generation", Map.of(
            "reportType", reportType,
            "reportFormat", reportFormat != null ? reportFormat : "PDF",
            "reportScope", reportScope,
            "startDate", startDate != null ? startDate : "",
            "endDate", endDate != null ? endDate : "",
            "reportParameters", reportParameters != null ? reportParameters : new HashMap<>(),
            "requestedBy", requestedBy,
            "priority", priority,
            "deliveryMethod", determineDeliveryMethod(priority),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send notification about report request
        try {
            auditNotificationService.sendReportRequestNotification(
                requestedBy,
                String.format("Your %s report request has been received and is being processed", reportType),
                Map.of("reportType", reportType, "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to send report request notification: {}", e.getMessage());
        }
    }

    private void handleReportGenerationFailure(String reportType, String reportScope,
            String requestedBy, Exception error, String correlationId) {

        reportGenerationFailureCounter.increment();

        log.error("Report generation failed: type={}, scope={}, correlationId={}, error={}",
            reportType, reportScope, correlationId, error.getMessage());

        // Send failure notification
        kafkaTemplate.send("audit-report-generation-failures", Map.of(
            "reportType", reportType,
            "reportScope", reportScope,
            "requestedBy", requestedBy,
            "error", error.getMessage(),
            "failureType", "GENERATION_FAILURE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send notification to requester
        try {
            auditNotificationService.sendReportFailureNotification(
                requestedBy,
                String.format("Your %s report generation failed: %s", reportType, error.getMessage()),
                Map.of("reportType", reportType, "error", error.getMessage(), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to send report failure notification: {}", e.getMessage());
        }

        // Alert operations team for critical reports
        if (isCriticalReport(reportType)) {
            try {
                notificationService.sendOperationalAlert(
                    "Critical Audit Report Generation Failed",
                    String.format("CRITICAL: %s report generation failed: %s", reportType, error.getMessage()),
                    "HIGH"
                );
            } catch (Exception e) {
                log.error("Failed to send critical report failure alert: {}", e.getMessage());
            }
        }
    }

    private boolean isCriticalReport(String reportType) {
        return reportType != null && (
            reportType.contains("COMPLIANCE") ||
            reportType.contains("EXECUTIVE") ||
            reportType.contains("SECURITY") ||
            reportType.contains("REGULATORY")
        );
    }

    private String determineDeliveryMethod(Integer priority) {
        return switch (priority) {
            case 1 -> "IMMEDIATE_EMAIL";
            case 2 -> "EMAIL";
            case 3 -> "PORTAL";
            case 4, 5 -> "SCHEDULED_DELIVERY";
            default -> "PORTAL";
        };
    }

    private AuditEvent.AuditSeverity mapPriorityToSeverity(Integer priority) {
        return switch (priority) {
            case 1 -> AuditEvent.AuditSeverity.CRITICAL;
            case 2 -> AuditEvent.AuditSeverity.HIGH;
            case 3 -> AuditEvent.AuditSeverity.MEDIUM;
            case 4, 5 -> AuditEvent.AuditSeverity.LOW;
            default -> AuditEvent.AuditSeverity.MEDIUM;
        };
    }

    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        if (objectMap == null) return new HashMap<>();

        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return stringMap;
    }
}