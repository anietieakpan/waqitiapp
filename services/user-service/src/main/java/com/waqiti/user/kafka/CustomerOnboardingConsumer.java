package com.waqiti.user.kafka;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.user.CustomerOnboardingEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.UserProfile;
import com.waqiti.user.domain.OnboardingStatus;
import com.waqiti.user.domain.OnboardingStep;
import com.waqiti.user.domain.UserTier;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.UserProfileRepository;
import com.waqiti.user.repository.OnboardingProgressRepository;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.OnboardingService;
import com.waqiti.user.service.KycService;
import com.waqiti.user.service.WalletCreationService;
import com.waqiti.user.service.ReferralService;
import com.waqiti.user.service.WelcomeService;
import com.waqiti.user.service.OnboardingNotificationService;
import com.waqiti.common.exceptions.OnboardingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade consumer for customer onboarding events.
 * Handles comprehensive user onboarding including:
 * - Account creation and activation
 * - Profile setup and preferences
 * - KYC initiation and tracking
 * - Wallet creation and funding
 * - Welcome bonuses and referral rewards
 * - Onboarding progress tracking
 * 
 * Critical for user acquisition and activation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerOnboardingConsumer {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final OnboardingProgressRepository progressRepository;
    private final UserService userService;
    private final OnboardingService onboardingService;
    private final KycService kycService;
    private final WalletCreationService walletService;
    private final ReferralService referralService;
    private final WelcomeService welcomeService;
    private final OnboardingNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;
    private final UniversalDLQHandler dlqHandler;

    private static final int ONBOARDING_TIMEOUT_HOURS = 72;
    private static final int MIN_PASSWORD_LENGTH = 12;

    @KafkaListener(
        topics = "customer-onboarding",
        groupId = "user-service-onboarding-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        include = {OnboardingException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleCustomerOnboarding(
            ConsumerRecord<String, CustomerOnboardingEvent> record,
            @Payload CustomerOnboardingEvent onboardingEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "onboarding-channel", required = false) String channel,
            Acknowledgment acknowledgment) {

        String eventId = onboardingEvent.getEventId() != null ? 
            onboardingEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing customer onboarding: {} for email: {} channel: {}", 
                    eventId, onboardingEvent.getEmail(), onboardingEvent.getOnboardingChannel());

            // Metrics tracking
            metricsService.incrementCounter("onboarding.processing.started",
                Map.of(
                    "channel", onboardingEvent.getOnboardingChannel(),
                    "user_type", onboardingEvent.getUserType()
                ));

            // Idempotency check
            if (isOnboardingAlreadyProcessed(onboardingEvent.getEmail(), eventId)) {
                log.info("Onboarding {} already processed for email {}", eventId, onboardingEvent.getEmail());
                acknowledgment.acknowledge();
                return;
            }

            // Create user account
            User user = createUserAccount(onboardingEvent, eventId, correlationId);

            // Create user profile
            UserProfile profile = createUserProfile(user, onboardingEvent);

            // Initialize onboarding progress
            var onboardingProgress = initializeOnboardingProgress(user, onboardingEvent);

            // Execute onboarding steps in parallel
            List<CompletableFuture<OnboardingStepResult>> onboardingTasks = 
                createOnboardingTasks(user, profile, onboardingEvent);

            // Wait for critical tasks to complete
            CompletableFuture<Void> criticalTasks = CompletableFuture.allOf(
                onboardingTasks.stream()
                    .filter(task -> !task.isDone())
                    .toArray(CompletableFuture[]::new)
            );

            criticalTasks.join();

            // Process onboarding results
            processOnboardingResults(onboardingProgress, onboardingTasks);

            // Handle referral if applicable
            if (onboardingEvent.getReferralCode() != null) {
                processReferral(user, onboardingEvent);
            }

            // Apply welcome benefits
            applyWelcomeBenefits(user, onboardingEvent);

            // Update onboarding status
            updateOnboardingStatus(onboardingProgress, user);

            // Save all entities
            User savedUser = userRepository.save(user);
            UserProfile savedProfile = profileRepository.save(profile);
            var savedProgress = progressRepository.save(onboardingProgress);

            // Send onboarding notifications
            sendOnboardingNotifications(savedUser, savedProfile, onboardingEvent);

            // Update metrics
            updateOnboardingMetrics(savedProgress, onboardingEvent);

            // Create comprehensive audit trail
            createOnboardingAuditLog(savedUser, savedProgress, onboardingEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("onboarding.processing.success",
                Map.of(
                    "channel", onboardingEvent.getOnboardingChannel(),
                    "completion_rate", String.valueOf(savedProgress.getCompletionPercentage())
                ));

            log.info("Successfully processed onboarding: {} for user: {} completion: {}%", 
                    eventId, savedUser.getId(), savedProgress.getCompletionPercentage());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Sent to DLQ: {}", result.getDestinationTopic()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ failed", dlqError);
                    return null;
                });

            throw new RuntimeException("Processing failed", e);
        }
    }

    @KafkaListener(
        topics = "onboarding-fast-track",
        groupId = "user-service-fast-track-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleFastTrackOnboarding(
            ConsumerRecord<String, CustomerOnboardingEvent> record,
            @Payload CustomerOnboardingEvent onboardingEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("FAST-TRACK ONBOARDING: Processing expedited onboarding for: {}",
                    onboardingEvent.getEmail());

            // Simplified onboarding for premium users
            User user = performFastTrackOnboarding(onboardingEvent, correlationId);

            // Immediate wallet creation
            walletService.createPremiumWallet(user.getId());

            // Send welcome package
            welcomeService.sendPremiumWelcomePackage(user);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Sent to DLQ: {}", result.getDestinationTopic()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ failed", dlqError);
                    return null;
                });

            throw new RuntimeException("Processing failed", e);
        }
    }

    private boolean isOnboardingAlreadyProcessed(String email, String eventId) {
        return userRepository.existsByEmail(email) || 
               progressRepository.existsByEventId(eventId);
    }

    private User createUserAccount(CustomerOnboardingEvent event, String eventId, String correlationId) {
        // Validate email uniqueness
        if (userRepository.existsByEmail(event.getEmail())) {
            throw new OnboardingException("Email already registered: " + event.getEmail());
        }

        // Validate phone uniqueness
        if (event.getPhoneNumber() != null && userRepository.existsByPhoneNumber(event.getPhoneNumber())) {
            throw new OnboardingException("Phone number already registered");
        }

        User user = User.builder()
            .id(UUID.randomUUID().toString())
            .email(event.getEmail())
            .phoneNumber(event.getPhoneNumber())
            .firstName(event.getFirstName())
            .lastName(event.getLastName())
            .username(generateUsername(event))
            .userType(event.getUserType())
            .tier(UserTier.BASIC)
            .onboardingChannel(event.getOnboardingChannel())
            .registrationIp(event.getIpAddress())
            .registrationDevice(event.getDeviceId())
            .referralCode(generateReferralCode())
            .isActive(false)
            .isEmailVerified(false)
            .isPhoneVerified(false)
            .termsAccepted(event.isTermsAccepted())
            .termsAcceptedAt(event.isTermsAccepted() ? LocalDateTime.now() : null)
            .marketingOptIn(event.isMarketingOptIn())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Set password if provided
        if (event.getPassword() != null) {
            validatePassword(event.getPassword());
            user.setPasswordHash(userService.hashPassword(event.getPassword()));
        }

        return user;
    }

    private UserProfile createUserProfile(User user, CustomerOnboardingEvent event) {
        return UserProfile.builder()
            .id(UUID.randomUUID().toString())
            .userId(user.getId())
            .dateOfBirth(event.getDateOfBirth())
            .nationality(event.getNationality())
            .country(event.getCountry())
            .state(event.getState())
            .city(event.getCity())
            .postalCode(event.getPostalCode())
            .language(event.getPreferredLanguage())
            .timezone(event.getTimezone())
            .currency(event.getPreferredCurrency())
            .occupation(event.getOccupation())
            .employmentStatus(event.getEmploymentStatus())
            .annualIncome(event.getAnnualIncome())
            .sourceOfFunds(event.getSourceOfFunds())
            .purposeOfAccount(event.getPurposeOfAccount())
            .expectedMonthlyVolume(event.getExpectedMonthlyVolume())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private OnboardingProgress initializeOnboardingProgress(User user, CustomerOnboardingEvent event) {
        var progress = new OnboardingProgress();
        progress.setId(UUID.randomUUID().toString());
        progress.setUserId(user.getId());
        progress.setEventId(event.getEventId());
        progress.setCurrentStep(OnboardingStep.ACCOUNT_CREATED);
        progress.setCompletedSteps(new ArrayList<>(List.of(OnboardingStep.ACCOUNT_CREATED)));
        progress.setPendingSteps(determinePendingSteps(event));
        progress.setStartedAt(LocalDateTime.now());
        progress.setExpiresAt(LocalDateTime.now().plusHours(ONBOARDING_TIMEOUT_HOURS));
        return progress;
    }

    private List<CompletableFuture<OnboardingStepResult>> createOnboardingTasks(
            User user, UserProfile profile, CustomerOnboardingEvent event) {
        
        List<CompletableFuture<OnboardingStepResult>> tasks = new ArrayList<>();

        // Email verification
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performEmailVerification(user, event)));

        // Phone verification (if provided)
        if (event.getPhoneNumber() != null) {
            tasks.add(CompletableFuture.supplyAsync(() -> 
                performPhoneVerification(user, event)));
        }

        // Create wallet
        tasks.add(CompletableFuture.supplyAsync(() -> 
            createUserWallet(user, event)));

        // Initiate KYC
        if (event.isInitiateKyc()) {
            tasks.add(CompletableFuture.supplyAsync(() -> 
                initiateKycProcess(user, profile, event)));
        }

        // Setup preferences
        tasks.add(CompletableFuture.supplyAsync(() -> 
            setupUserPreferences(user, profile, event)));

        // Device registration
        tasks.add(CompletableFuture.supplyAsync(() -> 
            registerUserDevice(user, event)));

        return tasks;
    }

    private OnboardingStepResult performEmailVerification(User user, CustomerOnboardingEvent event) {
        try {
            log.info("Performing email verification for user: {}", user.getId());

            String verificationToken = userService.generateEmailVerificationToken(user.getId());
            boolean sent = notificationService.sendEmailVerification(user.getEmail(), verificationToken);

            if (sent) {
                user.setEmailVerificationToken(verificationToken);
                user.setEmailVerificationSentAt(LocalDateTime.now());
            }

            return OnboardingStepResult.success(OnboardingStep.EMAIL_VERIFICATION, sent);

        } catch (Exception e) {
            log.error("Email verification failed: {}", e.getMessage());
            return OnboardingStepResult.failure(OnboardingStep.EMAIL_VERIFICATION, e.getMessage());
        }
    }

    private OnboardingStepResult performPhoneVerification(User user, CustomerOnboardingEvent event) {
        try {
            log.info("Performing phone verification for user: {}", user.getId());

            String otp = userService.generatePhoneOtp(user.getId());
            boolean sent = notificationService.sendSmsOtp(user.getPhoneNumber(), otp);

            if (sent) {
                user.setPhoneVerificationOtp(otp);
                user.setPhoneVerificationSentAt(LocalDateTime.now());
            }

            return OnboardingStepResult.success(OnboardingStep.PHONE_VERIFICATION, sent);

        } catch (Exception e) {
            log.error("Phone verification failed: {}", e.getMessage());
            return OnboardingStepResult.failure(OnboardingStep.PHONE_VERIFICATION, e.getMessage());
        }
    }

    private OnboardingStepResult createUserWallet(User user, CustomerOnboardingEvent event) {
        try {
            log.info("Creating wallet for user: {}", user.getId());

            var wallet = walletService.createWallet(
                user.getId(),
                event.getPreferredCurrency(),
                event.getInitialDepositAmount()
            );

            user.setWalletId(wallet.getWalletId());
            user.setWalletCreatedAt(LocalDateTime.now());

            return OnboardingStepResult.success(OnboardingStep.WALLET_CREATION, true);

        } catch (Exception e) {
            log.error("Wallet creation failed: {}", e.getMessage());
            return OnboardingStepResult.failure(OnboardingStep.WALLET_CREATION, e.getMessage());
        }
    }

    private OnboardingStepResult initiateKycProcess(User user, UserProfile profile, CustomerOnboardingEvent event) {
        try {
            log.info("Initiating KYC for user: {}", user.getId());

            var kycSession = kycService.initiateKyc(
                user.getId(),
                profile.getNationality(),
                event.getKycLevel()
            );

            user.setKycSessionId(kycSession.getSessionId());
            user.setKycInitiatedAt(LocalDateTime.now());

            return OnboardingStepResult.success(OnboardingStep.KYC_INITIATED, true);

        } catch (Exception e) {
            log.error("KYC initiation failed: {}", e.getMessage());
            return OnboardingStepResult.failure(OnboardingStep.KYC_INITIATED, e.getMessage());
        }
    }

    private OnboardingStepResult setupUserPreferences(User user, UserProfile profile, CustomerOnboardingEvent event) {
        try {
            log.info("Setting up preferences for user: {}", user.getId());

            // Notification preferences
            userService.setNotificationPreferences(user.getId(), event.getNotificationPreferences());

            // Security preferences
            if (event.isEnableTwoFactor()) {
                userService.enableTwoFactorAuth(user.getId());
            }

            // Privacy settings
            userService.setPrivacySettings(user.getId(), event.getPrivacySettings());

            return OnboardingStepResult.success(OnboardingStep.PREFERENCES_SET, true);

        } catch (Exception e) {
            log.error("Preference setup failed: {}", e.getMessage());
            return OnboardingStepResult.failure(OnboardingStep.PREFERENCES_SET, e.getMessage());
        }
    }

    private OnboardingStepResult registerUserDevice(User user, CustomerOnboardingEvent event) {
        try {
            log.info("Registering device for user: {}", user.getId());

            userService.registerDevice(
                user.getId(),
                event.getDeviceId(),
                event.getDeviceType(),
                event.getDeviceFingerprint()
            );

            return OnboardingStepResult.success(OnboardingStep.DEVICE_REGISTERED, true);

        } catch (Exception e) {
            log.error("Device registration failed: {}", e.getMessage());
            return OnboardingStepResult.failure(OnboardingStep.DEVICE_REGISTERED, e.getMessage());
        }
    }

    private void processOnboardingResults(OnboardingProgress progress, List<CompletableFuture<OnboardingStepResult>> tasks) {
        for (CompletableFuture<OnboardingStepResult> task : tasks) {
            try {
                OnboardingStepResult result = task.get();
                if (result.isSuccess()) {
                    progress.addCompletedStep(result.getStep());
                } else {
                    progress.addFailedStep(result.getStep(), result.getError());
                }
            } catch (Exception e) {
                log.error("Failed to process onboarding task: {}", e.getMessage());
            }
        }

        // Calculate completion percentage
        int totalSteps = progress.getCompletedSteps().size() + progress.getPendingSteps().size();
        int completedSteps = progress.getCompletedSteps().size();
        progress.setCompletionPercentage((completedSteps * 100) / totalSteps);
    }

    private void processReferral(User user, CustomerOnboardingEvent event) {
        try {
            log.info("Processing referral for user: {} with code: {}", user.getId(), event.getReferralCode());

            var referral = referralService.processReferral(
                user.getId(),
                event.getReferralCode()
            );

            if (referral.isValid()) {
                user.setReferredBy(referral.getReferrerId());
                user.setReferralBonusEligible(true);
                
                // Apply referral bonus
                referralService.applyReferralBonus(user.getId(), referral.getReferrerId());
            }

        } catch (Exception e) {
            log.error("Referral processing failed: {}", e.getMessage());
            // Continue onboarding even if referral fails
        }
    }

    private void applyWelcomeBenefits(User user, CustomerOnboardingEvent event) {
        try {
            log.info("Applying welcome benefits for user: {}", user.getId());

            // Welcome bonus
            if (event.isEligibleForWelcomeBonus()) {
                var bonus = welcomeService.applyWelcomeBonus(
                    user.getId(),
                    user.getTier(),
                    event.getOnboardingChannel()
                );
                user.setWelcomeBonusAmount(bonus.getAmount());
                user.setWelcomeBonusAppliedAt(LocalDateTime.now());
            }

            // Free transactions
            welcomeService.grantFreeTransactions(user.getId(), 5);

            // Premium trial (if applicable)
            if (event.isOfferPremiumTrial()) {
                welcomeService.activatePremiumTrial(user.getId(), 30);
            }

        } catch (Exception e) {
            log.error("Failed to apply welcome benefits: {}", e.getMessage());
            // Continue without benefits
        }
    }

    private void updateOnboardingStatus(OnboardingProgress progress, User user) {
        if (progress.getCompletionPercentage() == 100) {
            progress.setStatus(OnboardingStatus.COMPLETED);
            progress.setCompletedAt(LocalDateTime.now());
            user.setIsActive(true);
            user.setActivatedAt(LocalDateTime.now());
        } else if (progress.getFailedSteps() != null && !progress.getFailedSteps().isEmpty()) {
            progress.setStatus(OnboardingStatus.PARTIALLY_COMPLETED);
        } else {
            progress.setStatus(OnboardingStatus.IN_PROGRESS);
        }

        progress.setLastUpdatedAt(LocalDateTime.now());
    }

    private void sendOnboardingNotifications(User user, UserProfile profile, CustomerOnboardingEvent event) {
        try {
            // Welcome email
            notificationService.sendWelcomeEmail(user);

            // Next steps email
            if (user.getKycSessionId() != null) {
                notificationService.sendKycInstructionsEmail(user);
            }

            // Incomplete onboarding reminder
            if (!user.isActive()) {
                notificationService.scheduleOnboardingReminder(user);
            }

            // Referral success notification
            if (user.getReferredBy() != null) {
                notificationService.sendReferralSuccessNotification(user);
            }

        } catch (Exception e) {
            log.error("Failed to send onboarding notifications: {}", e.getMessage());
        }
    }

    private void updateOnboardingMetrics(OnboardingProgress progress, CustomerOnboardingEvent event) {
        try {
            // Record onboarding metrics
            metricsService.incrementCounter("onboarding.completed",
                Map.of(
                    "channel", event.getOnboardingChannel(),
                    "user_type", event.getUserType(),
                    "completion_rate", String.valueOf(progress.getCompletionPercentage())
                ));

            // Record step completion
            for (OnboardingStep step : progress.getCompletedSteps()) {
                metricsService.incrementCounter("onboarding.step.completed",
                    Map.of("step", step.toString()));
            }

            // Record onboarding time
            if (progress.getCompletedAt() != null) {
                long durationMinutes = ChronoUnit.MINUTES.between(
                    progress.getStartedAt(), progress.getCompletedAt()
                );
                metricsService.recordTimer("onboarding.duration_minutes", durationMinutes,
                    Map.of("channel", event.getOnboardingChannel()));
            }

            // Record referral metrics
            if (event.getReferralCode() != null) {
                metricsService.incrementCounter("onboarding.referral.used");
            }

        } catch (Exception e) {
            log.error("Failed to update onboarding metrics: {}", e.getMessage());
        }
    }

    private void createOnboardingAuditLog(User user, OnboardingProgress progress, CustomerOnboardingEvent event, String correlationId) {
        auditLogger.logUserEvent(
            "CUSTOMER_ONBOARDING_PROCESSED",
            user.getId(),
            progress.getId(),
            event.getOnboardingChannel(),
            "onboarding_processor",
            progress.getStatus() == OnboardingStatus.COMPLETED,
            Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "channel", event.getOnboardingChannel(),
                "userType", event.getUserType(),
                "status", progress.getStatus().toString(),
                "completionPercentage", String.valueOf(progress.getCompletionPercentage()),
                "completedSteps", progress.getCompletedSteps().stream()
                    .map(OnboardingStep::toString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""),
                "walletCreated", String.valueOf(user.getWalletId() != null),
                "kycInitiated", String.valueOf(user.getKycSessionId() != null),
                "referralUsed", String.valueOf(event.getReferralCode() != null),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private User performFastTrackOnboarding(CustomerOnboardingEvent event, String correlationId) {
        User user = createUserAccount(event, UUID.randomUUID().toString(), correlationId);
        user.setTier(UserTier.PREMIUM);
        user.setIsActive(true);
        user.setActivatedAt(LocalDateTime.now());
        user.setFastTrackOnboarding(true);
        
        return userRepository.save(user);
    }

    private String generateUsername(CustomerOnboardingEvent event) {
        String base = event.getFirstName().toLowerCase() + event.getLastName().toLowerCase();
        return base + UUID.randomUUID().toString().substring(0, 4);
    }

    private String generateReferralCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void validatePassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new OnboardingException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        // Additional password validation rules
    }

    private List<OnboardingStep> determinePendingSteps(CustomerOnboardingEvent event) {
        List<OnboardingStep> steps = new ArrayList<>();
        steps.add(OnboardingStep.EMAIL_VERIFICATION);
        steps.add(OnboardingStep.WALLET_CREATION);
        
        if (event.getPhoneNumber() != null) {
            steps.add(OnboardingStep.PHONE_VERIFICATION);
        }
        
        if (event.isInitiateKyc()) {
            steps.add(OnboardingStep.KYC_INITIATED);
        }
        
        steps.add(OnboardingStep.PREFERENCES_SET);
        steps.add(OnboardingStep.DEVICE_REGISTERED);
        
        return steps;
    }

    /**
     * Internal class for onboarding step results
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class OnboardingStepResult {
        private OnboardingStep step;
        private boolean success;
        private String error;

        public static OnboardingStepResult success(OnboardingStep step, boolean result) {
            return new OnboardingStepResult(step, result, null);
        }

        public static OnboardingStepResult failure(OnboardingStep step, String error) {
            return new OnboardingStepResult(step, false, error);
        }
    }

    /**
     * Internal class for onboarding progress tracking
     */
    @lombok.Data
    private static class OnboardingProgress {
        private String id;
        private String userId;
        private String eventId;
        private OnboardingStep currentStep;
        private List<OnboardingStep> completedSteps;
        private List<OnboardingStep> pendingSteps;
        private Map<OnboardingStep, String> failedSteps;
        private OnboardingStatus status;
        private int completionPercentage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime lastUpdatedAt;
        private LocalDateTime expiresAt;

        public void addCompletedStep(OnboardingStep step) {
            if (!completedSteps.contains(step)) {
                completedSteps.add(step);
            }
            pendingSteps.remove(step);
        }

        public void addFailedStep(OnboardingStep step, String error) {
            if (failedSteps == null) {
                failedSteps = new HashMap<>();
            }
            failedSteps.put(step, error);
        }
    }
}