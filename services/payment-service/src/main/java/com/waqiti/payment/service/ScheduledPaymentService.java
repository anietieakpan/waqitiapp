/**
 * Scheduled Payment Service
 * Handles creation, management, and execution of scheduled payments
 */
package com.waqiti.payment.service;

import com.waqiti.payment.entity.ScheduledPayment;
import com.waqiti.payment.entity.ScheduledPayment.ScheduledPaymentStatus;
import com.waqiti.payment.entity.ScheduledPayment.ScheduleType;
import com.waqiti.payment.repository.ScheduledPaymentRepository;
import com.waqiti.payment.dto.CreateScheduledPaymentRequest;
import com.waqiti.payment.dto.UpdateScheduledPaymentRequest;
import com.waqiti.payment.dto.ScheduledPaymentResponse;
import com.waqiti.payment.mapper.ScheduledPaymentMapper;
import com.waqiti.payment.event.ScheduledPaymentEvent;
import com.waqiti.payment.exception.ScheduledPaymentException;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledPaymentService {
    
    private final ScheduledPaymentRepository repository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ScheduledPaymentMapper mapper;
    private final EventPublisher eventPublisher;
    
    /**
     * Create a new scheduled payment
     */
    @Transactional
    public ScheduledPaymentResponse createScheduledPayment(CreateScheduledPaymentRequest request) {
        log.info("Creating scheduled payment for user: {}", request.getUserId());
        
        // Validate request
        validateCreateRequest(request);
        
        // Check for duplicate scheduled payments
        if (isDuplicateScheduledPayment(request)) {
            throw new ValidationException("Similar scheduled payment already exists");
        }
        
        // Map to entity
        ScheduledPayment payment = mapper.toEntity(request);
        
        // Set initial values
        payment.setStatus(ScheduledPaymentStatus.ACTIVE);
        payment.setCompletedPayments(0);
        payment.setFailedPayments(0);
        payment.setNextPaymentDate(calculateInitialPaymentDate(payment));
        
        // Save to database
        payment = repository.save(payment);
        
        // Publish event
        publishEvent(ScheduledPaymentEvent.EventType.CREATED, payment);
        
        // Send confirmation notification
        notificationService.sendScheduledPaymentCreated(payment);
        
        log.info("Created scheduled payment: {}", payment.getId());
        return mapper.toResponse(payment);
    }
    
    /**
     * Update scheduled payment
     */
    @Transactional
    public ScheduledPaymentResponse updateScheduledPayment(
            UUID paymentId, 
            UpdateScheduledPaymentRequest request
    ) {
        log.info("Updating scheduled payment: {}", paymentId);
        
        ScheduledPayment payment = repository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));
        
        // Validate ownership
        if (!payment.getUserId().equals(request.getUserId())) {
            throw new ValidationException("Unauthorized to update this payment");
        }
        
        // Update allowed fields
        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }
        if (request.getDescription() != null) {
            payment.setDescription(request.getDescription());
        }
        if (request.getEndDate() != null) {
            payment.setEndDate(request.getEndDate());
        }
        if (request.getPreferredTime() != null) {
            payment.setPreferredTime(request.getPreferredTime());
        }
        if (request.getSendReminder() != null) {
            payment.setSendReminder(request.getSendReminder());
        }
        if (request.getReminderDaysBefore() != null) {
            payment.setReminderDaysBefore(request.getReminderDaysBefore());
        }
        
        payment = repository.save(payment);
        
        // Publish event
        publishEvent(ScheduledPaymentEvent.EventType.UPDATED, payment);
        
        return mapper.toResponse(payment);
    }
    
    /**
     * Pause scheduled payment
     */
    @Transactional
    public ScheduledPaymentResponse pauseScheduledPayment(UUID paymentId, UUID userId, String reason) {
        log.info("Pausing scheduled payment: {}", paymentId);
        
        ScheduledPayment payment = repository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));
        
        // Validate ownership
        if (!payment.getUserId().equals(userId)) {
            throw new ValidationException("Unauthorized to pause this payment");
        }
        
        if (payment.getStatus() != ScheduledPaymentStatus.ACTIVE) {
            throw new ValidationException("Payment is not active");
        }
        
        payment.setStatus(ScheduledPaymentStatus.PAUSED);
        payment.setPauseReason(reason);
        payment.setPausedAt(LocalDateTime.now());
        
        payment = repository.save(payment);
        
        // Publish event
        publishEvent(ScheduledPaymentEvent.EventType.PAUSED, payment);
        
        // Send notification
        notificationService.sendScheduledPaymentPaused(payment);
        
        return mapper.toResponse(payment);
    }
    
    /**
     * Resume scheduled payment
     */
    @Transactional
    public ScheduledPaymentResponse resumeScheduledPayment(UUID paymentId, UUID userId) {
        log.info("Resuming scheduled payment: {}", paymentId);
        
        ScheduledPayment payment = repository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));
        
        // Validate ownership
        if (!payment.getUserId().equals(userId)) {
            throw new ValidationException("Unauthorized to resume this payment");
        }
        
        if (payment.getStatus() != ScheduledPaymentStatus.PAUSED) {
            throw new ValidationException("Payment is not paused");
        }
        
        payment.setStatus(ScheduledPaymentStatus.ACTIVE);
        payment.setPauseReason(null);
        payment.setPausedAt(null);
        
        // Recalculate next payment date
        LocalDate nextDate = calculateNextPaymentDateFromNow(payment);
        payment.setNextPaymentDate(nextDate);
        
        payment = repository.save(payment);
        
        // Publish event
        publishEvent(ScheduledPaymentEvent.EventType.RESUMED, payment);
        
        // Send notification
        notificationService.sendScheduledPaymentResumed(payment);
        
        return mapper.toResponse(payment);
    }
    
    /**
     * Cancel scheduled payment
     */
    @Transactional
    public void cancelScheduledPayment(UUID paymentId, UUID userId, String reason) {
        log.info("Cancelling scheduled payment: {}", paymentId);
        
        ScheduledPayment payment = repository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));
        
        // Validate ownership
        if (!payment.getUserId().equals(userId)) {
            throw new ValidationException("Unauthorized to cancel this payment");
        }
        
        if (payment.getStatus() == ScheduledPaymentStatus.CANCELLED) {
            throw new ValidationException("Payment is already cancelled");
        }
        
        payment.setStatus(ScheduledPaymentStatus.CANCELLED);
        payment.setCancellationReason(reason);
        payment.setCancelledAt(LocalDateTime.now());
        
        repository.save(payment);
        
        // Publish event
        publishEvent(ScheduledPaymentEvent.EventType.CANCELLED, payment);
        
        // Send notification
        notificationService.sendScheduledPaymentCancelled(payment);
    }
    
    /**
     * Get scheduled payments for user
     */
    @Transactional(readOnly = true)
    public Page<ScheduledPaymentResponse> getUserScheduledPayments(
            UUID userId, 
            ScheduledPaymentStatus status,
            Pageable pageable
    ) {
        Page<ScheduledPayment> payments;
        
        if (status != null) {
            payments = repository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            payments = repository.findByUserId(userId, pageable);
        }
        
        return payments.map(mapper::toResponse);
    }
    
    /**
     * Get scheduled payment details
     */
    @Transactional(readOnly = true)
    public ScheduledPaymentResponse getScheduledPayment(UUID paymentId, UUID userId) {
        ScheduledPayment payment = repository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));
        
        // Validate ownership
        if (!payment.getUserId().equals(userId)) {
            throw new ValidationException("Unauthorized to view this payment");
        }
        
        return mapper.toResponse(payment);
    }
    
    /**
     * Process due scheduled payments
     * Runs every minute to check for due payments
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processDuePayments() {
        log.debug("Processing due scheduled payments");
        
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();
        
        // Find all due payments
        List<ScheduledPayment> duePayments = repository.findDuePayments(today, currentTime);
        
        log.info("Found {} due payments to process", duePayments.size());
        
        for (ScheduledPayment payment : duePayments) {
            try {
                processScheduledPayment(payment);
            } catch (Exception e) {
                log.error("Failed to process scheduled payment: {}", payment.getId(), e);
                handlePaymentFailure(payment, e.getMessage());
            }
        }
    }
    
    /**
     * Send payment reminders
     * Runs daily at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendPaymentReminders() {
        log.info("Sending payment reminders");
        
        LocalDate today = LocalDate.now();
        
        // Find payments that need reminders
        List<ScheduledPayment> paymentsNeedingReminders = repository.findPaymentsNeedingReminders(today);
        
        log.info("Found {} payments needing reminders", paymentsNeedingReminders.size());
        
        for (ScheduledPayment payment : paymentsNeedingReminders) {
            try {
                notificationService.sendScheduledPaymentReminder(payment);
            } catch (Exception e) {
                log.error("Failed to send reminder for payment: {}", payment.getId(), e);
            }
        }
    }
    
    /**
     * Process a single scheduled payment
     */
    private void processScheduledPayment(ScheduledPayment payment) {
        log.info("Processing scheduled payment: {}", payment.getId());
        
        try {
            // Create payment request
            CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                    .senderId(payment.getUserId())
                    .recipientId(payment.getRecipientId())
                    .recipientType(payment.getRecipientType())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .description(payment.getDescription())
                    .paymentMethod(payment.getPaymentMethod())
                    .paymentMethodId(payment.getPaymentMethodId())
                    .metadata(payment.getMetadata())
                    .scheduledPaymentId(payment.getId())
                    .build();
            
            // Process payment
            PaymentResponse paymentResponse = paymentService.createPayment(paymentRequest);
            
            // Update scheduled payment
            payment.setLastPaymentDate(LocalDateTime.now());
            payment.setLastPaymentStatus("SUCCESS");
            payment.setLastPaymentId(paymentResponse.getId());
            payment.incrementCompletedPayments();
            
            // Calculate next payment date
            LocalDate nextDate = payment.calculateNextPaymentDate();
            payment.setNextPaymentDate(nextDate);
            
            // Check if payment should be completed
            if (payment.hasReachedLimit() || payment.hasExpired() || nextDate == null) {
                payment.setStatus(ScheduledPaymentStatus.COMPLETED);
                log.info("Scheduled payment completed: {}", payment.getId());
            }
            
            repository.save(payment);
            
            // Send notification if enabled
            if (Boolean.TRUE.equals(payment.getNotifyOnSuccess())) {
                notificationService.sendScheduledPaymentProcessed(payment, paymentResponse);
            }
            
            // Publish event
            publishEvent(ScheduledPaymentEvent.EventType.PROCESSED, payment);
            
        } catch (Exception e) {
            log.error("Failed to process scheduled payment: {}", payment.getId(), e);
            throw new ScheduledPaymentException("Failed to process payment", e);
        }
    }
    
    /**
     * Handle payment failure
     */
    private void handlePaymentFailure(ScheduledPayment payment, String error) {
        payment.setLastPaymentDate(LocalDateTime.now());
        payment.setLastPaymentStatus("FAILED");
        payment.incrementFailedPayments();
        
        // Check if too many failures
        if (payment.getFailedPayments() >= 3) {
            payment.setStatus(ScheduledPaymentStatus.FAILED);
            log.warn("Scheduled payment failed too many times: {}", payment.getId());
        }
        
        repository.save(payment);
        
        // Send notification if enabled
        if (Boolean.TRUE.equals(payment.getNotifyOnFailure())) {
            notificationService.sendScheduledPaymentFailed(payment, error);
        }
        
        // Publish event
        publishEvent(ScheduledPaymentEvent.EventType.FAILED, payment);
    }
    
    /**
     * Validate create request
     */
    private void validateCreateRequest(CreateScheduledPaymentRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new ValidationException("Start date cannot be in the past");
        }
        
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new ValidationException("End date must be after start date");
        }
        
        if (request.getScheduleType() == ScheduleType.RECURRING && request.getTotalPayments() == null) {
            if (request.getEndDate() == null) {
                throw new ValidationException("Recurring payments must have either end date or total payments");
            }
        }
    }
    
    /**
     * Check for duplicate scheduled payments
     */
    private boolean isDuplicateScheduledPayment(CreateScheduledPaymentRequest request) {
        return repository.existsByUserIdAndRecipientIdAndAmountAndStatusAndScheduleType(
                request.getUserId(),
                request.getRecipientId(),
                request.getAmount(),
                ScheduledPaymentStatus.ACTIVE,
                request.getScheduleType()
        );
    }
    
    /**
     * Calculate initial payment date
     */
    private LocalDate calculateInitialPaymentDate(ScheduledPayment payment) {
        LocalDate startDate = payment.getStartDate();
        LocalDate today = LocalDate.now();
        
        if (startDate.isAfter(today)) {
            return startDate;
        } else if (startDate.equals(today)) {
            // Check if preferred time has passed
            if (payment.getPreferredTime() != null && 
                LocalTime.now().isAfter(payment.getPreferredTime())) {
                // Schedule for next occurrence
                return payment.calculateNextPaymentDate();
            }
            return startDate;
        } else {
            // Start date is in the past, calculate next occurrence
            payment.setNextPaymentDate(startDate);
            LocalDate nextDate = payment.calculateNextPaymentDate();
            while (nextDate != null && nextDate.isBefore(today)) {
                payment.setNextPaymentDate(nextDate);
                nextDate = payment.calculateNextPaymentDate();
            }
            return nextDate;
        }
    }
    
    /**
     * Calculate next payment date from current date
     */
    private LocalDate calculateNextPaymentDateFromNow(ScheduledPayment payment) {
        LocalDate today = LocalDate.now();
        payment.setNextPaymentDate(today);
        
        LocalDate nextDate = payment.calculateNextPaymentDate();
        if (nextDate != null && !nextDate.isAfter(today)) {
            // If next date is today or past, calculate one more iteration
            payment.setNextPaymentDate(nextDate);
            nextDate = payment.calculateNextPaymentDate();
        }
        
        return nextDate;
    }
    
    /**
     * Publish scheduled payment event
     */
    private void publishEvent(ScheduledPaymentEvent.EventType type, ScheduledPayment payment) {
        ScheduledPaymentEvent event = ScheduledPaymentEvent.builder()
                .eventType(type)
                .userId(payment.getUserId())
                .paymentId(payment.getId())
                .recipientId(payment.getRecipientId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .timestamp(LocalDateTime.now())
                .build();
        
        eventPublisher.publishEvent("scheduled-payments", event);
    }
}