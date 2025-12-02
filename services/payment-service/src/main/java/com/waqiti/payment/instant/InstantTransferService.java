package com.waqiti.payment.instant;

import com.waqiti.payment.domain.InstantTransfer;
import com.waqiti.payment.commons.domain.PaymentStatus;
import com.waqiti.payment.repository.InstantTransferRepository;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.client.FraudDetectionClient;
import com.waqiti.payment.client.RealTimePaymentNetworkClient;
import com.waqiti.payment.dto.InstantTransferRequest;
import com.waqiti.payment.dto.InstantTransferResponse;
import com.waqiti.payment.exception.InstantTransferException;
import com.waqiti.payment.exception.InsufficientFundsException;
import com.waqiti.payment.exception.TransferLimitExceededException;
import com.waqiti.common.domain.Money;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.idempotency.Idempotent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for processing instant transfers (Zelle-style, FedNow, RTP).
 * Provides real-time money transfers with immediate settlement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstantTransferService {

    private final InstantTransferRepository instantTransferRepository;
    private final UnifiedWalletServiceClient walletServiceClient;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final FraudDetectionClient fraudDetectionClient;
    private final RealTimePaymentNetworkClient rtpNetworkClient;
    private final EventPublisher eventPublisher;
    private final InstantTransferValidationService validationService;
    private final InstantTransferLimitService limitService;

    /**
     * Processes an instant transfer request
     */
    @Transactional
    @CircuitBreaker(name = "instant-transfer", fallbackMethod = "instantTransferFallback")
    @Retry(name = "instant-transfer")
    @TimeLimiter(name = "instant-transfer")
    @Idempotent(
        keyExpression = "'instant-transfer:' + #request.senderId + ':' + #request.recipientId + ':' + #request.correlationId",
        serviceName = "payment-service",
        operationType = "PROCESS_INSTANT_TRANSFER",
        userIdExpression = "#request.senderId",
        correlationIdExpression = "#request.correlationId",
        amountExpression = "#request.amount",
        currencyExpression = "#request.currency",
        ttlHours = 72
    )
    public CompletableFuture<InstantTransferResponse> processInstantTransfer(InstantTransferRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Processing instant transfer: from={}, to={}, amount={}", 
                    request.getSenderId(), request.getRecipientId(), request.getAmount());
            
            try {
                // Step 1: Create instant transfer record
                InstantTransfer transfer = createInstantTransfer(request);
                
                // Step 2: Validate transfer
                validateInstantTransfer(transfer);
                
                // Step 3: Perform fraud detection
                performFraudDetection(transfer);
                
                // Step 4: Check and reserve funds
                reserveFunds(transfer);
                
                // Step 5: Execute instant transfer
                executeInstantTransfer(transfer);
                
                // Step 6: Notify participants
                sendNotifications(transfer);
                
                // Step 7: Update analytics
                updateAnalytics(transfer);
                
                log.info("Instant transfer completed successfully: transferId={}", transfer.getId());
                return buildSuccessResponse(transfer);
                
            } catch (Exception e) {
                log.error("Instant transfer failed: error={}", e.getMessage(), e);
                throw new InstantTransferException("Instant transfer failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Creates instant transfer record
     */
    private InstantTransfer createInstantTransfer(InstantTransferRequest request) {
        InstantTransfer transfer = InstantTransfer.builder()
                .id(UUID.randomUUID())
                .senderId(request.getSenderId())
                .recipientId(request.getRecipientId())
                .amount(new Money(request.getAmount(), request.getCurrency()))
                .description(request.getDescription())
                .transferMethod(determineTransferMethod(request))
                .status(PaymentStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .correlationId(request.getCorrelationId())
                .build();
        
        return instantTransferRepository.save(transfer);
    }

    /**
     * Validates instant transfer
     */
    private void validateInstantTransfer(InstantTransfer transfer) {
        log.debug("Validating instant transfer: transferId={}", transfer.getId());
        
        // Basic validation
        validationService.validateBasicTransferRules(transfer);
        
        // User validation
        validationService.validateUsers(transfer.getSenderId(), transfer.getRecipientId());
        
        // Amount validation
        validationService.validateAmount(transfer.getAmount());
        
        // Transfer limits validation
        limitService.validateTransferLimits(transfer.getSenderId(), transfer.getAmount());
        
        // Network availability validation
        validationService.validateNetworkAvailability(transfer.getTransferMethod());
        
        log.debug("Instant transfer validation completed: transferId={}", transfer.getId());
    }

    /**
     * Performs real-time fraud detection
     */
    private void performFraudDetection(InstantTransfer transfer) {
        log.debug("Performing fraud detection: transferId={}", transfer.getId());
        
        transfer.setStatus(PaymentStatus.FRAUD_CHECKING);
        instantTransferRepository.save(transfer);
        
        try {
            // Real-time fraud scoring
            var fraudScore = fraudDetectionClient.evaluateInstantTransfer(
                    transfer.getSenderId(),
                    transfer.getRecipientId(),
                    transfer.getAmount(),
                    transfer.getTransferMethod()
            );
            
            transfer.setFraudScore(fraudScore.getScore());
            transfer.setRiskLevel(fraudScore.getRiskLevel());
            
            // Check if transfer should be blocked
            if (fraudScore.shouldBlock()) {
                transfer.setStatus(PaymentStatus.BLOCKED);
                transfer.setFailureReason("Transfer blocked due to fraud risk: " + fraudScore.getReason());
                instantTransferRepository.save(transfer);
                
                // Notify fraud team
                notificationServiceClient.sendFraudAlert(transfer.getId(), fraudScore);
                
                throw new InstantTransferException("Transfer blocked due to fraud risk");
            }
            
            // High risk transfers may require additional verification
            if (fraudScore.isHighRisk()) {
                // For instant transfers, we may need to step down to regular transfer
                log.warn("High fraud risk detected for instant transfer: transferId={}, score={}", 
                        transfer.getId(), fraudScore.getScore());
            }
            
        } catch (Exception e) {
            transfer.setStatus(PaymentStatus.FAILED);
            transfer.setFailureReason("Fraud detection failed: " + e.getMessage());
            instantTransferRepository.save(transfer);
            throw e;
        }
        
        log.debug("Fraud detection completed: transferId={}, score={}", 
                transfer.getId(), transfer.getFraudScore());
    }

    /**
     * Reserves funds for the transfer
     */
    private void reserveFunds(InstantTransfer transfer) {
        log.debug("Reserving funds: transferId={}", transfer.getId());
        
        transfer.setStatus(PaymentStatus.RESERVING_FUNDS);
        instantTransferRepository.save(transfer);
        
        try {
            // Reserve funds from sender's wallet
            var reservationResult = walletServiceClient.reserveFunds(
                    transfer.getSenderId(),
                    transfer.getAmount(),
                    "INSTANT_TRANSFER:" + transfer.getId()
            );
            
            transfer.setReservationId(reservationResult.getReservationId());
            transfer.setStatus(PaymentStatus.FUNDS_RESERVED);
            instantTransferRepository.save(transfer);
            
            log.debug("Funds reserved successfully: transferId={}, reservationId={}", 
                    transfer.getId(), transfer.getReservationId());
            
        } catch (InsufficientFundsException e) {
            transfer.setStatus(PaymentStatus.FAILED);
            transfer.setFailureReason("Insufficient funds");
            instantTransferRepository.save(transfer);
            throw e;
        } catch (Exception e) {
            transfer.setStatus(PaymentStatus.FAILED);
            transfer.setFailureReason("Fund reservation failed: " + e.getMessage());
            instantTransferRepository.save(transfer);
            throw new InstantTransferException("Fund reservation failed", e);
        }
    }

    /**
     * Executes the instant transfer through appropriate network
     */
    private void executeInstantTransfer(InstantTransfer transfer) {
        log.debug("Executing instant transfer: transferId={}, method={}", 
                transfer.getId(), transfer.getTransferMethod());
        
        transfer.setStatus(PaymentStatus.PROCESSING);
        transfer.setProcessingStartedAt(LocalDateTime.now());
        instantTransferRepository.save(transfer);
        
        try {
            switch (transfer.getTransferMethod()) {
                case FEDNOW:
                    executeViaFedNow(transfer);
                    break;
                case RTP:
                    executeViaRTP(transfer);
                    break;
                case INTERNAL:
                    executeInternalTransfer(transfer);
                    break;
                case ZELLE:
                    executeViaZelle(transfer);
                    break;
                default:
                    throw new InstantTransferException("Unsupported transfer method: " + transfer.getTransferMethod());
            }
            
            transfer.setStatus(PaymentStatus.COMPLETED);
            transfer.setCompletedAt(LocalDateTime.now());
            transfer.setProcessingTime(calculateProcessingTime(transfer));
            instantTransferRepository.save(transfer);
            
            // Confirm fund usage
            walletServiceClient.confirmReservation(transfer.getReservationId());
            
            log.info("Instant transfer executed successfully: transferId={}, processingTime={}ms", 
                    transfer.getId(), transfer.getProcessingTime());
            
        } catch (Exception e) {
            transfer.setStatus(PaymentStatus.FAILED);
            transfer.setFailureReason("Transfer execution failed: " + e.getMessage());
            transfer.setCompletedAt(LocalDateTime.now());
            instantTransferRepository.save(transfer);
            
            // Release reserved funds
            walletServiceClient.releaseReservation(transfer.getReservationId());
            
            throw new InstantTransferException("Transfer execution failed", e);
        }
    }

    /**
     * Executes transfer via FedNow network
     */
    private void executeViaFedNow(InstantTransfer transfer) {
        log.debug("Executing transfer via FedNow: transferId={}", transfer.getId());
        
        try {
            var fedNowRequest = buildFedNowRequest(transfer);
            var fedNowResponse = rtpNetworkClient.sendFedNowTransfer(fedNowRequest);
            
            if (!fedNowResponse.isSuccessful()) {
                throw new InstantTransferException("FedNow transfer failed: " + fedNowResponse.getErrorMessage());
            }
            
            transfer.setNetworkTransactionId(fedNowResponse.getTransactionId());
            transfer.setNetworkResponse(fedNowResponse.toJson());
            
        } catch (Exception e) {
            log.error("FedNow transfer failed: transferId={}", transfer.getId(), e);
            throw e;
        }
    }

    /**
     * Executes transfer via Real-Time Payments (RTP) network
     */
    private void executeViaRTP(InstantTransfer transfer) {
        log.debug("Executing transfer via RTP: transferId={}", transfer.getId());
        
        try {
            var rtpRequest = buildRTPRequest(transfer);
            var rtpResponse = rtpNetworkClient.sendRTPTransfer(rtpRequest);
            
            if (!rtpResponse.isSuccessful()) {
                throw new InstantTransferException("RTP transfer failed: " + rtpResponse.getErrorMessage());
            }
            
            transfer.setNetworkTransactionId(rtpResponse.getTransactionId());
            transfer.setNetworkResponse(rtpResponse.toJson());
            
        } catch (Exception e) {
            log.error("RTP transfer failed: transferId={}", transfer.getId(), e);
            throw e;
        }
    }

    /**
     * Executes internal instant transfer (between Waqiti users)
     */
    private void executeInternalTransfer(InstantTransfer transfer) {
        log.debug("Executing internal instant transfer: transferId={}", transfer.getId());
        
        try {
            // Debit sender's wallet
            walletServiceClient.debitWallet(
                    transfer.getSenderId(),
                    transfer.getAmount(),
                    "INSTANT_TRANSFER_DEBIT:" + transfer.getId()
            );
            
            // Credit recipient's wallet
            walletServiceClient.creditWallet(
                    transfer.getRecipientId(),
                    transfer.getAmount(),
                    "INSTANT_TRANSFER_CREDIT:" + transfer.getId()
            );
            
            transfer.setNetworkTransactionId("INTERNAL:" + transfer.getId());
            
        } catch (Exception e) {
            log.error("Internal instant transfer failed: transferId={}", transfer.getId(), e);
            throw e;
        }
    }

    /**
     * Executes transfer via Zelle network
     */
    private void executeViaZelle(InstantTransfer transfer) {
        log.debug("Executing transfer via Zelle: transferId={}", transfer.getId());
        
        try {
            var zelleRequest = buildZelleRequest(transfer);
            var zelleResponse = rtpNetworkClient.sendZelleTransfer(zelleRequest);
            
            if (!zelleResponse.isSuccessful()) {
                throw new InstantTransferException("Zelle transfer failed: " + zelleResponse.getErrorMessage());
            }
            
            transfer.setNetworkTransactionId(zelleResponse.getTransactionId());
            transfer.setNetworkResponse(zelleResponse.toJson());
            
        } catch (Exception e) {
            log.error("Zelle transfer failed: transferId={}", transfer.getId(), e);
            throw e;
        }
    }

    /**
     * Sends notifications to participants
     */
    private void sendNotifications(InstantTransfer transfer) {
        try {
            // Notify sender
            notificationServiceClient.sendInstantTransferSentNotification(
                    transfer.getSenderId(),
                    transfer.getId(),
                    transfer.getAmount(),
                    transfer.getRecipientId()
            );
            
            // Notify recipient
            notificationServiceClient.sendInstantTransferReceivedNotification(
                    transfer.getRecipientId(),
                    transfer.getId(),
                    transfer.getAmount(),
                    transfer.getSenderId()
            );
            
        } catch (Exception e) {
            log.warn("Failed to send notifications for instant transfer: transferId={}", 
                    transfer.getId(), e);
            // Don't fail the transfer for notification errors
        }
    }

    /**
     * Updates analytics and metrics
     */
    private void updateAnalytics(InstantTransfer transfer) {
        try {
            eventPublisher.publishInstantTransferCompletedEvent(transfer);
        } catch (Exception e) {
            log.warn("Failed to update analytics for instant transfer: transferId={}", 
                    transfer.getId(), e);
            // Don't fail the transfer for analytics errors
        }
    }

    /**
     * Determines the appropriate transfer method
     */
    private InstantTransfer.TransferMethod determineTransferMethod(InstantTransferRequest request) {
        // Logic to determine best transfer method based on:
        // - Sender and recipient bank networks
        // - Amount limits
        // - Network availability
        // - Cost optimization
        
        if (request.getPreferredMethod() != null) {
            return request.getPreferredMethod();
        }
        
        // Check if both users are internal (Waqiti users)
        boolean bothInternal = userServiceClient.areBothUsersInternal(
                request.getSenderId(), request.getRecipientId());
        
        if (bothInternal) {
            return InstantTransfer.TransferMethod.INTERNAL;
        }
        
        // Check network availability and choose best option
        if (rtpNetworkClient.isFedNowAvailable() && isFedNowEligible(request)) {
            return InstantTransfer.TransferMethod.FEDNOW;
        } else if (rtpNetworkClient.isRTPAvailable() && isRTPEligible(request)) {
            return InstantTransfer.TransferMethod.RTP;
        } else if (rtpNetworkClient.isZelleAvailable() && isZelleEligible(request)) {
            return InstantTransfer.TransferMethod.ZELLE;
        }
        
        throw new InstantTransferException("No suitable instant transfer method available");
    }

    /**
     * Builds successful transfer response
     */
    private InstantTransferResponse buildSuccessResponse(InstantTransfer transfer) {
        return InstantTransferResponse.builder()
                .transferId(transfer.getId())
                .status(transfer.getStatus())
                .amount(transfer.getAmount().getAmount())
                .currency(transfer.getAmount().getCurrency().getCurrencyCode())
                .transferMethod(transfer.getTransferMethod())
                .processingTime(transfer.getProcessingTime())
                .networkTransactionId(transfer.getNetworkTransactionId())
                .completedAt(transfer.getCompletedAt())
                .build();
    }

    /**
     * Calculates processing time in milliseconds
     */
    private Long calculateProcessingTime(InstantTransfer transfer) {
        if (transfer.getProcessingStartedAt() != null && transfer.getCompletedAt() != null) {
            return java.time.Duration.between(transfer.getProcessingStartedAt(), transfer.getCompletedAt())
                    .toMillis();
        }
        
        // If transfer is still in progress, calculate partial processing time
        if (transfer.getProcessingStartedAt() != null && transfer.getStatus() == PaymentStatus.PROCESSING) {
            return java.time.Duration.between(transfer.getProcessingStartedAt(), Instant.now())
                    .toMillis();
        }
        
        // Return 0 for transfers that haven't started processing yet
        return 0L;
    }

    /**
     * Fallback method for circuit breaker
     */
    public CompletableFuture<InstantTransferResponse> instantTransferFallback(
            InstantTransferRequest request, Exception ex) {
        log.error("Instant transfer fallback triggered: error={}", ex.getMessage());
        
        return CompletableFuture.failedFuture(
                new InstantTransferException("Instant transfer service temporarily unavailable", ex)
        );
    }

    /**
     * Gets instant transfer status
     */
    public InstantTransferResponse getTransferStatus(UUID transferId) {
        InstantTransfer transfer = instantTransferRepository.findById(transferId)
                .orElseThrow(() -> new InstantTransferException("Transfer not found: " + transferId));
        
        return buildSuccessResponse(transfer);
    }

    /**
     * Cancels an instant transfer (if still possible)
     */
    @Transactional
    public void cancelInstantTransfer(UUID transferId, UUID userId, String reason) {
        InstantTransfer transfer = instantTransferRepository.findById(transferId)
                .orElseThrow(() -> new InstantTransferException("Transfer not found: " + transferId));
        
        // Verify user authorization
        if (!transfer.getSenderId().equals(userId)) {
            throw new InstantTransferException("Not authorized to cancel this transfer");
        }
        
        // Check if transfer can be canceled
        if (!transfer.canBeCanceled()) {
            throw new InstantTransferException("Transfer cannot be canceled in current status: " + transfer.getStatus());
        }
        
        // Cancel the transfer
        transfer.setStatus(PaymentStatus.CANCELLED);
        transfer.setCancellationReason(reason);
        transfer.setCanceledAt(LocalDateTime.now());
        instantTransferRepository.save(transfer);
        
        // Release any reserved funds
        if (transfer.getReservationId() != null) {
            walletServiceClient.releaseReservation(transfer.getReservationId());
        }
        
        log.info("Instant transfer canceled: transferId={}, reason={}", transferId, reason);
    }

    // Helper methods for transfer eligibility and request building
    
    /**
     * Check if transfer is eligible for FedNow network
     */
    private boolean isFedNowEligible(InstantTransferRequest request) {
        try {
            // Check amount limits (FedNow supports up to $500K)
            if (request.getAmount() > 500000) {
                log.debug("Amount {} exceeds FedNow limit", request.getAmount());
                return false;
            }
            
            // Check if both sender and recipient banks support FedNow
            boolean senderSupported;
            boolean recipientSupported;
            
            try {
                senderSupported = userServiceClient.doesUserBankSupportFedNow(request.getSenderId());
            } catch (Exception e) {
                log.warn("Failed to check FedNow support for sender {}: {}", request.getSenderId(), e.getMessage());
                return false;
            }
            
            try {
                recipientSupported = userServiceClient.doesUserBankSupportFedNow(request.getRecipientId());
            } catch (Exception e) {
                log.warn("Failed to check FedNow support for recipient {}: {}", request.getRecipientId(), e.getMessage());
                return false;
            }
            
            return senderSupported && recipientSupported;
        } catch (Exception e) {
            log.error("Error checking FedNow eligibility", e);
            return false;
        }
    }
    
    /**
     * Check if transfer is eligible for RTP network
     */
    private boolean isRTPEligible(InstantTransferRequest request) {
        try {
            // Check amount limits (RTP supports up to $100K)
            if (request.getAmount() > 100000) {
                log.debug("Amount {} exceeds RTP limit", request.getAmount());
                return false;
            }
            
            // Check if both sender and recipient banks support RTP
            boolean senderSupported;
            boolean recipientSupported;
            
            try {
                senderSupported = userServiceClient.doesUserBankSupportRTP(request.getSenderId());
            } catch (Exception e) {
                log.warn("Failed to check RTP support for sender {}: {}", request.getSenderId(), e.getMessage());
                return false;
            }
            
            try {
                recipientSupported = userServiceClient.doesUserBankSupportRTP(request.getRecipientId());
            } catch (Exception e) {
                log.warn("Failed to check RTP support for recipient {}: {}", request.getRecipientId(), e.getMessage());
                return false;
            }
            
            return senderSupported && recipientSupported;
        } catch (Exception e) {
            log.error("Error checking RTP eligibility", e);
            return false;
        }
    }
    
    /**
     * Check if transfer is eligible for Zelle network
     */
    private boolean isZelleEligible(InstantTransferRequest request) {
        try {
            // Check amount limits (Zelle typically supports up to $2,500 per day)
            if (request.getAmount() > 2500) {
                log.debug("Amount {} exceeds Zelle daily limit", request.getAmount());
                return false;
            }
            
            // Check if both users have Zelle-enabled accounts
            boolean senderSupported;
            boolean recipientSupported;
            
            try {
                senderSupported = userServiceClient.doesUserHaveZelle(request.getSenderId());
            } catch (Exception e) {
                log.warn("Failed to check Zelle support for sender {}: {}", request.getSenderId(), e.getMessage());
                return false;
            }
            
            try {
                recipientSupported = userServiceClient.doesUserHaveZelle(request.getRecipientId());
            } catch (Exception e) {
                log.warn("Failed to check Zelle support for recipient {}: {}", request.getRecipientId(), e.getMessage());
                return false;
            }
            
            return senderSupported && recipientSupported;
        } catch (Exception e) {
            log.error("Error checking Zelle eligibility", e);
            return false;
        }
    }
    
    /**
     * Build FedNow transfer request
     */
    private Object buildFedNowRequest(InstantTransfer transfer) {
        try {
            // Get user bank details
            var senderBankInfo = userServiceClient.getUserBankInfo(transfer.getSenderId());
            var recipientBankInfo = userServiceClient.getUserBankInfo(transfer.getRecipientId());
            
            return Map.of(
                "messageType", "FedNowInstantTransfer",
                "transferId", transfer.getId().toString(),
                "amount", transfer.getAmount().getAmount(),
                "currency", transfer.getAmount().getCurrency().getCurrencyCode(),
                "senderInfo", Map.of(
                    "userId", transfer.getSenderId().toString(),
                    "bankRoutingNumber", senderBankInfo.getRoutingNumber(),
                    "accountNumber", senderBankInfo.getAccountNumber()
                ),
                "recipientInfo", Map.of(
                    "userId", transfer.getRecipientId().toString(),
                    "bankRoutingNumber", recipientBankInfo.getRoutingNumber(),
                    "accountNumber", recipientBankInfo.getAccountNumber()
                ),
                "description", transfer.getDescription(),
                "timestamp", transfer.getCreatedAt().toString()
            );
        } catch (Exception e) {
            log.error("Failed to build FedNow request", e);
            throw new InstantTransferException("Failed to build FedNow request", e);
        }
    }
    
    /**
     * Build RTP transfer request
     */
    private Object buildRTPRequest(InstantTransfer transfer) {
        try {
            // Get user bank details
            var senderBankInfo = userServiceClient.getUserBankInfo(transfer.getSenderId());
            var recipientBankInfo = userServiceClient.getUserBankInfo(transfer.getRecipientId());
            
            return Map.of(
                "messageType", "RTPInstantTransfer",
                "transferId", transfer.getId().toString(),
                "amount", transfer.getAmount().getAmount(),
                "currency", transfer.getAmount().getCurrency().getCurrencyCode(),
                "senderInfo", Map.of(
                    "userId", transfer.getSenderId().toString(),
                    "bankRoutingNumber", senderBankInfo.getRoutingNumber(),
                    "accountNumber", senderBankInfo.getAccountNumber()
                ),
                "recipientInfo", Map.of(
                    "userId", transfer.getRecipientId().toString(),
                    "bankRoutingNumber", recipientBankInfo.getRoutingNumber(),
                    "accountNumber", recipientBankInfo.getAccountNumber()
                ),
                "description", transfer.getDescription(),
                "timestamp", transfer.getCreatedAt().toString(),
                "requestForPayment", false
            );
        } catch (Exception e) {
            log.error("Failed to build RTP request", e);
            throw new InstantTransferException("Failed to build RTP request", e);
        }
    }
    
    /**
     * Build Zelle transfer request
     */
    private Object buildZelleRequest(InstantTransfer transfer) {
        try {
            // Get user Zelle info
            var senderZelleInfo = userServiceClient.getUserZelleInfo(transfer.getSenderId());
            var recipientZelleInfo = userServiceClient.getUserZelleInfo(transfer.getRecipientId());
            
            return Map.of(
                "messageType", "ZelleInstantTransfer",
                "transferId", transfer.getId().toString(),
                "amount", transfer.getAmount().getAmount(),
                "currency", transfer.getAmount().getCurrency().getCurrencyCode(),
                "senderInfo", Map.of(
                    "userId", transfer.getSenderId().toString(),
                    "zelleId", senderZelleInfo.getZelleId(),
                    "phoneNumber", senderZelleInfo.getPhoneNumber(),
                    "email", senderZelleInfo.getEmail()
                ),
                "recipientInfo", Map.of(
                    "userId", transfer.getRecipientId().toString(),
                    "zelleId", recipientZelleInfo.getZelleId(),
                    "phoneNumber", recipientZelleInfo.getPhoneNumber(),
                    "email", recipientZelleInfo.getEmail()
                ),
                "description", transfer.getDescription(),
                "timestamp", transfer.getCreatedAt().toString()
            );
        } catch (Exception e) {
            log.error("Failed to build Zelle request", e);
            throw new InstantTransferException("Failed to build Zelle request", e);
        }
    }
}