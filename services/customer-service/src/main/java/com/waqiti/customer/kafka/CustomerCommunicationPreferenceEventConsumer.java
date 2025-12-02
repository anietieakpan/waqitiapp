package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.CommunicationPreferenceService;
import com.waqiti.customer.service.GDPRComplianceService;
import com.waqiti.customer.service.CustomerNotificationService;
import com.waqiti.customer.service.AuditService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CommunicationPreference;
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
 * Critical Event Consumer #223: Customer Communication Preference Event Consumer
 * Processes communication channel preferences and GDPR consent management
 * Implements 12-step zero-tolerance processing for communication preference lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerCommunicationPreferenceEventConsumer extends BaseKafkaConsumer {

    private final CommunicationPreferenceService preferenceService;
    private final GDPRComplianceService gdprService;
    private final CustomerNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-communication-preference-events", groupId = "customer-communication-preference-group")
    @CircuitBreaker(name = "customer-communication-preference-consumer")
    @Retry(name = "customer-communication-preference-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerCommunicationPreferenceEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-communication-preference-event");
        
        try {
            log.info("Step 1: Processing customer communication preference event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String preferenceType = eventData.path("preferenceType").asText();
            String channel = eventData.path("channel").asText();
            boolean optedIn = eventData.path("optedIn").asBoolean();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String consentType = eventData.path("consentType").asText();
            
            log.info("Step 2: Extracted preference details: customerId={}, channel={}, optedIn={}, type={}", 
                    customerId, channel, optedIn, preferenceType);
            
            // Step 3: Customer validation and authentication
            log.info("Step 3: Validating customer identity and preferences access");
            Customer customer = preferenceService.validateCustomerAccess(customerId);
            if (customer == null) {
                throw new IllegalStateException("Customer not found or access denied: " + customerId);
            }
            
            // Step 4: GDPR consent verification
            log.info("Step 4: Verifying GDPR consent requirements for preference changes");
            boolean consentRequired = gdprService.isConsentRequiredForChannel(channel, preferenceType);
            if (consentRequired && !gdprService.hasValidConsent(customerId, consentType)) {
                throw new IllegalStateException("GDPR consent required but not provided: " + customerId);
            }
            
            // Step 5: CAN-SPAM Act compliance validation
            log.info("Step 5: Validating CAN-SPAM Act compliance for communication preferences");
            if ("EMAIL".equals(channel) && "MARKETING".equals(preferenceType)) {
                boolean canSpamCompliant = preferenceService.validateCanSpamCompliance(customer, eventData);
                if (!canSpamCompliant) {
                    preferenceService.flagComplianceViolation(customerId, "CAN_SPAM_VIOLATION");
                    return;
                }
            }
            
            // Step 6: Preference update processing
            log.info("Step 6: Processing communication preference updates");
            CommunicationPreference preference = preferenceService.updateCommunicationPreference(
                    customer, channel, preferenceType, optedIn, timestamp);
            
            if ("SMS".equals(channel)) {
                preferenceService.validateTCPACompliance(customer, preference);
            }
            
            // Step 7: Cross-channel preference synchronization
            log.info("Step 7: Synchronizing preferences across communication channels");
            List<CommunicationPreference> relatedPreferences = preferenceService.getRelatedPreferences(customerId, channel);
            preferenceService.synchronizeCrossChannelPreferences(preference, relatedPreferences);
            
            // Step 8: Regulatory compliance assessment
            log.info("Step 8: Assessing regulatory compliance for preference changes");
            if (optedIn) {
                preferenceService.validateDoNotCallRegistry(customer, channel);
                preferenceService.updateMarketingEligibility(customer, preferenceType);
            } else {
                preferenceService.processOptOutRequest(customer, channel, preferenceType, timestamp);
                preferenceService.updateSuppressionLists(customer, channel);
            }
            
            // Step 9: Customer notification and confirmation
            log.info("Step 9: Sending preference confirmation notifications");
            notificationService.sendPreferenceConfirmation(customer, preference);
            if (!optedIn) {
                notificationService.sendOptOutConfirmation(customer, channel, preferenceType);
            }
            notificationService.updateCustomerPortalPreferences(customer, preference);
            
            // Step 10: Marketing system integration
            log.info("Step 10: Updating marketing systems and campaign eligibility");
            preferenceService.updateMarketingSystemPreferences(customer, preference);
            preferenceService.recalculateCampaignEligibility(customer);
            preferenceService.updateSegmentationRules(customer, preference);
            
            // Step 11: Audit trail and compliance documentation
            log.info("Step 11: Creating audit trail and compliance documentation");
            auditService.logPreferenceChange(customer, preference, eventData);
            gdprService.documentConsentDecision(customer, consentType, optedIn, timestamp);
            preferenceService.archivePreferenceHistory(customer, preference);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed communication preference: customerId={}, eventId={}", customerId, eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer communication preference event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("preferenceType") || !eventData.has("channel") ||
            !eventData.has("optedIn") || !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid customer communication preference event structure");
        }
    }
}