package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.merchant.service.MerchantService;
import com.waqiti.merchant.service.MerchantRiskService;
import com.waqiti.merchant.entity.Merchant;
import com.waqiti.merchant.enums.MerchantCategory;
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
import java.util.UUID;

/**
 * Critical Event Consumer #274: Merchant Account Creation Event Consumer
 * Processes merchant onboarding and KYB with regulatory compliance
 * Implements 12-step zero-tolerance processing for merchant onboarding
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantAccountCreationEventConsumer extends BaseKafkaConsumer {

    private final MerchantService merchantService;
    private final MerchantRiskService riskService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "merchant-account-creation-events", groupId = "merchant-account-creation-group")
    @CircuitBreaker(name = "merchant-account-creation-consumer")
    @Retry(name = "merchant-account-creation-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantAccountCreationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "merchant-account-creation-event");
        
        try {
            log.info("Step 1: Processing merchant account creation event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String businessName = eventData.path("businessName").asText();
            String businessType = eventData.path("businessType").asText();
            String taxId = eventData.path("taxId").asText();
            String businessAddress = eventData.path("businessAddress").asText();
            String ownerName = eventData.path("ownerName").asText();
            String merchantCategory = eventData.path("merchantCategory").asText();
            BigDecimal expectedVolume = new BigDecimal(eventData.path("expectedVolume").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted merchant details: business={}, category={}, expectedVolume={}", 
                    businessName, merchantCategory, expectedVolume);
            
            // Step 3: Business validation and eligibility
            boolean eligible = merchantService.validateBusinessEligibility(businessName, businessType, 
                    taxId, merchantCategory, timestamp);
            if (!eligible) {
                log.error("Step 3: Business eligibility validation failed");
                merchantService.rejectMerchantApplication(eventId, "NOT_ELIGIBLE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Business eligibility validated");
            
            // Step 4: KYB (Know Your Business) verification
            boolean kybPassed = merchantService.performKYBVerification(businessName, taxId, 
                    businessAddress, ownerName, timestamp);
            if (!kybPassed) {
                log.error("Step 4: KYB verification failed");
                merchantService.escalateForManualReview(eventId, "KYB_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: KYB verification successful");
            
            // Step 5: OFAC and sanctions screening
            boolean sanctionsCleared = riskService.performMerchantSanctionsScreening(businessName, 
                    ownerName, businessAddress, timestamp);
            if (!sanctionsCleared) {
                log.error("Step 5: Sanctions screening failed");
                merchantService.blockMerchantApplication(eventId, "SANCTIONS_VIOLATION", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Sanctions screening passed");
            
            // Step 6: Risk assessment and scoring
            int riskScore = riskService.calculateMerchantRiskScore(businessType, merchantCategory, 
                    expectedVolume, businessAddress, timestamp);
            if (riskScore > 750) {
                log.warn("Step 6: High risk merchant detected: score={}", riskScore);
                merchantService.applyEnhancedMonitoring(eventId, riskScore, timestamp);
            } else {
                log.info("Step 6: Risk score acceptable: {}", riskScore);
            }
            
            // Step 7: Merchant account creation
            Merchant merchant = merchantService.createMerchantAccount(eventId, businessName, 
                    businessType, taxId, businessAddress, ownerName, 
                    MerchantCategory.valueOf(merchantCategory.toUpperCase()), 
                    expectedVolume, riskScore, timestamp);
            log.info("Step 7: Merchant account created: merchantId={}", merchant.getId());
            
            // Step 8: Payment processing setup
            String processingAccountId = merchantService.setupPaymentProcessing(merchant.getId(), 
                    merchantCategory, expectedVolume, timestamp);
            log.info("Step 8: Payment processing setup: accountId={}", processingAccountId);
            
            // Step 9: Underwriting and approval workflow
            boolean underwritingPassed = merchantService.performUnderwriting(merchant.getId(), 
                    businessType, expectedVolume, riskScore, timestamp);
            if (!underwritingPassed) {
                log.error("Step 9: Underwriting failed");
                merchantService.suspendMerchantAccount(merchant.getId(), "UNDERWRITING_FAILED", timestamp);
            } else {
                log.info("Step 9: Underwriting approved");
            }
            
            // Step 10: Fee structure configuration
            merchantService.configureFeeStructure(merchant.getId(), merchantCategory, 
                    expectedVolume, riskScore, timestamp);
            log.info("Step 10: Fee structure configured");
            
            // Step 11: Compliance monitoring setup
            merchantService.setupComplianceMonitoring(merchant.getId(), merchantCategory, 
                    riskScore, timestamp);
            log.info("Step 11: Compliance monitoring activated");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed merchant account creation: eventId={}, merchantId={}", 
                    eventId, merchant.getId());
            
        } catch (Exception e) {
            log.error("Error processing merchant account creation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("businessName") || 
            !eventData.has("businessType") || !eventData.has("taxId")) {
            throw new IllegalArgumentException("Invalid merchant account creation event structure");
        }
    }
}