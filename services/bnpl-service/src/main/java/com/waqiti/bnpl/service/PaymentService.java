package com.waqiti.bnpl.service;

import com.waqiti.bnpl.domain.BnplPlan;
import com.waqiti.bnpl.domain.BnplTransaction;
import com.waqiti.bnpl.domain.enums.TransactionStatus;
import com.waqiti.bnpl.domain.enums.TransactionType;
import com.waqiti.bnpl.dto.request.ProcessPaymentRequest;
import com.waqiti.bnpl.exception.PaymentProcessingException;
import com.waqiti.bnpl.repository.BnplPlanRepository;
import com.waqiti.bnpl.repository.BnplTransactionRepository;

// Import UnifiedPaymentService
import com.waqiti.payment.core.UnifiedPaymentService;
import com.waqiti.payment.core.model.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MODERNIZED BNPL PaymentService - Now delegates to UnifiedPaymentService
 * Maintains backward compatibility while using the new unified architecture
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    // Legacy dependencies for backward compatibility
    private final BnplPlanRepository bnplPlanRepository;
    private final BnplTransactionRepository bnplTransactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebClient webClient;

    // NEW: Unified Payment Service
    private final UnifiedPaymentService unifiedPaymentService;

    /**
     * Process BNPL payment - MODERNIZED to use UnifiedPaymentService
     */
    @Transactional
    public BnplTransaction processPayment(ProcessPaymentRequest request) {
        log.info("Processing BNPL payment for user {} amount={} [UNIFIED]", 
                request.getUserId(), request.getAmount());

        try {
            // Find the BNPL plan
            BnplPlan bnplPlan = bnplPlanRepository.findById(request.getBnplPlanId())
                    .orElseThrow(() -> new PaymentProcessingException("BNPL plan not found: " + request.getBnplPlanId()));

            // Validate payment against plan
            validateBnplPayment(request, bnplPlan);

            // CREATE BNPL PAYMENT USING UNIFIED SERVICE
            PaymentRequest unifiedRequest = PaymentRequest.builder()
                    .paymentId(UUID.randomUUID())
                    .type(PaymentType.BNPL)
                    .providerType(ProviderType.INTERNAL)
                    .fromUserId(request.getUserId())
                    .toUserId(bnplPlan.getMerchantId())
                    .amount(request.getAmount())
                    .metadata(Map.of(
                            "bnplPlanId", request.getBnplPlanId().toString(),
                            "installments", bnplPlan.getInstallments().toString(),
                            "interestRate", bnplPlan.getInterestRate().toString(),
                            "installmentNumber", request.getInstallmentNumber().toString(),
                            "description", "BNPL payment: " + bnplPlan.getDescription(),
                            "currency", "USD"
                    ))
                    .build();

            // Process through UnifiedPaymentService
            PaymentResult result = unifiedPaymentService.processPayment(unifiedRequest);

            // Create BNPL transaction record
            BnplTransaction transaction = createBnplTransaction(request, bnplPlan, result);

            // Update BNPL plan status based on result
            updateBnplPlanStatus(bnplPlan, transaction, result);

            // Publish BNPL event
            publishBnplPaymentEvent(transaction, result);

            log.info("BNPL payment processed successfully: {} -> {}", 
                    transaction.getId(), result.getTransactionId());

            return transaction;

        } catch (Exception e) {
            log.error("Error processing BNPL payment via UnifiedPaymentService", e);
            throw new PaymentProcessingException("BNPL payment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process installment payment - MODERNIZED to use UnifiedPaymentService
     */
    @Transactional
    public BnplTransaction processInstallmentPayment(UUID bnplPlanId, String userId, int installmentNumber) {
        log.info("Processing BNPL installment {} for plan {} user {} [UNIFIED]", 
                installmentNumber, bnplPlanId, userId);

        try {
            BnplPlan bnplPlan = bnplPlanRepository.findById(bnplPlanId)
                    .orElseThrow(() -> new PaymentProcessingException("BNPL plan not found: " + bnplPlanId));

            // Calculate installment amount
            BigDecimal installmentAmount = calculateInstallmentAmount(bnplPlan, installmentNumber);

            // CREATE INSTALLMENT PAYMENT USING UNIFIED SERVICE
            PaymentRequest installmentRequest = PaymentRequest.builder()
                    .paymentId(UUID.randomUUID())
                    .type(PaymentType.BNPL)
                    .providerType(ProviderType.INTERNAL)
                    .fromUserId(userId)
                    .toUserId(bnplPlan.getMerchantId())
                    .amount(installmentAmount)
                    .metadata(Map.of(
                            "paymentType", "INSTALLMENT",
                            "bnplPlanId", bnplPlanId.toString(),
                            "installmentNumber", String.valueOf(installmentNumber),
                            "totalInstallments", bnplPlan.getInstallments().toString(),
                            "description", "BNPL installment " + installmentNumber + " of " + bnplPlan.getInstallments(),
                            "currency", "USD"
                    ))
                    .build();

            // Process through UnifiedPaymentService
            PaymentResult result = unifiedPaymentService.processPayment(installmentRequest);

            // Create installment transaction record
            BnplTransaction transaction = createInstallmentTransaction(bnplPlan, installmentNumber, result);

            // Check if plan is fully paid
            if (installmentNumber >= bnplPlan.getInstallments()) {
                completeBnplPlan(bnplPlan);
            }

            // Publish installment event
            publishBnplInstallmentEvent(transaction, result);

            log.info("BNPL installment processed successfully: installment {} -> {}", 
                    installmentNumber, result.getTransactionId());

            return transaction;

        } catch (Exception e) {
            log.error("Error processing BNPL installment via UnifiedPaymentService", e);
            throw new PaymentProcessingException("BNPL installment payment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get BNPL analytics - MODERNIZED to use UnifiedPaymentService
     */
    public BnplAnalytics getBnplAnalytics(String userId, String period) {
        log.info("Getting BNPL analytics for user {} period={} [UNIFIED]", userId, period);
        
        try {
            // Get analytics from UnifiedPaymentService
            AnalyticsFilter filter = AnalyticsFilter.builder()
                    .startDate(LocalDateTime.now().minusDays(period.equals("year") ? 365 : 30))
                    .endDate(LocalDateTime.now())
                    .paymentType(PaymentType.BNPL)
                    .groupBy(period.equals("year") ? "month" : "day")
                    .build();
            
            PaymentAnalytics unifiedAnalytics = unifiedPaymentService.getAnalytics(userId, filter);
            
            // Convert to BNPL specific analytics
            return convertToBnplAnalytics(unifiedAnalytics, userId);
            
        } catch (Exception e) {
            log.error("Error getting BNPL analytics", e);
            throw e;
        }
    }

    // LEGACY SUPPORT METHODS - Maintain backward compatibility

    private void validateBnplPayment(ProcessPaymentRequest request, BnplPlan bnplPlan) {
        if (!bnplPlan.getUserId().equals(request.getUserId())) {
            throw new PaymentProcessingException("User not authorized for this BNPL plan");
        }

        if (bnplPlan.getStatus() != com.waqiti.bnpl.domain.enums.PlanStatus.ACTIVE) {
            throw new PaymentProcessingException("BNPL plan is not active");
        }

        // Validate installment number
        if (request.getInstallmentNumber() > bnplPlan.getInstallments()) {
            throw new PaymentProcessingException("Invalid installment number");
        }
    }

    private BnplTransaction createBnplTransaction(ProcessPaymentRequest request, BnplPlan bnplPlan, PaymentResult result) {
        BnplTransaction transaction = new BnplTransaction();
        transaction.setBnplPlan(bnplPlan);
        transaction.setAmount(request.getAmount());
        transaction.setInstallmentNumber(request.getInstallmentNumber());
        transaction.setType(TransactionType.PAYMENT);
        transaction.setStatus(convertToTransactionStatus(result.getStatus()));
        transaction.setUnifiedTransactionId(result.getTransactionId());
        transaction.setProcessedAt(result.getProcessedAt());
        transaction.setCreatedAt(LocalDateTime.now());
        
        return bnplTransactionRepository.save(transaction);
    }

    private BnplTransaction createInstallmentTransaction(BnplPlan bnplPlan, int installmentNumber, PaymentResult result) {
        BnplTransaction transaction = new BnplTransaction();
        transaction.setBnplPlan(bnplPlan);
        transaction.setAmount(result.getAmount());
        transaction.setInstallmentNumber(installmentNumber);
        transaction.setType(TransactionType.INSTALLMENT);
        transaction.setStatus(convertToTransactionStatus(result.getStatus()));
        transaction.setUnifiedTransactionId(result.getTransactionId());
        transaction.setProcessedAt(result.getProcessedAt());
        transaction.setCreatedAt(LocalDateTime.now());
        
        return bnplTransactionRepository.save(transaction);
    }

    private TransactionStatus convertToTransactionStatus(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case COMPLETED -> TransactionStatus.COMPLETED;
            case PENDING, PROCESSING -> TransactionStatus.PENDING;
            case FAILED, FRAUD_BLOCKED -> TransactionStatus.FAILED;
            default -> TransactionStatus.FAILED;
        };
    }

    private BigDecimal calculateInstallmentAmount(BnplPlan bnplPlan, int installmentNumber) {
        // FIX: Use RoundingMode enum instead of deprecated BigDecimal.ROUND_* constants
        // FIX: Use valueOf() for integer conversion (better performance)
        BigDecimal baseAmount = bnplPlan.getTotalAmount().divide(
                BigDecimal.valueOf(bnplPlan.getInstallments()), 2, RoundingMode.HALF_UP);

        // Add interest if applicable
        if (bnplPlan.getInterestRate().compareTo(BigDecimal.ZERO) > 0) {
            // FIX: Critical - add scale and rounding mode to prevent ArithmeticException
            BigDecimal interest = baseAmount.multiply(bnplPlan.getInterestRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            baseAmount = baseAmount.add(interest);
        }

        return baseAmount;
    }

    private void updateBnplPlanStatus(BnplPlan bnplPlan, BnplTransaction transaction, PaymentResult result) {
        if (result.isSuccess()) {
            bnplPlan.setLastPaymentAt(LocalDateTime.now());
            // Additional plan status updates
        } else {
            bnplPlan.setFailedPaymentCount(bnplPlan.getFailedPaymentCount() + 1);
            if (bnplPlan.getFailedPaymentCount() >= 3) {
                bnplPlan.setStatus(com.waqiti.bnpl.domain.enums.PlanStatus.SUSPENDED);
            }
        }
        bnplPlanRepository.save(bnplPlan);
    }

    private void completeBnplPlan(BnplPlan bnplPlan) {
        bnplPlan.setStatus(com.waqiti.bnpl.domain.enums.PlanStatus.COMPLETED);
        bnplPlan.setCompletedAt(LocalDateTime.now());
        bnplPlanRepository.save(bnplPlan);
        
        log.info("BNPL plan completed: {}", bnplPlan.getId());
    }

    private void publishBnplPaymentEvent(BnplTransaction transaction, PaymentResult result) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "BNPL_PAYMENT_PROCESSED",
                    "transactionId", transaction.getId(),
                    "bnplPlanId", transaction.getBnplPlan().getId(),
                    "amount", transaction.getAmount(),
                    "installmentNumber", transaction.getInstallmentNumber(),
                    "status", transaction.getStatus(),
                    "unifiedTransactionId", result.getTransactionId(),
                    "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("bnpl-payment-events", transaction.getId().toString(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish BNPL payment event", e);
        }
    }

    private void publishBnplInstallmentEvent(BnplTransaction transaction, PaymentResult result) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "BNPL_INSTALLMENT_PROCESSED",
                    "transactionId", transaction.getId(),
                    "bnplPlanId", transaction.getBnplPlan().getId(),
                    "installmentNumber", transaction.getInstallmentNumber(),
                    "amount", transaction.getAmount(),
                    "status", transaction.getStatus(),
                    "unifiedTransactionId", result.getTransactionId(),
                    "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("bnpl-installment-events", transaction.getId().toString(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish BNPL installment event", e);
        }
    }

    private BnplAnalytics convertToBnplAnalytics(PaymentAnalytics unifiedAnalytics, String userId) {
        BnplAnalytics analytics = new BnplAnalytics();
        analytics.setUserId(userId);
        analytics.setTotalBnplPayments(unifiedAnalytics.getTotalPayments());
        analytics.setSuccessfulPayments(unifiedAnalytics.getSuccessfulPayments());
        analytics.setFailedPayments(unifiedAnalytics.getFailedPayments());
        analytics.setTotalAmount(unifiedAnalytics.getTotalAmount());
        analytics.setAverageAmount(unifiedAnalytics.getAverageAmount());
        analytics.setSuccessRate(unifiedAnalytics.getSuccessRate());
        analytics.setPeriodStart(unifiedAnalytics.getPeriodStart());
        analytics.setPeriodEnd(unifiedAnalytics.getPeriodEnd());
        return analytics;
    }
}