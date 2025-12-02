package com.waqiti.currency.kafka;

import com.waqiti.common.events.CurrencyMarkupEvent;
import com.waqiti.currency.domain.CurrencyMarkup;
import com.waqiti.currency.repository.CurrencyMarkupRepository;
import com.waqiti.currency.service.MarkupService;
import com.waqiti.currency.service.PricingService;
import com.waqiti.currency.service.CustomerTierService;
import com.waqiti.currency.metrics.CurrencyMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CurrencyMarkupEventsConsumer {
    
    private final CurrencyMarkupRepository markupRepository;
    private final MarkupService markupService;
    private final PricingService pricingService;
    private final CustomerTierService customerTierService;
    private final CurrencyMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MIN_MARKUP_BPS = new BigDecimal("50");
    private static final BigDecimal MAX_MARKUP_BPS = new BigDecimal("300");
    private static final BigDecimal VIP_MARKUP_BPS = new BigDecimal("50");
    private static final BigDecimal STANDARD_MARKUP_BPS = new BigDecimal("150");
    
    @KafkaListener(
        topics = {"currency-markup-events", "fx-pricing-events", "markup-configuration-events"},
        groupId = "currency-markup-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 60)
    public void handleCurrencyMarkupEvent(
            @Payload CurrencyMarkupEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("markup-%s-%s-p%d-o%d", 
            event.getBaseCurrency(), event.getQuoteCurrency(), partition, offset);
        
        log.info("Processing currency markup event: type={}, corridor={}/{}, markup={}bps", 
            event.getEventType(), event.getBaseCurrency(), event.getQuoteCurrency(), event.getMarkupBasisPoints());
        
        try {
            switch (event.getEventType()) {
                case MARKUP_CALCULATED:
                    processMarkupCalculated(event, correlationId);
                    break;
                case MARKUP_UPDATED:
                    processMarkupUpdated(event, correlationId);
                    break;
                case TIER_MARKUP_APPLIED:
                    processTierMarkupApplied(event, correlationId);
                    break;
                case VOLUME_DISCOUNT_APPLIED:
                    processVolumeDiscountApplied(event, correlationId);
                    break;
                case CORRIDOR_MARKUP_CHANGED:
                    processCorridorMarkupChanged(event, correlationId);
                    break;
                case PROMOTIONAL_MARKUP_APPLIED:
                    processPromotionalMarkupApplied(event, correlationId);
                    break;
                case MARKUP_OVERRIDE_REQUESTED:
                    processMarkupOverrideRequested(event, correlationId);
                    break;
                case MARKUP_OVERRIDE_APPROVED:
                    processMarkupOverrideApproved(event, correlationId);
                    break;
                case REVENUE_CALCULATED:
                    processRevenueCalculated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown currency markup event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logPricingEvent(
                "CURRENCY_MARKUP_EVENT_PROCESSED",
                event.getMarkupId() != null ? event.getMarkupId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "corridor", event.getBaseCurrency() + "/" + event.getQuoteCurrency(),
                    "markupBps", event.getMarkupBasisPoints() != null ? event.getMarkupBasisPoints().toString() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process currency markup event: {}", e.getMessage(), e);
            kafkaTemplate.send("currency-markup-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processMarkupCalculated(CurrencyMarkupEvent event, String correlationId) {
        log.info("Markup calculated: corridor={}/{}, baseMar kup={}bps, tierAdjustment={}bps, finalMarkup={}bps", 
            event.getBaseCurrency(), event.getQuoteCurrency(), 
            event.getBaseMarkup(), event.getTierAdjustment(), event.getMarkupBasisPoints());
        
        CurrencyMarkup markup = CurrencyMarkup.builder()
            .id(UUID.randomUUID().toString())
            .baseCurrency(event.getBaseCurrency())
            .quoteCurrency(event.getQuoteCurrency())
            .baseMarkup(event.getBaseMarkup())
            .tierAdjustment(event.getTierAdjustment())
            .volumeDiscount(event.getVolumeDiscount())
            .finalMarkup(event.getMarkupBasisPoints())
            .customerTier(event.getCustomerTier())
            .corridor(event.getCorridor())
            .calculatedAt(LocalDateTime.now())
            .validFrom(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusHours(24))
            .isActive(true)
            .correlationId(correlationId)
            .build();
        
        markupRepository.save(markup);
        
        pricingService.updateCustomerRates(event.getBaseCurrency(), event.getQuoteCurrency(), 
            event.getMarkupBasisPoints());
        
        metricsService.recordMarkupCalculated(event.getCorridor(), event.getMarkupBasisPoints());
    }
    
    private void processMarkupUpdated(CurrencyMarkupEvent event, String correlationId) {
        log.info("Markup updated: markupId={}, oldMarkup={}bps, newMarkup={}bps, reason={}", 
            event.getMarkupId(), event.getOldMarkup(), event.getNewMarkup(), event.getUpdateReason());
        
        CurrencyMarkup markup = markupRepository.findById(event.getMarkupId())
            .orElseThrow();
        
        markup.setFinalMarkup(event.getNewMarkup());
        markup.setLastUpdated(LocalDateTime.now());
        markup.setUpdateReason(event.getUpdateReason());
        markupRepository.save(markup);
        
        BigDecimal markupChange = event.getNewMarkup().subtract(event.getOldMarkup());
        BigDecimal markupChangePct = markupChange.divide(event.getOldMarkup(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        log.info("Markup change: {}bps ({}%)", markupChange, markupChangePct);
        
        pricingService.updateCustomerRates(markup.getBaseCurrency(), markup.getQuoteCurrency(), 
            event.getNewMarkup());
        
        metricsService.recordMarkupUpdated(event.getUpdateReason(), markupChange);
    }
    
    private void processTierMarkupApplied(CurrencyMarkupEvent event, String correlationId) {
        log.info("Tier markup applied: userId={}, tier={}, standardMarkup={}bps, tierMarkup={}bps, discount={}bps", 
            event.getUserId(), event.getCustomerTier(), 
            STANDARD_MARKUP_BPS, event.getMarkupBasisPoints(), 
            STANDARD_MARKUP_BPS.subtract(event.getMarkupBasisPoints()));
        
        String customerTier = customerTierService.getCustomerTier(event.getUserId());
        BigDecimal tierMarkup = calculateTierMarkup(customerTier, event.getCorridor());
        
        markupService.applyTierMarkup(event.getUserId(), event.getBaseCurrency(), 
            event.getQuoteCurrency(), tierMarkup);
        
        if (tierMarkup.compareTo(STANDARD_MARKUP_BPS) < 0) {
            BigDecimal savings = STANDARD_MARKUP_BPS.subtract(tierMarkup);
            log.info("Customer saves {}bps due to {} tier", savings, customerTier);
        }
        
        metricsService.recordTierMarkupApplied(customerTier, tierMarkup);
    }
    
    private void processVolumeDiscountApplied(CurrencyMarkupEvent event, String correlationId) {
        log.info("Volume discount applied: userId={}, monthlyVolume={}, discount={}bps, finalMarkup={}bps", 
            event.getUserId(), event.getMonthlyVolume(), 
            event.getVolumeDiscount(), event.getMarkupBasisPoints());
        
        BigDecimal discount = calculateVolumeDiscount(event.getMonthlyVolume());
        
        markupService.applyVolumeDiscount(event.getUserId(), event.getBaseCurrency(), 
            event.getQuoteCurrency(), discount);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Volume Discount Applied",
            String.format("Congratulations! You've earned a %s bps discount on FX transactions " +
                "based on your monthly volume of %s.",
                discount, event.getMonthlyVolume()),
            correlationId
        );
        
        metricsService.recordVolumeDiscountApplied(event.getMonthlyVolume(), discount);
    }
    
    private void processCorridorMarkupChanged(CurrencyMarkupEvent event, String correlationId) {
        log.info("Corridor markup changed: corridor={}, oldMarkup={}bps, newMarkup={}bps, effectiveDate={}", 
            event.getCorridor(), event.getOldMarkup(), event.getNewMarkup(), event.getEffectiveDate());
        
        List<CurrencyMarkup> existingMarkups = markupRepository
            .findByCorridor(event.getCorridor());
        
        for (CurrencyMarkup markup : existingMarkups) {
            markup.setIsActive(false);
            markup.setValidUntil(LocalDateTime.now());
            markupRepository.save(markup);
        }
        
        CurrencyMarkup newMarkup = CurrencyMarkup.builder()
            .id(UUID.randomUUID().toString())
            .baseCurrency(event.getBaseCurrency())
            .quoteCurrency(event.getQuoteCurrency())
            .corridor(event.getCorridor())
            .baseMarkup(event.getNewMarkup())
            .finalMarkup(event.getNewMarkup())
            .calculatedAt(LocalDateTime.now())
            .validFrom(event.getEffectiveDate())
            .isActive(true)
            .changeReason(event.getChangeReason())
            .correlationId(correlationId)
            .build();
        
        markupRepository.save(newMarkup);
        
        pricingService.broadcastPricingUpdate(event.getCorridor(), event.getNewMarkup());
        
        metricsService.recordCorridorMarkupChanged(event.getCorridor(), event.getOldMarkup(), event.getNewMarkup());
    }
    
    private void processPromotionalMarkupApplied(CurrencyMarkupEvent event, String correlationId) {
        log.info("Promotional markup applied: userId={}, promoCode={}, standardMarkup={}bps, promoMarkup={}bps", 
            event.getUserId(), event.getPromoCode(), 
            event.getStandardMarkup(), event.getPromotionalMarkup());
        
        CurrencyMarkup promoMarkup = CurrencyMarkup.builder()
            .id(UUID.randomUUID().toString())
            .baseCurrency(event.getBaseCurrency())
            .quoteCurrency(event.getQuoteCurrency())
            .userId(event.getUserId())
            .baseMarkup(event.getStandardMarkup())
            .finalMarkup(event.getPromotionalMarkup())
            .promotionCode(event.getPromoCode())
            .calculatedAt(LocalDateTime.now())
            .validFrom(LocalDateTime.now())
            .validUntil(event.getPromoExpiry())
            .isActive(true)
            .isPromotional(true)
            .correlationId(correlationId)
            .build();
        
        markupRepository.save(promoMarkup);
        
        BigDecimal savings = event.getStandardMarkup().subtract(event.getPromotionalMarkup());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Promotional Rate Applied",
            String.format("Your promo code %s has been applied! You're saving %s bps on %s/%s conversions until %s.",
                event.getPromoCode(), savings, event.getBaseCurrency(), event.getQuoteCurrency(), 
                event.getPromoExpiry()),
            correlationId
        );
        
        metricsService.recordPromotionalMarkupApplied(event.getPromoCode(), savings);
    }
    
    private void processMarkupOverrideRequested(CurrencyMarkupEvent event, String correlationId) {
        log.info("Markup override requested: userId={}, currentMarkup={}bps, requestedMarkup={}bps, reason={}", 
            event.getUserId(), event.getCurrentMarkup(), event.getRequestedMarkup(), event.getOverrideReason());
        
        if (event.getRequestedMarkup().compareTo(MIN_MARKUP_BPS) < 0) {
            log.error("Requested markup below minimum: requested={}bps, minimum={}bps", 
                event.getRequestedMarkup(), MIN_MARKUP_BPS);
            markupService.rejectOverrideRequest(event.getOverrideRequestId(), "BELOW_MINIMUM");
            return;
        }
        
        BigDecimal discount = event.getCurrentMarkup().subtract(event.getRequestedMarkup());
        BigDecimal discountPct = discount.divide(event.getCurrentMarkup(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        if (discountPct.compareTo(new BigDecimal("25")) > 0) {
            log.warn("Large discount requested: {}%, requires senior approval", discountPct);
            markupService.escalateToSeniorApproval(event.getOverrideRequestId());
        } else {
            markupService.routeForApproval(event.getOverrideRequestId());
        }
        
        metricsService.recordMarkupOverrideRequested(event.getOverrideReason(), discount);
    }
    
    private void processMarkupOverrideApproved(CurrencyMarkupEvent event, String correlationId) {
        log.info("Markup override approved: overrideId={}, approvedBy={}, approvedMarkup={}bps", 
            event.getOverrideRequestId(), event.getApprovedBy(), event.getApprovedMarkup());
        
        CurrencyMarkup overrideMarkup = CurrencyMarkup.builder()
            .id(UUID.randomUUID().toString())
            .baseCurrency(event.getBaseCurrency())
            .quoteCurrency(event.getQuoteCurrency())
            .userId(event.getUserId())
            .baseMarkup(event.getCurrentMarkup())
            .finalMarkup(event.getApprovedMarkup())
            .calculatedAt(LocalDateTime.now())
            .validFrom(LocalDateTime.now())
            .validUntil(event.getOverrideExpiry())
            .isActive(true)
            .isOverride(true)
            .overrideReason(event.getOverrideReason())
            .approvedBy(event.getApprovedBy())
            .correlationId(correlationId)
            .build();
        
        markupRepository.save(overrideMarkup);
        
        markupService.applyOverrideMarkup(event.getUserId(), event.getBaseCurrency(), 
            event.getQuoteCurrency(), event.getApprovedMarkup());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Special Rate Approved",
            String.format("Your request for a special rate has been approved. You'll receive %s bps markup " +
                "on %s/%s until %s.",
                event.getApprovedMarkup(), event.getBaseCurrency(), event.getQuoteCurrency(), 
                event.getOverrideExpiry()),
            correlationId
        );
        
        metricsService.recordMarkupOverrideApproved(event.getApprovedBy());
    }
    
    private void processRevenueCalculated(CurrencyMarkupEvent event, String correlationId) {
        log.info("Revenue calculated: corridor={}, transactionVolume={}, markupRevenue={}, period={}", 
            event.getCorridor(), event.getTransactionVolume(), event.getMarkupRevenue(), event.getRevenuePeriod());
        
        BigDecimal effectiveMarkupBps = event.getMarkupRevenue()
            .divide(event.getTransactionVolume(), 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("10000"));
        
        log.info("Effective markup: {}bps, revenue: {}, volume: {}", 
            effectiveMarkupBps, event.getMarkupRevenue(), event.getTransactionVolume());
        
        metricsService.recordRevenueCalculated(
            event.getCorridor(), 
            event.getTransactionVolume(), 
            event.getMarkupRevenue(), 
            effectiveMarkupBps
        );
    }
    
    private BigDecimal calculateTierMarkup(String tier, String corridor) {
        return switch (tier) {
            case "VIP", "PLATINUM" -> new BigDecimal("50");
            case "GOLD" -> new BigDecimal("75");
            case "SILVER" -> new BigDecimal("100");
            case "BRONZE" -> new BigDecimal("125");
            default -> new BigDecimal("150");
        };
    }
    
    private BigDecimal calculateVolumeDiscount(BigDecimal monthlyVolume) {
        if (monthlyVolume.compareTo(new BigDecimal("1000000")) >= 0) {
            return new BigDecimal("50");
        } else if (monthlyVolume.compareTo(new BigDecimal("500000")) >= 0) {
            return new BigDecimal("30");
        } else if (monthlyVolume.compareTo(new BigDecimal("100000")) >= 0) {
            return new BigDecimal("15");
        } else if (monthlyVolume.compareTo(new BigDecimal("50000")) >= 0) {
            return new BigDecimal("10");
        } else {
            return BigDecimal.ZERO;
        }
    }
}