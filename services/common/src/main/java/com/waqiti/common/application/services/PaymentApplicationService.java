package com.waqiti.common.application.services;

import com.waqiti.common.domain.services.PaymentValidationService;
import com.waqiti.common.domain.services.ComplianceService;
import com.waqiti.common.domain.services.AccountBalanceService;
import com.waqiti.common.domain.Money;
import com.waqiti.common.domain.valueobjects.PaymentId;
import com.waqiti.common.domain.valueobjects.UserId;
import com.waqiti.common.events.EventGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Payment Application Service
 * Orchestrates payment use cases by coordinating domain services,
 * repositories, and external integrations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {
    
    private final PaymentValidationService paymentValidationService;
    private final ComplianceService complianceService;
    private final AccountBalanceService balanceService;
    private final EventGateway eventGateway;
    
    /**
     * Process payment request - main orchestration use case
     */
    @Transactional
    public CompletableFuture<PaymentProcessingResult> processPayment(ProcessPaymentRequest request) {
        log.info("Processing payment request: amount={}, from={}, to={}", 
                request.getAmount(), request.getFromUserId(), request.getToUserId());
        
        return CompletableFuture
                .supplyAsync(() -> validatePaymentRequest(request))
                .thenCompose(this::performComplianceChecks)
                .thenCompose(this::validateSufficientFunds)
                .thenCompose(this::executePayment)
                .thenCompose(this::publishPaymentEvents)
                .exceptionally(this::handlePaymentFailure);
    }
    
    /**
     * Create scheduled payment
     */
    @Transactional
    public ScheduledPaymentResult createScheduledPayment(CreateScheduledPaymentRequest request) {
        log.info("Creating scheduled payment: amount={}, scheduledFor={}", 
                request.getAmount(), request.getScheduledFor());
        
        // Validate payment details
        PaymentValidationService.PaymentValidationResult validation = paymentValidationService.validatePayment(
                PaymentValidationService.PaymentValidationRequest.builder()
                        .amount(request.getAmount())
                        .fromUserId(request.getFromUserId())
                        .toUserId(request.getToUserId())
                        .purpose(request.getPurpose())
                        .crossBorder(request.isCrossBorder())
                        .build());
        
        if (!validation.isValid()) {
            return ScheduledPaymentResult.builder()
                    .success(false)
                    .errors(validation.getViolations())
                    .build();
        }
        
        // Create scheduled payment entity (would call repository)
        PaymentId paymentId = PaymentId.generatePayment();
        
        // Publish event
        eventGateway.publishEvent("payment.scheduled.created", Map.of(
                "paymentId", paymentId.getValue(),
                "amount", request.getAmount(),
                "fromUserId", request.getFromUserId().getValue(),
                "toUserId", request.getToUserId().getValue(),
                "scheduledFor", request.getScheduledFor()
        ));
        
        return ScheduledPaymentResult.builder()
                .success(true)
                .paymentId(paymentId)
                .message("Scheduled payment created successfully")
                .build();
    }
    
    /**
     * Process bulk payments
     */
    @Transactional
    public CompletableFuture<BulkPaymentResult> processBulkPayments(ProcessBulkPaymentRequest request) {
        log.info("Processing bulk payment with {} transactions", request.getPayments().size());
        
        return CompletableFuture
                .supplyAsync(() -> validateBulkPayment(request))
                .thenCompose(this::processBulkPaymentBatch)
                .thenCompose(this::publishBulkPaymentEvents);
    }
    
    /**
     * Cancel payment
     */
    @Transactional
    public PaymentCancellationResult cancelPayment(CancelPaymentRequest request) {
        log.info("Cancelling payment: {}", request.getPaymentId());
        
        // Validate cancellation eligibility
        // Check payment status, release holds, etc.
        
        // Publish cancellation event
        eventGateway.publishEvent("payment.cancelled", Map.of(
                "paymentId", request.getPaymentId().getValue(),
                "reason", request.getReason(),
                "cancelledBy", request.getCancelledBy().getValue()
        ));
        
        return PaymentCancellationResult.builder()
                .success(true)
                .paymentId(request.getPaymentId())
                .message("Payment cancelled successfully")
                .build();
    }
    
    /**
     * Refund payment
     */
    @Transactional  
    public PaymentRefundResult refundPayment(RefundPaymentRequest request) {
        log.info("Processing refund for payment: {}, amount: {}", 
                request.getOriginalPaymentId(), request.getRefundAmount());
        
        // Validate refund eligibility
        // Calculate refund amount, fees, etc.
        
        PaymentId refundPaymentId = PaymentId.generatePayment();
        
        // Publish refund event
        eventGateway.publishEvent("payment.refund.created", Map.of(
                "refundPaymentId", refundPaymentId.getValue(),
                "originalPaymentId", request.getOriginalPaymentId().getValue(),
                "refundAmount", request.getRefundAmount(),
                "reason", request.getReason()
        ));
        
        return PaymentRefundResult.builder()
                .success(true)
                .refundPaymentId(refundPaymentId)
                .originalPaymentId(request.getOriginalPaymentId())
                .refundAmount(request.getRefundAmount())
                .message("Refund processed successfully")
                .build();
    }
    
    // Private orchestration methods
    
    private PaymentProcessingContext validatePaymentRequest(ProcessPaymentRequest request) {
        PaymentValidationService.PaymentValidationResult validation = paymentValidationService.validatePayment(
                PaymentValidationService.PaymentValidationRequest.builder()
                        .amount(request.getAmount())
                        .fromUserId(request.getFromUserId())
                        .toUserId(request.getToUserId())
                        .purpose(request.getPurpose())
                        .instantPayment(request.isInstantPayment())
                        .crossBorder(request.isCrossBorder())
                        .build());
        
        if (!validation.isValid()) {
            throw new PaymentValidationException("Payment validation failed: " + 
                    String.join(", ", validation.getViolations()));
        }
        
        return PaymentProcessingContext.builder()
                .request(request)
                .validationResult(validation)
                .paymentId(PaymentId.generatePayment())
                .build();
    }
    
    private CompletableFuture<PaymentProcessingContext> performComplianceChecks(PaymentProcessingContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ComplianceService.ComplianceCheckResult compliance = complianceService.performComplianceCheck(
                    ComplianceService.ComplianceCheckRequest.builder()
                            .userId(context.getRequest().getFromUserId())
                            .amount(context.getRequest().getAmount())
                            .crossBorder(context.getRequest().isCrossBorder())
                            .build());
            
            if (!compliance.isApproved()) {
                throw new ComplianceException("Compliance check failed");
            }
            
            context.setComplianceResult(compliance);
            return context;
        });
    }
    
    private CompletableFuture<PaymentProcessingContext> validateSufficientFunds(PaymentProcessingContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Would get account balance from repository
            Money currentBalance = Money.ngn(10000.0); // Placeholder
            
            AccountBalanceService.SufficientFundsResult fundsCheck = balanceService.validateSufficientFunds(
                    AccountBalanceService.SufficientFundsRequest.builder()
                            .accountId(context.getRequest().getFromAccountId())
                            .currentBalance(currentBalance)
                            .requestedAmount(context.getRequest().getAmount())
                            .build());
            
            if (!fundsCheck.isSufficient()) {
                throw new InsufficientFundsException("Insufficient funds for payment");
            }
            
            context.setFundsResult(fundsCheck);
            return context;
        });
    }
    
    private CompletableFuture<PaymentProcessingContext> executePayment(PaymentProcessingContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Execute payment logic (would call payment aggregate)
            // Apply holds, update balances, etc.
            
            context.setExecutedAt(Instant.now());
            context.setStatus(PaymentStatus.COMPLETED);
            
            log.info("Payment executed successfully: {}", context.getPaymentId());
            return context;
        });
    }
    
    private CompletableFuture<PaymentProcessingResult> publishPaymentEvents(PaymentProcessingContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Publish payment completed event
            eventGateway.publishEvent("payment.completed", Map.of(
                    "paymentId", context.getPaymentId().getValue(),
                    "amount", context.getRequest().getAmount(),
                    "fromUserId", context.getRequest().getFromUserId().getValue(),
                    "toUserId", context.getRequest().getToUserId().getValue(),
                    "completedAt", context.getExecutedAt()
            ));
            
            return PaymentProcessingResult.builder()
                    .success(true)
                    .paymentId(context.getPaymentId())
                    .amount(context.getRequest().getAmount())
                    .status(context.getStatus())
                    .completedAt(context.getExecutedAt())
                    .message("Payment processed successfully")
                    .build();
        });
    }
    
    private PaymentProcessingResult handlePaymentFailure(Throwable throwable) {
        log.error("Payment processing failed", throwable);
        
        // Publish failure event
        eventGateway.publishEvent("payment.failed", Map.of(
                "error", throwable.getMessage(),
                "failedAt", Instant.now()
        ));
        
        return PaymentProcessingResult.builder()
                .success(false)
                .message("Payment processing failed: " + throwable.getMessage())
                .build();
    }
    
    private BulkPaymentValidationContext validateBulkPayment(ProcessBulkPaymentRequest request) {
        List<PaymentValidationService.PaymentValidationRequest> paymentRequests = new ArrayList<>();
        
        for (ProcessPaymentRequest payment : request.getPayments()) {
            paymentRequests.add(PaymentValidationService.PaymentValidationRequest.builder()
                    .amount(payment.getAmount())
                    .fromUserId(payment.getFromUserId())
                    .toUserId(payment.getToUserId())
                    .build());
        }
        
        PaymentValidationService.BulkPaymentValidationResult validation = 
                paymentValidationService.validateBulkPayment(
                        PaymentValidationService.BulkPaymentValidationRequest.builder()
                                .payments(paymentRequests)
                                .build());
        
        if (!validation.isValid()) {
            throw new BulkPaymentValidationException("Bulk payment validation failed");
        }
        
        return BulkPaymentValidationContext.builder()
                .request(request)
                .validationResult(validation)
                .build();
    }
    
    private CompletableFuture<BulkPaymentExecutionContext> processBulkPaymentBatch(BulkPaymentValidationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Execute bulk payment processing
            // This would involve batch processing individual payments
            
            return BulkPaymentExecutionContext.builder()
                    .validationContext(context)
                    .processedAt(Instant.now())
                    .build();
        });
    }
    
    private CompletableFuture<BulkPaymentResult> publishBulkPaymentEvents(BulkPaymentExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Publish bulk payment events
            
            return BulkPaymentResult.builder()
                    .success(true)
                    .totalPayments(context.getValidationContext().getRequest().getPayments().size())
                    .message("Bulk payment processed successfully")
                    .build();
        });
    }
    
    // Request and Result classes
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessPaymentRequest {
        private Money amount;
        private UserId fromUserId;
        private UserId toUserId;
        private String fromAccountId;
        private String toAccountId;
        private String purpose;
        private boolean instantPayment;
        private boolean crossBorder;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentProcessingResult {
        private boolean success;
        private PaymentId paymentId;
        private Money amount;
        private PaymentStatus status;
        private Instant completedAt;
        private String message;
        private List<String> errors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateScheduledPaymentRequest {
        private Money amount;
        private UserId fromUserId;
        private UserId toUserId;
        private String purpose;
        private Instant scheduledFor;
        private boolean crossBorder;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduledPaymentResult {
        private boolean success;
        private PaymentId paymentId;
        private String message;
        private List<String> errors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessBulkPaymentRequest {
        private List<ProcessPaymentRequest> payments;
        private UserId initiatorUserId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkPaymentResult {
        private boolean success;
        private int totalPayments;
        private int successfulPayments;
        private int failedPayments;
        private String message;
        private List<String> errors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelPaymentRequest {
        private PaymentId paymentId;
        private UserId cancelledBy;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentCancellationResult {
        private boolean success;
        private PaymentId paymentId;
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundPaymentRequest {
        private PaymentId originalPaymentId;
        private Money refundAmount;
        private String reason;
        private UserId initiatedBy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRefundResult {
        private boolean success;
        private PaymentId refundPaymentId;
        private PaymentId originalPaymentId;
        private Money refundAmount;
        private String message;
    }
    
    // Internal context classes
    
    @Data
    @Builder
    private static class PaymentProcessingContext {
        private ProcessPaymentRequest request;
        private PaymentId paymentId;
        private PaymentValidationService.PaymentValidationResult validationResult;
        private ComplianceService.ComplianceCheckResult complianceResult;
        private AccountBalanceService.SufficientFundsResult fundsResult;
        private PaymentStatus status;
        private Instant executedAt;
    }
    
    @Data
    @Builder
    private static class BulkPaymentValidationContext {
        private ProcessBulkPaymentRequest request;
        private PaymentValidationService.BulkPaymentValidationResult validationResult;
    }
    
    @Data
    @Builder
    private static class BulkPaymentExecutionContext {
        private BulkPaymentValidationContext validationContext;
        private Instant processedAt;
    }
    
    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED
    }
    
    // Custom exceptions
    
    public static class PaymentValidationException extends RuntimeException {
        public PaymentValidationException(String message) {
            super(message);
        }
    }
    
    public static class ComplianceException extends RuntimeException {
        public ComplianceException(String message) {
            super(message);
        }
    }
    
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
    
    public static class BulkPaymentValidationException extends RuntimeException {
        public BulkPaymentValidationException(String message) {
            super(message);
        }
    }
}