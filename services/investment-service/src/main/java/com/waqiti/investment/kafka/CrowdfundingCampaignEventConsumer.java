package com.waqiti.investment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.CrowdfundingService;
import com.waqiti.investment.service.CampaignComplianceService;
import com.waqiti.investment.service.InvestorVerificationService;
import com.waqiti.investment.entity.CrowdfundingCampaign;
import com.waqiti.investment.entity.CrowdfundingInvestment;
import com.waqiti.investment.entity.CampaignStatus;
import com.waqiti.investment.entity.CrowdfundingType;
import com.waqiti.investment.entity.InvestmentType;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #71: Crowdfunding Campaign Event Consumer
 * Processes crowdfunding campaign events with full securities law compliance
 * Implements 12-step zero-tolerance processing for crowdfunding investments and campaign management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrowdfundingCampaignEventConsumer extends BaseKafkaConsumer {

    private final CrowdfundingService crowdfundingService;
    private final CampaignComplianceService campaignComplianceService;
    private final InvestorVerificationService investorVerificationService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "crowdfunding-campaign-events", groupId = "crowdfunding-campaign-group")
    @CircuitBreaker(name = "crowdfunding-campaign-consumer")
    @Retry(name = "crowdfunding-campaign-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCrowdfundingCampaignEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crowdfunding-campaign-event");
        MDC.put("partition", String.valueOf(record.partition()));
        MDC.put("offset", String.valueOf(record.offset()));
        
        try {
            log.info("Step 1: Processing crowdfunding campaign event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            // Step 2: Parse and validate event structure
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            // Step 3: Extract and validate event details
            String eventId = eventData.path("eventId").asText();
            String eventType = eventData.path("eventType").asText();
            String campaignId = eventData.path("campaignId").asText();
            String issuerId = eventData.path("issuerId").asText();
            String investorId = eventData.path("investorId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String crowdfundingType = eventData.path("crowdfundingType").asText();
            String securityType = eventData.path("securityType").asText();
            BigDecimal fundingGoal = new BigDecimal(eventData.path("fundingGoal").asText("0"));
            LocalDateTime campaignStartDate = LocalDateTime.parse(eventData.path("campaignStartDate").asText());
            LocalDateTime campaignEndDate = LocalDateTime.parse(eventData.path("campaignEndDate").asText());
            String jurisdiction = eventData.path("jurisdiction").asText();
            String exemption = eventData.path("exemption").asText();
            BigDecimal minimumInvestment = new BigDecimal(eventData.path("minimumInvestment").asText("0"));
            BigDecimal maximumInvestment = new BigDecimal(eventData.path("maximumInvestment").asText("0"));
            String businessDescription = eventData.path("businessDescription").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            MDC.put("eventId", eventId);
            MDC.put("campaignId", campaignId);
            MDC.put("eventType", eventType);
            MDC.put("crowdfundingType", crowdfundingType);
            MDC.put("issuerId", issuerId);
            
            log.info("Step 2: Extracted crowdfunding event details: eventId={}, type={}, campaignId={}, amount={}, crowdfundingType={}", 
                    eventId, eventType, campaignId, amount, crowdfundingType);
            
            // Step 4: Validate securities law compliance
            validateSecuritiesCompliance(eventId, crowdfundingType, securityType, jurisdiction, exemption, amount);
            
            // Step 5: Validate investor eligibility and limits
            validateInvestorEligibility(eventId, investorId, amount, crowdfundingType, minimumInvestment, maximumInvestment);
            
            // Step 6: CRITICAL SECURITY - Idempotency check to prevent duplicate processing
            String idempotencyKey = "crowdfunding-campaign:" + campaignId + ":" + investorId + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crowdfunding event ignored: campaignId={}, investorId={}, eventId={}", campaignId, investorId, eventId);
                ack.acknowledge();
                return;
            }
            if (crowdfundingService.isCampaignEventProcessed(eventId, campaignId)) {
                log.warn("Step 6: Crowdfunding campaign event already processed: eventId={}, campaignId={}", eventId, campaignId);
                ack.acknowledge();
                return;
            }
            
            // Step 7: Validate issuer authorization and disclosure requirements
            validateIssuerAuthorization(issuerId, campaignId, crowdfundingType, jurisdiction, exemption);
            
            // Step 8: Process crowdfunding transaction based on type
            processCrowdfundingTransaction(eventId, eventType, campaignId, issuerId, investorId, amount, currency,
                    crowdfundingType, securityType, fundingGoal, campaignStartDate, campaignEndDate, jurisdiction,
                    exemption, minimumInvestment, maximumInvestment, businessDescription, timestamp);
            
            // Step 9: Update campaign progress and funding status
            updateCampaignProgress(eventId, campaignId, amount, fundingGoal, eventType, timestamp);
            
            // Step 10: Record regulatory compliance and disclosure
            recordRegulatoryCompliance(eventId, campaignId, issuerId, investorId, amount, crowdfundingType, 
                    jurisdiction, exemption, timestamp);
            
            // Step 11: Publish campaign status and investor notification events
            publishCampaignStatusEvents(eventId, campaignId, eventType, amount, crowdfundingType, timestamp);
            
            // Step 12: Complete processing and acknowledge
            crowdfundingService.markCampaignEventProcessed(eventId, campaignId);

            // CRITICAL SECURITY: Mark operation as completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("campaignId", campaignId, "investorId", investorId, "amount", amount.toString(),
                       "crowdfundingType", crowdfundingType, "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();

            log.info("Step 12: Successfully processed crowdfunding campaign event: eventId={}, campaignId={}",
                    eventId, campaignId);
            
        } catch (JsonProcessingException e) {
            log.error("SECURITY: Failed to parse crowdfunding campaign event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("SECURITY: Error processing crowdfunding campaign event: {}", e.getMessage(), e);
            String idempotencyKey = "crowdfunding-campaign:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        log.info("Step 2a: Validating crowdfunding campaign event structure");
        
        if (!eventData.has("eventId") || eventData.path("eventId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventId in crowdfunding campaign event");
        }
        
        if (!eventData.has("eventType") || eventData.path("eventType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventType in crowdfunding campaign event");
        }
        
        if (!eventData.has("campaignId") || eventData.path("campaignId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty campaignId in crowdfunding campaign event");
        }
        
        if (!eventData.has("crowdfundingType") || eventData.path("crowdfundingType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty crowdfundingType in crowdfunding campaign event");
        }
        
        if (!eventData.has("amount") || eventData.path("amount").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty amount in crowdfunding campaign event");
        }
        
        if (!eventData.has("jurisdiction") || eventData.path("jurisdiction").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty jurisdiction in crowdfunding campaign event");
        }
        
        if (!eventData.has("exemption") || eventData.path("exemption").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty exemption in crowdfunding campaign event");
        }
        
        log.info("Step 2b: Crowdfunding campaign event structure validation successful");
    }

    private void validateSecuritiesCompliance(String eventId, String crowdfundingType, String securityType, 
                                            String jurisdiction, String exemption, BigDecimal amount) {
        log.info("Step 4: Validating securities law compliance for eventId={}", eventId);
        
        try {
            // Validate crowdfunding type is legal in jurisdiction
            if (!campaignComplianceService.isLegalCrowdfundingType(crowdfundingType, jurisdiction)) {
                throw new IllegalArgumentException("Crowdfunding type not legal in jurisdiction: " + crowdfundingType + " in " + jurisdiction);
            }
            
            // Validate securities exemption
            if (!campaignComplianceService.isValidSecuritiesExemption(exemption, jurisdiction, crowdfundingType)) {
                throw new SecurityException("Invalid securities exemption: " + exemption + " for " + crowdfundingType);
            }
            
            // Check investment limits under exemption
            if (!campaignComplianceService.validateInvestmentLimits(amount, exemption, jurisdiction)) {
                throw new IllegalArgumentException("Investment amount exceeds exemption limits: " + amount + " under " + exemption);
            }
            
            // Validate security type compliance
            if (!campaignComplianceService.isCompliantSecurityType(securityType, crowdfundingType, jurisdiction)) {
                throw new IllegalArgumentException("Security type not compliant: " + securityType + " for " + crowdfundingType);
            }
            
            // Check disclosure requirements
            if (!campaignComplianceService.areDisclosureRequirementsMet(crowdfundingType, exemption, jurisdiction)) {
                throw new SecurityException("Disclosure requirements not met for: " + crowdfundingType + " under " + exemption);
            }
            
            log.info("Step 4: Securities law compliance validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 4: Securities law compliance validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("Securities law compliance validation failed: " + e.getMessage(), e);
        }
    }

    private void validateInvestorEligibility(String eventId, String investorId, BigDecimal amount, 
                                           String crowdfundingType, BigDecimal minimumInvestment, BigDecimal maximumInvestment) {
        log.info("Step 5: Validating investor eligibility for eventId={}", eventId);
        
        try {
            // Validate investor is verified and eligible
            if (!investorVerificationService.isVerifiedInvestor(investorId)) {
                throw new SecurityException("Investor not verified for crowdfunding: " + investorId);
            }
            
            // Check investor accreditation requirements
            if (!investorVerificationService.meetsAccreditationRequirements(investorId, crowdfundingType)) {
                throw new SecurityException("Investor does not meet accreditation requirements: " + investorId);
            }
            
            // Validate investment amount within limits
            if (amount.compareTo(minimumInvestment) < 0) {
                throw new IllegalArgumentException("Investment amount below minimum: " + amount + " < " + minimumInvestment);
            }
            
            if (maximumInvestment.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(maximumInvestment) > 0) {
                throw new IllegalArgumentException("Investment amount exceeds maximum: " + amount + " > " + maximumInvestment);
            }
            
            // Check investor annual investment limits
            if (!investorVerificationService.validateAnnualInvestmentLimits(investorId, amount, crowdfundingType)) {
                throw new IllegalArgumentException("Investment exceeds annual limits for investor: " + investorId);
            }
            
            // Validate investor sophistication for investment type
            if (!investorVerificationService.hasSufficientSophistication(investorId, crowdfundingType)) {
                throw new SecurityException("Investor lacks sophistication for investment type: " + crowdfundingType);
            }
            
            log.info("Step 5: Investor eligibility validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 5: Investor eligibility validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("Investor eligibility validation failed: " + e.getMessage(), e);
        }
    }

    private void validateIssuerAuthorization(String issuerId, String campaignId, String crowdfundingType, 
                                           String jurisdiction, String exemption) {
        log.info("Step 7: Validating issuer authorization for issuerId={}", issuerId);
        
        try {
            // Validate issuer is authorized to conduct crowdfunding
            if (!campaignComplianceService.isAuthorizedIssuer(issuerId, jurisdiction)) {
                throw new SecurityException("Issuer not authorized for crowdfunding in jurisdiction: " + issuerId);
            }
            
            // Check issuer compliance history
            if (!campaignComplianceService.hasCleanComplianceHistory(issuerId)) {
                throw new SecurityException("Issuer has compliance violations: " + issuerId);
            }
            
            // Validate campaign registration and disclosure
            if (!campaignComplianceService.isCampaignProperlyRegistered(campaignId, exemption, jurisdiction)) {
                throw new SecurityException("Campaign not properly registered: " + campaignId + " under " + exemption);
            }
            
            // Check required disclosures are complete
            if (!campaignComplianceService.areRequiredDisclosuresComplete(campaignId, crowdfundingType, exemption)) {
                throw new SecurityException("Required disclosures incomplete for campaign: " + campaignId);
            }
            
            // Validate issuer financial qualifications
            if (!campaignComplianceService.meetsFinancialQualifications(issuerId, crowdfundingType)) {
                throw new SecurityException("Issuer does not meet financial qualifications: " + issuerId);
            }
            
            log.info("Step 7: Issuer authorization validation successful for issuerId={}", issuerId);
            
        } catch (Exception e) {
            log.error("Step 7: Issuer authorization validation failed for issuerId={}: {}", issuerId, e.getMessage(), e);
            throw new SecurityException("Issuer authorization validation failed: " + e.getMessage(), e);
        }
    }

    private void processCrowdfundingTransaction(String eventId, String eventType, String campaignId, String issuerId,
                                              String investorId, BigDecimal amount, String currency, String crowdfundingType,
                                              String securityType, BigDecimal fundingGoal, LocalDateTime campaignStartDate,
                                              LocalDateTime campaignEndDate, String jurisdiction, String exemption,
                                              BigDecimal minimumInvestment, BigDecimal maximumInvestment,
                                              String businessDescription, LocalDateTime timestamp) {
        log.info("Step 8: Processing crowdfunding transaction: eventId={}, type={}", eventId, eventType);
        
        try {
            CrowdfundingInvestment investment = CrowdfundingInvestment.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .campaignId(campaignId)
                    .issuerId(issuerId)
                    .investorId(investorId)
                    .amount(amount)
                    .currency(currency)
                    .type(InvestmentType.valueOf(eventType.toUpperCase()))
                    .crowdfundingType(CrowdfundingType.valueOf(crowdfundingType.toUpperCase()))
                    .securityType(securityType)
                    .jurisdiction(jurisdiction)
                    .exemption(exemption)
                    .timestamp(timestamp)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Process based on transaction type
            switch (eventType.toUpperCase()) {
                case "INVESTMENT":
                    crowdfundingService.processInvestment(investment);
                    break;
                case "CAMPAIGN_LAUNCH":
                    crowdfundingService.processCampaignLaunch(campaignId, issuerId, fundingGoal, campaignStartDate, 
                            campaignEndDate, crowdfundingType, jurisdiction, exemption);
                    break;
                case "CAMPAIGN_UPDATE":
                    crowdfundingService.processCampaignUpdate(investment);
                    break;
                case "FUNDING_GOAL_REACHED":
                    crowdfundingService.processFundingGoalReached(investment);
                    break;
                case "CAMPAIGN_CLOSURE":
                    crowdfundingService.processCampaignClosure(investment);
                    break;
                case "REFUND":
                    crowdfundingService.processRefund(investment);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported crowdfunding transaction type: " + eventType);
            }
            
            // Save investment record
            crowdfundingService.saveCrowdfundingInvestment(investment);
            
            log.info("Step 8: Crowdfunding transaction processed successfully: eventId={}, campaignId={}", 
                    eventId, campaignId);
            
        } catch (Exception e) {
            log.error("Step 8: Crowdfunding transaction processing failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("Crowdfunding transaction processing failed: " + e.getMessage(), e);
        }
    }

    private void updateCampaignProgress(String eventId, String campaignId, BigDecimal amount, BigDecimal fundingGoal,
                                      String eventType, LocalDateTime timestamp) {
        log.info("Step 9: Updating campaign progress for eventId={}", eventId);
        
        try {
            // Update campaign funding progress
            crowdfundingService.updateCampaignFunding(campaignId, amount, eventType, timestamp);
            
            // Check if funding goal reached
            BigDecimal totalRaised = crowdfundingService.getTotalRaised(campaignId);
            if (totalRaised.compareTo(fundingGoal) >= 0) {
                crowdfundingService.triggerFundingGoalReached(campaignId, totalRaised, timestamp);
            }
            
            // Update campaign milestones
            crowdfundingService.updateCampaignMilestones(campaignId, totalRaised, fundingGoal, timestamp);
            
            // Update investor count and statistics
            crowdfundingService.updateCampaignStatistics(campaignId, amount, eventType, timestamp);
            
            log.info("Step 9: Campaign progress updated successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 9: Campaign progress update failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for progress update issues, but log for manual review
        }
    }

    private void recordRegulatoryCompliance(String eventId, String campaignId, String issuerId, String investorId,
                                          BigDecimal amount, String crowdfundingType, String jurisdiction,
                                          String exemption, LocalDateTime timestamp) {
        log.info("Step 10: Recording regulatory compliance for eventId={}", eventId);
        
        try {
            // Record securities transaction for regulatory reporting
            campaignComplianceService.recordSecuritiesTransaction(
                    eventId, campaignId, issuerId, investorId, amount, crowdfundingType, jurisdiction, exemption, timestamp
            );
            
            // Update regulatory filing requirements
            campaignComplianceService.updateRegulatoryFilings(
                    campaignId, crowdfundingType, jurisdiction, exemption, amount, timestamp
            );
            
            // Record disclosure compliance
            campaignComplianceService.recordDisclosureCompliance(
                    campaignId, issuerId, crowdfundingType, exemption, timestamp
            );
            
            // Generate compliance certificate
            campaignComplianceService.generateComplianceCertificate(
                    eventId, campaignId, crowdfundingType, jurisdiction, exemption, timestamp
            );
            
            log.info("Step 10: Regulatory compliance recorded successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 10: Regulatory compliance recording failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for compliance recording issues, but log for manual review
        }
    }

    private void publishCampaignStatusEvents(String eventId, String campaignId, String eventType, BigDecimal amount,
                                           String crowdfundingType, LocalDateTime timestamp) {
        log.info("Step 11: Publishing campaign status events for eventId={}", eventId);
        
        try {
            // Publish campaign progress event
            crowdfundingService.publishCampaignProgressEvent(
                    eventId, campaignId, eventType, amount, timestamp
            );
            
            // Publish investor notification
            crowdfundingService.publishInvestorNotification(
                    eventId, campaignId, eventType, amount, crowdfundingType, timestamp
            );
            
            // Publish to regulatory monitoring systems
            crowdfundingService.publishToRegulatoryMonitoring(
                    eventId, campaignId, crowdfundingType, amount, timestamp
            );
            
            // Publish campaign analytics event
            crowdfundingService.publishCampaignAnalyticsEvent(
                    campaignId, eventType, amount, timestamp
            );
            
            log.info("Step 11: Campaign status events published successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 11: Campaign status event publishing failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for event publishing issues, but log for manual review
        }
    }
}