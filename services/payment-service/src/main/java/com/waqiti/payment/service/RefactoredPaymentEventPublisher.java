package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment Event Publisher for RefactoredPaymentService
 * Publishes payment events to Kafka topics
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefactoredPaymentEventPublisher implements RefactoredPaymentService.PaymentEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    private static final String REFUND_EVENTS_TOPIC = "refund-events";
    
    @Override
    public void publishEvent(RefactoredPaymentService.PaymentEvent event) {
        try {
            log.info("Publishing payment event: type={}, transactionId={}", 
                event.getEventType(), event.getTransactionId());
            
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.getTransactionId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Payment event published successfully: offset={}", 
                            result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to publish payment event: {}", event.getTransactionId(), ex);
                    }
                });
                
        } catch (Exception e) {
            log.error("Error publishing payment event", e);
        }
    }
    
    public void publishPaymentInitiated(String transactionId, BigDecimal amount, String currency) {
        RefactoredPaymentService.PaymentEvent event = RefactoredPaymentService.PaymentEvent.builder()
            .eventType("PAYMENT_INITIATED")
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .status("PENDING")
            .timestamp(Instant.now())
            .build();
        
        publishEvent(event);
    }
    
    public void publishPaymentCompleted(String transactionId, BigDecimal amount, String currency) {
        RefactoredPaymentService.PaymentEvent event = RefactoredPaymentService.PaymentEvent.builder()
            .eventType("PAYMENT_COMPLETED")
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .status("COMPLETED")
            .timestamp(Instant.now())
            .build();
        
        publishEvent(event);
    }
    
    public void publishPaymentFailed(String transactionId, BigDecimal amount, String currency, String reason) {
        RefactoredPaymentService.PaymentEvent event = RefactoredPaymentService.PaymentEvent.builder()
            .eventType("PAYMENT_FAILED")
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .status("FAILED")
            .timestamp(Instant.now())
            .build();
        
        publishEvent(event);
    }
    
    public void publishRefundProcessed(String transactionId, BigDecimal amount, String reason) {
        try {
            log.info("Publishing refund event: transactionId={}, amount={}", transactionId, amount);
            
            RefactoredPaymentService.PaymentEvent event = RefactoredPaymentService.PaymentEvent.builder()
                .eventType("REFUND_PROCESSED")
                .transactionId(transactionId)
                .amount(amount)
                .status("REFUNDED")
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send(REFUND_EVENTS_TOPIC, transactionId, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Refund event published successfully");
                    } else {
                        log.error("Failed to publish refund event", ex);
                    }
                });
                
        } catch (Exception e) {
            log.error("Error publishing refund event", e);
        }
    }
}