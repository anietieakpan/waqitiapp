package com.waqiti.social.service;

import com.waqiti.social.domain.*;
import com.waqiti.social.dto.*;
import com.waqiti.social.exception.*;
import com.waqiti.social.repository.*;
import com.waqiti.common.security.AuthenticationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Social Payment Integration Service
 * 
 * Handles social payments, split bills, payment requests, group payments,
 * social challenges, payment streams, and social commerce integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialPaymentIntegrationService {

    private final SocialPaymentRepository paymentRepository;
    private final SocialConnectionRepository connectionRepository;
    private final SplitBillRepository splitBillRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentChallengeRepository challengeRepository;
    private final PaymentStreamRepository streamRepository;
    private final GroupPaymentRepository groupPaymentRepository;
    private final SocialWalletRepository walletRepository;
    private final PaymentService corePaymentService;
    private final NotificationService notificationService;
    private final AuthenticationFacade authenticationFacade;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Payment configuration
    private static final BigDecimal MAX_SOCIAL_PAYMENT_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_SPLIT_PARTICIPANTS = 20;
    private static final int PAYMENT_REQUEST_EXPIRY_DAYS = 30;

    /**
     * Send social payment with enhanced social features
     */
    public SocialPaymentResponse sendSocialPayment(UUID userId, CreateSocialPaymentRequest request) {
        log.info("Sending social payment from {} to {} amount: {}", 
                userId, request.getRecipientId(), request.getAmount());

        try {
            // Validate payment request
            validateSocialPaymentRequest(request, userId);

            // Check social connection
            validateSocialConnection(userId, request.getRecipientId());

            // Create social payment record
            SocialPayment payment = createSocialPayment(userId, request);

            // Process core payment
            PaymentResult paymentResult = processCorePayment(payment);

            if (paymentResult.isSuccessful()) {
                // Update payment status
                payment.setStatus(SocialPayment.PaymentStatus.COMPLETED);
                payment.setTransactionId(paymentResult.getTransactionId());
                payment.setCompletedAt(LocalDateTime.now());

                // Create social activity
                createPaymentActivity(payment);

                // Send notifications
                sendPaymentNotifications(payment);

                // Check for payment milestones and achievements
                checkPaymentMilestones(userId, payment);

                // Update friendship payment statistics
                updateFriendshipPaymentStats(userId, request.getRecipientId(), request.getAmount());

            } else {
                payment.setStatus(SocialPayment.PaymentStatus.FAILED);
                payment.setFailureReason(paymentResult.getError());
            }

            payment = paymentRepository.save(payment);

            // Publish payment event
            publishSocialPaymentEvent("SOCIAL_PAYMENT_" + payment.getStatus().name(), payment);

            log.info("Social payment processed: {} status: {}", payment.getId(), payment.getStatus());

            return mapToSocialPaymentResponse(payment);

        } catch (Exception e) {
            log.error("Failed to send social payment from {} to {}", userId, request.getRecipientId(), e);
            throw new SocialPaymentException("Failed to send social payment: " + e.getMessage(), e);
        }
    }

    /**
     * Create and manage split bills with smart splitting
     */
    public SplitBillResponse createSplitBill(UUID userId, CreateSplitBillRequest request) {
        log.info("Creating split bill by {} for {} participants amount: {}", 
                userId, request.getParticipants().size(), request.getTotalAmount());

        try {
            // Validate split bill request
            validateSplitBillRequest(request, userId);

            // Calculate split amounts using smart algorithm
            Map<UUID, BigDecimal> splitAmounts = calculateSplitAmounts(request);

            // Create split bill record
            SplitBill splitBill = SplitBill.builder()
                    .createdBy(userId)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .totalAmount(request.getTotalAmount())
                    .currency(request.getCurrency())
                    .splitType(request.getSplitType())
                    .category(request.getCategory())
                    .merchant(request.getMerchant())
                    .location(request.getLocation())
                    .billDate(request.getBillDate())
                    .dueDate(request.getDueDate())
                    .receiptUrl(request.getReceiptUrl())
                    .status(SplitBillStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            splitBill = splitBillRepository.save(splitBill);

            // Create participant records
            List<SplitBillParticipant> participants = createSplitBillParticipants(
                    splitBill, splitAmounts, request.getParticipants());

            // Send split bill notifications
            sendSplitBillInvitations(splitBill, participants);

            // Create social activity
            createSplitBillActivity(splitBill, participants);

            // Publish split bill event
            publishSplitBillEvent("SPLIT_BILL_CREATED", splitBill);

            log.info("Split bill created: {} with {} participants", splitBill.getId(), participants.size());

            return mapToSplitBillResponse(splitBill, participants);

        } catch (Exception e) {
            log.error("Failed to create split bill for user: {}", userId, e);
            throw new SplitBillException("Failed to create split bill: " + e.getMessage(), e);
        }
    }

    /**
     * Process split bill payment
     */
    public SplitBillPaymentResponse paySplitBillPortion(UUID userId, UUID splitBillId, 
                                                      PaySplitBillRequest request) {
        log.info("Processing split bill payment by {} for bill: {}", userId, splitBillId);

        try {
            SplitBill splitBill = getSplitBill(splitBillId);
            SplitBillParticipant participant = getParticipant(splitBillId, userId);

            // Validate payment
            validateSplitBillPayment(participant, request);

            // Calculate payment amount (could be partial)
            BigDecimal paymentAmount = request.getAmount() != null ? 
                    request.getAmount() : participant.getAmountOwed();

            // Process payment to bill creator
            PaymentResult paymentResult = corePaymentService.processP2PPayment(
                    userId, splitBill.getCreatedBy(), paymentAmount, 
                    "Split bill payment: " + splitBill.getTitle());

            if (paymentResult.isSuccessful()) {
                // Update participant status
                participant.setAmountPaid(participant.getAmountPaid().add(paymentAmount));
                participant.setPaidAt(LocalDateTime.now());
                
                if (participant.getAmountPaid().compareTo(participant.getAmountOwed()) >= 0) {
                    participant.setStatus(ParticipantStatus.PAID);
                }

                splitBillRepository.save(splitBill);

                // Check if bill is fully paid
                checkSplitBillCompletion(splitBill);

                // Send payment confirmations
                sendSplitBillPaymentNotifications(splitBill, participant, paymentAmount);

                // Create payment activity
                createSplitBillPaymentActivity(splitBill, participant, paymentAmount);

                return SplitBillPaymentResponse.builder()
                        .splitBillId(splitBillId)
                        .participantId(participant.getId())
                        .amountPaid(paymentAmount)
                        .remainingAmount(participant.getAmountOwed().subtract(participant.getAmountPaid()))
                        .status(participant.getStatus())
                        .paidAt(LocalDateTime.now())
                        .build();

            } else {
                throw new PaymentProcessingException("Payment failed: " + paymentResult.getError());
            }

        } catch (Exception e) {
            log.error("Failed to process split bill payment: {}", splitBillId, e);
            throw new SplitBillException("Failed to process payment: " + e.getMessage(), e);
        }
    }

    /**
     * Create and manage payment requests
     */
    public PaymentRequestResponse createPaymentRequest(UUID userId, CreatePaymentRequestRequest request) {
        log.info("Creating payment request from {} to {} amount: {}", 
                userId, request.getRequestedFromId(), request.getAmount());

        try {
            // Validate request
            validatePaymentRequestCreation(request, userId);

            // Check if users are connected
            validateSocialConnection(userId, request.getRequestedFromId());

            // Create payment request
            PaymentRequest paymentRequest = PaymentRequest.builder()
                    .requesterId(userId)
                    .requestedFromId(request.getRequestedFromId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .category(request.getCategory())
                    .dueDate(request.getDueDate())
                    .priority(request.getPriority())
                    .status(PaymentRequestStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusDays(PAYMENT_REQUEST_EXPIRY_DAYS))
                    .createdAt(LocalDateTime.now())
                    .metadata(request.getMetadata())
                    .build();

            paymentRequest = paymentRequestRepository.save(paymentRequest);

            // Send payment request notification
            sendPaymentRequestNotification(paymentRequest);

            // Create social activity
            createPaymentRequestActivity(paymentRequest);

            // Schedule reminder notifications
            schedulePaymentRequestReminders(paymentRequest);

            // Publish event
            publishPaymentRequestEvent("PAYMENT_REQUEST_CREATED", paymentRequest);

            log.info("Payment request created: {}", paymentRequest.getId());

            return mapToPaymentRequestResponse(paymentRequest);

        } catch (Exception e) {
            log.error("Failed to create payment request", e);
            throw new PaymentRequestException("Failed to create payment request: " + e.getMessage(), e);
        }
    }

    /**
     * Create social payment challenge
     */
    public PaymentChallengeResponse createPaymentChallenge(UUID userId, CreatePaymentChallengeRequest request) {
        log.info("Creating payment challenge by {} type: {}", userId, request.getChallengeType());

        try {
            // Validate challenge request
            validateChallengeRequest(request, userId);

            // Create challenge
            PaymentChallenge challenge = PaymentChallenge.builder()
                    .createdBy(userId)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .challengeType(request.getChallengeType())
                    .targetAmount(request.getTargetAmount())
                    .targetParticipants(request.getTargetParticipants())
                    .category(request.getCategory())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .rules(request.getRules())
                    .rewards(request.getRewards())
                    .visibility(request.getVisibility())
                    .status(ChallengeStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .build();

            challenge = challengeRepository.save(challenge);

            // Create initial participation (creator)
            createChallengeParticipation(challenge, userId, true);

            // Invite specified participants
            if (request.getInvitedParticipants() != null && !request.getInvitedParticipants().isEmpty()) {
                sendChallengeInvitations(challenge, request.getInvitedParticipants());
            }

            // Create challenge activity
            createChallengeActivity(challenge);

            // Publish challenge event
            publishChallengeEvent("PAYMENT_CHALLENGE_CREATED", challenge);

            log.info("Payment challenge created: {}", challenge.getId());

            return mapToChallengeResponse(challenge);

        } catch (Exception e) {
            log.error("Failed to create payment challenge", e);
            throw new PaymentChallengeException("Failed to create challenge: " + e.getMessage(), e);
        }
    }

    /**
     * Create subscription-like payment stream
     */
    public PaymentStreamResponse createPaymentStream(UUID userId, CreatePaymentStreamRequest request) {
        log.info("Creating payment stream from {} to {} amount: {} frequency: {}", 
                userId, request.getRecipientId(), request.getAmount(), request.getFrequency());

        try {
            // Validate stream request
            validatePaymentStreamRequest(request, userId);

            // Check social connection
            validateSocialConnection(userId, request.getRecipientId());

            // Create payment stream
            PaymentStream stream = PaymentStream.builder()
                    .senderId(userId)
                    .recipientId(request.getRecipientId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .frequency(request.getFrequency())
                    .description(request.getDescription())
                    .category(request.getCategory())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .maxPayments(request.getMaxPayments())
                    .status(StreamStatus.ACTIVE)
                    .nextPaymentDate(calculateNextPaymentDate(request.getStartDate(), request.getFrequency()))
                    .createdAt(LocalDateTime.now())
                    .metadata(request.getMetadata())
                    .build();

            stream = paymentStreamRepository.save(stream);

            // Schedule first payment
            scheduleStreamPayment(stream);

            // Send stream notifications
            sendPaymentStreamNotifications(stream);

            // Create stream activity
            createPaymentStreamActivity(stream);

            // Publish stream event
            publishStreamEvent("PAYMENT_STREAM_CREATED", stream);

            log.info("Payment stream created: {}", stream.getId());

            return mapToStreamResponse(stream);

        } catch (Exception e) {
            log.error("Failed to create payment stream", e);
            throw new PaymentStreamException("Failed to create payment stream: " + e.getMessage(), e);
        }
    }

    /**
     * Get social payment analytics and insights
     */
    @Transactional(readOnly = true)
    public SocialPaymentAnalytics getSocialPaymentAnalytics(UUID userId, AnalyticsPeriod period) {
        log.info("Generating social payment analytics for user: {} period: {}", userId, period);

        try {
            LocalDateTime startDate = calculatePeriodStart(period);
            LocalDateTime endDate = LocalDateTime.now();

            // Get payment data
            List<SocialPayment> payments = paymentRepository
                    .findByUserIdAndDateRange(userId, startDate, endDate);

            // Calculate metrics
            PaymentMetrics metrics = calculatePaymentMetrics(payments);

            // Social network analysis
            SocialNetworkInsights networkInsights = analyzeSocialNetwork(userId, payments);

            // Spending patterns by friend
            Map<UUID, FriendSpendingPattern> friendPatterns = analyzeFriendSpendingPatterns(payments);

            // Payment frequency analysis
            PaymentFrequencyAnalysis frequency = analyzePaymentFrequency(payments);

            // Category insights
            Map<String, CategorySpendingInsights> categoryInsights = analyzeCategorySpending(payments);

            // Social influence score
            SocialInfluenceScore influenceScore = calculateSocialInfluenceScore(userId, payments);

            // Payment habits comparison
            PaymentHabitsComparison comparison = compareWithPeerGroup(userId, metrics);

            return SocialPaymentAnalytics.builder()
                    .userId(userId)
                    .period(period)
                    .metrics(metrics)
                    .networkInsights(networkInsights)
                    .friendPatterns(friendPatterns)
                    .frequencyAnalysis(frequency)
                    .categoryInsights(categoryInsights)
                    .influenceScore(influenceScore)
                    .peerComparison(comparison)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate social payment analytics", e);
            throw new AnalyticsException("Failed to generate analytics", e);
        }
    }

    /**
     * Get payment recommendations based on social behavior
     */
    @Async
    public CompletableFuture<PaymentRecommendations> getPaymentRecommendations(UUID userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating payment recommendations for user: {}", userId);

                // Analyze recent payment patterns
                PaymentPatternAnalysis patterns = analyzeRecentPaymentPatterns(userId);

                // Get friend activity insights
                FriendActivityInsights friendActivity = analyzeFriendActivity(userId);

                // Generate recommendations
                List<PaymentRecommendation> recommendations = new ArrayList<>();

                // Smart bill splitting suggestions
                recommendations.addAll(generateBillSplittingRecommendations(userId, patterns));

                // Payment request suggestions
                recommendations.addAll(generatePaymentRequestSuggestions(userId, patterns));

                // Social challenge recommendations
                recommendations.addAll(generateChallengeRecommendations(userId, friendActivity));

                // Payment stream opportunities
                recommendations.addAll(generateStreamRecommendations(userId, patterns));

                // Social spending optimizations
                recommendations.addAll(generateSpendingOptimizations(userId, patterns));

                // Sort by relevance score
                recommendations.sort((a, b) -> b.getRelevanceScore().compareTo(a.getRelevanceScore()));

                return PaymentRecommendations.builder()
                        .userId(userId)
                        .recommendations(recommendations.stream().limit(10).collect(Collectors.toList()))
                        .patterns(patterns)
                        .friendActivity(friendActivity)
                        .generatedAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.error("Failed to generate payment recommendations for user: {}", userId, e);
                throw new RecommendationException("Failed to generate recommendations", e);
            }
        });
    }

    // Helper methods for payment processing

    private SocialPayment createSocialPayment(UUID userId, CreateSocialPaymentRequest request) {
        return SocialPayment.builder()
                .senderId(userId)
                .recipientId(request.getRecipientId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .message(request.getMessage())
                .emoji(request.getEmoji())
                .category(request.getCategory())
                .paymentType(request.getPaymentType())
                .visibility(request.getVisibility())
                .location(request.getLocation())
                .mediaAttachments(request.getMediaAttachments())
                .tags(request.getTags())
                .status(SocialPayment.PaymentStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .metadata(request.getMetadata())
                .build();
    }

    private PaymentResult processCorePayment(SocialPayment payment) {
        return corePaymentService.processP2PPayment(
                payment.getSenderId(),
                payment.getRecipientId(),
                payment.getAmount(),
                payment.getMessage()
        );
    }

    private Map<UUID, BigDecimal> calculateSplitAmounts(CreateSplitBillRequest request) {
        Map<UUID, BigDecimal> splits = new HashMap<>();

        switch (request.getSplitType()) {
            case EQUAL:
                BigDecimal equalAmount = request.getTotalAmount()
                        .divide(BigDecimal.valueOf(request.getParticipants().size()), 2, RoundingMode.HALF_UP);
                for (SplitBillParticipantRequest participant : request.getParticipants()) {
                    splits.put(participant.getUserId(), equalAmount);
                }
                break;

            case PERCENTAGE:
                for (SplitBillParticipantRequest participant : request.getParticipants()) {
                    BigDecimal amount = request.getTotalAmount()
                            .multiply(participant.getPercentage())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    splits.put(participant.getUserId(), amount);
                }
                break;

            case CUSTOM:
                for (SplitBillParticipantRequest participant : request.getParticipants()) {
                    splits.put(participant.getUserId(), participant.getCustomAmount());
                }
                break;
        }

        return splits;
    }

    private void validateSocialPaymentRequest(CreateSocialPaymentRequest request, UUID senderId) {
        if (request.getAmount().compareTo(MIN_PAYMENT_AMOUNT) < 0) {
            throw new InvalidPaymentAmountException("Payment amount too small");
        }
        
        if (request.getAmount().compareTo(MAX_SOCIAL_PAYMENT_AMOUNT) > 0) {
            throw new InvalidPaymentAmountException("Payment amount exceeds limit");
        }

        if (senderId.equals(request.getRecipientId())) {
            throw new InvalidPaymentRequestException("Cannot send payment to yourself");
        }
    }

    private void validateSocialConnection(UUID userId1, UUID userId2) {
        if (!connectionRepository.existsAcceptedConnection(userId1, userId2)) {
            throw new NotConnectedException("Users are not connected");
        }
    }

    // Event publishing methods
    private void publishSocialPaymentEvent(String eventType, SocialPayment payment) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "paymentId", payment.getId(),
                "senderId", payment.getSenderId(),
                "recipientId", payment.getRecipientId(),
                "amount", payment.getAmount(),
                "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("social-payment-events", payment.getId().toString(), event);
    }

    private void publishSplitBillEvent(String eventType, SplitBill splitBill) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "splitBillId", splitBill.getId(),
                "createdBy", splitBill.getCreatedBy(),
                "totalAmount", splitBill.getTotalAmount(),
                "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("split-bill-events", splitBill.getId().toString(), event);
    }

    // Response mapping methods
    private SocialPaymentResponse mapToSocialPaymentResponse(SocialPayment payment) {
        return SocialPaymentResponse.builder()
                .paymentId(payment.getId())
                .senderId(payment.getSenderId())
                .recipientId(payment.getRecipientId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .message(payment.getMessage())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }

    // Placeholder implementations for complex operations
    private void createPaymentActivity(SocialPayment payment) {
        try {
            SocialActivity activity = SocialActivity.builder()
                .userId(payment.getSenderId())
                .activityType(ActivityType.PAYMENT_SENT)
                .targetUserId(payment.getRecipientId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .description(payment.getDescription())
                .paymentId(payment.getId())
                .timestamp(Instant.now())
                .visibility(payment.isPublic() ? ActivityVisibility.FRIENDS : ActivityVisibility.PRIVATE)
                .build();
            
            socialActivityRepository.save(activity);
            
            // Create recipient activity
            SocialActivity recipientActivity = SocialActivity.builder()
                .userId(payment.getRecipientId())
                .activityType(ActivityType.PAYMENT_RECEIVED)
                .targetUserId(payment.getSenderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .description(payment.getDescription())
                .paymentId(payment.getId())
                .timestamp(Instant.now())
                .visibility(payment.isPublic() ? ActivityVisibility.FRIENDS : ActivityVisibility.PRIVATE)
                .build();
            
            socialActivityRepository.save(recipientActivity);
            
            log.info("Created social activities for payment {} between users {} and {}", 
                payment.getId(), payment.getSenderId(), payment.getRecipientId());
                
        } catch (Exception e) {
            log.error("Failed to create payment activity for payment {}", payment.getId(), e);
        }
    }
    private void sendPaymentNotifications(SocialPayment payment) {
        try {
            // Send notification to recipient
            NotificationRequest recipientNotification = NotificationRequest.builder()
                .userId(payment.getRecipientId())
                .type(NotificationType.PAYMENT_RECEIVED)
                .title("Payment Received")
                .message(String.format("You received $%.2f from %s", 
                    payment.getAmount(), getUserDisplayName(payment.getSenderId())))
                .data(Map.of(
                    "paymentId", payment.getId().toString(),
                    "senderId", payment.getSenderId().toString(),
                    "amount", payment.getAmount().toString(),
                    "currency", payment.getCurrency()
                ))
                .channels(List.of(NotificationChannel.PUSH, NotificationChannel.IN_APP))
                .priority(NotificationPriority.HIGH)
                .build();
            
            notificationService.sendNotification(recipientNotification);
            
            // Send confirmation to sender
            NotificationRequest senderNotification = NotificationRequest.builder()
                .userId(payment.getSenderId())
                .type(NotificationType.PAYMENT_SENT)
                .title("Payment Sent")
                .message(String.format("You sent $%.2f to %s", 
                    payment.getAmount(), getUserDisplayName(payment.getRecipientId())))
                .data(Map.of(
                    "paymentId", payment.getId().toString(),
                    "recipientId", payment.getRecipientId().toString(),
                    "amount", payment.getAmount().toString(),
                    "currency", payment.getCurrency()
                ))
                .channels(List.of(NotificationChannel.PUSH, NotificationChannel.IN_APP))
                .priority(NotificationPriority.MEDIUM)
                .build();
            
            notificationService.sendNotification(senderNotification);
            
            // If payment has social context (emoji, public), notify friends
            if (payment.isPublic() && (payment.getEmoji() != null || payment.getHashtags() != null)) {
                notifyFriendsOfPayment(payment);
            }
            
            log.info("Sent payment notifications for payment {} between users {} and {}", 
                payment.getId(), payment.getSenderId(), payment.getRecipientId());
                
        } catch (Exception e) {
            log.error("Failed to send payment notifications for payment {}", payment.getId(), e);
        }
    }
    private void checkPaymentMilestones(UUID userId, SocialPayment payment) {
        try {
            // Check user's payment milestones
            UserPaymentStats stats = getUserPaymentStats(userId);
            
            // Check for milestone achievements
            List<PaymentMilestone> achievedMilestones = new ArrayList<>();
            
            // First payment milestone
            if (stats.getTotalPaymentsSent() == 1) {
                achievedMilestones.add(PaymentMilestone.FIRST_PAYMENT);
            }
            
            // Volume milestones
            if (stats.getTotalAmountSent().compareTo(new BigDecimal("1000")) >= 0 && 
                stats.getTotalAmountSent().subtract(payment.getAmount()).compareTo(new BigDecimal("1000")) < 0) {
                achievedMilestones.add(PaymentMilestone.THOUSAND_DOLLARS_SENT);
            }
            
            if (stats.getTotalAmountSent().compareTo(new BigDecimal("10000")) >= 0 && 
                stats.getTotalAmountSent().subtract(payment.getAmount()).compareTo(new BigDecimal("10000")) < 0) {
                achievedMilestones.add(PaymentMilestone.TEN_THOUSAND_DOLLARS_SENT);
            }
            
            // Frequency milestones
            if (stats.getTotalPaymentsSent() == 10) {
                achievedMilestones.add(PaymentMilestone.TEN_PAYMENTS_SENT);
            }
            
            if (stats.getTotalPaymentsSent() == 100) {
                achievedMilestones.add(PaymentMilestone.HUNDRED_PAYMENTS_SENT);
            }
            
            // Social milestones
            if (stats.getUniqueFriendsPaid() == 5) {
                achievedMilestones.add(PaymentMilestone.FIVE_FRIENDS_PAID);
            }
            
            if (stats.getUniqueFriendsPaid() == 20) {
                achievedMilestones.add(PaymentMilestone.TWENTY_FRIENDS_PAID);
            }
            
            // Streak milestones
            if (stats.getCurrentStreak() == 7) {
                achievedMilestones.add(PaymentMilestone.SEVEN_DAY_STREAK);
            }
            
            if (stats.getCurrentStreak() == 30) {
                achievedMilestones.add(PaymentMilestone.THIRTY_DAY_STREAK);
            }
            
            // Process achieved milestones
            for (PaymentMilestone milestone : achievedMilestones) {
                processMilestoneAchievement(userId, milestone, payment);
            }
            
            log.info("Checked payment milestones for user {}, achieved: {}", userId, achievedMilestones);
            
        } catch (Exception e) {
            log.error("Failed to check payment milestones for user {} and payment {}", userId, payment.getId(), e);
        }
    }
    private void updateFriendshipPaymentStats(UUID userId, UUID friendId, BigDecimal amount) {
        try {
            // Get or create friendship payment stats
            Optional<FriendshipPaymentStats> existingStats = 
                friendshipStatsRepository.findByUserIdAndFriendId(userId, friendId);
            
            FriendshipPaymentStats stats;
            if (existingStats.isPresent()) {
                stats = existingStats.get();
            } else {
                stats = FriendshipPaymentStats.builder()
                    .userId(userId)
                    .friendId(friendId)
                    .totalPaymentsSent(0)
                    .totalAmountSent(BigDecimal.ZERO)
                    .totalPaymentsReceived(0)
                    .totalAmountReceived(BigDecimal.ZERO)
                    .firstPaymentDate(LocalDate.now())
                    .lastPaymentDate(LocalDate.now())
                    .build();
            }
            
            // Update stats
            stats.setTotalPaymentsSent(stats.getTotalPaymentsSent() + 1);
            stats.setTotalAmountSent(stats.getTotalAmountSent().add(amount));
            stats.setLastPaymentDate(LocalDate.now());
            
            // Calculate payment frequency
            if (stats.getFirstPaymentDate() != null) {
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                    stats.getFirstPaymentDate(), LocalDate.now());
                if (daysBetween > 0) {
                    stats.setPaymentFrequency(
                        BigDecimal.valueOf(stats.getTotalPaymentsSent())
                            .divide(BigDecimal.valueOf(daysBetween), 4, RoundingMode.HALF_UP));
                }
            }
            
            friendshipStatsRepository.save(stats);
            
            // Update reverse stats (friend receiving from user)
            Optional<FriendshipPaymentStats> reverseStats = 
                friendshipStatsRepository.findByUserIdAndFriendId(friendId, userId);
            
            FriendshipPaymentStats friendStats;
            if (reverseStats.isPresent()) {
                friendStats = reverseStats.get();
            } else {
                friendStats = FriendshipPaymentStats.builder()
                    .userId(friendId)
                    .friendId(userId)
                    .totalPaymentsSent(0)
                    .totalAmountSent(BigDecimal.ZERO)
                    .totalPaymentsReceived(0)
                    .totalAmountReceived(BigDecimal.ZERO)
                    .firstPaymentDate(LocalDate.now())
                    .lastPaymentDate(LocalDate.now())
                    .build();
            }
            
            friendStats.setTotalPaymentsReceived(friendStats.getTotalPaymentsReceived() + 1);
            friendStats.setTotalAmountReceived(friendStats.getTotalAmountReceived().add(amount));
            friendStats.setLastPaymentDate(LocalDate.now());
            
            friendshipStatsRepository.save(friendStats);
            
            log.info("Updated friendship payment stats between users {} and {}, amount: {}", 
                userId, friendId, amount);
                
        } catch (Exception e) {
            log.error("Failed to update friendship payment stats for users {} and {}", userId, friendId, e);
        }
    }
    private void validateSplitBillRequest(CreateSplitBillRequest request, UUID userId) {
        // Validate request parameters
        if (request == null) {
            throw new IllegalArgumentException("Split bill request cannot be null");
        }
        
        // Validate user ID
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        // Validate total amount
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero");
        }
        
        // Validate maximum amount limits
        BigDecimal maxSplitAmount = new BigDecimal("10000.00");
        if (request.getTotalAmount().compareTo(maxSplitAmount) > 0) {
            throw new IllegalArgumentException("Split bill amount exceeds maximum limit of " + maxSplitAmount);
        }
        
        // Validate participants
        if (request.getParticipantIds() == null || request.getParticipantIds().isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }
        
        // Validate maximum participants
        int maxParticipants = 20;
        if (request.getParticipantIds().size() > maxParticipants) {
            throw new IllegalArgumentException("Maximum " + maxParticipants + " participants allowed");
        }
        
        // Validate split type and percentages
        if (request.getSplitType() == SplitType.PERCENTAGE) {
            if (request.getPercentages() == null || request.getPercentages().size() != request.getParticipantIds().size()) {
                throw new IllegalArgumentException("Percentages must be provided for all participants in percentage split");
            }
            
            // Validate percentages sum to 100
            BigDecimal totalPercentage = request.getPercentages().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalPercentage.compareTo(new BigDecimal("100")) != 0) {
                throw new IllegalArgumentException("Percentages must sum to 100%");
            }
        }
        
        // Validate custom amounts
        if (request.getSplitType() == SplitType.CUSTOM) {
            if (request.getCustomAmounts() == null || request.getCustomAmounts().size() != request.getParticipantIds().size()) {
                throw new IllegalArgumentException("Custom amounts must be provided for all participants in custom split");
            }
            
            // Validate custom amounts sum to total
            BigDecimal totalCustom = request.getCustomAmounts().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalCustom.compareTo(request.getTotalAmount()) != 0) {
                throw new IllegalArgumentException("Custom amounts must sum to total amount");
            }
        }
        
        // Validate description
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description cannot exceed 500 characters");
        }
        
        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency must be specified");
        }
        
        log.debug("Split bill request validated successfully for user: {}", userId);
    }
    private void validatePaymentRequestCreation(CreatePaymentRequestRequest request, UUID userId) {
        // Validate request object
        if (request == null) {
            throw new IllegalArgumentException("Payment request cannot be null");
        }
        
        // Validate user ID
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        // Validate recipient
        if (request.getRecipientId() == null) {
            throw new IllegalArgumentException("Recipient ID cannot be null");
        }
        
        // Prevent self-requests
        if (userId.equals(request.getRecipientId())) {
            throw new IllegalArgumentException("Cannot request payment from yourself");
        }
        
        // Validate amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        
        // Validate minimum amount
        BigDecimal minAmount = new BigDecimal("1.00");
        if (request.getAmount().compareTo(minAmount) < 0) {
            throw new IllegalArgumentException("Minimum payment request amount is " + minAmount);
        }
        
        // Validate maximum amount
        BigDecimal maxAmount = new BigDecimal("5000.00");
        if (request.getAmount().compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("Maximum payment request amount is " + maxAmount);
        }
        
        // Validate expiry time
        if (request.getExpiresAt() != null) {
            LocalDateTime minExpiry = LocalDateTime.now().plusHours(1);
            LocalDateTime maxExpiry = LocalDateTime.now().plusDays(30);
            
            if (request.getExpiresAt().isBefore(minExpiry)) {
                throw new IllegalArgumentException("Payment request must be valid for at least 1 hour");
            }
            
            if (request.getExpiresAt().isAfter(maxExpiry)) {
                throw new IllegalArgumentException("Payment request cannot be valid for more than 30 days");
            }
        }
        
        // Validate reason/description
        if (request.getReason() != null && request.getReason().length() > 250) {
            throw new IllegalArgumentException("Reason cannot exceed 250 characters");
        }
        
        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency must be specified");
        }
        
        // Validate attachments if present
        if (request.getAttachments() != null && request.getAttachments().size() > 5) {
            throw new IllegalArgumentException("Maximum 5 attachments allowed");
        }
        
        log.debug("Payment request validated successfully from user: {} to recipient: {}", 
                 userId, request.getRecipientId());
    }
    private void validateChallengeRequest(CreatePaymentChallengeRequest request, UUID userId) {
        // Validate request object
        if (request == null) {
            throw new IllegalArgumentException("Challenge request cannot be null");
        }
        
        // Validate user ID
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        // Validate challenge name
        if (request.getChallengeName() == null || request.getChallengeName().trim().isEmpty()) {
            throw new IllegalArgumentException("Challenge name is required");
        }
        
        if (request.getChallengeName().length() > 100) {
            throw new IllegalArgumentException("Challenge name cannot exceed 100 characters");
        }
        
        // Validate target amount
        if (request.getTargetAmount() == null || request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target amount must be greater than zero");
        }
        
        // Validate challenge limits
        BigDecimal minChallenge = new BigDecimal("10.00");
        BigDecimal maxChallenge = new BigDecimal("50000.00");
        
        if (request.getTargetAmount().compareTo(minChallenge) < 0) {
            throw new IllegalArgumentException("Minimum challenge amount is " + minChallenge);
        }
        
        if (request.getTargetAmount().compareTo(maxChallenge) > 0) {
            throw new IllegalArgumentException("Maximum challenge amount is " + maxChallenge);
        }
        
        // Validate dates
        if (request.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        
        if (request.getEndDate() == null) {
            throw new IllegalArgumentException("End date is required");
        }
        
        // Validate date logic
        if (request.getStartDate().isBefore(LocalDateTime.now().minusHours(1))) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }
        
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        
        // Validate challenge duration
        long daysBetween = java.time.Duration.between(request.getStartDate(), request.getEndDate()).toDays();
        if (daysBetween < 1) {
            throw new IllegalArgumentException("Challenge must last at least 1 day");
        }
        
        if (daysBetween > 365) {
            throw new IllegalArgumentException("Challenge cannot last more than 365 days");
        }
        
        // Validate participants
        if (request.getParticipantIds() != null) {
            if (request.getParticipantIds().isEmpty()) {
                throw new IllegalArgumentException("At least one participant is required if participants are specified");
            }
            
            if (request.getParticipantIds().size() > 100) {
                throw new IllegalArgumentException("Maximum 100 participants allowed per challenge");
            }
            
            // Check for duplicate participants
            long uniqueCount = request.getParticipantIds().stream().distinct().count();
            if (uniqueCount != request.getParticipantIds().size()) {
                throw new IllegalArgumentException("Duplicate participants not allowed");
            }
        }
        
        // Validate rules
        if (request.getRules() != null && request.getRules().length() > 1000) {
            throw new IllegalArgumentException("Rules cannot exceed 1000 characters");
        }
        
        // Validate prize pool if specified
        if (request.getPrizePool() != null) {
            if (request.getPrizePool().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Prize pool cannot be negative");
            }
            
            if (request.getPrizePool().compareTo(new BigDecimal("10000.00")) > 0) {
                throw new IllegalArgumentException("Prize pool cannot exceed 10000.00");
            }
        }
        
        log.debug("Payment challenge validated successfully for user: {}", userId);
    }
    private void validatePaymentStreamRequest(CreatePaymentStreamRequest request, UUID userId) {
        // Validate request object
        if (request == null) {
            throw new IllegalArgumentException("Payment stream request cannot be null");
        }
        
        // Validate user ID
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        // Validate recipient
        if (request.getRecipientId() == null) {
            throw new IllegalArgumentException("Recipient ID cannot be null");
        }
        
        // Prevent self-streaming
        if (userId.equals(request.getRecipientId())) {
            throw new IllegalArgumentException("Cannot create payment stream to yourself");
        }
        
        // Validate stream name
        if (request.getStreamName() == null || request.getStreamName().trim().isEmpty()) {
            throw new IllegalArgumentException("Stream name is required");
        }
        
        if (request.getStreamName().length() > 100) {
            throw new IllegalArgumentException("Stream name cannot exceed 100 characters");
        }
        
        // Validate amount per interval
        if (request.getAmountPerInterval() == null || request.getAmountPerInterval().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount per interval must be greater than zero");
        }
        
        // Validate stream amount limits
        BigDecimal minStreamAmount = new BigDecimal("5.00");
        BigDecimal maxStreamAmount = new BigDecimal("1000.00");
        
        if (request.getAmountPerInterval().compareTo(minStreamAmount) < 0) {
            throw new IllegalArgumentException("Minimum stream amount per interval is " + minStreamAmount);
        }
        
        if (request.getAmountPerInterval().compareTo(maxStreamAmount) > 0) {
            throw new IllegalArgumentException("Maximum stream amount per interval is " + maxStreamAmount);
        }
        
        // Validate interval
        if (request.getInterval() == null) {
            throw new IllegalArgumentException("Payment interval is required");
        }
        
        // Validate supported intervals
        Set<String> supportedIntervals = Set.of("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY");
        if (!supportedIntervals.contains(request.getInterval().toUpperCase())) {
            throw new IllegalArgumentException("Unsupported interval. Supported: " + supportedIntervals);
        }
        
        // Validate dates
        if (request.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        
        if (request.getStartDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }
        
        // Validate end date if specified
        if (request.getEndDate() != null) {
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new IllegalArgumentException("End date must be after start date");
            }
            
            // Validate stream duration
            long daysBetween = java.time.Duration.between(request.getStartDate(), request.getEndDate()).toDays();
            if (daysBetween > 730) { // 2 years
                throw new IllegalArgumentException("Payment stream cannot exceed 2 years");
            }
        }
        
        // Validate total amount if specified
        if (request.getTotalAmount() != null) {
            if (request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Total amount must be greater than zero");
            }
            
            BigDecimal maxTotalAmount = new BigDecimal("25000.00");
            if (request.getTotalAmount().compareTo(maxTotalAmount) > 0) {
                throw new IllegalArgumentException("Total stream amount cannot exceed " + maxTotalAmount);
            }
        }
        
        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency must be specified");
        }
        
        // Validate description
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description cannot exceed 500 characters");
        }
        
        // Validate auto-renewal settings
        if (request.isAutoRenew() && request.getEndDate() == null) {
            throw new IllegalArgumentException("Auto-renewal requires an end date to be specified");
        }
        
        log.debug("Payment stream validated successfully from user: {} to recipient: {}", 
                 userId, request.getRecipientId());
    }

    // Enum definitions
    public enum SplitType { EQUAL, PERCENTAGE, CUSTOM }
    public enum SplitBillStatus { PENDING, ACTIVE, COMPLETED, CANCELLED }
    public enum ParticipantStatus { PENDING, ACCEPTED, PAID, DECLINED }
    public enum PaymentRequestStatus { PENDING, ACCEPTED, DECLINED, EXPIRED, COMPLETED }
    public enum ChallengeStatus { DRAFT, ACTIVE, COMPLETED, CANCELLED }
    public enum StreamStatus { ACTIVE, PAUSED, COMPLETED, CANCELLED }
    public enum AnalyticsPeriod { WEEK, MONTH, QUARTER, YEAR }
}