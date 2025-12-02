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
 * Critical Event Consumer #264: Wallet Rewards Event Consumer
 * Processes cashback and loyalty points with tax compliance
 * Implements 12-step zero-tolerance processing for rewards
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRewardsEventConsumer extends BaseKafkaConsumer {

    private final WalletService walletService;
    private final WalletComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-rewards-events", groupId = "wallet-rewards-group")
    @CircuitBreaker(name = "wallet-rewards-consumer")
    @Retry(name = "wallet-rewards-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWalletRewardsEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "wallet-rewards-event");
        
        try {
            log.info("Step 1: Processing wallet rewards event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String walletId = eventData.path("walletId").asText();
            String rewardType = eventData.path("rewardType").asText();
            BigDecimal rewardAmount = new BigDecimal(eventData.path("rewardAmount").asText());
            String triggerTransactionId = eventData.path("triggerTransactionId").asText();
            String rewardCategory = eventData.path("rewardCategory").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted rewards details: walletId={}, type={}, amount={}", 
                    walletId, rewardType, rewardAmount);
            
            // Step 3: Reward eligibility validation
            boolean eligible = walletService.validateRewardEligibility(walletId, rewardType, 
                    triggerTransactionId, timestamp);
            if (!eligible) {
                log.error("Step 3: Reward eligibility validation failed for walletId={}", walletId);
                walletService.rejectReward(eventId, "NOT_ELIGIBLE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Reward eligibility validated");
            
            // Step 4: Anti-fraud reward validation
            boolean fraudulent = complianceService.detectRewardFraud(walletId, rewardType, 
                    rewardAmount, triggerTransactionId, timestamp);
            if (fraudulent) {
                log.error("Step 4: Fraudulent reward attempt detected");
                walletService.flagFraudulentReward(eventId, walletId, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Fraud validation passed");
            
            // Step 5: Tax reporting threshold check
            BigDecimal annualRewards = walletService.getAnnualRewards(walletId, timestamp.getYear());
            if (annualRewards.add(rewardAmount).compareTo(new BigDecimal("600")) >= 0) {
                complianceService.prepare1099Reporting(walletId, annualRewards.add(rewardAmount), 
                        timestamp);
                log.info("Step 5: 1099 reporting threshold reached");
            } else {
                log.info("Step 5: Below tax reporting threshold");
            }
            
            // Step 6: Reward calculation and validation
            BigDecimal calculatedReward = walletService.calculateAccurateReward(triggerTransactionId, 
                    rewardType, rewardCategory, timestamp);
            if (!calculatedReward.equals(rewardAmount)) {
                log.warn("Step 6: Reward amount mismatch - using calculated: {}", calculatedReward);
                rewardAmount = calculatedReward;
            } else {
                log.info("Step 6: Reward amount validated");
            }
            
            // Step 7: Loyalty program integration
            String loyaltyTierId = walletService.getLoyaltyTier(walletId);
            BigDecimal bonusMultiplier = walletService.getLoyaltyBonus(loyaltyTierId, rewardCategory);
            BigDecimal finalRewardAmount = rewardAmount.multiply(bonusMultiplier);
            log.info("Step 7: Loyalty bonus applied: tier={}, multiplier={}", loyaltyTierId, bonusMultiplier);
            
            // Step 8: Reward processing and crediting
            Transaction rewardTransaction = walletService.processRewardCredit(eventId, walletId, 
                    finalRewardAmount, rewardType, triggerTransactionId, timestamp);
            log.info("Step 8: Reward credited: transactionId={}", rewardTransaction.getId());
            
            // Step 9: Cashback conversion handling
            if ("CASHBACK".equals(rewardType)) {
                walletService.convertCashbackToBalance(walletId, finalRewardAmount, 
                        rewardTransaction.getId(), timestamp);
                log.info("Step 9: Cashback converted to wallet balance");
            } else {
                walletService.creditLoyaltyPoints(walletId, finalRewardAmount, rewardTransaction.getId(), 
                        timestamp);
                log.info("Step 9: Loyalty points credited");
            }
            
            // Step 10: Promotional campaign tracking
            complianceService.trackPromotionalRewards(walletId, rewardType, finalRewardAmount, 
                    rewardCategory, timestamp);
            log.info("Step 10: Promotional tracking updated");
            
            // Step 11: Customer notification
            walletService.sendRewardNotification(walletId, rewardType, finalRewardAmount, 
                    rewardTransaction.getId(), timestamp);
            log.info("Step 11: Reward notification sent");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed wallet reward: eventId={}, transactionId={}", 
                    eventId, rewardTransaction.getId());
            
        } catch (Exception e) {
            log.error("Error processing wallet rewards event: partition={}, offset={}, error={}",
                    record.partition(), record.offset(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet rewards message sent to DLQ: offset={}, destination={}, category={}",
                        record.offset(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet rewards event - MESSAGE MAY BE LOST! " +
                            "partition={}, offset={}, error={}",
                            record.partition(), record.offset(), dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet rewards event processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("walletId") || 
            !eventData.has("rewardType") || !eventData.has("rewardAmount")) {
            throw new IllegalArgumentException("Invalid wallet rewards event structure");
        }
    }
}