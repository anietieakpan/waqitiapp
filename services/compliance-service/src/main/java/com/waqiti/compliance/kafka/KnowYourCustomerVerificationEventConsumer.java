package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.KYCService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.CaseManagementService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #180: Know Your Customer Verification Event Consumer
 * Processes CIP verification and Enhanced Due Diligence (EDD)
 * Implements 12-step zero-tolerance processing for secure KYC compliance workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowYourCustomerVerificationEventConsumer extends BaseKafkaConsumer {

    private final KYCService kycService;
    private final ComplianceAuditService auditService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final CaseManagementService caseManagementService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "know-your-customer-verification-events", groupId = "know-your-customer-verification-group")
    @CircuitBreaker(name = "know-your-customer-verification-consumer")
    @Retry(name = "know-your-customer-verification-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleKnowYourCustomerVerificationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "know-your-customer-verification-event");
        
        try {
            log.info("Step 1: Processing KYC verification event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String kycId = eventData.path("kycId").asText();
            String customerId = eventData.path("customerId").asText();
            String verificationType = eventData.path("verificationType").asText(); // CIP, EDD, SIMPLIFIED_DD
            String customerType = eventData.path("customerType").asText(); // INDIVIDUAL, CORPORATE, NON_PROFIT
            String riskCategory = eventData.path("riskCategory").asText(); // LOW, MEDIUM, HIGH, PROHIBITED
            List<String> documentTypes = objectMapper.convertValue(
                eventData.path("documentTypes"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String accountType = eventData.path("accountType").asText();
            String onboardingChannel = eventData.path("onboardingChannel").asText(); // BRANCH, ONLINE, MOBILE
            String jurisdiction = eventData.path("jurisdiction").asText();
            boolean isPEP = eventData.path("isPEP").asBoolean();
            boolean isHighRisk = eventData.path("isHighRisk").asBoolean();
            String expectedActivity = eventData.path("expectedActivity").asText();
            Map<String, Object> customerData = objectMapper.convertValue(
                eventData.path("customerData"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            List<String> beneficialOwners = objectMapper.convertValue(
                eventData.path("beneficialOwners"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String complianceOfficerId = eventData.path("complianceOfficerId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted KYC verification details: kycId={}, customer={}, type={}, risk={}", 
                    kycId, customerId, verificationType, riskCategory);
            
            // Step 3: Validate KYC regulatory requirements and CIP compliance
            kycService.validateKYCRegulatoryRequirements(
                verificationType, customerType, jurisdiction, accountType, 
                onboardingChannel, timestamp);
            
            log.info("Step 3: Validated KYC regulatory requirements and CIP compliance");
            
            // Step 4: Perform Customer Identification Program (CIP) verification
            ComplianceDecision cipVerification = kycService.performCIPVerification(
                kycId, customerId, customerData, documentTypes, 
                customerType, jurisdiction, timestamp);
            
            log.info("Step 4: Completed CIP verification: decision={}, confidence={}", 
                    cipVerification.getDecision(), cipVerification.getConfidenceScore());
            
            // Step 5: Conduct identity document verification and authentication
            Map<String, Object> documentVerification = kycService.verifyIdentityDocuments(
                kycId, customerId, documentTypes, customerData, timestamp);
            
            log.info("Step 5: Completed identity document verification");
            
            // Step 6: Perform beneficial ownership identification (if applicable)
            ComplianceDecision beneficialOwnershipVerification = null;
            if ("CORPORATE".equals(customerType) && !beneficialOwners.isEmpty()) {
                beneficialOwnershipVerification = kycService.verifyBeneficialOwnership(
                    kycId, customerId, beneficialOwners, customerData, timestamp);
                
                log.info("Step 6: Completed beneficial ownership verification: decision={}", 
                        beneficialOwnershipVerification.getDecision());
            } else {
                log.info("Step 6: Beneficial ownership verification not required");
            }
            
            // Step 7: Conduct Enhanced Due Diligence for high-risk customers
            ComplianceDecision eddVerification = null;
            if (isHighRisk || isPEP || "HIGH".equals(riskCategory) || "EDD".equals(verificationType)) {
                eddVerification = kycService.performEnhancedDueDiligence(
                    kycId, customerId, customerData, riskCategory, 
                    isPEP, expectedActivity, timestamp);
                
                log.info("Step 7: Completed Enhanced Due Diligence: decision={}", 
                        eddVerification.getDecision());
            } else {
                log.info("Step 7: Enhanced Due Diligence not required");
            }
            
            // Step 8: Consolidate KYC results and determine overall compliance status
            ComplianceDecision overallKYCDecision = kycService.consolidateKYCResults(
                kycId, cipVerification, documentVerification, 
                beneficialOwnershipVerification, eddVerification, timestamp);
            
            log.info("Step 8: Consolidated KYC results: overall decision={}, score={}", 
                    overallKYCDecision.getDecision(), overallKYCDecision.getConfidenceScore());
            
            // Step 9: Update customer risk profile and monitoring requirements
            CustomerRiskProfile riskProfile = kycService.updateCustomerRiskProfile(
                customerId, overallKYCDecision, riskCategory, isPEP, 
                isHighRisk, expectedActivity, timestamp);
            
            log.info("Step 9: Updated customer risk profile: riskLevel={}", 
                    riskProfile.getRiskLevel());
            
            // Step 10: Handle KYC decision outcomes and customer status
            if ("APPROVED".equals(overallKYCDecision.getDecision())) {
                kycService.approveCustomerOnboarding(customerId, kycId, riskProfile, timestamp);
                
                log.info("Step 10: Customer onboarding approved");
            } else if ("REJECTED".equals(overallKYCDecision.getDecision())) {
                kycService.rejectCustomerOnboarding(customerId, kycId, 
                    overallKYCDecision.getRejectionReason(), timestamp);
                
                log.info("Step 10: Customer onboarding rejected: reason={}", 
                        overallKYCDecision.getRejectionReason());
            } else if ("MANUAL_REVIEW".equals(overallKYCDecision.getDecision())) {
                String caseId = caseManagementService.createKYCReviewCase(
                    kycId, customerId, overallKYCDecision, complianceOfficerId, timestamp);
                
                log.info("Step 10: Created manual review case: caseId={}", caseId);
            }
            
            // Step 11: Generate KYC compliance notifications and alerts
            kycService.sendKYCComplianceNotifications(
                kycId, customerId, overallKYCDecision, complianceOfficerId, timestamp);
            
            // Notify senior compliance for high-risk approvals
            if ("APPROVED".equals(overallKYCDecision.getDecision()) && 
                ("HIGH".equals(riskCategory) || isPEP)) {
                kycService.notifySeniorCompliance(
                    kycId, customerId, riskProfile, overallKYCDecision, timestamp);
                
                log.info("Step 11: Notified senior compliance for high-risk approval");
            }
            
            // Step 12: Log KYC verification for audit trail and regulatory examination
            auditService.logKYCVerificationEvent(
                kycId, customerId, verificationType, customerType, 
                overallKYCDecision.getDecision(), riskCategory, isPEP, 
                isHighRisk, complianceOfficerId, timestamp);
            
            regulatoryReportingService.generateKYCComplianceReports(
                overallKYCDecision, riskProfile, kycId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed KYC verification event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing KYC verification event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("kycId") || 
            !eventData.has("customerId") || !eventData.has("verificationType") ||
            !eventData.has("customerType") || !eventData.has("riskCategory") ||
            !eventData.has("documentTypes") || !eventData.has("accountType") ||
            !eventData.has("onboardingChannel") || !eventData.has("jurisdiction") ||
            !eventData.has("expectedActivity") || !eventData.has("customerData") ||
            !eventData.has("complianceOfficerId") || !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid KYC verification event structure");
        }
    }
}