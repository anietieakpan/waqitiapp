package com.waqiti.payment.kafka;

import com.waqiti.common.events.P2PPaymentEvent;
import com.waqiti.payment.domain.P2PPayment;
import com.waqiti.payment.repository.P2PPaymentRepository;
import com.waqiti.payment.service.P2PPaymentService;
import com.waqiti.payment.service.P2PRecipientService;
import com.waqiti.payment.service.InstantTransferService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class P2PPaymentEventConsumer {
    
    private final P2PPaymentRepository p2pRepository;
    private final P2PPaymentService p2pPaymentService;
    private final P2PRecipientService recipientService;
    private final InstantTransferService instantTransferService;
    private final FraudDetectionService fraudDetectionService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal INSTANT_TRANSFER_LIMIT = new BigDecimal("5000.00");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000.00");
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("10000.00");
    private static final int FRAUD_SCORE_THRESHOLD = 70;
    
    @KafkaListener(
        topics = {"p2p-payment-events", "instant-transfer-events", "scheduled-p2p-events"},
        groupId = "p2p-payment-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleP2PPaymentEvent(
            @Payload P2PPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("p2p-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing P2P payment: id={}, status={}, sender={}, recipient={}, amount={}",
            event.getPaymentId(), event.getStatus(), event.getSenderId(), 
            event.getRecipientIdentifier(), event.getAmount());
        
        try {
            switch (event.getStatus()) {
                case "P2P_INITIATED":
                    processP2PInitiated(event, correlationId);
                    break;
                    
                case "RECIPIENT_LOOKUP":
                    processRecipientLookup(event, correlationId);
                    break;
                    
                case "RECIPIENT_VERIFIED":
                    processRecipientVerified(event, correlationId);
                    break;
                    
                case "FRAUD_CHECK":
                    processFraudCheck(event, correlationId);
                    break;
                    
                case "INSTANT_PROCESSING":
                    processInstantTransfer(event, correlationId);
                    break;
                    
                case "STANDARD_PROCESSING":
                    processStandardTransfer(event, correlationId);
                    break;
                    
                case "SCHEDULED":
                    processScheduledPayment(event, correlationId);
                    break;
                    
                case "P2P_COMPLETED":
                    processP2PCompleted(event, correlationId);
                    break;
                    
                case "P2P_FAILED":
                    processP2PFailed(event, correlationId);
                    break;
                    
                case "RECIPIENT_INVITATION":
                    processRecipientInvitation(event, correlationId);
                    break;
                    
                case "PAYMENT_REQUEST":
                    processPaymentRequest(event, correlationId);
                    break;
                    
                case "PAYMENT_CANCELLED":
                    processPaymentCancelled(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown P2P status: {}", event.getStatus());
                    break;
            }
            
            auditService.logPaymentEvent("P2P_PAYMENT_PROCESSED", event.getPaymentId(),
                Map.of("status", event.getStatus(), "sender", event.getSenderId(),
                    "recipient", event.getRecipientIdentifier(), "amount", event.getAmount(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process P2P payment: {}", e.getMessage(), e);
            kafkaTemplate.send("p2p-payment-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processP2PInitiated(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = P2PPayment.builder()
            .paymentId(event.getPaymentId())
            .senderId(event.getSenderId())
            .recipientIdentifier(event.getRecipientIdentifier())
            .identifierType(event.getIdentifierType())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .paymentMethod(event.getPaymentMethod())
            .memo(event.getMemo())
            .urgency(event.getUrgency())
            .isPrivate(event.getIsPrivate())
            .status("P2P_INITIATED")
            .initiatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        p2pRepository.save(p2pPayment);
        
        BigDecimal dailyTotal = p2pPaymentService.getDailyP2PTotal(event.getSenderId());
        if (dailyTotal.add(event.getAmount()).compareTo(DAILY_LIMIT) > 0) {
            p2pPayment.setStatus("P2P_FAILED");
            p2pPayment.setFailureReason("DAILY_LIMIT_EXCEEDED");
            p2pPayment.setFailedAt(LocalDateTime.now());
            p2pRepository.save(p2pPayment);
            
            notificationService.sendNotification(event.getSenderId(), "P2P Payment Failed",
                String.format("Daily limit of %s %s exceeded", DAILY_LIMIT, event.getCurrency()),
                correlationId);
            return;
        }
        
        if ("INSTANT".equals(event.getUrgency()) && 
            event.getAmount().compareTo(INSTANT_TRANSFER_LIMIT) > 0) {
            p2pPayment.setStatus("P2P_FAILED");
            p2pPayment.setFailureReason("INSTANT_TRANSFER_LIMIT_EXCEEDED");
            p2pPayment.setFailedAt(LocalDateTime.now());
            p2pRepository.save(p2pPayment);
            
            notificationService.sendNotification(event.getSenderId(), "Instant Transfer Limit Exceeded",
                String.format("Instant transfers limited to %s %s. Use standard transfer.", 
                    INSTANT_TRANSFER_LIMIT, event.getCurrency()),
                correlationId);
            return;
        }
        
        kafkaTemplate.send("p2p-payment-events", Map.of(
            "paymentId", event.getPaymentId(),
            "status", "RECIPIENT_LOOKUP",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordP2PInitiated(event.getUrgency(), event.getAmount());
        
        log.info("P2P initiated: id={}, urgency={}, amount={}", 
            event.getPaymentId(), event.getUrgency(), event.getAmount());
    }
    
    private void processRecipientLookup(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("RECIPIENT_LOOKUP");
        p2pPayment.setRecipientLookupStartedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        Map<String, Object> recipientLookup = recipientService.lookupRecipient(
            event.getRecipientIdentifier(),
            event.getIdentifierType()
        );
        
        boolean found = (boolean) recipientLookup.get("found");
        String recipientId = (String) recipientLookup.get("recipientId");
        
        if (!found) {
            p2pPayment.setStatus("RECIPIENT_INVITATION");
            p2pRepository.save(p2pPayment);
            
            kafkaTemplate.send("p2p-payment-events", Map.of(
                "paymentId", event.getPaymentId(),
                "status", "RECIPIENT_INVITATION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            
            log.info("Recipient not found, initiating invitation: paymentId={}", event.getPaymentId());
            return;
        }
        
        p2pPayment.setRecipientId(recipientId);
        p2pPayment.setRecipientLookupCompletedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        kafkaTemplate.send("p2p-payment-events", Map.of(
            "paymentId", event.getPaymentId(),
            "status", "RECIPIENT_VERIFIED",
            "recipientId", recipientId,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Recipient lookup completed: paymentId={}, recipientId={}", 
            event.getPaymentId(), recipientId);
    }
    
    private void processRecipientVerified(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("RECIPIENT_VERIFIED");
        p2pPayment.setRecipientVerifiedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        boolean recipientActive = recipientService.isRecipientActive(p2pPayment.getRecipientId());
        if (!recipientActive) {
            p2pPayment.setStatus("P2P_FAILED");
            p2pPayment.setFailureReason("RECIPIENT_ACCOUNT_INACTIVE");
            p2pPayment.setFailedAt(LocalDateTime.now());
            p2pRepository.save(p2pPayment);
            
            notificationService.sendNotification(event.getSenderId(), "P2P Payment Failed",
                "Recipient account is inactive or closed",
                correlationId);
            return;
        }
        
        kafkaTemplate.send("p2p-payment-events", Map.of(
            "paymentId", event.getPaymentId(),
            "status", "FRAUD_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Recipient verified: paymentId={}, recipientId={}", 
            event.getPaymentId(), p2pPayment.getRecipientId());
    }
    
    private void processFraudCheck(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("FRAUD_CHECK");
        p2pPayment.setFraudCheckStartedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        Map<String, Object> fraudCheck = fraudDetectionService.assessP2PFraudRisk(
            event.getPaymentId(),
            event.getSenderId(),
            p2pPayment.getRecipientId(),
            event.getAmount(),
            event.getPaymentMethod(),
            event.getSenderDevice()
        );
        
        int fraudScore = (int) fraudCheck.get("fraudScore");
        String riskLevel = (String) fraudCheck.get("riskLevel");
        
        p2pPayment.setFraudScore(fraudScore);
        p2pPayment.setRiskLevel(riskLevel);
        p2pPayment.setFraudCheckCompletedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        if (fraudScore >= FRAUD_SCORE_THRESHOLD) {
            p2pPayment.setStatus("P2P_FAILED");
            p2pPayment.setFailureReason("FRAUD_DETECTED");
            p2pPayment.setFailedAt(LocalDateTime.now());
            p2pRepository.save(p2pPayment);
            
            kafkaTemplate.send("fraud-alerts", Map.of(
                "paymentId", event.getPaymentId(),
                "senderId", event.getSenderId(),
                "fraudScore", fraudScore,
                "alertType", "HIGH_FRAUD_SCORE_P2P",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            
            notificationService.sendNotification(event.getSenderId(), "P2P Payment Blocked",
                "Payment blocked for security reasons. Please contact support.",
                correlationId);
            return;
        }
        
        String nextStatus = "INSTANT".equals(event.getUrgency()) ? "INSTANT_PROCESSING" : 
                           "SCHEDULED".equals(event.getUrgency()) ? "SCHEDULED" : 
                           "STANDARD_PROCESSING";
        
        p2pPayment.setStatus(nextStatus);
        p2pRepository.save(p2pPayment);
        
        kafkaTemplate.send("p2p-payment-events", Map.of(
            "paymentId", event.getPaymentId(),
            "status", nextStatus,
            "fraudScore", fraudScore,
            "riskLevel", riskLevel,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Fraud check completed: paymentId={}, fraudScore={}, riskLevel={}, nextStatus={}", 
            event.getPaymentId(), fraudScore, riskLevel, nextStatus);
    }
    
    private void processInstantTransfer(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("INSTANT_PROCESSING");
        p2pPayment.setProcessingStartedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        Map<String, Object> balanceCheck = p2pPaymentService.checkSenderBalance(
            event.getSenderId(),
            event.getPaymentMethod(),
            event.getAmount()
        );
        
        boolean sufficient = (boolean) balanceCheck.get("sufficient");
        if (!sufficient) {
            p2pPayment.setStatus("P2P_FAILED");
            p2pPayment.setFailureReason("INSUFFICIENT_FUNDS");
            p2pPayment.setFailedAt(LocalDateTime.now());
            p2pRepository.save(p2pPayment);
            
            notificationService.sendNotification(event.getSenderId(), "P2P Payment Failed",
                "Insufficient funds for instant transfer",
                correlationId);
            return;
        }
        
        String transferReference = instantTransferService.processInstantTransfer(
            event.getPaymentId(),
            event.getSenderId(),
            p2pPayment.getRecipientId(),
            event.getAmount(),
            event.getCurrency()
        );
        
        p2pPayment.setTransferReference(transferReference);
        p2pPayment.setProcessingCompletedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        kafkaTemplate.send("p2p-payment-events", Map.of(
            "paymentId", event.getPaymentId(),
            "status", "P2P_COMPLETED",
            "transferReference", transferReference,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordInstantTransfer(event.getAmount());
        
        log.info("Instant transfer processed: paymentId={}, transferRef={}", 
            event.getPaymentId(), transferReference);
    }
    
    private void processStandardTransfer(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("STANDARD_PROCESSING");
        p2pPayment.setProcessingStartedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        String transferReference = p2pPaymentService.queueStandardTransfer(
            event.getPaymentId(),
            event.getSenderId(),
            p2pPayment.getRecipientId(),
            event.getAmount(),
            event.getCurrency()
        );
        
        p2pPayment.setTransferReference(transferReference);
        p2pPayment.setExpectedCompletionDate(LocalDateTime.now().plusDays(1));
        p2pRepository.save(p2pPayment);
        
        notificationService.sendNotification(event.getSenderId(), "P2P Payment Processing",
            String.format("Your payment of %s %s is being processed. Expected completion: %s", 
                event.getAmount(), event.getCurrency(), p2pPayment.getExpectedCompletionDate()),
            correlationId);
        
        log.info("Standard transfer queued: paymentId={}, transferRef={}, expectedCompletion={}", 
            event.getPaymentId(), transferReference, p2pPayment.getExpectedCompletionDate());
    }
    
    private void processScheduledPayment(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("SCHEDULED");
        p2pPayment.setScheduledAt(LocalDateTime.now());
        p2pPayment.setScheduledExecutionDate(event.getRequestedDate());
        p2pRepository.save(p2pPayment);
        
        p2pPaymentService.scheduleP2PPayment(
            event.getPaymentId(),
            event.getRequestedDate()
        );
        
        notificationService.sendNotification(event.getSenderId(), "P2P Payment Scheduled",
            String.format("Your payment of %s %s is scheduled for %s", 
                event.getAmount(), event.getCurrency(), event.getRequestedDate()),
            correlationId);
        
        notificationService.sendNotification(p2pPayment.getRecipientId(), "Scheduled Payment Notification",
            String.format("You will receive %s %s from %s on %s", 
                event.getAmount(), event.getCurrency(), event.getSenderName(), event.getRequestedDate()),
            correlationId);
        
        metricsService.recordScheduledP2P(event.getAmount());
        
        log.info("P2P payment scheduled: paymentId={}, executionDate={}", 
            event.getPaymentId(), event.getRequestedDate());
    }
    
    private void processP2PCompleted(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("P2P_COMPLETED");
        p2pPayment.setCompletedAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        notificationService.sendNotification(event.getSenderId(), "P2P Payment Sent",
            String.format("You sent %s %s to %s", 
                event.getAmount(), event.getCurrency(), 
                recipientService.getRecipientDisplayName(p2pPayment.getRecipientId())),
            correlationId);
        
        notificationService.sendNotification(p2pPayment.getRecipientId(), "P2P Payment Received",
            String.format("You received %s %s from %s%s", 
                event.getAmount(), event.getCurrency(), event.getSenderName(),
                event.getMemo() != null ? ". Memo: " + event.getMemo() : ""),
            correlationId);
        
        if (!event.getIsPrivate()) {
            p2pPaymentService.updateActivityFeed(
                event.getSenderId(),
                p2pPayment.getRecipientId(),
                event.getAmount(),
                event.getCurrency(),
                event.getMemo()
            );
        }
        
        kafkaTemplate.send("ledger-recorded-events", Map.of(
            "transactionId", event.getPaymentId(),
            "fromAccountId", event.getSenderId(),
            "toAccountId", p2pPayment.getRecipientId(),
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "transactionType", "P2P_TRANSFER",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordP2PCompleted(event.getUrgency(), event.getAmount());
        
        log.info("P2P payment completed: paymentId={}, amount={}", 
            event.getPaymentId(), event.getAmount());
    }
    
    private void processP2PFailed(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("P2P_FAILED");
        p2pPayment.setFailedAt(LocalDateTime.now());
        p2pPayment.setFailureReason(event.getFailureReason());
        p2pPayment.setErrorCode(event.getErrorCode());
        p2pRepository.save(p2pPayment);
        
        notificationService.sendNotification(event.getSenderId(), "P2P Payment Failed",
            String.format("Your payment of %s %s failed: %s", 
                event.getAmount(), event.getCurrency(), event.getFailureReason()),
            correlationId);
        
        metricsService.recordP2PFailed(event.getUrgency(), event.getFailureReason());
        
        log.error("P2P payment failed: paymentId={}, reason={}, errorCode={}", 
            event.getPaymentId(), event.getFailureReason(), event.getErrorCode());
    }
    
    private void processRecipientInvitation(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("RECIPIENT_INVITATION");
        p2pPayment.setInvitationSentAt(LocalDateTime.now());
        p2pRepository.save(p2pPayment);
        
        String invitationToken = recipientService.createRecipientInvitation(
            event.getRecipientIdentifier(),
            event.getIdentifierType(),
            event.getSenderId(),
            event.getAmount(),
            event.getCurrency()
        );
        
        p2pPayment.setInvitationToken(invitationToken);
        p2pPayment.setInvitationExpiresAt(LocalDateTime.now().plusDays(7));
        p2pRepository.save(p2pPayment);
        
        recipientService.sendInvitation(
            event.getRecipientIdentifier(),
            event.getIdentifierType(),
            event.getSenderName(),
            event.getAmount(),
            event.getCurrency(),
            invitationToken
        );
        
        notificationService.sendNotification(event.getSenderId(), "Invitation Sent",
            String.format("Invitation sent to %s. They have 7 days to accept and receive %s %s", 
                event.getRecipientIdentifier(), event.getAmount(), event.getCurrency()),
            correlationId);
        
        metricsService.recordRecipientInvitation();
        
        log.info("Recipient invitation sent: paymentId={}, identifier={}, invitationToken={}", 
            event.getPaymentId(), event.getRecipientIdentifier(), invitationToken);
    }
    
    private void processPaymentRequest(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = P2PPayment.builder()
            .paymentId(event.getPaymentId())
            .senderId(event.getRecipientIdentifier())
            .recipientId(event.getSenderId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .memo(event.getMemo())
            .isRequest(true)
            .status("PAYMENT_REQUEST")
            .requestedAt(LocalDateTime.now())
            .requestExpiresAt(LocalDateTime.now().plusDays(7))
            .correlationId(correlationId)
            .build();
        p2pRepository.save(p2pPayment);
        
        notificationService.sendNotification(event.getSenderId(), "Payment Request",
            String.format("%s is requesting %s %s%s", 
                event.getRecipientName(), event.getAmount(), event.getCurrency(),
                event.getMemo() != null ? " for: " + event.getMemo() : ""),
            correlationId);
        
        metricsService.recordPaymentRequest(event.getAmount());
        
        log.info("Payment request created: paymentId={}, requester={}, amount={}", 
            event.getPaymentId(), event.getRecipientIdentifier(), event.getAmount());
    }
    
    private void processPaymentCancelled(P2PPaymentEvent event, String correlationId) {
        P2PPayment p2pPayment = p2pRepository.findByPaymentId(event.getPaymentId())
            .orElseThrow(() -> new RuntimeException("P2P payment not found"));
        
        p2pPayment.setStatus("PAYMENT_CANCELLED");
        p2pPayment.setCancelledAt(LocalDateTime.now());
        p2pPayment.setCancellationReason(event.getCancellationReason());
        p2pRepository.save(p2pPayment);
        
        if (p2pPayment.getRecipientId() != null) {
            notificationService.sendNotification(p2pPayment.getRecipientId(), "Payment Cancelled",
                String.format("Payment of %s %s from %s was cancelled", 
                    event.getAmount(), event.getCurrency(), event.getSenderName()),
                correlationId);
        }
        
        notificationService.sendNotification(event.getSenderId(), "Payment Cancelled",
            String.format("Your payment of %s %s has been cancelled", 
                event.getAmount(), event.getCurrency()),
            correlationId);
        
        metricsService.recordP2PCancelled(event.getUrgency());
        
        log.info("P2P payment cancelled: paymentId={}, reason={}", 
            event.getPaymentId(), event.getCancellationReason());
    }
}