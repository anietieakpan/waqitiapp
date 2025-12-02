package com.waqiti.payment.kafka;

import com.waqiti.common.events.ACHPaymentEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.entity.ACHTransaction;
import com.waqiti.payment.repository.ACHTransactionRepository;
import com.waqiti.payment.service.ACHPaymentService;
import com.waqiti.payment.service.ACHBatchService;
import com.waqiti.payment.service.ACHValidationService;
import com.waqiti.payment.service.NAACHAComplianceService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.alerting.PagerDutyAlertService;
import com.waqiti.common.alerting.SlackAlertService;
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
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ACHPaymentEventsConsumer {
    
    private final ACHTransactionRepository achRepository;
    private final ACHPaymentService achPaymentService;
    private final ACHBatchService achBatchService;
    private final ACHValidationService achValidationService;
    private final NAACHAComplianceService naachaService;
    private final FraudDetectionService fraudDetectionService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PagerDutyAlertService pagerDutyAlertService;
    private final SlackAlertService slackAlertService;
    private final UniversalDLQHandler dlqHandler;
    
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("25000.00");
    private static final BigDecimal SAME_DAY_ACH_LIMIT = new BigDecimal("1000000.00");
    private static final int MAX_RETURN_ATTEMPTS = 2;
    private static final int ACH_SETTLEMENT_DAYS = 3;
    
    @KafkaListener(
        topics = {"ach-payment-events", "ach-debit-events", "ach-credit-events"},
        groupId = "ach-payment-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleACHPaymentEvent(
            @Payload ACHPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("ach-%s-p%d-o%d", 
            event.getAchTransactionId(), partition, offset);
        
        log.info("Processing ACH payment: id={}, status={}, secCode={}, amount={}, routingNumber={}",
            event.getAchTransactionId(), event.getStatus(), event.getSecCode(), 
            event.getAmount(), event.getRoutingNumber());
        
        try {
            switch (event.getStatus()) {
                case "ACH_INITIATED":
                    processACHInitiated(event, correlationId);
                    break;
                    
                case "ACH_VALIDATION":
                    processACHValidation(event, correlationId);
                    break;
                    
                case "ACH_BATCH_CREATED":
                    processACHBatchCreated(event, correlationId);
                    break;
                    
                case "ACH_SUBMITTED":
                    processACHSubmitted(event, correlationId);
                    break;
                    
                case "ACH_PENDING":
                    processACHPending(event, correlationId);
                    break;
                    
                case "ACH_SETTLED":
                    processACHSettled(event, correlationId);
                    break;
                    
                case "ACH_COMPLETED":
                    processACHCompleted(event, correlationId);
                    break;
                    
                case "ACH_RETURN":
                    processACHReturn(event, correlationId);
                    break;
                    
                case "ACH_REVERSAL":
                    processACHReversal(event, correlationId);
                    break;
                    
                case "ACH_FAILED":
                    processACHFailed(event, correlationId);
                    break;
                    
                case "ACH_CORRECTION":
                    processACHCorrection(event, correlationId);
                    break;
                    
                case "ACH_NOTIFICATION_OF_CHANGE":
                    processACHNOC(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown ACH status: {}", event.getStatus());
                    break;
            }
            
            auditService.logPaymentEvent("ACH_PAYMENT_PROCESSED", event.getAchTransactionId(),
                Map.of("status", event.getStatus(), "secCode", event.getSecCode(),
                    "amount", event.getAmount(), "routingNumber", event.getRoutingNumber(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process ACH payment: achId={}, partition={}, offset={}, error={}",
                    event.getAchTransactionId(), partition, offset, e.getMessage(), e);

            // Send to DLQ with ConsumerRecord wrapper
            org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record =
                new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                    "ach-payment-events", partition, offset,
                    event.getAchTransactionId(), event.toString());

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> {
                    log.info("ACH message sent to DLQ: achId={}, offset={}, destination={}, category={}",
                            event.getAchTransactionId(), offset, result.getDestinationTopic(), result.getFailureCategory());
                })
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for ACH payment - MESSAGE MAY BE LOST! " +
                            "achId={}, partition={}, offset={}, error={}",
                            event.getAchTransactionId(), partition, offset, dlqError.getMessage(), dlqError);

                    // CRITICAL: Trigger alerts for DLQ failure
                    triggerACHDLQAlert(event, partition, offset, dlqError, e);

                    return null;
                });

            // Do NOT acknowledge - let Kafka retry or manual intervention handle this
            throw new RuntimeException("ACH payment processing failed", e);
        }
    }
    
    private void processACHInitiated(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = ACHTransaction.builder()
            .achTransactionId(event.getAchTransactionId())
            .customerId(event.getCustomerId())
            .transactionType(event.getTransactionType())
            .secCode(event.getSecCode())
            .originatorId(event.getOriginatorId())
            .receiverId(event.getReceiverId())
            .routingNumber(event.getRoutingNumber())
            .accountNumber(event.getAccountNumber())
            .accountType(event.getAccountType())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .description(event.getDescription())
            .companyName(event.getCompanyName())
            .companyId(event.getCompanyId())
            .effectiveDate(event.getEffectiveDate())
            .status("ACH_INITIATED")
            .initiatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        achRepository.save(achTransaction);
        
        if (event.getAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            notificationService.sendNotification("ACH_OPERATIONS", "High Value ACH Transaction",
                String.format("ACH transaction %s for %s %s requires monitoring", 
                    event.getAchTransactionId(), event.getAmount(), event.getCurrency()),
                correlationId);
        }
        
        kafkaTemplate.send("ach-payment-events", Map.of(
            "achTransactionId", event.getAchTransactionId(),
            "status", "ACH_VALIDATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordACHInitiated(event.getSecCode(), event.getAmount());
        
        log.info("ACH initiated: id={}, secCode={}, amount={}", 
            event.getAchTransactionId(), event.getSecCode(), event.getAmount());
    }
    
    private void processACHValidation(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_VALIDATION");
        achTransaction.setValidationStartedAt(LocalDateTime.now());
        achRepository.save(achTransaction);
        
        Map<String, Object> validationResult = achValidationService.validateACHTransaction(
            event.getAchTransactionId(),
            event.getRoutingNumber(),
            event.getAccountNumber(),
            event.getSecCode(),
            event.getAmount()
        );
        
        boolean valid = (boolean) validationResult.get("valid");
        String validationErrors = (String) validationResult.get("errors");
        
        achTransaction.setValidationResult(validationResult);
        
        if (!valid) {
            achTransaction.setStatus("ACH_FAILED");
            achTransaction.setFailureReason("VALIDATION_FAILED");
            achTransaction.setFailureDetails(validationErrors);
            achTransaction.setFailedAt(LocalDateTime.now());
            achRepository.save(achTransaction);
            
            notificationService.sendNotification(event.getCustomerId(), "ACH Transaction Failed",
                String.format("ACH transaction failed validation: %s", validationErrors),
                correlationId);
            return;
        }
        
        Map<String, Object> fraudCheck = fraudDetectionService.assessACHFraudRisk(
            event.getAchTransactionId(),
            event.getCustomerId(),
            event.getAmount(),
            event.getTransactionType(),
            event.getSecCode()
        );
        
        int fraudScore = (int) fraudCheck.get("fraudScore");
        achTransaction.setFraudScore(fraudScore);
        
        if (fraudScore > 70) {
            achTransaction.setStatus("ACH_FAILED");
            achTransaction.setFailureReason("FRAUD_DETECTED");
            achTransaction.setFailedAt(LocalDateTime.now());
            achRepository.save(achTransaction);
            
            kafkaTemplate.send("fraud-alerts", Map.of(
                "achTransactionId", event.getAchTransactionId(),
                "fraudScore", fraudScore,
                "alertType", "HIGH_FRAUD_SCORE_ACH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            return;
        }
        
        achTransaction.setStatus("ACH_BATCH_CREATED");
        achTransaction.setValidationCompletedAt(LocalDateTime.now());
        achRepository.save(achTransaction);
        
        kafkaTemplate.send("ach-payment-events", Map.of(
            "achTransactionId", event.getAchTransactionId(),
            "status", "ACH_BATCH_CREATED",
            "fraudScore", fraudScore,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("ACH validation completed: id={}, valid={}, fraudScore={}", 
            event.getAchTransactionId(), valid, fraudScore);
    }
    
    private void processACHBatchCreated(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_BATCH_CREATED");
        achTransaction.setBatchCreatedAt(LocalDateTime.now());
        achRepository.save(achTransaction);
        
        String batchId = achBatchService.addTransactionToBatch(
            event.getAchTransactionId(),
            event.getSecCode(),
            event.getEffectiveDate(),
            event.getCompanyId()
        );
        
        achTransaction.setBatchId(batchId);
        achRepository.save(achTransaction);
        
        String naachaFile = naachaService.generateNAACHAFile(
            batchId,
            event.getCompanyId(),
            event.getCompanyName(),
            event.getEffectiveDate()
        );
        
        achTransaction.setNaachaFileReference(naachaFile);
        achTransaction.setStatus("ACH_SUBMITTED");
        achRepository.save(achTransaction);
        
        kafkaTemplate.send("ach-payment-events", Map.of(
            "achTransactionId", event.getAchTransactionId(),
            "status", "ACH_SUBMITTED",
            "batchId", batchId,
            "naachaFile", naachaFile,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("ACH batch created: id={}, batchId={}, naachaFile={}", 
            event.getAchTransactionId(), batchId, naachaFile);
    }
    
    private void processACHSubmitted(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_SUBMITTED");
        achTransaction.setSubmittedAt(LocalDateTime.now());
        achTransaction.setSubmittedToBank(event.getSubmittedToBank());
        achRepository.save(achTransaction);
        
        LocalDate settlementDate = calculateSettlementDate(event.getEffectiveDate(), event.getSecCode());
        achTransaction.setExpectedSettlementDate(settlementDate);
        achRepository.save(achTransaction);
        
        kafkaTemplate.send("ach-payment-events", Map.of(
            "achTransactionId", event.getAchTransactionId(),
            "status", "ACH_PENDING",
            "expectedSettlementDate", settlementDate,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification(event.getCustomerId(), "ACH Transaction Submitted",
            String.format("Your ACH transaction of %s %s has been submitted. Expected settlement: %s", 
                event.getAmount(), event.getCurrency(), settlementDate),
            correlationId);
        
        log.info("ACH submitted: id={}, bank={}, settlementDate={}", 
            event.getAchTransactionId(), event.getSubmittedToBank(), settlementDate);
    }
    
    private void processACHPending(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_PENDING");
        achTransaction.setPendingAt(LocalDateTime.now());
        achRepository.save(achTransaction);
        
        metricsService.recordACHPending(event.getSecCode());
        
        log.info("ACH pending: id={}, expectedSettlement={}", 
            event.getAchTransactionId(), achTransaction.getExpectedSettlementDate());
    }
    
    private void processACHSettled(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_SETTLED");
        achTransaction.setSettledAt(LocalDateTime.now());
        achTransaction.setSettlementReference(event.getSettlementReference());
        achRepository.save(achTransaction);
        
        achPaymentService.updateAccountBalances(
            event.getAchTransactionId(),
            event.getCustomerId(),
            event.getAccountNumber(),
            event.getAmount(),
            event.getTransactionType()
        );
        
        kafkaTemplate.send("ach-payment-events", Map.of(
            "achTransactionId", event.getAchTransactionId(),
            "status", "ACH_COMPLETED",
            "settlementReference", event.getSettlementReference(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        kafkaTemplate.send("ledger-recorded-events", Map.of(
            "transactionId", event.getAchTransactionId(),
            "accountNumber", event.getAccountNumber(),
            "amount", event.getAmount(),
            "transactionType", event.getTransactionType(),
            "secCode", event.getSecCode(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("ACH settled: id={}, settlementRef={}", 
            event.getAchTransactionId(), event.getSettlementReference());
    }
    
    private void processACHCompleted(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_COMPLETED");
        achTransaction.setCompletedAt(LocalDateTime.now());
        achRepository.save(achTransaction);
        
        notificationService.sendNotification(event.getCustomerId(), "ACH Transaction Completed",
            String.format("Your ACH transaction of %s %s has been completed successfully", 
                event.getAmount(), event.getCurrency()),
            correlationId);
        
        metricsService.recordACHCompleted(event.getSecCode(), event.getAmount());
        
        log.info("ACH completed: id={}, amount={}", 
            event.getAchTransactionId(), event.getAmount());
    }
    
    private void processACHReturn(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_RETURN");
        achTransaction.setReturnedAt(LocalDateTime.now());
        achTransaction.setReturnCode(event.getReturnCode());
        achTransaction.setReturnReason(event.getReturnReason());
        achTransaction.setReturnAttempts(achTransaction.getReturnAttempts() + 1);
        achRepository.save(achTransaction);
        
        achPaymentService.reverseAccountBalances(
            event.getAchTransactionId(),
            event.getCustomerId(),
            event.getAccountNumber(),
            event.getAmount(),
            event.getTransactionType()
        );
        
        notificationService.sendNotification(event.getCustomerId(), "ACH Transaction Returned",
            String.format("Your ACH transaction was returned. Code: %s. Reason: %s", 
                event.getReturnCode(), event.getReturnReason()),
            correlationId);
        
        notificationService.sendNotification("ACH_OPERATIONS", "ACH Return Received",
            String.format("ACH transaction %s returned with code %s: %s", 
                event.getAchTransactionId(), event.getReturnCode(), event.getReturnReason()),
            correlationId);
        
        if (achTransaction.getReturnAttempts() < MAX_RETURN_ATTEMPTS && 
            "R01".equals(event.getReturnCode())) {
            kafkaTemplate.send("ach-retry-queue", Map.of(
                "achTransactionId", event.getAchTransactionId(),
                "retryAttempt", achTransaction.getReturnAttempts(),
                "retryAfter", LocalDateTime.now().plusDays(1),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordACHReturn(event.getSecCode(), event.getReturnCode());
        
        log.error("ACH returned: id={}, returnCode={}, reason={}", 
            event.getAchTransactionId(), event.getReturnCode(), event.getReturnReason());
    }
    
    private void processACHReversal(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_REVERSAL");
        achTransaction.setReversedAt(LocalDateTime.now());
        achTransaction.setReversalReason(event.getReversalReason());
        achRepository.save(achTransaction);
        
        achPaymentService.processACHReversal(
            event.getAchTransactionId(),
            event.getOriginalTransactionId(),
            event.getAmount(),
            event.getReversalReason()
        );
        
        kafkaTemplate.send("transaction-reversals", Map.of(
            "reversalId", UUID.randomUUID().toString(),
            "originalTransactionId", event.getOriginalTransactionId(),
            "achTransactionId", event.getAchTransactionId(),
            "amount", event.getAmount(),
            "reversalType", "ACH_REVERSAL",
            "reason", event.getReversalReason(),
            "status", "REVERSAL_COMPLETED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordACHReversal(event.getSecCode());
        
        log.warn("ACH reversed: id={}, reason={}", 
            event.getAchTransactionId(), event.getReversalReason());
    }
    
    private void processACHFailed(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_FAILED");
        achTransaction.setFailedAt(LocalDateTime.now());
        achTransaction.setFailureReason(event.getFailureReason());
        achTransaction.setErrorCode(event.getErrorCode());
        achRepository.save(achTransaction);
        
        notificationService.sendNotification(event.getCustomerId(), "ACH Transaction Failed",
            String.format("Your ACH transaction of %s %s failed: %s", 
                event.getAmount(), event.getCurrency(), event.getFailureReason()),
            correlationId);
        
        metricsService.recordACHFailed(event.getSecCode(), event.getFailureReason());
        
        log.error("ACH failed: id={}, reason={}, errorCode={}", 
            event.getAchTransactionId(), event.getFailureReason(), event.getErrorCode());
    }
    
    private void processACHCorrection(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setStatus("ACH_CORRECTION");
        achTransaction.setCorrectionAppliedAt(LocalDateTime.now());
        achTransaction.setCorrectionCode(event.getCorrectionCode());
        achTransaction.setCorrectionDetails(event.getCorrectionDetails());
        achRepository.save(achTransaction);
        
        achPaymentService.applyCorrectionToACH(
            event.getAchTransactionId(),
            event.getCorrectionCode(),
            event.getCorrectionDetails()
        );
        
        notificationService.sendNotification("ACH_OPERATIONS", "ACH Correction Applied",
            String.format("Correction applied to ACH %s: Code %s", 
                event.getAchTransactionId(), event.getCorrectionCode()),
            correlationId);
        
        log.warn("ACH correction applied: id={}, code={}", 
            event.getAchTransactionId(), event.getCorrectionCode());
    }
    
    private void processACHNOC(ACHPaymentEvent event, String correlationId) {
        ACHTransaction achTransaction = achRepository.findByAchTransactionId(event.getAchTransactionId())
            .orElseThrow(() -> new RuntimeException("ACH transaction not found"));
        
        achTransaction.setNocReceivedAt(LocalDateTime.now());
        achTransaction.setNocCode(event.getNocCode());
        achTransaction.setNocDetails(event.getNocDetails());
        achRepository.save(achTransaction);
        
        achPaymentService.processNotificationOfChange(
            event.getAchTransactionId(),
            event.getCustomerId(),
            event.getNocCode(),
            event.getNocDetails()
        );
        
        kafkaTemplate.send("ach-noc-updates", Map.of(
            "achTransactionId", event.getAchTransactionId(),
            "customerId", event.getCustomerId(),
            "nocCode", event.getNocCode(),
            "nocDetails", event.getNocDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification("ACH_OPERATIONS", "ACH Notification of Change",
            String.format("NOC received for ACH %s: Code %s", 
                event.getAchTransactionId(), event.getNocCode()),
            correlationId);
        
        log.info("ACH NOC received: id={}, code={}", 
            event.getAchTransactionId(), event.getNocCode());
    }
    
    private LocalDate calculateSettlementDate(LocalDate effectiveDate, String secCode) {
        LocalDate baseDate = effectiveDate != null ? effectiveDate : LocalDate.now();

        if ("WEB".equals(secCode) || "TEL".equals(secCode)) {
            return baseDate.plusDays(1);
        } else if ("PPD".equals(secCode) || "CCD".equals(secCode)) {
            return baseDate.plusDays(ACH_SETTLEMENT_DAYS);
        }

        return baseDate.plusDays(ACH_SETTLEMENT_DAYS);
    }

    /**
     * Triggers CRITICAL alerts for ACH DLQ failures
     * ACH payments are time-sensitive and require immediate attention
     */
    private void triggerACHDLQAlert(ACHPaymentEvent event, int partition, long offset,
                                    Throwable dlqError, Exception originalError) {
        try {
            String topic = "ach-payment-events";
            String messageId = String.format("%s-%d-%d", topic, partition, offset);
            String errorDetails = String.format("ACH Transaction: %s | Original Error: %s | DLQ Failure: %s",
                event.getAchTransactionId(), originalError.getMessage(), dlqError.getMessage());

            Map<String, Object> payload = new HashMap<>();
            payload.put("achTransactionId", event.getAchTransactionId());
            payload.put("customerId", event.getCustomerId());
            payload.put("amount", event.getAmount().toString());
            payload.put("currency", event.getCurrency());
            payload.put("secCode", event.getSecCode());
            payload.put("transactionType", event.getTransactionType());
            payload.put("routingNumber", event.getRoutingNumber());
            payload.put("eventType", event.getEventType());
            payload.put("effectiveDate", event.getEffectiveDate() != null ? event.getEffectiveDate().toString() : "N/A");

            // Trigger PagerDuty - ACH is time-sensitive
            pagerDutyAlertService.triggerDLQFailureAlert(topic, messageId, errorDetails, payload);

            // Send to Slack payments channel
            slackAlertService.sendDLQFailureAlert(topic, messageId, errorDetails, payload);

            log.info("ACH DLQ failure alerts triggered: achId={}, messageId={}",
                event.getAchTransactionId(), messageId);

        } catch (Exception alertError) {
            log.error("CRITICAL: Failed to send ACH DLQ alerts - MANUAL INTERVENTION REQUIRED! " +
                "ACH Transaction: {}", event.getAchTransactionId(), alertError);
        }
    }
}