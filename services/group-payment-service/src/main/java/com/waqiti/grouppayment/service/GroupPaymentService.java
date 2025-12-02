package com.waqiti.grouppayment.service;

import com.waqiti.grouppayment.dto.*;
import com.waqiti.grouppayment.entity.*;
import com.waqiti.grouppayment.exception.GroupPaymentNotFoundException;
import com.waqiti.grouppayment.exception.InsufficientPermissionException;
import com.waqiti.grouppayment.repository.GroupPaymentRepository;
import com.waqiti.grouppayment.repository.GroupPaymentParticipantRepository;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;

// Import UnifiedPaymentService
import com.waqiti.payment.core.UnifiedPaymentService;
import com.waqiti.payment.core.model.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MODERNIZED GroupPaymentService - Now delegates to UnifiedPaymentService
 * Maintains backward compatibility while using the new unified architecture
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPaymentService {

    // Legacy dependencies for backward compatibility
    private final GroupPaymentRepository groupPaymentRepository;
    private final GroupPaymentParticipantRepository participantRepository;
    private final GroupPaymentMapper mapper;
    private final SplitCalculatorService splitCalculatorService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KYCClientService kycClientService;

    // NEW: Unified Payment Service
    private final UnifiedPaymentService unifiedPaymentService;

    /**
     * Create group payment - MODERNIZED to use UnifiedPaymentService
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "GROUP_PAYMENT_CREATE")
    public GroupPaymentResponse createGroupPayment(String createdBy, CreateGroupPaymentRequest request) {
        log.info("Creating group payment: {} by user: {} [UNIFIED]", request.getTitle(), createdBy);

        try {
            // Enhanced KYC check for high-value group payments
            if (request.getTotalAmount().compareTo(new BigDecimal("5000")) > 0) {
                if (!kycClientService.canUserMakeHighValueTransfer(createdBy)) {
                    throw new RuntimeException("Enhanced KYC verification required for group payments over $5,000");
                }
            }

            // Create legacy group payment entity for tracking
            GroupPayment groupPayment = createLegacyGroupPayment(createdBy, request);

            // CREATE GROUP PAYMENT USING UNIFIED SERVICE
            PaymentRequest unifiedRequest = PaymentRequest.builder()
                    .paymentId(UUID.randomUUID())
                    .type(PaymentType.GROUP)
                    .providerType(ProviderType.INTERNAL)
                    .fromUserId(createdBy)
                    .amount(request.getTotalAmount())
                    .metadata(Map.of(
                            "title", request.getTitle(),
                            "description", request.getDescription() != null ? request.getDescription() : "",
                            "participants", request.getParticipants().stream()
                                    .map(CreateGroupPaymentParticipantRequest::getUserId)
                                    .toList(),
                            "splitType", request.getSplitType().toString(),
                            "participantCount", request.getParticipants().size(),
                            "currency", "USD",
                            "groupPaymentId", groupPayment.getId().toString()
                    ))
                    .build();

            // Process through UnifiedPaymentService
            PaymentResult result = unifiedPaymentService.processPayment(unifiedRequest);

            // Update group payment with unified result
            groupPayment.setUnifiedTransactionId(result.getTransactionId());
            groupPayment.setUnifiedStatus(result.getStatus().toString());
            groupPayment = groupPaymentRepository.save(groupPayment);

            // Create participants and calculate splits
            List<GroupPaymentParticipant> participants = createParticipants(groupPayment, request, result);

            // Send notifications (legacy)
            notificationService.sendGroupPaymentCreatedNotifications(groupPayment, participants);

            // Publish Kafka event
            publishGroupPaymentEvent(groupPayment, result, "CREATED");

            log.info("Group payment created successfully: {} with unified transaction: {}", 
                    groupPayment.getId(), result.getTransactionId());

            return enrichGroupPaymentResponse(mapper.toGroupPaymentResponse(groupPayment), result);

        } catch (Exception e) {
            log.error("Error creating group payment via UnifiedPaymentService", e);
            throw e;
        }
    }

    /**
     * Process group payment settlement - MODERNIZED to use UnifiedPaymentService
     */
    @Transactional
    public GroupPaymentResponse settleGroupPayment(UUID groupPaymentId, String settledBy) {
        log.info("Settling group payment: {} by user: {} [UNIFIED]", groupPaymentId, settledBy);

        try {
            GroupPayment groupPayment = findGroupPaymentById(groupPaymentId);
            
            // Check permissions
            if (!canUserSettleGroupPayment(groupPayment, settledBy)) {
                throw new InsufficientPermissionException("User not authorized to settle this group payment");
            }

            // Get all participants who haven't paid
            List<GroupPaymentParticipant> unpaidParticipants = participantRepository
                    .findByGroupPaymentAndStatus(groupPayment, ParticipantStatus.PENDING);

            // Process settlements through UnifiedPaymentService
            for (GroupPaymentParticipant participant : unpaidParticipants) {
                PaymentRequest settlementRequest = PaymentRequest.builder()
                        .paymentId(UUID.randomUUID())
                        .type(PaymentType.GROUP)
                        .providerType(ProviderType.INTERNAL)
                        .fromUserId(participant.getUserId())
                        .toUserId(groupPayment.getCreatedBy())
                        .amount(participant.getAmountOwed())
                        .metadata(Map.of(
                                "settlementType", "GROUP_PAYMENT_SETTLEMENT",
                                "groupPaymentId", groupPaymentId.toString(),
                                "participantId", participant.getId().toString(),
                                "originalTitle", groupPayment.getTitle()
                        ))
                        .build();

                PaymentResult settlementResult = unifiedPaymentService.processPayment(settlementRequest);

                // Update participant status based on result
                if (settlementResult.isSuccess()) {
                    participant.setStatus(ParticipantStatus.PAID);
                    participant.setPaidAt(Instant.now());
                    participant.setUnifiedTransactionId(settlementResult.getTransactionId());
                } else {
                    log.warn("Settlement failed for participant {}: {}", 
                            participant.getUserId(), settlementResult.getProviderResponse());
                }
                participantRepository.save(participant);
            }

            // Update group payment status
            boolean allPaid = participantRepository.findByGroupPayment(groupPayment)
                    .stream()
                    .allMatch(p -> p.getStatus() == ParticipantStatus.PAID);

            if (allPaid) {
                groupPayment.setStatus(GroupPaymentStatus.COMPLETED);
                groupPayment.setCompletedAt(Instant.now());
                log.info("Group payment {} fully settled [UNIFIED]", groupPaymentId);
            } else {
                groupPayment.setStatus(GroupPaymentStatus.PARTIALLY_PAID);
            }

            groupPayment = groupPaymentRepository.save(groupPayment);

            // Publish settlement event
            publishGroupPaymentEvent(groupPayment, null, "SETTLED");

            return mapper.toGroupPaymentResponse(groupPayment);

        } catch (Exception e) {
            log.error("Error settling group payment via UnifiedPaymentService", e);
            throw e;
        }
    }

    /**
     * Get group payment analytics - MODERNIZED to use UnifiedPaymentService
     */
    public GroupPaymentAnalytics getGroupPaymentAnalytics(String userId, String period) {
        log.info("Getting group payment analytics for user {} period={} [UNIFIED]", userId, period);
        
        try {
            // Get analytics from UnifiedPaymentService
            AnalyticsFilter filter = switch (period.toLowerCase()) {
                case "week" -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusWeeks(1))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.GROUP)
                        .groupBy("day")
                        .build();
                case "month" -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusMonths(1))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.GROUP)
                        .groupBy("day")
                        .build();
                case "year" -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusYears(1))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.GROUP)
                        .groupBy("month")
                        .build();
                default -> AnalyticsFilter.builder()
                        .startDate(LocalDateTime.now().minusDays(30))
                        .endDate(LocalDateTime.now())
                        .paymentType(PaymentType.GROUP)
                        .groupBy("day")
                        .build();
            };
            
            PaymentAnalytics unifiedAnalytics = unifiedPaymentService.getAnalytics(userId, filter);
            
            // Convert to group payment specific analytics
            return convertToGroupPaymentAnalytics(unifiedAnalytics, userId);
            
        } catch (Exception e) {
            log.error("Error getting group payment analytics", e);
            throw e;
        }
    }

    /**
     * Get active group payments for user
     */
    public Page<GroupPaymentResponse> getActiveGroupPayments(String userId, Pageable pageable) {
        log.info("Getting active group payments for user: {} [UNIFIED]", userId);
        
        Page<GroupPayment> groupPayments = groupPaymentRepository
                .findActiveGroupPaymentsByUser(userId, pageable);
        
        return groupPayments.map(gp -> {
            GroupPaymentResponse response = mapper.toGroupPaymentResponse(gp);
            // Enrich with unified payment data if available
            if (gp.getUnifiedTransactionId() != null) {
                enrichWithUnifiedData(response, gp.getUnifiedTransactionId());
            }
            return response;
        });
    }

    // LEGACY SUPPORT METHODS - Maintain backward compatibility

    private GroupPayment createLegacyGroupPayment(String createdBy, CreateGroupPaymentRequest request) {
        GroupPayment groupPayment = new GroupPayment();
        groupPayment.setTitle(request.getTitle());
        groupPayment.setDescription(request.getDescription());
        groupPayment.setTotalAmount(request.getTotalAmount());
        groupPayment.setCreatedBy(createdBy);
        groupPayment.setSplitType(request.getSplitType());
        groupPayment.setStatus(GroupPaymentStatus.ACTIVE);
        groupPayment.setCreatedAt(Instant.now());
        
        return groupPaymentRepository.save(groupPayment);
    }

    private List<GroupPaymentParticipant> createParticipants(GroupPayment groupPayment, 
                                                           CreateGroupPaymentRequest request, 
                                                           PaymentResult result) {
        List<GroupPaymentParticipant> participants = request.getParticipants().stream()
                .map(participantRequest -> {
                    GroupPaymentParticipant participant = new GroupPaymentParticipant();
                    participant.setGroupPayment(groupPayment);
                    participant.setUserId(participantRequest.getUserId());
                    
                    // Calculate amount owed using split calculator
                    BigDecimal amountOwed = splitCalculatorService.calculateParticipantAmount(
                            groupPayment.getTotalAmount(),
                            request.getParticipants().size(),
                            participantRequest.getSharePercentage(),
                            groupPayment.getSplitType()
                    );
                    
                    participant.setAmountOwed(amountOwed);
                    participant.setStatus(ParticipantStatus.PENDING);
                    participant.setJoinedAt(Instant.now());
                    participant.setUnifiedTransactionId(result.getTransactionId());
                    
                    return participant;
                })
                .collect(Collectors.toList());

        return participantRepository.saveAll(participants);
    }

    private GroupPaymentResponse enrichGroupPaymentResponse(GroupPaymentResponse response, PaymentResult result) {
        response.setUnifiedTransactionId(result.getTransactionId());
        response.setUnifiedStatus(result.getStatus().toString());
        response.setProcessedAt(result.getProcessedAt());
        return response;
    }

    private void publishGroupPaymentEvent(GroupPayment groupPayment, PaymentResult result, String eventType) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "GROUP_PAYMENT_" + eventType,
                    "groupPaymentId", groupPayment.getId(),
                    "title", groupPayment.getTitle(),
                    "amount", groupPayment.getTotalAmount(),
                    "createdBy", groupPayment.getCreatedBy(),
                    "status", groupPayment.getStatus(),
                    "unifiedTransactionId", result != null ? result.getTransactionId() : "",
                    "timestamp", Instant.now()
            );
            
            kafkaTemplate.send("group-payment-events", groupPayment.getId().toString(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish group payment event", e);
        }
    }

    private GroupPaymentAnalytics convertToGroupPaymentAnalytics(PaymentAnalytics unifiedAnalytics, String userId) {
        GroupPaymentAnalytics analytics = new GroupPaymentAnalytics();
        analytics.setUserId(userId);
        analytics.setTotalGroupPayments(unifiedAnalytics.getTotalPayments());
        analytics.setSuccessfulGroupPayments(unifiedAnalytics.getSuccessfulPayments());
        analytics.setFailedGroupPayments(unifiedAnalytics.getFailedPayments());
        analytics.setTotalAmount(unifiedAnalytics.getTotalAmount());
        analytics.setAverageAmount(unifiedAnalytics.getAverageAmount());
        analytics.setSuccessRate(unifiedAnalytics.getSuccessRate());
        analytics.setPeriodStart(unifiedAnalytics.getPeriodStart());
        analytics.setPeriodEnd(unifiedAnalytics.getPeriodEnd());
        return analytics;
    }

    private void enrichWithUnifiedData(GroupPaymentResponse response, String transactionId) {
        try {
            // In production, could fetch additional unified payment data
            response.setUnifiedTransactionId(transactionId);
        } catch (Exception e) {
            log.warn("Could not enrich with unified data: {}", e.getMessage());
        }
    }

    private GroupPayment findGroupPaymentById(UUID groupPaymentId) {
        return groupPaymentRepository.findById(groupPaymentId)
                .orElseThrow(() -> new GroupPaymentNotFoundException("Group payment not found: " + groupPaymentId));
    }

    private boolean canUserSettleGroupPayment(GroupPayment groupPayment, String userId) {
        return groupPayment.getCreatedBy().equals(userId) ||
               participantRepository.existsByGroupPaymentAndUserId(groupPayment, userId);
    }

    /**
     * Get single group payment by ID
     */
    public GroupPaymentResponse getGroupPayment(String groupPaymentId, String userId) {
        log.info("Getting group payment: {} for user: {}", groupPaymentId, userId);

        GroupPayment groupPayment = groupPaymentRepository.findByGroupPaymentId(groupPaymentId)
                .orElseThrow(() -> new GroupPaymentNotFoundException("Group payment not found: " + groupPaymentId));

        // Check if user has access (creator or participant)
        if (!canUserAccessGroupPayment(groupPayment, userId)) {
            throw new InsufficientPermissionException("User not authorized to view this group payment");
        }

        GroupPaymentResponse response = mapper.toGroupPaymentResponse(groupPayment);

        // Enrich with unified payment data if available
        if (groupPayment.getUnifiedTransactionId() != null) {
            enrichWithUnifiedData(response, groupPayment.getUnifiedTransactionId());
        }

        return response;
    }

    /**
     * Get all group payments for a user
     */
    public Page<GroupPaymentResponse> getUserGroupPayments(String userId, Pageable pageable) {
        log.info("Getting all group payments for user: {}", userId);

        Page<GroupPayment> groupPayments = groupPaymentRepository.findByUserInvolvement(userId, pageable);

        return groupPayments.map(gp -> {
            GroupPaymentResponse response = mapper.toGroupPaymentResponse(gp);
            if (gp.getUnifiedTransactionId() != null) {
                enrichWithUnifiedData(response, gp.getUnifiedTransactionId());
            }
            return response;
        });
    }

    /**
     * Update group payment
     */
    @Transactional
    public GroupPaymentResponse updateGroupPayment(String groupPaymentId, String userId, UpdateGroupPaymentRequest request) {
        log.info("Updating group payment: {} by user: {}", groupPaymentId, userId);

        GroupPayment groupPayment = groupPaymentRepository.findByGroupPaymentId(groupPaymentId)
                .orElseThrow(() -> new GroupPaymentNotFoundException("Group payment not found: " + groupPaymentId));

        // Only creator can update
        if (!groupPayment.getCreatedBy().equals(userId)) {
            throw new InsufficientPermissionException("Only the creator can update this group payment");
        }

        // Only allow updates if status is DRAFT or ACTIVE
        if (groupPayment.getStatus() != GroupPayment.GroupPaymentStatus.DRAFT &&
            groupPayment.getStatus() != GroupPayment.GroupPaymentStatus.ACTIVE) {
            throw new IllegalStateException("Cannot update group payment in current status: " + groupPayment.getStatus());
        }

        // Update allowed fields
        if (request.getTitle() != null) {
            groupPayment.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            groupPayment.setDescription(request.getDescription());
        }
        if (request.getDueDate() != null) {
            groupPayment.setDueDate(request.getDueDate());
        }

        groupPayment = groupPaymentRepository.save(groupPayment);

        // Publish update event
        publishGroupPaymentEvent(groupPayment, null, "UPDATED");

        return mapper.toGroupPaymentResponse(groupPayment);
    }

    /**
     * Cancel group payment
     */
    @Transactional
    public void cancelGroupPayment(String groupPaymentId, String userId) {
        log.info("Cancelling group payment: {} by user: {}", groupPaymentId, userId);

        GroupPayment groupPayment = groupPaymentRepository.findByGroupPaymentId(groupPaymentId)
                .orElseThrow(() -> new GroupPaymentNotFoundException("Group payment not found: " + groupPaymentId));

        // Only creator can cancel
        if (!groupPayment.getCreatedBy().equals(userId)) {
            throw new InsufficientPermissionException("Only the creator can cancel this group payment");
        }

        // Cannot cancel completed payments
        if (groupPayment.getStatus() == GroupPayment.GroupPaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed group payment");
        }

        groupPayment.setStatus(GroupPayment.GroupPaymentStatus.CANCELLED);
        groupPaymentRepository.save(groupPayment);

        // Send cancellation notifications
        List<GroupPaymentParticipant> participants = participantRepository.findByGroupPayment(groupPayment);
        notificationService.sendGroupPaymentCancelledNotifications(groupPayment, participants);

        // Publish cancellation event
        publishGroupPaymentEvent(groupPayment, null, "CANCELLED");

        log.info("Group payment cancelled successfully: {}", groupPaymentId);
    }

    /**
     * Record payment from a participant
     */
    @Transactional
    public void recordPayment(String groupPaymentId, String userId, RecordPaymentRequest request) {
        log.info("Recording payment for group payment: {} by user: {}", groupPaymentId, userId);

        GroupPayment groupPayment = groupPaymentRepository.findByGroupPaymentId(groupPaymentId)
                .orElseThrow(() -> new GroupPaymentNotFoundException("Group payment not found: " + groupPaymentId));

        // Find participant
        GroupPaymentParticipant participant = participantRepository
                .findByGroupPaymentAndStatus(groupPayment, GroupPaymentParticipant.ParticipantStatus.PENDING)
                .stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Participant not found or already paid"));

        // Process payment through UnifiedPaymentService
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .paymentId(UUID.randomUUID())
                .type(PaymentType.GROUP)
                .providerType(ProviderType.INTERNAL)
                .fromUserId(userId)
                .toUserId(groupPayment.getCreatedBy())
                .amount(participant.getAmountOwed())
                .metadata(Map.of(
                        "groupPaymentId", groupPaymentId,
                        "participantId", participant.getId().toString(),
                        "paymentMethod", request.getPaymentMethod() != null ? request.getPaymentMethod() : "WALLET"
                ))
                .build();

        PaymentResult result = unifiedPaymentService.processPayment(paymentRequest);

        if (result.isSuccess()) {
            participant.setStatus(GroupPaymentParticipant.ParticipantStatus.PAID);
            participant.setPaidAt(Instant.now());
            participant.setUnifiedTransactionId(result.getTransactionId());
            participantRepository.save(participant);

            // Check if all participants have paid
            updateGroupPaymentStatus(groupPayment);

            // Send notification
            notificationService.sendPaymentReceivedNotification(groupPayment, participant);

            log.info("Payment recorded successfully for participant: {}", userId);
        } else {
            log.error("Payment failed for participant: {}, reason: {}", userId, result.getProviderResponse());
            throw new RuntimeException("Payment processing failed: " + result.getProviderResponse());
        }
    }

    /**
     * Send payment reminders to unpaid participants
     */
    @Transactional
    public void sendReminders(String groupPaymentId, String userId) {
        log.info("Sending reminders for group payment: {} by user: {}", groupPaymentId, userId);

        GroupPayment groupPayment = groupPaymentRepository.findByGroupPaymentId(groupPaymentId)
                .orElseThrow(() -> new GroupPaymentNotFoundException("Group payment not found: " + groupPaymentId));

        // Only creator can send reminders
        if (!groupPayment.getCreatedBy().equals(userId)) {
            throw new InsufficientPermissionException("Only the creator can send reminders");
        }

        // Get all unpaid participants
        List<GroupPaymentParticipant> unpaidParticipants = participantRepository
                .findByGroupPaymentAndStatus(groupPayment, GroupPaymentParticipant.ParticipantStatus.PENDING);

        if (unpaidParticipants.isEmpty()) {
            log.info("No unpaid participants to remind for group payment: {}", groupPaymentId);
            return;
        }

        // Send reminders
        notificationService.sendPaymentReminders(groupPayment, unpaidParticipants);

        log.info("Sent {} payment reminders for group payment: {}", unpaidParticipants.size(), groupPaymentId);
    }

    // Helper methods

    private boolean canUserAccessGroupPayment(GroupPayment groupPayment, String userId) {
        return groupPayment.getCreatedBy().equals(userId) ||
               participantRepository.existsByGroupPaymentAndUserId(groupPayment, userId);
    }

    private void updateGroupPaymentStatus(GroupPayment groupPayment) {
        List<GroupPaymentParticipant> allParticipants = participantRepository.findByGroupPayment(groupPayment);

        long paidCount = allParticipants.stream()
                .filter(p -> p.getStatus() == GroupPaymentParticipant.ParticipantStatus.PAID)
                .count();

        if (paidCount == allParticipants.size()) {
            groupPayment.setStatus(GroupPayment.GroupPaymentStatus.COMPLETED);
            groupPayment.setCompletedAt(Instant.now());
            log.info("Group payment completed: {}", groupPayment.getId());
        } else if (paidCount > 0) {
            groupPayment.setStatus(GroupPayment.GroupPaymentStatus.PARTIALLY_PAID);
        }

        groupPaymentRepository.save(groupPayment);

        if (groupPayment.getStatus() == GroupPayment.GroupPaymentStatus.COMPLETED) {
            publishGroupPaymentEvent(groupPayment, null, "COMPLETED");
        }
    }
}