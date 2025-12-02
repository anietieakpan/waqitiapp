package com.waqiti.merchant.service;

import com.waqiti.common.cache.CacheService;
import com.waqiti.common.cache.DistributedLockService;
import com.waqiti.common.event.EventPublisher;

// Import UnifiedPaymentService
import com.waqiti.payment.core.UnifiedPaymentService;
import com.waqiti.payment.core.model.*;
import com.waqiti.merchant.domain.Merchant;
import com.waqiti.merchant.domain.MerchantPayment;
import com.waqiti.merchant.dto.PaymentRequest;
import com.waqiti.merchant.dto.PaymentResponse;
import com.waqiti.merchant.dto.PaymentRefundRequest;
import com.waqiti.merchant.dto.PaymentCaptureRequest;
import com.waqiti.merchant.repository.MerchantRepository;
import com.waqiti.merchant.repository.MerchantPaymentRepository;
import com.waqiti.merchant.event.PaymentCreatedEvent;
import com.waqiti.merchant.event.PaymentCompletedEvent;
import com.waqiti.merchant.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantPaymentService {

    private final MerchantPaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentProcessorService paymentProcessorService;
    private final FraudDetectionService fraudDetectionService;
    private final RiskAssessmentService riskAssessmentService;
    private final CacheService cacheService;
    private final DistributedLockService lockService;
    private final EventPublisher eventPublisher;

    @Transactional
    public PaymentResponse createPayment(String merchantId, PaymentRequest request) {
        log.info("Creating payment for merchant: {} amount: {}", merchantId, request.getAmount());

        // Find merchant
        Merchant merchant = findMerchantByMerchantId(merchantId);
        validateMerchantCanAcceptPayments(merchant);

        // Validate payment request
        validatePaymentRequest(request, merchant);

        // Perform fraud detection
        Map<String, Object> fraudAssessment = fraudDetectionService.assessPayment(request, merchant);
        int fraudScore = (Integer) fraudAssessment.getOrDefault("score", 0);
        
        if (fraudScore > 85) {
            throw new IllegalStateException("Payment rejected due to high fraud risk");
        }

        // Calculate fees
        BigDecimal feeAmount = calculateProcessingFee(request.getAmount(), merchant);
        BigDecimal netAmount = request.getAmount().subtract(feeAmount);

        // Create payment entity
        MerchantPayment payment = MerchantPayment.builder()
                .merchantId(merchant.getId())
                .customerId(request.getCustomerId())
                .orderId(request.getOrderId())
                .externalReference(request.getExternalReference())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .taxAmount(request.getTaxAmount())
                .tipAmount(request.getTipAmount())
                .paymentMethod(MerchantPayment.PaymentMethod.valueOf(request.getPaymentMethod()))
                .description(request.getDescription())
                .lineItems(request.getLineItems())
                .customerInfo(request.getCustomerInfo())
                .paymentDetails(request.getPaymentDetails())
                .metadata(request.getMetadata())
                .receiptEmail(request.getReceiptEmail())
                .receiptPhone(request.getReceiptPhone())
                .refundPolicy(request.getRefundPolicy())
                .expiresAt(request.getExpiresAt())
                .riskAssessment(fraudAssessment)
                .fraudScore(fraudScore)
                .chargebackRisk(riskAssessmentService.assessChargebackRisk(request, merchant))
                .deviceFingerprint(request.getDeviceFingerprint())
                .ipAddress(request.getIpAddress())
                .userAgent(request.getUserAgent())
                .locationData(request.getLocationData())
                .build();

        payment = paymentRepository.save(payment);

        // Cache payment
        cachePayment(payment);

        // Publish event
        eventPublisher.publish(PaymentCreatedEvent.builder()
                .paymentId(payment.getId())
                .merchantId(merchant.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod().name())
                .fraudScore(fraudScore)
                .build());

        log.info("Payment created: {} for merchant: {}", payment.getPaymentId(), merchantId);
        
        return mapToPaymentResponse(payment, merchant);
    }

    @Transactional
    public PaymentResponse authorizePayment(String paymentId) {
        log.info("Authorizing payment: {}", paymentId);

        String lockKey = "payment:auth:" + paymentId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {
            
            MerchantPayment payment = findPaymentByPaymentId(paymentId);
            
            if (payment.getStatus() != MerchantPayment.PaymentStatus.PENDING) {
                throw new IllegalStateException("Payment cannot be authorized in current status: " + payment.getStatus());
            }

            // Check expiration
            if (payment.getExpiresAt() != null && LocalDateTime.now().isAfter(payment.getExpiresAt())) {
                payment.setStatus(MerchantPayment.PaymentStatus.FAILED);
                payment.setFailedAt(LocalDateTime.now());
                payment.setFailureReason("Payment expired");
                payment.setFailureCode("EXPIRED");
                paymentRepository.save(payment);
                throw new IllegalStateException("Payment has expired");
            }

            try {
                // Process authorization with payment processor
                Map<String, Object> authResult = paymentProcessorService.authorizePayment(payment);
                
                payment.setStatus(MerchantPayment.PaymentStatus.AUTHORIZED);
                payment.setAuthorizedAt(LocalDateTime.now());
                payment.setProcessorTransactionId((String) authResult.get("transactionId"));
                payment.setAuthorizationCode((String) authResult.get("authorizationCode"));
                
                payment = paymentRepository.save(payment);
                
                // Update cache
                cachePayment(payment);
                
                log.info("Payment authorized: {}", paymentId);
                
                return mapToPaymentResponse(payment, findMerchantById(payment.getMerchantId()));
                
            } catch (Exception e) {
                log.error("Payment authorization failed: {}", paymentId, e);
                
                payment.setStatus(MerchantPayment.PaymentStatus.FAILED);
                payment.setFailedAt(LocalDateTime.now());
                payment.setFailureReason(e.getMessage());
                payment.setFailureCode("AUTH_FAILED");
                paymentRepository.save(payment);
                
                // Publish failure event
                eventPublisher.publish(PaymentFailedEvent.builder()
                        .paymentId(payment.getId())
                        .reason(e.getMessage())
                        .build());
                
                throw new RuntimeException("Payment authorization failed: " + e.getMessage());
            }
        });
    }

    @Transactional
    public PaymentResponse capturePayment(String paymentId, PaymentCaptureRequest request) {
        log.info("Capturing payment: {} amount: {}", paymentId, request.getAmount());

        String lockKey = "payment:capture:" + paymentId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {
            
            MerchantPayment payment = findPaymentByPaymentId(paymentId);
            
            if (payment.getStatus() != MerchantPayment.PaymentStatus.AUTHORIZED) {
                throw new IllegalStateException("Payment cannot be captured in current status: " + payment.getStatus());
            }

            // Validate capture amount
            BigDecimal captureAmount = request.getAmount() != null ? request.getAmount() : payment.getAmount();
            if (captureAmount.compareTo(payment.getAmount()) > 0) {
                throw new IllegalArgumentException("Capture amount cannot exceed authorized amount");
            }

            try {
                // Process capture with payment processor
                Map<String, Object> captureResult = paymentProcessorService.capturePayment(payment, captureAmount);
                
                payment.setStatus(MerchantPayment.PaymentStatus.CAPTURED);
                payment.setCapturedAt(LocalDateTime.now());
                payment.setAmount(captureAmount);
                
                // Recalculate fees if amount changed
                if (!captureAmount.equals(payment.getAmount())) {
                    Merchant merchant = findMerchantById(payment.getMerchantId());
                    BigDecimal newFeeAmount = calculateProcessingFee(captureAmount, merchant);
                    payment.setFeeAmount(newFeeAmount);
                    payment.setNetAmount(captureAmount.subtract(newFeeAmount));
                }
                
                payment = paymentRepository.save(payment);
                
                // Update merchant metrics
                updateMerchantMetrics(payment);
                
                // Update cache
                cachePayment(payment);
                
                // Publish event
                eventPublisher.publish(PaymentCompletedEvent.builder()
                        .paymentId(payment.getId())
                        .merchantId(payment.getMerchantId())
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .netAmount(payment.getNetAmount())
                        .feeAmount(payment.getFeeAmount())
                        .build());
                
                log.info("Payment captured: {} amount: {}", paymentId, captureAmount);
                
                return mapToPaymentResponse(payment, findMerchantById(payment.getMerchantId()));
                
            } catch (Exception e) {
                log.error("Payment capture failed: {}", paymentId, e);
                
                payment.setStatus(MerchantPayment.PaymentStatus.FAILED);
                payment.setFailedAt(LocalDateTime.now());
                payment.setFailureReason(e.getMessage());
                payment.setFailureCode("CAPTURE_FAILED");
                paymentRepository.save(payment);
                
                throw new RuntimeException("Payment capture failed: " + e.getMessage());
            }
        });
    }

    @Transactional
    public PaymentResponse refundPayment(String paymentId, PaymentRefundRequest request) {
        log.info("Refunding payment: {} amount: {}", paymentId, request.getAmount());

        MerchantPayment payment = findPaymentByPaymentId(paymentId);
        
        if (!List.of(MerchantPayment.PaymentStatus.CAPTURED, MerchantPayment.PaymentStatus.SETTLED).contains(payment.getStatus())) {
            throw new IllegalStateException("Payment cannot be refunded in current status: " + payment.getStatus());
        }

        BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : payment.getAmount();
        
        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed payment amount");
        }

        try {
            // Process refund with payment processor
            Map<String, Object> refundResult = paymentProcessorService.refundPayment(payment, refundAmount);
            
            if (refundAmount.equals(payment.getAmount())) {
                payment.setStatus(MerchantPayment.PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(MerchantPayment.PaymentStatus.PARTIALLY_REFUNDED);
            }
            
            payment.setRefundedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            
            // Update cache
            cachePayment(payment);
            
            log.info("Payment refunded: {} amount: {}", paymentId, refundAmount);
            
            return mapToPaymentResponse(payment, findMerchantById(payment.getMerchantId()));
            
        } catch (Exception e) {
            log.error("Payment refund failed: {}", paymentId, e);
            throw new RuntimeException("Payment refund failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId) {
        String cacheKey = cacheService.buildPaymentKey(paymentId);
        
        MerchantPayment cached = cacheService.get(cacheKey, MerchantPayment.class);
        if (cached != null) {
            return mapToPaymentResponse(cached, findMerchantById(cached.getMerchantId()));
        }
        
        MerchantPayment payment = findPaymentByPaymentId(paymentId);
        cachePayment(payment);
        
        return mapToPaymentResponse(payment, findMerchantById(payment.getMerchantId()));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getMerchantPayments(String merchantId, Pageable pageable) {
        Merchant merchant = findMerchantByMerchantId(merchantId);
        Page<MerchantPayment> payments = paymentRepository.findByMerchantId(merchant.getId(), pageable);
        
        return payments.map(payment -> mapToPaymentResponse(payment, merchant));
    }

    @Transactional(readOnly = true)
    public List<MerchantPayment> getExpiredPayments() {
        return paymentRepository.findExpiredPayments(
                MerchantPayment.PaymentStatus.PENDING, 
                LocalDateTime.now());
    }

    @Transactional
    public void processExpiredPayments() {
        List<MerchantPayment> expiredPayments = getExpiredPayments();
        
        for (MerchantPayment payment : expiredPayments) {
            try {
                payment.setStatus(MerchantPayment.PaymentStatus.CANCELLED);
                payment.setCancelledAt(LocalDateTime.now());
                paymentRepository.save(payment);
                
                // Remove from cache
                invalidatePaymentCache(payment);
                
                log.info("Expired payment cancelled: {}", payment.getPaymentId());
                
            } catch (Exception e) {
                log.error("Error cancelling expired payment: {}", payment.getPaymentId(), e);
            }
        }
    }

    private void validateMerchantCanAcceptPayments(Merchant merchant) {
        if (merchant.getStatus() != Merchant.MerchantStatus.ACTIVE) {
            throw new IllegalStateException("Merchant is not active");
        }
        
        if (merchant.getVerificationStatus() == Merchant.VerificationStatus.REJECTED ||
            merchant.getVerificationStatus() == Merchant.VerificationStatus.SUSPENDED) {
            throw new IllegalStateException("Merchant verification status does not allow payments");
        }
    }

    private void validatePaymentRequest(PaymentRequest request, Merchant merchant) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        
        if (request.getAmount().compareTo(merchant.getSingleTransactionLimit()) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds merchant single transaction limit");
        }
        
        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isEmpty()) {
            throw new IllegalArgumentException("Payment method is required");
        }
    }

    private BigDecimal calculateProcessingFee(BigDecimal amount, Merchant merchant) {
        BigDecimal percentageFee = amount.multiply(merchant.getProcessingFeeRate()).setScale(2, RoundingMode.HALF_UP);
        return percentageFee.add(merchant.getFixedFee());
    }

    private void updateMerchantMetrics(MerchantPayment payment) {
        try {
            // This would be handled by a separate service call or event
            // merchantService.updateTransactionMetrics(payment.getMerchantId(), payment.getAmount());
        } catch (Exception e) {
            log.warn("Failed to update merchant metrics for payment: {}", payment.getPaymentId(), e);
        }
    }

    private Merchant findMerchantByMerchantId(String merchantId) {
        return merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
    }

    private Merchant findMerchantById(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
    }

    private MerchantPayment findPaymentByPaymentId(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    private void cachePayment(MerchantPayment payment) {
        String cacheKey = cacheService.buildPaymentKey(payment.getPaymentId());
        cacheService.set(cacheKey, payment, Duration.ofHours(1));
    }

    private void invalidatePaymentCache(MerchantPayment payment) {
        String cacheKey = cacheService.buildPaymentKey(payment.getPaymentId());
        cacheService.delete(cacheKey);
    }

    private PaymentResponse mapToPaymentResponse(MerchantPayment payment, Merchant merchant) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .merchantId(merchant.getMerchantId())
                .customerId(payment.getCustomerId())
                .orderId(payment.getOrderId())
                .externalReference(payment.getExternalReference())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .feeAmount(payment.getFeeAmount())
                .netAmount(payment.getNetAmount())
                .taxAmount(payment.getTaxAmount())
                .tipAmount(payment.getTipAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .status(payment.getStatus().name())
                .description(payment.getDescription())
                .receiptEmail(payment.getReceiptEmail())
                .receiptPhone(payment.getReceiptPhone())
                .receiptUrl(payment.getReceiptUrl())
                .refundPolicy(payment.getRefundPolicy())
                .expiresAt(payment.getExpiresAt())
                .authorizedAt(payment.getAuthorizedAt())
                .capturedAt(payment.getCapturedAt())
                .settledAt(payment.getSettledAt())
                .failedAt(payment.getFailedAt())
                .cancelledAt(payment.getCancelledAt())
                .refundedAt(payment.getRefundedAt())
                .failureReason(payment.getFailureReason())
                .failureCode(payment.getFailureCode())
                .processorTransactionId(payment.getProcessorTransactionId())
                .authorizationCode(payment.getAuthorizationCode())
                .fraudScore(payment.getFraudScore())
                .chargebackRisk(payment.getChargebackRisk())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}