package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.ConsentManagementService;
import com.waqiti.customer.service.GDPRComplianceService;
import com.waqiti.customer.service.PrivacyPreferenceService;
import com.waqiti.customer.service.DataProcessingService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.ConsentRecord;
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
import java.util.UUID;

/**
 * Critical Event Consumer #222: Customer Consent Management Event Consumer
 * Processes GDPR consent and privacy settings with data protection compliance
 * Implements 12-step zero-tolerance processing for consent management lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerConsentManagementEventConsumer extends BaseKafkaConsumer {

    private final ConsentManagementService consentService;
    private final GDPRComplianceService gdprService;
    private final PrivacyPreferenceService privacyService;
    private final DataProcessingService dataProcessingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-consent-management-events", groupId = "customer-consent-management-group")
    @CircuitBreaker(name = "customer-consent-management-consumer")
    @Retry(name = "customer-consent-management-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerConsentManagementEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-consent-management-event");
        
        try {
            log.info("Step 1: Processing customer consent management event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String consentType = eventData.path("consentType").asText();
            String consentAction = eventData.path("consentAction").asText(); // GRANT, REVOKE, UPDATE
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String dataProcessingPurpose = eventData.path("dataProcessingPurpose").asText();
            List<String> dataCategories = objectMapper.convertValue(eventData.path("dataCategories"), List.class);
            String legalBasis = eventData.path("legalBasis").asText();
            boolean isExplicitConsent = eventData.path("isExplicitConsent").asBoolean();
            
            log.info("Step 2: Extracted consent details: customerId={}, consentType={}, action={}, legalBasis={}", 
                    customerId, consentType, consentAction, legalBasis);
            
            // Step 3: Customer identification and consent capability verification
            log.info("Step 3: Verifying customer identity and consent capability");
            Customer customer = consentService.getCustomerById(customerId);
            consentService.verifyConsentCapability(customer);
            consentService.validateCustomerAge(customer);
            
            // Step 4: GDPR compliance validation and legal basis verification
            log.info("Step 4: Validating GDPR compliance and legal basis");
            gdprService.validateGDPRApplicability(customer);
            gdprService.verifyLegalBasisForProcessing(legalBasis, consentType);
            gdprService.checkDataMinimizationPrinciple(dataCategories, dataProcessingPurpose);
            gdprService.validateConsentSpecificity(consentType, dataProcessingPurpose);
            
            // Step 5: Consent record creation and validation
            log.info("Step 5: Creating and validating consent records");
            ConsentRecord consentRecord = consentService.createConsentRecord(
                customer, consentType, consentAction, dataProcessingPurpose, 
                dataCategories, legalBasis, isExplicitConsent, timestamp
            );
            consentService.validateConsentFormat(consentRecord);
            consentService.checkConsentGranularity(consentRecord);
            
            // Step 6: Consent processing and data permission updates
            log.info("Step 6: Processing consent and updating data permissions");
            switch (consentAction) {
                case "GRANT":
                    consentService.grantConsent(consentRecord);
                    dataProcessingService.enableDataProcessing(customer, dataCategories, dataProcessingPurpose);
                    break;
                case "REVOKE":
                    consentService.revokeConsent(customer, consentType);
                    dataProcessingService.disableDataProcessing(customer, dataCategories, dataProcessingPurpose);
                    break;
                case "UPDATE":
                    consentService.updateConsent(consentRecord);
                    dataProcessingService.updateDataProcessingPermissions(customer, consentRecord);
                    break;
            }
            
            // Step 7: Privacy preference configuration and data rights management
            log.info("Step 7: Configuring privacy preferences and data rights");
            privacyService.updatePrivacyPreferences(customer, consentRecord);
            privacyService.configureCookieSettings(customer, consentType);
            privacyService.updateMarketingPreferences(customer, consentRecord);
            gdprService.updateDataSubjectRights(customer, consentRecord);
            
            // Step 8: Third-party sharing and processing notifications
            log.info("Step 8: Managing third-party sharing and processing notifications");
            consentService.updateThirdPartySharing(customer, consentRecord);
            consentService.notifyDataProcessors(customer, consentAction, dataCategories);
            consentService.updateCrossBorderTransferPermissions(customer, consentRecord);
            
            // Step 9: Consent documentation and customer communication
            log.info("Step 9: Generating consent documentation and customer communication");
            consentService.generateConsentConfirmation(customer, consentRecord);
            consentService.updatePrivacyNotice(customer);
            consentService.sendConsentConfirmationEmail(customer, consentRecord);
            if ("REVOKE".equals(consentAction)) {
                consentService.informDataDeletionRights(customer);
            }
            
            // Step 10: Regulatory compliance and audit trail creation
            log.info("Step 10: Ensuring regulatory compliance and creating audit trails");
            gdprService.recordConsentAuditTrail(consentRecord);
            gdprService.updateDPIAIfRequired(customer, consentRecord);
            gdprService.checkDataRetentionCompliance(customer, consentRecord);
            consentService.updateConsentRegister(consentRecord);
            
            // Step 11: Data processing impact assessment and archival
            log.info("Step 11: Conducting data processing impact assessment and archival");
            gdprService.assessDataProcessingImpact(customer, consentRecord);
            consentService.archiveConsentHistory(customer, consentRecord);
            consentService.updateConsentMetrics(consentType, consentAction);
            gdprService.scheduleConsentRenewalIfRequired(customer, consentRecord);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed customer consent management event: customerId={}, eventId={}", customerId, eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer consent management event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("consentType") || !eventData.has("consentAction") ||
            !eventData.has("dataProcessingPurpose") || !eventData.has("legalBasis") ||
            !eventData.has("dataCategories")) {
            throw new IllegalArgumentException("Invalid customer consent management event structure");
        }
    }
}