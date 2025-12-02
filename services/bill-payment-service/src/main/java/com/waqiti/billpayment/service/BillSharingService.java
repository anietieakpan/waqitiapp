package com.waqiti.billpayment.service;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing bill sharing/splitting
 * Allows users to split bills with roommates, family, friends
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillSharingService {

    private final BillShareRequestRepository shareRequestRepository;
    private final BillRepository billRepository;
    private final BillPaymentProcessingService paymentProcessingService;
    private final BillPaymentAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;
    // private final NotificationServiceClient notificationClient;

    private Counter shareRequestCreatedCounter;
    private Counter shareRequestAcceptedCounter;
    private Counter shareRequestPaidCounter;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        shareRequestCreatedCounter = Counter.builder("share.request.created")
                .description("Number of share requests created")
                .register(meterRegistry);

        shareRequestAcceptedCounter = Counter.builder("share.request.accepted")
                .description("Number of share requests accepted")
                .register(meterRegistry);

        shareRequestPaidCounter = Counter.builder("share.request.paid")
                .description("Number of shares paid")
                .register(meterRegistry);
    }

    /**
     * Create share request
     */
    @Transactional
    public BillShareRequest createShareRequest(String creatorUserId, UUID billId, String participantUserId,
                                                BigDecimal shareAmount, String message) {
        log.info("Creating share request: creator={}, bill={}, participant={}, amount={}",
                creatorUserId, billId, participantUserId, shareAmount);

        // Validate bill
        Bill bill = billRepository.findByIdAndUserId(billId, creatorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.UNPAID && bill.getStatus() != BillStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Cannot share paid bill");
        }

        // Validate share amount
        if (shareAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Share amount must be positive");
        }

        if (shareAmount.compareTo(bill.getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("Share amount exceeds remaining bill amount");
        }

        // Check for duplicate share request
        boolean exists = shareRequestRepository.existsByBillIdAndParticipantUserIdAndStatusIn(
                billId, participantUserId, List.of(ShareStatus.PENDING, ShareStatus.ACCEPTED)
        );

        if (exists) {
            throw new IllegalStateException("Share request already exists for this participant");
        }

        // Calculate share percentage
        BigDecimal sharePercentage = shareAmount.divide(bill.getAmount(), 2, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        // Set expiration (30 days)
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        BillShareRequest shareRequest = BillShareRequest.builder()
                .billId(billId)
                .creatorUserId(creatorUserId)
                .participantUserId(participantUserId)
                .shareAmount(shareAmount)
                .sharePercentage(sharePercentage)
                .status(ShareStatus.PENDING)
                .invitationMessage(message)
                .expiresAt(expiresAt)
                .build();

        BillShareRequest savedRequest = shareRequestRepository.save(shareRequest);

        // Send notification to participant
        sendShareRequestNotification(savedRequest, bill);

        auditLog(savedRequest, "SHARE_REQUEST_CREATED", creatorUserId);
        shareRequestCreatedCounter.increment();

        log.info("Share request created: {}", savedRequest.getId());
        return savedRequest;
    }

    /**
     * Accept share request
     */
    @Transactional
    public BillShareRequest acceptShareRequest(UUID shareRequestId, String participantUserId) {
        log.info("Accepting share request: {}, participant: {}", shareRequestId, participantUserId);

        BillShareRequest shareRequest = getShareRequest(shareRequestId, participantUserId);

        if (shareRequest.getStatus() != ShareStatus.PENDING) {
            throw new IllegalStateException("Can only accept pending share requests");
        }

        if (shareRequest.isExpired()) {
            shareRequest.setStatus(ShareStatus.EXPIRED);
            shareRequestRepository.save(shareRequest);
            throw new IllegalStateException("Share request has expired");
        }

        shareRequest.accept();
        BillShareRequest savedRequest = shareRequestRepository.save(shareRequest);

        // Notify creator
        sendShareAcceptedNotification(savedRequest);

        auditLog(savedRequest, "SHARE_REQUEST_ACCEPTED", participantUserId);
        shareRequestAcceptedCounter.increment();

        log.info("Share request accepted: {}", shareRequestId);
        return savedRequest;
    }

    /**
     * Reject share request
     */
    @Transactional
    public void rejectShareRequest(UUID shareRequestId, String participantUserId, String reason) {
        log.info("Rejecting share request: {}, participant: {}, reason: {}",
                shareRequestId, participantUserId, reason);

        BillShareRequest shareRequest = getShareRequest(shareRequestId, participantUserId);

        if (shareRequest.getStatus() != ShareStatus.PENDING) {
            throw new IllegalStateException("Can only reject pending share requests");
        }

        shareRequest.reject(reason);
        shareRequestRepository.save(shareRequest);

        // Notify creator
        sendShareRejectedNotification(shareRequest);

        auditLog(shareRequest, "SHARE_REQUEST_REJECTED", participantUserId);

        log.info("Share request rejected: {}", shareRequestId);
    }

    /**
     * Pay share
     */
    @Transactional
    public void payShare(UUID shareRequestId, String participantUserId, PaymentMethod paymentMethod) {
        log.info("Paying share: {}, participant: {}", shareRequestId, participantUserId);

        BillShareRequest shareRequest = getShareRequest(shareRequestId, participantUserId);

        if (shareRequest.getStatus() != ShareStatus.ACCEPTED) {
            throw new IllegalStateException("Can only pay accepted share requests");
        }

        if (shareRequest.isPaid()) {
            throw new IllegalStateException("Share already paid");
        }

        // Initiate payment
        BillPayment payment = paymentProcessingService.initiatePayment(
                participantUserId,
                shareRequest.getBillId(),
                shareRequest.getShareAmount(),
                paymentMethod,
                "SHARE-" + shareRequestId
        );

        // Mark share as paid
        shareRequest.markAsPaid(payment.getId());
        shareRequestRepository.save(shareRequest);

        // Notify creator
        sendSharePaidNotification(shareRequest);

        auditLog(shareRequest, "SHARE_PAID", participantUserId);
        shareRequestPaidCounter.increment();

        log.info("Share paid: {}, payment: {}", shareRequestId, payment.getId());
    }

    /**
     * Cancel share request (by creator)
     */
    @Transactional
    public void cancelShareRequest(UUID shareRequestId, String creatorUserId) {
        log.info("Cancelling share request: {}, creator: {}", shareRequestId, creatorUserId);

        BillShareRequest shareRequest = shareRequestRepository.findById(shareRequestId)
                .filter(sr -> sr.getCreatorUserId().equals(creatorUserId))
                .orElseThrow(() -> new IllegalArgumentException("Share request not found: " + shareRequestId));

        if (shareRequest.getStatus() == ShareStatus.PAID) {
            throw new IllegalStateException("Cannot cancel paid share request");
        }

        shareRequest.setStatus(ShareStatus.CANCELLED);
        shareRequestRepository.save(shareRequest);

        // Notify participant if request was accepted
        if (shareRequest.getStatus() == ShareStatus.ACCEPTED) {
            sendShareCancelledNotification(shareRequest);
        }

        auditLog(shareRequest, "SHARE_REQUEST_CANCELLED", creatorUserId);

        log.info("Share request cancelled: {}", shareRequestId);
    }

    /**
     * Get share request
     */
    @Transactional(readOnly = true)
    public BillShareRequest getShareRequest(UUID shareRequestId, String userId) {
        return shareRequestRepository.findById(shareRequestId)
                .filter(sr -> sr.getCreatorUserId().equals(userId) || sr.getParticipantUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Share request not found: " + shareRequestId));
    }

    /**
     * Get share requests for bill
     */
    @Transactional(readOnly = true)
    public List<BillShareRequest> getShareRequestsByBill(UUID billId) {
        return shareRequestRepository.findByBillId(billId);
    }

    /**
     * Get share requests created by user
     */
    @Transactional(readOnly = true)
    public List<BillShareRequest> getShareRequestsByCreator(String creatorUserId) {
        return shareRequestRepository.findByCreatorUserId(creatorUserId);
    }

    /**
     * Get share requests for participant
     */
    @Transactional(readOnly = true)
    public List<BillShareRequest> getShareRequestsByParticipant(String participantUserId) {
        return shareRequestRepository.findByParticipantUserId(participantUserId);
    }

    /**
     * Get pending share requests for participant
     */
    @Transactional(readOnly = true)
    public List<BillShareRequest> getPendingShareRequests(String participantUserId) {
        return shareRequestRepository.findByParticipantUserIdAndStatus(participantUserId, ShareStatus.PENDING);
    }

    /**
     * Get total shared amount for bill
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalSharedAmount(UUID billId) {
        BigDecimal total = shareRequestRepository.getTotalSharedAmount(billId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Send reminders for unpaid shares (called by scheduler)
     */
    @Transactional
    public void sendShareReminders() {
        log.info("Sending share reminders");

        List<BillShareRequest> unpaidShares = shareRequestRepository.findUnpaidAcceptedShares();
        int remindersSent = 0;

        for (BillShareRequest shareRequest : unpaidShares) {
            try {
                // Check if reminder should be sent (max 3 reminders, once per week)
                if (shareRequest.getReminderCount() < 3) {
                    LocalDateTime lastReminder = shareRequest.getLastReminderSentAt();
                    if (lastReminder == null || lastReminder.plusDays(7).isBefore(LocalDateTime.now())) {
                        sendShareReminderNotification(shareRequest);
                        shareRequest.recordReminderSent();
                        shareRequestRepository.save(shareRequest);
                        remindersSent++;
                    }
                }
            } catch (Exception e) {
                log.error("Error sending share reminder: {}", shareRequest.getId(), e);
            }
        }

        log.info("Sent {} share reminders", remindersSent);
    }

    /**
     * Process expired share requests (called by scheduler)
     */
    @Transactional
    public void processExpiredShareRequests() {
        log.info("Processing expired share requests");

        List<BillShareRequest> expiredRequests = shareRequestRepository.findExpiredPendingShares(LocalDateTime.now());

        for (BillShareRequest shareRequest : expiredRequests) {
            shareRequest.setStatus(ShareStatus.EXPIRED);
            shareRequestRepository.save(shareRequest);

            // Notify creator
            sendShareExpiredNotification(shareRequest);

            auditLog(shareRequest, "SHARE_REQUEST_EXPIRED", shareRequest.getCreatorUserId());
        }

        log.info("Processed {} expired share requests", expiredRequests.size());
    }

    // Private helper methods

    private void sendShareRequestNotification(BillShareRequest shareRequest, Bill bill) {
        log.info("Sending share request notification to: {}", shareRequest.getParticipantUserId());
        // TODO: Call notification service
    }

    private void sendShareAcceptedNotification(BillShareRequest shareRequest) {
        log.info("Sending share accepted notification to: {}", shareRequest.getCreatorUserId());
        // TODO: Call notification service
    }

    private void sendShareRejectedNotification(BillShareRequest shareRequest) {
        log.info("Sending share rejected notification to: {}", shareRequest.getCreatorUserId());
        // TODO: Call notification service
    }

    private void sendSharePaidNotification(BillShareRequest shareRequest) {
        log.info("Sending share paid notification to: {}", shareRequest.getCreatorUserId());
        // TODO: Call notification service
    }

    private void sendShareCancelledNotification(BillShareRequest shareRequest) {
        log.info("Sending share cancelled notification to: {}", shareRequest.getParticipantUserId());
        // TODO: Call notification service
    }

    private void sendShareReminderNotification(BillShareRequest shareRequest) {
        log.info("Sending share reminder notification to: {}", shareRequest.getParticipantUserId());
        // TODO: Call notification service
    }

    private void sendShareExpiredNotification(BillShareRequest shareRequest) {
        log.info("Sending share expired notification to: {}", shareRequest.getCreatorUserId());
        // TODO: Call notification service
    }

    private void auditLog(BillShareRequest shareRequest, String action, String userId) {
        try {
            BillPaymentAuditLog auditLog = BillPaymentAuditLog.builder()
                    .entityType("BILL_SHARE_REQUEST")
                    .entityId(shareRequest.getId())
                    .action(action)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log for share request: {}", shareRequest.getId(), e);
        }
    }
}
