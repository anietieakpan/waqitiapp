/**
 * Installment Payment Service
 * Manages BNPL installment payments and collections
 */
package com.waqiti.bnpl.service;

import com.waqiti.bnpl.dto.request.ProcessPaymentRequest;
import com.waqiti.bnpl.dto.response.PaymentResponse;
import com.waqiti.bnpl.entity.BnplApplication;
import com.waqiti.bnpl.entity.BnplInstallment;
import com.waqiti.bnpl.entity.BnplInstallment.InstallmentStatus;
import com.waqiti.bnpl.exception.PaymentProcessingException;
import com.waqiti.bnpl.repository.BnplApplicationRepository;
import com.waqiti.bnpl.repository.BnplInstallmentRepository;
import com.waqiti.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentPaymentService {
    
    @Lazy
    private final InstallmentPaymentService self;
    private final BnplInstallmentRepository installmentRepository;
    private final BnplApplicationRepository applicationRepository;
    private final PaymentProcessorService paymentProcessor;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.05"); // 5% late fee
    private static final int RETRY_ATTEMPTS = 3;
    private static final int DAYS_BEFORE_COLLECTIONS = 30;
    
    /**
     * Processes payment for a specific installment
     */
    @Transactional
    public PaymentResponse processInstallmentPayment(UUID installmentId, ProcessPaymentRequest request) {
        log.info("Processing payment for installment {} amount {}", installmentId, request.getAmount());
        
        try {
            BnplInstallment installment = installmentRepository.findById(installmentId)
                    .orElseThrow(() -> new PaymentProcessingException("Installment not found"));
            
            // Validate payment
            validatePayment(installment, request);
            
            // Calculate total due including late fees
            BigDecimal totalDue = installment.getAmount().add(
                    installment.getLateFeeAmount() != null ? installment.getLateFeeAmount() : BigDecimal.ZERO
            );
            
            // Process payment through payment gateway
            PaymentResult result = paymentProcessor.processPayment(
                    installment.getUserId(),
                    request.getPaymentMethodId(),
                    request.getAmount(),
                    "BNPL Installment #" + installment.getInstallmentNumber()
            );
            
            if (result.isSuccessful()) {
                // Update installment
                installment.setStatus(
                        request.getAmount().compareTo(totalDue) >= 0 
                        ? InstallmentStatus.PAID 
                        : InstallmentStatus.PARTIALLY_PAID
                );
                installment.setPaymentDate(LocalDateTime.now());
                installment.setPaymentAmount(request.getAmount());
                installment.setPaymentMethod(request.getPaymentMethodId());
                installment.setPaymentReference(result.getTransactionId());
                installment.setTransactionId(result.getPaymentId());
                
                installmentRepository.save(installment);
                
                // Check if all installments are paid
                checkAndCompleteApplication(installment.getApplication());
                
                // Send confirmation
                notificationService.sendPaymentConfirmation(installment);
                
                // Publish event
                publishPaymentEvent(installment, "PAYMENT_SUCCESSFUL");
                
                return PaymentResponse.builder()
                        .success(true)
                        .transactionId(result.getTransactionId())
                        .amount(request.getAmount())
                        .remainingBalance(totalDue.subtract(request.getAmount()))
                        .build();
                
            } else {
                // Handle failed payment
                installment.setRetryCount(installment.getRetryCount() + 1);
                installment.setLastRetryDate(LocalDateTime.now());
                
                if (installment.getRetryCount() < RETRY_ATTEMPTS) {
                    installment.setNextRetryDate(LocalDateTime.now().plusDays(3));
                } else {
                    installment.setStatus(InstallmentStatus.FAILED);
                }
                
                installmentRepository.save(installment);
                
                publishPaymentEvent(installment, "PAYMENT_FAILED");
                
                throw new PaymentProcessingException("Payment failed: " + result.getFailureReason());
            }
            
        } catch (Exception e) {
            log.error("Failed to process installment payment", e);
            throw new PaymentProcessingException("Failed to process payment", e);
        }
    }
    
    /**
     * Processes automatic payment for due installments
     */
    @Scheduled(cron = "0 0 9 * * *") // Run daily at 9 AM
    @Transactional
    public void processAutomaticPayments() {
        log.info("Starting automatic payment processing");
        
        LocalDate today = LocalDate.now();
        List<BnplInstallment> dueInstallments = installmentRepository.findInstallmentsDueOn(today);
        
        for (BnplInstallment installment : dueInstallments) {
            try {
                // Check if user has automatic payment enabled
                if (isAutomaticPaymentEnabled(installment.getUserId())) {
                    ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                            .amount(installment.getAmount())
                            .paymentMethodId(getDefaultPaymentMethod(installment.getUserId()))
                            .build();
                    
                    self.processInstallmentPayment(installment.getId(), request);
                } else {
                    // Send payment reminder
                    notificationService.sendPaymentReminder(installment);
                }
            } catch (Exception e) {
                log.error("Failed to process automatic payment for installment {}", installment.getId(), e);
            }
        }
    }
    
    /**
     * Marks overdue installments and applies late fees
     */
    @Scheduled(cron = "0 0 1 * * *") // Run daily at 1 AM
    @Transactional
    public void processOverdueInstallments() {
        log.info("Processing overdue installments");
        
        LocalDate today = LocalDate.now();
        List<BnplInstallment> overdueInstallments = installmentRepository.findOverdueInstallments(today);
        
        for (BnplInstallment installment : overdueInstallments) {
            try {
                // Calculate days late
                int daysLate = (int) ChronoUnit.DAYS.between(installment.getDueDate(), today);
                installment.setDaysLate(daysLate);
                installment.setStatus(InstallmentStatus.OVERDUE);
                
                // Apply late fee if not already applied
                if (installment.getLateFeeAmount() == null || installment.getLateFeeAmount().equals(BigDecimal.ZERO)) {
                    BigDecimal lateFee = installment.getAmount().multiply(LATE_FEE_PERCENTAGE);
                    installment.setLateFeeAmount(lateFee);
                    installment.setLateFeeAppliedDate(LocalDateTime.now());
                    
                    notificationService.sendLateFeeNotification(installment);
                }
                
                // Check if ready for collections
                if (daysLate >= DAYS_BEFORE_COLLECTIONS && installment.getCollectionStatus() == null) {
                    installment.setCollectionStatus("PENDING_ASSIGNMENT");
                    installment.setCollectionAssignedDate(LocalDateTime.now());
                    
                    publishPaymentEvent(installment, "SENT_TO_COLLECTIONS");
                }
                
                installmentRepository.save(installment);
                
            } catch (Exception e) {
                log.error("Failed to process overdue installment {}", installment.getId(), e);
            }
        }
    }
    
    /**
     * Retries failed payments
     */
    @Scheduled(cron = "0 0 11 * * *") // Run daily at 11 AM
    @Transactional
    public void retryFailedPayments() {
        log.info("Retrying failed payments");
        
        List<BnplInstallment> installmentsForRetry = installmentRepository.findInstallmentsForRetry(LocalDateTime.now());
        
        for (BnplInstallment installment : installmentsForRetry) {
            try {
                ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                        .amount(installment.getAmount().add(
                                installment.getLateFeeAmount() != null ? installment.getLateFeeAmount() : BigDecimal.ZERO
                        ))
                        .paymentMethodId(getDefaultPaymentMethod(installment.getUserId()))
                        .build();
                
                self.processInstallmentPayment(installment.getId(), request);
                
            } catch (Exception e) {
                log.error("Retry failed for installment {}", installment.getId(), e);
                
                // Update retry information
                installment.setRetryCount(installment.getRetryCount() + 1);
                installment.setLastRetryDate(LocalDateTime.now());
                
                if (installment.getRetryCount() < RETRY_ATTEMPTS) {
                    installment.setNextRetryDate(LocalDateTime.now().plusDays(3));
                } else {
                    installment.setStatus(InstallmentStatus.FAILED);
                }
                
                installmentRepository.save(installment);
            }
        }
    }
    
    /**
     * Validates payment request
     */
    private void validatePayment(BnplInstallment installment, ProcessPaymentRequest request) {
        if (installment.isPaid()) {
            throw new PaymentProcessingException("Installment is already paid");
        }
        
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("Payment amount must be greater than zero");
        }
        
        BigDecimal totalDue = installment.getAmount().add(
                installment.getLateFeeAmount() != null ? installment.getLateFeeAmount() : BigDecimal.ZERO
        );
        
        if (request.getAmount().compareTo(totalDue) > 0) {
            throw new PaymentProcessingException("Payment amount exceeds total due");
        }
    }
    
    /**
     * Checks if all installments are paid and completes the application
     */
    private void checkAndCompleteApplication(BnplApplication application) {
        boolean allPaid = !installmentRepository.existsByApplicationIdAndStatusIn(
                application.getId(),
                List.of(InstallmentStatus.PENDING, InstallmentStatus.OVERDUE, InstallmentStatus.PARTIALLY_PAID)
        );
        
        if (allPaid) {
            application.setStatus(BnplApplication.ApplicationStatus.COMPLETED);
            applicationRepository.save(application);
            
            notificationService.sendApplicationCompleted(application);
            publishApplicationEvent(application, "APPLICATION_COMPLETED");
        }
    }
    
    private boolean isAutomaticPaymentEnabled(UUID userId) {
        // Check user preferences
        return true; // Simplified for now
    }
    
    private String getDefaultPaymentMethod(UUID userId) {
        // Get user's default payment method
        return "DEFAULT_PAYMENT_METHOD"; // Simplified
    }
    
    private void publishPaymentEvent(BnplInstallment installment, String eventType) {
        eventPublisher.publish("bnpl.payment." + eventType.toLowerCase(), installment);
    }
    
    private void publishApplicationEvent(BnplApplication application, String eventType) {
        eventPublisher.publish("bnpl.application." + eventType.toLowerCase(), application);
    }
    
    @Data
    @Builder
    private static class PaymentResult {
        private boolean successful;
        private String transactionId;
        private UUID paymentId;
        private String failureReason;
    }
}