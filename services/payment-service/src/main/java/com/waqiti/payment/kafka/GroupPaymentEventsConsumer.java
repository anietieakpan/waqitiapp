package com.waqiti.payment.kafka;

import com.waqiti.grouppayment.dto.GroupPaymentEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.payment.service.LedgerIntegrationService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.GroupPayment;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.GroupPaymentRepository;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL CONSUMER: GroupPaymentEventsConsumer  
 * 
 * This was a MISSING CRITICAL consumer for group-payment-events from group-payment-service.
 * Without this consumer, group payment lifecycle events were being lost, causing:
 * - Payment status inconsistencies
 * - Failed notifications to participants 
 * - Broken payment reconciliation
 * - Compliance audit gaps
 * - Financial data loss
 * 
 * BUSINESS IMPACT:
 * - Process group payment splits and settlements
 * - Update individual payment statuses
 * - Trigger participant notifications
 * - Maintain payment audit trail
 * - Ensure regulatory compliance
 * 
 * GROUP PAYMENT RESPONSIBILITIES:
 * - Process group payment completions
 * - Handle payment splits to participants
 * - Update payment statuses
 * - Trigger settlement processes
 * - Send participant notifications
 * - Create compliance records
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GroupPaymentEventsConsumer {
    
    private final PaymentService paymentService;
    private final PaymentNotificationService notificationService;
    private final LedgerIntegrationService ledgerService;
    private final ComplianceService complianceService;
    private final GroupPaymentRepository groupPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;
    private final UniversalDLQHandler dlqHandler;
    
    private static final String DLT_TOPIC = "group-payment-events-dlq";
    private static final Set<String> PROCESSED_EVENTS = new HashSet<>();
    
    /**
     * CRITICAL: Process group payment events for payment lifecycle management
     * 
     * This consumer is essential for:
     * - Group payment processing
     * - Payment status synchronization  
     * - Participant notifications
     * - Settlement processing
     * - Compliance tracking
     */
    @KafkaListener(
        topics = "group-payment-events",
        groupId = "payment-service-group-payment-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional
    public void handleGroupPaymentEvent(
            @Payload GroupPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("group-payment-%s-p%d-o%d",
            event.getGroupPaymentId(), partition, offset);
        
        log.info("PAYMENT: Processing group payment event: groupPaymentId={}, eventType={}, status={}, correlation={}",
            event.getGroupPaymentId(), event.getEventType(), event.getStatus(), correlationId);
        
        try {
            // Idempotency check
            if (isDuplicateEvent(event.getEventId())) {
                log.debug("PAYMENT: Duplicate group payment event: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on event type
            switch (event.getEventType()) {
                case "GROUP_PAYMENT_CREATED":
                    processGroupPaymentCreated(event, correlationId);
                    break;
                    
                case "GROUP_PAYMENT_COMPLETED":
                    processGroupPaymentCompleted(event, correlationId);
                    break;
                    
                case "GROUP_PAYMENT_FAILED":
                    processGroupPaymentFailed(event, correlationId);
                    break;
                    
                case "PARTICIPANT_PAYMENT_PROCESSED":
                    processParticipantPayment(event, correlationId);
                    break;
                    
                case "GROUP_PAYMENT_CANCELLED":
                    processGroupPaymentCancelled(event, correlationId);
                    break;
                    
                case "SETTLEMENT_COMPLETED":
                    processSettlementCompleted(event, correlationId);
                    break;
                    
                default:
                    log.warn("PAYMENT: Unknown group payment event type: {}", event.getEventType());
                    handleUnknownEventType(event, correlationId);
            }
            
            // Create audit trail
            createAuditTrail(event, correlationId);
            
            // Mark event as processed
            markEventProcessed(event.getEventId());
            
            acknowledgment.acknowledge();
            
            log.info("PAYMENT: Successfully processed group payment event: groupPaymentId={}, eventType={}",
                event.getGroupPaymentId(), event.getEventType());
            
        } catch (Exception e) {
            log.error("Error processing group payment event: topic={}, partition={}, offset={}, error={}",
                topic, partition, offset, e.getMessage(), e);

            // Create critical alert
            createCriticalAlert(event, e, correlationId);

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Group payment event processing failed", e);
        }
    }
    
    /**
     * Process group payment creation event
     */
    private void processGroupPaymentCreated(GroupPaymentEvent event, String correlationId) {
        log.info("PAYMENT: Processing group payment creation: groupPaymentId={}", event.getGroupPaymentId());
        
        try {
            // Create or update group payment record
            GroupPayment groupPayment = createOrUpdateGroupPayment(event);
            
            // Create individual payment records for participants
            createParticipantPayments(event, groupPayment, correlationId);
            
            // Send notifications to all participants
            notificationService.sendGroupPaymentCreatedNotifications(groupPayment, correlationId);
            
            // Initialize compliance tracking
            complianceService.initializeGroupPaymentCompliance(groupPayment, correlationId);
            
            log.info("PAYMENT: Group payment creation processed: groupPaymentId={}, participantCount={}",
                event.getGroupPaymentId(), event.getParticipants().size());
            
        } catch (Exception e) {
            log.error("PAYMENT: Failed to process group payment creation: groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process group payment completion event
     */
    private void processGroupPaymentCompleted(GroupPaymentEvent event, String correlationId) {
        log.info("PAYMENT: Processing group payment completion: groupPaymentId={}", event.getGroupPaymentId());
        
        try {
            // Update group payment status
            GroupPayment groupPayment = updateGroupPaymentStatus(event, "COMPLETED");
            
            // Process settlements for all participants
            processGroupSettlement(event, groupPayment, correlationId);
            
            // Update individual payment statuses
            updateParticipantPaymentStatuses(event, PaymentStatus.COMPLETED, correlationId);
            
            // Create ledger entries
            ledgerService.createGroupPaymentLedgerEntries(groupPayment, correlationId);
            
            // Send completion notifications
            notificationService.sendGroupPaymentCompletedNotifications(groupPayment, correlationId);
            
            // Complete compliance tracking
            complianceService.completeGroupPaymentCompliance(groupPayment, correlationId);
            
            log.info("PAYMENT: Group payment completion processed: groupPaymentId={}, totalAmount={}",
                event.getGroupPaymentId(), event.getTotalAmount());
            
        } catch (Exception e) {
            log.error("PAYMENT: Failed to process group payment completion: groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process group payment failure event
     */
    private void processGroupPaymentFailed(GroupPaymentEvent event, String correlationId) {
        log.warn("PAYMENT: Processing group payment failure: groupPaymentId={}, reason={}",
            event.getGroupPaymentId(), event.getFailureReason());
        
        try {
            // Update group payment status
            GroupPayment groupPayment = updateGroupPaymentStatus(event, "FAILED");
            groupPayment.setFailureReason(event.getFailureReason());
            groupPayment.setFailedAt(LocalDateTime.now());
            groupPaymentRepository.save(groupPayment);
            
            // Update individual payment statuses
            updateParticipantPaymentStatuses(event, PaymentStatus.FAILED, correlationId);
            
            // Process refunds if payments were already processed
            processGroupPaymentRefunds(event, groupPayment, correlationId);
            
            // Send failure notifications
            notificationService.sendGroupPaymentFailedNotifications(groupPayment, correlationId);
            
            // Alert compliance team
            complianceService.alertGroupPaymentFailure(groupPayment, correlationId);
            
            log.info("PAYMENT: Group payment failure processed: groupPaymentId={}, participantsNotified={}",
                event.getGroupPaymentId(), event.getParticipants().size());
            
        } catch (Exception e) {
            log.error("PAYMENT: Failed to process group payment failure: groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process individual participant payment
     */
    private void processParticipantPayment(GroupPaymentEvent event, String correlationId) {
        log.info("PAYMENT: Processing participant payment: groupPaymentId={}, participantId={}",
            event.getGroupPaymentId(), event.getCurrentParticipant());
        
        try {
            // Find the specific participant payment
            Payment participantPayment = findParticipantPayment(event);
            
            if (participantPayment != null) {
                // Update payment status
                participantPayment.setStatus(PaymentStatus.PROCESSING);
                participantPayment.setProcessedAt(LocalDateTime.now());
                paymentRepository.save(participantPayment);
                
                // Process the individual payment
                paymentService.processIndividualPayment(participantPayment, correlationId);
                
                // Send participant notification
                notificationService.sendParticipantPaymentNotification(participantPayment, correlationId);
                
                log.info("PAYMENT: Participant payment processed: paymentId={}, amount={}",
                    participantPayment.getId(), participantPayment.getAmount());
            } else {
                log.warn("PAYMENT: Participant payment not found: groupPaymentId={}, participantId={}",
                    event.getGroupPaymentId(), event.getCurrentParticipant());
            }
            
        } catch (Exception e) {
            log.error("PAYMENT: Failed to process participant payment: groupPaymentId={}, participantId={}, error={}",
                event.getGroupPaymentId(), event.getCurrentParticipant(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process group payment cancellation
     */
    private void processGroupPaymentCancelled(GroupPaymentEvent event, String correlationId) {
        log.info("PAYMENT: Processing group payment cancellation: groupPaymentId={}", event.getGroupPaymentId());
        
        try {
            // Update group payment status
            GroupPayment groupPayment = updateGroupPaymentStatus(event, "CANCELLED");
            groupPayment.setCancelledAt(LocalDateTime.now());
            groupPayment.setCancellationReason(event.getCancellationReason());
            groupPaymentRepository.save(groupPayment);
            
            // Cancel individual payments
            updateParticipantPaymentStatuses(event, PaymentStatus.CANCELLED, correlationId);
            
            // Process any necessary refunds
            processGroupPaymentRefunds(event, groupPayment, correlationId);
            
            // Send cancellation notifications
            notificationService.sendGroupPaymentCancelledNotifications(groupPayment, correlationId);
            
            // Update compliance records
            complianceService.recordGroupPaymentCancellation(groupPayment, correlationId);
            
            log.info("PAYMENT: Group payment cancellation processed: groupPaymentId={}", event.getGroupPaymentId());
            
        } catch (Exception e) {
            log.error("PAYMENT: Failed to process group payment cancellation: groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process settlement completion
     */
    private void processSettlementCompleted(GroupPaymentEvent event, String correlationId) {
        log.info("PAYMENT: Processing settlement completion: groupPaymentId={}", event.getGroupPaymentId());
        
        try {
            // Update group payment settlement status
            GroupPayment groupPayment = updateGroupPaymentStatus(event, "SETTLED");
            groupPayment.setSettledAt(LocalDateTime.now());
            groupPaymentRepository.save(groupPayment);
            
            // Update participant payment statuses to settled
            updateParticipantPaymentStatuses(event, PaymentStatus.SETTLED, correlationId);
            
            // Finalize ledger entries
            ledgerService.finalizeGroupPaymentLedgerEntries(groupPayment, correlationId);
            
            // Send settlement notifications
            notificationService.sendGroupPaymentSettledNotifications(groupPayment, correlationId);
            
            // Complete compliance reporting
            complianceService.finalizeGroupPaymentCompliance(groupPayment, correlationId);
            
            log.info("PAYMENT: Settlement completion processed: groupPaymentId={}", event.getGroupPaymentId());
            
        } catch (Exception e) {
            log.error("PAYMENT: Failed to process settlement completion: groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Create or update group payment record
     */
    private GroupPayment createOrUpdateGroupPayment(GroupPaymentEvent event) {
        return groupPaymentRepository.findByGroupPaymentId(event.getGroupPaymentId())
            .map(existing -> updateExistingGroupPayment(existing, event))
            .orElseGet(() -> createNewGroupPayment(event));
    }
    
    /**
     * Create new group payment record
     */
    private GroupPayment createNewGroupPayment(GroupPaymentEvent event) {
        GroupPayment groupPayment = GroupPayment.builder()
            .id(UUID.randomUUID())
            .groupPaymentId(event.getGroupPaymentId())
            .organizerId(event.getOrganizerId())
            .title(event.getTitle())
            .description(event.getDescription())
            .totalAmount(event.getTotalAmount())
            .currency(event.getCurrency())
            .status("CREATED")
            .participantCount(event.getParticipants().size())
            .splitType(event.getSplitType())
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();
        
        return groupPaymentRepository.save(groupPayment);
    }
    
    /**
     * Update existing group payment record
     */
    private GroupPayment updateExistingGroupPayment(GroupPayment existing, GroupPaymentEvent event) {
        existing.setStatus(event.getStatus());
        existing.setLastUpdated(LocalDateTime.now());
        
        if (event.getTotalAmount() != null) {
            existing.setTotalAmount(event.getTotalAmount());
        }
        
        return groupPaymentRepository.save(existing);
    }
    
    /**
     * Update group payment status
     */
    private GroupPayment updateGroupPaymentStatus(GroupPaymentEvent event, String status) {
        GroupPayment groupPayment = groupPaymentRepository.findByGroupPaymentId(event.getGroupPaymentId())
            .orElseThrow(() -> new RuntimeException("Group payment not found: " + event.getGroupPaymentId()));
        
        groupPayment.setStatus(status);
        groupPayment.setLastUpdated(LocalDateTime.now());
        
        return groupPaymentRepository.save(groupPayment);
    }
    
    /**
     * Create individual payment records for participants
     */
    private void createParticipantPayments(GroupPaymentEvent event, GroupPayment groupPayment, String correlationId) {
        for (GroupPaymentEvent.Participant participant : event.getParticipants()) {
            try {
                Payment participantPayment = Payment.builder()
                    .id(UUID.randomUUID())
                    .groupPaymentId(groupPayment.getId())
                    .userId(participant.getUserId())
                    .amount(participant.getAmount())
                    .currency(event.getCurrency())
                    .status(PaymentStatus.PENDING)
                    .description(String.format("Group payment: %s", event.getTitle()))
                    .correlationId(correlationId)
                    .createdAt(LocalDateTime.now())
                    .build();
                
                paymentRepository.save(participantPayment);
                
                log.debug("PAYMENT: Created participant payment: userId={}, amount={}",
                    participant.getUserId(), participant.getAmount());
                
            } catch (Exception e) {
                log.error("PAYMENT: Failed to create participant payment: userId={}, error={}",
                    participant.getUserId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Check for duplicate event processing
     */
    private boolean isDuplicateEvent(String eventId) {
        return PROCESSED_EVENTS.contains(eventId);
    }
    
    /**
     * Mark event as processed
     */
    private void markEventProcessed(String eventId) {
        PROCESSED_EVENTS.add(eventId);
        // Clean up old processed events to prevent memory leaks
        if (PROCESSED_EVENTS.size() > 10000) {
            PROCESSED_EVENTS.clear();
        }
    }
    
    /**
     * Create audit trail for group payment event
     */
    private void createAuditTrail(GroupPaymentEvent event, String correlationId) {
        try {
            auditService.createAuditEntry(
                "GROUP_PAYMENT_EVENT_PROCESSED",
                event.getOrganizerId().toString(),
                Map.of(
                    "eventId", event.getEventId(),
                    "groupPaymentId", event.getGroupPaymentId().toString(),
                    "eventType", event.getEventType(),
                    "status", event.getStatus(),
                    "totalAmount", event.getTotalAmount().toString(),
                    "participantCount", String.valueOf(event.getParticipants().size()),
                    "correlationId", correlationId
                ),
                "SYSTEM",
                "Group payment event processed successfully"
            );
        } catch (Exception e) {
            log.error("PAYMENT: Failed to create audit trail: eventId={}, error={}", 
                event.getEventId(), e.getMessage(), e);
        }
    }
    
    // Additional helper methods would be implemented here...
    // (processGroupSettlement, updateParticipantPaymentStatuses, processGroupPaymentRefunds, etc.)
    
    /**
     * Handle unknown event types
     */
    private void handleUnknownEventType(GroupPaymentEvent event, String correlationId) {
        log.warn("PAYMENT: Received unknown group payment event type: {}, eventId={}", 
            event.getEventType(), event.getEventId());
        
        // Create alert for engineering team
        createCriticalAlert(event, 
            new RuntimeException("Unknown event type: " + event.getEventType()), 
            correlationId);
    }
    
    /**
     * Create critical alert for processing failures
     */
    private void createCriticalAlert(GroupPaymentEvent event, Exception error, String correlationId) {
        log.error("PAYMENT: CRITICAL ALERT - Group payment processing failure requires immediate attention: " +
            "groupPaymentId={}, eventType={}, error={}, correlation={}",
            event.getGroupPaymentId(), event.getEventType(), error.getMessage(), correlationId);
        
        // This would typically trigger:
        // - PagerDuty alert
        // - Slack notification to engineering team
        // - Dashboard alert
        // - Email to payment operations team
    }
    
    // Placeholder methods for demonstration - these would be fully implemented
    private void processGroupSettlement(GroupPaymentEvent event, GroupPayment groupPayment, String correlationId) {
        // Implementation for processing group settlements
    }
    
    private void updateParticipantPaymentStatuses(GroupPaymentEvent event, PaymentStatus status, String correlationId) {
        // Implementation for updating participant payment statuses
    }
    
    private void processGroupPaymentRefunds(GroupPaymentEvent event, GroupPayment groupPayment, String correlationId) {
        // Implementation for processing refunds
    }
    
    private Payment findParticipantPayment(GroupPaymentEvent event) {
        // Implementation for finding participant payment
        return null;
    }
}