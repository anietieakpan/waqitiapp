package com.waqiti.gamification.service;

import com.waqiti.gamification.domain.UserPoints;
import com.waqiti.gamification.dto.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topics.points-earned}")
    private String pointsEarnedTopic;
    
    @Value("${kafka.topics.badge-unlocked}")
    private String badgeUnlockedTopic;
    
    @Value("${kafka.topics.level-up}")
    private String levelUpTopic;
    
    @Value("${kafka.topics.challenge-completed}")
    private String challengeCompletedTopic;
    
    @Value("${kafka.topics.leaderboard-updated}")
    private String leaderboardUpdatedTopic;
    
    @Value("${kafka.topics.reward-claimed}")
    private String rewardClaimedTopic;
    
    @Value("${kafka.topics.notification}")
    private String notificationTopic;
    
    public void publishPointsEarnedEvent(String userId, Long pointsEarned, String eventType, Long totalPoints) {
        PointsEarnedEvent event = PointsEarnedEvent.builder()
                .userId(userId)
                .pointsEarned(pointsEarned)
                .eventType(eventType)
                .totalPoints(totalPoints)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(pointsEarnedTopic, userId, event);
        log.debug("Published points earned event for user {}: {} points", userId, pointsEarned);
    }
    
    public void publishBadgeUnlockedEvent(String userId, Long badgeId, String badgeName, Long pointsRewarded) {
        BadgeUnlockedEvent event = BadgeUnlockedEvent.builder()
                .userId(userId)
                .badgeId(badgeId)
                .badgeName(badgeName)
                .pointsRewarded(pointsRewarded)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(badgeUnlockedTopic, userId, event);
        log.debug("Published badge unlocked event for user {}: {}", userId, badgeName);
        
        // Also send notification
        publishNotificationEvent(userId, "BADGE_UNLOCKED", 
            "Congratulations! You've unlocked the " + badgeName + " badge!", 
            pointsRewarded > 0 ? "Earned " + pointsRewarded + " bonus points!" : null);
    }
    
    public void publishLevelUpEvent(String userId, UserPoints.Level previousLevel, UserPoints.Level newLevel, Long totalPoints) {
        LevelUpEvent event = LevelUpEvent.builder()
                .userId(userId)
                .previousLevel(previousLevel.name())
                .newLevel(newLevel.name())
                .totalPoints(totalPoints)
                .newCashbackRate(newLevel.getCashbackRate())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(levelUpTopic, userId, event);
        log.info("Published level up event for user {}: {} -> {}", userId, previousLevel, newLevel);
        
        // Also send notification
        publishNotificationEvent(userId, "LEVEL_UP", 
            "Congratulations! You've reached " + newLevel.name() + " level!", 
            "Your new cashback rate is " + newLevel.getCashbackRate() + "%");
    }
    
    public void publishChallengeCompletedEvent(String userId, Long challengeId, String challengeTitle, Long pointsEarned) {
        ChallengeCompletedEvent event = ChallengeCompletedEvent.builder()
                .userId(userId)
                .challengeId(challengeId)
                .challengeTitle(challengeTitle)
                .pointsEarned(pointsEarned)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(challengeCompletedTopic, userId, event);
        log.debug("Published challenge completed event for user {}: {}", userId, challengeTitle);
        
        // Also send notification
        publishNotificationEvent(userId, "CHALLENGE_COMPLETED", 
            "Challenge completed: " + challengeTitle, 
            "Earned " + pointsEarned + " points!");
    }
    
    public void publishLeaderboardUpdatedEvent(String userId, String leaderboardType, Integer newRank, Integer previousRank) {
        LeaderboardUpdatedEvent event = LeaderboardUpdatedEvent.builder()
                .userId(userId)
                .leaderboardType(leaderboardType)
                .newRank(newRank)
                .previousRank(previousRank)
                .rankChange(previousRank != null ? previousRank - newRank : null)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(leaderboardUpdatedTopic, userId, event);
        log.debug("Published leaderboard updated event for user {}: rank {}", userId, newRank);
        
        // Send notification for significant rank improvements
        if (previousRank != null && newRank < previousRank && (previousRank - newRank) >= 5) {
            publishNotificationEvent(userId, "RANK_IMPROVED", 
                "Your rank improved!", 
                "You moved from #" + previousRank + " to #" + newRank + " in the " + leaderboardType + " leaderboard!");
        }
    }
    
    public void publishRewardClaimedEvent(String userId, Long rewardId, String rewardName, Long pointsSpent) {
        RewardClaimedEvent event = RewardClaimedEvent.builder()
                .userId(userId)
                .rewardId(rewardId)
                .rewardName(rewardName)
                .pointsSpent(pointsSpent)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(rewardClaimedTopic, userId, event);
        log.debug("Published reward claimed event for user {}: {}", userId, rewardName);
    }
    
    public void publishNotificationEvent(String userId, String type, String title, String message) {
        NotificationEvent event = NotificationEvent.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .channel("IN_APP")
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(notificationTopic, userId, event);
        log.debug("Published notification event for user {}: {}", userId, title);
    }
}