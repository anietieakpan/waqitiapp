package com.waqiti.user.consumer;

import com.waqiti.common.events.AccountCreatedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.user.service.UserOnboardingService;
import com.waqiti.user.service.WalletInitializationService;
import com.waqiti.user.service.ReferralService;
import com.waqiti.user.service.ComplianceService;
import com.waqiti.user.service.NotificationPreferenceService;
import com.waqiti.user.repository.ProcessedEventRepository;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.model.ProcessedEvent;
import com.waqiti.user.model.User;
import com.waqiti.user.model.UserStatus;
import com.waqiti.user.model.OnboardingStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;

/**
 * Consumer for AccountCreatedEvent - Critical for user onboarding initialization
 * Orchestrates complete user setup after account creation
 * ZERO TOLERANCE: All new accounts must be properly initialized
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountCreatedEventConsumer {
    
    private final UserOnboardingService userOnboardingService;
    private final WalletInitializationService walletInitializationService;
    private final ReferralService referralService;
    private final ComplianceService complianceService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final ProcessedEventRepository processedEventRepository;
    private final UserRepository userRepository;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(
        topics = "user.account.created",
        groupId = "user-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for user creation
    public void handleAccountCreated(ConsumerRecord<String, AccountCreatedEvent> record,
                                    @Payload AccountCreatedEvent event,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Processing account creation: User {} created with email {} from country {}", 
            event.getUserId(), event.getEmail(), event.getCountryCode());
        
        // IDEMPOTENCY CHECK - Prevent duplicate account setup
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Account creation already processed for event: {}", event.getEventId());
            return;
        }
        
        try {
            // Get user from database
            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + event.getUserId()));
            
            // STEP 1: Initialize user onboarding workflow
            initializeOnboardingWorkflow(user, event);
            
            // STEP 2: Create and initialize wallet
            initializeUserWallet(user, event);
            
            // STEP 3: Set up notification preferences
            setupNotificationPreferences(user, event);
            
            // STEP 4: Process referral if applicable
            if (event.getReferralCode() != null) {
                processReferralRegistration(user, event);
            }
            
            // STEP 5: Initialize compliance profile
            initializeComplianceProfile(user, event);
            
            // STEP 6: Set up security defaults
            setupSecurityDefaults(user, event);
            
            // STEP 7: Initialize user preferences
            initializeUserPreferences(user, event);
            
            // STEP 8: Send welcome notification
            sendWelcomeNotification(user, event);
            
            // STEP 9: Create user profile analytics
            initializeUserAnalytics(user, event);
            
            // STEP 10: Set up default payment methods based on country
            setupDefaultPaymentMethods(user, event);
            
            // STEP 11: Update user status to active
            user.setStatus(UserStatus.ACTIVE);
            user.setOnboardingStage(OnboardingStage.WALLET_CREATED);
            user.setLastUpdated(Instant.now());
            userRepository.save(user);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("AccountCreatedEvent")
                .processedAt(Instant.now())
                .userId(event.getUserId())
                .email(event.getEmail())
                .countryCode(event.getCountryCode())
                .onboardingStepsCompleted(calculateOnboardingStepsCompleted())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed account creation for user: {} - Onboarding initialized", 
                event.getUserId());
                
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
    
    private void initializeOnboardingWorkflow(User user, AccountCreatedEvent event) {
        // Create personalized onboarding workflow based on user profile
        userOnboardingService.createOnboardingWorkflow(
            user.getId(),
            event.getCountryCode(),
            event.getSignupSource(),
            event.getUserType(),
            event.getReferralCode() != null
        );
        
        // Set initial onboarding stage
        user.setOnboardingStage(OnboardingStage.ACCOUNT_CREATED);
        
        log.info("Onboarding workflow initialized for user: {}", user.getId());
    }
    
    private void initializeUserWallet(User user, AccountCreatedEvent event) {
        // Create primary wallet for the user
        String walletId = walletInitializationService.createPrimaryWallet(
            user.getId(),
            event.getPreferredCurrency() != null ? event.getPreferredCurrency() : "USD",
            event.getCountryCode()
        );
        
        // Set up multi-currency support based on country
        walletInitializationService.setupMultiCurrencySupport(
            user.getId(),
            walletId,
            event.getCountryCode()
        );
        
        // Initialize wallet with welcome bonus if applicable
        if (event.isEligibleForWelcomeBonus()) {
            walletInitializationService.addWelcomeBonus(
                user.getId(),
                walletId,
                event.getWelcomeBonusAmount()
            );
        }
        
        log.info("Wallet initialized for user: {} with ID: {}", user.getId(), walletId);
    }
    
    private void setupNotificationPreferences(User user, AccountCreatedEvent event) {
        // Set up default notification preferences based on country and user preferences
        notificationPreferenceService.initializeDefaultPreferences(
            user.getId(),
            event.getCountryCode(),
            event.getPreferredLanguage(),
            event.getMarketingOptIn()
        );
        
        // Enable critical financial notifications by default
        notificationPreferenceService.enableCriticalNotifications(user.getId());
        
        log.info("Notification preferences set up for user: {}", user.getId());
    }
    
    private void processReferralRegistration(User user, AccountCreatedEvent event) {
        // Process referral code and credit both referrer and referee
        String referralResult = referralService.processNewUserReferral(
            user.getId(),
            event.getReferralCode(),
            event.getSignupDate()
        );
        
        // Create referral tracking record
        referralService.createReferralTrackingRecord(
            user.getId(),
            event.getReferralCode(),
            referralResult
        );
        
        log.info("Referral processed for user: {} with code: {} - Result: {}", 
            user.getId(), event.getReferralCode(), referralResult);
    }
    
    private void initializeComplianceProfile(User user, AccountCreatedEvent event) {
        // Initialize compliance profile with basic information
        complianceService.createComplianceProfile(
            user.getId(),
            event.getDateOfBirth(),
            event.getCountryCode(),
            event.getPhoneNumber(),
            event.getIPAddress()
        );
        
        // Schedule KYC verification based on country requirements
        complianceService.scheduleKYCVerification(
            user.getId(),
            event.getCountryCode(),
            event.getUserType()
        );
        
        // Perform initial risk assessment
        String riskLevel = complianceService.performInitialRiskAssessment(
            user.getId(),
            event.getCountryCode(),
            event.getSignupSource(),
            event.getIPAddress()
        );
        
        user.setRiskLevel(riskLevel);
        
        log.info("Compliance profile initialized for user: {} with risk level: {}", 
            user.getId(), riskLevel);
    }
    
    private void setupSecurityDefaults(User user, AccountCreatedEvent event) {
        // Enable 2FA setup reminder
        userOnboardingService.scheduleTwoFactorSetupReminder(user.getId());
        
        // Set up security monitoring
        userOnboardingService.initializeSecurityMonitoring(
            user.getId(),
            event.getIPAddress(),
            event.getUserAgent(),
            event.getDeviceFingerprint()
        );
        
        // Create security audit log
        userOnboardingService.createSecurityAuditLog(
            user.getId(),
            "ACCOUNT_CREATED",
            event.getIPAddress(),
            event.getUserAgent()
        );
        
        log.info("Security defaults set up for user: {}", user.getId());
    }
    
    private void initializeUserPreferences(User user, AccountCreatedEvent event) {
        // Set up user preferences based on signup data
        userOnboardingService.initializeUserPreferences(
            user.getId(),
            event.getPreferredLanguage(),
            event.getTimezone(),
            event.getPreferredCurrency(),
            event.getCountryCode()
        );
        
        // Set up privacy preferences
        userOnboardingService.initializePrivacySettings(
            user.getId(),
            event.getPrivacySettings()
        );
        
        log.info("User preferences initialized for user: {}", user.getId());
    }
    
    private void sendWelcomeNotification(User user, AccountCreatedEvent event) {
        // Send multi-channel welcome notifications
        userOnboardingService.sendWelcomeNotifications(
            user.getId(),
            event.getEmail(),
            event.getPhoneNumber(),
            event.getPreferredLanguage(),
            event.getSignupSource()
        );
        
        log.info("Welcome notifications sent for user: {}", user.getId());
    }
    
    private void initializeUserAnalytics(User user, AccountCreatedEvent event) {
        // Create user analytics profile
        userOnboardingService.createUserAnalyticsProfile(
            user.getId(),
            event.getSignupSource(),
            event.getReferralCode(),
            event.getCountryCode(),
            event.getUserType(),
            event.getSignupDate()
        );
        
        // Track signup conversion metrics
        userOnboardingService.trackSignupConversion(
            event.getSignupSource(),
            event.getCountryCode(),
            event.getReferralCode()
        );
        
        log.info("User analytics initialized for user: {}", user.getId());
    }
    
    private void setupDefaultPaymentMethods(User user, AccountCreatedEvent event) {
        // Set up default payment methods based on country
        userOnboardingService.setupDefaultPaymentMethods(
            user.getId(),
            event.getCountryCode(),
            event.getPreferredCurrency()
        );
        
        // Initialize payment limits based on country regulations
        userOnboardingService.initializePaymentLimits(
            user.getId(),
            event.getCountryCode(),
            user.getRiskLevel(),
            event.getUserType()
        );
        
        log.info("Default payment methods set up for user: {}", user.getId());
    }
    
    private int calculateOnboardingStepsCompleted() {
        // Count the number of onboarding steps completed
        return 10; // All major initialization steps completed
    }
    
    private void markUserForManualIntervention(AccountCreatedEvent event, Exception exception) {
        try {
            User user = userRepository.findById(event.getUserId()).orElse(null);
            if (user != null) {
                user.setStatus(UserStatus.REQUIRES_MANUAL_INTERVENTION);
                user.setOnboardingStage(OnboardingStage.FAILED);
                user.setLastUpdated(Instant.now());
                userRepository.save(user);
            }
        } catch (Exception e) {
            log.error("Failed to mark user for manual intervention: {}", event.getUserId(), e);
        }
    }
    
    private void createManualInterventionRecord(AccountCreatedEvent event, Exception exception) {
        manualInterventionService.createCriticalTask(
            "ACCOUNT_CREATION_PROCESSING_FAILED",
            String.format(
                "CRITICAL: Failed to process account creation. " +
                "User ID: %s, Email: %s, Country: %s. " +
                "User account may be in incomplete state. " +
                "Exception: %s. IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                event.getUserId(),
                event.getEmail(),
                event.getCountryCode(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}