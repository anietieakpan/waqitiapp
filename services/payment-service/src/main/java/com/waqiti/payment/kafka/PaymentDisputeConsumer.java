package com.waqiti.payment.kafka;

import com.waqiti.payment.dto.PaymentDisputeEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Payment dispute consumer - handles customer payment disputes.
 * Critical for maintaining customer trust and handling chargebacks properly.
 * This was one of the orphaned events identified in the architecture analysis.
 */
@Slf4j
@Component
public class PaymentDisputeConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final RedisIdempotencyService idempotencyService;
    
    // Active disputes tracking
    private final Map<String, DisputeCase> activeDisputes = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter disputesReceived;
    private final Counter disputesResolved;
    private final Counter disputesEscalated;
    private final Counter fundsHeld;
    private final Counter refundsIssued;
    private final Timer disputeProcessingTimer;
    
    // Configuration
    @Value("${dispute.auto-hold-funds:true}")
    private boolean autoHoldFunds;
    
    @Value("${dispute.auto-resolve.enabled:false}")
    private boolean autoResolveEnabled;
    
    @Value("${dispute.auto-resolve.max-amount:100}")
    private BigDecimal autoResolveMaxAmount;
    
    @Value("${dispute.fraud.freeze-account:true}")
    private boolean freezeAccountOnFraud;
    
    @Value("${dispute.response.deadline.days:7}")
    private int responseDeadlineDays;
    
    public PaymentDisputeConsumer(
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            RefundService refundService,
            KafkaTemplate<String, Object> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry,
            RedisIdempotencyService idempotencyService) {

        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.idempotencyService = idempotencyService;
        
        // Initialize metrics
        this.disputesReceived = Counter.builder("payment.disputes.received")
            .description("Total payment disputes received")
            .register(meterRegistry);
        
        this.disputesResolved = Counter.builder("payment.disputes.resolved")
            .description("Total disputes resolved")
            .register(meterRegistry);
        
        this.disputesEscalated = Counter.builder("payment.disputes.escalated")
            .description("Total disputes escalated")
            .register(meterRegistry);
        
        this.fundsHeld = Counter.builder("payment.disputes.funds.held")
            .description("Total times funds were held")
            .register(meterRegistry);
        
        this.refundsIssued = Counter.builder("payment.disputes.refunds.issued")
            .description("Total refunds issued from disputes")
            .register(meterRegistry);
        
        this.disputeProcessingTimer = Timer.builder("payment.dispute.processing.time")
            .description("Payment dispute processing time")
            .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "payment-disputes",
        groupId = "payment-service-dispute-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "2"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 45, rollbackFor = Exception.class)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handlePaymentDispute(
            @Valid @Payload PaymentDisputeEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId() != null ?
            event.getCorrelationId() : UUID.randomUUID().toString();

        // Build idempotency key
        String idempotencyKey = idempotencyService.buildIdempotencyKey(
            "payment-service",
            "PaymentDisputeEvent",
            event.getDisputeId()
        );

        log.warn("Processing payment dispute: correlationId={}, disputeId={}, " +
                "transactionId={}, amount={}, type={}, reason={}, priority={}",
                correlationId, event.getDisputeId(), event.getTransactionId(),
                event.getDisputeAmount(), event.getDisputeType(),
                event.getDisputeReason(), event.getPriority());

        DisputeCase disputeCase = null;

        try {
            // Universal idempotency check (30-day TTL for disputes)
            if (idempotencyService.isProcessed(idempotencyKey)) {
                log.warn("⏭️ Duplicate dispute detected, skipping: disputeId={}", event.getDisputeId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Fetch payment
            Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found: " + event.getPaymentId()));
            
            // Create dispute case
            disputeCase = createDisputeCase(event, payment, correlationId);
            
            // Validate dispute eligibility
            DisputeValidation validation = validateDispute(event, payment);
            if (!validation.isValid()) {
                rejectDispute(event, validation.getReason());
                acknowledgment.acknowledge();
                return;
            }
            
            // Determine dispute response
            DisputeResponse response = determineDisputeResponse(event, payment, validation);
            
            // Execute dispute actions
            List<DisputeAction> actions = executeDisputeActions(event, payment, response, disputeCase);
            
            // Handle auto-resolution if applicable
            if (response.canAutoResolve() && autoResolveEnabled) {
                autoResolveDispute(event, payment, response, disputeCase);
            } else if (response.requiresInvestigation()) {
                createInvestigationCase(event, payment, response, disputeCase);
            }
            
            // Send notifications
            sendDisputeNotifications(event, payment, response, disputeCase);
            
            // Publish dispute event for other services
            publishDisputeEvent(event, payment, response, actions);
            
            // Update metrics
            updateDisputeMetrics(event, response);
            
            // Update dispute case status
            disputeCase.setStatus(DisputeCase.Status.PROCESSING);
            disputeCase.setActions(actions);
            disputeCase.setLastUpdated(LocalDateTime.now());

            // Mark as processed (30-day TTL for financial disputes)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed payment dispute: correlationId={}, " +
                    "disputeId={}, response={}, actions={}", 
                    correlationId, event.getDisputeId(), response.getDecision(), actions.size());
            
        } catch (Exception e) {
            log.error("Error processing payment dispute: correlationId={}, disputeId={}, error={}", 
                    correlationId, event.getDisputeId(), e.getMessage(), e);
            
            // Update dispute case with error
            if (disputeCase != null) {
                disputeCase.setStatus(DisputeCase.Status.ERROR);
                disputeCase.setErrorMessage(e.getMessage());
            }
            
            // Send to DLQ after max retries
            if (getRetryCount(event) >= 3) {
                sendToDeadLetterQueue(event, e);
                acknowledgment.acknowledge();
            } else {
                throw new RuntimeException("Dispute processing failed", e);
            }

        } finally {
            disputeProcessingTimer.stop(sample);
            clearTemporaryCache(event.getDisputeId());
        }
    }
    
    /**
     * Validate dispute eligibility
     */
    private DisputeValidation validateDispute(PaymentDisputeEvent event, Payment payment) {
        DisputeValidation.Builder validation = DisputeValidation.builder();
        
        // Check if payment exists and is completed
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            return validation
                .valid(false)
                .reason("Payment is not in completed status")
                .build();
        }
        
        // Check dispute time limit (typically 120 days)
        LocalDateTime disputeDeadline = payment.getCompletedAt().plusDays(120);
        if (LocalDateTime.now().isAfter(disputeDeadline)) {
            return validation
                .valid(false)
                .reason("Dispute period has expired")
                .build();
        }
        
        // Check for duplicate disputes
        String disputeKey = "dispute:payment:" + payment.getId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(disputeKey))) {
            return validation
                .valid(false)
                .reason("Dispute already exists for this payment")
                .build();
        }
        
        // Check dispute amount
        if (event.getDisputeAmount().compareTo(payment.getAmount()) > 0) {
            return validation
                .valid(false)
                .reason("Dispute amount exceeds transaction amount")
                .build();
        }
        
        // Check if already refunded
        if (payment.isRefunded()) {
            return validation
                .valid(false)
                .reason("Payment has already been refunded")
                .build();
        }
        
        return validation
            .valid(true)
            .eligibleForAutoResolve(event.canAutoResolve())
            .requiresMerchantResponse(true)
            .build();
    }
    
    /**
     * Determine appropriate response to dispute
     */
    private DisputeResponse determineDisputeResponse(
            PaymentDisputeEvent event, 
            Payment payment,
            DisputeValidation validation) {
        
        DisputeResponse.Builder response = DisputeResponse.builder();
        
        // Check for fraud-related disputes
        if (event.isFraudRelated()) {
            return response
                .decision(DisputeResponse.Decision.IMMEDIATE_ACTION)
                .holdFunds(true)
                .freezeAccount(freezeAccountOnFraud)
                .notifyFraudTeam(true)
                .requiresInvestigation(true)
                .priority(1)
                .reason("Fraud-related dispute requires immediate action")
                .build();
        }
        
        // Check for auto-resolution eligibility
        if (validation.isEligibleForAutoResolve() && 
            event.getDisputeAmount().compareTo(autoResolveMaxAmount) <= 0) {
            return response
                .decision(DisputeResponse.Decision.AUTO_RESOLVE)
                .canAutoResolve(true)
                .issueRefund(true)
                .refundAmount(event.getDisputeAmount())
                .priority(3)
                .reason("Eligible for automatic resolution")
                .build();
        }
        
        // High-value disputes require investigation
        if (event.isHighValue()) {
            return response
                .decision(DisputeResponse.Decision.INVESTIGATE)
                .holdFunds(true)
                .requiresInvestigation(true)
                .requiresMerchantResponse(true)
                .responseDeadline(LocalDateTime.now().plusDays(responseDeadlineDays))
                .priority(2)
                .reason("High-value dispute requires investigation")
                .build();
        }
        
        // Standard dispute processing
        return response
            .decision(DisputeResponse.Decision.STANDARD_PROCESS)
            .holdFunds(autoHoldFunds)
            .requiresMerchantResponse(true)
            .responseDeadline(LocalDateTime.now().plusDays(responseDeadlineDays))
            .priority(3)
            .reason("Standard dispute processing")
            .build();
    }
    
    /**
     * Execute dispute-related actions
     */
    private List<DisputeAction> executeDisputeActions(
            PaymentDisputeEvent event,
            Payment payment,
            DisputeResponse response,
            DisputeCase disputeCase) {
        
        List<DisputeAction> actions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // Hold merchant funds if required
        if (response.shouldHoldFunds() && autoHoldFunds) {
            try {
                Map<String, Object> holdRequest = new HashMap<>();
                holdRequest.put("merchantId", event.getMerchantId());
                holdRequest.put("amount", event.getDisputeAmount());
                holdRequest.put("reason", "Payment dispute: " + event.getDisputeId());
                holdRequest.put("releaseDate", response.getResponseDeadline());
                
                kafkaTemplate.send("merchant-fund-holds", event.getMerchantId(), holdRequest);
                
                actions.add(DisputeAction.builder()
                    .action("FUNDS_HELD")
                    .status("SUCCESS")
                    .amount(event.getDisputeAmount())
                    .timestamp(now)
                    .details("Merchant funds held pending dispute resolution")
                    .build());
                
                fundsHeld.increment();
                log.info("Held merchant funds: merchantId={}, amount={}", 
                    event.getMerchantId(), event.getDisputeAmount());
                
            } catch (Exception e) {
                log.error("Failed to hold merchant funds: merchantId={}", event.getMerchantId(), e);
                actions.add(DisputeAction.builder()
                    .action("FUNDS_HOLD_FAILED")
                    .status("ERROR")
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Update payment status
        try {
            payment.setStatus(PaymentStatus.DISPUTED);
            payment.setDisputeId(event.getDisputeId());
            payment.setDisputeDate(now);
            paymentRepository.save(payment);
            
            actions.add(DisputeAction.builder()
                .action("PAYMENT_STATUS_UPDATED")
                .status("SUCCESS")
                .timestamp(now)
                .details("Payment marked as disputed")
                .build());
            
        } catch (Exception e) {
            log.error("Failed to update payment status: paymentId={}", payment.getId(), e);
        }
        
        // Freeze account if fraud suspected
        if (response.shouldFreezeAccount() && event.isFraudRelated()) {
            try {
                Map<String, Object> freezeRequest = new HashMap<>();
                freezeRequest.put("customerId", event.getCustomerId());
                freezeRequest.put("reason", "Fraud dispute: " + event.getDisputeType());
                freezeRequest.put("disputeId", event.getDisputeId());
                freezeRequest.put("timestamp", now);
                
                kafkaTemplate.send("account-freeze-requests", event.getCustomerId(), freezeRequest);
                
                actions.add(DisputeAction.builder()
                    .action("ACCOUNT_FROZEN")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Customer account frozen due to fraud dispute")
                    .build());
                
                log.warn("Froze customer account due to fraud: customerId={}", event.getCustomerId());
                
            } catch (Exception e) {
                log.error("Failed to freeze account: customerId={}", event.getCustomerId(), e);
            }
        }
        
        // Create merchant notification request
        if (response.requiresMerchantResponse()) {
            try {
                Map<String, Object> merchantRequest = new HashMap<>();
                merchantRequest.put("merchantId", event.getMerchantId());
                merchantRequest.put("disputeId", event.getDisputeId());
                merchantRequest.put("transactionId", event.getTransactionId());
                merchantRequest.put("disputeAmount", event.getDisputeAmount());
                merchantRequest.put("disputeReason", event.getDisputeReason());
                merchantRequest.put("responseDeadline", response.getResponseDeadline());
                merchantRequest.put("evidenceRequired", true);
                
                kafkaTemplate.send("merchant-dispute-notifications", event.getMerchantId(), merchantRequest);
                
                actions.add(DisputeAction.builder()
                    .action("MERCHANT_NOTIFIED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Merchant notified of dispute")
                    .build());
                
            } catch (Exception e) {
                log.error("Failed to notify merchant: merchantId={}", event.getMerchantId(), e);
            }
        }
        
        // Store dispute in cache for quick lookup
        try {
            String cacheKey = "dispute:active:" + event.getDisputeId();
            redisTemplate.opsForHash().putAll(cacheKey, Map.of(
                "paymentId", payment.getId(),
                "customerId", event.getCustomerId(),
                "merchantId", event.getMerchantId(),
                "amount", event.getDisputeAmount().toString(),
                "status", response.getDecision().toString(),
                "createdAt", now.toString()
            ));
            redisTemplate.expire(cacheKey, Duration.ofDays(180));
            
            actions.add(DisputeAction.builder()
                .action("DISPUTE_CACHED")
                .status("SUCCESS")
                .timestamp(now)
                .build());
            
        } catch (Exception e) {
            log.error("Failed to cache dispute", e);
        }
        
        return actions;
    }
    
    /**
     * Auto-resolve eligible disputes
     */
    private void autoResolveDispute(
            PaymentDisputeEvent event,
            Payment payment,
            DisputeResponse response,
            DisputeCase disputeCase) {
        
        try {
            // Process refund
            Map<String, Object> refundRequest = new HashMap<>();
            refundRequest.put("paymentId", payment.getId());
            refundRequest.put("amount", event.getDisputeAmount());
            refundRequest.put("reason", "Auto-resolved dispute: " + event.getDisputeReason());
            refundRequest.put("disputeId", event.getDisputeId());
            refundRequest.put("autoResolved", true);
            
            String refundId = refundService.processRefund(refundRequest);
            
            // Update dispute case
            disputeCase.setStatus(DisputeCase.Status.AUTO_RESOLVED);
            disputeCase.setResolution("AUTO_REFUND");
            disputeCase.setRefundId(refundId);
            disputeCase.setResolvedAt(LocalDateTime.now());
            
            // Release held funds
            if (response.shouldHoldFunds()) {
                releaseMerchantFunds(event.getMerchantId(), event.getDisputeAmount());
            }
            
            refundsIssued.increment();
            log.info("Auto-resolved dispute with refund: disputeId={}, refundId={}", 
                event.getDisputeId(), refundId);
            
        } catch (Exception e) {
            log.error("Failed to auto-resolve dispute: disputeId={}", event.getDisputeId(), e);
            disputeCase.setStatus(DisputeCase.Status.REQUIRES_MANUAL_REVIEW);
        }
    }
    
    /**
     * Create investigation case for complex disputes
     */
    private void createInvestigationCase(
            PaymentDisputeEvent event,
            Payment payment,
            DisputeResponse response,
            DisputeCase disputeCase) {
        
        try {
            Map<String, Object> investigation = new HashMap<>();
            investigation.put("caseId", UUID.randomUUID().toString());
            investigation.put("disputeId", event.getDisputeId());
            investigation.put("paymentId", payment.getId());
            investigation.put("customerId", event.getCustomerId());
            investigation.put("merchantId", event.getMerchantId());
            investigation.put("amount", event.getDisputeAmount());
            investigation.put("type", event.getDisputeType());
            investigation.put("reason", event.getDisputeReason());
            investigation.put("priority", response.getPriority());
            investigation.put("fraudSuspected", event.isFraudRelated());
            investigation.put("deadline", response.getResponseDeadline());
            investigation.put("createdAt", LocalDateTime.now());
            investigation.put("status", "OPEN");
            
            kafkaTemplate.send("dispute-investigations", event.getDisputeId(), investigation);
            
            disputeCase.setInvestigationId(investigation.get("caseId").toString());
            disputesEscalated.increment();
            
            log.info("Created investigation case for dispute: disputeId={}, caseId={}", 
                event.getDisputeId(), investigation.get("caseId"));
            
        } catch (Exception e) {
            log.error("Failed to create investigation case: disputeId={}", event.getDisputeId(), e);
        }
    }
    
    /**
     * Send dispute notifications
     */
    private void sendDisputeNotifications(
            PaymentDisputeEvent event,
            Payment payment,
            DisputeResponse response,
            DisputeCase disputeCase) {
        
        CompletableFuture.runAsync(() -> {
            try {
                // Notify customer
                if (event.getNotifyCustomer()) {
                    Map<String, Object> customerNotification = new HashMap<>();
                    customerNotification.put("type", "DISPUTE_RECEIVED");
                    customerNotification.put("customerId", event.getCustomerId());
                    customerNotification.put("disputeId", event.getDisputeId());
                    customerNotification.put("transactionId", event.getTransactionId());
                    customerNotification.put("amount", event.getDisputeAmount());
                    customerNotification.put("status", response.getDecision());
                    customerNotification.put("expectedResolution", response.getResponseDeadline());
                    customerNotification.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("customer-notifications", event.getCustomerId(), customerNotification);
                }
                
                // Notify fraud team if needed
                if (response.shouldNotifyFraudTeam()) {
                    Map<String, Object> fraudAlert = new HashMap<>();
                    fraudAlert.put("type", "FRAUD_DISPUTE");
                    fraudAlert.put("disputeId", event.getDisputeId());
                    fraudAlert.put("customerId", event.getCustomerId());
                    fraudAlert.put("merchantId", event.getMerchantId());
                    fraudAlert.put("amount", event.getDisputeAmount());
                    fraudAlert.put("fraudType", event.getDisputeType());
                    fraudAlert.put("priority", "HIGH");
                    fraudAlert.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("fraud-team-alerts", fraudAlert);
                }
                
            } catch (Exception e) {
                log.error("Failed to send dispute notifications", e);
            }
        });
    }
    
    /**
     * Publish dispute event for other services
     */
    private void publishDisputeEvent(
            PaymentDisputeEvent event,
            Payment payment,
            DisputeResponse response,
            List<DisputeAction> actions) {
        
        try {
            Map<String, Object> disputeEvent = new HashMap<>();
            disputeEvent.put("disputeId", event.getDisputeId());
            disputeEvent.put("paymentId", payment.getId());
            disputeEvent.put("customerId", event.getCustomerId());
            disputeEvent.put("merchantId", event.getMerchantId());
            disputeEvent.put("amount", event.getDisputeAmount());
            disputeEvent.put("type", event.getDisputeType());
            disputeEvent.put("response", response.getDecision());
            disputeEvent.put("actions", actions);
            disputeEvent.put("timestamp", LocalDateTime.now());
            disputeEvent.put("correlationId", event.getCorrelationId());
            
            kafkaTemplate.send("payment-dispute-processed", event.getDisputeId(), disputeEvent);
            
        } catch (Exception e) {
            log.error("Failed to publish dispute event", e);
        }
    }
    
    /**
     * Reject invalid dispute
     */
    private void rejectDispute(PaymentDisputeEvent event, String reason) {
        try {
            Map<String, Object> rejection = new HashMap<>();
            rejection.put("disputeId", event.getDisputeId());
            rejection.put("reason", reason);
            rejection.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("dispute-rejections", event.getDisputeId(), rejection);
            
            // Notify customer
            if (event.getNotifyCustomer()) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "DISPUTE_REJECTED");
                notification.put("customerId", event.getCustomerId());
                notification.put("disputeId", event.getDisputeId());
                notification.put("reason", reason);
                
                kafkaTemplate.send("customer-notifications", event.getCustomerId(), notification);
            }
            
            log.info("Rejected dispute: disputeId={}, reason={}", event.getDisputeId(), reason);
            
        } catch (Exception e) {
            log.error("Failed to reject dispute: disputeId={}", event.getDisputeId(), e);
        }
    }
    
    /**
     * Release merchant funds
     */
    private void releaseMerchantFunds(String merchantId, BigDecimal amount) {
        try {
            Map<String, Object> releaseRequest = new HashMap<>();
            releaseRequest.put("merchantId", merchantId);
            releaseRequest.put("amount", amount);
            releaseRequest.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("merchant-fund-releases", merchantId, releaseRequest);
            
        } catch (Exception e) {
            log.error("Failed to release merchant funds: merchantId={}", merchantId, e);
        }
    }
    
    /**
     * Update dispute metrics
     */
    private void updateDisputeMetrics(PaymentDisputeEvent event, DisputeResponse response) {
        disputesReceived.increment();
        
        // Tag-based metrics
        Counter.builder("payment.dispute.type")
            .tag("type", event.getDisputeType().toString())
            .register(meterRegistry)
            .increment();
        
        Counter.builder("payment.dispute.reason")
            .tag("reason", event.getDisputeReason().toString())
            .register(meterRegistry)
            .increment();
        
        if (response.canAutoResolve()) {
            Counter.builder("payment.dispute.auto.resolved")
                .register(meterRegistry)
                .increment();
        }
    }
    
    // Helper methods and inner classes
    
    private DisputeCase createDisputeCase(PaymentDisputeEvent event, Payment payment, String correlationId) {
        String caseId = UUID.randomUUID().toString();
        DisputeCase disputeCase = DisputeCase.builder()
            .caseId(caseId)
            .disputeId(event.getDisputeId())
            .paymentId(payment.getId())
            .customerId(event.getCustomerId())
            .merchantId(event.getMerchantId())
            .amount(event.getDisputeAmount())
            .type(event.getDisputeType())
            .reason(event.getDisputeReason())
            .status(DisputeCase.Status.RECEIVED)
            .priority(event.getPriority())
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        activeDisputes.put(event.getDisputeId(), disputeCase);
        return disputeCase;
    }
    
    private int getRetryCount(PaymentDisputeEvent event) {
        String key = "dispute:retry:" + event.getDisputeId();
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        if (count == null) count = 0;
        count++;
        redisTemplate.opsForValue().set(key, count, Duration.ofHours(1));
        return count;
    }
    
    private void clearTemporaryCache(String disputeId) {
        try {
            redisTemplate.delete("dispute:processing:" + disputeId);
        } catch (Exception e) {
            log.debug("Error clearing temporary cache", e);
        }
    }
    
    private void sendToDeadLetterQueue(PaymentDisputeEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("event", event);
            dlqMessage.put("error", error.getMessage());
            dlqMessage.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("payment-disputes-dlq", event.getDisputeId(), dlqMessage);
            log.info("Sent dispute to DLQ: disputeId={}", event.getDisputeId());
        } catch (Exception e) {
            log.error("Failed to send to DLQ: disputeId={}", event.getDisputeId(), e);
        }
    }
    
    // Inner classes
    
    @Data
    @Builder
    private static class DisputeValidation {
        private boolean valid;
        private String reason;
        private boolean eligibleForAutoResolve;
        private boolean requiresMerchantResponse;
    }
    
    @Data
    @Builder
    private static class DisputeResponse {
        private Decision decision;
        private boolean holdFunds;
        private boolean freezeAccount;
        private boolean canAutoResolve;
        private boolean issueRefund;
        private BigDecimal refundAmount;
        private boolean requiresInvestigation;
        private boolean requiresMerchantResponse;
        private boolean notifyFraudTeam;
        private LocalDateTime responseDeadline;
        private int priority;
        private String reason;
        
        public enum Decision {
            IMMEDIATE_ACTION,
            AUTO_RESOLVE,
            INVESTIGATE,
            STANDARD_PROCESS,
            REJECT
        }
        
        public boolean shouldHoldFunds() {
            return holdFunds;
        }
        
        public boolean shouldFreezeAccount() {
            return freezeAccount;
        }
        
        public boolean shouldNotifyFraudTeam() {
            return notifyFraudTeam;
        }
    }
    
    @Data
    @Builder
    private static class DisputeCase {
        private String caseId;
        private String disputeId;
        private String paymentId;
        private String customerId;
        private String merchantId;
        private BigDecimal amount;
        private PaymentDisputeEvent.DisputeType type;
        private PaymentDisputeEvent.DisputeReason reason;
        private Status status;
        private PaymentDisputeEvent.Priority priority;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdated;
        private LocalDateTime resolvedAt;
        private String resolution;
        private String refundId;
        private String investigationId;
        private List<DisputeAction> actions;
        private String errorMessage;
        private String correlationId;
        
        public enum Status {
            RECEIVED,
            PROCESSING,
            AUTO_RESOLVED,
            INVESTIGATING,
            REQUIRES_MANUAL_REVIEW,
            RESOLVED,
            REJECTED,
            ERROR
        }
    }
    
    @Data
    @Builder
    private static class DisputeAction {
        private String action;
        private String status;
        private BigDecimal amount;
        private LocalDateTime timestamp;
        private String details;
        private String errorMessage;
    }
}