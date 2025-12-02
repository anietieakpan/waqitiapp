package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentChargebackProcessedEvent;
import com.waqiti.payment.service.ChargebackService;
import com.waqiti.payment.service.LedgerService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.common.audit.AuditService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CRITICAL EVENT CONSUMER: Payment Chargeback Processed Events
 * 
 * This consumer handles chargeback processing events that were previously orphaned,
 * causing critical data loss in chargeback tracking and financial reconciliation.
 * 
 * Key Responsibilities:
 * - Update payment and chargeback status in payment service
 * - Record chargeback entries in ledger service
 * - Trigger merchant notifications
 * - Update dispute tracking systems
 * - Generate compliance reports
 * - Initiate representment workflows if applicable
 * 
 * Business Impact:
 * - Prevents loss of chargeback tracking data
 * - Ensures accurate financial reconciliation
 * - Maintains merchant notification workflows
 * - Supports regulatory compliance requirements
 * 
 * @author Waqiti Payments Team
 * @since 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentChargebackProcessedEventConsumer {

    private final ChargebackService chargebackService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    private Counter chargebackEventsProcessed;
    private Counter chargebackProcessingErrors;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        // Initialize metrics
        this.chargebackEventsProcessed = Counter.builder("payment_chargeback_events_processed")
            .description("Number of chargeback events processed")
            .register(meterRegistry);
            
        this.chargebackProcessingErrors = Counter.builder("payment_chargeback_processing_errors")
            .description("Number of chargeback event processing errors")
            .register(meterRegistry);
    }

    /**
     * Processes payment chargeback events from the bank integration service.
     * 
     * @param event The chargeback processed event
     * @param acknowledgment Kafka acknowledgment for manual commit
     * @param partition The Kafka partition
     * @param offset The message offset
     */
    @KafkaListener(
        topics = "bank-integration-events",
        groupId = "payment-chargeback-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Timed(value = "chargeback_event_processing_duration", description = "Time to process chargeback event")
    public void handleChargebackProcessedEvent(
            @Payload PaymentChargebackProcessedEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.info("Processing chargeback event for payment: {} chargeback: {} amount: {}", 
                event.getPaymentId(), event.getChargebackId(), event.getChargebackAmount());
            
            // Validate event data
            validateChargebackEvent(event);
            
            // Update chargeback status and details
            updateChargebackStatus(event);
            
            // Record ledger entries for the chargeback
            recordChargebackLedgerEntries(event);
            
            // Update payment status to reflect chargeback
            updatePaymentStatus(event);
            
            // Send notifications to merchant
            sendMerchantNotifications(event);
            
            // Update dispute tracking if applicable
            updateDisputeTracking(event);
            
            // Generate compliance reports if required
            generateComplianceReports(event);
            
            // Check if representment is possible/recommended
            evaluateRepresentmentOptions(event);
            
            // Audit the chargeback processing
            auditChargebackProcessing(event);
            
            // Update metrics
            chargebackEventsProcessed.increment();
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed chargeback event for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error processing chargeback event for payment: {}", event.getPaymentId(), e);
            
            // Update error metrics
            chargebackProcessingErrors.increment();
            
            // Handle processing error
            handleChargebackProcessingError(event, e);
            
            // Acknowledge to prevent infinite reprocessing (error handled in DLQ)
            acknowledgment.acknowledge();
        }
    }

    private void validateChargebackEvent(PaymentChargebackProcessedEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required for chargeback event");
        }
        
        if (event.getChargebackId() == null || event.getChargebackId().trim().isEmpty()) {
            throw new IllegalArgumentException("Chargeback ID is required for chargeback event");
        }
        
        if (event.getChargebackAmount() == null || event.getChargebackAmount().signum() <= 0) {
            throw new IllegalArgumentException("Chargeback amount must be positive");
        }
        
        if (event.getChargebackReason() == null || event.getChargebackReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Chargeback reason is required");
        }
    }

    private void updateChargebackStatus(PaymentChargebackProcessedEvent event) {
        try {
            chargebackService.updateChargebackStatus(
                event.getChargebackId(),
                "PROCESSED",
                event.getChargebackAmount(),
                event.getChargebackReason(),
                event.getProcessedAt()
            );
            
            log.debug("Updated chargeback status for chargeback: {}", event.getChargebackId());
            
        } catch (Exception e) {
            log.error("Failed to update chargeback status for chargeback: {}", event.getChargebackId(), e);
            throw new RuntimeException("Chargeback status update failed", e);
        }
    }

    private void recordChargebackLedgerEntries(PaymentChargebackProcessedEvent event) {
        try {
            // Create chargeback ledger entry
            ledgerService.recordChargebackEntry(
                event.getPaymentId(),
                event.getChargebackId(),
                event.getChargebackAmount(),
                event.getMerchantId(),
                event.getChargebackReason(),
                event.getProcessedAt()
            );
            
            log.debug("Recorded chargeback ledger entries for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to record chargeback ledger entries for payment: {}", event.getPaymentId(), e);
            throw new RuntimeException("Chargeback ledger recording failed", e);
        }
    }

    private void updatePaymentStatus(PaymentChargebackProcessedEvent event) {
        try {
            chargebackService.updatePaymentForChargeback(
                event.getPaymentId(),
                event.getChargebackId(),
                event.getChargebackAmount(),
                event.getProcessedAt()
            );
            
            log.debug("Updated payment status for chargeback on payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to update payment status for payment: {}", event.getPaymentId(), e);
            throw new RuntimeException("Payment status update failed", e);
        }
    }

    private void sendMerchantNotifications(PaymentChargebackProcessedEvent event) {
        try {
            notificationService.sendChargebackNotification(
                event.getMerchantId(),
                event.getPaymentId(),
                event.getChargebackId(),
                event.getChargebackAmount(),
                event.getChargebackReason(),
                event.getProcessedAt()
            );
            
            log.debug("Sent chargeback notification to merchant: {}", event.getMerchantId());
            
        } catch (Exception e) {
            log.warn("Failed to send chargeback notification to merchant: {}", event.getMerchantId(), e);
            // Don't throw exception for notification failures
        }
    }

    private void updateDisputeTracking(PaymentChargebackProcessedEvent event) {
        try {
            if (event.getDisputeId() != null) {
                chargebackService.updateDisputeForChargeback(
                    event.getDisputeId(),
                    event.getChargebackId(),
                    event.getChargebackAmount(),
                    event.getProcessedAt()
                );
                
                log.debug("Updated dispute tracking for dispute: {}", event.getDisputeId());
            }
            
        } catch (Exception e) {
            log.warn("Failed to update dispute tracking for dispute: {}", event.getDisputeId(), e);
            // Don't throw exception for dispute tracking failures
        }
    }

    private void generateComplianceReports(PaymentChargebackProcessedEvent event) {
        try {
            // Generate regulatory reports if chargeback amount exceeds threshold
            if (event.getChargebackAmount().doubleValue() > 10000.0) {
                chargebackService.generateChargebackComplianceReport(
                    event.getChargebackId(),
                    event.getPaymentId(),
                    event.getChargebackAmount(),
                    event.getChargebackReason()
                );
                
                log.debug("Generated compliance report for high-value chargeback: {}", event.getChargebackId());
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate compliance report for chargeback: {}", event.getChargebackId(), e);
            // Don't throw exception for compliance report failures
        }
    }

    private void evaluateRepresentmentOptions(PaymentChargebackProcessedEvent event) {
        try {
            boolean representmentRecommended = chargebackService.evaluateRepresentmentViability(
                event.getChargebackId(),
                event.getChargebackReason(),
                event.getChargebackAmount()
            );
            
            if (representmentRecommended) {
                chargebackService.initiateRepresentmentWorkflow(
                    event.getChargebackId(),
                    event.getPaymentId(),
                    event.getMerchantId()
                );
                
                log.info("Initiated representment workflow for chargeback: {}", event.getChargebackId());
            }
            
        } catch (Exception e) {
            log.warn("Failed to evaluate representment options for chargeback: {}", event.getChargebackId(), e);
            // Don't throw exception for representment evaluation failures
        }
    }

    private void auditChargebackProcessing(PaymentChargebackProcessedEvent event) {
        try {
            auditService.auditChargebackProcessing(
                event.getPaymentId(),
                event.getChargebackId(),
                event.getChargebackAmount(),
                event.getChargebackReason(),
                event.getMerchantId(),
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.warn("Failed to audit chargeback processing for chargeback: {}", event.getChargebackId(), e);
            // Don't throw exception for audit failures
        }
    }

    private void handleChargebackProcessingError(PaymentChargebackProcessedEvent event, Exception error) {
        try {
            log.error("Chargeback processing error - sending to DLQ. Payment: {}, Chargeback: {}, Error: {}", 
                event.getPaymentId(), event.getChargebackId(), error.getMessage());
            
            // Send to dead letter queue for manual investigation
            chargebackService.sendChargebackEventToDLQ(event, error.getMessage());
            
            // Alert operations team
            notificationService.sendChargebackProcessingAlert(
                event.getPaymentId(),
                event.getChargebackId(),
                error.getMessage()
            );
            
        } catch (Exception e) {
            log.error("Failed to handle chargeback processing error", e);
        }
    }
}