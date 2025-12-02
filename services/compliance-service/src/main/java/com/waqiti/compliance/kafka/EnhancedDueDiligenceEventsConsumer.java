package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.EnhancedDueDiligenceService;
import com.waqiti.compliance.service.HighRiskCustomerService;
import com.waqiti.compliance.service.SanctionsScreeningService;
import com.waqiti.compliance.service.DocumentVerificationService;
import com.waqiti.compliance.service.AuditService;
import com.waqiti.compliance.entity.EDDInvestigation;
import com.waqiti.compliance.entity.RiskAssessment;
import com.waqiti.compliance.entity.DocumentVerification;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #8: Enhanced Due Diligence Events Consumer
 * Processes enhanced due diligence investigations, high-risk customer reviews, and compliance assessments
 * Implements 12-step zero-tolerance processing for EDD compliance
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnhancedDueDiligenceEventsConsumer extends BaseKafkaConsumer {

    private final EnhancedDueDiligenceService eddService;
    private final HighRiskCustomerService highRiskCustomerService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final DocumentVerificationService documentVerificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "enhanced-due-diligence-events", 
        groupId = "enhanced-due-diligence-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "enhanced-due-diligence-consumer")
    @Retry(name = "enhanced-due-diligence-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleEnhancedDueDiligenceEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "enhanced-due-diligence-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing enhanced due diligence event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String triggerReason = eventData.path("triggerReason").asText(); // HIGH_RISK_COUNTRY, LARGE_TRANSACTION, PEP_MATCH, SANCTIONS_ALERT
            String customerType = eventData.path("customerType").asText(); // INDIVIDUAL, CORPORATE, TRUST, NGO
            BigDecimal relationshipValue = new BigDecimal(eventData.path("relationshipValue").asText());
            String sourceOfFunds = eventData.path("sourceOfFunds").asText();
            String sourceOfWealth = eventData.path("sourceOfWealth").asText();
            String businessPurpose = eventData.path("businessPurpose").asText();
            String geographicRisk = eventData.path("geographicRisk").asText(); // HIGH, MEDIUM, LOW
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            int currentRiskScore = eventData.path("currentRiskScore").asInt();
            
            log.info("Step 2: Extracted EDD details: customerId={}, trigger={}, type={}, value={}, risk={}", 
                    customerId, triggerReason, customerType, relationshipValue, currentRiskScore);
            
            // Step 3: EDD investigation initiation and case setup
            log.info("Step 3: Initiating EDD investigation and establishing case parameters");
            EDDInvestigation investigation = eddService.createEDDInvestigation(eventData);
            
            eddService.validateEDDTrigger(triggerReason);
            eddService.validateCustomerType(customerType);
            eddService.establishInvestigationScope(investigation);
            eddService.assignInvestigationTeam(investigation);
            
            if (!eddService.isValidTriggerReason(triggerReason)) {
                throw new IllegalStateException("Invalid EDD trigger reason: " + triggerReason);
            }
            
            eddService.setInvestigationPriority(investigation, currentRiskScore);
            
            // Step 4: Customer background and profile analysis
            log.info("Step 4: Conducting comprehensive customer background analysis");
            RiskAssessment riskAssessment = eddService.createRiskAssessment(investigation);
            
            eddService.analyzeCustomerBackground(customerId, riskAssessment);
            eddService.validateCustomerInformation(customerId);
            eddService.assessReputationalRisk(customerId, riskAssessment);
            eddService.analyzeBusinessActivities(customerId, businessPurpose);
            
            highRiskCustomerService.evaluateHighRiskIndicators(customerId, riskAssessment);
            highRiskCustomerService.assessPoliticalExposure(customerId);
            highRiskCustomerService.analyzeAdverseMedia(customerId, riskAssessment);
            
            // Step 5: Enhanced sanctions and PEP screening
            log.info("Step 5: Conducting enhanced sanctions and PEP screening procedures");
            sanctionsScreeningService.performEnhancedScreening(customerId, investigation);
            sanctionsScreeningService.screenBeneficialOwners(customerId);
            sanctionsScreeningService.screenAssociatedEntities(customerId);
            
            boolean sanctionsMatch = sanctionsScreeningService.detectSanctionsMatch(customerId);
            boolean pepMatch = sanctionsScreeningService.detectPEPMatch(customerId);
            
            if (sanctionsMatch || pepMatch) {
                eddService.escalateToCompliance(investigation);
                eddService.requireSeniorApproval(investigation);
            }
            
            sanctionsScreeningService.documentScreeningResults(investigation);
            
            // Step 6: Source of funds and wealth verification
            log.info("Step 6: Verifying source of funds and wealth documentation");
            DocumentVerification docVerification = documentVerificationService.createDocumentVerification(investigation);
            
            documentVerificationService.verifySourceOfFunds(customerId, sourceOfFunds, docVerification);
            documentVerificationService.verifySourceOfWealth(customerId, sourceOfWealth, docVerification);
            documentVerificationService.validateSupportingDocuments(customerId, docVerification);
            
            if ("CORPORATE".equals(customerType)) {
                documentVerificationService.verifyOwnershipStructure(customerId, docVerification);
                documentVerificationService.validateBeneficialOwnership(customerId, docVerification);
            }
            
            documentVerificationService.assessDocumentAuthenticity(docVerification);
            
            // Step 7: Geographic and jurisdictional risk assessment
            log.info("Step 7: Assessing geographic and jurisdictional risk factors");
            eddService.analyzeGeographicRisk(customerId, geographicRisk, riskAssessment);
            eddService.assessJurisdictionalRisk(customerId, riskAssessment);
            eddService.evaluateCountryRiskFactors(customerId, riskAssessment);
            
            if ("HIGH".equals(geographicRisk)) {
                eddService.requireAdditionalDocumentation(investigation);
                eddService.implementEnhancedMonitoring(customerId);
            }
            
            eddService.updateGeographicRiskScore(riskAssessment, geographicRisk);
            
            // Step 8: Transaction pattern and behavior analysis
            log.info("Step 8: Analyzing transaction patterns and behavioral indicators");
            eddService.analyzeTransactionPatterns(customerId, investigation);
            eddService.assessTransactionComplexity(customerId);
            eddService.evaluateUnusualActivity(customerId, riskAssessment);
            
            eddService.compareWithPeerProfiles(customerId, customerType);
            eddService.identifyAnomalousTransactions(customerId, investigation);
            
            if (eddService.detectSuspiciousPatterns(customerId)) {
                eddService.flagForSARConsideration(investigation);
            }
            
            // Step 9: Business relationship and purpose validation
            log.info("Step 9: Validating business relationship and purpose justification");
            eddService.validateBusinessRelationship(customerId, businessPurpose);
            eddService.assessExpectedActivity(customerId, relationshipValue);
            eddService.evaluateBusinessJustification(customerId, businessPurpose);
            
            if (!eddService.isBusinessPurposeValid(businessPurpose, customerType)) {
                eddService.requireBusinessJustification(investigation);
                eddService.escalateToRelationshipManager(investigation);
            }
            
            eddService.updateRelationshipRisk(customerId, riskAssessment);
            
            // Step 10: Risk rating calculation and decision making
            log.info("Step 10: Calculating comprehensive risk rating and making decisions");
            int updatedRiskScore = eddService.calculateEnhancedRiskScore(riskAssessment);
            String riskRating = eddService.determineRiskRating(updatedRiskScore);
            
            investigation.setFinalRiskScore(updatedRiskScore);
            investigation.setRiskRating(riskRating);
            
            eddService.makeEDDDecision(investigation);
            eddService.documentDecisionRationale(investigation);
            
            if (updatedRiskScore >= 85) {
                eddService.recommendAccountClosure(investigation);
            } else if (updatedRiskScore >= 70) {
                eddService.requireOngoingMonitoring(investigation);
            }
            
            // Step 11: Regulatory reporting and documentation
            log.info("Step 11: Generating regulatory reports and compliance documentation");
            eddService.generateEDDReport(investigation);
            eddService.documentComplianceFindings(investigation);
            eddService.updateCustomerRiskProfile(customerId, riskAssessment);
            
            if (eddService.requiresRegulatoryReporting(investigation)) {
                eddService.generateRegulatoryReport(investigation);
                eddService.notifyRegulators(investigation);
            }
            
            eddService.archiveInvestigationDocuments(investigation);
            
            // Step 12: Audit trail and case closure
            log.info("Step 12: Completing audit trail and finalizing case documentation");
            auditService.logEDDInvestigation(investigation);
            auditService.logRiskAssessment(riskAssessment);
            auditService.logDocumentVerification(docVerification);
            
            eddService.updateEDDMetrics(investigation);
            highRiskCustomerService.updateRiskStatistics(customerId);
            
            auditService.generateEDDReport(investigation);
            auditService.updateRegulatoryReporting(investigation);
            
            eddService.closeInvestigation(investigation);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed EDD event: customerId={}, eventId={}, finalRisk={}", 
                    customerId, eventId, riskRating);
            
        } catch (Exception e) {
            log.error("Error processing enhanced due diligence event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("triggerReason") || !eventData.has("customerType") ||
            !eventData.has("relationshipValue") || !eventData.has("sourceOfFunds") ||
            !eventData.has("sourceOfWealth") || !eventData.has("businessPurpose") ||
            !eventData.has("geographicRisk") || !eventData.has("timestamp") ||
            !eventData.has("currentRiskScore")) {
            throw new IllegalArgumentException("Invalid enhanced due diligence event structure");
        }
    }
}