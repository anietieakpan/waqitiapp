package com.waqiti.compliance.kafka;

import com.waqiti.common.events.RegulatoryReportingEvent;
import com.waqiti.compliance.domain.RegulatoryReport;
import com.waqiti.compliance.repository.RegulatoryReportRepository;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.ComplianceService;
import com.waqiti.common.audit.AuditService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class RegulatoryReportingConsumer {

    private final RegulatoryReportRepository reportRepository;
    private final RegulatoryReportingService reportingService;
    private final ComplianceService complianceService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter urgentReportsCounter;
    private Counter overdueReportsCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("regulatory_reporting_processed_total")
            .description("Total number of successfully processed regulatory reporting events")
            .register(meterRegistry);
        errorCounter = Counter.builder("regulatory_reporting_errors_total")
            .description("Total number of regulatory reporting processing errors")
            .register(meterRegistry);
        urgentReportsCounter = Counter.builder("regulatory_reports_urgent_total")
            .description("Total number of urgent regulatory reports")
            .register(meterRegistry);
        overdueReportsCounter = Counter.builder("regulatory_reports_overdue_total")
            .description("Total number of overdue regulatory reports")
            .register(meterRegistry);
        processingTimer = Timer.builder("regulatory_reporting_processing_duration")
            .description("Time taken to process regulatory reporting events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance.regulatory.reporting", "regulatory-reports", "regulatory-filing-requirements", "ctr-reports", "sar-reports"},
        groupId = "compliance-regulatory-reporting-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 1.5, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "regulatory-reporting", fallbackMethod = "handleRegulatoryReportingFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 1.5, maxDelay = 15000))
    public void handleRegulatoryReportingEvent(
            @Payload RegulatoryReportingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("reg-report-%s-p%d-o%d", event.getReportId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getReportId(), event.getReportType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing regulatory reporting event: reportId={}, type={}, priority={}, dueDate={}",
                event.getReportId(), event.getReportType(), event.getPriority(), event.getDueDate());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case REPORT_REQUIRED:
                    processReportRequired(event, correlationId);
                    break;

                case REPORT_GENERATED:
                    processReportGenerated(event, correlationId);
                    break;

                case REPORT_SUBMITTED:
                    processReportSubmitted(event, correlationId);
                    break;

                case REPORT_REJECTED:
                    processReportRejected(event, correlationId);
                    break;

                case REPORT_OVERDUE:
                    processReportOverdue(event, correlationId);
                    break;

                case REPORT_AMENDED:
                    processReportAmended(event, correlationId);
                    break;

                case COMPLIANCE_VIOLATION:
                    processComplianceViolation(event, correlationId);
                    break;

                default:
                    log.warn("Unknown regulatory reporting event type: {}", event.getEventType());
                    break;
            }

            // Check for regulatory deadlines and escalate if needed
            checkRegulatoryDeadlines(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logRegulatoryEvent("REGULATORY_REPORTING_EVENT_PROCESSED", event.getReportId(),
                Map.of("reportType", event.getReportType(), "eventType", event.getEventType(),
                    "priority", event.getPriority(), "correlationId", correlationId,
                    "dueDate", event.getDueDate(), "timestamp", Instant.now()));

            successCounter.increment();
            if ("URGENT".equals(event.getPriority())) {
                urgentReportsCounter.increment();
            }
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process regulatory reporting event: {}", e.getMessage(), e);

            // Escalate urgent/overdue report failures immediately
            if ("URGENT".equals(event.getPriority()) || "OVERDUE".equals(event.getStatus())) {
                escalateRegulatoryFailure(event, correlationId, e);
            }

            // Send fallback event
            kafkaTemplate.send("regulatory-reporting-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "requiresEscalation", "URGENT".equals(event.getPriority())));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleRegulatoryReportingFallback(
            RegulatoryReportingEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("reg-report-fallback-%s-p%d-o%d", event.getReportId(), partition, offset);

        log.error("Circuit breaker fallback triggered for regulatory reporting: reportId={}, error={}",
            event.getReportId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("regulatory-reporting-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for regulatory reporting failures
        try {
            notificationService.sendCriticalAlert(
                "Critical Regulatory Reporting System Failure",
                String.format("Regulatory reporting processing failed for report %s (%s): %s",
                    event.getReportId(), event.getReportType(), ex.getMessage()),
                "CRITICAL"
            );

            // Mandatory escalation for regulatory failures
            escalateRegulatoryFailure(event, correlationId, ex);

        } catch (Exception notificationEx) {
            log.error("Failed to send critical regulatory reporting alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltRegulatoryReportingEvent(
            @Payload RegulatoryReportingEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-reg-report-%s-%d", event.getReportId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Regulatory reporting permanently failed: reportId={}, topic={}, error={}",
            event.getReportId(), topic, exceptionMessage);

        // Save to dead letter store for regulatory investigation
        auditService.logRegulatoryEvent("REGULATORY_REPORTING_DLT_EVENT", event.getReportId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "reportType", event.getReportType(), "correlationId", correlationId,
                "requiresRegulatoryNotification", true, "priority", "EMERGENCY", "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Emergency: Regulatory Reporting in DLT",
                String.format("CRITICAL: Regulatory report sent to DLT - Report: %s (%s), Error: %s",
                    event.getReportId(), event.getReportType(), exceptionMessage),
                Map.of("reportId", event.getReportId(), "reportType", event.getReportType(),
                    "topic", topic, "correlationId", correlationId,
                    "severity", "EMERGENCY", "requiresImmediateAction", true)
            );

            // Mandatory executive and regulatory escalation for DLT events
            escalateRegulatoryFailure(event, correlationId, new Exception(exceptionMessage));
            notifyRegulatoryBodies(event, correlationId, exceptionMessage);

        } catch (Exception ex) {
            log.error("Failed to send emergency regulatory DLT alert: {}", ex.getMessage());
        }
    }

    private void processReportRequired(RegulatoryReportingEvent event, String correlationId) {
        RegulatoryReport report = RegulatoryReport.builder()
            .reportId(event.getReportId())
            .reportType(event.getReportType())
            .status("REQUIRED")
            .priority(event.getPriority())
            .dueDate(event.getDueDate())
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .regulatoryBody(event.getRegulatoryBody())
            .submissionMethod(event.getSubmissionMethod())
            .build();

        reportRepository.save(report);

        // Automatically initiate report generation for urgent reports
        if ("URGENT".equals(event.getPriority())) {
            reportingService.initiateUrgentReportGeneration(event.getReportId(), correlationId);
        }

        // Check if due date is approaching
        if (reportingService.isDueDateApproaching(event.getDueDate())) {
            notificationService.sendOperationalAlert(
                "Regulatory Report Due Soon",
                String.format("Report %s (%s) is due on %s",
                    event.getReportId(), event.getReportType(), event.getDueDate()),
                "MEDIUM"
            );
        }

        log.info("Regulatory report requirement created: reportId={}, type={}, dueDate={}",
            event.getReportId(), event.getReportType(), event.getDueDate());
    }

    private void processReportGenerated(RegulatoryReportingEvent event, String correlationId) {
        RegulatoryReport report = reportRepository.findByReportId(event.getReportId())
            .orElseThrow(() -> new RuntimeException("Regulatory report not found"));

        report.setStatus("GENERATED");
        report.setGeneratedAt(LocalDateTime.now());
        report.setFilePath(event.getFilePath());
        reportRepository.save(report);

        // Validate report before submission
        reportingService.validateReportData(event.getReportId());

        // Auto-submit urgent reports
        if ("URGENT".equals(event.getPriority())) {
            kafkaTemplate.send("regulatory-auto-submission", Map.of(
                "reportId", event.getReportId(),
                "reportType", event.getReportType(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Regulatory report generated: reportId={}, type={}", event.getReportId(), event.getReportType());
    }

    private void processReportSubmitted(RegulatoryReportingEvent event, String correlationId) {
        RegulatoryReport report = reportRepository.findByReportId(event.getReportId())
            .orElseThrow(() -> new RuntimeException("Regulatory report not found"));

        report.setStatus("SUBMITTED");
        report.setSubmittedAt(LocalDateTime.now());
        report.setSubmissionReference(event.getSubmissionReference());
        reportRepository.save(report);

        // Confirm submission with regulatory body
        reportingService.confirmSubmissionWithRegulator(event.getReportId(), event.getSubmissionReference());

        // Send confirmation notification
        notificationService.sendOperationalAlert(
            "Regulatory Report Submitted",
            String.format("Report %s (%s) submitted successfully. Reference: %s",
                event.getReportId(), event.getReportType(), event.getSubmissionReference()),
            "LOW"
        );

        log.info("Regulatory report submitted: reportId={}, reference={}",
            event.getReportId(), event.getSubmissionReference());
    }

    private void processReportRejected(RegulatoryReportingEvent event, String correlationId) {
        RegulatoryReport report = reportRepository.findByReportId(event.getReportId())
            .orElseThrow(() -> new RuntimeException("Regulatory report not found"));

        report.setStatus("REJECTED");
        report.setRejectedAt(LocalDateTime.now());
        report.setRejectionReason(event.getRejectionReason());
        reportRepository.save(report);

        // Escalate all rejections immediately
        escalateRegulatoryFailure(event, correlationId,
            new Exception("Regulatory report rejected: " + event.getRejectionReason()));

        // Initiate immediate remediation
        reportingService.initiateReportRemediation(event.getReportId(), event.getRejectionReason(), correlationId);

        notificationService.sendCriticalAlert(
            "Regulatory Report Rejected",
            String.format("Report %s (%s) rejected by %s. Reason: %s",
                event.getReportId(), event.getReportType(), event.getRegulatoryBody(), event.getRejectionReason()),
            "HIGH"
        );

        log.error("Regulatory report rejected: reportId={}, reason={}",
            event.getReportId(), event.getRejectionReason());
    }

    private void processReportOverdue(RegulatoryReportingEvent event, String correlationId) {
        RegulatoryReport report = reportRepository.findByReportId(event.getReportId())
            .orElseThrow(() -> new RuntimeException("Regulatory report not found"));

        report.setStatus("OVERDUE");
        report.setOverdueAt(LocalDateTime.now());
        reportRepository.save(report);

        overdueReportsCounter.increment();

        // Immediate executive escalation for overdue reports
        escalateRegulatoryFailure(event, correlationId,
            new Exception("Regulatory report is overdue"));

        // Notify regulatory body about delay
        reportingService.notifyRegulatoryBodyOfDelay(event.getReportId(), correlationId);

        notificationService.sendEmergencyAlert(
            "Regulatory Report Overdue",
            String.format("URGENT: Report %s (%s) is overdue. Due date was: %s",
                event.getReportId(), event.getReportType(), event.getDueDate()),
            Map.of("reportId", event.getReportId(), "severity", "EMERGENCY")
        );

        log.error("Regulatory report overdue: reportId={}, dueDate={}",
            event.getReportId(), event.getDueDate());
    }

    private void processReportAmended(RegulatoryReportingEvent event, String correlationId) {
        RegulatoryReport report = reportRepository.findByReportId(event.getReportId())
            .orElseThrow(() -> new RuntimeException("Regulatory report not found"));

        report.setStatus("AMENDED");
        report.setAmendedAt(LocalDateTime.now());
        report.setAmendmentReason(event.getAmendmentReason());
        reportRepository.save(report);

        // Track amendment for audit trail
        auditService.logRegulatoryEvent("REGULATORY_REPORT_AMENDED", event.getReportId(),
            Map.of("amendmentReason", event.getAmendmentReason(), "correlationId", correlationId));

        log.info("Regulatory report amended: reportId={}, reason={}",
            event.getReportId(), event.getAmendmentReason());
    }

    private void processComplianceViolation(RegulatoryReportingEvent event, String correlationId) {
        // Create compliance incident
        complianceService.createComplianceIncident(
            "REGULATORY_REPORTING_VIOLATION",
            event.getViolationDescription(),
            "HIGH",
            correlationId
        );

        // Immediate escalation for compliance violations
        escalateRegulatoryFailure(event, correlationId,
            new Exception("Compliance violation: " + event.getViolationDescription()));

        log.error("Compliance violation detected: reportId={}, violation={}",
            event.getReportId(), event.getViolationDescription());
    }

    private void checkRegulatoryDeadlines(RegulatoryReportingEvent event, String correlationId) {
        if (event.getDueDate() != null && reportingService.isDueDateCritical(event.getDueDate())) {
            notificationService.sendCriticalAlert(
                "Critical Regulatory Deadline",
                String.format("Report %s (%s) has critical deadline: %s",
                    event.getReportId(), event.getReportType(), event.getDueDate()),
                "HIGH"
            );
        }
    }

    private void escalateRegulatoryFailure(RegulatoryReportingEvent event, String correlationId, Exception error) {
        try {
            notificationService.sendExecutiveEscalation(
                "Critical Regulatory Reporting Issue",
                String.format("URGENT: Regulatory reporting issue requiring executive attention.\n" +
                    "Report ID: %s\n" +
                    "Report Type: %s\n" +
                    "Regulatory Body: %s\n" +
                    "Priority: %s\n" +
                    "Due Date: %s\n" +
                    "Error: %s\n" +
                    "Correlation ID: %s\n" +
                    "Time: %s",
                    event.getReportId(), event.getReportType(), event.getRegulatoryBody(),
                    event.getPriority(), event.getDueDate(), error.getMessage(), correlationId, Instant.now()),
                Map.of(
                    "priority", "EMERGENCY",
                    "category", "REGULATORY_COMPLIANCE",
                    "reportId", event.getReportId(),
                    "correlationId", correlationId,
                    "requiresImmediateAction", true
                )
            );
        } catch (Exception ex) {
            log.error("Failed to escalate regulatory failure to executive team: {}", ex.getMessage());
        }
    }

    private void notifyRegulatoryBodies(RegulatoryReportingEvent event, String correlationId, String error) {
        try {
            reportingService.notifyRegulatoryBodyOfSystemFailure(
                event.getRegulatoryBody(),
                event.getReportId(),
                error,
                correlationId
            );
        } catch (Exception ex) {
            log.error("Failed to notify regulatory body of system failure: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}