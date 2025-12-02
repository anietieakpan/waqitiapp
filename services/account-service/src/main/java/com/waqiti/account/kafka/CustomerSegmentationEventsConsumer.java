package com.waqiti.account.kafka;

import com.waqiti.common.events.CustomerSegmentationEvent;
import com.waqiti.account.domain.CustomerSegment;
import com.waqiti.account.domain.SegmentMembership;
import com.waqiti.account.repository.CustomerSegmentRepository;
import com.waqiti.account.repository.SegmentMembershipRepository;
import com.waqiti.account.service.SegmentationService;
import com.waqiti.account.service.PersonalizationService;
import com.waqiti.account.metrics.SegmentationMetricsService;
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
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerSegmentationEventsConsumer {
    
    private final CustomerSegmentRepository segmentRepository;
    private final SegmentMembershipRepository membershipRepository;
    private final SegmentationService segmentationService;
    private final PersonalizationService personalizationService;
    private final SegmentationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"customer-segmentation-events", "user-profiling-events", "segment-assignment-events"},
        groupId = "account-segmentation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerSegmentationEvent(
            @Payload CustomerSegmentationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("segmentation-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing segmentation event: userId={}, type={}", 
            event.getUserId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case SEGMENT_CREATED:
                    processSegmentCreated(event, correlationId);
                    break;
                case USER_ASSIGNED_TO_SEGMENT:
                    processUserAssignedToSegment(event, correlationId);
                    break;
                case USER_REMOVED_FROM_SEGMENT:
                    processUserRemovedFromSegment(event, correlationId);
                    break;
                case SEGMENT_CRITERIA_UPDATED:
                    processSegmentCriteriaUpdated(event, correlationId);
                    break;
                case BEHAVIORAL_SEGMENT_DETECTED:
                    processBehavioralSegmentDetected(event, correlationId);
                    break;
                case VALUE_SEGMENT_UPDATED:
                    processValueSegmentUpdated(event, correlationId);
                    break;
                case LIFECYCLE_STAGE_CHANGED:
                    processLifecycleStageChanged(event, correlationId);
                    break;
                case RISK_PROFILE_UPDATED:
                    processRiskProfileUpdated(event, correlationId);
                    break;
                case ENGAGEMENT_SCORE_CALCULATED:
                    processEngagementScoreCalculated(event, correlationId);
                    break;
                case CHURN_RISK_DETECTED:
                    processChurnRiskDetected(event, correlationId);
                    break;
                default:
                    log.warn("Unknown segmentation event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "SEGMENTATION_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "segmentId", event.getSegmentId() != null ? event.getSegmentId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process segmentation event: {}", e.getMessage(), e);
            kafkaTemplate.send("customer-segmentation-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processSegmentCreated(CustomerSegmentationEvent event, String correlationId) {
        log.info("Segment created: segmentId={}, name={}", 
            event.getSegmentId(), event.getSegmentName());
        
        CustomerSegment segment = CustomerSegment.builder()
            .id(event.getSegmentId())
            .name(event.getSegmentName())
            .description(event.getSegmentDescription())
            .segmentType(event.getSegmentType())
            .criteria(event.getSegmentCriteria())
            .createdAt(LocalDateTime.now())
            .active(true)
            .memberCount(0)
            .correlationId(correlationId)
            .build();
        
        segmentRepository.save(segment);
        segmentationService.evaluateExistingUsers(segment.getId());
        
        metricsService.recordSegmentCreated(event.getSegmentType());
    }
    
    private void processUserAssignedToSegment(CustomerSegmentationEvent event, String correlationId) {
        log.info("User assigned to segment: userId={}, segmentId={}, segmentName={}", 
            event.getUserId(), event.getSegmentId(), event.getSegmentName());
        
        SegmentMembership membership = SegmentMembership.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .segmentId(event.getSegmentId())
            .segmentName(event.getSegmentName())
            .assignedAt(LocalDateTime.now())
            .assignmentReason(event.getAssignmentReason())
            .score(event.getSegmentScore())
            .active(true)
            .correlationId(correlationId)
            .build();
        
        membershipRepository.save(membership);
        
        CustomerSegment segment = segmentRepository.findById(event.getSegmentId())
            .orElseThrow();
        segment.incrementMemberCount();
        segmentRepository.save(segment);
        
        personalizationService.applySegmentPersonalization(event.getUserId(), event.getSegmentId());
        
        if (shouldNotifyUser(event.getSegmentName())) {
            notificationService.sendNotification(
                event.getUserId(),
                getSegmentWelcomeTitle(event.getSegmentName()),
                getSegmentWelcomeMessage(event.getSegmentName()),
                correlationId
            );
        }
        
        metricsService.recordUserAssignedToSegment(event.getSegmentName());
    }
    
    private void processUserRemovedFromSegment(CustomerSegmentationEvent event, String correlationId) {
        log.info("User removed from segment: userId={}, segmentId={}", 
            event.getUserId(), event.getSegmentId());
        
        SegmentMembership membership = membershipRepository
            .findByUserIdAndSegmentId(event.getUserId(), event.getSegmentId())
            .orElseThrow();
        
        membership.setActive(false);
        membership.setRemovedAt(LocalDateTime.now());
        membership.setRemovalReason(event.getRemovalReason());
        membershipRepository.save(membership);
        
        CustomerSegment segment = segmentRepository.findById(event.getSegmentId())
            .orElseThrow();
        segment.decrementMemberCount();
        segmentRepository.save(segment);
        
        personalizationService.removeSegmentPersonalization(event.getUserId(), event.getSegmentId());
        metricsService.recordUserRemovedFromSegment(event.getSegmentName());
    }
    
    private void processSegmentCriteriaUpdated(CustomerSegmentationEvent event, String correlationId) {
        log.info("Segment criteria updated: segmentId={}", event.getSegmentId());
        
        CustomerSegment segment = segmentRepository.findById(event.getSegmentId())
            .orElseThrow();
        
        segment.setCriteria(event.getSegmentCriteria());
        segment.setUpdatedAt(LocalDateTime.now());
        segmentRepository.save(segment);
        
        segmentationService.reevaluateSegmentMembers(segment.getId());
        metricsService.recordSegmentCriteriaUpdated();
    }
    
    private void processBehavioralSegmentDetected(CustomerSegmentationEvent event, String correlationId) {
        log.info("Behavioral segment detected: userId={}, behavior={}", 
            event.getUserId(), event.getBehaviorType());
        
        String segmentName = determineBehavioralSegment(event.getBehaviorType());
        
        SegmentMembership membership = SegmentMembership.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .segmentName(segmentName)
            .assignedAt(LocalDateTime.now())
            .assignmentReason("Behavioral pattern: " + event.getBehaviorType())
            .behaviorMetrics(event.getBehaviorMetrics())
            .active(true)
            .correlationId(correlationId)
            .build();
        
        membershipRepository.save(membership);
        personalizationService.applyBehavioralPersonalization(event.getUserId(), event.getBehaviorType());
        
        metricsService.recordBehavioralSegmentDetected(event.getBehaviorType());
    }
    
    private void processValueSegmentUpdated(CustomerSegmentationEvent event, String correlationId) {
        log.info("Value segment updated: userId={}, ltv={}, tier={}", 
            event.getUserId(), event.getLifetimeValue(), event.getValueTier());
        
        membershipRepository.findActiveByUserId(event.getUserId())
            .stream()
            .filter(m -> m.getSegmentName().startsWith("VALUE_"))
            .forEach(m -> {
                m.setActive(false);
                m.setRemovedAt(LocalDateTime.now());
                membershipRepository.save(m);
            });
        
        String newSegmentName = "VALUE_" + event.getValueTier();
        SegmentMembership membership = SegmentMembership.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .segmentName(newSegmentName)
            .assignedAt(LocalDateTime.now())
            .lifetimeValue(event.getLifetimeValue())
            .valueTier(event.getValueTier())
            .active(true)
            .correlationId(correlationId)
            .build();
        
        membershipRepository.save(membership);
        personalizationService.applyValueTierBenefits(event.getUserId(), event.getValueTier());
        
        metricsService.recordValueSegmentUpdated(event.getValueTier());
    }
    
    private void processLifecycleStageChanged(CustomerSegmentationEvent event, String correlationId) {
        log.info("Lifecycle stage changed: userId={}, from={}, to={}", 
            event.getUserId(), event.getPreviousLifecycleStage(), event.getCurrentLifecycleStage());
        
        SegmentMembership membership = SegmentMembership.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .segmentName("LIFECYCLE_" + event.getCurrentLifecycleStage())
            .assignedAt(LocalDateTime.now())
            .previousStage(event.getPreviousLifecycleStage())
            .currentStage(event.getCurrentLifecycleStage())
            .active(true)
            .correlationId(correlationId)
            .build();
        
        membershipRepository.save(membership);
        personalizationService.applyLifecyclePersonalization(event.getUserId(), event.getCurrentLifecycleStage());
        
        metricsService.recordLifecycleStageChanged(
            event.getPreviousLifecycleStage(),
            event.getCurrentLifecycleStage()
        );
    }
    
    private void processRiskProfileUpdated(CustomerSegmentationEvent event, String correlationId) {
        log.info("Risk profile updated: userId={}, riskLevel={}, score={}", 
            event.getUserId(), event.getRiskLevel(), event.getRiskScore());
        
        SegmentMembership membership = SegmentMembership.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .segmentName("RISK_" + event.getRiskLevel())
            .assignedAt(LocalDateTime.now())
            .riskLevel(event.getRiskLevel())
            .riskScore(event.getRiskScore())
            .riskFactors(event.getRiskFactors())
            .active(true)
            .correlationId(correlationId)
            .build();
        
        membershipRepository.save(membership);
        
        if ("HIGH".equals(event.getRiskLevel()) || "CRITICAL".equals(event.getRiskLevel())) {
            segmentationService.applyRiskControls(event.getUserId(), event.getRiskLevel());
        }
        
        metricsService.recordRiskProfileUpdated(event.getRiskLevel());
    }
    
    private void processEngagementScoreCalculated(CustomerSegmentationEvent event, String correlationId) {
        log.info("Engagement score calculated: userId={}, score={}, level={}", 
            event.getUserId(), event.getEngagementScore(), event.getEngagementLevel());
        
        String engagementSegment = determineEngagementSegment(event.getEngagementScore());
        
        SegmentMembership membership = SegmentMembership.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .segmentName(engagementSegment)
            .assignedAt(LocalDateTime.now())
            .engagementScore(event.getEngagementScore())
            .engagementLevel(event.getEngagementLevel())
            .active(true)
            .correlationId(correlationId)
            .build();
        
        membershipRepository.save(membership);
        
        if ("LOW".equals(event.getEngagementLevel())) {
            segmentationService.triggerEngagementCampaign(event.getUserId());
        }
        
        metricsService.recordEngagementScoreCalculated(event.getEngagementLevel());
    }
    
    private void processChurnRiskDetected(CustomerSegmentationEvent event, String correlationId) {
        log.warn("Churn risk detected: userId={}, riskLevel={}, probability={}", 
            event.getUserId(), event.getChurnRiskLevel(), event.getChurnProbability());
        
        SegmentMembership membership = SegmentMembership.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .segmentName("CHURN_RISK_" + event.getChurnRiskLevel())
            .assignedAt(LocalDateTime.now())
            .churnProbability(event.getChurnProbability())
            .churnIndicators(event.getChurnIndicators())
            .active(true)
            .correlationId(correlationId)
            .build();
        
        membershipRepository.save(membership);
        
        if (event.getChurnProbability() > 0.7) {
            segmentationService.triggerRetentionCampaign(event.getUserId(), event.getChurnRiskLevel());
            
            notificationService.sendNotification(
                event.getUserId(),
                "We Value Your Business",
                "We noticed you haven't been as active lately. Let us know how we can serve you better.",
                correlationId
            );
        }
        
        metricsService.recordChurnRiskDetected(event.getChurnRiskLevel(), event.getChurnProbability());
    }
    
    private String determineBehavioralSegment(String behaviorType) {
        return switch (behaviorType) {
            case "HIGH_FREQUENCY_TRANSACTOR" -> "BEHAVIOR_POWER_USER";
            case "SAVINGS_FOCUSED" -> "BEHAVIOR_SAVER";
            case "INVESTMENT_FOCUSED" -> "BEHAVIOR_INVESTOR";
            case "INTERNATIONAL_USER" -> "BEHAVIOR_GLOBAL";
            case "MOBILE_FIRST" -> "BEHAVIOR_MOBILE_NATIVE";
            default -> "BEHAVIOR_STANDARD";
        };
    }
    
    private String determineEngagementSegment(double engagementScore) {
        if (engagementScore >= 80) return "ENGAGEMENT_HIGHLY_ENGAGED";
        if (engagementScore >= 60) return "ENGAGEMENT_MODERATELY_ENGAGED";
        if (engagementScore >= 40) return "ENGAGEMENT_LIGHTLY_ENGAGED";
        return "ENGAGEMENT_AT_RISK";
    }
    
    private boolean shouldNotifyUser(String segmentName) {
        return segmentName.startsWith("VALUE_PREMIUM") ||
               segmentName.startsWith("VALUE_VIP") ||
               segmentName.equals("LIFECYCLE_LOYAL");
    }
    
    private String getSegmentWelcomeTitle(String segmentName) {
        if (segmentName.startsWith("VALUE_VIP")) return "Welcome to VIP Status!";
        if (segmentName.startsWith("VALUE_PREMIUM")) return "You're Now a Premium Member!";
        if (segmentName.equals("LIFECYCLE_LOYAL")) return "Thank You for Your Loyalty!";
        return "Welcome!";
    }
    
    private String getSegmentWelcomeMessage(String segmentName) {
        if (segmentName.startsWith("VALUE_VIP")) {
            return "Congratulations! You've reached VIP status. Enjoy exclusive benefits and priority support.";
        }
        if (segmentName.startsWith("VALUE_PREMIUM")) {
            return "You've been upgraded to Premium membership. Enjoy enhanced features and rewards.";
        }
        if (segmentName.equals("LIFECYCLE_LOYAL")) {
            return "We appreciate your continued trust. Here's a special thank you from our team.";
        }
        return "Welcome to your new segment!";
    }
}