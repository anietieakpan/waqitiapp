package com.waqiti.payment.social;

import com.waqiti.payment.domain.SocialPayment;
import com.waqiti.payment.domain.PaymentFeed;
import com.waqiti.payment.domain.PaymentComment;
import com.waqiti.payment.domain.PaymentReaction;
import com.waqiti.payment.repository.SocialPaymentRepository;
import com.waqiti.payment.repository.PaymentFeedRepository;
import com.waqiti.payment.repository.PaymentCommentRepository;
import com.waqiti.payment.repository.PaymentReactionRepository;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.dto.SocialPaymentRequest;
import com.waqiti.payment.dto.SocialPaymentResponse;
import com.waqiti.payment.dto.PaymentFeedResponse;
import com.waqiti.payment.exception.SocialPaymentException;
import com.waqiti.common.domain.Money;
import com.waqiti.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for social payment features including payment feeds, comments,
 * reactions, and social interactions around payments (Venmo-style).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialPaymentService {

    private final SocialPaymentRepository socialPaymentRepository;
    private final PaymentFeedRepository paymentFeedRepository;
    private final PaymentCommentRepository paymentCommentRepository;
    private final PaymentReactionRepository paymentReactionRepository;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final EventPublisher eventPublisher;
    private final SocialPaymentValidationService validationService;
    private final ContentModerationService contentModerationService;

    /**
     * Creates a social payment with visibility settings
     */
    @Transactional
    public SocialPaymentResponse createSocialPayment(SocialPaymentRequest request) {
        log.info("Creating social payment: from={}, to={}, amount={}, visibility={}", 
                request.getSenderId(), request.getRecipientId(), request.getAmount(), request.getVisibility());
        
        try {
            // Validate request
            validationService.validateSocialPaymentRequest(request);
            
            // Moderate content
            var moderationResult = contentModerationService.moderateContent(
                    request.getDescription(), request.getEmoji());
            
            if (moderationResult.isBlocked()) {
                throw new SocialPaymentException("Payment description contains inappropriate content");
            }
            
            // Create social payment
            SocialPayment socialPayment = SocialPayment.builder()
                    .id(UUID.randomUUID())
                    .senderId(request.getSenderId())
                    .recipientId(request.getRecipientId())
                    .amount(new Money(request.getAmount(), request.getCurrency()))
                    .description(moderationResult.getCleanedContent())
                    .emoji(request.getEmoji())
                    .visibility(request.getVisibility())
                    .location(request.getLocation())
                    .tags(request.getTags())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            socialPayment = socialPaymentRepository.save(socialPayment);
            
            // Create feed entry if visible
            if (shouldCreateFeedEntry(socialPayment)) {
                createFeedEntry(socialPayment);
            }
            
            // Send notifications based on visibility
            sendSocialPaymentNotifications(socialPayment);
            
            // Publish events
            eventPublisher.publishSocialPaymentCreatedEvent(socialPayment);
            
            log.info("Social payment created successfully: paymentId={}", socialPayment.getId());
            return buildSocialPaymentResponse(socialPayment);
            
        } catch (Exception e) {
            log.error("Failed to create social payment: error={}", e.getMessage(), e);
            throw new SocialPaymentException("Failed to create social payment", e);
        }
    }

    /**
     * Gets payment feed for a user based on their social connections
     */
    public Page<PaymentFeedResponse> getPaymentFeed(UUID userId, Pageable pageable) {
        log.debug("Getting payment feed for user: {}", userId);
        
        try {
            // Get user's social connections
            Set<UUID> connections = userServiceClient.getUserConnections(userId);
            connections.add(userId); // Include user's own payments
            
            // Get feed entries
            Page<PaymentFeed> feedPage = paymentFeedRepository.findVisibleFeedForUser(
                    connections, userId, pageable);
            
            return feedPage.map(this::buildPaymentFeedResponse);
            
        } catch (Exception e) {
            log.error("Failed to get payment feed for user: userId={}", userId, e);
            throw new SocialPaymentException("Failed to get payment feed", e);
        }
    }

    /**
     * Gets public payment feed (trending/popular payments)
     */
    public Page<PaymentFeedResponse> getPublicFeed(UUID userId, Pageable pageable) {
        log.debug("Getting public payment feed");
        
        try {
            Page<PaymentFeed> feedPage = paymentFeedRepository.findPublicFeed(pageable);
            return feedPage.map(this::buildPaymentFeedResponse);
            
        } catch (Exception e) {
            log.error("Failed to get public payment feed", e);
            throw new SocialPaymentException("Failed to get public payment feed", e);
        }
    }

    /**
     * Adds a comment to a payment
     */
    @Transactional
    public PaymentComment addComment(UUID paymentId, UUID userId, String comment) {
        log.debug("Adding comment to payment: paymentId={}, userId={}", paymentId, userId);
        
        try {
            // Validate payment exists and is visible to user
            SocialPayment payment = validatePaymentAccess(paymentId, userId);
            
            // Moderate comment content
            var moderationResult = contentModerationService.moderateContent(comment, null);
            if (moderationResult.isBlocked()) {
                throw new SocialPaymentException("Comment contains inappropriate content");
            }
            
            // Create comment
            PaymentComment paymentComment = PaymentComment.builder()
                    .id(UUID.randomUUID())
                    .paymentId(paymentId)
                    .userId(userId)
                    .comment(moderationResult.getCleanedContent())
                    .createdAt(LocalDateTime.now())
                    .build();
            
            paymentComment = paymentCommentRepository.save(paymentComment);
            
            // Update feed entry
            updateFeedEngagement(paymentId);
            
            // Notify payment participants
            notifyCommentAdded(payment, paymentComment, userId);
            
            // Publish event
            eventPublisher.publishPaymentCommentAddedEvent(paymentComment);
            
            log.debug("Comment added successfully: commentId={}", paymentComment.getId());
            return paymentComment;
            
        } catch (Exception e) {
            log.error("Failed to add comment: paymentId={}, userId={}", paymentId, userId, e);
            throw new SocialPaymentException("Failed to add comment", e);
        }
    }

    /**
     * Adds a reaction (like, love, laugh, etc.) to a payment
     */
    @Transactional
    public PaymentReaction addReaction(UUID paymentId, UUID userId, PaymentReaction.ReactionType reactionType) {
        log.debug("Adding reaction to payment: paymentId={}, userId={}, reaction={}", 
                paymentId, userId, reactionType);
        
        try {
            // Validate payment exists and is visible to user
            SocialPayment payment = validatePaymentAccess(paymentId, userId);
            
            // Check if user already reacted
            Optional<PaymentReaction> existingReaction = paymentReactionRepository
                    .findByPaymentIdAndUserId(paymentId, userId);
            
            PaymentReaction reaction;
            if (existingReaction.isPresent()) {
                // Update existing reaction
                reaction = existingReaction.get();
                reaction.setReactionType(reactionType);
                reaction.setUpdatedAt(LocalDateTime.now());
            } else {
                // Create new reaction
                reaction = PaymentReaction.builder()
                        .id(UUID.randomUUID())
                        .paymentId(paymentId)
                        .userId(userId)
                        .reactionType(reactionType)
                        .createdAt(LocalDateTime.now())
                        .build();
            }
            
            reaction = paymentReactionRepository.save(reaction);
            
            // Update feed entry
            updateFeedEngagement(paymentId);
            
            // Notify payment participants (but not for every reaction to avoid spam)
            if (shouldNotifyForReaction(payment, userId, reactionType)) {
                notifyReactionAdded(payment, reaction, userId);
            }
            
            // Publish event
            eventPublisher.publishPaymentReactionAddedEvent(reaction);
            
            log.debug("Reaction added successfully: reactionId={}", reaction.getId());
            return reaction;
            
        } catch (Exception e) {
            log.error("Failed to add reaction: paymentId={}, userId={}, reaction={}", 
                    paymentId, userId, reactionType, e);
            throw new SocialPaymentException("Failed to add reaction", e);
        }
    }

    /**
     * Removes a reaction from a payment
     */
    @Transactional
    public void removeReaction(UUID paymentId, UUID userId) {
        log.debug("Removing reaction from payment: paymentId={}, userId={}", paymentId, userId);
        
        try {
            PaymentReaction reaction = paymentReactionRepository.findByPaymentIdAndUserId(paymentId, userId)
                    .orElseThrow(() -> new SocialPaymentException("Reaction not found"));
            
            paymentReactionRepository.delete(reaction);
            
            // Update feed entry
            updateFeedEngagement(paymentId);
            
            // Publish event
            eventPublisher.publishPaymentReactionRemovedEvent(reaction);
            
            log.debug("Reaction removed successfully: paymentId={}, userId={}", paymentId, userId);
            
        } catch (Exception e) {
            log.error("Failed to remove reaction: paymentId={}, userId={}", paymentId, userId, e);
            throw new SocialPaymentException("Failed to remove reaction", e);
        }
    }

    /**
     * Gets comments for a payment
     */
    public List<PaymentComment> getPaymentComments(UUID paymentId, UUID userId) {
        log.debug("Getting comments for payment: paymentId={}, userId={}", paymentId, userId);
        
        try {
            // Validate payment access
            validatePaymentAccess(paymentId, userId);
            
            return paymentCommentRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
            
        } catch (Exception e) {
            log.error("Failed to get payment comments: paymentId={}, userId={}", paymentId, userId, e);
            throw new SocialPaymentException("Failed to get payment comments", e);
        }
    }

    /**
     * Gets reactions for a payment
     */
    public Map<PaymentReaction.ReactionType, Long> getPaymentReactionCounts(UUID paymentId, UUID userId) {
        log.debug("Getting reactions for payment: paymentId={}, userId={}", paymentId, userId);
        
        try {
            // Validate payment access
            validatePaymentAccess(paymentId, userId);
            
            List<PaymentReaction> reactions = paymentReactionRepository.findByPaymentId(paymentId);
            
            return reactions.stream()
                    .collect(Collectors.groupingBy(
                            PaymentReaction::getReactionType,
                            Collectors.counting()
                    ));
            
        } catch (Exception e) {
            log.error("Failed to get payment reactions: paymentId={}, userId={}", paymentId, userId, e);
            throw new SocialPaymentException("Failed to get payment reactions", e);
        }
    }

    /**
     * Updates payment visibility settings
     */
    @Transactional
    public void updatePaymentVisibility(UUID paymentId, UUID userId, SocialPayment.Visibility visibility) {
        log.debug("Updating payment visibility: paymentId={}, userId={}, visibility={}", 
                paymentId, userId, visibility);
        
        try {
            SocialPayment payment = socialPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new SocialPaymentException("Payment not found"));
            
            // Verify user owns the payment
            if (!payment.getSenderId().equals(userId)) {
                throw new SocialPaymentException("Not authorized to update payment visibility");
            }
            
            payment.setVisibility(visibility);
            payment.setUpdatedAt(LocalDateTime.now());
            socialPaymentRepository.save(payment);
            
            // Update feed entry visibility
            updateFeedVisibility(paymentId, visibility);
            
            // Publish event
            eventPublisher.publishPaymentVisibilityUpdatedEvent(payment);
            
            log.debug("Payment visibility updated successfully: paymentId={}, visibility={}", 
                    paymentId, visibility);
            
        } catch (Exception e) {
            log.error("Failed to update payment visibility: paymentId={}, userId={}, visibility={}", 
                    paymentId, userId, visibility, e);
            throw new SocialPaymentException("Failed to update payment visibility", e);
        }
    }

    /**
     * Gets trending payments (most engaged)
     */
    public List<PaymentFeedResponse> getTrendingPayments(int limit) {
        log.debug("Getting trending payments: limit={}", limit);
        
        try {
            List<PaymentFeed> trendingPayments = paymentFeedRepository.findTrendingPayments(limit);
            return trendingPayments.stream()
                    .map(this::buildPaymentFeedResponse)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get trending payments", e);
            throw new SocialPaymentException("Failed to get trending payments", e);
        }
    }

    /**
     * Searches payments by description or tags
     */
    public Page<PaymentFeedResponse> searchPayments(String query, UUID userId, Pageable pageable) {
        log.debug("Searching payments: query={}, userId={}", query, userId);
        
        try {
            // Get user's connections for privacy filtering
            Set<UUID> connections = userServiceClient.getUserConnections(userId);
            connections.add(userId);
            
            Page<PaymentFeed> searchResults = paymentFeedRepository.searchPayments(
                    query, connections, userId, pageable);
            
            return searchResults.map(this::buildPaymentFeedResponse);
            
        } catch (Exception e) {
            log.error("Failed to search payments: query={}, userId={}", query, userId, e);
            throw new SocialPaymentException("Failed to search payments", e);
        }
    }

    /**
     * Reports inappropriate content
     */
    @Transactional
    public void reportPayment(UUID paymentId, UUID reporterId, String reason) {
        log.info("Reporting payment: paymentId={}, reporterId={}, reason={}", 
                paymentId, reporterId, reason);
        
        try {
            SocialPayment payment = socialPaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new SocialPaymentException("Payment not found"));
            
            // Create report record
            contentModerationService.createReport(paymentId, reporterId, reason);
            
            // If payment receives multiple reports, hide it temporarily
            long reportCount = contentModerationService.getReportCount(paymentId);
            if (reportCount >= 3) { // Threshold for automatic hiding
                payment.setVisibility(SocialPayment.Visibility.PRIVATE);
                payment.setModerationStatus(SocialPayment.ModerationStatus.UNDER_REVIEW);
                socialPaymentRepository.save(payment);
                
                // Hide from feed
                updateFeedVisibility(paymentId, SocialPayment.Visibility.PRIVATE);
                
                // Notify moderation team
                notificationServiceClient.sendModerationAlert(paymentId, reportCount);
            }
            
            log.info("Payment reported successfully: paymentId={}, reportCount={}", paymentId, reportCount);
            
        } catch (Exception e) {
            log.error("Failed to report payment: paymentId={}, reporterId={}", paymentId, reporterId, e);
            throw new SocialPaymentException("Failed to report payment", e);
        }
    }

    // Private helper methods

    private boolean shouldCreateFeedEntry(SocialPayment payment) {
        return payment.getVisibility() != SocialPayment.Visibility.PRIVATE;
    }

    private void createFeedEntry(SocialPayment payment) {
        PaymentFeed feedEntry = PaymentFeed.builder()
                .id(UUID.randomUUID())
                .paymentId(payment.getId())
                .senderId(payment.getSenderId())
                .recipientId(payment.getRecipientId())
                .amount(payment.getAmount())
                .description(payment.getDescription())
                .emoji(payment.getEmoji())
                .visibility(payment.getVisibility())
                .location(payment.getLocation())
                .tags(payment.getTags())
                .engagementScore(0L)
                .createdAt(payment.getCreatedAt())
                .build();
        
        paymentFeedRepository.save(feedEntry);
    }

    private void updateFeedEngagement(UUID paymentId) {
        PaymentFeed feedEntry = paymentFeedRepository.findByPaymentId(paymentId);
        if (feedEntry != null) {
            long commentCount = paymentCommentRepository.countByPaymentId(paymentId);
            long reactionCount = paymentReactionRepository.countByPaymentId(paymentId);
            
            // Calculate engagement score
            long engagementScore = (commentCount * 3) + (reactionCount * 1); // Comments worth more
            feedEntry.setEngagementScore(engagementScore);
            feedEntry.setUpdatedAt(LocalDateTime.now());
            
            paymentFeedRepository.save(feedEntry);
        }
    }

    private void updateFeedVisibility(UUID paymentId, SocialPayment.Visibility visibility) {
        PaymentFeed feedEntry = paymentFeedRepository.findByPaymentId(paymentId);
        if (feedEntry != null) {
            feedEntry.setVisibility(visibility);
            feedEntry.setUpdatedAt(LocalDateTime.now());
            paymentFeedRepository.save(feedEntry);
        }
    }

    private SocialPayment validatePaymentAccess(UUID paymentId, UUID userId) {
        SocialPayment payment = socialPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new SocialPaymentException("Payment not found"));
        
        // Check if user can view this payment based on visibility settings
        if (!canUserViewPayment(payment, userId)) {
            throw new SocialPaymentException("Payment not accessible");
        }
        
        return payment;
    }

    private boolean canUserViewPayment(SocialPayment payment, UUID userId) {
        switch (payment.getVisibility()) {
            case PUBLIC:
                return true;
            case FRIENDS:
                // Check if user is friend with sender or recipient
                return userServiceClient.areFriends(userId, payment.getSenderId()) ||
                       userServiceClient.areFriends(userId, payment.getRecipientId()) ||
                       userId.equals(payment.getSenderId()) ||
                       userId.equals(payment.getRecipientId());
            case PRIVATE:
                // Only sender and recipient can view
                return userId.equals(payment.getSenderId()) || userId.equals(payment.getRecipientId());
            default:
                return false;
        }
    }

    private boolean shouldNotifyForReaction(SocialPayment payment, UUID userId, 
                                          PaymentReaction.ReactionType reactionType) {
        // Don't notify for user's own payments
        if (payment.getSenderId().equals(userId)) {
            return false;
        }
        
        // Only notify for certain reaction types to avoid spam
        return reactionType == PaymentReaction.ReactionType.LOVE ||
               reactionType == PaymentReaction.ReactionType.LAUGH;
    }

    private void sendSocialPaymentNotifications(SocialPayment payment) {
        // Notify based on visibility and relationships
        // Implementation would send appropriate notifications
    }

    private void notifyCommentAdded(SocialPayment payment, PaymentComment comment, UUID commenterId) {
        // Notify payment participants about new comment
        // Implementation would send notifications
    }

    private void notifyReactionAdded(SocialPayment payment, PaymentReaction reaction, UUID reactorId) {
        // Notify payment participants about new reaction
        // Implementation would send notifications
    }

    private SocialPaymentResponse buildSocialPaymentResponse(SocialPayment payment) {
        return SocialPaymentResponse.builder()
                .id(payment.getId())
                .senderId(payment.getSenderId())
                .recipientId(payment.getRecipientId())
                .amount(payment.getAmount().getAmount())
                .currency(payment.getAmount().getCurrency().getCurrencyCode())
                .description(payment.getDescription())
                .emoji(payment.getEmoji())
                .visibility(payment.getVisibility())
                .location(payment.getLocation())
                .tags(payment.getTags())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentFeedResponse buildPaymentFeedResponse(PaymentFeed feedEntry) {
        // Get engagement counts
        long commentCount = paymentCommentRepository.countByPaymentId(feedEntry.getPaymentId());
        long reactionCount = paymentReactionRepository.countByPaymentId(feedEntry.getPaymentId());
        
        return PaymentFeedResponse.builder()
                .paymentId(feedEntry.getPaymentId())
                .senderId(feedEntry.getSenderId())
                .recipientId(feedEntry.getRecipientId())
                .amount(feedEntry.getAmount().getAmount())
                .currency(feedEntry.getAmount().getCurrency().getCurrencyCode())
                .description(feedEntry.getDescription())
                .emoji(feedEntry.getEmoji())
                .location(feedEntry.getLocation())
                .tags(feedEntry.getTags())
                .commentCount(commentCount)
                .reactionCount(reactionCount)
                .engagementScore(feedEntry.getEngagementScore())
                .createdAt(feedEntry.getCreatedAt())
                .build();
    }
}