package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.merchant.service.MerchantFeeCalculationService;
import com.waqiti.merchant.service.InterchangeOptimizationService;
import com.waqiti.merchant.service.PricingTierService;
import com.waqiti.merchant.service.FeeReconciliationService;
import com.waqiti.merchant.service.AuditService;
import com.waqiti.merchant.entity.MerchantFeeStructure;
import com.waqiti.merchant.entity.FeeCalculation;
import com.waqiti.merchant.entity.InterchangeOptimization;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Critical Event Consumer #6: Merchant Fee Events Consumer
 * Processes merchant fee calculations, interchange optimization, and pricing tier management
 * Implements 12-step zero-tolerance processing for merchant fee management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantFeeEventsConsumer extends BaseKafkaConsumer {

    private final MerchantFeeCalculationService feeCalculationService;
    private final InterchangeOptimizationService interchangeService;
    private final PricingTierService pricingTierService;
    private final FeeReconciliationService reconciliationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "merchant-fee-events", 
        groupId = "merchant-fee-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "merchant-fee-consumer")
    @Retry(name = "merchant-fee-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantFeeEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "merchant-fee-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing merchant fee event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String merchantId = eventData.path("merchantId").asText();
            String transactionId = eventData.path("transactionId").asText();
            BigDecimal transactionAmount = new BigDecimal(eventData.path("transactionAmount").asText());
            String cardType = eventData.path("cardType").asText(); // VISA, MASTERCARD, AMEX, DISCOVER
            String cardCategory = eventData.path("cardCategory").asText(); // DEBIT, CREDIT, PREMIUM, CORPORATE
            String processingMethod = eventData.path("processingMethod").asText(); // CARD_PRESENT, CARD_NOT_PRESENT, CONTACTLESS
            String interchangeCategory = eventData.path("interchangeCategory").asText();
            String currency = eventData.path("currency").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            boolean isInternational = eventData.path("isInternational").asBoolean();
            
            log.info("Step 2: Extracted fee details: merchantId={}, amount={} {}, cardType={}, category={}", 
                    merchantId, transactionAmount, currency, cardType, cardCategory);
            
            // Step 3: Merchant fee structure retrieval and validation
            log.info("Step 3: Retrieving merchant fee structure and validating pricing tiers");
            MerchantFeeStructure feeStructure = feeCalculationService.getMerchantFeeStructure(merchantId);
            
            feeCalculationService.validateFeeStructure(feeStructure);
            feeCalculationService.validateMerchantStatus(merchantId);
            pricingTierService.validatePricingTier(merchantId);
            
            if (!feeCalculationService.isValidCardType(cardType)) {
                throw new IllegalStateException("Invalid card type for fee calculation: " + cardType);
            }
            
            feeCalculationService.checkFeeStructureExpiry(feeStructure);
            
            // Step 4: Interchange rate determination and optimization
            log.info("Step 4: Determining optimal interchange rates and qualification levels");
            InterchangeOptimization optimization = interchangeService.createOptimization(eventData);
            
            BigDecimal baseInterchangeRate = interchangeService.getBaseInterchangeRate(cardType, cardCategory, interchangeCategory);
            BigDecimal qualifiedRate = interchangeService.determineQualifiedRate(processingMethod, transactionAmount);
            BigDecimal downgradedRate = interchangeService.calculateDowngradedRate(baseInterchangeRate);
            
            interchangeService.optimizeInterchangeQualification(optimization, processingMethod);
            interchangeService.applyL2L3DataOptimization(optimization, merchantId);
            
            BigDecimal finalInterchangeRate = interchangeService.selectOptimalRate(
                baseInterchangeRate, qualifiedRate, downgradedRate, optimization);
            
            // Step 5: Processing fee calculation and assessment
            log.info("Step 5: Calculating comprehensive processing fees and assessments");
            FeeCalculation feeCalculation = feeCalculationService.createFeeCalculation(eventData);
            
            BigDecimal processingFeeRate = feeCalculationService.getProcessingFeeRate(feeStructure, cardType, processingMethod);
            BigDecimal processingFee = transactionAmount.multiply(processingFeeRate).setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal perTransactionFee = feeCalculationService.getPerTransactionFee(feeStructure, cardType);
            BigDecimal assessmentFee = feeCalculationService.calculateAssessmentFee(transactionAmount, cardType);
            
            feeCalculation.setProcessingFee(processingFee);
            feeCalculation.setPerTransactionFee(perTransactionFee);
            feeCalculation.setAssessmentFee(assessmentFee);
            feeCalculation.setInterchangeRate(finalInterchangeRate);
            
            // Step 6: International and cross-border fee processing
            log.info("Step 6: Processing international and cross-border fee components");
            if (isInternational) {
                BigDecimal internationalFee = feeCalculationService.calculateInternationalFee(transactionAmount, cardType);
                BigDecimal crossBorderFee = feeCalculationService.calculateCrossBorderFee(transactionAmount);
                BigDecimal foreignExchangeFee = feeCalculationService.calculateFXFee(transactionAmount, currency);
                
                feeCalculation.setInternationalFee(internationalFee);
                feeCalculation.setCrossBorderFee(crossBorderFee);
                feeCalculation.setForeignExchangeFee(foreignExchangeFee);
            }
            
            feeCalculationService.applyInternationalOptimizations(feeCalculation, isInternational);
            
            // Step 7: Volume-based pricing and tier adjustments
            log.info("Step 7: Applying volume-based pricing and tier-specific adjustments");
            BigDecimal monthlyVolume = pricingTierService.getMerchantMonthlyVolume(merchantId);
            String currentTier = pricingTierService.determineCurrentTier(merchantId, monthlyVolume);
            
            BigDecimal tierDiscount = pricingTierService.calculateTierDiscount(currentTier, transactionAmount);
            BigDecimal volumeIncentive = pricingTierService.calculateVolumeIncentive(merchantId, monthlyVolume);
            
            feeCalculation.setTierDiscount(tierDiscount);
            feeCalculation.setVolumeIncentive(volumeIncentive);
            
            pricingTierService.updateVolumeMetrics(merchantId, transactionAmount);
            pricingTierService.evaluateTierPromotion(merchantId, monthlyVolume);
            
            // Step 8: Markup and margin calculation
            log.info("Step 8: Calculating markup rates and profit margins");
            BigDecimal markupRate = feeCalculationService.calculateMarkupRate(feeStructure, transactionAmount);
            BigDecimal markupAmount = feeCalculationService.calculateMarkupAmount(
                finalInterchangeRate, markupRate, transactionAmount);
            
            BigDecimal totalInterchangeCost = transactionAmount.multiply(finalInterchangeRate);
            BigDecimal totalMerchantFee = feeCalculationService.calculateTotalMerchantFee(feeCalculation);
            BigDecimal profitMargin = totalMerchantFee.subtract(totalInterchangeCost).subtract(assessmentFee);
            
            feeCalculation.setMarkupAmount(markupAmount);
            feeCalculation.setTotalMerchantFee(totalMerchantFee);
            feeCalculation.setProfitMargin(profitMargin);
            
            // Step 9: Fee reconciliation and validation
            log.info("Step 9: Performing fee reconciliation and validation checks");
            reconciliationService.validateFeeCalculation(feeCalculation);
            reconciliationService.reconcileWithExpectedFees(merchantId, feeCalculation);
            reconciliationService.validateProfitMargins(feeCalculation);
            
            boolean feeAnomaly = reconciliationService.detectFeeAnomalies(feeCalculation, feeStructure);
            if (feeAnomaly) {
                reconciliationService.flagFeeAnomaly(feeCalculation);
                reconciliationService.escalateToFinance(feeCalculation);
            }
            
            reconciliationService.updateFeeReconciliation(merchantId, feeCalculation);
            
            // Step 10: Billing and settlement preparation
            log.info("Step 10: Preparing billing information and settlement processing");
            feeCalculationService.prepareBillingEntry(merchantId, feeCalculation);
            feeCalculationService.updateMerchantBilling(merchantId, feeCalculation);
            feeCalculationService.scheduleSettlement(merchantId, feeCalculation);
            
            if (feeCalculationService.requiresImmediateSettlement(feeCalculation)) {
                feeCalculationService.processImmediateSettlement(feeCalculation);
            }
            
            feeCalculationService.updateSettlementQueue(merchantId, feeCalculation);
            
            // Step 11: Merchant reporting and transparency
            log.info("Step 11: Generating merchant reports and ensuring fee transparency");
            feeCalculationService.generateFeeBreakdown(merchantId, feeCalculation);
            feeCalculationService.updateMerchantDashboard(merchantId, feeCalculation);
            feeCalculationService.calculateDailyFeeSummary(merchantId, feeCalculation);
            
            interchangeService.generateInterchangeReport(optimization);
            pricingTierService.updateTierProgressReport(merchantId);
            
            // Step 12: Audit trail and compliance reporting
            log.info("Step 12: Completing audit trail and regulatory compliance documentation");
            auditService.logFeeCalculation(feeCalculation);
            auditService.logInterchangeOptimization(optimization);
            auditService.logFeeStructure(feeStructure);
            
            feeCalculationService.updateFeeMetrics(feeCalculation);
            interchangeService.updateOptimizationStatistics(optimization);
            pricingTierService.updateTierMetrics(merchantId);
            
            auditService.generateFeeReport(feeCalculation);
            auditService.updateRegulatoryReporting(feeCalculation);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed merchant fee event: merchantId={}, eventId={}, totalFee={} {}", 
                    merchantId, eventId, feeCalculation.getTotalMerchantFee(), currency);
            
        } catch (Exception e) {
            log.error("Error processing merchant fee event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("merchantId") || 
            !eventData.has("transactionId") || !eventData.has("transactionAmount") ||
            !eventData.has("cardType") || !eventData.has("cardCategory") ||
            !eventData.has("processingMethod") || !eventData.has("interchangeCategory") ||
            !eventData.has("currency") || !eventData.has("timestamp") ||
            !eventData.has("isInternational")) {
            throw new IllegalArgumentException("Invalid merchant fee event structure");
        }
    }
}