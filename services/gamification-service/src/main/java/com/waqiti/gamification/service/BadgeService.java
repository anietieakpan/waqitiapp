package com.waqiti.gamification.service;

import com.waqiti.gamification.domain.Badge;
import com.waqiti.gamification.domain.UserBadge;
import com.waqiti.gamification.domain.UserPoints;
import com.waqiti.gamification.repository.BadgeRepository;
import com.waqiti.gamification.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {
    
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserPointsService userPointsService;
    private final GamificationEventPublisher eventPublisher;
    
    @Transactional(readOnly = true)
    public List<Badge> getAllActiveBadges() {
        return badgeRepository.findActiveBadges(LocalDateTime.now());
    }
    
    @Transactional(readOnly = true)
    public List<Badge> getBadgesByCategory(Badge.BadgeCategory category) {
        return badgeRepository.findByCategoryAndPublic(category);
    }
    
    @Transactional(readOnly = true)
    public List<UserBadge> getUserBadges(String userId) {
        return userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId);
    }
    
    @Transactional(readOnly = true)
    public List<UserBadge> getDisplayedBadges(String userId) {
        return userBadgeRepository.findDisplayedBadgesByUserId(userId);
    }
    
    @Transactional
    public UserBadge awardBadge(String userId, String badgeCode, String triggerEvent) {
        Optional<Badge> badgeOpt = badgeRepository.findByBadgeCode(badgeCode);
        if (badgeOpt.isEmpty()) {
            throw new IllegalArgumentException("Badge not found: " + badgeCode);
        }
        
        Badge badge = badgeOpt.get();
        
        // Check if user already has this badge
        if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
            log.warn("User {} already has badge {}", userId, badgeCode);
            return userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId()).orElse(null);
        }
        
        // Award the badge
        UserBadge userBadge = UserBadge.builder()
                .userId(userId)
                .badge(badge)
                .earnedAt(LocalDateTime.now())
                .progressPercentage(100)
                .triggerEvent(triggerEvent)
                .build();
        
        userBadge = userBadgeRepository.save(userBadge);
        
        // Award points if badge has points reward
        if (badge.getPointsReward() > 0) {
            userPointsService.addPoints(
                userId, 
                badge.getPointsReward(), 
                "BADGE_EARNED", 
                "Badge earned: " + badge.getName(), 
                "badge:" + badge.getId()
            );
        }
        
        // Publish badge unlocked event
        eventPublisher.publishBadgeUnlockedEvent(
            userId, 
            badge.getId(), 
            badge.getName(), 
            badge.getPointsReward()
        );
        
        log.info("Awarded badge {} to user {}", badge.getName(), userId);
        
        return userBadge;
    }
    
    @Transactional
    public void checkAndAwardBadges(String userId, Badge.RequirementType requirementType, Long value) {
        List<Badge> eligibleBadges = badgeRepository.findEligibleBadges(requirementType, value);
        
        for (Badge badge : eligibleBadges) {
            if (!userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
                awardBadge(userId, badge.getBadgeCode(), requirementType.name());
            }
        }
    }
    
    @Transactional
    public UserBadge toggleBadgeDisplay(String userId, Long badgeId) {
        Optional<UserBadge> userBadgeOpt = userBadgeRepository.findByUserIdAndBadgeId(userId, badgeId);
        
        if (userBadgeOpt.isEmpty()) {
            throw new IllegalArgumentException("User badge not found");
        }
        
        UserBadge userBadge = userBadgeOpt.get();
        userBadge.setIsDisplayed(!userBadge.getIsDisplayed());
        userBadge.setUpdatedAt(LocalDateTime.now());
        
        return userBadgeRepository.save(userBadge);
    }
    
    @Transactional
    public void updateBadgeDisplayOrder(String userId, List<Long> badgeIds) {
        List<UserBadge> displayedBadges = userBadgeRepository.findDisplayedBadgesByUserId(userId);
        
        for (int i = 0; i < badgeIds.size() && i < displayedBadges.size(); i++) {
            Long badgeId = badgeIds.get(i);
            
            displayedBadges.stream()
                .filter(ub -> ub.getBadge().getId().equals(badgeId))
                .findFirst()
                .ifPresent(userBadge -> {
                    userBadge.setDisplayPosition(i + 1);
                    userBadge.setUpdatedAt(LocalDateTime.now());
                });
        }
        
        userBadgeRepository.saveAll(displayedBadges);
    }
    
    @Transactional(readOnly = true)
    public Page<Object[]> getMostEarnedBadges(Pageable pageable) {
        return userBadgeRepository.findMostEarnedBadges(pageable);
    }
    
    @Transactional(readOnly = true)
    public Long getUserBadgeCount(String userId) {
        return userBadgeRepository.countBadgesByUserId(userId);
    }
    
    @Transactional
    public void markBadgeNotificationSent(Long userBadgeId) {
        userBadgeRepository.findById(userBadgeId).ifPresent(userBadge -> {
            userBadge.setNotificationSent(true);
            userBadge.setUpdatedAt(LocalDateTime.now());
            userBadgeRepository.save(userBadge);
        });
    }
    
    @Transactional(readOnly = true)
    public List<UserBadge> getUnnotifiedBadges() {
        return userBadgeRepository.findUnnotifiedBadges();
    }
}