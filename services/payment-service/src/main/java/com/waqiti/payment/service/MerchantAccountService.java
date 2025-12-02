package com.waqiti.payment.service;

import com.waqiti.payment.client.MerchantServiceClient;
import com.waqiti.payment.client.MerchantPaymentServiceClient;
import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.payment.dto.PaymentDisputeEvent;
import com.waqiti.payment.exception.MerchantAccountException;
import com.waqiti.payment.exception.InsufficientFundsException;
import com.waqiti.payment.merchant.dto.*;
import com.waqiti.payment.merchant.event.*;
import com.waqiti.payment.merchant.saga.ChargebackSaga;
import com.waqiti.payment.merchant.saga.SagaOrchestrator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Merchant Account Integration Service for Payment Service.
 * 
 * This service acts as an orchestrator and integration layer between the payment-service
 * and the merchant-related services (merchant-service and merchant-payment-service).
 * It handles merchant-related operations in the context of payment processing,
 * chargebacks, and disputes.
 * 
 * Architecture:
 * - Integrates with merchant-service for merchant account management
 * - Integrates with merchant-payment-service for payment processing
 * - Implements saga pattern for distributed transactions
 * - Provides caching layer for merchant data
 * - Handles chargeback and dispute processing against merchant accounts
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAccountService {
    
    // Service clients
    private final MerchantServiceClient merchantServiceClient;
    private final MerchantPaymentServiceClient merchantPaymentServiceClient;
    private final WebClient.Builder webClientBuilder;
    
    // Infrastructure components
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StreamBridge streamBridge;
    private final MeterRegistry meterRegistry;
    
    // Saga orchestration
    private final SagaOrchestrator sagaOrchestrator;
    
    // Service configuration
    @Value("${merchant.service.base-url:http://merchant-service:8080}")
    private String merchantServiceBaseUrl;
    
    @Value("${merchant.payment.service.base-url:http://merchant-payment-service:8080}")
    private String merchantPaymentServiceBaseUrl;
    
    @Value("${merchant.chargeback.fee:25.00}")
    private BigDecimal chargebackFee;
    
    @Value("${merchant.chargeback.reserve.percentage:0.01}")
    private BigDecimal chargebackReservePercentage;
    
    @Value("${merchant.dispute.hold.percentage:1.1}")
    private BigDecimal disputeHoldPercentage;
    
    @Value("${merchant.cache.ttl.minutes:15}")
    private long cacheTtlMinutes;
    
    // Metrics
    private Counter chargebackProcessedCounter;
    private Counter chargebackFailedCounter;
    private Counter disputeProcessedCounter;
    private Timer merchantOperationTimer;
    
    // Cache keys
    private static final String MERCHANT_CACHE_PREFIX = "merchant:account:";
    private static final String MERCHANT_BALANCE_PREFIX = "merchant:balance:";
    private static final String MERCHANT_STATUS_PREFIX = "merchant:status:";
    private static final String PROCESSING_LOCK_PREFIX = "merchant:lock:";
    
    @PostConstruct
    public void initialize() {
        // Initialize metrics
        chargebackProcessedCounter = Counter.builder("merchant.chargeback.processed")
            .description("Number of chargebacks processed")
            .register(meterRegistry);
            
        chargebackFailedCounter = Counter.builder("merchant.chargeback.failed")
            .description("Number of chargebacks failed")
            .register(meterRegistry);
            
        disputeProcessedCounter = Counter.builder("merchant.dispute.processed")
            .description("Number of disputes processed")
            .register(meterRegistry);
            
        merchantOperationTimer = Timer.builder("merchant.operation.duration")
            .description("Merchant operation duration")
            .register(meterRegistry);
            
        log.info("MerchantAccountService initialized with merchant-service at {} and merchant-payment-service at {}", 
            merchantServiceBaseUrl, merchantPaymentServiceBaseUrl);
    }
    
    /**
     * Process chargeback against merchant account.
     * This method orchestrates the chargeback process across multiple services
     * using the saga pattern for distributed transaction management.
     */
    @Transactional
    @CircuitBreaker(name = "merchant-chargeback", fallbackMethod = "processChargebackFallback")
    @Bulkhead(name = "merchant-chargeback")
    public CompletableFuture<ChargebackResult> processChargeback(
            String merchantId, 
            PaymentChargebackEvent chargebackEvent) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String sagaId = UUID.randomUUID().toString();
        
        log.info("Processing chargeback {} for merchant {} with saga {}", 
            chargebackEvent.getChargebackId(), merchantId, sagaId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Validate merchant account
                MerchantAccountStatus merchantStatus = getMerchantStatus(merchantId);
                if (!merchantStatus.isActive()) {
                    throw new MerchantAccountException("Merchant account is not active: " + merchantStatus.getStatus());
                }
                
                // Step 2: Calculate chargeback amounts
                ChargebackCalculation calculation = calculateChargebackAmounts(
                    merchantId, 
                    chargebackEvent.getAmount()
                );
                
                // Step 3: Check merchant balance
                MerchantBalance merchantBalance = getMerchantBalance(merchantId);
                if (!merchantBalance.canDebit(calculation.getTotalAmount())) {
                    return handleInsufficientFunds(merchantId, calculation, chargebackEvent);
                }
                
                // Step 4: Start saga for distributed transaction
                ChargebackSaga saga = ChargebackSaga.builder()
                    .sagaId(sagaId)
                    .merchantId(merchantId)
                    .chargebackEvent(chargebackEvent)
                    .calculation(calculation)
                    .build();
                
                sagaOrchestrator.startSaga(saga);
                
                // Step 5: Place hold on merchant funds
                HoldResult holdResult = placeMerchantHold(
                    merchantId, 
                    calculation.getTotalAmount(), 
                    "CHARGEBACK_" + chargebackEvent.getChargebackId()
                );
                
                saga.addCompensation(() -> releaseMerchantHold(merchantId, holdResult.getHoldId()));
                
                // Step 6: Debit merchant account via merchant-payment-service
                DebitResult debitResult = debitMerchantAccount(
                    merchantId,
                    calculation,
                    chargebackEvent
                );
                
                saga.addCompensation(() -> reverseMerchantDebit(merchantId, debitResult.getTransactionId()));
                
                // Step 7: Create reserve for future chargebacks
                if (calculation.getReserveAmount().compareTo(BigDecimal.ZERO) > 0) {
                    ReserveResult reserveResult = createMerchantReserve(
                        merchantId,
                        calculation.getReserveAmount(),
                        "CHARGEBACK_RESERVE"
                    );
                    saga.addCompensation(() -> releaseMerchantReserve(merchantId, reserveResult.getReserveId()));
                }
                
                // Step 8: Update merchant risk profile
                updateMerchantRiskProfile(merchantId, chargebackEvent);
                
                // Step 9: Send notifications
                sendChargebackNotifications(merchantId, chargebackEvent, calculation);
                
                // Step 10: Publish events
                publishChargebackProcessedEvent(merchantId, chargebackEvent, calculation);
                
                // Complete saga
                saga.complete();
                sagaOrchestrator.completeSaga(sagaId);
                
                // Update metrics
                chargebackProcessedCounter.increment();
                merchantOperationTimer.stop(sample);

                return ChargebackResult.success(
                    debitResult.getTransactionId(),
                    calculation,
                    debitResult.getNewBalance()
                );
                
            } catch (Exception e) {
                log.error("Error processing chargeback for merchant {}: {}", merchantId, e.getMessage(), e);
                
                // Compensate saga on failure
                sagaOrchestrator.compensateSaga(sagaId);

                chargebackFailedCounter.increment();
                merchantOperationTimer.stop(sample);

                throw new MerchantAccountException("Failed to process chargeback: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Process dispute against merchant account
     */
    @Transactional
    @CircuitBreaker(name = "merchant-dispute")
    @Retry(name = "merchant-dispute")
    public CompletableFuture<DisputeResult> processDispute(
            String merchantId,
            PaymentDisputeEvent disputeEvent) {
        
        log.info("Processing dispute {} for merchant {}", disputeEvent.getDisputeId(), merchantId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate merchant
                MerchantAccountStatus status = getMerchantStatus(merchantId);
                if (!status.canProcessDisputes()) {
                    throw new MerchantAccountException("Merchant cannot process disputes in status: " + status.getStatus());
                }
                
                // Calculate dispute hold amount (typically 110% of disputed amount)
                BigDecimal holdAmount = disputeEvent.getAmount().multiply(disputeHoldPercentage);
                
                // Place hold on funds
                HoldResult holdResult = placeMerchantHold(
                    merchantId,
                    holdAmount,
                    "DISPUTE_" + disputeEvent.getDisputeId()
                );
                
                // Create dispute record in merchant-payment-service
                CreateDisputeRequest disputeRequest = CreateDisputeRequest.builder()
                    .merchantId(merchantId)
                    .disputeId(disputeEvent.getDisputeId())
                    .transactionId(disputeEvent.getTransactionId())
                    .amount(disputeEvent.getAmount())
                    .reason(disputeEvent.getReason())
                    .holdId(holdResult.getHoldId())
                    .build();
                
                DisputeResponse disputeResponse = merchantPaymentServiceClient.createDispute(disputeRequest);
                
                // Send notification to merchant
                sendDisputeNotification(merchantId, disputeEvent);
                
                // Publish event
                publishDisputeProcessedEvent(merchantId, disputeEvent, holdResult);
                
                disputeProcessedCounter.increment();
                
                return DisputeResult.success(
                    disputeResponse.getDisputeRecordId(),
                    holdResult.getHoldId(),
                    holdAmount
                );
                
            } catch (Exception e) {
                log.error("Error processing dispute for merchant {}: {}", merchantId, e.getMessage(), e);
                throw new MerchantAccountException("Failed to process dispute: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Get merchant account details from merchant-service
     */
    @Cacheable(value = "merchantAccount", key = "#merchantId")
    @CircuitBreaker(name = "merchant-service")
    public MerchantAccount getMerchantAccount(String merchantId) {
        log.debug("Fetching merchant account for {}", merchantId);
        
        return merchantServiceClient.getMerchantAccount(merchantId)
            .orElseThrow(() -> new MerchantAccountException("Merchant account not found: " + merchantId));
    }
    
    /**
     * Get merchant status with caching
     */
    @Cacheable(value = "merchantStatus", key = "#merchantId")
    public MerchantAccountStatus getMerchantStatus(String merchantId) {
        String cacheKey = MERCHANT_STATUS_PREFIX + merchantId;
        
        // Try cache first
        MerchantAccountStatus cached = (MerchantAccountStatus) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Fetch from merchant-service
        MerchantAccountStatus status = merchantServiceClient.getMerchantStatus(merchantId);
        
        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, status, cacheTtlMinutes, TimeUnit.MINUTES);
        
        return status;
    }
    
    /**
     * Get merchant balance from merchant-payment-service
     */
    @CircuitBreaker(name = "merchant-payment-service")
    public MerchantBalance getMerchantBalance(String merchantId) {
        String cacheKey = MERCHANT_BALANCE_PREFIX + merchantId;
        
        // Check cache
        MerchantBalance cached = (MerchantBalance) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isStale()) {
            return cached;
        }
        
        // Fetch from merchant-payment-service
        MerchantBalance balance = merchantPaymentServiceClient.getMerchantBalance(merchantId);
        
        // Cache for short duration (balance changes frequently)
        redisTemplate.opsForValue().set(cacheKey, balance, 1, TimeUnit.MINUTES);
        
        return balance;
    }
    
    /**
     * Debit merchant account through merchant-payment-service
     */
    @Transactional
    @Retry(name = "merchant-debit")
    @TimeLimiter(name = "merchant-debit")
    public DebitResult debitMerchantAccount(
            String merchantId,
            ChargebackCalculation calculation,
            PaymentChargebackEvent chargebackEvent) {
        
        log.info("Debiting merchant {} for chargeback {} amount {}", 
            merchantId, chargebackEvent.getChargebackId(), calculation.getTotalAmount());
        
        DebitMerchantRequest request = DebitMerchantRequest.builder()
            .merchantId(merchantId)
            .amount(calculation.getTotalAmount())
            .chargebackAmount(calculation.getChargebackAmount())
            .chargebackFee(calculation.getChargebackFee())
            .processingFee(calculation.getProcessingFee())
            .reason("CHARGEBACK")
            .referenceType("CHARGEBACK")
            .referenceId(chargebackEvent.getChargebackId())
            .originalTransactionId(chargebackEvent.getTransactionId())
            .metadata(buildChargebackMetadata(chargebackEvent))
            .idempotencyKey(generateIdempotencyKey(merchantId, chargebackEvent.getChargebackId()))
            .build();
        
        DebitMerchantResponse response = merchantPaymentServiceClient.debitMerchant(request);
        
        if (!response.isSuccess()) {
            throw new MerchantAccountException("Failed to debit merchant account: " + response.getError());
        }
        
        // Clear balance cache after debit
        clearMerchantBalanceCache(merchantId);
        
        return DebitResult.builder()
            .transactionId(response.getTransactionId())
            .newBalance(response.getNewBalance())
            .timestamp(response.getTimestamp())
            .build();
    }
    
    /**
     * Place hold on merchant funds
     */
    @Transactional
    public HoldResult placeMerchantHold(String merchantId, BigDecimal amount, String reason) {
        log.debug("Placing hold of {} on merchant {} for {}", amount, merchantId, reason);
        
        PlaceHoldRequest request = PlaceHoldRequest.builder()
            .merchantId(merchantId)
            .amount(amount)
            .reason(reason)
            .expiryHours(72) // 3 days default
            .build();
        
        PlaceHoldResponse response = merchantPaymentServiceClient.placeHold(request);
        
        return HoldResult.builder()
            .holdId(response.getHoldId())
            .amount(response.getAmount())
            .expiryTime(response.getExpiryTime())
            .build();
    }
    
    /**
     * Release merchant hold
     */
    @Async
    public CompletableFuture<Void> releaseMerchantHold(String merchantId, String holdId) {
        return CompletableFuture.runAsync(() -> {
            try {
                merchantPaymentServiceClient.releaseHold(merchantId, holdId);
                log.debug("Released hold {} for merchant {}", holdId, merchantId);
            } catch (Exception e) {
                log.error("Failed to release hold {} for merchant {}: {}", holdId, merchantId, e.getMessage());
            }
        });
    }
    
    /**
     * Create merchant reserve
     */
    public ReserveResult createMerchantReserve(String merchantId, BigDecimal amount, String type) {
        CreateReserveRequest request = CreateReserveRequest.builder()
            .merchantId(merchantId)
            .amount(amount)
            .type(type)
            .duration(180) // 180 days for chargeback reserves
            .build();
        
        CreateReserveResponse response = merchantPaymentServiceClient.createReserve(request);
        
        return ReserveResult.builder()
            .reserveId(response.getReserveId())
            .amount(response.getAmount())
            .releaseDate(response.getReleaseDate())
            .build();
    }
    
    /**
     * Update merchant risk profile after chargeback
     */
    @Async
    public void updateMerchantRiskProfile(String merchantId, PaymentChargebackEvent chargebackEvent) {
        try {
            UpdateRiskProfileRequest request = UpdateRiskProfileRequest.builder()
                .merchantId(merchantId)
                .eventType("CHARGEBACK")
                .eventId(chargebackEvent.getChargebackId())
                .amount(chargebackEvent.getAmount())
                .reasonCode(chargebackEvent.getReasonCode())
                .timestamp(LocalDateTime.now())
                .build();
            
            merchantServiceClient.updateRiskProfile(request);
            
            log.debug("Updated risk profile for merchant {} after chargeback {}", 
                merchantId, chargebackEvent.getChargebackId());
            
        } catch (Exception e) {
            log.error("Failed to update risk profile for merchant {}: {}", merchantId, e.getMessage());
        }
    }
    
    /**
     * Calculate chargeback amounts including fees
     */
    private ChargebackCalculation calculateChargebackAmounts(String merchantId, BigDecimal chargebackAmount) {
        // Get merchant fee structure from merchant-service
        MerchantFeeStructure feeStructure = merchantServiceClient.getMerchantFeeStructure(merchantId);
        
        BigDecimal processingFee = chargebackAmount
            .multiply(feeStructure.getChargebackProcessingRate())
            .setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal reserveAmount = chargebackAmount
            .multiply(chargebackReservePercentage)
            .setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal totalAmount = chargebackAmount
            .add(chargebackFee)
            .add(processingFee);
        
        return ChargebackCalculation.builder()
            .chargebackAmount(chargebackAmount)
            .chargebackFee(chargebackFee)
            .processingFee(processingFee)
            .reserveAmount(reserveAmount)
            .totalAmount(totalAmount)
            .build();
    }
    
    /**
     * Handle insufficient funds scenario
     */
    private ChargebackResult handleInsufficientFunds(
            String merchantId, 
            ChargebackCalculation calculation,
            PaymentChargebackEvent chargebackEvent) {
        
        log.warn("Insufficient funds for merchant {} chargeback {}", merchantId, chargebackEvent.getChargebackId());
        
        // Create negative balance record
        CreateNegativeBalanceRequest request = CreateNegativeBalanceRequest.builder()
            .merchantId(merchantId)
            .amount(calculation.getTotalAmount())
            .reason("CHARGEBACK_INSUFFICIENT_FUNDS")
            .referenceId(chargebackEvent.getChargebackId())
            .build();
        
        merchantPaymentServiceClient.createNegativeBalance(request);
        
        // Flag merchant account
        merchantServiceClient.flagMerchant(merchantId, "INSUFFICIENT_FUNDS_CHARGEBACK");
        
        // Publish event for collections
        publishInsufficientFundsEvent(merchantId, calculation, chargebackEvent);
        
        return ChargebackResult.insufficientFunds(
            "Insufficient funds to process chargeback",
            calculation.getTotalAmount()
        );
    }
    
    /**
     * Send chargeback notifications
     */
    @Async
    private void sendChargebackNotifications(
            String merchantId,
            PaymentChargebackEvent chargebackEvent,
            ChargebackCalculation calculation) {
        
        MerchantChargebackNotification notification = MerchantChargebackNotification.builder()
            .merchantId(merchantId)
            .chargebackId(chargebackEvent.getChargebackId())
            .amount(calculation.getTotalAmount())
            .chargebackAmount(calculation.getChargebackAmount())
            .fees(calculation.getTotalFees())
            .reason(chargebackEvent.getReason())
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send("merchant-notifications", notification);
    }
    
    /**
     * Publish chargeback processed event
     */
    private void publishChargebackProcessedEvent(
            String merchantId,
            PaymentChargebackEvent chargebackEvent,
            ChargebackCalculation calculation) {
        
        MerchantChargebackProcessedEvent event = MerchantChargebackProcessedEvent.builder()
            .merchantId(merchantId)
            .chargebackId(chargebackEvent.getChargebackId())
            .transactionId(chargebackEvent.getTransactionId())
            .amount(calculation.getTotalAmount())
            .timestamp(LocalDateTime.now())
            .build();
        
        streamBridge.send("merchant-chargeback-processed", event);
    }
    
    /**
     * Clear merchant balance cache
     */
    @CacheEvict(value = "merchantBalance", key = "#merchantId")
    public void clearMerchantBalanceCache(String merchantId) {
        String cacheKey = MERCHANT_BALANCE_PREFIX + merchantId;
        redisTemplate.delete(cacheKey);
    }
    
    /**
     * Generate idempotency key for operations
     */
    private String generateIdempotencyKey(String merchantId, String operationId) {
        return String.format("%s_%s_%d", merchantId, operationId, System.currentTimeMillis());
    }
    
    /**
     * Build chargeback metadata
     */
    private Map<String, Object> buildChargebackMetadata(PaymentChargebackEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chargebackId", event.getChargebackId());
        metadata.put("transactionId", event.getTransactionId());
        metadata.put("reason", event.getReason());
        metadata.put("reasonCode", event.getReasonCode());
        metadata.put("initiatedAt", event.getInitiatedAt());
        metadata.put("cardNetwork", event.getCardNetwork());
        return metadata;
    }
    
    /**
     * Fallback method for chargeback processing
     */
    public CompletableFuture<ChargebackResult> processChargebackFallback(
            String merchantId,
            PaymentChargebackEvent chargebackEvent,
            Exception ex) {
        
        log.error("Chargeback processing failed, using fallback for merchant {}: {}", 
            merchantId, ex.getMessage());
        
        // Queue for manual processing
        QueuedChargeback queued = QueuedChargeback.builder()
            .merchantId(merchantId)
            .chargebackEvent(chargebackEvent)
            .failureReason(ex.getMessage())
            .queuedAt(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send("chargeback-manual-queue", queued);
        
        return CompletableFuture.completedFuture(
            ChargebackResult.queued("Chargeback queued for manual processing due to system issues")
        );
    }
}