package com.waqiti.payment.kafka;

import com.waqiti.common.events.ChargebackAlertEvent;
import com.waqiti.payment.domain.ChargebackAlert;
import com.waqiti.payment.repository.ChargebackAlertRepository;
import com.waqiti.payment.service.ChargebackManagementService;
import com.waqiti.payment.service.DisputeResolutionService;
import com.waqiti.payment.service.MerchantRiskService;
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
public class ChargebackAlertsConsumer {
    
    private final ChargebackAlertRepository alertRepository;
    private final ChargebackManagementService chargebackService;
    private final DisputeResolutionService disputeService;
    private final MerchantRiskService riskService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");
    private static final int RESPONSE_DEADLINE_DAYS = 10;
    
    @KafkaListener(
        topics = {"chargeback-alerts", "dispute-notifications", "payment-disputes"},
        groupId = "chargeback-alerts-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleChargebackAlert(
            @Payload ChargebackAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("chargeback-%s-p%d-o%d", 
            event.getChargebackId(), partition, offset);
        
        log.warn("Processing chargeback alert: id={}, paymentId={}, amount={}, reasonCode={}",
            event.getChargebackId(), event.getPaymentId(), event.getAmount(), event.getReasonCode());
        
        try {
            switch (event.getStatus()) {
                case "CHARGEBACK_RECEIVED":
                    processChargebackReceived(event, correlationId);
                    break;
                    
                case "EVIDENCE_COLLECTION":
                    collectEvidence(event, correlationId);
                    break;
                    
                case "RESPONSE_SUBMITTED":
                    submitResponse(event, correlationId);
                    break;
                    
                case "CHARGEBACK_WON":
                    handleChargebackWon(event, correlationId);
                    break;
                    
                case "CHARGEBACK_LOST":
                    handleChargebackLost(event, correlationId);
                    break;
                    
                case "PRE_ARBITRATION":
                    handlePreArbitration(event, correlationId);
                    break;
                    
                case "ARBITRATION_FILED":
                    handleArbitrationFiled(event, correlationId);
                    break;
                    
                case "CHARGEBACK_CLOSED":
                    closeChargeback(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown chargeback status: {}", event.getStatus());
                    break;
            }
            
            auditService.logPaymentEvent("CHARGEBACK_ALERT_PROCESSED", event.getChargebackId(),
                Map.of("paymentId", event.getPaymentId(), "status", event.getStatus(),
                    "amount", event.getAmount(), "reasonCode", event.getReasonCode(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process chargeback alert: {}", e.getMessage(), e);
            kafkaTemplate.send("chargeback-alerts-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processChargebackReceived(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = ChargebackAlert.builder()
            .chargebackId(event.getChargebackId())
            .paymentId(event.getPaymentId())
            .merchantId(event.getMerchantId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .reasonCode(event.getReasonCode())
            .status("CHARGEBACK_RECEIVED")
            .receivedAt(LocalDateTime.now())
            .responseDeadline(LocalDateTime.now().plusDays(RESPONSE_DEADLINE_DAYS))
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);
        
        int riskScore = riskService.calculateChargebackRisk(
            event.getMerchantId(),
            event.getPaymentId(),
            event.getReasonCode()
        );
        
        alert.setRiskScore(riskScore);
        alertRepository.save(alert);
        
        notificationService.sendNotification("DISPUTE_TEAM", "Chargeback Received",
            String.format("Chargeback %s received for payment %s. Amount: %s %s. Reason: %s. Deadline: %d days", 
                event.getChargebackId(), event.getPaymentId(), event.getAmount(), 
                event.getCurrency(), event.getReasonCode(), RESPONSE_DEADLINE_DAYS),
            correlationId);
        
        if (event.getAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            notificationService.sendNotification("DISPUTE_MANAGER", "High Value Chargeback",
                String.format("High value chargeback %s: %s %s requires immediate attention", 
                    event.getChargebackId(), event.getAmount(), event.getCurrency()),
                correlationId);
        }
        
        kafkaTemplate.send("chargeback-alerts", Map.of(
            "chargebackId", event.getChargebackId(),
            "paymentId", event.getPaymentId(),
            "status", "EVIDENCE_COLLECTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        chargebackService.freezeMerchantFunds(event.getMerchantId(), event.getAmount());
        
        metricsService.recordChargebackReceived(event.getReasonCode(), event.getAmount());
        
        log.error("Chargeback received: id={}, paymentId={}, amount={}, reasonCode={}", 
            event.getChargebackId(), event.getPaymentId(), event.getAmount(), event.getReasonCode());
    }
    
    private void collectEvidence(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = alertRepository.findByChargebackId(event.getChargebackId())
            .orElseThrow(() -> new RuntimeException("Chargeback alert not found"));
        
        alert.setStatus("EVIDENCE_COLLECTION");
        alert.setEvidenceCollectionStartedAt(LocalDateTime.now());
        alertRepository.save(alert);
        
        Map<String, Object> evidence = disputeService.collectEvidence(
            event.getPaymentId(),
            event.getReasonCode()
        );
        
        alert.setEvidenceDocuments((List<String>) evidence.get("documents"));
        alert.setEvidenceCount(alert.getEvidenceDocuments().size());
        alertRepository.save(alert);
        
        disputeService.analyzeWinProbability(event.getChargebackId(), evidence);
        
        metricsService.recordEvidenceCollected(event.getReasonCode(), alert.getEvidenceCount());
        
        log.info("Evidence collection started: chargebackId={}, evidenceCount={}", 
            event.getChargebackId(), alert.getEvidenceCount());
    }
    
    private void submitResponse(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = alertRepository.findByChargebackId(event.getChargebackId())
            .orElseThrow(() -> new RuntimeException("Chargeback alert not found"));
        
        alert.setStatus("RESPONSE_SUBMITTED");
        alert.setResponseSubmittedAt(LocalDateTime.now());
        alert.setResponseStrategy(event.getResponseStrategy());
        alertRepository.save(alert);
        
        String responseId = disputeService.submitChargebackResponse(
            event.getChargebackId(),
            event.getResponseStrategy(),
            alert.getEvidenceDocuments()
        );
        
        alert.setResponseId(responseId);
        alertRepository.save(alert);
        
        notificationService.sendNotification("DISPUTE_TEAM", "Chargeback Response Submitted",
            String.format("Response submitted for chargeback %s. Response ID: %s", 
                event.getChargebackId(), responseId),
            correlationId);
        
        metricsService.recordChargebackResponseSubmitted(event.getReasonCode());
        
        log.info("Chargeback response submitted: id={}, responseId={}, strategy={}", 
            event.getChargebackId(), responseId, event.getResponseStrategy());
    }
    
    private void handleChargebackWon(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = alertRepository.findByChargebackId(event.getChargebackId())
            .orElseThrow(() -> new RuntimeException("Chargeback alert not found"));
        
        alert.setStatus("CHARGEBACK_WON");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setOutcome("WON");
        alertRepository.save(alert);
        
        chargebackService.releaseMerchantFunds(event.getMerchantId(), event.getAmount());
        chargebackService.restoreMerchantRevenue(event.getPaymentId(), event.getAmount());
        
        notificationService.sendNotification("DISPUTE_TEAM", "Chargeback Won",
            String.format("Chargeback %s successfully disputed. Funds restored: %s %s", 
                event.getChargebackId(), event.getAmount(), event.getCurrency()),
            correlationId);
        
        riskService.updateMerchantChargebackProfile(event.getMerchantId(), "WON");
        
        metricsService.recordChargebackWon(event.getReasonCode(), event.getAmount());
        
        log.info("Chargeback won: id={}, amount={} restored", event.getChargebackId(), event.getAmount());
    }
    
    private void handleChargebackLost(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = alertRepository.findByChargebackId(event.getChargebackId())
            .orElseThrow(() -> new RuntimeException("Chargeback alert not found"));
        
        alert.setStatus("CHARGEBACK_LOST");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setOutcome("LOST");
        alertRepository.save(alert);
        
        chargebackService.processChargebackDebit(event.getMerchantId(), event.getAmount());
        chargebackService.assessChargebackFee(event.getMerchantId(), event.getChargebackId());
        
        notificationService.sendNotification("DISPUTE_TEAM", "Chargeback Lost",
            String.format("Chargeback %s lost. Amount debited: %s %s", 
                event.getChargebackId(), event.getAmount(), event.getCurrency()),
            correlationId);
        
        riskService.updateMerchantChargebackProfile(event.getMerchantId(), "LOST");
        
        if (riskService.exceedsChargebackThreshold(event.getMerchantId())) {
            kafkaTemplate.send("merchant-risk-alerts", Map.of(
                "merchantId", event.getMerchantId(),
                "alertType", "CHARGEBACK_THRESHOLD_EXCEEDED",
                "chargebackId", event.getChargebackId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordChargebackLost(event.getReasonCode(), event.getAmount());
        
        log.error("Chargeback lost: id={}, amount={} debited", event.getChargebackId(), event.getAmount());
    }
    
    private void handlePreArbitration(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = alertRepository.findByChargebackId(event.getChargebackId())
            .orElseThrow(() -> new RuntimeException("Chargeback alert not found"));
        
        alert.setStatus("PRE_ARBITRATION");
        alert.setPreArbitrationAt(LocalDateTime.now());
        alertRepository.save(alert);
        
        notificationService.sendNotification("DISPUTE_MANAGER", "Pre-Arbitration Initiated",
            String.format("Pre-arbitration for chargeback %s. Escalation required for %s %s", 
                event.getChargebackId(), event.getAmount(), event.getCurrency()),
            correlationId);
        
        boolean shouldProceed = disputeService.evaluateArbitrationViability(
            event.getChargebackId(),
            event.getAmount()
        );
        
        if (shouldProceed) {
            kafkaTemplate.send("chargeback-alerts", Map.of(
                "chargebackId", event.getChargebackId(),
                "status", "ARBITRATION_FILED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("chargeback-alerts", Map.of(
                "chargebackId", event.getChargebackId(),
                "status", "CHARGEBACK_CLOSED",
                "outcome", "ACCEPTED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordPreArbitration(event.getReasonCode());
        
        log.warn("Pre-arbitration initiated: chargebackId={}, shouldProceed={}", 
            event.getChargebackId(), shouldProceed);
    }
    
    private void handleArbitrationFiled(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = alertRepository.findByChargebackId(event.getChargebackId())
            .orElseThrow(() -> new RuntimeException("Chargeback alert not found"));
        
        alert.setStatus("ARBITRATION_FILED");
        alert.setArbitrationFiledAt(LocalDateTime.now());
        alertRepository.save(alert);
        
        String arbitrationCaseId = disputeService.fileArbitration(
            event.getChargebackId(),
            event.getAmount()
        );
        
        alert.setArbitrationCaseId(arbitrationCaseId);
        alertRepository.save(alert);
        
        notificationService.sendNotification("LEGAL_TEAM", "Arbitration Filed",
            String.format("Arbitration filed for chargeback %s. Case ID: %s. Amount: %s %s", 
                event.getChargebackId(), arbitrationCaseId, event.getAmount(), event.getCurrency()),
            correlationId);
        
        metricsService.recordArbitrationFiled(event.getReasonCode(), event.getAmount());
        
        log.error("Arbitration filed: chargebackId={}, caseId={}", 
            event.getChargebackId(), arbitrationCaseId);
    }
    
    private void closeChargeback(ChargebackAlertEvent event, String correlationId) {
        ChargebackAlert alert = alertRepository.findByChargebackId(event.getChargebackId())
            .orElseThrow(() -> new RuntimeException("Chargeback alert not found"));
        
        alert.setStatus("CHARGEBACK_CLOSED");
        alert.setClosedAt(LocalDateTime.now());
        alert.setFinalOutcome(event.getFinalOutcome());
        alertRepository.save(alert);
        
        disputeService.archiveChargebackCase(event.getChargebackId());
        
        metricsService.recordChargebackClosed(event.getReasonCode(), event.getFinalOutcome());
        
        log.info("Chargeback closed: id={}, finalOutcome={}", 
            event.getChargebackId(), event.getFinalOutcome());
    }
}