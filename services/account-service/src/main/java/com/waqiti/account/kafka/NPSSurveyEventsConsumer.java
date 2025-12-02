package com.waqiti.account.kafka;

import com.waqiti.common.events.NPSSurveyEvent;
import com.waqiti.account.domain.NPSSurvey;
import com.waqiti.account.domain.NPSResponse;
import com.waqiti.account.domain.NPSFollowUp;
import com.waqiti.account.repository.NPSSurveyRepository;
import com.waqiti.account.repository.NPSResponseRepository;
import com.waqiti.account.repository.NPSFollowUpRepository;
import com.waqiti.account.service.NPSService;
import com.waqiti.account.service.CustomerFeedbackService;
import com.waqiti.account.metrics.FeedbackMetricsService;
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
public class NPSSurveyEventsConsumer {
    
    private final NPSSurveyRepository surveyRepository;
    private final NPSResponseRepository responseRepository;
    private final NPSFollowUpRepository followUpRepository;
    private final NPSService npsService;
    private final CustomerFeedbackService feedbackService;
    private final FeedbackMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"nps-survey-events", "customer-feedback-events", "satisfaction-survey-events"},
        groupId = "account-nps-service-group",
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
    public void handleNPSSurveyEvent(
            @Payload NPSSurveyEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("nps-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing NPS survey event: userId={}, type={}", 
            event.getUserId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case SURVEY_TRIGGERED:
                    processSurveyTriggered(event, correlationId);
                    break;
                case SURVEY_SENT:
                    processSurveySent(event, correlationId);
                    break;
                case SURVEY_OPENED:
                    processSurveyOpened(event, correlationId);
                    break;
                case SCORE_SUBMITTED:
                    processScoreSubmitted(event, correlationId);
                    break;
                case FEEDBACK_SUBMITTED:
                    processFeedbackSubmitted(event, correlationId);
                    break;
                case DETRACTOR_IDENTIFIED:
                    processDetractorIdentified(event, correlationId);
                    break;
                case PASSIVE_IDENTIFIED:
                    processPassiveIdentified(event, correlationId);
                    break;
                case PROMOTER_IDENTIFIED:
                    processPromoterIdentified(event, correlationId);
                    break;
                case FOLLOW_UP_REQUIRED:
                    processFollowUpRequired(event, correlationId);
                    break;
                case FOLLOW_UP_COMPLETED:
                    processFollowUpCompleted(event, correlationId);
                    break;
                case NPS_SCORE_CALCULATED:
                    processNPSScoreCalculated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown NPS survey event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "NPS_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "surveyId", event.getSurveyId() != null ? event.getSurveyId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process NPS event: {}", e.getMessage(), e);
            kafkaTemplate.send("nps-survey-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processSurveyTriggered(NPSSurveyEvent event, String correlationId) {
        log.info("NPS survey triggered: userId={}, trigger={}", 
            event.getUserId(), event.getTriggerType());
        
        NPSSurvey survey = NPSSurvey.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .triggerType(event.getTriggerType())
            .triggerEvent(event.getTriggerEvent())
            .triggeredAt(LocalDateTime.now())
            .status("TRIGGERED")
            .channel(event.getChannel())
            .customerSegment(event.getCustomerSegment())
            .correlationId(correlationId)
            .build();
        
        surveyRepository.save(survey);
        npsService.scheduleSurveyDelivery(survey.getId());
        
        metricsService.recordSurveyTriggered(event.getTriggerType());
    }
    
    private void processSurveySent(NPSSurveyEvent event, String correlationId) {
        log.info("NPS survey sent: surveyId={}, userId={}, channel={}", 
            event.getSurveyId(), event.getUserId(), event.getChannel());
        
        NPSSurvey survey = surveyRepository.findById(event.getSurveyId())
            .orElseThrow();
        
        survey.setStatus("SENT");
        survey.setSentAt(LocalDateTime.now());
        survey.setExpiresAt(LocalDateTime.now().plusDays(7));
        surveyRepository.save(survey);
        
        notificationService.sendNotification(
            event.getUserId(),
            "We'd Love Your Feedback!",
            "How likely are you to recommend Waqiti to a friend? It only takes a minute.",
            correlationId
        );
        
        metricsService.recordSurveySent(event.getChannel());
    }
    
    private void processSurveyOpened(NPSSurveyEvent event, String correlationId) {
        log.info("NPS survey opened: surveyId={}", event.getSurveyId());
        
        NPSSurvey survey = surveyRepository.findById(event.getSurveyId())
            .orElseThrow();
        
        survey.setOpened(true);
        survey.setOpenedAt(LocalDateTime.now());
        surveyRepository.save(survey);
        
        metricsService.recordSurveyOpened();
    }
    
    private void processScoreSubmitted(NPSSurveyEvent event, String correlationId) {
        log.info("NPS score submitted: surveyId={}, score={}", 
            event.getSurveyId(), event.getNpsScore());
        
        NPSSurvey survey = surveyRepository.findById(event.getSurveyId())
            .orElseThrow();
        
        survey.setStatus("COMPLETED");
        survey.setCompletedAt(LocalDateTime.now());
        survey.setNpsScore(event.getNpsScore());
        survey.setRespondentCategory(categorizeScore(event.getNpsScore()));
        surveyRepository.save(survey);
        
        NPSResponse response = NPSResponse.builder()
            .id(UUID.randomUUID().toString())
            .surveyId(event.getSurveyId())
            .userId(event.getUserId())
            .npsScore(event.getNpsScore())
            .category(categorizeScore(event.getNpsScore()))
            .submittedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        responseRepository.save(response);
        
        npsService.processResponse(response.getId());
        metricsService.recordScoreSubmitted(event.getNpsScore(), categorizeScore(event.getNpsScore()));
    }
    
    private void processFeedbackSubmitted(NPSSurveyEvent event, String correlationId) {
        log.info("NPS feedback submitted: surveyId={}, hasComment={}", 
            event.getSurveyId(), event.getFeedbackComment() != null);
        
        NPSResponse response = responseRepository.findBySurveyId(event.getSurveyId())
            .orElseThrow();
        
        response.setFeedbackComment(event.getFeedbackComment());
        response.setFeedbackTags(event.getFeedbackTags());
        responseRepository.save(response);
        
        feedbackService.analyzeFeedback(response.getId());
        
        if (event.getFeedbackComment() != null && !event.getFeedbackComment().isEmpty()) {
            metricsService.recordFeedbackWithComment();
        }
    }
    
    private void processDetractorIdentified(NPSSurveyEvent event, String correlationId) {
        log.warn("Detractor identified: userId={}, score={}", 
            event.getUserId(), event.getNpsScore());
        
        NPSFollowUp followUp = NPSFollowUp.builder()
            .id(UUID.randomUUID().toString())
            .surveyId(event.getSurveyId())
            .userId(event.getUserId())
            .category("DETRACTOR")
            .priority("HIGH")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .dueDate(LocalDateTime.now().plusDays(2))
            .assignedTo(event.getAssignedTeam())
            .correlationId(correlationId)
            .build();
        
        followUpRepository.save(followUp);
        
        npsService.escalateToSupport(followUp.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "We're Sorry to Hear That",
            "We take your feedback seriously. A member of our team will reach out to you shortly.",
            correlationId
        );
        
        metricsService.recordDetractorIdentified();
    }
    
    private void processPassiveIdentified(NPSSurveyEvent event, String correlationId) {
        log.info("Passive identified: userId={}, score={}", 
            event.getUserId(), event.getNpsScore());
        
        NPSFollowUp followUp = NPSFollowUp.builder()
            .id(UUID.randomUUID().toString())
            .surveyId(event.getSurveyId())
            .userId(event.getUserId())
            .category("PASSIVE")
            .priority("MEDIUM")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .dueDate(LocalDateTime.now().plusDays(5))
            .correlationId(correlationId)
            .build();
        
        followUpRepository.save(followUp);
        
        npsService.scheduleEngagementCampaign(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Thanks for Your Feedback!",
            "We're always working to improve. Stay tuned for exciting new features!",
            correlationId
        );
        
        metricsService.recordPassiveIdentified();
    }
    
    private void processPromoterIdentified(NPSSurveyEvent event, String correlationId) {
        log.info("Promoter identified: userId={}, score={}", 
            event.getUserId(), event.getNpsScore());
        
        NPSFollowUp followUp = NPSFollowUp.builder()
            .id(UUID.randomUUID().toString())
            .surveyId(event.getSurveyId())
            .userId(event.getUserId())
            .category("PROMOTER")
            .priority("LOW")
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        followUpRepository.save(followUp);
        
        npsService.inviteToReferralProgram(event.getUserId());
        npsService.requestReview(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Thank You for Your Support!",
            "We're thrilled you love Waqiti! Share the love and earn rewards through our referral program.",
            correlationId
        );
        
        metricsService.recordPromoterIdentified();
    }
    
    private void processFollowUpRequired(NPSSurveyEvent event, String correlationId) {
        log.info("Follow-up required: surveyId={}, priority={}", 
            event.getSurveyId(), event.getPriority());
        
        NPSFollowUp followUp = followUpRepository.findBySurveyId(event.getSurveyId())
            .orElseThrow();
        
        followUp.setStatus("IN_PROGRESS");
        followUp.setStartedAt(LocalDateTime.now());
        followUp.setAssignedTo(event.getAssignedTeam());
        followUpRepository.save(followUp);
        
        feedbackService.assignToTeam(followUp.getId(), event.getAssignedTeam());
        metricsService.recordFollowUpStarted(event.getPriority());
    }
    
    private void processFollowUpCompleted(NPSSurveyEvent event, String correlationId) {
        log.info("Follow-up completed: surveyId={}, resolution={}", 
            event.getSurveyId(), event.getResolutionType());
        
        NPSFollowUp followUp = followUpRepository.findBySurveyId(event.getSurveyId())
            .orElseThrow();
        
        followUp.setStatus("COMPLETED");
        followUp.setCompletedAt(LocalDateTime.now());
        followUp.setResolutionType(event.getResolutionType());
        followUp.setResolutionNotes(event.getResolutionNotes());
        followUpRepository.save(followUp);
        
        metricsService.recordFollowUpCompleted(
            followUp.getCategory(),
            event.getResolutionType()
        );
    }
    
    private void processNPSScoreCalculated(NPSSurveyEvent event, String correlationId) {
        log.info("NPS score calculated: period={}, score={}, promoters={}, passives={}, detractors={}", 
            event.getCalculationPeriod(),
            event.getOverallNpsScore(),
            event.getPromoterCount(),
            event.getPassiveCount(),
            event.getDetractorCount());
        
        int totalResponses = event.getPromoterCount() + event.getPassiveCount() + event.getDetractorCount();
        double promoterPercentage = (event.getPromoterCount() * 100.0) / totalResponses;
        double detractorPercentage = (event.getDetractorCount() * 100.0) / totalResponses;
        double calculatedNPS = promoterPercentage - detractorPercentage;
        
        metricsService.recordNPSScoreCalculated(
            event.getCalculationPeriod(),
            calculatedNPS,
            event.getPromoterCount(),
            event.getPassiveCount(),
            event.getDetractorCount(),
            totalResponses
        );
        
        if (calculatedNPS < 0) {
            log.warn("Negative NPS score detected: {}", calculatedNPS);
            npsService.triggerImprovementInitiative();
        } else if (calculatedNPS > 50) {
            log.info("Excellent NPS score: {}", calculatedNPS);
            npsService.celebrateSuccess();
        }
    }
    
    private String categorizeScore(int score) {
        if (score >= 9) return "PROMOTER";
        if (score >= 7) return "PASSIVE";
        return "DETRACTOR";
    }
}