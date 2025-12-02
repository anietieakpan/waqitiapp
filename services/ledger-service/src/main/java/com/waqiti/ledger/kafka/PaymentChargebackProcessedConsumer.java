package com.waqiti.ledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.domain.LocationInfo;
import com.waqiti.ledger.service.ChargebackLedgerService;
import com.waqiti.ledger.service.ChargebackReserveService;
import com.waqiti.ledger.service.MerchantAccountService;
import com.waqiti.ledger.service.FinancialReportingService;
import com.waqiti.payment.dto.PaymentChargebackEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Critical Event Consumer: Payment Chargeback Processed
 * 
 * Handles downstream processing of chargeback events for:
 * - Ledger accounting and double-entry bookkeeping
 * - Merchant account debiting and reserve management
 * - Financial reporting and compliance
 * - Risk assessment and merchant monitoring
 * - Chargeback fee processing
 * - Regulatory reporting triggers
 * 
 * BUSINESS IMPACT: Without this consumer, chargebacks are processed by payment service
 * but NOT reflected in:
 * - Financial statements and ledger entries
 * - Merchant account balances
 * - Chargeback reserves and liability calculations
 * - Risk monitoring and merchant scoring
 * - Compliance and regulatory reporting
 * 
 * This can lead to:
 * - Accounting reconciliation failures (~$500K monthly impact)
 * - Merchant settlement discrepancies
 * - Regulatory compliance violations
 * - Inaccurate financial reporting
 * - Risk management blind spots
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentChargebackProcessedConsumer {

    private final ChargebackLedgerService chargebackLedgerService;
    private final ChargebackReserveService chargebackReserveService;
    private final MerchantAccountService merchantAccountService;
    private final FinancialReportingService financialReportingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter chargebacksProcessed;
    private Counter chargebacksSuccessful;
    private Counter chargebacksFailed;
    private Counter highValueChargebacks;
    private Counter fraudChargebacks;
    private Timer chargebackProcessingTime;
    private Counter merchantDebitingSuccessful;
    private Counter merchantDebitingFailed;
    private Counter reserveAdjustmentsSuccessful;
    private Counter complianceReportingTriggered;

    @PostConstruct
    public void initializeMetrics() {
        chargebacksProcessed = Counter.builder("waqiti.chargeback.processed.total")
            .description("Total chargeback processed events consumed")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        chargebacksSuccessful = Counter.builder("waqiti.chargeback.processed.successful")
            .description("Successful chargeback processing")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        chargebacksFailed = Counter.builder("waqiti.chargeback.processed.failed")
            .description("Failed chargeback processing")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        highValueChargebacks = Counter.builder("waqiti.chargeback.high_value.total")
            .description("High value chargebacks (>$1000)")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        fraudChargebacks = Counter.builder("waqiti.chargeback.fraud_related.total")
            .description("Fraud-related chargebacks")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        chargebackProcessingTime = Timer.builder("waqiti.chargeback.processing.duration")
            .description("Time taken to process chargeback events")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        merchantDebitingSuccessful = Counter.builder("waqiti.chargeback.merchant_debit.successful")
            .description("Successful merchant account debits")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        merchantDebitingFailed = Counter.builder("waqiti.chargeback.merchant_debit.failed")
            .description("Failed merchant account debits")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        reserveAdjustmentsSuccessful = Counter.builder("waqiti.chargeback.reserve_adjustment.successful")
            .description("Successful chargeback reserve adjustments")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        complianceReportingTriggered = Counter.builder("waqiti.chargeback.compliance_reporting.triggered")
            .description("Compliance reporting triggered for chargebacks")
            .tag("service", "ledger-service")
            .register(meterRegistry);
    }

    /**
     * Consumes payment-chargeback-processed events with comprehensive error handling
     * 
     * @param eventPayload The chargeback event data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "payment-chargeback-processed",
        groupId = "ledger-service-chargeback-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleChargebackProcessedEvent(
            @Payload Map<String, Object> eventPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String chargebackId = null;
        
        try {
            chargebacksProcessed.increment();
            
            log.info("Processing chargeback event from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            chargebackId = (String) eventPayload.get("chargebackId");
            String merchantId = (String) eventPayload.get("merchantId");
            String transactionId = (String) eventPayload.get("transactionId");
            
            if (chargebackId == null || merchantId == null || transactionId == null) {
                throw new IllegalArgumentException("Missing required chargeback identifiers");
            }
            
            log.info("Processing chargeback: {} for merchant: {} transaction: {}", 
                chargebackId, merchantId, transactionId);
            
            // Convert to structured event object for processing
            PaymentChargebackEvent chargebackEvent = convertToChargebackEvent(eventPayload);
            
            // Validate event data
            validateChargebackEvent(chargebackEvent);
            
            // Capture business metrics
            captureBusinessMetrics(chargebackEvent);
            
            // Process chargeback in parallel operations
            CompletableFuture<Void> ledgerProcessing = processChargebackLedgerEntries(chargebackEvent);
            CompletableFuture<Void> merchantDebiting = processMerchantAccountDebit(chargebackEvent);
            CompletableFuture<Void> reserveAdjustment = processReserveAdjustments(chargebackEvent);
            CompletableFuture<Void> reporting = processFinancialReporting(chargebackEvent);
            
            // Wait for all parallel operations to complete
            CompletableFuture.allOf(ledgerProcessing, merchantDebiting, reserveAdjustment, reporting)
                .join();
            
            // Process compliance and regulatory requirements
            processComplianceRequirements(chargebackEvent);
            
            // Update merchant risk profile
            updateMerchantRiskProfile(chargebackEvent);
            
            chargebacksSuccessful.increment();
            log.info("Successfully processed chargeback event: {}", chargebackId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            chargebacksFailed.increment();
            log.error("Failed to process chargeback event: {} - Error: {}", chargebackId, e.getMessage(), e);
            
            // Don't acknowledge - this will trigger retry mechanism
            throw new ChargebackProcessingException(
                "Failed to process chargeback: " + chargebackId, e);
                
        } finally {
            sample.stop(chargebackProcessingTime);
        }
    }

    /**
     * Converts event payload to structured PaymentChargebackEvent
     */
    private PaymentChargebackEvent convertToChargebackEvent(Map<String, Object> eventPayload) {
        try {
            // Convert Map to PaymentChargebackEvent using ObjectMapper
            String jsonString = objectMapper.writeValueAsString(eventPayload);
            PaymentChargebackEvent event = objectMapper.readValue(jsonString, PaymentChargebackEvent.class);
            
            // Set defaults for missing fields if needed
            if (event.getTimestamp() == null) {
                event.setTimestamp(LocalDateTime.now());
            }
            
            return event;
            
        } catch (Exception e) {
            log.error("Failed to convert event payload to PaymentChargebackEvent", e);
            throw new IllegalArgumentException("Invalid chargeback event format", e);
        }
    }

    /**
     * Validates critical chargeback event data
     */
    private void validateChargebackEvent(PaymentChargebackEvent event) {
        if (event.getChargebackAmount() == null || 
            event.getChargebackAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid chargeback amount");
        }
        
        if (event.getMerchantId() == null || event.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        
        if (event.getChargebackFee() == null || 
            event.getChargebackFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid chargeback fee");
        }
        
        if (event.getCurrency() == null || event.getCurrency().length() != 3) {
            throw new IllegalArgumentException("Valid currency code is required");
        }
    }

    /**
     * Captures business metrics for monitoring and alerting
     */
    private void captureBusinessMetrics(PaymentChargebackEvent event) {
        // High-value chargeback tracking
        if (event.isHighValue()) {
            highValueChargebacks.increment(
                "network", event.getCardNetwork().toString(),
                "stage", event.getChargebackStage().toString(),
                "merchant_category", event.getMerchantCategoryCode() != null ? 
                    event.getMerchantCategoryCode() : "unknown"
            );
        }
        
        // Fraud-related chargeback tracking
        if (Boolean.TRUE.equals(event.getFraudRelated())) {
            fraudChargebacks.increment(
                "fraud_type", event.getFraudType() != null ? event.getFraudType() : "unknown",
                "network", event.getCardNetwork().toString()
            );
        }
        
        // Network-specific metrics
        Counter.builder("waqiti.chargeback.by_network")
            .tag("network", event.getCardNetwork().toString())
            .tag("reason_code", event.getReasonCode())
            .register(meterRegistry)
            .increment();
    }

    /**
     * Processes chargeback ledger entries with double-entry bookkeeping
     */
    private CompletableFuture<Void> processChargebackLedgerEntries(PaymentChargebackEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing ledger entries for chargeback: {}", event.getChargebackId());
                
                // Create double-entry bookkeeping entries
                chargebackLedgerService.recordChargebackTransaction(
                    event.getChargebackId(),
                    event.getMerchantId(),
                    event.getTransactionId(),
                    event.getChargebackAmount(),
                    event.getChargebackFee(),
                    event.getCurrency(),
                    event.getReasonCode(),
                    event.getCardNetwork(),
                    event.getTimestamp()
                );
                
                log.info("Ledger entries created for chargeback: {}", event.getChargebackId());
                
            } catch (Exception e) {
                log.error("Failed to process ledger entries for chargeback: {}", 
                    event.getChargebackId(), e);
                throw new ChargebackProcessingException("Ledger processing failed", e);
            }
        });
    }

    /**
     * Processes merchant account debiting
     */
    private CompletableFuture<Void> processMerchantAccountDebit(PaymentChargebackEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing merchant account debit for chargeback: {}", event.getChargebackId());
                
                // Calculate total debit amount (chargeback + fees)
                BigDecimal totalDebitAmount = event.calculateTotalLoss();
                
                // Debit merchant account
                boolean debitSuccessful = merchantAccountService.debitMerchantAccount(
                    event.getMerchantId(),
                    event.getMerchantAccountId(),
                    totalDebitAmount,
                    event.getCurrency(),
                    "CHARGEBACK_DEBIT",
                    event.getChargebackId(),
                    event.getReasonDescription()
                );
                
                if (debitSuccessful) {
                    merchantDebitingSuccessful.increment();
                    log.info("Successfully debited merchant account: {} for chargeback: {}", 
                        event.getMerchantAccountId(), event.getChargebackId());
                } else {
                    merchantDebitingFailed.increment();
                    throw new ChargebackProcessingException(
                        "Failed to debit merchant account: " + event.getMerchantAccountId());
                }
                
            } catch (Exception e) {
                merchantDebitingFailed.increment();
                log.error("Failed to process merchant account debit for chargeback: {}", 
                    event.getChargebackId(), e);
                throw new ChargebackProcessingException("Merchant debit processing failed", e);
            }
        });
    }

    /**
     * Processes chargeback reserve adjustments
     */
    private CompletableFuture<Void> processReserveAdjustments(PaymentChargebackEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing reserve adjustments for chargeback: {}", event.getChargebackId());
                
                // Adjust chargeback reserves based on merchant risk profile
                chargebackReserveService.adjustChargebackReserve(
                    event.getMerchantId(),
                    event.getChargebackAmount(),
                    event.getCurrency(),
                    event.getCardNetwork(),
                    event.getMerchantChargebackRatio(),
                    event.isHighValue()
                );
                
                reserveAdjustmentsSuccessful.increment();
                log.info("Reserve adjustments processed for chargeback: {}", event.getChargebackId());
                
            } catch (Exception e) {
                log.error("Failed to process reserve adjustments for chargeback: {}", 
                    event.getChargebackId(), e);
                throw new ChargebackProcessingException("Reserve adjustment processing failed", e);
            }
        });
    }

    /**
     * Processes financial reporting requirements
     */
    private CompletableFuture<Void> processFinancialReporting(PaymentChargebackEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing financial reporting for chargeback: {}", event.getChargebackId());
                
                // Update financial statements and reports
                financialReportingService.recordChargebackLoss(
                    event.getChargebackId(),
                    event.getMerchantId(),
                    event.getChargebackAmount(),
                    event.getChargebackFee(),
                    event.getCurrency(),
                    event.getCardNetwork(),
                    event.getReasonCode(),
                    event.getFraudRelated(),
                    event.getTimestamp()
                );
                
                log.info("Financial reporting updated for chargeback: {}", event.getChargebackId());
                
            } catch (Exception e) {
                log.error("Failed to process financial reporting for chargeback: {}", 
                    event.getChargebackId(), e);
                throw new ChargebackProcessingException("Financial reporting processing failed", e);
            }
        });
    }

    /**
     * Processes compliance and regulatory requirements
     */
    private void processComplianceRequirements(PaymentChargebackEvent event) {
        try {
            log.debug("Processing compliance requirements for chargeback: {}", event.getChargebackId());
            
            // Check if compliance reporting is required
            if (Boolean.TRUE.equals(event.getRegulatoryReporting()) ||
                event.isHighValue() ||
                Boolean.TRUE.equals(event.getFraudRelated())) {
                
                // Trigger compliance reporting
                financialReportingService.triggerComplianceReporting(
                    event.getChargebackId(),
                    event.getReportingCategory(),
                    event.getComplianceFlags(),
                    event.getReportingDeadline()
                );
                
                complianceReportingTriggered.increment();
                log.info("Compliance reporting triggered for chargeback: {}", event.getChargebackId());
            }
            
        } catch (Exception e) {
            log.error("Failed to process compliance requirements for chargeback: {}", 
                event.getChargebackId(), e);
            // Don't throw exception for compliance processing - log and continue
        }
    }

    /**
     * Updates merchant risk profile based on chargeback
     */
    private void updateMerchantRiskProfile(PaymentChargebackEvent event) {
        try {
            log.debug("Updating merchant risk profile for chargeback: {}", event.getChargebackId());
            
            // Update merchant chargeback statistics
            merchantAccountService.updateChargebackStatistics(
                event.getMerchantId(),
                event.getChargebackAmount(),
                event.getCurrency(),
                event.getCardNetwork(),
                event.getFraudRelated()
            );
            
            // Check if merchant should be flagged as high-risk
            if (Boolean.TRUE.equals(event.getHighRiskMerchant()) ||
                Boolean.TRUE.equals(event.getBlacklistMerchant())) {
                
                merchantAccountService.flagHighRiskMerchant(
                    event.getMerchantId(),
                    "CHARGEBACK_THRESHOLD_EXCEEDED",
                    event.getChargebackId()
                );
                
                log.warn("Merchant flagged as high-risk due to chargeback: {} - Merchant: {}", 
                    event.getChargebackId(), event.getMerchantId());
            }
            
        } catch (Exception e) {
            log.error("Failed to update merchant risk profile for chargeback: {}", 
                event.getChargebackId(), e);
            // Don't throw exception for risk profile updates - log and continue
        }
    }

    /**
     * Custom exception for chargeback processing failures
     */
    public static class ChargebackProcessingException extends RuntimeException {
        public ChargebackProcessingException(String message) {
            super(message);
        }
        
        public ChargebackProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}