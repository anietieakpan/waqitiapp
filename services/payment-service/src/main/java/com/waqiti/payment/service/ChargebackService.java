package com.waqiti.payment.service;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.ChargebackReason;
import com.waqiti.payment.domain.ChargebackStatus;
import com.waqiti.payment.entity.Chargeback;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.ChargebackRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for initiating and managing payment chargebacks.
 * This service produces chargeback events that are consumed by PaymentChargebackConsumer.
 * Implements comprehensive chargeback management with proper event publishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargebackService {

    private final PaymentRepository paymentRepository;
    private final ChargebackRepository chargebackRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    private static final String CHARGEBACK_TOPIC = "payment-chargeback-events";
    private static final String CHARGEBACK_NOTIFICATION_TOPIC = "chargeback-notifications";
    private static final String CHARGEBACK_AUDIT_TOPIC = "chargeback-audit-events";

    /**
     * Initiate a chargeback for a payment.
     * This is the main entry point that produces chargeback events.
     */
    @Transactional
    public CompletableFuture<Chargeback> initiateChargeback(String paymentId, ChargebackReason reason, 
                                                            BigDecimal amount, String initiatedBy) {
        log.info("Initiating chargeback for payment: {}, reason: {}, amount: {}", paymentId, reason, amount);
        
        try {
            // Validate payment exists and is eligible for chargeback
            Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
            
            validateChargebackEligibility(payment, amount);
            
            // Create chargeback record
            Chargeback chargeback = createChargebackRecord(payment, reason, amount, initiatedBy);
            
            // Save chargeback to database
            chargeback = chargebackRepository.save(chargeback);
            
            // Create chargeback event
            PaymentChargebackEvent event = buildChargebackEvent(chargeback, payment);
            
            // Publish event to Kafka (this is what was missing!)
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(CHARGEBACK_TOPIC, payment.getPaymentId(), event);
            
            final Chargeback finalChargeback = chargeback;
            
            return future.thenApply(result -> {
                log.info("Chargeback event published successfully for payment: {}, offset: {}", 
                    paymentId, result.getRecordMetadata().offset());
                
                // Update metrics
                meterRegistry.counter("chargeback.initiated", "reason", reason.name()).increment();
                
                // Process immediate chargeback actions
                processChargebackActions(finalChargeback, payment);
                
                // Send notifications
                sendChargebackNotifications(finalChargeback, payment);
                
                // Audit the chargeback
                auditChargeback(finalChargeback, payment);
                
                return finalChargeback;
            }).exceptionally(ex -> {
                log.error("Failed to publish chargeback event for payment: {}", paymentId, ex);
                
                // Update chargeback status to failed
                finalChargeback.setStatus(ChargebackStatus.FAILED);
                finalChargeback.setFailureReason(ex.getMessage());
                chargebackRepository.save(finalChargeback);
                
                throw new RuntimeException("Failed to initiate chargeback", ex);
            });
            
        } catch (Exception e) {
            log.error("Error initiating chargeback for payment: {}", paymentId, e);
            meterRegistry.counter("chargeback.failed", "reason", reason.name()).increment();
            throw new RuntimeException("Chargeback initiation failed", e);
        }
    }

    /**
     * Process a chargeback received from external card networks.
     */
    @Transactional
    public void processExternalChargeback(String networkChargebackId, String paymentId, 
                                         ChargebackReason reason, BigDecimal amount,
                                         String cardNetwork, Map<String, Object> networkData) {
        log.info("Processing external chargeback from {}: networkId={}, paymentId={}", 
            cardNetwork, networkChargebackId, paymentId);
        
        try {
            // Check if chargeback already exists
            if (chargebackRepository.existsByNetworkChargebackId(networkChargebackId)) {
                log.warn("Duplicate chargeback received: {}", networkChargebackId);
                return;
            }
            
            Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
            
            // Create chargeback from network data
            Chargeback chargeback = Chargeback.builder()
                .chargebackId(UUID.randomUUID().toString())
                .networkChargebackId(networkChargebackId)
                .paymentId(paymentId)
                .originalAmount(payment.getAmount())
                .chargebackAmount(amount)
                .reason(reason)
                .status(ChargebackStatus.PENDING)
                .cardNetwork(cardNetwork)
                .networkData(networkData)
                .initiatedAt(Instant.now())
                .initiatedBy(cardNetwork)
                .build();
            
            chargeback = chargebackRepository.save(chargeback);
            
            // Create and publish event
            PaymentChargebackEvent event = buildChargebackEvent(chargeback, payment);
            kafkaTemplate.send(CHARGEBACK_TOPIC, payment.getPaymentId(), event);
            
            log.info("External chargeback processed and event published: {}", networkChargebackId);
            
        } catch (Exception e) {
            log.error("Failed to process external chargeback: {}", networkChargebackId, e);
            throw new RuntimeException("External chargeback processing failed", e);
        }
    }

    /**
     * Challenge a chargeback with evidence.
     */
    @Transactional
    public CompletableFuture<Chargeback> challengeChargeback(String chargebackId, 
                                                             Map<String, Object> evidence,
                                                             String challengedBy) {
        log.info("Challenging chargeback: {} with evidence", chargebackId);
        
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
            .orElseThrow(() -> new IllegalArgumentException("Chargeback not found: " + chargebackId));
        
        if (!chargeback.getStatus().canChallenge()) {
            throw new IllegalStateException("Chargeback cannot be challenged in status: " + chargeback.getStatus());
        }
        
        // Update chargeback with challenge
        chargeback.setStatus(ChargebackStatus.CHALLENGED);
        chargeback.setChallengedAt(Instant.now());
        chargeback.setChallengedBy(challengedBy);
        chargeback.setChallengeEvidence(evidence);
        
        chargeback = chargebackRepository.save(chargeback);
        
        // Create challenge event
        Map<String, Object> challengeEvent = new HashMap<>();
        challengeEvent.put("chargebackId", chargebackId);
        challengeEvent.put("paymentId", chargeback.getPaymentId());
        challengeEvent.put("challengedAt", chargeback.getChallengedAt());
        challengeEvent.put("evidence", evidence);
        challengeEvent.put("action", "CHALLENGE");
        
        // Publish challenge event
        return CompletableFuture.supplyAsync(() -> {
            kafkaTemplate.send(CHARGEBACK_TOPIC, chargeback.getPaymentId(), challengeEvent);
            
            // Notify relevant parties
            notificationService.sendChargebackChallengeNotification(chargeback);
            
            meterRegistry.counter("chargeback.challenged").increment();
            
            return chargeback;
        });
    }

    /**
     * Accept a chargeback and process refund.
     */
    @Transactional
    public Chargeback acceptChargeback(String chargebackId, String acceptedBy) {
        log.info("Accepting chargeback: {}", chargebackId);
        
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
            .orElseThrow(() -> new IllegalArgumentException("Chargeback not found: " + chargebackId));
        
        Payment payment = paymentRepository.findById(chargeback.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + chargeback.getPaymentId()));
        
        // Update chargeback status
        chargeback.setStatus(ChargebackStatus.ACCEPTED);
        chargeback.setResolvedAt(Instant.now());
        chargeback.setResolvedBy(acceptedBy);
        
        chargeback = chargebackRepository.save(chargeback);
        
        // Process refund
        processChargebackRefund(chargeback, payment);
        
        // Create acceptance event
        Map<String, Object> acceptanceEvent = new HashMap<>();
        acceptanceEvent.put("chargebackId", chargebackId);
        acceptanceEvent.put("paymentId", chargeback.getPaymentId());
        acceptanceEvent.put("acceptedAt", chargeback.getResolvedAt());
        acceptanceEvent.put("amount", chargeback.getChargebackAmount());
        acceptanceEvent.put("action", "ACCEPT");
        
        // Publish acceptance event
        kafkaTemplate.send(CHARGEBACK_TOPIC, payment.getPaymentId(), acceptanceEvent);
        
        meterRegistry.counter("chargeback.accepted").increment();
        
        return chargeback;
    }

    /**
     * Handle chargeback resolution from card network.
     */
    @Transactional
    public void resolveChargeback(String chargebackId, boolean wonChallenge, String resolutionDetails) {
        log.info("Resolving chargeback: {}, won: {}", chargebackId, wonChallenge);
        
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
            .orElseThrow(() -> new IllegalArgumentException("Chargeback not found: " + chargebackId));
        
        ChargebackStatus newStatus = wonChallenge ? ChargebackStatus.WON : ChargebackStatus.LOST;
        chargeback.setStatus(newStatus);
        chargeback.setResolvedAt(Instant.now());
        chargeback.setResolutionDetails(resolutionDetails);
        
        chargeback = chargebackRepository.save(chargeback);
        
        // Create resolution event
        Map<String, Object> resolutionEvent = new HashMap<>();
        resolutionEvent.put("chargebackId", chargebackId);
        resolutionEvent.put("paymentId", chargeback.getPaymentId());
        resolutionEvent.put("resolvedAt", chargeback.getResolvedAt());
        resolutionEvent.put("status", newStatus);
        resolutionEvent.put("resolutionDetails", resolutionDetails);
        resolutionEvent.put("action", "RESOLVE");
        
        // Publish resolution event
        kafkaTemplate.send(CHARGEBACK_TOPIC, chargeback.getPaymentId(), resolutionEvent);
        
        // Process based on resolution
        if (!wonChallenge) {
            Payment payment = paymentRepository.findById(chargeback.getPaymentId()).orElse(null);
            if (payment != null) {
                processChargebackRefund(chargeback, payment);
            }
        }
        
        // Send resolution notification
        notificationService.sendChargebackResolutionNotification(chargeback, wonChallenge);
        
        meterRegistry.counter("chargeback.resolved", "won", String.valueOf(wonChallenge)).increment();
    }

    // Private helper methods

    private void validateChargebackEligibility(Payment payment, BigDecimal amount) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Payment already refunded");
        }
        
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Payment is cancelled");
        }
        
        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException("Chargeback amount exceeds payment amount");
        }
        
        // Check if payment is within chargeback timeframe (typically 120 days)
        Instant chargebackDeadline = payment.getCreatedAt().plusSeconds(120 * 24 * 60 * 60);
        if (Instant.now().isAfter(chargebackDeadline)) {
            throw new IllegalStateException("Payment is outside chargeback timeframe");
        }
    }

    private Chargeback createChargebackRecord(Payment payment, ChargebackReason reason, 
                                             BigDecimal amount, String initiatedBy) {
        return Chargeback.builder()
            .chargebackId(UUID.randomUUID().toString())
            .paymentId(payment.getPaymentId())
            .merchantId(payment.getMerchantId())
            .userId(payment.getUserId())
            .originalAmount(payment.getAmount())
            .chargebackAmount(amount)
            .currency(payment.getCurrency())
            .reason(reason)
            .status(ChargebackStatus.PENDING)
            .initiatedAt(Instant.now())
            .initiatedBy(initiatedBy)
            .build();
    }

    private PaymentChargebackEvent buildChargebackEvent(Chargeback chargeback, Payment payment) {
        return PaymentChargebackEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .chargebackId(chargeback.getChargebackId())
            .paymentId(payment.getPaymentId())
            .merchantId(payment.getMerchantId())
            .userId(payment.getUserId())
            .originalAmount(payment.getAmount())
            .chargebackAmount(chargeback.getChargebackAmount())
            .currency(payment.getCurrency())
            .reason(chargeback.getReason())
            .status(chargeback.getStatus())
            .initiatedAt(chargeback.getInitiatedAt())
            .timestamp(Instant.now())
            .metadata(buildEventMetadata(chargeback, payment))
            .build();
    }

    private Map<String, Object> buildEventMetadata(Chargeback chargeback, Payment payment) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("paymentMethod", payment.getPaymentMethod());
        metadata.put("cardLast4", payment.getCardLast4());
        metadata.put("originalTransactionDate", payment.getCreatedAt());
        metadata.put("chargebackDeadline", payment.getCreatedAt().plusSeconds(120 * 24 * 60 * 60));
        metadata.put("merchantName", payment.getMerchantName());
        return metadata;
    }

    @Transactional(rollbackFor = Exception.class)
    private void processChargebackActions(Chargeback chargeback, Payment payment) {
        try {
            // Freeze the disputed amount in merchant account
            walletService.freezeAmount(payment.getMerchantId(), chargeback.getChargebackAmount(), 
                "Chargeback: " + chargeback.getChargebackId());
            
            // Update payment status
            payment.setStatus(PaymentStatus.CHARGEBACK_PENDING);
            payment.setChargebackId(chargeback.getChargebackId());
            paymentRepository.save(payment);
            
            log.info("Chargeback actions processed for payment: {}", payment.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error processing chargeback actions: {}", chargeback.getChargebackId(), e);
            throw e; // Re-throw to trigger rollback
        }
    }

    @Transactional(rollbackFor = Exception.class)
    private void processChargebackRefund(Chargeback chargeback, Payment payment) {
        try {
            // Debit merchant account
            walletService.debitWallet(payment.getMerchantId(), chargeback.getChargebackAmount(), 
                "Chargeback loss: " + chargeback.getChargebackId());
            
            // Credit user account
            walletService.creditWallet(payment.getUserId(), chargeback.getChargebackAmount(), 
                "Chargeback refund: " + chargeback.getChargebackId());
            
            // Update payment status
            payment.setStatus(PaymentStatus.CHARGEBACK_REFUNDED);
            paymentRepository.save(payment);
            
            log.info("Chargeback refund processed: {}", chargeback.getChargebackId());
            
        } catch (Exception e) {
            log.error("Error processing chargeback refund: {}", chargeback.getChargebackId(), e);
            throw new RuntimeException("Chargeback refund failed", e);
        }
    }

    private void sendChargebackNotifications(Chargeback chargeback, Payment payment) {
        try {
            // Notify merchant
            Map<String, Object> merchantNotification = new HashMap<>();
            merchantNotification.put("type", "CHARGEBACK_INITIATED");
            merchantNotification.put("merchantId", payment.getMerchantId());
            merchantNotification.put("chargebackId", chargeback.getChargebackId());
            merchantNotification.put("amount", chargeback.getChargebackAmount());
            merchantNotification.put("reason", chargeback.getReason());
            
            kafkaTemplate.send(CHARGEBACK_NOTIFICATION_TOPIC, merchantNotification);
            
            // Notify user
            notificationService.sendChargebackInitiatedNotification(payment.getUserId(), chargeback);
            
        } catch (Exception e) {
            log.error("Error sending chargeback notifications: {}", chargeback.getChargebackId(), e);
        }
    }

    private void auditChargeback(Chargeback chargeback, Payment payment) {
        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "CHARGEBACK_INITIATED");
            auditEvent.put("chargebackId", chargeback.getChargebackId());
            auditEvent.put("paymentId", payment.getPaymentId());
            auditEvent.put("merchantId", payment.getMerchantId());
            auditEvent.put("userId", payment.getUserId());
            auditEvent.put("amount", chargeback.getChargebackAmount());
            auditEvent.put("reason", chargeback.getReason());
            auditEvent.put("timestamp", Instant.now());
            
            kafkaTemplate.send(CHARGEBACK_AUDIT_TOPIC, auditEvent);
            
            auditService.logChargebackEvent(chargeback);
            
        } catch (Exception e) {
            log.error("Error auditing chargeback: {}", chargeback.getChargebackId(), e);
        }
    }
}