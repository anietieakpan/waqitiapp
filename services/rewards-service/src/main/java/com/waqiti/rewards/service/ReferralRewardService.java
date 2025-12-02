package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.domain.ReferralReward;
import com.waqiti.rewards.enums.RewardStatus;
import com.waqiti.rewards.enums.RewardType;
import com.waqiti.rewards.exception.ReferralRewardNotFoundException;
import com.waqiti.rewards.exception.ReferralRewardExpiredException;
import com.waqiti.rewards.repository.ReferralRewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing referral rewards
 *
 * Handles reward calculation, distribution, expiry, and redemption
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralRewardService {

    private final ReferralRewardRepository rewardRepository;
    private final ReferralProgramService programService;

    /**
     * Creates a referral reward
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReferralReward createReward(String referralId, String programId, UUID recipientUserId,
                                       String recipientType, RewardType rewardType,
                                       Long pointsAmount, BigDecimal cashbackAmount) {
        log.info("Creating referral reward: referralId={}, recipient={}, type={}",
                referralId, recipientUserId, rewardType);

        // Validate program and check budget
        ReferralProgram program = programService.getProgramByProgramId(programId);

        // Calculate expiry date if configured
        LocalDate expiryDate = null;
        if (program.getRewardExpiryDays() != null) {
            expiryDate = LocalDate.now().plusDays(program.getRewardExpiryDays());
        }

        // Generate reward ID
        String rewardId = "RWD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Determine if approval is required (based on amount threshold)
        boolean requiresApproval = shouldRequireApproval(cashbackAmount, pointsAmount);

        ReferralReward reward = ReferralReward.builder()
                .rewardId(rewardId)
                .referralId(referralId)
                .programId(programId)
                .recipientUserId(recipientUserId)
                .recipientType(recipientType)
                .rewardType(rewardType)
                .pointsAmount(pointsAmount)
                .cashbackAmount(cashbackAmount)
                .currency(program.getReferrerRewardCurrency())
                .status(requiresApproval ? RewardStatus.PENDING : RewardStatus.APPROVED)
                .expiryDate(expiryDate)
                .requiresApproval(requiresApproval)
                .build();

        ReferralReward savedReward = rewardRepository.save(reward);

        // Update program budget
        if (cashbackAmount != null) {
            programService.addRewardAmount(programId, cashbackAmount);
        }

        log.info("Created referral reward: rewardId={}, status={}",
                savedReward.getRewardId(), savedReward.getStatus());

        return savedReward;
    }

    /**
     * Approves a pending reward
     */
    @Transactional
    public void approveReward(String rewardId, String approver, String notes) {
        log.info("Approving reward: rewardId={}, approver={}", rewardId, approver);

        ReferralReward reward = getRewardByRewardId(rewardId);

        if (reward.getStatus() != RewardStatus.PENDING) {
            throw new IllegalStateException("Reward is not pending approval: " + rewardId);
        }

        reward.approve(approver, notes);
        rewardRepository.save(reward);

        log.info("Approved reward: rewardId={}", rewardId);
    }

    /**
     * Issues a reward to the recipient
     */
    @Transactional
    public void issueReward(String rewardId, String method, String reference) {
        log.info("Issuing reward: rewardId={}, method={}", rewardId, method);

        ReferralReward reward = getRewardByRewardId(rewardId);

        if (!reward.canBeIssued()) {
            throw new IllegalStateException("Reward cannot be issued: " + rewardId +
                    ", status=" + reward.getStatus() + ", expired=" + reward.isExpired());
        }

        reward.issue(method, reference);
        rewardRepository.save(reward);

        log.info("Issued reward: rewardId={}, method={}, reference={}",
                rewardId, method, reference);
    }

    /**
     * Redeems a reward
     */
    @Transactional
    public void redeemReward(String rewardId, String accountId, String transactionId) {
        log.info("Redeeming reward: rewardId={}, account={}", rewardId, accountId);

        ReferralReward reward = getRewardByRewardId(rewardId);

        if (!reward.canBeRedeemed()) {
            if (reward.isExpired()) {
                throw new ReferralRewardExpiredException("Reward has expired: " + rewardId);
            }
            throw new IllegalStateException("Reward cannot be redeemed: " + rewardId +
                    ", status=" + reward.getStatus());
        }

        reward.redeem(accountId, transactionId);
        rewardRepository.save(reward);

        log.info("Redeemed reward: rewardId={}, transaction={}", rewardId, transactionId);
    }

    /**
     * Rejects a reward
     */
    @Transactional
    public void rejectReward(String rewardId, String reason, String code) {
        log.info("Rejecting reward: rewardId={}, reason={}", rewardId, reason);

        ReferralReward reward = getRewardByRewardId(rewardId);
        reward.reject(reason, code);
        rewardRepository.save(reward);

        log.info("Rejected reward: rewardId={}, code={}", rewardId, code);
    }

    /**
     * Expires a reward
     */
    @Transactional
    public void expireReward(String rewardId) {
        log.info("Expiring reward: rewardId={}", rewardId);

        ReferralReward reward = getRewardByRewardId(rewardId);
        reward.expire();
        rewardRepository.save(reward);

        log.info("Expired reward: rewardId={}", rewardId);
    }

    /**
     * Gets a reward by reward ID
     */
    public ReferralReward getRewardByRewardId(String rewardId) {
        return rewardRepository.findByRewardId(rewardId)
                .orElseThrow(() -> new ReferralRewardNotFoundException("Reward not found: " + rewardId));
    }

    /**
     * Gets rewards for a user
     */
    public Page<ReferralReward> getUserRewards(UUID userId, Pageable pageable) {
        return rewardRepository.findByRecipientUserId(userId, pageable);
    }

    /**
     * Gets rewards by status for a user
     */
    public List<ReferralReward> getUserRewardsByStatus(UUID userId, RewardStatus status) {
        return rewardRepository.findByRecipientUserIdAndStatus(userId, status);
    }

    /**
     * Gets unredeemed rewards for a user
     */
    public List<ReferralReward> getUnredeemedRewards(UUID userId) {
        return rewardRepository.findUnredeemedRewards(userId, LocalDate.now());
    }

    /**
     * Gets total points earned by a user
     */
    public Long getTotalPointsEarned(UUID userId) {
        List<RewardStatus> countedStatuses = Arrays.asList(
                RewardStatus.ISSUED, RewardStatus.REDEEMED);
        return rewardRepository.getTotalPointsEarned(userId, countedStatuses);
    }

    /**
     * Gets total cashback earned by a user
     */
    public BigDecimal getTotalCashbackEarned(UUID userId) {
        List<RewardStatus> countedStatuses = Arrays.asList(
                RewardStatus.ISSUED, RewardStatus.REDEEMED);
        return rewardRepository.getTotalCashbackEarned(userId, countedStatuses);
    }

    /**
     * Gets rewards requiring approval
     */
    public List<ReferralReward> getRewardsRequiringApproval() {
        return rewardRepository.findByRequiresApprovalTrueAndStatus(RewardStatus.PENDING);
    }

    /**
     * Gets rewards expiring soon (within next N days)
     */
    public List<ReferralReward> getRewardsExpiringSoon(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(daysAhead);
        return rewardRepository.findRewardsExpiringSoon(RewardStatus.ISSUED, today, endDate);
    }

    /**
     * Processes expired rewards (scheduled job)
     */
    @Transactional
    public int processExpiredRewards() {
        log.info("Processing expired rewards");

        List<ReferralReward> expiredRewards = rewardRepository.findExpiredRewards(LocalDate.now());
        int count = 0;

        for (ReferralReward reward : expiredRewards) {
            try {
                reward.expire();
                rewardRepository.save(reward);
                count++;
                log.debug("Expired reward: rewardId={}", reward.getRewardId());
            } catch (Exception e) {
                log.error("Failed to expire reward: rewardId={}, error={}",
                        reward.getRewardId(), e.getMessage());
            }
        }

        log.info("Processed {} expired rewards", count);
        return count;
    }

    /**
     * Calculates reward amount based on program configuration
     */
    public BigDecimal calculateRewardAmount(String programId, String recipientType,
                                            BigDecimal transactionAmount) {
        ReferralProgram program = programService.getProgramByProgramId(programId);

        boolean isReferrer = "REFERRER".equalsIgnoreCase(recipientType);

        RewardType rewardType = isReferrer ?
                program.getReferrerRewardType() : program.getRefereeRewardType();

        if (rewardType == null) {
            return BigDecimal.ZERO;
        }

        switch (rewardType) {
            case FIXED_AMOUNT:
            case CASHBACK:
            case BONUS:
                return isReferrer ?
                        program.getReferrerRewardAmount() : program.getRefereeRewardAmount();

            case PERCENTAGE:
            case COMMISSION:
                if (transactionAmount == null) {
                    return BigDecimal.ZERO;
                }
                BigDecimal percentage = isReferrer ?
                        program.getReferrerRewardPercentage() : program.getRefereeRewardPercentage();
                return transactionAmount.multiply(percentage);

            case POINTS:
                // Points are handled separately
                return BigDecimal.ZERO;

            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * Calculates reward points based on program configuration
     */
    public Long calculateRewardPoints(String programId, String recipientType) {
        ReferralProgram program = programService.getProgramByProgramId(programId);

        boolean isReferrer = "REFERRER".equalsIgnoreCase(recipientType);

        RewardType rewardType = isReferrer ?
                program.getReferrerRewardType() : program.getRefereeRewardType();

        if (rewardType != RewardType.POINTS) {
            return 0L;
        }

        return isReferrer ?
                program.getReferrerRewardPoints() : program.getRefereeRewardPoints();
    }

    /**
     * Gets program statistics
     */
    public BigDecimal getTotalRewardsIssuedForProgram(String programId) {
        return rewardRepository.getTotalRewardsIssuedForProgram(programId);
    }

    /**
     * Gets redemption rate for a program
     */
    public Double getRedemptionRate(String programId) {
        Double rate = rewardRepository.getRedemptionRate(programId);
        return rate != null ? rate : 0.0;
    }

    /**
     * Gets top earners
     */
    public Page<Object[]> getTopEarners(Pageable pageable) {
        List<RewardStatus> countedStatuses = Arrays.asList(
                RewardStatus.ISSUED, RewardStatus.REDEEMED);
        return rewardRepository.findTopEarners(countedStatuses, pageable);
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    /**
     * Determines if reward requires manual approval based on amount
     */
    private boolean shouldRequireApproval(BigDecimal cashbackAmount, Long pointsAmount) {
        // Require approval for high-value rewards
        if (cashbackAmount != null && cashbackAmount.compareTo(new BigDecimal("1000")) > 0) {
            return true;
        }

        if (pointsAmount != null && pointsAmount > 10000) {
            return true;
        }

        return false;
    }
}
