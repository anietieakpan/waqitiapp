package com.waqiti.currency.kafka;

import com.waqiti.common.events.CurrencyConversionEvent;
import com.waqiti.currency.domain.CurrencyConversion;
import com.waqiti.currency.repository.CurrencyConversionRepository;
import com.waqiti.currency.service.ConversionService;
import com.waqiti.currency.service.FXRateService;
import com.waqiti.currency.service.TransactionService;
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
public class CurrencyConversionEventsConsumer {
    
    private final CurrencyConversionRepository conversionRepository;
    private final ConversionService conversionService;
    private final FXRateService fxRateService;
    private final TransactionService transactionService;
    private final CurrencyMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MIN_CONVERSION_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_SINGLE_CONVERSION = new BigDecimal("1000000");
    private static final BigDecimal LARGE_CONVERSION_THRESHOLD = new BigDecimal("50000");
    
    @KafkaListener(
        topics = {"currency-conversion-events", "fx-conversion-requests", "multi-currency-exchange-events"},
        groupId = "currency-conversion-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 60)
    public void handleCurrencyConversionEvent(
            @Payload CurrencyConversionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("conversion-%s-p%d-o%d", 
            event.getConversionId(), partition, offset);
        
        log.info("Processing currency conversion: conversionId={}, type={}, {}{} to {}", 
            event.getConversionId(), event.getEventType(), 
            event.getSourceAmount(), event.getSourceCurrency(), event.getTargetCurrency());
        
        try {
            switch (event.getEventType()) {
                case CONVERSION_REQUESTED:
                    processConversionRequested(event, correlationId);
                    break;
                case CONVERSION_QUOTE_GENERATED:
                    processConversionQuoteGenerated(event, correlationId);
                    break;
                case CONVERSION_APPROVED:
                    processConversionApproved(event, correlationId);
                    break;
                case CONVERSION_EXECUTED:
                    processConversionExecuted(event, correlationId);
                    break;
                case CONVERSION_COMPLETED:
                    processConversionCompleted(event, correlationId);
                    break;
                case CONVERSION_FAILED:
                    processConversionFailed(event, correlationId);
                    break;
                case CONVERSION_REVERSED:
                    processConversionReversed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown currency conversion event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCurrencyEvent(
                "CURRENCY_CONVERSION_EVENT_PROCESSED",
                event.getConversionId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "corridor", event.getSourceCurrency() + "-" + event.getTargetCurrency(),
                    "sourceAmount", event.getSourceAmount().toString(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process currency conversion event: {}", e.getMessage(), e);
            kafkaTemplate.send("currency-conversion-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processConversionRequested(CurrencyConversionEvent event, String correlationId) {
        log.info("Conversion requested: conversionId={}, user={}, {}{} to {}", 
            event.getConversionId(), event.getUserId(), 
            event.getSourceAmount(), event.getSourceCurrency(), event.getTargetCurrency());
        
        if (event.getSourceAmount().compareTo(MIN_CONVERSION_AMOUNT) < 0) {
            log.error("Conversion amount too small: conversionId={}, amount={}", 
                event.getConversionId(), event.getSourceAmount());
            conversionService.rejectConversion(event.getConversionId(), "AMOUNT_TOO_SMALL");
            return;
        }
        
        if (event.getSourceAmount().compareTo(MAX_SINGLE_CONVERSION) > 0) {
            log.error("Conversion amount exceeds limit: conversionId={}, amount={}", 
                event.getConversionId(), event.getSourceAmount());
            conversionService.rejectConversion(event.getConversionId(), "AMOUNT_EXCEEDS_LIMIT");
            return;
        }
        
        boolean userEligible = conversionService.validateUserEligibility(
            event.getUserId(), event.getSourceCurrency(), event.getTargetCurrency());
        
        if (!userEligible) {
            log.warn("User not eligible for conversion: userId={}, conversionId={}", 
                event.getUserId(), event.getConversionId());
            conversionService.rejectConversion(event.getConversionId(), "USER_NOT_ELIGIBLE");
            return;
        }
        
        BigDecimal sourceBalance = transactionService.getUserBalance(
            event.getUserId(), event.getSourceCurrency());
        
        if (sourceBalance.compareTo(event.getSourceAmount()) < 0) {
            log.error("Insufficient balance: userId={}, conversionId={}, required={}, available={}", 
                event.getUserId(), event.getConversionId(), event.getSourceAmount(), sourceBalance);
            conversionService.rejectConversion(event.getConversionId(), "INSUFFICIENT_BALANCE");
            return;
        }
        
        CurrencyConversion conversion = CurrencyConversion.builder()
            .id(event.getConversionId())
            .userId(event.getUserId())
            .sourceCurrency(event.getSourceCurrency())
            .targetCurrency(event.getTargetCurrency())
            .sourceAmount(event.getSourceAmount())
            .requestedAt(LocalDateTime.now())
            .conversionType(event.getConversionType())
            .conversionPurpose(event.getConversionPurpose())
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        conversionRepository.save(conversion);
        
        conversionService.generateConversionQuote(conversion.getId());
        
        metricsService.recordConversionRequested(event.getSourceCurrency(), event.getTargetCurrency());
    }
    
    private void processConversionQuoteGenerated(CurrencyConversionEvent event, String correlationId) {
        log.info("Conversion quote generated: conversionId={}, rate={}, targetAmount={}, fee={}", 
            event.getConversionId(), event.getExchangeRate(), event.getTargetAmount(), event.getConversionFee());
        
        CurrencyConversion conversion = conversionRepository.findById(event.getConversionId())
            .orElseThrow();
        
        conversion.setExchangeRate(event.getExchangeRate());
        conversion.setTargetAmount(event.getTargetAmount());
        conversion.setConversionFee(event.getConversionFee());
        conversion.setFxMarkup(event.getFxMarkup());
        conversion.setQuoteGeneratedAt(LocalDateTime.now());
        conversion.setQuoteExpiresAt(LocalDateTime.now().plusMinutes(5));
        conversion.setStatus("QUOTE_GENERATED");
        conversionRepository.save(conversion);
        
        BigDecimal midMarketRate = fxRateService.getMidMarketRate(
            event.getSourceCurrency(), event.getTargetCurrency());
        conversion.setMidMarketRate(midMarketRate);
        
        BigDecimal markupPct = event.getExchangeRate().subtract(midMarketRate)
            .divide(midMarketRate, 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("10000"));
        conversion.setMarkupBasisPoints(markupPct);
        
        conversionRepository.save(conversion);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Conversion Quote",
            String.format("Your conversion quote: %s %s = %s %s (Rate: %s, Fee: %s %s). " +
                "Quote expires in 5 minutes.",
                event.getSourceAmount(), event.getSourceCurrency(),
                event.getTargetAmount(), event.getTargetCurrency(),
                event.getExchangeRate(), event.getConversionFee(), event.getSourceCurrency()),
            correlationId
        );
        
        metricsService.recordQuoteGenerated();
    }
    
    private void processConversionApproved(CurrencyConversionEvent event, String correlationId) {
        log.info("Conversion approved: conversionId={}, user={}", 
            event.getConversionId(), event.getUserId());
        
        CurrencyConversion conversion = conversionRepository.findById(event.getConversionId())
            .orElseThrow();
        
        if (LocalDateTime.now().isAfter(conversion.getQuoteExpiresAt())) {
            log.error("Quote expired: conversionId={}, expiresAt={}", 
                event.getConversionId(), conversion.getQuoteExpiresAt());
            conversion.setStatus("QUOTE_EXPIRED");
            conversionRepository.save(conversion);
            return;
        }
        
        conversion.setApprovedAt(LocalDateTime.now());
        conversion.setStatus("APPROVED");
        conversionRepository.save(conversion);
        
        if (conversion.getSourceAmount().compareTo(LARGE_CONVERSION_THRESHOLD) > 0) {
            log.info("Large conversion requires additional verification: conversionId={}, amount={}", 
                event.getConversionId(), conversion.getSourceAmount());
            conversionService.requestComplianceReview(conversion.getId());
        } else {
            conversionService.executeConversion(conversion.getId());
        }
        
        metricsService.recordConversionApproved();
    }
    
    private void processConversionExecuted(CurrencyConversionEvent event, String correlationId) {
        log.info("Conversion executed: conversionId={}, debitedFrom={}{}, creditedTo={}{}", 
            event.getConversionId(), event.getSourceAmount(), event.getSourceCurrency(),
            event.getTargetAmount(), event.getTargetCurrency());
        
        CurrencyConversion conversion = conversionRepository.findById(event.getConversionId())
            .orElseThrow();
        
        conversion.setExecutedAt(LocalDateTime.now());
        conversion.setActualExchangeRate(event.getActualExchangeRate());
        conversion.setActualTargetAmount(event.getActualTargetAmount());
        conversion.setStatus("EXECUTED");
        conversionRepository.save(conversion);
        
        transactionService.debitAccount(event.getUserId(), event.getSourceCurrency(), 
            event.getSourceAmount().add(event.getConversionFee()), event.getConversionId());
        
        transactionService.creditAccount(event.getUserId(), event.getTargetCurrency(), 
            event.getActualTargetAmount(), event.getConversionId());
        
        BigDecimal slippage = event.getActualExchangeRate().subtract(conversion.getExchangeRate())
            .divide(conversion.getExchangeRate(), 6, RoundingMode.HALF_UP)
            .abs();
        
        if (slippage.compareTo(new BigDecimal("0.001")) > 0) {
            log.warn("Rate slippage detected: conversionId={}, slippage={}%", 
                event.getConversionId(), slippage.multiply(new BigDecimal("100")));
        }
        
        metricsService.recordConversionExecuted(
            event.getSourceCurrency(), event.getTargetCurrency(), event.getSourceAmount());
    }
    
    private void processConversionCompleted(CurrencyConversionEvent event, String correlationId) {
        log.info("Conversion completed: conversionId={}, finalTargetAmount={}", 
            event.getConversionId(), event.getFinalTargetAmount());
        
        CurrencyConversion conversion = conversionRepository.findById(event.getConversionId())
            .orElseThrow();
        
        conversion.setCompletedAt(LocalDateTime.now());
        conversion.setFinalTargetAmount(event.getFinalTargetAmount());
        conversion.setStatus("COMPLETED");
        conversionRepository.save(conversion);
        
        conversionService.generateConversionReceipt(conversion.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Conversion Completed",
            String.format("Your conversion is complete! You converted %s %s to %s %s at rate %s. " +
                "Fee: %s %s. Receipt available in your transaction history.",
                conversion.getSourceAmount(), conversion.getSourceCurrency(),
                event.getFinalTargetAmount(), conversion.getTargetCurrency(),
                conversion.getActualExchangeRate(),
                conversion.getConversionFee(), conversion.getSourceCurrency()),
            correlationId
        );
        
        metricsService.recordConversionCompleted(
            event.getSourceCurrency(), event.getTargetCurrency(), 
            conversion.getConversionFee());
    }
    
    private void processConversionFailed(CurrencyConversionEvent event, String correlationId) {
        log.error("Conversion failed: conversionId={}, failureReason={}", 
            event.getConversionId(), event.getFailureReason());
        
        CurrencyConversion conversion = conversionRepository.findById(event.getConversionId())
            .orElseThrow();
        
        conversion.setFailedAt(LocalDateTime.now());
        conversion.setFailureReason(event.getFailureReason());
        conversion.setStatus("FAILED");
        conversionRepository.save(conversion);
        
        if (conversion.getExecutedAt() != null) {
            conversionService.reverseConversion(conversion.getId());
        }
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Conversion Failed",
            String.format("Your conversion of %s %s to %s could not be completed. Reason: %s. " +
                "Any debited funds will be refunded.",
                conversion.getSourceAmount(), conversion.getSourceCurrency(),
                conversion.getTargetCurrency(), event.getFailureReason()),
            correlationId
        );
        
        metricsService.recordConversionFailed(event.getFailureReason());
    }
    
    private void processConversionReversed(CurrencyConversionEvent event, String correlationId) {
        log.warn("Conversion reversed: conversionId={}, reversalReason={}", 
            event.getConversionId(), event.getReversalReason());
        
        CurrencyConversion conversion = conversionRepository.findById(event.getConversionId())
            .orElseThrow();
        
        conversion.setReversedAt(LocalDateTime.now());
        conversion.setReversalReason(event.getReversalReason());
        conversion.setStatus("REVERSED");
        conversionRepository.save(conversion);
        
        transactionService.creditAccount(event.getUserId(), conversion.getSourceCurrency(), 
            conversion.getSourceAmount().add(conversion.getConversionFee()), 
            "REVERSAL-" + event.getConversionId());
        
        transactionService.debitAccount(event.getUserId(), conversion.getTargetCurrency(), 
            conversion.getActualTargetAmount(), "REVERSAL-" + event.getConversionId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Conversion Reversed",
            String.format("Your conversion has been reversed. %s %s returned to your account. Reason: %s",
                conversion.getSourceAmount(), conversion.getSourceCurrency(), event.getReversalReason()),
            correlationId
        );
        
        metricsService.recordConversionReversed(event.getReversalReason());
    }
}