package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.RegulatoryFilingService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.model.RegulatoryFiling;
import com.waqiti.compliance.model.RegulatoryFilingType;
import com.waqiti.compliance.model.RegulatoryFilingStatus;
import com.waqiti.compliance.model.RegulatoryFilingPriority;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Critical Event Consumer #69: Regulatory Reporting Event Consumer
 * Processes automated compliance reporting events with zero-tolerance for regulatory violations
 * Implements 12-step processing for critical regulatory filing and reporting requirements
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegulatoryReportingEventConsumer extends BaseKafkaConsumer {

    private final RegulatoryReportingService regulatoryReportingService;
    private final RegulatoryFilingService regulatoryFilingService;
    private final ComplianceAuditService complianceAuditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "regulatory-reporting-events", groupId = "regulatory-reporting-group")
    @CircuitBreaker(name = "regulatory-reporting-consumer")
    @Retry(name = "regulatory-reporting-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleRegulatoryReportingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "regulatory-reporting-event");
        MDC.put("partition", String.valueOf(record.partition()));
        MDC.put("offset", String.valueOf(record.offset()));
        
        try {
            log.info("Step 1: Processing regulatory reporting event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            // Step 2: Parse and validate event structure
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            // Step 3: Extract and validate event details
            String eventId = eventData.path("eventId").asText();
            String reportType = eventData.path("reportType").asText();
            String regulatorId = eventData.path("regulatorId").asText();
            String jurisdiction = eventData.path("jurisdiction").asText();
            String reportPeriod = eventData.path("reportPeriod").asText();
            String priority = eventData.path("priority").asText();
            LocalDateTime reportingDeadline = LocalDateTime.parse(eventData.path("reportingDeadline").asText());
            String triggeredBy = eventData.path("triggeredBy").asText();
            String entityId = eventData.path("entityId").asText();
            BigDecimal amount = eventData.has("amount") ? 
                    new BigDecimal(eventData.path("amount").asText()) : BigDecimal.ZERO;
            String currency = eventData.path("currency").asText("USD");
            String complianceLevel = eventData.path("complianceLevel").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            MDC.put("eventId", eventId);
            MDC.put("reportType", reportType);
            MDC.put("regulatorId", regulatorId);
            MDC.put("jurisdiction", jurisdiction);
            MDC.put("priority", priority);
            
            log.info("Step 2: Extracted regulatory reporting details: eventId={}, reportType={}, regulator={}, jurisdiction={}", 
                    eventId, reportType, regulatorId, jurisdiction);
            
            // Step 4: Validate regulatory compliance requirements
            validateRegulatoryRequirements(eventId, reportType, regulatorId, jurisdiction, complianceLevel);
            
            // Step 5: Check reporting deadline and urgency
            validateReportingDeadline(eventId, reportType, reportingDeadline, priority);
            
            // Step 6: Check idempotency to prevent duplicate processing
            if (regulatoryReportingService.isReportingEventProcessed(eventId)) {
                log.warn("Step 6: Regulatory reporting event already processed: eventId={}", eventId);
                ack.acknowledge();
                return;
            }
            
            // Step 7: Validate regulatory authority and jurisdiction
            validateRegulatoryAuthority(regulatorId, jurisdiction, reportType);
            
            // Step 8: Process regulatory filing based on report type
            processRegulatoryFiling(eventId, reportType, regulatorId, jurisdiction, reportPeriod, 
                    priority, reportingDeadline, triggeredBy, entityId, amount, currency, complianceLevel, timestamp);
            
            // Step 9: Generate compliance documentation
            generateComplianceDocumentation(eventId, reportType, regulatorId, jurisdiction, entityId, timestamp);
            
            // Step 10: Update regulatory audit trail
            updateRegulatoryAuditTrail(eventId, reportType, regulatorId, entityId, amount, currency, timestamp);
            
            // Step 11: Publish regulatory status events
            publishRegulatoryStatusEvents(eventId, reportType, regulatorId, jurisdiction, priority, timestamp);
            
            // Step 12: Complete processing and acknowledge
            regulatoryReportingService.markReportingEventProcessed(eventId);
            ack.acknowledge();
            
            log.info("Step 12: Successfully processed regulatory reporting event: eventId={}, reportType={}", 
                    eventId, reportType);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse regulatory reporting event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing regulatory reporting event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        log.info("Step 2a: Validating regulatory reporting event structure");
        
        if (!eventData.has("eventId") || eventData.path("eventId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventId in regulatory reporting event");
        }
        
        if (!eventData.has("reportType") || eventData.path("reportType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty reportType in regulatory reporting event");
        }
        
        if (!eventData.has("regulatorId") || eventData.path("regulatorId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty regulatorId in regulatory reporting event");
        }
        
        if (!eventData.has("jurisdiction") || eventData.path("jurisdiction").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty jurisdiction in regulatory reporting event");
        }
        
        if (!eventData.has("reportingDeadline") || eventData.path("reportingDeadline").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty reportingDeadline in regulatory reporting event");
        }
        
        if (!eventData.has("priority") || eventData.path("priority").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty priority in regulatory reporting event");
        }
        
        log.info("Step 2b: Regulatory reporting event structure validation successful");
    }

    private void validateRegulatoryRequirements(String eventId, String reportType, String regulatorId, 
                                              String jurisdiction, String complianceLevel) {
        log.info("Step 4: Validating regulatory requirements for eventId={}", eventId);
        
        try {
            // Validate report type is supported
            if (!regulatoryReportingService.isSupportedReportType(reportType)) {
                throw new IllegalArgumentException("Unsupported regulatory report type: " + reportType);
            }
            
            // Validate regulator authority
            if (!regulatoryReportingService.isAuthorizedRegulator(regulatorId, jurisdiction)) {
                throw new SecurityException("Unauthorized regulator for jurisdiction: " + regulatorId + " in " + jurisdiction);
            }
            
            // Validate compliance level requirements
            if (!regulatoryReportingService.validateComplianceLevel(reportType, complianceLevel)) {
                throw new IllegalArgumentException("Invalid compliance level for report type: " + complianceLevel);
            }
            
            // Check mandatory reporting requirements
            if (!regulatoryReportingService.checkMandatoryReportingRequirements(reportType, jurisdiction)) {
                throw new IllegalArgumentException("Mandatory reporting requirements not met for: " + reportType);
            }
            
            log.info("Step 4: Regulatory requirements validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 4: Regulatory requirements validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("Regulatory requirements validation failed: " + e.getMessage(), e);
        }
    }

    private void validateReportingDeadline(String eventId, String reportType, LocalDateTime reportingDeadline, 
                                         String priority) {
        log.info("Step 5: Validating reporting deadline for eventId={}", eventId);
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Check if deadline has already passed
            if (reportingDeadline.isBefore(now)) {
                log.error("Step 5: Reporting deadline has passed for eventId={}: deadline={}, current={}", 
                        eventId, reportingDeadline, now);
                // Still process but escalate urgency
                priority = "CRITICAL";
                regulatoryReportingService.escalateOverdueReporting(eventId, reportType, reportingDeadline);
            }
            
            // Calculate time remaining
            long hoursToDeadline = java.time.Duration.between(now, reportingDeadline).toHours();
            
            // Validate urgency based on time remaining
            if (hoursToDeadline <= 2 && !"CRITICAL".equals(priority)) {
                log.warn("Step 5: Reporting deadline within 2 hours, escalating priority: eventId={}", eventId);
                priority = "CRITICAL";
            } else if (hoursToDeadline <= 24 && !"HIGH".equals(priority) && !"CRITICAL".equals(priority)) {
                log.warn("Step 5: Reporting deadline within 24 hours, escalating priority: eventId={}", eventId);
                priority = "HIGH";
            }
            
            log.info("Step 5: Reporting deadline validation successful: eventId={}, hoursToDeadline={}, priority={}", 
                    eventId, hoursToDeadline, priority);
            
        } catch (Exception e) {
            log.error("Step 5: Reporting deadline validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new IllegalArgumentException("Reporting deadline validation failed: " + e.getMessage(), e);
        }
    }

    private void validateRegulatoryAuthority(String regulatorId, String jurisdiction, String reportType) {
        log.info("Step 7: Validating regulatory authority: regulatorId={}, jurisdiction={}", regulatorId, jurisdiction);
        
        try {
            // Validate regulator exists and is active
            if (!regulatoryReportingService.isActiveRegulator(regulatorId)) {
                throw new SecurityException("Inactive or unknown regulator: " + regulatorId);
            }
            
            // Validate jurisdiction authority
            if (!regulatoryReportingService.hasJurisdictionAuthority(regulatorId, jurisdiction)) {
                throw new SecurityException("Regulator lacks authority in jurisdiction: " + regulatorId + " in " + jurisdiction);
            }
            
            // Validate report type authority
            if (!regulatoryReportingService.hasReportTypeAuthority(regulatorId, reportType)) {
                throw new SecurityException("Regulator lacks authority for report type: " + regulatorId + " for " + reportType);
            }
            
            // Check for regulatory sanctions or restrictions
            if (regulatoryReportingService.hasRegulatorySanctions(regulatorId)) {
                throw new SecurityException("Regulator has active sanctions or restrictions: " + regulatorId);
            }
            
            log.info("Step 7: Regulatory authority validation successful: regulatorId={}", regulatorId);
            
        } catch (Exception e) {
            log.error("Step 7: Regulatory authority validation failed: regulatorId={}, error={}", regulatorId, e.getMessage(), e);
            throw new SecurityException("Regulatory authority validation failed: " + e.getMessage(), e);
        }
    }

    private void processRegulatoryFiling(String eventId, String reportType, String regulatorId, String jurisdiction,
                                       String reportPeriod, String priority, LocalDateTime reportingDeadline,
                                       String triggeredBy, String entityId, BigDecimal amount, String currency,
                                       String complianceLevel, LocalDateTime timestamp) {
        log.info("Step 8: Processing regulatory filing: eventId={}, reportType={}", eventId, reportType);
        
        try {
            RegulatoryFiling filing = RegulatoryFiling.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .filingType(RegulatoryFilingType.valueOf(reportType.toUpperCase().replace("-", "_")))
                    .regulatorId(regulatorId)
                    .jurisdiction(jurisdiction)
                    .reportPeriod(reportPeriod)
                    .priority(RegulatoryFilingPriority.valueOf(priority.toUpperCase()))
                    .status(RegulatoryFilingStatus.PENDING)
                    .reportingDeadline(reportingDeadline)
                    .triggeredBy(triggeredBy)
                    .entityId(entityId)
                    .amount(amount)
                    .currency(currency)
                    .complianceLevel(complianceLevel)
                    .filingDate(timestamp)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Process based on filing type
            switch (reportType.toUpperCase()) {
                case "SAR":
                case "SUSPICIOUS-ACTIVITY-REPORT":
                    regulatoryFilingService.processSARFiling(filing);
                    break;
                case "CTR":
                case "CURRENCY-TRANSACTION-REPORT":
                    regulatoryFilingService.processCTRFiling(filing);
                    break;
                case "FBAR":
                case "FOREIGN-BANK-ACCOUNT-REPORT":
                    regulatoryFilingService.processFBARFiling(filing);
                    break;
                case "BSA":
                case "BANK-SECRECY-ACT":
                    regulatoryFilingService.processBSAFiling(filing);
                    break;
                case "KYC-REPORT":
                    regulatoryFilingService.processKYCReportFiling(filing);
                    break;
                case "AML-REPORT":
                    regulatoryFilingService.processAMLReportFiling(filing);
                    break;
                case "SANCTIONS-REPORT":
                    regulatoryFilingService.processSanctionsReportFiling(filing);
                    break;
                case "REGULATORY-CAPITAL-REPORT":
                    regulatoryFilingService.processCapitalReportFiling(filing);
                    break;
                case "LIQUIDITY-REPORT":
                    regulatoryFilingService.processLiquidityReportFiling(filing);
                    break;
                case "STRESS-TEST-REPORT":
                    regulatoryFilingService.processStressTestReportFiling(filing);
                    break;
                default:
                    regulatoryFilingService.processGenericRegulatoryFiling(filing);
                    break;
            }
            
            // Update filing status
            filing.setStatus(RegulatoryFilingStatus.SUBMITTED);
            filing.setSubmittedAt(LocalDateTime.now());
            regulatoryFilingService.saveRegulatoryFiling(filing);
            
            log.info("Step 8: Regulatory filing processed successfully: eventId={}, filingId={}", 
                    eventId, filing.getId());
            
        } catch (Exception e) {
            log.error("Step 8: Regulatory filing processing failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("Regulatory filing processing failed: " + e.getMessage(), e);
        }
    }

    private void generateComplianceDocumentation(String eventId, String reportType, String regulatorId, 
                                               String jurisdiction, String entityId, LocalDateTime timestamp) {
        log.info("Step 9: Generating compliance documentation for eventId={}", eventId);
        
        try {
            // Generate regulatory report document
            String reportDocument = regulatoryReportingService.generateRegulatoryReport(
                    eventId, reportType, regulatorId, jurisdiction, entityId, timestamp
            );
            
            // Generate compliance certification
            String complianceCertification = regulatoryReportingService.generateComplianceCertification(
                    eventId, reportType, jurisdiction, timestamp
            );
            
            // Generate audit documentation
            String auditDocumentation = regulatoryReportingService.generateAuditDocumentation(
                    eventId, reportType, entityId, timestamp
            );
            
            // Store all documentation securely
            regulatoryReportingService.storeRegulatoryDocuments(
                    eventId, reportDocument, complianceCertification, auditDocumentation
            );
            
            log.info("Step 9: Compliance documentation generated successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 9: Compliance documentation generation failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for documentation issues, but log for manual review
        }
    }

    private void updateRegulatoryAuditTrail(String eventId, String reportType, String regulatorId, 
                                          String entityId, BigDecimal amount, String currency, LocalDateTime timestamp) {
        log.info("Step 10: Updating regulatory audit trail for eventId={}", eventId);
        
        try {
            complianceAuditService.recordRegulatoryFilingEvent(
                    eventId,
                    reportType,
                    regulatorId,
                    entityId,
                    amount,
                    currency,
                    timestamp,
                    "REGULATORY_FILING_PROCESSED"
            );
            
            // Create comprehensive audit record
            complianceAuditService.createComprehensiveAuditRecord(
                    eventId,
                    "REGULATORY_REPORTING",
                    reportType,
                    entityId,
                    amount,
                    currency,
                    regulatorId,
                    timestamp
            );
            
            // Update regulatory metrics
            regulatoryReportingService.updateRegulatoryMetrics(reportType, regulatorId, amount, timestamp);
            
            log.info("Step 10: Regulatory audit trail updated successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 10: Regulatory audit trail update failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for audit issues, but log for manual review
        }
    }

    private void publishRegulatoryStatusEvents(String eventId, String reportType, String regulatorId, 
                                             String jurisdiction, String priority, LocalDateTime timestamp) {
        log.info("Step 11: Publishing regulatory status events for eventId={}", eventId);
        
        try {
            // Publish filing completion event
            regulatoryReportingService.publishFilingCompletionEvent(
                    eventId, reportType, regulatorId, jurisdiction, timestamp
            );
            
            // Publish compliance status update
            regulatoryReportingService.publishComplianceStatusUpdate(
                    eventId, reportType, "COMPLIANT", timestamp
            );
            
            // If high priority, publish urgent notification
            if ("CRITICAL".equals(priority) || "HIGH".equals(priority)) {
                regulatoryReportingService.publishUrgentRegulatoryNotification(
                    eventId, reportType, regulatorId, priority, timestamp
                );
            }
            
            // Publish to regulatory monitoring systems
            regulatoryReportingService.publishToRegulatoryMonitoring(
                    eventId, reportType, regulatorId, jurisdiction, timestamp
            );
            
            log.info("Step 11: Regulatory status events published successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 11: Regulatory status event publishing failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for event publishing issues, but log for manual review
        }
    }
}