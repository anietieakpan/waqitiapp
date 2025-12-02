package com.waqiti.currency.kafka;

import com.waqiti.common.events.FXRateUpdateEvent;
import com.waqiti.currency.domain.ExchangeRate;
import com.waqiti.currency.repository.ExchangeRateRepository;
import com.waqiti.currency.service.FXRateService;
import com.waqiti.currency.service.MarketDataService;
import com.waqiti.currency.service.RateValidationService;
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

/**
 * FX Rate Update Events Consumer
 * Processes real-time foreign exchange rate updates from multiple sources
 * Implements 12-step zero-tolerance processing for FX rate management
 * 
 * Business Context:
 * - Real-time rate feeds from: Reuters, Bloomberg, XE, ECB, Federal Reserve
 * - Mid-market rates, bid/ask spreads, volatility indicators
 * - Update frequency: Real-time (streaming) or periodic (1-60 minutes)
 * - 180+ currency pairs monitored
 * - Rate validation: Cross-rate consistency, outlier detection, stale rate alerts
 * - Revenue impact: FX markup on customer transactions (50-200 bps typical)
 * - Risk management: Rate volatility monitoring, circuit breakers
 * 
 * Rate Sources Priority:
 * 1. Tier-1 banks (direct feeds)
 * 2. Reuters/Bloomberg (market standard)
 * 3. Central banks (ECB, Fed, BoE, BoJ)
 * 4. Aggregators (XE, OANDA)
 * 
 * @author Waqiti Currency Services Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FXRateUpdateEventsConsumer {
    
    private final ExchangeRateRepository exchangeRateRepository;
    private final FXRateService fxRateService;
    private final MarketDataService marketDataService;
    private final RateValidationService rateValidationService;
    private final CurrencyMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal VOLATILITY_THRESHOLD = new BigDecimal("0.05"); // 5% move
    private static final BigDecimal SPREAD_WARNING_THRESHOLD = new BigDecimal("0.01"); // 1% spread
    private static final int STALE_RATE_MINUTES = 15;
    
    @KafkaListener(
        topics = {"fx-rate-update-events", "exchange-rate-feed", "currency-rate-updates"},
        groupId = "currency-fx-rate-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public void handleFXRateUpdateEvent(
            @Payload FXRateUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("fxrate-%s-%s-p%d-o%d", 
            event.getBaseCurrency(), event.getQuoteCurrency(), partition, offset);
        
        long startTime = System.currentTimeMillis();
        
        try {
            String currencyPair = event.getBaseCurrency() + "/" + event.getQuoteCurrency();
            
            log.debug("Processing FX rate update: pair={}, rate={}, source={}", 
                currencyPair, event.getMidRate(), event.getRateSource());
            
            // Step 1: Rate source validation
            if (!rateValidationService.isAuthorizedSource(event.getRateSource())) {
                log.warn("Unauthorized rate source: {}", event.getRateSource());
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Rate sanity checks
            if (!rateValidationService.isValidRate(event.getMidRate())) {
                log.error("Invalid rate value: pair={}, rate={}", currencyPair, event.getMidRate());
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Fetch previous rate
            ExchangeRate previousRate = exchangeRateRepository
                .findLatestByPair(event.getBaseCurrency(), event.getQuoteCurrency())
                .orElse(null);
            
            // Step 4: Calculate rate change and volatility
            BigDecimal rateChange = BigDecimal.ZERO;
            BigDecimal volatilityPct = BigDecimal.ZERO;
            
            if (previousRate != null) {
                rateChange = event.getMidRate().subtract(previousRate.getMidRate());
                volatilityPct = rateChange.divide(previousRate.getMidRate(), 6, RoundingMode.HALF_UP).abs();
                
                // Detect unusual volatility
                if (volatilityPct.compareTo(VOLATILITY_THRESHOLD) > 0) {
                    log.warn("High volatility detected: pair={}, change={}%, previousRate={}, newRate={}", 
                        currencyPair, volatilityPct.multiply(new BigDecimal("100")), 
                        previousRate.getMidRate(), event.getMidRate());
                    
                    fxRateService.triggerVolatilityAlert(event.getBaseCurrency(), 
                        event.getQuoteCurrency(), volatilityPct);
                }
            }
            
            // Step 5: Validate bid/ask spread
            BigDecimal spread = event.getAskRate().subtract(event.getBidRate());
            BigDecimal spreadPct = spread.divide(event.getMidRate(), 6, RoundingMode.HALF_UP);
            
            if (spreadPct.compareTo(SPREAD_WARNING_THRESHOLD) > 0) {
                log.warn("Wide spread detected: pair={}, spread={}%", currencyPair, 
                    spreadPct.multiply(new BigDecimal("100")));
            }
            
            // Step 6: Cross-rate validation
            boolean crossRateValid = rateValidationService.validateCrossRates(
                event.getBaseCurrency(), event.getQuoteCurrency(), event.getMidRate());
            
            if (!crossRateValid) {
                log.error("Cross-rate validation failed: pair={}", currencyPair);
                fxRateService.flagForManualReview(currencyPair, event.getMidRate(), "CROSS_RATE_MISMATCH");
            }
            
            // Step 7: Create new exchange rate record
            ExchangeRate exchangeRate = ExchangeRate.builder()
                .id(UUID.randomUUID().toString())
                .baseCurrency(event.getBaseCurrency())
                .quoteCurrency(event.getQuoteCurrency())
                .midRate(event.getMidRate())
                .bidRate(event.getBidRate())
                .askRate(event.getAskRate())
                .spread(spread)
                .spreadPercentage(spreadPct)
                .rateSource(event.getRateSource())
                .rateTimestamp(event.getRateTimestamp())
                .receivedAt(LocalDateTime.now())
                .rateChange(rateChange)
                .volatility(volatilityPct)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusMinutes(STALE_RATE_MINUTES))
                .active(true)
                .correlationId(correlationId)
                .build();
            
            exchangeRateRepository.save(exchangeRate);
            
            // Step 8: Deactivate previous rate
            if (previousRate != null) {
                previousRate.setActive(false);
                previousRate.setValidUntil(LocalDateTime.now());
                exchangeRateRepository.save(previousRate);
            }
            
            // Step 9: Update rate cache for fast lookups
            fxRateService.updateRateCache(exchangeRate);
            
            // Step 10: Recalculate customer rates with markup
            BigDecimal customerBuyRate = fxRateService.calculateCustomerBuyRate(
                event.getAskRate(), event.getBaseCurrency(), event.getQuoteCurrency());
            BigDecimal customerSellRate = fxRateService.calculateCustomerSellRate(
                event.getBidRate(), event.getBaseCurrency(), event.getQuoteCurrency());
            
            exchangeRate.setCustomerBuyRate(customerBuyRate);
            exchangeRate.setCustomerSellRate(customerSellRate);
            exchangeRateRepository.save(exchangeRate);
            
            // Step 11: Update related currency pairs (inverse, crosses)
            fxRateService.updateInverseRate(exchangeRate);
            fxRateService.recalculateCrossRates(event.getBaseCurrency(), event.getQuoteCurrency());
            
            // Step 12: Metrics and monitoring
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordRateUpdate(currencyPair, event.getRateSource(), processingTime);
            
            if (volatilityPct.compareTo(new BigDecimal("0.02")) > 0) {
                metricsService.recordHighVolatilityEvent(currencyPair, volatilityPct);
            }
            
            acknowledgment.acknowledge();
            
            if (processingTime > 100) {
                log.warn("Slow rate update processing: pair={}, time={}ms", currencyPair, processingTime);
            }
            
        } catch (Exception e) {
            log.error("Failed to process FX rate update event: {}", e.getMessage(), e);
            kafkaTemplate.send("fx-rate-update-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
}