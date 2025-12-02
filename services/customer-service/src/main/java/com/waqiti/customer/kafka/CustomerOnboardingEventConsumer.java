package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.CustomerOnboardingService;
import com.waqiti.customer.service.KYCVerificationService;
import com.waqiti.customer.service.CIPComplianceService;
import com.waqiti.customer.service.RiskAssessmentService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.OnboardingRequest;
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
import java.util.UUID;

/**
 * Critical Event Consumer #218: Customer Onboarding Event Consumer
 * Processes customer account opening and CIP verification with BSA/AML compliance
 * Implements 12-step zero-tolerance processing for customer onboarding lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerOnboardingEventConsumer extends BaseKafkaConsumer {

    private final CustomerOnboardingService onboardingService;
    private final KYCVerificationService kycService;
    private final CIPComplianceService cipService;
    private final RiskAssessmentService riskService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-onboarding-events", groupId = "customer-onboarding-group")
    @CircuitBreaker(name = "customer-onboarding-consumer")
    @Retry(name = "customer-onboarding-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerOnboardingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-onboarding-event");
        
        try {
            log.info("Step 1: Processing customer onboarding event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String applicationId = eventData.path("applicationId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String customerType = eventData.path("customerType").asText();
            String identificationDocument = eventData.path("identificationDocument").asText();
            String socialSecurityNumber = eventData.path("ssn").asText();
            
            log.info("Step 2: Extracted onboarding details: customerId={}, applicationId={}, type={}", 
                    customerId, applicationId, customerType);
            
            // Step 3: CIP (Customer Identification Program) verification
            log.info("Step 3: Initiating CIP verification for customer: {}", customerId);
            boolean cipVerified = cipService.verifyCIPRequirements(customerId, identificationDocument, socialSecurityNumber);
            if (!cipVerified) {
                throw new IllegalStateException("CIP verification failed for customer: " + customerId);
            }
            
            // Step 4: KYC (Know Your Customer) processing
            log.info("Step 4: Executing KYC verification procedures");
            OnboardingRequest request = onboardingService.createOnboardingRequest(eventData);
            kycService.performKYCVerification(request);
            
            // Step 5: BSA/AML compliance screening
            log.info("Step 5: Conducting BSA/AML compliance screening");
            riskService.performAMLScreening(request);
            boolean ofacScreenPassed = riskService.screenAgainstOFACList(request.getCustomerName(), request.getCustomerAddress());
            if (!ofacScreenPassed) {
                onboardingService.flagForManualReview(applicationId, "OFAC_SCREENING_FAILED");
                return;
            }
            
            // Step 6: Account creation processing
            log.info("Step 6: Creating customer account and initial setup");
            Customer customer = onboardingService.createCustomerAccount(request);
            onboardingService.assignAccountNumbers(customer);
            onboardingService.setupInitialProducts(customer, eventData.path("requestedProducts"));
            
            // Step 7: External system integrations
            log.info("Step 7: Integrating with external verification systems");
            onboardingService.registerWithCreditBureaus(customer);
            onboardingService.notifyRegulatoryReporting(customer);
            onboardingService.setupACHRelationships(customer);
            
            // Step 8: Risk profile assessment
            log.info("Step 8: Calculating customer risk score and profile");
            int riskScore = riskService.calculateCustomerRiskScore(customer);
            onboardingService.assignRiskRating(customer, riskScore);
            if (riskScore > 75) {
                onboardingService.requireEnhancedDueDiligence(customer);
            }
            
            // Step 9: Welcome communications and notifications
            log.info("Step 9: Sending welcome communications and account setup notifications");
            onboardingService.sendWelcomePackage(customer);
            onboardingService.scheduleInitialDebitCardDelivery(customer);
            onboardingService.notifyRelationshipManager(customer);
            
            // Step 10: Regulatory reporting and compliance documentation
            log.info("Step 10: Generating regulatory reports and compliance documentation");
            onboardingService.generateCIPReport(customer);
            onboardingService.fileCTRIfRequired(customer);
            onboardingService.updateCBSAReporting(customer);
            
            // Step 11: Data archival and audit trail
            log.info("Step 11: Archiving onboarding documents and creating audit trail");
            onboardingService.archiveOnboardingDocuments(applicationId);
            onboardingService.createAuditTrail(customer, "ONBOARDING_COMPLETED");
            onboardingService.storeComplianceRecords(customer, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully completed customer onboarding: customerId={}, eventId={}", customerId, eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer onboarding event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("applicationId") || !eventData.has("customerType") ||
            !eventData.has("identificationDocument") || !eventData.has("ssn")) {
            throw new IllegalArgumentException("Invalid customer onboarding event structure");
        }
    }
}