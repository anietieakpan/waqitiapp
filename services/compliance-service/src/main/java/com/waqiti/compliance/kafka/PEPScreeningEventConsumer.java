package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.service.OFACSanctionsScreeningService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.EnhancedMonitoringService;
import com.waqiti.compliance.entity.CustomerRiskProfile;
import com.waqiti.compliance.entity.ComplianceDecision;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #181: PEP Screening Event Consumer
 * Processes Politically Exposed Persons detection and enhanced monitoring
 * Implements 12-step zero-tolerance processing for secure PEP compliance workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PEPScreeningEventConsumer extends BaseKafkaConsumer {

    private final OFACSanctionsScreeningService pepScreeningService;
    private final ComplianceAuditService auditService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final EnhancedMonitoringService enhancedMonitoringService;
    private final UniversalDLQHandler universalDLQHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "pep-screening-events", groupId = "pep-screening-group")
    @CircuitBreaker(name = "pep-screening-consumer")
    @Retry(name = "pep-screening-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePEPScreeningEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "pep-screening-event");
        
        try {
            log.info("Step 1: Processing PEP screening event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String screeningId = eventData.path("screeningId").asText();
            String customerId = eventData.path("customerId").asText();
            String customerName = eventData.path("customerName").asText();
            String nationality = eventData.path("nationality").asText();
            String countryOfResidence = eventData.path("countryOfResidence").asText();
            String dateOfBirth = eventData.path("dateOfBirth").asText();
            String position = eventData.path("position").asText();
            String organization = eventData.path("organization").asText();
            List<String> aliases = objectMapper.convertValue(
                eventData.path("aliases"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            List<String> familyMembers = objectMapper.convertValue(
                eventData.path("familyMembers"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            List<String> closeAssociates = objectMapper.convertValue(
                eventData.path("closeAssociates"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String pepCategory = eventData.path("pepCategory").asText(); // FOREIGN_PEP, DOMESTIC_PEP, INTERNATIONAL_ORG
            String riskLevel = eventData.path("riskLevel").asText();
            String complianceOfficerId = eventData.path("complianceOfficerId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted PEP screening details: screeningId={}, customer={}, category={}, risk={}", 
                    screeningId, customerName, pepCategory, riskLevel);
            
            // Step 3: Validate PEP screening requirements and regulatory obligations
            pepScreeningService.validatePEPScreeningRequirements(
                pepCategory, nationality, countryOfResidence, position, organization, timestamp);
            
            log.info("Step 3: Validated PEP screening requirements and regulatory obligations");
            
            // Step 4: Conduct comprehensive PEP database screening
            ComplianceDecision pepDatabaseResult = pepScreeningService.screenAgainstPEPDatabases(
                screeningId, customerName, aliases, nationality, dateOfBirth, 
                position, organization, pepCategory, timestamp);
            
            log.info("Step 4: Completed PEP database screening: decision={}, confidence={}", 
                    pepDatabaseResult.getDecision(), pepDatabaseResult.getConfidenceScore());
            
            // Step 5: Screen family members and close associates (RCA screening)
            Map<String, Object> rcaScreeningResults = pepScreeningService.screenRelatedAndCloseAssociates(
                screeningId, familyMembers, closeAssociates, pepCategory, timestamp);
            
            log.info("Step 5: Completed RCA screening for {} family members and {} associates", 
                    familyMembers.size(), closeAssociates.size());
            
            // Step 6: Analyze PEP risk factors and exposure assessment
            Map<String, Object> riskAssessment = pepScreeningService.analyzePEPRiskFactors(
                screeningId, pepCategory, position, organization, nationality, 
                countryOfResidence, rcaScreeningResults, timestamp);
            
            log.info("Step 6: Completed PEP risk factor analysis");
            
            // Step 7: Determine PEP classification and monitoring requirements
            ComplianceDecision pepClassification = pepScreeningService.determinePEPClassification(
                screeningId, pepDatabaseResult, riskAssessment, pepCategory, riskLevel, timestamp);
            
            log.info("Step 7: Determined PEP classification: status={}, monitoringLevel={}", 
                    pepClassification.getDecision(), pepClassification.getMonitoringLevel());
            
            // Step 8: Activate enhanced due diligence for confirmed PEPs
            if ("PEP_CONFIRMED".equals(pepClassification.getDecision())) {
                enhancedMonitoringService.activatePEPEnhancedDueDiligence(
                    customerId, screeningId, pepCategory, riskLevel, timestamp);
                
                log.info("Step 8: Activated enhanced due diligence for confirmed PEP");
            } else {
                log.info("Step 8: No enhanced due diligence required - PEP not confirmed");
            }
            
            // Step 9: Update customer risk profile and set monitoring parameters
            CustomerRiskProfile updatedProfile = enhancedMonitoringService.updateCustomerRiskProfileForPEP(
                customerId, pepClassification, riskAssessment, timestamp);
            
            log.info("Step 9: Updated customer risk profile: newRiskLevel={}", 
                    updatedProfile.getRiskLevel());
            
            // Step 10: Implement transaction monitoring enhancements
            if ("PEP_CONFIRMED".equals(pepClassification.getDecision()) || 
                "PEP_POTENTIAL".equals(pepClassification.getDecision())) {
                
                enhancedMonitoringService.implementPEPTransactionMonitoring(
                    customerId, pepCategory, pepClassification.getMonitoringLevel(), timestamp);
                
                log.info("Step 10: Implemented enhanced transaction monitoring for PEP");
            }
            
            // Step 11: Generate PEP compliance notifications and senior management alerts
            pepScreeningService.sendPEPComplianceNotifications(
                screeningId, customerId, pepClassification, complianceOfficerId, timestamp);
            
            // Notify senior management for high-risk PEPs
            if ("PEP_CONFIRMED".equals(pepClassification.getDecision()) && "HIGH".equals(riskLevel)) {
                pepScreeningService.notifySeniorManagementPEP(
                    screeningId, customerId, pepCategory, position, organization, timestamp);
                
                log.info("Step 11: Notified senior management for high-risk PEP");
            }
            
            // Step 12: Log PEP screening for audit trail and regulatory examination
            auditService.logPEPScreeningEvent(
                screeningId, customerId, customerName, pepCategory, 
                pepClassification.getDecision(), riskLevel, position, 
                organization, complianceOfficerId, timestamp);
            
            regulatoryReportingService.generatePEPScreeningReports(
                pepClassification, updatedProfile, screeningId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed PEP screening event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing PEP screening event: {}", e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                universalDLQHandler.handleFailedMessage(record, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send PEP screening event to DLQ", dlqEx);
            }

            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("screeningId") || 
            !eventData.has("customerId") || !eventData.has("customerName") ||
            !eventData.has("nationality") || !eventData.has("countryOfResidence") ||
            !eventData.has("dateOfBirth") || !eventData.has("position") ||
            !eventData.has("organization") || !eventData.has("pepCategory") ||
            !eventData.has("riskLevel") || !eventData.has("complianceOfficerId") ||
            !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid PEP screening event structure");
        }
    }
}