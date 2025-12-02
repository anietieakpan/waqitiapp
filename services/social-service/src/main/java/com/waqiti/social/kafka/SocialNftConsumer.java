package com.waqiti.social.kafka;

import com.waqiti.social.event.SocialNftEvent;
import com.waqiti.social.service.GroupService;
import com.waqiti.social.service.NftService;
import com.waqiti.social.service.SocialNotificationService;
import com.waqiti.social.service.RewardService;
import com.waqiti.social.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for social and NFT events
 * Handles: social-group-events, nft-events, social-notifications, rewards-events,
 * gamification-events, community-events, social-feed-updates, achievement-unlocked,
 * nft-minting, nft-transfers, social-interactions, group-payments, social-campaigns,
 * referral-events, loyalty-events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialNftConsumer {

    private final GroupService groupService;
    private final NftService nftService;
    private final SocialNotificationService notificationService;
    private final RewardService rewardService;
    private final GameService gameService;

    @KafkaListener(topics = {"social-group-events", "nft-events", "social-notifications", "rewards-events",
                             "gamification-events", "community-events", "social-feed-updates", "achievement-unlocked",
                             "nft-minting", "nft-transfers", "social-interactions", "group-payments",
                             "social-campaigns", "referral-events", "loyalty-events"}, 
                   groupId = "social-nft-processor")
    @Transactional
    public void processSocialNftEvent(@Payload SocialNftEvent event,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     Acknowledgment acknowledgment) {
        try {
            log.info("Processing social/NFT event: {} - Type: {} - User: {} - Group: {}", 
                    event.getEventId(), event.getEventType(), event.getUserId(), event.getGroupId());
            
            // Process based on topic
            switch (topic) {
                case "social-group-events" -> handleGroupEvent(event);
                case "nft-events" -> handleNftEvent(event);
                case "social-notifications" -> handleSocialNotification(event);
                case "rewards-events" -> handleRewardEvent(event);
                case "gamification-events" -> handleGamificationEvent(event);
                case "community-events" -> handleCommunityEvent(event);
                case "social-feed-updates" -> handleFeedUpdate(event);
                case "achievement-unlocked" -> handleAchievement(event);
                case "nft-minting" -> handleNftMinting(event);
                case "nft-transfers" -> handleNftTransfer(event);
                case "social-interactions" -> handleSocialInteraction(event);
                case "group-payments" -> handleGroupPayment(event);
                case "social-campaigns" -> handleSocialCampaign(event);
                case "referral-events" -> handleReferralEvent(event);
                case "loyalty-events" -> handleLoyaltyEvent(event);
            }
            
            // Update social metrics
            updateSocialMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed social/NFT event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to process social/NFT event {}: {}", 
                    event.getEventId(), e.getMessage(), e);
            throw new RuntimeException("Social/NFT processing failed", e);
        }
    }

    private void handleGroupEvent(SocialNftEvent event) {
        String groupId = event.getGroupId();
        String eventType = event.getGroupEventType();
        
        switch (eventType) {
            case "GROUP_CREATED" -> {
                groupService.createGroup(
                    groupId,
                    event.getGroupName(),
                    event.getGroupType(),
                    event.getCreatorId(),
                    event.getGroupSettings()
                );
                
                // Send invitations
                if (event.getInitialMembers() != null) {
                    groupService.sendInvitations(
                        groupId,
                        event.getInitialMembers(),
                        event.getCreatorId()
                    );
                }
            }
            case "MEMBER_JOINED" -> {
                groupService.addMember(
                    groupId,
                    event.getUserId(),
                    event.getMemberRole(),
                    event.getJoinMethod()
                );
                
                // Notify group
                notificationService.notifyGroupMemberJoined(
                    groupId,
                    event.getUserId()
                );
            }
            case "MEMBER_LEFT" -> {
                groupService.removeMember(
                    groupId,
                    event.getUserId(),
                    event.getLeaveReason()
                );
            }
            case "GROUP_ACTIVITY" -> {
                groupService.recordActivity(
                    groupId,
                    event.getActivityType(),
                    event.getActivityData(),
                    event.getUserId()
                );
            }
            case "GROUP_SETTINGS_UPDATED" -> {
                groupService.updateSettings(
                    groupId,
                    event.getUpdatedSettings(),
                    event.getUpdatedBy()
                );
            }
        }
        
        // Update group statistics
        groupService.updateStatistics(groupId);
    }

    private void handleNftEvent(SocialNftEvent event) {
        String nftId = event.getNftId();
        String eventType = event.getNftEventType();
        
        switch (eventType) {
            case "NFT_CREATED" -> {
                nftService.createNft(
                    nftId,
                    event.getNftMetadata(),
                    event.getCreatorId(),
                    event.getNftType()
                );
            }
            case "NFT_LISTED" -> {
                nftService.listForSale(
                    nftId,
                    event.getListingPrice(),
                    event.getListingCurrency(),
                    event.getListingDuration()
                );
            }
            case "NFT_SOLD" -> {
                nftService.processSale(
                    nftId,
                    event.getBuyerId(),
                    event.getSalePrice(),
                    event.getTransactionId()
                );
                
                // Transfer ownership
                nftService.transferOwnership(
                    nftId,
                    event.getSellerId(),
                    event.getBuyerId()
                );
                
                // Distribute royalties
                if (event.hasRoyalties()) {
                    nftService.distributeRoyalties(
                        nftId,
                        event.getSalePrice(),
                        event.getRoyaltyRecipients()
                    );
                }
            }
            case "NFT_BURNED" -> {
                nftService.burnNft(
                    nftId,
                    event.getBurnReason(),
                    event.getBurnedBy()
                );
            }
        }
    }

    private void handleSocialNotification(SocialNftEvent event) {
        // Create notification
        String notificationId = notificationService.createNotification(
            event.getNotificationType(),
            event.getUserId(),
            event.getNotificationContent(),
            event.getPriority()
        );
        
        // Determine delivery channels
        List<String> channels = notificationService.determineChannels(
            event.getUserId(),
            event.getNotificationType(),
            event.getPriority()
        );
        
        // Send through channels
        for (String channel : channels) {
            switch (channel) {
                case "PUSH" -> notificationService.sendPushNotification(
                    event.getUserId(),
                    event.getNotificationContent()
                );
                case "IN_APP" -> notificationService.sendInAppNotification(
                    event.getUserId(),
                    notificationId,
                    event.getNotificationContent()
                );
                case "EMAIL" -> notificationService.sendEmailNotification(
                    event.getUserId(),
                    event.getNotificationContent()
                );
                case "SMS" -> notificationService.sendSmsNotification(
                    event.getUserId(),
                    event.getNotificationContent()
                );
            }
        }
        
        // Track delivery
        notificationService.trackDelivery(
            notificationId,
            channels,
            LocalDateTime.now()
        );
    }

    private void handleRewardEvent(SocialNftEvent event) {
        String rewardType = event.getRewardType();
        
        switch (rewardType) {
            case "POINTS" -> {
                rewardService.awardPoints(
                    event.getUserId(),
                    event.getPointsAmount(),
                    event.getPointsReason(),
                    event.getPointsCategory()
                );
            }
            case "BADGE" -> {
                rewardService.awardBadge(
                    event.getUserId(),
                    event.getBadgeId(),
                    event.getBadgeLevel(),
                    event.getAchievementId()
                );
            }
            case "CASHBACK" -> {
                rewardService.processCashback(
                    event.getUserId(),
                    event.getCashbackAmount(),
                    event.getTransactionId(),
                    event.getCashbackRate()
                );
            }
            case "VOUCHER" -> {
                String voucherId = rewardService.issueVoucher(
                    event.getUserId(),
                    event.getVoucherType(),
                    event.getVoucherValue(),
                    event.getVoucherExpiry()
                );
                
                // Send voucher notification
                notificationService.sendVoucherNotification(
                    event.getUserId(),
                    voucherId,
                    event.getVoucherDetails()
                );
            }
            case "TIER_UPGRADE" -> {
                rewardService.upgradeTier(
                    event.getUserId(),
                    event.getNewTier(),
                    event.getTierBenefits()
                );
            }
        }
        
        // Update reward balance
        rewardService.updateBalance(event.getUserId());
    }

    private void handleGamificationEvent(SocialNftEvent event) {
        String gameType = event.getGameType();
        
        switch (gameType) {
            case "CHALLENGE_STARTED" -> {
                gameService.startChallenge(
                    event.getChallengeId(),
                    event.getUserId(),
                    event.getChallengeType(),
                    event.getChallengeGoal()
                );
            }
            case "CHALLENGE_COMPLETED" -> {
                gameService.completeChallenge(
                    event.getChallengeId(),
                    event.getUserId(),
                    event.getCompletionTime(),
                    event.getScore()
                );
                
                // Award challenge rewards
                rewardService.awardChallengeRewards(
                    event.getUserId(),
                    event.getChallengeId(),
                    event.getChallengeRewards()
                );
            }
            case "LEADERBOARD_UPDATE" -> {
                gameService.updateLeaderboard(
                    event.getLeaderboardId(),
                    event.getUserId(),
                    event.getScore(),
                    event.getRank()
                );
                
                // Check for rank changes
                if (event.hasRankChanged()) {
                    notificationService.sendRankChangeNotification(
                        event.getUserId(),
                        event.getOldRank(),
                        event.getNewRank()
                    );
                }
            }
            case "STREAK_UPDATE" -> {
                gameService.updateStreak(
                    event.getUserId(),
                    event.getStreakType(),
                    event.getStreakCount(),
                    event.getStreakMilestone()
                );
            }
            case "QUEST_PROGRESS" -> {
                gameService.updateQuestProgress(
                    event.getQuestId(),
                    event.getUserId(),
                    event.getProgressPercentage(),
                    event.getCompletedSteps()
                );
            }
        }
    }

    private void handleCommunityEvent(SocialNftEvent event) {
        String communityEventType = event.getCommunityEventType();
        
        switch (communityEventType) {
            case "POST_CREATED" -> {
                groupService.createPost(
                    event.getPostId(),
                    event.getGroupId(),
                    event.getUserId(),
                    event.getPostContent(),
                    event.getPostType()
                );
                
                // Update feed
                updateFeeds(event.getGroupId(), event.getPostId());
            }
            case "COMMENT_ADDED" -> {
                groupService.addComment(
                    event.getCommentId(),
                    event.getPostId(),
                    event.getUserId(),
                    event.getCommentContent()
                );
                
                // Notify post owner
                notificationService.notifyNewComment(
                    event.getPostOwnerId(),
                    event.getCommentId()
                );
            }
            case "REACTION_ADDED" -> {
                groupService.addReaction(
                    event.getPostId(),
                    event.getUserId(),
                    event.getReactionType()
                );
            }
            case "POLL_CREATED" -> {
                groupService.createPoll(
                    event.getPollId(),
                    event.getGroupId(),
                    event.getPollQuestion(),
                    event.getPollOptions(),
                    event.getPollDuration()
                );
            }
            case "VOTE_CAST" -> {
                groupService.recordVote(
                    event.getPollId(),
                    event.getUserId(),
                    event.getVoteOption()
                );
            }
        }
    }

    private void handleFeedUpdate(SocialNftEvent event) {
        // Update user feed
        String userId = event.getUserId();
        String updateType = event.getFeedUpdateType();
        
        // Get feed items
        List<Map<String, Object>> feedItems = groupService.getFeedItems(
            userId,
            event.getFeedFilter(),
            event.getFeedLimit()
        );
        
        // Apply ranking algorithm
        List<Map<String, Object>> rankedItems = groupService.rankFeedItems(
            feedItems,
            event.getRankingAlgorithm()
        );
        
        // Update feed cache
        groupService.updateFeedCache(
            userId,
            rankedItems,
            LocalDateTime.now()
        );
        
        // Send real-time update if user is online
        if (notificationService.isUserOnline(userId)) {
            notificationService.sendRealtimeFeedUpdate(
                userId,
                rankedItems
            );
        }
    }

    private void handleAchievement(SocialNftEvent event) {
        String achievementId = event.getAchievementId();
        String userId = event.getUserId();
        
        // Record achievement
        gameService.unlockAchievement(
            userId,
            achievementId,
            event.getAchievementLevel(),
            LocalDateTime.now()
        );
        
        // Award achievement rewards
        Map<String, Object> rewards = event.getAchievementRewards();
        if (rewards != null) {
            rewardService.processAchievementRewards(
                userId,
                achievementId,
                rewards
            );
        }
        
        // Create achievement NFT if configured
        if (event.isNftReward()) {
            String nftId = nftService.mintAchievementNft(
                userId,
                achievementId,
                event.getAchievementMetadata()
            );
            
            // Transfer to user
            nftService.transferToUser(nftId, userId);
        }
        
        // Send celebration notification
        notificationService.sendAchievementNotification(
            userId,
            achievementId,
            event.getAchievementName()
        );
        
        // Update user profile
        gameService.updateUserProfile(
            userId,
            achievementId,
            event.getProfileBadge()
        );
    }

    private void handleNftMinting(SocialNftEvent event) {
        // Process NFT minting
        String mintRequestId = event.getMintRequestId();
        
        // Validate minting request
        if (!nftService.validateMintRequest(mintRequestId, event)) {
            nftService.rejectMintRequest(
                mintRequestId,
                "VALIDATION_FAILED"
            );
            return;
        }
        
        // Mint NFT
        String nftId = nftService.mintNft(
            event.getNftType(),
            event.getNftMetadata(),
            event.getCreatorId(),
            event.getMintingParams()
        );
        
        // Store on blockchain if configured
        if (event.isBlockchainEnabled()) {
            String txHash = nftService.storeOnBlockchain(
                nftId,
                event.getBlockchainNetwork(),
                event.getSmartContractAddress()
            );
            
            // Update NFT with blockchain details
            nftService.updateBlockchainDetails(
                nftId,
                txHash,
                event.getBlockchainNetwork()
            );
        }
        
        // Generate NFT certificate
        String certificateUrl = nftService.generateCertificate(
            nftId,
            event.getCertificateTemplate()
        );
        
        // Send minting confirmation
        notificationService.sendMintingConfirmation(
            event.getCreatorId(),
            nftId,
            certificateUrl
        );
    }

    private void handleNftTransfer(SocialNftEvent event) {
        String nftId = event.getNftId();
        String fromUserId = event.getFromUserId();
        String toUserId = event.getToUserId();
        
        // Validate transfer
        if (!nftService.canTransfer(nftId, fromUserId)) {
            log.error("Transfer not allowed: NFT {} from user {}", nftId, fromUserId);
            return;
        }
        
        // Execute transfer
        nftService.executeTransfer(
            nftId,
            fromUserId,
            toUserId,
            event.getTransferType()
        );
        
        // Update ownership records
        nftService.updateOwnership(
            nftId,
            toUserId,
            LocalDateTime.now()
        );
        
        // Record transfer history
        nftService.recordTransferHistory(
            nftId,
            fromUserId,
            toUserId,
            event.getTransferReason(),
            event.getTransferValue()
        );
        
        // Send transfer notifications
        notificationService.sendTransferNotification(
            fromUserId,
            toUserId,
            nftId,
            event.getTransferType()
        );
    }

    private void handleSocialInteraction(SocialNftEvent event) {
        String interactionType = event.getInteractionType();
        
        switch (interactionType) {
            case "FOLLOW" -> {
                groupService.followUser(
                    event.getFollowerId(),
                    event.getFollowedId()
                );
                
                // Send follow notification
                notificationService.sendFollowNotification(
                    event.getFollowedId(),
                    event.getFollowerId()
                );
            }
            case "MENTION" -> {
                groupService.recordMention(
                    event.getMentionedUserId(),
                    event.getMentioningUserId(),
                    event.getContextId(),
                    event.getContextType()
                );
                
                // Send mention notification
                notificationService.sendMentionNotification(
                    event.getMentionedUserId(),
                    event.getMentioningUserId(),
                    event.getContextId()
                );
            }
            case "SHARE" -> {
                groupService.shareContent(
                    event.getContentId(),
                    event.getSharerId(),
                    event.getSharePlatform(),
                    event.getShareMessage()
                );
            }
            case "INVITE" -> {
                groupService.sendInvite(
                    event.getInviterId(),
                    event.getInviteeContact(),
                    event.getInviteType(),
                    event.getInviteReward()
                );
            }
        }
        
        // Update interaction metrics
        groupService.updateInteractionMetrics(
            event.getUserId(),
            interactionType
        );
    }

    private void handleGroupPayment(SocialNftEvent event) {
        String paymentType = event.getGroupPaymentType();
        
        switch (paymentType) {
            case "SPLIT_BILL" -> {
                groupService.createSplitBill(
                    event.getBillId(),
                    event.getGroupId(),
                    event.getTotalAmount(),
                    event.getSplitMethod(),
                    event.getParticipants()
                );
            }
            case "GROUP_CONTRIBUTION" -> {
                groupService.processContribution(
                    event.getContributionId(),
                    event.getGroupId(),
                    event.getUserId(),
                    event.getContributionAmount()
                );
            }
            case "POOL_PAYOUT" -> {
                groupService.processPoolPayout(
                    event.getPoolId(),
                    event.getPayoutAmount(),
                    event.getRecipients(),
                    event.getPayoutMethod()
                );
            }
            case "GROUP_SAVINGS" -> {
                groupService.updateGroupSavings(
                    event.getGroupId(),
                    event.getUserId(),
                    event.getSavingsAmount(),
                    event.getSavingsType()
                );
            }
        }
    }

    private void handleSocialCampaign(SocialNftEvent event) {
        String campaignId = event.getCampaignId();
        String campaignType = event.getCampaignType();
        
        switch (campaignType) {
            case "CAMPAIGN_STARTED" -> {
                groupService.startCampaign(
                    campaignId,
                    event.getCampaignName(),
                    event.getCampaignGoal(),
                    event.getCampaignDuration(),
                    event.getCampaignRewards()
                );
            }
            case "CAMPAIGN_PARTICIPATION" -> {
                groupService.recordParticipation(
                    campaignId,
                    event.getUserId(),
                    event.getParticipationType(),
                    event.getParticipationData()
                );
            }
            case "CAMPAIGN_MILESTONE" -> {
                groupService.recordMilestone(
                    campaignId,
                    event.getMilestoneType(),
                    event.getMilestoneValue()
                );
                
                // Distribute milestone rewards
                if (event.hasMilestoneRewards()) {
                    rewardService.distributeMilestoneRewards(
                        campaignId,
                        event.getMilestoneRewards()
                    );
                }
            }
            case "CAMPAIGN_ENDED" -> {
                groupService.endCampaign(
                    campaignId,
                    event.getCampaignResults(),
                    event.getWinners()
                );
                
                // Distribute final rewards
                rewardService.distributeCampaignRewards(
                    campaignId,
                    event.getWinners(),
                    event.getFinalRewards()
                );
            }
        }
    }

    private void handleReferralEvent(SocialNftEvent event) {
        String referralType = event.getReferralType();
        
        switch (referralType) {
            case "REFERRAL_CREATED" -> {
                String referralCode = rewardService.createReferralCode(
                    event.getReferrerId(),
                    event.getReferralCampaignId(),
                    event.getReferralExpiry()
                );
                
                // Send referral code
                notificationService.sendReferralCode(
                    event.getReferrerId(),
                    referralCode,
                    event.getReferralIncentive()
                );
            }
            case "REFERRAL_USED" -> {
                rewardService.processReferral(
                    event.getReferralCode(),
                    event.getReferredUserId(),
                    event.getReferralValue()
                );
                
                // Award referrer bonus
                rewardService.awardReferrerBonus(
                    event.getReferrerId(),
                    event.getReferrerBonus()
                );
                
                // Award referred user bonus
                rewardService.awardReferredBonus(
                    event.getReferredUserId(),
                    event.getReferredBonus()
                );
            }
            case "REFERRAL_MILESTONE" -> {
                rewardService.processReferralMilestone(
                    event.getReferrerId(),
                    event.getReferralCount(),
                    event.getMilestoneReward()
                );
            }
        }
    }

    private void handleLoyaltyEvent(SocialNftEvent event) {
        String loyaltyType = event.getLoyaltyType();
        
        switch (loyaltyType) {
            case "POINTS_EARNED" -> {
                rewardService.addLoyaltyPoints(
                    event.getUserId(),
                    event.getPointsAmount(),
                    event.getEarningReason(),
                    event.getTransactionId()
                );
            }
            case "POINTS_REDEEMED" -> {
                rewardService.redeemLoyaltyPoints(
                    event.getUserId(),
                    event.getRedemptionAmount(),
                    event.getRedemptionType(),
                    event.getRedemptionDetails()
                );
            }
            case "TIER_PROGRESS" -> {
                rewardService.updateTierProgress(
                    event.getUserId(),
                    event.getCurrentTier(),
                    event.getProgressPercentage(),
                    event.getPointsToNextTier()
                );
            }
            case "BENEFIT_ACTIVATED" -> {
                rewardService.activateLoyaltyBenefit(
                    event.getUserId(),
                    event.getBenefitId(),
                    event.getBenefitType(),
                    event.getBenefitDuration()
                );
            }
        }
        
        // Update loyalty dashboard
        rewardService.updateLoyaltyDashboard(event.getUserId());
    }

    private void updateFeeds(String groupId, String postId) {
        // Get group members
        List<String> members = groupService.getGroupMembers(groupId);
        
        // Update feeds asynchronously
        CompletableFuture.runAsync(() -> {
            for (String memberId : members) {
                groupService.addToFeed(memberId, postId);
            }
        });
    }

    private void updateSocialMetrics(SocialNftEvent event) {
        // Update engagement metrics
        groupService.updateEngagementMetrics(
            event.getEventType(),
            event.getUserId(),
            event.getGroupId(),
            event.getTimestamp()
        );
        
        // Update NFT metrics if applicable
        if (event.getNftId() != null) {
            nftService.updateNftMetrics(
                event.getNftId(),
                event.getEventType(),
                event.getTimestamp()
            );
        }
        
        // Update reward metrics
        if (event.hasRewardData()) {
            rewardService.updateRewardMetrics(
                event.getUserId(),
                event.getRewardType(),
                event.getRewardValue()
            );
        }
    }
}