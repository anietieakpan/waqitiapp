package com.waqiti.social.service;

import com.waqiti.social.domain.*;
import com.waqiti.social.dto.*;
import com.waqiti.social.exception.*;
import com.waqiti.social.repository.*;
import com.waqiti.common.security.AuthenticationFacade;
import com.waqiti.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Comprehensive Social Engagement Service
 * 
 * Handles reactions, comments, shares, trending analysis, engagement scoring,
 * viral content detection, and social influence measurement
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialEngagementService {

    private final SocialInteractionRepository interactionRepository;
    private final SocialActivityRepository activityRepository;
    private final SocialConnectionRepository connectionRepository;
    private final EngagementMetricsRepository metricsRepository;
    private final TrendingContentRepository trendingRepository;
    private final ContentModerationService moderationService;
    private final NotificationService notificationService;
    private final AuthenticationFacade authenticationFacade;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Engagement configuration
    private static final int TRENDING_THRESHOLD_SCORE = 100;
    private static final int VIRAL_THRESHOLD_SCORE = 500;
    private static final int MAX_COMMENT_LENGTH = 2000;
    private static final int TRENDING_TIME_WINDOW_HOURS = 24;
    private static final double ENGAGEMENT_DECAY_RATE = 0.1;

    /**
     * Add reaction to social activity with intelligent deduplication
     */
    public ReactionResponse addReaction(UUID userId, UUID activityId, AddReactionRequest request) {
        log.info("Adding reaction by {} to activity {} type: {}", 
                userId, activityId, request.getReactionType());

        try {
            // Validate activity exists
            SocialActivity activity = getValidatedActivity(activityId);

            // Check for existing reactions and handle appropriately
            Optional<SocialInteraction> existingReaction = interactionRepository
                    .findActiveReactionByUserAndActivity(userId, activityId);

            if (existingReaction.isPresent()) {
                SocialInteraction existing = existingReaction.get();
                
                if (existing.getInteractionType().name().equals(request.getReactionType().name())) {
                    // Same reaction - remove it (toggle off)
                    return removeReaction(userId, existing.getId());
                } else {
                    // Different reaction - update it
                    return updateReaction(userId, existing.getId(), request);
                }
            }

            // Create new reaction
            SocialInteraction reaction = SocialInteraction.builder()
                    .userId(userId)
                    .targetActivityId(activityId)
                    .targetUserId(activity.getUserId())
                    .interactionType(mapReactionTypeToInteractionType(request.getReactionType()))
                    .emoji(request.getCustomEmoji())
                    .intensity(request.getIntensity())
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            reaction = interactionRepository.save(reaction);

            // Update activity engagement metrics
            updateActivityEngagement(activity, EngagementType.REACTION, 1);

            // Calculate and update engagement score
            updateEngagementScore(activity);

            // Check for viral content
            checkViralContent(activity);

            // Send notification to content owner
            if (!userId.equals(activity.getUserId())) {
                sendReactionNotification(activity, reaction);
            }

            // Update user engagement statistics
            updateUserEngagementStats(userId, EngagementAction.REACT);

            // Publish engagement event
            publishEngagementEvent("REACTION_ADDED", reaction);

            log.info("Reaction added: {} to activity: {}", reaction.getId(), activityId);

            return mapToReactionResponse(reaction);

        } catch (Exception e) {
            log.error("Failed to add reaction to activity: {}", activityId, e);
            throw new EngagementException("Failed to add reaction: " + e.getMessage(), e);
        }
    }

    /**
     * Add comment with advanced features (threading, mentions, media)
     */
    public CommentResponse addComment(UUID userId, UUID activityId, AddCommentRequest request) {
        log.info("Adding comment by {} to activity {}", userId, activityId);

        try {
            // Validate activity and comment
            SocialActivity activity = getValidatedActivity(activityId);
            validateCommentRequest(request);

            // Content moderation check
            ModerationResult moderation = moderationService.moderateContent(request.getCommentText());
            if (!moderation.isApproved()) {
                throw new ContentModerationException("Comment contains inappropriate content");
            }

            // Extract mentions and hashtags
            List<String> mentions = extractMentions(request.getCommentText());
            List<String> hashtags = extractHashtags(request.getCommentText());

            // Create comment
            SocialInteraction comment = SocialInteraction.builder()
                    .userId(userId)
                    .targetActivityId(activityId)
                    .targetUserId(activity.getUserId())
                    .interactionType(SocialInteraction.InteractionType.COMMENT)
                    .commentText(request.getCommentText())
                    .replyToInteractionId(request.getReplyToCommentId())
                    .mentions(mentions)
                    .hashtags(hashtags)
                    .mediaAttachments(request.getMediaAttachments())
                    .gifUrl(request.getGifUrl())
                    .stickerData(request.getStickerData())
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            comment = interactionRepository.save(comment);

            // Update activity engagement metrics
            updateActivityEngagement(activity, EngagementType.COMMENT, 1);

            // Update engagement score with higher weight for comments
            updateEngagementScore(activity, 2.0);

            // Process mentions
            processMentionNotifications(comment, mentions);

            // Send notification to content owner and thread participants
            sendCommentNotifications(activity, comment);

            // Update user engagement statistics
            updateUserEngagementStats(userId, EngagementAction.COMMENT);

            // Index comment for search
            indexCommentForSearch(comment);

            // Publish engagement event
            publishEngagementEvent("COMMENT_ADDED", comment);

            log.info("Comment added: {} to activity: {}", comment.getId(), activityId);

            return mapToCommentResponse(comment);

        } catch (Exception e) {
            log.error("Failed to add comment to activity: {}", activityId, e);
            throw new EngagementException("Failed to add comment: " + e.getMessage(), e);
        }
    }

    /**
     * Share activity with personalized message
     */
    public ShareResponse shareActivity(UUID userId, UUID activityId, ShareActivityRequest request) {
        log.info("Sharing activity {} by user {}", activityId, userId);

        try {
            // Validate activity and sharing permissions
            SocialActivity originalActivity = getValidatedActivity(activityId);
            validateSharingPermissions(userId, originalActivity);

            // Create share interaction
            SocialInteraction share = SocialInteraction.builder()
                    .userId(userId)
                    .targetActivityId(activityId)
                    .targetUserId(originalActivity.getUserId())
                    .interactionType(SocialInteraction.InteractionType.SHARE)
                    .commentText(request.getMessage())
                    .shareVisibility(request.getVisibility())
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            share = interactionRepository.save(share);

            // Create new activity for the share
            SocialActivity shareActivity = createShareActivity(userId, originalActivity, request);

            // Update original activity metrics
            updateActivityEngagement(originalActivity, EngagementType.SHARE, 1);
            updateEngagementScore(originalActivity, 3.0); // High weight for shares

            // Check for viral content
            checkViralContent(originalActivity);

            // Update sharing chain analytics
            updateSharingChainAnalytics(originalActivity, userId);

            // Send notifications
            sendShareNotifications(originalActivity, shareActivity, userId);

            // Update user engagement statistics
            updateUserEngagementStats(userId, EngagementAction.SHARE);

            // Publish sharing event
            publishEngagementEvent("ACTIVITY_SHARED", share);

            log.info("Activity shared: {} by user: {}", activityId, userId);

            return ShareResponse.builder()
                    .shareId(share.getId())
                    .shareActivityId(shareActivity.getId())
                    .originalActivityId(activityId)
                    .sharedBy(userId)
                    .sharedAt(LocalDateTime.now())
                    .visibility(request.getVisibility())
                    .build();

        } catch (Exception e) {
            log.error("Failed to share activity: {}", activityId, e);
            throw new EngagementException("Failed to share activity: " + e.getMessage(), e);
        }
    }

    /**
     * Get trending activities with intelligent ranking
     */
    @Cacheable(value = "trendingActivities", key = "#timeframe + '_' + #category", unless = "#result.isEmpty()")
    public List<TrendingActivityResponse> getTrendingActivities(TrendingTimeframe timeframe, 
                                                              String category, int limit) {
        log.info("Getting trending activities for timeframe: {} category: {}", timeframe, category);

        try {
            LocalDateTime since = calculateTrendingWindowStart(timeframe);
            
            // Get activities with high engagement
            List<SocialActivity> candidateActivities = activityRepository
                    .findHighEngagementActivities(since, category, limit * 3); // Get more candidates

            // Calculate trending scores
            List<TrendingActivity> trendingActivities = candidateActivities.stream()
                    .map(this::calculateTrendingScore)
                    .filter(ta -> ta.getScore() >= TRENDING_THRESHOLD_SCORE)
                    .sorted((a, b) -> b.getScore().compareTo(a.getScore()))
                    .limit(limit)
                    .collect(Collectors.toList());

            // Update trending cache
            updateTrendingCache(trendingActivities, timeframe, category);

            // Convert to response
            return trendingActivities.stream()
                    .map(this::mapToTrendingResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to get trending activities", e);
            throw new EngagementException("Failed to get trending activities", e);
        }
    }

    /**
     * Get engagement analytics for activities
     */
    @Transactional(readOnly = true)
    public EngagementAnalytics getEngagementAnalytics(UUID userId, AnalyticsPeriod period) {
        log.info("Generating engagement analytics for user: {} period: {}", userId, period);

        try {
            LocalDateTime startDate = calculatePeriodStart(period);
            LocalDateTime endDate = LocalDateTime.now();

            // Get user's activities
            List<SocialActivity> userActivities = activityRepository
                    .findByUserIdAndDateRange(userId, startDate, endDate);

            // Get user's interactions
            List<SocialInteraction> userInteractions = interactionRepository
                    .findByUserIdAndDateRange(userId, startDate, endDate);

            // Calculate engagement metrics
            EngagementMetrics metrics = calculateEngagementMetrics(userActivities, userInteractions);

            // Analyze engagement patterns
            EngagementPatternAnalysis patterns = analyzeEngagementPatterns(userInteractions);

            // Calculate influence score
            InfluenceScore influence = calculateUserInfluenceScore(userId, userActivities);

            // Get top performing content
            List<TopPerformingContent> topContent = getTopPerformingContent(userActivities, 10);

            // Analyze audience engagement
            AudienceEngagementAnalysis audience = analyzeAudienceEngagement(userActivities);

            // Generate engagement insights
            List<EngagementInsight> insights = generateEngagementInsights(metrics, patterns);

            return EngagementAnalytics.builder()
                    .userId(userId)
                    .period(period)
                    .metrics(metrics)
                    .patterns(patterns)
                    .influence(influence)
                    .topContent(topContent)
                    .audience(audience)
                    .insights(insights)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate engagement analytics", e);
            throw new AnalyticsException("Failed to generate analytics", e);
        }
    }

    /**
     * Get personalized content recommendations based on engagement
     */
    @Async
    public CompletableFuture<ContentRecommendations> getContentRecommendations(UUID userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating content recommendations for user: {}", userId);

                // Analyze user's engagement history
                EngagementHistory history = analyzeUserEngagementHistory(userId);

                // Get friend interactions
                FriendEngagementInsights friendInsights = analyzeFriendEngagement(userId);

                // Generate recommendations based on ML models
                List<ContentRecommendation> recommendations = new ArrayList<>();

                // Content type recommendations
                recommendations.addAll(generateContentTypeRecommendations(history));

                // Timing recommendations
                recommendations.addAll(generateTimingRecommendations(history));

                // Topic recommendations
                recommendations.addAll(generateTopicRecommendations(history, friendInsights));

                // Format recommendations
                recommendations.addAll(generateFormatRecommendations(history));

                // Social strategy recommendations
                recommendations.addAll(generateSocialStrategyRecommendations(friendInsights));

                // Sort by potential impact
                recommendations.sort((a, b) -> b.getPotentialImpact().compareTo(a.getPotentialImpact()));

                return ContentRecommendations.builder()
                        .userId(userId)
                        .recommendations(recommendations.stream().limit(15).collect(Collectors.toList()))
                        .engagementHistory(history)
                        .friendInsights(friendInsights)
                        .generatedAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.error("Failed to generate content recommendations for user: {}", userId, e);
                throw new RecommendationException("Failed to generate recommendations", e);
            }
        });
    }

    /**
     * Process viral content detection and amplification
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    @Async
    public void detectAndProcessViralContent() {
        log.debug("Processing viral content detection");

        try {
            LocalDateTime recentWindow = LocalDateTime.now().minusHours(2);
            
            // Get recently active content
            List<SocialActivity> recentActivities = activityRepository
                    .findRecentlyActiveContent(recentWindow);

            for (SocialActivity activity : recentActivities) {
                try {
                    processViralContentCandidate(activity);
                } catch (Exception e) {
                    log.warn("Failed to process viral candidate: {}", activity.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in viral content detection job", e);
        }
    }

    /**
     * Update engagement metrics and trending status
     */
    @Scheduled(fixedDelay = 900000) // Every 15 minutes
    @Async
    public void updateEngagementMetrics() {
        log.debug("Updating engagement metrics");

        try {
            LocalDateTime window = LocalDateTime.now().minusHours(TRENDING_TIME_WINDOW_HOURS);
            
            // Update trending activities
            updateTrendingActivities(window);

            // Update engagement decay
            applyEngagementDecay();

            // Clean up old metrics
            cleanupOldMetrics();

            log.debug("Engagement metrics updated successfully");

        } catch (Exception e) {
            log.error("Error updating engagement metrics", e);
        }
    }

    // Helper methods for engagement processing

    private SocialActivity getValidatedActivity(UUID activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new ActivityNotFoundException("Activity not found"));
    }

    private void updateActivityEngagement(SocialActivity activity, EngagementType type, int delta) {
        switch (type) {
            case REACTION -> activity.setLikeCount(activity.getLikeCount() + delta);
            case COMMENT -> activity.setCommentCount(activity.getCommentCount() + delta);
            case SHARE -> activity.setShareCount(activity.getShareCount() + delta);
        }
        activityRepository.save(activity);
    }

    private void updateEngagementScore(SocialActivity activity) {
        updateEngagementScore(activity, 1.0);
    }

    private void updateEngagementScore(SocialActivity activity, double weight) {
        try {
            // Calculate engagement score using weighted formula
            double score = calculateEngagementScore(activity) * weight;
            
            // Apply time decay
            long hoursOld = ChronoUnit.HOURS.between(activity.getCreatedAt(), LocalDateTime.now());
            double decayFactor = Math.exp(-hoursOld * ENGAGEMENT_DECAY_RATE / 24.0);
            score = score * decayFactor;

            // Update activity score
            activity.setEngagementScore(BigDecimal.valueOf(score));
            activityRepository.save(activity);

            // Update metrics record
            updateEngagementMetricsRecord(activity, score);

        } catch (Exception e) {
            log.warn("Failed to update engagement score for activity: {}", activity.getId(), e);
        }
    }

    private double calculateEngagementScore(SocialActivity activity) {
        // Weighted engagement score calculation
        return (activity.getLikeCount() * 1.0) +
               (activity.getCommentCount() * 2.0) +
               (activity.getShareCount() * 3.0) +
               (activity.getViewCount() * 0.1);
    }

    private TrendingActivity calculateTrendingScore(SocialActivity activity) {
        double baseScore = calculateEngagementScore(activity);
        
        // Apply velocity multiplier
        long hoursOld = ChronoUnit.HOURS.between(activity.getCreatedAt(), LocalDateTime.now());
        double velocityMultiplier = Math.max(0.1, 24.0 / (hoursOld + 1));
        
        // Apply network effect multiplier
        double networkMultiplier = calculateNetworkEffectMultiplier(activity);
        
        double finalScore = baseScore * velocityMultiplier * networkMultiplier;
        
        return new TrendingActivity(activity, BigDecimal.valueOf(finalScore));
    }

    private double calculateNetworkEffectMultiplier(SocialActivity activity) {
        // Calculate based on unique user interactions and their network reach
        Set<UUID> uniqueInteractors = interactionRepository
                .findUniqueInteractorsByActivity(activity.getId());
        
        if (uniqueInteractors.size() < 2) return 1.0;
        
        // Logarithmic scale for network effect
        return 1.0 + Math.log(uniqueInteractors.size()) * 0.5;
    }

    private void checkViralContent(SocialActivity activity) {
        if (activity.getEngagementScore().compareTo(BigDecimal.valueOf(VIRAL_THRESHOLD_SCORE)) > 0) {
            processViralContentCandidate(activity);
        }
    }

    private void processViralContentCandidate(SocialActivity activity) {
        // Mark as viral
        activity.setIsViral(true);
        activityRepository.save(activity);

        // Amplify reach through recommendations
        amplifyViralContent(activity);

        // Send viral achievement notification
        sendViralContentNotification(activity);

        // Publish viral event
        publishEngagementEvent("CONTENT_GONE_VIRAL", activity);

        log.info("Content marked as viral: {} score: {}", 
                activity.getId(), activity.getEngagementScore());
    }

    private List<String> extractMentions(String text) {
        return text.matches(".*@\\w+.*") ? 
                List.of(text.replaceAll(".*@(\\w+).*", "$1")) : 
                new ArrayList<>();
    }

    private List<String> extractHashtags(String text) {
        return text.matches(".*#\\w+.*") ? 
                List.of(text.replaceAll(".*#(\\w+).*", "$1")) : 
                new ArrayList<>();
    }

    // Event publishing
    private void publishEngagementEvent(String eventType, Object target) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("timestamp", LocalDateTime.now());
        
        if (target instanceof SocialInteraction interaction) {
            event.put("interactionId", interaction.getId());
            event.put("userId", interaction.getUserId());
            event.put("activityId", interaction.getTargetActivityId());
            kafkaTemplate.send("social-engagement-events", interaction.getId().toString(), event);
        } else if (target instanceof SocialActivity activity) {
            event.put("activityId", activity.getId());
            event.put("userId", activity.getUserId());
            kafkaTemplate.send("social-engagement-events", activity.getId().toString(), event);
        }
    }

    // Response mapping
    private ReactionResponse mapToReactionResponse(SocialInteraction reaction) {
        return ReactionResponse.builder()
                .reactionId(reaction.getId())
                .userId(reaction.getUserId())
                .activityId(reaction.getTargetActivityId())
                .reactionType(ReactionType.valueOf(reaction.getInteractionType().name()))
                .emoji(reaction.getEmoji())
                .createdAt(reaction.getCreatedAt())
                .build();
    }

    private CommentResponse mapToCommentResponse(SocialInteraction comment) {
        return CommentResponse.builder()
                .commentId(comment.getId())
                .userId(comment.getUserId())
                .activityId(comment.getTargetActivityId())
                .commentText(comment.getCommentText())
                .replyToCommentId(comment.getReplyToInteractionId())
                .mentions(comment.getMentions())
                .hashtags(comment.getHashtags())
                .likeCount(comment.getLikeCount())
                .replyCount(comment.getReplyCount())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    // Placeholder implementations for complex operations
    private void validateCommentRequest(AddCommentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Comment request cannot be null");
        }
        
        if (request.getCommentText() == null || request.getCommentText().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment text cannot be empty");
        }
        
        if (request.getCommentText().length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment exceeds maximum length of " + MAX_COMMENT_LENGTH);
        }
        
        // Check for spam patterns
        if (containsSpamPatterns(request.getCommentText())) {
            throw new IllegalArgumentException("Comment contains prohibited content");
        }
        
        // Validate mentioned users exist
        if (request.getMentions() != null && !request.getMentions().isEmpty()) {
            for (String mention : request.getMentions()) {
                if (!isValidUserMention(mention)) {
                    throw new IllegalArgumentException("Invalid user mention: " + mention);
                }
            }
        }
    }
    
    private boolean containsSpamPatterns(String text) {
        // Check for common spam patterns
        String lowerText = text.toLowerCase();
        String[] spamPatterns = {"click here", "buy now", "limited offer", "act now", "100% free"};
        for (String pattern : spamPatterns) {
            if (lowerText.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isValidUserMention(String mention) {
        // Remove @ symbol if present
        String username = mention.startsWith("@") ? mention.substring(1) : mention;
        // Check if username matches valid pattern
        return username.matches("^[a-zA-Z0-9_]{3,20}$");
    }
    private void validateSharingPermissions(UUID userId, SocialActivity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity cannot be null");
        }
        
        // Check if activity is shareable
        if (activity.getPrivacyLevel() == SocialActivity.PrivacyLevel.PRIVATE) {
            throw new IllegalArgumentException("Private activities cannot be shared");
        }
        
        // Check if user is blocked by activity owner
        if (isUserBlocked(activity.getUserId(), userId)) {
            throw new IllegalArgumentException("You are not authorized to share this activity");
        }
        
        // Check if activity owner allows sharing
        if (activity.getSharingSettings() != null && 
            !activity.getSharingSettings().isAllowSharing()) {
            throw new IllegalArgumentException("This activity cannot be shared");
        }
        
        // Check connection-only sharing
        if (activity.getPrivacyLevel() == SocialActivity.PrivacyLevel.CONNECTIONS_ONLY) {
            if (!areConnected(activity.getUserId(), userId)) {
                throw new IllegalArgumentException("Only connections can share this activity");
            }
        }
    }
    
    private boolean isUserBlocked(UUID blockerId, UUID blockedUserId) {
        // Check if user is blocked
        return connectionRepository.existsBlockedConnection(blockerId, blockedUserId);
    }
    
    private boolean areConnected(UUID userId1, UUID userId2) {
        return connectionRepository.existsByUser1IdAndUser2IdAndStatus(
            userId1, userId2, SocialConnection.ConnectionStatus.ACCEPTED
        ) || connectionRepository.existsByUser1IdAndUser2IdAndStatus(
            userId2, userId1, SocialConnection.ConnectionStatus.ACCEPTED
        );
    }
    private SocialActivity createShareActivity(UUID userId, SocialActivity original, ShareActivityRequest request) {
        SocialActivity shareActivity = new SocialActivity();
        shareActivity.setUserId(userId);
        shareActivity.setActivityType(SocialActivity.ActivityType.SHARE);
        shareActivity.setSharedActivityId(original.getId());
        shareActivity.setContent(request.getShareComment() != null ? request.getShareComment() : "");
        shareActivity.setPrivacyLevel(request.getPrivacyLevel() != null ? 
            request.getPrivacyLevel() : SocialActivity.PrivacyLevel.PUBLIC);
        shareActivity.setCreatedAt(LocalDateTime.now());
        shareActivity.setUpdatedAt(LocalDateTime.now());
        
        // Copy relevant metadata from original
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("originalUserId", original.getUserId().toString());
        metadata.put("originalActivityId", original.getId().toString());
        metadata.put("originalCreatedAt", original.getCreatedAt().toString());
        metadata.put("shareType", request.getShareType() != null ? request.getShareType() : "STANDARD");
        shareActivity.setMetadata(metadata);
        
        // Initialize engagement counters
        shareActivity.setLikeCount(0);
        shareActivity.setCommentCount(0);
        shareActivity.setShareCount(0);
        shareActivity.setViewCount(0);
        
        // Set hashtags from share comment
        if (request.getShareComment() != null) {
            shareActivity.setHashtags(extractHashtags(request.getShareComment()));
        }
        
        return shareActivity;
    }
    
    private List<String> extractHashtags(String text) {
        List<String> hashtags = new ArrayList<>();
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.startsWith("#") && word.length() > 1) {
                hashtags.add(word.substring(1));
            }
        }
        return hashtags;
    }
    @Async
    private void processMentionNotifications(SocialInteraction comment, List<String> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }
        
        for (String mention : mentions) {
            try {
                // Extract username from mention (remove @ if present)
                String username = mention.startsWith("@") ? mention.substring(1) : mention;
                
                // Find user by username
                UUID mentionedUserId = findUserIdByUsername(username);
                if (mentionedUserId != null && !mentionedUserId.equals(comment.getUserId())) {
                    // Send mention notification
                    NotificationRequest notification = NotificationRequest.builder()
                        .userId(mentionedUserId)
                        .type("MENTION_IN_COMMENT")
                        .title("You were mentioned in a comment")
                        .message(String.format("%s mentioned you in a comment", 
                            getUserDisplayName(comment.getUserId())))
                        .metadata(Map.of(
                            "commentId", comment.getId().toString(),
                            "activityId", comment.getTargetActivityId().toString(),
                            "mentionedBy", comment.getUserId().toString()
                        ))
                        .build();
                    
                    notificationService.sendNotification(notification);
                    
                    // Publish mention event
                    kafkaTemplate.send("social-mentions", Map.of(
                        "mentionedUserId", mentionedUserId,
                        "mentionedBy", comment.getUserId(),
                        "commentId", comment.getId(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            } catch (Exception e) {
                log.error("Error processing mention notification for {}: {}", mention, e.getMessage());
            }
        }
    }
    
    private UUID findUserIdByUsername(String username) {
        try {
            if (username == null || username.trim().isEmpty()) {
                log.debug("Username is null or empty");
                return null;
            }
            
            String cleanUsername = username.trim().toLowerCase();
            log.debug("Looking up user ID for username: {}", cleanUsername);
            
            // 1. Check local cache first for performance
            String cacheKey = "username:" + cleanUsername;
            UUID cachedUserId = usernameLookupCache.get(cacheKey);
            if (cachedUserId != null) {
                log.debug("Found cached user ID for username {}: {}", cleanUsername, cachedUserId);
                return cachedUserId;
            }
            
            // 2. Query local social profiles if available
            UUID userIdFromSocialProfile = findUserIdFromSocialProfile(cleanUsername);
            if (userIdFromSocialProfile != null) {
                // Cache the result for 10 minutes
                usernameLookupCache.put(cacheKey, userIdFromSocialProfile);
                return userIdFromSocialProfile;
            }
            
            // 3. Make inter-service call to user-service
            UUID userIdFromUserService = queryUserService(cleanUsername);
            if (userIdFromUserService != null) {
                // Cache the result for 10 minutes
                usernameLookupCache.put(cacheKey, userIdFromUserService);
                log.debug("Found user ID from user service for username {}: {}", cleanUsername, userIdFromUserService);
                return userIdFromUserService;
            }
            
            // 4. Try alternative lookup methods
            UUID userIdFromAlternativeLookup = tryAlternativeUserLookup(cleanUsername);
            if (userIdFromAlternativeLookup != null) {
                // Cache for shorter time as this is less reliable
                usernameLookupCache.put(cacheKey, userIdFromAlternativeLookup);
                return userIdFromAlternativeLookup;
            }
            
            log.debug("No user ID found for username: {}", cleanUsername);
            
            // Cache negative result for 2 minutes to avoid repeated lookups
            UUID negativeResult = UUID.fromString("00000000-0000-0000-0000-000000000000");
            usernameLookupCache.put(cacheKey, negativeResult);
            
            return null;
            
        } catch (Exception e) {
            log.error("Error looking up user ID for username {}: {}", username, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Find user ID from local social profiles
     */
    private UUID findUserIdFromSocialProfile(String username) {
        try {
            // Query social profiles table if we maintain username mappings
            return socialProfileRepository.findUserIdByUsername(username);
        } catch (Exception e) {
            log.debug("Failed to find user from social profile: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Query user service via REST API or message queue
     */
    private UUID queryUserService(String username) {
        try {
            // Option 1: REST API call to user service
            return queryUserServiceViaRest(username);
        } catch (Exception restException) {
            log.debug("REST call to user service failed: {}", restException.getMessage());
            
            try {
                // Option 2: Fallback to async message-based lookup
                return queryUserServiceViaMessage(username);
            } catch (Exception msgException) {
                log.debug("Message-based user lookup failed: {}", msgException.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Query user service via REST API
     */
    private UUID queryUserServiceViaRest(String username) {
        try {
            String userServiceUrl = String.format("%s/internal/users/by-username/%s", 
                userServiceBaseUrl, URLEncoder.encode(username, StandardCharsets.UTF_8));
            
            ResponseEntity<UserLookupResponse> response = restTemplate.exchange(
                userServiceUrl,
                HttpMethod.GET,
                createAuthenticatedRequest(),
                UserLookupResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserLookupResponse userResponse = response.getBody();
                return userResponse.getUserId();
            }
            
            return null;
            
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("User not found in user service: {}", username);
            return null;
        } catch (Exception e) {
            log.warn("Failed to query user service for username {}: {}", username, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Query user service via message queue (async with timeout)
     */
    private UUID queryUserServiceViaMessage(String username) {
        try {
            String correlationId = UUID.randomUUID().toString();
            
            UserLookupRequest request = UserLookupRequest.builder()
                .username(username)
                .correlationId(correlationId)
                .requestedBy("social-service")
                .build();
            
            // Send message to user lookup queue
            kafkaTemplate.send("user-lookup-requests", correlationId, request);
            
            // Wait for response with timeout (5 seconds)
            return waitForUserLookupResponse(correlationId, 5000);
            
        } catch (Exception e) {
            log.warn("Message-based user lookup failed for username {}: {}", username, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Try alternative lookup methods (email, social handles, etc.)
     */
    private UUID tryAlternativeUserLookup(String username) {
        try {
            // 1. Try as email address
            if (username.contains("@")) {
                return queryUserServiceByEmail(username);
            }
            
            // 2. Try as social media handle
            return queryUserServiceBySocialHandle(username);
            
        } catch (Exception e) {
            log.debug("Alternative lookup failed for {}: {}", username, e.getMessage());
            return null;
        }
    }
    
    /**
     * Query user by email address
     */
    private UUID queryUserServiceByEmail(String email) {
        try {
            String userServiceUrl = String.format("%s/internal/users/by-email/%s", 
                userServiceBaseUrl, URLEncoder.encode(email, StandardCharsets.UTF_8));
            
            ResponseEntity<UserLookupResponse> response = restTemplate.exchange(
                userServiceUrl,
                HttpMethod.GET,
                createAuthenticatedRequest(),
                UserLookupResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().getUserId();
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("User not found by email: {}", email);
        } catch (Exception e) {
            log.debug("Failed to query user by email: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Query user by social media handle
     */
    private UUID queryUserServiceBySocialHandle(String handle) {
        try {
            String userServiceUrl = String.format("%s/internal/users/by-social-handle/%s", 
                userServiceBaseUrl, URLEncoder.encode(handle, StandardCharsets.UTF_8));
            
            ResponseEntity<UserLookupResponse> response = restTemplate.exchange(
                userServiceUrl,
                HttpMethod.GET,
                createAuthenticatedRequest(),
                UserLookupResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().getUserId();
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("User not found by social handle: {}", handle);
        } catch (Exception e) {
            log.debug("Failed to query user by social handle: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Create authenticated request for inter-service communication
     */
    private HttpEntity<?> createAuthenticatedRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + getServiceToken());
        headers.set("X-Service-Name", "social-service");
        headers.set("Content-Type", "application/json");
        return new HttpEntity<>(headers);
    }
    
    /**
     * Get service-to-service authentication token
     */
    private String getServiceToken() {
        // In production, this would get a JWT token for service-to-service communication
        return jwtTokenProvider.generateServiceToken("social-service");
    }
    
    /**
     * Wait for async user lookup response
     */
    private UUID waitForUserLookupResponse(String correlationId, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            UserLookupResponse response = pendingUserLookups.remove(correlationId);
            if (response != null) {
                return response.getUserId();
            }
            
            try {
                TimeUnit.MILLISECONDS.sleep(50); // Poll every 50ms with proper interruption handling
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("User lookup polling interrupted for correlation: {}", correlationId);
                return null;
            }
        }
        
        log.debug("Timeout waiting for user lookup response: {}", correlationId);
        return null;
    }
    
    private String getUserDisplayName(UUID userId) {
        // This would typically query the user service
        return "User";
    }
    @Async
    private void sendReactionNotification(SocialActivity activity, SocialInteraction reaction) {
        // Don't notify if user is reacting to their own content
        if (activity.getUserId().equals(reaction.getUserId())) {
            return;
        }
        
        try {
            NotificationRequest notification = NotificationRequest.builder()
                .userId(activity.getUserId())
                .type("REACTION_RECEIVED")
                .title("New reaction on your post")
                .message(String.format("%s reacted to your post", 
                    getUserDisplayName(reaction.getUserId())))
                .metadata(Map.of(
                    "activityId", activity.getId().toString(),
                    "reactionId", reaction.getId().toString(),
                    "reactionType", reaction.getReactionType().toString(),
                    "reactedBy", reaction.getUserId().toString()
                ))
                .build();
            
            notificationService.sendNotification(notification);
            
            // Publish reaction event for analytics
            kafkaTemplate.send("social-reactions", Map.of(
                "activityId", activity.getId(),
                "userId", reaction.getUserId(),
                "reactionType", reaction.getReactionType(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error sending reaction notification: {}", e.getMessage());
        }
    }
    @Async
    private void sendCommentNotifications(SocialActivity activity, SocialInteraction comment) {
        // Don't notify if user is commenting on their own content
        if (activity.getUserId().equals(comment.getUserId())) {
            return;
        }
        
        try {
            // Notify activity owner
            NotificationRequest notification = NotificationRequest.builder()
                .userId(activity.getUserId())
                .type("COMMENT_RECEIVED")
                .title("New comment on your post")
                .message(String.format("%s commented on your post", 
                    getUserDisplayName(comment.getUserId())))
                .metadata(Map.of(
                    "activityId", activity.getId().toString(),
                    "commentId", comment.getId().toString(),
                    "commentedBy", comment.getUserId().toString(),
                    "commentPreview", comment.getCommentText().length() > 50 ? 
                        comment.getCommentText().substring(0, 50) + "..." : 
                        comment.getCommentText()
                ))
                .build();
            
            notificationService.sendNotification(notification);
            
            // If this is a reply, notify the parent comment author
            if (comment.getReplyToInteractionId() != null) {
                SocialInteraction parentComment = interactionRepository.findById(comment.getReplyToInteractionId())
                    .orElse(null);
                if (parentComment != null && !parentComment.getUserId().equals(comment.getUserId())) {
                    NotificationRequest replyNotification = NotificationRequest.builder()
                        .userId(parentComment.getUserId())
                        .type("COMMENT_REPLY_RECEIVED")
                        .title("New reply to your comment")
                        .message(String.format("%s replied to your comment", 
                            getUserDisplayName(comment.getUserId())))
                        .metadata(Map.of(
                            "activityId", activity.getId().toString(),
                            "commentId", comment.getId().toString(),
                            "parentCommentId", parentComment.getId().toString(),
                            "repliedBy", comment.getUserId().toString()
                        ))
                        .build();
                    
                    notificationService.sendNotification(replyNotification);
                }
            }
            
            // Publish comment event
            kafkaTemplate.send("social-comments", Map.of(
                "activityId", activity.getId(),
                "commentId", comment.getId(),
                "userId", comment.getUserId(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error sending comment notification: {}", e.getMessage());
        }
    }
    @Async
    private void sendShareNotifications(SocialActivity original, SocialActivity share, UUID userId) {
        // Don't notify if user is sharing their own content
        if (original.getUserId().equals(userId)) {
            return;
        }
        
        try {
            NotificationRequest notification = NotificationRequest.builder()
                .userId(original.getUserId())
                .type("CONTENT_SHARED")
                .title("Your post was shared")
                .message(String.format("%s shared your post", getUserDisplayName(userId)))
                .metadata(Map.of(
                    "originalActivityId", original.getId().toString(),
                    "shareActivityId", share.getId().toString(),
                    "sharedBy", userId.toString(),
                    "shareComment", share.getContent() != null ? share.getContent() : ""
                ))
                .build();
            
            notificationService.sendNotification(notification);
            
            // Publish share event
            kafkaTemplate.send("social-shares", Map.of(
                "originalActivityId", original.getId(),
                "shareActivityId", share.getId(),
                "userId", userId,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error sending share notification: {}", e.getMessage());
        }
    }
    @Async
    private void updateUserEngagementStats(UUID userId, EngagementAction action) {
        try {
            // Find or create user engagement metrics
            EngagementMetrics metrics = metricsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    EngagementMetrics newMetrics = new EngagementMetrics();
                    newMetrics.setUserId(userId);
                    newMetrics.setTotalEngagements(0L);
                    newMetrics.setReactionCount(0L);
                    newMetrics.setCommentCount(0L);
                    newMetrics.setShareCount(0L);
                    newMetrics.setViewCount(0L);
                    newMetrics.setEngagementScore(BigDecimal.ZERO);
                    newMetrics.setLastEngagementAt(LocalDateTime.now());
                    return newMetrics;
                });
            
            // Update counts based on action
            switch (action) {
                case REACT:
                    metrics.setReactionCount(metrics.getReactionCount() + 1);
                    metrics.setEngagementScore(metrics.getEngagementScore().add(BigDecimal.valueOf(1)));
                    break;
                case COMMENT:
                    metrics.setCommentCount(metrics.getCommentCount() + 1);
                    metrics.setEngagementScore(metrics.getEngagementScore().add(BigDecimal.valueOf(2)));
                    break;
                case SHARE:
                    metrics.setShareCount(metrics.getShareCount() + 1);
                    metrics.setEngagementScore(metrics.getEngagementScore().add(BigDecimal.valueOf(3)));
                    break;
                case VIEW:
                    metrics.setViewCount(metrics.getViewCount() + 1);
                    metrics.setEngagementScore(metrics.getEngagementScore().add(BigDecimal.valueOf(0.1)));
                    break;
            }
            
            metrics.setTotalEngagements(metrics.getTotalEngagements() + 1);
            metrics.setLastEngagementAt(LocalDateTime.now());
            
            // Calculate engagement rate
            if (metrics.getViewCount() > 0) {
                BigDecimal engagementRate = BigDecimal.valueOf(
                    (metrics.getReactionCount() + metrics.getCommentCount() + metrics.getShareCount()) * 100.0 / 
                    metrics.getViewCount()
                );
                metrics.setEngagementRate(engagementRate);
            }
            
            metricsRepository.save(metrics);
            
            // Publish engagement stats update
            kafkaTemplate.send("user-engagement-stats", Map.of(
                "userId", userId,
                "action", action.toString(),
                "totalEngagements", metrics.getTotalEngagements(),
                "engagementScore", metrics.getEngagementScore(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error updating user engagement stats for {}: {}", userId, e.getMessage());
        }
    }
    @Async
    private void indexCommentForSearch(SocialInteraction comment) {
        try {
            // Publish to search indexing service
            Map<String, Object> searchDocument = Map.of(
                "id", comment.getId().toString(),
                "type", "comment",
                "userId", comment.getUserId().toString(),
                "activityId", comment.getTargetActivityId().toString(),
                "content", comment.getCommentText(),
                "mentions", comment.getMentions() != null ? comment.getMentions() : List.of(),
                "hashtags", comment.getHashtags() != null ? comment.getHashtags() : List.of(),
                "createdAt", comment.getCreatedAt().toString(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("search-indexing", searchDocument);
            
            log.debug("Comment {} queued for search indexing", comment.getId());
        } catch (Exception e) {
            log.error("Error indexing comment {} for search: {}", comment.getId(), e.getMessage());
        }
    }
    @Async
    private void amplifyViralContent(SocialActivity activity) {
        try {
            // Update viral status
            activity.setViralStatus(SocialActivity.ViralStatus.VIRAL);
            activity.setViralDetectedAt(LocalDateTime.now());
            activityRepository.save(activity);
            
            // Boost visibility in feeds
            Map<String, Object> amplificationRequest = Map.of(
                "activityId", activity.getId().toString(),
                "boostFactor", 2.0,
                "duration", "PT24H",
                "reason", "VIRAL_CONTENT",
                "engagementScore", activity.getEngagementScore(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("content-amplification", amplificationRequest);
            
            // Add to trending topics
            TrendingContent trending = new TrendingContent();
            trending.setActivityId(activity.getId());
            trending.setScore(activity.getEngagementScore());
            trending.setTrendingType(TrendingContent.TrendingType.VIRAL);
            trending.setStartedTrendingAt(LocalDateTime.now());
            trending.setExpiresAt(LocalDateTime.now().plusHours(24));
            trendingRepository.save(trending);
            
            log.info("Amplified viral content: activity {} with score {}", 
                activity.getId(), activity.getEngagementScore());
        } catch (Exception e) {
            log.error("Error amplifying viral content {}: {}", activity.getId(), e.getMessage());
        }
    }
    @Async
    private void sendViralContentNotification(SocialActivity activity) {
        try {
            // Notify content creator
            NotificationRequest notification = NotificationRequest.builder()
                .userId(activity.getUserId())
                .type("CONTENT_VIRAL")
                .title("Your post is going viral! 🔥")
                .message(String.format("Your post has reached %d engagements and is trending!", 
                    activity.getLikeCount() + activity.getCommentCount() + activity.getShareCount()))
                .metadata(Map.of(
                    "activityId", activity.getId().toString(),
                    "engagementScore", activity.getEngagementScore().toString(),
                    "likeCount", activity.getLikeCount(),
                    "commentCount", activity.getCommentCount(),
                    "shareCount", activity.getShareCount()
                ))
                .priority("HIGH")
                .build();
            
            notificationService.sendNotification(notification);
            
            // Publish viral content event for analytics
            kafkaTemplate.send("viral-content-detected", Map.of(
                "activityId", activity.getId(),
                "userId", activity.getUserId(),
                "engagementScore", activity.getEngagementScore(),
                "detectedAt", LocalDateTime.now().toString(),
                "timestamp", System.currentTimeMillis()
            ));
            
            log.info("Viral content notification sent for activity {}", activity.getId());
        } catch (Exception e) {
            log.error("Error sending viral content notification: {}", e.getMessage());
        }
    }
    private ReactionResponse removeReaction(UUID userId, UUID reactionId) {
        try {
            SocialInteraction reaction = interactionRepository.findById(reactionId)
                .orElseThrow(() -> new IllegalArgumentException("Reaction not found"));
            
            // Verify user owns this reaction
            if (!reaction.getUserId().equals(userId)) {
                throw new IllegalArgumentException("You can only remove your own reactions");
            }
            
            // Update activity counts
            SocialActivity activity = activityRepository.findById(reaction.getTargetActivityId())
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
            
            activity.setLikeCount(Math.max(0, activity.getLikeCount() - 1));
            activity.setEngagementScore(activity.getEngagementScore().subtract(BigDecimal.ONE));
            activityRepository.save(activity);
            
            // Delete the reaction
            interactionRepository.delete(reaction);
            
            // Update user engagement stats
            updateUserEngagementStats(userId, EngagementAction.REACT);
            
            return ReactionResponse.builder()
                .reactionId(reactionId)
                .userId(userId)
                .activityId(reaction.getTargetActivityId())
                .reactionType(mapInteractionTypeToReactionType(reaction.getReactionType()))
                .removed(true)
                .timestamp(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Error removing reaction {}: {}", reactionId, e.getMessage());
            return ReactionResponse.builder()
                .reactionId(reactionId)
                .error(e.getMessage())
                .build();
        }
    }
    
    private ReactionType mapInteractionTypeToReactionType(SocialInteraction.InteractionType type) {
        return switch (type) {
            case LIKE -> ReactionType.LIKE;
            case LOVE -> ReactionType.LOVE;
            case LAUGH -> ReactionType.LAUGH;
            case WOW -> ReactionType.WOW;
            case REACT_EMOJI -> ReactionType.CELEBRATE;
            default -> ReactionType.LIKE;
        };
    }
    private ReactionResponse updateReaction(UUID userId, UUID reactionId, AddReactionRequest request) {
        try {
            SocialInteraction reaction = interactionRepository.findById(reactionId)
                .orElseThrow(() -> new IllegalArgumentException("Reaction not found"));
            
            // Verify user owns this reaction
            if (!reaction.getUserId().equals(userId)) {
                throw new IllegalArgumentException("You can only update your own reactions");
            }
            
            // Update reaction type
            SocialInteraction.InteractionType newType = mapReactionTypeToInteractionType(request.getReactionType());
            reaction.setReactionType(newType);
            reaction.setUpdatedAt(LocalDateTime.now());
            
            interactionRepository.save(reaction);
            
            return ReactionResponse.builder()
                .reactionId(reactionId)
                .userId(userId)
                .activityId(reaction.getTargetActivityId())
                .reactionType(request.getReactionType())
                .updated(true)
                .timestamp(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("Error updating reaction {}: {}", reactionId, e.getMessage());
            return ReactionResponse.builder()
                .reactionId(reactionId)
                .error(e.getMessage())
                .build();
        }
    }

    // Enum definitions
    public enum EngagementType { REACTION, COMMENT, SHARE, VIEW }
    public enum EngagementAction { REACT, COMMENT, SHARE, VIEW }
    public enum TrendingTimeframe { HOUR, DAY, WEEK, MONTH }
    public enum AnalyticsPeriod { WEEK, MONTH, QUARTER, YEAR }
    public enum ReactionType { LIKE, LOVE, LAUGH, WOW, ANGRY, SAD, CELEBRATE }

    // Helper classes
    private static class TrendingActivity {
        private final SocialActivity activity;
        private final BigDecimal score;

        public TrendingActivity(SocialActivity activity, BigDecimal score) {
            this.activity = activity;
            this.score = score;
        }

        public SocialActivity getActivity() { return activity; }
        public BigDecimal getScore() { return score; }
    }

    private SocialInteraction.InteractionType mapReactionTypeToInteractionType(ReactionType reactionType) {
        return switch (reactionType) {
            case LIKE -> SocialInteraction.InteractionType.LIKE;
            case LOVE -> SocialInteraction.InteractionType.LOVE;
            case LAUGH -> SocialInteraction.InteractionType.LAUGH;
            case WOW -> SocialInteraction.InteractionType.WOW;
            case CELEBRATE -> SocialInteraction.InteractionType.REACT_EMOJI;
            default -> SocialInteraction.InteractionType.LIKE;
        };
    }
}