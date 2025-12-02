package com.waqiti.wallet.events;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.AccountLimitsService;
import com.waqiti.wallet.dto.AccountLimitsUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Account Limits Update Event Consumer
 * 
 * Processes account limits update events and applies new limits to wallet accounts
 * Handles events from account-limits-updated topic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountLimitsUpdateEventConsumer {

    private final WalletService walletService;
    private final AccountLimitsService accountLimitsService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    /**
     * Processes account limits update events
     */
    @KafkaListener(
        topics = "account-limits-updated",
        groupId = "wallet-service-limits-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleAccountLimitsUpdate(
            @Payload AccountLimitsUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Processing account limits update event for user: {}, type: {}", 
            event.getUserId(), event.getLimitType());

        try {
            switch (event.getLimitType()) {
                case "DAILY_TRANSACTION_LIMIT":
                    updateDailyTransactionLimit(event);
                    break;
                case "MONTHLY_TRANSACTION_LIMIT":
                    updateMonthlyTransactionLimit(event);
                    break;
                case "SINGLE_TRANSACTION_LIMIT":
                    updateSingleTransactionLimit(event);
                    break;
                case "WITHDRAWAL_LIMIT":
                    updateWithdrawalLimit(event);
                    break;
                case "DEPOSIT_LIMIT":
                    updateDepositLimit(event);
                    break;
                case "ACCOUNT_BALANCE_LIMIT":
                    updateAccountBalanceLimit(event);
                    break;
                case "VELOCITY_LIMIT":
                    updateVelocityLimit(event);
                    break;
                default:
                    log.warn("Unknown account limit type: {}", event.getLimitType());
            }

            // Acknowledge successful processing
            acknowledgment.acknowledge();
            log.info("Successfully updated {} for user: {}", 
                event.getLimitType(), event.getUserId());

        } catch (Exception e) {
            log.error("Failed to process account limits update for user: {}",
                event.getUserId(), e);

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("Account limits update event sent to DLQ: userId={}, destination={}, category={}",
                        event.getUserId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for account limits update event - MESSAGE MAY BE LOST! " +
                            "userId={}, partition={}, offset={}, error={}",
                            event.getUserId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            // Don't acknowledge - this will trigger retry
            throw e;
        }
    }

    /**
     * Updates daily transaction limit
     */
    private void updateDailyTransactionLimit(AccountLimitsUpdateEvent event) {
        BigDecimal newLimit = new BigDecimal(event.getNewLimitValue().toString());
        BigDecimal oldLimit = event.getOldLimitValue() != null ? 
            new BigDecimal(event.getOldLimitValue().toString()) : BigDecimal.ZERO;
        
        accountLimitsService.updateDailyTransactionLimit(
            event.getUserId(), 
            newLimit, 
            event.getReason(),
            event.getUpdatedBy()
        );

        // Log significant changes
        if (newLimit.compareTo(oldLimit.multiply(new BigDecimal("2"))) > 0) {
            log.warn("Significant daily limit increase for user {}: {} -> {}", 
                event.getUserId(), oldLimit, newLimit);
        }

        // Update user wallet configurations
        walletService.updateWalletLimits(event.getUserId(), "DAILY_LIMIT", newLimit);
    }

    /**
     * Updates monthly transaction limit
     */
    private void updateMonthlyTransactionLimit(AccountLimitsUpdateEvent event) {
        BigDecimal newLimit = new BigDecimal(event.getNewLimitValue().toString());
        
        accountLimitsService.updateMonthlyTransactionLimit(
            event.getUserId(), 
            newLimit, 
            event.getReason(),
            event.getUpdatedBy()
        );

        // Update user wallet configurations
        walletService.updateWalletLimits(event.getUserId(), "MONTHLY_LIMIT", newLimit);
    }

    /**
     * Updates single transaction limit
     */
    private void updateSingleTransactionLimit(AccountLimitsUpdateEvent event) {
        BigDecimal newLimit = new BigDecimal(event.getNewLimitValue().toString());
        
        accountLimitsService.updateSingleTransactionLimit(
            event.getUserId(), 
            newLimit, 
            event.getReason(),
            event.getUpdatedBy()
        );

        // Update user wallet configurations
        walletService.updateWalletLimits(event.getUserId(), "SINGLE_TRANSACTION_LIMIT", newLimit);
    }

    /**
     * Updates withdrawal limit
     */
    private void updateWithdrawalLimit(AccountLimitsUpdateEvent event) {
        BigDecimal newLimit = new BigDecimal(event.getNewLimitValue().toString());
        
        accountLimitsService.updateWithdrawalLimit(
            event.getUserId(), 
            newLimit, 
            event.getReason(),
            event.getUpdatedBy()
        );

        // Update wallet withdrawal settings
        walletService.updateWithdrawalLimits(event.getUserId(), newLimit);
        
        // Send notification for withdrawal limit changes
        walletService.notifyUserOfLimitChange(event.getUserId(), "WITHDRAWAL_LIMIT", newLimit);
    }

    /**
     * Updates deposit limit
     */
    private void updateDepositLimit(AccountLimitsUpdateEvent event) {
        BigDecimal newLimit = new BigDecimal(event.getNewLimitValue().toString());
        
        accountLimitsService.updateDepositLimit(
            event.getUserId(), 
            newLimit, 
            event.getReason(),
            event.getUpdatedBy()
        );

        // Update wallet deposit settings
        walletService.updateDepositLimits(event.getUserId(), newLimit);
    }

    /**
     * Updates account balance limit
     */
    private void updateAccountBalanceLimit(AccountLimitsUpdateEvent event) {
        BigDecimal newLimit = new BigDecimal(event.getNewLimitValue().toString());
        
        accountLimitsService.updateAccountBalanceLimit(
            event.getUserId(), 
            newLimit, 
            event.getReason(),
            event.getUpdatedBy()
        );

        // Check if current balance exceeds new limit
        walletService.validateBalanceAgainstLimit(event.getUserId(), newLimit);
        
        // Update wallet balance settings
        walletService.updateBalanceLimits(event.getUserId(), newLimit);
    }

    /**
     * Updates velocity limit (transactions per time period)
     */
    private void updateVelocityLimit(AccountLimitsUpdateEvent event) {
        Integer newLimit = Integer.valueOf(event.getNewLimitValue().toString());
        
        accountLimitsService.updateVelocityLimit(
            event.getUserId(), 
            newLimit, 
            event.getTimeWindow(),
            event.getReason(),
            event.getUpdatedBy()
        );

        // Update velocity controls in wallet service
        walletService.updateVelocityControls(event.getUserId(), newLimit, event.getTimeWindow());
        
        log.info("Updated velocity limit for user {}: {} transactions per {}", 
            event.getUserId(), newLimit, event.getTimeWindow());
    }
}