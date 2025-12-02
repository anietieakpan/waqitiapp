package com.waqiti.savings.consumer;

import com.waqiti.common.events.SavingsGoalAchievedEvent;
import com.waqiti.savings.service.SavingsGoalService;
import com.waqiti.savings.service.RewardService;
import com.waqiti.savings.service.NotificationService;
import com.waqiti.savings.service.InvestmentRecommendationService;
import com.waqiti.savings.repository.ProcessedEventRepository;
import com.waqiti.savings.repository.SavingsGoalRepository;
import com.waqiti.savings.model.ProcessedEvent;
import com.waqiti.savings.model.SavingsGoal;
import com.waqiti.savings.model.GoalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for SavingsGoalAchievedEvent - Critical for savings milestone celebration
 * Handles rewards, notifications, and investment recommendations
 * ZERO TOLERANCE: All achieved savings goals must be properly celebrated and rewarded
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SavingsGoalAchievedEventConsumer {
    
    private final SavingsGoalService savingsGoalService;
    private final RewardService rewardService;
    private final NotificationService notificationService;
    private final InvestmentRecommendationService investmentRecommendationService;
    private final ProcessedEventRepository processedEventRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = "savings.goal.achieved",
        groupId = "savings-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleSavingsGoalAchieved(SavingsGoalAchievedEvent event) {
        log.info("Processing savings goal achievement: Goal {} achieved by user {} - Amount: ${}", 
            event.getGoalId(), event.getUserId(), event.getGoalAmount());
        
        // IDEMPOTENCY CHECK - Prevent duplicate celebrations
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Savings goal achievement already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Get savings goal from database
            SavingsGoal savingsGoal = savingsGoalRepository.findById(event.getGoalId())
                .orElseThrow(() -> new RuntimeException("Savings goal not found: " + event.getGoalId()));
            
            // STEP 1: Update goal status to achieved
            updateGoalToAchieved(savingsGoal, event);
            
            // STEP 2: Calculate and award achievement rewards
            awardAchievementRewards(savingsGoal, event);
            
            // STEP 3: Send multi-channel celebration notifications
            sendCelebrationNotifications(savingsGoal, event);
            
            // STEP 4: Create achievement badge/milestone
            createAchievementBadge(savingsGoal, event);
            
            // STEP 5: Generate investment recommendations for excess funds
            generateInvestmentRecommendations(savingsGoal, event);
            
            // STEP 6: Create new stretch goal if applicable
            if (event.isCreateStretchGoal()) {
                createStretchGoal(savingsGoal, event);
            }
            
            // STEP 7: Update user savings statistics
            updateUserSavingsStats(savingsGoal, event);
            
            // STEP 8: Share achievement with social network if enabled
            if (event.isSocialSharingEnabled()) {
                enableSocialSharing(savingsGoal, event);
            }
            
            // STEP 9: Schedule goal review and new goal creation
            scheduleGoalReview(savingsGoal, event);
            
            // STEP 10: Trigger savings habit reinforcement
            reinforceSavingsHabits(savingsGoal, event);
            
            // STEP 11: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("SavingsGoalAchievedEvent")
                .processedAt(Instant.now())
                .goalId(event.getGoalId())
                .userId(event.getUserId())
                .goalAmount(event.getGoalAmount())
                .rewardsAwarded(true)
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed savings goal achievement: {} - Celebrations initiated", 
                event.getGoalId());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process savings goal achievement: {}", 
                event.getGoalId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Savings goal achievement processing failed", e);
        }
    }
    
    private void updateGoalToAchieved(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Update goal status and achievement details
        savingsGoal.setStatus(GoalStatus.ACHIEVED);
        savingsGoal.setAchievedAt(event.getAchievedAt());
        savingsGoal.setFinalAmount(event.getFinalAmount());
        savingsGoal.setDaysToAchieve(event.getDaysToAchieve());
        savingsGoal.setAchievementType(event.getAchievementType()); // EARLY, ON_TIME, LATE
        
        // Calculate achievement metrics
        BigDecimal overAchievement = event.getFinalAmount().subtract(event.getGoalAmount());
        savingsGoal.setOverAchievementAmount(overAchievement);
        savingsGoal.setAchievementPercentage(
            event.getFinalAmount().divide(event.getGoalAmount(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
        );
        
        // Calculate savings rate
        long daysTaken = event.getDaysToAchieve();
        BigDecimal dailySavingsRate = event.getFinalAmount().divide(
            new BigDecimal(daysTaken), 4, RoundingMode.HALF_UP);
        savingsGoal.setDailySavingsRate(dailySavingsRate);
        
        savingsGoalRepository.save(savingsGoal);
        
        log.info("Savings goal {} updated to ACHIEVED - Final amount: ${}, Days: {}", 
            savingsGoal.getId(), event.getFinalAmount(), daysTaken);
    }
    
    private void awardAchievementRewards(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Calculate base reward based on goal amount and achievement type
        BigDecimal baseReward = calculateBaseReward(event.getGoalAmount(), event.getAchievementType());
        
        // Add bonus for early achievement
        if ("EARLY".equals(event.getAchievementType())) {
            BigDecimal earlyBonus = baseReward.multiply(new BigDecimal("0.25")); // 25% bonus
            baseReward = baseReward.add(earlyBonus);
        }
        
        // Add bonus for over-achievement
        if (savingsGoal.getOverAchievementAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal overBonus = savingsGoal.getOverAchievementAmount().multiply(new BigDecimal("0.05")); // 5% of excess
            baseReward = baseReward.add(overBonus);
        }
        
        // Award cashback reward
        String rewardId = rewardService.awardCashbackReward(
            event.getUserId(),
            baseReward,
            "SAVINGS_GOAL_ACHIEVED",
            savingsGoal.getId(),
            "Congratulations on achieving your savings goal!"
        );
        
        savingsGoal.setRewardId(rewardId);
        savingsGoal.setRewardAmount(baseReward);
        
        // Award achievement points
        int achievementPoints = calculateAchievementPoints(event.getGoalAmount(), event.getAchievementType());
        rewardService.awardPoints(
            event.getUserId(),
            achievementPoints,
            "SAVINGS_GOAL_ACHIEVEMENT",
            savingsGoal.getId()
        );
        
        savingsGoal.setPointsAwarded(achievementPoints);
        
        savingsGoalRepository.save(savingsGoal);
        
        log.info("Achievement rewards awarded for goal {}: ${} cashback, {} points", 
            savingsGoal.getId(), baseReward, achievementPoints);
    }
    
    private void sendCelebrationNotifications(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Send congratulatory push notification with celebration animation
        notificationService.sendGoalAchievementPush(
            event.getUserId(),
            savingsGoal.getId(),
            savingsGoal.getGoalName(),
            event.getFinalAmount(),
            savingsGoal.getRewardAmount()
        );
        
        // Send detailed celebration email
        notificationService.sendGoalAchievementEmail(
            event.getUserId(),
            savingsGoal,
            event,
            generateAchievementCertificate(savingsGoal, event)
        );
        
        // SMS for major milestones
        if (event.getGoalAmount().compareTo(new BigDecimal("1000")) > 0) {
            notificationService.sendGoalAchievementSMS(
                event.getUserId(),
                String.format("🎉 Congratulations! You've achieved your %s savings goal of $%.2f! Reward: $%.2f earned.",
                    savingsGoal.getGoalName(),
                    event.getFinalAmount(),
                    savingsGoal.getRewardAmount())
            );
        }
        
        // Create in-app celebration with confetti animation
        notificationService.createInAppCelebration(
            event.getUserId(),
            "🎉 Goal Achieved!",
            String.format("You've successfully saved $%.2f for your %s goal!", 
                event.getFinalAmount(), savingsGoal.getGoalName()),
            "CELEBRATION",
            savingsGoal.getId()
        );
        
        log.info("Celebration notifications sent for goal achievement: {}", savingsGoal.getId());
    }
    
    private void createAchievementBadge(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Create digital achievement badge
        String badgeType = determineBadgeType(event.getGoalAmount(), event.getAchievementType());
        
        String badgeId = rewardService.createAchievementBadge(
            event.getUserId(),
            badgeType,
            savingsGoal.getGoalName(),
            event.getFinalAmount(),
            event.getAchievedAt()
        );
        
        savingsGoal.setBadgeId(badgeId);
        savingsGoal.setBadgeType(badgeType);
        
        // Update user's achievement profile
        rewardService.updateAchievementProfile(
            event.getUserId(),
            badgeType,
            event.getGoalAmount()
        );
        
        savingsGoalRepository.save(savingsGoal);
        
        log.info("Achievement badge created for goal {}: {} badge", 
            savingsGoal.getId(), badgeType);
    }
    
    private void generateInvestmentRecommendations(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Generate personalized investment recommendations
        if (event.getFinalAmount().compareTo(new BigDecimal("500")) > 0) {
            
            String recommendationId = investmentRecommendationService.generateRecommendations(
                event.getUserId(),
                event.getFinalAmount(),
                savingsGoal.getRiskProfile(),
                savingsGoal.getTimeHorizon(),
                "SAVINGS_GOAL_ACHIEVED"
            );
            
            savingsGoal.setInvestmentRecommendationId(recommendationId);
            
            // Send investment opportunity notification
            notificationService.sendInvestmentRecommendation(
                event.getUserId(),
                savingsGoal.getId(),
                event.getFinalAmount(),
                recommendationId
            );
            
            savingsGoalRepository.save(savingsGoal);
            
            log.info("Investment recommendations generated for goal {}: {}", 
                savingsGoal.getId(), recommendationId);
        }
    }
    
    private void createStretchGoal(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Create new stretch goal with 50% higher target
        BigDecimal stretchAmount = event.getGoalAmount().multiply(new BigDecimal("1.5"));
        
        String stretchGoalId = savingsGoalService.createStretchGoal(
            event.getUserId(),
            savingsGoal.getGoalName() + " - Stretch Goal",
            stretchAmount,
            savingsGoal.getTargetDate().plusMonths(6), // Extended timeline
            savingsGoal.getGoalCategory(),
            savingsGoal.getId() // Link to original goal
        );
        
        savingsGoal.setStretchGoalId(stretchGoalId);
        savingsGoalRepository.save(savingsGoal);
        
        // Notify user about new stretch goal
        notificationService.sendStretchGoalCreated(
            event.getUserId(),
            stretchGoalId,
            stretchAmount,
            savingsGoal.getGoalName()
        );
        
        log.info("Stretch goal created for achieved goal {}: ${}", 
            savingsGoal.getId(), stretchAmount);
    }
    
    private void updateUserSavingsStats(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Update user's overall savings statistics
        savingsGoalService.updateUserStats(
            event.getUserId(),
            event.getFinalAmount(),
            event.getDaysToAchieve(),
            event.getAchievementType()
        );
        
        // Update savings milestones
        savingsGoalService.checkAndAwardSavingsMilestones(
            event.getUserId(),
            event.getFinalAmount()
        );
        
        log.info("User savings statistics updated for user: {}", event.getUserId());
    }
    
    private void enableSocialSharing(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Create shareable achievement post
        String shareableContent = String.format(
            "🎉 Just achieved my savings goal! Saved $%.2f for %s in %d days! #SavingsGoals #FinancialSuccess",
            event.getFinalAmount(),
            savingsGoal.getGoalName(),
            event.getDaysToAchieve()
        );
        
        String shareId = rewardService.createSocialShare(
            event.getUserId(),
            savingsGoal.getId(),
            shareableContent,
            generateAchievementImage(savingsGoal, event)
        );
        
        savingsGoal.setSocialShareId(shareId);
        savingsGoalRepository.save(savingsGoal);
        
        log.info("Social sharing enabled for goal achievement: {}", savingsGoal.getId());
    }
    
    private void scheduleGoalReview(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Schedule review session to set new goals
        LocalDateTime reviewDate = LocalDateTime.now().plusDays(7);
        
        savingsGoalService.scheduleGoalReview(
            event.getUserId(),
            reviewDate,
            "POST_ACHIEVEMENT_REVIEW",
            savingsGoal.getId()
        );
        
        // Send calendar invite
        notificationService.sendGoalReviewInvite(
            event.getUserId(),
            reviewDate,
            "Review your savings achievements and set new goals"
        );
        
        log.info("Goal review scheduled for user {} on {}", event.getUserId(), reviewDate);
    }
    
    private void reinforceSavingsHabits(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Analyze successful saving habits
        Map<String, Object> habitAnalysis = savingsGoalService.analyzeSavingHabits(
            event.getUserId(),
            savingsGoal.getId()
        );
        
        // Provide personalized habit reinforcement
        notificationService.sendHabitReinforcement(
            event.getUserId(),
            habitAnalysis,
            "Keep up these successful saving habits!"
        );
        
        // Set up habit tracking for consistency
        savingsGoalService.enableHabitTracking(
            event.getUserId(),
            habitAnalysis
        );
        
        log.info("Savings habits reinforced for user: {}", event.getUserId());
    }
    
    private BigDecimal calculateBaseReward(BigDecimal goalAmount, String achievementType) {
        // Base reward is 1% of goal amount
        BigDecimal basePercentage = new BigDecimal("0.01");
        
        // Adjust based on goal size
        if (goalAmount.compareTo(new BigDecimal("10000")) > 0) {
            basePercentage = new BigDecimal("0.015"); // 1.5% for large goals
        } else if (goalAmount.compareTo(new BigDecimal("1000")) > 0) {
            basePercentage = new BigDecimal("0.012"); // 1.2% for medium goals
        }
        
        return goalAmount.multiply(basePercentage);
    }
    
    private int calculateAchievementPoints(BigDecimal goalAmount, String achievementType) {
        int basePoints = goalAmount.divide(new BigDecimal("10")).intValue(); // 1 point per $10
        
        switch (achievementType) {
            case "EARLY" -> basePoints = (int) (basePoints * 1.5);
            case "ON_TIME" -> basePoints = basePoints;
            case "LATE" -> basePoints = (int) (basePoints * 0.8);
        }
        
        return Math.min(basePoints, 10000); // Cap at 10,000 points
    }
    
    private String determineBadgeType(BigDecimal goalAmount, String achievementType) {
        String prefix = "EARLY".equals(achievementType) ? "FAST_" : "";
        
        if (goalAmount.compareTo(new BigDecimal("10000")) > 0) {
            return prefix + "GOLD_SAVER";
        } else if (goalAmount.compareTo(new BigDecimal("5000")) > 0) {
            return prefix + "SILVER_SAVER";
        } else if (goalAmount.compareTo(new BigDecimal("1000")) > 0) {
            return prefix + "BRONZE_SAVER";
        } else {
            return prefix + "STARTER_SAVER";
        }
    }
    
    private String generateAchievementCertificate(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Generate PDF certificate (simplified)
        return String.format("certificate_%s_%s.pdf", savingsGoal.getId(), event.getAchievedAt());
    }
    
    private String generateAchievementImage(SavingsGoal savingsGoal, SavingsGoalAchievedEvent event) {
        // Generate shareable image (simplified)
        return String.format("achievement_%s_%s.png", savingsGoal.getId(), event.getAchievedAt());
    }
    
    private void createManualInterventionRecord(SavingsGoalAchievedEvent event, Exception exception) {
        manualInterventionService.createTask(
            "SAVINGS_GOAL_ACHIEVEMENT_PROCESSING_FAILED",
            String.format(
                "Failed to process savings goal achievement. " +
                "Goal ID: %s, User ID: %s, Amount: $%.2f. " +
                "User may not have received rewards or notifications. " +
                "Exception: %s. Manual intervention required.",
                event.getGoalId(),
                event.getUserId(),
                event.getGoalAmount(),
                exception.getMessage()
            ),
            "HIGH",
            event,
            exception
        );
    }
}