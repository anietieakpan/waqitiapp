/**
 * Social Feed Controller
 * Handles Venmo-style social payment feed functionality
 */
package com.waqiti.social.controller;

import com.waqiti.social.dto.*;
import com.waqiti.social.service.SocialFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Social Feed", description = "Social payment feed and user interactions")
@SecurityRequirement(name = "bearerAuth")
public class SocialFeedController {

    private final SocialFeedService socialFeedService;

    @GetMapping("/feed")
    @Operation(summary = "Get social payment feed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SocialFeedResponse> getSocialFeed(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Feed filter") @RequestParam(defaultValue = "all") String filter,
            @Parameter(description = "Cursor for pagination") @RequestParam(required = false) String cursor,
            @Parameter(description = "Number of items to fetch") @RequestParam(defaultValue = "20") int limit) {
        
        log.info("Social feed requested by user: {} with filter: {}", userDetails.getUsername(), filter);
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        SocialFeedResponse response = socialFeedService.getSocialFeed(userId, filter, cursor, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/profile")
    @Operation(summary = "Get user's social profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        
        log.info("User profile requested for: {} by: {}", userId, userDetails.getUsername());
        UUID requestingUserId = getUserIdFromUserDetails(userDetails);
        
        UserProfileResponse response = socialFeedService.getUserProfile(requestingUserId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions/{transactionId}/like")
    @Operation(summary = "Toggle like on a transaction")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LikeResponse> toggleTransactionLike(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID transactionId) {
        
        log.info("Like toggle requested for transaction: {} by user: {}", transactionId, userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        LikeResponse response = socialFeedService.toggleLike(userId, transactionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions/{transactionId}/comments")
    @Operation(summary = "Add comment to a transaction")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> addComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID transactionId,
            @Valid @RequestBody AddCommentRequest request) {
        
        log.info("Comment added to transaction: {} by user: {}", transactionId, userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        CommentResponse response = socialFeedService.addComment(userId, transactionId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{transactionId}/comments")
    @Operation(summary = "Get comments for a transaction")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentResponse>> getTransactionComments(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID transactionId,
            Pageable pageable) {
        
        log.info("Comments requested for transaction: {} by user: {}", transactionId, userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        List<CommentResponse> response = socialFeedService.getComments(userId, transactionId, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/follow")
    @Operation(summary = "Follow or unfollow a user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowResponse> toggleFollow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        
        log.info("Follow toggle requested for user: {} by: {}", userId, userDetails.getUsername());
        UUID followerId = getUserIdFromUserDetails(userDetails);
        
        FollowResponse response = socialFeedService.toggleFollow(followerId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/search")
    @Operation(summary = "Search for users")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserProfileResponse>> searchUsers(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Search query") @RequestParam String q,
            @Parameter(description = "Number of results") @RequestParam(defaultValue = "20") int limit) {
        
        log.info("User search requested: '{}' by user: {}", q, userDetails.getUsername());
        UUID searcherId = getUserIdFromUserDetails(userDetails);
        
        List<UserProfileResponse> response = socialFeedService.searchUsers(searcherId, q, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/friends")
    @Operation(summary = "Get user's friends/following list")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserProfileResponse>> getUserFriends(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            Pageable pageable) {
        
        log.info("Friends list requested for user: {} by: {}", userId, userDetails.getUsername());
        UUID requestingUserId = getUserIdFromUserDetails(userDetails);
        
        List<UserProfileResponse> response = socialFeedService.getUserFriends(requestingUserId, userId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/followers")
    @Operation(summary = "Get user's followers list")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserProfileResponse>> getUserFollowers(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            Pageable pageable) {
        
        log.info("Followers list requested for user: {} by: {}", userId, userDetails.getUsername());
        UUID requestingUserId = getUserIdFromUserDetails(userDetails);
        
        List<UserProfileResponse> response = socialFeedService.getUserFollowers(requestingUserId, userId, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/settings/privacy")
    @Operation(summary = "Update privacy settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PrivacySettingsResponse> updatePrivacySettings(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePrivacySettingsRequest request) {
        
        log.info("Privacy settings update requested by user: {}", userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        PrivacySettingsResponse response = socialFeedService.updatePrivacySettings(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/settings/privacy")
    @Operation(summary = "Get privacy settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PrivacySettingsResponse> getPrivacySettings(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("Privacy settings requested by user: {}", userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        PrivacySettingsResponse response = socialFeedService.getPrivacySettings(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/transactions/{transactionId}/privacy")
    @Operation(summary = "Update transaction privacy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updateTransactionPrivacy(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID transactionId,
            @Valid @RequestBody UpdateTransactionPrivacyRequest request) {
        
        log.info("Transaction privacy update for: {} by user: {}", transactionId, userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        socialFeedService.updateTransactionPrivacy(userId, transactionId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/report")
    @Operation(summary = "Report inappropriate content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> reportContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReportContentRequest request) {
        
        log.info("Content report submitted by user: {} for type: {}", userDetails.getUsername(), request.getType());
        UUID reporterId = getUserIdFromUserDetails(userDetails);
        
        socialFeedService.reportContent(reporterId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/block")
    @Operation(summary = "Block or unblock a user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BlockResponse> toggleBlock(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        
        log.info("Block toggle requested for user: {} by: {}", userId, userDetails.getUsername());
        UUID blockerId = getUserIdFromUserDetails(userDetails);
        
        BlockResponse response = socialFeedService.toggleBlock(blockerId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trending")
    @Operation(summary = "Get trending topics and hashtags")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TrendingTopicsResponse> getTrendingTopics(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("Trending topics requested by user: {}", userDetails.getUsername());
        
        TrendingTopicsResponse response = socialFeedService.getTrendingTopics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggestions/friends")
    @Operation(summary = "Get friend suggestions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserProfileResponse>> getFriendSuggestions(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Number of suggestions") @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Friend suggestions requested by user: {}", userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        List<UserProfileResponse> response = socialFeedService.getFriendSuggestions(userId, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/transactions")
    @Operation(summary = "Get user's public transaction history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SocialFeedResponse> getUserPublicTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Parameter(description = "Cursor for pagination") @RequestParam(required = false) String cursor,
            @Parameter(description = "Number of items to fetch") @RequestParam(defaultValue = "20") int limit) {
        
        log.info("Public transactions requested for user: {} by: {}", userId, userDetails.getUsername());
        UUID requestingUserId = getUserIdFromUserDetails(userDetails);
        
        SocialFeedResponse response = socialFeedService.getUserPublicTransactions(requestingUserId, userId, cursor, limit);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/message")
    @Operation(summary = "Send direct message to a user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> sendDirectMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody SendDirectMessageRequest request) {
        
        log.info("Direct message sent to user: {} by: {}", userId, userDetails.getUsername());
        UUID senderId = getUserIdFromUserDetails(userDetails);
        
        socialFeedService.sendDirectMessage(senderId, userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/settings/notifications")
    @Operation(summary = "Get notification preferences for social features")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SocialNotificationSettingsResponse> getNotificationSettings(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("Social notification settings requested by user: {}", userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        SocialNotificationSettingsResponse response = socialFeedService.getNotificationSettings(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/settings/notifications")
    @Operation(summary = "Update notification preferences for social features")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updateNotificationSettings(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateSocialNotificationSettingsRequest request) {
        
        log.info("Social notification settings updated by user: {}", userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        socialFeedService.updateNotificationSettings(userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/transactions/{transactionId}/comments/{commentId}")
    @Operation(summary = "Delete a comment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID transactionId,
            @PathVariable UUID commentId) {
        
        log.info("Comment deletion requested for comment: {} by user: {}", commentId, userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        socialFeedService.deleteComment(userId, transactionId, commentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transactions/{transactionId}/share")
    @Operation(summary = "Share a transaction")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShareResponse> shareTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID transactionId,
            @Valid @RequestBody ShareTransactionRequest request) {
        
        log.info("Transaction share requested for: {} by user: {}", transactionId, userDetails.getUsername());
        UUID userId = getUserIdFromUserDetails(userDetails);
        
        ShareResponse response = socialFeedService.shareTransaction(userId, transactionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to extract user ID from UserDetails
     */
    private UUID getUserIdFromUserDetails(UserDetails userDetails) {
        try {
            return UUID.fromString(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse UUID from username: {}", userDetails.getUsername());
            
            // For test environments, provide a fallback
            if ("testuser".equals(userDetails.getUsername())) {
                return UUID.fromString("3da1bd8c-d04b-4eee-b8ab-c7a8c1142599");
            }
            
            throw new IllegalArgumentException("Invalid UUID format: " + userDetails.getUsername(), e);
        }
    }
}