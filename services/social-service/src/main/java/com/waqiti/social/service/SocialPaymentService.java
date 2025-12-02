package com.waqiti.social.service;

import com.waqiti.common.cache.CacheService;
import com.waqiti.common.cache.DistributedLockService;
import com.waqiti.common.event.EventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.waqiti.ai.recommendation.PaymentRecommendationEngine;
import com.waqiti.ai.content.ContentModerationService;
import com.waqiti.social.analytics.ViralPaymentTracker;
import com.waqiti.social.gamification.PaymentGamificationService;

// Import UnifiedPaymentService
import com.waqiti.payment.core.UnifiedPaymentService;
import com.waqiti.payment.core.model.*;
import com.waqiti.social.domain.SocialConnection;
import com.waqiti.social.domain.SocialPayment;
import com.waqiti.social.domain.SocialFeed;
import com.waqiti.social.dto.request.*;
import com.waqiti.social.dto.response.*;
import com.waqiti.social.event.*;
import com.waqiti.social.exception.*;
import com.waqiti.social.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialPaymentService {

    private final SocialPaymentRepository paymentRepository;
    private final SocialConnectionRepository connectionRepository;
    private final SocialFeedRepository feedRepository;
    private final PaymentProcessingService paymentProcessingService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final CacheService cacheService;
    private final DistributedLockService lockService;
    private final EventPublisher eventPublisher;
    
    // Missing dependencies identified by Qodana
    private final PlaidBankingProvider plaidBankingProvider;
    private final PaymentReactionRepository paymentReactionRepository;
    private final PaymentCommentRepository paymentCommentRepository;
    private final PaymentFeedRepository paymentFeedRepository;
    private final ContentModerationService contentModerationService;
    private final SocialPaymentValidationService socialPaymentValidationService;
    private final PaymentQRCodeRepository paymentQRCodeRepository;
    private final PaymentChallengeRepository paymentChallengeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OpenAiChatClient openAiChatClient;
    private final PaymentRecommendationEngine recommendationEngine;
    private final ViralPaymentTracker viralPaymentTracker;
    private final PaymentGamificationService gamificationService;
    private final CacheManager cacheManager;
    
    // Real-time WebSocket connections tracking
    private final Map<UUID, Set<String>> userWebSocketSessions = new ConcurrentHashMap<>();
    
    // AI-powered recommendation cache
    private final Map<UUID, List<PaymentRecommendation>> recommendationCache = new ConcurrentHashMap<>();
    
    // Viral payment tracking
    private final Map<UUID, ViralPaymentMetrics> viralMetrics = new ConcurrentHashMap<>();
    
    // Scheduled executor for real-time updates
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    
    @Value("${social.payment.ai.recommendations.enabled:true}")
    private boolean aiRecommendationsEnabled;
    
    @Value("${social.payment.websocket.enabled:true}")
    private boolean webSocketEnabled;
    
    @Value("${social.payment.viral-tracking.enabled:true}")
    private boolean viralTrackingEnabled;
    
    @Value("${social.payment.gamification.enabled:true}")
    private boolean gamificationEnabled;
    
    @Value("${social.payment.content-moderation.ai-threshold:0.8}")
    private double aiModerationThreshold;

    @Transactional
    public SocialPaymentResponse sendMoney(UUID senderId, SendMoneyRequest request) {
        log.info("Processing send money request from {} to {} amount: {}", 
                senderId, request.getRecipientId(), request.getAmount());

        String lockKey = "social-payment:send:" + senderId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {
            
            // Validate sender and recipient
            validateUsers(senderId, request.getRecipientId());
            
            // Check connection and limits
            SocialConnection connection = validateConnection(senderId, request.getRecipientId());
            validateTransactionLimits(connection, request.getAmount());
            
            // Create social payment
            SocialPayment payment = SocialPayment.builder()
                    .senderId(senderId)
                    .recipientId(request.getRecipientId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .paymentType(SocialPayment.PaymentType.SEND)
                    .status(SocialPayment.PaymentStatus.PENDING)
                    .message(request.getMessage())
                    .emoji(request.getEmoji())
                    .isPublic(request.getIsPublic())
                    .visibility(request.getVisibility())
                    .mediaAttachments(request.getMediaAttachments())
                    .tags(request.getTags())
                    .location(request.getLocation())
                    .occasion(request.getOccasion())
                    .sourceMethod(request.getSourceMethod())
                    .initiatedVia(request.getInitiatedVia())
                    .metadata(request.getMetadata())
                    .build();

            payment = paymentRepository.save(payment);

            try {
                // Process payment through core payment system
                PaymentResult result = paymentProcessingService.processPayment(
                        PaymentProcessingRequest.builder()
                                .senderId(senderId)
                                .recipientId(request.getRecipientId())
                                .amount(request.getAmount())
                                .currency(request.getCurrency())
                                .reference(payment.getPaymentId())
                                .description("Social payment: " + request.getMessage())
                                .sourceMethod(request.getSourceMethod())
                                .build());

                // Update payment status
                payment.setTransactionId(result.getTransactionId());
                payment.setStatus(SocialPayment.PaymentStatus.COMPLETED);
                payment.setCompletedAt(LocalDateTime.now());
                payment = paymentRepository.save(payment);

                // Update connection statistics
                updateConnectionStats(connection, request.getAmount(), true);

                // Create social feed entry
                createSocialFeedEntry(payment);

                // Send notifications
                sendPaymentNotifications(payment);

                // Publish events
                publishPaymentEvents(payment);
                
                // Enhanced features for completed payments
                handleEnhancedPaymentFeatures(payment);

                log.info("Social payment completed: {}", payment.getPaymentId());
                
                return mapToSocialPaymentResponse(payment);

            } catch (Exception e) {
                log.error("Payment processing failed for social payment: {}", payment.getPaymentId(), e);
                
                payment.setStatus(SocialPayment.PaymentStatus.FAILED);
                payment.setFailedAt(LocalDateTime.now());
                payment.setFailureReason(e.getMessage());
                paymentRepository.save(payment);
                
                throw new SocialPaymentException("Payment processing failed", e);
            }
        });
    }

    @Transactional
    public SocialPaymentResponse requestMoney(UUID requesterId, RequestMoneyRequest request) {
        log.info("Processing money request from {} to {} amount: {}", 
                requesterId, request.getFromUserId(), request.getAmount());

        // Validate users and connection
        validateUsers(requesterId, request.getFromUserId());
        SocialConnection connection = validateConnection(requesterId, request.getFromUserId());

        // Create payment request
        SocialPayment payment = SocialPayment.builder()
                .senderId(request.getFromUserId()) // Person who will pay
                .recipientId(requesterId) // Person requesting
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentType(SocialPayment.PaymentType.REQUEST)
                .status(SocialPayment.PaymentStatus.REQUESTED)
                .message(request.getMessage())
                .emoji(request.getEmoji())
                .isPublic(request.getIsPublic())
                .visibility(request.getVisibility())
                .requestExpiresAt(request.getExpiresAt())
                .occasion(request.getOccasion())
                .requestedAt(LocalDateTime.now())
                .metadata(request.getMetadata())
                .build();

        payment = paymentRepository.save(payment);

        // Send notification to the person being requested
        notificationService.sendPaymentRequestNotification(
                request.getFromUserId(), requesterId, payment);

        // Create social feed entry
        createSocialFeedEntry(payment);

        // Publish event
        eventPublisher.publish(PaymentRequestedEvent.builder()
                .paymentId(payment.getId())
                .requesterId(requesterId)
                .fromUserId(request.getFromUserId())
                .amount(request.getAmount())
                .message(request.getMessage())
                .build());

        log.info("Money request created: {}", payment.getPaymentId());
        
        return mapToSocialPaymentResponse(payment);
    }

    @Transactional
    public SocialPaymentResponse approvePaymentRequest(UUID userId, String paymentId) {
        log.info("Approving payment request: {} by user: {}", paymentId, userId);

        SocialPayment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new SocialPaymentNotFoundException("Payment not found"));

        // Validate user can approve this request
        if (!payment.getSenderId().equals(userId)) {
            throw new UnauthorizedOperationException("User cannot approve this payment request");
        }

        if (payment.getStatus() != SocialPayment.PaymentStatus.REQUESTED) {
            throw new InvalidPaymentStatusException("Payment request cannot be approved in current status");
        }

        // Check if request has expired
        if (payment.getRequestExpiresAt() != null && 
            LocalDateTime.now().isAfter(payment.getRequestExpiresAt())) {
            payment.setStatus(SocialPayment.PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
            throw new PaymentRequestExpiredException("Payment request has expired");
        }

        String lockKey = "social-payment:approve:" + paymentId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {
            
            try {
                // Process payment
                PaymentResult result = paymentProcessingService.processPayment(
                        PaymentProcessingRequest.builder()
                                .senderId(payment.getSenderId())
                                .recipientId(payment.getRecipientId())
                                .amount(payment.getAmount())
                                .currency(payment.getCurrency())
                                .reference(payment.getPaymentId())
                                .description("Approved payment request: " + payment.getMessage())
                                .build());

                // Update payment status
                payment.setTransactionId(result.getTransactionId());
                payment.setStatus(SocialPayment.PaymentStatus.COMPLETED);
                payment.setCompletedAt(LocalDateTime.now());
                payment = paymentRepository.save(payment);

                // Update connection statistics
                SocialConnection connection = connectionRepository
                        .findByUserIdAndConnectedUserId(payment.getSenderId(), payment.getRecipientId())
                        .orElse(null);
                if (connection != null) {
                    updateConnectionStats(connection, payment.getAmount(), true);
                }

                // Send notifications
                sendPaymentNotifications(payment);

                // Update social feed
                updateSocialFeedEntry(payment);

                // Publish events
                publishPaymentEvents(payment);

                log.info("Payment request approved and completed: {}", paymentId);
                
                return mapToSocialPaymentResponse(payment);

            } catch (Exception e) {
                log.error("Failed to process approved payment request: {}", paymentId, e);
                
                payment.setStatus(SocialPayment.PaymentStatus.FAILED);
                payment.setFailedAt(LocalDateTime.now());
                payment.setFailureReason(e.getMessage());
                paymentRepository.save(payment);
                
                throw new SocialPaymentException("Failed to process payment", e);
            }
        });
    }

    @Transactional
    public void declinePaymentRequest(UUID userId, String paymentId, String reason) {
        log.info("Declining payment request: {} by user: {}", paymentId, userId);

        SocialPayment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new SocialPaymentNotFoundException("Payment not found"));

        if (!payment.getSenderId().equals(userId)) {
            throw new UnauthorizedOperationException("User cannot decline this payment request");
        }

        if (payment.getStatus() != SocialPayment.PaymentStatus.REQUESTED) {
            throw new InvalidPaymentStatusException("Payment request cannot be declined in current status");
        }

        payment.setStatus(SocialPayment.PaymentStatus.DECLINED);
        payment.setFailureReason(reason);
        payment.setFailedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Notify requester
        notificationService.sendPaymentDeclinedNotification(
                payment.getRecipientId(), userId, payment, reason);

        // Publish event
        eventPublisher.publish(PaymentDeclinedEvent.builder()
                .paymentId(payment.getId())
                .requesterId(payment.getRecipientId())
                .declinedBy(userId)
                .reason(reason)
                .build());

        log.info("Payment request declined: {}", paymentId);
    }

    @Transactional
    public SplitBillResponse splitBill(UUID userId, SplitBillRequest request) {
        log.info("Processing bill split request from user: {} amount: {} participants: {}", 
                userId, request.getTotalAmount(), request.getParticipants().size());

        // Validate participants
        validateParticipants(userId, request.getParticipants());

        // Calculate split amounts
        Map<UUID, BigDecimal> splitAmounts = calculateSplitAmounts(
                request.getTotalAmount(), request.getParticipants(), request.getSplitType());

        List<SocialPayment> splitPayments = new ArrayList<>();

        // Create individual payment requests for each participant
        for (Map.Entry<UUID, BigDecimal> entry : splitAmounts.entrySet()) {
            UUID participantId = entry.getKey();
            BigDecimal amount = entry.getValue();

            if (participantId.equals(userId)) {
                continue; // Skip the organizer
            }

            SocialPayment payment = SocialPayment.builder()
                    .senderId(participantId)
                    .recipientId(userId)
                    .amount(amount)
                    .currency(request.getCurrency())
                    .paymentType(SocialPayment.PaymentType.SPLIT)
                    .status(SocialPayment.PaymentStatus.REQUESTED)
                    .message(request.getDescription())
                    .emoji("💰")
                    .isPublic(request.getIsPublic())
                    .visibility(request.getVisibility())
                    .splitDetails(Map.of(
                            "totalAmount", request.getTotalAmount(),
                            "splitType", request.getSplitType(),
                            "organizer", userId,
                            "participants", request.getParticipants()
                    ))
                    .requestExpiresAt(request.getExpiresAt())
                    .requestedAt(LocalDateTime.now())
                    .build();

            splitPayments.add(paymentRepository.save(payment));
        }

        // Send notifications to participants
        for (SocialPayment payment : splitPayments) {
            notificationService.sendBillSplitNotification(
                    payment.getSenderId(), userId, payment, request);
        }

        // Create social feed entry for the bill split
        createBillSplitFeedEntry(userId, request, splitPayments);

        // Publish event
        eventPublisher.publish(BillSplitCreatedEvent.builder()
                .organizerId(userId)
                .totalAmount(request.getTotalAmount())
                .participants(new ArrayList<>(splitAmounts.keySet()))
                .description(request.getDescription())
                .splitPayments(splitPayments.stream().map(SocialPayment::getId).collect(Collectors.toList()))
                .build());

        log.info("Bill split created with {} payment requests", splitPayments.size());

        return SplitBillResponse.builder()
                .organizerId(userId)
                .totalAmount(request.getTotalAmount())
                .splitAmounts(splitAmounts)
                .paymentRequests(splitPayments.stream()
                        .map(this::mapToSocialPaymentResponse)
                        .collect(Collectors.toList()))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<SocialPaymentResponse> getPaymentHistory(UUID userId, Pageable pageable) {
        Page<SocialPayment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return payments.map(this::mapToSocialPaymentResponse);
    }

    @Transactional(readOnly = true)
    public Page<SocialPaymentResponse> getPendingRequests(UUID userId, Pageable pageable) {
        Page<SocialPayment> requests = paymentRepository.findPendingRequestsForUser(userId, pageable);
        return requests.map(this::mapToSocialPaymentResponse);
    }

    @Transactional(readOnly = true)
    public SocialPaymentAnalytics getPaymentAnalytics(UUID userId) {
        String cacheKey = cacheService.buildUserKey(userId.toString(), "payment-analytics");
        
        SocialPaymentAnalytics cached = cacheService.get(cacheKey, SocialPaymentAnalytics.class);
        if (cached != null) {
            return cached;
        }

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        SocialPaymentAnalytics analytics = SocialPaymentAnalytics.builder()
                .userId(userId)
                .totalSent(paymentRepository.getTotalSentAmount(userId, thirtyDaysAgo))
                .totalReceived(paymentRepository.getTotalReceivedAmount(userId, thirtyDaysAgo))
                .transactionCount(paymentRepository.getTransactionCount(userId, thirtyDaysAgo))
                .averageTransactionAmount(paymentRepository.getAverageTransactionAmount(userId, thirtyDaysAgo))
                .topRecipients(paymentRepository.getTopRecipients(userId, thirtyDaysAgo))
                .topSenders(paymentRepository.getTopSenders(userId, thirtyDaysAgo))
                .paymentTypeDistribution(paymentRepository.getPaymentTypeDistribution(userId, thirtyDaysAgo))
                .monthlyTrends(paymentRepository.getMonthlyTrends(userId))
                .lastUpdated(LocalDateTime.now())
                .build();

        // Cache for 1 hour
        cacheService.set(cacheKey, analytics, Duration.ofHours(1));
        
        return analytics;
    }

    // Helper methods
    private void validateUsers(UUID userId1, UUID userId2) {
        if (userId1.equals(userId2)) {
            throw new InvalidOperationException("Cannot perform payment operation with self");
        }

        if (!userService.userExists(userId1) || !userService.userExists(userId2)) {
            throw new UserNotFoundException("One or more users not found");
        }
    }

    private SocialConnection validateConnection(UUID userId1, UUID userId2) {
        Optional<SocialConnection> connection = connectionRepository
                .findByUserIdAndConnectedUserId(userId1, userId2);
        
        if (connection.isEmpty() || !connection.get().isActive()) {
            throw new ConnectionRequiredException("Active connection required for payment");
        }
        
        return connection.get();
    }

    private void validateTransactionLimits(SocialConnection connection, BigDecimal amount) {
        if (connection.getTransactionLimit() != null && 
            amount.compareTo(connection.getTransactionLimit()) > 0) {
            throw new TransactionLimitExceededException("Amount exceeds transaction limit");
        }

        // Check monthly limits
        if (connection.getMonthlyLimit() != null) {
            BigDecimal monthlySpent = paymentRepository.getMonthlySpentAmount(
                    connection.getUserId(), connection.getConnectedUserId());
            
            if (monthlySpent.add(amount).compareTo(connection.getMonthlyLimit()) > 0) {
                throw new MonthlyLimitExceededException("Amount exceeds monthly limit");
            }
        }
    }

    private void validateParticipants(UUID organizerId, List<UUID> participants) {
        if (participants.isEmpty()) {
            throw new InvalidOperationException("At least one participant required");
        }

        if (!participants.contains(organizerId)) {
            participants.add(organizerId);
        }

        for (UUID participantId : participants) {
            if (!organizerId.equals(participantId)) {
                validateConnection(organizerId, participantId);
            }
        }
    }

    private Map<UUID, BigDecimal> calculateSplitAmounts(BigDecimal totalAmount,
                                                        List<UUID> participants,
                                                        String splitType) {
        Map<UUID, BigDecimal> splitAmounts = new HashMap<>();

        if ("EQUAL".equals(splitType)) {
            int count = participants.size();

            // CRITICAL FIX: Distribute remainder to prevent money loss
            // Calculate base amount (rounded down)
            BigDecimal baseAmount = totalAmount.divide(
                    BigDecimal.valueOf(count), 2, RoundingMode.DOWN);

            // Calculate total allocated
            BigDecimal totalAllocated = baseAmount.multiply(BigDecimal.valueOf(count));

            // Calculate remainder (in cents)
            BigDecimal remainder = totalAmount.subtract(totalAllocated);
            BigDecimal cent = new BigDecimal("0.01");
            int centsToDistribute = remainder.divide(cent, 0, RoundingMode.DOWN).intValue();

            // Distribute base amount to all participants
            for (int i = 0; i < participants.size(); i++) {
                UUID participantId = participants.get(i);
                BigDecimal amount = baseAmount;

                // Add one cent to first N participants to distribute remainder
                if (i < centsToDistribute) {
                    amount = amount.add(cent);
                }

                splitAmounts.put(participantId, amount);
            }

            // ASSERT: Verify total equals original amount (prevents money loss)
            BigDecimal sum = splitAmounts.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (sum.compareTo(totalAmount) != 0) {
                log.error("CRITICAL: Split calculation error - sum: {}, expected: {}", sum, totalAmount);
                throw new IllegalStateException(
                    String.format("Split calculation error: calculated %s but expected %s", sum, totalAmount));
            }

            log.debug("Split {} equally among {} participants (base: {}, with cent: {})",
                totalAmount, count, baseAmount, centsToDistribute);
        }
        // Additional split types (CUSTOM, PERCENTAGE) would be implemented here

        return splitAmounts;
    }

    private void updateConnectionStats(SocialConnection connection, BigDecimal amount, boolean isSent) {
        connection.setTotalTransactions(connection.getTotalTransactions() + 1);
        connection.setLastTransactionAt(LocalDateTime.now());
        
        if (connection.getFirstTransactionAt() == null) {
            connection.setFirstTransactionAt(LocalDateTime.now());
        }
        
        if (isSent) {
            connection.setTotalAmountSent(connection.getTotalAmountSent().add(amount));
        } else {
            connection.setTotalAmountReceived(connection.getTotalAmountReceived().add(amount));
        }
        
        connectionRepository.save(connection);
    }

    private void createSocialFeedEntry(SocialPayment payment) {
        if (!payment.getIsPublic()) {
            return; // Don't create feed entry for private payments
        }

        SocialFeed feedEntry = SocialFeed.builder()
                .userId(payment.getSenderId())
                .activityId(payment.getId())
                .activityType(mapToFeedActivityType(payment.getPaymentType()))
                .title(generateFeedTitle(payment))
                .description(payment.getMessage())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .emoji(payment.getEmoji())
                .participants(List.of(payment.getRecipientId()))
                .mediaUrls(payment.getMediaAttachments())
                .tags(payment.getTags())
                .location(payment.getLocation())
                .visibility(SocialFeed.Visibility.valueOf(payment.getVisibility()))
                .build();

        feedRepository.save(feedEntry);
    }

    private void createBillSplitFeedEntry(UUID organizerId, SplitBillRequest request, 
                                         List<SocialPayment> splitPayments) {
        if (!request.getIsPublic()) {
            return;
        }

        SocialFeed feedEntry = SocialFeed.builder()
                .userId(organizerId)
                .activityId(splitPayments.get(0).getId()) // Use first split payment as reference
                .activityType(SocialFeed.ActivityType.BILL_SPLIT)
                .title("Split a bill")
                .description(request.getDescription())
                .amount(request.getTotalAmount())
                .currency(request.getCurrency())
                .emoji("🧾")
                .participants(request.getParticipants())
                .visibility(SocialFeed.Visibility.valueOf(request.getVisibility()))
                .build();

        feedRepository.save(feedEntry);
    }

    private void updateSocialFeedEntry(SocialPayment payment) {
        // Update existing feed entry when payment status changes
        feedRepository.findByActivityId(payment.getId())
                .ifPresent(feedEntry -> {
                    feedEntry.setDescription(feedEntry.getDescription() + " ✅ Completed");
                    feedRepository.save(feedEntry);
                });
    }

    private void sendPaymentNotifications(SocialPayment payment) {
        // Send notification to recipient
        notificationService.sendPaymentCompletedNotification(
                payment.getRecipientId(), payment.getSenderId(), payment);
        
        // Send confirmation to sender
        notificationService.sendPaymentSentConfirmation(
                payment.getSenderId(), payment.getRecipientId(), payment);
    }

    private void publishPaymentEvents(SocialPayment payment) {
        if (payment.getStatus() == SocialPayment.PaymentStatus.COMPLETED) {
            eventPublisher.publish(SocialPaymentCompletedEvent.builder()
                    .paymentId(payment.getId())
                    .senderId(payment.getSenderId())
                    .recipientId(payment.getRecipientId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .paymentType(payment.getPaymentType())
                    .completedAt(payment.getCompletedAt())
                    .build());
        }
    }

    private SocialFeed.ActivityType mapToFeedActivityType(SocialPayment.PaymentType paymentType) {
        switch (paymentType) {
            case SEND: return SocialFeed.ActivityType.PAYMENT_SENT;
            case REQUEST: return SocialFeed.ActivityType.PAYMENT_REQUESTED;
            case SPLIT: return SocialFeed.ActivityType.BILL_SPLIT;
            case GROUP: return SocialFeed.ActivityType.GROUP_PAYMENT;
            default: return SocialFeed.ActivityType.PAYMENT_SENT;
        }
    }

    private String generateFeedTitle(SocialPayment payment) {
        switch (payment.getPaymentType()) {
            case SEND:
                return "Sent money to " + userService.getUserDisplayName(payment.getRecipientId());
            case REQUEST:
                return "Requested money from " + userService.getUserDisplayName(payment.getSenderId());
            case SPLIT:
                return "Split a bill";
            case GIFT:
                return "Sent a gift";
            default:
                return "Made a payment";
        }
    }

    private SocialPaymentResponse mapToSocialPaymentResponse(SocialPayment payment) {
        return SocialPaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .senderId(payment.getSenderId())
                .recipientId(payment.getRecipientId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .message(payment.getMessage())
                .emoji(payment.getEmoji())
                .isPublic(payment.getIsPublic())
                .visibility(payment.getVisibility())
                .mediaAttachments(payment.getMediaAttachments())
                .tags(payment.getTags())
                .location(payment.getLocation())
                .occasion(payment.getOccasion())
                .requestExpiresAt(payment.getRequestExpiresAt())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }

    // ================== VENMO PARITY ENHANCEMENTS ==================

    /**
     * Add reaction to a payment (like, love, laugh, etc.)
     */
    @Transactional
    public PaymentReactionResponse addPaymentReaction(UUID userId, String paymentId, PaymentReactionRequest request) {
        log.info("Adding reaction {} to payment {} by user {}", request.getReactionType(), paymentId, userId);
        
        SocialPayment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new SocialPaymentNotFoundException("Payment not found"));
        
        // Validate user can react (must be sender, recipient, or have visibility)
        if (!canUserInteractWithPayment(userId, payment)) {
            throw new UnauthorizedOperationException("User cannot react to this payment");
        }
        
        // Create or update reaction
        PaymentReaction reaction = PaymentReaction.builder()
                .paymentId(payment.getId())
                .userId(userId)
                .reactionType(request.getReactionType())
                .createdAt(LocalDateTime.now())
                .build();
        
        // Save reaction and update payment metrics
        reaction = paymentReactionRepository.save(reaction);
        updatePaymentEngagementMetrics(payment);
        
        // Send notification to payment participants
        sendReactionNotification(payment, userId, request.getReactionType());
        
        // Publish event
        eventPublisher.publish(PaymentReactionAddedEvent.builder()
                .paymentId(payment.getId())
                .userId(userId)
                .reactionType(request.getReactionType())
                .build());
        
        return PaymentReactionResponse.builder()
                .reactionId(reaction.getId())
                .paymentId(paymentId)
                .userId(userId)
                .reactionType(request.getReactionType())
                .createdAt(reaction.getCreatedAt())
                .build();
    }

    /**
     * Add comment to a payment
     */
    @Transactional
    public PaymentCommentResponse addPaymentComment(UUID userId, String paymentId, PaymentCommentRequest request) {
        log.info("Adding comment to payment {} by user {}", paymentId, userId);
        
        SocialPayment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new SocialPaymentNotFoundException("Payment not found"));
        
        if (!canUserInteractWithPayment(userId, payment)) {
            throw new UnauthorizedOperationException("User cannot comment on this payment");
        }
        
        PaymentComment comment = PaymentComment.builder()
                .paymentId(payment.getId())
                .userId(userId)
                .comment(request.getComment())
                .emoji(request.getEmoji())
                .createdAt(LocalDateTime.now())
                .build();
        
        comment = paymentCommentRepository.save(comment);
        updatePaymentEngagementMetrics(payment);
        
        // Send notification to payment participants
        sendCommentNotification(payment, userId, request.getComment());
        
        return PaymentCommentResponse.builder()
                .commentId(comment.getId())
                .paymentId(paymentId)
                .userId(userId)
                .comment(request.getComment())
                .emoji(request.getEmoji())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    /**
     * Get enhanced social payment feed with reactions and comments
     */
    @Transactional(readOnly = true)
    public Page<EnhancedSocialFeedResponse> getEnhancedSocialFeed(UUID userId, Pageable pageable) {
        log.info("Getting enhanced social feed for user {}", userId);
        
        // Get feed items visible to user (friends, public, etc.)
        Page<SocialFeed> feedItems = feedRepository.findVisibleFeedForUser(userId, pageable);
        
        return feedItems.map(feedItem -> {
            // Get payment details
            SocialPayment payment = paymentRepository.findById(feedItem.getActivityId())
                    .orElse(null);
            
            if (payment == null) {
                return null; // Skip invalid entries
            }
            
            // Get reactions and comments
            List<PaymentReaction> reactions = paymentReactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
            List<PaymentComment> comments = paymentCommentRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
            
            return EnhancedSocialFeedResponse.builder()
                    .feedId(feedItem.getId())
                    .payment(mapToSocialPaymentResponse(payment))
                    .title(feedItem.getTitle())
                    .description(feedItem.getDescription())
                    .emoji(feedItem.getEmoji())
                    .mediaUrls(feedItem.getMediaUrls())
                    .tags(feedItem.getTags())
                    .location(feedItem.getLocation())
                    .reactions(reactions.stream()
                            .collect(Collectors.groupingBy(
                                    PaymentReaction::getReactionType,
                                    Collectors.counting())))
                    .comments(comments.stream()
                            .map(this::mapToPaymentCommentResponse)
                            .collect(Collectors.toList()))
                    .engagementCount(reactions.size() + comments.size())
                    .canInteract(canUserInteractWithPayment(userId, payment))
                    .createdAt(feedItem.getCreatedAt())
                    .build();
        }).filter(Objects::nonNull);
    }

    /**
     * Create payment story (temporary payment sharing)
     */
    @Transactional
    public PaymentStoryResponse createPaymentStory(UUID userId, CreatePaymentStoryRequest request) {
        log.info("Creating payment story for user {}", userId);
        
        SocialPayment payment = paymentRepository.findByPaymentId(request.getPaymentId())
                .orElseThrow(() -> new SocialPaymentNotFoundException("Payment not found"));
        
        // Validate user owns the payment
        if (!payment.getSenderId().equals(userId) && !payment.getRecipientId().equals(userId)) {
            throw new UnauthorizedOperationException("User cannot create story for this payment");
        }
        
        PaymentStory story = PaymentStory.builder()
                .userId(userId)
                .paymentId(payment.getId())
                .storyType(request.getStoryType())
                .caption(request.getCaption())
                .backgroundStyle(request.getBackgroundStyle())
                .stickers(request.getStickers())
                .mediaUrl(request.getMediaUrl())
                .visibility(request.getVisibility())
                .expiresAt(LocalDateTime.now().plusHours(24)) // Stories expire in 24 hours
                .createdAt(LocalDateTime.now())
                .build();
        
        story = paymentStoryRepository.save(story);
        
        // Publish event
        eventPublisher.publish(PaymentStoryCreatedEvent.builder()
                .storyId(story.getId())
                .userId(userId)
                .paymentId(payment.getId())
                .visibility(request.getVisibility())
                .build());
        
        return PaymentStoryResponse.builder()
                .storyId(story.getId())
                .paymentId(request.getPaymentId())
                .caption(story.getCaption())
                .backgroundStyle(story.getBackgroundStyle())
                .mediaUrl(story.getMediaUrl())
                .visibility(story.getVisibility())
                .expiresAt(story.getExpiresAt())
                .createdAt(story.getCreatedAt())
                .build();
    }

    /**
     * Get active payment stories from friends
     */
    @Transactional(readOnly = true)
    public List<PaymentStoryResponse> getActiveStories(UUID userId) {
        log.info("Getting active stories for user {}", userId);
        
        // Get stories from friends and followed users
        List<UUID> friendIds = connectionRepository.findActiveConnectionUserIds(userId);
        List<PaymentStory> activeStories = paymentStoryRepository.findActiveStoriesFromUsers(friendIds);
        
        return activeStories.stream()
                .map(this::mapToPaymentStoryResponse)
                .collect(Collectors.toList());
    }

    /**
     * Enhanced split bill with custom amounts and percentages
     */
    @Transactional
    public EnhancedSplitBillResponse createEnhancedSplitBill(UUID userId, EnhancedSplitBillRequest request) {
        log.info("Creating enhanced split bill from user: {} with {} participants", userId, request.getParticipants().size());
        
        validateParticipants(userId, request.getParticipants().stream().map(p -> p.getUserId()).collect(Collectors.toList()));
        
        List<SocialPayment> splitPayments = new ArrayList<>();
        BigDecimal totalCollected = BigDecimal.ZERO;
        
        for (SplitParticipant participant : request.getParticipants()) {
            if (participant.getUserId().equals(userId)) {
                continue; // Skip organizer
            }
            
            BigDecimal participantAmount = calculateParticipantAmount(request, participant);
            
            SocialPayment payment = SocialPayment.builder()
                    .senderId(participant.getUserId())
                    .recipientId(userId)
                    .amount(participantAmount)
                    .currency(request.getCurrency())
                    .paymentType(SocialPayment.PaymentType.SPLIT)
                    .status(SocialPayment.PaymentStatus.REQUESTED)
                    .message(request.getDescription())
                    .emoji("🧾")
                    .isPublic(request.getIsPublic())
                    .visibility(request.getVisibility())
                    .splitDetails(Map.of(
                            "totalAmount", request.getTotalAmount(),
                            "splitType", request.getSplitType(),
                            "organizer", userId,
                            "participants", request.getParticipants(),
                            "customAmount", participant.getCustomAmount(),
                            "percentage", participant.getPercentage(),
                            "billCategory", request.getBillCategory(),
                            "location", request.getLocation()
                    ))
                    .requestExpiresAt(request.getExpiresAt())
                    .requestedAt(LocalDateTime.now())
                    .build();
            
            splitPayments.add(paymentRepository.save(payment));
            totalCollected = totalCollected.add(participantAmount);
        }
        
        // Create enhanced bill entry
        SplitBill splitBill = SplitBill.builder()
                .organizerId(userId)
                .billTitle(request.getBillTitle())
                .description(request.getDescription())
                .totalAmount(request.getTotalAmount())
                .currency(request.getCurrency())
                .splitType(request.getSplitType())
                .billCategory(request.getBillCategory())
                .location(request.getLocation())
                .participants(request.getParticipants())
                .splitPayments(splitPayments.stream().map(SocialPayment::getId).collect(Collectors.toList()))
                .isRecurring(request.getIsRecurring())
                .recurringSchedule(request.getRecurringSchedule())
                .status(SplitBillStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        splitBill = splitBillRepository.save(splitBill);
        
        // Send enhanced notifications
        sendEnhancedSplitBillNotifications(splitBill, splitPayments);
        
        // Create enhanced feed entry
        createEnhancedBillSplitFeedEntry(splitBill);
        
        return EnhancedSplitBillResponse.builder()
                .billId(splitBill.getId())
                .organizerId(userId)
                .billTitle(request.getBillTitle())
                .totalAmount(request.getTotalAmount())
                .totalCollected(totalCollected)
                .totalRemaining(request.getTotalAmount().subtract(totalCollected))
                .participantDetails(request.getParticipants())
                .paymentRequests(splitPayments.stream()
                        .map(this::mapToSocialPaymentResponse)
                        .collect(Collectors.toList()))
                .status(splitBill.getStatus())
                .createdAt(splitBill.getCreatedAt())
                .build();
    }

    /**
     * Generate payment QR code for easy sharing
     */
    @Transactional
    public PaymentQRCodeResponse generatePaymentQRCode(UUID userId, GenerateQRCodeRequest request) {
        log.info("Generating payment QR code for user {}", userId);
        
        // Create QR code data
        Map<String, Object> qrData = Map.of(
                "type", "PAYMENT_REQUEST",
                "userId", userId,
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "message", request.getMessage(),
                "expiresAt", LocalDateTime.now().plusMinutes(request.getExpiryMinutes())
        );
        
        PaymentQRCode qrCode = PaymentQRCode.builder()
                .userId(userId)
                .qrCodeData(qrData)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .message(request.getMessage())
                .isActive(true)
                .usageCount(0)
                .maxUsages(request.getMaxUsages())
                .expiresAt(LocalDateTime.now().plusMinutes(request.getExpiryMinutes()))
                .createdAt(LocalDateTime.now())
                .build();
        
        qrCode = paymentQRCodeRepository.save(qrCode);
        
        // Generate QR code image
        String qrCodeImageUrl = qrCodeGeneratorService.generateQRCodeImage(qrCode.getId().toString(), qrData);
        qrCode.setQrCodeImageUrl(qrCodeImageUrl);
        qrCode = paymentQRCodeRepository.save(qrCode);
        
        return PaymentQRCodeResponse.builder()
                .qrCodeId(qrCode.getId())
                .qrCodeImageUrl(qrCodeImageUrl)
                .qrCodeData(qrData)
                .expiresAt(qrCode.getExpiresAt())
                .build();
    }

    /**
     * Process payment via QR code scan
     */
    @Transactional
    public SocialPaymentResponse processQRCodePayment(UUID userId, QRCodePaymentRequest request) {
        log.info("Processing QR code payment from user {} to QR code {}", userId, request.getQrCodeId());
        
        PaymentQRCode qrCode = paymentQRCodeRepository.findById(request.getQrCodeId())
                .orElseThrow(() -> new QRCodeNotFoundException("QR code not found"));
        
        // Validate QR code
        if (!qrCode.getIsActive() || LocalDateTime.now().isAfter(qrCode.getExpiresAt())) {
            throw new QRCodeExpiredException("QR code has expired");
        }
        
        if (qrCode.getMaxUsages() != null && qrCode.getUsageCount() >= qrCode.getMaxUsages()) {
            throw new QRCodeUsageLimitExceededException("QR code usage limit exceeded");
        }
        
        // Create payment request
        SendMoneyRequest paymentRequest = SendMoneyRequest.builder()
                .recipientId(qrCode.getUserId())
                .amount(request.getAmount() != null ? request.getAmount() : qrCode.getAmount())
                .currency(qrCode.getCurrency())
                .message(qrCode.getMessage())
                .emoji("📱")
                .isPublic(request.getIsPublic())
                .visibility(request.getVisibility())
                .sourceMethod("QR_CODE")
                .metadata(Map.of("qrCodeId", qrCode.getId()))
                .build();
        
        // Process payment
        SocialPaymentResponse response = sendMoney(userId, paymentRequest);
        
        // Update QR code usage
        qrCode.setUsageCount(qrCode.getUsageCount() + 1);
        qrCode.setLastUsedAt(LocalDateTime.now());
        paymentQRCodeRepository.save(qrCode);
        
        return response;
    }

    /**
     * Start payment challenge between friends
     */
    @Transactional
    public PaymentChallengeResponse createPaymentChallenge(UUID userId, CreatePaymentChallengeRequest request) {
        log.info("Creating payment challenge from user {} to {}", userId, request.getChallengedUserId());
        
        validateConnection(userId, request.getChallengedUserId());
        
        PaymentChallenge challenge = PaymentChallenge.builder()
                .challengerId(userId)
                .challengedUserId(request.getChallengedUserId())
                .challengeType(request.getChallengeType())
                .title(request.getTitle())
                .description(request.getDescription())
                .targetAmount(request.getTargetAmount())
                .targetTransactions(request.getTargetTransactions())
                .timeLimit(request.getTimeLimit())
                .prize(request.getPrize())
                .status(PaymentChallengeStatus.PENDING)
                .expiresAt(LocalDateTime.now().plus(request.getTimeLimit()))
                .createdAt(LocalDateTime.now())
                .build();
        
        challenge = paymentChallengeRepository.save(challenge);
        
        // Send challenge notification
        notificationService.sendPaymentChallengeNotification(request.getChallengedUserId(), userId, challenge);
        
        return PaymentChallengeResponse.builder()
                .challengeId(challenge.getId())
                .challengerId(userId)
                .challengedUserId(request.getChallengedUserId())
                .title(challenge.getTitle())
                .description(challenge.getDescription())
                .status(challenge.getStatus())
                .expiresAt(challenge.getExpiresAt())
                .build();
    }

    // ================== HELPER METHODS FOR ENHANCEMENTS ==================

    private boolean canUserInteractWithPayment(UUID userId, SocialPayment payment) {
        // User can interact if they are sender, recipient, or payment is public/friends-visible
        if (payment.getSenderId().equals(userId) || payment.getRecipientId().equals(userId)) {
            return true;
        }
        
        if (!payment.getIsPublic()) {
            return false;
        }
        
        // Check if users are connected for friends-only visibility
        if ("FRIENDS".equals(payment.getVisibility())) {
            return connectionRepository.areUsersConnected(userId, payment.getSenderId()) ||
                   connectionRepository.areUsersConnected(userId, payment.getRecipientId());
        }
        
        return "PUBLIC".equals(payment.getVisibility());
    }

    private void updatePaymentEngagementMetrics(SocialPayment payment) {
        int reactionCount = paymentReactionRepository.countByPaymentId(payment.getId());
        int commentCount = paymentCommentRepository.countByPaymentId(payment.getId());
        
        payment.setEngagementScore(reactionCount + commentCount);
        paymentRepository.save(payment);
    }

    private void sendReactionNotification(SocialPayment payment, UUID reactorId, String reactionType) {
        // Notify payment participants about the reaction
        Set<UUID> participantIds = Set.of(payment.getSenderId(), payment.getRecipientId());
        participantIds.remove(reactorId); // Don't notify the reactor
        
        for (UUID participantId : participantIds) {
            notificationService.sendPaymentReactionNotification(participantId, reactorId, payment, reactionType);
        }
    }

    private void sendCommentNotification(SocialPayment payment, UUID commenterId, String comment) {
        Set<UUID> participantIds = Set.of(payment.getSenderId(), payment.getRecipientId());
        participantIds.remove(commenterId);
        
        for (UUID participantId : participantIds) {
            notificationService.sendPaymentCommentNotification(participantId, commenterId, payment, comment);
        }
    }

    private PaymentCommentResponse mapToPaymentCommentResponse(PaymentComment comment) {
        return PaymentCommentResponse.builder()
                .commentId(comment.getId())
                .userId(comment.getUserId())
                .userDisplayName(userService.getUserDisplayName(comment.getUserId()))
                .comment(comment.getComment())
                .emoji(comment.getEmoji())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private PaymentStoryResponse mapToPaymentStoryResponse(PaymentStory story) {
        return PaymentStoryResponse.builder()
                .storyId(story.getId())
                .userId(story.getUserId())
                .paymentId(story.getPaymentId().toString())
                .caption(story.getCaption())
                .backgroundStyle(story.getBackgroundStyle())
                .mediaUrl(story.getMediaUrl())
                .visibility(story.getVisibility())
                .expiresAt(story.getExpiresAt())
                .createdAt(story.getCreatedAt())
                .build();
    }

    private BigDecimal calculateParticipantAmount(EnhancedSplitBillRequest request, SplitParticipant participant) {
        switch (request.getSplitType()) {
            case "EQUAL":
                return request.getTotalAmount().divide(
                        BigDecimal.valueOf(request.getParticipants().size()), 2, RoundingMode.HALF_UP);
            case "CUSTOM":
                return participant.getCustomAmount();
            case "PERCENTAGE":
                return request.getTotalAmount()
                        .multiply(participant.getPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            default:
                throw new IllegalArgumentException("Unsupported split type: " + request.getSplitType());
        }
    }

    private void sendEnhancedSplitBillNotifications(SplitBill splitBill, List<SocialPayment> splitPayments) {
        for (SocialPayment payment : splitPayments) {
            notificationService.sendEnhancedBillSplitNotification(
                    payment.getSenderId(), splitBill.getOrganizerId(), splitBill, payment);
        }
    }

    private void createEnhancedBillSplitFeedEntry(SplitBill splitBill) {
        SocialFeed feedEntry = SocialFeed.builder()
                .userId(splitBill.getOrganizerId())
                .activityId(splitBill.getId())
                .activityType(SocialFeed.ActivityType.ENHANCED_BILL_SPLIT)
                .title(splitBill.getBillTitle())
                .description(splitBill.getDescription())
                .amount(splitBill.getTotalAmount())
                .currency(splitBill.getCurrency())
                .emoji("🧾")
                .participants(splitBill.getParticipants().stream()
                        .map(SplitParticipant::getUserId)
                        .collect(Collectors.toList()))
                .location(splitBill.getLocation())
                .tags(List.of(splitBill.getBillCategory()))
                .visibility(SocialFeed.Visibility.FRIENDS)
                .build();

        feedRepository.save(feedEntry);
    }
    
    // ================== AI-POWERED & REAL-TIME ENHANCEMENTS ==================
    
    /**
     * Handle enhanced AI-powered and real-time features for completed payments
     */
    private void handleEnhancedPaymentFeatures(SocialPayment payment) {
        CompletableFuture.runAsync(() -> {
            try {
                // Real-time WebSocket notifications
                if (webSocketEnabled) {
                    broadcastPaymentUpdate(payment);
                }
                
                // AI-powered recommendations update
                if (aiRecommendationsEnabled) {
                    updateAIRecommendations(payment);
                }
                
                // Viral payment tracking
                if (viralTrackingEnabled) {
                    trackViralPayment(payment);
                }
                
                // Gamification features
                if (gamificationEnabled) {
                    processGamificationRewards(payment);
                }
                
                // Enhanced content moderation
                moderatePaymentContent(payment);
                
            } catch (Exception e) {
                log.error("Error in enhanced payment features for payment: {}", payment.getId(), e);
            }
        });
    }
    
    /**
     * Broadcast real-time payment updates via WebSocket
     */
    private void broadcastPaymentUpdate(SocialPayment payment) {
        try {
            PaymentUpdateMessage updateMessage = PaymentUpdateMessage.builder()
                    .paymentId(payment.getPaymentId())
                    .senderId(payment.getSenderId())
                    .recipientId(payment.getRecipientId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .status(payment.getStatus())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Broadcast to sender
            messagingTemplate.convertAndSendToUser(
                payment.getSenderId().toString(), 
                "/queue/payment-updates", 
                updateMessage
            );
            
            // Broadcast to recipient
            messagingTemplate.convertAndSendToUser(
                payment.getRecipientId().toString(), 
                "/queue/payment-updates", 
                updateMessage
            );
            
            // Broadcast to friends' feeds if public
            if (payment.getIsPublic()) {
                broadcastToFriendsFeed(payment, updateMessage);
            }
            
            log.debug("Broadcasted real-time payment update for payment: {}", payment.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error broadcasting payment update: {}", payment.getPaymentId(), e);
        }
    }
    
    /**
     * Broadcast payment updates to friends' feeds
     */
    private void broadcastToFriendsFeed(SocialPayment payment, PaymentUpdateMessage updateMessage) {
        try {
            // Get friends of both sender and recipient
            Set<UUID> friendIds = new HashSet<>();
            friendIds.addAll(connectionRepository.findActiveConnectionUserIds(payment.getSenderId()));
            friendIds.addAll(connectionRepository.findActiveConnectionUserIds(payment.getRecipientId()));
            
            // Remove sender and recipient from friends list
            friendIds.remove(payment.getSenderId());
            friendIds.remove(payment.getRecipientId());
            
            for (UUID friendId : friendIds) {
                messagingTemplate.convertAndSendToUser(
                    friendId.toString(), 
                    "/queue/feed-updates", 
                    updateMessage
                );
            }
            
        } catch (Exception e) {
            log.error("Error broadcasting to friends feed for payment: {}", payment.getPaymentId(), e);
        }
    }
    
    /**
     * Update AI-powered payment recommendations based on new payment
     */
    private void updateAIRecommendations(SocialPayment payment) {
        try {
            // Update recommendations for sender
            CompletableFuture.runAsync(() -> {
                List<PaymentRecommendation> senderRecommendations = 
                    recommendationEngine.generateRecommendations(payment.getSenderId(), payment);
                recommendationCache.put(payment.getSenderId(), senderRecommendations);
                
                // Send real-time recommendation updates
                if (webSocketEnabled) {
                    messagingTemplate.convertAndSendToUser(
                        payment.getSenderId().toString(),
                        "/queue/recommendations",
                        senderRecommendations
                    );
                }
            });
            
            // Update recommendations for recipient
            CompletableFuture.runAsync(() -> {
                List<PaymentRecommendation> recipientRecommendations = 
                    recommendationEngine.generateRecommendations(payment.getRecipientId(), payment);
                recommendationCache.put(payment.getRecipientId(), recipientRecommendations);
                
                if (webSocketEnabled) {
                    messagingTemplate.convertAndSendToUser(
                        payment.getRecipientId().toString(),
                        "/queue/recommendations",
                        recipientRecommendations
                    );
                }
            });
            
            log.debug("Updated AI recommendations for payment: {}", payment.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error updating AI recommendations for payment: {}", payment.getPaymentId(), e);
        }
    }
    
    /**
     * Track viral payment patterns and influence
     */
    private void trackViralPayment(SocialPayment payment) {
        try {
            ViralPaymentMetrics metrics = viralTracker.trackPayment(payment);
            viralMetrics.put(payment.getId(), metrics);
            
            // Check if payment is going viral
            if (metrics.getViralScore() > 0.7) {
                handleViralPayment(payment, metrics);
            }
            
            // Update trending payments cache
            updateTrendingPayments(payment, metrics);
            
            log.debug("Tracked viral metrics for payment: {}, score: {}", 
                payment.getPaymentId(), metrics.getViralScore());
                
        } catch (Exception e) {
            log.error("Error tracking viral payment: {}", payment.getPaymentId(), e);
        }
    }
    
    /**
     * Handle payments that are going viral
     */
    private void handleViralPayment(SocialPayment payment, ViralPaymentMetrics metrics) {
        try {
            // Create viral payment notification
            ViralPaymentNotification notification = ViralPaymentNotification.builder()
                    .paymentId(payment.getId())
                    .viralScore(metrics.getViralScore())
                    .engagementRate(metrics.getEngagementRate())
                    .shareCount(metrics.getShareCount())
                    .reactionVelocity(metrics.getReactionVelocity())
                    .detectedAt(LocalDateTime.now())
                    .build();
            
            // Boost visibility in feeds
            boostPaymentVisibility(payment, metrics);
            
            // Send viral achievement notifications
            sendViralAchievementNotifications(payment, metrics);
            
            // Update gamification rewards
            if (gamificationEnabled) {
                gamificationService.awardViralPaymentBadge(payment.getSenderId(), payment);
            }
            
            log.info("Payment going viral detected: {}, score: {}", 
                payment.getPaymentId(), metrics.getViralScore());
                
        } catch (Exception e) {
            log.error("Error handling viral payment: {}", payment.getPaymentId(), e);
        }
    }
    
    /**
     * Process gamification rewards and achievements
     */
    private void processGamificationRewards(SocialPayment payment) {
        try {
            // Award points for payment completion
            gamificationService.awardPaymentPoints(payment.getSenderId(), payment);
            gamificationService.awardPaymentPoints(payment.getRecipientId(), payment);
            
            // Check for achievement milestones
            List<Achievement> senderAchievements = 
                gamificationService.checkAchievements(payment.getSenderId(), payment);
            List<Achievement> recipientAchievements = 
                gamificationService.checkAchievements(payment.getRecipientId(), payment);
            
            // Send achievement notifications
            if (!senderAchievements.isEmpty()) {
                sendAchievementNotifications(payment.getSenderId(), senderAchievements);
            }
            if (!recipientAchievements.isEmpty()) {
                sendAchievementNotifications(payment.getRecipientId(), recipientAchievements);
            }
            
            // Update leaderboards
            gamificationService.updateLeaderboards(payment);
            
            log.debug("Processed gamification rewards for payment: {}", payment.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error processing gamification rewards: {}", payment.getPaymentId(), e);
        }
    }
    
    /**
     * Enhanced AI-powered content moderation
     */
    private void moderatePaymentContent(SocialPayment payment) {
        try {
            // Moderate payment message
            if (payment.getMessage() != null && !payment.getMessage().isEmpty()) {
                ContentModerationResult messageResult = 
                    contentModerationService.moderateText(payment.getMessage());
                    
                if (messageResult.getRiskScore() > aiModerationThreshold) {
                    handleHighRiskContent(payment, messageResult, "message");
                }
            }
            
            // Moderate media attachments
            if (payment.getMediaAttachments() != null && !payment.getMediaAttachments().isEmpty()) {
                for (String mediaUrl : payment.getMediaAttachments()) {
                    ContentModerationResult mediaResult = 
                        contentModerationService.moderateMedia(mediaUrl);
                        
                    if (mediaResult.getRiskScore() > aiModerationThreshold) {
                        handleHighRiskContent(payment, mediaResult, "media");
                    }
                }
            }
            
            // AI-powered fraud detection
            FraudDetectionResult fraudResult = 
                contentModerationService.detectPaymentFraud(payment);
                
            if (fraudResult.isSuspicious()) {
                handleSuspiciousPayment(payment, fraudResult);
            }
            
            log.debug("Completed content moderation for payment: {}", payment.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error in content moderation for payment: {}", payment.getPaymentId(), e);
        }
    }
    
    /**
     * Get AI-powered payment recommendations for user
     */
    @Cacheable(value = "payment-recommendations", key = "#userId")
    public List<PaymentRecommendation> getAIPaymentRecommendations(UUID userId) {
        log.debug("Getting AI payment recommendations for user: {}", userId);
        
        try {
            // Check cache first
            List<PaymentRecommendation> cached = recommendationCache.get(userId);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
            
            // Generate new recommendations
            List<PaymentRecommendation> recommendations = 
                recommendationEngine.generatePersonalizedRecommendations(userId);
                
            // Cache recommendations
            recommendationCache.put(userId, recommendations);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error getting AI recommendations for user: {}", userId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get trending payments with viral metrics
     */
    @Cacheable(value = "trending-payments", key = "#userId + '_' + #timeframe")
    public List<TrendingPaymentResponse> getTrendingPayments(UUID userId, String timeframe) {
        log.debug("Getting trending payments for user: {}, timeframe: {}", userId, timeframe);
        
        try {
            LocalDateTime since = calculateTimeframeCutoff(timeframe);
            
            // Get payments with high viral scores
            List<SocialPayment> trendingPayments = paymentRepository
                .findTrendingPayments(since, userId);
                
            return trendingPayments.stream()
                .map(payment -> {
                    ViralPaymentMetrics metrics = viralMetrics.get(payment.getId());
                    return TrendingPaymentResponse.builder()
                        .payment(mapToSocialPaymentResponse(payment))
                        .viralScore(metrics != null ? metrics.getViralScore() : 0.0)
                        .engagementRate(metrics != null ? metrics.getEngagementRate() : 0.0)
                        .trendingRank(calculateTrendingRank(payment, metrics))
                        .build();
                })
                .sorted((a, b) -> Double.compare(b.getViralScore(), a.getViralScore()))
                .limit(20)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting trending payments for user: {}", userId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Enhanced real-time payment analytics with AI insights
     */
    @Cacheable(value = "enhanced-analytics", key = "#userId")
    public EnhancedPaymentAnalytics getEnhancedPaymentAnalytics(UUID userId) {
        log.debug("Getting enhanced payment analytics for user: {}", userId);
        
        try {
            // Base analytics
            SocialPaymentAnalytics baseAnalytics = getPaymentAnalytics(userId);
            
            // AI-powered insights
            PaymentInsights aiInsights = generateAIInsights(userId, baseAnalytics);
            
            // Viral performance metrics
            ViralPerformanceMetrics viralMetrics = calculateViralPerformance(userId);
            
            // Gamification progress
            GamificationProgress gamificationProgress = 
                gamificationService.getUserProgress(userId);
                
            return EnhancedPaymentAnalytics.builder()
                .baseAnalytics(baseAnalytics)
                .aiInsights(aiInsights)
                .viralMetrics(viralMetrics)
                .gamificationProgress(gamificationProgress)
                .recommendations(getAIPaymentRecommendations(userId))
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error getting enhanced analytics for user: {}", userId, e);
            return null;
        }
    }
    
    // ================== HELPER METHODS FOR ENHANCED FEATURES ==================
    
    private void updateTrendingPayments(SocialPayment payment, ViralPaymentMetrics metrics) {
        // Update trending payments cache
        cacheManager.getCache("trending-payments").evict("global");
    }
    
    private void boostPaymentVisibility(SocialPayment payment, ViralPaymentMetrics metrics) {
        // Boost payment in algorithms and feeds
        payment.setViralScore(metrics.getViralScore());
        payment.setTrendingBoost(true);
        paymentRepository.save(payment);
    }
    
    private void sendViralAchievementNotifications(SocialPayment payment, ViralPaymentMetrics metrics) {
        notificationService.sendViralPaymentNotification(
            payment.getSenderId(), payment, metrics
        );
    }
    
    private void sendAchievementNotifications(UUID userId, List<Achievement> achievements) {
        for (Achievement achievement : achievements) {
            notificationService.sendAchievementNotification(userId, achievement);
            
            if (webSocketEnabled) {
                messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/achievements",
                    achievement
                );
            }
        }
    }
    
    private void handleHighRiskContent(SocialPayment payment, ContentModerationResult result, String contentType) {
        log.warn("High risk {} content detected in payment: {}, risk score: {}", 
            contentType, payment.getPaymentId(), result.getRiskScore());
            
        // Flag for review
        payment.setContentFlagged(true);
        payment.setModerationScore(result.getRiskScore());
        paymentRepository.save(payment);
        
        // Notify moderation team
        notificationService.sendModerationAlert(payment, result, contentType);
    }
    
    private void handleSuspiciousPayment(SocialPayment payment, FraudDetectionResult fraudResult) {
        log.warn("Suspicious payment detected: {}, fraud score: {}", 
            payment.getPaymentId(), fraudResult.getFraudScore());
            
        // Temporarily hold payment for review
        payment.setStatus(SocialPayment.PaymentStatus.UNDER_REVIEW);
        payment.setFraudScore(fraudResult.getFraudScore());
        paymentRepository.save(payment);
        
        // Alert fraud team
        notificationService.sendFraudAlert(payment, fraudResult);
    }
    
    private PaymentInsights generateAIInsights(UUID userId, SocialPaymentAnalytics analytics) {
        try {
            String prompt = String.format(
                "Analyze these payment patterns and generate insights: %s", 
                analytics.toString()
            );
            
            ChatResponse response = openAiChatClient.call(
                new Prompt(new UserMessage(prompt))
            );
            
            return PaymentInsights.builder()
                .spendingPatterns(extractSpendingPatterns(response))
                .savingsOpportunities(extractSavingsOpportunities(response))
                .socialTrends(extractSocialTrends(response))
                .personalizedTips(extractPersonalizedTips(response))
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating AI insights for user: {}", userId, e);
            return PaymentInsights.builder().build();
        }
    }
    
    private ViralPerformanceMetrics calculateViralPerformance(UUID userId) {
        List<SocialPayment> userPayments = paymentRepository.findRecentPaymentsByUser(userId, 30);
        
        double avgViralScore = userPayments.stream()
            .mapToDouble(payment -> {
                ViralPaymentMetrics metrics = viralMetrics.get(payment.getId());
                return metrics != null ? metrics.getViralScore() : 0.0;
            })
            .average()
            .orElse(0.0);
            
        long viralPaymentsCount = userPayments.stream()
            .mapToLong(payment -> {
                ViralPaymentMetrics metrics = viralMetrics.get(payment.getId());
                return metrics != null && metrics.getViralScore() > 0.5 ? 1 : 0;
            })
            .sum();
            
        return ViralPerformanceMetrics.builder()
            .averageViralScore(avgViralScore)
            .viralPaymentsCount(viralPaymentsCount)
            .totalEngagementReceived(calculateTotalEngagement(userPayments))
            .viralRank(calculateUserViralRank(userId, avgViralScore))
            .build();
    }
    
    private LocalDateTime calculateTimeframeCutoff(String timeframe) {
        switch (timeframe.toLowerCase()) {
            case "hour": return LocalDateTime.now().minusHours(1);
            case "day": return LocalDateTime.now().minusDays(1);
            case "week": return LocalDateTime.now().minusWeeks(1);
            case "month": return LocalDateTime.now().minusMonths(1);
            default: return LocalDateTime.now().minusDays(1);
        }
    }
    
    private int calculateTrendingRank(SocialPayment payment, ViralPaymentMetrics metrics) {
        // Complex ranking algorithm considering viral score, engagement, recency
        if (metrics == null) return Integer.MAX_VALUE;
        
        double timeDecay = calculateTimeDecay(payment.getCreatedAt());
        double rankScore = metrics.getViralScore() * 0.4 + 
                          metrics.getEngagementRate() * 0.3 + 
                          timeDecay * 0.3;
                          
        return (int) (rankScore * 1000); // Scale for ranking
    }
    
    private double calculateTimeDecay(LocalDateTime createdAt) {
        long hoursAgo = Duration.between(createdAt, LocalDateTime.now()).toHours();
        return Math.max(0.1, 1.0 / (1.0 + hoursAgo * 0.1));
    }
    
    private long calculateTotalEngagement(List<SocialPayment> payments) {
        return payments.stream()
            .mapToLong(payment -> {
                ViralPaymentMetrics metrics = viralMetrics.get(payment.getId());
                return metrics != null ? metrics.getTotalEngagement() : 0;
            })
            .sum();
    }
    
    private int calculateUserViralRank(UUID userId, double avgViralScore) {
        // Calculate user's rank among all users based on viral performance
        // This would typically query a leaderboard or ranking service
        return gamificationService.getUserViralRank(userId);
    }
    
    private List<String> extractSpendingPatterns(ChatResponse response) {
        // Parse AI response to extract spending patterns
        return Arrays.asList(response.getResult().getOutput().getContent().split("\\n"));
    }
    
    private List<String> extractSavingsOpportunities(ChatResponse response) {
        // Parse AI response to extract savings opportunities
        return Arrays.asList();
    }
    
    private List<String> extractSocialTrends(ChatResponse response) {
        // Parse AI response to extract social trends
        return Arrays.asList();
    }
    
    private List<String> extractPersonalizedTips(ChatResponse response) {
        // Parse AI response to extract personalized tips
        return Arrays.asList();
    }
}