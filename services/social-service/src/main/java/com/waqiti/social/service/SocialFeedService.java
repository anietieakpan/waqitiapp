package com.waqiti.social.service;

import com.waqiti.social.domain.*;
import com.waqiti.social.dto.*;
import com.waqiti.social.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialFeedService {
    
    private final SocialActivityRepository activityRepository;
    private final SocialConnectionRepository connectionRepository;
    private final SocialInteractionRepository interactionRepository;
    private final SocialPaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final PersonalizationService personalizationService;
    
    /**
     * Get personalized social feed for user
     */
    public Page<SocialActivityDto> getPersonalizedFeed(UUID userId, Pageable pageable) {
        log.debug("Getting personalized feed for user: {}", userId);
        
        // Get user's connections for friend-based filtering
        List<UUID> friendIds = connectionRepository.findAcceptedConnectionsByUserId(userId)
                .stream()
                .map(conn -> conn.getConnectedUserId().equals(userId) ? 
                     conn.getUserId() : conn.getConnectedUserId())
                .collect(Collectors.toList());
        
        // Include user's own activities
        friendIds.add(userId);
        
        // Get activities from friends and public activities
        Page<SocialActivity> activities = activityRepository.findPersonalizedFeed(
            friendIds, 
            Arrays.asList("FRIENDS", "PUBLIC"),
            pageable
        );
        
        // Apply ML-based personalization ranking
        List<SocialActivity> rankedActivities = personalizationService.rankActivitiesByRelevance(
            userId, activities.getContent()
        );
        
        return activities.map(activity -> convertToDto(activity, userId));
    }
    
    /**
     * Get trending activities and payments
     */
    public List<SocialActivityDto> getTrendingActivities(UUID userId, int limit) {
        log.debug("Getting trending activities for user: {}", userId);
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<SocialActivity> trending = activityRepository.findTrendingActivities(since, limit);
        
        return trending.stream()
                .map(activity -> convertToDto(activity, userId))
                .collect(Collectors.toList());
    }
    
    /**
     * Create social activity from payment
     */
    public SocialActivityDto createPaymentActivity(SocialPayment payment) {
        log.debug("Creating social activity for payment: {}", payment.getPaymentId());
        
        SocialActivity activity = SocialActivity.builder()
                .userId(payment.getSenderId())
                .activityType(determineActivityType(payment))
                .paymentId(payment.getId())
                .targetUserId(payment.getRecipientId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .message(payment.getMessage())
                .emoji(payment.getEmoji())
                .visibility(payment.getVisibility())
                .mediaAttachments(payment.getMediaAttachments())
                .location(payment.getLocation())
                .tags(payment.getTags())
                .build();
        
        activity = activityRepository.save(activity);
        
        // Generate activity for recipient if it's a received payment
        if (payment.getStatus() == SocialPayment.PaymentStatus.COMPLETED) {
            createRecipientActivity(payment, activity);
        }
        
        return convertToDto(activity, payment.getSenderId());
    }
    
    /**
     * Add interaction to activity (like, comment, etc.)
     */
    public SocialInteractionDto addInteraction(UUID userId, UUID activityId, 
                                              CreateInteractionRequest request) {
        log.debug("Adding interaction for user: {} on activity: {}", userId, activityId);
        
        SocialActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        
        // Check if user already has this type of interaction
        if (request.getInteractionType().name().contains("LIKE") || 
            request.getInteractionType().name().contains("LOVE")) {
            
            Optional<SocialInteraction> existing = interactionRepository
                    .findByUserIdAndTargetActivityIdAndInteractionType(
                        userId, activityId, request.getInteractionType());
            
            if (existing.isPresent()) {
                // Remove existing reaction
                interactionRepository.delete(existing.get());
                activity.setLikeCount(Math.max(0, activity.getLikeCount() - 1));
                activityRepository.save(activity);
                return null; // Reaction removed
            }
        }
        
        SocialInteraction interaction = SocialInteraction.builder()
                .userId(userId)
                .targetActivityId(activityId)
                .targetUserId(activity.getUserId())
                .interactionType(request.getInteractionType())
                .commentText(request.getCommentText())
                .emoji(request.getEmoji())
                .gifUrl(request.getGifUrl())
                .stickerData(request.getStickerData())
                .replyToInteractionId(request.getReplyToInteractionId())
                .build();
        
        interaction = interactionRepository.save(interaction);
        
        // Update activity counters
        updateActivityCounters(activity, request.getInteractionType());
        
        // Send notification to activity owner
        if (!userId.equals(activity.getUserId())) {
            notificationService.sendInteractionNotification(activity.getUserId(), interaction);
        }
        
        return convertInteractionToDto(interaction);
    }
    
    /**
     * Get activity interactions (comments, reactions)
     */
    public List<SocialInteractionDto> getActivityInteractions(UUID activityId, Pageable pageable) {
        log.debug("Getting interactions for activity: {}", activityId);
        
        List<SocialInteraction> interactions = interactionRepository
                .findByTargetActivityIdOrderByCreatedAtDesc(activityId, pageable);
        
        return interactions.stream()
                .map(this::convertInteractionToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Share activity to user's feed
     */
    public SocialActivityDto shareActivity(UUID userId, UUID activityId, String message) {
        log.debug("User {} sharing activity: {}", userId, activityId);
        
        SocialActivity originalActivity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        
        // Create share activity
        SocialActivity shareActivity = SocialActivity.builder()
                .userId(userId)
                .activityType(SocialActivity.ActivityType.PAYMENT_SENT) // Could be SHARED_ACTIVITY
                .paymentId(originalActivity.getPaymentId())
                .targetUserId(originalActivity.getUserId())
                .amount(originalActivity.getAmount())
                .currency(originalActivity.getCurrency())
                .message(message)
                .visibility("FRIENDS")
                .metadata(Map.of("sharedActivityId", activityId))
                .build();
        
        shareActivity = activityRepository.save(shareActivity);
        
        // Update original activity share count
        originalActivity.incrementShares();
        activityRepository.save(originalActivity);
        
        // Notify original activity owner
        notificationService.sendShareNotification(originalActivity.getUserId(), userId, shareActivity);
        
        return convertToDto(shareActivity, userId);
    }
    
    /**
     * Get user's activity history
     */
    public Page<SocialActivityDto> getUserActivities(UUID userId, UUID viewerUserId, Pageable pageable) {
        log.debug("Getting activities for user: {} viewed by: {}", userId, viewerUserId);
        
        // Determine visibility based on relationship
        List<String> allowedVisibility = determineVisibilityForViewer(userId, viewerUserId);
        
        Page<SocialActivity> activities = activityRepository.findByUserIdAndVisibilityIn(
            userId, allowedVisibility, pageable);
        
        return activities.map(activity -> convertToDto(activity, viewerUserId));
    }
    
    /**
     * Search activities by hashtags, mentions, or keywords
     */
    public List<SocialActivityDto> searchActivities(UUID userId, String query, int limit) {
        log.debug("Searching activities for user: {} with query: {}", userId, query);
        
        List<SocialActivity> activities = activityRepository.searchActivities(query, limit);
        
        return activities.stream()
                .filter(activity -> canUserViewActivity(userId, activity))
                .map(activity -> convertToDto(activity, userId))
                .collect(Collectors.toList());
    }
    
    /**
     * Get activities for a specific location
     */
    public List<SocialActivityDto> getActivitiesByLocation(UUID userId, String location, int limit) {
        log.debug("Getting activities for location: {} for user: {}", location, userId);
        
        List<SocialActivity> activities = activityRepository.findByLocationContainingIgnoreCase(location);
        
        return activities.stream()
                .filter(activity -> canUserViewActivity(userId, activity))
                .limit(limit)
                .map(activity -> convertToDto(activity, userId))
                .collect(Collectors.toList());
    }
    
    /**
     * Mark activity as trending based on engagement metrics
     */
    public void updateTrendingStatus() {
        log.debug("Updating trending status for activities");
        
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        
        // Find activities with high engagement
        List<SocialActivity> candidates = activityRepository.findHighEngagementActivities(since);
        
        for (SocialActivity activity : candidates) {
            double engagementScore = calculateEngagementScore(activity);
            
            if (engagementScore > 50.0) { // Threshold for trending
                activity.setIsTrending(true);
                activityRepository.save(activity);
            }
        }
        
        // Remove trending status from old activities
        activityRepository.removeTrendingStatusForOldActivities(since.minusDays(7));
    }
    
    // Helper methods
    
    private SocialActivity.ActivityType determineActivityType(SocialPayment payment) {
        switch (payment.getPaymentType()) {
            case SEND:
                return SocialActivity.ActivityType.PAYMENT_SENT;
            case REQUEST:
                return SocialActivity.ActivityType.PAYMENT_REQUESTED;
            case SPLIT:
                return SocialActivity.ActivityType.PAYMENT_SPLIT;
            case CHARITY:
                return SocialActivity.ActivityType.CHARITY_DONATION;
            default:
                return SocialActivity.ActivityType.PAYMENT_SENT;
        }
    }
    
    private void createRecipientActivity(SocialPayment payment, SocialActivity senderActivity) {
        SocialActivity recipientActivity = SocialActivity.builder()
                .userId(payment.getRecipientId())
                .activityType(SocialActivity.ActivityType.PAYMENT_RECEIVED)
                .paymentId(payment.getId())
                .targetUserId(payment.getSenderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .message(payment.getMessage())
                .emoji(payment.getEmoji())
                .visibility(payment.getVisibility())
                .location(payment.getLocation())
                .build();
        
        activityRepository.save(recipientActivity);
    }
    
    private void updateActivityCounters(SocialActivity activity, 
                                      SocialInteraction.InteractionType interactionType) {
        switch (interactionType) {
            case LIKE:
            case LOVE:
            case LAUGH:
            case WOW:
            case REACT_EMOJI:
                activity.incrementLikes();
                break;
            case COMMENT:
                activity.incrementComments();
                break;
            case SHARE:
                activity.incrementShares();
                break;
        }
        
        activityRepository.save(activity);
    }
    
    private List<String> determineVisibilityForViewer(UUID userId, UUID viewerUserId) {
        if (userId.equals(viewerUserId)) {
            return Arrays.asList("PRIVATE", "FRIENDS", "PUBLIC");
        }
        
        // Check if viewer is a friend
        boolean isFriend = connectionRepository.existsAcceptedConnection(userId, viewerUserId);
        
        if (isFriend) {
            return Arrays.asList("FRIENDS", "PUBLIC");
        } else {
            return Arrays.asList("PUBLIC");
        }
    }
    
    private boolean canUserViewActivity(UUID userId, SocialActivity activity) {
        switch (activity.getVisibility()) {
            case "PUBLIC":
                return true;
            case "FRIENDS":
                return userId.equals(activity.getUserId()) || 
                       connectionRepository.existsAcceptedConnection(userId, activity.getUserId());
            case "PRIVATE":
                return userId.equals(activity.getUserId());
            default:
                return false;
        }
    }
    
    private double calculateEngagementScore(SocialActivity activity) {
        int totalEngagement = activity.getLikeCount() + 
                             (activity.getCommentCount() * 2) + 
                             (activity.getShareCount() * 3);
        
        long hoursSinceCreated = java.time.Duration.between(
            activity.getCreatedAt(), LocalDateTime.now()).toHours();
        
        // Decay factor for time
        double timeDecay = Math.max(0.1, 1.0 / (1.0 + hoursSinceCreated * 0.1));
        
        return totalEngagement * timeDecay;
    }
    
    private SocialActivityDto convertToDto(SocialActivity activity, UUID viewerUserId) {
        // This would typically use a mapper like MapStruct
        return SocialActivityDto.builder()
                .id(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getActivityType())
                .paymentId(activity.getPaymentId())
                .targetUserId(activity.getTargetUserId())
                .amount(activity.getAmount())
                .currency(activity.getCurrency())
                .message(activity.getMessage())
                .emoji(activity.getEmoji())
                .visibility(activity.getVisibility())
                .mediaAttachments(activity.getMediaAttachments())
                .location(activity.getLocation())
                .tags(activity.getTags())
                .likeCount(activity.getLikeCount())
                .commentCount(activity.getCommentCount())
                .shareCount(activity.getShareCount())
                .isPinned(activity.getIsPinned())
                .isTrending(activity.getIsTrending())
                .createdAt(activity.getCreatedAt())
                .build();
    }
    
    private SocialInteractionDto convertInteractionToDto(SocialInteraction interaction) {
        return SocialInteractionDto.builder()
                .id(interaction.getId())
                .userId(interaction.getUserId())
                .interactionType(interaction.getInteractionType())
                .commentText(interaction.getCommentText())
                .emoji(interaction.getEmoji())
                .gifUrl(interaction.getGifUrl())
                .likeCount(interaction.getLikeCount())
                .createdAt(interaction.getCreatedAt())
                .isEdited(interaction.getIsEdited())
                .build();
    }
}