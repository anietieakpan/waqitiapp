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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Social Network Management Service
 * 
 * Handles connections, friend discovery, groups, privacy settings,
 * social insights, and network analytics
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialNetworkManagementService {

    private final SocialConnectionRepository connectionRepository;
    private final SocialGroupRepository groupRepository;
    private final SocialActivityRepository activityRepository;
    private final SocialPaymentRepository paymentRepository;
    private final UserProfileRepository profileRepository;
    private final PrivacySettingsRepository privacyRepository;
    private final FriendSuggestionRepository suggestionRepository;
    private final AuthenticationFacade authenticationFacade;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_CONNECTIONS_PER_USER = 5000;
    private static final int MAX_GROUPS_PER_USER = 100;
    private static final int SUGGESTION_BATCH_SIZE = 50;

    /**
     * Send friend connection request
     */
    public ConnectionResponse sendConnectionRequest(UUID userId, UUID targetUserId, ConnectionRequest request) {
        log.info("User {} sending connection request to {}", userId, targetUserId);

        try {
            validateConnectionRequest(userId, targetUserId);

            // Check if connection already exists
            if (connectionRepository.existsConnection(userId, targetUserId)) {
                throw new ConnectionAlreadyExistsException("Connection already exists between users");
            }

            // Check user's connection limit
            int currentConnections = connectionRepository.countActiveConnectionsByUserId(userId);
            if (currentConnections >= MAX_CONNECTIONS_PER_USER) {
                throw new ConnectionLimitExceededException("Maximum connections limit reached");
            }

            // Create connection request
            SocialConnection connection = SocialConnection.builder()
                    .userId(userId)
                    .connectedUserId(targetUserId)
                    .status(ConnectionStatus.PENDING)
                    .requestMessage(request.getMessage())
                    .connectionType(request.getConnectionType())
                    .connectionSource(request.getSource())
                    .requestedAt(LocalDateTime.now())
                    .build();

            connection = connectionRepository.save(connection);

            // Send notification to target user
            sendConnectionRequestNotification(connection);

            // Update friend suggestions
            updateFriendSuggestions(userId, targetUserId);

            // Publish connection event
            publishConnectionEvent("CONNECTION_REQUEST_SENT", connection);

            log.info("Connection request sent: {} to {}", connection.getId(), targetUserId);

            return mapToConnectionResponse(connection);

        } catch (Exception e) {
            log.error("Failed to send connection request from {} to {}", userId, targetUserId, e);
            throw new SocialNetworkException("Failed to send connection request: " + e.getMessage(), e);
        }
    }

    /**
     * Accept or reject connection request
     */
    public ConnectionResponse respondToConnectionRequest(UUID userId, UUID connectionId, 
                                                       ConnectionResponse.Decision decision) {
        log.info("User {} responding to connection request {} with decision: {}", 
                userId, connectionId, decision);

        try {
            SocialConnection connection = connectionRepository.findById(connectionId)
                    .orElseThrow(() -> new ConnectionNotFoundException("Connection request not found"));

            // Validate user can respond to this request
            if (!connection.getConnectedUserId().equals(userId)) {
                throw new UnauthorizedConnectionAccessException("User not authorized to respond to this request");
            }

            if (connection.getStatus() != ConnectionStatus.PENDING) {
                throw new InvalidConnectionStatusException("Connection request is not pending");
            }

            // Update connection status
            ConnectionStatus newStatus = decision == ConnectionResponse.Decision.ACCEPT ? 
                    ConnectionStatus.ACCEPTED : ConnectionStatus.REJECTED;

            connection.setStatus(newStatus);
            connection.setRespondedAt(LocalDateTime.now());

            if (newStatus == ConnectionStatus.ACCEPTED) {
                connection.setConnectedAt(LocalDateTime.now());
                
                // Create mutual connection record
                createMutualConnection(connection);
                
                // Update connection strength
                updateConnectionStrength(connection);
            }

            connection = connectionRepository.save(connection);

            // Send notification to requester
            sendConnectionResponseNotification(connection, decision);

            // Update friend suggestions for both users
            if (newStatus == ConnectionStatus.ACCEPTED) {
                updateMutualFriendSuggestions(connection.getUserId(), connection.getConnectedUserId());
            }

            // Publish connection event
            publishConnectionEvent(newStatus == ConnectionStatus.ACCEPTED ? 
                    "CONNECTION_ACCEPTED" : "CONNECTION_REJECTED", connection);

            return mapToConnectionResponse(connection);

        } catch (Exception e) {
            log.error("Failed to respond to connection request: {}", connectionId, e);
            throw new SocialNetworkException("Failed to respond to connection request: " + e.getMessage(), e);
        }
    }

    /**
     * Get user's connections with filtering and sorting
     */
    @Transactional(readOnly = true)
    public Page<UserConnectionResponse> getUserConnections(UUID userId, ConnectionFilter filter, Pageable pageable) {
        log.debug("Getting connections for user: {} with filter: {}", userId, filter);

        try {
            Page<SocialConnection> connections = connectionRepository.findConnectionsWithFilter(
                    userId, filter.getStatus(), filter.getConnectionType(), 
                    filter.getSearchQuery(), pageable);

            return connections.map(this::mapToUserConnectionResponse);

        } catch (Exception e) {
            log.error("Failed to get connections for user: {}", userId, e);
            throw new SocialNetworkException("Failed to retrieve connections", e);
        }
    }

    /**
     * Get intelligent friend suggestions
     */
    @Async
    public CompletableFuture<FriendSuggestionsResponse> getFriendSuggestions(UUID userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating friend suggestions for user: {}", userId);

                // Get existing connections to exclude
                Set<UUID> existingConnections = getExistingConnectionIds(userId);

                // Generate suggestions based on multiple algorithms
                List<FriendSuggestion> suggestions = new ArrayList<>();

                // Mutual friends algorithm
                suggestions.addAll(getMutualFriendSuggestions(userId, existingConnections));

                // Contact book matching
                suggestions.addAll(getContactBookSuggestions(userId, existingConnections));

                // Payment history based suggestions
                suggestions.addAll(getPaymentHistorySuggestions(userId, existingConnections));

                // Location-based suggestions
                suggestions.addAll(getLocationBasedSuggestions(userId, existingConnections));

                // Interest-based suggestions
                suggestions.addAll(getInterestBasedSuggestions(userId, existingConnections));

                // Sort by relevance score and limit results
                suggestions = suggestions.stream()
                        .sorted((a, b) -> b.getRelevanceScore().compareTo(a.getRelevanceScore()))
                        .limit(SUGGESTION_BATCH_SIZE)
                        .collect(Collectors.toList());

                // Cache suggestions for future use
                cacheFriendSuggestions(userId, suggestions);

                return FriendSuggestionsResponse.builder()
                        .userId(userId)
                        .suggestions(suggestions)
                        .totalSuggestions(suggestions.size())
                        .generatedAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.error("Failed to generate friend suggestions for user: {}", userId, e);
                throw new SocialNetworkException("Failed to generate friend suggestions", e);
            }
        });
    }

    /**
     * Create or join social group
     */
    public SocialGroupResponse createGroup(UUID userId, CreateGroupRequest request) {
        log.info("User {} creating group: {}", userId, request.getGroupName());

        try {
            // Check user's group limit
            int currentGroups = groupRepository.countGroupsByUserId(userId);
            if (currentGroups >= MAX_GROUPS_PER_USER) {
                throw new GroupLimitExceededException("Maximum groups limit reached");
            }

            // Create group
            SocialGroup group = SocialGroup.builder()
                    .groupName(request.getGroupName())
                    .description(request.getDescription())
                    .groupType(request.getGroupType())
                    .privacy(request.getPrivacy())
                    .createdBy(userId)
                    .maxMembers(request.getMaxMembers())
                    .joinApprovalRequired(request.isJoinApprovalRequired())
                    .allowMemberInvites(request.isAllowMemberInvites())
                    .groupIcon(request.getGroupIcon())
                    .groupBanner(request.getGroupBanner())
                    .tags(request.getTags())
                    .rules(request.getRules())
                    .createdAt(LocalDateTime.now())
                    .build();

            group = groupRepository.save(group);

            // Add creator as admin member
            addGroupMember(group.getId(), userId, GroupRole.ADMIN, true);

            // Send invitations if provided
            if (request.getInitialMembers() != null && !request.getInitialMembers().isEmpty()) {
                sendGroupInvitations(group, request.getInitialMembers());
            }

            // Publish group creation event
            publishGroupEvent("GROUP_CREATED", group);

            log.info("Group created successfully: {}", group.getId());

            return mapToGroupResponse(group);

        } catch (Exception e) {
            log.error("Failed to create group for user: {}", userId, e);
            throw new SocialNetworkException("Failed to create group: " + e.getMessage(), e);
        }
    }

    /**
     * Join social group
     */
    public GroupMembershipResponse joinGroup(UUID userId, UUID groupId, JoinGroupRequest request) {
        log.info("User {} joining group: {}", userId, groupId);

        try {
            SocialGroup group = getValidatedGroup(groupId);

            // Check if user is already a member
            if (groupRepository.isUserMember(groupId, userId)) {
                throw new AlreadyGroupMemberException("User is already a member of this group");
            }

            // Check group capacity
            if (group.getCurrentMembers() >= group.getMaxMembers()) {
                throw new GroupCapacityExceededException("Group has reached maximum capacity");
            }

            // Determine membership status based on group settings
            GroupMembershipStatus status = group.isJoinApprovalRequired() ? 
                    GroupMembershipStatus.PENDING : GroupMembershipStatus.ACTIVE;

            // Add member
            GroupMembership membership = addGroupMember(groupId, userId, GroupRole.MEMBER, 
                    status == GroupMembershipStatus.ACTIVE);

            // Send notifications
            if (status == GroupMembershipStatus.PENDING) {
                sendJoinRequestNotification(group, membership);
            } else {
                sendMemberJoinedNotification(group, membership);
            }

            return mapToMembershipResponse(membership);

        } catch (Exception e) {
            log.error("Failed to join group {} for user: {}", groupId, userId, e);
            throw new SocialNetworkException("Failed to join group: " + e.getMessage(), e);
        }
    }

    /**
     * Get social network analytics
     */
    @Transactional(readOnly = true)
    public SocialNetworkAnalytics getNetworkAnalytics(UUID userId, AnalyticsPeriod period) {
        log.info("Generating network analytics for user: {} period: {}", userId, period);

        try {
            LocalDateTime startDate = calculateStartDate(period);
            LocalDateTime endDate = LocalDateTime.now();

            // Connection metrics
            ConnectionMetrics connectionMetrics = calculateConnectionMetrics(userId, startDate, endDate);

            // Activity metrics
            ActivityMetrics activityMetrics = calculateActivityMetrics(userId, startDate, endDate);

            // Engagement metrics
            EngagementMetrics engagementMetrics = calculateEngagementMetrics(userId, startDate, endDate);

            // Network influence score
            NetworkInfluenceScore influenceScore = calculateNetworkInfluence(userId);

            // Popular interactions
            List<PopularInteraction> popularInteractions = getPopularInteractions(userId, startDate, endDate);

            // Network growth trends
            List<NetworkGrowthPoint> growthTrends = calculateNetworkGrowth(userId, period);

            return SocialNetworkAnalytics.builder()
                    .userId(userId)
                    .period(period)
                    .connectionMetrics(connectionMetrics)
                    .activityMetrics(activityMetrics)
                    .engagementMetrics(engagementMetrics)
                    .influenceScore(influenceScore)
                    .popularInteractions(popularInteractions)
                    .growthTrends(growthTrends)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate network analytics for user: {}", userId, e);
            throw new SocialNetworkException("Failed to generate analytics", e);
        }
    }

    /**
     * Update privacy settings
     */
    public PrivacySettingsResponse updatePrivacySettings(UUID userId, UpdatePrivacyRequest request) {
        log.info("Updating privacy settings for user: {}", userId);

        try {
            PrivacySettings settings = privacyRepository.findByUserId(userId)
                    .orElse(PrivacySettings.builder().userId(userId).build());

            // Update settings
            settings.setProfileVisibility(request.getProfileVisibility());
            settings.setActivityVisibility(request.getActivityVisibility());
            settings.setConnectionsVisibility(request.getConnectionsVisibility());
            settings.setAllowFriendRequests(request.isAllowFriendRequests());
            settings.setAllowGroupInvites(request.isAllowGroupInvites());
            settings.setAllowPaymentRequests(request.isAllowPaymentRequests());
            settings.setShowOnlineStatus(request.isShowOnlineStatus());
            settings.setAllowLocationSharing(request.isAllowLocationSharing());
            settings.setUpdatedAt(LocalDateTime.now());

            settings = privacyRepository.save(settings);

            return mapToPrivacyResponse(settings);

        } catch (Exception e) {
            log.error("Failed to update privacy settings for user: {}", userId, e);
            throw new SocialNetworkException("Failed to update privacy settings", e);
        }
    }

    /**
     * Block or unblock user
     */
    public BlockResponse blockUser(UUID userId, UUID targetUserId, BlockRequest request) {
        log.info("User {} blocking user: {}", userId, targetUserId);

        try {
            validateBlockRequest(userId, targetUserId);

            // Remove existing connection if any
            connectionRepository.removeConnection(userId, targetUserId);

            // Create block record
            UserBlock block = UserBlock.builder()
                    .blockingUserId(userId)
                    .blockedUserId(targetUserId)
                    .reason(request.getReason())
                    .blockedAt(LocalDateTime.now())
                    .build();

            block = blockRepository.save(block);

            // Update privacy and visibility
            updateBlockedUserVisibility(userId, targetUserId);

            return BlockResponse.builder()
                    .blockId(block.getId())
                    .blockedUserId(targetUserId)
                    .blockedAt(block.getBlockedAt())
                    .build();

        } catch (Exception e) {
            log.error("Failed to block user {} by {}", targetUserId, userId, e);
            throw new SocialNetworkException("Failed to block user", e);
        }
    }

    // Helper methods for network management

    private void validateConnectionRequest(UUID userId, UUID targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new InvalidConnectionRequestException("Cannot send connection request to yourself");
        }

        // Check if users are blocked
        if (blockRepository.existsBlock(userId, targetUserId)) {
            throw new BlockedUserException("Cannot connect to blocked user");
        }

        // Check privacy settings
        PrivacySettings targetPrivacy = privacyRepository.findByUserId(targetUserId).orElse(null);
        if (targetPrivacy != null && !targetPrivacy.isAllowFriendRequests()) {
            throw new PrivacyViolationException("User does not allow friend requests");
        }
    }

    private void createMutualConnection(SocialConnection originalConnection) {
        // Create reverse connection for mutual relationship
        SocialConnection mutualConnection = SocialConnection.builder()
                .userId(originalConnection.getConnectedUserId())
                .connectedUserId(originalConnection.getUserId())
                .status(ConnectionStatus.ACCEPTED)
                .connectionType(originalConnection.getConnectionType())
                .connectionSource("MUTUAL")
                .requestedAt(originalConnection.getRequestedAt())
                .connectedAt(LocalDateTime.now())
                .build();

        connectionRepository.save(mutualConnection);
    }

    private Set<UUID> getExistingConnectionIds(UUID userId) {
        return connectionRepository.findAcceptedConnectionsByUserId(userId)
                .stream()
                .map(conn -> conn.getConnectedUserId().equals(userId) ? 
                        conn.getUserId() : conn.getConnectedUserId())
                .collect(Collectors.toSet());
    }

    private List<FriendSuggestion> getMutualFriendSuggestions(UUID userId, Set<UUID> existingConnections) {
        List<MutualFriendResult> mutualFriends = connectionRepository.findMutualFriends(userId, 20);
        
        return mutualFriends.stream()
                .filter(mf -> !existingConnections.contains(mf.getUserId()))
                .map(mf -> FriendSuggestion.builder()
                        .userId(mf.getUserId())
                        .suggestionReason("You have " + mf.getMutualCount() + " mutual friends")
                        .relevanceScore(calculateMutualFriendScore(mf.getMutualCount()))
                        .suggestionType(SuggestionType.MUTUAL_FRIENDS)
                        .build())
                .collect(Collectors.toList());
    }

    private List<FriendSuggestion> getPaymentHistorySuggestions(UUID userId, Set<UUID> existingConnections) {
        List<UUID> paymentUsers = paymentRepository.findFrequentPaymentPartners(userId, 10);
        
        return paymentUsers.stream()
                .filter(id -> !existingConnections.contains(id))
                .map(id -> FriendSuggestion.builder()
                        .userId(id)
                        .suggestionReason("You've made payments with this person")
                        .relevanceScore(75.0)
                        .suggestionType(SuggestionType.PAYMENT_HISTORY)
                        .build())
                .collect(Collectors.toList());
    }

    private Double calculateMutualFriendScore(int mutualCount) {
        return Math.min(95.0, 50.0 + (mutualCount * 5.0));
    }

    // Placeholder implementations for complex operations
    /**
     * Get friend suggestions based on contact book integration
     * PRIVACY-FIRST: Requires explicit user consent for contact access
     */
    private List<FriendSuggestion> getContactBookSuggestions(UUID userId, Set<UUID> existingConnections) {
        try {
            // Check if user has granted contact book access permission
            PrivacySettings privacy = privacyRepository.findByUserId(userId).orElse(null);
            if (privacy == null || !privacy.isContactBookSharingEnabled()) {
                log.debug("Contact book sharing not enabled for user: {}", userId);
                return new ArrayList<>();
            }
            
            // Get user's uploaded contacts (phone numbers, emails)
            UserProfile profile = profileRepository.findById(userId).orElse(null);
            if (profile == null || profile.getContactBook() == null) {
                return new ArrayList<>();
            }
            
            // Find platform users matching contact information
            List<String> contactPhones = profile.getContactBook().getPhoneNumbers();
            List<String> contactEmails = profile.getContactBook().getEmails();
            
            List<UUID> matchedUsers = profileRepository
                .findByPhoneNumberInOrEmailIn(contactPhones, contactEmails).stream()
                .map(UserProfile::getUserId)
                .filter(id -> !existingConnections.contains(id))
                .filter(id -> !id.equals(userId))
                .limit(10)
                .collect(Collectors.toList());
            
            return matchedUsers.stream()
                .map(id -> FriendSuggestion.builder()
                    .userId(id)
                    .suggestionReason("From your contacts")
                    .relevanceScore(85.0)
                    .suggestionType(SuggestionType.CONTACT_BOOK)
                    .build())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get contact book suggestions for user: {}", userId, e);
            return new ArrayList<>();
        }
    }
    /**
     * Get friend suggestions based on geographic proximity
     * PRIVACY-FIRST: Requires location sharing consent
     */
    private List<FriendSuggestion> getLocationBasedSuggestions(UUID userId, Set<UUID> existingConnections) {
        try {
            // Check if user has enabled location-based suggestions
            PrivacySettings privacy = privacyRepository.findByUserId(userId).orElse(null);
            if (privacy == null || !privacy.isLocationSharingEnabled()) {
                log.debug("Location sharing not enabled for user: {}", userId);
                return new ArrayList<>();
            }
            
            // Get user's current location
            UserProfile profile = profileRepository.findById(userId).orElse(null);
            if (profile == null || profile.getLocation() == null) {
                return new ArrayList<>();
            }
            
            Double latitude = profile.getLocation().getLatitude();
            Double longitude = profile.getLocation().getLongitude();
            
            // Find users within 50km radius who also have location sharing enabled
            List<UUID> nearbyUsers = profileRepository
                .findNearbyUsers(latitude, longitude, 50.0) // 50km radius
                .stream()
                .map(UserProfile::getUserId)
                .filter(id -> !existingConnections.contains(id))
                .filter(id -> !id.equals(userId))
                .limit(10)
                .collect(Collectors.toList());
            
            return nearbyUsers.stream()
                .map(id -> FriendSuggestion.builder()
                    .userId(id)
                    .suggestionReason("Lives nearby")
                    .relevanceScore(70.0)
                    .suggestionType(SuggestionType.LOCATION)
                    .build())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get location-based suggestions for user: {}", userId, e);
            return new ArrayList<>();
        }
    }
    /**
     * Get friend suggestions based on shared interests and activities
     * ML-POWERED: Uses interest matching algorithm
     */
    private List<FriendSuggestion> getInterestBasedSuggestions(UUID userId, Set<UUID> existingConnections) {
        try {
            // Get user's interests and activities
            UserProfile profile = profileRepository.findById(userId).orElse(null);
            if (profile == null || profile.getInterests() == null || profile.getInterests().isEmpty()) {
                return new ArrayList<>();
            }
            
            List<String> userInterests = profile.getInterests();
            
            // Find users with similar interests
            List<UserProfile> similarUsers = profileRepository
                .findUsersWithSimilarInterests(userInterests, 3) // At least 3 matching interests
                .stream()
                .filter(p -> !existingConnections.contains(p.getUserId()))
                .filter(p -> !p.getUserId().equals(userId))
                .limit(10)
                .collect(Collectors.toList());
            
            return similarUsers.stream()
                .map(p -> {
                    // Calculate similarity score
                    long matchingInterests = p.getInterests().stream()
                        .filter(userInterests::contains)
                        .count();
                    
                    double similarityScore = Math.min(90.0, 50.0 + (matchingInterests * 10.0));
                    
                    return FriendSuggestion.builder()
                        .userId(p.getUserId())
                        .suggestionReason(matchingInterests + " shared interests")
                        .relevanceScore(similarityScore)
                        .suggestionType(SuggestionType.INTERESTS)
                        .build();
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get interest-based suggestions for user: {}", userId, e);
            return new ArrayList<>();
        }
    }
    /**
     * Cache friend suggestions for faster retrieval
     * PERFORMANCE: Reduces database load for frequently requested suggestions
     */
    @Async
    private void cacheFriendSuggestions(UUID userId, List<FriendSuggestion> suggestions) {
        try {
            log.debug("Caching {} friend suggestions for user: {}", suggestions.size(), userId);
            
            // Delete old suggestions
            suggestionRepository.deleteByUserId(userId);
            
            // Save new suggestions with timestamps
            LocalDateTime now = LocalDateTime.now();
            suggestions.forEach(suggestion -> {
                suggestion.setUserId(userId);
                suggestion.setCreatedAt(now);
                suggestion.setExpiresAt(now.plusDays(7)); // Expire after 7 days
            });
            
            suggestionRepository.saveAll(suggestions);
            
            log.info("Successfully cached {} friend suggestions for user: {}", suggestions.size(), userId);
            
        } catch (Exception e) {
            log.error("Failed to cache friend suggestions for user: {}", userId, e);
        }
    }
    /**
     * Update friend suggestions after connection request
     * BUSINESS LOGIC: Removes accepted connection from suggestions, adds mutual friends
     */
    @Async
    private void updateFriendSuggestions(UUID userId, UUID targetUserId) {
        try {
            log.debug("Updating friend suggestions after connection: {} -> {}", userId, targetUserId);
            
            // Remove target user from sender's suggestions
            suggestionRepository.deleteByUserIdAndSuggestedUserId(userId, targetUserId);
            
            // Remove sender from target's suggestions
            suggestionRepository.deleteByUserIdAndSuggestedUserId(targetUserId, userId);
            
            // Get mutual friends
            List<UUID> senderFriends = connectionRepository.getConnectedUserIds(userId);
            List<UUID> targetFriends = connectionRepository.getConnectedUserIds(targetUserId);
            
            Set<UUID> mutualFriends = new HashSet<>(senderFriends);
            mutualFriends.retainAll(targetFriends);
            
            // Update suggestions with updated mutual friend counts
            updateMutualFriendSuggestions(userId, targetUserId);
            
            log.info("Updated friend suggestions for users: {} and {} (mutual friends: {})", 
                    userId, targetUserId, mutualFriends.size());
            
        } catch (Exception e) {
            log.error("Failed to update friend suggestions for users: {} and {}", userId, targetUserId, e);
        }
    }
    /**
     * Update mutual friend suggestions for both users
     * ALGORITHM: Finds common friends and increases suggestion scores
     */
    @Async
    private void updateMutualFriendSuggestions(UUID userId1, UUID userId2) {
        try {
            log.debug("Updating mutual friend suggestions for users: {} and {}", userId1, userId2);
            
            // Get friends of both users
            List<UUID> user1Friends = connectionRepository.getConnectedUserIds(userId1);
            List<UUID> user2Friends = connectionRepository.getConnectedUserIds(userId2);
            
            // Find mutual friends
            Set<UUID> mutualFriends = new HashSet<>(user1Friends);
            mutualFriends.retainAll(user2Friends);
            
            // For each friend of user1, check if they could be friends with user2
            user1Friends.stream()
                .filter(friendId -> !connectionRepository.existsConnection(friendId, userId2))
                .filter(friendId -> !friendId.equals(userId2))
                .forEach(friendId -> {
                    // Check if suggestion already exists
                    Optional<FriendSuggestion> existing = suggestionRepository
                        .findByUserIdAndSuggestedUserId(friendId, userId2);
                    
                    if (existing.isPresent()) {
                        // Increase relevance score
                        FriendSuggestion suggestion = existing.get();
                        suggestion.setRelevanceScore(Math.min(95.0, suggestion.getRelevanceScore() + 10.0));
                        suggestionRepository.save(suggestion);
                    } else {
                        // Create new suggestion
                        FriendSuggestion newSuggestion = FriendSuggestion.builder()
                            .userId(friendId)
                            .suggestedUserId(userId2)
                            .suggestionReason("Mutual friend with " + user1Friends.size() + " connections")
                            .relevanceScore(calculateMutualFriendScore(1))
                            .suggestionType(SuggestionType.MUTUAL_FRIENDS)
                            .createdAt(LocalDateTime.now())
                            .expiresAt(LocalDateTime.now().plusDays(14))
                            .build();
                        suggestionRepository.save(newSuggestion);
                    }
                });
            
            log.info("Updated mutual friend suggestions for users: {} and {}", userId1, userId2);
            
        } catch (Exception e) {
            log.error("Failed to update mutual friend suggestions for users: {} and {}", userId1, userId2, e);
        }
    }
    /**
     * Update connection strength based on interactions
     * ANALYTICS: Tracks interaction frequency and recency
     */
    @Async
    private void updateConnectionStrength(SocialConnection connection) {
        try {
            log.debug("Updating connection strength for: {}", connection.getId());
            
            // Get interaction statistics
            int messageCount = activityRepository.countMessages(connection.getUserId(), connection.getConnectedUserId());
            int paymentCount = paymentRepository.countPayments(connection.getUserId(), connection.getConnectedUserId());
            int sharedGroupCount = groupRepository.countSharedGroups(connection.getUserId(), connection.getConnectedUserId());
            
            // Calculate strength score (0-100)
            double strengthScore = 0.0;
            
            // Messages contribute up to 40 points
            strengthScore += Math.min(40, messageCount * 2);
            
            // Payments contribute up to 30 points
            strengthScore += Math.min(30, paymentCount * 5);
            
            // Shared groups contribute up to 20 points
            strengthScore += Math.min(20, sharedGroupCount * 10);
            
            // Recency factor (10 points)
            LocalDateTime lastInteraction = activityRepository
                .findLastInteraction(connection.getUserId(), connection.getConnectedUserId());
            
            if (lastInteraction != null) {
                long daysSinceInteraction = ChronoUnit.DAYS.between(lastInteraction, LocalDateTime.now());
                if (daysSinceInteraction < 7) {
                    strengthScore += 10; // Very recent
                } else if (daysSinceInteraction < 30) {
                    strengthScore += 5; // Recent
                }
            }
            
            // Update connection
            connection.setStrengthScore(strengthScore);
            connection.setLastUpdated(LocalDateTime.now());
            connectionRepository.save(connection);
            
            log.info("Updated connection strength for: {} - Score: {}", connection.getId(), strengthScore);
            
        } catch (Exception e) {
            log.error("Failed to update connection strength for: {}", connection.getId(), e);
        }
    }

    // Notification methods
    /**
     * Send notification when connection request is sent
     * CUSTOMER EXPERIENCE: Real-time notification delivery
     */
    @Async
    private void sendConnectionRequestNotification(SocialConnection connection) {
        try {
            log.debug("Sending connection request notification: {}", connection.getId());
            
            // Get user profile information
            UserProfile senderProfile = profileRepository.findById(connection.getUserId())
                .orElse(null);
            
            String senderName = senderProfile != null ? senderProfile.getDisplayName() : "Someone";
            
            // Create notification payload
            Map<String, Object> notificationData = Map.of(
                "type", "CONNECTION_REQUEST",
                "connectionId", connection.getId().toString(),
                "fromUserId", connection.getUserId().toString(),
                "fromUserName", senderName,
                "message", connection.getRequestMessage() != null ? connection.getRequestMessage() : "",
                "timestamp", LocalDateTime.now().toString(),
                "actionUrl", "/social/connections/requests/" + connection.getId()
            );
            
            // Publish to Kafka for notification service to handle
            kafkaTemplate.send("social-notification-events", 
                connection.getConnectedUserId().toString(), 
                notificationData);
            
            log.info("Connection request notification sent: {} -> {}", 
                connection.getUserId(), connection.getConnectedUserId());
            
        } catch (Exception e) {
            log.error("Failed to send connection request notification for: {}", connection.getId(), e);
        }
    }
    /**
     * Send notification when connection request is responded to
     * CUSTOMER EXPERIENCE: Notify sender of acceptance/rejection
     */
    @Async
    private void sendConnectionResponseNotification(SocialConnection connection, ConnectionResponse.Decision decision) {
        try {
            log.debug("Sending connection response notification: {} - Decision: {}", connection.getId(), decision);
            
            // Get user profile information
            UserProfile responderProfile = profileRepository.findById(connection.getConnectedUserId())
                .orElse(null);
            
            String responderName = responderProfile != null ? responderProfile.getDisplayName() : "Someone";
            
            // Create notification payload
            Map<String, Object> notificationData = Map.of(
                "type", decision == ConnectionResponse.Decision.ACCEPT ? 
                    "CONNECTION_ACCEPTED" : "CONNECTION_REJECTED",
                "connectionId", connection.getId().toString(),
                "fromUserId", connection.getConnectedUserId().toString(),
                "fromUserName", responderName,
                "decision", decision.toString(),
                "timestamp", LocalDateTime.now().toString(),
                "actionUrl", decision == ConnectionResponse.Decision.ACCEPT ? 
                    "/social/connections/" + connection.getConnectedUserId() : null
            );
            
            // Publish to Kafka for notification service to handle
            kafkaTemplate.send("social-notification-events", 
                connection.getUserId().toString(), 
                notificationData);
            
            log.info("Connection response notification sent: {} - Decision: {}", 
                connection.getId(), decision);
            
        } catch (Exception e) {
            log.error("Failed to send connection response notification for: {}", connection.getId(), e);
        }
    }
    /**
     * Send group invitations to multiple members
     * BATCH OPERATION: Efficient multi-user notification
     */
    @Async
    private void sendGroupInvitations(SocialGroup group, List<UUID> memberIds) {
        try {
            log.debug("Sending group invitations for group: {} to {} members", group.getId(), memberIds.size());
            
            // Get creator profile information
            UserProfile creatorProfile = profileRepository.findById(group.getCreatedBy())
                .orElse(null);
            
            String creatorName = creatorProfile != null ? creatorProfile.getDisplayName() : "Someone";
            
            // Send invitation to each member
            for (UUID memberId : memberIds) {
                try {
                    // Create notification payload
                    Map<String, Object> notificationData = Map.of(
                        "type", "GROUP_INVITATION",
                        "groupId", group.getId().toString(),
                        "groupName", group.getName(),
                        "groupDescription", group.getDescription() != null ? group.getDescription() : "",
                        "fromUserId", group.getCreatedBy().toString(),
                        "fromUserName", creatorName,
                        "timestamp", LocalDateTime.now().toString(),
                        "actionUrl", "/social/groups/invitations/" + group.getId()
                    );
                    
                    // Publish to Kafka for notification service to handle
                    kafkaTemplate.send("social-notification-events", 
                        memberId.toString(), 
                        notificationData);
                    
                } catch (Exception e) {
                    log.error("Failed to send group invitation to member: {} for group: {}", 
                        memberId, group.getId(), e);
                }
            }
            
            log.info("Group invitations sent for group: {} to {} members", group.getId(), memberIds.size());
            
        } catch (Exception e) {
            log.error("Failed to send group invitations for group: {}", group.getId(), e);
        }
    }
    /**
     * Send notification to group admins when someone requests to join
     * ADMIN WORKFLOW: Notify admins of pending approvals
     */
    @Async
    private void sendJoinRequestNotification(SocialGroup group, GroupMembership membership) {
        try {
            log.debug("Sending join request notification for group: {}", group.getId());
            
            // Get requester profile information
            UserProfile requesterProfile = profileRepository.findById(membership.getUserId())
                .orElse(null);
            
            String requesterName = requesterProfile != null ? requesterProfile.getDisplayName() : "Someone";
            
            // Get all admin members
            List<GroupMembership> admins = groupRepository.getAdminMembers(group.getId());
            
            // Create notification payload
            Map<String, Object> notificationData = Map.of(
                "type", "GROUP_JOIN_REQUEST",
                "groupId", group.getId().toString(),
                "groupName", group.getName(),
                "membershipId", membership.getId().toString(),
                "fromUserId", membership.getUserId().toString(),
                "fromUserName", requesterName,
                "timestamp", LocalDateTime.now().toString(),
                "actionUrl", "/social/groups/" + group.getId() + "/requests"
            );
            
            // Send notification to all admins
            for (GroupMembership admin : admins) {
                try {
                    kafkaTemplate.send("social-notification-events", 
                        admin.getUserId().toString(), 
                        notificationData);
                } catch (Exception e) {
                    log.error("Failed to send join request notification to admin: {} for group: {}", 
                        admin.getUserId(), group.getId(), e);
                }
            }
            
            log.info("Join request notification sent for group: {} to {} admins", 
                group.getId(), admins.size());
            
        } catch (Exception e) {
            log.error("Failed to send join request notification for group: {}", group.getId(), e);
        }
    }
    /**
     * Send notification when a member joins the group
     * GROUP ACTIVITY: Notify all members of new member
     */
    @Async
    private void sendMemberJoinedNotification(SocialGroup group, GroupMembership membership) {
        try {
            log.debug("Sending member joined notification for group: {}", group.getId());
            
            // Get new member profile information
            UserProfile newMemberProfile = profileRepository.findById(membership.getUserId())
                .orElse(null);
            
            String newMemberName = newMemberProfile != null ? newMemberProfile.getDisplayName() : "Someone";
            
            // Get all active group members (except the new member)
            List<GroupMembership> activeMembers = groupRepository.getActiveMembers(group.getId()).stream()
                .filter(m -> !m.getUserId().equals(membership.getUserId()))
                .collect(Collectors.toList());
            
            // Create notification payload
            Map<String, Object> notificationData = Map.of(
                "type", "GROUP_MEMBER_JOINED",
                "groupId", group.getId().toString(),
                "groupName", group.getName(),
                "newMemberId", membership.getUserId().toString(),
                "newMemberName", newMemberName,
                "memberCount", activeMembers.size() + 1,
                "timestamp", LocalDateTime.now().toString(),
                "actionUrl", "/social/groups/" + group.getId()
            );
            
            // Send notification to all existing members
            for (GroupMembership member : activeMembers) {
                try {
                    kafkaTemplate.send("social-notification-events", 
                        member.getUserId().toString(), 
                        notificationData);
                } catch (Exception e) {
                    log.error("Failed to send member joined notification to: {} for group: {}", 
                        member.getUserId(), group.getId(), e);
                }
            }
            
            log.info("Member joined notification sent for group: {} to {} members", 
                group.getId(), activeMembers.size());
            
        } catch (Exception e) {
            log.error("Failed to send member joined notification for group: {}", group.getId(), e);
        }
    }

    // Event publishing
    private void publishConnectionEvent(String eventType, SocialConnection connection) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "connectionId", connection.getId(),
                "userId", connection.getUserId(),
                "connectedUserId", connection.getConnectedUserId(),
                "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("social-connection-events", connection.getId().toString(), event);
    }

    private void publishGroupEvent(String eventType, SocialGroup group) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "groupId", group.getId(),
                "createdBy", group.getCreatedBy(),
                "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("social-group-events", group.getId().toString(), event);
    }

    // Response mapping methods
    private ConnectionResponse mapToConnectionResponse(SocialConnection connection) {
        return ConnectionResponse.builder()
                .connectionId(connection.getId())
                .status(connection.getStatus())
                .connectedUserId(connection.getConnectedUserId())
                .requestedAt(connection.getRequestedAt())
                .build();
    }

    private SocialGroupResponse mapToGroupResponse(SocialGroup group) {
        return SocialGroupResponse.builder()
                .groupId(group.getId())
                .groupName(group.getGroupName())
                .description(group.getDescription())
                .privacy(group.getPrivacy())
                .currentMembers(group.getCurrentMembers())
                .createdAt(group.getCreatedAt())
                .build();
    }

    // Enum definitions and data classes
    public enum ConnectionStatus { PENDING, ACCEPTED, REJECTED, BLOCKED }
    public enum ConnectionType { FRIEND, FAMILY, COLLEAGUE, BUSINESS }
    public enum GroupRole { MEMBER, MODERATOR, ADMIN }
    public enum GroupMembershipStatus { PENDING, ACTIVE, SUSPENDED }
    public enum SuggestionType { MUTUAL_FRIENDS, CONTACT_BOOK, PAYMENT_HISTORY, LOCATION, INTERESTS }
    public enum AnalyticsPeriod { WEEK, MONTH, QUARTER, YEAR }
}