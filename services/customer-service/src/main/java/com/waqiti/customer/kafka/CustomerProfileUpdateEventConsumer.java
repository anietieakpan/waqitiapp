package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.CustomerProfileService;
import com.waqiti.customer.service.KYCRefreshService;
import com.waqiti.customer.service.ProfileChangeValidationService;
import com.waqiti.customer.service.ComplianceUpdateService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.ProfileUpdate;
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
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #219: Customer Profile Update Event Consumer
 * Processes profile changes and KYC refresh with GDPR compliance
 * Implements 12-step zero-tolerance processing for customer profile lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileUpdateEventConsumer extends BaseKafkaConsumer {

    private final CustomerProfileService profileService;
    private final KYCRefreshService kycRefreshService;
    private final ProfileChangeValidationService validationService;
    private final ComplianceUpdateService complianceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-profile-update-events", groupId = "customer-profile-update-group")
    @CircuitBreaker(name = "customer-profile-update-consumer")
    @Retry(name = "customer-profile-update-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerProfileUpdateEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-profile-update-event");
        
        try {
            log.info("Step 1: Processing customer profile update event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String updateType = eventData.path("updateType").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            Map<String, Object> previousValues = objectMapper.convertValue(eventData.path("previousValues"), Map.class);
            Map<String, Object> newValues = objectMapper.convertValue(eventData.path("newValues"), Map.class);
            String initiatedBy = eventData.path("initiatedBy").asText();
            
            log.info("Step 2: Extracted profile update details: customerId={}, updateType={}, initiatedBy={}", 
                    customerId, updateType, initiatedBy);
            
            // Step 3: Customer authentication and authorization
            log.info("Step 3: Validating customer identity and update authorization");
            Customer customer = profileService.getCustomerById(customerId);
            validationService.validateUpdateAuthorization(customer, updateType, initiatedBy);
            
            // Step 4: Profile change validation and business rules
            log.info("Step 4: Applying business rules and validation for profile changes");
            ProfileUpdate updateRequest = profileService.createProfileUpdate(customerId, previousValues, newValues, updateType);
            validationService.validateProfileChanges(updateRequest);
            boolean requiresManualApproval = validationService.checkIfManualApprovalRequired(updateRequest);
            
            // Step 5: GDPR and privacy compliance checks
            log.info("Step 5: Ensuring GDPR compliance and privacy protection");
            complianceService.validateGDPRCompliance(updateRequest);
            complianceService.checkDataProcessingConsent(customer, updateType);
            boolean requiresKYCRefresh = complianceService.determineKYCRefreshRequirement(updateRequest);
            
            // Step 6: Profile update processing
            log.info("Step 6: Processing approved profile changes");
            if (requiresManualApproval) {
                profileService.submitForManualApproval(updateRequest);
            } else {
                profileService.applyProfileChanges(updateRequest);
                profileService.updateCustomerRecord(customer, newValues);
            }
            
            // Step 7: External system synchronization
            log.info("Step 7: Synchronizing changes with external systems");
            profileService.updateCreditBureauRecords(customer, updateRequest);
            profileService.notifyCoreBankingSystem(customer, updateRequest);
            profileService.updateThirdPartyServices(customer, updateRequest);
            
            // Step 8: Risk reassessment and KYC refresh
            log.info("Step 8: Conducting risk reassessment and KYC refresh if required");
            if (requiresKYCRefresh) {
                kycRefreshService.initiateKYCRefresh(customer);
                kycRefreshService.updateRiskProfile(customer);
            }
            profileService.recalculateCustomerRiskScore(customer);
            
            // Step 9: Customer and stakeholder notifications
            log.info("Step 9: Sending notifications to customer and relevant stakeholders");
            profileService.notifyCustomerOfChanges(customer, updateRequest);
            if (updateRequest.isHighRiskChange()) {
                profileService.notifyComplianceTeam(customer, updateRequest);
            }
            profileService.notifyRelationshipManager(customer, updateRequest);
            
            // Step 10: Regulatory reporting and audit trail
            log.info("Step 10: Updating regulatory reports and compliance documentation");
            complianceService.updateRegulatoryReports(customer, updateRequest);
            complianceService.generateChangeAuditReport(updateRequest);
            complianceService.updateCustomerDueDiligenceFiles(customer);
            
            // Step 11: Data archival and version control
            log.info("Step 11: Archiving previous profile data and maintaining version history");
            profileService.archivePreviousProfileVersion(customer, previousValues, timestamp);
            profileService.createProfileChangeAuditTrail(updateRequest);
            profileService.updateProfileVersionHistory(customer);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed customer profile update: customerId={}, eventId={}", customerId, eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer profile update event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("updateType") || !eventData.has("previousValues") ||
            !eventData.has("newValues") || !eventData.has("initiatedBy")) {
            throw new IllegalArgumentException("Invalid customer profile update event structure");
        }
    }
}