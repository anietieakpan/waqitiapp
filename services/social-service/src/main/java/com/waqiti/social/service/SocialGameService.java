package com.waqiti.social.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialGameService {
    
    /**
     * Venmo-like social gamification features including:
     * - Payment streaks
     * - Social challenges
     * - Leaderboards
     * - Achievement badges
     * - Friend competitions
     */
    
    /**
     * Check and update payment streak for user
     */
    public PaymentStreakDto checkPaymentStreak(UUID userId) {
        log.debug("Checking payment streak for user: {}", userId);
        
        // Get user's recent payment activity
        List<LocalDateTime> paymentDates = getRecentPaymentDates(userId, 30);
        
        int currentStreak = calculateCurrentStreak(paymentDates);
        int longestStreak = calculateLongestStreak(paymentDates);
        
        // Check for streak milestones and award badges
        if (currentStreak > 0 && isStreakMilestone(currentStreak)) {
            awardStreakBadge(userId, currentStreak);
            createStreakActivity(userId, currentStreak);
        }
        
        return PaymentStreakDto.builder()
                .userId(userId)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .lastPaymentDate(paymentDates.isEmpty() ? null : paymentDates.get(0))
                .nextMilestone(getNextStreakMilestone(currentStreak))
                .streakReward(calculateStreakReward(currentStreak))
                .build();
    }
    
    /**
     * Create social challenge between friends
     */
    public SocialChallengeDto createSocialChallenge(UUID creatorId, CreateChallengeRequest request) {
        log.debug("Creating social challenge by user: {}", creatorId);
        
        SocialChallenge challenge = SocialChallenge.builder()
                .id(UUID.randomUUID())
                .creatorId(creatorId)
                .title(request.getTitle())
                .description(request.getDescription())
                .challengeType(request.getChallengeType())
                .targetAmount(request.getTargetAmount())
                .targetCount(request.getTargetCount())
                .participantIds(new ArrayList<>(request.getParticipantIds()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .prizeAmount(request.getPrizeAmount())
                .status(ChallengeStatus.ACTIVE)
                .isPublic(request.getIsPublic())
                .build();
        
        // Save challenge and notify participants
        // challengeRepository.save(challenge);
        notifyParticipants(challenge);
        
        return convertChallengeToDto(challenge);
    }
    
    /**
     * Join social challenge
     */
    public void joinChallenge(UUID userId, UUID challengeId) {
        log.debug("User {} joining challenge: {}", userId, challengeId);
        
        // Validate challenge exists and is joinable
        // Add user to participants
        // Send notifications
        // Update challenge stats
    }
    
    /**
     * Get friend leaderboard for various metrics
     */
    public FriendLeaderboardDto getFriendLeaderboard(UUID userId, LeaderboardType type, 
                                                    TimePeriod period) {
        log.debug("Getting {} leaderboard for user: {} period: {}", type, userId, period);
        
        List<UUID> friendIds = getFriendIds(userId);
        LocalDateTime startDate = getStartDateForPeriod(period);
        
        List<LeaderboardEntry> entries = new ArrayList<>();
        
        switch (type) {
            case PAYMENT_VOLUME:
                entries = calculatePaymentVolumeLeaderboard(friendIds, startDate);
                break;
            case PAYMENT_COUNT:
                entries = calculatePaymentCountLeaderboard(friendIds, startDate);
                break;
            case SOCIAL_ACTIVITY:
                entries = calculateSocialActivityLeaderboard(friendIds, startDate);
                break;
            case STREAK_LENGTH:
                entries = calculateStreakLeaderboard(friendIds);
                break;
            case CHARITY_DONATIONS:
                entries = calculateCharityLeaderboard(friendIds, startDate);
                break;
        }
        
        // Sort and rank entries
        entries.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }
        
        return FriendLeaderboardDto.builder()
                .type(type)
                .period(period)
                .entries(entries)
                .userRank(findUserRank(userId, entries))
                .totalParticipants(entries.size())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    /**
     * Get user achievements and badges
     */
    public List<AchievementDto> getUserAchievements(UUID userId) {
        log.debug("Getting achievements for user: {}", userId);
        
        List<Achievement> achievements = new ArrayList<>();
        
        // Payment-based achievements
        achievements.addAll(checkPaymentAchievements(userId));
        
        // Social achievements
        achievements.addAll(checkSocialAchievements(userId));
        
        // Streak achievements
        achievements.addAll(checkStreakAchievements(userId));
        
        // Special event achievements
        achievements.addAll(checkSpecialEventAchievements(userId));
        
        return achievements.stream()
                .map(this::convertAchievementToDto)
                .sorted((a, b) -> b.getEarnedAt().compareTo(a.getEarnedAt()))
                .toList();
    }
    
    /**
     * Create friend payment competition
     */
    public CompetitionDto createFriendCompetition(UUID userId, CreateCompetitionRequest request) {
        log.debug("Creating friend competition by user: {}", userId);
        
        Competition competition = Competition.builder()
                .id(UUID.randomUUID())
                .creatorId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .competitionType(request.getCompetitionType())
                .participantIds(new ArrayList<>(request.getParticipantIds()))
                .targetMetric(request.getTargetMetric())
                .targetValue(request.getTargetValue())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .prizePool(request.getPrizePool())
                .entryFee(request.getEntryFee())
                .status(CompetitionStatus.UPCOMING)
                .rules(request.getRules())
                .build();
        
        // Save and notify participants
        // competitionRepository.save(competition);
        notifyCompetitionParticipants(competition);
        
        return convertCompetitionToDto(competition);
    }
    
    /**
     * Get trending payment challenges
     */
    public List<TrendingChallengeDto> getTrendingChallenges(UUID userId, int limit) {
        log.debug("Getting trending challenges for user: {}", userId);
        
        // Get popular challenges based on participation and engagement
        List<SocialChallenge> trending = new ArrayList<>(); // challengeRepository.findTrending(limit);
        
        return trending.stream()
                .map(this::convertTrendingChallengeToDto)
                .toList();
    }
    
    /**
     * Create payment dare between friends
     */
    public PaymentDareDto createPaymentDare(UUID creatorId, CreateDareRequest request) {
        log.debug("Creating payment dare by user: {}", creatorId);
        
        PaymentDare dare = PaymentDare.builder()
                .id(UUID.randomUUID())
                .creatorId(creatorId)
                .targetUserId(request.getTargetUserId())
                .dareType(request.getDareType())
                .description(request.getDescription())
                .amount(request.getAmount())
                .timeLimit(request.getTimeLimit())
                .status(DareStatus.PENDING)
                .isPublic(request.getIsPublic())
                .stakes(request.getStakes())
                .build();
        
        // Save and notify target user
        // dareRepository.save(dare);
        notifyDareTarget(dare);
        
        return convertDareToDto(dare);
    }
    
    /**
     * Get social payment statistics for user
     */
    public SocialStatsDto getSocialStats(UUID userId, TimePeriod period) {
        log.debug("Getting social stats for user: {} period: {}", userId, period);
        
        LocalDateTime startDate = getStartDateForPeriod(period);
        
        return SocialStatsDto.builder()
                .userId(userId)
                .period(period)
                .totalPaymentsSent(calculateTotalSent(userId, startDate))
                .totalPaymentsReceived(calculateTotalReceived(userId, startDate))
                .uniquePaymentPartners(calculateUniquePartners(userId, startDate))
                .socialInteractions(calculateSocialInteractions(userId, startDate))
                .averagePaymentAmount(calculateAverageAmount(userId, startDate))
                .mostFrequentRecipient(findMostFrequentRecipient(userId, startDate))
                .paymentStreak(getCurrentPaymentStreak(userId))
                .friendRanking(getFriendRanking(userId, period))
                .achievementsEarned(getAchievementsCount(userId, startDate))
                .socialScore(calculateSocialScore(userId))
                .build();
    }
    
    // Helper methods and calculations
    
    private List<LocalDateTime> getRecentPaymentDates(UUID userId, int days) {
        // Mock implementation - would query payment repository
        return Arrays.asList(
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(3)
        );
    }
    
    private int calculateCurrentStreak(List<LocalDateTime> paymentDates) {
        if (paymentDates.isEmpty()) return 0;
        
        paymentDates.sort(Collections.reverseOrder());
        int streak = 0;
        LocalDateTime current = LocalDateTime.now().toLocalDate().atStartOfDay();
        
        for (LocalDateTime paymentDate : paymentDates) {
            if (paymentDate.toLocalDate().equals(current.toLocalDate()) ||
                paymentDate.toLocalDate().equals(current.minusDays(1).toLocalDate())) {
                streak++;
                current = current.minusDays(1);
            } else {
                break;
            }
        }
        
        return streak;
    }
    
    private int calculateLongestStreak(List<LocalDateTime> paymentDates) {
        // Implementation for longest streak calculation
        return Math.max(7, calculateCurrentStreak(paymentDates)); // Mock
    }
    
    private boolean isStreakMilestone(int streak) {
        return streak == 3 || streak == 7 || streak == 14 || streak == 30 || 
               streak == 50 || streak == 100 || streak % 100 == 0;
    }
    
    private void awardStreakBadge(UUID userId, int streak) {
        log.info("Awarding streak badge to user: {} for {} day streak", userId, streak);
        // Implementation to award badge
    }
    
    private void createStreakActivity(UUID userId, int streak) {
        // Create social activity for streak milestone
    }
    
    private int getNextStreakMilestone(int currentStreak) {
        int[] milestones = {3, 7, 14, 30, 50, 100};
        for (int milestone : milestones) {
            if (milestone > currentStreak) {
                return milestone;
            }
        }
        return ((currentStreak / 100) + 1) * 100;
    }
    
    private BigDecimal calculateStreakReward(int streak) {
        return BigDecimal.valueOf(streak * 0.10); // 10 cents per day
    }
    
    // Data Transfer Objects
    
    public static class PaymentStreakDto {
        private UUID userId;
        private int currentStreak;
        private int longestStreak;
        private LocalDateTime lastPaymentDate;
        private int nextMilestone;
        private BigDecimal streakReward;
        
        public static PaymentStreakDtoBuilder builder() {
            return new PaymentStreakDtoBuilder();
        }
        
        public static class PaymentStreakDtoBuilder {
            private UUID userId;
            private int currentStreak;
            private int longestStreak;
            private LocalDateTime lastPaymentDate;
            private int nextMilestone;
            private BigDecimal streakReward;
            
            public PaymentStreakDtoBuilder userId(UUID userId) {
                this.userId = userId;
                return this;
            }
            
            public PaymentStreakDtoBuilder currentStreak(int currentStreak) {
                this.currentStreak = currentStreak;
                return this;
            }
            
            public PaymentStreakDtoBuilder longestStreak(int longestStreak) {
                this.longestStreak = longestStreak;
                return this;
            }
            
            public PaymentStreakDtoBuilder lastPaymentDate(LocalDateTime lastPaymentDate) {
                this.lastPaymentDate = lastPaymentDate;
                return this;
            }
            
            public PaymentStreakDtoBuilder nextMilestone(int nextMilestone) {
                this.nextMilestone = nextMilestone;
                return this;
            }
            
            public PaymentStreakDtoBuilder streakReward(BigDecimal streakReward) {
                this.streakReward = streakReward;
                return this;
            }
            
            public PaymentStreakDto build() {
                PaymentStreakDto dto = new PaymentStreakDto();
                dto.userId = this.userId;
                dto.currentStreak = this.currentStreak;
                dto.longestStreak = this.longestStreak;
                dto.lastPaymentDate = this.lastPaymentDate;
                dto.nextMilestone = this.nextMilestone;
                dto.streakReward = this.streakReward;
                return dto;
            }
        }
        
        // Getters
        public UUID getUserId() { return userId; }
        public int getCurrentStreak() { return currentStreak; }
        public int getLongestStreak() { return longestStreak; }
        public LocalDateTime getLastPaymentDate() { return lastPaymentDate; }
        public int getNextMilestone() { return nextMilestone; }
        public BigDecimal getStreakReward() { return streakReward; }
    }
    
    // Enums and other supporting classes would be defined here
    public enum LeaderboardType {
        PAYMENT_VOLUME, PAYMENT_COUNT, SOCIAL_ACTIVITY, STREAK_LENGTH, CHARITY_DONATIONS
    }
    
    public enum TimePeriod {
        DAILY, WEEKLY, MONTHLY, YEARLY, ALL_TIME
    }
    
    public enum ChallengeStatus {
        ACTIVE, COMPLETED, CANCELLED, EXPIRED
    }
    
    public enum CompetitionStatus {
        UPCOMING, ACTIVE, COMPLETED, CANCELLED
    }
    
    public enum DareStatus {
        PENDING, ACCEPTED, DECLINED, COMPLETED, EXPIRED
    }
    
    // Mock implementations for remaining helper methods
    private List<UUID> getFriendIds(UUID userId) { 
        try {
            return socialConnectionRepository.findFriendIdsByUserId(userId);
        } catch (Exception e) {
            log.error("Error fetching friend IDs for user: {}", userId, e);
            return new ArrayList<>();
        }
    }
    private LocalDateTime getStartDateForPeriod(TimePeriod period) { return LocalDateTime.now().minusDays(7); }
    private List<LeaderboardEntry> calculatePaymentVolumeLeaderboard(List<UUID> friendIds, LocalDateTime startDate) { 
        try {
            List<LeaderboardEntry> leaderboard = new ArrayList<>();
            for (UUID friendId : friendIds) {
                BigDecimal totalVolume = paymentRepository.getTotalPaymentVolume(friendId, startDate, LocalDateTime.now());
                leaderboard.add(LeaderboardEntry.builder()
                    .userId(friendId)
                    .username(userRepository.findById(friendId).map(User::getUsername).orElse("Unknown"))
                    .score(totalVolume)
                    .rank(0) // Will be set after sorting
                    .build());
            }
            // Sort by score descending and assign ranks
            leaderboard.sort((a, b) -> b.getScore().compareTo(a.getScore()));
            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).setRank(i + 1);
            }
            return leaderboard;
        } catch (Exception e) {
            log.error("Error calculating payment volume leaderboard", e);
            return new ArrayList<>();
        }
    }
    private List<LeaderboardEntry> calculatePaymentCountLeaderboard(List<UUID> friendIds, LocalDateTime startDate) { 
        try {
            List<LeaderboardEntry> leaderboard = new ArrayList<>();
            for (UUID friendId : friendIds) {
                Long paymentCount = paymentRepository.getPaymentCount(friendId, startDate, LocalDateTime.now());
                leaderboard.add(LeaderboardEntry.builder()
                    .userId(friendId)
                    .username(userRepository.findById(friendId).map(User::getUsername).orElse("Unknown"))
                    .score(new BigDecimal(paymentCount))
                    .rank(0)
                    .build());
            }
            leaderboard.sort((a, b) -> b.getScore().compareTo(a.getScore()));
            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).setRank(i + 1);
            }
            return leaderboard;
        } catch (Exception e) {
            log.error("Error calculating payment count leaderboard", e);
            return new ArrayList<>();
        }
    }
    private List<LeaderboardEntry> calculateSocialActivityLeaderboard(List<UUID> friendIds, LocalDateTime startDate) { 
        try {
            List<LeaderboardEntry> leaderboard = new ArrayList<>();
            for (UUID friendId : friendIds) {
                // Calculate social activity score: comments + likes + shares + reactions
                Long socialScore = socialActivityRepository.getSocialActivityScore(friendId, startDate, LocalDateTime.now());
                leaderboard.add(LeaderboardEntry.builder()
                    .userId(friendId)
                    .username(userRepository.findById(friendId).map(User::getUsername).orElse("Unknown"))
                    .score(new BigDecimal(socialScore))
                    .rank(0)
                    .build());
            }
            leaderboard.sort((a, b) -> b.getScore().compareTo(a.getScore()));
            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).setRank(i + 1);
            }
            return leaderboard;
        } catch (Exception e) {
            log.error("Error calculating social activity leaderboard", e);
            return new ArrayList<>();
        }
    }
    private List<LeaderboardEntry> calculateStreakLeaderboard(List<UUID> friendIds) { 
        try {
            List<LeaderboardEntry> leaderboard = new ArrayList<>();
            for (UUID friendId : friendIds) {
                int currentStreak = getCurrentPaymentStreak(friendId);
                leaderboard.add(LeaderboardEntry.builder()
                    .userId(friendId)
                    .username(userRepository.findById(friendId).map(User::getUsername).orElse("Unknown"))
                    .score(new BigDecimal(currentStreak))
                    .rank(0)
                    .build());
            }
            leaderboard.sort((a, b) -> b.getScore().compareTo(a.getScore()));
            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).setRank(i + 1);
            }
            return leaderboard;
        } catch (Exception e) {
            log.error("Error calculating streak leaderboard", e);
            return new ArrayList<>();
        }
    }
    private List<LeaderboardEntry> calculateCharityLeaderboard(List<UUID> friendIds, LocalDateTime startDate) { 
        try {
            List<LeaderboardEntry> leaderboard = new ArrayList<>();
            for (UUID friendId : friendIds) {
                BigDecimal charityDonations = paymentRepository.getCharityDonations(friendId, startDate, LocalDateTime.now());
                leaderboard.add(LeaderboardEntry.builder()
                    .userId(friendId)
                    .username(userRepository.findById(friendId).map(User::getUsername).orElse("Unknown"))
                    .score(charityDonations != null ? charityDonations : BigDecimal.ZERO)
                    .rank(0)
                    .build());
            }
            leaderboard.sort((a, b) -> b.getScore().compareTo(a.getScore()));
            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).setRank(i + 1);
            }
            return leaderboard;
        } catch (Exception e) {
            log.error("Error calculating charity leaderboard", e);
            return new ArrayList<>();
        }
    }
    private int findUserRank(UUID userId, List<LeaderboardEntry> entries) { return 1; }
    private List<Achievement> checkPaymentAchievements(UUID userId) { 
        List<Achievement> achievements = new ArrayList<>();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            
            // Check total payment volume achievements
            BigDecimal totalVolume = paymentRepository.getTotalPaymentVolume(userId, monthStart, now);
            if (totalVolume.compareTo(new BigDecimal("1000")) >= 0) {
                achievements.add(createAchievement("HIGH_VOLUME_SENDER", "Sent over $1000 this month", AchievementType.PAYMENT, now));
            }
            if (totalVolume.compareTo(new BigDecimal("10000")) >= 0) {
                achievements.add(createAchievement("SUPER_SENDER", "Sent over $10000 this month", AchievementType.PAYMENT, now));
            }
            
            // Check payment count achievements
            Long paymentCount = paymentRepository.getPaymentCount(userId, monthStart, now);
            if (paymentCount >= 50) {
                achievements.add(createAchievement("FREQUENT_SENDER", "Made 50+ payments this month", AchievementType.PAYMENT, now));
            }
            if (paymentCount >= 100) {
                achievements.add(createAchievement("PAYMENT_MASTER", "Made 100+ payments this month", AchievementType.PAYMENT, now));
            }
            
            // Check first payment achievement
            if (paymentRepository.isFirstPayment(userId)) {
                achievements.add(createAchievement("FIRST_PAYMENT", "Made your first payment", AchievementType.MILESTONE, now));
            }
            
            return achievements;
        } catch (Exception e) {
            log.error("Error checking payment achievements for user: {}", userId, e);
            return achievements;
        }
    }
    private List<Achievement> checkSocialAchievements(UUID userId) { 
        List<Achievement> achievements = new ArrayList<>();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            
            // Check friend count achievement
            int friendCount = socialConnectionRepository.getFriendCount(userId);
            if (friendCount >= 10) {
                achievements.add(createAchievement("SOCIAL_BUTTERFLY", "Have 10+ friends", AchievementType.SOCIAL, now));
            }
            if (friendCount >= 50) {
                achievements.add(createAchievement("POPULAR_USER", "Have 50+ friends", AchievementType.SOCIAL, now));
            }
            
            // Check social activity achievement
            Long socialActivity = socialActivityRepository.getSocialActivityScore(userId, monthStart, now);
            if (socialActivity >= 100) {
                achievements.add(createAchievement("SOCIAL_ACTIVE", "100+ social interactions this month", AchievementType.SOCIAL, now));
            }
            
            // Check challenge participation
            int challengesParticipated = socialChallengeRepository.getParticipationCount(userId, monthStart, now);
            if (challengesParticipated >= 5) {
                achievements.add(createAchievement("CHALLENGE_ENTHUSIAST", "Participated in 5+ challenges this month", AchievementType.SOCIAL, now));
            }
            
            return achievements;
        } catch (Exception e) {
            log.error("Error checking social achievements for user: {}", userId, e);
            return achievements;
        }
    }
    private List<Achievement> checkStreakAchievements(UUID userId) { 
        List<Achievement> achievements = new ArrayList<>();
        try {
            int currentStreak = getCurrentPaymentStreak(userId);
            LocalDateTime now = LocalDateTime.now();
            
            // Check various streak milestones
            if (currentStreak >= 7) {
                achievements.add(createAchievement("WEEK_STREAK", "7-day payment streak", AchievementType.STREAK, now));
            }
            if (currentStreak >= 30) {
                achievements.add(createAchievement("MONTH_STREAK", "30-day payment streak", AchievementType.STREAK, now));
            }
            if (currentStreak >= 100) {
                achievements.add(createAchievement("CENTURY_STREAK", "100-day payment streak", AchievementType.STREAK, now));
            }
            if (currentStreak >= 365) {
                achievements.add(createAchievement("YEAR_STREAK", "365-day payment streak", AchievementType.STREAK, now));
            }
            
            return achievements;
        } catch (Exception e) {
            log.error("Error checking streak achievements for user: {}", userId, e);
            return achievements;
        }
    }
    private List<Achievement> checkSpecialEventAchievements(UUID userId) { 
        List<Achievement> achievements = new ArrayList<>();
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Check holiday/special event achievements
            if (isHolidayPeriod(now)) {
                BigDecimal holidaySpending = paymentRepository.getHolidaySpending(userId, getHolidayPeriodStart(now), now);
                if (holidaySpending.compareTo(new BigDecimal("500")) >= 0) {
                    achievements.add(createAchievement("HOLIDAY_SPENDER", "Big spender during holidays", AchievementType.SPECIAL_EVENT, now));
                }
            }
            
            // Check charity giving during special events
            if (isCharityMonth(now)) {
                BigDecimal charityDonations = paymentRepository.getCharityDonations(userId, now.withDayOfMonth(1), now);
                if (charityDonations.compareTo(BigDecimal.ZERO) > 0) {
                    achievements.add(createAchievement("CHARITY_GIVER", "Made charity donations", AchievementType.SPECIAL_EVENT, now));
                }
            }
            
            // Check app anniversary achievement
            if (isAppAnniversary(now) && paymentRepository.hasPaymentOnDate(userId, now.toLocalDate())) {
                achievements.add(createAchievement("ANNIVERSARY_PARTICIPANT", "Participated on app anniversary", AchievementType.SPECIAL_EVENT, now));
            }
            
            return achievements;
        } catch (Exception e) {
            log.error("Error checking special event achievements for user: {}", userId, e);
            return achievements;
        }
    }
    private AchievementDto convertAchievementToDto(Achievement achievement) { 
        if (achievement == null) return null;
        
        return AchievementDto.builder()
            .id(achievement.getId())
            .achievementType(achievement.getAchievementType().name())
            .title(achievement.getTitle())
            .description(achievement.getDescription())
            .badgeImageUrl(achievement.getBadgeImageUrl())
            .pointsAwarded(achievement.getPointsAwarded())
            .unlockedAt(achievement.getUnlockedAt())
            .rarity(achievement.getRarity())
            .progress(achievement.getProgress())
            .maxProgress(achievement.getMaxProgress())
            .isCompleted(achievement.isCompleted())
            .build();
    }
    private void notifyParticipants(SocialChallenge challenge) {
        // Notify all participants about challenge updates
        if (challenge == null || challenge.getParticipants() == null) {
            return;
        }
        
        try {
            // Create notification message
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("challengeId", challenge.getId());
            notificationData.put("challengeTitle", challenge.getTitle());
            notificationData.put("challengeStatus", challenge.getStatus());
            notificationData.put("currentProgress", challenge.getCurrentProgress());
            notificationData.put("targetAmount", challenge.getTargetAmount());
            notificationData.put("participantCount", challenge.getParticipants().size());
            
            // Determine notification type based on challenge status
            String notificationType;
            String message;
            
            switch (challenge.getStatus()) {
                case STARTED:
                    notificationType = "CHALLENGE_STARTED";
                    message = String.format("Challenge '%s' has started! Join now to compete!", challenge.getTitle());
                    break;
                case IN_PROGRESS:
                    notificationType = "CHALLENGE_UPDATE";
                    BigDecimal progressPercentage = challenge.getTargetAmount().compareTo(BigDecimal.ZERO) > 0 ?
                        challenge.getCurrentProgress().multiply(new BigDecimal(100))
                            .divide(challenge.getTargetAmount(), 2, RoundingMode.HALF_UP) :
                        BigDecimal.ZERO;
                    message = String.format("Challenge '%s' is %s%% complete!", 
                        challenge.getTitle(), progressPercentage);
                    break;
                case COMPLETED:
                    notificationType = "CHALLENGE_COMPLETED";
                    message = String.format("Challenge '%s' completed! Check out the final results!", 
                        challenge.getTitle());
                    break;
                case CANCELLED:
                    notificationType = "CHALLENGE_CANCELLED";
                    message = String.format("Challenge '%s' has been cancelled.", challenge.getTitle());
                    break;
                default:
                    notificationType = "CHALLENGE_UPDATE";
                    message = String.format("Update on challenge '%s'", challenge.getTitle());
            }
            
            notificationData.put("notificationType", notificationType);
            notificationData.put("message", message);
            notificationData.put("timestamp", LocalDateTime.now());
            
            // Send notifications to all participants
            for (UUID participantId : challenge.getParticipants()) {
                try {
                    // Skip notification for challenge creator if they're also a participant
                    if (participantId.equals(challenge.getCreatorId()) && 
                        challenge.getStatus() == ChallengeStatus.STARTED) {
                        continue;
                    }
                    
                    // Send via notification service
                    SocialNotification notification = SocialNotification.builder()
                        .userId(participantId)
                        .type(notificationType)
                        .title("Challenge Update")
                        .message(message)
                        .data(notificationData)
                        .priority(challenge.getStatus() == ChallengeStatus.COMPLETED ? 
                            NotificationPriority.HIGH : NotificationPriority.MEDIUM)
                        .build();
                    
                    kafkaTemplate.send("social-notifications", notification);
                    
                } catch (Exception e) {
                    log.error("Failed to notify participant {} about challenge {}", 
                        participantId, challenge.getId(), e);
                }
            }
            
            log.debug("Notified {} participants about challenge {} update", 
                challenge.getParticipants().size(), challenge.getId());
            
        } catch (Exception e) {
            log.error("Failed to notify participants about challenge {}", challenge.getId(), e);
        }
    }
    private void notifyCompetitionParticipants(Competition competition) {
        // Notify all competition participants about updates
        if (competition == null || competition.getParticipants() == null) {
            return;
        }
        
        try {
            // Prepare competition data
            Map<String, Object> competitionData = new HashMap<>();
            competitionData.put("competitionId", competition.getId());
            competitionData.put("competitionName", competition.getName());
            competitionData.put("competitionType", competition.getType());
            competitionData.put("status", competition.getStatus());
            competitionData.put("startDate", competition.getStartDate());
            competitionData.put("endDate", competition.getEndDate());
            competitionData.put("prizePool", competition.getPrizePool());
            competitionData.put("participantCount", competition.getParticipants().size());
            
            // Get current leaderboard
            List<LeaderboardEntry> leaderboard = competition.getLeaderboard();
            if (leaderboard != null && !leaderboard.isEmpty()) {
                competitionData.put("topPerformer", leaderboard.get(0).getUserId());
                competitionData.put("topScore", leaderboard.get(0).getScore());
            }
            
            // Determine notification details based on status
            String notificationType;
            String title;
            String message;
            NotificationPriority priority;
            
            switch (competition.getStatus()) {
                case UPCOMING:
                    notificationType = "COMPETITION_REMINDER";
                    title = "Competition Starting Soon";
                    message = String.format("Competition '%s' starts in 24 hours! Get ready!", 
                        competition.getName());
                    priority = NotificationPriority.MEDIUM;
                    break;
                case ACTIVE:
                    notificationType = "COMPETITION_STARTED";
                    title = "Competition Started";
                    message = String.format("Competition '%s' is now live! Start competing!", 
                        competition.getName());
                    priority = NotificationPriority.HIGH;
                    break;
                case ENDING_SOON:
                    notificationType = "COMPETITION_ENDING";
                    title = "Competition Ending Soon";
                    message = String.format("Competition '%s' ends in 1 hour! Last chance to improve your score!", 
                        competition.getName());
                    priority = NotificationPriority.HIGH;
                    break;
                case COMPLETED:
                    notificationType = "COMPETITION_ENDED";
                    title = "Competition Ended";
                    message = String.format("Competition '%s' has ended! Check the final results!", 
                        competition.getName());
                    priority = NotificationPriority.HIGH;
                    break;
                case CANCELLED:
                    notificationType = "COMPETITION_CANCELLED";
                    title = "Competition Cancelled";
                    message = String.format("Competition '%s' has been cancelled.", 
                        competition.getName());
                    priority = NotificationPriority.HIGH;
                    break;
                default:
                    notificationType = "COMPETITION_UPDATE";
                    title = "Competition Update";
                    message = String.format("Update on competition '%s'", competition.getName());
                    priority = NotificationPriority.LOW;
            }
            
            // Send personalized notifications to each participant
            for (CompetitionParticipant participant : competition.getParticipants()) {
                try {
                    // Add participant-specific data
                    Map<String, Object> personalData = new HashMap<>(competitionData);
                    personalData.put("participantRank", participant.getCurrentRank());
                    personalData.put("participantScore", participant.getScore());
                    personalData.put("participantProgress", participant.getProgress());
                    
                    // Customize message for top performers
                    String personalMessage = message;
                    if (participant.getCurrentRank() != null && participant.getCurrentRank() <= 3) {
                        personalMessage += String.format(" You're currently in position #%d!", 
                            participant.getCurrentRank());
                    }
                    
                    // Create and send notification
                    SocialNotification notification = SocialNotification.builder()
                        .userId(participant.getUserId())
                        .type(notificationType)
                        .title(title)
                        .message(personalMessage)
                        .data(personalData)
                        .priority(priority)
                        .build();
                    
                    kafkaTemplate.send("social-notifications", notification);
                    
                    // Send push notification for high priority
                    if (priority == NotificationPriority.HIGH) {
                        sendPushNotification(participant.getUserId(), title, personalMessage);
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to notify participant {} about competition {}", 
                        participant.getUserId(), competition.getId(), e);
                }
            }
            
            log.info("Notified {} participants about competition {} ({})", 
                competition.getParticipants().size(), competition.getId(), notificationType);
            
        } catch (Exception e) {
            log.error("Failed to notify competition participants", e);
        }
    }
    
    private void sendPushNotification(UUID userId, String title, String message) {
        // Send push notification via push service
        try {
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("userId", userId);
            pushData.put("title", title);
            pushData.put("message", message);
            pushData.put("timestamp", System.currentTimeMillis());
            
            kafkaTemplate.send("push-notifications", pushData);
        } catch (Exception e) {
            log.error("Failed to send push notification to user {}", userId, e);
        }
    }
    private void notifyDareTarget(PaymentDare dare) {
        // Notify the target user about the payment dare
        if (dare == null || dare.getTargetUserId() == null) {
            return;
        }
        
        try {
            // Prepare dare notification data
            Map<String, Object> dareData = new HashMap<>();
            dareData.put("dareId", dare.getId());
            dareData.put("challengerId", dare.getChallengerId());
            dareData.put("challengerName", dare.getChallengerName());
            dareData.put("dareAmount", dare.getDareAmount());
            dareData.put("description", dare.getDescription());
            dareData.put("expiresAt", dare.getExpiresAt());
            dareData.put("status", dare.getStatus());
            dareData.put("createdAt", dare.getCreatedAt());
            
            // Calculate time remaining
            if (dare.getExpiresAt() != null) {
                Duration timeRemaining = Duration.between(LocalDateTime.now(), dare.getExpiresAt());
                long hoursRemaining = timeRemaining.toHours();
                dareData.put("hoursRemaining", hoursRemaining);
            }
            
            // Determine notification content based on dare status
            String notificationType;
            String title;
            String message;
            NotificationPriority priority;
            
            switch (dare.getStatus()) {
                case PENDING:
                    notificationType = "DARE_RECEIVED";
                    title = "New Payment Dare!";
                    message = String.format("%s dared you to %s for %s!", 
                        dare.getChallengerName(), 
                        dare.getDescription(), 
                        formatCurrency(dare.getDareAmount()));
                    priority = NotificationPriority.HIGH;
                    break;
                    
                case ACCEPTED:
                    notificationType = "DARE_ACCEPTED";
                    title = "Dare Accepted!";
                    message = String.format("You accepted the dare from %s! Complete it to claim %s", 
                        dare.getChallengerName(), 
                        formatCurrency(dare.getDareAmount()));
                    priority = NotificationPriority.MEDIUM;
                    
                    // Also notify the challenger
                    notifyChallengerAboutAcceptance(dare);
                    break;
                    
                case COMPLETED:
                    notificationType = "DARE_COMPLETED";
                    title = "Dare Completed!";
                    message = String.format("Congratulations! You completed the dare and earned %s!", 
                        formatCurrency(dare.getDareAmount()));
                    priority = NotificationPriority.HIGH;
                    
                    // Notify challenger about completion
                    notifyChallengerAboutCompletion(dare);
                    break;
                    
                case DECLINED:
                    notificationType = "DARE_DECLINED";
                    title = "Dare Declined";
                    message = "You declined the payment dare.";
                    priority = NotificationPriority.LOW;
                    
                    // Notify challenger about decline
                    notifyChallengerAboutDecline(dare);
                    break;
                    
                case EXPIRED:
                    notificationType = "DARE_EXPIRED";
                    title = "Dare Expired";
                    message = String.format("The dare from %s has expired.", dare.getChallengerName());
                    priority = NotificationPriority.LOW;
                    break;
                    
                default:
                    notificationType = "DARE_UPDATE";
                    title = "Dare Update";
                    message = String.format("Update on your dare from %s", dare.getChallengerName());
                    priority = NotificationPriority.LOW;
            }
            
            // Create notification for target user
            SocialNotification notification = SocialNotification.builder()
                .userId(dare.getTargetUserId())
                .type(notificationType)
                .title(title)
                .message(message)
                .data(dareData)
                .priority(priority)
                .actionRequired(dare.getStatus() == DareStatus.PENDING)
                .build();
            
            // Send notification
            kafkaTemplate.send("social-notifications", notification);
            
            // Send push notification for new dares
            if (dare.getStatus() == DareStatus.PENDING) {
                sendPushNotification(dare.getTargetUserId(), title, message);
                
                // Send in-app notification
                sendInAppNotification(dare.getTargetUserId(), dareData);
            }
            
            log.debug("Notified user {} about dare {} (status: {})", 
                dare.getTargetUserId(), dare.getId(), dare.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to notify target user {} about dare {}", 
                dare.getTargetUserId(), dare.getId(), e);
        }
    }
    
    private void notifyChallengerAboutAcceptance(PaymentDare dare) {
        try {
            String message = String.format("%s accepted your dare! They're attempting: %s", 
                dare.getTargetUserName(), dare.getDescription());
            
            SocialNotification notification = SocialNotification.builder()
                .userId(dare.getChallengerId())
                .type("DARE_ACCEPTED_BY_TARGET")
                .title("Your Dare Was Accepted!")
                .message(message)
                .data(Map.of("dareId", dare.getId(), "targetUser", dare.getTargetUserId()))
                .priority(NotificationPriority.MEDIUM)
                .build();
            
            kafkaTemplate.send("social-notifications", notification);
        } catch (Exception e) {
            log.error("Failed to notify challenger about dare acceptance", e);
        }
    }
    
    private void notifyChallengerAboutCompletion(PaymentDare dare) {
        try {
            String message = String.format("%s completed your dare! %s has been transferred.", 
                dare.getTargetUserName(), formatCurrency(dare.getDareAmount()));
            
            SocialNotification notification = SocialNotification.builder()
                .userId(dare.getChallengerId())
                .type("DARE_COMPLETED_BY_TARGET")
                .title("Your Dare Was Completed!")
                .message(message)
                .data(Map.of("dareId", dare.getId(), "targetUser", dare.getTargetUserId(), 
                    "amount", dare.getDareAmount()))
                .priority(NotificationPriority.HIGH)
                .build();
            
            kafkaTemplate.send("social-notifications", notification);
        } catch (Exception e) {
            log.error("Failed to notify challenger about dare completion", e);
        }
    }
    
    private void notifyChallengerAboutDecline(PaymentDare dare) {
        try {
            String message = String.format("%s declined your dare.", dare.getTargetUserName());
            
            SocialNotification notification = SocialNotification.builder()
                .userId(dare.getChallengerId())
                .type("DARE_DECLINED_BY_TARGET")
                .title("Your Dare Was Declined")
                .message(message)
                .data(Map.of("dareId", dare.getId(), "targetUser", dare.getTargetUserId()))
                .priority(NotificationPriority.LOW)
                .build();
            
            kafkaTemplate.send("social-notifications", notification);
        } catch (Exception e) {
            log.error("Failed to notify challenger about dare decline", e);
        }
    }
    
    private void sendInAppNotification(UUID userId, Map<String, Object> data) {
        // Send real-time in-app notification via WebSocket
        try {
            Map<String, Object> wsData = new HashMap<>(data);
            wsData.put("userId", userId);
            wsData.put("type", "IN_APP_NOTIFICATION");
            
            kafkaTemplate.send("websocket-notifications", wsData);
        } catch (Exception e) {
            log.error("Failed to send in-app notification to user {}", userId, e);
        }
    }
    
    private String formatCurrency(BigDecimal amount) {
        // Format currency for display
        if (amount == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", amount);
    }
    private SocialChallengeDto convertChallengeToDto(SocialChallenge challenge) { 
        if (challenge == null) return null;
        
        return SocialChallengeDto.builder()
            .id(challenge.getId())
            .title(challenge.getTitle())
            .description(challenge.getDescription())
            .challengeType(challenge.getChallengeType())
            .targetAmount(challenge.getTargetAmount())
            .targetCount(challenge.getTargetCount())
            .currentProgress(challenge.getCurrentProgress())
            .participantCount(challenge.getParticipantCount())
            .startDate(challenge.getStartDate())
            .endDate(challenge.getEndDate())
            .status(challenge.getStatus())
            .prizeAmount(challenge.getPrizeAmount())
            .isPublic(challenge.isPublic())
            .createdBy(challenge.getCreatedBy())
            .createdAt(challenge.getCreatedAt())
            .winnerId(challenge.getWinnerId())
            .completedAt(challenge.getCompletedAt())
            .build();
    }
    private CompetitionDto convertCompetitionToDto(Competition competition) { 
        if (competition == null) return null;
        
        return CompetitionDto.builder()
            .id(competition.getId())
            .name(competition.getName())
            .description(competition.getDescription())
            .competitionType(competition.getCompetitionType())
            .targetMetric(competition.getTargetMetric())
            .targetValue(competition.getTargetValue())
            .participantCount(competition.getParticipantCount())
            .startDate(competition.getStartDate())
            .endDate(competition.getEndDate())
            .status(competition.getStatus())
            .prizePool(competition.getPrizePool())
            .entryFee(competition.getEntryFee())
            .rules(competition.getRules())
            .winnerId(competition.getWinnerId())
            .createdAt(competition.getCreatedAt())
            .completedAt(competition.getCompletedAt())
            .build();
    }
    private TrendingChallengeDto convertTrendingChallengeToDto(SocialChallenge challenge) { 
        if (challenge == null) return null;
        
        return TrendingChallengeDto.builder()
            .id(challenge.getId())
            .title(challenge.getTitle())
            .description(challenge.getDescription())
            .challengeType(challenge.getChallengeType())
            .participantCount(challenge.getParticipantCount())
            .trendingScore(calculateTrendingScore(challenge))
            .engagement(calculateEngagement(challenge))
            .daysRemaining(calculateDaysRemaining(challenge))
            .prizeAmount(challenge.getPrizeAmount())
            .thumbnailUrl(challenge.getThumbnailUrl())
            .tags(challenge.getTags())
            .isSponsored(challenge.isSponsored())
            .sponsorName(challenge.getSponsorName())
            .build();
    }
    private PaymentDareDto convertDareToDto(PaymentDare dare) { 
        if (dare == null) return null;
        
        return PaymentDareDto.builder()
            .id(dare.getId())
            .challenge(dare.getChallenge())
            .amount(dare.getAmount())
            .currency(dare.getCurrency())
            .targetUserId(dare.getTargetUserId())
            .targetUsername(userRepository.findById(dare.getTargetUserId()).map(User::getUsername).orElse("Unknown"))
            .initiatedBy(dare.getInitiatedBy())
            .initiatorUsername(userRepository.findById(dare.getInitiatedBy()).map(User::getUsername).orElse("Unknown"))
            .status(dare.getStatus())
            .expiresAt(dare.getExpiresAt())
            .createdAt(dare.getCreatedAt())
            .acceptedAt(dare.getAcceptedAt())
            .completedAt(dare.getCompletedAt())
            .isPublic(dare.isPublic())
            .witnessCount(dare.getWitnessCount())
            .comments(dare.getComments())
            .build();
    }
    private BigDecimal calculateTotalSent(UUID userId, LocalDateTime startDate) { 
        try {
            return paymentRepository.getTotalPaymentVolume(userId, startDate, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error calculating total sent for user: {}", userId, e);
            return BigDecimal.ZERO;
        }
    }
    private BigDecimal calculateTotalReceived(UUID userId, LocalDateTime startDate) { 
        try {
            return paymentRepository.getTotalReceivedVolume(userId, startDate, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error calculating total received for user: {}", userId, e);
            return BigDecimal.ZERO;
        }
    }
    private int calculateUniquePartners(UUID userId, LocalDateTime startDate) { 
        try {
            return paymentRepository.getUniquePartnersCount(userId, startDate, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error calculating unique partners for user: {}", userId, e);
            return 0;
        }
    }
    private int calculateSocialInteractions(UUID userId, LocalDateTime startDate) { 
        try {
            Long interactions = socialActivityRepository.getSocialActivityScore(userId, startDate, LocalDateTime.now());
            return interactions.intValue();
        } catch (Exception e) {
            log.error("Error calculating social interactions for user: {}", userId, e);
            return 0;
        }
    }
    private BigDecimal calculateAverageAmount(UUID userId, LocalDateTime startDate) { 
        try {
            BigDecimal totalAmount = paymentRepository.getTotalPaymentVolume(userId, startDate, LocalDateTime.now());
            Long paymentCount = paymentRepository.getPaymentCount(userId, startDate, LocalDateTime.now());
            
            if (paymentCount == 0) {
                return BigDecimal.ZERO;
            }
            
            return totalAmount.divide(new BigDecimal(paymentCount), 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Error calculating average amount for user: {}", userId, e);
            return BigDecimal.ZERO;
        }
    }
    private String findMostFrequentRecipient(UUID userId, LocalDateTime startDate) { 
        try {
            Optional<UUID> recipientId = paymentRepository.getMostFrequentRecipient(userId, startDate, LocalDateTime.now());
            if (recipientId.isPresent()) {
                return userRepository.findById(recipientId.get())
                    .map(User::getUsername)
                    .orElse("Unknown");
            }
            return "None";
        } catch (Exception e) {
            log.error("Error finding most frequent recipient for user: {}", userId, e);
            return "Unknown";
        }
    }
    private int getCurrentPaymentStreak(UUID userId) { 
        try {
            // Calculate current payment streak by checking consecutive days with payments
            LocalDate today = LocalDate.now();
            int streak = 0;
            LocalDate checkDate = today;
            
            while (paymentRepository.hasPaymentOnDate(userId, checkDate)) {
                streak++;
                checkDate = checkDate.minusDays(1);
                
                // Prevent infinite loop - max reasonable streak is 365 days
                if (streak > 365) break;
            }
            
            return streak;
        } catch (Exception e) {
            log.error("Error calculating payment streak for user: {}", userId, e);
            return 0;
        }
    }
    private int getFriendRanking(UUID userId, TimePeriod period) { 
        try {
            List<UUID> friendIds = getFriendIds(userId);
            if (friendIds.isEmpty()) return 0;
            
            LocalDateTime startDate = getStartDateForPeriod(period);
            List<LeaderboardEntry> leaderboard = calculatePaymentVolumeLeaderboard(friendIds, startDate);
            
            return leaderboard.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .findFirst()
                .map(LeaderboardEntry::getRank)
                .orElse(friendIds.size() + 1);
        } catch (Exception e) {
            log.error("Error calculating friend ranking for user: {}", userId, e);
            return 0;
        }
    }
    private int getAchievementsCount(UUID userId, LocalDateTime startDate) { 
        try {
            return achievementRepository.getAchievementCount(userId, startDate, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error getting achievements count for user: {}", userId, e);
            return 0;
        }
    }

    // Helper methods for achievement creation and special event detection
    private Achievement createAchievement(String code, String title, AchievementType type, LocalDateTime unlockedAt) {
        return Achievement.builder()
            .achievementType(type)
            .code(code)
            .title(title)
            .description(title)
            .pointsAwarded(calculatePointsForAchievement(code))
            .badgeImageUrl("/images/badges/" + code.toLowerCase() + ".png")
            .rarity(calculateRarity(code))
            .unlockedAt(unlockedAt)
            .isCompleted(true)
            .progress(100)
            .maxProgress(100)
            .build();
    }

    private int calculatePointsForAchievement(String code) {
        // Different achievement types give different points
        return switch (code) {
            case "FIRST_PAYMENT" -> 10;
            case "WEEK_STREAK", "SOCIAL_BUTTERFLY" -> 25;
            case "MONTH_STREAK", "HIGH_VOLUME_SENDER", "FREQUENT_SENDER" -> 50;
            case "CENTURY_STREAK", "SUPER_SENDER", "PAYMENT_MASTER" -> 100;
            case "YEAR_STREAK" -> 500;
            default -> 20;
        };
    }

    private String calculateRarity(String code) {
        return switch (code) {
            case "FIRST_PAYMENT", "WEEK_STREAK" -> "COMMON";
            case "MONTH_STREAK", "HIGH_VOLUME_SENDER", "SOCIAL_BUTTERFLY" -> "UNCOMMON";
            case "CENTURY_STREAK", "SUPER_SENDER", "POPULAR_USER" -> "RARE";
            case "YEAR_STREAK" -> "LEGENDARY";
            default -> "COMMON";
        };
    }

    private boolean isHolidayPeriod(LocalDateTime dateTime) {
        int month = dateTime.getMonthValue();
        int day = dateTime.getDayOfMonth();
        
        // Christmas season
        if (month == 12 && day >= 20) return true;
        // New Year
        if (month == 1 && day <= 5) return true;
        // Valentine's Day
        if (month == 2 && day >= 10 && day <= 16) return true;
        // Black Friday period
        if (month == 11 && day >= 25) return true;
        
        return false;
    }

    private LocalDateTime getHolidayPeriodStart(LocalDateTime dateTime) {
        int month = dateTime.getMonthValue();
        
        if (month == 12) return dateTime.withDayOfMonth(20).withHour(0).withMinute(0).withSecond(0);
        if (month == 1) return dateTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        if (month == 2) return dateTime.withDayOfMonth(10).withHour(0).withMinute(0).withSecond(0);
        if (month == 11) return dateTime.withDayOfMonth(25).withHour(0).withMinute(0).withSecond(0);
        
        return dateTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
    }

    private boolean isCharityMonth(LocalDateTime dateTime) {
        // November and December are typically charity months
        int month = dateTime.getMonthValue();
        return month == 11 || month == 12;
    }

    private boolean isAppAnniversary(LocalDateTime dateTime) {
        // Assuming app launched on January 15th
        return dateTime.getMonthValue() == 1 && dateTime.getDayOfMonth() == 15;
    }

    private double calculateTrendingScore(SocialChallenge challenge) {
        // Calculate trending score based on participation, engagement, and recency
        double participationScore = Math.min(challenge.getParticipantCount() / 100.0, 1.0) * 40;
        double engagementScore = calculateEngagement(challenge) * 30;
        double recencyScore = calculateRecencyScore(challenge) * 30;
        
        return participationScore + engagementScore + recencyScore;
    }

    private double calculateEngagement(SocialChallenge challenge) {
        try {
            Long totalInteractions = socialActivityRepository.getChallengeInteractions(challenge.getId());
            // Normalize engagement score between 0 and 1
            return Math.min(totalInteractions / 1000.0, 1.0);
        } catch (Exception e) {
            log.error("Error calculating engagement for challenge: {}", challenge.getId(), e);
            return 0.0;
        }
    }

    private double calculateRecencyScore(SocialChallenge challenge) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime created = challenge.getCreatedAt();
        long hoursOld = java.time.Duration.between(created, now).toHours();
        
        // Newer challenges get higher scores (decay over 168 hours = 1 week)
        return Math.max(0, 1.0 - (hoursOld / 168.0));
    }

    private long calculateDaysRemaining(SocialChallenge challenge) {
        LocalDateTime now = LocalDateTime.now();
        if (challenge.getEndDate().isBefore(now)) {
            return 0;
        }
        return java.time.Duration.between(now, challenge.getEndDate()).toDays();
    }
    private double calculateSocialScore(UUID userId) { return 0.0; }
    
    // Placeholder classes for compilation
    public static class SocialChallenge { public static SocialChallengeBuilder builder() { return new SocialChallengeBuilder(); } public static class SocialChallengeBuilder { public SocialChallengeBuilder id(UUID id) { return this; } public SocialChallengeBuilder creatorId(UUID creatorId) { return this; } public SocialChallengeBuilder title(String title) { return this; } public SocialChallengeBuilder description(String description) { return this; } public SocialChallengeBuilder challengeType(String challengeType) { return this; } public SocialChallengeBuilder targetAmount(BigDecimal targetAmount) { return this; } public SocialChallengeBuilder targetCount(Integer targetCount) { return this; } public SocialChallengeBuilder participantIds(List<UUID> participantIds) { return this; } public SocialChallengeBuilder startDate(LocalDateTime startDate) { return this; } public SocialChallengeBuilder endDate(LocalDateTime endDate) { return this; } public SocialChallengeBuilder prizeAmount(BigDecimal prizeAmount) { return this; } public SocialChallengeBuilder status(ChallengeStatus status) { return this; } public SocialChallengeBuilder isPublic(Boolean isPublic) { return this; } public SocialChallenge build() { return new SocialChallenge(); } } }
    public static class Competition { public static CompetitionBuilder builder() { return new CompetitionBuilder(); } public static class CompetitionBuilder { public CompetitionBuilder id(UUID id) { return this; } public CompetitionBuilder creatorId(UUID creatorId) { return this; } public CompetitionBuilder name(String name) { return this; } public CompetitionBuilder description(String description) { return this; } public CompetitionBuilder competitionType(String competitionType) { return this; } public CompetitionBuilder participantIds(List<UUID> participantIds) { return this; } public CompetitionBuilder targetMetric(String targetMetric) { return this; } public CompetitionBuilder targetValue(BigDecimal targetValue) { return this; } public CompetitionBuilder startDate(LocalDateTime startDate) { return this; } public CompetitionBuilder endDate(LocalDateTime endDate) { return this; } public CompetitionBuilder prizePool(BigDecimal prizePool) { return this; } public CompetitionBuilder entryFee(BigDecimal entryFee) { return this; } public CompetitionBuilder status(CompetitionStatus status) { return this; } public CompetitionBuilder rules(List<String> rules) { return this; } public Competition build() { return new Competition(); } } }
    public static class PaymentDare { public static PaymentDareBuilder builder() { return new PaymentDareBuilder(); } public static class PaymentDareBuilder { public PaymentDareBuilder id(UUID id) { return this; } public PaymentDareBuilder creatorId(UUID creatorId) { return this; } public PaymentDareBuilder targetUserId(UUID targetUserId) { return this; } public PaymentDareBuilder dareType(String dareType) { return this; } public PaymentDareBuilder description(String description) { return this; } public PaymentDareBuilder amount(BigDecimal amount) { return this; } public PaymentDareBuilder timeLimit(Integer timeLimit) { return this; } public PaymentDareBuilder status(DareStatus status) { return this; } public PaymentDareBuilder isPublic(Boolean isPublic) { return this; } public PaymentDareBuilder stakes(String stakes) { return this; } public PaymentDare build() { return new PaymentDare(); } } }
    
    // Placeholder DTOs and Request classes
    public static class SocialStatsDto { public static SocialStatsDtoBuilder builder() { return new SocialStatsDtoBuilder(); } public static class SocialStatsDtoBuilder { public SocialStatsDtoBuilder userId(UUID userId) { return this; } public SocialStatsDtoBuilder period(TimePeriod period) { return this; } public SocialStatsDtoBuilder totalPaymentsSent(BigDecimal totalPaymentsSent) { return this; } public SocialStatsDtoBuilder totalPaymentsReceived(BigDecimal totalPaymentsReceived) { return this; } public SocialStatsDtoBuilder uniquePaymentPartners(int uniquePaymentPartners) { return this; } public SocialStatsDtoBuilder socialInteractions(int socialInteractions) { return this; } public SocialStatsDtoBuilder averagePaymentAmount(BigDecimal averagePaymentAmount) { return this; } public SocialStatsDtoBuilder mostFrequentRecipient(String mostFrequentRecipient) { return this; } public SocialStatsDtoBuilder paymentStreak(int paymentStreak) { return this; } public SocialStatsDtoBuilder friendRanking(int friendRanking) { return this; } public SocialStatsDtoBuilder achievementsEarned(int achievementsEarned) { return this; } public SocialStatsDtoBuilder socialScore(double socialScore) { return this; } public SocialStatsDto build() { return new SocialStatsDto(); } } }
    public static class FriendLeaderboardDto { public static FriendLeaderboardDtoBuilder builder() { return new FriendLeaderboardDtoBuilder(); } public static class FriendLeaderboardDtoBuilder { public FriendLeaderboardDtoBuilder type(LeaderboardType type) { return this; } public FriendLeaderboardDtoBuilder period(TimePeriod period) { return this; } public FriendLeaderboardDtoBuilder entries(List<LeaderboardEntry> entries) { return this; } public FriendLeaderboardDtoBuilder userRank(int userRank) { return this; } public FriendLeaderboardDtoBuilder totalParticipants(int totalParticipants) { return this; } public FriendLeaderboardDtoBuilder lastUpdated(LocalDateTime lastUpdated) { return this; } public FriendLeaderboardDto build() { return new FriendLeaderboardDto(); } } }
    
    public static class LeaderboardEntry { private BigDecimal score; private int rank; public BigDecimal getScore() { return score; } public void setRank(int rank) { this.rank = rank; } }
    public static class Achievement { private LocalDateTime earnedAt; public LocalDateTime getEarnedAt() { return earnedAt; } }
    public static class AchievementDto {}
    public static class SocialChallengeDto {}
    public static class CompetitionDto {}
    public static class TrendingChallengeDto {}
    public static class PaymentDareDto {}
    
    public static class CreateChallengeRequest { public String getTitle() { return ""; } public String getDescription() { return ""; } public String getChallengeType() { return ""; } public BigDecimal getTargetAmount() { return BigDecimal.ZERO; } public Integer getTargetCount() { return 0; } public List<UUID> getParticipantIds() { return new ArrayList<>(); } public LocalDateTime getStartDate() { return LocalDateTime.now(); } public LocalDateTime getEndDate() { return LocalDateTime.now(); } public BigDecimal getPrizeAmount() { return BigDecimal.ZERO; } public Boolean getIsPublic() { return false; } }
    public static class CreateCompetitionRequest { public String getName() { return ""; } public String getDescription() { return ""; } public String getCompetitionType() { return ""; } public List<UUID> getParticipantIds() { return new ArrayList<>(); } public String getTargetMetric() { return ""; } public BigDecimal getTargetValue() { return BigDecimal.ZERO; } public LocalDateTime getStartDate() { return LocalDateTime.now(); } public LocalDateTime getEndDate() { return LocalDateTime.now(); } public BigDecimal getPrizePool() { return BigDecimal.ZERO; } public BigDecimal getEntryFee() { return BigDecimal.ZERO; } public List<String> getRules() { return new ArrayList<>(); } }
    public static class CreateDareRequest { public UUID getTargetUserId() { return UUID.randomUUID(); } public String getDareType() { return ""; } public String getDescription() { return ""; } public BigDecimal getAmount() { return BigDecimal.ZERO; } public Integer getTimeLimit() { return 0; } public Boolean getIsPublic() { return false; } public String getStakes() { return ""; } }
}