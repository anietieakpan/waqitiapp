package com.waqiti.payment.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.payment.PaymentTrackingEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentTracking;
import com.waqiti.payment.domain.TrackingStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentTrackingRepository;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.payment.service.RealTimeAnalyticsService;
import com.waqiti.common.exceptions.PaymentNotFoundException;
import com.waqiti.common.exceptions.TrackingProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

/**
 * Production-grade consumer for payment tracking events.
 * Maintains real-time payment status tracking with:
 * - Complete payment lifecycle visibility
 * - Real-time customer notifications
 * - Analytics data collection
 * - SLA monitoring and alerting
 * - Cross-provider tracking correlation
 * 
 * Critical for customer experience and operational visibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTrackingConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentTrackingRepository trackingRepository;
    private final PaymentNotificationService notificationService;
    private final RealTimeAnalyticsService analyticsService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    @KafkaListener(
        topics = "payment-tracking",
        groupId = "payment-service-tracking-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0),
        include = {TrackingProcessingException.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentTracking(
            @Payload PaymentTrackingEvent trackingEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        String eventId = trackingEvent.getEventId() != null ? 
            trackingEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing payment tracking event: {} for payment: {} with status: {}", 
                    eventId, trackingEvent.getPaymentId(), trackingEvent.getStatus());

            // Metrics tracking
            metricsService.incrementCounter("payment.tracking.processing.started",
                Map.of("status", trackingEvent.getStatus()));

            // Idempotency check
            if (isTrackingEventProcessed(trackingEvent.getPaymentId(), eventId)) {
                log.info("Tracking event {} already processed for payment {}", 
                        eventId, trackingEvent.getPaymentId());
                acknowledgment.acknowledge();
                return;
            }

            // Retrieve and validate payment
            Payment payment = getAndValidatePayment(trackingEvent.getPaymentId());

            // Create tracking record
            PaymentTracking tracking = createTrackingRecord(trackingEvent, payment, eventId);

            // Update payment status if needed
            updatePaymentStatusIfRequired(payment, trackingEvent);

            // Save tracking data
            PaymentTracking savedTracking = trackingRepository.save(tracking);

            // Send real-time notifications
            sendCustomerNotifications(savedTracking, payment);

            // Update analytics
            updateAnalytics(savedTracking, payment);

            // Monitor SLAs
            monitorSLAs(savedTracking, payment);

            // Create audit trail
            createTrackingAuditLog(savedTracking, payment, correlationId);

            // Success metrics
            metricsService.incrementCounter("payment.tracking.processing.success",
                Map.of("status", trackingEvent.getStatus()));

            log.info("Successfully processed payment tracking: {} for payment: {}", 
                    savedTracking.getId(), payment.getId());

            acknowledgment.acknowledge();

        } catch (PaymentNotFoundException e) {
            log.error("Payment not found for tracking event {}: {}", eventId, e.getMessage());
            metricsService.incrementCounter("payment.tracking.payment_not_found");
            acknowledgment.acknowledge(); // Don't retry for missing payments
            
        } catch (Exception e) {
            log.error("Error processing tracking event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("payment.tracking.processing.error");
            throw new TrackingProcessingException("Failed to process tracking event: " + e.getMessage(), e);
        }
    }

    private boolean isTrackingEventProcessed(String paymentId, String eventId) {
        return trackingRepository.existsByPaymentIdAndEventId(paymentId, eventId);
    }

    private Payment getAndValidatePayment(String paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            throw new PaymentNotFoundException("Payment not found: " + paymentId);
        }
        return paymentOpt.get();
    }

    private PaymentTracking createTrackingRecord(PaymentTrackingEvent event, Payment payment, String eventId) {
        return PaymentTracking.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventId(eventId)
            .status(TrackingStatus.valueOf(event.getStatus().toUpperCase()))
            .statusDescription(event.getStatusDescription())
            .providerReference(event.getProviderReference())
            .providerId(event.getProviderId())
            .timestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
            .estimatedCompletionTime(event.getEstimatedCompletionTime())
            .actualCompletionTime(isCompletedStatus(event.getStatus()) ? LocalDateTime.now() : null)
            .location(event.getLocation())
            .additionalInfo(event.getAdditionalInfo())
            .slaStatus(calculateSLAStatus(event, payment))
            .processingTimeMillis(calculateProcessingTime(event, payment))
            .customerNotified(false)
            .internalNotified(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void updatePaymentStatusIfRequired(Payment payment, PaymentTrackingEvent event) {
        // Update payment status based on tracking status
        String currentStatus = payment.getStatus();
        String newStatus = mapTrackingStatusToPaymentStatus(event.getStatus());
        
        if (!currentStatus.equals(newStatus) && isValidStatusTransition(currentStatus, newStatus)) {
            payment.setStatus(newStatus);
            payment.setLastUpdated(LocalDateTime.now());
            
            if (isCompletedStatus(newStatus)) {
                payment.setCompletedAt(LocalDateTime.now());
            }
            
            paymentRepository.save(payment);
            
            log.info("Updated payment {} status from {} to {}", 
                    payment.getId(), currentStatus, newStatus);
        }
    }

    private void sendCustomerNotifications(PaymentTracking tracking, Payment payment) {
        try {
            // Send notification for significant status changes
            if (shouldNotifyCustomer(tracking.getStatus())) {
                notificationService.sendPaymentStatusNotification(payment, tracking);
                tracking.setCustomerNotified(true);
            }
            
            // Send merchant notifications
            if (shouldNotifyMerchant(tracking.getStatus())) {
                notificationService.sendMerchantStatusNotification(payment, tracking);
                tracking.setInternalNotified(true);
            }
            
        } catch (Exception e) {
            log.error("Failed to send notifications for tracking {}: {}", tracking.getId(), e.getMessage());
            // Don't fail the entire process for notification issues
        }
    }

    private void updateAnalytics(PaymentTracking tracking, Payment payment) {
        try {
            analyticsService.recordPaymentTracking(payment, tracking);
            
            // Record processing time metrics
            if (tracking.getProcessingTimeMillis() != null) {
                metricsService.recordTimer("payment.processing.duration", 
                    tracking.getProcessingTimeMillis(),
                    Map.of(
                        "status", tracking.getStatus().toString(),
                        "provider", tracking.getProviderId(),
                        "amount_range", categorizeAmount(payment.getAmount())
                    ));
            }
            
        } catch (Exception e) {
            log.error("Failed to update analytics for tracking {}: {}", tracking.getId(), e.getMessage());
        }
    }

    private void monitorSLAs(PaymentTracking tracking, Payment payment) {
        try {
            // Check if payment is exceeding SLA thresholds
            if (isSLABreach(tracking, payment)) {
                // Send SLA breach alert
                notificationService.sendSLABreachAlert(payment, tracking);
                
                // Log SLA breach for monitoring
                auditLogger.logAlert("PAYMENT_SLA_BREACH", 
                    "Payment processing SLA exceeded",
                    Map.of(
                        "paymentId", payment.getId(),
                        "currentStatus", tracking.getStatus().toString(),
                        "processingTime", tracking.getProcessingTimeMillis().toString(),
                        "slaThreshold", getSLAThreshold(payment).toString()
                    ));
                
                metricsService.incrementCounter("payment.sla.breach",
                    Map.of("provider", tracking.getProviderId()));
            }
            
        } catch (Exception e) {
            log.error("Failed to monitor SLAs for tracking {}: {}", tracking.getId(), e.getMessage());
        }
    }

    private void createTrackingAuditLog(PaymentTracking tracking, Payment payment, String correlationId) {
        auditLogger.logPaymentEvent(
            "PAYMENT_TRACKING_UPDATE",
            "system",
            tracking.getId(),
            payment.getAmount().doubleValue(),
            payment.getCurrency(),
            "tracking_processor",
            true,
            Map.of(
                "paymentId", payment.getId(),
                "trackingStatus", tracking.getStatus().toString(),
                "providerId", tracking.getProviderId(),
                "providerReference", tracking.getProviderReference() != null ? tracking.getProviderReference() : "N/A",
                "processingTime", tracking.getProcessingTimeMillis() != null ? tracking.getProcessingTimeMillis().toString() : "N/A",
                "correlationId", correlationId != null ? correlationId : "N/A"
            )
        );
    }

    private boolean isCompletedStatus(String status) {
        return status.equalsIgnoreCase("COMPLETED") || 
               status.equalsIgnoreCase("SETTLED") || 
               status.equalsIgnoreCase("FAILED") ||
               status.equalsIgnoreCase("CANCELLED");
    }

    private String mapTrackingStatusToPaymentStatus(String trackingStatus) {
        return switch (trackingStatus.toUpperCase()) {
            case "INITIATED" -> "PENDING";
            case "PROCESSING" -> "PROCESSING";
            case "AUTHORIZED" -> "AUTHORIZED";
            case "CAPTURED" -> "CAPTURED";
            case "SETTLED" -> "COMPLETED";
            case "FAILED" -> "FAILED";
            case "CANCELLED" -> "CANCELLED";
            case "REFUNDED" -> "REFUNDED";
            default -> trackingStatus;
        };
    }

    private boolean isValidStatusTransition(String currentStatus, String newStatus) {
        // Define valid payment status transitions
        return switch (currentStatus) {
            case "PENDING" -> true; // Can transition to any status
            case "PROCESSING" -> !newStatus.equals("PENDING");
            case "AUTHORIZED" -> newStatus.equals("CAPTURED") || newStatus.equals("CANCELLED") || newStatus.equals("FAILED");
            case "CAPTURED" -> newStatus.equals("COMPLETED") || newStatus.equals("REFUNDED") || newStatus.equals("FAILED");
            case "COMPLETED" -> newStatus.equals("REFUNDED");
            case "FAILED", "CANCELLED", "REFUNDED" -> false; // Terminal states
            default -> false;
        };
    }

    private boolean shouldNotifyCustomer(TrackingStatus status) {
        return status == TrackingStatus.COMPLETED || 
               status == TrackingStatus.FAILED || 
               status == TrackingStatus.REFUNDED ||
               status == TrackingStatus.DELAYED;
    }

    private boolean shouldNotifyMerchant(TrackingStatus status) {
        return status == TrackingStatus.COMPLETED || 
               status == TrackingStatus.FAILED || 
               status == TrackingStatus.CHARGEBACK;
    }

    private String calculateSLAStatus(PaymentTrackingEvent event, Payment payment) {
        Long processingTime = calculateProcessingTime(event, payment);
        Long slaThreshold = getSLAThreshold(payment);
        
        if (processingTime == null || slaThreshold == null) {
            return "UNKNOWN";
        }
        
        if (processingTime > slaThreshold) {
            return "BREACHED";
        } else if (processingTime > slaThreshold * 0.8) {
            return "AT_RISK";
        } else {
            return "ON_TIME";
        }
    }

    private Long calculateProcessingTime(PaymentTrackingEvent event, Payment payment) {
        if (payment.getCreatedAt() != null) {
            LocalDateTime eventTime = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();
            return java.time.Duration.between(payment.getCreatedAt(), eventTime).toMillis();
        }
        return null;
    }

    private Long getSLAThreshold(Payment payment) {
        // SLA thresholds based on payment type and amount
        if (payment.getPaymentType().equals("INSTANT")) {
            return 30_000L; // 30 seconds
        } else if (payment.getPaymentType().equals("STANDARD")) {
            return 300_000L; // 5 minutes
        } else {
            return 1_800_000L; // 30 minutes
        }
    }

    private boolean isSLABreach(PaymentTracking tracking, Payment payment) {
        if (tracking.getProcessingTimeMillis() == null) {
            return false;
        }
        return tracking.getProcessingTimeMillis() > getSLAThreshold(payment);
    }

    private String categorizeAmount(java.math.BigDecimal amount) {
        if (amount.compareTo(new java.math.BigDecimal("10000")) > 0) {
            return "high";
        } else if (amount.compareTo(new java.math.BigDecimal("1000")) > 0) {
            return "medium";
        } else {
            return "low";
        }
    }
}