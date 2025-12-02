package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.DeceasedCustomerService;
import com.waqiti.customer.service.EstateProcessingService;
import com.waqiti.customer.service.AccountFreezeService;
import com.waqiti.customer.service.BeneficiaryNotificationService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.DeathNotification;
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
 * Critical Event Consumer #220: Customer Death Notification Event Consumer
 * Processes deceased account handling with estate settlement compliance
 * Implements 12-step zero-tolerance processing for death notification procedures
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerDeathNotificationEventConsumer extends BaseKafkaConsumer {

    private final DeceasedCustomerService deceasedService;
    private final EstateProcessingService estateService;
    private final AccountFreezeService freezeService;
    private final BeneficiaryNotificationService beneficiaryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-death-notification-events", groupId = "customer-death-notification-group")
    @CircuitBreaker(name = "customer-death-notification-consumer")
    @Retry(name = "customer-death-notification-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerDeathNotificationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-death-notification-event");
        
        try {
            log.info("Step 1: Processing customer death notification event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            LocalDateTime deathDate = LocalDateTime.parse(eventData.path("deathDate").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String deathCertificateNumber = eventData.path("deathCertificateNumber").asText();
            String reportedBy = eventData.path("reportedBy").asText();
            String relationshipToDeceased = eventData.path("relationshipToDeceased").asText();
            String probateCourtJurisdiction = eventData.path("probateCourtJurisdiction").asText();
            
            log.info("Step 2: Extracted death notification details: customerId={}, deathDate={}, reportedBy={}", 
                    customerId, deathDate, reportedBy);
            
            // Step 3: Death certificate verification and authentication
            log.info("Step 3: Verifying death certificate and reporter authorization");
            Customer customer = deceasedService.getCustomerById(customerId);
            DeathNotification notification = deceasedService.createDeathNotification(eventData);
            deceasedService.verifyDeathCertificate(deathCertificateNumber, customer.getFullName(), deathDate);
            deceasedService.validateReporterAuthorization(reportedBy, relationshipToDeceased, customer);
            
            // Step 4: Immediate account freeze and access restriction
            log.info("Step 4: Implementing immediate account freeze and access restrictions");
            freezeService.freezeAllCustomerAccounts(customerId, "CUSTOMER_DECEASED");
            freezeService.disableOnlineBankingAccess(customerId);
            freezeService.cancelPendingTransactions(customerId);
            freezeService.blockDebitCardAccess(customerId);
            
            // Step 5: Estate and probate law compliance
            log.info("Step 5: Initiating estate settlement and probate compliance procedures");
            estateService.initiateEstateProcessing(customer, notification);
            estateService.determineProbateRequirements(customer, probateCourtJurisdiction);
            estateService.calculateEstateValue(customer);
            boolean requiresProbate = estateService.checkProbateThreshold(customer);
            
            // Step 6: Beneficiary identification and POD processing
            log.info("Step 6: Identifying beneficiaries and processing POD accounts");
            estateService.identifyAccountBeneficiaries(customer);
            estateService.processPODAccounts(customer, deathDate);
            estateService.validateBeneficiaryDesignations(customer);
            beneficiaryService.initiateContactWithBeneficiaries(customer);
            
            // Step 7: External notifications and third-party services
            log.info("Step 7: Notifying external entities and third-party services");
            deceasedService.notifySSADeathMasterFile(customer, deathDate);
            deceasedService.notifyIRSOfDeath(customer);
            deceasedService.cancelDirectDeposits(customer);
            deceasedService.notifyCorrespondentBanks(customer);
            
            // Step 8: Joint account and co-ownership processing
            log.info("Step 8: Processing joint accounts and co-ownership arrangements");
            estateService.processJointAccounts(customer, deathDate);
            estateService.transferSoleOwnershipToSurvivor(customer);
            estateService.updateTitleAndOwnership(customer);
            estateService.notifyJointAccountHolders(customer);
            
            // Step 9: Insurance and loan processing
            log.info("Step 9: Processing insurance claims and loan obligations");
            estateService.processInsuranceClaims(customer);
            estateService.evaluateLoanObligations(customer);
            estateService.applyLoanInsuranceBenefits(customer);
            estateService.calculateNetEstateValue(customer);
            
            // Step 10: Tax and regulatory reporting
            log.info("Step 10: Generating tax reports and regulatory notifications");
            estateService.generateForm1041Requirements(customer);
            estateService.reportToStateTaxAuthorities(customer, probateCourtJurisdiction);
            estateService.fileCTRForFinalTransactions(customer);
            estateService.updateFBARReporting(customer);
            
            // Step 11: Document preservation and legal compliance
            log.info("Step 11: Preserving estate documents and ensuring legal compliance");
            estateService.preserveAccountRecords(customer, deathDate);
            estateService.createEstateInventory(customer);
            estateService.generateFinalAccountStatements(customer);
            estateService.archiveCustomerDocuments(customer);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed customer death notification: customerId={}, eventId={}", customerId, eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer death notification event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("deathDate") || !eventData.has("deathCertificateNumber") ||
            !eventData.has("reportedBy") || !eventData.has("relationshipToDeceased")) {
            throw new IllegalArgumentException("Invalid customer death notification event structure");
        }
    }
}