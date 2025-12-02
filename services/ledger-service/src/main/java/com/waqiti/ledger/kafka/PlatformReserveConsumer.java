package com.waqiti.ledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.ledger.service.LedgerServiceImpl;
import com.waqiti.ledger.service.PlatformReserveService;
import com.waqiti.ledger.service.FinancialReportingService;
import com.waqiti.ledger.service.RiskManagementService;

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
 * Critical Event Consumer: Platform Reserve Requests
 * 
 * Handles platform reserve fund management for:
 * - Chargeback liability coverage and risk mitigation
 * - Fraud loss coverage and operational risk reserves
 * - Regulatory capital requirements and compliance reserves
 * - Merchant settlement guarantee and counterparty risk
 * - Platform operational liquidity and cash flow management
 * - Emergency fund allocation for crisis situations
 * 
 * BUSINESS IMPACT: Without this consumer, reserve requests are generated
 * but NOT processed, leading to:
 * - Inadequate chargeback coverage (~$2M monthly exposure)
 * - Regulatory capital compliance violations
 * - Platform liquidity shortfalls during high-risk periods
 * - Merchant settlement risk exposure
 * - Inability to cover operational losses
 * - Financial reporting inaccuracies for reserve calculations
 * 
 * This consumer enables:
 * - Automated platform reserve management
 * - Real-time risk-based reserve calculations
 * - Regulatory compliance for capital adequacy
 * - Dynamic reserve adjustments based on risk profile
 * - Platform financial stability and liquidity management
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformReserveConsumer {

    private final LedgerServiceImpl ledgerService;
    private final PlatformReserveService platformReserveService;
    private final FinancialReportingService financialReportingService;
    private final RiskManagementService riskManagementService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter reserveRequestsProcessed;
    private Counter reserveRequestsSuccessful;
    private Counter reserveRequestsFailed;
    private Counter chargebackReservesCreated;
    private Counter fraudReservesCreated;
    private Counter operationalReservesCreated;
    private Counter emergencyReservesCreated;
    private Counter highValueReserves;
    private Timer reserveProcessingTime;
    private Counter regulatoryReservesCreated;
    private Counter reserveAdjustmentsSuccessful;

    @PostConstruct
    public void initializeMetrics() {
        reserveRequestsProcessed = Counter.builder("waqiti.platform.reserve_requests.processed.total")
            .description("Total platform reserve requests processed")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        reserveRequestsSuccessful = Counter.builder("waqiti.platform.reserve_requests.successful")
            .description("Successful platform reserve request processing")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        reserveRequestsFailed = Counter.builder("waqiti.platform.reserve_requests.failed")
            .description("Failed platform reserve request processing")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        chargebackReservesCreated = Counter.builder("waqiti.platform.reserve.chargeback.created")
            .description("Chargeback reserves created")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        fraudReservesCreated = Counter.builder("waqiti.platform.reserve.fraud.created")
            .description("Fraud loss reserves created")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        operationalReservesCreated = Counter.builder("waqiti.platform.reserve.operational.created")
            .description("Operational risk reserves created")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        emergencyReservesCreated = Counter.builder("waqiti.platform.reserve.emergency.created")
            .description("Emergency reserves created")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        highValueReserves = Counter.builder("waqiti.platform.reserve.high_value.created")
            .description("High-value reserves (>$10K) created")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        reserveProcessingTime = Timer.builder("waqiti.platform.reserve.processing.duration")
            .description("Time taken to process reserve requests")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        regulatoryReservesCreated = Counter.builder("waqiti.platform.reserve.regulatory.created")
            .description("Regulatory compliance reserves created")
            .tag("service", "ledger-service")
            .register(meterRegistry);

        reserveAdjustmentsSuccessful = Counter.builder("waqiti.platform.reserve.adjustments.successful")
            .description("Successful reserve adjustments")
            .tag("service", "ledger-service")
            .register(meterRegistry);
    }

    /**
     * Consumes platform-reserve-requests events with comprehensive error handling
     * 
     * @param reserveRequestPayload The reserve request data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "platform-reserve-requests",
        groupId = "ledger-service-reserve-group",
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
    public void handlePlatformReserveRequest(
            @Payload Map<String, Object> reserveRequestPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String reserveRequestId = null;
        
        try {
            reserveRequestsProcessed.increment();
            
            log.info("Processing platform reserve request from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            reserveRequestId = (String) reserveRequestPayload.getOrDefault("reserveRequestId", 
                "reserve_" + System.currentTimeMillis());
            String reason = (String) reserveRequestPayload.get("reason");
            BigDecimal amount = new BigDecimal(reserveRequestPayload.get("amount").toString());
            String currency = (String) reserveRequestPayload.get("currency");
            
            log.info("Processing reserve request: {} - Amount: {} {} - Reason: {}", 
                reserveRequestId, amount, currency, reason);
            
            // Convert to structured reserve request object
            PlatformReserveRequest reserveRequest = convertToReserveRequest(reserveRequestPayload);
            
            // Validate reserve request data
            validateReserveRequest(reserveRequest);
            
            // Capture business metrics
            captureBusinessMetrics(reserveRequest);
            
            // Process reserve request in parallel operations
            CompletableFuture<Void> reserveCreation = processReserveCreation(reserveRequest);
            CompletableFuture<Void> ledgerEntries = processReserveLedgerEntries(reserveRequest);
            CompletableFuture<Void> riskAssessment = processRiskAssessment(reserveRequest);
            CompletableFuture<Void> complianceReporting = processComplianceReporting(reserveRequest);
            
            // Wait for all reserve operations to complete
            CompletableFuture.allOf(reserveCreation, ledgerEntries, riskAssessment, complianceReporting)
                .join();
            
            // Update platform liquidity metrics
            updatePlatformLiquidityMetrics(reserveRequest);
            
            // Trigger regulatory reporting if required
            triggerRegulatoryReporting(reserveRequest);
            
            reserveRequestsSuccessful.increment();
            log.info("Successfully processed platform reserve request: {}", reserveRequestId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            reserveRequestsFailed.increment();
            log.error("Failed to process platform reserve request: {} - Error: {}", 
                reserveRequestId, e.getMessage(), e);
            
            // Don't acknowledge - this will trigger retry mechanism
            throw new PlatformReserveException(
                "Failed to process platform reserve request: " + reserveRequestId, e);
                
        } finally {
            sample.stop(reserveProcessingTime);
        }
    }

    /**
     * Converts reserve request payload to structured PlatformReserveRequest
     */
    private PlatformReserveRequest convertToReserveRequest(Map<String, Object> reserveRequestPayload) {
        try {
            return PlatformReserveRequest.builder()
                .reserveRequestId((String) reserveRequestPayload.getOrDefault("reserveRequestId", 
                    "reserve_" + System.currentTimeMillis()))
                .amount(new BigDecimal(reserveRequestPayload.get("amount").toString()))
                .currency((String) reserveRequestPayload.get("currency"))
                .reason((String) reserveRequestPayload.get("reason"))
                .reserveType(determineReserveType((String) reserveRequestPayload.get("reason")))
                .priority(ReservePriority.valueOf(
                    reserveRequestPayload.getOrDefault("priority", "MEDIUM").toString()))
                .chargebackId((String) reserveRequestPayload.get("chargebackId"))
                .merchantId((String) reserveRequestPayload.get("merchantId"))
                .transactionId((String) reserveRequestPayload.get("transactionId"))
                .riskLevel((String) reserveRequestPayload.get("riskLevel"))
                .duration(reserveRequestPayload.get("duration") != null ? 
                    Integer.valueOf(reserveRequestPayload.get("duration").toString()) : null)
                .emergencyReserve(Boolean.valueOf(
                    reserveRequestPayload.getOrDefault("emergencyReserve", "false").toString()))
                .regulatoryRequired(Boolean.valueOf(
                    reserveRequestPayload.getOrDefault("regulatoryRequired", "false").toString()))
                .timestamp(LocalDateTime.parse(
                    reserveRequestPayload.getOrDefault("timestamp", LocalDateTime.now().toString()).toString()))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert reserve request payload", e);
            throw new IllegalArgumentException("Invalid platform reserve request format", e);
        }
    }

    /**
     * Determines reserve type based on reason
     */
    private ReserveType determineReserveType(String reason) {
        if (reason == null) return ReserveType.OPERATIONAL;
        
        String lowerReason = reason.toLowerCase();
        if (lowerReason.contains("chargeback")) {
            return ReserveType.CHARGEBACK;
        } else if (lowerReason.contains("fraud")) {
            return ReserveType.FRAUD;
        } else if (lowerReason.contains("emergency")) {
            return ReserveType.EMERGENCY;
        } else if (lowerReason.contains("regulatory") || lowerReason.contains("compliance")) {
            return ReserveType.REGULATORY;
        } else {
            return ReserveType.OPERATIONAL;
        }
    }

    /**
     * Validates platform reserve request data
     */
    private void validateReserveRequest(PlatformReserveRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid reserve amount");
        }
        
        if (request.getCurrency() == null || request.getCurrency().length() != 3) {
            throw new IllegalArgumentException("Valid currency code is required");
        }
        
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reserve reason is required");
        }
        
        if (request.getPriority() == null) {
            throw new IllegalArgumentException("Reserve priority is required");
        }
        
        if (request.getReserveType() == null) {
            throw new IllegalArgumentException("Reserve type is required");
        }
    }

    /**
     * Captures business metrics for monitoring and alerting
     */
    private void captureBusinessMetrics(PlatformReserveRequest request) {
        // Track reserves by type
        switch (request.getReserveType()) {
            case CHARGEBACK:
                chargebackReservesCreated.increment(
                    "priority", request.getPriority().toString(),
                    "currency", request.getCurrency()
                );
                break;
            case FRAUD:
                fraudReservesCreated.increment(
                    "priority", request.getPriority().toString(),
                    "currency", request.getCurrency()
                );
                break;
            case OPERATIONAL:
                operationalReservesCreated.increment(
                    "priority", request.getPriority().toString(),
                    "currency", request.getCurrency()
                );
                break;
            case EMERGENCY:
                emergencyReservesCreated.increment(
                    "priority", request.getPriority().toString(),
                    "currency", request.getCurrency()
                );
                break;
            case REGULATORY:
                regulatoryReservesCreated.increment(
                    "priority", request.getPriority().toString(),
                    "currency", request.getCurrency()
                );
                break;
        }
        
        // High-value reserve tracking
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            highValueReserves.increment(
                "reserve_type", request.getReserveType().toString(),
                "priority", request.getPriority().toString()
            );
        }
        
        // Emergency reserve tracking
        if (request.isEmergencyReserve()) {
            Counter.builder("waqiti.platform.reserve.emergency_flag")
                .tag("reserve_type", request.getReserveType().toString())
                .tag("priority", request.getPriority().toString())
                .register(meterRegistry)
                .increment();
        }
    }

    /**
     * Processes platform reserve creation
     */
    private CompletableFuture<Void> processReserveCreation(PlatformReserveRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Creating platform reserve for request: {}", request.getReserveRequestId());
                
                // Create platform reserve
                boolean reserveCreated = platformReserveService.createPlatformReserve(
                    request.getReserveRequestId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getReserveType(),
                    request.getReason(),
                    request.getDuration(),
                    request.isEmergencyReserve()
                );
                
                if (reserveCreated) {
                    reserveAdjustmentsSuccessful.increment();
                    log.info("Successfully created platform reserve: {} for amount: {} {}", 
                        request.getReserveRequestId(), request.getAmount(), request.getCurrency());
                } else {
                    throw new PlatformReserveException(
                        "Failed to create platform reserve: " + request.getReserveRequestId());
                }
                
            } catch (Exception e) {
                log.error("Failed to create platform reserve for request: {}", 
                    request.getReserveRequestId(), e);
                throw new PlatformReserveException("Platform reserve creation failed", e);
            }
        });
    }

    /**
     * Processes reserve ledger entries with double-entry bookkeeping
     */
    private CompletableFuture<Void> processReserveLedgerEntries(PlatformReserveRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Creating ledger entries for reserve: {}", request.getReserveRequestId());
                
                // Create double-entry bookkeeping entries for reserve
                ledgerService.createDoubleEntry(
                    request.getReserveRequestId(),
                    "PLATFORM_RESERVE_ALLOCATION",
                    request.getReason(),
                    "PLATFORM",
                    request.getAmount(),
                    request.getCurrency(),
                    "PLATFORM_RESERVES", // Debit platform reserves account
                    "CASH_LIQUIDITY", // Credit cash liquidity account
                    request.getTimestamp(),
                    Map.of(
                        "reserve_type", request.getReserveType().toString(),
                        "reserve_request_id", request.getReserveRequestId(),
                        "chargeback_id", request.getChargebackId() != null ? request.getChargebackId() : "",
                        "merchant_id", request.getMerchantId() != null ? request.getMerchantId() : "",
                        "transaction_id", request.getTransactionId() != null ? request.getTransactionId() : "",
                        "priority", request.getPriority().toString(),
                        "emergency_reserve", String.valueOf(request.isEmergencyReserve())
                    )
                );
                
                log.info("Ledger entries created for platform reserve: {}", request.getReserveRequestId());
                
            } catch (Exception e) {
                log.error("Failed to create ledger entries for reserve: {}", 
                    request.getReserveRequestId(), e);
                throw new PlatformReserveException("Reserve ledger entry processing failed", e);
            }
        });
    }

    /**
     * Processes risk assessment for reserve adequacy
     */
    private CompletableFuture<Void> processRiskAssessment(PlatformReserveRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing risk assessment for reserve: {}", request.getReserveRequestId());
                
                // Assess reserve adequacy and platform risk exposure
                riskManagementService.assessReserveAdequacy(
                    request.getReserveRequestId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getReserveType(),
                    request.getRiskLevel(),
                    request.isEmergencyReserve()
                );
                
                // Check if reserve triggers risk thresholds
                if (request.getAmount().compareTo(new BigDecimal("50000")) > 0) {
                    riskManagementService.flagHighValueReserve(
                        request.getReserveRequestId(),
                        request.getAmount(),
                        request.getReserveType(),
                        request.getReason()
                    );
                }
                
                log.info("Risk assessment completed for reserve: {}", request.getReserveRequestId());
                
            } catch (Exception e) {
                log.error("Failed to process risk assessment for reserve: {}", 
                    request.getReserveRequestId(), e);
                // Don't throw exception for risk assessment failures - log and continue
            }
        });
    }

    /**
     * Processes compliance reporting requirements
     */
    private CompletableFuture<Void> processComplianceReporting(PlatformReserveRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (request.isRegulatoryRequired() || 
                    request.getReserveType() == ReserveType.REGULATORY ||
                    request.getAmount().compareTo(new BigDecimal("25000")) > 0) {
                    
                    log.debug("Processing compliance reporting for reserve: {}", request.getReserveRequestId());
                    
                    // Trigger compliance reporting
                    financialReportingService.reportPlatformReserve(
                        request.getReserveRequestId(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getReserveType(),
                        request.getReason(),
                        request.isRegulatoryRequired()
                    );
                    
                    log.info("Compliance reporting processed for reserve: {}", request.getReserveRequestId());
                }
                
            } catch (Exception e) {
                log.error("Failed to process compliance reporting for reserve: {}", 
                    request.getReserveRequestId(), e);
                // Don't throw exception for compliance reporting failures - log and continue
            }
        });
    }

    /**
     * Updates platform liquidity metrics
     */
    private void updatePlatformLiquidityMetrics(PlatformReserveRequest request) {
        try {
            log.debug("Updating platform liquidity metrics for reserve: {}", request.getReserveRequestId());
            
            // Update platform liquidity and reserve ratios
            platformReserveService.updatePlatformLiquidityMetrics(
                request.getAmount(),
                request.getCurrency(),
                request.getReserveType()
            );
            
            // Check liquidity thresholds
            platformReserveService.checkLiquidityThresholds(request.getCurrency());
            
            log.info("Platform liquidity metrics updated for reserve: {}", request.getReserveRequestId());
            
        } catch (Exception e) {
            log.error("Failed to update platform liquidity metrics for reserve: {}", 
                request.getReserveRequestId(), e);
        }
    }

    /**
     * Triggers regulatory reporting if required
     */
    private void triggerRegulatoryReporting(PlatformReserveRequest request) {
        try {
            if (request.isRegulatoryRequired() || 
                request.getReserveType() == ReserveType.REGULATORY ||
                (request.getReserveType() == ReserveType.EMERGENCY && 
                 request.getAmount().compareTo(new BigDecimal("100000")) > 0)) {
                
                log.debug("Triggering regulatory reporting for reserve: {}", request.getReserveRequestId());
                
                // Trigger regulatory capital adequacy reporting
                financialReportingService.triggerCapitalAdequacyReporting(
                    request.getReserveRequestId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getReserveType(),
                    request.getReason()
                );
                
                log.info("Regulatory reporting triggered for reserve: {}", request.getReserveRequestId());
            }
            
        } catch (Exception e) {
            log.error("Failed to trigger regulatory reporting for reserve: {}", 
                request.getReserveRequestId(), e);
            // Don't throw exception for regulatory reporting failures - log and continue
        }
    }

    /**
     * Platform reserve request data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class PlatformReserveRequest {
        private String reserveRequestId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private ReserveType reserveType;
        private ReservePriority priority;
        private String chargebackId;
        private String merchantId;
        private String transactionId;
        private String riskLevel;
        private Integer duration; // Duration in days
        private boolean emergencyReserve;
        private boolean regulatoryRequired;
        private LocalDateTime timestamp;
    }

    /**
     * Platform reserve types
     */
    private enum ReserveType {
        CHARGEBACK,      // Chargeback liability coverage
        FRAUD,           // Fraud loss coverage
        OPERATIONAL,     // Operational risk coverage
        EMERGENCY,       // Emergency fund allocation
        REGULATORY       // Regulatory capital requirements
    }

    /**
     * Reserve priority levels
     */
    private enum ReservePriority {
        CRITICAL,        // Immediate reserve allocation required
        HIGH,           // Reserve within 1 hour
        MEDIUM,         // Reserve within 4 hours
        LOW             // Reserve within 24 hours
    }

    /**
     * Custom exception for platform reserve processing
     */
    public static class PlatformReserveException extends RuntimeException {
        public PlatformReserveException(String message) {
            super(message);
        }
        
        public PlatformReserveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}