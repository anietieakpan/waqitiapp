package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.domain.*;
import com.waqiti.user.dto.KycCompletedEvent;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.UserProfileRepository;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.AuthService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL: KYC Completed Event Consumer
 * This consumer is essential for the user onboarding flow.
 * Without this, users cannot be activated after KYC verification.
 * 
 * This was identified as one of the most critical missing consumers
 * in the architecture analysis - it was breaking the entire onboarding process.
 */
@Slf4j
@Component
public class KycCompletedConsumer {
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserService userService;
    private final AuthService authService;
    private final UniversalDLQHandler dlqHandler;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter kycProcessedCounter;
    private final Counter kycActivatedCounter;
    private final Counter kycFailedCounter;
    private final Counter kycRejectedCounter;
    private final Timer kycProcessingTimer;
    
    // Configuration
    @Value("${kyc.auto-activate.enabled:true}")
    private boolean autoActivateEnabled;
    
    @Value("${kyc.high-risk.threshold:0.7}")
    private double highRiskThreshold;
    
    @Value("${kyc.welcome.delay.minutes:5}")
    private int welcomeDelayMinutes;
    
    @Value("${kyc.processing.timeout.seconds:30}")
    private int processingTimeoutSeconds;
    
    @Value("${kyc.notification.enabled:true}")
    private boolean notificationEnabled;
    
    public KycCompletedConsumer(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            UserService userService,
            AuthService authService,
            KafkaTemplate<String, Object> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userService = userService;
        this.authService = authService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.kycProcessedCounter = Counter.builder("kyc.completed.processed")
            .description("Total KYC completed events processed")
            .register(meterRegistry);
        
        this.kycActivatedCounter = Counter.builder("kyc.accounts.activated")
            .description("Total accounts activated after KYC")
            .register(meterRegistry);
        
        this.kycFailedCounter = Counter.builder("kyc.processing.failed")
            .description("Total KYC processing failures")
            .register(meterRegistry);
        
        this.kycRejectedCounter = Counter.builder("kyc.accounts.rejected")
            .description("Total accounts rejected after KYC")
            .register(meterRegistry);
        
        this.kycProcessingTimer = Timer.builder("kyc.processing.time")
            .description("KYC event processing time")
            .register(meterRegistry);
    }
    
    /**
     * CRITICAL: Main KYC completion handler
     * This method activates user accounts after successful KYC verification
     */
    @KafkaListener(
        topics = "kyc-completed",
        groupId = "user-service-kyc-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "2"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 60)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handleKycCompleted(
            @Valid @Payload KycCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId() != null ? 
            event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.info("Processing KYC completed event: correlationId={}, userId={}, " +
                "verificationLevel={}, riskLevel={}, score={}, partition={}, offset={}", 
                correlationId, event.getUserId(), event.getVerificationLevel(), 
                event.getRiskLevel(), event.getVerificationScore(), partition, offset);
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // Check for duplicate processing
            if (isDuplicateEvent(event.getEventId())) {
                log.warn("Duplicate KYC event detected: eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Fetch user
            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + event.getUserId()));
            
            // Verify user is in correct state for KYC completion
            if (user.getStatus() != UserStatus.PENDING_VERIFICATION && 
                user.getStatus() != UserStatus.KYC_IN_PROGRESS) {
                log.warn("User not in correct state for KYC completion: userId={}, status={}", 
                    user.getId(), user.getStatus());
                acknowledgment.acknowledge();
                return;
            }
            
            // Update user profile with KYC data
            UserProfile profile = updateUserProfile(user, event);
            
            // Determine account activation based on risk and compliance
            AccountActivationDecision decision = determineAccountActivation(event);
            
            if (decision.shouldActivate()) {
                // Activate the account
                activateUserAccount(user, profile, event, decision);
                
                // Set account limits based on verification level and risk
                applyAccountLimits(user, event);
                
                // Enable features based on tier
                enableAccountFeatures(user, event);
                
                // Schedule welcome communications
                if (event.getSendWelcomeEmail()) {
                    scheduleWelcomePackage(user, event);
                }
                
                kycActivatedCounter.increment();
                
                log.info("Successfully activated account: userId={}, tier={}, limits={}/{}/{}", 
                    user.getId(), event.getAccountTier(),
                    event.getSingleTransactionLimit(),
                    event.getDailyTransactionLimit(),
                    event.getMonthlyTransactionLimit());
                
            } else if (decision.requiresManualReview()) {
                // Queue for manual review
                user.setStatus(UserStatus.PENDING_REVIEW);
                user.setKycStatus(KycStatus.MANUAL_REVIEW_REQUIRED);
                userRepository.save(user);
                
                createManualReviewCase(user, event, decision);
                
                log.info("Account queued for manual review: userId={}, reason={}", 
                    user.getId(), decision.getReason());
                
            } else {
                // Reject account activation
                user.setStatus(UserStatus.REJECTED);
                user.setKycStatus(KycStatus.REJECTED);
                userRepository.save(user);
                
                sendRejectionNotification(user, event, decision);
                
                kycRejectedCounter.increment();
                
                log.warn("Account activation rejected: userId={}, reason={}", 
                    user.getId(), decision.getReason());
            }
            
            // Publish user status update event
            publishUserStatusUpdate(user, event, decision);
            
            // Update metrics
            kycProcessedCounter.increment();
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            long processingTime = Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.info("Successfully processed KYC completion: correlationId={}, userId={}, " +
                    "decision={}, processingTime={}ms", 
                    correlationId, event.getUserId(), decision.getDecision(), processingTime);
            
        } catch (Exception e) {
            log.error("Error processing KYC completed event: correlationId={}, userId={}, error={}", 
                    correlationId, event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Processing failed", e);
        } finally {
            sample.stop(kycProcessingTimer);
            clearTemporaryCache(event.getEventId());
        }
    }
    
    /**
     * Update user profile with KYC verification data
     */
    private UserProfile updateUserProfile(User user, KycCompletedEvent event) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> {
                UserProfile newProfile = new UserProfile();
                newProfile.setUserId(user.getId());
                return newProfile;
            });
        
        // Update personal information
        profile.setFirstName(event.getFirstName());
        profile.setLastName(event.getLastName());
        profile.setMiddleName(event.getMiddleName());
        profile.setDateOfBirth(event.getDateOfBirth());
        profile.setNationality(event.getNationality());
        profile.setCountryOfResidence(event.getCountryOfResidence());
        
        // Update address
        profile.setAddressLine1(event.getAddressLine1());
        profile.setAddressLine2(event.getAddressLine2());
        profile.setCity(event.getCity());
        profile.setState(event.getState());
        profile.setPostalCode(event.getPostalCode());
        profile.setCountry(event.getCountry());
        
        // Update verification details
        profile.setKycVerificationId(event.getKycVerificationId());
        profile.setKycVerificationLevel(event.getVerificationLevel().toString());
        profile.setKycVerificationScore(event.getVerificationScore());
        profile.setKycVerifiedAt(event.getVerificationCompletedAt());
        profile.setKycProvider(event.getVerificationProvider());
        
        // Update document information
        profile.setDocumentType(event.getPrimaryDocumentType().toString());
        profile.setDocumentNumber(maskDocumentNumber(event.getPrimaryDocumentNumber()));
        profile.setDocumentCountry(event.getPrimaryDocumentCountry());
        profile.setDocumentExpiry(event.getPrimaryDocumentExpiry());
        
        // Update risk assessment
        profile.setRiskLevel(event.getRiskLevel().toString());
        profile.setRiskScore(event.getRiskScore());
        profile.setComplianceFlags(event.getComplianceFlags());
        
        // Update biometric status
        if (event.getBiometricVerificationCompleted() != null) {
            profile.setBiometricEnabled(event.getBiometricVerificationCompleted());
            profile.setBiometricType(event.getBiometricType());
        }
        
        profile.setUpdatedAt(LocalDateTime.now());
        
        return userProfileRepository.save(profile);
    }
    
    /**
     * Determine if account should be activated based on KYC results
     */
    private AccountActivationDecision determineAccountActivation(KycCompletedEvent event) {
        AccountActivationDecision.Builder decision = AccountActivationDecision.builder();
        
        // Check if auto-activation is disabled globally
        if (!autoActivateEnabled) {
            return decision
                .decision(AccountActivationDecision.Decision.MANUAL_REVIEW)
                .shouldActivate(false)
                .requiresManualReview(true)
                .reason("Auto-activation disabled - manual review required")
                .build();
        }
        
        // Check compliance flags
        if (event.hasComplianceIssues()) {
            if (!event.getAmlCheckPassed() || !event.getSanctionsCheckPassed()) {
                return decision
                    .decision(AccountActivationDecision.Decision.REJECT)
                    .shouldActivate(false)
                    .requiresManualReview(false)
                    .reason("Failed AML/Sanctions checks")
                    .complianceBlock(true)
                    .build();
            }
            
            return decision
                .decision(AccountActivationDecision.Decision.MANUAL_REVIEW)
                .shouldActivate(false)
                .requiresManualReview(true)
                .reason("Compliance issues require review")
                .complianceFlags(event.getComplianceFlags())
                .build();
        }
        
        // Check risk level
        if (event.isHighRisk() || event.getRiskScore() > highRiskThreshold) {
            if (event.getRiskLevel() == KycCompletedEvent.RiskLevel.VERY_HIGH) {
                return decision
                    .decision(AccountActivationDecision.Decision.REJECT)
                    .shouldActivate(false)
                    .requiresManualReview(false)
                    .reason("Risk level too high for activation")
                    .riskBlock(true)
                    .build();
            }
            
            return decision
                .decision(AccountActivationDecision.Decision.CONDITIONAL_ACTIVATE)
                .shouldActivate(true)
                .requiresManualReview(false)
                .withRestrictions(true)
                .restrictions(Arrays.asList(
                    "Limited transaction amounts",
                    "Enhanced monitoring enabled",
                    "International transfers disabled"
                ))
                .reason("High risk - activated with restrictions")
                .build();
        }
        
        // Check if manual review is explicitly required
        if (event.requiresManualIntervention()) {
            return decision
                .decision(AccountActivationDecision.Decision.MANUAL_REVIEW)
                .shouldActivate(false)
                .requiresManualReview(true)
                .reason("Manual review required by KYC provider")
                .build();
        }
        
        // Check verification score
        if (event.getVerificationScore() < 70.0) {
            return decision
                .decision(AccountActivationDecision.Decision.MANUAL_REVIEW)
                .shouldActivate(false)
                .requiresManualReview(true)
                .reason("Low verification score requires review")
                .build();
        }
        
        // Standard activation
        return decision
            .decision(AccountActivationDecision.Decision.ACTIVATE)
            .shouldActivate(true)
            .requiresManualReview(false)
            .reason("KYC verification successful")
            .build();
    }
    
    /**
     * Activate the user account after successful KYC
     */
    private void activateUserAccount(User user, UserProfile profile, 
                                    KycCompletedEvent event, 
                                    AccountActivationDecision decision) {
        
        // Update user status
        user.setStatus(UserStatus.ACTIVE);
        user.setKycStatus(KycStatus.VERIFIED);
        user.setKycVerifiedAt(event.getVerificationCompletedAt());
        user.setAccountTier(event.getAccountTier().toString());
        
        // Set activation timestamp
        user.setActivatedAt(LocalDateTime.now());
        
        // Apply any restrictions
        if (decision.hasRestrictions()) {
            user.setRestrictions(decision.getRestrictions());
            user.setRestrictedUntil(LocalDateTime.now().plusDays(90)); // Review after 90 days
        }
        
        // Enable transaction capabilities
        user.setTransactionsEnabled(event.getEnableTransactions());
        
        // Set next review date if needed
        if (event.getNextReviewDate() != null) {
            user.setNextReviewDate(event.getNextReviewDate());
        }
        
        // Update metadata
        Map<String, Object> metadata = user.getMetadata() != null ? 
            user.getMetadata() : new HashMap<>();
        metadata.put("kycCompletedAt", event.getVerificationCompletedAt());
        metadata.put("kycProvider", event.getVerificationProvider());
        metadata.put("kycScore", event.getVerificationScore());
        metadata.put("activationReason", decision.getReason());
        user.setMetadata(metadata);
        
        userRepository.save(user);
        
        log.info("Account activated: userId={}, tier={}, restrictions={}", 
            user.getId(), user.getAccountTier(), 
            decision.hasRestrictions() ? decision.getRestrictions().size() : 0);
    }
    
    /**
     * Apply account limits based on KYC verification level
     */
    private void applyAccountLimits(User user, KycCompletedEvent event) {
        try {
            Map<String, Object> limits = new HashMap<>();
            limits.put("daily", event.getDailyTransactionLimit());
            limits.put("monthly", event.getMonthlyTransactionLimit());
            limits.put("single", event.getSingleTransactionLimit());
            limits.put("tier", event.getAccountTier());
            limits.put("effectiveFrom", LocalDateTime.now());
            
            // Store limits in user metadata
            Map<String, Object> metadata = user.getMetadata();
            metadata.put("transactionLimits", limits);
            user.setMetadata(metadata);
            userRepository.save(user);
            
            // Publish limits update event
            Map<String, Object> limitsEvent = new HashMap<>();
            limitsEvent.put("userId", user.getId());
            limitsEvent.put("limits", limits);
            limitsEvent.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("account-limits-updated", user.getId(), limitsEvent);
            
            log.debug("Applied account limits for user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to apply account limits: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Enable features based on account tier
     */
    private void enableAccountFeatures(User user, KycCompletedEvent event) {
        try {
            Set<String> features = new HashSet<>();
            
            // Base features for all verified accounts
            features.add("SEND_MONEY");
            features.add("RECEIVE_MONEY");
            features.add("VIEW_TRANSACTIONS");
            
            // Add tier-specific features
            switch (event.getAccountTier()) {
                case ENTERPRISE:
                    features.add("BULK_PAYMENTS");
                    features.add("API_ACCESS");
                    features.add("CUSTOM_LIMITS");
                case BUSINESS:
                    features.add("INVOICING");
                    features.add("MULTI_USER");
                    features.add("BUSINESS_ANALYTICS");
                case PREMIUM:
                    features.add("INTERNATIONAL_TRANSFERS");
                    features.add("CURRENCY_EXCHANGE");
                    features.add("PREMIUM_SUPPORT");
                case STANDARD:
                    features.add("BILL_PAYMENTS");
                    features.add("SCHEDULED_PAYMENTS");
                    features.add("SAVINGS_GOALS");
                case STARTER:
                    features.add("P2P_TRANSFERS");
                    features.add("QR_PAYMENTS");
                    break;
            }
            
            // Add from event if specified
            if (event.getEnabledFeatures() != null) {
                features.addAll(event.getEnabledFeatures());
            }
            
            // Remove restricted features
            if (event.getRestrictedFeatures() != null) {
                features.removeAll(event.getRestrictedFeatures());
            }
            
            user.setEnabledFeatures(new ArrayList<>(features));
            userRepository.save(user);
            
            log.debug("Enabled {} features for user: {}", features.size(), user.getId());
            
        } catch (Exception e) {
            log.error("Failed to enable account features: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Schedule welcome package for new users
     */
    private void scheduleWelcomePackage(User user, KycCompletedEvent event) {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    // Wait before sending welcome package
                    Thread.sleep(welcomeDelayMinutes * 60 * 1000);
                    
                    Map<String, Object> welcomeEvent = new HashMap<>();
                    welcomeEvent.put("userId", user.getId());
                    welcomeEvent.put("email", user.getEmail());
                    welcomeEvent.put("firstName", event.getFirstName());
                    welcomeEvent.put("lastName", event.getLastName());
                    welcomeEvent.put("accountTier", event.getAccountTier());
                    welcomeEvent.put("features", user.getEnabledFeatures());
                    welcomeEvent.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("user-welcome-package", user.getId(), welcomeEvent);
                    
                    log.info("Scheduled welcome package for user: {}", user.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to schedule welcome package: userId={}", user.getId(), e);
                }
            });
        } catch (Exception e) {
            log.error("Error scheduling welcome package: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Create manual review case for accounts requiring human intervention
     */
    private void createManualReviewCase(User user, KycCompletedEvent event, 
                                       AccountActivationDecision decision) {
        try {
            Map<String, Object> reviewCase = new HashMap<>();
            reviewCase.put("caseId", UUID.randomUUID().toString());
            reviewCase.put("userId", user.getId());
            reviewCase.put("caseType", "KYC_MANUAL_REVIEW");
            reviewCase.put("priority", event.isHighRisk() ? "HIGH" : "MEDIUM");
            reviewCase.put("reason", decision.getReason());
            reviewCase.put("kycData", event);
            reviewCase.put("complianceFlags", event.getComplianceFlags());
            reviewCase.put("riskScore", event.getRiskScore());
            reviewCase.put("createdAt", LocalDateTime.now());
            reviewCase.put("status", "PENDING");
            
            kafkaTemplate.send("manual-review-cases", user.getId(), reviewCase);
            
            // Send notification to compliance team
            if (notificationEnabled) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "COMPLIANCE_REVIEW_REQUIRED");
                notification.put("userId", user.getId());
                notification.put("reason", decision.getReason());
                notification.put("priority", event.isHighRisk() ? "HIGH" : "MEDIUM");
                
                kafkaTemplate.send("compliance-notifications", notification);
            }
            
            log.info("Created manual review case for user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Failed to create manual review case: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Send rejection notification for failed KYC
     */
    private void sendRejectionNotification(User user, KycCompletedEvent event, 
                                          AccountActivationDecision decision) {
        try {
            if (notificationEnabled) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "KYC_REJECTED");
                notification.put("userId", user.getId());
                notification.put("email", user.getEmail());
                notification.put("reason", decision.getReason());
                notification.put("timestamp", LocalDateTime.now());
                notification.put("supportContact", "support@example.com");
                
                kafkaTemplate.send("user-notifications", user.getId(), notification);
                
                log.info("Sent rejection notification to user: {}", user.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send rejection notification: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Publish user status update event for other services
     */
    private void publishUserStatusUpdate(User user, KycCompletedEvent event, 
                                        AccountActivationDecision decision) {
        try {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("userId", user.getId());
            statusUpdate.put("previousStatus", "PENDING_VERIFICATION");
            statusUpdate.put("newStatus", user.getStatus().toString());
            statusUpdate.put("kycStatus", user.getKycStatus().toString());
            statusUpdate.put("accountTier", event.getAccountTier());
            statusUpdate.put("decision", decision.getDecision());
            statusUpdate.put("timestamp", LocalDateTime.now());
            statusUpdate.put("correlationId", event.getCorrelationId());
            
            kafkaTemplate.send("user-status-updated", user.getId(), statusUpdate);
            
            log.debug("Published user status update: userId={}, status={}", 
                user.getId(), user.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to publish user status update: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Send event to dead letter queue
     */    
    // Helper methods
    
    private boolean isDuplicateEvent(String eventId) {
        String key = "kyc:processed:" + eventId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            redisTemplate.opsForValue().set(key, true, Duration.ofHours(24));
            return false;
        }
        return true;
    }
        
    private void clearTemporaryCache(String eventId) {
        try {
            redisTemplate.delete("kyc:processing:" + eventId);
        } catch (Exception e) {
            log.debug("Error clearing temporary cache", e);
        }
    }
    
    private String maskDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.length() < 4) {
            return "****";
        }
        int visibleChars = Math.min(4, documentNumber.length() / 3);
        String visible = documentNumber.substring(documentNumber.length() - visibleChars);
        return "*".repeat(documentNumber.length() - visibleChars) + visible;
    }
    
    /**
     * Account activation decision model
     */
    @Data
    @Builder
    private static class AccountActivationDecision {
        private Decision decision;
        private boolean shouldActivate;
        private boolean requiresManualReview;
        private String reason;
        private boolean withRestrictions;
        private List<String> restrictions;
        private boolean complianceBlock;
        private boolean riskBlock;
        private List<String> complianceFlags;
        
        public enum Decision {
            ACTIVATE,
            CONDITIONAL_ACTIVATE,
            MANUAL_REVIEW,
            REJECT
        }
        
        public boolean hasRestrictions() {
            return withRestrictions && restrictions != null && !restrictions.isEmpty();
        }
    }
}