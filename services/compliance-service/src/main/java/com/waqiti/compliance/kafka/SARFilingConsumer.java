package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.SARProcessingService;
import com.waqiti.compliance.service.RegulatoryFilingService;
import com.waqiti.compliance.service.ComplianceNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Critical Event Consumer: SAR Filing Queue
 * 
 * Handles automated Suspicious Activity Report (SAR) filing for regulatory compliance:
 * - FinCEN SAR filing preparation and submission
 * - 14-day regulatory deadline management and tracking
 * - SAR quality validation and review workflows
 * - Multi-jurisdiction SAR filing coordination
 * - Executive notification for critical SARs
 * - Regulatory correspondence and follow-up management
 * 
 * BUSINESS IMPACT: Without this consumer, SAR filing requests are published
 * but NOT processed, leading to:
 * - Regulatory violations and massive fines ($50M+ annual risk)
 * - Missed 14-day SAR filing deadlines (criminal penalties)
 * - Failed BSA/AML compliance obligations
 * - Potential license suspension or revocation
 * - Criminal liability for executives and institution
 * - Regulatory consent orders and enforcement actions
 * 
 * This consumer enables:
 * - Automated SAR preparation and filing
 * - Regulatory deadline compliance (14-day requirement)
 * - Executive escalation for critical cases
 * - Multi-regulator coordination and filing
 * - Complete audit trail for regulatory examination
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SARFilingConsumer {

    private final SarFilingService sarFilingService;
    private final SARProcessingService sarProcessingService;
    private final RegulatoryFilingService regulatoryFilingService;
    private final ComplianceNotificationService complianceNotificationService;
    private final UniversalDLQHandler universalDLQHandler;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter sarFilingsProcessed;
    private Counter sarFilingsSuccessful;
    private Counter sarFilingsFailed;
    private Counter criticalSARsProcessed;
    private Counter emergencySARsProcessed;
    private Counter executiveNotificationsSent;
    private Counter regulatoryFilingsSubmitted;
    private Counter sarDeadlineViolations;
    private Counter sarQualityFailures;
    private Timer sarProcessingTime;
    private Counter highPrioritySARs;
    private Counter multiJurisdictionSARs;

    @PostConstruct
    public void initializeMetrics() {
        sarFilingsProcessed = Counter.builder("waqiti.sar.filings.processed.total")
            .description("Total SAR filing requests processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        sarFilingsSuccessful = Counter.builder("waqiti.sar.filings.successful")
            .description("Successful SAR filing processing")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        sarFilingsFailed = Counter.builder("waqiti.sar.filings.failed")
            .description("Failed SAR filing processing")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        criticalSARsProcessed = Counter.builder("waqiti.sar.critical.processed")
            .description("Critical SAR filings processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        emergencySARsProcessed = Counter.builder("waqiti.sar.emergency.processed")
            .description("Emergency SAR filings processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        executiveNotificationsSent = Counter.builder("waqiti.sar.executive_notifications.sent")
            .description("Executive notifications sent for SARs")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        regulatoryFilingsSubmitted = Counter.builder("waqiti.sar.regulatory_filings.submitted")
            .description("SAR regulatory filings submitted")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        sarDeadlineViolations = Counter.builder("waqiti.sar.deadline_violations")
            .description("SAR filing deadline violations")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        sarQualityFailures = Counter.builder("waqiti.sar.quality_failures")
            .description("SAR quality validation failures")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        sarProcessingTime = Timer.builder("waqiti.sar.filing.processing.duration")
            .description("Time taken to process SAR filings")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        highPrioritySARs = Counter.builder("waqiti.sar.high_priority.processed")
            .description("High priority SAR filings processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        multiJurisdictionSARs = Counter.builder("waqiti.sar.multi_jurisdiction.processed")
            .description("Multi-jurisdiction SAR filings processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);
    }

    /**
     * Consumes sar-filing-queue with comprehensive SAR filing processing
     * 
     * @param sarPayload The SAR filing data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "sar-filing-queue",
        groupId = "compliance-service-sar-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleSARFiling(
            @Payload Map<String, Object> sarPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String sarId = null;
        
        try {
            sarFilingsProcessed.increment();
            
            log.info("Processing SAR filing from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            sarId = (String) sarPayload.get("sarId");
            String priority = (String) sarPayload.get("priority");
            
            if (sarId == null) {
                throw new IllegalArgumentException("Missing required SAR ID");
            }
            
            log.info("Processing SAR filing: {} - Priority: {}", sarId, priority);
            
            // Convert to structured SAR filing request
            SARFilingRequest sarRequest = convertToSARFilingRequest(sarPayload);
            
            // Validate SAR filing request
            validateSARFilingRequest(sarRequest);
            
            // Capture business metrics
            captureBusinessMetrics(sarRequest);
            
            // Check filing deadline urgency
            checkFilingDeadlineUrgency(sarRequest);
            
            // Process SAR filing based on priority in parallel operations
            CompletableFuture<Void> sarPreparation = prepareSARFiling(sarRequest);
            CompletableFuture<Void> qualityValidation = performQualityValidation(sarRequest);
            CompletableFuture<Void> regulatoryReview = performRegulatoryReview(sarRequest);
            CompletableFuture<Void> notificationProcessing = processNotifications(sarRequest);
            
            // Wait for parallel processing to complete
            CompletableFuture.allOf(
                sarPreparation, 
                qualityValidation, 
                regulatoryReview, 
                notificationProcessing
            ).join();
            
            // Submit SAR filing to regulatory authorities
            submitSARFiling(sarRequest);
            
            // Update case management and tracking
            updateSARCaseManagement(sarRequest);
            
            sarFilingsSuccessful.increment();
            log.info("Successfully processed SAR filing: {}", sarId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sarFilingsFailed.increment();
            log.error("Failed to process SAR filing: {} - Error: {}", sarId, e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, Map<String, Object>> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        "sar-filing-queue", partition, offset, sarId, sarPayload);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send SAR filing to DLQ: {}", sarId, dlqEx);
            }

            // Don't acknowledge - this will trigger retry mechanism
            throw new SARFilingProcessingException(
                "Failed to process SAR filing: " + sarId, e);

        } finally {
            sample.stop(sarProcessingTime);
        }
    }

    /**
     * Converts SAR payload to structured SARFilingRequest
     */
    private SARFilingRequest convertToSARFilingRequest(Map<String, Object> sarPayload) {
        try {
            // Extract SAR data
            Map<String, Object> sarData = (Map<String, Object>) sarPayload.get("data");
            
            return SARFilingRequest.builder()
                .sarId((String) sarPayload.get("sarId"))
                .priority((String) sarPayload.get("priority"))
                .filingType((String) sarPayload.get("filingType"))
                .timestamp(LocalDateTime.parse(sarPayload.get("timestamp").toString()))
                .data(sarData)
                .customerId(sarData != null ? (String) sarData.get("customerId") : null)
                .accountId(sarData != null ? (String) sarData.get("accountId") : null)
                .transactionId(sarData != null ? (String) sarData.get("transactionId") : null)
                .suspiciousAmount(sarData != null && sarData.get("suspiciousAmount") != null ? 
                    new BigDecimal(sarData.get("suspiciousAmount").toString()) : null)
                .currency(sarData != null ? (String) sarData.get("currency") : "USD")
                .suspiciousActivity(sarData != null ? (String) sarData.get("suspiciousActivity") : null)
                .narrativeDescription(sarData != null ? (String) sarData.get("narrativeDescription") : null)
                .reportingDate(sarData != null && sarData.get("reportingDate") != null ?
                    LocalDateTime.parse(sarData.get("reportingDate").toString()) : null)
                .dueDate(sarData != null && sarData.get("dueDate") != null ?
                    LocalDateTime.parse(sarData.get("dueDate").toString()) : null)
                .jurisdiction(sarData != null ? (String) sarData.get("jurisdiction") : "US")
                .regulatoryBody(sarData != null ? (String) sarData.get("regulatoryBody") : "FinCEN")
                .relatedCases(sarData != null ? (Map<String, String>) sarData.get("relatedCases") : null)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert SAR filing payload", e);
            throw new IllegalArgumentException("Invalid SAR filing format", e);
        }
    }

    /**
     * Validates SAR filing request data
     */
    private void validateSARFilingRequest(SARFilingRequest request) {
        if (request.getSarId() == null || request.getSarId().trim().isEmpty()) {
            throw new IllegalArgumentException("SAR ID is required");
        }
        
        if (request.getCustomerId() == null || request.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (request.getSuspiciousActivity() == null || request.getSuspiciousActivity().trim().isEmpty()) {
            throw new IllegalArgumentException("Suspicious activity description is required");
        }
        
        if (request.getNarrativeDescription() == null || request.getNarrativeDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Narrative description is required");
        }
        
        // Validate due date
        if (request.getDueDate() != null && request.getDueDate().isBefore(LocalDateTime.now())) {
            log.warn("SAR filing due date has passed: {} - SAR: {}", request.getDueDate(), request.getSarId());
        }
        
        // Validate priority
        if (request.getPriority() != null && 
            !request.getPriority().matches("(?i)(LOW|MEDIUM|HIGH|CRITICAL|EMERGENCY)")) {
            throw new IllegalArgumentException("Invalid priority level");
        }
        
        // Validate amounts if present
        if (request.getSuspiciousAmount() != null && 
            request.getSuspiciousAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Suspicious amount cannot be negative");
        }
    }

    /**
     * Captures business metrics for monitoring and alerting
     */
    private void captureBusinessMetrics(SARFilingRequest request) {
        // Track SARs by priority
        switch (request.getPriority().toUpperCase()) {
            case "CRITICAL":
                criticalSARsProcessed.increment(
                    "jurisdiction", request.getJurisdiction(),
                    "regulatory_body", request.getRegulatoryBody()
                );
                break;
            case "EMERGENCY":
                emergencySARsProcessed.increment(
                    "jurisdiction", request.getJurisdiction(),
                    "regulatory_body", request.getRegulatoryBody()
                );
                break;
            case "HIGH":
                highPrioritySARs.increment(
                    "jurisdiction", request.getJurisdiction(),
                    "regulatory_body", request.getRegulatoryBody()
                );
                break;
        }
        
        // Track multi-jurisdiction SARs
        if (!"US".equals(request.getJurisdiction()) || request.getRelatedCases() != null) {
            multiJurisdictionSARs.increment(
                "jurisdiction", request.getJurisdiction(),
                "filing_type", request.getFilingType()
            );
        }
        
        // Track large amount SARs
        if (request.getSuspiciousAmount() != null && 
            request.getSuspiciousAmount().compareTo(new BigDecimal("100000")) > 0) {
            highPrioritySARs.increment(
                "amount_category", "LARGE",
                "currency", request.getCurrency()
            );
        }
    }

    /**
     * Checks filing deadline urgency and escalates if needed
     */
    private void checkFilingDeadlineUrgency(SARFilingRequest request) {
        try {
            if (request.getDueDate() != null) {
                LocalDateTime now = LocalDateTime.now();
                long hoursUntilDue = java.time.Duration.between(now, request.getDueDate()).toHours();
                
                if (hoursUntilDue < 0) {
                    // Past due - critical violation
                    sarDeadlineViolations.increment();
                    log.error("SAR filing PAST DUE: {} - Due: {} ({}h overdue)", 
                        request.getSarId(), request.getDueDate(), Math.abs(hoursUntilDue));
                    escalateOverdueSAR(request, hoursUntilDue);
                } else if (hoursUntilDue < 24) {
                    // Due within 24 hours - urgent
                    log.warn("SAR filing DUE SOON: {} - Due: {} ({}h remaining)", 
                        request.getSarId(), request.getDueDate(), hoursUntilDue);
                    escalateUrgentSAR(request, hoursUntilDue);
                } else if (hoursUntilDue < 72) {
                    // Due within 72 hours - high priority
                    log.info("SAR filing approaching deadline: {} - Due: {} ({}h remaining)", 
                        request.getSarId(), request.getDueDate(), hoursUntilDue);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check filing deadline urgency", e);
        }
    }

    /**
     * Prepares SAR filing documentation and forms
     */
    private CompletableFuture<Void> prepareSARFiling(SARFilingRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Preparing SAR filing for: {}", request.getSarId());
                
                // Generate SAR report
                sarProcessingService.generateSARReport(
                    request.getSarId(),
                    request.getCustomerId(),
                    request.getAccountId(),
                    request.getTransactionId(),
                    request.getSuspiciousAmount(),
                    request.getCurrency(),
                    request.getSuspiciousActivity(),
                    request.getNarrativeDescription(),
                    request.getReportingDate()
                );
                
                // Prepare supporting documentation
                sarProcessingService.prepareSupportingDocumentation(
                    request.getSarId(),
                    request.getCustomerId(),
                    request.getTransactionId(),
                    request.getRelatedCases()
                );
                
                // Format for regulatory submission
                sarProcessingService.formatForRegulatorySubmission(
                    request.getSarId(),
                    request.getJurisdiction(),
                    request.getRegulatoryBody(),
                    request.getFilingType()
                );
                
                log.info("SAR filing preparation completed for: {}", request.getSarId());
                
            } catch (Exception e) {
                log.error("Failed to prepare SAR filing for: {}", request.getSarId(), e);
                throw new SARFilingProcessingException("SAR preparation failed", e);
            }
        });
    }

    /**
     * Performs quality validation on SAR filing
     */
    private CompletableFuture<Void> performQualityValidation(SARFilingRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Performing quality validation for SAR: {}", request.getSarId());
                
                // Validate completeness
                boolean isComplete = sarProcessingService.validateSARCompleteness(
                    request.getSarId(),
                    request.getCustomerId(),
                    request.getSuspiciousActivity(),
                    request.getNarrativeDescription()
                );
                
                if (!isComplete) {
                    sarQualityFailures.increment();
                    throw new SARFilingProcessingException("SAR completeness validation failed");
                }
                
                // Validate accuracy
                boolean isAccurate = sarProcessingService.validateSARAccuracy(
                    request.getSarId(),
                    request.getCustomerId(),
                    request.getTransactionId(),
                    request.getSuspiciousAmount()
                );
                
                if (!isAccurate) {
                    sarQualityFailures.increment();
                    throw new SARFilingProcessingException("SAR accuracy validation failed");
                }
                
                // Validate regulatory requirements
                boolean meetsRequirements = sarProcessingService.validateRegulatoryRequirements(
                    request.getSarId(),
                    request.getJurisdiction(),
                    request.getRegulatoryBody(),
                    request.getFilingType()
                );
                
                if (!meetsRequirements) {
                    sarQualityFailures.increment();
                    throw new SARFilingProcessingException("SAR regulatory requirements validation failed");
                }
                
                log.info("Quality validation passed for SAR: {}", request.getSarId());
                
            } catch (Exception e) {
                log.error("Failed quality validation for SAR: {}", request.getSarId(), e);
                // Don't throw exception for validation failures - log and continue with manual review flag
                sarProcessingService.flagForManualReview(request.getSarId(), "Quality validation failed: " + e.getMessage());
            }
        });
    }

    /**
     * Performs regulatory review and approval
     */
    private CompletableFuture<Void> performRegulatoryReview(SARFilingRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Only perform review for non-emergency filings
                if (!"EMERGENCY".equals(request.getPriority())) {
                    log.debug("Performing regulatory review for SAR: {}", request.getSarId());
                    
                    regulatoryFilingService.performRegulatoryReview(
                        request.getSarId(),
                        request.getJurisdiction(),
                        request.getRegulatoryBody(),
                        request.getPriority(),
                        request.getDueDate()
                    );
                    
                    // Check for regulatory approval
                    boolean approved = regulatoryFilingService.checkRegulatoryApproval(
                        request.getSarId(),
                        request.getPriority()
                    );
                    
                    if (!approved && "CRITICAL".equals(request.getPriority())) {
                        log.warn("Critical SAR pending regulatory approval: {}", request.getSarId());
                        escalatePendingApproval(request);
                    }
                    
                    log.info("Regulatory review completed for SAR: {}", request.getSarId());
                }
                
            } catch (Exception e) {
                log.error("Failed regulatory review for SAR: {}", request.getSarId(), e);
                // Don't throw exception for review failures - continue with filing
            }
        });
    }

    /**
     * Processes notifications for SAR filing
     */
    private CompletableFuture<Void> processNotifications(SARFilingRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing notifications for SAR: {}", request.getSarId());
                
                // Executive notification for critical and emergency SARs
                if ("CRITICAL".equals(request.getPriority()) || "EMERGENCY".equals(request.getPriority())) {
                    complianceNotificationService.sendExecutiveNotification(
                        request.getSarId(),
                        request.getCustomerId(),
                        request.getSuspiciousActivity(),
                        request.getSuspiciousAmount(),
                        request.getPriority(),
                        request.getDueDate()
                    );
                    
                    executiveNotificationsSent.increment();
                }
                
                // Compliance team notification
                complianceNotificationService.notifyComplianceTeam(
                    request.getSarId(),
                    request.getCustomerId(),
                    request.getPriority(),
                    request.getDueDate()
                );
                
                // Regulatory body notification if required
                if (requiresRegulatoryNotification(request)) {
                    complianceNotificationService.notifyRegulatoryBody(
                        request.getSarId(),
                        request.getRegulatoryBody(),
                        request.getJurisdiction(),
                        request.getPriority()
                    );
                }
                
                log.info("Notifications processed for SAR: {}", request.getSarId());
                
            } catch (Exception e) {
                log.error("Failed to process notifications for SAR: {}", request.getSarId(), e);
                // Don't throw exception for notification failures - log and continue
            }
        });
    }

    /**
     * Submits SAR filing to regulatory authorities
     */
    private void submitSARFiling(SARFilingRequest request) {
        try {
            log.info("Submitting SAR filing: {} to {}", request.getSarId(), request.getRegulatoryBody());
            
            // Submit to primary regulatory body
            String submissionId = regulatoryFilingService.submitSARFiling(
                request.getSarId(),
                request.getRegulatoryBody(),
                request.getJurisdiction(),
                request.getFilingType(),
                request.getPriority()
            );
            
            if (submissionId != null) {
                regulatoryFilingsSubmitted.increment();
                log.info("SAR filing submitted successfully: {} - Submission ID: {}", 
                    request.getSarId(), submissionId);
                
                // Update filing status
                sarProcessingService.updateSARFilingStatus(
                    request.getSarId(),
                    "SUBMITTED",
                    "Filed with " + request.getRegulatoryBody() + " - ID: " + submissionId
                );
                
                // Submit to additional jurisdictions if required
                submitToAdditionalJurisdictions(request);
                
            } else {
                throw new SARFilingProcessingException("Failed to submit SAR filing - no submission ID received");
            }
            
        } catch (Exception e) {
            log.error("Failed to submit SAR filing: {}", request.getSarId(), e);
            throw new SARFilingProcessingException("SAR filing submission failed", e);
        }
    }

    /**
     * Updates SAR case management and tracking
     */
    private void updateSARCaseManagement(SARFilingRequest request) {
        try {
            log.debug("Updating SAR case management for: {}", request.getSarId());
            
            // Update case status
            sarProcessingService.updateSARCaseStatus(
                request.getSarId(),
                "FILED",
                "SAR successfully filed with regulatory authorities",
                LocalDateTime.now()
            );
            
            // Schedule follow-up activities
            sarProcessingService.scheduleFollowUpActivities(
                request.getSarId(),
                request.getCustomerId(),
                request.getPriority()
            );
            
            // Link related cases if applicable
            if (request.getRelatedCases() != null && !request.getRelatedCases().isEmpty()) {
                sarProcessingService.linkRelatedSARCases(
                    request.getSarId(),
                    request.getRelatedCases()
                );
            }
            
            log.debug("SAR case management updated for: {}", request.getSarId());
            
        } catch (Exception e) {
            log.error("Failed to update SAR case management: {}", request.getSarId(), e);
        }
    }

    // Helper methods

    private void escalateOverdueSAR(SARFilingRequest request, long hoursOverdue) {
        try {
            log.error("ESCALATING OVERDUE SAR: {} - {}h overdue", request.getSarId(), Math.abs(hoursOverdue));
            
            // Immediate executive notification
            complianceNotificationService.sendEmergencyExecutiveAlert(
                request.getSarId(),
                "SAR FILING OVERDUE",
                "CRITICAL: SAR " + request.getSarId() + " is " + Math.abs(hoursOverdue) + " hours overdue",
                request.getCustomerId()
            );
            
            // Regulatory escalation
            regulatoryFilingService.escalateOverdueFiling(
                request.getSarId(),
                request.getRegulatoryBody(),
                hoursOverdue
            );
            
        } catch (Exception e) {
            log.error("Failed to escalate overdue SAR", e);
        }
    }

    private void escalateUrgentSAR(SARFilingRequest request, long hoursRemaining) {
        try {
            log.warn("ESCALATING URGENT SAR: {} - {}h remaining", request.getSarId(), hoursRemaining);
            
            complianceNotificationService.sendUrgentNotification(
                request.getSarId(),
                "SAR FILING URGENT",
                "SAR " + request.getSarId() + " due in " + hoursRemaining + " hours",
                request.getPriority()
            );
            
        } catch (Exception e) {
            log.error("Failed to escalate urgent SAR", e);
        }
    }

    private void escalatePendingApproval(SARFilingRequest request) {
        try {
            complianceNotificationService.sendApprovalEscalation(
                request.getSarId(),
                "CRITICAL SAR PENDING APPROVAL",
                "Critical SAR " + request.getSarId() + " requires immediate approval",
                request.getDueDate()
            );
            
        } catch (Exception e) {
            log.error("Failed to escalate pending approval", e);
        }
    }

    private boolean requiresRegulatoryNotification(SARFilingRequest request) {
        return "EMERGENCY".equals(request.getPriority()) ||
               (request.getSuspiciousAmount() != null && 
                request.getSuspiciousAmount().compareTo(new BigDecimal("1000000")) > 0) ||
               request.getSuspiciousActivity().contains("TERRORISM") ||
               request.getSuspiciousActivity().contains("SANCTIONS");
    }

    private void submitToAdditionalJurisdictions(SARFilingRequest request) {
        try {
            // Submit to additional jurisdictions if multi-jurisdictional case
            if (request.getRelatedCases() != null) {
                for (Map.Entry<String, String> entry : request.getRelatedCases().entrySet()) {
                    if (entry.getKey().startsWith("JURISDICTION_")) {
                        String jurisdiction = entry.getValue();
                        if (!jurisdiction.equals(request.getJurisdiction())) {
                            regulatoryFilingService.submitToAdditionalJurisdiction(
                                request.getSarId(),
                                jurisdiction,
                                entry.getKey()
                            );
                            multiJurisdictionSARs.increment();
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to submit to additional jurisdictions", e);
        }
    }

    /**
     * SAR filing request data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class SARFilingRequest {
        private String sarId;
        private String priority;
        private String filingType;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
        private String customerId;
        private String accountId;
        private String transactionId;
        private BigDecimal suspiciousAmount;
        private String currency;
        private String suspiciousActivity;
        private String narrativeDescription;
        private LocalDateTime reportingDate;
        private LocalDateTime dueDate;
        private String jurisdiction;
        private String regulatoryBody;
        private Map<String, String> relatedCases;
    }

    /**
     * Custom exception for SAR filing processing
     */
    public static class SARFilingProcessingException extends RuntimeException {
        public SARFilingProcessingException(String message) {
            super(message);
        }
        
        public SARFilingProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}