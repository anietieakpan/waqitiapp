package com.waqiti.account.kafka;

import com.waqiti.common.events.CustomerOnboardingEvent;
import com.waqiti.account.domain.OnboardingJourney;
import com.waqiti.account.domain.OnboardingStep;
import com.waqiti.account.repository.OnboardingJourneyRepository;
import com.waqiti.account.service.OnboardingOrchestrationService;
import com.waqiti.account.service.KYCOrchestrationService;
import com.waqiti.account.service.AccountProvisioningService;
import com.waqiti.account.metrics.OnboardingMetricsService;
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
public class CustomerOnboardingEventsConsumer {
    
    private final OnboardingJourneyRepository journeyRepository;
    private final OnboardingOrchestrationService orchestrationService;
    private final KYCOrchestrationService kycService;
    private final AccountProvisioningService provisioningService;
    private final OnboardingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"customer-onboarding-events", "account-signup-events", "kyc-onboarding-events"},
        groupId = "account-onboarding-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerOnboardingEvent(
            @Payload CustomerOnboardingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("onboarding-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing onboarding event: userId={}, type={}, step={}", 
            event.getUserId(), event.getEventType(), event.getOnboardingStep());
        
        try {
            switch (event.getEventType()) {
                case ONBOARDING_STARTED:
                    processOnboardingStarted(event, correlationId);
                    break;
                case STEP_COMPLETED:
                    processStepCompleted(event, correlationId);
                    break;
                case STEP_FAILED:
                    processStepFailed(event, correlationId);
                    break;
                case EMAIL_VERIFIED:
                    processEmailVerified(event, correlationId);
                    break;
                case PHONE_VERIFIED:
                    processPhoneVerified(event, correlationId);
                    break;
                case IDENTITY_VERIFICATION_INITIATED:
                    processIdentityVerificationInitiated(event, correlationId);
                    break;
                case IDENTITY_VERIFICATION_COMPLETED:
                    processIdentityVerificationCompleted(event, correlationId);
                    break;
                case DOCUMENT_UPLOADED:
                    processDocumentUploaded(event, correlationId);
                    break;
                case DOCUMENT_VERIFIED:
                    processDocumentVerified(event, correlationId);
                    break;
                case ADDRESS_VERIFIED:
                    processAddressVerified(event, correlationId);
                    break;
                case FUNDING_SOURCE_ADDED:
                    processFundingSourceAdded(event, correlationId);
                    break;
                case ACCOUNT_CREATED:
                    processAccountCreated(event, correlationId);
                    break;
                case ONBOARDING_COMPLETED:
                    processOnboardingCompleted(event, correlationId);
                    break;
                case ONBOARDING_ABANDONED:
                    processOnboardingAbandoned(event, correlationId);
                    break;
                default:
                    log.warn("Unknown onboarding event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "ONBOARDING_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "step", event.getOnboardingStep(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process onboarding event: {}", e.getMessage(), e);
            kafkaTemplate.send("customer-onboarding-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processOnboardingStarted(CustomerOnboardingEvent event, String correlationId) {
        log.info("Customer onboarding started: userId={}, channel={}", 
            event.getUserId(), event.getChannel());
        
        OnboardingJourney journey = OnboardingJourney.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .email(event.getEmail())
            .phoneNumber(event.getPhoneNumber())
            .channel(event.getChannel())
            .deviceType(event.getDeviceType())
            .startedAt(LocalDateTime.now())
            .status("IN_PROGRESS")
            .currentStep("EMAIL_VERIFICATION")
            .completionPercentage(0)
            .referralCode(event.getReferralCode())
            .correlationId(correlationId)
            .build();
        
        journey.addStep(OnboardingStep.builder()
            .stepName("EMAIL_VERIFICATION")
            .status("PENDING")
            .startedAt(LocalDateTime.now())
            .build());
        
        journeyRepository.save(journey);
        
        orchestrationService.sendVerificationEmail(event.getUserId(), event.getEmail());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Welcome to Waqiti!",
            "Let's get your account set up. First, verify your email address.",
            correlationId
        );
        
        metricsService.recordOnboardingStarted(event.getChannel(), event.getDeviceType());
    }
    
    private void processStepCompleted(CustomerOnboardingEvent event, String correlationId) {
        log.info("Onboarding step completed: userId={}, step={}", 
            event.getUserId(), event.getOnboardingStep());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.completeStep(event.getOnboardingStep(), true);
        journey.setCompletionPercentage(calculateCompletionPercentage(journey));
        
        String nextStep = determineNextStep(journey);
        if (nextStep != null) {
            journey.setCurrentStep(nextStep);
            journey.addStep(OnboardingStep.builder()
                .stepName(nextStep)
                .status("PENDING")
                .startedAt(LocalDateTime.now())
                .build());
            
            orchestrationService.triggerNextStep(event.getUserId(), nextStep);
        }
        
        journeyRepository.save(journey);
        metricsService.recordStepCompleted(event.getOnboardingStep(), journey.getChannel());
    }
    
    private void processStepFailed(CustomerOnboardingEvent event, String correlationId) {
        log.warn("Onboarding step failed: userId={}, step={}, reason={}", 
            event.getUserId(), event.getOnboardingStep(), event.getFailureReason());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.completeStep(event.getOnboardingStep(), false);
        journey.setFailureReason(event.getFailureReason());
        journey.setRetryCount(journey.getRetryCount() + 1);
        
        if (journey.getRetryCount() >= 3) {
            journey.setStatus("FAILED");
            journey.setCompletedAt(LocalDateTime.now());
            
            notificationService.sendNotification(
                event.getUserId(),
                "Onboarding Issue",
                "We're having trouble completing your account setup. Please contact support.",
                correlationId
            );
        } else {
            orchestrationService.scheduleRetry(event.getUserId(), event.getOnboardingStep());
        }
        
        journeyRepository.save(journey);
        metricsService.recordStepFailed(event.getOnboardingStep(), event.getFailureReason());
    }
    
    private void processEmailVerified(CustomerOnboardingEvent event, String correlationId) {
        log.info("Email verified: userId={}", event.getUserId());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setEmailVerified(true);
        journey.setEmailVerifiedAt(LocalDateTime.now());
        journey.completeStep("EMAIL_VERIFICATION", true);
        journeyRepository.save(journey);
        
        orchestrationService.triggerNextStep(event.getUserId(), "PHONE_VERIFICATION");
        metricsService.recordEmailVerified();
    }
    
    private void processPhoneVerified(CustomerOnboardingEvent event, String correlationId) {
        log.info("Phone verified: userId={}", event.getUserId());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setPhoneVerified(true);
        journey.setPhoneVerifiedAt(LocalDateTime.now());
        journey.completeStep("PHONE_VERIFICATION", true);
        journeyRepository.save(journey);
        
        orchestrationService.triggerNextStep(event.getUserId(), "IDENTITY_VERIFICATION");
        metricsService.recordPhoneVerified();
    }
    
    private void processIdentityVerificationInitiated(CustomerOnboardingEvent event, String correlationId) {
        log.info("Identity verification initiated: userId={}, provider={}", 
            event.getUserId(), event.getVerificationProvider());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setIdentityVerificationProvider(event.getVerificationProvider());
        journey.setIdentityVerificationStartedAt(LocalDateTime.now());
        journeyRepository.save(journey);
        
        kycService.initiateIdentityVerification(event.getUserId(), event.getVerificationProvider());
        metricsService.recordIdentityVerificationInitiated(event.getVerificationProvider());
    }
    
    private void processIdentityVerificationCompleted(CustomerOnboardingEvent event, String correlationId) {
        log.info("Identity verification completed: userId={}, result={}", 
            event.getUserId(), event.getVerificationResult());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setIdentityVerificationResult(event.getVerificationResult());
        journey.setIdentityVerificationCompletedAt(LocalDateTime.now());
        
        if ("VERIFIED".equals(event.getVerificationResult())) {
            journey.setIdentityVerified(true);
            journey.completeStep("IDENTITY_VERIFICATION", true);
            orchestrationService.triggerNextStep(event.getUserId(), "FUNDING_SOURCE");
        } else {
            journey.completeStep("IDENTITY_VERIFICATION", false);
            journey.setFailureReason("Identity verification failed: " + event.getVerificationResult());
        }
        
        journeyRepository.save(journey);
        metricsService.recordIdentityVerificationCompleted(event.getVerificationResult());
    }
    
    private void processDocumentUploaded(CustomerOnboardingEvent event, String correlationId) {
        log.info("Document uploaded: userId={}, docType={}", 
            event.getUserId(), event.getDocumentType());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.addDocument(event.getDocumentType(), event.getDocumentId());
        journeyRepository.save(journey);
        
        kycService.verifyDocument(event.getDocumentId());
        metricsService.recordDocumentUploaded(event.getDocumentType());
    }
    
    private void processDocumentVerified(CustomerOnboardingEvent event, String correlationId) {
        log.info("Document verified: userId={}, docType={}, result={}", 
            event.getUserId(), event.getDocumentType(), event.getVerificationResult());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.markDocumentVerified(event.getDocumentType(), event.getVerificationResult());
        journeyRepository.save(journey);
        
        if (journey.areAllDocumentsVerified()) {
            journey.completeStep("DOCUMENT_VERIFICATION", true);
            journeyRepository.save(journey);
        }
        
        metricsService.recordDocumentVerified(event.getDocumentType(), event.getVerificationResult());
    }
    
    private void processAddressVerified(CustomerOnboardingEvent event, String correlationId) {
        log.info("Address verified: userId={}", event.getUserId());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setAddressVerified(true);
        journey.setAddressVerifiedAt(LocalDateTime.now());
        journey.completeStep("ADDRESS_VERIFICATION", true);
        journeyRepository.save(journey);
        
        metricsService.recordAddressVerified();
    }
    
    private void processFundingSourceAdded(CustomerOnboardingEvent event, String correlationId) {
        log.info("Funding source added: userId={}, type={}", 
            event.getUserId(), event.getFundingSourceType());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setFundingSourceAdded(true);
        journey.setFundingSourceType(event.getFundingSourceType());
        journey.completeStep("FUNDING_SOURCE", true);
        journeyRepository.save(journey);
        
        orchestrationService.triggerNextStep(event.getUserId(), "ACCOUNT_CREATION");
        metricsService.recordFundingSourceAdded(event.getFundingSourceType());
    }
    
    private void processAccountCreated(CustomerOnboardingEvent event, String correlationId) {
        log.info("Account created: userId={}, accountId={}", 
            event.getUserId(), event.getAccountId());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setAccountCreated(true);
        journey.setAccountId(event.getAccountId());
        journey.setAccountCreatedAt(LocalDateTime.now());
        journey.completeStep("ACCOUNT_CREATION", true);
        journeyRepository.save(journey);
        
        provisioningService.provisionAccount(event.getAccountId());
        metricsService.recordAccountCreated();
    }
    
    private void processOnboardingCompleted(CustomerOnboardingEvent event, String correlationId) {
        log.info("Onboarding completed: userId={}", event.getUserId());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setStatus("COMPLETED");
        journey.setCompletedAt(LocalDateTime.now());
        journey.setCompletionPercentage(100);
        
        Duration onboardingDuration = Duration.between(journey.getStartedAt(), journey.getCompletedAt());
        journey.setOnboardingDuration(onboardingDuration);
        
        journeyRepository.save(journey);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Welcome to Waqiti!",
            "Your account is ready! Start exploring your financial dashboard.",
            correlationId
        );
        
        metricsService.recordOnboardingCompleted(
            journey.getChannel(), 
            onboardingDuration.toMinutes(),
            journey.getRetryCount()
        );
    }
    
    private void processOnboardingAbandoned(CustomerOnboardingEvent event, String correlationId) {
        log.info("Onboarding abandoned: userId={}, lastStep={}", 
            event.getUserId(), event.getOnboardingStep());
        
        OnboardingJourney journey = journeyRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        journey.setStatus("ABANDONED");
        journey.setAbandonedAt(LocalDateTime.now());
        journey.setAbandonedStep(event.getOnboardingStep());
        journeyRepository.save(journey);
        
        orchestrationService.scheduleReengagementCampaign(event.getUserId(), event.getOnboardingStep());
        metricsService.recordOnboardingAbandoned(event.getOnboardingStep(), journey.getChannel());
    }
    
    private int calculateCompletionPercentage(OnboardingJourney journey) {
        int totalSteps = 8;
        int completedSteps = (int) journey.getSteps().stream()
            .filter(step -> "COMPLETED".equals(step.getStatus()))
            .count();
        
        return (completedSteps * 100) / totalSteps;
    }
    
    private String determineNextStep(OnboardingJourney journey) {
        if (!journey.isEmailVerified()) return "EMAIL_VERIFICATION";
        if (!journey.isPhoneVerified()) return "PHONE_VERIFICATION";
        if (!journey.isIdentityVerified()) return "IDENTITY_VERIFICATION";
        if (!journey.isAddressVerified()) return "ADDRESS_VERIFICATION";
        if (!journey.isFundingSourceAdded()) return "FUNDING_SOURCE";
        if (!journey.isAccountCreated()) return "ACCOUNT_CREATION";
        return null;
    }
}