package com.waqiti.reconciliation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.reconciliation.domain.*;
import com.waqiti.reconciliation.repository.ReconciliationRepository;
import com.waqiti.reconciliation.service.ReportingService;
import com.waqiti.reconciliation.service.NotificationService;
import com.waqiti.reconciliation.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL KAFKA CONSUMER - Payment Reconciliation Completed
 * 
 * This consumer handles the completion of payment reconciliation processes and triggers
 * follow-up actions such as reporting, metrics collection, and stakeholder notifications.
 * 
 * Business Impact:
 * - Generates reconciliation reports for compliance
 * - Updates financial metrics and dashboards
 * - Notifies finance team of reconciliation results
 * - Triggers corrective workflows for unresolved discrepancies
 * - Archives reconciliation data for audit trail
 * 
 * Event Source: reconciliation-service (from PaymentReconciliationInitiatedConsumer)
 * Topic: payment.reconciliation.completed
 * 
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReconciliationCompletedConsumer {

    private final ReconciliationRepository reconciliationRepository;
    private final ReportingService reportingService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Processes payment reconciliation completion events.
     * 
     * Retry Strategy:
     * - 3 attempts with exponential backoff
     * - Failed messages sent to DLQ for manual review
     * - Important for reporting but not transaction-critical
     */
    @KafkaListener(
        topics = "payment.reconciliation.completed",
        groupId = "reconciliation-completion-processing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 4000),
        autoCreateTopics = "false",
        dltTopicSuffix = "-dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentReconciliationCompleted(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Processing payment reconciliation completion: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the completion event
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            
            String reconciliationId = (String) event.get("reconciliationId");
            String reconciliationType = (String) event.get("reconciliationType");
            Long totalRecords = getLongValue(event.get("totalRecords"));
            Long matchedRecords = getLongValue(event.get("matchedRecords"));
            Long unmatchedRecords = getLongValue(event.get("unmatchedRecords"));
            Long discrepanciesFound = getLongValue(event.get("discrepanciesFound"));
            String completedAtStr = (String) event.get("completedAt");
            
            // Validate required fields
            if (reconciliationId == null) {
                log.error("Invalid reconciliation completion event - missing reconciliationId: {}", event);
                acknowledgment.acknowledge();
                return;
            }
            
            // Verify reconciliation exists
            Optional<ReconciliationRun> reconciliationOpt = reconciliationRepository
                .findById(UUID.fromString(reconciliationId));
            
            if (reconciliationOpt.isEmpty()) {
                log.error("Reconciliation run not found: {}", reconciliationId);
                acknowledgment.acknowledge();
                return;
            }
            
            ReconciliationRun reconciliation = reconciliationOpt.get();
            
            // Generate comprehensive reconciliation report
            generateReconciliationReport(reconciliation);
            
            // Update metrics and KPIs
            updateReconciliationMetrics(reconciliation, matchedRecords, unmatchedRecords, discrepanciesFound);
            
            // Send notifications to stakeholders based on results
            sendStakeholderNotifications(reconciliation);
            
            // Trigger corrective workflows if needed
            triggerCorrectiveWorkflows(reconciliation);
            
            // Archive reconciliation data for compliance
            archiveReconciliationData(reconciliation);
            
            // Audit the completion processing
            auditService.logEvent("RECONCILIATION_COMPLETION_PROCESSED", Map.of(
                "reconciliationId", reconciliationId,
                "type", reconciliationType,
                "totalRecords", totalRecords.toString(),
                "matchedRecords", matchedRecords.toString(),
                "unmatchedRecords", unmatchedRecords.toString(),
                "discrepanciesFound", discrepanciesFound.toString(),
                "processingTimeMs", (System.currentTimeMillis() - startTime)
            ));
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully processed payment reconciliation completion: {} in {}ms", 
                reconciliationId, duration);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Critical error processing payment reconciliation completion", e);
            
            // Extract reconciliation ID for error reporting
            String reconciliationId = extractReconciliationId(message);
            
            // Audit the failure
            auditService.logEvent("RECONCILIATION_COMPLETION_PROCESSING_FAILURE", Map.of(
                "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                "errorType", e.getClass().getSimpleName(),
                "errorMessage", e.getMessage(),
                "stackTrace", getStackTraceAsString(e),
                "timestamp", LocalDateTime.now().toString()
            ));
            
            // Rethrow to trigger retry mechanism
            throw new RuntimeException("Failed to process payment reconciliation completion", e);
        }
    }
    
    /**
     * Generates a comprehensive PDF/Excel report of reconciliation results.
     */
    private void generateReconciliationReport(ReconciliationRun reconciliation) {
        try {
            log.info("Generating reconciliation report for: {}", reconciliation.getId());
            
            ReconciliationReport report = reportingService.generateReconciliationReport(
                reconciliation.getId(),
                true // include details
            );
            
            // Store report
            reconciliation.setReportUrl(report.getReportUrl());
            reconciliation.setReportGenerated(true);
            reconciliation.setReportGeneratedAt(LocalDateTime.now());
            reconciliationRepository.save(reconciliation);
            
            log.info("Generated reconciliation report: {} at {}", 
                reconciliation.getId(), report.getReportUrl());
            
        } catch (Exception e) {
            log.error("Failed to generate reconciliation report for: {}", reconciliation.getId(), e);
            // Non-critical - log but don't fail the entire process
        }
    }
    
    /**
     * Updates reconciliation metrics in the metrics system (Prometheus, etc.).
     */
    private void updateReconciliationMetrics(ReconciliationRun reconciliation, 
                                            Long matchedRecords, 
                                            Long unmatchedRecords, 
                                            Long discrepanciesFound) {
        try {
            log.info("Updating reconciliation metrics for: {}", reconciliation.getId());
            
            metricsService.recordReconciliationCompletion(
                reconciliation.getReconciliationType().toString(),
                reconciliation.getScope().toString(),
                matchedRecords,
                unmatchedRecords,
                discrepanciesFound
            );
            
            // Calculate and record reconciliation accuracy rate
            if (reconciliation.getTotalRecordsToReconcile() > 0) {
                double accuracyRate = (double) matchedRecords / reconciliation.getTotalRecordsToReconcile() * 100.0;
                metricsService.recordReconciliationAccuracy(
                    reconciliation.getReconciliationType().toString(),
                    accuracyRate
                );
            }
            
            // Record processing duration
            if (reconciliation.getInitiatedAt() != null && reconciliation.getCompletedAt() != null) {
                long durationSeconds = java.time.Duration.between(
                    reconciliation.getInitiatedAt(),
                    reconciliation.getCompletedAt()
                ).getSeconds();
                
                metricsService.recordReconciliationDuration(
                    reconciliation.getReconciliationType().toString(),
                    durationSeconds
                );
            }
            
            log.info("Updated reconciliation metrics for: {}", reconciliation.getId());
            
        } catch (Exception e) {
            log.error("Failed to update reconciliation metrics for: {}", reconciliation.getId(), e);
            // Non-critical - log but don't fail the entire process
        }
    }
    
    /**
     * Sends notifications to relevant stakeholders based on reconciliation results.
     */
    private void sendStakeholderNotifications(ReconciliationRun reconciliation) {
        try {
            log.info("Sending stakeholder notifications for reconciliation: {}", reconciliation.getId());
            
            // Determine notification priority based on discrepancies
            NotificationPriority priority = determineNotificationPriority(reconciliation);
            
            // Send to finance team
            notificationService.sendReconciliationCompletionToFinanceTeam(
                reconciliation,
                priority
            );
            
            // If critical discrepancies found, escalate to management
            if (reconciliation.getDiscrepanciesFound() > 0) {
                Long criticalDiscrepancies = reconciliation.getDiscrepanciesFound();
                
                if (criticalDiscrepancies > 10) {
                    notificationService.sendReconciliationEscalationToManagement(
                        reconciliation,
                        "High number of discrepancies detected: " + criticalDiscrepancies
                    );
                }
            }
            
            // Send to compliance team if required
            if (reconciliation.getReconciliationType() == ReconciliationType.REGULATORY_COMPLIANCE) {
                notificationService.sendReconciliationToComplianceTeam(reconciliation);
            }
            
            log.info("Sent stakeholder notifications for reconciliation: {}", reconciliation.getId());
            
        } catch (Exception e) {
            log.error("Failed to send stakeholder notifications for: {}", reconciliation.getId(), e);
            // Non-critical - log but don't fail the entire process
        }
    }
    
    /**
     * Triggers corrective workflows for unresolved discrepancies.
     */
    private void triggerCorrectiveWorkflows(ReconciliationRun reconciliation) {
        try {
            if (reconciliation.getDiscrepanciesFound() == 0) {
                log.info("No discrepancies found for reconciliation: {}, no corrective workflows needed", 
                    reconciliation.getId());
                return;
            }
            
            log.info("Triggering corrective workflows for {} discrepancies in reconciliation: {}", 
                reconciliation.getDiscrepanciesFound(), reconciliation.getId());
            
            // Create corrective action tasks
            reportingService.createCorrectiveActionTasks(reconciliation.getId());
            
            // Schedule follow-up reconciliation if needed
            if (reconciliation.getDiscrepanciesFound() > 5) {
                reportingService.scheduleFollowUpReconciliation(
                    reconciliation.getId(),
                    LocalDateTime.now().plusDays(1) // Schedule for tomorrow
                );
            }
            
            log.info("Triggered corrective workflows for reconciliation: {}", reconciliation.getId());
            
        } catch (Exception e) {
            log.error("Failed to trigger corrective workflows for: {}", reconciliation.getId(), e);
            // Non-critical - log but don't fail the entire process
        }
    }
    
    /**
     * Archives reconciliation data for long-term compliance and audit requirements.
     */
    private void archiveReconciliationData(ReconciliationRun reconciliation) {
        try {
            log.info("Archiving reconciliation data for: {}", reconciliation.getId());
            
            // Archive to long-term storage (S3, GCS, etc.)
            String archiveLocation = reportingService.archiveReconciliationData(reconciliation.getId());
            
            reconciliation.setArchived(true);
            reconciliation.setArchivedAt(LocalDateTime.now());
            reconciliation.setArchiveLocation(archiveLocation);
            reconciliationRepository.save(reconciliation);
            
            log.info("Archived reconciliation data for: {} at {}", 
                reconciliation.getId(), archiveLocation);
            
        } catch (Exception e) {
            log.error("Failed to archive reconciliation data for: {}", reconciliation.getId(), e);
            // Non-critical - log but don't fail the entire process
        }
    }
    
    private NotificationPriority determineNotificationPriority(ReconciliationRun reconciliation) {
        Long discrepancies = reconciliation.getDiscrepanciesFound();
        Long totalRecords = reconciliation.getTotalRecordsToReconcile();
        
        if (discrepancies == 0) {
            return NotificationPriority.LOW;
        }
        
        // Calculate discrepancy rate
        double discrepancyRate = (double) discrepancies / totalRecords * 100.0;
        
        if (discrepancyRate > 5.0 || discrepancies > 20) {
            return NotificationPriority.CRITICAL;
        } else if (discrepancyRate > 1.0 || discrepancies > 5) {
            return NotificationPriority.HIGH;
        } else {
            return NotificationPriority.MEDIUM;
        }
    }
    
    private Long getLongValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        return 0L;
    }
    
    private String extractReconciliationId(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            return (String) event.get("reconciliationId");
        } catch (Exception e) {
            log.error("Failed to extract reconciliationId from message", e);
            return null;
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}