package com.waqiti.account.kafka;

import com.waqiti.common.events.CustomerReactivationEvent;
import com.waqiti.account.domain.ReactivationCampaign;
import com.waqiti.account.repository.ReactivationCampaignRepository;
import com.waqiti.account.service.AccountReactivationService;
import com.waqiti.account.service.CampaignOrchestrationService;
import com.waqiti.account.metrics.AccountMetricsService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerReactivationEventsConsumer {
    
    private final ReactivationCampaignRepository campaignRepository;
    private final AccountReactivationService reactivationService;
    private final CampaignOrchestrationService campaignService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int DORMANCY_THRESHOLD_DAYS = 90;
    private static final int CAMPAIGN_DURATION_DAYS = 30;
    
    @KafkaListener(
        topics = {"customer-reactivation-events", "dormant-user-events", "winback-campaign-events"},
        groupId = "account-reactivation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerReactivationEvent(
            @Payload CustomerReactivationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("reactivation-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing reactivation event: userId={}, type={}", 
            event.getUserId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case DORMANCY_DETECTED:
                    processDormancyDetected(event, correlationId);
                    break;
                case CAMPAIGN_INITIATED:
                    processCampaignInitiated(event, correlationId);
                    break;
                case REACTIVATION_EMAIL_SENT:
                    processReactivationEmailSent(event, correlationId);
                    break;
                case REACTIVATION_LINK_CLICKED:
                    processReactivationLinkClicked(event, correlationId);
                    break;
                case INCENTIVE_OFFERED:
                    processIncentiveOffered(event, correlationId);
                    break;
                case INCENTIVE_CLAIMED:
                    processIncentiveClaimed(event, correlationId);
                    break;
                case ACCOUNT_REACTIVATED:
                    processAccountReactivated(event, correlationId);
                    break;
                case REACTIVATION_SUCCESSFUL:
                    processReactivationSuccessful(event, correlationId);
                    break;
                case CAMPAIGN_FAILED:
                    processCampaignFailed(event, correlationId);
                    break;
                case UNSUBSCRIBED:
                    processUnsubscribed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown reactivation event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "REACTIVATION_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "campaignId", event.getCampaignId() != null ? event.getCampaignId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process reactivation event: {}", e.getMessage(), e);
            kafkaTemplate.send("customer-reactivation-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processDormancyDetected(CustomerReactivationEvent event, String correlationId) {
        log.info("Dormancy detected: userId={}, daysSinceLastActivity={}", 
            event.getUserId(), event.getDaysSinceLastActivity());
        
        ReactivationCampaign campaign = ReactivationCampaign.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .dormancyDetectedAt(LocalDateTime.now())
            .daysSinceLastActivity(event.getDaysSinceLastActivity())
            .lastActivityDate(event.getLastActivityDate())
            .status("DORMANT")
            .dormancyReason(determineDormancyReason(event))
            .userSegment(event.getUserSegment())
            .lifetimeValue(event.getLifetimeValue())
            .correlationId(correlationId)
            .build();
        
        campaignRepository.save(campaign);
        
        if (event.getDaysSinceLastActivity() >= DORMANCY_THRESHOLD_DAYS) {
            campaignService.scheduleCampaign(campaign.getId());
        }
        
        metricsService.recordDormancyDetected(event.getDaysSinceLastActivity(), event.getUserSegment());
    }
    
    private void processCampaignInitiated(CustomerReactivationEvent event, String correlationId) {
        log.info("Reactivation campaign initiated: campaignId={}, userId={}", 
            event.getCampaignId(), event.getUserId());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setStatus("ACTIVE");
        campaign.setCampaignStartedAt(LocalDateTime.now());
        campaign.setCampaignEndDate(LocalDateTime.now().plusDays(CAMPAIGN_DURATION_DAYS));
        campaign.setCampaignType(event.getCampaignType());
        campaign.setIncentiveType(event.getIncentiveType());
        campaign.setIncentiveValue(event.getIncentiveValue());
        campaignRepository.save(campaign);
        
        campaignService.executeCampaign(campaign.getId());
        metricsService.recordCampaignInitiated(event.getCampaignType(), event.getUserSegment());
    }
    
    private void processReactivationEmailSent(CustomerReactivationEvent event, String correlationId) {
        log.info("Reactivation email sent: campaignId={}, userId={}", 
            event.getCampaignId(), event.getUserId());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setEmailSent(true);
        campaign.setEmailSentAt(LocalDateTime.now());
        campaign.setEmailCount(campaign.getEmailCount() + 1);
        campaign.setReactivationLink(event.getReactivationLink());
        campaignRepository.save(campaign);
        
        notificationService.sendNotification(
            event.getUserId(),
            "We Miss You!",
            "Come back and enjoy exclusive offers. Your account is waiting for you.",
            correlationId
        );
        
        metricsService.recordReactivationEmailSent(event.getCampaignType());
    }
    
    private void processReactivationLinkClicked(CustomerReactivationEvent event, String correlationId) {
        log.info("Reactivation link clicked: campaignId={}, userId={}", 
            event.getCampaignId(), event.getUserId());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setLinkClicked(true);
        campaign.setLinkClickedAt(LocalDateTime.now());
        campaign.setClickCount(campaign.getClickCount() + 1);
        campaignRepository.save(campaign);
        
        if (campaign.getIncentiveType() != null) {
            reactivationService.presentIncentive(event.getUserId(), campaign.getId());
        }
        
        metricsService.recordReactivationLinkClicked(event.getCampaignType());
    }
    
    private void processIncentiveOffered(CustomerReactivationEvent event, String correlationId) {
        log.info("Incentive offered: campaignId={}, type={}, value={}", 
            event.getCampaignId(), event.getIncentiveType(), event.getIncentiveValue());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setIncentiveOffered(true);
        campaign.setIncentiveOfferedAt(LocalDateTime.now());
        campaign.setIncentiveExpiryDate(LocalDateTime.now().plusDays(14));
        campaignRepository.save(campaign);
        
        String incentiveMessage = formatIncentiveMessage(event.getIncentiveType(), event.getIncentiveValue());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Special Offer Just for You!",
            incentiveMessage,
            correlationId
        );
        
        metricsService.recordIncentiveOffered(event.getIncentiveType(), event.getIncentiveValue());
    }
    
    private void processIncentiveClaimed(CustomerReactivationEvent event, String correlationId) {
        log.info("Incentive claimed: campaignId={}, userId={}", 
            event.getCampaignId(), event.getUserId());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setIncentiveClaimed(true);
        campaign.setIncentiveClaimedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
        
        reactivationService.applyIncentive(event.getUserId(), campaign.getId());
        metricsService.recordIncentiveClaimed(event.getIncentiveType());
    }
    
    private void processAccountReactivated(CustomerReactivationEvent event, String correlationId) {
        log.info("Account reactivated: userId={}, campaignId={}", 
            event.getUserId(), event.getCampaignId());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setAccountReactivated(true);
        campaign.setAccountReactivatedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
        
        reactivationService.reactivateAccount(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Welcome Back!",
            "Great to see you again! Your account is now active.",
            correlationId
        );
        
        metricsService.recordAccountReactivated();
    }
    
    private void processReactivationSuccessful(CustomerReactivationEvent event, String correlationId) {
        log.info("Reactivation successful: userId={}, campaignId={}", 
            event.getUserId(), event.getCampaignId());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setStatus("SUCCESSFUL");
        campaign.setCampaignCompletedAt(LocalDateTime.now());
        
        Duration campaignDuration = Duration.between(
            campaign.getCampaignStartedAt(), 
            campaign.getCampaignCompletedAt()
        );
        campaign.setCampaignDuration(campaignDuration);
        
        campaignRepository.save(campaign);
        
        metricsService.recordReactivationSuccessful(
            campaign.getCampaignType(),
            campaign.getUserSegment(),
            campaignDuration.toDays(),
            campaign.getEmailCount(),
            campaign.getIncentiveType()
        );
    }
    
    private void processCampaignFailed(CustomerReactivationEvent event, String correlationId) {
        log.info("Reactivation campaign failed: campaignId={}, reason={}", 
            event.getCampaignId(), event.getFailureReason());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setStatus("FAILED");
        campaign.setFailureReason(event.getFailureReason());
        campaign.setCampaignCompletedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
        
        metricsService.recordReactivationFailed(
            campaign.getCampaignType(),
            event.getFailureReason()
        );
    }
    
    private void processUnsubscribed(CustomerReactivationEvent event, String correlationId) {
        log.info("User unsubscribed from reactivation campaigns: userId={}", event.getUserId());
        
        ReactivationCampaign campaign = campaignRepository.findById(event.getCampaignId())
            .orElseThrow();
        
        campaign.setStatus("UNSUBSCRIBED");
        campaign.setUnsubscribedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
        
        campaignService.disableCampaigns(event.getUserId());
        metricsService.recordReactivationUnsubscribed();
    }
    
    private String determineDormancyReason(CustomerReactivationEvent event) {
        int days = event.getDaysSinceLastActivity();
        
        if (days >= 365) {
            return "LONG_TERM_DORMANT";
        } else if (days >= 180) {
            return "MEDIUM_TERM_DORMANT";
        } else if (days >= 90) {
            return "SHORT_TERM_DORMANT";
        } else {
            return "AT_RISK";
        }
    }
    
    private String formatIncentiveMessage(String incentiveType, String incentiveValue) {
        return switch (incentiveType) {
            case "CASH_BONUS" -> String.format("Get %s cash bonus when you reactivate your account!", incentiveValue);
            case "FEE_WAIVER" -> String.format("Enjoy %s fee waiver for your first 3 months!", incentiveValue);
            case "INTEREST_BOOST" -> String.format("Earn %s bonus interest on your savings!", incentiveValue);
            case "CASHBACK" -> String.format("Get %s cashback on your first transaction!", incentiveValue);
            default -> "Special offer waiting for you!";
        };
    }
}