package com.waqiti.social.service;

import com.waqiti.social.domain.*;
import com.waqiti.social.dto.request.*;
import com.waqiti.social.dto.response.*;
import com.waqiti.social.repository.*;
import com.waqiti.social.exception.*;
import com.waqiti.social.mapper.SocialMapper;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.event.EventPublisher;

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

/**
 * Social Network Service
 * 
 * Manages social connections, friend networks, and social discovery features
 * for the Waqiti payment platform.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SocialNetworkService {

    private final SocialConnectionRepository connectionRepository;
    private final SocialGroupRepository groupRepository;
    private final SocialChallengeRepository challengeRepository;
    private final SocialRecommendationRepository recommendationRepository;
    private final SocialProfileRepository profileRepository;
    
    private final SocialMapper socialMapper;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;
    private final UserServiceClient userServiceClient;
    private final SecurityServiceClient securityServiceClient;

    /**
     * Send a friend request
     */
    public SocialConnectionResponse sendFriendRequest(UUID fromUserId, SendFriendRequestRequest request) {
        log.info("Sending friend request from {} to {}", fromUserId, request.getToUserId());
        
        // Validate users exist
        validateUsersExist(fromUserId, request.getToUserId());
        
        // Check if connection already exists
        Optional<SocialConnection> existingConnection = connectionRepository
                .findByUserIdAndConnectedUserId(fromUserId, request.getToUserId());
        
        if (existingConnection.isPresent()) {
            SocialConnection connection = existingConnection.get();
            if (connection.getStatus() == SocialConnection.ConnectionStatus.CONNECTED) {
                throw new ConnectionAlreadyExistsException("Users are already connected");
            }
            if (connection.getStatus() == SocialConnection.ConnectionStatus.PENDING) {
                throw new PendingConnectionExistsException("Friend request already pending");
            }
        }
        
        // Check daily friend request limits
        validateDailyRequestLimits(fromUserId);
        
        // Create friend request
        SocialConnection connection = SocialConnection.builder()
                .userId(fromUserId)
                .connectedUserId(request.getToUserId())
                .status(SocialConnection.ConnectionStatus.PENDING)
                .requestMessage(request.getMessage())
                .connectionType(SocialConnection.ConnectionType.FRIEND)
                .requestedAt(LocalDateTime.now())
                .privacyLevel(SocialConnection.PrivacyLevel.FRIENDS)
                .canSendMoney(true)
                .canRequestMoney(true)
                .canViewActivity(true)
                .transactionLimit(BigDecimal.valueOf(1000.00)) // Default limit
                .monthlyLimit(BigDecimal.valueOf(5000.00)) // Default monthly limit
                .build();
        
        connection = connectionRepository.save(connection);
        
        // Send notification
        sendFriendRequestNotification(connection);
        
        // Publish event
        publishFriendRequestEvent(connection);
        
        // Cache invalidation
        invalidateUserConnectionsCache(fromUserId, request.getToUserId());
        
        log.info("Friend request sent: {}", connection.getId());
        return socialMapper.toConnectionResponse(connection);
    }

    /**
     * Accept a friend request
     */
    public SocialConnectionResponse acceptFriendRequest(UUID userId, AcceptFriendRequestRequest request) {
        log.info("Accepting friend request: {} by user: {}", request.getConnectionId(), userId);
        
        SocialConnection connection = connectionRepository.findById(request.getConnectionId())
                .orElseThrow(() -> new ConnectionNotFoundException("Friend request not found"));
        
        // Validate user can accept this request
        if (!connection.getConnectedUserId().equals(userId)) {
            throw new UnauthorizedOperationException("Cannot accept this friend request");
        }
        
        if (connection.getStatus() != SocialConnection.ConnectionStatus.PENDING) {
            throw new InvalidConnectionStatusException("Friend request cannot be accepted");
        }
        
        // Accept the connection
        connection.setStatus(SocialConnection.ConnectionStatus.CONNECTED);
        connection.setConnectedAt(LocalDateTime.now());
        connection.setAcceptanceMessage(request.getMessage());
        
        // Set permissions based on request
        if (request.getPermissions() != null) {
            connection.setCanSendMoney(request.getPermissions().getCanSendMoney());
            connection.setCanRequestMoney(request.getPermissions().getCanRequestMoney());
            connection.setCanViewActivity(request.getPermissions().getCanViewActivity());
            connection.setTransactionLimit(request.getPermissions().getTransactionLimit());
            connection.setMonthlyLimit(request.getPermissions().getMonthlyLimit());
        }
        
        connection = connectionRepository.save(connection);
        
        // Create reverse connection
        createReverseConnection(connection);
        
        // Update social profiles
        updateSocialProfiles(connection);
        
        // Send notifications
        sendConnectionAcceptedNotification(connection);
        
        // Generate friend recommendations
        generateFriendRecommendations(connection.getUserId(), connection.getConnectedUserId());
        
        // Publish event
        publishConnectionAcceptedEvent(connection);
        
        // Cache invalidation
        invalidateUserConnectionsCache(connection.getUserId(), connection.getConnectedUserId());
        
        log.info("Friend request accepted: {}", connection.getId());
        return socialMapper.toConnectionResponse(connection);
    }

    /**
     * Create a social group
     */
    public SocialGroupResponse createGroup(UUID creatorId, CreateGroupRequest request) {
        log.info("Creating social group: {} by user: {}", request.getGroupName(), creatorId);
        
        // Validate group name uniqueness for user
        if (groupRepository.existsByCreatorIdAndGroupName(creatorId, request.getGroupName())) {
            throw new GroupNameAlreadyExistsException("Group name already exists");
        }
        
        // Generate group code
        String groupCode = generateGroupCode();
        
        SocialGroup group = SocialGroup.builder()
                .groupName(request.getGroupName())
                .groupDescription(request.getGroupDescription())
                .groupCode(groupCode)
                .creatorId(creatorId)
                .groupType(request.getGroupType())
                .privacyLevel(request.getPrivacyLevel())
                .maxMembers(request.getMaxMembers())
                .isActive(true)
                .allowMemberInvites(request.getAllowMemberInvites())
                .allowGroupPayments(request.getAllowGroupPayments())
                .allowBillSplitting(request.getAllowBillSplitting())
                .defaultSplitType(request.getDefaultSplitType())
                .groupImageUrl(request.getGroupImageUrl())
                .tags(request.getTags())
                .location(request.getLocation())
                .build();
        
        group = groupRepository.save(group);
        
        // Add creator as admin member
        addGroupMember(group, creatorId, SocialGroupMember.MemberRole.ADMIN, null);
        
        // Add initial members if provided
        if (request.getInitialMembers() != null && !request.getInitialMembers().isEmpty()) {
            for (UUID memberId : request.getInitialMembers()) {
                if (!memberId.equals(creatorId)) {
                    inviteToGroup(group, memberId, creatorId);
                }
            }
        }
        
        // Publish event
        publishGroupCreatedEvent(group);
        
        log.info("Social group created: {} with code: {}", group.getId(), groupCode);
        return socialMapper.toGroupResponse(group);
    }

    /**
     * Create a social challenge
     */
    public SocialChallengeResponse createChallenge(UUID creatorId, CreateChallengeRequest request) {
        log.info("Creating social challenge: {} by user: {}", request.getChallengeName(), creatorId);
        
        SocialChallenge challenge = SocialChallenge.builder()
                .challengeName(request.getChallengeName())
                .challengeDescription(request.getChallengeDescription())
                .creatorId(creatorId)
                .challengeType(request.getChallengeType())
                .difficultyLevel(request.getDifficultyLevel())
                .targetAmount(request.getTargetAmount())
                .targetTransactions(request.getTargetTransactions())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isPublic(request.getIsPublic())
                .maxParticipants(request.getMaxParticipants())
                .entryFee(request.getEntryFee())
                .prizePool(request.getPrizePool())
                .rewardStructure(request.getRewardStructure())
                .rules(request.getRules())
                .tags(request.getTags())
                .sponsorInfo(request.getSponsorInfo())
                .status(SocialChallenge.ChallengeStatus.ACTIVE)
                .build();
        
        challenge = challengeRepository.save(challenge);
        
        // Auto-join creator to challenge
        joinChallenge(creatorId, challenge.getId());
        
        // Send invitations if provided
        if (request.getInvitedUsers() != null && !request.getInvitedUsers().isEmpty()) {
            for (UUID userId : request.getInvitedUsers()) {
                if (!userId.equals(creatorId)) {
                    sendChallengeInvitation(challenge, userId);
                }
            }
        }
        
        // Publish event
        publishChallengeCreatedEvent(challenge);
        
        log.info("Social challenge created: {}", challenge.getId());
        return socialMapper.toChallengeResponse(challenge);
    }

    /**
     * Join a social challenge
     */
    public SocialChallengeParticipantResponse joinChallenge(UUID userId, UUID challengeId) {
        log.info("User {} joining challenge: {}", userId, challengeId);
        
        SocialChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Challenge not found"));
        
        // Validate challenge is joinable
        validateChallengeJoinable(challenge, userId);
        
        // Check if user already participating
        if (challengeParticipantRepository.existsByChallengeIdAndUserId(challengeId, userId)) {
            throw new AlreadyParticipatingException("User already participating in challenge");
        }
        
        // Create participant entry
        SocialChallengeParticipant participant = SocialChallengeParticipant.builder()
                .challenge(challenge)
                .userId(userId)
                .joinedAt(LocalDateTime.now())
                .status(SocialChallengeParticipant.ParticipantStatus.ACTIVE)
                .currentProgress(BigDecimal.ZERO)
                .transactionCount(0)
                .lastActivityAt(LocalDateTime.now())
                .build();
        
        participant = challengeParticipantRepository.save(participant);
        
        // Update challenge participant count
        challenge.setCurrentParticipants(challenge.getCurrentParticipants() + 1);
        challengeRepository.save(challenge);
        
        // Send welcome notification
        sendChallengeJoinedNotification(participant);
        
        // Publish event
        publishChallengeJoinedEvent(participant);
        
        log.info("User {} joined challenge: {}", userId, challengeId);
        return socialMapper.toParticipantResponse(participant);
    }

    /**
     * Get social feed for user
     */
    @Transactional(readOnly = true)
    public Page<SocialFeedResponse> getSocialFeed(UUID userId, Pageable pageable) {
        log.info("Getting social feed for user: {}", userId);
        
        String cacheKey = buildFeedCacheKey(userId, pageable);
        Page<SocialFeedResponse> cachedFeed = cacheService.get(cacheKey, Page.class);
        
        if (cachedFeed != null) {
            return cachedFeed;
        }
        
        // Get user's connections
        List<UUID> connectionIds = getActiveConnectionIds(userId);
        connectionIds.add(userId); // Include user's own activities
        
        // Get feed entries
        Page<SocialFeed> feedEntries = feedRepository.findByUserIdInOrderByCreatedAtDesc(
                connectionIds, pageable);
        
        Page<SocialFeedResponse> response = feedEntries.map(socialMapper::toFeedResponse);
        
        // Cache for 5 minutes
        cacheService.set(cacheKey, response, Duration.ofMinutes(5));
        
        return response;
    }

    /**
     * Get friend recommendations
     */
    @Transactional(readOnly = true)
    public List<UserRecommendationResponse> getFriendRecommendations(UUID userId) {
        log.info("Getting friend recommendations for user: {}", userId);
        
        String cacheKey = buildRecommendationsCacheKey(userId);
        List<UserRecommendationResponse> cached = cacheService.get(cacheKey, List.class);
        
        if (cached != null) {
            return cached;
        }
        
        List<SocialRecommendation> recommendations = recommendationRepository
                .findByUserIdAndRecommendationTypeAndIsActiveTrue(
                        userId, SocialRecommendation.RecommendationType.FRIEND_SUGGESTION);
        
        List<UserRecommendationResponse> response = recommendations.stream()
                .map(socialMapper::toRecommendationResponse)
                .collect(Collectors.toList());
        
        // Cache for 30 minutes
        cacheService.set(cacheKey, response, Duration.ofMinutes(30));
        
        return response;
    }

    /**
     * Get social analytics for user
     */
    @Transactional(readOnly = true)
    public SocialAnalyticsResponse getSocialAnalytics(UUID userId) {
        log.info("Getting social analytics for user: {}", userId);
        
        String cacheKey = buildAnalyticsCacheKey(userId);
        SocialAnalyticsResponse cached = cacheService.get(cacheKey, SocialAnalyticsResponse.class);
        
        if (cached != null) {
            return cached;
        }
        
        // Calculate various social metrics
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        SocialAnalyticsResponse analytics = SocialAnalyticsResponse.builder()
                .userId(userId)
                .totalConnections(connectionRepository.countActiveConnectionsByUserId(userId))
                .connectionsThisMonth(connectionRepository.countConnectionsSince(userId, thirtyDaysAgo))
                .totalGroups(groupRepository.countActiveGroupsByUserId(userId))
                .activeChallenges(challengeParticipantRepository.countActiveChallengesByUserId(userId))
                .socialScore(calculateSocialScore(userId))
                .engagementLevel(calculateEngagementLevel(userId))
                .topConnections(getTopConnections(userId))
                .recentActivities(getRecentSocialActivities(userId, 10))
                .socialGrowthTrend(calculateSocialGrowthTrend(userId))
                .lastUpdated(LocalDateTime.now())
                .build();
        
        // Cache for 1 hour
        cacheService.set(cacheKey, analytics, Duration.ofHours(1));
        
        return analytics;
    }

    // Private helper methods

    private void validateUsersExist(UUID userId1, UUID userId2) {
        if (userId1.equals(userId2)) {
            throw new InvalidOperationException("Cannot connect to self");
        }
        
        if (!userServiceClient.userExists(userId1) || !userServiceClient.userExists(userId2)) {
            throw new UserNotFoundException("One or more users not found");
        }
    }

    private void validateDailyRequestLimits(UUID userId) {
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        int dailyRequests = connectionRepository.countRequestsSince(userId, today);
        
        if (dailyRequests >= 20) { // Daily limit
            throw new DailyLimitExceededException("Daily friend request limit exceeded");
        }
    }

    private void createReverseConnection(SocialConnection originalConnection) {
        SocialConnection reverseConnection = SocialConnection.builder()
                .userId(originalConnection.getConnectedUserId())
                .connectedUserId(originalConnection.getUserId())
                .status(SocialConnection.ConnectionStatus.CONNECTED)
                .connectionType(originalConnection.getConnectionType())
                .connectedAt(originalConnection.getConnectedAt())
                .privacyLevel(originalConnection.getPrivacyLevel())
                .canSendMoney(originalConnection.getCanSendMoney())
                .canRequestMoney(originalConnection.getCanRequestMoney())
                .canViewActivity(originalConnection.getCanViewActivity())
                .transactionLimit(originalConnection.getTransactionLimit())
                .monthlyLimit(originalConnection.getMonthlyLimit())
                .build();
        
        connectionRepository.save(reverseConnection);
    }

    private void updateSocialProfiles(SocialConnection connection) {
        // Update connection counts in user profiles
        profileRepository.findByUserId(connection.getUserId())
                .ifPresent(profile -> {
                    profile.setConnectionCount(profile.getConnectionCount() + 1);
                    profileRepository.save(profile);
                });
        
        profileRepository.findByUserId(connection.getConnectedUserId())
                .ifPresent(profile -> {
                    profile.setConnectionCount(profile.getConnectionCount() + 1);
                    profileRepository.save(profile);
                });
    }

    private void generateFriendRecommendations(UUID userId1, UUID userId2) {
        // Mutual friends logic
        List<UUID> mutualFriends = connectionRepository.findMutualConnections(userId1, userId2);
        
        // Create recommendations based on mutual connections
        for (UUID mutualFriend : mutualFriends) {
            List<UUID> friendsOfFriend = connectionRepository.findConnectionsByUserId(mutualFriend);
            
            for (UUID potentialFriend : friendsOfFriend) {
                if (!potentialFriend.equals(userId1) && 
                    !connectionRepository.existsByUserIdAndConnectedUserId(userId1, potentialFriend)) {
                    
                    createRecommendation(userId1, potentialFriend, 
                            SocialRecommendation.RecommendationType.FRIEND_SUGGESTION,
                            "Mutual friend: " + userServiceClient.getUserDisplayName(mutualFriend),
                            0.8); // High confidence for mutual friends
                }
            }
        }
    }

    private void createRecommendation(UUID userId, UUID recommendedUserId, 
                                    SocialRecommendation.RecommendationType type,
                                    String reason, double confidenceScore) {
        
        // Check if recommendation already exists
        if (!recommendationRepository.existsByUserIdAndRecommendedUserIdAndRecommendationType(
                userId, recommendedUserId, type)) {
            
            SocialRecommendation recommendation = SocialRecommendation.builder()
                    .userId(userId)
                    .recommendedUserId(recommendedUserId)
                    .recommendationType(type)
                    .reason(reason)
                    .confidenceScore(confidenceScore)
                    .isActive(true)
                    .build();
            
            recommendationRepository.save(recommendation);
        }
    }

    private String generateGroupCode() {
        // Generate 8-character alphanumeric code
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase();
    }

    private void addGroupMember(SocialGroup group, UUID userId, 
                               SocialGroupMember.MemberRole role, UUID invitedBy) {
        SocialGroupMember member = SocialGroupMember.builder()
                .group(group)
                .userId(userId)
                .role(role)
                .status(SocialGroupMember.MemberStatus.ACTIVE)
                .invitedBy(invitedBy)
                .joinedAt(LocalDateTime.now())
                .build();
        
        groupMemberRepository.save(member);
        
        // Update group member count
        group.setCurrentMembers(group.getCurrentMembers() + 1);
        groupRepository.save(group);
    }

    private void inviteToGroup(SocialGroup group, UUID userId, UUID invitedBy) {
        SocialGroupMember member = SocialGroupMember.builder()
                .group(group)
                .userId(userId)
                .role(SocialGroupMember.MemberRole.MEMBER)
                .status(SocialGroupMember.MemberStatus.INVITED)
                .invitedBy(invitedBy)
                .invitedAt(LocalDateTime.now())
                .build();
        
        groupMemberRepository.save(member);
        
        // Send invitation notification
        sendGroupInvitationNotification(member);
    }

    private void validateChallengeJoinable(SocialChallenge challenge, UUID userId) {
        if (challenge.getStatus() != SocialChallenge.ChallengeStatus.ACTIVE) {
            throw new ChallengeNotActiveException("Challenge is not active");
        }
        
        if (challenge.getStartDate() != null && LocalDateTime.now().isBefore(challenge.getStartDate())) {
            throw new ChallengeNotStartedException("Challenge has not started yet");
        }
        
        if (challenge.getEndDate() != null && LocalDateTime.now().isAfter(challenge.getEndDate())) {
            throw new ChallengeEndedException("Challenge has ended");
        }
        
        if (challenge.getMaxParticipants() != null && 
            challenge.getCurrentParticipants() >= challenge.getMaxParticipants()) {
            throw new ChallengeFullException("Challenge is full");
        }
        
        // Check entry fee if required
        if (challenge.getEntryFee() != null && challenge.getEntryFee().compareTo(BigDecimal.ZERO) > 0) {
            // Validate user can afford entry fee
            if (!walletServiceClient.hasBalance(userId, challenge.getEntryFee())) {
                throw new InsufficientBalanceException("Insufficient balance for entry fee");
            }
        }
    }

    private List<UUID> getActiveConnectionIds(UUID userId) {
        return connectionRepository.findActiveConnectionIdsByUserId(userId);
    }

    private int calculateSocialScore(UUID userId) {
        // Complex social score calculation based on various factors
        int connections = connectionRepository.countActiveConnectionsByUserId(userId);
        int groups = groupRepository.countActiveGroupsByUserId(userId);
        int challenges = challengeParticipantRepository.countCompletedChallengesByUserId(userId);
        int transactions = paymentRepository.countSocialTransactionsByUserId(userId);
        
        return (connections * 10) + (groups * 15) + (challenges * 25) + (transactions * 5);
    }

    private String calculateEngagementLevel(UUID userId) {
        int score = calculateSocialScore(userId);
        
        if (score >= 1000) return "HIGHLY_ACTIVE";
        if (score >= 500) return "ACTIVE";
        if (score >= 100) return "MODERATE";
        return "LOW";
    }

    private List<TopConnectionResponse> getTopConnections(UUID userId) {
        return connectionRepository.findTopConnectionsByUserId(userId, 5)
                .stream()
                .map(socialMapper::toTopConnectionResponse)
                .collect(Collectors.toList());
    }

    private List<SocialActivityResponse> getRecentSocialActivities(UUID userId, int limit) {
        return feedRepository.findRecentActivitiesByUserId(userId, limit)
                .stream()
                .map(socialMapper::toActivityResponse)
                .collect(Collectors.toList());
    }

    private SocialGrowthTrendResponse calculateSocialGrowthTrend(UUID userId) {
        // Calculate growth trends over different time periods
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastWeek = now.minusDays(7);
        LocalDateTime lastMonth = now.minusDays(30);
        
        int connectionsThisWeek = connectionRepository.countConnectionsSince(userId, lastWeek);
        int connectionsThisMonth = connectionRepository.countConnectionsSince(userId, lastMonth);
        
        return SocialGrowthTrendResponse.builder()
                .weeklyGrowth(connectionsThisWeek)
                .monthlyGrowth(connectionsThisMonth)
                .growthPercentage(calculateGrowthPercentage(connectionsThisWeek, connectionsThisMonth))
                .trend(calculateTrendDirection(connectionsThisWeek, connectionsThisMonth))
                .build();
    }

    private double calculateGrowthPercentage(int weeklyGrowth, int monthlyGrowth) {
        if (monthlyGrowth == 0) return 0.0;
        return ((double) weeklyGrowth / monthlyGrowth) * 100.0;
    }

    private String calculateTrendDirection(int weeklyGrowth, int monthlyGrowth) {
        double weeklyRate = weeklyGrowth / 7.0;
        double monthlyRate = monthlyGrowth / 30.0;
        
        if (weeklyRate > monthlyRate * 1.1) return "UPWARD";
        if (weeklyRate < monthlyRate * 0.9) return "DOWNWARD";
        return "STABLE";
    }

    // Cache key builders
    private String buildFeedCacheKey(UUID userId, Pageable pageable) {
        return String.format("social:feed:%s:page:%d:size:%d", 
                userId, pageable.getPageNumber(), pageable.getPageSize());
    }

    private String buildRecommendationsCacheKey(UUID userId) {
        return String.format("social:recommendations:%s", userId);
    }

    private String buildAnalyticsCacheKey(UUID userId) {
        return String.format("social:analytics:%s", userId);
    }

    // Cache invalidation methods
    private void invalidateUserConnectionsCache(UUID... userIds) {
        for (UUID userId : userIds) {
            cacheService.evictPattern("social:*:" + userId + ":*");
        }
    }

    // Notification methods
    private void sendFriendRequestNotification(SocialConnection connection) {
        // Implementation would send notification
    }

    private void sendConnectionAcceptedNotification(SocialConnection connection) {
        // Implementation would send notification
    }

    private void sendGroupInvitationNotification(SocialGroupMember member) {
        // Implementation would send notification
    }

    private void sendChallengeInvitation(SocialChallenge challenge, UUID userId) {
        // Implementation would send notification
    }

    private void sendChallengeJoinedNotification(SocialChallengeParticipant participant) {
        // Implementation would send notification
    }

    // Event publishing methods
    private void publishFriendRequestEvent(SocialConnection connection) {
        // Implementation would publish event
    }

    private void publishConnectionAcceptedEvent(SocialConnection connection) {
        // Implementation would publish event
    }

    private void publishGroupCreatedEvent(SocialGroup group) {
        // Implementation would publish event
    }

    private void publishChallengeCreatedEvent(SocialChallenge challenge) {
        // Implementation would publish event
    }

    private void publishChallengeJoinedEvent(SocialChallengeParticipant participant) {
        // Implementation would publish event
    }
}