package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.BeneficiaryManagementService;
import com.waqiti.customer.service.PODAccountService;
import com.waqiti.customer.service.BeneficiaryVerificationService;
import com.waqiti.customer.service.EstateplanningComplianceService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.Beneficiary;
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
import java.util.UUID;

/**
 * Critical Event Consumer #221: Customer Beneficiary Event Consumer
 * Processes beneficiary designation and POD accounts with estate planning compliance
 * Implements 12-step zero-tolerance processing for beneficiary management lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerBeneficiaryEventConsumer extends BaseKafkaConsumer {

    private final BeneficiaryManagementService beneficiaryService;
    private final PODAccountService podService;
    private final BeneficiaryVerificationService verificationService;
    private final EstateplanningComplianceService complianceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-beneficiary-events", groupId = "customer-beneficiary-group")
    @CircuitBreaker(name = "customer-beneficiary-consumer")
    @Retry(name = "customer-beneficiary-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerBeneficiaryEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-beneficiary-event");
        
        try {
            log.info("Step 1: Processing customer beneficiary event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String actionType = eventData.path("actionType").asText(); // ADD, UPDATE, REMOVE
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String accountId = eventData.path("accountId").asText();
            JsonNode beneficiaryData = eventData.path("beneficiaryData");
            boolean isPODDesignation = eventData.path("isPODDesignation").asBoolean();
            BigDecimal beneficiaryPercentage = new BigDecimal(eventData.path("beneficiaryPercentage").asText());
            
            log.info("Step 2: Extracted beneficiary details: customerId={}, actionType={}, accountId={}, isPOD={}", 
                    customerId, actionType, accountId, isPODDesignation);
            
            // Step 3: Customer authentication and account ownership verification
            log.info("Step 3: Verifying customer identity and account ownership");
            Customer customer = beneficiaryService.getCustomerById(customerId);
            beneficiaryService.validateAccountOwnership(customer, accountId);
            beneficiaryService.verifyCustomerCapacity(customer);
            
            // Step 4: Beneficiary information validation and verification
            log.info("Step 4: Validating and verifying beneficiary information");
            Beneficiary beneficiary = null;
            if (!"REMOVE".equals(actionType)) {
                beneficiary = beneficiaryService.createBeneficiaryFromData(beneficiaryData);
                verificationService.validateBeneficiaryIdentity(beneficiary);
                verificationService.checkBeneficiaryEligibility(beneficiary, customer);
                verificationService.verifySSNOrTIN(beneficiary);
            }
            
            // Step 5: Estate planning and legal compliance validation
            log.info("Step 5: Ensuring estate planning and legal compliance");
            complianceService.validateStateEstateLaws(customer.getState(), isPODDesignation);
            complianceService.checkBeneficiaryDesignationLimits(customer, accountId);
            if (isPODDesignation) {
                complianceService.validatePODRequirements(customer.getState());
            }
            
            // Step 6: Beneficiary designation processing
            log.info("Step 6: Processing beneficiary designation changes");
            switch (actionType) {
                case "ADD":
                    beneficiaryService.addBeneficiary(customer, accountId, beneficiary, beneficiaryPercentage, isPODDesignation);
                    break;
                case "UPDATE":
                    beneficiaryService.updateBeneficiary(customer, accountId, beneficiary, beneficiaryPercentage);
                    break;
                case "REMOVE":
                    String beneficiaryId = eventData.path("beneficiaryId").asText();
                    beneficiaryService.removeBeneficiary(customer, accountId, beneficiaryId);
                    break;
            }
            
            // Step 7: POD account configuration and trust setup
            log.info("Step 7: Configuring POD accounts and trust arrangements");
            if (isPODDesignation) {
                podService.setupPODAccount(customer, accountId, beneficiary);
                podService.generatePODDocumentation(customer, accountId, beneficiary);
                podService.registerPODWithState(customer, accountId, beneficiary);
            }
            beneficiaryService.validateTotalBeneficiaryPercentages(customer, accountId);
            
            // Step 8: Tax implications and IRS reporting requirements
            log.info("Step 8: Evaluating tax implications and IRS reporting requirements");
            complianceService.evaluateTaxImplications(customer, beneficiary, beneficiaryPercentage);
            complianceService.checkIRSReportingRequirements(customer, beneficiary);
            if (beneficiaryPercentage.compareTo(new BigDecimal("10000")) > 0) {
                complianceService.generateForm8300IfRequired(customer, beneficiary);
            }
            
            // Step 9: Documentation and notification generation
            log.info("Step 9: Generating documentation and sending notifications");
            beneficiaryService.generateBeneficiaryConfirmationLetter(customer, beneficiary, actionType);
            beneficiaryService.updateAccountDocumentation(customer, accountId);
            beneficiaryService.notifyCustomerOfChanges(customer, actionType, beneficiary);
            if (beneficiary != null) {
                beneficiaryService.sendBeneficiaryNotification(beneficiary, customer, actionType);
            }
            
            // Step 10: Regulatory compliance and state law adherence
            log.info("Step 10: Ensuring regulatory compliance and state law adherence");
            complianceService.updateRegulatoryFilings(customer, beneficiary, actionType);
            complianceService.checkUTMAUTGMACompliance(customer, beneficiary);
            complianceService.validateSpouseRights(customer, beneficiary, customer.getState());
            
            // Step 11: Audit trail and record preservation
            log.info("Step 11: Creating audit trail and preserving beneficiary records");
            beneficiaryService.createBeneficiaryAuditTrail(customer, accountId, beneficiary, actionType, timestamp);
            beneficiaryService.archivePreviousBeneficiaryRecords(customer, accountId);
            beneficiaryService.updateEstateplanningRecords(customer);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed customer beneficiary event: customerId={}, eventId={}", customerId, eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer beneficiary event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("actionType") || !eventData.has("accountId") ||
            !eventData.has("isPODDesignation") || !eventData.has("beneficiaryPercentage")) {
            throw new IllegalArgumentException("Invalid customer beneficiary event structure");
        }
    }
}