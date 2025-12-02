package com.waqiti.recurringpayment.service;

import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.event.RecurringPaymentEvent;
import com.waqiti.common.exception.ErrorCode;
import com.waqiti.common.exception.WaqitiException;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;

// Import UnifiedPaymentService
import com.waqiti.payment.core.UnifiedPaymentService;
import com.waqiti.payment.core.model.*;

import com.waqiti.recurringpayment.domain.RecurringExecution;
import com.waqiti.recurringpayment.domain.RecurringPayment;
import com.waqiti.recurringpayment.dto.CreateRecurringPaymentRequest;
import com.waqiti.recurringpayment.exception.RecurringPaymentNotFoundException;
import com.waqiti.recurringpayment.repository.RecurringExecutionRepository;
import com.waqiti.recurringpayment.repository.RecurringPaymentRepository;
import com.waqiti.recurringpayment.repository.RecurringTemplateRepository;
import com.waqiti.recurringpayment.service.clients.FraudDetectionServiceClient;
import com.waqiti.recurringpayment.service.clients.NotificationServiceClient;
import com.waqiti.recurringpayment.service.clients.PaymentServiceClient;
import com.waqiti.recurringpayment.service.clients.WalletServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MODERNIZED RecurringPaymentService - Now delegates to UnifiedPaymentService
 * Maintains backward compatibility while using the new unified architecture
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringPaymentService {

    // Legacy dependencies for backward compatibility
    private final RecurringPaymentRepository recurringRepository;
    private final RecurringExecutionRepository executionRepository;
    private final RecurringTemplateRepository templateRepository;
    private final PaymentServiceClient paymentService;
    private final WalletServiceClient walletService;
    private final NotificationServiceClient notificationService;
    private final FraudDetectionServiceClient fraudDetectionService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    private final KYCClientService kycClientService;

    // NEW: Unified Payment Service
    private final UnifiedPaymentService unifiedPaymentService;
    
    // In-memory cache for active recurring payments
    private final Map<UUID, RecurringPayment> activeRecurringPayments = new ConcurrentHashMap<>();

    /**
     * Create recurring payment - MODERNIZED to use UnifiedPaymentService
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "RECURRING_PAYMENT_CREATE")
    public RecurringPaymentResponse createRecurringPayment(String userId, CreateRecurringPaymentRequest request) {
        log.info("Creating recurring payment for user {} to {} [UNIFIED]", userId, request.getRecipientId());

        try {
            // Enhanced KYC check for high-value recurring payments
            if (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
                if (!kycClientService.canUserMakeHighValueTransfer(userId)) {
                    throw new WaqitiException(ErrorCode.USER_KYC_REQUIRED, 
                        "Enhanced KYC verification required for recurring payments over $1,000")
                        .withMetadata("amount", request.getAmount())
                        .withMetadata("userId", userId)
                        .withMetadata("requiredLevel", "ENHANCED");
                }
            }

            // Create legacy recurring payment entity
            RecurringPayment recurringPayment = createLegacyRecurringPayment(userId, request);

            // CREATE RECURRING PAYMENT USING UNIFIED SERVICE
            PaymentRequest unifiedRequest = PaymentRequest.builder()
                    .paymentId(UUID.randomUUID())
                    .type(PaymentType.RECURRING)
                    .providerType(ProviderType.INTERNAL)
                    .fromUserId(userId)
                    .toUserId(request.getRecipientId())
                    .amount(request.getAmount())
                    .metadata(Map.of(
                            "frequency", request.getFrequency().toString(),
                            "nextPaymentDate", request.getStartDate().toString(),
                            "endDate", request.getEndDate() != null ? request.getEndDate().toString() : "",
                            "description", request.getDescription() != null ? request.getDescription() : "",
                            "maxExecutions", request.getMaxExecutions() != null ? request.getMaxExecutions().toString() : "0",
                            "recurringPaymentId", recurringPayment.getId().toString(),
                            "currency", "USD"
                    ))
                    .build();

            // Process initial setup through UnifiedPaymentService
            PaymentResult result = unifiedPaymentService.processPayment(unifiedRequest);

            // Update recurring payment with unified result
            recurringPayment.setUnifiedTransactionId(result.getTransactionId());
            recurringPayment.setUnifiedStatus(result.getStatus().toString());
            
            // Set status based on unified result
            if (result.isSuccess()) {
                recurringPayment.setStatus(RecurringPaymentStatus.ACTIVE);
                // Add to active cache for scheduling
                activeRecurringPayments.put(recurringPayment.getId(), recurringPayment);
            } else {
                recurringPayment.setStatus(RecurringPaymentStatus.FAILED);
                log.warn("Recurring payment setup failed: {}", result.getProviderResponse());
            }

            recurringPayment = recurringRepository.save(recurringPayment);

            // Create first execution record
            createInitialExecution(recurringPayment, result);

            // Send notifications
            notificationService.sendRecurringPaymentCreatedNotification(recurringPayment);

            // Publish event
            publishRecurringPaymentEvent(recurringPayment, result, "CREATED");

            log.info("Recurring payment created successfully: {} with unified transaction: {}", 
                    recurringPayment.getId(), result.getTransactionId());

            return enrichRecurringPaymentResponse(mapToRecurringPaymentResponse(recurringPayment), result);

        } catch (Exception e) {
            log.error("Error creating recurring payment via UnifiedPaymentService", e);
            throw e;
        }
    }

    /**
     * Execute scheduled recurring payments - MODERNIZED to use UnifiedPaymentService
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void executeScheduledRecurringPayments() {
        log.info("Executing scheduled recurring payments [UNIFIED]");

        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Find recurring payments due for execution
            List<RecurringPayment> duePayments = recurringRepository
                    .findActivePaymentsDueForExecution(now);

            log.info("Found {} recurring payments due for execution", duePayments.size());

            // Process each payment
            List<CompletableFuture<Void>> futures = duePayments.stream()
                    .map(payment -> CompletableFuture.runAsync(() -> 
                            executeRecurringPaymentAsync(payment)))
                    .collect(Collectors.toList());

            // Wait for all executions to complete with timeout
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, java.util.concurrent.TimeUnit.MINUTES);
                log.info("Completed processing {} recurring payments", duePayments.size());
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Recurring payment processing timed out after 5 minutes. Processed: {}", duePayments.size(), e);
                // Cancel remaining futures
                futures.forEach(f -> f.cancel(true));
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Recurring payment execution failed", e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Recurring payment processing interrupted", e);
            }

        } catch (Exception e) {
            log.error("Error executing scheduled recurring payments", e);
        }
    }

    /**
     * Execute individual recurring payment - MODERNIZED to use UnifiedPaymentService
     */
    private void executeRecurringPaymentAsync(RecurringPayment recurringPayment) {
        log.info("Executing recurring payment: {} [UNIFIED]", recurringPayment.getId());

        try {
            // Check if payment is still active
            if (recurringPayment.getStatus() != RecurringPaymentStatus.ACTIVE) {
                log.info("Skipping inactive recurring payment: {}", recurringPayment.getId());
                return;
            }

            // Check if max executions reached
            if (recurringPayment.getMaxExecutions() != null) {
                long executionCount = executionRepository.countByRecurringPayment(recurringPayment);
                if (executionCount >= recurringPayment.getMaxExecutions()) {
                    completeRecurringPayment(recurringPayment, "Max executions reached");
                    return;
                }
            }

            // Check if end date reached
            if (recurringPayment.getEndDate() != null && 
                LocalDateTime.now().isAfter(recurringPayment.getEndDate())) {
                completeRecurringPayment(recurringPayment, "End date reached");
                return;
            }

            // CREATE EXECUTION PAYMENT USING UNIFIED SERVICE
            PaymentRequest executionRequest = PaymentRequest.builder()
                    .paymentId(UUID.randomUUID())
                    .type(PaymentType.RECURRING)
                    .providerType(ProviderType.INTERNAL)
                    .fromUserId(recurringPayment.getPayerId())
                    .toUserId(recurringPayment.getRecipientId())
                    .amount(recurringPayment.getAmount())
                    .metadata(Map.of(
                            "executionType", "RECURRING_EXECUTION",
                            "recurringPaymentId", recurringPayment.getId().toString(),
                            "executionDate", LocalDateTime.now().toString(),
                            "frequency", recurringPayment.getFrequency().toString(),
                            "description", "Recurring payment: " + recurringPayment.getDescription(),
                            "currency", "USD"
                    ))
                    .build();

            // Execute through UnifiedPaymentService
            PaymentResult executionResult = unifiedPaymentService.processPayment(executionRequest);

            // Create execution record
            createExecutionRecord(recurringPayment, executionResult);

            // Update next execution date
            updateNextExecutionDate(recurringPayment);

            // Handle execution result
            if (executionResult.isSuccess()) {
                log.info("Recurring payment execution successful: {} -> {}", 
                        recurringPayment.getId(), executionResult.getTransactionId());
                
                // Send success notification
                notificationService.sendRecurringPaymentExecutedNotification(
                        recurringPayment, executionResult);
                
                // Reset failure count on success
                recurringPayment.setFailureCount(0);
                
            } else {
                log.warn("Recurring payment execution failed: {} - {}", 
                        recurringPayment.getId(), executionResult.getProviderResponse());
                
                // Increment failure count
                int failureCount = recurringPayment.getFailureCount() + 1;
                recurringPayment.setFailureCount(failureCount);
                
                // Check if max failures reached
                if (failureCount >= 3) {
                    pauseRecurringPayment(recurringPayment, "Max failures reached");
                    notificationService.sendRecurringPaymentFailedNotification(
                            recurringPayment, executionResult);
                }
            }

            // Save updated recurring payment
            recurringRepository.save(recurringPayment);

            // Publish execution event
            publishRecurringPaymentEvent(recurringPayment, executionResult, "EXECUTED");

        } catch (Exception e) {
            log.error("Error executing recurring payment: {}", recurringPayment.getId(), e);
            
            // Handle execution error
            handleExecutionError(recurringPayment, e);
        }
    }

    /**
     * Cancel recurring payment - MODERNIZED to use UnifiedPaymentService
     */
    @Transactional
    public RecurringPaymentResponse cancelRecurringPayment(UUID recurringPaymentId, String userId, String reason) {
        log.info("Cancelling recurring payment: {} by user: {} [UNIFIED]", recurringPaymentId, userId);

        try {
            RecurringPayment recurringPayment = findRecurringPaymentById(recurringPaymentId);
            
            // Check permissions
            if (!canUserModifyRecurringPayment(recurringPayment, userId)) {
                throw new InsufficientPermissionException("User not authorized to cancel this recurring payment");
            }

            // Update status
            recurringPayment.setStatus(RecurringPaymentStatus.CANCELLED);
            recurringPayment.setCancelledAt(LocalDateTime.now());
            recurringPayment.setCancelledBy(userId);
            recurringPayment.setCancellationReason(reason);

            recurringPayment = recurringRepository.save(recurringPayment);

            // Remove from active cache
            activeRecurringPayments.remove(recurringPaymentId);

            // Send notification
            notificationService.sendRecurringPaymentCancelledNotification(recurringPayment);

            // Publish cancellation event
            publishRecurringPaymentEvent(recurringPayment, null, "CANCELLED");

            log.info("Recurring payment cancelled successfully: {}", recurringPaymentId);

            return mapToRecurringPaymentResponse(recurringPayment);

        } catch (Exception e) {
            log.error("Error cancelling recurring payment", e);
            throw e;
        }
    }

    /**
     * Get recurring payment analytics - MODERNIZED to use UnifiedPaymentService
     */
    public RecurringPaymentAnalytics getRecurringPaymentAnalytics(String userId, String period) {
        log.info("Getting recurring payment analytics for user {} period={} [UNIFIED]", userId, period);
        
        try {
            // Get analytics from UnifiedPaymentService
            AnalyticsFilter filter = switch (period.toLowerCase()) {
                case "month" -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusMonths(1))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.RECURRING)
                        .groupBy("day")
                        .build();
                case "quarter" -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusMonths(3))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.RECURRING)
                        .groupBy("week")
                        .build();
                case "year" -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusYears(1))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.RECURRING)
                        .groupBy("month")
                        .build();
                default -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusDays(30))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.RECURRING)
                        .groupBy("day")
                        .build();
            };
            
            PaymentAnalytics unifiedAnalytics = unifiedPaymentService.getAnalytics(userId, filter);
            
            // Convert to recurring payment specific analytics
            return convertToRecurringPaymentAnalytics(unifiedAnalytics, userId);
            
        } catch (Exception e) {
            log.error("Error getting recurring payment analytics", e);
            throw e;
        }
    }

    /**
     * Get user's recurring payments
     */
    public Page<RecurringPaymentResponse> getUserRecurringPayments(String userId, Pageable pageable) {
        log.info("Getting recurring payments for user: {} [UNIFIED]", userId);
        
        Page<RecurringPayment> recurringPayments = recurringRepository
                .findByPayerIdOrRecipientId(userId, userId, pageable);
        
        return recurringPayments.map(rp -> {
            RecurringPaymentResponse response = mapToRecurringPaymentResponse(rp);
            // Enrich with unified payment data if available
            if (rp.getUnifiedTransactionId() != null) {
                enrichWithUnifiedData(response, rp.getUnifiedTransactionId());
            }
            return response;
        });
    }

    // LEGACY SUPPORT METHODS - Maintain backward compatibility

    private RecurringPayment createLegacyRecurringPayment(String userId, CreateRecurringPaymentRequest request) {
        RecurringPayment recurringPayment = new RecurringPayment();
        recurringPayment.setPayerId(userId);
        recurringPayment.setRecipientId(request.getRecipientId());
        recurringPayment.setAmount(request.getAmount());
        recurringPayment.setFrequency(request.getFrequency());
        recurringPayment.setDescription(request.getDescription());
        recurringPayment.setStartDate(request.getStartDate());
        recurringPayment.setEndDate(request.getEndDate());
        recurringPayment.setMaxExecutions(request.getMaxExecutions());
        recurringPayment.setStatus(RecurringPaymentStatus.PENDING);
        recurringPayment.setCreatedAt(LocalDateTime.now());
        recurringPayment.setFailureCount(0);
        
        // Calculate next execution date
        recurringPayment.setNextExecutionDate(calculateNextExecutionDate(
                request.getStartDate(), request.getFrequency()));
        
        return recurringRepository.save(recurringPayment);
    }

    private void createInitialExecution(RecurringPayment recurringPayment, PaymentResult result) {
        RecurringExecution execution = new RecurringExecution();
        execution.setRecurringPayment(recurringPayment);
        execution.setExecutionDate(LocalDateTime.now());
        execution.setAmount(recurringPayment.getAmount());
        execution.setStatus(convertToExecutionStatus(result.getStatus()));
        execution.setUnifiedTransactionId(result.getTransactionId());
        execution.setResultMessage(result.getProviderResponse());
        executionRepository.save(execution);
    }

    private void createExecutionRecord(RecurringPayment recurringPayment, PaymentResult result) {
        RecurringExecution execution = new RecurringExecution();
        execution.setRecurringPayment(recurringPayment);
        execution.setExecutionDate(LocalDateTime.now());
        execution.setAmount(recurringPayment.getAmount());
        execution.setStatus(convertToExecutionStatus(result.getStatus()));
        execution.setUnifiedTransactionId(result.getTransactionId());
        execution.setResultMessage(result.getProviderResponse());
        executionRepository.save(execution);
    }

    private RecurringExecutionStatus convertToExecutionStatus(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case COMPLETED -> RecurringExecutionStatus.SUCCESS;
            case PENDING, PROCESSING -> RecurringExecutionStatus.PENDING;
            case FAILED, FRAUD_BLOCKED -> RecurringExecutionStatus.FAILED;
            default -> RecurringExecutionStatus.FAILED;
        };
    }

    private void updateNextExecutionDate(RecurringPayment recurringPayment) {
        LocalDateTime currentNext = recurringPayment.getNextExecutionDate();
        LocalDateTime newNext = calculateNextExecutionDate(currentNext, recurringPayment.getFrequency());
        recurringPayment.setNextExecutionDate(newNext);
    }

    private LocalDateTime calculateNextExecutionDate(LocalDateTime baseDate, RecurrenceFrequency frequency) {
        return switch (frequency) {
            case DAILY -> baseDate.plusDays(1);
            case WEEKLY -> baseDate.plusWeeks(1);
            case MONTHLY -> baseDate.plusMonths(1);
            case QUARTERLY -> baseDate.plusMonths(3);
            case YEARLY -> baseDate.plusYears(1);
        };
    }

    private void completeRecurringPayment(RecurringPayment recurringPayment, String reason) {
        recurringPayment.setStatus(RecurringPaymentStatus.COMPLETED);
        recurringPayment.setCompletedAt(LocalDateTime.now());
        recurringPayment.setCompletionReason(reason);
        recurringRepository.save(recurringPayment);
        
        // Remove from active cache
        activeRecurringPayments.remove(recurringPayment.getId());
        
        log.info("Recurring payment completed: {} - {}", recurringPayment.getId(), reason);
    }

    private void pauseRecurringPayment(RecurringPayment recurringPayment, String reason) {
        recurringPayment.setStatus(RecurringPaymentStatus.PAUSED);
        recurringPayment.setPausedAt(LocalDateTime.now());
        recurringPayment.setPauseReason(reason);
        recurringRepository.save(recurringPayment);
        
        log.info("Recurring payment paused: {} - {}", recurringPayment.getId(), reason);
    }

    private void handleExecutionError(RecurringPayment recurringPayment, Exception error) {
        int failureCount = recurringPayment.getFailureCount() + 1;
        recurringPayment.setFailureCount(failureCount);
        
        if (failureCount >= 3) {
            pauseRecurringPayment(recurringPayment, "Execution errors: " + error.getMessage());
        }
        
        recurringRepository.save(recurringPayment);
    }

    private RecurringPaymentResponse enrichRecurringPaymentResponse(RecurringPaymentResponse response, PaymentResult result) {
        response.setUnifiedTransactionId(result.getTransactionId());
        response.setUnifiedStatus(result.getStatus().toString());
        response.setProcessedAt(result.getProcessedAt());
        return response;
    }

    private void publishRecurringPaymentEvent(RecurringPayment recurringPayment, PaymentResult result, String eventType) {
        try {
            RecurringPaymentEvent event = new RecurringPaymentEvent();
            event.setEventType("RECURRING_PAYMENT_" + eventType);
            event.setRecurringPaymentId(recurringPayment.getId());
            event.setPayerId(recurringPayment.getPayerId());
            event.setRecipientId(recurringPayment.getRecipientId());
            event.setAmount(recurringPayment.getAmount());
            event.setFrequency(recurringPayment.getFrequency());
            event.setStatus(recurringPayment.getStatus());
            event.setUnifiedTransactionId(result != null ? result.getTransactionId() : null);
            event.setTimestamp(LocalDateTime.now());
            
            eventPublisher.publish(event);
            
        } catch (Exception e) {
            log.error("Failed to publish recurring payment event", e);
        }
    }

    private RecurringPaymentAnalytics convertToRecurringPaymentAnalytics(PaymentAnalytics unifiedAnalytics, String userId) {
        RecurringPaymentAnalytics analytics = new RecurringPaymentAnalytics();
        analytics.setUserId(userId);
        analytics.setTotalRecurringPayments(unifiedAnalytics.getTotalPayments());
        analytics.setActiveRecurringPayments(activeRecurringPayments.size());
        analytics.setSuccessfulExecutions(unifiedAnalytics.getSuccessfulPayments());
        analytics.setFailedExecutions(unifiedAnalytics.getFailedPayments());
        analytics.setTotalAmount(unifiedAnalytics.getTotalAmount());
        analytics.setAverageAmount(unifiedAnalytics.getAverageAmount());
        analytics.setSuccessRate(unifiedAnalytics.getSuccessRate());
        analytics.setPeriodStart(unifiedAnalytics.getPeriodStart());
        analytics.setPeriodEnd(unifiedAnalytics.getPeriodEnd());
        return analytics;
    }

    private void enrichWithUnifiedData(RecurringPaymentResponse response, String transactionId) {
        try {
            response.setUnifiedTransactionId(transactionId);
        } catch (Exception e) {
            log.warn("Could not enrich with unified data: {}", e.getMessage());
        }
    }

    private RecurringPayment findRecurringPaymentById(UUID recurringPaymentId) {
        return recurringRepository.findById(recurringPaymentId)
                .orElseThrow(() -> new RecurringPaymentNotFoundException("Recurring payment not found: " + recurringPaymentId));
    }

    private boolean canUserModifyRecurringPayment(RecurringPayment recurringPayment, String userId) {
        return recurringPayment.getPayerId().equals(userId);
    }

    private RecurringPaymentResponse mapToRecurringPaymentResponse(RecurringPayment recurringPayment) {
        RecurringPaymentResponse response = new RecurringPaymentResponse();
        response.setId(recurringPayment.getId());
        response.setPayerId(recurringPayment.getPayerId());
        response.setRecipientId(recurringPayment.getRecipientId());
        response.setAmount(recurringPayment.getAmount());
        response.setFrequency(recurringPayment.getFrequency());
        response.setDescription(recurringPayment.getDescription());
        response.setStatus(recurringPayment.getStatus());
        response.setStartDate(recurringPayment.getStartDate());
        response.setEndDate(recurringPayment.getEndDate());
        response.setNextExecutionDate(recurringPayment.getNextExecutionDate());
        response.setCreatedAt(recurringPayment.getCreatedAt());
        response.setUnifiedTransactionId(recurringPayment.getUnifiedTransactionId());
        response.setUnifiedStatus(recurringPayment.getUnifiedStatus());
        return response;
    }
}