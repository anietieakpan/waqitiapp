package com.waqiti.crypto.lightning.service;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ErrorCode;
import com.waqiti.crypto.lightning.LightningNetworkService;
import com.waqiti.crypto.lightning.LightningNetworkService.PaymentResult;
import com.waqiti.crypto.lightning.LightningNetworkService.SubmarineSwapResult;
import com.waqiti.crypto.lightning.entity.*;
import com.waqiti.crypto.lightning.repository.*;
import com.waqiti.crypto.dto.lightning.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade service for managing Lightning payments
 * Handles payment processing, tracking, and analytics
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LightningPaymentService {

    @Lazy
    private final LightningPaymentService self;
    private final PaymentRepository paymentRepository;
    private final StreamRepository streamRepository;
    private final SwapRepository swapRepository;
    private final LnurlRepository lnurlRepository;
    private final LightningAddressRepository addressRepository;
    private final LightningNetworkService lightningService;
    private final MeterRegistry meterRegistry;
    
    private final ConcurrentHashMap<String, PaymentTracking> activePayments = new ConcurrentHashMap<>();
    private Counter paymentSuccessCounter;
    private Counter paymentFailureCounter;
    private Timer paymentDurationTimer;

    @jakarta.annotation.PostConstruct
    public void init() {
        paymentSuccessCounter = Counter.builder("lightning.payment.success")
            .description("Number of successful Lightning payments")
            .register(meterRegistry);
            
        paymentFailureCounter = Counter.builder("lightning.payment.failure")
            .description("Number of failed Lightning payments")
            .register(meterRegistry);
            
        paymentDurationTimer = Timer.builder("lightning.payment.duration")
            .description("Lightning payment processing duration")
            .register(meterRegistry);
    }

    /**
     * Validate a Lightning invoice
     */
    public InvoiceValidation validateInvoice(String paymentRequest) {
        try {
            Map<String, Object> decoded = lightningService.decodePaymentRequest(paymentRequest);
            
            InvoiceValidation validation = new InvoiceValidation();
            validation.setValid(true);
            validation.setDestination((String) decoded.get("destination"));
            validation.setAmountSat((Long) decoded.get("amount"));
            validation.setDescription((String) decoded.get("description"));
            validation.setPaymentHash((String) decoded.get("paymentHash"));
            validation.setExpiry((Long) decoded.get("expiry"));
            
            // Check if expired
            Long timestamp = (Long) decoded.get("timestamp");
            Long expiry = (Long) decoded.get("expiry");
            if (timestamp != null && expiry != null) {
                if (Instant.now().getEpochSecond() > timestamp + expiry) {
                    validation.setValid(false);
                    validation.setError("Invoice has expired");
                }
            }
            
            // Check if already paid
            if (paymentRepository.existsByPaymentHash(validation.getPaymentHash())) {
                validation.setValid(false);
                validation.setError("Invoice already paid");
            }
            
            return validation;
            
        } catch (Exception e) {
            log.error("Error validating invoice", e);
            InvoiceValidation validation = new InvoiceValidation();
            validation.setValid(false);
            validation.setError("Invalid payment request: " + e.getMessage());
            return validation;
        }
    }

    /**
     * Save a successful payment
     */
    public PaymentEntity savePayment(PaymentResult result, String userId) {
        log.info("Saving payment for user: {}, hash: {}", userId, result.getPaymentHash());
        
        PaymentEntity payment = PaymentEntity.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .paymentHash(result.getPaymentHash())
            .paymentPreimage(result.getPaymentPreimage())
            .amountSat(result.getAmountSat())
            .feeSat(result.getFeeSat())
            .status(PaymentStatus.COMPLETED)
            .type(PaymentType.INVOICE)
            .createdAt(Instant.now())
            .completedAt(Instant.now())
            .route(serializeRoute(result.getRoute()))
            .hopCount(result.getRoute() != null ? result.getRoute().size() : 0)
            .build();
        
        payment = paymentRepository.save(payment);
        
        // Update metrics
        paymentSuccessCounter.increment();
        
        log.info("Payment {} saved successfully", payment.getId());
        return payment;
    }

    /**
     * Save a keysend payment
     */
    public PaymentEntity saveKeysendPayment(PaymentResult result, KeysendRequest request, String userId) {
        log.info("Saving keysend payment for user: {}", userId);
        
        PaymentEntity payment = PaymentEntity.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .destinationPubkey(request.getDestinationPubkey())
            .paymentHash(result.getPaymentHash())
            .paymentPreimage(result.getPaymentPreimage())
            .amountSat(request.getAmountSat())
            .feeSat(result.getFeeSat())
            .status(PaymentStatus.COMPLETED)
            .type(PaymentType.KEYSEND)
            .customData(request.getCustomData())
            .createdAt(Instant.now())
            .completedAt(Instant.now())
            .route(serializeRoute(result.getRoute()))
            .hopCount(result.getRoute() != null ? result.getRoute().size() : 0)
            .build();
        
        payment = paymentRepository.save(payment);
        paymentSuccessCounter.increment();
        
        return payment;
    }

    /**
     * Get payment by ID
     */
    @Cacheable(value = "payments", key = "#paymentId")
    public PaymentEntity getPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Payment not found"));
    }

    /**
     * Search payments with filters
     */
    public Page<PaymentEntity> searchPayments(PaymentFilter filter, Pageable pageable) {
        Specification<PaymentEntity> spec = buildPaymentSpecification(filter);
        return paymentRepository.findAll(spec, pageable);
    }

    /**
     * Save a payment stream
     */
    public StreamEntity savePaymentStream(String streamId, StartStreamRequest request, String userId) {
        log.info("Saving payment stream: {} for user: {}", streamId, userId);
        
        StreamEntity stream = StreamEntity.builder()
            .id(streamId)
            .userId(userId)
            .destination(request.getDestination())
            .amountPerInterval(request.getAmountPerInterval())
            .interval(request.getInterval())
            .totalDuration(request.getTotalDuration())
            .status(StreamStatus.ACTIVE)
            .startedAt(Instant.now())
            .totalPaid(0L)
            .paymentCount(0)
            .build();
        
        return streamRepository.save(stream);
    }

    /**
     * Get stream by ID
     */
    public StreamEntity getStream(String streamId) {
        return streamRepository.findById(streamId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Stream not found"));
    }

    /**
     * Stop a payment stream and return statistics
     */
    public StreamStatistics stopStream(String streamId) {
        StreamEntity stream = self.getStream(streamId);
        stream.setStatus(StreamStatus.STOPPED);
        stream.setStoppedAt(Instant.now());
        streamRepository.save(stream);
        
        StreamStatistics stats = new StreamStatistics();
        stats.setTotalPayments(stream.getPaymentCount());
        stats.setTotalAmount(stream.getTotalPaid());
        stats.setDuration(java.time.Duration.between(stream.getStartedAt(), Instant.now()));
        
        return stats;
    }

    /**
     * Get user's payment streams
     */
    public List<StreamEntity> getUserStreams(String userId, boolean activeOnly) {
        if (activeOnly) {
            return streamRepository.findByUserIdAndStatus(userId, StreamStatus.ACTIVE);
        } else {
            return streamRepository.findByUserId(userId);
        }
    }

    /**
     * Generate LNURL-pay code
     */
    public String generateLnurlPay(String userId, long minSendable, long maxSendable, 
                                   String metadata, String callbackUrl) {
        log.info("Generating LNURL-pay for user: {}", userId);
        
        String k = UUID.randomUUID().toString();
        
        LnurlEntity lnurl = LnurlEntity.builder()
            .id(k)
            .userId(userId)
            .type(LnurlType.PAY)
            .minSendable(minSendable)
            .maxSendable(maxSendable)
            .metadata(metadata)
            .callbackUrl(callbackUrl != null ? callbackUrl : generateDefaultCallbackUrl(k))
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        
        lnurlRepository.save(lnurl);
        
        // Encode URL as LNURL
        String url = lnurl.getCallbackUrl() + "?k=" + k;
        return "lightning:" + Base64.getEncoder().encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get LNURL-pay parameters
     */
    public Map<String, Object> getLnurlPayParams(String k) {
        LnurlEntity lnurl = lnurlRepository.findById(k)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "LNURL not found"));
        
        if (lnurl.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED, "LNURL has expired");
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("minSendable", lnurl.getMinSendable());
        params.put("maxSendable", lnurl.getMaxSendable());
        params.put("metadata", lnurl.getMetadata());
        params.put("callback", lnurl.getCallbackUrl());
        params.put("tag", "payRequest");
        
        return params;
    }

    /**
     * Create invoice for LNURL payment
     */
    public String createLnurlInvoice(String k, long amount) {
        LnurlEntity lnurl = lnurlRepository.findById(k)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "LNURL not found"));
        
        if (amount < lnurl.getMinSendable() || amount > lnurl.getMaxSendable()) {
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Amount out of range");
        }
        
        // Create Lightning invoice
        var invoice = lightningService.createInvoice(
            amount / 1000, // Convert millisats to sats
            lnurl.getMetadata(),
            600,
            Map.of("lnurlId", k)
        );
        
        // Update LNURL usage
        lnurl.setUsageCount(lnurl.getUsageCount() + 1);
        lnurl.setLastUsedAt(Instant.now());
        lnurlRepository.save(lnurl);
        
        return invoice.getPaymentRequest();
    }

    /**
     * Check if Lightning address is taken
     */
    public boolean isAddressTaken(String username) {
        return addressRepository.existsByUsername(username);
    }

    /**
     * Save Lightning address mapping
     */
    public void saveLightningAddress(String userId, String lightningAddress) {
        String username = lightningAddress.split("@")[0];
        
        LightningAddressEntity address = LightningAddressEntity.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .username(username)
            .domain("pay.waqiti.com")
            .fullAddress(lightningAddress)
            .isActive(true)
            .createdAt(Instant.now())
            .build();
        
        addressRepository.save(address);
    }

    /**
     * Save submarine swap
     */
    public SwapEntity saveSwap(SubmarineSwapResult swap, String userId) {
        log.info("Saving submarine swap: {} for user: {}", swap.getSwapId(), userId);
        
        SwapEntity entity = SwapEntity.builder()
            .id(swap.getSwapId())
            .userId(userId)
            .type(SwapType.SUBMARINE)
            .onchainAddress(swap.getOnchainAddress())
            .lightningInvoice(swap.getLightningInvoice())
            .amountSat(swap.getAmountSat())
            .estimatedFee(swap.getEstimatedFee())
            .status(SwapStatus.PENDING)
            .expiry(swap.getExpiry())
            .createdAt(Instant.now())
            .build();
        
        return swapRepository.save(entity);
    }

    /**
     * Get swap by ID
     */
    public SwapEntity getSwap(String swapId) {
        return swapRepository.findById(swapId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Swap not found"));
    }

    /**
     * Update swap status
     */
    @CacheEvict(value = "swaps", key = "#swapId")
    public SwapEntity updateSwapStatus(String swapId, SwapStatus newStatus) {
        SwapEntity swap = self.getSwap(swapId);
        swap.setStatus(newStatus);
        swap.setUpdatedAt(Instant.now());
        
        if (newStatus == SwapStatus.COMPLETED) {
            swap.setCompletedAt(Instant.now());
        }
        
        return swapRepository.save(swap);
    }

    /**
     * Process streaming payments
     */
    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void processStreamingPayments() {
        log.debug("Processing streaming payments");
        
        List<StreamEntity> activeStreams = streamRepository.findByStatus(StreamStatus.ACTIVE);
        
        for (StreamEntity stream : activeStreams) {
            try {
                if (shouldProcessStreamPayment(stream)) {
                    processStreamPayment(stream);
                }
            } catch (Exception e) {
                log.error("Error processing stream: {}", stream.getId(), e);
            }
        }
    }

    /**
     * Monitor swap confirmations
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void monitorSwapConfirmations() {
        log.debug("Monitoring swap confirmations");
        
        List<SwapEntity> pendingSwaps = swapRepository.findByStatus(SwapStatus.PENDING);
        
        for (SwapEntity swap : pendingSwaps) {
            try {
                checkSwapConfirmations(swap);
            } catch (Exception e) {
                log.error("Error checking swap: {}", swap.getId(), e);
            }
        }
    }

    // Helper methods

    private Specification<PaymentEntity> buildPaymentSpecification(PaymentFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filter.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), filter.getUserId()));
            }
            
            if (filter.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), filter.getType()));
            }
            
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }
            
            if (filter.getFromDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"), filter.getFromDate().toInstant(ZoneOffset.UTC)));
            }
            
            if (filter.getToDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("createdAt"), filter.getToDate().toInstant(ZoneOffset.UTC)));
            }
            
            if (filter.getMinAmount() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("amountSat"), filter.getMinAmount()));
            }
            
            if (filter.getMaxAmount() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("amountSat"), filter.getMaxAmount()));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String serializeRoute(List<?> route) {
        if (route == null || route.isEmpty()) {
            log.warn("CRITICAL: Lightning route is null or empty - payment routing may fail");
            return "[]"; // Return empty JSON array instead of null
        }
        try {
            return route.stream()
                .map(Object::toString)
                .collect(Collectors.joining(" -> "));
        } catch (Exception e) {
            return route.toString();
        }
    }

    private String generateDefaultCallbackUrl(String k) {
        return "https://api.example.com/api/v1/lightning/lnurl/pay/callback";
    }

    private boolean shouldProcessStreamPayment(StreamEntity stream) {
        if (stream.getLastPaymentAt() == null) {
            return true;
        }
        
        java.time.Duration interval = java.time.Duration.parse(stream.getInterval());
        Instant nextPaymentTime = stream.getLastPaymentAt().plus(interval);
        
        return Instant.now().isAfter(nextPaymentTime);
    }

    private void processStreamPayment(StreamEntity stream) {
        log.info("Processing stream payment for stream: {}", stream.getId());
        
        try {
            // Execute payment
            PaymentResult result = lightningService.sendKeysend(
                stream.getDestination(),
                stream.getAmountPerInterval(),
                ("Stream payment " + stream.getId()).getBytes()
            );
            
            if (result.isSuccess()) {
                // Update stream
                stream.setLastPaymentAt(Instant.now());
                stream.setPaymentCount(stream.getPaymentCount() + 1);
                stream.setTotalPaid(stream.getTotalPaid() + stream.getAmountPerInterval());
                
                // Check if stream should end
                java.time.Duration totalDuration = java.time.Duration.parse(stream.getTotalDuration());
                if (java.time.Duration.between(stream.getStartedAt(), Instant.now()).compareTo(totalDuration) > 0) {
                    stream.setStatus(StreamStatus.COMPLETED);
                    stream.setCompletedAt(Instant.now());
                }
                
                streamRepository.save(stream);
                
                log.info("Stream payment successful for stream: {}", stream.getId());
            } else {
                log.error("Stream payment failed for stream: {}, error: {}", 
                    stream.getId(), result.getError());
                
                stream.setFailureCount(stream.getFailureCount() + 1);
                if (stream.getFailureCount() > 5) {
                    stream.setStatus(StreamStatus.FAILED);
                }
                streamRepository.save(stream);
            }
        } catch (Exception e) {
            log.error("Error processing stream payment: {}", stream.getId(), e);
        }
    }

    private void checkSwapConfirmations(SwapEntity swap) {
        // Check on-chain confirmations
        Map<String, Object> txInfo = lightningService.getOnchainTransaction(swap.getOnchainTxId());
        
        if (txInfo != null) {
            Integer confirmations = (Integer) txInfo.get("confirmations");
            swap.setConfirmations(confirmations != null ? confirmations : 0);
            
            if (swap.getConfirmations() >= 3) {
                // Execute Lightning payment
                try {
                    PaymentResult result = lightningService.payInvoice(swap.getLightningInvoice(), null);
                    
                    if (result.isSuccess()) {
                        swap.setStatus(SwapStatus.COMPLETED);
                        swap.setLightningPaymentHash(result.getPaymentHash());
                        swap.setCompletedAt(Instant.now());
                        log.info("Swap {} completed successfully", swap.getId());
                    } else {
                        swap.setStatus(SwapStatus.FAILED);
                        swap.setError(result.getError());
                        log.error("Swap {} failed: {}", swap.getId(), result.getError());
                    }
                } catch (Exception e) {
                    swap.setStatus(SwapStatus.FAILED);
                    swap.setError(e.getMessage());
                    log.error("Error completing swap: {}", swap.getId(), e);
                }
            }
            
            swapRepository.save(swap);
        }
    }

    /**
     * Payment tracking for monitoring
     */
    private static class PaymentTracking {
        private final String paymentId;
        private final Instant startTime;
        private PaymentStatus status;
        private String error;
        
        public PaymentTracking(String paymentId) {
            this.paymentId = paymentId;
            this.startTime = Instant.now();
            this.status = PaymentStatus.PENDING;
        }
    }

    /**
     * Invoice validation result
     */
    @lombok.Data
    public static class InvoiceValidation {
        private boolean valid;
        private String error;
        private String destination;
        private Long amountSat;
        private String description;
        private String paymentHash;
        private Long expiry;
    }

    /**
     * Stream statistics
     */
    @lombok.Data
    public static class StreamStatistics {
        private int totalPayments;
        private long totalAmount;
        private java.time.Duration duration;
    }
}