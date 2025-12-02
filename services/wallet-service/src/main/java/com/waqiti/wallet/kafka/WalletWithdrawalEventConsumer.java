package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletComplianceService;
import com.waqiti.wallet.domain.Transaction;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #261: Wallet Withdrawal Event Consumer
 * Processes cash-out and bank transfers with CTR, BSA compliance
 * Implements 12-step zero-tolerance processing for withdrawals
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletWithdrawalEventConsumer extends BaseKafkaConsumer {

    private final WalletService walletService;
    private final WalletComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-withdrawal-events", groupId = "wallet-withdrawal-group")
    @CircuitBreaker(name = "wallet-withdrawal-consumer")
    @Retry(name = "wallet-withdrawal-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWalletWithdrawalEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "wallet-withdrawal-event");
        
        try {
            log.info("Step 1: Processing wallet withdrawal event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String walletId = eventData.path("walletId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String destinationType = eventData.path("destinationType").asText();
            String destinationAccount = eventData.path("destinationAccount").asText();
            String withdrawalPurpose = eventData.path("withdrawalPurpose").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted withdrawal details: walletId={}, amount={}, destinationType={}", 
                    walletId, amount, destinationType);
            
            // Step 3: Withdrawal validation with early return
            boolean validated = walletService.validateWithdrawalRequest(walletId, amount, 
                    destinationType, destinationAccount, timestamp);
            if (!validated) {
                log.error("Step 3: Withdrawal validation failed for walletId={}", walletId);
                walletService.rejectWithdrawal(eventId, "VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Withdrawal validation successful");
            
            // Step 4: CTR threshold check for cash transactions
            if ("CASH".equals(destinationType) && amount.compareTo(new BigDecimal("10000")) >= 0) {
                boolean ctrFiled = complianceService.fileCTR(walletId, amount, withdrawalPurpose, timestamp);
                if (!ctrFiled) {
                    log.error("Step 4: CTR filing failed");
                    walletService.holdWithdrawal(eventId, "CTR_FILING_REQUIRED", timestamp);
                }
                log.info("Step 4: CTR processed for large cash withdrawal");
            } else {
                log.info("Step 4: No CTR required for withdrawal type/amount");
            }
            
            // Step 5: Enhanced due diligence for high-risk destinations
            boolean eddRequired = complianceService.requiresEnhancedDueDiligence(destinationType, 
                    destinationAccount, amount, timestamp);
            if (eddRequired) {
                boolean eddPassed = complianceService.performEnhancedDueDiligence(walletId, 
                        destinationAccount, amount, timestamp);
                if (!eddPassed) {
                    log.error("Step 5: Enhanced due diligence failed");
                    walletService.escalateForReview(eventId, "EDD_REVIEW_REQUIRED", timestamp);
                    ack.acknowledge();
                    return;
                }
            }
            log.info("Step 5: Due diligence checks completed");
            
            // Step 6: Destination account verification
            boolean destinationVerified = walletService.verifyDestinationAccount(destinationType, 
                    destinationAccount, timestamp);
            if (!destinationVerified) {
                log.error("Step 6: Destination account verification failed");
                walletService.rejectWithdrawal(eventId, "INVALID_DESTINATION", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 6: Destination account verified");
            
            // Step 7: Withdrawal processing and balance deduction
            Transaction transaction = walletService.processWithdrawal(eventId, walletId, amount, 
                    destinationType, destinationAccount, withdrawalPurpose, timestamp);
            log.info("Step 7: Withdrawal processed: transactionId={}", transaction.getId());
            
            // Step 8: SWIFT network routing for international transfers
            if ("INTERNATIONAL_WIRE".equals(destinationType)) {
                String swiftCode = walletService.routeInternationalTransfer(destinationAccount, 
                        amount, transaction.getId(), timestamp);
                log.info("Step 8: International transfer routed via SWIFT: {}", swiftCode);
            } else {
                log.info("Step 8: Domestic transfer, no SWIFT routing required");
            }
            
            // Step 9: Real-time balance monitoring
            walletService.monitorPostWithdrawalBalance(walletId, transaction.getId(), timestamp);
            log.info("Step 9: Post-withdrawal balance monitoring completed");
            
            // Step 10: Fraud prevention checks
            int fraudScore = complianceService.calculateWithdrawalFraudScore(walletId, amount, 
                    destinationType, timestamp);
            if (fraudScore > 700) {
                walletService.flagSuspiciousWithdrawal(transaction.getId(), fraudScore, timestamp);
                log.warn("Step 10: High fraud score for withdrawal: {}", fraudScore);
            } else {
                log.info("Step 10: Fraud score acceptable: {}", fraudScore);
            }
            
            // Step 11: Withdrawal confirmation and tracking
            walletService.sendWithdrawalConfirmation(walletId, transaction.getId(), 
                    destinationType, amount, timestamp);
            log.info("Step 11: Withdrawal confirmation sent");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed wallet withdrawal: eventId={}, transactionId={}", 
                    eventId, transaction.getId());
            
        } catch (Exception e) {
            log.error("Error processing wallet withdrawal event: partition={}, offset={}, error={}",
                    record.partition(), record.offset(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet withdrawal message sent to DLQ: offset={}, destination={}, category={}",
                        record.offset(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet withdrawal event - MESSAGE MAY BE LOST! " +
                            "partition={}, offset={}, error={}",
                            record.partition(), record.offset(), dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet withdrawal event processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("walletId") || 
            !eventData.has("amount") || !eventData.has("destinationType")) {
            throw new IllegalArgumentException("Invalid wallet withdrawal event structure");
        }
    }
}