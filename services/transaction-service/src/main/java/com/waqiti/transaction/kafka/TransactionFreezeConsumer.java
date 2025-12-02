package com.waqiti.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.FraudDetectionService;
import com.waqiti.transaction.service.TransactionService;
import com.waqiti.transaction.service.TransactionProcessingService;
import com.waqiti.transaction.client.NotificationServiceClient;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
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
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

/**
 * Critical Event Consumer: Transaction Freeze Requests
 * 
 * Handles suspicious transaction freezing for fraud prevention:
 * - Real-time transaction suspension based on fraud indicators
 * - Customer account freezing for suspicious activity
 * - Merchant transaction blocking for compliance violations
 * - Automated fraud response and investigation triggering
 * - Regulatory compliance for suspicious activity reporting
 * - Risk mitigation through immediate transaction halting
 * 
 * BUSINESS IMPACT: Without this consumer, freeze requests are generated
 * but NOT executed, leading to:
 * - Fraudulent transactions continuing to process
 * - Financial losses from unblocked suspicious activity
 * - Compliance violations for failed suspicious activity response
 * - Customer account security compromises
 * - Merchant risk exposure from unfreezed risky transactions
 * 
 * This consumer enables:
 * - Immediate fraud response and transaction blocking
 * - Automated suspicious activity investigation
 * - Compliance with regulatory freeze requirements
 * - Real-time risk mitigation and loss prevention
 * - Customer and merchant protection from fraud
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionFreezeConsumer {

    private final TransactionService transactionService;
    private final TransactionProcessingService transactionProcessingService;
    private final FraudDetectionService fraudDetectionService;
    private final TransactionRepository transactionRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Metrics
    private Counter freezeRequestsProcessed;
    private Counter freezeRequestsSuccessful;
    private Counter freezeRequestsFailed;
    private Counter fraudRelatedFreezes;
    private Counter complianceRelatedFreezes;
    private Counter customerAccountsFrozen;
    private Counter merchantAccountsFrozen;
    private Counter transactionsSuspended;
    private Timer freezeProcessingTime;
    private Counter investigationsTriggered;
    private Counter emergencyFreezesExecuted;

    @PostConstruct
    public void initializeMetrics() {
        freezeRequestsProcessed = Counter.builder("waqiti.transaction.freeze_requests.processed.total")
            .description("Total transaction freeze requests processed")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        freezeRequestsSuccessful = Counter.builder("waqiti.transaction.freeze_requests.successful")
            .description("Successful transaction freeze request processing")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        freezeRequestsFailed = Counter.builder("waqiti.transaction.freeze_requests.failed")
            .description("Failed transaction freeze request processing")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        fraudRelatedFreezes = Counter.builder("waqiti.transaction.freeze.fraud_related.total")
            .description("Fraud-related transaction freezes")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        complianceRelatedFreezes = Counter.builder("waqiti.transaction.freeze.compliance_related.total")
            .description("Compliance-related transaction freezes")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        customerAccountsFrozen = Counter.builder("waqiti.transaction.freeze.customer_accounts.total")
            .description("Customer accounts frozen")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        merchantAccountsFrozen = Counter.builder("waqiti.transaction.freeze.merchant_accounts.total")
            .description("Merchant accounts frozen")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        transactionsSuspended = Counter.builder("waqiti.transaction.freeze.transactions_suspended.total")
            .description("Transactions suspended due to freeze requests")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        freezeProcessingTime = Timer.builder("waqiti.transaction.freeze.processing.duration")
            .description("Time taken to process freeze requests")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        investigationsTriggered = Counter.builder("waqiti.transaction.freeze.investigations_triggered.total")
            .description("Fraud investigations triggered by freeze requests")
            .tag("service", "transaction-service")
            .register(meterRegistry);

        emergencyFreezesExecuted = Counter.builder("waqiti.transaction.freeze.emergency.total")
            .description("Emergency freeze requests executed")
            .tag("service", "transaction-service")
            .register(meterRegistry);
    }

    /**
     * Consumes transaction-freeze-requests events with comprehensive error handling
     * 
     * @param freezeRequestPayload The freeze request data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "transaction-freeze-requests",
        groupId = "transaction-service-freeze-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleTransactionFreezeRequest(
            ConsumerRecord<String, Map<String, Object>> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Map<String, Object> freezeRequestPayload = record.value();

        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = null;
        
        try {
            freezeRequestsProcessed.increment();
            
            log.info("Processing freeze request from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            requestId = (String) freezeRequestPayload.get("requestId");
            String customerId = (String) freezeRequestPayload.get("customerId");
            String merchantId = (String) freezeRequestPayload.get("merchantId");
            String reason = (String) freezeRequestPayload.get("reason");
            
            if (requestId == null) {
                requestId = "freeze_" + System.currentTimeMillis();
                freezeRequestPayload.put("requestId", requestId);
            }
            
            log.info("Processing freeze request: {} for customer: {} merchant: {} reason: {}", 
                requestId, customerId, merchantId, reason);
            
            // Convert to structured freeze request object
            TransactionFreezeRequest freezeRequest = convertToFreezeRequest(freezeRequestPayload);
            
            // Validate freeze request data
            validateFreezeRequest(freezeRequest);
            
            // Capture business metrics
            captureBusinessMetrics(freezeRequest);
            
            // Process freeze request in parallel operations
            CompletableFuture<Void> customerFreezing = processCustomerFreezing(freezeRequest);
            CompletableFuture<Void> merchantFreezing = processMerchantFreezing(freezeRequest);
            CompletableFuture<Void> transactionSuspension = processTransactionSuspension(freezeRequest);
            CompletableFuture<Void> fraudInvestigation = processFraudInvestigation(freezeRequest);
            
            // Wait for all freeze operations to complete
            CompletableFuture.allOf(customerFreezing, merchantFreezing, transactionSuspension, fraudInvestigation)
                .join();
            
            // Send notifications to affected parties
            sendFreezeNotifications(freezeRequest);
            
            // Trigger compliance reporting if required
            triggerComplianceReporting(freezeRequest);
            
            freezeRequestsSuccessful.increment();
            log.info("Successfully processed freeze request: {}", requestId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            freezeRequestsFailed.increment();
            log.error("Failed to process freeze request: {} - Error: {}", requestId, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Transaction freeze request sent to DLQ: requestId={}, destination={}, attemptNumber={}",
                        requestId, result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction freeze request - MESSAGE MAY BE LOST! " +
                            "requestId={}, partition={}, offset={}, error={}",
                            requestId, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            // Don't acknowledge - this will trigger retry mechanism
            throw new TransactionFreezeException(
                "Failed to process freeze request: " + requestId, e);

        } finally {
            sample.stop(freezeProcessingTime);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, Map<String, Object>> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Transaction freeze request sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        try {
            Map<String, Object> freezeRequestPayload = record.value();
            String requestId = (String) freezeRequestPayload.get("requestId");
            String customerId = (String) freezeRequestPayload.get("customerId");

            log.error("CRITICAL: Freeze request {} for customer {} sent to DLT - requires manual intervention: {}",
                    requestId, customerId, exceptionMessage);

            // Send critical alert for manual intervention
            if (customerId != null) {
                notificationServiceClient.sendComplianceFreezeNotification(
                    requestId,
                    "FREEZE_REQUEST_DLT: " + exceptionMessage,
                    new java.util.HashSet<>()
                );
            }
        } catch (Exception e) {
            log.error("Failed to process DLT event for freeze request: {}", e.getMessage(), e);
        }
    }

    /**
     * Converts freeze request payload to structured TransactionFreezeRequest
     */
    private TransactionFreezeRequest convertToFreezeRequest(Map<String, Object> freezeRequestPayload) {
        try {
            return TransactionFreezeRequest.builder()
                .requestId((String) freezeRequestPayload.get("requestId"))
                .customerId((String) freezeRequestPayload.get("customerId"))
                .merchantId((String) freezeRequestPayload.get("merchantId"))
                .transactionId((String) freezeRequestPayload.get("transactionId"))
                .chargebackId((String) freezeRequestPayload.get("chargebackId"))
                .reason((String) freezeRequestPayload.get("reason"))
                .priority(FreezeRequestPriority.valueOf(
                    freezeRequestPayload.getOrDefault("priority", "MEDIUM").toString()))
                .freezeType(FreezeRequestType.valueOf(
                    freezeRequestPayload.getOrDefault("freezeType", "SUSPICIOUS_ACTIVITY").toString()))
                .emergencyFreeze(Boolean.valueOf(
                    freezeRequestPayload.getOrDefault("emergencyFreeze", "false").toString()))
                .duration(freezeRequestPayload.get("duration") != null ? 
                    Integer.valueOf(freezeRequestPayload.get("duration").toString()) : null)
                .amount(freezeRequestPayload.get("amount") != null ? 
                    new BigDecimal(freezeRequestPayload.get("amount").toString()) : null)
                .currency((String) freezeRequestPayload.get("currency"))
                .fraudScore(freezeRequestPayload.get("fraudScore") != null ? 
                    Double.valueOf(freezeRequestPayload.get("fraudScore").toString()) : null)
                .riskLevel((String) freezeRequestPayload.get("riskLevel"))
                .complianceFlags(freezeRequestPayload.get("complianceFlags") != null ?
                    (Set<String>) freezeRequestPayload.get("complianceFlags") : new HashSet<>())
                .investigationRequired(Boolean.valueOf(
                    freezeRequestPayload.getOrDefault("investigationRequired", "true").toString()))
                .timestamp(LocalDateTime.parse(
                    freezeRequestPayload.getOrDefault("timestamp", LocalDateTime.now().toString()).toString()))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert freeze request payload", e);
            throw new IllegalArgumentException("Invalid freeze request format", e);
        }
    }

    /**
     * Validates freeze request data
     */
    private void validateFreezeRequest(TransactionFreezeRequest request) {
        if (request.getCustomerId() == null && request.getMerchantId() == null && request.getTransactionId() == null) {
            throw new IllegalArgumentException("At least one target identifier (customer, merchant, or transaction) is required");
        }
        
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Freeze reason is required");
        }
        
        if (request.getPriority() == null) {
            throw new IllegalArgumentException("Freeze priority is required");
        }
        
        if (request.getFreezeType() == null) {
            throw new IllegalArgumentException("Freeze type is required");
        }
    }

    /**
     * Captures business metrics for monitoring and alerting
     */
    private void captureBusinessMetrics(TransactionFreezeRequest request) {
        // Track freeze by type
        switch (request.getFreezeType()) {
            case FRAUD_DETECTED:
            case SUSPICIOUS_ACTIVITY:
                fraudRelatedFreezes.increment(
                    "reason", request.getReason(),
                    "priority", request.getPriority().toString()
                );
                break;
            case COMPLIANCE_VIOLATION:
            case REGULATORY_REQUIREMENT:
                complianceRelatedFreezes.increment(
                    "reason", request.getReason(),
                    "priority", request.getPriority().toString()
                );
                break;
        }
        
        // Emergency freeze tracking
        if (request.isEmergencyFreeze()) {
            emergencyFreezesExecuted.increment(
                "freeze_type", request.getFreezeType().toString(),
                "priority", request.getPriority().toString()
            );
        }
        
        // Risk level tracking
        Counter.builder("waqiti.transaction.freeze.by_risk_level")
            .tag("risk_level", request.getRiskLevel() != null ? request.getRiskLevel() : "unknown")
            .tag("freeze_type", request.getFreezeType().toString())
            .register(meterRegistry)
            .increment();
    }

    /**
     * Processes customer account freezing
     */
    private CompletableFuture<Void> processCustomerFreezing(TransactionFreezeRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (request.getCustomerId() != null) {
                    log.debug("Processing customer freeze for request: {}", request.getRequestId());
                    
                    // Freeze customer account
                    boolean freezeSuccessful = transactionService.freezeCustomerAccount(
                        request.getCustomerId(),
                        request.getReason(),
                        request.getDuration(),
                        request.isEmergencyFreeze()
                    );
                    
                    if (freezeSuccessful) {
                        customerAccountsFrozen.increment();
                        log.info("Successfully froze customer account: {} for request: {}", 
                            request.getCustomerId(), request.getRequestId());
                    } else {
                        throw new TransactionFreezeException(
                            "Failed to freeze customer account: " + request.getCustomerId());
                    }
                }
                
            } catch (Exception e) {
                log.error("Failed to process customer freeze for request: {}", 
                    request.getRequestId(), e);
                throw new TransactionFreezeException("Customer freeze processing failed", e);
            }
        });
    }

    /**
     * Processes merchant account freezing
     */
    private CompletableFuture<Void> processMerchantFreezing(TransactionFreezeRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (request.getMerchantId() != null) {
                    log.debug("Processing merchant freeze for request: {}", request.getRequestId());
                    
                    // Freeze merchant account
                    boolean freezeSuccessful = transactionService.freezeMerchantAccount(
                        request.getMerchantId(),
                        request.getReason(),
                        request.getDuration(),
                        request.isEmergencyFreeze()
                    );
                    
                    if (freezeSuccessful) {
                        merchantAccountsFrozen.increment();
                        log.info("Successfully froze merchant account: {} for request: {}", 
                            request.getMerchantId(), request.getRequestId());
                    } else {
                        throw new TransactionFreezeException(
                            "Failed to freeze merchant account: " + request.getMerchantId());
                    }
                }
                
            } catch (Exception e) {
                log.error("Failed to process merchant freeze for request: {}", 
                    request.getRequestId(), e);
                throw new TransactionFreezeException("Merchant freeze processing failed", e);
            }
        });
    }

    /**
     * Processes transaction suspension
     */
    private CompletableFuture<Void> processTransactionSuspension(TransactionFreezeRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (request.getTransactionId() != null) {
                    log.debug("Processing transaction suspension for request: {}", request.getRequestId());
                    
                    // Suspend specific transaction
                    boolean suspensionSuccessful = transactionProcessingService.suspendTransaction(
                        request.getTransactionId(),
                        request.getReason(),
                        request.isEmergencyFreeze()
                    );
                    
                    if (suspensionSuccessful) {
                        transactionsSuspended.increment();
                        log.info("Successfully suspended transaction: {} for request: {}", 
                            request.getTransactionId(), request.getRequestId());
                    } else {
                        throw new TransactionFreezeException(
                            "Failed to suspend transaction: " + request.getTransactionId());
                    }
                }
                
                // Also suspend all related transactions if customer/merchant freeze
                if (request.getCustomerId() != null || request.getMerchantId() != null) {
                    suspendRelatedTransactions(request);
                }
                
            } catch (Exception e) {
                log.error("Failed to process transaction suspension for request: {}", 
                    request.getRequestId(), e);
                throw new TransactionFreezeException("Transaction suspension processing failed", e);
            }
        });
    }

    /**
     * Processes fraud investigation triggering
     */
    private CompletableFuture<Void> processFraudInvestigation(TransactionFreezeRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (request.isInvestigationRequired() && 
                    (request.getFreezeType() == FreezeRequestType.FRAUD_DETECTED ||
                     request.getFreezeType() == FreezeRequestType.SUSPICIOUS_ACTIVITY)) {
                    
                    log.debug("Processing fraud investigation for request: {}", request.getRequestId());
                    
                    // Trigger fraud investigation
                    fraudDetectionService.triggerInvestigation(
                        request.getRequestId(),
                        request.getCustomerId(),
                        request.getMerchantId(),
                        request.getTransactionId(),
                        request.getReason(),
                        request.getFraudScore(),
                        request.getRiskLevel()
                    );
                    
                    investigationsTriggered.increment();
                    log.info("Triggered fraud investigation for request: {}", request.getRequestId());
                }
                
            } catch (Exception e) {
                log.error("Failed to process fraud investigation for request: {}", 
                    request.getRequestId(), e);
                // Don't throw exception for investigation failures - log and continue
            }
        });
    }

    /**
     * Suspends all related transactions for customer/merchant freeze
     */
    private void suspendRelatedTransactions(TransactionFreezeRequest request) {
        try {
            if (request.getCustomerId() != null) {
                // Find and suspend all pending transactions for customer
                transactionRepository.findPendingTransactionsByCustomer(request.getCustomerId())
                    .forEach(transaction -> {
                        try {
                            transactionProcessingService.suspendTransaction(
                                transaction.getId(),
                                "Related to freeze request: " + request.getReason(),
                                request.isEmergencyFreeze()
                            );
                            transactionsSuspended.increment();
                        } catch (Exception e) {
                            log.error("Failed to suspend related transaction: {}", transaction.getId(), e);
                        }
                    });
            }
            
            if (request.getMerchantId() != null) {
                // Find and suspend all pending transactions for merchant
                transactionRepository.findPendingTransactionsByMerchant(request.getMerchantId())
                    .forEach(transaction -> {
                        try {
                            transactionProcessingService.suspendTransaction(
                                transaction.getId(),
                                "Related to freeze request: " + request.getReason(),
                                request.isEmergencyFreeze()
                            );
                            transactionsSuspended.increment();
                        } catch (Exception e) {
                            log.error("Failed to suspend related transaction: {}", transaction.getId(), e);
                        }
                    });
            }
            
        } catch (Exception e) {
            log.error("Failed to suspend related transactions for request: {}", 
                request.getRequestId(), e);
        }
    }

    /**
     * Sends freeze notifications to affected parties
     */
    private void sendFreezeNotifications(TransactionFreezeRequest request) {
        try {
            log.debug("Sending freeze notifications for request: {}", request.getRequestId());
            
            // Notify customer if account frozen
            if (request.getCustomerId() != null) {
                notificationServiceClient.sendCustomerFreezeNotification(
                    request.getCustomerId(),
                    request.getReason(),
                    request.isEmergencyFreeze(),
                    request.getDuration()
                );
            }
            
            // Notify merchant if account frozen
            if (request.getMerchantId() != null) {
                notificationServiceClient.sendMerchantFreezeNotification(
                    request.getMerchantId(),
                    request.getReason(),
                    request.isEmergencyFreeze(),
                    request.getDuration()
                );
            }
            
            // Notify compliance team for regulatory freezes
            if (request.getFreezeType() == FreezeRequestType.COMPLIANCE_VIOLATION ||
                request.getFreezeType() == FreezeRequestType.REGULATORY_REQUIREMENT) {
                notificationServiceClient.sendComplianceFreezeNotification(
                    request.getRequestId(),
                    request.getReason(),
                    request.getComplianceFlags()
                );
            }
            
            log.info("Sent freeze notifications for request: {}", request.getRequestId());
            
        } catch (Exception e) {
            log.error("Failed to send freeze notifications for request: {}", 
                request.getRequestId(), e);
            // Don't throw exception for notification failures - log and continue
        }
    }

    /**
     * Triggers compliance reporting if required
     */
    private void triggerComplianceReporting(TransactionFreezeRequest request) {
        try {
            if (request.getFreezeType() == FreezeRequestType.COMPLIANCE_VIOLATION ||
                request.getFreezeType() == FreezeRequestType.REGULATORY_REQUIREMENT ||
                (request.getFraudScore() != null && request.getFraudScore() > 0.8)) {
                
                log.debug("Triggering compliance reporting for request: {}", request.getRequestId());
                
                // Trigger SAR filing if suspicious activity
                if (request.getFreezeType() == FreezeRequestType.SUSPICIOUS_ACTIVITY) {
                    notificationServiceClient.triggerSarFiling(
                        request.getCustomerId(),
                        request.getMerchantId(),
                        request.getTransactionId(),
                        request.getReason(),
                        request.getAmount()
                    );
                }
                
                // Trigger compliance review
                notificationServiceClient.triggerComplianceReview(
                    request.getRequestId(),
                    request.getComplianceFlags(),
                    request.getPriority().toString()
                );
                
                log.info("Triggered compliance reporting for request: {}", request.getRequestId());
            }
            
        } catch (Exception e) {
            log.error("Failed to trigger compliance reporting for request: {}", 
                request.getRequestId(), e);
            // Don't throw exception for compliance reporting failures - log and continue
        }
    }

    /**
     * Transaction freeze request data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class TransactionFreezeRequest {
        private String requestId;
        private String customerId;
        private String merchantId;
        private String transactionId;
        private String chargebackId;
        private String reason;
        private FreezeRequestPriority priority;
        private FreezeRequestType freezeType;
        private boolean emergencyFreeze;
        private Integer duration; // Duration in hours
        private BigDecimal amount;
        private String currency;
        private Double fraudScore;
        private String riskLevel;
        private Set<String> complianceFlags;
        private boolean investigationRequired;
        private LocalDateTime timestamp;
    }

    /**
     * Freeze request priority levels
     */
    private enum FreezeRequestPriority {
        CRITICAL,    // Immediate freeze required
        HIGH,        // Freeze within 5 minutes
        MEDIUM,      // Freeze within 15 minutes
        LOW          // Freeze within 1 hour
    }

    /**
     * Freeze request types
     */
    private enum FreezeRequestType {
        FRAUD_DETECTED,          // Confirmed fraud activity
        SUSPICIOUS_ACTIVITY,     // Potential fraud or suspicious behavior
        COMPLIANCE_VIOLATION,    // Regulatory compliance violation
        REGULATORY_REQUIREMENT,  // Government or regulatory mandate
        CHARGEBACK_RELATED,      // Related to chargeback activity
        MANUAL_REVIEW_REQUIRED   // Manual investigation needed
    }

    /**
     * Custom exception for transaction freeze processing
     */
    public static class TransactionFreezeException extends RuntimeException {
        public TransactionFreezeException(String message) {
            super(message);
        }
        
        public TransactionFreezeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}