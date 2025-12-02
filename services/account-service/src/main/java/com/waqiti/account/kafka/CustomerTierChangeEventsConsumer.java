package com.waqiti.account.kafka;

import com.waqiti.common.events.CustomerTierChangeEvent;
import com.waqiti.account.domain.TierChange;
import com.waqiti.account.repository.TierChangeRepository;
import com.waqiti.account.service.TierManagementService;
import com.waqiti.account.service.BenefitsService;
import com.waqiti.account.service.PricingService;
import com.waqiti.account.metrics.TierMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

/**
 * Customer Tier Change Events Consumer
 * Processes customer tier upgrades and downgrades (Basic → Premium → VIP)
 * Implements 12-step zero-tolerance processing for tier transitions
 * 
 * Business Context:
 * - Tier structure: Basic → Silver → Gold → Platinum → VIP/Diamond
 * - Automatic upgrades based on: spending, transaction volume, balance, tenure
 * - Manual upgrades: Customer requests, special promotions, retention offers
 * - Benefit changes: Fees, interest rates, rewards, limits, customer support
 * - Revenue impact: Tier changes affect pricing and profitability
 * 
 * @author Waqiti Account Management Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerTierChangeEventsConsumer {
    
    private final TierChangeRepository tierChangeRepository;
    private final TierManagementService tierManagementService;
    private final BenefitsService benefitsService;
    private final PricingService pricingService;
    private final TierMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"customer-tier-change-events", "tier-upgrade-events", "tier-downgrade-events", "vip-status-events"},
        groupId = "account-tier-change-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerTierChangeEvent(
            @Payload CustomerTierChangeEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("tier-change-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing tier change event: userId={}, from={}, to={}, type={}", 
            event.getUserId(), event.getCurrentTier(), event.getNewTier(), event.getChangeType());
        
        try {
            switch (event.getEventType()) {
                case TIER_UPGRADE_TRIGGERED:
                    processTierUpgradeTriggered(event, correlationId);
                    break;
                case TIER_DOWNGRADE_TRIGGERED:
                    processTierDowngradeTriggered(event, correlationId);
                    break;
                case TIER_CHANGE_APPROVED:
                    processTierChangeApproved(event, correlationId);
                    break;
                case BENEFITS_UPDATED:
                    processBenefitsUpdated(event, correlationId);
                    break;
                case PRICING_UPDATED:
                    processPricingUpdated(event, correlationId);
                    break;
                case LIMITS_UPDATED:
                    processLimitsUpdated(event, correlationId);
                    break;
                case TIER_CHANGE_COMPLETED:
                    processTierChangeCompleted(event, correlationId);
                    break;
                case VIP_STATUS_GRANTED:
                    processVIPStatusGranted(event, correlationId);
                    break;
                case VIP_STATUS_REVOKED:
                    processVIPStatusRevoked(event, correlationId);
                    break;
                default:
                    log.warn("Unknown tier change event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "TIER_CHANGE_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "currentTier", event.getCurrentTier(),
                    "newTier", event.getNewTier(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process tier change event: {}", e.getMessage(), e);
            kafkaTemplate.send("customer-tier-change-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processTierUpgradeTriggered(CustomerTierChangeEvent event, String correlationId) {
        log.info("Tier upgrade triggered: userId={}, from={} to {}, reason={}", 
            event.getUserId(), event.getCurrentTier(), event.getNewTier(), event.getTriggerReason());
        
        TierChange tierChange = TierChange.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .currentTier(event.getCurrentTier())
            .newTier(event.getNewTier())
            .changeType("UPGRADE")
            .triggerType(event.getTriggerType()) // AUTO, MANUAL, PROMOTIONAL
            .triggerReason(event.getTriggerReason())
            .qualificationMetrics(event.getQualificationMetrics())
            .triggeredAt(LocalDateTime.now())
            .status("TRIGGERED")
            .effectiveDate(event.getEffectiveDate())
            .correlationId(correlationId)
            .build();
        
        tierChangeRepository.save(tierChange);
        
        boolean approved = tierManagementService.evaluateUpgradeEligibility(tierChange.getId());
        
        if (approved) {
            tierManagementService.initiateUpgradeProcess(tierChange.getId());
        } else {
            log.warn("Tier upgrade not approved: userId={}, reason=Eligibility criteria not met", 
                event.getUserId());
            tierChange.setStatus("REJECTED");
            tierChangeRepository.save(tierChange);
        }
        
        metricsService.recordTierUpgradeTriggered(event.getCurrentTier(), event.getNewTier(), 
            event.getTriggerType());
    }
    
    private void processTierDowngradeTriggered(CustomerTierChangeEvent event, String correlationId) {
        log.warn("Tier downgrade triggered: userId={}, from={} to {}, reason={}", 
            event.getUserId(), event.getCurrentTier(), event.getNewTier(), event.getTriggerReason());
        
        TierChange tierChange = TierChange.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .currentTier(event.getCurrentTier())
            .newTier(event.getNewTier())
            .changeType("DOWNGRADE")
            .triggerType(event.getTriggerType())
            .triggerReason(event.getTriggerReason())
            .triggeredAt(LocalDateTime.now())
            .status("TRIGGERED")
            .effectiveDate(event.getEffectiveDate())
            .gracePeriodDays(event.getGracePeriodDays())
            .correlationId(correlationId)
            .build();
        
        tierChangeRepository.save(tierChange);
        
        // Offer retention campaign before downgrade
        boolean retentionSuccessful = tierManagementService.offerRetentionIncentive(
            event.getUserId(), tierChange.getId());
        
        if (!retentionSuccessful) {
            tierManagementService.scheduleDowngrade(tierChange.getId(), event.getEffectiveDate());
            
            notificationService.sendNotification(
                event.getUserId(),
                "Account Tier Update Notice",
                String.format("Your account tier will change from %s to %s on %s. " +
                    "To maintain your current tier, please review the requirements.",
                    event.getCurrentTier(), event.getNewTier(), event.getEffectiveDate()),
                correlationId
            );
        }
        
        metricsService.recordTierDowngradeTriggered(event.getCurrentTier(), event.getNewTier(), 
            event.getTriggerReason());
    }
    
    private void processTierChangeApproved(CustomerTierChangeEvent event, String correlationId) {
        log.info("Tier change approved: tierChangeId={}", event.getTierChangeId());
        
        TierChange tierChange = tierChangeRepository.findById(event.getTierChangeId())
            .orElseThrow();
        
        tierChange.setStatus("APPROVED");
        tierChange.setApprovedAt(LocalDateTime.now());
        tierChange.setApprovedBy(event.getApprovedBy());
        tierChangeRepository.save(tierChange);
        
        benefitsService.prepareBenefitsUpdate(tierChange.getId());
        pricingService.preparePricingUpdate(tierChange.getId());
        
        metricsService.recordTierChangeApproved(tierChange.getChangeType());
    }
    
    private void processBenefitsUpdated(CustomerTierChangeEvent event, String correlationId) {
        log.info("Benefits updated: userId={}, newTier={}", event.getUserId(), event.getNewTier());
        
        TierChange tierChange = tierChangeRepository.findById(event.getTierChangeId())
            .orElseThrow();
        
        tierChange.setBenefitsUpdated(true);
        tierChange.setBenefitsUpdatedAt(LocalDateTime.now());
        tierChange.setNewBenefits(event.getNewBenefits());
        tierChange.setRemovedBenefits(event.getRemovedBenefits());
        tierChangeRepository.save(tierChange);
        
        benefitsService.activateNewBenefits(event.getUserId(), event.getNewBenefits());
        benefitsService.deactivateOldBenefits(event.getUserId(), event.getRemovedBenefits());
        
        metricsService.recordBenefitsUpdated();
    }
    
    private void processPricingUpdated(CustomerTierChangeEvent event, String correlationId) {
        log.info("Pricing updated: userId={}, newTier={}", event.getUserId(), event.getNewTier());
        
        TierChange tierChange = tierChangeRepository.findById(event.getTierChangeId())
            .orElseThrow();
        
        tierChange.setPricingUpdated(true);
        tierChange.setPricingUpdatedAt(LocalDateTime.now());
        tierChange.setOldMonthlyFee(event.getOldMonthlyFee());
        tierChange.setNewMonthlyFee(event.getNewMonthlyFee());
        tierChange.setOldTransactionFees(event.getOldTransactionFees());
        tierChange.setNewTransactionFees(event.getNewTransactionFees());
        tierChangeRepository.save(tierChange);
        
        pricingService.updateAccountPricing(event.getUserId(), event.getNewTier());
        
        if (event.getNewMonthlyFee().compareTo(event.getOldMonthlyFee()) < 0) {
            log.info("Tier upgrade resulted in fee reduction: old={}, new={}", 
                event.getOldMonthlyFee(), event.getNewMonthlyFee());
        }
        
        metricsService.recordPricingUpdated(event.getOldMonthlyFee(), event.getNewMonthlyFee());
    }
    
    private void processLimitsUpdated(CustomerTierChangeEvent event, String correlationId) {
        log.info("Limits updated: userId={}, newTier={}", event.getUserId(), event.getNewTier());
        
        TierChange tierChange = tierChangeRepository.findById(event.getTierChangeId())
            .orElseThrow();
        
        tierChange.setLimitsUpdated(true);
        tierChange.setLimitsUpdatedAt(LocalDateTime.now());
        tierChange.setOldTransactionLimit(event.getOldTransactionLimit());
        tierChange.setNewTransactionLimit(event.getNewTransactionLimit());
        tierChange.setOldDailyLimit(event.getOldDailyLimit());
        tierChange.setNewDailyLimit(event.getNewDailyLimit());
        tierChangeRepository.save(tierChange);
        
        tierManagementService.updateTransactionLimits(event.getUserId(), 
            event.getNewTransactionLimit(), event.getNewDailyLimit());
        
        metricsService.recordLimitsUpdated();
    }
    
    private void processTierChangeCompleted(CustomerTierChangeEvent event, String correlationId) {
        log.info("Tier change completed: userId={}, newTier={}", event.getUserId(), event.getNewTier());
        
        TierChange tierChange = tierChangeRepository.findById(event.getTierChangeId())
            .orElseThrow();
        
        tierChange.setStatus("COMPLETED");
        tierChange.setCompletedAt(LocalDateTime.now());
        tierChangeRepository.save(tierChange);

        tierManagementService.finalizeTierChange(tierChange.getId());
        
        String congratulationsMessage = tierChange.getChangeType().equals("UPGRADE") ?
            String.format("Congratulations! You've been upgraded to %s tier. " +
                "Enjoy your new benefits including %s.",
                event.getNewTier(), formatBenefitsSummary(event.getNewBenefits())) :
            String.format("Your account tier has been updated to %s effective %s.",
                event.getNewTier(), event.getEffectiveDate());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Tier Updated",
            congratulationsMessage,
            correlationId
        );
        
        metricsService.recordTierChangeCompleted(
            event.getCurrentTier(), 
            event.getNewTier(), 
            tierChange.getChangeType()
        );
    }
    
    private void processVIPStatusGranted(CustomerTierChangeEvent event, String correlationId) {
        log.info("VIP status granted: userId={}, vipLevel={}", event.getUserId(), event.getVipLevel());
        
        TierChange tierChange = TierChange.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .currentTier(event.getCurrentTier())
            .newTier("VIP_" + event.getVipLevel())
            .changeType("VIP_UPGRADE")
            .triggeredAt(LocalDateTime.now())
            .status("COMPLETED")
            .completedAt(LocalDateTime.now())
            .vipStatus(true)
            .vipLevel(event.getVipLevel())
            .vipPerks(event.getVipPerks())
            .personalAccountManager(event.getPersonalAccountManager())
            .correlationId(correlationId)
            .build();
        
        tierChangeRepository.save(tierChange);
        
        tierManagementService.activateVIPStatus(event.getUserId(), event.getVipLevel());
        tierManagementService.assignPersonalAccountManager(event.getUserId(), 
            event.getPersonalAccountManager());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Welcome to VIP Status!",
            String.format("Congratulations! You've been granted %s VIP status. " +
                "Your personal account manager %s will contact you shortly. " +
                "Exclusive perks: %s",
                event.getVipLevel(), event.getPersonalAccountManager(), 
                String.join(", ", event.getVipPerks())),
            correlationId
        );
        
        metricsService.recordVIPStatusGranted(event.getVipLevel());
    }
    
    private void processVIPStatusRevoked(CustomerTierChangeEvent event, String correlationId) {
        log.warn("VIP status revoked: userId={}, reason={}", event.getUserId(), event.getRevocationReason());
        
        TierChange tierChange = TierChange.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .currentTier("VIP")
            .newTier(event.getNewTier())
            .changeType("VIP_REVOCATION")
            .triggeredAt(LocalDateTime.now())
            .status("COMPLETED")
            .completedAt(LocalDateTime.now())
            .vipStatus(false)
            .revocationReason(event.getRevocationReason())
            .correlationId(correlationId)
            .build();
        
        tierChangeRepository.save(tierChange);
        
        tierManagementService.revokeVIPStatus(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "VIP Status Update",
            "Your VIP status has been updated. You've been moved to " + event.getNewTier() + 
                " tier. Contact us if you have questions.",
            correlationId
        );
        
        metricsService.recordVIPStatusRevoked(event.getRevocationReason());
    }
    
    private String formatBenefitsSummary(List<String> benefits) {
        if (benefits == null || benefits.isEmpty()) {
            return "enhanced features and exclusive benefits";
        }
        
        if (benefits.size() <= 3) {
            return String.join(", ", benefits);
        }
        
        return String.join(", ", benefits.subList(0, 3)) + " and more";
    }
}