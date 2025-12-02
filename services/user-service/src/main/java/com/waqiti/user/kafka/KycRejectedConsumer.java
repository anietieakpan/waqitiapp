package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.domain.*;
import com.waqiti.user.dto.KycRejectedEvent;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.UserProfileRepository;
import com.waqiti.user.service.UserService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * KYC Rejected Event Consumer
 * Handles failed KYC verifications, updates user status, and manages retry logic.
 * This consumer ensures users are properly notified and guided through re-verification.
 */
@Slf4j
@Component
public class KycRejectedConsumer {
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserService userService;
    private final UniversalDLQHandler dlqHandler;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter kycRejectedCounter;
    private final Counter permanentRejectionsCounter;
    private final Counter retryableRejectionsCounter;
    private final Counter complianceRejectionsCounter;
    private final Timer rejectionProcessingTimer;
    
    // Configuration
    @Value("${kyc.rejection.auto-suspend:true}")
    private boolean autoSuspendEnabled;
    
    @Value("${kyc.rejection.max-retries:3}")
    private int maxRetries;
    
    @Value("${kyc.rejection.retry-delay-hours:24}")
    private int retryDelayHours;
    
    @Value("${kyc.rejection.appeal-window-days:30}")
    private int appealWindowDays;
    
    public KycRejectedConsumer(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            UserService userService,
            KafkaTemplate<String, Object> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userService = userService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.kycRejectedCounter = Counter.builder("kyc.rejected.total")
            .description("Total KYC rejections processed")
            .register(meterRegistry);
        
        this.permanentRejectionsCounter = Counter.builder("kyc.rejected.permanent")
            .description("Permanent KYC rejections")
            .register(meterRegistry);
        
        this.retryableRejectionsCounter = Counter.builder("kyc.rejected.retryable")
            .description("Retryable KYC rejections")
            .register(meterRegistry);
        
        this.complianceRejectionsCounter = Counter.builder("kyc.rejected.compliance")
            .description("Compliance-related KYC rejections")
            .register(meterRegistry);
        
        this.rejectionProcessingTimer = Timer.builder("kyc.rejection.processing.time")
            .description("KYC rejection processing time")
            .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "kyc-rejected",
        groupId = "user-service-kyc-rejected-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "2"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handleKycRejected(
            @Valid @Payload KycRejectedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId() != null ? 
            event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.warn("Processing KYC rejection: correlationId={}, userId={}, reason={}, " +
                "canRetry={}, riskLevel={}, partition={}, offset={}", 
                correlationId, event.getUserId(), event.getRejectionReason(), 
                event.getCanRetry(), event.getRiskLevel(), partition, offset);
        
        try {
            // Check for duplicate processing
            if (isDuplicateEvent(event.getEventId())) {
                log.warn("Duplicate KYC rejection event detected: eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Fetch user
            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + event.getUserId()));
            
            // Update user profile with rejection details
            UserProfile profile = updateUserProfileWithRejection(user, event);
            
            // Determine user status based on rejection type
            RejectionResponse response = determineRejectionResponse(event, user);
            
            // Update user status
            updateUserStatus(user, event, response);
            
            // Execute rejection actions
            List<RejectionAction> actions = executeRejectionActions(user, event, response);
            
            // Schedule retry if applicable
            if (response.canRetry() && !event.hasReachedMaxAttempts()) {
                scheduleKycRetry(user, event, response);
            }
            
            // Send notifications
            sendRejectionNotifications(user, event, response);
            
            // Create support ticket if needed
            if (response.requiresSupport() || event.isComplianceRejection()) {
                createSupportTicket(user, event, response);
            }
            
            // Publish rejection event for other services
            publishKycRejectionEvent(user, event, response, actions);
            
            // Update metrics
            updateRejectionMetrics(event, response);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed KYC rejection: correlationId={}, userId={}, " +
                    "status={}, canRetry={}, actions={}", 
                    correlationId, event.getUserId(), user.getStatus(), 
                    response.canRetry(), actions.size());
            
        } catch (Exception e) {
            log.error("Error processing KYC rejection: correlationId={}, userId={}, error={}", 
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
            sample.stop(rejectionProcessingTimer);
            clearTemporaryCache(event.getEventId());
        }
    }
    
    /**
     * Update user profile with rejection details
     */
    private UserProfile updateUserProfileWithRejection(User user, KycRejectedEvent event) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> {
                UserProfile newProfile = new UserProfile();
                newProfile.setUserId(user.getId());
                return newProfile;
            });
        
        // Store rejection details
        profile.setKycVerificationId(event.getKycVerificationId());
        profile.setKycRejectionReason(event.getRejectionReason().toString());
        profile.setKycRejectionDetails(event.getRejectionDetails());
        profile.setKycRejectedAt(event.getRejectedAt() != null ? 
            event.getRejectedAt() : LocalDateTime.now());
        profile.setKycAttemptNumber(event.getAttemptNumber());
        
        // Store failed checks for reference
        if (event.getFailedChecks() != null) {
            profile.setKycFailedChecks(event.getFailedChecks());
        }
        
        // Update risk assessment
        if (event.getRiskScore() != null) {
            profile.setRiskScore(event.getRiskScore());
            profile.setRiskLevel(event.getRiskLevel().toString());
        }
        
        profile.setUpdatedAt(LocalDateTime.now());
        
        return userProfileRepository.save(profile);
    }
    
    /**
     * Determine appropriate response based on rejection type
     */
    private RejectionResponse determineRejectionResponse(KycRejectedEvent event, User user) {
        RejectionResponse.Builder response = RejectionResponse.builder();
        
        // Check for permanent rejections
        if (event.isPermanentRejection()) {
            return response
                .status(UserStatus.PERMANENTLY_REJECTED)
                .canRetry(false)
                .canAppeal(event.getAllowAppeal())
                .requiresSupport(true)
                .blockAllServices(true)
                .reason("Permanent rejection: " + event.getRejectionReason())
                .build();
        }
        
        // Compliance rejections require special handling
        if (event.isComplianceRejection()) {
            return response
                .status(UserStatus.COMPLIANCE_HOLD)
                .canRetry(false)
                .canAppeal(true)
                .requiresSupport(true)
                .requiresManualReview(true)
                .blockAllServices(true)
                .reason("Compliance issue: " + event.getRejectionReason())
                .build();
        }
        
        // Document issues can be retried
        if (event.isDocumentRejection()) {
            boolean canRetry = event.getCanRetry() && 
                              (event.getAttemptNumber() == null || event.getAttemptNumber() < maxRetries);
            
            return response
                .status(canRetry ? UserStatus.KYC_FAILED_RETRYABLE : UserStatus.KYC_FAILED)
                .canRetry(canRetry)
                .retryDelay(Duration.ofHours(retryDelayHours))
                .requiredDocuments(event.getRequiredDocuments())
                .requiredActions(event.getRequiredActions())
                .reason("Document verification failed: " + event.getRejectionDetails())
                .build();
        }
        
        // Identity verification issues
        if (event.isIdentityRejection()) {
            boolean canRetry = event.getCanRetry() && 
                              event.getAttemptNumber() != null && 
                              event.getAttemptNumber() < 2; // Limited retries for identity issues
            
            return response
                .status(UserStatus.IDENTITY_VERIFICATION_FAILED)
                .canRetry(canRetry)
                .requiresSupport(!canRetry)
                .requiresManualReview(true)
                .reason("Identity verification failed: " + event.getRejectionDetails())
                .build();
        }
        
        // Default response for other rejections
        return response
            .status(UserStatus.KYC_FAILED)
            .canRetry(event.getCanRetry())
            .retryDelay(Duration.ofHours(retryDelayHours))
            .reason(event.getRejectionDetails())
            .build();
    }
    
    /**
     * Update user status based on rejection
     */
    private void updateUserStatus(User user, KycRejectedEvent event, RejectionResponse response) {
        // Update user status
        user.setStatus(response.getStatus());
        user.setKycStatus(KycStatus.REJECTED);
        user.setKycRejectedAt(LocalDateTime.now());
        user.setKycRejectionReason(event.getRejectionReason().toString());
        
        // Suspend account if required
        if (event.getSuspendAccount() && autoSuspendEnabled) {
            user.setSuspended(true);
            user.setSuspendedAt(LocalDateTime.now());
            user.setSuspensionReason("KYC verification failed: " + event.getRejectionReason());
        }
        
        // Block transactions if required
        if (event.getBlockTransactions()) {
            user.setTransactionsEnabled(false);
        }
        
        // Set retry information
        if (response.canRetry()) {
            user.setKycRetryAllowed(true);
            user.setKycRetryAfter(LocalDateTime.now().plus(response.getRetryDelay()));
            user.setKycAttemptCount(event.getAttemptNumber() != null ? 
                event.getAttemptNumber() : 1);
        } else {
            user.setKycRetryAllowed(false);
        }
        
        // Set appeal deadline if applicable
        if (response.canAppeal()) {
            user.setAppealDeadline(LocalDateTime.now().plusDays(appealWindowDays));
        }
        
        // Update metadata
        Map<String, Object> metadata = user.getMetadata() != null ? 
            user.getMetadata() : new HashMap<>();
        metadata.put("kycRejection", Map.of(
            "reason", event.getRejectionReason(),
            "details", event.getRejectionDetails(),
            "timestamp", LocalDateTime.now(),
            "canRetry", response.canRetry(),
            "provider", event.getVerificationProvider()
        ));
        user.setMetadata(metadata);
        
        userRepository.save(user);
        
        log.info("Updated user status: userId={}, status={}, suspended={}, canRetry={}", 
            user.getId(), user.getStatus(), user.getSuspended(), response.canRetry());
    }
    
    /**
     * Execute rejection-related actions
     */
    private List<RejectionAction> executeRejectionActions(
            User user, KycRejectedEvent event, RejectionResponse response) {
        
        List<RejectionAction> actions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // Invalidate any pending sessions
        if (response.shouldBlockAllServices()) {
            try {
                String sessionKey = "user:sessions:" + user.getId();
                redisTemplate.delete(sessionKey);
                
                actions.add(RejectionAction.builder()
                    .action("SESSIONS_INVALIDATED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .build());
                
                log.debug("Invalidated sessions for rejected user: {}", user.getId());
            } catch (Exception e) {
                log.error("Failed to invalidate sessions: userId={}", user.getId(), e);
            }
        }
        
        // Disable API access
        try {
            String apiKey = "user:api:access:" + user.getId();
            redisTemplate.opsForValue().set(apiKey, "DISABLED", Duration.ofDays(90));
            
            actions.add(RejectionAction.builder()
                .action("API_ACCESS_DISABLED")
                .status("SUCCESS")
                .timestamp(now)
                .build());
        } catch (Exception e) {
            log.error("Failed to disable API access: userId={}", user.getId(), e);
        }
        
        // Store rejection in cache for quick lookup
        try {
            String rejectionKey = "kyc:rejection:" + user.getId();
            Map<String, Object> rejectionData = new HashMap<>();
            rejectionData.put("reason", event.getRejectionReason());
            rejectionData.put("timestamp", now);
            rejectionData.put("canRetry", response.canRetry());
            rejectionData.put("retryAfter", response.canRetry() ? 
                now.plus(response.getRetryDelay()) : null);
            
            redisTemplate.opsForHash().putAll(rejectionKey, rejectionData);
            redisTemplate.expire(rejectionKey, Duration.ofDays(30));
            
            actions.add(RejectionAction.builder()
                .action("REJECTION_CACHED")
                .status("SUCCESS")
                .timestamp(now)
                .build());
        } catch (Exception e) {
            log.error("Failed to cache rejection: userId={}", user.getId(), e);
        }
        
        return actions;
    }
    
    /**
     * Schedule KYC retry for eligible users
     */
    private void scheduleKycRetry(User user, KycRejectedEvent event, RejectionResponse response) {
        try {
            LocalDateTime retryTime = LocalDateTime.now().plus(response.getRetryDelay());
            
            Map<String, Object> retrySchedule = new HashMap<>();
            retrySchedule.put("userId", user.getId());
            retrySchedule.put("attemptNumber", event.getAttemptNumber() != null ? 
                event.getAttemptNumber() + 1 : 2);
            retrySchedule.put("scheduledFor", retryTime);
            retrySchedule.put("previousReason", event.getRejectionReason());
            retrySchedule.put("requiredActions", event.getRequiredActions());
            retrySchedule.put("requiredDocuments", event.getRequiredDocuments());
            
            // Store in Redis with TTL
            String retryKey = "kyc:retry:scheduled:" + user.getId();
            redisTemplate.opsForValue().set(retryKey, retrySchedule, 
                response.getRetryDelay().plusHours(1));
            
            log.info("Scheduled KYC retry for user: userId={}, retryAt={}", 
                user.getId(), retryTime);
            
        } catch (Exception e) {
            log.error("Failed to schedule KYC retry: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Send rejection notifications to user
     */
    private void sendRejectionNotifications(User user, KycRejectedEvent event, 
                                           RejectionResponse response) {
        
        if (!event.getNotifyUser()) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "KYC_REJECTED");
                notification.put("userId", user.getId());
                notification.put("email", event.getEmail());
                notification.put("firstName", event.getFirstName());
                notification.put("lastName", event.getLastName());
                notification.put("reason", event.getRejectionReason().getDescription());
                notification.put("details", event.getRejectionDetails());
                notification.put("canRetry", response.canRetry());
                notification.put("canAppeal", response.canAppeal());
                
                if (response.canRetry()) {
                    notification.put("retryAfter", LocalDateTime.now().plus(response.getRetryDelay()));
                    notification.put("retryInstructions", event.getRetryInstructions());
                    notification.put("requiredActions", event.getRequiredActions());
                    notification.put("requiredDocuments", event.getRequiredDocuments());
                }
                
                if (response.canAppeal()) {
                    notification.put("appealDeadline", LocalDateTime.now().plusDays(appealWindowDays));
                    notification.put("appealProcess", "Contact support to appeal this decision");
                }
                
                notification.put("supportEmail", event.getSupportContactEmail());
                notification.put("supportPhone", event.getSupportContactPhone());
                notification.put("timestamp", LocalDateTime.now());
                
                kafkaTemplate.send("user-notifications", user.getId(), notification);
                
                log.info("Sent KYC rejection notification to user: {}", user.getId());
                
            } catch (Exception e) {
                log.error("Failed to send rejection notification: userId={}", user.getId(), e);
            }
        });
    }
    
    /**
     * Create support ticket for complex rejections
     */
    private void createSupportTicket(User user, KycRejectedEvent event, 
                                    RejectionResponse response) {
        try {
            Map<String, Object> ticket = new HashMap<>();
            ticket.put("ticketId", UUID.randomUUID().toString());
            ticket.put("userId", user.getId());
            ticket.put("type", "KYC_REJECTION_SUPPORT");
            ticket.put("priority", event.isComplianceRejection() ? "HIGH" : "MEDIUM");
            ticket.put("subject", "KYC Rejection - " + event.getRejectionReason());
            ticket.put("description", event.getRejectionDetails());
            ticket.put("rejectionData", event);
            ticket.put("userResponse", response);
            ticket.put("createdAt", LocalDateTime.now());
            ticket.put("status", "OPEN");
            
            if (event.isComplianceRejection()) {
                ticket.put("assignToTeam", "COMPLIANCE");
                ticket.put("requiresEscalation", true);
            }
            
            kafkaTemplate.send("support-tickets", user.getId(), ticket);
            
            // Update event with ticket ID
            if (event.getSupportTicketId() == null) {
                event.setSupportTicketId(ticket.get("ticketId").toString());
            }
            
            log.info("Created support ticket for KYC rejection: userId={}, ticketId={}", 
                user.getId(), ticket.get("ticketId"));
            
        } catch (Exception e) {
            log.error("Failed to create support ticket: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Publish rejection event for other services
     */
    private void publishKycRejectionEvent(User user, KycRejectedEvent event,
                                         RejectionResponse response,
                                         List<RejectionAction> actions) {
        try {
            Map<String, Object> rejectionEvent = new HashMap<>();
            rejectionEvent.put("userId", user.getId());
            rejectionEvent.put("accountId", event.getAccountId());
            rejectionEvent.put("rejectionReason", event.getRejectionReason());
            rejectionEvent.put("userStatus", user.getStatus());
            rejectionEvent.put("canRetry", response.canRetry());
            rejectionEvent.put("actions", actions);
            rejectionEvent.put("timestamp", LocalDateTime.now());
            rejectionEvent.put("correlationId", event.getCorrelationId());
            
            kafkaTemplate.send("user-kyc-rejected", user.getId(), rejectionEvent);
            
        } catch (Exception e) {
            log.error("Failed to publish KYC rejection event: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Update rejection metrics
     */
    private void updateRejectionMetrics(KycRejectedEvent event, RejectionResponse response) {
        kycRejectedCounter.increment();
        
        if (event.isPermanentRejection()) {
            permanentRejectionsCounter.increment();
        }
        
        if (response.canRetry()) {
            retryableRejectionsCounter.increment();
        }
        
        if (event.isComplianceRejection()) {
            complianceRejectionsCounter.increment();
        }
        
        // Tag-based metrics for rejection reasons
        Counter.builder("kyc.rejection.reason")
            .tag("reason", event.getRejectionReason().toString())
            .register(meterRegistry)
            .increment();
    }
    
    // Helper methods
    
    private boolean isDuplicateEvent(String eventId) {
        String key = "kyc:rejected:processed:" + eventId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            redisTemplate.opsForValue().set(key, true, Duration.ofHours(24));
            return false;
        }
        return true;
    }
        
    private void clearTemporaryCache(String eventId) {
        try {
            redisTemplate.delete("kyc:rejected:processing:" + eventId);
        } catch (Exception e) {
            log.debug("Error clearing temporary cache", e);
        }
    }
        
    // Inner classes
    
    @Data
    @Builder
    private static class RejectionResponse {
        private UserStatus status;
        private boolean canRetry;
        private boolean canAppeal;
        private Duration retryDelay;
        private boolean requiresSupport;
        private boolean requiresManualReview;
        private boolean blockAllServices;
        private List<String> requiredDocuments;
        private List<String> requiredActions;
        private String reason;
        
        public boolean shouldBlockAllServices() {
            return blockAllServices;
        }
    }
    
    @Data
    @Builder
    private static class RejectionAction {
        private String action;
        private String status;
        private LocalDateTime timestamp;
        private String details;
    }
}