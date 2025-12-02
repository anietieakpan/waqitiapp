package com.waqiti.voice.service;

import com.waqiti.voice.domain.*;
import com.waqiti.voice.dto.*;
import com.waqiti.voice.repository.*;
import com.waqiti.common.security.SecureTokenVaultService;
import com.waqiti.common.validation.FinancialInputValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Enterprise voice payment transaction manager handling the complete
 * lifecycle of voice-initiated financial transactions with security,
 * compliance, and fraud detection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VoicePaymentTransactionManager {

    private final VoiceTransactionRepository voiceTransactionRepository;
    private final VoicePaymentMethodRepository paymentMethodRepository;
    private final VoiceComplianceService complianceService;
    private final VoiceFraudDetectionService fraudDetectionService;
    private final SecureTokenVaultService tokenVaultService;
    private final FinancialInputValidator financialValidator;
    private final VoiceAuditService auditService;
    private final VoiceNotificationService notificationService;
    private final PaymentGatewayService paymentGatewayService;
    private final VoiceSessionManager sessionManager;
    
    private final Map<UUID, ReentrantLock> transactionLocks = new HashMap<>();
    
    // Transaction limits
    private static final BigDecimal VOICE_DAILY_LIMIT = new BigDecimal("5000.00");
    private static final BigDecimal VOICE_SINGLE_LIMIT = new BigDecimal("1000.00");
    private static final BigDecimal CONFIRMATION_THRESHOLD = new BigDecimal("100.00");
    private static final int MAX_RETRIES = 3;

    /**
     * Processes a voice-initiated payment with comprehensive security
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public VoicePaymentResult processVoicePayment(VoicePaymentRequest request) {
        try {
            log.info("Processing voice payment: userId={}, amount={}, recipient={}, sessionId={}", 
                    request.getUserId(), request.getAmount(), 
                    request.getRecipientIdentifier(), request.getSessionId());

            // 1. Validate voice payment request
            VoicePaymentValidation validation = validateVoicePaymentRequest(request);
            if (!validation.isValid()) {
                return VoicePaymentResult.failure(validation.getErrorMessage(), validation.getErrors());
            }

            // 2. Acquire transaction lock to prevent concurrent processing
            ReentrantLock lock = transactionLocks.computeIfAbsent(request.getUserId(), k -> new ReentrantLock());
            lock.lock();
            
            try {
                // 3. Create voice transaction record
                VoiceTransaction transaction = createVoiceTransaction(request, validation);
                transaction = voiceTransactionRepository.save(transaction);

                // 4. Perform voice-specific fraud detection
                VoiceFraudAssessment fraudAssessment = fraudDetectionService.assessVoicePayment(
                        request, transaction, validation.getVoiceMetrics());

                if (fraudAssessment.getRiskLevel() == VoiceRiskLevel.CRITICAL) {
                    transaction.setStatus(VoiceTransactionStatus.BLOCKED);
                    transaction.setFailureReason("Blocked due to fraud detection");
                    voiceTransactionRepository.save(transaction);
                    
                    auditService.logSecurityEvent(request.getUserId(), "VOICE_PAYMENT_BLOCKED", 
                            fraudAssessment.getRiskIndicators());
                    
                    return VoicePaymentResult.blocked("Payment blocked due to security concerns", 
                            fraudAssessment);
                }

                // 5. Check compliance requirements
                VoiceComplianceResult complianceResult = complianceService.validateVoicePayment(
                        request, transaction, fraudAssessment);

                if (!complianceResult.isCompliant()) {
                    transaction.setStatus(VoiceTransactionStatus.COMPLIANCE_REVIEW);
                    transaction.setComplianceFlags(complianceResult.getFlags());
                    voiceTransactionRepository.save(transaction);
                    
                    return VoicePaymentResult.complianceHold("Payment requires compliance review", 
                            complianceResult);
                }

                // 6. Process based on risk level and amount
                return processBasedOnRiskAndAmount(transaction, request, fraudAssessment, complianceResult);

            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            log.error("Voice payment processing failed: userId={}, sessionId={}", 
                    request.getUserId(), request.getSessionId(), e);
            
            auditService.logTransactionError(request.getUserId(), request.getSessionId(), 
                    "VOICE_PAYMENT_ERROR", e.getMessage());
            
            return VoicePaymentResult.error("Payment processing failed: " + e.getMessage());
        }
    }

    /**
     * Confirms a pending voice payment transaction
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public VoicePaymentResult confirmVoicePayment(VoicePaymentConfirmationRequest request) {
        try {
            log.info("Confirming voice payment: transactionId={}, userId={}, confirmed={}", 
                    request.getTransactionId(), request.getUserId(), request.isConfirmed());

            // Get the pending transaction
            VoiceTransaction transaction = voiceTransactionRepository
                    .findByIdAndUserId(request.getTransactionId(), request.getUserId())
                    .orElseThrow(() -> new VoiceTransactionException("Transaction not found"));

            // Validate transaction can be confirmed
            if (transaction.getStatus() != VoiceTransactionStatus.PENDING_CONFIRMATION) {
                return VoicePaymentResult.failure("Transaction is not in confirmable state", 
                        Arrays.asList("Current status: " + transaction.getStatus()));
            }

            // Check confirmation timeout
            if (transaction.getConfirmationExpiresAt().isBefore(LocalDateTime.now())) {
                transaction.setStatus(VoiceTransactionStatus.EXPIRED);
                voiceTransactionRepository.save(transaction);
                
                return VoicePaymentResult.failure("Confirmation expired", 
                        Arrays.asList("Please initiate a new payment"));
            }

            if (request.isConfirmed()) {
                // Execute the confirmed payment
                return executeConfirmedPayment(transaction, request);
            } else {
                // Cancel the payment
                return cancelVoicePayment(transaction, request.getCancellationReason());
            }

        } catch (Exception e) {
            log.error("Voice payment confirmation failed: transactionId={}", 
                    request.getTransactionId(), e);
            return VoicePaymentResult.error("Confirmation processing failed: " + e.getMessage());
        }
    }

    /**
     * Handles voice payment retry logic
     */
    @Transactional
    public VoicePaymentResult retryVoicePayment(VoicePaymentRetryRequest request) {
        try {
            log.info("Retrying voice payment: originalTransactionId={}, userId={}, retryReason={}", 
                    request.getOriginalTransactionId(), request.getUserId(), request.getRetryReason());

            // Get original transaction
            VoiceTransaction originalTransaction = voiceTransactionRepository
                    .findByIdAndUserId(request.getOriginalTransactionId(), request.getUserId())
                    .orElseThrow(() -> new VoiceTransactionException("Original transaction not found"));

            // Check retry limits
            if (originalTransaction.getRetryCount() >= MAX_RETRIES) {
                return VoicePaymentResult.failure("Maximum retry attempts exceeded", 
                        Arrays.asList("Please initiate a new payment"));
            }

            // Update original transaction
            originalTransaction.incrementRetryCount();
            originalTransaction.setLastRetryAt(LocalDateTime.now());
            originalTransaction.setRetryReason(request.getRetryReason());

            // Create new payment request from original
            VoicePaymentRequest newRequest = VoicePaymentRequest.builder()
                    .userId(request.getUserId())
                    .sessionId(request.getNewSessionId())
                    .amount(originalTransaction.getAmount())
                    .currency(originalTransaction.getCurrency())
                    .recipientIdentifier(originalTransaction.getRecipientIdentifier())
                    .paymentMethod(originalTransaction.getPaymentMethod())
                    .description(originalTransaction.getDescription())
                    .voiceMetrics(request.getUpdatedVoiceMetrics())
                    .isRetry(true)
                    .originalTransactionId(originalTransaction.getId())
                    .build();

            // Process the retry payment
            VoicePaymentResult result = processVoicePayment(newRequest);

            // Link transactions
            if (result.isSuccess()) {
                originalTransaction.setRetryTransactionId(result.getTransactionId());
                originalTransaction.setStatus(VoiceTransactionStatus.RETRIED);
            } else {
                originalTransaction.setStatus(VoiceTransactionStatus.RETRY_FAILED);
            }

            voiceTransactionRepository.save(originalTransaction);
            return result;

        } catch (Exception e) {
            log.error("Voice payment retry failed: originalTransactionId={}", 
                    request.getOriginalTransactionId(), e);
            return VoicePaymentResult.error("Payment retry failed: " + e.getMessage());
        }
    }

    /**
     * Processes batch voice payments for bill splitting or group payments
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public VoiceBatchPaymentResult processBatchVoicePayments(VoiceBatchPaymentRequest request) {
        try {
            log.info("Processing batch voice payments: userId={}, batchSize={}, totalAmount={}", 
                    request.getUserId(), request.getPayments().size(), request.getTotalAmount());

            // Validate batch request
            VoiceBatchValidation batchValidation = validateBatchPaymentRequest(request);
            if (!batchValidation.isValid()) {
                return VoiceBatchPaymentResult.failure(batchValidation.getErrorMessage());
            }

            // Create batch transaction record
            VoiceBatchTransaction batchTransaction = createBatchTransaction(request, batchValidation);
            batchTransaction = voiceTransactionRepository.saveBatch(batchTransaction);

            List<VoicePaymentResult> individualResults = new ArrayList<>();
            List<VoiceTransaction> successfulTransactions = new ArrayList<>();
            List<VoiceTransaction> failedTransactions = new ArrayList<>();

            // Process each payment in the batch
            for (VoicePaymentRequest paymentRequest : request.getPayments()) {
                try {
                    VoicePaymentResult result = processVoicePayment(paymentRequest);
                    individualResults.add(result);

                    if (result.isSuccess()) {
                        VoiceTransaction transaction = voiceTransactionRepository
                                .findById(result.getTransactionId()).orElse(null);
                        if (transaction != null) {
                            transaction.setBatchId(batchTransaction.getId());
                            successfulTransactions.add(transaction);
                        }
                    } else {
                        // Create failed transaction record
                        VoiceTransaction failedTransaction = createFailedTransactionFromResult(
                                paymentRequest, result, batchTransaction.getId());
                        failedTransactions.add(failedTransaction);
                    }

                } catch (Exception e) {
                    log.error("Individual payment failed in batch: userId={}, recipient={}", 
                            paymentRequest.getUserId(), paymentRequest.getRecipientIdentifier(), e);
                    
                    VoicePaymentResult failureResult = VoicePaymentResult.error(
                            "Payment failed: " + e.getMessage());
                    individualResults.add(failureResult);
                }
            }

            // Update batch transaction status
            batchTransaction.setSuccessfulCount(successfulTransactions.size());
            batchTransaction.setFailedCount(failedTransactions.size());
            batchTransaction.setProcessedAt(LocalDateTime.now());

            if (failedTransactions.isEmpty()) {
                batchTransaction.setStatus(VoiceBatchStatus.COMPLETED);
            } else if (successfulTransactions.isEmpty()) {
                batchTransaction.setStatus(VoiceBatchStatus.FAILED);
            } else {
                batchTransaction.setStatus(VoiceBatchStatus.PARTIAL_SUCCESS);
            }

            voiceTransactionRepository.saveBatch(batchTransaction);

            // Send notifications
            sendBatchPaymentNotifications(batchTransaction, successfulTransactions, failedTransactions);

            log.info("Batch voice payment processing completed: batchId={}, successful={}, failed={}", 
                    batchTransaction.getId(), successfulTransactions.size(), failedTransactions.size());

            return VoiceBatchPaymentResult.builder()
                    .batchId(batchTransaction.getId())
                    .totalPayments(request.getPayments().size())
                    .successfulPayments(successfulTransactions.size())
                    .failedPayments(failedTransactions.size())
                    .individualResults(individualResults)
                    .batchTransaction(batchTransaction)
                    .processedAt(LocalDateTime.now())
                    .overallSuccess(failedTransactions.isEmpty())
                    .build();

        } catch (Exception e) {
            log.error("Batch voice payment processing failed: userId={}", request.getUserId(), e);
            return VoiceBatchPaymentResult.failure("Batch processing failed: " + e.getMessage());
        }
    }

    /**
     * Monitors voice payment status with real-time updates
     */
    @Transactional(readOnly = true)
    public VoicePaymentStatusResult monitorVoicePaymentStatus(VoicePaymentStatusRequest request) {
        try {
            log.debug("Monitoring voice payment status: transactionId={}, userId={}", 
                    request.getTransactionId(), request.getUserId());

            VoiceTransaction transaction = voiceTransactionRepository
                    .findByIdAndUserId(request.getTransactionId(), request.getUserId())
                    .orElseThrow(() -> new VoiceTransactionException("Transaction not found"));

            // Get real-time status from payment gateway
            PaymentGatewayStatus gatewayStatus = null;
            if (transaction.getPaymentGatewayId() != null) {
                gatewayStatus = paymentGatewayService.getTransactionStatus(
                        transaction.getPaymentGatewayId());
            }

            // Calculate processing metrics
            VoicePaymentMetrics metrics = calculatePaymentMetrics(transaction, gatewayStatus);

            // Determine status updates
            VoiceTransactionStatus updatedStatus = determineUpdatedStatus(transaction, gatewayStatus);
            if (updatedStatus != transaction.getStatus()) {
                transaction.setStatus(updatedStatus);
                transaction.setLastStatusUpdate(LocalDateTime.now());
                voiceTransactionRepository.save(transaction);

                // Send status update notification
                notificationService.sendVoicePaymentStatusUpdate(transaction, updatedStatus);
            }

            // Generate status timeline
            List<VoicePaymentStatusEvent> statusTimeline = generateStatusTimeline(transaction);

            // Check for any issues or alerts
            List<VoicePaymentAlert> alerts = checkForPaymentAlerts(transaction, gatewayStatus, metrics);

            return VoicePaymentStatusResult.builder()
                    .transactionId(transaction.getId())
                    .currentStatus(transaction.getStatus())
                    .statusTimeline(statusTimeline)
                    .paymentMetrics(metrics)
                    .gatewayStatus(gatewayStatus)
                    .alerts(alerts)
                    .lastUpdated(LocalDateTime.now())
                    .estimatedCompletion(estimateCompletionTime(transaction, gatewayStatus))
                    .isTerminalState(isTerminalStatus(transaction.getStatus()))
                    .build();

        } catch (Exception e) {
            log.error("Voice payment status monitoring failed: transactionId={}", 
                    request.getTransactionId(), e);
            return VoicePaymentStatusResult.error("Status monitoring failed: " + e.getMessage());
        }
    }

    /**
     * Handles voice payment cancellation
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public VoicePaymentResult cancelVoicePayment(VoicePaymentCancellationRequest request) {
        try {
            log.info("Cancelling voice payment: transactionId={}, userId={}, reason={}", 
                    request.getTransactionId(), request.getUserId(), request.getCancellationReason());

            VoiceTransaction transaction = voiceTransactionRepository
                    .findByIdAndUserId(request.getTransactionId(), request.getUserId())
                    .orElseThrow(() -> new VoiceTransactionException("Transaction not found"));

            // Check if transaction can be cancelled
            if (!canCancelTransaction(transaction)) {
                return VoicePaymentResult.failure("Transaction cannot be cancelled", 
                        Arrays.asList("Current status: " + transaction.getStatus()));
            }

            return cancelVoicePayment(transaction, request.getCancellationReason());

        } catch (Exception e) {
            log.error("Voice payment cancellation failed: transactionId={}", 
                    request.getTransactionId(), e);
            return VoicePaymentResult.error("Cancellation failed: " + e.getMessage());
        }
    }

    /**
     * Processes voice payment dispute
     */
    @Transactional
    public VoiceDisputeResult processVoicePaymentDispute(VoiceDisputeRequest request) {
        try {
            log.info("Processing voice payment dispute: transactionId={}, userId={}, disputeType={}", 
                    request.getTransactionId(), request.getUserId(), request.getDisputeType());

            VoiceTransaction transaction = voiceTransactionRepository
                    .findByIdAndUserId(request.getTransactionId(), request.getUserId())
                    .orElseThrow(() -> new VoiceTransactionException("Transaction not found"));

            // Validate dispute eligibility
            DisputeEligibility eligibility = validateDisputeEligibility(transaction, request);
            if (!eligibility.isEligible()) {
                return VoiceDisputeResult.ineligible(eligibility.getReason());
            }

            // Create dispute record
            VoicePaymentDispute dispute = VoicePaymentDispute.builder()
                    .id(UUID.randomUUID())
                    .transactionId(transaction.getId())
                    .userId(request.getUserId())
                    .disputeType(request.getDisputeType())
                    .reason(request.getReason())
                    .evidence(request.getEvidence())
                    .voiceRecordingUrl(transaction.getVoiceRecordingUrl())
                    .status(VoiceDisputeStatus.SUBMITTED)
                    .submittedAt(LocalDateTime.now())
                    .build();

            dispute = voiceTransactionRepository.saveDispute(dispute);

            // Update transaction status
            transaction.setStatus(VoiceTransactionStatus.DISPUTED);
            transaction.setDisputeId(dispute.getId());
            voiceTransactionRepository.save(transaction);

            // Initiate dispute investigation
            initiateDisputeInvestigation(dispute, transaction);

            // Send dispute confirmation
            notificationService.sendDisputeConfirmation(request.getUserId(), dispute);

            log.info("Voice payment dispute created: disputeId={}, transactionId={}", 
                    dispute.getId(), transaction.getId());

            return VoiceDisputeResult.builder()
                    .disputeId(dispute.getId())
                    .transactionId(transaction.getId())
                    .disputeStatus(dispute.getStatus())
                    .estimatedResolutionTime(LocalDateTime.now().plusDays(7))
                    .caseNumber(generateCaseNumber(dispute))
                    .nextSteps(generateDisputeNextSteps(dispute))
                    .submittedAt(dispute.getSubmittedAt())
                    .successful(true)
                    .build();

        } catch (Exception e) {
            log.error("Voice payment dispute processing failed: transactionId={}", 
                    request.getTransactionId(), e);
            return VoiceDisputeResult.error("Dispute processing failed: " + e.getMessage());
        }
    }

    // Private helper methods

    private VoicePaymentValidation validateVoicePaymentRequest(VoicePaymentRequest request) {
        List<String> errors = new ArrayList<>();
        VoiceMetrics voiceMetrics = new VoiceMetrics();

        // Validate basic payment parameters
        FinancialInputValidator.ValidationResult amountValidation = financialValidator
                .validatePaymentAmount(request.getAmount(), request.getCurrency(), 
                        request.getUserId().toString());
        
        if (!amountValidation.isValid()) {
            errors.addAll(amountValidation.getErrors().stream()
                    .map(e -> e.getMessage()).toList());
        }

        // Voice-specific validations
        if (request.getVoiceConfidenceScore() < 0.75) {
            errors.add("Voice command not clear enough (confidence: " + 
                      request.getVoiceConfidenceScore() + ")");
            voiceMetrics.setConfidenceScore(request.getVoiceConfidenceScore());
        }

        // Validate recipient
        if (request.getRecipientIdentifier() == null || request.getRecipientIdentifier().trim().isEmpty()) {
            errors.add("Recipient identifier is required");
        }

        // Check voice daily limits
        BigDecimal dailyTotal = calculateDailyVoicePaymentTotal(request.getUserId());
        if (dailyTotal.add(request.getAmount()).compareTo(VOICE_DAILY_LIMIT) > 0) {
            errors.add("Voice payment daily limit exceeded");
        }

        // Check single transaction limit
        if (request.getAmount().compareTo(VOICE_SINGLE_LIMIT) > 0) {
            errors.add("Voice payment amount exceeds single transaction limit");
        }

        return VoicePaymentValidation.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .voiceMetrics(voiceMetrics)
                .build();
    }

    private VoiceTransaction createVoiceTransaction(VoicePaymentRequest request, 
                                                   VoicePaymentValidation validation) {
        return VoiceTransaction.builder()
                .id(UUID.randomUUID())
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .recipientIdentifier(request.getRecipientIdentifier())
                .paymentMethod(request.getPaymentMethod())
                .description(request.getDescription())
                .status(VoiceTransactionStatus.INITIATED)
                .voiceConfidenceScore(request.getVoiceConfidenceScore())
                .transcribedCommand(request.getTranscribedCommand())
                .voiceRecordingUrl(request.getVoiceRecordingUrl())
                .deviceInfo(request.getDeviceInfo())
                .locationInfo(request.getLocationInfo())
                .isRetry(request.isRetry())
                .originalTransactionId(request.getOriginalTransactionId())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .retryCount(0)
                .build();
    }

    private VoicePaymentResult processBasedOnRiskAndAmount(VoiceTransaction transaction,
                                                           VoicePaymentRequest request,
                                                           VoiceFraudAssessment fraudAssessment,
                                                           VoiceComplianceResult complianceResult) {
        try {
            // Determine if confirmation is required
            boolean needsConfirmation = request.getAmount().compareTo(CONFIRMATION_THRESHOLD) > 0 ||
                                      fraudAssessment.getRiskLevel() == VoiceRiskLevel.HIGH ||
                                      !request.isVoiceVerified();

            if (needsConfirmation) {
                return requestVoiceConfirmation(transaction, request, fraudAssessment);
            } else {
                return executeImmediatePayment(transaction, request, fraudAssessment, complianceResult);
            }

        } catch (Exception e) {
            transaction.setStatus(VoiceTransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            voiceTransactionRepository.save(transaction);
            throw e;
        }
    }

    private VoicePaymentResult requestVoiceConfirmation(VoiceTransaction transaction,
                                                        VoicePaymentRequest request,
                                                        VoiceFraudAssessment fraudAssessment) {
        // Set transaction to pending confirmation
        transaction.setStatus(VoiceTransactionStatus.PENDING_CONFIRMATION);
        transaction.setConfirmationExpiresAt(LocalDateTime.now().plusMinutes(5));
        transaction.setFraudScore(fraudAssessment.getFraudScore());
        voiceTransactionRepository.save(transaction);

        // Generate confirmation message
        String confirmationMessage = generateConfirmationMessage(transaction, request);

        // Store secure confirmation data
        Map<String, String> confirmationMetadata = new HashMap<>();
        confirmationMetadata.put("transactionId", transaction.getId().toString());
        confirmationMetadata.put("amount", transaction.getAmount().toString());
        confirmationMetadata.put("currency", transaction.getCurrency());
        confirmationMetadata.put("recipient", transaction.getRecipientIdentifier());

        String confirmationToken = tokenVaultService.vaultSensitiveData(
                confirmationMessage, request.getUserId(), "PAYMENT_CONFIRMATION", confirmationMetadata);

        // Log confirmation request
        auditService.logConfirmationRequest(transaction.getId(), request.getUserId(), 
                fraudAssessment.getRiskLevel());

        return VoicePaymentResult.builder()
                .transactionId(transaction.getId())
                .status(VoiceTransactionStatus.PENDING_CONFIRMATION)
                .requiresConfirmation(true)
                .confirmationMessage(confirmationMessage)
                .confirmationToken(confirmationToken)
                .confirmationExpiresAt(transaction.getConfirmationExpiresAt())
                .fraudAssessment(fraudAssessment)
                .successful(true)
                .build();
    }

    private VoicePaymentResult executeImmediatePayment(VoiceTransaction transaction,
                                                       VoicePaymentRequest request,
                                                       VoiceFraudAssessment fraudAssessment,
                                                       VoiceComplianceResult complianceResult) {
        try {
            // Update transaction status
            transaction.setStatus(VoiceTransactionStatus.PROCESSING);
            transaction.setProcessingStartedAt(LocalDateTime.now());
            voiceTransactionRepository.save(transaction);

            // Execute payment through gateway
            PaymentGatewayRequest gatewayRequest = PaymentGatewayRequest.builder()
                    .transactionId(transaction.getId())
                    .userId(request.getUserId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .recipientIdentifier(request.getRecipientIdentifier())
                    .paymentMethod(request.getPaymentMethod())
                    .description(request.getDescription())
                    .fraudScore(fraudAssessment.getFraudScore())
                    .complianceFlags(complianceResult.getFlags())
                    .initiatedVia("VOICE")
                    .build();

            PaymentGatewayResult gatewayResult = paymentGatewayService.processPayment(gatewayRequest);

            // Update transaction with gateway result
            transaction.setPaymentGatewayId(gatewayResult.getGatewayTransactionId());
            transaction.setGatewayResponse(gatewayResult.getResponseCode());

            if (gatewayResult.isSuccess()) {
                transaction.setStatus(VoiceTransactionStatus.COMPLETED);
                transaction.setCompletedAt(LocalDateTime.now());
                transaction.setConfirmationNumber(gatewayResult.getConfirmationNumber());

                // Send success notification
                notificationService.sendVoicePaymentSuccess(transaction);

                log.info("Voice payment executed successfully: transactionId={}, gatewayId={}", 
                        transaction.getId(), gatewayResult.getGatewayTransactionId());

                return VoicePaymentResult.builder()
                        .transactionId(transaction.getId())
                        .status(VoiceTransactionStatus.COMPLETED)
                        .confirmationNumber(gatewayResult.getConfirmationNumber())
                        .gatewayTransactionId(gatewayResult.getGatewayTransactionId())
                        .completedAt(transaction.getCompletedAt())
                        .fraudAssessment(fraudAssessment)
                        .complianceResult(complianceResult)
                        .successful(true)
                        .build();
            } else {
                transaction.setStatus(VoiceTransactionStatus.FAILED);
                transaction.setFailureReason(gatewayResult.getErrorMessage());
                transaction.setFailedAt(LocalDateTime.now());

                log.error("Voice payment failed at gateway: transactionId={}, error={}", 
                        transaction.getId(), gatewayResult.getErrorMessage());

                return VoicePaymentResult.failure(gatewayResult.getErrorMessage(), 
                        Arrays.asList(gatewayResult.getErrorDetails()));
            }

        } catch (Exception e) {
            transaction.setStatus(VoiceTransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transaction.setFailedAt(LocalDateTime.now());
            voiceTransactionRepository.save(transaction);
            throw e;
        } finally {
            voiceTransactionRepository.save(transaction);
        }
    }

    private VoicePaymentResult executeConfirmedPayment(VoiceTransaction transaction,
                                                       VoicePaymentConfirmationRequest request) {
        try {
            log.info("Executing confirmed voice payment: transactionId={}", transaction.getId());

            // Validate confirmation security
            VoiceConfirmationSecurity security = validateConfirmationSecurity(transaction, request);
            if (!security.isValid()) {
                return VoicePaymentResult.failure("Confirmation security validation failed", 
                        security.getErrors());
            }

            // Create gateway request from confirmed transaction
            PaymentGatewayRequest gatewayRequest = PaymentGatewayRequest.builder()
                    .transactionId(transaction.getId())
                    .userId(transaction.getUserId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .recipientIdentifier(transaction.getRecipientIdentifier())
                    .paymentMethod(transaction.getPaymentMethod())
                    .description(transaction.getDescription())
                    .fraudScore(transaction.getFraudScore())
                    .confirmationToken(request.getConfirmationToken())
                    .voiceVerified(true)
                    .initiatedVia("VOICE_CONFIRMED")
                    .build();

            // Execute payment
            transaction.setStatus(VoiceTransactionStatus.PROCESSING);
            transaction.setConfirmedAt(LocalDateTime.now());
            transaction.setConfirmationMethod(request.getConfirmationMethod());
            voiceTransactionRepository.save(transaction);

            PaymentGatewayResult gatewayResult = paymentGatewayService.processPayment(gatewayRequest);

            // Update transaction with result
            transaction.setPaymentGatewayId(gatewayResult.getGatewayTransactionId());
            transaction.setGatewayResponse(gatewayResult.getResponseCode());

            if (gatewayResult.isSuccess()) {
                transaction.setStatus(VoiceTransactionStatus.COMPLETED);
                transaction.setCompletedAt(LocalDateTime.now());
                transaction.setConfirmationNumber(gatewayResult.getConfirmationNumber());

                // Send success notification
                notificationService.sendVoicePaymentSuccess(transaction);

                // Clean up confirmation token
                tokenVaultService.revokeToken(request.getConfirmationToken(), transaction.getUserId());

                return VoicePaymentResult.builder()
                        .transactionId(transaction.getId())
                        .status(VoiceTransactionStatus.COMPLETED)
                        .confirmationNumber(gatewayResult.getConfirmationNumber())
                        .gatewayTransactionId(gatewayResult.getGatewayTransactionId())
                        .completedAt(transaction.getCompletedAt())
                        .successful(true)
                        .build();
            } else {
                transaction.setStatus(VoiceTransactionStatus.FAILED);
                transaction.setFailureReason(gatewayResult.getErrorMessage());

                return VoicePaymentResult.failure(gatewayResult.getErrorMessage(), 
                        Arrays.asList(gatewayResult.getErrorDetails()));
            }

        } catch (Exception e) {
            transaction.setStatus(VoiceTransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            voiceTransactionRepository.save(transaction);
            
            log.error("Confirmed voice payment execution failed: transactionId={}", 
                    transaction.getId(), e);
            return VoicePaymentResult.error("Payment execution failed: " + e.getMessage());
        }
    }

    private VoicePaymentResult cancelVoicePayment(VoiceTransaction transaction, String reason) {
        try {
            // Cancel at gateway if already submitted
            if (transaction.getPaymentGatewayId() != null) {
                PaymentGatewayCancellationResult cancellation = paymentGatewayService
                        .cancelPayment(transaction.getPaymentGatewayId(), reason);
                
                if (!cancellation.isSuccess()) {
                    log.warn("Gateway cancellation failed: transactionId={}, gatewayError={}", 
                            transaction.getId(), cancellation.getErrorMessage());
                }
            }

            // Update transaction status
            transaction.setStatus(VoiceTransactionStatus.CANCELLED);
            transaction.setCancellationReason(reason);
            transaction.setCancelledAt(LocalDateTime.now());
            voiceTransactionRepository.save(transaction);

            // Send cancellation notification
            notificationService.sendVoicePaymentCancellation(transaction, reason);

            // Log cancellation
            auditService.logTransactionCancellation(transaction.getId(), 
                    transaction.getUserId(), reason);

            log.info("Voice payment cancelled: transactionId={}, reason={}", 
                    transaction.getId(), reason);

            return VoicePaymentResult.builder()
                    .transactionId(transaction.getId())
                    .status(VoiceTransactionStatus.CANCELLED)
                    .cancellationReason(reason)
                    .cancelledAt(transaction.getCancelledAt())
                    .successful(true)
                    .build();

        } catch (Exception e) {
            log.error("Voice payment cancellation failed: transactionId={}", transaction.getId(), e);
            throw new VoiceTransactionException("Failed to cancel payment", e);
        }
    }

    private BigDecimal calculateDailyVoicePaymentTotal(UUID userId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        return voiceTransactionRepository.sumDailyVoicePayments(userId, startOfDay);
    }

    private String generateConfirmationMessage(VoiceTransaction transaction, VoicePaymentRequest request) {
        return String.format(
                "Please confirm: Send %s %s to %s%s. Say 'confirm' to proceed or 'cancel' to stop.",
                transaction.getCurrency(),
                transaction.getAmount(),
                transaction.getRecipientIdentifier(),
                transaction.getDescription() != null ? " for " + transaction.getDescription() : ""
        );
    }

    private boolean canCancelTransaction(VoiceTransaction transaction) {
        return transaction.getStatus() == VoiceTransactionStatus.INITIATED ||
               transaction.getStatus() == VoiceTransactionStatus.PENDING_CONFIRMATION ||
               transaction.getStatus() == VoiceTransactionStatus.PROCESSING;
    }

    private VoiceConfirmationSecurity validateConfirmationSecurity(VoiceTransaction transaction,
                                                                   VoicePaymentConfirmationRequest request) {
        List<String> errors = new ArrayList<>();

        // Validate confirmation token
        try {
            String tokenData = tokenVaultService.retrieveSensitiveData(
                    request.getConfirmationToken(), transaction.getUserId());
            if (tokenData == null) {
                errors.add("Invalid confirmation token");
            }
        } catch (Exception e) {
            errors.add("Confirmation token validation failed");
        }

        // Validate confirmation method
        if (request.getConfirmationMethod() == null) {
            errors.add("Confirmation method is required");
        }

        // Validate voice confirmation if applicable
        if ("VOICE".equals(request.getConfirmationMethod()) && request.getConfirmationVoiceScore() < 0.8) {
            errors.add("Voice confirmation not clear enough");
        }

        return VoiceConfirmationSecurity.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .build();
    }

    private VoiceBatchValidation validateBatchPaymentRequest(VoiceBatchPaymentRequest request) {
        List<String> errors = new ArrayList<>();

        // Validate batch size
        if (request.getPayments().size() > 10) {
            errors.add("Batch size exceeds maximum of 10 payments");
        }

        // Validate total amount
        if (request.getTotalAmount().compareTo(new BigDecimal("5000.00")) > 0) {
            errors.add("Batch total amount exceeds limit");
        }

        // Validate individual payments
        for (VoicePaymentRequest payment : request.getPayments()) {
            if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Invalid payment amount in batch");
                break;
            }
        }

        return VoiceBatchValidation.builder()
                .valid(errors.isEmpty())
                .errorMessage(errors.isEmpty() ? null : String.join("; ", errors))
                .build();
    }

    private VoiceBatchTransaction createBatchTransaction(VoiceBatchPaymentRequest request,
                                                         VoiceBatchValidation validation) {
        return VoiceBatchTransaction.builder()
                .id(UUID.randomUUID())
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .totalAmount(request.getTotalAmount())
                .currency(request.getCurrency())
                .batchType(request.getBatchType())
                .description(request.getDescription())
                .totalPayments(request.getPayments().size())
                .status(VoiceBatchStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String generateCaseNumber(VoicePaymentDispute dispute) {
        return "VD-" + LocalDateTime.now().getYear() + "-" + 
               String.format("%06d", dispute.getId().toString().hashCode() & 0xFFFFFF);
    }

    // Additional helper methods would continue here...
    
    private List<String> generateDisputeNextSteps(VoicePaymentDispute dispute) {
        return Arrays.asList(
                "Your dispute has been submitted and assigned case number: " + generateCaseNumber(dispute),
                "Our investigation team will review your case within 2-3 business days",
                "You will receive email updates on the progress of your dispute",
                "Additional documentation may be requested if needed"
        );
    }

    private void initiateDisputeInvestigation(VoicePaymentDispute dispute, VoiceTransaction transaction) {
        // Log dispute initiation
        auditService.logDisputeInitiation(dispute.getId(), transaction.getId(), dispute.getDisputeType());
        
        // Notify investigation team
        notificationService.notifyDisputeTeam(dispute, transaction);
        
        log.info("Dispute investigation initiated: disputeId={}, transactionId={}", 
                dispute.getId(), transaction.getId());
    }

    private DisputeEligibility validateDisputeEligibility(VoiceTransaction transaction, 
                                                           VoiceDisputeRequest request) {
        // Check transaction age (must be within 60 days)
        if (transaction.getCreatedAt().isBefore(LocalDateTime.now().minusDays(60))) {
            return DisputeEligibility.ineligible("Transaction is too old for dispute");
        }

        // Check if already disputed
        if (transaction.getStatus() == VoiceTransactionStatus.DISPUTED) {
            return DisputeEligibility.ineligible("Transaction is already disputed");
        }

        // Check transaction status
        if (transaction.getStatus() != VoiceTransactionStatus.COMPLETED) {
            return DisputeEligibility.ineligible("Only completed transactions can be disputed");
        }

        return DisputeEligibility.eligible();
    }

    // Helper data classes

    public static class VoicePaymentValidation {
        private boolean valid;
        private List<String> errors;
        private VoiceMetrics voiceMetrics;

        public static VoicePaymentValidationBuilder builder() {
            return new VoicePaymentValidationBuilder();
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public String getErrorMessage() { return errors != null ? String.join("; ", errors) : null; }
        public VoiceMetrics getVoiceMetrics() { return voiceMetrics; }

        public static class VoicePaymentValidationBuilder {
            private VoicePaymentValidation validation = new VoicePaymentValidation();
            
            public VoicePaymentValidationBuilder valid(boolean valid) { validation.valid = valid; return this; }
            public VoicePaymentValidationBuilder errors(List<String> errors) { validation.errors = errors; return this; }
            public VoicePaymentValidationBuilder voiceMetrics(VoiceMetrics voiceMetrics) { validation.voiceMetrics = voiceMetrics; return this; }
            
            public VoicePaymentValidation build() { return validation; }
        }
    }

    public static class VoiceMetrics {
        private double confidenceScore;
        private double audioQuality;
        private int backgroundNoiseLevel;

        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    }

    public static class VoiceConfirmationSecurity {
        private boolean valid;
        private List<String> errors;

        public static VoiceConfirmationSecurityBuilder builder() {
            return new VoiceConfirmationSecurityBuilder();
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }

        public static class VoiceConfirmationSecurityBuilder {
            private VoiceConfirmationSecurity security = new VoiceConfirmationSecurity();
            
            public VoiceConfirmationSecurityBuilder valid(boolean valid) { security.valid = valid; return this; }
            public VoiceConfirmationSecurityBuilder errors(List<String> errors) { security.errors = errors; return this; }
            
            public VoiceConfirmationSecurity build() { return security; }
        }
    }

    public static class VoiceBatchValidation {
        private boolean valid;
        private String errorMessage;

        public static VoiceBatchValidationBuilder builder() {
            return new VoiceBatchValidationBuilder();
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }

        public static class VoiceBatchValidationBuilder {
            private VoiceBatchValidation validation = new VoiceBatchValidation();
            
            public VoiceBatchValidationBuilder valid(boolean valid) { validation.valid = valid; return this; }
            public VoiceBatchValidationBuilder errorMessage(String errorMessage) { validation.errorMessage = errorMessage; return this; }
            
            public VoiceBatchValidation build() { return validation; }
        }
    }

    public static class DisputeEligibility {
        private boolean eligible;
        private String reason;

        public static DisputeEligibility eligible() {
            DisputeEligibility eligibility = new DisputeEligibility();
            eligibility.eligible = true;
            return eligibility;
        }

        public static DisputeEligibility ineligible(String reason) {
            DisputeEligibility eligibility = new DisputeEligibility();
            eligibility.eligible = false;
            eligibility.reason = reason;
            return eligibility;
        }

        public boolean isEligible() { return eligible; }
        public String getReason() { return reason; }
    }
}