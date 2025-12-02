package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.ReversalReason;
import com.waqiti.payment.entity.PaymentReversal;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentReversalRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.ReversalNotificationService;
import com.waqiti.payment.service.WalletServiceClient;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Critical Kafka Consumer for Payment Reversal Events
 * 
 * Handles payment reversal initiation events from various sources:
 * - Manual admin reversals
 * - Automated fraud reversals
 * - Chargeback reversals
 * - Customer dispute reversals
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReversalInitiatedConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentReversalRepository reversalRepository;
    private final PaymentService paymentService;
    private final WalletServiceClient walletServiceClient;
    private final ReversalNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
        topics = "payment-reversal-initiated",
        groupId = "payment-reversal-processing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentReversalInitiated(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing payment reversal initiation: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the reversal event
            Map<String, Object> reversalEvent = objectMapper.readValue(message, Map.class);
            
            String paymentId = (String) reversalEvent.get("paymentId");
            String reversalReason = (String) reversalEvent.get("reason");
            String initiatedBy = (String) reversalEvent.get("initiatedBy");
            String reversalType = (String) reversalEvent.get("reversalType");
            BigDecimal reversalAmount = new BigDecimal(reversalEvent.get("amount").toString());
            String notes = (String) reversalEvent.get("notes");
            
            // Validate required fields
            if (paymentId == null || reversalReason == null || initiatedBy == null) {
                log.error("Invalid reversal event - missing required fields: {}", reversalEvent);
                publishReversalFailedEvent(paymentId, "VALIDATION_ERROR", "Missing required fields");
                acknowledgment.acknowledge();
                return;
            }
            
            // Find the original payment
            Optional<Payment> paymentOpt = paymentRepository.findById(UUID.fromString(paymentId));
            if (paymentOpt.isEmpty()) {
                log.error("Payment not found for reversal: {}", paymentId);
                publishReversalFailedEvent(paymentId, "PAYMENT_NOT_FOUND", "Original payment not found");
                acknowledgment.acknowledge();
                return;
            }
            
            Payment payment = paymentOpt.get();
            
            // Validate payment can be reversed
            if (!canPaymentBeReversed(payment)) {
                log.warn("Payment cannot be reversed - invalid status: {} for payment {}", 
                    payment.getStatus(), paymentId);
                publishReversalFailedEvent(paymentId, "INVALID_STATUS", 
                    "Payment in status " + payment.getStatus() + " cannot be reversed");
                acknowledgment.acknowledge();
                return;
            }
            
            // Validate reversal amount
            if (reversalAmount.compareTo(payment.getAmount()) > 0) {
                log.error("Reversal amount {} exceeds payment amount {} for payment {}", 
                    reversalAmount, payment.getAmount(), paymentId);
                publishReversalFailedEvent(paymentId, "INVALID_AMOUNT", "Reversal amount exceeds payment amount");
                acknowledgment.acknowledge();
                return;
            }
            
            // Create reversal record
            PaymentReversal reversal = createReversalRecord(payment, reversalReason, initiatedBy, 
                reversalType, reversalAmount, notes);
            
            // Execute the reversal process
            executePaymentReversal(payment, reversal);
            
            // Audit the reversal
            auditService.logEvent("PAYMENT_REVERSAL_PROCESSED", 
                Map.of(
                    "paymentId", paymentId,
                    "reversalId", reversal.getId().toString(),
                    "amount", reversalAmount.toString(),
                    "reason", reversalReason,
                    "initiatedBy", initiatedBy
                ));
            
            log.info("Successfully processed payment reversal: paymentId={}, reversalId={}, amount={}", 
                paymentId, reversal.getId(), reversalAmount);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Critical error processing payment reversal initiation", e);
            
            // Try to extract payment ID for error event
            String paymentId = null;
            try {
                Map<String, Object> event = objectMapper.readValue(message, Map.class);
                paymentId = (String) event.get("paymentId");
            } catch (Exception parseException) {
                log.error("Failed to parse event message for error reporting", parseException);
                auditService.logEvent("PAYMENT_REVERSAL_PARSE_ERROR", 
                    Map.of(
                        "rawMessage", message,
                        "error", parseException.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                    ));
            }
            
            // Publish failure event with comprehensive error details
            publishReversalFailedEvent(paymentId, "PROCESSING_ERROR", e.getMessage());
            
            // Audit the critical failure
            auditService.logEvent("PAYMENT_REVERSAL_PROCESSING_FAILURE", 
                Map.of(
                    "paymentId", paymentId != null ? paymentId : "unknown",
                    "errorType", e.getClass().getSimpleName(),
                    "errorMessage", e.getMessage(),
                    "stackTrace", getStackTraceAsString(e),
                    "timestamp", LocalDateTime.now().toString()
                ));
            
            // Don't acknowledge - will be retried
            throw new RuntimeException("Failed to process payment reversal", e);
        }
    }

    private boolean canPaymentBeReversed(Payment payment) {
        return payment.getStatus() == PaymentStatus.COMPLETED ||
               payment.getStatus() == PaymentStatus.SETTLED ||
               payment.getStatus() == PaymentStatus.PROCESSING;
    }

    private PaymentReversal createReversalRecord(Payment payment, String reasonCode, 
                                               String initiatedBy, String reversalType, 
                                               BigDecimal amount, String notes) {
        
        PaymentReversal reversal = PaymentReversal.builder()
            .id(UUID.randomUUID())
            .paymentId(payment.getId())
            .originalAmount(payment.getAmount())
            .reversalAmount(amount)
            .reasonCode(ReversalReason.valueOf(reasonCode))
            .reversalType(reversalType)
            .initiatedBy(initiatedBy)
            .notes(notes)
            .status("INITIATED")
            .createdAt(LocalDateTime.now())
            .build();
        
        return reversalRepository.save(reversal);
    }

    private void executePaymentReversal(Payment payment, PaymentReversal reversal) {
        try {
            log.info("Executing payment reversal: paymentId={}, reversalId={}", 
                payment.getId(), reversal.getId());
            
            // Step 1: Update payment status to reversing
            payment.setStatus(PaymentStatus.REVERSING);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Step 2: Reverse wallet transactions
            boolean walletReversalSuccess = reverseWalletTransactions(payment, reversal);
            
            if (!walletReversalSuccess) {
                handleReversalFailure(payment, reversal, "WALLET_REVERSAL_FAILED");
                return;
            }
            
            // Step 3: Reverse external provider transactions (if applicable)
            if (payment.getExternalTransactionId() != null) {
                boolean providerReversalSuccess = reverseProviderTransaction(payment, reversal);
                
                if (!providerReversalSuccess) {
                    handleReversalFailure(payment, reversal, "PROVIDER_REVERSAL_FAILED");
                    return;
                }
            }
            
            // Step 4: Update statuses to completed
            payment.setStatus(PaymentStatus.REVERSED);
            payment.setReversedAt(LocalDateTime.now());
            payment.setReversalId(reversal.getId());
            paymentRepository.save(payment);
            
            reversal.setStatus("COMPLETED");
            reversal.setCompletedAt(LocalDateTime.now());
            reversalRepository.save(reversal);
            
            // Step 5: Send notifications
            notificationService.sendReversalCompletedNotification(payment, reversal);
            
            // Step 6: Publish reversal completed event
            publishReversalCompletedEvent(payment, reversal);
            
            log.info("Payment reversal completed successfully: paymentId={}, reversalId={}", 
                payment.getId(), reversal.getId());
            
        } catch (Exception e) {
            log.error("Error executing payment reversal: paymentId={}, reversalId={}", 
                payment.getId(), reversal.getId(), e);
            handleReversalFailure(payment, reversal, "EXECUTION_ERROR: " + e.getMessage());
        }
    }

    private boolean reverseWalletTransactions(Payment payment, PaymentReversal reversal) {
        try {
            log.info("Reversing wallet transactions for payment: {}", payment.getId());
            
            // Reverse the debit from sender (credit back)
            boolean senderReversalSuccess = walletServiceClient.creditWallet(
                payment.getSenderId(),
                reversal.getReversalAmount(),
                payment.getCurrency(),
                "PAYMENT_REVERSAL",
                "Reversal of payment " + payment.getId(),
                reversal.getId().toString()
            );
            
            if (!senderReversalSuccess) {
                log.error("Failed to credit sender wallet for reversal: senderId={}, paymentId={}", 
                    payment.getSenderId(), payment.getId());
                return false;
            }
            
            // Reverse the credit to recipient (debit back)
            boolean recipientReversalSuccess = walletServiceClient.debitWallet(
                payment.getRecipientId(),
                reversal.getReversalAmount(),
                payment.getCurrency(),
                "PAYMENT_REVERSAL",
                "Reversal of payment " + payment.getId(),
                reversal.getId().toString()
            );
            
            if (!recipientReversalSuccess) {
                log.error("Failed to debit recipient wallet for reversal: recipientId={}, paymentId={}", 
                    payment.getRecipientId(), payment.getId());
                
                // Compensate by reversing sender credit
                walletServiceClient.debitWallet(
                    payment.getSenderId(),
                    reversal.getReversalAmount(),
                    payment.getCurrency(),
                    "REVERSAL_COMPENSATION",
                    "Compensation for failed recipient reversal",
                    reversal.getId().toString()
                );
                
                return false;
            }
            
            log.info("Wallet transactions reversed successfully for payment: {}", payment.getId());
            return true;
            
        } catch (Exception e) {
            log.error("Error reversing wallet transactions for payment: {}", payment.getId(), e);
            return false;
        }
    }

    private boolean reverseProviderTransaction(Payment payment, PaymentReversal reversal) {
        try {
            log.info("Reversing external provider transaction: paymentId={}, externalId={}", 
                payment.getId(), payment.getExternalTransactionId());
            
            // Call the payment service to reverse the external transaction
            boolean success = paymentService.reverseExternalTransaction(
                payment.getExternalTransactionId(),
                payment.getProvider(),
                reversal.getReversalAmount(),
                reversal.getReasonCode().toString()
            );
            
            if (success) {
                log.info("External provider transaction reversed successfully: paymentId={}", payment.getId());
            } else {
                log.error("Failed to reverse external provider transaction: paymentId={}", payment.getId());
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Error reversing external provider transaction: paymentId={}", payment.getId(), e);
            return false;
        }
    }

    private void handleReversalFailure(Payment payment, PaymentReversal reversal, String failureReason) {
        log.error("Payment reversal failed: paymentId={}, reason={}", payment.getId(), failureReason);
        
        // Update statuses
        payment.setStatus(PaymentStatus.REVERSAL_FAILED);
        paymentRepository.save(payment);
        
        reversal.setStatus("FAILED");
        reversal.setFailureReason(failureReason);
        reversal.setCompletedAt(LocalDateTime.now());
        reversalRepository.save(reversal);
        
        // Send failure notification
        notificationService.sendReversalFailedNotification(payment, reversal);
        
        // Publish failure event
        publishReversalFailedEvent(payment.getId().toString(), "REVERSAL_EXECUTION_FAILED", failureReason);
    }

    private void publishReversalCompletedEvent(Payment payment, PaymentReversal reversal) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "payment-reversal-completed",
                "paymentId", payment.getId().toString(),
                "reversalId", reversal.getId().toString(),
                "originalAmount", payment.getAmount().toString(),
                "reversalAmount", reversal.getReversalAmount().toString(),
                "senderId", payment.getSenderId(),
                "recipientId", payment.getRecipientId(),
                "currency", payment.getCurrency(),
                "reason", reversal.getReasonCode().toString(),
                "initiatedBy", reversal.getInitiatedBy(),
                "completedAt", reversal.getCompletedAt().toString(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("payment-reversal-completed", payment.getId().toString(), event);
            log.debug("Published payment reversal completed event: {}", payment.getId());
            
        } catch (Exception e) {
            log.error("Failed to publish payment reversal completed event", e);
        }
    }

    private void publishReversalFailedEvent(String paymentId, String errorCode, String errorMessage) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "payment-reversal-failed",
                "paymentId", paymentId != null ? paymentId : "unknown",
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("payment-reversal-failed", paymentId, event);
            log.debug("Published payment reversal failed event: paymentId={}, error={}", paymentId, errorCode);
            
        } catch (Exception e) {
            log.error("Failed to publish payment reversal failed event", e);
            // Last resort - audit the failure to publish
            try {
                auditService.logEvent("PAYMENT_REVERSAL_EVENT_PUBLISH_FAILURE", 
                    Map.of(
                        "paymentId", paymentId != null ? paymentId : "unknown",
                        "errorCode", errorCode,
                        "publishError", e.getMessage()
                    ));
            } catch (Exception auditException) {
                // Absolute last resort - just log to console
                log.error("CRITICAL: Unable to audit or publish reversal failure - manual intervention required", auditException);
            }
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}