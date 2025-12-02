package com.waqiti.payment.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.payment.ChargebackEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.payment.domain.Chargeback;
import com.waqiti.payment.domain.ChargebackStatus;
import com.waqiti.payment.domain.ChargebackReason;
import com.waqiti.payment.domain.ChargebackStage;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.repository.ChargebackRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.ChargebackService;
import com.waqiti.payment.service.DisputeManagementService;
import com.waqiti.payment.service.EvidenceCollectionService;
import com.waqiti.payment.service.MerchantService;
import com.waqiti.payment.service.PaymentProviderService;
import com.waqiti.payment.service.ChargebackNotificationService;
import com.waqiti.common.exceptions.ChargebackProcessingException;

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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade consumer for chargeback processing events.
 * Handles comprehensive chargeback management including:
 * - Chargeback initiation and response
 * - Evidence collection and submission
 * - Liability assessment and shifting
 * - Provider-specific chargeback handling
 * - Win/loss tracking and analytics
 * - Merchant notifications and debits
 * 
 * Critical for dispute resolution and financial liability management.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargebackProcessingConsumer {

    private final ChargebackRepository chargebackRepository;
    private final PaymentRepository paymentRepository;
    private final ChargebackService chargebackService;
    private final DisputeManagementService disputeService;
    private final EvidenceCollectionService evidenceService;
    private final MerchantService merchantService;
    private final PaymentProviderService providerService;
    private final ChargebackNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final int CHARGEBACK_RESPONSE_DAYS = 10;
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("500");

    @KafkaListener(
        topics = "chargeback-notifications",
        groupId = "payment-service-chargeback-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        include = {ChargebackProcessingException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleChargebackNotification(
            @Payload ChargebackEvent chargebackEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "chargeback-priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = chargebackEvent.getEventId() != null ? 
            chargebackEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.warn("Processing chargeback notification: {} for payment: {} amount: {} reason: {}", 
                    eventId, chargebackEvent.getPaymentId(), 
                    chargebackEvent.getChargebackAmount(), chargebackEvent.getReasonCode());

            // Metrics tracking
            metricsService.incrementCounter("chargeback.processing.started",
                Map.of(
                    "reason_code", chargebackEvent.getReasonCode(),
                    "provider", chargebackEvent.getProvider()
                ));

            // Idempotency check
            if (isChargebackAlreadyProcessed(chargebackEvent.getProviderChargebackId(), eventId)) {
                log.info("Chargeback {} already processed", chargebackEvent.getProviderChargebackId());
                acknowledgment.acknowledge();
                return;
            }

            // Retrieve original payment
            Payment payment = getOriginalPayment(chargebackEvent.getPaymentId());

            // Create chargeback record
            Chargeback chargeback = createChargebackRecord(chargebackEvent, payment, eventId, correlationId);

            // Validate chargeback eligibility
            validateChargebackEligibility(chargeback, payment, chargebackEvent);

            // Assess liability
            assessLiability(chargeback, payment, chargebackEvent);

            // Collect evidence automatically
            collectEvidence(chargeback, payment, chargebackEvent);

            // Determine response strategy
            determineResponseStrategy(chargeback, chargebackEvent);

            // Execute chargeback response actions
            List<CompletableFuture<ChargebackActionResult>> actions = 
                executeChargebackActions(chargeback, payment, chargebackEvent);

            // Wait for critical actions to complete
            try {
                CompletableFuture.allOf(actions.toArray(new CompletableFuture[0]))
                    .get(2, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Chargeback action processing timed out after 2 minutes. Chargeback ID: {}", chargeback.getId(), e);
                actions.forEach(f -> f.cancel(true));
                throw new RuntimeException("Chargeback action processing timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Chargeback action processing execution failed. Chargeback ID: {}", chargeback.getId(), e.getCause());
                throw new RuntimeException("Chargeback action processing failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Chargeback action processing interrupted. Chargeback ID: {}", chargeback.getId(), e);
                throw new RuntimeException("Chargeback action processing interrupted", e);
            }

            // Process action results
            processActionResults(chargeback, actions);

            // Update chargeback status
            updateChargebackStatus(chargeback);

            // Save chargeback record
            Chargeback savedChargeback = chargebackRepository.save(chargeback);

            // Update payment status
            updatePaymentForChargeback(payment, savedChargeback);

            // Send notifications
            sendChargebackNotifications(savedChargeback, payment, chargebackEvent);

            // Update metrics
            updateChargebackMetrics(savedChargeback, chargebackEvent);

            // Create comprehensive audit trail
            createChargebackAuditLog(savedChargeback, payment, chargebackEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("chargeback.processing.success",
                Map.of(
                    "status", savedChargeback.getStatus().toString(),
                    "liability", savedChargeback.getLiabilityHolder()
                ));

            log.warn("Successfully processed chargeback: {} for payment: {} status: {} liability: {}", 
                    savedChargeback.getId(), payment.getId(), 
                    savedChargeback.getStatus(), savedChargeback.getLiabilityHolder());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing chargeback event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("chargeback.processing.error");
            
            auditLogger.logCriticalAlert("CHARGEBACK_PROCESSING_ERROR",
                "Critical chargeback processing failure",
                Map.of(
                    "paymentId", chargebackEvent.getPaymentId(),
                    "amount", chargebackEvent.getChargebackAmount().toString(),
                    "reasonCode", chargebackEvent.getReasonCode(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new ChargebackProcessingException("Failed to process chargeback: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "chargeback-urgent",
        groupId = "payment-service-urgent-chargeback-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUrgentChargeback(
            @Payload ChargebackEvent chargebackEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.error("URGENT CHARGEBACK: Processing high-priority chargeback for payment: {}", 
                    chargebackEvent.getPaymentId());

            // Fast-track high-value or time-sensitive chargebacks
            Chargeback chargeback = processUrgentChargeback(chargebackEvent, correlationId);

            // Immediate merchant notification
            notificationService.sendUrgentChargebackAlert(chargeback);

            // Auto-accept if no defense possible
            if (!chargeback.isDefensible()) {
                acceptChargeback(chargeback);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process urgent chargeback: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isChargebackAlreadyProcessed(String providerChargebackId, String eventId) {
        return chargebackRepository.existsByProviderChargebackIdOrEventId(providerChargebackId, eventId);
    }

    private Payment getOriginalPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ChargebackProcessingException("Payment not found: " + paymentId));
    }

    private Chargeback createChargebackRecord(ChargebackEvent event, Payment payment, String eventId, String correlationId) {
        return Chargeback.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .paymentId(payment.getId())
            .merchantId(payment.getMerchantId())
            .userId(payment.getUserId())
            .providerChargebackId(event.getProviderChargebackId())
            .provider(event.getProvider())
            .reasonCode(event.getReasonCode())
            .reason(ChargebackReason.fromCode(event.getReasonCode()))
            .chargebackAmount(event.getChargebackAmount())
            .originalAmount(payment.getAmount())
            .currency(payment.getCurrency())
            .stage(ChargebackStage.FIRST_CHARGEBACK)
            .status(ChargebackStatus.INITIATED)
            .receivedAt(event.getReceivedAt())
            .dueDate(calculateDueDate(event))
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void validateChargebackEligibility(Chargeback chargeback, Payment payment, ChargebackEvent event) {
        // Check if payment is eligible for chargeback
        if (!isPaymentChargebackEligible(payment)) {
            chargeback.setEligible(false);
            chargeback.setIneligibilityReason("Payment not eligible for chargeback");
            throw new ChargebackProcessingException("Payment not eligible for chargeback");
        }

        // Check chargeback time limits
        long daysSincePayment = ChronoUnit.DAYS.between(payment.getCreatedAt(), LocalDateTime.now());
        int chargebackTimeLimit = getChargebackTimeLimit(chargeback.getReason());
        
        if (daysSincePayment > chargebackTimeLimit) {
            chargeback.setEligible(false);
            chargeback.setIneligibilityReason("Chargeback time limit exceeded");
            log.warn("Chargeback time limit exceeded: {} days", daysSincePayment);
        }

        // Check for duplicate chargebacks
        if (chargebackRepository.existsByPaymentIdAndStatus(payment.getId(), ChargebackStatus.ACCEPTED)) {
            chargeback.setEligible(false);
            chargeback.setIneligibilityReason("Chargeback already processed for this payment");
        }

        chargeback.setEligible(true);
        chargeback.setEligibilityVerifiedAt(LocalDateTime.now());
    }

    private void assessLiability(Chargeback chargeback, Payment payment, ChargebackEvent event) {
        try {
            log.info("Assessing liability for chargeback: {}", chargeback.getId());

            // Check 3D Secure status
            if (payment.isThreeDSecure()) {
                chargeback.setLiabilityShift(true);
                chargeback.setLiabilityHolder("ISSUER");
                chargeback.setLiabilityReason("3D Secure authenticated - liability shift applies");
            } 
            // Check for fraud
            else if (chargeback.getReason() == ChargebackReason.FRAUD) {
                if (payment.getFraudScore() != null && payment.getFraudScore() > 80) {
                    chargeback.setLiabilityHolder("MERCHANT");
                    chargeback.setLiabilityReason("High fraud score - merchant liable");
                } else {
                    chargeback.setLiabilityHolder("PLATFORM");
                    chargeback.setLiabilityReason("Fraud not detected - platform liable");
                }
            }
            // Check for merchant error
            else if (isMerchantError(chargeback.getReason())) {
                chargeback.setLiabilityHolder("MERCHANT");
                chargeback.setLiabilityReason("Merchant error - merchant liable");
            }
            // Default liability
            else {
                chargeback.setLiabilityHolder("MERCHANT");
                chargeback.setLiabilityReason("Standard chargeback - merchant liable");
            }

            chargeback.setLiabilityAssessedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error assessing liability: {}", e.getMessage());
            chargeback.setLiabilityHolder("PLATFORM");
            chargeback.setLiabilityReason("Unable to determine liability - defaulting to platform");
        }
    }

    private void collectEvidence(Chargeback chargeback, Payment payment, ChargebackEvent event) {
        try {
            log.info("Collecting evidence for chargeback: {}", chargeback.getId());

            List<Map<String, Object>> evidence = new ArrayList<>();

            // Collect transaction evidence
            var transactionEvidence = evidenceService.collectTransactionEvidence(payment.getId());
            if (transactionEvidence != null) {
                evidence.add(Map.of(
                    "type", "TRANSACTION",
                    "data", transactionEvidence
                ));
            }

            // Collect delivery evidence
            if (payment.getMerchantId() != null) {
                var deliveryEvidence = evidenceService.collectDeliveryEvidence(
                    payment.getId(), 
                    payment.getMerchantId()
                );
                if (deliveryEvidence != null) {
                    evidence.add(Map.of(
                        "type", "DELIVERY",
                        "data", deliveryEvidence
                    ));
                }
            }

            // Collect customer communication
            var communicationEvidence = evidenceService.collectCustomerCommunication(
                payment.getUserId(),
                payment.getMerchantId()
            );
            if (communicationEvidence != null) {
                evidence.add(Map.of(
                    "type", "COMMUNICATION",
                    "data", communicationEvidence
                ));
            }

            // Collect usage evidence
            var usageEvidence = evidenceService.collectUsageEvidence(
                payment.getUserId(),
                payment.getCreatedAt()
            );
            if (usageEvidence != null) {
                evidence.add(Map.of(
                    "type", "USAGE",
                    "data", usageEvidence
                ));
            }

            chargeback.setEvidenceCollected(evidence);
            chargeback.setEvidenceCollectedAt(LocalDateTime.now());
            chargeback.setEvidenceScore(calculateEvidenceScore(evidence));

        } catch (Exception e) {
            log.error("Error collecting evidence: {}", e.getMessage());
            chargeback.setEvidenceCollectionError(e.getMessage());
        }
    }

    private void determineResponseStrategy(Chargeback chargeback, ChargebackEvent event) {
        // High-value chargebacks - always fight
        if (chargeback.getChargebackAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            chargeback.setResponseStrategy("FIGHT");
            chargeback.setDefensible(true);
        }
        // Strong evidence - fight
        else if (chargeback.getEvidenceScore() != null && chargeback.getEvidenceScore() > 70) {
            chargeback.setResponseStrategy("FIGHT");
            chargeback.setDefensible(true);
        }
        // Liability shifted - fight
        else if (chargeback.isLiabilityShift()) {
            chargeback.setResponseStrategy("FIGHT");
            chargeback.setDefensible(true);
        }
        // Low value with weak evidence - accept
        else if (chargeback.getChargebackAmount().compareTo(new BigDecimal("50")) < 0) {
            chargeback.setResponseStrategy("ACCEPT");
            chargeback.setDefensible(false);
        }
        // Default - evaluate case by case
        else {
            chargeback.setResponseStrategy("EVALUATE");
            chargeback.setDefensible(chargeback.getEvidenceScore() != null && chargeback.getEvidenceScore() > 50);
        }

        chargeback.setStrategyDeterminedAt(LocalDateTime.now());
    }

    private List<CompletableFuture<ChargebackActionResult>> executeChargebackActions(
            Chargeback chargeback, Payment payment, ChargebackEvent event) {
        
        List<CompletableFuture<ChargebackActionResult>> actions = new ArrayList<>();

        // Submit response to provider if fighting
        if ("FIGHT".equals(chargeback.getResponseStrategy())) {
            actions.add(CompletableFuture.supplyAsync(() -> 
                submitChargebackResponse(chargeback, payment)));
        }

        // Debit merchant account
        if ("MERCHANT".equals(chargeback.getLiabilityHolder())) {
            actions.add(CompletableFuture.supplyAsync(() -> 
                debitMerchantAccount(chargeback, payment)));
        }

        // Hold funds
        actions.add(CompletableFuture.supplyAsync(() -> 
            holdFunds(chargeback, payment)));

        // Update risk profile
        actions.add(CompletableFuture.supplyAsync(() -> 
            updateRiskProfile(chargeback, payment)));

        // Create dispute case
        if (chargeback.isDefensible()) {
            actions.add(CompletableFuture.supplyAsync(() -> 
                createDisputeCase(chargeback, payment)));
        }

        return actions;
    }

    private ChargebackActionResult submitChargebackResponse(Chargeback chargeback, Payment payment) {
        try {
            log.info("Submitting chargeback response for: {}", chargeback.getId());

            var response = providerService.submitChargebackResponse(
                chargeback.getProvider(),
                chargeback.getProviderChargebackId(),
                chargeback.getEvidenceCollected(),
                chargeback.getResponseStrategy()
            );

            chargeback.setResponseSubmitted(true);
            chargeback.setResponseSubmittedAt(LocalDateTime.now());
            chargeback.setProviderResponseId(response.getResponseId());
            chargeback.setProviderResponseStatus(response.getStatus());

            return ChargebackActionResult.success("SUBMIT_RESPONSE", response.getResponseId());

        } catch (Exception e) {
            log.error("Failed to submit chargeback response: {}", e.getMessage());
            chargeback.setResponseError(e.getMessage());
            return ChargebackActionResult.failure("SUBMIT_RESPONSE", e.getMessage());
        }
    }

    private ChargebackActionResult debitMerchantAccount(Chargeback chargeback, Payment payment) {
        try {
            log.info("Debiting merchant account for chargeback: {}", chargeback.getId());

            var debitResult = merchantService.debitForChargeback(
                payment.getMerchantId(),
                chargeback.getChargebackAmount(),
                chargeback.getId()
            );

            chargeback.setMerchantDebited(true);
            chargeback.setMerchantDebitAmount(chargeback.getChargebackAmount());
            chargeback.setMerchantDebitedAt(LocalDateTime.now());
            chargeback.setMerchantDebitTransactionId(debitResult.getTransactionId());

            return ChargebackActionResult.success("DEBIT_MERCHANT", debitResult.getTransactionId());

        } catch (Exception e) {
            log.error("Failed to debit merchant account: {}", e.getMessage());
            chargeback.setMerchantDebitError(e.getMessage());
            return ChargebackActionResult.failure("DEBIT_MERCHANT", e.getMessage());
        }
    }

    private ChargebackActionResult holdFunds(Chargeback chargeback, Payment payment) {
        try {
            log.info("Holding funds for chargeback: {}", chargeback.getId());

            BigDecimal holdAmount = chargeback.getChargebackAmount();
            
            // Add chargeback fee
            BigDecimal chargebackFee = calculateChargebackFee(chargeback);
            holdAmount = holdAmount.add(chargebackFee);
            chargeback.setChargebackFee(chargebackFee);

            var holdResult = merchantService.holdFunds(
                payment.getMerchantId(),
                holdAmount,
                chargeback.getId()
            );

            chargeback.setFundsHeld(true);
            chargeback.setFundsHeldAmount(holdAmount);
            chargeback.setFundsHeldAt(LocalDateTime.now());

            return ChargebackActionResult.success("HOLD_FUNDS", holdAmount);

        } catch (Exception e) {
            log.error("Failed to hold funds: {}", e.getMessage());
            return ChargebackActionResult.failure("HOLD_FUNDS", e.getMessage());
        }
    }

    private ChargebackActionResult updateRiskProfile(Chargeback chargeback, Payment payment) {
        try {
            log.info("Updating risk profiles for chargeback: {}", chargeback.getId());

            // Update merchant risk profile
            merchantService.updateChargebackRate(payment.getMerchantId(), chargeback);

            // Update user risk profile
            chargebackService.updateUserChargebackHistory(payment.getUserId(), chargeback);

            return ChargebackActionResult.success("UPDATE_RISK", "Risk profiles updated");

        } catch (Exception e) {
            log.error("Failed to update risk profiles: {}", e.getMessage());
            return ChargebackActionResult.failure("UPDATE_RISK", e.getMessage());
        }
    }

    private ChargebackActionResult createDisputeCase(Chargeback chargeback, Payment payment) {
        try {
            log.info("Creating dispute case for chargeback: {}", chargeback.getId());

            var disputeCase = disputeService.createChargebackDispute(
                chargeback.getId(),
                payment.getId(),
                chargeback.getChargebackAmount(),
                chargeback.getReason().toString()
            );

            chargeback.setDisputeCaseId(disputeCase.getCaseId());
            chargeback.setDisputeCaseCreatedAt(LocalDateTime.now());

            return ChargebackActionResult.success("CREATE_DISPUTE", disputeCase.getCaseId());

        } catch (Exception e) {
            log.error("Failed to create dispute case: {}", e.getMessage());
            return ChargebackActionResult.failure("CREATE_DISPUTE", e.getMessage());
        }
    }

    private void processActionResults(Chargeback chargeback, List<CompletableFuture<ChargebackActionResult>> actions) {
        int successfulActions = 0;
        List<String> failedActions = new ArrayList<>();

        for (CompletableFuture<ChargebackActionResult> action : actions) {
            try {
                ChargebackActionResult result = action.get();
                if (result.isSuccess()) {
                    successfulActions++;
                } else {
                    failedActions.add(result.getAction() + ": " + result.getError());
                }
            } catch (Exception e) {
                log.error("Failed to process action result: {}", e.getMessage());
                failedActions.add("UNKNOWN: " + e.getMessage());
            }
        }

        chargeback.setSuccessfulActions(successfulActions);
        chargeback.setFailedActions(failedActions);
    }

    private void updateChargebackStatus(Chargeback chargeback) {
        if (chargeback.isResponseSubmitted() && chargeback.isDefensible()) {
            chargeback.setStatus(ChargebackStatus.DISPUTED);
        } else if (!chargeback.isDefensible() || "ACCEPT".equals(chargeback.getResponseStrategy())) {
            chargeback.setStatus(ChargebackStatus.ACCEPTED);
            chargeback.setAcceptedAt(LocalDateTime.now());
        } else if (chargeback.getResponseError() != null) {
            chargeback.setStatus(ChargebackStatus.RESPONSE_FAILED);
        } else {
            chargeback.setStatus(ChargebackStatus.PENDING_RESPONSE);
        }

        chargeback.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(chargeback.getCreatedAt(), LocalDateTime.now())
        );
        chargeback.setUpdatedAt(LocalDateTime.now());
    }

    private void updatePaymentForChargeback(Payment payment, Chargeback chargeback) {
        payment.setHasChargeback(true);
        payment.setChargebackId(chargeback.getId());
        payment.setChargebackStatus(chargeback.getStatus().toString());
        payment.setChargebackAmount(chargeback.getChargebackAmount());
        payment.setUpdatedAt(LocalDateTime.now());
        
        if (chargeback.getStatus() == ChargebackStatus.ACCEPTED) {
            payment.setStatus("CHARGEBACK_LOST");
        } else if (chargeback.getStatus() == ChargebackStatus.DISPUTED) {
            payment.setStatus("CHARGEBACK_DISPUTED");
        }
        
        paymentRepository.save(payment);
    }

    private void sendChargebackNotifications(Chargeback chargeback, Payment payment, ChargebackEvent event) {
        try {
            // Notify merchant
            notificationService.sendMerchantChargebackNotification(chargeback, payment);

            // Notify user if applicable
            if (shouldNotifyUser(chargeback)) {
                notificationService.sendUserChargebackNotification(chargeback, payment);
            }

            // High-value alert
            if (chargeback.getChargebackAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
                notificationService.sendHighValueChargebackAlert(chargeback);
            }

            // Response deadline reminder
            if (chargeback.getDueDate() != null) {
                notificationService.scheduleResponseDeadlineReminder(chargeback);
            }

        } catch (Exception e) {
            log.error("Failed to send chargeback notifications: {}", e.getMessage());
        }
    }

    private void updateChargebackMetrics(Chargeback chargeback, ChargebackEvent event) {
        try {
            // Chargeback metrics
            metricsService.incrementCounter("chargeback.received",
                Map.of(
                    "reason", chargeback.getReason().toString(),
                    "provider", chargeback.getProvider(),
                    "liability", chargeback.getLiabilityHolder()
                ));

            // Amount metrics
            metricsService.recordTimer("chargeback.amount", 
                chargeback.getChargebackAmount().doubleValue(),
                Map.of("currency", chargeback.getCurrency()));

            // Response metrics
            if (chargeback.isResponseSubmitted()) {
                metricsService.incrementCounter("chargeback.response.submitted",
                    Map.of("strategy", chargeback.getResponseStrategy()));
            }

            // Processing time
            metricsService.recordTimer("chargeback.processing_time", 
                chargeback.getProcessingTimeMs(),
                Map.of("status", chargeback.getStatus().toString()));

        } catch (Exception e) {
            log.error("Failed to update chargeback metrics: {}", e.getMessage());
        }
    }

    private void createChargebackAuditLog(Chargeback chargeback, Payment payment, ChargebackEvent event, String correlationId) {
        auditLogger.logFinancialEvent(
            "CHARGEBACK_PROCESSED",
            payment.getUserId(),
            chargeback.getId(),
            chargeback.getReason().toString(),
            chargeback.getChargebackAmount().doubleValue(),
            "chargeback_processor",
            chargeback.getStatus() != ChargebackStatus.ACCEPTED,
            Map.of(
                "chargebackId", chargeback.getId(),
                "paymentId", payment.getId(),
                "providerChargebackId", chargeback.getProviderChargebackId(),
                "reason", chargeback.getReason().toString(),
                "status", chargeback.getStatus().toString(),
                "amount", chargeback.getChargebackAmount().toString(),
                "liability", chargeback.getLiabilityHolder(),
                "defensible", String.valueOf(chargeback.isDefensible()),
                "responseStrategy", chargeback.getResponseStrategy(),
                "evidenceScore", String.valueOf(chargeback.getEvidenceScore()),
                "processingTimeMs", String.valueOf(chargeback.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private Chargeback processUrgentChargeback(ChargebackEvent event, String correlationId) {
        Payment payment = getOriginalPayment(event.getPaymentId());
        Chargeback chargeback = createChargebackRecord(event, payment, UUID.randomUUID().toString(), correlationId);
        
        // Quick assessment
        chargeback.setDefensible(false);
        chargeback.setResponseStrategy("ACCEPT");
        chargeback.setStatus(ChargebackStatus.ACCEPTED);
        chargeback.setAcceptedAt(LocalDateTime.now());
        
        // Immediate merchant debit
        chargeback.setLiabilityHolder("MERCHANT");
        debitMerchantAccount(chargeback, payment);
        
        return chargebackRepository.save(chargeback);
    }

    private void acceptChargeback(Chargeback chargeback) {
        chargeback.setStatus(ChargebackStatus.ACCEPTED);
        chargeback.setAcceptedAt(LocalDateTime.now());
        chargeback.setAcceptanceReason("Auto-accepted - no viable defense");
        chargebackRepository.save(chargeback);
    }

    private boolean isPaymentChargebackEligible(Payment payment) {
        return "COMPLETED".equals(payment.getStatus()) || 
               "SETTLED".equals(payment.getStatus());
    }

    private int getChargebackTimeLimit(ChargebackReason reason) {
        return switch (reason) {
            case FRAUD -> 120; // 120 days for fraud
            case AUTHORIZATION -> 90; // 90 days for authorization issues
            case PROCESSING_ERROR -> 120; // 120 days for processing errors
            case CONSUMER_DISPUTE -> 540; // 540 days for consumer disputes
            default -> 120; // Default 120 days
        };
    }

    private boolean isMerchantError(ChargebackReason reason) {
        return reason == ChargebackReason.PRODUCT_NOT_RECEIVED ||
               reason == ChargebackReason.PRODUCT_UNACCEPTABLE ||
               reason == ChargebackReason.DUPLICATE_PROCESSING ||
               reason == ChargebackReason.CREDIT_NOT_PROCESSED;
    }

    private double calculateEvidenceScore(List<Map<String, Object>> evidence) {
        if (evidence.isEmpty()) return 0.0;
        
        double score = 0.0;
        for (Map<String, Object> item : evidence) {
            String type = (String) item.get("type");
            score += switch (type) {
                case "TRANSACTION" -> 25.0;
                case "DELIVERY" -> 30.0;
                case "COMMUNICATION" -> 20.0;
                case "USAGE" -> 25.0;
                default -> 10.0;
            };
        }
        
        return Math.min(100.0, score);
    }

    private LocalDateTime calculateDueDate(ChargebackEvent event) {
        if (event.getDueDate() != null) {
            return event.getDueDate();
        }
        return LocalDateTime.now().plusDays(CHARGEBACK_RESPONSE_DAYS);
    }

    private BigDecimal calculateChargebackFee(Chargeback chargeback) {
        // Standard chargeback fee
        return new BigDecimal("15.00");
    }

    private boolean shouldNotifyUser(Chargeback chargeback) {
        // Notify user for fraud-related chargebacks
        return chargeback.getReason() == ChargebackReason.FRAUD ||
               chargeback.getReason() == ChargebackReason.AUTHORIZATION;
    }

    /**
     * Internal class for chargeback action results
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ChargebackActionResult {
        private String action;
        private boolean success;
        private Object result;
        private String error;

        public static ChargebackActionResult success(String action, Object result) {
            return new ChargebackActionResult(action, true, result, null);
        }

        public static ChargebackActionResult failure(String action, String error) {
            return new ChargebackActionResult(action, false, null, error);
        }
    }
}