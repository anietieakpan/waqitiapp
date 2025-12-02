package com.waqiti.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.ChargebackService;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.entity.Chargeback;
import com.waqiti.payment.entity.ChargebackStatus;
import com.waqiti.payment.entity.ChargebackReason;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Kafka consumer for processing chargeback events
 * Handles chargeback notifications from payment processors and card networks
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargebackProcessingConsumer {

    private final ChargebackService chargebackService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000L, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = "payment-chargeback-processed", groupId = "payment-service-chargeback-group")
    public void processChargebackEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing chargeback event from topic: {}, partition: {}", topic, partition);
            
            // Parse chargeback event
            ChargebackEvent event = objectMapper.readValue(payload, ChargebackEvent.class);
            
            // Validate event
            validateChargebackEvent(event);
            
            // Process based on chargeback action
            switch (event.getAction()) {
                case "INITIATED":
                    handleChargebackInitiated(event);
                    break;
                case "ACCEPTED":
                    handleChargebackAccepted(event);
                    break;
                case "DISPUTED":
                    handleChargebackDisputed(event);
                    break;
                case "WON":
                    handleChargebackWon(event);
                    break;
                case "LOST":
                    handleChargebackLost(event);
                    break;
                case "EXPIRED":
                    handleChargebackExpired(event);
                    break;
                default:
                    log.warn("Unknown chargeback action: {}", event.getAction());
            }
            
            log.info("Successfully processed chargeback event for payment: {}, action: {}", 
                event.getPaymentId(), event.getAction());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing chargeback event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process chargeback event", e);
        }
    }

    private void validateChargebackEvent(ChargebackEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getChargebackId() == null || event.getChargebackId().trim().isEmpty()) {
            throw new IllegalArgumentException("Chargeback ID is required");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Chargeback action is required");
        }
        
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid chargeback amount is required");
        }
    }

    private void handleChargebackInitiated(ChargebackEvent event) {
        log.info("Processing chargeback initiation for payment: {}", event.getPaymentId());
        
        try {
            // Create new chargeback record
            Chargeback chargeback = new Chargeback();
            chargeback.setId(UUID.randomUUID());
            chargeback.setChargebackId(event.getChargebackId());
            chargeback.setPaymentId(UUID.fromString(event.getPaymentId()));
            chargeback.setAmount(event.getAmount());
            chargeback.setCurrency(event.getCurrency());
            chargeback.setStatus(ChargebackStatus.INITIATED);
            chargeback.setReason(ChargebackReason.fromString(event.getReason()));
            chargeback.setReasonCode(event.getReasonCode());
            chargeback.setDueDate(event.getDueDate());
            chargeback.setProcessorReference(event.getProcessorReference());
            chargeback.setCardNetwork(event.getCardNetwork());
            chargeback.setDescription(event.getDescription());
            chargeback.setCreatedAt(LocalDateTime.now());
            chargeback.setUpdatedAt(LocalDateTime.now());
            
            // Save chargeback
            chargebackService.createChargeback(chargeback);
            
            // Update payment status to under chargeback
            paymentService.markPaymentUnderChargeback(UUID.fromString(event.getPaymentId()), event.getChargebackId());
            
            // Initiate chargeback workflow
            chargebackService.initiateChargebackWorkflow(chargeback);
            
            // Send alerts to relevant teams
            chargebackService.sendChargebackAlerts(chargeback, "CHARGEBACK_INITIATED");
            
            log.info("Chargeback initiated successfully for payment: {}, chargeback ID: {}", 
                event.getPaymentId(), event.getChargebackId());
            
        } catch (Exception e) {
            log.error("Error handling chargeback initiation for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle chargeback initiation", e);
        }
    }

    private void handleChargebackAccepted(ChargebackEvent event) {
        log.info("Processing chargeback acceptance for payment: {}", event.getPaymentId());
        
        try {
            // Find existing chargeback
            Chargeback chargeback = chargebackService.findByChargebackId(event.getChargebackId());
            if (chargeback == null) {
                throw new IllegalStateException("Chargeback not found: " + event.getChargebackId());
            }
            
            // Update chargeback status
            chargeback.setStatus(ChargebackStatus.ACCEPTED);
            chargeback.setAcceptedAt(LocalDateTime.now());
            chargeback.setAcceptanceReason(event.getAcceptanceReason());
            chargeback.setUpdatedAt(LocalDateTime.now());
            
            // Save updated chargeback
            chargebackService.updateChargeback(chargeback);
            
            // Process refund to customer
            chargebackService.processChargebackRefund(chargeback);
            
            // Update payment status to refunded
            paymentService.markPaymentRefundedByChargeback(
                UUID.fromString(event.getPaymentId()), 
                event.getChargebackId(),
                event.getAmount()
            );
            
            // Close chargeback workflow
            chargebackService.closeChargebackWorkflow(chargeback, "ACCEPTED");
            
            // Send acceptance notifications
            chargebackService.sendChargebackAlerts(chargeback, "CHARGEBACK_ACCEPTED");
            
            log.info("Chargeback accepted and processed for payment: {}, chargeback ID: {}", 
                event.getPaymentId(), event.getChargebackId());
            
        } catch (Exception e) {
            log.error("Error handling chargeback acceptance for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle chargeback acceptance", e);
        }
    }

    private void handleChargebackDisputed(ChargebackEvent event) {
        log.info("Processing chargeback dispute for payment: {}", event.getPaymentId());
        
        try {
            // Find existing chargeback
            Chargeback chargeback = chargebackService.findByChargebackId(event.getChargebackId());
            if (chargeback == null) {
                throw new IllegalStateException("Chargeback not found: " + event.getChargebackId());
            }
            
            // Update chargeback status
            chargeback.setStatus(ChargebackStatus.DISPUTED);
            chargeback.setDisputedAt(LocalDateTime.now());
            chargeback.setDisputeReason(event.getDisputeReason());
            chargeback.setUpdatedAt(LocalDateTime.now());
            
            // Save updated chargeback
            chargebackService.updateChargeback(chargeback);
            
            // Submit dispute evidence
            chargebackService.submitDisputeEvidence(chargeback, event.getEvidenceDocuments());
            
            // Update payment status to disputed
            paymentService.markPaymentDisputed(UUID.fromString(event.getPaymentId()), event.getChargebackId());
            
            // Continue chargeback workflow in dispute phase
            chargebackService.escalateChargebackWorkflow(chargeback, "DISPUTED");
            
            // Send dispute notifications
            chargebackService.sendChargebackAlerts(chargeback, "CHARGEBACK_DISPUTED");
            
            log.info("Chargeback disputed for payment: {}, chargeback ID: {}", 
                event.getPaymentId(), event.getChargebackId());
            
        } catch (Exception e) {
            log.error("Error handling chargeback dispute for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle chargeback dispute", e);
        }
    }

    private void handleChargebackWon(ChargebackEvent event) {
        log.info("Processing chargeback won for payment: {}", event.getPaymentId());
        
        try {
            // Find existing chargeback
            Chargeback chargeback = chargebackService.findByChargebackId(event.getChargebackId());
            if (chargeback == null) {
                throw new IllegalStateException("Chargeback not found: " + event.getChargebackId());
            }
            
            // Update chargeback status
            chargeback.setStatus(ChargebackStatus.WON);
            chargeback.setResolvedAt(LocalDateTime.now());
            chargeback.setResolutionReason(event.getResolutionReason());
            chargeback.setUpdatedAt(LocalDateTime.now());
            
            // Save updated chargeback
            chargebackService.updateChargeback(chargeback);
            
            // Restore payment status (remove chargeback flag)
            paymentService.restorePaymentFromChargeback(UUID.fromString(event.getPaymentId()), event.getChargebackId());
            
            // Process any recovered funds
            chargebackService.processChargebackRecovery(chargeback);
            
            // Close chargeback workflow successfully
            chargebackService.closeChargebackWorkflow(chargeback, "WON");
            
            // Send success notifications
            chargebackService.sendChargebackAlerts(chargeback, "CHARGEBACK_WON");
            
            log.info("Chargeback won for payment: {}, chargeback ID: {}", 
                event.getPaymentId(), event.getChargebackId());
            
        } catch (Exception e) {
            log.error("Error handling chargeback won for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle chargeback won", e);
        }
    }

    private void handleChargebackLost(ChargebackEvent event) {
        log.info("Processing chargeback lost for payment: {}", event.getPaymentId());
        
        try {
            // Find existing chargeback
            Chargeback chargeback = chargebackService.findByChargebackId(event.getChargebackId());
            if (chargeback == null) {
                throw new IllegalStateException("Chargeback not found: " + event.getChargebackId());
            }
            
            // Update chargeback status
            chargeback.setStatus(ChargebackStatus.LOST);
            chargeback.setResolvedAt(LocalDateTime.now());
            chargeback.setResolutionReason(event.getResolutionReason());
            chargeback.setUpdatedAt(LocalDateTime.now());
            
            // Save updated chargeback
            chargebackService.updateChargeback(chargeback);
            
            // Finalize payment refund
            paymentService.finalizeChargebackRefund(UUID.fromString(event.getPaymentId()), event.getChargebackId());
            
            // Process chargeback fees
            chargebackService.processChargebackFees(chargeback);
            
            // Close chargeback workflow as lost
            chargebackService.closeChargebackWorkflow(chargeback, "LOST");
            
            // Send loss notifications
            chargebackService.sendChargebackAlerts(chargeback, "CHARGEBACK_LOST");
            
            log.info("Chargeback lost for payment: {}, chargeback ID: {}", 
                event.getPaymentId(), event.getChargebackId());
            
        } catch (Exception e) {
            log.error("Error handling chargeback lost for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle chargeback lost", e);
        }
    }

    private void handleChargebackExpired(ChargebackEvent event) {
        log.info("Processing chargeback expiration for payment: {}", event.getPaymentId());
        
        try {
            // Find existing chargeback
            Chargeback chargeback = chargebackService.findByChargebackId(event.getChargebackId());
            if (chargeback == null) {
                throw new IllegalStateException("Chargeback not found: " + event.getChargebackId());
            }
            
            // Update chargeback status
            chargeback.setStatus(ChargebackStatus.EXPIRED);
            chargeback.setExpiredAt(LocalDateTime.now());
            chargeback.setUpdatedAt(LocalDateTime.now());
            
            // Save updated chargeback
            chargebackService.updateChargeback(chargeback);
            
            // Determine default outcome based on status at expiration
            String outcome = determineExpirationOutcome(chargeback);
            
            if ("MERCHANT_WIN".equals(outcome)) {
                // Restore payment if expired without response (merchant wins by default)
                paymentService.restorePaymentFromChargeback(UUID.fromString(event.getPaymentId()), event.getChargebackId());
            } else {
                // Finalize refund if expired during dispute (customer wins by default)
                paymentService.finalizeChargebackRefund(UUID.fromString(event.getPaymentId()), event.getChargebackId());
            }
            
            // Close chargeback workflow as expired
            chargebackService.closeChargebackWorkflow(chargeback, "EXPIRED");
            
            // Send expiration notifications
            chargebackService.sendChargebackAlerts(chargeback, "CHARGEBACK_EXPIRED");
            
            log.info("Chargeback expired for payment: {}, chargeback ID: {}, outcome: {}", 
                event.getPaymentId(), event.getChargebackId(), outcome);
            
        } catch (Exception e) {
            log.error("Error handling chargeback expiration for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to handle chargeback expiration", e);
        }
    }

    private String determineExpirationOutcome(Chargeback chargeback) {
        // Business logic to determine outcome when chargeback expires
        if (chargeback.getStatus() == ChargebackStatus.INITIATED && chargeback.getDisputedAt() == null) {
            // No dispute filed - merchant wins by default
            return "MERCHANT_WIN";
        } else if (chargeback.getStatus() == ChargebackStatus.DISPUTED) {
            // Dispute in progress - customer wins by default on expiration
            return "CUSTOMER_WIN";
        } else {
            // Default to customer win for safety
            return "CUSTOMER_WIN";
        }
    }

    // Chargeback event data structure
    public static class ChargebackEvent {
        private String paymentId;
        private String chargebackId;
        private String action;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private String reasonCode;
        private LocalDateTime dueDate;
        private String processorReference;
        private String cardNetwork;
        private String description;
        private String acceptanceReason;
        private String disputeReason;
        private String resolutionReason;
        private String[] evidenceDocuments;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getChargebackId() { return chargebackId; }
        public void setChargebackId(String chargebackId) { this.chargebackId = chargebackId; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
        
        public LocalDateTime getDueDate() { return dueDate; }
        public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
        
        public String getProcessorReference() { return processorReference; }
        public void setProcessorReference(String processorReference) { this.processorReference = processorReference; }
        
        public String getCardNetwork() { return cardNetwork; }
        public void setCardNetwork(String cardNetwork) { this.cardNetwork = cardNetwork; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getAcceptanceReason() { return acceptanceReason; }
        public void setAcceptanceReason(String acceptanceReason) { this.acceptanceReason = acceptanceReason; }
        
        public String getDisputeReason() { return disputeReason; }
        public void setDisputeReason(String disputeReason) { this.disputeReason = disputeReason; }
        
        public String getResolutionReason() { return resolutionReason; }
        public void setResolutionReason(String resolutionReason) { this.resolutionReason = resolutionReason; }
        
        public String[] getEvidenceDocuments() { return evidenceDocuments; }
        public void setEvidenceDocuments(String[] evidenceDocuments) { this.evidenceDocuments = evidenceDocuments; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}