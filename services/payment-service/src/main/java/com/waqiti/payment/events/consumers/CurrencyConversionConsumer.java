package com.waqiti.payment.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.payment.CurrencyConversionEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.payment.domain.CurrencyConversion;
import com.waqiti.payment.domain.ConversionStatus;
import com.waqiti.payment.domain.ExchangeRateSource;
import com.waqiti.payment.domain.ConversionType;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.repository.CurrencyConversionRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.ExchangeRateService;
import com.waqiti.payment.service.FxProviderService;
import com.waqiti.payment.service.CurrencyValidationService;
import com.waqiti.payment.service.FxRiskManagementService;
import com.waqiti.payment.service.ConversionNotificationService;
import com.waqiti.common.exceptions.CurrencyConversionException;

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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade consumer for currency conversion events.
 * Handles comprehensive foreign exchange operations including:
 * - Real-time exchange rate fetching
 * - Multi-source rate aggregation
 * - Spread and markup calculations
 * - Currency hedging and risk management
 * - Rate locking and guarantees
 * - Conversion limits and controls
 * - Settlement currency optimization
 * 
 * Critical for international payments and multi-currency operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyConversionConsumer {

    private final CurrencyConversionRepository conversionRepository;
    private final PaymentRepository paymentRepository;
    private final ExchangeRateService rateService;
    private final FxProviderService fxProviderService;
    private final CurrencyValidationService validationService;
    private final FxRiskManagementService riskService;
    private final ConversionNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final BigDecimal MIN_CONVERSION_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_CONVERSION_AMOUNT = new BigDecimal("1000000");
    private static final int RATE_LOCK_MINUTES = 15;
    private static final BigDecimal MAX_SPREAD_PERCENTAGE = new BigDecimal("5.0");

    @KafkaListener(
        topics = "currency-conversion-requests",
        groupId = "payment-service-fx-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0),
        include = {CurrencyConversionException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleCurrencyConversion(
            @Payload CurrencyConversionEvent conversionEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "rate-lock", required = false) String rateLock,
            Acknowledgment acknowledgment) {

        String eventId = conversionEvent.getEventId() != null ? 
            conversionEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing currency conversion: {} from {} to {} amount: {}", 
                    eventId, conversionEvent.getSourceCurrency(), 
                    conversionEvent.getTargetCurrency(), conversionEvent.getSourceAmount());

            // Metrics tracking
            metricsService.incrementCounter("fx.conversion.processing.started",
                Map.of(
                    "source_currency", conversionEvent.getSourceCurrency(),
                    "target_currency", conversionEvent.getTargetCurrency()
                ));

            // Idempotency check
            if (isConversionAlreadyProcessed(conversionEvent.getReferenceId(), eventId)) {
                log.info("Conversion {} already processed for reference {}", 
                        eventId, conversionEvent.getReferenceId());
                acknowledgment.acknowledge();
                return;
            }

            // Validate currencies
            validateCurrencies(conversionEvent);

            // Create conversion record
            CurrencyConversion conversion = createConversionRecord(conversionEvent, eventId, correlationId);

            // Fetch exchange rates from multiple sources
            List<ExchangeRate> rates = fetchExchangeRates(conversion, conversionEvent);

            // Select best rate
            ExchangeRate bestRate = selectBestRate(rates, conversionEvent);
            applySelectedRate(conversion, bestRate);

            // Apply spread and markup
            applySpreadAndMarkup(conversion, conversionEvent);

            // Calculate conversion amounts
            calculateConversionAmounts(conversion, conversionEvent);

            // Apply rate lock if requested
            if ("true".equals(rateLock)) {
                applyRateLock(conversion, conversionEvent);
            }

            // Validate conversion limits
            validateConversionLimits(conversion, conversionEvent);

            // Check FX risk and hedging
            performRiskAssessment(conversion, conversionEvent);

            // Execute conversion
            executeConversion(conversion, conversionEvent);

            // Update conversion status
            updateConversionStatus(conversion);

            // Save conversion
            CurrencyConversion savedConversion = conversionRepository.save(conversion);

            // Update payment if applicable
            if (conversionEvent.getPaymentId() != null) {
                updatePaymentWithConversion(conversionEvent.getPaymentId(), savedConversion);
            }

            // Process settlement
            processSettlement(savedConversion, conversionEvent);

            // Send notifications
            sendConversionNotifications(savedConversion, conversionEvent);

            // Update metrics
            updateConversionMetrics(savedConversion, conversionEvent);

            // Create audit trail
            createConversionAuditLog(savedConversion, conversionEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("fx.conversion.processing.success",
                Map.of(
                    "status", savedConversion.getStatus().toString(),
                    "converted_amount", savedConversion.getTargetAmount().toString()
                ));

            log.info("Successfully processed currency conversion: {} amount: {} {} to {} {} rate: {}", 
                    savedConversion.getId(), savedConversion.getSourceAmount(), 
                    savedConversion.getSourceCurrency(), savedConversion.getTargetAmount(),
                    savedConversion.getTargetCurrency(), savedConversion.getFinalRate());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing currency conversion event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("fx.conversion.processing.error");
            
            auditLogger.logCriticalAlert("CURRENCY_CONVERSION_ERROR",
                "Critical FX conversion failure",
                Map.of(
                    "sourceCurrency", conversionEvent.getSourceCurrency(),
                    "targetCurrency", conversionEvent.getTargetCurrency(),
                    "amount", conversionEvent.getSourceAmount().toString(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new CurrencyConversionException("Failed to process currency conversion: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "currency-conversion-realtime",
        groupId = "payment-service-realtime-fx-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleRealtimeConversion(
            @Payload CurrencyConversionEvent conversionEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("REALTIME FX: Processing immediate conversion {} to {}", 
                    conversionEvent.getSourceCurrency(), conversionEvent.getTargetCurrency());

            // Fast conversion for real-time needs
            CurrencyConversion conversion = processInstantConversion(conversionEvent, correlationId);

            // Send immediate response
            notificationService.sendRealtimeConversionResult(conversion);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process realtime conversion: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isConversionAlreadyProcessed(String referenceId, String eventId) {
        return conversionRepository.existsByReferenceIdOrEventId(referenceId, eventId);
    }

    private void validateCurrencies(CurrencyConversionEvent event) {
        // Validate source currency
        if (!validationService.isValidCurrency(event.getSourceCurrency())) {
            throw new CurrencyConversionException("Invalid source currency: " + event.getSourceCurrency());
        }

        // Validate target currency
        if (!validationService.isValidCurrency(event.getTargetCurrency())) {
            throw new CurrencyConversionException("Invalid target currency: " + event.getTargetCurrency());
        }

        // Check if currencies are different
        if (event.getSourceCurrency().equals(event.getTargetCurrency())) {
            throw new CurrencyConversionException("Source and target currencies are the same");
        }

        // Check if currency pair is supported
        if (!validationService.isCurrencyPairSupported(event.getSourceCurrency(), event.getTargetCurrency())) {
            throw new CurrencyConversionException("Currency pair not supported");
        }

        // Check for restricted currencies
        if (validationService.isRestricted(event.getSourceCurrency()) || 
            validationService.isRestricted(event.getTargetCurrency())) {
            throw new CurrencyConversionException("Currency conversion restricted");
        }
    }

    private CurrencyConversion createConversionRecord(CurrencyConversionEvent event, String eventId, String correlationId) {
        return CurrencyConversion.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .referenceId(event.getReferenceId())
            .paymentId(event.getPaymentId())
            .userId(event.getUserId())
            .merchantId(event.getMerchantId())
            .sourceCurrency(event.getSourceCurrency())
            .targetCurrency(event.getTargetCurrency())
            .sourceAmount(event.getSourceAmount())
            .conversionType(ConversionType.valueOf(
                event.getConversionType() != null ? event.getConversionType().toUpperCase() : "SPOT"
            ))
            .purpose(event.getPurpose())
            .correlationId(correlationId)
            .status(ConversionStatus.INITIATED)
            .requestedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private List<ExchangeRate> fetchExchangeRates(CurrencyConversion conversion, CurrencyConversionEvent event) {
        log.info("Fetching exchange rates for {}/{}", 
                conversion.getSourceCurrency(), conversion.getTargetCurrency());

        List<CompletableFuture<ExchangeRate>> rateFutures = new ArrayList<>();

        // Fetch from primary provider
        rateFutures.add(CompletableFuture.supplyAsync(() -> 
            fetchRateFromProvider("PRIMARY", conversion, event)));

        // Fetch from secondary providers
        rateFutures.add(CompletableFuture.supplyAsync(() -> 
            fetchRateFromProvider("SECONDARY", conversion, event)));

        // Fetch from market data provider
        rateFutures.add(CompletableFuture.supplyAsync(() -> 
            fetchRateFromProvider("MARKET", conversion, event)));

        // Fetch from central bank if available
        if (shouldUseCentralBankRate(event)) {
            rateFutures.add(CompletableFuture.supplyAsync(() -> 
                fetchRateFromProvider("CENTRAL_BANK", conversion, event)));
        }

        // Wait for all rates
        CompletableFuture<Void> allRates = CompletableFuture.allOf(
            rateFutures.toArray(new CompletableFuture[0])
        );
        allRates.join();

        // Collect results
        List<ExchangeRate> rates = rateFutures.stream()
            .map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    log.error("Failed to fetch rate: {}", e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (rates.isEmpty()) {
            throw new CurrencyConversionException("No exchange rates available");
        }

        conversion.setRatesFetched(rates.size());
        conversion.setRatesFetchedAt(LocalDateTime.now());

        return rates;
    }

    private ExchangeRate fetchRateFromProvider(String provider, CurrencyConversion conversion, CurrencyConversionEvent event) {
        try {
            var rate = fxProviderService.getExchangeRate(
                provider,
                conversion.getSourceCurrency(),
                conversion.getTargetCurrency(),
                conversion.getSourceAmount()
            );

            return ExchangeRate.builder()
                .provider(provider)
                .bidRate(rate.getBid())
                .askRate(rate.getAsk())
                .midRate(rate.getMid())
                .timestamp(rate.getTimestamp())
                .build();

        } catch (Exception e) {
            log.error("Error fetching rate from {}: {}", provider, e.getMessage());
            return null;
        }
    }

    private ExchangeRate selectBestRate(List<ExchangeRate> rates, CurrencyConversionEvent event) {
        // Sort by mid-rate (best rate first)
        rates.sort((r1, r2) -> r2.getMidRate().compareTo(r1.getMidRate()));

        ExchangeRate bestRate = rates.get(0);

        // Log rate comparison
        log.info("Rate comparison - Best: {} from {}, Worst: {} from {}",
                bestRate.getMidRate(), bestRate.getProvider(),
                rates.get(rates.size() - 1).getMidRate(), 
                rates.get(rates.size() - 1).getProvider());

        // Check for rate anomalies
        checkRateAnomalies(rates);

        return bestRate;
    }

    private void applySelectedRate(CurrencyConversion conversion, ExchangeRate rate) {
        conversion.setRateProvider(rate.getProvider());
        conversion.setBaseRate(rate.getMidRate());
        conversion.setBidRate(rate.getBidRate());
        conversion.setAskRate(rate.getAskRate());
        conversion.setRateTimestamp(rate.getTimestamp());
    }

    private void applySpreadAndMarkup(CurrencyConversion conversion, CurrencyConversionEvent event) {
        log.info("Applying spread and markup for conversion: {}", conversion.getId());

        // Calculate base spread
        BigDecimal spread = calculateSpread(conversion, event);
        conversion.setSpread(spread);

        // Apply merchant markup
        BigDecimal markup = BigDecimal.ZERO;
        if (event.getMerchantId() != null) {
            markup = rateService.getMerchantMarkup(
                event.getMerchantId(),
                conversion.getSourceCurrency(),
                conversion.getTargetCurrency()
            );
        }
        conversion.setMarkup(markup);

        // Apply platform fee
        BigDecimal platformFee = rateService.getPlatformFxFee(
            conversion.getSourceAmount(),
            conversion.getConversionType()
        );
        conversion.setPlatformFee(platformFee);

        // Calculate final rate
        BigDecimal finalRate = conversion.getBaseRate();
        
        // Apply spread (as percentage)
        BigDecimal spreadMultiplier = BigDecimal.ONE.subtract(
            spread.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
        );
        finalRate = finalRate.multiply(spreadMultiplier);

        // Apply markup (as percentage)
        if (markup.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal markupMultiplier = BigDecimal.ONE.subtract(
                markup.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
            );
            finalRate = finalRate.multiply(markupMultiplier);
        }

        conversion.setFinalRate(finalRate.setScale(6, RoundingMode.HALF_UP));

        // Calculate effective spread
        BigDecimal effectiveSpread = conversion.getBaseRate()
            .subtract(conversion.getFinalRate())
            .divide(conversion.getBaseRate(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        conversion.setEffectiveSpread(effectiveSpread);
    }

    private void calculateConversionAmounts(CurrencyConversion conversion, CurrencyConversionEvent event) {
        log.info("Calculating conversion amounts for: {}", conversion.getId());

        // Calculate target amount
        BigDecimal targetAmount = conversion.getSourceAmount()
            .multiply(conversion.getFinalRate())
            .setScale(2, RoundingMode.HALF_UP);
        conversion.setTargetAmount(targetAmount);

        // Calculate fee in source currency
        BigDecimal feeInSource = conversion.getSourceAmount()
            .multiply(conversion.getPlatformFee())
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        conversion.setFeeAmount(feeInSource);

        // Calculate net amounts
        BigDecimal netSourceAmount = conversion.getSourceAmount().subtract(feeInSource);
        BigDecimal netTargetAmount = netSourceAmount.multiply(conversion.getFinalRate())
            .setScale(2, RoundingMode.HALF_UP);
        
        conversion.setNetSourceAmount(netSourceAmount);
        conversion.setNetTargetAmount(netTargetAmount);

        // Calculate savings compared to worst rate
        BigDecimal worstRate = conversion.getBaseRate()
            .multiply(new BigDecimal("0.95")); // Assume 5% worse rate
        BigDecimal worstAmount = conversion.getSourceAmount()
            .multiply(worstRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal savings = targetAmount.subtract(worstAmount);
        conversion.setSavings(savings.abs());

        log.info("Conversion amounts - Source: {} {}, Target: {} {}, Rate: {}",
                conversion.getSourceAmount(), conversion.getSourceCurrency(),
                conversion.getTargetAmount(), conversion.getTargetCurrency(),
                conversion.getFinalRate());
    }

    private void applyRateLock(CurrencyConversion conversion, CurrencyConversionEvent event) {
        log.info("Applying rate lock for conversion: {}", conversion.getId());

        conversion.setRateLocked(true);
        conversion.setRateLockedAt(LocalDateTime.now());
        conversion.setRateLockedUntil(LocalDateTime.now().plusMinutes(RATE_LOCK_MINUTES));
        conversion.setRateLockReference(UUID.randomUUID().toString());

        // Store locked rate for future reference
        rateService.storeLoc kedRate(
            conversion.getRateLockReference(),
            conversion.getFinalRate(),
            conversion.getRateLockedUntil()
        );
    }

    private void validateConversionLimits(CurrencyConversion conversion, CurrencyConversionEvent event) {
        // Check minimum amount
        if (conversion.getSourceAmount().compareTo(MIN_CONVERSION_AMOUNT) < 0) {
            throw new CurrencyConversionException(
                "Amount below minimum: " + MIN_CONVERSION_AMOUNT
            );
        }

        // Check maximum amount
        if (conversion.getSourceAmount().compareTo(MAX_CONVERSION_AMOUNT) > 0) {
            throw new CurrencyConversionException(
                "Amount exceeds maximum: " + MAX_CONVERSION_AMOUNT
            );
        }

        // Check daily limits for user
        if (event.getUserId() != null) {
            BigDecimal dailyTotal = conversionRepository.getDailyTotalForUser(
                event.getUserId(),
                LocalDateTime.now().toLocalDate()
            );
            
            BigDecimal dailyLimit = rateService.getUserDailyLimit(event.getUserId());
            if (dailyTotal.add(conversion.getSourceAmount()).compareTo(dailyLimit) > 0) {
                throw new CurrencyConversionException("Daily conversion limit exceeded");
            }
        }

        // Check merchant limits
        if (event.getMerchantId() != null) {
            BigDecimal merchantLimit = rateService.getMerchantConversionLimit(
                event.getMerchantId()
            );
            if (conversion.getSourceAmount().compareTo(merchantLimit) > 0) {
                throw new CurrencyConversionException("Merchant conversion limit exceeded");
            }
        }

        conversion.setLimitsValidated(true);
    }

    private void performRiskAssessment(CurrencyConversion conversion, CurrencyConversionEvent event) {
        try {
            log.info("Performing FX risk assessment for conversion: {}", conversion.getId());

            var riskAssessment = riskService.assessConversionRisk(
                conversion.getSourceCurrency(),
                conversion.getTargetCurrency(),
                conversion.getSourceAmount(),
                event.getUserId(),
                event.getMerchantId()
            );

            conversion.setRiskScore(riskAssessment.getScore());
            conversion.setRiskLevel(riskAssessment.getLevel());

            // Check if hedging is required
            if (riskAssessment.requiresHedging()) {
                var hedgeResult = riskService.hedgeExposure(
                    conversion.getId(),
                    conversion.getSourceCurrency(),
                    conversion.getTargetCurrency(),
                    conversion.getSourceAmount()
                );
                
                conversion.setHedged(true);
                conversion.setHedgeReference(hedgeResult.getReference());
                conversion.setHedgeCost(hedgeResult.getCost());
            }

            // Apply risk-based adjustments
            if (riskAssessment.getScore() > 70) {
                // High risk - apply additional spread
                BigDecimal riskSpread = new BigDecimal("0.5"); // 0.5% additional
                conversion.setRiskAdjustment(riskSpread);

                BigDecimal adjustedRate = conversion.getFinalRate()
                    .multiply(BigDecimal.ONE.subtract(riskSpread.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP)));
                conversion.setFinalRate(adjustedRate);
            }

        } catch (Exception e) {
            log.error("Risk assessment failed: {}", e.getMessage());
            conversion.setRiskAssessmentError(e.getMessage());
        }
    }

    private void executeConversion(CurrencyConversion conversion, CurrencyConversionEvent event) {
        try {
            log.info("Executing currency conversion: {}", conversion.getId());

            // Execute with FX provider
            var executionResult = fxProviderService.executeConversion(
                conversion.getRateProvider(),
                conversion.getSourceCurrency(),
                conversion.getTargetCurrency(),
                conversion.getSourceAmount(),
                conversion.getFinalRate()
            );

            conversion.setExecutionId(executionResult.getExecutionId());
            conversion.setExecutionReference(executionResult.getReference());
            conversion.setExecutedAt(LocalDateTime.now());
            conversion.setExecutedRate(executionResult.getExecutedRate());
            
            // Check for slippage
            BigDecimal slippage = conversion.getFinalRate()
                .subtract(executionResult.getExecutedRate())
                .abs();
            conversion.setSlippage(slippage);
            
            if (slippage.compareTo(new BigDecimal("0.001")) > 0) {
                log.warn("Slippage detected in conversion {}: {}", 
                        conversion.getId(), slippage);
            }

            // Recalculate target amount with executed rate
            BigDecimal actualTargetAmount = conversion.getSourceAmount()
                .multiply(executionResult.getExecutedRate())
                .setScale(2, RoundingMode.HALF_UP);
            conversion.setActualTargetAmount(actualTargetAmount);

            conversion.setStatus(ConversionStatus.EXECUTED);

        } catch (Exception e) {
            log.error("Conversion execution failed: {}", e.getMessage());
            conversion.setStatus(ConversionStatus.FAILED);
            conversion.setFailureReason(e.getMessage());
            throw new CurrencyConversionException("Execution failed: " + e.getMessage(), e);
        }
    }

    private void updateConversionStatus(CurrencyConversion conversion) {
        if (conversion.getStatus() == ConversionStatus.EXECUTED) {
            conversion.setStatus(ConversionStatus.COMPLETED);
            conversion.setCompletedAt(LocalDateTime.now());
        }

        conversion.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(conversion.getRequestedAt(), LocalDateTime.now())
        );
        conversion.setUpdatedAt(LocalDateTime.now());
    }

    private void updatePaymentWithConversion(String paymentId, CurrencyConversion conversion) {
        try {
            Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CurrencyConversionException("Payment not found: " + paymentId));

            payment.setConversionId(conversion.getId());
            payment.setConvertedAmount(conversion.getTargetAmount());
            payment.setConversionRate(conversion.getFinalRate());
            payment.setTargetCurrency(conversion.getTargetCurrency());
            payment.setUpdatedAt(LocalDateTime.now());

            paymentRepository.save(payment);

        } catch (Exception e) {
            log.error("Failed to update payment with conversion: {}", e.getMessage());
        }
    }

    private void processSettlement(CurrencyConversion conversion, CurrencyConversionEvent event) {
        try {
            if (conversion.getStatus() == ConversionStatus.COMPLETED) {
                // Initiate settlement in target currency
                fxProviderService.initiateSettlement(
                    conversion.getExecutionId(),
                    conversion.getTargetCurrency(),
                    conversion.getActualTargetAmount()
                );
                
                conversion.setSettlementInitiated(true);
                conversion.setSettlementInitiatedAt(LocalDateTime.now());
            }
        } catch (Exception e) {
            log.error("Settlement processing failed: {}", e.getMessage());
            conversion.setSettlementError(e.getMessage());
        }
    }

    private void sendConversionNotifications(CurrencyConversion conversion, CurrencyConversionEvent event) {
        try {
            // Send conversion confirmation
            notificationService.sendConversionConfirmation(conversion);

            // Send rate alert if significant
            if (conversion.getEffectiveSpread().compareTo(new BigDecimal("3")) > 0) {
                notificationService.sendHighSpreadAlert(conversion);
            }

            // Send execution notification
            if (conversion.getStatus() == ConversionStatus.COMPLETED) {
                notificationService.sendConversionCompleteNotification(conversion);
            }

            // Send failure notification
            if (conversion.getStatus() == ConversionStatus.FAILED) {
                notificationService.sendConversionFailureNotification(conversion);
            }

        } catch (Exception e) {
            log.error("Failed to send conversion notifications: {}", e.getMessage());
        }
    }

    private void updateConversionMetrics(CurrencyConversion conversion, CurrencyConversionEvent event) {
        try {
            // Conversion metrics
            metricsService.incrementCounter("fx.conversion.completed",
                Map.of(
                    "source", conversion.getSourceCurrency(),
                    "target", conversion.getTargetCurrency(),
                    "status", conversion.getStatus().toString()
                ));

            // Volume metrics
            metricsService.recordGauge("fx.conversion.volume", 
                conversion.getSourceAmount().doubleValue(),
                Map.of("currency", conversion.getSourceCurrency()));

            // Rate metrics
            metricsService.recordGauge("fx.rate.final", 
                conversion.getFinalRate().doubleValue(),
                Map.of(
                    "pair", conversion.getSourceCurrency() + "/" + conversion.getTargetCurrency()
                ));

            // Spread metrics
            metricsService.recordGauge("fx.spread.effective", 
                conversion.getEffectiveSpread().doubleValue(),
                Map.of("provider", conversion.getRateProvider()));

            // Processing time
            metricsService.recordTimer("fx.conversion.processing_time", 
                conversion.getProcessingTimeMs(),
                Map.of("type", conversion.getConversionType().toString()));

        } catch (Exception e) {
            log.error("Failed to update conversion metrics: {}", e.getMessage());
        }
    }

    private void createConversionAuditLog(CurrencyConversion conversion, CurrencyConversionEvent event, String correlationId) {
        auditLogger.logFinancialEvent(
            "CURRENCY_CONVERSION_COMPLETED",
            event.getUserId() != null ? event.getUserId() : "system",
            conversion.getId(),
            "FX_CONVERSION",
            conversion.getSourceAmount().doubleValue(),
            "fx_processor",
            conversion.getStatus() == ConversionStatus.COMPLETED,
            Map.of(
                "conversionId", conversion.getId(),
                "sourceCurrency", conversion.getSourceCurrency(),
                "targetCurrency", conversion.getTargetCurrency(),
                "sourceAmount", conversion.getSourceAmount().toString(),
                "targetAmount", conversion.getTargetAmount().toString(),
                "baseRate", conversion.getBaseRate().toString(),
                "finalRate", conversion.getFinalRate().toString(),
                "effectiveSpread", conversion.getEffectiveSpread().toString(),
                "provider", conversion.getRateProvider(),
                "rateLocked", String.valueOf(conversion.isRateLocked()),
                "processingTimeMs", String.valueOf(conversion.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private CurrencyConversion processInstantConversion(CurrencyConversionEvent event, String correlationId) {
        CurrencyConversion conversion = createConversionRecord(event, UUID.randomUUID().toString(), correlationId);
        
        // Get cached rate for speed
        BigDecimal cachedRate = rateService.getCachedRate(
            event.getSourceCurrency(),
            event.getTargetCurrency()
        );
        
        conversion.setBaseRate(cachedRate);
        conversion.setFinalRate(cachedRate.multiply(new BigDecimal("0.99"))); // 1% spread
        
        // Quick calculation
        BigDecimal targetAmount = event.getSourceAmount().multiply(conversion.getFinalRate());
        conversion.setTargetAmount(targetAmount.setScale(2, RoundingMode.HALF_UP));
        
        conversion.setStatus(ConversionStatus.COMPLETED);
        conversion.setCompletedAt(LocalDateTime.now());
        
        return conversionRepository.save(conversion);
    }

    private boolean shouldUseCentralBankRate(CurrencyConversionEvent event) {
        // Use central bank rates for certain currencies or large amounts
        return event.getSourceAmount().compareTo(new BigDecimal("100000")) > 0 ||
               Set.of("EUR", "GBP", "JPY").contains(event.getSourceCurrency());
    }

    private BigDecimal calculateSpread(CurrencyConversion conversion, CurrencyConversionEvent event) {
        BigDecimal baseSpread = new BigDecimal("1.0"); // 1% base spread
        
        // Adjust based on amount
        if (conversion.getSourceAmount().compareTo(new BigDecimal("10000")) > 0) {
            baseSpread = new BigDecimal("0.5"); // Better rate for larger amounts
        }
        
        // Adjust based on currency pair
        if (isMajorCurrencyPair(conversion.getSourceCurrency(), conversion.getTargetCurrency())) {
            baseSpread = baseSpread.multiply(new BigDecimal("0.8")); // 20% discount for major pairs
        }
        
        return baseSpread.min(MAX_SPREAD_PERCENTAGE);
    }

    private boolean isMajorCurrencyPair(String source, String target) {
        Set<String> majorCurrencies = Set.of("USD", "EUR", "GBP", "JPY", "CHF");
        return majorCurrencies.contains(source) && majorCurrencies.contains(target);
    }

    private void checkRateAnomalies(List<ExchangeRate> rates) {
        if (rates.size() < 2) return;
        
        BigDecimal maxRate = rates.stream()
            .map(ExchangeRate::getMidRate)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal minRate = rates.stream()
            .map(ExchangeRate::getMidRate)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal variance = maxRate.subtract(minRate)
            .divide(maxRate, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        if (variance.compareTo(new BigDecimal("2")) > 0) {
            log.warn("High rate variance detected: {}% between providers", variance);
        }
    }

    /**
     * Internal classes
     */
    @lombok.Data
    @lombok.Builder
    private static class ExchangeRate {
        private String provider;
        private BigDecimal bidRate;
        private BigDecimal askRate;
        private BigDecimal midRate;
        private LocalDateTime timestamp;
    }
}