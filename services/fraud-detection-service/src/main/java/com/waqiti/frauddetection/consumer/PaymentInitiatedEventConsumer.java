package com.waqiti.frauddetection.consumer;

import com.waqiti.common.events.PaymentInitiatedEvent;
import com.waqiti.frauddetection.service.FraudAnalysisService;
import com.waqiti.frauddetection.service.RiskScoringService;
import com.waqiti.frauddetection.repository.ProcessedEventRepository;
import com.waqiti.frauddetection.model.ProcessedEvent;
import com.waqiti.frauddetection.model.FraudAnalysisResult;
import com.waqiti.common.events.FraudCheckCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer for PaymentInitiatedEvent - Critical for fraud detection
 * ZERO TOLERANCE: No placeholders, full implementation required
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentInitiatedEventConsumer {
    
    private final FraudAnalysisService fraudAnalysisService;
    private final RiskScoringService riskScoringService;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = "payment.initiated",
        groupId = "fraud-detection-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void handlePaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Processing PaymentInitiatedEvent: {}", event.getEventId());
        
        // IDEMPOTENCY CHECK - Critical for preventing duplicate processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Event already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // STEP 1: Perform comprehensive fraud analysis
            FraudAnalysisResult fraudResult = fraudAnalysisService.analyzePayment(
                event.getPaymentId(),
                event.getFromAccount(),
                event.getToAccount(),
                event.getAmount(),
                event.getCurrency(),
                event.getIpAddress(),
                event.getDeviceFingerprint(),
                event.getTimestamp()
            );
            
            // STEP 2: Calculate risk score
            double riskScore = riskScoringService.calculateRiskScore(
                event.getFromAccount(),
                event.getAmount(),
                fraudResult
            );
            
            // STEP 3: Apply fraud decision logic
            if (riskScore > 0.8) {
                // HIGH RISK - Block transaction immediately
                log.warn("HIGH RISK transaction detected: {} with score {}", 
                    event.getPaymentId(), riskScore);
                
                publishFraudCheckResult(event, "BLOCKED", riskScore, 
                    "High risk transaction blocked by fraud detection");
                    
            } else if (riskScore > 0.5) {
                // MEDIUM RISK - Require additional verification
                log.info("MEDIUM RISK transaction: {} with score {}", 
                    event.getPaymentId(), riskScore);
                
                publishFraudCheckResult(event, "REVIEW_REQUIRED", riskScore,
                    "Transaction requires manual review");
                    
            } else {
                // LOW RISK - Allow transaction
                log.info("LOW RISK transaction approved: {} with score {}", 
                    event.getPaymentId(), riskScore);
                
                publishFraudCheckResult(event, "APPROVED", riskScore,
                    "Transaction approved by fraud detection");
            }
            
            // STEP 4: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("PaymentInitiatedEvent")
                .processedAt(Instant.now())
                .processingResult("SUCCESS")
                .riskScore(riskScore)
                .build();
                
            processedEventRepository.save(processedEvent);
            
            // STEP 5: Update fraud detection models with new data
            fraudAnalysisService.updateModels(event, fraudResult);
            
        } catch (Exception e) {
            log.error("Failed to process PaymentInitiatedEvent: {}", event.getEventId(), e);
            
            // Send to dead letter queue for manual investigation
            sendToDeadLetterQueue(event, e);
            
            // Re-throw to trigger retry mechanism
            throw new RuntimeException("Fraud detection processing failed", e);
        }
    }
    
    private void publishFraudCheckResult(PaymentInitiatedEvent event, String decision, 
                                         double riskScore, String reason) {
        FraudCheckCompletedEvent fraudEvent = FraudCheckCompletedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(event.getPaymentId())
            .decision(decision)
            .riskScore(riskScore)
            .reason(reason)
            .timestamp(Instant.now())
            .correlationId(event.getCorrelationId())
            .build();
            
        kafkaTemplate.send("fraud.check.completed", fraudEvent);
        log.info("Published FraudCheckCompletedEvent for payment: {} with decision: {}", 
            event.getPaymentId(), decision);
    }
    
    private void sendToDeadLetterQueue(PaymentInitiatedEvent event, Exception exception) {
        // Send failed events to DLQ for manual processing
        kafkaTemplate.send("payment.initiated.dlq", event);
        log.error("Sent event to DLQ: {} due to: {}", 
            event.getEventId(), exception.getMessage());
    }
}