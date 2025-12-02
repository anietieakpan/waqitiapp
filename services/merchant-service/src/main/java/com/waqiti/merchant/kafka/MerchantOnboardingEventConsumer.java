package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.merchant.service.MerchantOnboardingService;
import com.waqiti.merchant.service.MerchantComplianceService;
import com.waqiti.merchant.entity.MerchantApplication;
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
 * Critical Event Consumer #99: Merchant Onboarding Event Consumer
 * Processes merchant onboarding with comprehensive KYB, compliance screening, and risk assessment
 * Implements 12-step zero-tolerance processing for merchant verification and approval workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantOnboardingEventConsumer extends BaseKafkaConsumer {

    private final MerchantOnboardingService merchantOnboardingService;
    private final MerchantComplianceService merchantComplianceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "merchant-onboarding-events", groupId = "merchant-onboarding-group")
    @CircuitBreaker(name = "merchant-onboarding-consumer")
    @Retry(name = "merchant-onboarding-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantOnboardingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "merchant-onboarding-event");
        
        try {
            log.info("Step 1: Processing merchant onboarding event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String merchantId = eventData.path("merchantId").asText();
            String businessName = eventData.path("businessName").asText();
            String businessType = eventData.path("businessType").asText();
            String taxId = eventData.path("taxId").asText();
            String country = eventData.path("country").asText();
            String industry = eventData.path("industry").asText();
            String websiteUrl = eventData.path("websiteUrl").asText();
            String ownerName = eventData.path("ownerName").asText();
            String ownerEmail = eventData.path("ownerEmail").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted merchant details: merchantId={}, businessName={}, industry={}", 
                    merchantId, businessName, industry);
            
            // Step 3: Create merchant application record
            MerchantApplication application = merchantOnboardingService.createMerchantApplication(
                    merchantId, businessName, businessType, taxId, country, industry, 
                    websiteUrl, ownerName, ownerEmail, timestamp);
            
            log.info("Step 3: Created merchant application: applicationId={}", application.getApplicationId());
            
            // Step 4: Execute KYB (Know Your Business) verification
            merchantComplianceService.performKYBVerification(merchantId, businessName, taxId, 
                    country, timestamp);
            
            log.info("Step 4: Completed KYB verification for merchant: {}", merchantId);
            
            // Step 5: Screen against sanctions lists (OFAC, EU, UN)
            boolean sanctionsMatch = merchantComplianceService.screenAgainstSanctions(
                    merchantId, businessName, ownerName, country);
            
            if (sanctionsMatch) {
                log.error("Step 5: CRITICAL - Merchant matched sanctions list: {}", merchantId);
                merchantOnboardingService.rejectApplication(merchantId, "SANCTIONS_MATCH", timestamp);
                return;
            }
            
            // Step 6: Assess business risk level
            String riskLevel = merchantComplianceService.assessMerchantRisk(industry, country, 
                    businessType, timestamp);
            
            log.info("Step 6: Assessed merchant risk level: {}", riskLevel);
            
            // Step 7: Verify business documentation
            merchantComplianceService.verifyBusinessDocuments(merchantId, taxId, timestamp);
            
            log.info("Step 7: Verified business documentation");
            
            // Step 8: Conduct website and business legitimacy check
            merchantComplianceService.verifyBusinessLegitimacy(merchantId, websiteUrl, 
                    businessName, timestamp);
            
            // Step 9: Perform PEP (Politically Exposed Person) screening on owners
            boolean pepMatch = merchantComplianceService.screenForPEP(ownerName, country, timestamp);
            
            if (pepMatch) {
                log.warn("Step 9: PEP match found for merchant owner: {}", ownerName);
                merchantOnboardingService.flagForEnhancedDueDiligence(merchantId, "PEP_MATCH", timestamp);
            }
            
            // Step 10: Set merchant payment limits based on risk
            merchantOnboardingService.setPaymentLimits(merchantId, riskLevel, timestamp);
            
            log.info("Step 10: Set payment limits for merchant based on risk: {}", riskLevel);
            
            // Step 11: Generate merchant onboarding report
            merchantComplianceService.generateOnboardingReport(merchantId, riskLevel, timestamp);
            
            // Step 12: Approve or flag application based on checks
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                merchantOnboardingService.flagForManualReview(merchantId, riskLevel, timestamp);
                log.info("Step 12: Flagged merchant for manual review: merchantId={}, risk={}", 
                        merchantId, riskLevel);
            } else {
                merchantOnboardingService.approveApplication(merchantId, timestamp);
                log.info("Step 12: Approved merchant application: merchantId={}", merchantId);
            }
            
            ack.acknowledge();
            log.info("Successfully processed merchant onboarding event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing merchant onboarding event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("merchantId") || 
            !eventData.has("businessName") || !eventData.has("taxId")) {
            throw new IllegalArgumentException("Invalid merchant onboarding event structure");
        }
    }
}