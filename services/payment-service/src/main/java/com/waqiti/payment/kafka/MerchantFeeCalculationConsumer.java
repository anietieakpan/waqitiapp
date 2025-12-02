package com.waqiti.payment.kafka;

import com.waqiti.common.events.MerchantFeeCalculationEvent;
import com.waqiti.payment.domain.MerchantFeeCalculation;
import com.waqiti.payment.repository.MerchantFeeCalculationRepository;
import com.waqiti.payment.service.FeeCalculationService;
import com.waqiti.payment.service.MerchantTierService;
import com.waqiti.payment.service.VolumeDiscountService;
import com.waqiti.payment.service.InterchangeService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantFeeCalculationConsumer {
    
    private final MerchantFeeCalculationRepository feeCalculationRepository;
    private final FeeCalculationService feeService;
    private final MerchantTierService tierService;
    private final VolumeDiscountService volumeService;
    private final InterchangeService interchangeService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal HIGH_VOLUME_THRESHOLD = new BigDecimal("100000.00");
    private static final BigDecimal PREMIUM_TIER_THRESHOLD = new BigDecimal("500000.00");
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    @KafkaListener(
        topics = {"merchant-fee-calculation", "fee-calculation-requests", "interchange-updates"},
        groupId = "merchant-fee-calculation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantFeeCalculation(
            @Payload MerchantFeeCalculationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("fee-calc-%s-p%d-o%d", 
            event.getCalculationId(), partition, offset);
        
        log.info("Processing merchant fee calculation: id={}, merchantId={}, amount={}, paymentMethod={}, transactionType={}",
            event.getCalculationId(), event.getMerchantId(), event.getTransactionAmount(), 
            event.getPaymentMethod(), event.getTransactionType());
        
        try {
            switch (event.getCalculationType()) {
                case "REAL_TIME_CALCULATION":
                    processRealTimeCalculation(event, correlationId);
                    break;
                    
                case "BATCH_CALCULATION":
                    processBatchCalculation(event, correlationId);
                    break;
                    
                case "TIER_REASSESSMENT":
                    processTierReassessment(event, correlationId);
                    break;
                    
                case "VOLUME_DISCOUNT_UPDATE":
                    processVolumeDiscountUpdate(event, correlationId);
                    break;
                    
                case "INTERCHANGE_RATE_UPDATE":
                    processInterchangeRateUpdate(event, correlationId);
                    break;
                    
                case "MONTHLY_FEE_SUMMARY":
                    processMonthlyFeeSummary(event, correlationId);
                    break;
                    
                case "CUSTOM_RATE_CALCULATION":
                    processCustomRateCalculation(event, correlationId);
                    break;
                    
                case "CHARGEBACK_FEE_CALCULATION":
                    processChargebackFeeCalculation(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown fee calculation type: {}", event.getCalculationType());
                    break;
            }
            
            auditService.logPaymentEvent("FEE_CALCULATION_PROCESSED", event.getCalculationId(),
                Map.of("merchantId", event.getMerchantId(), "calculationType", event.getCalculationType(),
                    "amount", event.getTransactionAmount(), "paymentMethod", event.getPaymentMethod(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process merchant fee calculation: {}", e.getMessage(), e);
            kafkaTemplate.send("merchant-fee-calculation-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processRealTimeCalculation(MerchantFeeCalculationEvent event, String correlationId) {
        MerchantFeeCalculation calculation = MerchantFeeCalculation.builder()
            .calculationId(event.getCalculationId())
            .merchantId(event.getMerchantId())
            .transactionId(event.getTransactionId())
            .transactionAmount(event.getTransactionAmount())
            .currency(event.getCurrency())
            .paymentMethod(event.getPaymentMethod())
            .transactionType(event.getTransactionType())
            .calculationType("REAL_TIME_CALCULATION")
            .calculatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        String merchantTier = tierService.getMerchantTier(event.getMerchantId());
        calculation.setMerchantTier(merchantTier);
        
        BigDecimal baseInterchangeRate = interchangeService.getInterchangeRate(
            event.getPaymentMethod(),
            event.getCardType(),
            event.getTransactionType()
        );
        calculation.setInterchangeRate(baseInterchangeRate);
        
        BigDecimal processingFeeRate = feeService.getProcessingFeeRate(
            event.getMerchantId(),
            event.getPaymentMethod(),
            merchantTier
        );
        calculation.setProcessingFeeRate(processingFeeRate);
        
        BigDecimal volumeDiscount = volumeService.calculateVolumeDiscount(
            event.getMerchantId(),
            event.getTransactionAmount(),
            LocalDate.now().withDayOfMonth(1)
        );
        calculation.setVolumeDiscountRate(volumeDiscount);
        
        BigDecimal interchangeFee = event.getTransactionAmount()
            .multiply(baseInterchangeRate)
            .setScale(SCALE, ROUNDING_MODE);
        calculation.setInterchangeFee(interchangeFee);
        
        BigDecimal processingFee = event.getTransactionAmount()
            .multiply(processingFeeRate)
            .setScale(SCALE, ROUNDING_MODE);
        calculation.setProcessingFee(processingFee);
        
        BigDecimal discountAmount = event.getTransactionAmount()
            .multiply(volumeDiscount)
            .setScale(SCALE, ROUNDING_MODE);
        calculation.setVolumeDiscountAmount(discountAmount);
        
        BigDecimal totalFee = interchangeFee.add(processingFee).subtract(discountAmount);
        calculation.setTotalFee(totalFee);
        
        BigDecimal netAmount = event.getTransactionAmount().subtract(totalFee);
        calculation.setNetAmount(netAmount);
        
        calculation.setEffectiveFeeRate(
            totalFee.divide(event.getTransactionAmount(), SCALE, ROUNDING_MODE)
        );
        
        feeCalculationRepository.save(calculation);
        
        if (event.getTransactionAmount().compareTo(HIGH_VOLUME_THRESHOLD) >= 0) {
            notificationService.sendNotification("FINANCE_TEAM", "High Volume Transaction Fee",
                String.format("High volume transaction fee calculated: Merchant %s, Amount: %s %s, Fee: %s %s", 
                    event.getMerchantId(), event.getTransactionAmount(), event.getCurrency(),
                    totalFee, event.getCurrency()),
                correlationId);
        }
        
        kafkaTemplate.send("merchant-fee-calculated", Map.of(
            "calculationId", event.getCalculationId(),
            "merchantId", event.getMerchantId(),
            "transactionId", event.getTransactionId(),
            "totalFee", totalFee,
            "netAmount", netAmount,
            "effectiveFeeRate", calculation.getEffectiveFeeRate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordFeeCalculation(
            event.getPaymentMethod(), 
            merchantTier, 
            totalFee,
            calculation.getEffectiveFeeRate()
        );
        
        log.info("Real-time fee calculated: calculationId={}, merchantId={}, totalFee={}, effectiveRate={}", 
            event.getCalculationId(), event.getMerchantId(), totalFee, calculation.getEffectiveFeeRate());
    }
    
    private void processBatchCalculation(MerchantFeeCalculationEvent event, String correlationId) {
        List<String> transactionIds = event.getTransactionIds();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        
        for (String transactionId : transactionIds) {
            Map<String, Object> transactionData = feeService.getTransactionData(transactionId);
            
            BigDecimal amount = (BigDecimal) transactionData.get("amount");
            String paymentMethod = (String) transactionData.get("paymentMethod");
            
            BigDecimal transactionFee = feeService.calculateTransactionFee(
                event.getMerchantId(),
                amount,
                paymentMethod
            );
            
            totalAmount = totalAmount.add(amount);
            totalFees = totalFees.add(transactionFee);
        }
        
        MerchantFeeCalculation batchCalculation = MerchantFeeCalculation.builder()
            .calculationId(event.getCalculationId())
            .merchantId(event.getMerchantId())
            .calculationType("BATCH_CALCULATION")
            .batchSize(transactionIds.size())
            .transactionAmount(totalAmount)
            .totalFee(totalFees)
            .netAmount(totalAmount.subtract(totalFees))
            .effectiveFeeRate(totalFees.divide(totalAmount, SCALE, ROUNDING_MODE))
            .calculatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        feeCalculationRepository.save(batchCalculation);
        
        kafkaTemplate.send("batch-fee-calculation-completed", Map.of(
            "calculationId", event.getCalculationId(),
            "merchantId", event.getMerchantId(),
            "batchSize", transactionIds.size(),
            "totalAmount", totalAmount,
            "totalFees", totalFees,
            "effectiveFeeRate", batchCalculation.getEffectiveFeeRate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordBatchFeeCalculation(
            event.getMerchantId(), 
            transactionIds.size(), 
            totalFees
        );
        
        log.info("Batch fee calculation completed: calculationId={}, merchantId={}, batchSize={}, totalFees={}", 
            event.getCalculationId(), event.getMerchantId(), transactionIds.size(), totalFees);
    }
    
    private void processTierReassessment(MerchantFeeCalculationEvent event, String correlationId) {
        String currentTier = tierService.getMerchantTier(event.getMerchantId());
        
        BigDecimal monthlyVolume = volumeService.getMonthlyVolume(
            event.getMerchantId(),
            LocalDate.now().withDayOfMonth(1)
        );
        
        String newTier = tierService.calculateTierBasedOnVolume(monthlyVolume);
        
        if (!currentTier.equals(newTier)) {
            tierService.updateMerchantTier(event.getMerchantId(), newTier);
            
            MerchantFeeCalculation tierUpdate = MerchantFeeCalculation.builder()
                .calculationId(event.getCalculationId())
                .merchantId(event.getMerchantId())
                .calculationType("TIER_REASSESSMENT")
                .previousTier(currentTier)
                .newTier(newTier)
                .monthlyVolume(monthlyVolume)
                .calculatedAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            
            feeCalculationRepository.save(tierUpdate);
            
            BigDecimal newProcessingRate = feeService.getProcessingFeeRate(
                event.getMerchantId(),
                "CARD",
                newTier
            );
            
            notificationService.sendNotification(event.getMerchantId(), "Merchant Tier Updated",
                String.format("Your merchant tier has been updated from %s to %s based on your monthly volume of %s. Your new processing rate is %.2f%%", 
                    currentTier, newTier, monthlyVolume, newProcessingRate.multiply(new BigDecimal("100"))),
                correlationId);
            
            kafkaTemplate.send("merchant-tier-updated", Map.of(
                "merchantId", event.getMerchantId(),
                "previousTier", currentTier,
                "newTier", newTier,
                "monthlyVolume", monthlyVolume,
                "newProcessingRate", newProcessingRate,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            
            if (monthlyVolume.compareTo(PREMIUM_TIER_THRESHOLD) >= 0) {
                notificationService.sendNotification("ACCOUNT_MANAGEMENT", "Premium Merchant Tier Reached",
                    String.format("Merchant %s has reached premium tier with monthly volume: %s", 
                        event.getMerchantId(), monthlyVolume),
                    correlationId);
            }
            
            log.info("Merchant tier updated: merchantId={}, from={} to={}, monthlyVolume={}", 
                event.getMerchantId(), currentTier, newTier, monthlyVolume);
        } else {
            log.info("Merchant tier unchanged: merchantId={}, tier={}, monthlyVolume={}", 
                event.getMerchantId(), currentTier, monthlyVolume);
        }
        
        metricsService.recordTierReassessment(event.getMerchantId(), currentTier, newTier);
    }
    
    private void processVolumeDiscountUpdate(MerchantFeeCalculationEvent event, String correlationId) {
        BigDecimal currentDiscount = volumeService.getCurrentVolumeDiscount(event.getMerchantId());
        
        BigDecimal newDiscount = volumeService.calculateVolumeDiscount(
            event.getMerchantId(),
            event.getTransactionAmount(),
            LocalDate.now().withDayOfMonth(1)
        );
        
        if (currentDiscount.compareTo(newDiscount) != 0) {
            volumeService.updateVolumeDiscount(event.getMerchantId(), newDiscount);
            
            MerchantFeeCalculation discountUpdate = MerchantFeeCalculation.builder()
                .calculationId(event.getCalculationId())
                .merchantId(event.getMerchantId())
                .calculationType("VOLUME_DISCOUNT_UPDATE")
                .previousVolumeDiscountRate(currentDiscount)
                .volumeDiscountRate(newDiscount)
                .calculatedAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            
            feeCalculationRepository.save(discountUpdate);
            
            kafkaTemplate.send("volume-discount-updated", Map.of(
                "merchantId", event.getMerchantId(),
                "previousDiscount", currentDiscount,
                "newDiscount", newDiscount,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            
            log.info("Volume discount updated: merchantId={}, from={} to={}", 
                event.getMerchantId(), currentDiscount, newDiscount);
        }
        
        metricsService.recordVolumeDiscountUpdate(event.getMerchantId(), newDiscount);
    }
    
    private void processInterchangeRateUpdate(MerchantFeeCalculationEvent event, String correlationId) {
        BigDecimal newInterchangeRate = event.getNewInterchangeRate();
        
        interchangeService.updateInterchangeRate(
            event.getPaymentMethod(),
            event.getCardType(),
            event.getTransactionType(),
            newInterchangeRate
        );
        
        MerchantFeeCalculation rateUpdate = MerchantFeeCalculation.builder()
            .calculationId(event.getCalculationId())
            .calculationType("INTERCHANGE_RATE_UPDATE")
            .paymentMethod(event.getPaymentMethod())
            .cardType(event.getCardType())
            .transactionType(event.getTransactionType())
            .interchangeRate(newInterchangeRate)
            .calculatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        feeCalculationRepository.save(rateUpdate);
        
        kafkaTemplate.send("interchange-rate-updated", Map.of(
            "paymentMethod", event.getPaymentMethod(),
            "cardType", event.getCardType(),
            "transactionType", event.getTransactionType(),
            "newInterchangeRate", newInterchangeRate,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordInterchangeRateUpdate(event.getPaymentMethod(), newInterchangeRate);
        
        log.info("Interchange rate updated: paymentMethod={}, cardType={}, newRate={}", 
            event.getPaymentMethod(), event.getCardType(), newInterchangeRate);
    }
    
    private void processMonthlyFeeSummary(MerchantFeeCalculationEvent event, String correlationId) {
        LocalDate summaryMonth = event.getSummaryMonth();
        LocalDate startDate = summaryMonth.withDayOfMonth(1);
        LocalDate endDate = summaryMonth.withDayOfMonth(summaryMonth.lengthOfMonth());
        
        Map<String, Object> feeSummary = feeService.generateMonthlyFeeSummary(
            event.getMerchantId(),
            startDate,
            endDate
        );
        
        MerchantFeeCalculation summary = MerchantFeeCalculation.builder()
            .calculationId(event.getCalculationId())
            .merchantId(event.getMerchantId())
            .calculationType("MONTHLY_FEE_SUMMARY")
            .summaryMonth(summaryMonth)
            .transactionCount((Integer) feeSummary.get("transactionCount"))
            .transactionAmount((BigDecimal) feeSummary.get("totalAmount"))
            .totalFee((BigDecimal) feeSummary.get("totalFees"))
            .interchangeFee((BigDecimal) feeSummary.get("totalInterchange"))
            .processingFee((BigDecimal) feeSummary.get("totalProcessing"))
            .volumeDiscountAmount((BigDecimal) feeSummary.get("totalDiscounts"))
            .effectiveFeeRate((BigDecimal) feeSummary.get("effectiveRate"))
            .calculatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        feeCalculationRepository.save(summary);
        
        kafkaTemplate.send("monthly-fee-summary-generated", Map.of(
            "calculationId", event.getCalculationId(),
            "merchantId", event.getMerchantId(),
            "summaryMonth", summaryMonth,
            "feeSummary", feeSummary,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordMonthlyFeeSummary(
            event.getMerchantId(), 
            (BigDecimal) feeSummary.get("totalFees"),
            (BigDecimal) feeSummary.get("effectiveRate")
        );
        
        log.info("Monthly fee summary generated: merchantId={}, month={}, totalFees={}, effectiveRate={}", 
            event.getMerchantId(), summaryMonth, feeSummary.get("totalFees"), feeSummary.get("effectiveRate"));
    }
    
    private void processCustomRateCalculation(MerchantFeeCalculationEvent event, String correlationId) {
        BigDecimal customRate = event.getCustomRate();
        
        BigDecimal customFee = event.getTransactionAmount()
            .multiply(customRate)
            .setScale(SCALE, ROUNDING_MODE);
        
        MerchantFeeCalculation customCalculation = MerchantFeeCalculation.builder()
            .calculationId(event.getCalculationId())
            .merchantId(event.getMerchantId())
            .transactionId(event.getTransactionId())
            .transactionAmount(event.getTransactionAmount())
            .calculationType("CUSTOM_RATE_CALCULATION")
            .customRate(customRate)
            .totalFee(customFee)
            .netAmount(event.getTransactionAmount().subtract(customFee))
            .effectiveFeeRate(customRate)
            .calculatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        feeCalculationRepository.save(customCalculation);
        
        notificationService.sendNotification("FINANCE_TEAM", "Custom Rate Applied",
            String.format("Custom rate %.4f%% applied to merchant %s for transaction %s", 
                customRate.multiply(new BigDecimal("100")), event.getMerchantId(), event.getTransactionId()),
            correlationId);
        
        kafkaTemplate.send("custom-fee-calculated", Map.of(
            "calculationId", event.getCalculationId(),
            "merchantId", event.getMerchantId(),
            "transactionId", event.getTransactionId(),
            "customRate", customRate,
            "customFee", customFee,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordCustomRateCalculation(event.getMerchantId(), customRate, customFee);
        
        log.warn("Custom rate applied: calculationId={}, merchantId={}, customRate={}, customFee={}", 
            event.getCalculationId(), event.getMerchantId(), customRate, customFee);
    }
    
    private void processChargebackFeeCalculation(MerchantFeeCalculationEvent event, String correlationId) {
        BigDecimal chargebackFee = feeService.calculateChargebackFee(
            event.getMerchantId(),
            event.getTransactionAmount(),
            event.getChargebackReasonCode()
        );
        
        MerchantFeeCalculation chargebackCalculation = MerchantFeeCalculation.builder()
            .calculationId(event.getCalculationId())
            .merchantId(event.getMerchantId())
            .transactionId(event.getTransactionId())
            .transactionAmount(event.getTransactionAmount())
            .calculationType("CHARGEBACK_FEE_CALCULATION")
            .chargebackReasonCode(event.getChargebackReasonCode())
            .chargebackFee(chargebackFee)
            .totalFee(chargebackFee)
            .calculatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        feeCalculationRepository.save(chargebackCalculation);
        
        kafkaTemplate.send("chargeback-fee-calculated", Map.of(
            "calculationId", event.getCalculationId(),
            "merchantId", event.getMerchantId(),
            "transactionId", event.getTransactionId(),
            "chargebackFee", chargebackFee,
            "chargebackReasonCode", event.getChargebackReasonCode(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordChargebackFeeCalculation(
            event.getMerchantId(), 
            event.getChargebackReasonCode(), 
            chargebackFee
        );
        
        log.error("Chargeback fee calculated: calculationId={}, merchantId={}, chargebackFee={}, reasonCode={}", 
            event.getCalculationId(), event.getMerchantId(), chargebackFee, event.getChargebackReasonCode());
    }
}