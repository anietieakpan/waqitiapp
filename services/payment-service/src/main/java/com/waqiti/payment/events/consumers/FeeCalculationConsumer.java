package com.waqiti.payment.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.payment.FeeCalculationEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.payment.domain.FeeCalculation;
import com.waqiti.payment.domain.FeeType;
import com.waqiti.payment.domain.FeeStatus;
import com.waqiti.payment.domain.FeeTier;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.repository.FeeCalculationRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.FeeService;
import com.waqiti.payment.service.MerchantFeeService;
import com.waqiti.payment.service.ProviderFeeService;
import com.waqiti.payment.service.DynamicPricingService;
import com.waqiti.payment.service.FeeNotificationService;
import com.waqiti.common.exceptions.FeeCalculationException;

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
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade consumer for fee calculation events.
 * Handles comprehensive transaction fee processing including:
 * - Processing fees (percentage and fixed)
 * - Platform fees and commissions
 * - Provider-specific fees
 * - Cross-border and FX fees
 * - Volume-based tiered pricing
 * - Dynamic pricing adjustments
 * - Fee splits and distributions
 * 
 * Critical for revenue management and transparent pricing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeeCalculationConsumer {

    private final FeeCalculationRepository feeRepository;
    private final PaymentRepository paymentRepository;
    private final FeeService feeService;
    private final MerchantFeeService merchantFeeService;
    private final ProviderFeeService providerFeeService;
    private final DynamicPricingService pricingService;
    private final FeeNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final BigDecimal MAX_FEE_PERCENTAGE = new BigDecimal("10.0"); // 10% max
    private static final BigDecimal MIN_TRANSACTION_FEE = new BigDecimal("0.10");

    @KafkaListener(
        topics = "fee-calculation-requests",
        groupId = "payment-service-fee-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        include = {FeeCalculationException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleFeeCalculation(
            @Payload FeeCalculationEvent feeEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "fee-override", required = false) String feeOverride,
            Acknowledgment acknowledgment) {

        String eventId = feeEvent.getEventId() != null ? 
            feeEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing fee calculation: {} for transaction: {} amount: {} type: {}", 
                    eventId, feeEvent.getTransactionId(), 
                    feeEvent.getTransactionAmount(), feeEvent.getTransactionType());

            // Metrics tracking
            metricsService.incrementCounter("fee.calculation.processing.started",
                Map.of(
                    "transaction_type", feeEvent.getTransactionType(),
                    "merchant_category", feeEvent.getMerchantCategory()
                ));

            // Idempotency check
            if (isFeeAlreadyCalculated(feeEvent.getTransactionId(), eventId)) {
                log.info("Fee {} already calculated for transaction {}", eventId, feeEvent.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // Retrieve payment if needed
            Payment payment = null;
            if (feeEvent.getTransactionId() != null) {
                payment = getPayment(feeEvent.getTransactionId());
            }

            // Create fee calculation record
            FeeCalculation feeCalculation = createFeeCalculation(feeEvent, payment, eventId, correlationId);

            // Calculate all fee components
            calculateFeeComponents(feeCalculation, feeEvent, payment);

            // Apply tiered pricing if applicable
            applyTieredPricing(feeCalculation, feeEvent);

            // Apply dynamic pricing adjustments
            applyDynamicPricing(feeCalculation, feeEvent);

            // Apply fee overrides if provided
            if (feeOverride != null) {
                applyFeeOverride(feeCalculation, feeOverride, feeEvent);
            }

            // Calculate fee splits
            calculateFeeSplits(feeCalculation, feeEvent);

            // Validate fee limits
            validateFeeLimits(feeCalculation, feeEvent);

            // Update fee status
            updateFeeStatus(feeCalculation);

            // Save fee calculation
            FeeCalculation savedFee = feeRepository.save(feeCalculation);

            // Update payment with fees
            if (payment != null) {
                updatePaymentFees(payment, savedFee);
            }

            // Process fee distribution
            processFeeDistribution(savedFee, feeEvent);

            // Send notifications
            sendFeeNotifications(savedFee, feeEvent);

            // Update metrics
            updateFeeMetrics(savedFee, feeEvent);

            // Create audit trail
            createFeeAuditLog(savedFee, feeEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("fee.calculation.processing.success",
                Map.of(
                    "total_fee", savedFee.getTotalFee().toString(),
                    "status", savedFee.getStatus().toString()
                ));

            log.info("Successfully calculated fees: {} for transaction: {} total: {} status: {}", 
                    savedFee.getId(), feeEvent.getTransactionId(), 
                    savedFee.getTotalFee(), savedFee.getStatus());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing fee calculation event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("fee.calculation.processing.error");
            
            auditLogger.logError("FEE_CALCULATION_ERROR",
                "system", feeEvent.getMerchantId(), e.getMessage(),
                Map.of(
                    "transactionId", feeEvent.getTransactionId(),
                    "amount", feeEvent.getTransactionAmount().toString(),
                    "eventId", eventId,
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new FeeCalculationException("Failed to calculate fees: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "fee-calculation-bulk",
        groupId = "payment-service-bulk-fee-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleBulkFeeCalculation(
            @Payload List<FeeCalculationEvent> feeEvents,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("Processing bulk fee calculation for {} transactions", feeEvents.size());

            List<FeeCalculation> calculations = new ArrayList<>();
            
            for (FeeCalculationEvent event : feeEvents) {
                try {
                    FeeCalculation calculation = processSingleFeeCalculation(event, correlationId);
                    calculations.add(calculation);
                } catch (Exception e) {
                    log.error("Failed to calculate fee for transaction {}: {}", 
                            event.getTransactionId(), e.getMessage());
                }
            }

            // Save all calculations
            feeRepository.saveAll(calculations);

            log.info("Completed bulk fee calculation: {} successful out of {} total",
                    calculations.size(), feeEvents.size());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process bulk fee calculation: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isFeeAlreadyCalculated(String transactionId, String eventId) {
        return feeRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private Payment getPayment(String transactionId) {
        return paymentRepository.findById(transactionId)
            .orElse(null);
    }

    private FeeCalculation createFeeCalculation(FeeCalculationEvent event, Payment payment, String eventId, String correlationId) {
        return FeeCalculation.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .transactionId(event.getTransactionId())
            .merchantId(event.getMerchantId())
            .userId(event.getUserId())
            .transactionAmount(event.getTransactionAmount())
            .currency(event.getCurrency())
            .transactionType(event.getTransactionType())
            .paymentMethod(event.getPaymentMethod())
            .provider(event.getProvider())
            .merchantCategory(event.getMerchantCategory())
            .correlationId(correlationId)
            .status(FeeStatus.CALCULATING)
            .calculatedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void calculateFeeComponents(FeeCalculation calculation, FeeCalculationEvent event, Payment payment) {
        log.info("Calculating fee components for transaction: {}", calculation.getTransactionId());

        BigDecimal transactionAmount = calculation.getTransactionAmount();

        // 1. Processing Fee (Percentage + Fixed)
        BigDecimal processingPercentage = merchantFeeService.getProcessingPercentage(
            event.getMerchantId(),
            event.getTransactionType()
        );
        BigDecimal processingFixed = merchantFeeService.getProcessingFixed(
            event.getMerchantId(),
            event.getTransactionType()
        );
        
        BigDecimal processingFee = transactionAmount
            .multiply(processingPercentage)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
            .add(processingFixed);
        
        calculation.setProcessingFeePercentage(processingPercentage);
        calculation.setProcessingFeeFixed(processingFixed);
        calculation.setProcessingFee(processingFee);

        // 2. Platform Fee
        BigDecimal platformPercentage = feeService.getPlatformFeePercentage(
            event.getMerchantCategory(),
            transactionAmount
        );
        BigDecimal platformFee = transactionAmount
            .multiply(platformPercentage)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        
        calculation.setPlatformFeePercentage(platformPercentage);
        calculation.setPlatformFee(platformFee);

        // 3. Provider Fee
        BigDecimal providerFee = providerFeeService.calculateProviderFee(
            event.getProvider(),
            event.getPaymentMethod(),
            transactionAmount
        );
        calculation.setProviderFee(providerFee);

        // 4. Cross-Border Fee (if applicable)
        if (event.isCrossBorder()) {
            BigDecimal crossBorderPercentage = feeService.getCrossBorderFeePercentage();
            BigDecimal crossBorderFee = transactionAmount
                .multiply(crossBorderPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            calculation.setCrossBorderFee(crossBorderFee);
            calculation.setCrossBorder(true);
        }

        // 5. FX Fee (if currency conversion)
        if (event.getSourceCurrency() != null && 
            !event.getSourceCurrency().equals(event.getCurrency())) {
            
            BigDecimal fxPercentage = feeService.getFxFeePercentage();
            BigDecimal fxFee = transactionAmount
                .multiply(fxPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            calculation.setFxFee(fxFee);
            calculation.setFxApplied(true);
            calculation.setSourceCurrency(event.getSourceCurrency());
            calculation.setTargetCurrency(event.getCurrency());
        }

        // 6. Risk Fee (for high-risk transactions)
        if (event.getRiskScore() != null && event.getRiskScore() > 70) {
            BigDecimal riskFee = calculateRiskFee(transactionAmount, event.getRiskScore());
            calculation.setRiskFee(riskFee);
            calculation.setRiskScore(event.getRiskScore());
        }

        // 7. Regulatory Fee
        BigDecimal regulatoryFee = feeService.getRegulatoryFee(
            event.getTransactionType(),
            event.getMerchantCategory()
        );
        calculation.setRegulatoryFee(regulatoryFee);

        // Calculate subtotal
        BigDecimal subtotal = processingFee
            .add(platformFee)
            .add(providerFee)
            .add(calculation.getCrossBorderFee() != null ? calculation.getCrossBorderFee() : BigDecimal.ZERO)
            .add(calculation.getFxFee() != null ? calculation.getFxFee() : BigDecimal.ZERO)
            .add(calculation.getRiskFee() != null ? calculation.getRiskFee() : BigDecimal.ZERO)
            .add(regulatoryFee);
        
        calculation.setSubtotalFee(subtotal);
    }

    private void applyTieredPricing(FeeCalculation calculation, FeeCalculationEvent event) {
        try {
            // Get merchant's volume tier
            BigDecimal monthlyVolume = merchantFeeService.getMonthlyVolume(
                event.getMerchantId(),
                LocalDateTime.now().minusDays(30)
            );

            FeeTier tier = determineTier(monthlyVolume);
            calculation.setFeeTier(tier);

            // Apply tier discount
            BigDecimal tierDiscount = getTierDiscount(tier);
            if (tierDiscount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountAmount = calculation.getSubtotalFee()
                    .multiply(tierDiscount)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                
                calculation.setTierDiscount(discountAmount);
                calculation.setTierDiscountPercentage(tierDiscount);
                
                // Update subtotal after discount
                calculation.setSubtotalFee(
                    calculation.getSubtotalFee().subtract(discountAmount)
                );
            }

            calculation.setMonthlyVolume(monthlyVolume);

        } catch (Exception e) {
            log.error("Error applying tiered pricing: {}", e.getMessage());
        }
    }

    private void applyDynamicPricing(FeeCalculation calculation, FeeCalculationEvent event) {
        try {
            // Apply dynamic pricing based on various factors
            var pricingAdjustment = pricingService.calculateDynamicAdjustment(
                event.getMerchantId(),
                event.getTransactionType(),
                calculation.getTransactionAmount(),
                LocalDateTime.now()
            );

            if (pricingAdjustment != null) {
                // Peak hour surcharge
                if (pricingAdjustment.isPeakHour()) {
                    BigDecimal peakSurcharge = calculation.getSubtotalFee()
                        .multiply(new BigDecimal("0.02")) // 2% peak surcharge
                        .setScale(2, RoundingMode.HALF_UP);
                    calculation.setPeakHourSurcharge(peakSurcharge);
                    calculation.setSubtotalFee(calculation.getSubtotalFee().add(peakSurcharge));
                }

                // Promotional discount
                if (pricingAdjustment.hasPromotion()) {
                    BigDecimal promoDiscount = pricingAdjustment.getPromotionDiscount();
                    calculation.setPromotionalDiscount(promoDiscount);
                    calculation.setPromotionCode(pricingAdjustment.getPromotionCode());
                    calculation.setSubtotalFee(calculation.getSubtotalFee().subtract(promoDiscount));
                }

                // Loyalty discount
                if (event.getUserId() != null) {
                    BigDecimal loyaltyDiscount = calculateLoyaltyDiscount(
                        event.getUserId(),
                        calculation.getSubtotalFee()
                    );
                    if (loyaltyDiscount.compareTo(BigDecimal.ZERO) > 0) {
                        calculation.setLoyaltyDiscount(loyaltyDiscount);
                        calculation.setSubtotalFee(calculation.getSubtotalFee().subtract(loyaltyDiscount));
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error applying dynamic pricing: {}", e.getMessage());
        }
    }

    private void applyFeeOverride(FeeCalculation calculation, String override, FeeCalculationEvent event) {
        try {
            log.info("Applying fee override: {} for transaction: {}", override, calculation.getTransactionId());

            // Parse override (format: "type:amount" or "type:percentage%")
            String[] parts = override.split(":");
            if (parts.length == 2) {
                String overrideType = parts[0];
                String overrideValue = parts[1];
                
                if (overrideValue.endsWith("%")) {
                    // Percentage override
                    BigDecimal percentage = new BigDecimal(overrideValue.replace("%", ""));
                    BigDecimal newFee = calculation.getTransactionAmount()
                        .multiply(percentage)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    
                    calculation.setOverrideFee(newFee);
                    calculation.setOverrideReason("Manual percentage override: " + percentage + "%");
                } else {
                    // Fixed amount override
                    BigDecimal fixedFee = new BigDecimal(overrideValue);
                    calculation.setOverrideFee(fixedFee);
                    calculation.setOverrideReason("Manual fixed override: " + fixedFee);
                }
                
                calculation.setHasOverride(true);
                calculation.setOverrideAppliedBy("system");
                calculation.setOverrideAppliedAt(LocalDateTime.now());
            }

        } catch (Exception e) {
            log.error("Error applying fee override: {}", e.getMessage());
        }
    }

    private void calculateFeeSplits(FeeCalculation calculation, FeeCalculationEvent event) {
        BigDecimal totalFee = calculation.getHasOverride() ? 
            calculation.getOverrideFee() : calculation.getSubtotalFee();
        
        calculation.setTotalFee(totalFee);

        // Calculate splits
        Map<String, BigDecimal> splits = new HashMap<>();
        
        // Platform share (e.g., 20% of total fee)
        BigDecimal platformShare = totalFee.multiply(new BigDecimal("0.20"))
            .setScale(2, RoundingMode.HALF_UP);
        splits.put("PLATFORM", platformShare);
        
        // Provider share (e.g., 30% of total fee)
        BigDecimal providerShare = totalFee.multiply(new BigDecimal("0.30"))
            .setScale(2, RoundingMode.HALF_UP);
        splits.put("PROVIDER", providerShare);
        
        // Acquirer share (remaining)
        BigDecimal acquirerShare = totalFee.subtract(platformShare).subtract(providerShare);
        splits.put("ACQUIRER", acquirerShare);
        
        calculation.setFeeSplits(splits);
        calculation.setPlatformRevenue(platformShare);
        
        // Calculate net amount for merchant
        BigDecimal netAmount = calculation.getTransactionAmount().subtract(totalFee);
        calculation.setNetAmount(netAmount);
    }

    private void validateFeeLimits(FeeCalculation calculation, FeeCalculationEvent event) {
        BigDecimal totalFee = calculation.getTotalFee();
        BigDecimal transactionAmount = calculation.getTransactionAmount();
        
        // Check maximum fee percentage
        BigDecimal feePercentage = totalFee
            .multiply(new BigDecimal("100"))
            .divide(transactionAmount, 2, RoundingMode.HALF_UP);
        
        if (feePercentage.compareTo(MAX_FEE_PERCENTAGE) > 0) {
            // Cap at maximum
            BigDecimal cappedFee = transactionAmount
                .multiply(MAX_FEE_PERCENTAGE)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            calculation.setTotalFee(cappedFee);
            calculation.setFeeCapped(true);
            calculation.setFeeCapReason("Exceeded maximum fee percentage of " + MAX_FEE_PERCENTAGE + "%");
            
            log.warn("Fee capped for transaction {}: was {}, capped to {}",
                    calculation.getTransactionId(), totalFee, cappedFee);
        }
        
        // Check minimum fee
        if (totalFee.compareTo(MIN_TRANSACTION_FEE) < 0) {
            calculation.setTotalFee(MIN_TRANSACTION_FEE);
            calculation.setFeeAdjusted(true);
            calculation.setFeeAdjustmentReason("Below minimum fee threshold");
        }
        
        calculation.setEffectiveFeePercentage(feePercentage);
    }

    private void updateFeeStatus(FeeCalculation calculation) {
        if (calculation.getTotalFee() != null && calculation.getNetAmount() != null) {
            calculation.setStatus(FeeStatus.CALCULATED);
            calculation.setCalculationCompletedAt(LocalDateTime.now());
        } else {
            calculation.setStatus(FeeStatus.FAILED);
            calculation.setFailureReason("Unable to calculate fees");
        }
        
        calculation.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(calculation.getCalculatedAt(), LocalDateTime.now())
        );
        calculation.setUpdatedAt(LocalDateTime.now());
    }

    private void updatePaymentFees(Payment payment, FeeCalculation feeCalculation) {
        payment.setProcessingFee(feeCalculation.getProcessingFee());
        payment.setPlatformFee(feeCalculation.getPlatformFee());
        payment.setTotalFees(feeCalculation.getTotalFee());
        payment.setNetAmount(feeCalculation.getNetAmount());
        payment.setFeeCalculationId(feeCalculation.getId());
        payment.setUpdatedAt(LocalDateTime.now());
        
        paymentRepository.save(payment);
    }

    private void processFeeDistribution(FeeCalculation calculation, FeeCalculationEvent event) {
        try {
            if (calculation.getFeeSplits() != null) {
                for (Map.Entry<String, BigDecimal> split : calculation.getFeeSplits().entrySet()) {
                    // Record fee distribution
                    feeService.recordFeeDistribution(
                        calculation.getId(),
                        split.getKey(),
                        split.getValue()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error processing fee distribution: {}", e.getMessage());
        }
    }

    private void sendFeeNotifications(FeeCalculation calculation, FeeCalculationEvent event) {
        try {
            // Notify merchant of fees
            if (event.getMerchantId() != null) {
                notificationService.sendFeeCalculationNotification(calculation);
            }
            
            // High fee alert
            if (calculation.getEffectiveFeePercentage().compareTo(new BigDecimal("5")) > 0) {
                notificationService.sendHighFeeAlert(calculation);
            }
            
            // Fee override notification
            if (calculation.getHasOverride()) {
                notificationService.sendFeeOverrideNotification(calculation);
            }
            
        } catch (Exception e) {
            log.error("Failed to send fee notifications: {}", e.getMessage());
        }
    }

    private void updateFeeMetrics(FeeCalculation calculation, FeeCalculationEvent event) {
        try {
            // Fee metrics
            metricsService.incrementCounter("fee.calculation.completed",
                Map.of(
                    "type", event.getTransactionType(),
                    "status", calculation.getStatus().toString()
                ));
            
            // Fee amounts
            metricsService.recordGauge("fee.amount.total", 
                calculation.getTotalFee().doubleValue(),
                Map.of("currency", calculation.getCurrency()));
            
            // Fee percentage
            metricsService.recordGauge("fee.percentage.effective", 
                calculation.getEffectiveFeePercentage().doubleValue(),
                Map.of("merchant_category", event.getMerchantCategory()));
            
            // Platform revenue
            if (calculation.getPlatformRevenue() != null) {
                metricsService.recordGauge("fee.revenue.platform", 
                    calculation.getPlatformRevenue().doubleValue(),
                    Map.of("currency", calculation.getCurrency()));
            }
            
        } catch (Exception e) {
            log.error("Failed to update fee metrics: {}", e.getMessage());
        }
    }

    private void createFeeAuditLog(FeeCalculation calculation, FeeCalculationEvent event, String correlationId) {
        auditLogger.logBusinessEvent(
            "FEE_CALCULATED",
            event.getUserId() != null ? event.getUserId() : "system",
            calculation.getId(),
            "FEE_CALCULATION",
            calculation.getTotalFee().doubleValue(),
            "fee_processor",
            calculation.getStatus() == FeeStatus.CALCULATED,
            Map.of(
                "calculationId", calculation.getId(),
                "transactionId", calculation.getTransactionId(),
                "merchantId", event.getMerchantId(),
                "transactionAmount", calculation.getTransactionAmount().toString(),
                "totalFee", calculation.getTotalFee().toString(),
                "netAmount", calculation.getNetAmount().toString(),
                "effectivePercentage", calculation.getEffectiveFeePercentage().toString(),
                "tier", calculation.getFeeTier() != null ? calculation.getFeeTier().toString() : "N/A",
                "hasOverride", String.valueOf(calculation.getHasOverride()),
                "processingTimeMs", String.valueOf(calculation.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private FeeCalculation processSingleFeeCalculation(FeeCalculationEvent event, String correlationId) {
        FeeCalculation calculation = createFeeCalculation(event, null, UUID.randomUUID().toString(), correlationId);
        
        calculateFeeComponents(calculation, event, null);
        applyTieredPricing(calculation, event);
        applyDynamicPricing(calculation, event);
        calculateFeeSplits(calculation, event);
        validateFeeLimits(calculation, event);
        updateFeeStatus(calculation);
        
        return calculation;
    }

    private BigDecimal calculateRiskFee(BigDecimal amount, Integer riskScore) {
        // Higher risk = higher fee
        BigDecimal riskMultiplier = new BigDecimal(riskScore).divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
        return amount.multiply(riskMultiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private FeeTier determineTier(BigDecimal monthlyVolume) {
        if (monthlyVolume.compareTo(new BigDecimal("1000000")) >= 0) {
            return FeeTier.ENTERPRISE;
        } else if (monthlyVolume.compareTo(new BigDecimal("100000")) >= 0) {
            return FeeTier.PREMIUM;
        } else if (monthlyVolume.compareTo(new BigDecimal("10000")) >= 0) {
            return FeeTier.STANDARD;
        } else {
            return FeeTier.STARTER;
        }
    }

    private BigDecimal getTierDiscount(FeeTier tier) {
        return switch (tier) {
            case ENTERPRISE -> new BigDecimal("30"); // 30% discount
            case PREMIUM -> new BigDecimal("20");    // 20% discount
            case STANDARD -> new BigDecimal("10");   // 10% discount
            case STARTER -> BigDecimal.ZERO;         // No discount
        };
    }

    private BigDecimal calculateLoyaltyDiscount(String userId, BigDecimal fee) {
        // Mock loyalty discount calculation
        return fee.multiply(new BigDecimal("0.05")); // 5% loyalty discount
    }
}