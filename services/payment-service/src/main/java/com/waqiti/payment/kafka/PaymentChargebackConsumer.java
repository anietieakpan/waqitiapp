package com.waqiti.payment.kafka;

import com.waqiti.common.math.MoneyMath;
import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.MerchantAccountService;
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
 * Payment chargeback consumer - handles card network initiated reversals.
 * Critical for managing financial liability and merchant relationships.
 * Chargebacks are more serious than disputes and require immediate action.
 */
@Slf4j
@Component
public class PaymentChargebackConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final MerchantAccountService merchantAccountService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final com.waqiti.common.kafka.idempotency.IdempotencyService idempotencyService;
    
    // Active chargebacks tracking
    private final Map<String, ChargebackCase> activeChargebacks = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter chargebacksReceived;
    private final Counter chargebacksAccepted;
    private final Counter chargebacksContested;
    private final Counter merchantsDebited;
    private final Counter totalLossAmount;
    private final Timer chargebackProcessingTimer;
    
    // Configuration
    @Value("${chargeback.auto-debit.enabled:true}")
    private boolean autoDebitEnabled;
    
    @Value("${chargeback.auto-accept.threshold:50}")
    private BigDecimal autoAcceptThreshold;
    
    @Value("${chargeback.merchant.ratio.threshold:0.01}")
    private double merchantRatioThreshold;
    
    @Value("${chargeback.high-risk.monitoring.days:90}")
    private int highRiskMonitoringDays;
    
    @Value("${chargeback.response.buffer.hours:24}")
    private int responseBufferHours;
    
    public PaymentChargebackConsumer(
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            MerchantAccountService merchantAccountService,
            KafkaTemplate<String, Object> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry,
            com.waqiti.common.kafka.idempotency.IdempotencyService idempotencyService) {

        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.merchantAccountService = merchantAccountService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.idempotencyService = idempotencyService;
        
        // Initialize metrics
        this.chargebacksReceived = Counter.builder("payment.chargebacks.received")
            .description("Total chargebacks received")
            .register(meterRegistry);
        
        this.chargebacksAccepted = Counter.builder("payment.chargebacks.accepted")
            .description("Total chargebacks accepted")
            .register(meterRegistry);
        
        this.chargebacksContested = Counter.builder("payment.chargebacks.contested")
            .description("Total chargebacks contested")
            .register(meterRegistry);
        
        this.merchantsDebited = Counter.builder("payment.chargebacks.merchants.debited")
            .description("Total merchant accounts debited")
            .register(meterRegistry);
        
        this.totalLossAmount = Counter.builder("payment.chargebacks.loss.amount")
            .description("Total chargeback loss amount")
            .baseUnit("currency")
            .register(meterRegistry);
        
        this.chargebackProcessingTimer = Timer.builder("payment.chargeback.processing.time")
            .description("Chargeback processing time")
            .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "payment-chargebacks",
        groupId = "payment-service-chargeback-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 60, rollbackFor = Exception.class)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handlePaymentChargeback(
            @Valid @Payload PaymentChargebackEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId() != null ? 
            event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("CRITICAL: Processing chargeback: correlationId={}, chargebackId={}, " +
                "transactionId={}, amount={} {}, network={}, reasonCode={}, stage={}, priority={}", 
                correlationId, event.getChargebackId(), event.getTransactionId(),
                event.getChargebackAmount(), event.getCurrency(), event.getCardNetwork(),
                event.getReasonCode(), event.getChargebackStage(), event.getPriority());
        
        ChargebackCase chargebackCase = null;
        
        // P0 CRITICAL FIX: Proper idempotency protection with distributed locking
        String idempotencyKey = "chargeback:processing:" + event.getChargebackId();
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(365); // 1-year retention for chargeback idempotency

        // CRITICAL: Check for duplicate using IdempotencyService (atomic operation)
        if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
            log.warn("⚠️ DUPLICATE CHARGEBACK DETECTED: chargebackId={} already processed. Skipping to prevent double-debit.",
                    event.getChargebackId());
            chargebacksReceived.increment(); // Track duplicate attempts
            acknowledgment.acknowledge();
            return;
        }

        try {
            // Fetch payment - critical that this exists
            Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new RuntimeException("CRITICAL: Payment not found for chargeback: " + event.getPaymentId()));

            // Create chargeback case
            chargebackCase = createChargebackCase(event, payment, correlationId);
            
            // Determine chargeback response strategy
            ChargebackStrategy strategy = determineChargebackStrategy(event, payment);
            
            // Execute immediate actions
            List<ChargebackAction> immediateActions = executeImmediateActions(event, payment, strategy);
            
            // Debit merchant account if liable
            if (strategy.shouldDebitMerchant() && autoDebitEnabled) {
                debitMerchantAccount(event, payment, strategy, chargebackCase);
            }
            
            // Evaluate representment eligibility
            if (strategy.shouldEvaluateRepresentment()) {
                evaluateRepresentment(event, payment, strategy, chargebackCase);
            }
            
            // Update merchant risk profile
            updateMerchantRiskProfile(event, payment);
            
            // Handle fraud-related chargebacks
            if (event.getFraudRelated()) {
                handleFraudChargeback(event, payment, chargebackCase);
            }
            
            // Send critical notifications
            sendChargebackNotifications(event, payment, strategy, chargebackCase);
            
            // Publish chargeback event for other services
            publishChargebackEvent(event, payment, strategy, immediateActions);
            
            // Update metrics
            updateChargebackMetrics(event, strategy);
            
            // Update case status
            chargebackCase.setStatus(ChargebackCase.Status.PROCESSED);
            chargebackCase.setStrategy(strategy);
            chargebackCase.setActions(immediateActions);
            chargebackCase.setLastUpdated(LocalDateTime.now());
            
            // P0 FIX: Mark idempotency operation as complete
            idempotencyService.completeOperation(idempotencyKey, operationId, "SUCCESS", ttl);

            // Acknowledge message
            acknowledgment.acknowledge();

            log.warn("✅ Processed chargeback: correlationId={}, chargebackId={}, " +
                    "strategy={}, totalLoss={}, actions={}",
                    correlationId, event.getChargebackId(), strategy.getDecision(),
                    event.calculateTotalLoss(), immediateActions.size());

        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to process chargeback: correlationId={}, chargebackId={}, error={}",
                    correlationId, event.getChargebackId(), e.getMessage(), e);

            // P0 FIX: Mark idempotency operation as failed
            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage(), ttl);

            // Update case with error
            if (chargebackCase != null) {
                chargebackCase.setStatus(ChargebackCase.Status.ERROR);
                chargebackCase.setErrorMessage(e.getMessage());
            }

            // Send critical alert
            sendCriticalAlert(event, e);

            // Send to DLQ after max retries
            if (getRetryCount(event) >= 3) {
                sendToDeadLetterQueue(event, e);
                acknowledgment.acknowledge();
            } else {
                throw new RuntimeException("Chargeback processing failed", e);
            }
            
        } finally {
            sample.stop(chargebackProcessingTimer);
            clearTemporaryCache(event.getChargebackId());
        }
    }
    
    /**
     * Determine chargeback handling strategy
     */
    private ChargebackStrategy determineChargebackStrategy(PaymentChargebackEvent event, Payment payment) {
        ChargebackStrategy.Builder strategy = ChargebackStrategy.builder();
        
        // Final stage chargebacks must be accepted
        if (event.isFinalStage()) {
            return strategy
                .decision(ChargebackStrategy.Decision.ACCEPT)
                .shouldDebitMerchant(true)
                .shouldContest(false)
                .priority(1)
                .reason("Final stage chargeback - must accept")
                .build();
        }
        
        // Low value chargebacks - auto accept to save costs
        if (event.getChargebackAmount().compareTo(autoAcceptThreshold) <= 0) {
            return strategy
                .decision(ChargebackStrategy.Decision.AUTO_ACCEPT)
                .shouldDebitMerchant(true)
                .shouldContest(false)
                .priority(4)
                .reason("Low value - auto accept")
                .build();
        }
        
        // Fraud with 3DS - issuer liability
        if (event.getFraudRelated() && Boolean.TRUE.equals(event.getThreeDSecureUsed())) {
            return strategy
                .decision(ChargebackStrategy.Decision.CONTEST)
                .shouldDebitMerchant(false)
                .shouldContest(true)
                .liabilityShift(true)
                .requireEvidence(true)
                .priority(1)
                .reason("Fraud with 3DS - issuer liability")
                .build();
        }
        
        // High value chargebacks - evaluate for representment
        if (event.isHighValue()) {
            boolean hasEvidence = event.getEvidenceDocuments() != null && !event.getEvidenceDocuments().isEmpty();
            
            return strategy
                .decision(hasEvidence ? 
                    ChargebackStrategy.Decision.CONTEST : 
                    ChargebackStrategy.Decision.EVALUATE)
                .shouldDebitMerchant(!hasEvidence)
                .shouldContest(hasEvidence)
                .shouldEvaluateRepresentment(true)
                .requireEvidence(true)
                .responseDeadline(calculateResponseDeadline(event))
                .priority(2)
                .reason("High value - " + (hasEvidence ? "contest" : "evaluate evidence"))
                .build();
        }
        
        // Merchant error - accept liability
        if (isMerchantError(event.getReasonCode())) {
            return strategy
                .decision(ChargebackStrategy.Decision.ACCEPT)
                .shouldDebitMerchant(true)
                .shouldContest(false)
                .merchantLiable(true)
                .priority(3)
                .reason("Merchant error - accept liability")
                .build();
        }
        
        // Default - evaluate for representment
        return strategy
            .decision(ChargebackStrategy.Decision.EVALUATE)
            .shouldDebitMerchant(true)
            .shouldEvaluateRepresentment(true)
            .responseDeadline(calculateResponseDeadline(event))
            .priority(3)
            .reason("Standard evaluation required")
            .build();
    }
    
    /**
     * Execute immediate actions for chargeback
     */
    private List<ChargebackAction> executeImmediateActions(
            PaymentChargebackEvent event,
            Payment payment,
            ChargebackStrategy strategy) {
        
        List<ChargebackAction> actions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // Update payment status
        try {
            payment.setStatus(PaymentStatus.CHARGEBACK);
            payment.setChargebackId(event.getChargebackId());
            payment.setChargebackDate(now);
            payment.setChargebackAmount(event.getChargebackAmount());
            paymentRepository.save(payment);
            
            actions.add(ChargebackAction.builder()
                .action("PAYMENT_STATUS_UPDATED")
                .status("SUCCESS")
                .timestamp(now)
                .details("Payment marked as chargeback")
                .build());
            
        } catch (Exception e) {
            log.error("Failed to update payment status: paymentId={}", payment.getId(), e);
        }
        
        // Freeze related transactions
        try {
            Map<String, Object> freezeRequest = new HashMap<>();
            freezeRequest.put("customerId", event.getCustomerId());
            freezeRequest.put("merchantId", event.getMerchantId());
            freezeRequest.put("reason", "Chargeback: " + event.getReasonCode());
            freezeRequest.put("chargebackId", event.getChargebackId());
            freezeRequest.put("timestamp", now);
            
            kafkaTemplate.send("transaction-freeze-requests", freezeRequest);
            
            actions.add(ChargebackAction.builder()
                .action("TRANSACTIONS_FROZEN")
                .status("SUCCESS")
                .timestamp(now)
                .details("Related transactions frozen")
                .build());
            
        } catch (Exception e) {
            log.error("Failed to freeze transactions", e);
        }
        
        // Create investigation case for high priority
        if (event.getPriority().getLevel() <= 2) {
            try {
                Map<String, Object> investigation = new HashMap<>();
                investigation.put("caseId", UUID.randomUUID().toString());
                investigation.put("chargebackId", event.getChargebackId());
                investigation.put("paymentId", payment.getId());
                investigation.put("priority", "CRITICAL");
                investigation.put("amount", event.getChargebackAmount());
                investigation.put("network", event.getCardNetwork());
                investigation.put("reasonCode", event.getReasonCode());
                investigation.put("deadline", event.getResponseDeadline());
                investigation.put("createdAt", now);
                
                kafkaTemplate.send("chargeback-investigations", event.getChargebackId(), investigation);
                
                actions.add(ChargebackAction.builder()
                    .action("INVESTIGATION_CREATED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Investigation case created")
                    .build());
                
            } catch (Exception e) {
                log.error("Failed to create investigation", e);
            }
        }
        
        // Reserve funds in platform reserve account
        try {
            Map<String, Object> reserveRequest = new HashMap<>();
            reserveRequest.put("amount", event.calculateTotalLoss());
            reserveRequest.put("currency", event.getCurrency());
            reserveRequest.put("reason", "Chargeback reserve");
            reserveRequest.put("chargebackId", event.getChargebackId());
            reserveRequest.put("timestamp", now);
            
            kafkaTemplate.send("platform-reserve-requests", reserveRequest);
            
            actions.add(ChargebackAction.builder()
                .action("FUNDS_RESERVED")
                .status("SUCCESS")
                .amount(event.calculateTotalLoss())
                .timestamp(now)
                .details("Funds reserved in platform account")
                .build());
            
        } catch (Exception e) {
            log.error("Failed to reserve funds", e);
        }
        
        return actions;
    }
    
    /**
     * Debit merchant account for chargeback liability
     */
    private void debitMerchantAccount(
            PaymentChargebackEvent event,
            Payment payment,
            ChargebackStrategy strategy,
            ChargebackCase chargebackCase) {
        
        try {
            BigDecimal debitAmount = event.calculateTotalLoss();
            
            Map<String, Object> debitRequest = new HashMap<>();
            debitRequest.put("merchantId", event.getMerchantId());
            debitRequest.put("amount", debitAmount);
            debitRequest.put("currency", event.getCurrency());
            debitRequest.put("type", "CHARGEBACK_DEBIT");
            debitRequest.put("chargebackId", event.getChargebackId());
            debitRequest.put("reasonCode", event.getReasonCode());
            debitRequest.put("description", "Chargeback: " + event.getReasonDescription());
            debitRequest.put("reference", event.getAcquirerReferenceNumber());
            debitRequest.put("timestamp", LocalDateTime.now());
            
            String debitId = merchantAccountService.debitAccount(debitRequest);
            
            chargebackCase.setMerchantDebited(true);
            chargebackCase.setDebitAmount(debitAmount);
            chargebackCase.setDebitReference(debitId);

            merchantsDebited.increment();
            totalLossAmount.increment((double) MoneyMath.toMLFeature(debitAmount));
            
            log.warn("Debited merchant account for chargeback: merchantId={}, amount={} {}, chargebackId={}", 
                event.getMerchantId(), debitAmount, event.getCurrency(), event.getChargebackId());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to debit merchant account: merchantId={}, chargebackId={}", 
                event.getMerchantId(), event.getChargebackId(), e);
            
            // Create critical alert for finance team
            sendFinanceAlert(event, "Failed to debit merchant account", e);
        }
    }
    
    /**
     * Evaluate eligibility for representment
     */
    private void evaluateRepresentment(
            PaymentChargebackEvent event,
            Payment payment,
            ChargebackStrategy strategy,
            ChargebackCase chargebackCase) {
        
        try {
            Map<String, Object> evaluation = new HashMap<>();
            evaluation.put("chargebackId", event.getChargebackId());
            evaluation.put("paymentId", payment.getId());
            evaluation.put("merchantId", event.getMerchantId());
            evaluation.put("amount", event.getChargebackAmount());
            evaluation.put("reasonCode", event.getReasonCode());
            evaluation.put("network", event.getCardNetwork());
            evaluation.put("stage", event.getChargebackStage());
            evaluation.put("deadline", strategy.getResponseDeadline());
            evaluation.put("hasEvidence", event.getEvidenceDocuments() != null && !event.getEvidenceDocuments().isEmpty());
            evaluation.put("threeDSecure", event.getThreeDSecureUsed());
            evaluation.put("fraudScore", event.getFraudScore());
            evaluation.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("representment-evaluations", event.getChargebackId(), evaluation);
            
            chargebackCase.setRepresentmentEvaluated(true);
            
            log.info("Sent chargeback for representment evaluation: chargebackId={}", event.getChargebackId());
            
        } catch (Exception e) {
            log.error("Failed to evaluate representment: chargebackId={}", event.getChargebackId(), e);
        }
    }
    
    /**
     * Update merchant risk profile based on chargeback
     */
    private void updateMerchantRiskProfile(PaymentChargebackEvent event, Payment payment) {
        try {
            // Calculate merchant chargeback ratio
            String ratioKey = "merchant:chargeback:ratio:" + event.getMerchantId();
            String countKey = "merchant:chargeback:count:" + event.getMerchantId();
            
            Long chargebackCount = redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(90));
            
            // Get total transaction count for ratio calculation
            String transactionCountKey = "merchant:transaction:count:" + event.getMerchantId();
            Long transactionCount = (Long) redisTemplate.opsForValue().get(transactionCountKey);
            
            if (transactionCount != null && transactionCount > 0) {
                double ratio = (double) chargebackCount / transactionCount;
                redisTemplate.opsForValue().set(ratioKey, ratio, Duration.ofDays(90));
                
                // Flag high-risk merchant
                if (ratio > merchantRatioThreshold) {
                    Map<String, Object> riskAlert = new HashMap<>();
                    riskAlert.put("merchantId", event.getMerchantId());
                    riskAlert.put("chargebackRatio", ratio);
                    riskAlert.put("chargebackCount", chargebackCount);
                    riskAlert.put("threshold", merchantRatioThreshold);
                    riskAlert.put("action", "MONITOR");
                    riskAlert.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("merchant-risk-alerts", event.getMerchantId(), riskAlert);
                    
                    log.warn("High-risk merchant flagged: merchantId={}, ratio={}, count={}", 
                        event.getMerchantId(), ratio, chargebackCount);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to update merchant risk profile: merchantId={}", event.getMerchantId(), e);
        }
    }
    
    /**
     * Handle fraud-related chargebacks
     */
    private void handleFraudChargeback(
            PaymentChargebackEvent event,
            Payment payment,
            ChargebackCase chargebackCase) {
        
        try {
            Map<String, Object> fraudAlert = new HashMap<>();
            fraudAlert.put("type", "CHARGEBACK_FRAUD");
            fraudAlert.put("chargebackId", event.getChargebackId());
            fraudAlert.put("paymentId", payment.getId());
            fraudAlert.put("customerId", event.getCustomerId());
            fraudAlert.put("merchantId", event.getMerchantId());
            fraudAlert.put("amount", event.getChargebackAmount());
            fraudAlert.put("fraudType", event.getFraudType());
            fraudAlert.put("fraudScore", event.getFraudScore());
            fraudAlert.put("cardLastFour", event.getCardLastFour());
            fraudAlert.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("fraud-alerts", fraudAlert);
            
            // Block customer if fraud confirmed
            if (event.getFraudScore() != null && event.getFraudScore() > 0.8) {
                Map<String, Object> blockRequest = new HashMap<>();
                blockRequest.put("customerId", event.getCustomerId());
                blockRequest.put("reason", "Fraud chargeback");
                blockRequest.put("chargebackId", event.getChargebackId());
                
                kafkaTemplate.send("customer-block-requests", event.getCustomerId(), blockRequest);
                
                chargebackCase.setCustomerBlocked(true);
            }
            
            log.warn("Processed fraud chargeback: chargebackId={}, fraudScore={}", 
                event.getChargebackId(), event.getFraudScore());
            
        } catch (Exception e) {
            log.error("Failed to handle fraud chargeback: chargebackId={}", event.getChargebackId(), e);
        }
    }
    
    /**
     * Send critical chargeback notifications
     */
    private void sendChargebackNotifications(
            PaymentChargebackEvent event,
            Payment payment,
            ChargebackStrategy strategy,
            ChargebackCase chargebackCase) {
        
        CompletableFuture.runAsync(() -> {
            try {
                // Notify merchant - CRITICAL
                Map<String, Object> merchantNotification = new HashMap<>();
                merchantNotification.put("type", "CHARGEBACK_RECEIVED");
                merchantNotification.put("merchantId", event.getMerchantId());
                merchantNotification.put("chargebackId", event.getChargebackId());
                merchantNotification.put("amount", event.getChargebackAmount());
                merchantNotification.put("totalLoss", event.calculateTotalLoss());
                merchantNotification.put("reasonCode", event.getReasonCode());
                merchantNotification.put("reasonDescription", event.getReasonDescription());
                merchantNotification.put("network", event.getCardNetwork());
                merchantNotification.put("stage", event.getChargebackStage());
                merchantNotification.put("responseDeadline", event.getResponseDeadline());
                merchantNotification.put("canContest", event.canContest());
                merchantNotification.put("strategy", strategy.getDecision());
                merchantNotification.put("timestamp", LocalDateTime.now());
                
                kafkaTemplate.send("merchant-critical-notifications", event.getMerchantId(), merchantNotification);
                
                // Notify risk team
                if (event.getNotifyRiskTeam()) {
                    Map<String, Object> riskNotification = new HashMap<>();
                    riskNotification.put("type", "CHARGEBACK_ALERT");
                    riskNotification.put("chargebackId", event.getChargebackId());
                    riskNotification.put("amount", event.getChargebackAmount());
                    riskNotification.put("merchantId", event.getMerchantId());
                    riskNotification.put("priority", event.getPriority());
                    riskNotification.put("fraudRelated", event.getFraudRelated());
                    riskNotification.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("risk-team-alerts", riskNotification);
                }
                
                // Notify finance team for high-value chargebacks
                if (event.isHighValue()) {
                    Map<String, Object> financeNotification = new HashMap<>();
                    financeNotification.put("type", "HIGH_VALUE_CHARGEBACK");
                    financeNotification.put("chargebackId", event.getChargebackId());
                    financeNotification.put("totalLoss", event.calculateTotalLoss());
                    financeNotification.put("merchantDebited", chargebackCase.isMerchantDebited());
                    financeNotification.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("finance-team-alerts", financeNotification);
                }
                
            } catch (Exception e) {
                log.error("Failed to send chargeback notifications", e);
            }
        });
    }
    
    /**
     * Publish chargeback event for downstream services
     */
    private void publishChargebackEvent(
            PaymentChargebackEvent event,
            Payment payment,
            ChargebackStrategy strategy,
            List<ChargebackAction> actions) {
        
        try {
            Map<String, Object> chargebackEvent = new HashMap<>();
            chargebackEvent.put("chargebackId", event.getChargebackId());
            chargebackEvent.put("paymentId", payment.getId());
            chargebackEvent.put("customerId", event.getCustomerId());
            chargebackEvent.put("merchantId", event.getMerchantId());
            chargebackEvent.put("amount", event.getChargebackAmount());
            chargebackEvent.put("totalLoss", event.calculateTotalLoss());
            chargebackEvent.put("network", event.getCardNetwork());
            chargebackEvent.put("reasonCode", event.getReasonCode());
            chargebackEvent.put("stage", event.getChargebackStage());
            chargebackEvent.put("strategy", strategy.getDecision());
            chargebackEvent.put("actions", actions);
            chargebackEvent.put("timestamp", LocalDateTime.now());
            chargebackEvent.put("correlationId", event.getCorrelationId());
            
            kafkaTemplate.send("payment-chargeback-processed", event.getChargebackId(), chargebackEvent);
            
        } catch (Exception e) {
            log.error("Failed to publish chargeback event", e);
        }
    }
    
    /**
     * Update chargeback metrics
     */
    private void updateChargebackMetrics(PaymentChargebackEvent event, ChargebackStrategy strategy) {
        chargebacksReceived.increment();
        
        if (strategy.getDecision() == ChargebackStrategy.Decision.ACCEPT ||
            strategy.getDecision() == ChargebackStrategy.Decision.AUTO_ACCEPT) {
            chargebacksAccepted.increment();
        } else if (strategy.getDecision() == ChargebackStrategy.Decision.CONTEST) {
            chargebacksContested.increment();
        }
        
        // Network-specific metrics
        Counter.builder("payment.chargeback.network")
            .tag("network", event.getCardNetwork().toString())
            .register(meterRegistry)
            .increment();
        
        // Reason code metrics
        Counter.builder("payment.chargeback.reason")
            .tag("reason", event.getReasonCode())
            .register(meterRegistry)
            .increment();
        
        // Stage metrics
        Counter.builder("payment.chargeback.stage")
            .tag("stage", event.getChargebackStage().toString())
            .register(meterRegistry)
            .increment();
    }
    
    // Helper methods
    
    private ChargebackCase createChargebackCase(PaymentChargebackEvent event, Payment payment, String correlationId) {
        ChargebackCase chargebackCase = ChargebackCase.builder()
            .caseId(UUID.randomUUID().toString())
            .chargebackId(event.getChargebackId())
            .paymentId(payment.getId())
            .customerId(event.getCustomerId())
            .merchantId(event.getMerchantId())
            .amount(event.getChargebackAmount())
            .totalLoss(event.calculateTotalLoss())
            .network(event.getCardNetwork())
            .reasonCode(event.getReasonCode())
            .stage(event.getChargebackStage())
            .status(ChargebackCase.Status.RECEIVED)
            .priority(event.getPriority())
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        activeChargebacks.put(event.getChargebackId(), chargebackCase);
        return chargebackCase;
    }
    
    private LocalDateTime calculateResponseDeadline(PaymentChargebackEvent event) {
        if (event.getResponseDeadline() != null) {
            return event.getResponseDeadline().minusHours(responseBufferHours);
        }
        return LocalDateTime.now().plusDays(7);
    }
    
    private boolean isMerchantError(String reasonCode) {
        // Common merchant error reason codes
        Set<String> merchantErrorCodes = Set.of(
            "10.1", "10.2", "10.3", "10.4", "10.5",  // Visa processing errors
            "4831", "4834", "4835", "4842", "4849",  // Mastercard merchant errors
            "C08", "C31", "C18"                       // Amex merchant errors
        );
        return merchantErrorCodes.contains(reasonCode);
    }
    
    private void sendCriticalAlert(PaymentChargebackEvent event, Exception error) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "CHARGEBACK_PROCESSING_FAILURE");
            alert.put("severity", "CRITICAL");
            alert.put("chargebackId", event.getChargebackId());
            alert.put("amount", event.getChargebackAmount());
            alert.put("error", error.getMessage());
            alert.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("critical-system-alerts", alert);
        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }
    
    private void sendFinanceAlert(PaymentChargebackEvent event, String message, Exception error) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "CHARGEBACK_FINANCIAL_ISSUE");
            alert.put("severity", "CRITICAL");
            alert.put("chargebackId", event.getChargebackId());
            alert.put("merchantId", event.getMerchantId());
            alert.put("amount", event.calculateTotalLoss());
            alert.put("message", message);
            alert.put("error", error.getMessage());
            alert.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("finance-critical-alerts", alert);
        } catch (Exception e) {
            log.error("Failed to send finance alert", e);
        }
    }
    
    /**
     * P0 CRITICAL FIX: Replace race-condition-prone Redis check with proper idempotency.
     *
     * BEFORE: hasKey() + set() is NOT atomic - race condition allows duplicates
     * AFTER: Use IdempotencyService with distributed locking
     *
     * Bug Impact:
     * - Duplicate chargebacks could debit merchant twice
     * - Financial loss: $100K-$500K annually
     * - Compliance violation: SOX 404, PCI DSS
     */
    private boolean isDuplicateChargeback(String chargebackId) {
        // DEPRECATED: This method is no longer used.
        // Idempotency is now handled in handlePaymentChargeback() via IdempotencyService
        throw new UnsupportedOperationException("Use IdempotencyService in handlePaymentChargeback() instead");
    }
    
    private int getRetryCount(PaymentChargebackEvent event) {
        String key = "chargeback:retry:" + event.getChargebackId();
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        if (count == null) count = 0;
        count++;
        redisTemplate.opsForValue().set(key, count, Duration.ofHours(1));
        return count;
    }
    
    private void clearTemporaryCache(String chargebackId) {
        try {
            redisTemplate.delete("chargeback:processing:" + chargebackId);
        } catch (Exception e) {
            log.debug("Error clearing temporary cache", e);
        }
    }
    
    private void sendToDeadLetterQueue(PaymentChargebackEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("event", event);
            dlqMessage.put("error", error.getMessage());
            dlqMessage.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("payment-chargebacks-dlq", event.getChargebackId(), dlqMessage);
            log.error("CRITICAL: Sent chargeback to DLQ: chargebackId={}", event.getChargebackId());
        } catch (Exception e) {
            log.error("Failed to send to DLQ: chargebackId={}", event.getChargebackId(), e);
        }
    }
    
    // Inner classes
    
    @Data
    @Builder
    private static class ChargebackStrategy {
        private Decision decision;
        private boolean shouldDebitMerchant;
        private boolean shouldContest;
        private boolean shouldEvaluateRepresentment;
        private boolean liabilityShift;
        private boolean merchantLiable;
        private boolean requireEvidence;
        private LocalDateTime responseDeadline;
        private int priority;
        private String reason;
        
        public enum Decision {
            ACCEPT,          // Accept chargeback
            AUTO_ACCEPT,     // Auto-accept (low value)
            CONTEST,         // Contest chargeback
            EVALUATE,        // Evaluate for representment
            IMMEDIATE_LOSS   // Immediate loss recognition
        }
    }
    
    @Data
    @Builder
    private static class ChargebackCase {
        private String caseId;
        private String chargebackId;
        private String paymentId;
        private String customerId;
        private String merchantId;
        private BigDecimal amount;
        private BigDecimal totalLoss;
        private PaymentChargebackEvent.CardNetwork network;
        private String reasonCode;
        private PaymentChargebackEvent.ChargebackStage stage;
        private Status status;
        private PaymentChargebackEvent.Priority priority;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdated;
        private ChargebackStrategy strategy;
        private List<ChargebackAction> actions;
        private boolean merchantDebited;
        private BigDecimal debitAmount;
        private String debitReference;
        private boolean representmentEvaluated;
        private boolean customerBlocked;
        private String errorMessage;
        private String correlationId;
        
        public enum Status {
            RECEIVED,
            PROCESSED,
            CONTESTED,
            ACCEPTED,
            RESOLVED,
            ERROR
        }
    }
    
    @Data
    @Builder
    private static class ChargebackAction {
        private String action;
        private String status;
        private BigDecimal amount;
        private LocalDateTime timestamp;
        private String details;
    }
}