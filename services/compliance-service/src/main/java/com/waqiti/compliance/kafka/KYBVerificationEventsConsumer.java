package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.KYBVerificationService;
import com.waqiti.compliance.service.ComplianceScreeningService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.service.BusinessRiskService;
import com.waqiti.common.exception.ComplianceProcessingException;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for KYB (Know Your Business) Verification Events
 * Handles business verification, corporate structure validation, and compliance screening
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KYBVerificationEventsConsumer {
    
    private final KYBVerificationService kybService;
    private final ComplianceScreeningService screeningService;
    private final ComplianceNotificationService notificationService;
    private final BusinessRiskService businessRiskService;
    private final UniversalDLQHandler universalDLQHandler;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"kyb-verification-events", "business-verification-initiated", "corporate-structure-validated", "kyb-verification-completed"},
        groupId = "compliance-service-kyb-verification-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleKYBVerificationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID verificationId = null;
        UUID businessId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            verificationId = UUID.fromString((String) event.get("verificationId"));
            businessId = UUID.fromString((String) event.get("businessId"));
            eventType = (String) event.get("eventType");
            String businessName = (String) event.get("businessName");
            String businessType = (String) event.get("businessType"); // LLC, CORPORATION, PARTNERSHIP, SOLE_PROPRIETORSHIP
            String industryCode = (String) event.get("industryCode"); // NAICS code
            String taxIdentificationNumber = (String) event.get("taxIdentificationNumber"); // EIN
            String businessAddress = (String) event.get("businessAddress");
            String jurisdictionOfIncorporation = (String) event.get("jurisdictionOfIncorporation");
            String registrationNumber = (String) event.get("registrationNumber");
            LocalDateTime incorporationDate = LocalDateTime.parse((String) event.get("incorporationDate"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Business details
            // SAFETY FIX: Safe parsing with null check to prevent NumberFormatException
            BigDecimal projectedAnnualRevenue = NullSafetyUtils.safeParseBigDecimal(
                event.get("projectedAnnualRevenue") != null ? (String) event.get("projectedAnnualRevenue") : null,
                BigDecimal.ZERO
            );
            Integer numberOfEmployees = (Integer) event.get("numberOfEmployees");
            String businessPurpose = (String) event.get("businessPurpose");
            Boolean isPubliclyTraded = (Boolean) event.getOrDefault("isPubliclyTraded", false);
            Boolean isRegulatedIndustry = (Boolean) event.getOrDefault("isRegulatedIndustry", false);
            String stockExchange = (String) event.get("stockExchange");
            String tickerSymbol = (String) event.get("tickerSymbol");

            // Ownership structure
            String ultimateBeneficialOwner = (String) event.get("ultimateBeneficialOwner");
            // SAFETY FIX: Safe parsing with null check to prevent NumberFormatException
            BigDecimal uboOwnershipPercentage = NullSafetyUtils.safeParseBigDecimal(
                event.get("uboOwnershipPercentage") != null ? (String) event.get("uboOwnershipPercentage") : null,
                BigDecimal.ZERO
            );
            Boolean hasComplexOwnership = (Boolean) event.getOrDefault("hasComplexOwnership", false);
            String ownershipStructure = (String) event.get("ownershipStructure");
            
            // Verification status
            String verificationStatus = (String) event.get("verificationStatus"); // PENDING, IN_PROGRESS, VERIFIED, FAILED
            String verificationLevel = (String) event.get("verificationLevel"); // BASIC, STANDARD, ENHANCED
            String failureReason = (String) event.get("failureReason");
            Boolean requiresEnhancedDueDiligence = (Boolean) event.getOrDefault("requiresEnhancedDueDiligence", false);
            
            log.info("Processing KYB verification event - VerificationId: {}, BusinessId: {}, Type: {}, Business: {}", 
                    verificationId, businessId, eventType, businessName);
            
            // Step 1: Validate business verification data
            Map<String, Object> validationResult = kybService.validateBusinessData(
                    verificationId, businessId, businessName, businessType, taxIdentificationNumber,
                    jurisdictionOfIncorporation, registrationNumber, incorporationDate, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                kybService.failVerification(verificationId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("KYB verification failed - Invalid data: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Business registry verification
            Map<String, Object> registryVerification = kybService.verifyBusinessRegistry(
                    businessName, registrationNumber, jurisdictionOfIncorporation,
                    incorporationDate, businessType, timestamp);
            
            if ("NOT_FOUND".equals(registryVerification.get("status"))) {
                kybService.escalateRegistryMismatch(verificationId, registryVerification, timestamp);
                log.warn("Business registry verification failed");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Industry risk assessment
            Map<String, Object> industryRiskAssessment = businessRiskService.assessIndustryRisk(
                    industryCode, businessPurpose, isRegulatedIndustry, projectedAnnualRevenue,
                    jurisdictionOfIncorporation, timestamp);
            
            String industryRiskLevel = (String) industryRiskAssessment.get("riskLevel");
            
            // Step 4: Ownership structure analysis
            Map<String, Object> ownershipAnalysis = kybService.analyzeOwnershipStructure(
                    verificationId, ultimateBeneficialOwner, uboOwnershipPercentage,
                    hasComplexOwnership, ownershipStructure, isPubliclyTraded, timestamp);
            
            Boolean ownershipRequiresEDD = (Boolean) ownershipAnalysis.get("requiresEDD");
            
            // Step 5: Process based on event type
            switch (eventType) {
                case "BUSINESS_VERIFICATION_INITIATED":
                    kybService.initiateBusinessVerification(verificationId, businessId, businessName,
                            businessType, industryCode, taxIdentificationNumber, businessAddress,
                            jurisdictionOfIncorporation, registrationNumber, verificationLevel, timestamp);
                    break;
                    
                case "CORPORATE_STRUCTURE_VALIDATED":
                    kybService.validateCorporateStructure(verificationId, businessId, ownershipStructure,
                            ultimateBeneficialOwner, uboOwnershipPercentage, hasComplexOwnership,
                            isPubliclyTraded, stockExchange, tickerSymbol, timestamp);
                    break;
                    
                case "KYB_VERIFICATION_COMPLETED":
                    kybService.completeVerification(verificationId, businessId, verificationStatus,
                            verificationLevel, industryRiskAssessment, ownershipAnalysis, timestamp);
                    break;
                    
                default:
                    kybService.processGenericKYBEvent(verificationId, eventType, event, timestamp);
            }
            
            // Step 6: Sanctions and PEP screening for business and UBOs
            Map<String, Object> sanctionsScreening = screeningService.performBusinessSanctionsScreening(
                    verificationId, businessName, ultimateBeneficialOwner, jurisdictionOfIncorporation,
                    registrationNumber, timestamp);
            
            if ("HIT".equals(sanctionsScreening.get("status"))) {
                kybService.handleSanctionsHit(verificationId, sanctionsScreening, timestamp);
                log.warn("Sanctions screening hit detected for business verification");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 7: Enhanced due diligence assessment
            if (requiresEnhancedDueDiligence || ownershipRequiresEDD || "HIGH".equals(industryRiskLevel)) {
                kybService.performEnhancedDueDiligence(verificationId, businessId, businessName,
                        ultimateBeneficialOwner, hasComplexOwnership, industryRiskAssessment,
                        ownershipAnalysis, timestamp);
            }
            
            // Step 8: Politically exposed persons (PEP) screening
            Map<String, Object> pepScreening = screeningService.performPEPScreeningForBusiness(
                    verificationId, ultimateBeneficialOwner, ownershipStructure, 
                    jurisdictionOfIncorporation, timestamp);
            
            Boolean isPEPBusiness = (Boolean) pepScreening.get("isPEP");
            
            // Step 9: Regulatory compliance validation
            if (isRegulatedIndustry) {
                Map<String, Object> regulatoryCompliance = kybService.validateRegulatoryCompliance(
                        verificationId, industryCode, jurisdictionOfIncorporation, businessType,
                        isPubliclyTraded, stockExchange, timestamp);
                
                if ("NON_COMPLIANT".equals(regulatoryCompliance.get("status"))) {
                    kybService.escalateRegulatoryIssue(verificationId, regulatoryCompliance, timestamp);
                }
            }
            
            // Step 10: Final risk rating calculation
            String finalRiskRating = businessRiskService.calculateFinalBusinessRiskRating(
                    industryRiskLevel, ownershipAnalysis, sanctionsScreening, pepScreening,
                    projectedAnnualRevenue, hasComplexOwnership, isRegulatedIndustry, timestamp);
            
            // Step 11: Verification decision and status update
            if ("KYB_VERIFICATION_COMPLETED".equals(eventType)) {
                kybService.finalizeVerificationDecision(verificationId, businessId, verificationStatus,
                        finalRiskRating, industryRiskAssessment, ownershipAnalysis, 
                        sanctionsScreening, pepScreening, timestamp);
            }
            
            // Step 12: Generate compliance reports
            kybService.generateKYBComplianceReport(verificationId, businessId, businessName,
                    verificationStatus, finalRiskRating, industryRiskLevel, isPEPBusiness,
                    requiresEnhancedDueDiligence, timestamp);
            
            // Step 13: Send verification notifications
            notificationService.sendKYBNotification(verificationId, businessId, eventType,
                    verificationStatus, businessName, finalRiskRating, timestamp);
            
            // Step 14: Audit logging
            auditService.auditFinancialEvent(
                    "KYB_VERIFICATION_EVENT_PROCESSED",
                    businessId.toString(),
                    String.format("KYB verification event processed - Type: %s, Business: %s, Status: %s, Risk: %s", 
                            eventType, businessName, verificationStatus, finalRiskRating),
                    Map.of(
                            "verificationId", verificationId.toString(),
                            "businessId", businessId.toString(),
                            "eventType", eventType,
                            "businessName", businessName,
                            "businessType", businessType,
                            "industryCode", industryCode,
                            "verificationStatus", verificationStatus,
                            "verificationLevel", verificationLevel,
                            "finalRiskRating", finalRiskRating,
                            "industryRiskLevel", industryRiskLevel,
                            "projectedAnnualRevenue", projectedAnnualRevenue.toString(),
                            "hasComplexOwnership", hasComplexOwnership.toString(),
                            "isPubliclyTraded", isPubliclyTraded.toString(),
                            "isRegulatedIndustry", isRegulatedIndustry.toString(),
                            "isPEPBusiness", isPEPBusiness.toString(),
                            "requiresEnhancedDueDiligence", requiresEnhancedDueDiligence.toString(),
                            "jurisdictionOfIncorporation", jurisdictionOfIncorporation,
                            "uboOwnershipPercentage", uboOwnershipPercentage.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed KYB verification event - VerificationId: {}, Status: {}, Risk: {}", 
                    verificationId, verificationStatus, finalRiskRating);
            
        } catch (Exception e) {
            log.error("KYB verification event processing failed - VerificationId: {}, BusinessId: {}, Error: {}",
                    verificationId, businessId, e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, String> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, String.valueOf(verificationId), eventJson);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send KYB verification event to DLQ: {}", verificationId, dlqEx);
            }

            throw new ComplianceProcessingException("KYB verification event processing failed", e);
        }
    }
}