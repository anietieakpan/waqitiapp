package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.merchant.service.MerchantOnboardingService;
import com.waqiti.merchant.service.MerchantComplianceService;
import com.waqiti.merchant.service.MerchantRiskService;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.common.exception.MerchantProcessingException;
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
 * Kafka Consumer for Merchant Onboarding Events
 * Handles merchant registration, KYB verification, and account setup processes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantOnboardingEventsConsumer {
    
    private final MerchantOnboardingService onboardingService;
    private final MerchantComplianceService complianceService;
    private final MerchantRiskService riskService;
    private final MerchantNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"merchant-onboarding-events", "merchant-application-submitted", "kyb-verification-completed", "merchant-account-activated"},
        groupId = "merchant-service-onboarding-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleMerchantOnboardingEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID merchantId = null;
        UUID applicationId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            merchantId = UUID.fromString((String) event.get("merchantId"));
            applicationId = UUID.fromString((String) event.get("applicationId"));
            eventType = (String) event.get("eventType");
            String businessName = (String) event.get("businessName");
            String businessType = (String) event.get("businessType"); // LLC, CORPORATION, PARTNERSHIP, SOLE_PROPRIETORSHIP
            String industryCode = (String) event.get("industryCode"); // MCC code
            String businessAddress = (String) event.get("businessAddress");
            String businessPhone = (String) event.get("businessPhone");
            String businessEmail = (String) event.get("businessEmail");
            String website = (String) event.get("website");
            String taxId = (String) event.get("taxId"); // EIN or SSN
            String dbaName = (String) event.get("dbaName");
            BigDecimal projectedVolume = new BigDecimal((String) event.get("projectedVolume"));
            BigDecimal averageTicket = new BigDecimal((String) event.get("averageTicket"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Principal owner information
            String ownerName = (String) event.get("ownerName");
            String ownerSsn = (String) event.get("ownerSsn");
            String ownerDateOfBirth = (String) event.get("ownerDateOfBirth");
            String ownerAddress = (String) event.get("ownerAddress");
            String ownerPhone = (String) event.get("ownerPhone");
            String ownerEmail = (String) event.get("ownerEmail");
            BigDecimal ownershipPercentage = new BigDecimal((String) event.get("ownershipPercentage"));
            
            // Banking information
            String bankAccountNumber = (String) event.get("bankAccountNumber");
            String bankRoutingNumber = (String) event.get("bankRoutingNumber");
            String bankName = (String) event.get("bankName");
            String accountType = (String) event.get("accountType");
            
            // Processing preferences
            String settlementSchedule = (String) event.get("settlementSchedule"); // DAILY, WEEKLY, MONTHLY
            Boolean acceptsCreditCards = (Boolean) event.getOrDefault("acceptsCreditCards", true);
            Boolean acceptsDebitCards = (Boolean) event.getOrDefault("acceptsDebitCards", true);
            Boolean acceptsACH = (Boolean) event.getOrDefault("acceptsACH", false);
            
            log.info("Processing merchant onboarding event - MerchantId: {}, ApplicationId: {}, Type: {}, Business: {}", 
                    merchantId, applicationId, eventType, businessName);
            
            // Step 1: Validate merchant application data
            Map<String, Object> validationResult = onboardingService.validateMerchantApplication(
                    applicationId, businessName, businessType, industryCode, taxId, 
                    ownerName, ownerSsn, bankAccountNumber, bankRoutingNumber);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                onboardingService.rejectApplication(applicationId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Merchant application rejected: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Perform KYB (Know Your Business) verification
            Map<String, Object> kybResult = complianceService.performKYBVerification(
                    applicationId, businessName, businessType, taxId, businessAddress,
                    ownerName, ownerSsn, ownerDateOfBirth, ownerAddress, timestamp);
            
            if ("FAILED".equals(kybResult.get("status"))) {
                onboardingService.requestAdditionalDocumentation(applicationId, 
                        (Map<String, Object>) kybResult.get("requiredDocuments"), timestamp);
                log.info("Additional documentation requested for KYB verification");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Risk assessment and tier determination
            Map<String, Object> riskAssessment = riskService.assessMerchantRisk(
                    merchantId, industryCode, projectedVolume, averageTicket, 
                    businessType, ownershipPercentage, kybResult, timestamp);
            
            String riskTier = (String) riskAssessment.get("riskTier"); // LOW, MEDIUM, HIGH
            String merchantTier = riskService.determineMerchantTier(riskTier, projectedVolume, 
                    industryCode, timestamp);
            
            // Step 4: Process based on event type
            switch (eventType) {
                case "MERCHANT_APPLICATION_SUBMITTED":
                    onboardingService.initiateOnboardingProcess(applicationId, merchantId,
                            businessName, businessType, industryCode, projectedVolume,
                            riskTier, merchantTier, timestamp);
                    break;
                    
                case "KYB_VERIFICATION_COMPLETED":
                    onboardingService.processKYBCompletion(applicationId, merchantId,
                            (String) kybResult.get("verificationId"), 
                            (String) kybResult.get("status"), timestamp);
                    break;
                    
                case "MERCHANT_ACCOUNT_ACTIVATED":
                    onboardingService.activateMerchantAccount(merchantId, applicationId,
                            settlementSchedule, acceptsCreditCards, acceptsDebitCards, 
                            acceptsACH, timestamp);
                    break;
                    
                default:
                    onboardingService.processGenericOnboardingEvent(merchantId, applicationId, 
                            eventType, event, timestamp);
            }
            
            // Step 5: Set up payment processing capabilities
            if ("MERCHANT_ACCOUNT_ACTIVATED".equals(eventType)) {
                onboardingService.setupPaymentProcessing(merchantId, merchantTier,
                        acceptsCreditCards, acceptsDebitCards, acceptsACH, timestamp);
                
                // Configure settlement account
                onboardingService.configureBankAccount(merchantId, bankAccountNumber,
                        bankRoutingNumber, bankName, accountType, timestamp);
            }
            
            // Step 6: Generate merchant credentials and API keys
            if ("MERCHANT_ACCOUNT_ACTIVATED".equals(eventType)) {
                Map<String, String> credentials = onboardingService.generateMerchantCredentials(
                        merchantId, merchantTier, timestamp);
                
                // Setup initial limits and processing rules
                onboardingService.setupProcessingLimits(merchantId, merchantTier, 
                        projectedVolume, averageTicket, riskTier, timestamp);
            }
            
            // Step 7: Compliance documentation and reporting
            complianceService.generateOnboardingComplianceReport(merchantId, applicationId,
                    kybResult, riskAssessment, businessType, industryCode, timestamp);
            
            // Step 8: Send onboarding notifications
            notificationService.sendOnboardingNotification(merchantId, applicationId,
                    eventType, businessName, ownerEmail, businessEmail, timestamp);
            
            // Step 9: Setup monitoring and alerting
            if ("MERCHANT_ACCOUNT_ACTIVATED".equals(eventType)) {
                riskService.setupMerchantMonitoring(merchantId, riskTier, industryCode, 
                        projectedVolume, timestamp);
            }
            
            // Step 10: Audit logging
            auditService.auditFinancialEvent(
                    "MERCHANT_ONBOARDING_EVENT_PROCESSED",
                    merchantId.toString(),
                    String.format("Merchant onboarding event processed - Type: %s, Business: %s, Tier: %s", 
                            eventType, businessName, merchantTier),
                    Map.of(
                            "merchantId", merchantId.toString(),
                            "applicationId", applicationId.toString(),
                            "eventType", eventType,
                            "businessName", businessName,
                            "businessType", businessType,
                            "industryCode", industryCode,
                            "merchantTier", merchantTier,
                            "riskTier", riskTier,
                            "projectedVolume", projectedVolume.toString(),
                            "averageTicket", averageTicket.toString(),
                            "kybStatus", kybResult.get("status").toString(),
                            "settlementSchedule", settlementSchedule,
                            "acceptsCreditCards", acceptsCreditCards.toString(),
                            "acceptsDebitCards", acceptsDebitCards.toString(),
                            "acceptsACH", acceptsACH.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed merchant onboarding event - MerchantId: {}, EventType: {}", 
                    merchantId, eventType);
            
        } catch (Exception e) {
            log.error("Merchant onboarding event processing failed - MerchantId: {}, ApplicationId: {}, Error: {}", 
                    merchantId, applicationId, e.getMessage(), e);
            throw new MerchantProcessingException("Merchant onboarding event processing failed", e);
        }
    }
}