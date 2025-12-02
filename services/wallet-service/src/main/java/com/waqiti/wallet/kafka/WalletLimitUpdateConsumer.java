package com.waqiti.wallet.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.event.WalletLimitUpdateEvent;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.LimitManagementService;
import com.waqiti.wallet.service.ComplianceService;
import com.waqiti.wallet.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for wallet limit updates
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLimitUpdateConsumer {

    private final WalletService walletService;
    private final LimitManagementService limitService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-limit-updates", groupId = "limit-processor")
    public void processLimitUpdate(
            ConsumerRecord<String, WalletLimitUpdateEvent> record,
            @Payload WalletLimitUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        try {
            log.info("Processing limit update for wallet: {} type: {} new limit: {}",
                    event.getWalletId(), event.getLimitType(), event.getNewLimit());
            
            // Validate event
            validateLimitUpdate(event);
            
            // Check authorization
            if (!isAuthorizedUpdate(event)) {
                log.error("Unauthorized limit update attempt for wallet: {}", event.getWalletId());
                handleUnauthorizedUpdate(event);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on limit type
            switch (event.getLimitType()) {
                case "TRANSACTION_LIMIT" -> updateTransactionLimit(event);
                case "DAILY_LIMIT" -> updateDailyLimit(event);
                case "MONTHLY_LIMIT" -> updateMonthlyLimit(event);
                case "WITHDRAWAL_LIMIT" -> updateWithdrawalLimit(event);
                case "TRANSFER_LIMIT" -> updateTransferLimit(event);
                case "PAYMENT_LIMIT" -> updatePaymentLimit(event);
                case "INTERNATIONAL_LIMIT" -> updateInternationalLimit(event);
                case "ATM_LIMIT" -> updateAtmLimit(event);
                default -> handleGenericLimit(event);
            }
            
            // Apply compliance checks
            applyComplianceChecks(event);
            
            // Update dependent limits
            updateDependentLimits(event);
            
            // Send notifications
            sendLimitUpdateNotifications(event);
            
            // Create audit trail
            createAuditTrail(event);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully updated limit for wallet: {}", event.getWalletId());
            
        } catch (Exception e) {
            log.error("Error processing wallet event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet event - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet event processing failed", e);
        }
    }

    private void validateLimitUpdate(WalletLimitUpdateEvent event) {
        if (event.getWalletId() == null || event.getWalletId().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        
        if (event.getLimitType() == null || event.getLimitType().trim().isEmpty()) {
            throw new IllegalArgumentException("Limit type is required");
        }
        
        if (event.getNewLimit() == null || event.getNewLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("New limit must be non-negative");
        }
    }

    private boolean isAuthorizedUpdate(WalletLimitUpdateEvent event) {
        // Check if update is from authorized source
        if (event.getAuthorizedBy() == null) {
            return false;
        }
        
        // Verify authorization level
        return limitService.verifyAuthorizationLevel(
            event.getAuthorizedBy(),
            event.getLimitType(),
            event.getNewLimit(),
            event.getPreviousLimit()
        );
    }

    private void updateTransactionLimit(WalletLimitUpdateEvent event) {
        // Get current limit
        BigDecimal currentLimit = limitService.getTransactionLimit(event.getWalletId());
        
        // Validate limit increase
        if (event.getNewLimit().compareTo(currentLimit) > 0) {
            validateLimitIncrease(event, currentLimit);
        }
        
        // Update limit
        limitService.updateTransactionLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getCurrency(),
            event.getEffectiveDate(),
            event.getExpiryDate()
        );
        
        // Update transaction velocity checks
        walletService.updateTransactionVelocityRules(
            event.getWalletId(),
            event.getNewLimit()
        );
        
        log.info("Updated transaction limit for wallet: {} from {} to {}", 
                event.getWalletId(), currentLimit, event.getNewLimit());
    }

    private void updateDailyLimit(WalletLimitUpdateEvent event) {
        // Get current daily limit and usage
        BigDecimal currentLimit = limitService.getDailyLimit(event.getWalletId());
        BigDecimal dailyUsage = walletService.getDailyUsage(event.getWalletId());
        
        // Check if new limit accommodates current usage
        if (event.getNewLimit().compareTo(dailyUsage) < 0) {
            log.warn("New daily limit {} is less than current usage {} for wallet: {}", 
                    event.getNewLimit(), dailyUsage, event.getWalletId());
            
            if (!event.isForceUpdate()) {
                throw new IllegalStateException("New limit less than current daily usage");
            }
        }
        
        // Update limit
        limitService.updateDailyLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getResetTime()
        );
        
        // Reset counters if requested
        if (event.isResetCounters()) {
            walletService.resetDailyCounters(event.getWalletId());
        }
    }

    private void updateMonthlyLimit(WalletLimitUpdateEvent event) {
        // Get current monthly limit and usage
        BigDecimal currentLimit = limitService.getMonthlyLimit(event.getWalletId());
        BigDecimal monthlyUsage = walletService.getMonthlyUsage(event.getWalletId());
        
        // Validate new limit
        if (event.getNewLimit().compareTo(monthlyUsage) < 0 && !event.isForceUpdate()) {
            throw new IllegalStateException("New limit less than current monthly usage");
        }
        
        // Update limit
        limitService.updateMonthlyLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getBillingCycle()
        );
        
        // Update spending analytics
        walletService.updateSpendingAnalytics(
            event.getWalletId(),
            event.getNewLimit(),
            monthlyUsage
        );
    }

    private void updateWithdrawalLimit(WalletLimitUpdateEvent event) {
        // Update withdrawal limit
        limitService.updateWithdrawalLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getWithdrawalType(),
            event.getChannels()
        );
        
        // Update ATM withdrawal rules
        if (event.getChannels() != null && event.getChannels().contains("ATM")) {
            walletService.updateAtmWithdrawalRules(
                event.getWalletId(),
                event.getNewLimit(),
                event.getDailyAtmLimit()
            );
        }
        
        // Update cash withdrawal rules
        if (event.getChannels() != null && event.getChannels().contains("CASH")) {
            walletService.updateCashWithdrawalRules(
                event.getWalletId(),
                event.getNewLimit()
            );
        }
    }

    private void updateTransferLimit(WalletLimitUpdateEvent event) {
        // Update transfer limits
        limitService.updateTransferLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getTransferType(),
            event.getRecipientType()
        );
        
        // Update P2P transfer limits
        if ("P2P".equals(event.getTransferType())) {
            walletService.updateP2PTransferLimit(
                event.getWalletId(),
                event.getNewLimit(),
                event.getDailyP2PLimit()
            );
        }
        
        // Update bank transfer limits
        if ("BANK".equals(event.getTransferType())) {
            walletService.updateBankTransferLimit(
                event.getWalletId(),
                event.getNewLimit(),
                event.getDomesticLimit(),
                event.getInternationalLimit()
            );
        }
    }

    private void updatePaymentLimit(WalletLimitUpdateEvent event) {
        // Update payment limits
        limitService.updatePaymentLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getPaymentType(),
            event.getMerchantCategory()
        );
        
        // Update online payment limits
        if ("ONLINE".equals(event.getPaymentType())) {
            walletService.updateOnlinePaymentLimit(
                event.getWalletId(),
                event.getNewLimit(),
                event.getPerTransactionLimit()
            );
        }
        
        // Update POS limits
        if ("POS".equals(event.getPaymentType())) {
            walletService.updatePosLimit(
                event.getWalletId(),
                event.getNewLimit(),
                event.getContactlessLimit()
            );
        }
    }

    private void updateInternationalLimit(WalletLimitUpdateEvent event) {
        // Update international transaction limits
        limitService.updateInternationalLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getAllowedCountries(),
            event.getBlockedCountries()
        );
        
        // Update currency conversion limits
        if (event.getCurrencyLimits() != null) {
            for (Map.Entry<String, BigDecimal> entry : event.getCurrencyLimits().entrySet()) {
                walletService.updateCurrencyLimit(
                    event.getWalletId(),
                    entry.getKey(),
                    entry.getValue()
                );
            }
        }
        
        // Update FX rate limits
        if (event.getFxMarkupLimit() != null) {
            walletService.updateFxMarkupLimit(
                event.getWalletId(),
                event.getFxMarkupLimit()
            );
        }
    }

    private void updateAtmLimit(WalletLimitUpdateEvent event) {
        // Update ATM specific limits
        limitService.updateAtmLimit(
            event.getWalletId(),
            event.getNewLimit(),
            event.getDailyAtmTransactions(),
            event.getAtmNetworks()
        );
        
        // Update per-transaction ATM limit
        if (event.getPerTransactionAtmLimit() != null) {
            walletService.updatePerTransactionAtmLimit(
                event.getWalletId(),
                event.getPerTransactionAtmLimit()
            );
        }
        
        // Update ATM fee limits
        if (event.getMaxAtmFee() != null) {
            walletService.updateMaxAtmFee(
                event.getWalletId(),
                event.getMaxAtmFee()
            );
        }
    }

    private void handleGenericLimit(WalletLimitUpdateEvent event) {
        // Update generic limit
        limitService.updateGenericLimit(
            event.getWalletId(),
            event.getLimitType(),
            event.getNewLimit(),
            event.getMetadata()
        );
    }

    private void validateLimitIncrease(WalletLimitUpdateEvent event, BigDecimal currentLimit) {
        // Calculate increase percentage
        BigDecimal increasePercent = event.getNewLimit().subtract(currentLimit)
            .divide(currentLimit, 2, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        // Check if increase requires additional verification
        if (increasePercent.compareTo(BigDecimal.valueOf(50)) > 0) {
            // Require additional verification
            if (!event.isAdditionalVerificationCompleted()) {
                throw new IllegalStateException("Large limit increase requires additional verification");
            }
        }
        
        // Check against maximum allowed limits
        BigDecimal maxAllowedLimit = limitService.getMaxAllowedLimit(
            event.getWalletId(),
            event.getLimitType(),
            event.getAccountTier()
        );
        
        if (event.getNewLimit().compareTo(maxAllowedLimit) > 0) {
            throw new IllegalStateException("New limit exceeds maximum allowed: " + maxAllowedLimit);
        }
    }

    private void applyComplianceChecks(WalletLimitUpdateEvent event) {
        // Check regulatory compliance
        boolean compliant = complianceService.checkLimitCompliance(
            event.getWalletId(),
            event.getLimitType(),
            event.getNewLimit(),
            event.getJurisdiction()
        );
        
        if (!compliant) {
            log.error("Limit update violates compliance rules for wallet: {}", event.getWalletId());
            
            // Report compliance violation
            complianceService.reportLimitViolation(
                event.getWalletId(),
                event.getLimitType(),
                event.getNewLimit(),
                event.getComplianceRules()
            );
            
            if (!event.isOverrideCompliance()) {
                throw new IllegalStateException("Limit update violates compliance rules");
            }
        }
        
        // Check AML limits
        if (event.getNewLimit().compareTo(event.getAmlThreshold()) > 0) {
            complianceService.flagForAmlReview(
                event.getWalletId(),
                event.getLimitType(),
                event.getNewLimit()
            );
        }
    }

    private void updateDependentLimits(WalletLimitUpdateEvent event) {
        // Get dependent limits
        Map<String, BigDecimal> dependentLimits = limitService.getDependentLimits(
            event.getLimitType()
        );
        
        // Update each dependent limit
        for (Map.Entry<String, BigDecimal> entry : dependentLimits.entrySet()) {
            String dependentType = entry.getKey();
            BigDecimal ratio = entry.getValue();
            
            BigDecimal dependentLimit = event.getNewLimit().multiply(ratio);
            
            limitService.updateDependentLimit(
                event.getWalletId(),
                dependentType,
                dependentLimit
            );
        }
    }

    private void sendLimitUpdateNotifications(WalletLimitUpdateEvent event) {
        // Send primary notification
        notificationService.sendLimitUpdateNotification(
            event.getWalletId(),
            event.getLimitType(),
            event.getPreviousLimit(),
            event.getNewLimit(),
            event.getEffectiveDate()
        );
        
        // Send SMS if significant change
        BigDecimal changePercent = calculateChangePercent(event.getPreviousLimit(), event.getNewLimit());
        if (changePercent.compareTo(BigDecimal.valueOf(25)) > 0) {
            notificationService.sendLimitChangeSms(
                event.getWalletId(),
                event.getLimitType(),
                event.getNewLimit()
            );
        }
        
        // Send email with details
        notificationService.sendLimitChangeEmail(
            event.getWalletId(),
            event.getLimitType(),
            event.getPreviousLimit(),
            event.getNewLimit(),
            event.getReason(),
            event.getAuthorizedBy()
        );
    }

    private BigDecimal calculateChangePercent(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        return current.subtract(previous)
            .divide(previous, 2, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .abs();
    }

    private void createAuditTrail(WalletLimitUpdateEvent event) {
        limitService.createAuditEntry(
            event.getWalletId(),
            event.getLimitType(),
            event.getPreviousLimit(),
            event.getNewLimit(),
            event.getReason(),
            event.getAuthorizedBy(),
            event.getApprovedBy(),
            LocalDateTime.now()
        );
    }

    private void handleUnauthorizedUpdate(WalletLimitUpdateEvent event) {
        // Log security incident
        complianceService.logSecurityIncident(
            event.getWalletId(),
            "UNAUTHORIZED_LIMIT_UPDATE",
            event.getLimitType(),
            event.getNewLimit(),
            event.getAuthorizedBy()
        );
        
        // Send security alert
        notificationService.sendSecurityAlert(
            event.getWalletId(),
            "Unauthorized limit update attempt",
            event.getLimitType()
        );
    }
}