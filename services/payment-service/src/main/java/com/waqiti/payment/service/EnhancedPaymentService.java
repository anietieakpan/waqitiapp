package com.waqiti.payment.service;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.idempotency.IdempotencyResult;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.locking.DistributedLock;
import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.repository.PaymentRequestRepository;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced payment service with financial integrity controls.
 * Ensures payment operations are idempotent and financially consistent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedPaymentService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final UnifiedWalletServiceClient walletServiceClient;
    private final UserServiceClient userServiceClient;
    private final DistributedLockService lockService;
    private final IdempotencyService idempotencyService;

    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(30);
    private static final String SYSTEM_USER = "SYSTEM";

    /**
     * Creates a payment request with idempotency protection.
     */
    @Transactional
    public PaymentRequestResponse createPaymentRequest(CreatePaymentRequestRequest request) {
        // Generate idempotency key
        String idempotencyKey = IdempotencyService.FinancialIdempotencyKeys
            .paymentRequest(request.getSenderId(), request.getMerchantTransactionId());
            
        UUID operationId = UUID.randomUUID();
        
        // Check for existing operation
        Optional<IdempotencyResult<PaymentRequestResponse>> existingResult = 
            idempotencyService.checkIdempotency(idempotencyKey, PaymentRequestResponse.class);
            
        if (existingResult.isPresent()) {
            if (existingResult.get().hasResult()) {
                log.info("Returning cached payment request for merchant tx: {}", request.getMerchantTransactionId());
                return existingResult.get().getResult();
            } else if (existingResult.get().isInProgress()) {
                throw new IllegalStateException("Payment request already in progress: " + request.getMerchantTransactionId());
            }
        }

        // Start operation
        if (!idempotencyService.startOperation(idempotencyKey, operationId)) {
            throw new IllegalStateException("Failed to start payment request operation");
        }

        try {
            log.info("Creating payment request: sender={}, recipient={}, amount={}, merchant_tx={}",
                    request.getSenderId(), request.getRecipientId(), request.getAmount(), 
                    request.getMerchantTransactionId());

            // Validate request
            validatePaymentRequest(request);

            // Verify users exist
            verifyUserExists(request.getSenderId());
            verifyUserExists(request.getRecipientId());

            // Create payment request entity
            PaymentRequest paymentRequest = PaymentRequest.builder()
                    .senderId(request.getSenderId())
                    .recipientId(request.getRecipientId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .merchantTransactionId(request.getMerchantTransactionId())
                    .status(PaymentStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            paymentRequest = paymentRequestRepository.save(paymentRequest);

            PaymentRequestResponse response = mapToPaymentRequestResponse(paymentRequest);
            
            // Complete operation
            idempotencyService.completeOperation(idempotencyKey, operationId, response);
            
            log.info("Payment request created successfully: {}", paymentRequest.getId());
            return response;
            
        } catch (Exception e) {
            log.error("Payment request creation failed", e);
            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            throw e;
        }
    }

    /**
     * Approves and processes a payment request with full financial integrity.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PaymentRequestResponse approvePaymentRequest(UUID paymentRequestId, 
                                                      ApprovePaymentRequestRequest request) {
        // Generate idempotency key for approval
        String idempotencyKey = "payment_approval:" + paymentRequestId + ":" + request.getApproverId();
        UUID operationId = UUID.randomUUID();
        
        // Check for existing approval operation
        Optional<IdempotencyResult<PaymentRequestResponse>> existingResult = 
            idempotencyService.checkIdempotency(idempotencyKey, PaymentRequestResponse.class);
            
        if (existingResult.isPresent()) {
            if (existingResult.get().hasResult()) {
                log.info("Returning cached payment approval for request: {}", paymentRequestId);
                return existingResult.get().getResult();
            } else if (existingResult.get().isInProgress()) {
                throw new IllegalStateException("Payment approval already in progress: " + paymentRequestId);
            }
        }

        // Start operation
        if (!idempotencyService.startOperation(idempotencyKey, operationId)) {
            throw new IllegalStateException("Failed to start payment approval operation");
        }

        try {
            log.info("Approving payment request: {} by user: {}", paymentRequestId, request.getApproverId());

            // Acquire distributed lock for payment processing
            String lockKey = DistributedLockService.FinancialLocks.paymentProcessing(paymentRequestId);
            
            try (DistributedLock lock = lockService.acquireLock(lockKey, Duration.ofSeconds(10), OPERATION_TIMEOUT)) {
                if (lock == null) {
                    throw new IllegalStateException("Could not acquire lock for payment approval");
                }

                // Get payment request with lock
                PaymentRequest paymentRequest = paymentRequestRepository.findByIdWithLock(paymentRequestId)
                        .orElseThrow(() -> new PaymentRequestNotFoundException("Payment request not found: " + paymentRequestId));

                // Validate payment request state
                validatePaymentRequestForApproval(paymentRequest, request.getApproverId());

                // Update payment request status
                paymentRequest.setStatus(PaymentStatus.PROCESSING);
                paymentRequest.setProcessedAt(LocalDateTime.now());
                paymentRequest.setUpdatedAt(LocalDateTime.now());
                paymentRequest = paymentRequestRepository.save(paymentRequest);

                try {
                    // Process the actual payment through wallet service
                    TransferResponse transferResponse = executeWalletTransfer(paymentRequest);

                    // Update payment request with transfer details
                    paymentRequest.setStatus(PaymentStatus.COMPLETED);
                    paymentRequest.setTransactionId(transferResponse.getTransactionId());
                    paymentRequest.setProcessedAt(LocalDateTime.now());
                    paymentRequest.setUpdatedAt(LocalDateTime.now());
                    paymentRequest = paymentRequestRepository.save(paymentRequest);

                    PaymentRequestResponse response = mapToPaymentRequestResponse(paymentRequest);
                    
                    // Complete operation
                    idempotencyService.completeOperation(idempotencyKey, operationId, response);
                    
                    log.info("Payment request approved and processed successfully: {}", paymentRequestId);
                    return response;

                } catch (Exception e) {
                    log.error("Payment processing failed for request: {}", paymentRequestId, e);
                    
                    // Mark payment as failed
                    paymentRequest.setStatus(PaymentStatus.FAILED);
                    paymentRequest.setFailureReason(e.getMessage());
                    paymentRequest.setUpdatedAt(LocalDateTime.now());
                    paymentRequestRepository.save(paymentRequest);
                    
                    throw new PaymentProcessingException("Payment processing failed: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Payment approval failed", e);
            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            throw e;
        }
    }

    /**
     * Rejects a payment request.
     */
    @Transactional
    public PaymentRequestResponse rejectPaymentRequest(UUID paymentRequestId, 
                                                     RejectPaymentRequestRequest request) {
        log.info("Rejecting payment request: {} by user: {} with reason: {}", 
                paymentRequestId, request.getRejecterId(), request.getReason());

        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Payment request not found: " + paymentRequestId));

        // Validate payment request can be rejected
        validatePaymentRequestForRejection(paymentRequest, request.getRejecterId());

        // Update payment request
        paymentRequest.setStatus(PaymentStatus.REJECTED);
        paymentRequest.setFailureReason(request.getReason());
        paymentRequest.setProcessedAt(LocalDateTime.now());
        paymentRequest.setUpdatedAt(LocalDateTime.now());
        paymentRequest = paymentRequestRepository.save(paymentRequest);

        log.info("Payment request rejected successfully: {}", paymentRequestId);
        return mapToPaymentRequestResponse(paymentRequest);
    }

    /**
     * Gets a payment request by ID.
     */
    @Transactional(readOnly = true)
    public PaymentRequestResponse getPaymentRequest(UUID paymentRequestId) {
        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Payment request not found: " + paymentRequestId));

        return mapToPaymentRequestResponse(paymentRequest);
    }

    /**
     * Gets payment requests for a user.
     */
    @Transactional(readOnly = true)
    public Page<PaymentRequestResponse> getUserPaymentRequests(UUID userId, Pageable pageable) {
        Page<PaymentRequest> paymentRequests = paymentRequestRepository
                .findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId, pageable);

        return paymentRequests.map(this::mapToPaymentRequestResponse);
    }

    /**
     * Cancels a payment request (only if pending).
     */
    @Transactional
    public PaymentRequestResponse cancelPaymentRequest(UUID paymentRequestId, UUID userId) {
        log.info("Cancelling payment request: {} by user: {}", paymentRequestId, userId);

        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Payment request not found: " + paymentRequestId));

        // Validate user can cancel this request
        if (!paymentRequest.getSenderId().equals(userId) && !paymentRequest.getRecipientId().equals(userId)) {
            throw new IllegalArgumentException("User is not authorized to cancel this payment request");
        }

        // Validate payment request can be cancelled
        if (paymentRequest.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot cancel payment request with status: " + paymentRequest.getStatus());
        }

        // Update payment request
        paymentRequest.setStatus(PaymentStatus.CANCELLED);
        paymentRequest.setProcessedAt(LocalDateTime.now());
        paymentRequest.setUpdatedAt(LocalDateTime.now());
        paymentRequest = paymentRequestRepository.save(paymentRequest);

        log.info("Payment request cancelled successfully: {}", paymentRequestId);
        return mapToPaymentRequestResponse(paymentRequest);
    }

    /**
     * Executes the actual wallet transfer for a payment.
     */
    private TransferResponse executeWalletTransfer(PaymentRequest paymentRequest) {
        // Get sender and recipient wallets
        WalletResponse senderWallet = getSenderWallet(paymentRequest.getSenderId(), paymentRequest.getCurrency());
        WalletResponse recipientWallet = getRecipientWallet(paymentRequest.getRecipientId(), paymentRequest.getCurrency());

        // Create transfer request
        TransferRequest transferRequest = TransferRequest.builder()
                .sourceWalletId(senderWallet.getId())
                .targetWalletId(recipientWallet.getId())
                .amount(paymentRequest.getAmount())
                .description("Payment: " + paymentRequest.getDescription())
                .clientTransactionId(paymentRequest.getMerchantTransactionId())
                .build();

        // Execute transfer through wallet service
        return walletServiceClient.transfer(transferRequest);
    }

    /**
     * Gets sender wallet for the specified currency.
     */
    private WalletResponse getSenderWallet(UUID senderId, String currency) {
        try {
            return walletServiceClient.getPrimaryWallet(senderId, currency);
        } catch (Exception e) {
            log.error("Failed to get sender wallet for user: {} currency: {}", senderId, currency, e);
            throw new PaymentProcessingException("Sender wallet not found or not accessible", e);
        }
    }

    /**
     * Gets recipient wallet for the specified currency.
     */
    private WalletResponse getRecipientWallet(UUID recipientId, String currency) {
        try {
            return walletServiceClient.getPrimaryWallet(recipientId, currency);
        } catch (Exception e) {
            log.error("Failed to get recipient wallet for user: {} currency: {}", recipientId, currency, e);
            throw new PaymentProcessingException("Recipient wallet not found or not accessible", e);
        }
    }

    /**
     * Validates payment request creation.
     */
    private void validatePaymentRequest(CreatePaymentRequestRequest request) {
        if (request.getSenderId().equals(request.getRecipientId())) {
            throw new IllegalArgumentException("Sender and recipient cannot be the same");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        if (request.getAmount().scale() > 2) {
            throw new IllegalArgumentException("Payment amount cannot have more than 2 decimal places");
        }
    }

    /**
     * Validates payment request for approval.
     */
    private void validatePaymentRequestForApproval(PaymentRequest paymentRequest, UUID approverId) {
        if (paymentRequest.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment request is not in pending status: " + paymentRequest.getStatus());
        }

        // Only recipient can approve
        if (!paymentRequest.getRecipientId().equals(approverId)) {
            throw new IllegalArgumentException("Only the payment recipient can approve this request");
        }
    }

    /**
     * Validates payment request for rejection.
     */
    private void validatePaymentRequestForRejection(PaymentRequest paymentRequest, UUID rejecterId) {
        if (paymentRequest.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment request is not in pending status: " + paymentRequest.getStatus());
        }

        // Both sender and recipient can reject
        if (!paymentRequest.getSenderId().equals(rejecterId) && !paymentRequest.getRecipientId().equals(rejecterId)) {
            throw new IllegalArgumentException("Only sender or recipient can reject this payment request");
        }
    }

    /**
     * Verifies that a user exists.
     */
    private void verifyUserExists(UUID userId) {
        try {
            userServiceClient.getUser(userId);
        } catch (Exception e) {
            log.error("User verification failed for user: {}", userId, e);
            throw new IllegalArgumentException("User not found or not accessible: " + userId);
        }
    }

    /**
     * Calculate fraud risk score for payment request
     * @param fraudCheckData Map containing fraud analysis data
     * @return fraud score between 0.0 (low risk) and 1.0 (high risk)
     */
    public Double calculateFraudScore(Map<String, Object> fraudCheckData) {
        try {
            double score = 0.0;
            
            // Analyze payment amount (higher amounts = higher risk)
            if (fraudCheckData.containsKey("amount")) {
                BigDecimal amount = (BigDecimal) fraudCheckData.get("amount");
                if (amount.compareTo(new BigDecimal("1000")) > 0) {
                    score += 0.1;
                }
                if (amount.compareTo(new BigDecimal("10000")) > 0) {
                    score += 0.2;
                }
            }
            
            // Check velocity - number of transactions in last hour
            if (fraudCheckData.containsKey("transactionVelocity")) {
                Integer velocity = (Integer) fraudCheckData.get("transactionVelocity");
                if (velocity != null && velocity > 10) {
                    score += 0.3;
                }
            }
            
            // Check geographical location anomalies
            if (fraudCheckData.containsKey("locationAnomaly")) {
                Boolean anomaly = (Boolean) fraudCheckData.get("locationAnomaly");
                if (Boolean.TRUE.equals(anomaly)) {
                    score += 0.2;
                }
            }
            
            // Check device fingerprint
            if (fraudCheckData.containsKey("deviceFingerprint")) {
                String fingerprint = (String) fraudCheckData.get("deviceFingerprint");
                if (fingerprint == null || fingerprint.isEmpty() || "unknown".equals(fingerprint)) {
                    score += 0.15;
                }
            }
            
            // Check time of transaction (unusual hours)
            if (fraudCheckData.containsKey("transactionHour")) {
                Integer hour = (Integer) fraudCheckData.get("transactionHour");
                if (hour != null && (hour < 6 || hour > 22)) {
                    score += 0.1;
                }
            }
            
            // Check payment method risk
            if (fraudCheckData.containsKey("paymentMethod")) {
                String method = (String) fraudCheckData.get("paymentMethod");
                if ("CRYPTO".equals(method) || "INTERNATIONAL_WIRE".equals(method)) {
                    score += 0.2;
                }
            }
            
            // Cap the score at 1.0
            return Math.min(1.0, score);
            
        } catch (Exception e) {
            log.error("Error calculating fraud score", e);
            // Return moderate risk score if calculation fails
            return 0.5;
        }
    }

    /**
     * Maps PaymentRequest entity to response DTO.
     */
    private PaymentRequestResponse mapToPaymentRequestResponse(PaymentRequest paymentRequest) {
        return PaymentRequestResponse.builder()
                .id(paymentRequest.getId())
                .senderId(paymentRequest.getSenderId())
                .recipientId(paymentRequest.getRecipientId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .description(paymentRequest.getDescription())
                .merchantTransactionId(paymentRequest.getMerchantTransactionId())
                .status(paymentRequest.getStatus())
                .transactionId(paymentRequest.getTransactionId())
                .failureReason(paymentRequest.getFailureReason())
                .createdAt(paymentRequest.getCreatedAt())
                .processedAt(paymentRequest.getProcessedAt())
                .updatedAt(paymentRequest.getUpdatedAt())
                .build();
    }
}