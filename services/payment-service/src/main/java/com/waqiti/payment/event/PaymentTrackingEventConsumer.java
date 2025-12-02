package com.waqiti.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.dto.PaymentTrackingEvent;
import com.waqiti.payment.service.PaymentTrackingService;
import com.waqiti.common.exception.EventProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka consumer for payment tracking events.
 * Processes payment status updates, delivery confirmations, and tracking information.
 * Implements retry logic, error handling, and dead letter queue processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTrackingEventConsumer {

    private final PaymentTrackingService paymentTrackingService;
    private final ObjectMapper objectMapper;
    
    /**
     * Main consumer for payment-tracking events
     */
    @KafkaListener(
        topics = "payment-tracking",
        groupId = "payment-tracking-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {EventProcessingException.class}
    )
    @Transactional
    public void handlePaymentTrackingEvent(
            @Payload @Valid PaymentTrackingEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        log.info("Processing payment tracking event: paymentId={}, status={}, topic={}, partition={}, offset={}", 
            event.getPaymentId(), event.getTrackingStatus(), topic, partition, offset);
        
        try {
            // Validate event
            if (event.getPaymentId() == null) {
                throw new EventProcessingException("Payment ID is required in tracking event");
            }
            
            // Process the tracking event
            processTrackingEvent(event, key);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed payment tracking event for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error processing payment tracking event for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            
            // Don't acknowledge - let retry mechanism handle it
            throw new EventProcessingException("Failed to process payment tracking event", e);
        }
    }
    
    /**
     * Consumer for failed payment tracking events (DLT)
     */
    @KafkaListener(
        topics = "payment-tracking-dlt",
        groupId = "payment-tracking-dlt-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFailedPaymentTrackingEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        log.error("Processing failed payment tracking event from DLT: topic={}, exception={}", 
            topic, exceptionMessage);
        
        try {
            PaymentTrackingEvent event = objectMapper.readValue(eventJson, PaymentTrackingEvent.class);
            
            // Mark payment as tracking failed
            paymentTrackingService.markTrackingFailed(
                event.getPaymentId(),
                "DLT processing: " + exceptionMessage
            );
            
            // Send alert to operations team
            sendTrackingFailureAlert(event, exceptionMessage);
            
            acknowledgment.acknowledge();
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse DLT payment tracking event: {}", e.getMessage());
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite loop
        } catch (Exception e) {
            log.error("Failed to process DLT payment tracking event: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite loop
        }
    }
    
    /**
     * Consumer for payment tracking status updates
     */
    @KafkaListener(
        topics = "payment-tracking-status",
        groupId = "payment-tracking-status-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentTrackingStatusEvent(
            @Payload @Valid PaymentTrackingEvent event,
            Acknowledgment acknowledgment) {
        
        log.info("Processing payment tracking status update: paymentId={}, status={}", 
            event.getPaymentId(), event.getTrackingStatus());
        
        try {
            // Update tracking status
            paymentTrackingService.updateTrackingStatus(event);
            
            // If status is delivered, update payment status
            if ("DELIVERED".equals(event.getTrackingStatus())) {
                paymentTrackingService.markPaymentDelivered(event.getPaymentId());
                
                // Send delivery confirmation
                sendDeliveryConfirmation(event);
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing payment tracking status event: {}", e.getMessage(), e);
            throw new EventProcessingException("Failed to process tracking status event", e);
        }
    }
    
    /**
     * Consumer for payment receipt events
     */
    @KafkaListener(
        topics = "payment-receipt",
        groupId = "payment-receipt-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentReceiptEvent(
            @Payload String receiptData,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String paymentId,
            Acknowledgment acknowledgment) {
        
        log.info("Processing payment receipt event for payment: {}", paymentId);
        
        try {
            // Process receipt data
            paymentTrackingService.processPaymentReceipt(paymentId, receiptData);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing payment receipt event for payment {}: {}", 
                paymentId, e.getMessage(), e);
            throw new EventProcessingException("Failed to process payment receipt", e);
        }
    }
    
    /**
     * Async method to process tracking events
     */
    private void processTrackingEvent(PaymentTrackingEvent event, String messageKey) {
        CompletableFuture.runAsync(() -> {
            try {
                // Update payment tracking information
                paymentTrackingService.updatePaymentTracking(
                    event.getPaymentId(),
                    event.getTrackingStatus(),
                    event.getTrackingDetails(),
                    event.getProviderTrackingId(),
                    event.getEstimatedDelivery()
                );
                
                // Process any additional tracking metadata
                if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
                    paymentTrackingService.updateTrackingMetadata(
                        event.getPaymentId(),
                        event.getMetadata()
                    );
                }
                
                // Update delivery status if applicable
                if (event.getDeliveryStatus() != null) {
                    paymentTrackingService.updateDeliveryStatus(
                        event.getPaymentId(),
                        event.getDeliveryStatus()
                    );
                }
                
            } catch (Exception e) {
                log.error("Async processing failed for payment tracking event: {}", e.getMessage(), e);
                // Mark for manual review
                paymentTrackingService.markForManualReview(event.getPaymentId(), e.getMessage());
            }
        });
    }
    
    /**
     * Send delivery confirmation to relevant parties
     */
    private void sendDeliveryConfirmation(PaymentTrackingEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                paymentTrackingService.sendDeliveryNotification(
                    event.getPaymentId(),
                    event.getTrackingDetails()
                );
            } catch (Exception e) {
                log.error("Failed to send delivery confirmation for payment {}: {}", 
                    event.getPaymentId(), e.getMessage());
            }
        });
    }
    
    /**
     * Send alert when tracking fails
     */
    private void sendTrackingFailureAlert(PaymentTrackingEvent event, String errorMessage) {
        CompletableFuture.runAsync(() -> {
            try {
                paymentTrackingService.sendTrackingFailureAlert(
                    event.getPaymentId(),
                    errorMessage,
                    LocalDateTime.now()
                );
            } catch (Exception e) {
                log.error("Failed to send tracking failure alert for payment {}: {}", 
                    event.getPaymentId(), e.getMessage());
            }
        });
    }
}