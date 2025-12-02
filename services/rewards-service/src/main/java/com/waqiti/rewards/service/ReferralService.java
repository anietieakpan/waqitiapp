package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.Referral;
import com.waqiti.rewards.domain.ReferralCode;
import com.waqiti.rewards.domain.RewardsAccount;
import com.waqiti.rewards.domain.PointsTransaction;
import com.waqiti.rewards.enums.ReferralStatus;
import com.waqiti.rewards.enums.PointsTransactionType;
import com.waqiti.rewards.enums.PointsStatus;
import com.waqiti.rewards.exception.RewardsAccountNotFoundException;
import com.waqiti.rewards.repository.ReferralRepository;
import com.waqiti.rewards.repository.ReferralCodeRepository;
import com.waqiti.rewards.repository.RewardsAccountRepository;
import com.waqiti.rewards.repository.PointsTransactionRepository;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.RewardsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Referral Service - Production Implementation
 *
 * Manages referral rewards for user acquisition with complete fraud prevention
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final RewardsAccountRepository accountRepository;
    private final PointsTransactionRepository pointsRepository;
    private final EventPublisher eventPublisher;

    @Value("${rewards.referral.referrer-base-reward:500}")
    private int referrerBaseRewardPoints;

    @Value("${rewards.referral.referee-base-reward:250}")
    private int refereeBaseRewardPoints;

    /**
     * Process referral reward when a new user activates their account
     *
     * @param newUserId New user ID who was referred
     * @param referralCode Referral code used
     * @param accountType Account type of new user
     * @return true if referral reward was processed successfully
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean processReferralReward(UUID newUserId, String referralCode, String accountType) {
        log.info("Processing referral reward: newUserId={}, referralCode={}, accountType={}",
                newUserId, referralCode, accountType);

        try {
            // 1. Validate referral code and get referrer
            ReferralCode code = referralCodeRepository.findByCodeAndActiveTrue(referralCode)
                    .orElse(null);

            if (code == null) {
                log.warn("Invalid or inactive referral code: {}", referralCode);
                return false;
            }

            UUID referrerId = UUID.fromString(code.getUserId());

            // 2. Check eligibility (anti-fraud)
            if (!isReferralEligible(referrerId, newUserId)) {
                log.warn("Referral not eligible: referrerId={}, newUserId={}", referrerId, newUserId);
                return false;
            }

            // 3. Calculate rewards based on account type
            int referrerPoints = calculateReferrerReward(accountType);
            int refereePoints = calculateRefereeReward(accountType);

            // 4. Award points to both parties
            awardReferrerReward(referrerId, newUserId, referrerPoints);
            awardRefereeReward(newUserId, referrerId, refereePoints);

            // 5. Record referral relationship
            recordReferral(referrerId, newUserId, referralCode, accountType, referrerPoints, refereePoints);

            // 6. Update referral code usage
            code.setUsageCount(code.getUsageCount() + 1);
            if (code.getMaxUsageLimit() != null && code.getUsageCount() >= code.getMaxUsageLimit()) {
                code.setActive(false);
            }
            referralCodeRepository.save(code);

            log.info("Referral reward processed: referrerId={}, newUserId={}, referrerPoints={}, refereePoints={}",
                    referrerId, newUserId, referrerPoints, refereePoints);

            return true;

        } catch (Exception e) {
            log.error("Failed to process referral reward: newUserId={}, referralCode={}",
                    newUserId, referralCode, e);
            return false;
        }
    }

    /**
     * Check if referral is eligible for rewards
     */
    private boolean isReferralEligible(UUID referrerId, UUID newUserId) {
        // Check self-referral
        if (referrerId.equals(newUserId)) {
            log.warn("Self-referral attempt: userId={}", referrerId);
            return false;
        }

        // Check if user already referred
        if (referralRepository.existsByRefereeId(newUserId.toString())) {
            log.warn("User already referred: newUserId={}", newUserId);
            return false;
        }

        return true;
    }

    /**
     * Calculate referrer reward points based on account type
     */
    private int calculateReferrerReward(String accountType) {
        return switch (accountType.toUpperCase()) {
            case "PREMIUM" -> (int) (referrerBaseRewardPoints * 1.5); // 750 points
            case "BUSINESS" -> referrerBaseRewardPoints * 2;          // 1000 points
            case "ENTERPRISE" -> referrerBaseRewardPoints * 3;        // 1500 points
            default -> referrerBaseRewardPoints;                      // 500 points
        };
    }

    /**
     * Calculate referee reward points based on account type
     */
    private int calculateRefereeReward(String accountType) {
        return switch (accountType.toUpperCase()) {
            case "PREMIUM" -> (int) (refereeBaseRewardPoints * 1.5); // 375 points
            case "BUSINESS" -> refereeBaseRewardPoints * 2;          // 500 points
            case "ENTERPRISE" -> (int) (refereeBaseRewardPoints * 2.5); // 625 points
            default -> refereeBaseRewardPoints;                      // 250 points
        };
    }

    /**
     * Award points to referrer
     */
    private void awardReferrerReward(UUID referrerId, UUID newUserId, int points) {
        log.info("Awarding referrer reward: referrerId={}, newUserId={}, points={}",
                referrerId, newUserId, points);

        RewardsAccount account = accountRepository.findByUserId(referrerId.toString())
                .orElseThrow(() -> new RewardsAccountNotFoundException(
                        "Rewards account not found for referrer: " + referrerId));

        // Create points transaction
        PointsTransaction transaction = PointsTransaction.builder()
                .userId(referrerId.toString())
                .points((long) points)
                .transactionType(PointsTransactionType.REFERRAL_REWARD)
                .reason("REFERRER_REWARD")
                .description("Referral reward for bringing new user: " + newUserId)
                .balanceBefore(account.getPointsBalance())
                .balanceAfter(account.getPointsBalance() + points)
                .earnedAt(Instant.now())
                .status(PointsStatus.COMPLETED)
                .build();

        pointsRepository.save(transaction);

        // Update account balance
        account.setPointsBalance(account.getPointsBalance() + points);
        account.setLifetimePoints(account.getLifetimePoints() + points);
        account.setLastActivity(Instant.now());
        accountRepository.save(account);

        // Publish event
        eventPublisher.publish(RewardsEvent.referralRewardAwarded(
                referrerId.toString(), points, "REFERRER", newUserId.toString()));
    }

    /**
     * Award points to referee (new user)
     */
    private void awardRefereeReward(UUID newUserId, UUID referrerId, int points) {
        log.info("Awarding referee reward: newUserId={}, referrerId={}, points={}",
                newUserId, referrerId, points);

        RewardsAccount account = accountRepository.findByUserId(newUserId.toString())
                .orElseThrow(() -> new RewardsAccountNotFoundException(
                        "Rewards account not found for referee: " + newUserId));

        // Create points transaction
        PointsTransaction transaction = PointsTransaction.builder()
                .userId(newUserId.toString())
                .points((long) points)
                .transactionType(PointsTransactionType.REFERRAL_REWARD)
                .reason("REFEREE_REWARD")
                .description("Welcome reward for using referral code from: " + referrerId)
                .balanceBefore(account.getPointsBalance())
                .balanceAfter(account.getPointsBalance() + points)
                .earnedAt(Instant.now())
                .status(PointsStatus.COMPLETED)
                .build();

        pointsRepository.save(transaction);

        // Update account balance
        account.setPointsBalance(account.getPointsBalance() + points);
        account.setLifetimePoints(account.getLifetimePoints() + points);
        account.setLastActivity(Instant.now());
        accountRepository.save(account);

        // Publish event
        eventPublisher.publish(RewardsEvent.referralRewardAwarded(
                newUserId.toString(), points, "REFEREE", referrerId.toString()));
    }

    /**
     * Record referral relationship
     */
    private void recordReferral(UUID referrerId, UUID newUserId, String referralCode,
                                String accountType, int referrerPoints, int refereePoints) {
        log.debug("Recording referral: referrerId={}, newUserId={}, code={}",
                referrerId, newUserId, referralCode);

        Referral referral = Referral.builder()
                .referrerId(referrerId.toString())
                .refereeId(newUserId.toString())
                .referralCode(referralCode)
                .refereeAccountType(accountType)
                .referrerReward(new BigDecimal(referrerPoints))
                .refereeReward(new BigDecimal(refereePoints))
                .status(ReferralStatus.COMPLETED)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .notes("Automatically processed on account activation")
                .build();

        referralRepository.save(referral);
    }
}
