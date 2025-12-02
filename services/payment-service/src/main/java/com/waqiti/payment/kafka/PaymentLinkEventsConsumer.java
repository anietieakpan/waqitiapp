package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentLinkEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentLink;
import com.waqiti.payment.domain.PaymentLinkStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentLinkRepository;
import com.waqiti.payment.service.PaymentLinkService;
import com.waqiti.payment.service.PaymentCollectionService;
import com.waqiti.payment.exception.PaymentLinkException;
import com.waqiti.payment.metrics.PaymentLinkMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

/**
 * CRITICAL Consumer for Payment Link Events
 * 
 * Handles payment link lifecycle including:
 * - Payment link generation and customization
 * - Link expiration and automatic renewal
 * - Payment collection through links
 * - Multi-currency link support
 * - Partial payment collection
 * - Link analytics and tracking
 * - Security and fraud prevention for links
 * - Invoice integration and billing
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentLinkEventsConsumer {
    
    private final PaymentLinkRepository linkRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentLinkService linkService;
    private final PaymentCollectionService collectionService;
    private final PaymentLinkMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Link configuration
    private static final Duration DEFAULT_LINK_EXPIRY = Duration.ofDays(30);
    private static final Duration QUICK_LINK_EXPIRY = Duration.ofHours(24);
    private static final Duration INVOICE_LINK_EXPIRY = Duration.ofDays(90);
    
    @KafkaListener(
        topics = "payment-link-events",
        groupId = "payment-link-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentLinkEvent(
            @Payload PaymentLinkEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("link-%s-p%d-o%d", 
            event.getLinkId(), partition, offset);
        
        log.info("Processing payment link event: linkId={}, action={}, correlation={}",
            event.getLinkId(), event.getAction(), correlationId);
        
        try {
            securityContext.validateFinancialOperation(event.getLinkId(), "PAYMENT_LINK");
            validateLinkEvent(event);
            
            switch (event.getAction()) {
                case CREATE_LINK:
                    processCreateLink(event, correlationId);
                    break;
                case LINK_ACCESSED:
                    processLinkAccessed(event, correlationId);
                    break;
                case PAYMENT_COLLECTED:
                    processPaymentCollected(event, correlationId);
                    break;
                case LINK_EXPIRED:
                    processLinkExpired(event, correlationId);
                    break;
                case PARTIAL_PAYMENT:
                    processPartialPayment(event, correlationId);
                    break;
                case LINK_DEACTIVATED:
                    processLinkDeactivated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown link action: {}", event.getAction());
                    break;
            }
            
            auditService.logFinancialEvent(
                "PAYMENT_LINK_EVENT_PROCESSED",
                event.getLinkId(),
                Map.of(
                    "action", event.getAction(),
                    "amount", event.getAmount() != null ? event.getAmount() : BigDecimal.ZERO,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process payment link event: linkId={}, error={}",
                event.getLinkId(), e.getMessage(), e);
            handleLinkEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processCreateLink(PaymentLinkEvent event, String correlationId) {
        log.info("Creating payment link: amount={}, description={}", 
            event.getAmount(), event.getDescription());
        
        PaymentLink link = PaymentLink.builder()
            .id(UUID.randomUUID().toString())
            .merchantId(event.getMerchantId())
            .customerId(event.getCustomerId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .description(event.getDescription())
            .status(PaymentLinkStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plus(DEFAULT_LINK_EXPIRY))
            .linkUrl(linkService.generateLinkUrl(event))
            .accessCount(0)
            .maxUsageCount(event.getMaxUsageCount() != null ? event.getMaxUsageCount() : 1)
            .allowPartialPayment(event.isAllowPartialPayment())
            .correlationId(correlationId)
            .build();
        
        linkRepository.save(link);
        
        // Send link to customer
        sendPaymentLinkNotification(link);
        
        metricsService.recordLinkCreated(event.getMerchantId(), event.getAmount());
        
        log.info("Payment link created: linkId={}, url={}", link.getId(), link.getLinkUrl());
    }
    
    private void processLinkAccessed(PaymentLinkEvent event, String correlationId) {
        PaymentLink link = getLinkById(event.getLinkId());
        
        link.setAccessCount(link.getAccessCount() + 1);
        link.setLastAccessedAt(LocalDateTime.now());
        linkRepository.save(link);
        
        // Track analytics
        metricsService.recordLinkAccessed(link.getMerchantId(), event.getUserAgent());
        
        log.info("Payment link accessed: linkId={}, accessCount={}", 
            link.getId(), link.getAccessCount());
    }
    
    private void processPaymentCollected(PaymentLinkEvent event, String correlationId) {
        PaymentLink link = getLinkById(event.getLinkId());
        
        // Create payment record
        Payment payment = Payment.builder()
            .id(UUID.randomUUID().toString())
            .amount(event.getCollectedAmount())
            .currency(link.getCurrency())
            .merchantId(link.getMerchantId())
            .customerId(link.getCustomerId())
            .paymentLinkId(link.getId())
            .description("Payment via link: " + link.getDescription())
            .correlationId(correlationId)
            .build();
        
        paymentRepository.save(payment);
        
        // Update link
        link.setCollectedAmount(
            link.getCollectedAmount() != null ? 
            link.getCollectedAmount().add(event.getCollectedAmount()) : 
            event.getCollectedAmount()
        );
        link.setLastPaymentAt(LocalDateTime.now());
        
        // Check if fully paid
        if (link.getCollectedAmount().compareTo(link.getAmount()) >= 0) {
            link.setStatus(PaymentLinkStatus.COMPLETED);
            link.setCompletedAt(LocalDateTime.now());
        }
        
        linkRepository.save(link);
        
        // Send confirmation
        sendPaymentConfirmation(link, payment);
        
        metricsService.recordPaymentCollected(link.getMerchantId(), event.getCollectedAmount());
        
        log.info("Payment collected via link: linkId={}, amount={}, total={}", 
            link.getId(), event.getCollectedAmount(), link.getCollectedAmount());
    }
    
    private void processPartialPayment(PaymentLinkEvent event, String correlationId) {
        PaymentLink link = getLinkById(event.getLinkId());
        
        if (!link.isAllowPartialPayment()) {
            log.warn("Partial payment not allowed for link: {}", link.getId());
            return;
        }
        
        // Process as partial payment collection
        processPaymentCollected(event, correlationId);
        
        // Send partial payment notification
        sendPartialPaymentNotification(link, event.getCollectedAmount());
    }
    
    private void processLinkExpired(PaymentLinkEvent event, String correlationId) {
        PaymentLink link = getLinkById(event.getLinkId());
        
        link.setStatus(PaymentLinkStatus.EXPIRED);
        link.setExpiredAt(LocalDateTime.now());
        linkRepository.save(link);
        
        // Notify merchant of expiration
        sendLinkExpirationNotification(link);
        
        metricsService.recordLinkExpired(link.getMerchantId());
        
        log.info("Payment link expired: linkId={}", link.getId());
    }
    
    private void processLinkDeactivated(PaymentLinkEvent event, String correlationId) {
        PaymentLink link = getLinkById(event.getLinkId());
        
        link.setStatus(PaymentLinkStatus.DEACTIVATED);
        link.setDeactivatedAt(LocalDateTime.now());
        link.setDeactivationReason(event.getDeactivationReason());
        linkRepository.save(link);
        
        log.info("Payment link deactivated: linkId={}, reason={}", 
            link.getId(), event.getDeactivationReason());
    }
    
    @Scheduled(fixedRate = 3600000) // Every hour
    public void checkExpiredLinks() {
        log.debug("Checking for expired payment links...");
        
        List<PaymentLink> expiredLinks = linkRepository.findExpiredActiveLinks(LocalDateTime.now());
        
        for (PaymentLink link : expiredLinks) {
            try {
                PaymentLinkEvent expirationEvent = PaymentLinkEvent.builder()
                    .linkId(link.getId())
                    .action("LINK_EXPIRED")
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("payment-link-events", expirationEvent);
                
            } catch (Exception e) {
                log.error("Failed to process expired link: linkId={}, error={}",
                    link.getId(), e.getMessage());
            }
        }
        
        if (!expiredLinks.isEmpty()) {
            log.info("Processed {} expired payment links", expiredLinks.size());
        }
    }
    
    private PaymentLink getLinkById(String linkId) {
        return linkRepository.findById(linkId)
            .orElseThrow(() -> new PaymentLinkException("Payment link not found: " + linkId));
    }
    
    private void validateLinkEvent(PaymentLinkEvent event) {
        if (event.getLinkId() == null || event.getLinkId().trim().isEmpty()) {
            throw new IllegalArgumentException("Link ID is required");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private void sendPaymentLinkNotification(PaymentLink link) {
        if (link.getCustomerId() != null) {
            notificationService.sendCustomerNotification(
                link.getCustomerId(),
                "Payment Link Ready",
                String.format("Your payment link for $%s is ready: %s", 
                    link.getAmount(), link.getLinkUrl()),
                NotificationService.Priority.MEDIUM
            );
        }
    }
    
    private void sendPaymentConfirmation(PaymentLink link, Payment payment) {
        if (link.getCustomerId() != null) {
            notificationService.sendCustomerNotification(
                link.getCustomerId(),
                "Payment Confirmation",
                String.format("Your payment of $%s has been processed successfully.", 
                    payment.getAmount()),
                NotificationService.Priority.HIGH
            );
        }
    }
    
    private void sendPartialPaymentNotification(PaymentLink link, BigDecimal amount) {
        if (link.getCustomerId() != null) {
            BigDecimal remaining = link.getAmount().subtract(
                link.getCollectedAmount() != null ? link.getCollectedAmount() : BigDecimal.ZERO);
            
            notificationService.sendCustomerNotification(
                link.getCustomerId(),
                "Partial Payment Received",
                String.format("Partial payment of $%s received. Remaining: $%s", 
                    amount, remaining),
                NotificationService.Priority.MEDIUM
            );
        }
    }
    
    private void sendLinkExpirationNotification(PaymentLink link) {
        if (link.getMerchantId() != null) {
            notificationService.sendMerchantNotification(
                link.getMerchantId(),
                "Payment Link Expired",
                String.format("Payment link %s has expired without payment", link.getId()),
                NotificationService.Priority.LOW
            );
        }
    }
    
    private void handleLinkEventError(PaymentLinkEvent event, Exception error, String correlationId) {
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-link-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Payment Link Event Processing Failed",
            String.format("Failed to process payment link event: %s", error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementLinkEventError(event.getAction());
    }
}