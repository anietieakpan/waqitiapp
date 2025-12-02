package com.waqiti.payment.kafka;

import com.waqiti.common.events.WireTransferEvent;
import com.waqiti.payment.domain.WireTransfer;
import com.waqiti.payment.repository.WireTransferRepository;
import com.waqiti.payment.service.WireTransferService;
import com.waqiti.payment.service.ComplianceScreeningService;
import com.waqiti.payment.service.SwiftMessageService;
import com.waqiti.payment.service.CurrencyExchangeService;
import com.waqiti.payment.service.CorrespondentBankService;
import com.waqiti.payment.service.AMLReviewService;
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
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class WireTransferEventsConsumer {
    
    private final WireTransferRepository wireTransferRepository;
    private final WireTransferService wireTransferService;
    private final ComplianceScreeningService complianceService;
    private final SwiftMessageService swiftService;
    private final CurrencyExchangeService exchangeService;
    private final CorrespondentBankService correspondentService;
    private final AMLReviewService amlService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100000.00");
    private static final BigDecimal SANCTIONS_SCREENING_THRESHOLD = new BigDecimal("3000.00");
    private static final int SWIFT_TIMEOUT_MINUTES = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    @KafkaListener(
        topics = {"wire-transfer-events", "international-wire-transfers", "domestic-wire-transfers"},
        groupId = "wire-transfer-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWireTransferEvent(
            @Payload WireTransferEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("wire-%s-p%d-o%d", 
            event.getWireTransferId(), partition, offset);
        
        log.info("Processing wire transfer: id={}, status={}, amount={}, beneficiaryBank={}, swiftCode={}",
            event.getWireTransferId(), event.getStatus(), event.getAmount(), 
            event.getBeneficiaryBank(), event.getSwiftCode());
        
        try {
            switch (event.getStatus()) {
                case "WIRE_INITIATED":
                    processWireInitiated(event, correlationId);
                    break;
                    
                case "COMPLIANCE_SCREENING":
                    processComplianceScreening(event, correlationId);
                    break;
                    
                case "AML_REVIEW":
                    processAMLReview(event, correlationId);
                    break;
                    
                case "CURRENCY_EXCHANGE":
                    processCurrencyExchange(event, correlationId);
                    break;
                    
                case "SWIFT_MESSAGE_SENT":
                    processSwiftMessageSent(event, correlationId);
                    break;
                    
                case "CORRESPONDENT_BANK_PROCESSING":
                    processCorrespondentBankProcessing(event, correlationId);
                    break;
                    
                case "BENEFICIARY_BANK_RECEIVED":
                    processBeneficiaryBankReceived(event, correlationId);
                    break;
                    
                case "FUNDS_DELIVERED":
                    processFundsDelivered(event, correlationId);
                    break;
                    
                case "WIRE_COMPLETED":
                    processWireCompleted(event, correlationId);
                    break;
                    
                case "WIRE_FAILED":
                    processWireFailed(event, correlationId);
                    break;
                    
                case "WIRE_RETURNED":
                    processWireReturned(event, correlationId);
                    break;
                    
                case "REGULATORY_HOLD":
                    processRegulatoryHold(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown wire transfer status: {}", event.getStatus());
                    break;
            }
            
            auditService.logPaymentEvent("WIRE_TRANSFER_PROCESSED", event.getWireTransferId(),
                Map.of("status", event.getStatus(), "amount", event.getAmount(),
                    "beneficiaryBank", event.getBeneficiaryBank(), "swiftCode", event.getSwiftCode(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process wire transfer: {}", e.getMessage(), e);
            kafkaTemplate.send("wire-transfer-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processWireInitiated(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = WireTransfer.builder()
            .wireTransferId(event.getWireTransferId())
            .senderId(event.getSenderId())
            .senderAccountId(event.getSenderAccountId())
            .beneficiaryName(event.getBeneficiaryName())
            .beneficiaryAccountNumber(event.getBeneficiaryAccountNumber())
            .beneficiaryBank(event.getBeneficiaryBank())
            .swiftCode(event.getSwiftCode())
            .routingNumber(event.getRoutingNumber())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .purposeCode(event.getPurposeCode())
            .instructions(event.getInstructions())
            .isUrgent(event.getIsUrgent())
            .status("WIRE_INITIATED")
            .initiatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        wireTransferRepository.save(wireTransfer);
        
        if (event.getAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            notificationService.sendNotification("COMPLIANCE_TEAM", "High Value Wire Transfer",
                String.format("Wire transfer %s for %s %s requires enhanced monitoring", 
                    event.getWireTransferId(), event.getAmount(), event.getCurrency()),
                correlationId);
        }
        
        kafkaTemplate.send("wire-transfer-events", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "status", "COMPLIANCE_SCREENING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordWireTransferInitiated(event.getCurrency(), event.getAmount());
        
        log.info("Wire transfer initiated: id={}, amount={}, beneficiary={}", 
            event.getWireTransferId(), event.getAmount(), event.getBeneficiaryName());
    }
    
    private void processComplianceScreening(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("COMPLIANCE_SCREENING");
        wireTransfer.setComplianceScreeningStartedAt(LocalDateTime.now());
        wireTransferRepository.save(wireTransfer);
        
        Map<String, Object> screeningResult = complianceService.performScreening(
            event.getSenderName(),
            event.getBeneficiaryName(),
            event.getSenderCountry(),
            event.getBeneficiaryCountry(),
            event.getAmount(),
            event.getPurposeCode()
        );
        
        boolean passed = (boolean) screeningResult.get("passed");
        String riskLevel = (String) screeningResult.get("riskLevel");
        
        wireTransfer.setComplianceScreeningResult(screeningResult);
        wireTransfer.setComplianceRiskLevel(riskLevel);
        
        if (!passed) {
            wireTransfer.setStatus("WIRE_FAILED");
            wireTransfer.setFailureReason("COMPLIANCE_SCREENING_FAILED");
            wireTransfer.setFailedAt(LocalDateTime.now());
            wireTransferRepository.save(wireTransfer);
            
            notificationService.sendNotification("COMPLIANCE_TEAM", "Wire Transfer Blocked",
                String.format("Wire transfer %s blocked due to compliance screening failure", 
                    event.getWireTransferId()),
                correlationId);
            
            kafkaTemplate.send("compliance-violation-alerts", Map.of(
                "wireTransferId", event.getWireTransferId(),
                "violationType", "SANCTIONS_SCREENING_FAILED",
                "riskLevel", riskLevel,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            return;
        }
        
        String nextStatus = "HIGH".equals(riskLevel) ? "AML_REVIEW" : 
                           event.getAmount().compareTo(SANCTIONS_SCREENING_THRESHOLD) >= 0 ? "AML_REVIEW" : 
                           "CURRENCY_EXCHANGE";
        
        wireTransfer.setStatus(nextStatus);
        wireTransferRepository.save(wireTransfer);
        
        kafkaTemplate.send("wire-transfer-events", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "status", nextStatus,
            "riskLevel", riskLevel,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Compliance screening completed: id={}, passed={}, riskLevel={}, nextStatus={}", 
            event.getWireTransferId(), passed, riskLevel, nextStatus);
    }
    
    private void processAMLReview(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("AML_REVIEW");
        wireTransfer.setAmlReviewStartedAt(LocalDateTime.now());
        wireTransferRepository.save(wireTransfer);
        
        Map<String, Object> amlResult = amlService.performAMLReview(
            event.getWireTransferId(),
            event.getSenderId(),
            event.getBeneficiaryName(),
            event.getAmount(),
            event.getPurposeCode()
        );
        
        boolean approved = (boolean) amlResult.get("approved");
        String reviewNotes = (String) amlResult.get("reviewNotes");
        
        wireTransfer.setAmlReviewResult(amlResult);
        wireTransfer.setAmlReviewNotes(reviewNotes);
        
        if (approved) {
            wireTransfer.setStatus("CURRENCY_EXCHANGE");
            wireTransfer.setAmlReviewCompletedAt(LocalDateTime.now());
            wireTransferRepository.save(wireTransfer);
            
            kafkaTemplate.send("wire-transfer-events", Map.of(
                "wireTransferId", event.getWireTransferId(),
                "status", "CURRENCY_EXCHANGE",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            wireTransfer.setStatus("REGULATORY_HOLD");
            wireTransfer.setHoldReason("AML_REVIEW_FAILED");
            wireTransfer.setExpectedReleaseDate(LocalDateTime.now().plusDays(5));
            wireTransferRepository.save(wireTransfer);
            
            notificationService.sendNotification("AML_TEAM", "Wire Transfer on Hold",
                String.format("Wire transfer %s placed on regulatory hold due to AML review", 
                    event.getWireTransferId()),
                correlationId);
            
            kafkaTemplate.send("regulatory-hold-alerts", Map.of(
                "wireTransferId", event.getWireTransferId(),
                "holdReason", "AML_REVIEW_FAILED",
                "expectedReleaseDate", wireTransfer.getExpectedReleaseDate(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        log.info("AML review completed: id={}, approved={}, notes={}", 
            event.getWireTransferId(), approved, reviewNotes);
    }
    
    private void processCurrencyExchange(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("CURRENCY_EXCHANGE");
        wireTransfer.setCurrencyExchangeStartedAt(LocalDateTime.now());
        wireTransferRepository.save(wireTransfer);
        
        if (!event.getCurrency().equals(event.getTargetCurrency())) {
            Map<String, Object> exchangeResult = exchangeService.performCurrencyExchange(
                event.getWireTransferId(),
                event.getCurrency(),
                event.getTargetCurrency(),
                event.getAmount()
            );
            
            BigDecimal exchangeRate = (BigDecimal) exchangeResult.get("exchangeRate");
            BigDecimal exchangedAmount = (BigDecimal) exchangeResult.get("exchangedAmount");
            BigDecimal exchangeFee = (BigDecimal) exchangeResult.get("exchangeFee");
            
            wireTransfer.setExchangeRate(exchangeRate);
            wireTransfer.setExchangedAmount(exchangedAmount);
            wireTransfer.setExchangeFee(exchangeFee);
            wireTransfer.setTargetCurrency(event.getTargetCurrency());
        } else {
            wireTransfer.setExchangedAmount(event.getAmount());
            wireTransfer.setTargetCurrency(event.getCurrency());
        }
        
        wireTransfer.setStatus("SWIFT_MESSAGE_SENT");
        wireTransfer.setCurrencyExchangeCompletedAt(LocalDateTime.now());
        wireTransferRepository.save(wireTransfer);
        
        kafkaTemplate.send("wire-transfer-events", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "status", "SWIFT_MESSAGE_SENT",
            "exchangedAmount", wireTransfer.getExchangedAmount(),
            "targetCurrency", wireTransfer.getTargetCurrency(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Currency exchange completed: id={}, originalAmount={}, exchangedAmount={}, rate={}", 
            event.getWireTransferId(), event.getAmount(), wireTransfer.getExchangedAmount(), 
            wireTransfer.getExchangeRate());
    }
    
    private void processSwiftMessageSent(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("SWIFT_MESSAGE_SENT");
        wireTransfer.setSwiftMessageSentAt(LocalDateTime.now());
        wireTransferRepository.save(wireTransfer);
        
        String swiftMessageId = swiftService.sendSwiftMessage(
            event.getWireTransferId(),
            event.getSwiftCode(),
            event.getBeneficiaryBank(),
            event.getBeneficiaryAccountNumber(),
            wireTransfer.getExchangedAmount(),
            wireTransfer.getTargetCurrency(),
            event.getInstructions()
        );
        
        wireTransfer.setSwiftMessageId(swiftMessageId);
        wireTransfer.setSwiftTimeout(LocalDateTime.now().plusMinutes(SWIFT_TIMEOUT_MINUTES));
        wireTransferRepository.save(wireTransfer);
        
        kafkaTemplate.send("wire-transfer-events", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "status", "CORRESPONDENT_BANK_PROCESSING",
            "swiftMessageId", swiftMessageId,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        kafkaTemplate.send("swift-message-tracking", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "swiftMessageId", swiftMessageId,
            "sentAt", wireTransfer.getSwiftMessageSentAt(),
            "timeout", wireTransfer.getSwiftTimeout(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("SWIFT message sent: id={}, swiftMessageId={}, swiftCode={}", 
            event.getWireTransferId(), swiftMessageId, event.getSwiftCode());
    }
    
    private void processCorrespondentBankProcessing(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("CORRESPONDENT_BANK_PROCESSING");
        wireTransfer.setCorrespondentBankProcessingAt(LocalDateTime.now());
        wireTransferRepository.save(wireTransfer);
        
        Map<String, Object> correspondentResult = correspondentService.processCorrespondentBankTransfer(
            event.getWireTransferId(),
            event.getSwiftCode(),
            wireTransfer.getSwiftMessageId()
        );
        
        String correspondentReference = (String) correspondentResult.get("correspondentReference");
        String routingPath = (String) correspondentResult.get("routingPath");
        
        wireTransfer.setCorrespondentReference(correspondentReference);
        wireTransfer.setRoutingPath(routingPath);
        wireTransferRepository.save(wireTransfer);
        
        kafkaTemplate.send("wire-transfer-events", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "status", "BENEFICIARY_BANK_RECEIVED",
            "correspondentReference", correspondentReference,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Correspondent bank processing: id={}, correspondentRef={}, routingPath={}", 
            event.getWireTransferId(), correspondentReference, routingPath);
    }
    
    private void processBeneficiaryBankReceived(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("BENEFICIARY_BANK_RECEIVED");
        wireTransfer.setBeneficiaryBankReceivedAt(LocalDateTime.now());
        wireTransfer.setBeneficiaryConfirmation(event.getBeneficiaryConfirmation());
        wireTransferRepository.save(wireTransfer);
        
        kafkaTemplate.send("wire-transfer-events", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "status", "FUNDS_DELIVERED",
            "beneficiaryConfirmation", event.getBeneficiaryConfirmation(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Beneficiary bank received: id={}, confirmation={}", 
            event.getWireTransferId(), event.getBeneficiaryConfirmation());
    }
    
    private void processFundsDelivered(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("FUNDS_DELIVERED");
        wireTransfer.setFundsDeliveredAt(LocalDateTime.now());
        wireTransfer.setDeliveryConfirmation(event.getDeliveryConfirmation());
        wireTransferRepository.save(wireTransfer);
        
        kafkaTemplate.send("wire-transfer-events", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "status", "WIRE_COMPLETED",
            "deliveryConfirmation", event.getDeliveryConfirmation(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Funds delivered: id={}, deliveryConfirmation={}", 
            event.getWireTransferId(), event.getDeliveryConfirmation());
    }
    
    private void processWireCompleted(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("WIRE_COMPLETED");
        wireTransfer.setCompletedAt(LocalDateTime.now());
        wireTransfer.setFinalConfirmationNumber(event.getFinalConfirmationNumber());
        wireTransferRepository.save(wireTransfer);
        
        notificationService.sendNotification(event.getSenderId(), "Wire Transfer Completed",
            String.format("Your wire transfer of %s %s to %s has been completed successfully", 
                wireTransfer.getExchangedAmount(), wireTransfer.getTargetCurrency(), 
                event.getBeneficiaryName()),
            correlationId);
        
        kafkaTemplate.send("ledger-recorded-events", Map.of(
            "transactionId", event.getWireTransferId(),
            "fromAccountId", event.getSenderAccountId(),
            "toAccountId", event.getBeneficiaryAccountNumber(),
            "amount", wireTransfer.getExchangedAmount(),
            "currency", wireTransfer.getTargetCurrency(),
            "transactionType", "WIRE_TRANSFER",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordWireTransferCompleted(
            wireTransfer.getTargetCurrency(), 
            wireTransfer.getExchangedAmount()
        );
        
        log.info("Wire transfer completed: id={}, finalAmount={}, confirmationNumber={}", 
            event.getWireTransferId(), wireTransfer.getExchangedAmount(), 
            event.getFinalConfirmationNumber());
    }
    
    private void processWireFailed(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("WIRE_FAILED");
        wireTransfer.setFailedAt(LocalDateTime.now());
        wireTransfer.setFailureReason(event.getFailureReason());
        wireTransfer.setErrorCode(event.getErrorCode());
        wireTransferRepository.save(wireTransfer);
        
        notificationService.sendNotification(event.getSenderId(), "Wire Transfer Failed",
            String.format("Your wire transfer of %s %s failed: %s", 
                event.getAmount(), event.getCurrency(), event.getFailureReason()),
            correlationId);
        
        notificationService.sendNotification("WIRE_TRANSFER_TEAM", "Wire Transfer Failure",
            String.format("Wire transfer %s failed: %s", event.getWireTransferId(), event.getFailureReason()),
            correlationId);
        
        if (event.getRetryCount() < MAX_RETRY_ATTEMPTS && "TEMPORARY_FAILURE".equals(event.getErrorCode())) {
            kafkaTemplate.send("wire-transfer-retry-queue", Map.of(
                "wireTransferId", event.getWireTransferId(),
                "retryCount", event.getRetryCount() + 1,
                "retryAfter", LocalDateTime.now().plusMinutes(30),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordWireTransferFailed(event.getCurrency(), event.getFailureReason());
        
        log.error("Wire transfer failed: id={}, reason={}, errorCode={}", 
            event.getWireTransferId(), event.getFailureReason(), event.getErrorCode());
    }
    
    private void processWireReturned(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("WIRE_RETURNED");
        wireTransfer.setReturnedAt(LocalDateTime.now());
        wireTransfer.setReturnReason(event.getReturnReason());
        wireTransfer.setReturnAmount(event.getReturnAmount());
        wireTransferRepository.save(wireTransfer);
        
        notificationService.sendNotification(event.getSenderId(), "Wire Transfer Returned",
            String.format("Your wire transfer has been returned. Amount: %s %s. Reason: %s", 
                event.getReturnAmount(), event.getCurrency(), event.getReturnReason()),
            correlationId);
        
        kafkaTemplate.send("ledger-recorded-events", Map.of(
            "transactionId", event.getWireTransferId() + "-RETURN",
            "fromAccountId", "WIRE_RETURNS",
            "toAccountId", event.getSenderAccountId(),
            "amount", event.getReturnAmount(),
            "currency", event.getCurrency(),
            "transactionType", "WIRE_RETURN",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordWireTransferReturned(event.getCurrency(), event.getReturnReason());
        
        log.warn("Wire transfer returned: id={}, returnAmount={}, reason={}", 
            event.getWireTransferId(), event.getReturnAmount(), event.getReturnReason());
    }
    
    private void processRegulatoryHold(WireTransferEvent event, String correlationId) {
        WireTransfer wireTransfer = wireTransferRepository.findByWireTransferId(event.getWireTransferId())
            .orElseThrow(() -> new RuntimeException("Wire transfer not found"));
        
        wireTransfer.setStatus("REGULATORY_HOLD");
        wireTransfer.setHoldStartedAt(LocalDateTime.now());
        wireTransfer.setHoldReason(event.getHoldReason());
        wireTransfer.setExpectedReleaseDate(event.getExpectedReleaseDate());
        wireTransferRepository.save(wireTransfer);
        
        notificationService.sendNotification(event.getSenderId(), "Wire Transfer on Hold",
            String.format("Your wire transfer has been placed on regulatory hold. Expected release: %s", 
                event.getExpectedReleaseDate()),
            correlationId);
        
        notificationService.sendNotification("COMPLIANCE_TEAM", "Wire Transfer Regulatory Hold",
            String.format("Wire transfer %s placed on regulatory hold: %s", 
                event.getWireTransferId(), event.getHoldReason()),
            correlationId);
        
        kafkaTemplate.send("regulatory-hold-queue", Map.of(
            "wireTransferId", event.getWireTransferId(),
            "holdReason", event.getHoldReason(),
            "expectedReleaseDate", event.getExpectedReleaseDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordWireTransferHold(event.getCurrency(), event.getHoldReason());
        
        log.warn("Wire transfer on regulatory hold: id={}, reason={}, expectedRelease={}", 
            event.getWireTransferId(), event.getHoldReason(), event.getExpectedReleaseDate());
    }
}