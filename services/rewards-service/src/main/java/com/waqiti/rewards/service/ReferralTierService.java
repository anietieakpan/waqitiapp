package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralTier;
import com.waqiti.rewards.repository.ReferralTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing referral tiers and progression
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralTierService {

    private final ReferralTierRepository tierRepository;
    private final ReferralProgramService programService;

    /**
     * Creates a new tier
     */
    @Transactional
    public ReferralTier createTier(ReferralTier tier) {
        log.info("Creating referral tier: program={}, level={}",
                tier.getProgram().getProgramId(), tier.getTierLevel());

        // Validate tier doesn't already exist for this level
        if (tierRepository.existsByProgram_ProgramIdAndTierLevel(
                tier.getProgram().getProgramId(), tier.getTierLevel())) {
            throw new IllegalArgumentException("Tier already exists for this level");
        }

        ReferralTier saved = tierRepository.save(tier);
        log.info("Created referral tier: tierId={}, level={}", saved.getTierId(), saved.getTierLevel());
        return saved;
    }

    /**
     * Gets qualifying tier for a user based on their metrics
     */
    public Optional<ReferralTier> getQualifyingTier(String programId, int referralCount,
                                                     int conversionCount, BigDecimal revenue) {
        log.debug("Finding qualifying tier: program={}, referrals={}, conversions={}, revenue={}",
                programId, referralCount, conversionCount, revenue);

        return tierRepository.findQualifyingTier(programId, referralCount);
    }

    /**
     * Gets the next tier a user should aim for
     */
    public Optional<ReferralTier> getNextTier(String programId, int currentReferrals) {
        return tierRepository.findNextTier(programId, currentReferrals);
    }

    /**
     * Gets all tiers for a program (ordered by level)
     */
    public List<ReferralTier> getProgramTiers(String programId) {
        return tierRepository.findByProgramOrderedByLevel(programId);
    }

    /**
     * Gets active tiers for a program
     */
    public List<ReferralTier> getActiveProgramTiers(String programId) {
        return tierRepository.findByProgram_ProgramIdAndIsActiveTrue(programId);
    }

    /**
     * Calculates tiered reward amount
     */
    public BigDecimal calculateTieredReward(String programId, int referralCount,
                                            BigDecimal baseReward) {
        Optional<ReferralTier> tier = getQualifyingTier(programId, referralCount, 0, BigDecimal.ZERO);

        if (tier.isPresent()) {
            BigDecimal tieredAmount = tier.get().calculateTieredReward(baseReward);
            log.debug("Applied tier multiplier: base={}, tiered={}, multiplier={}",
                    baseReward, tieredAmount, tier.get().getRewardMultiplier());
            return tieredAmount;
        }

        return baseReward;
    }

    /**
     * Checks if user qualifies for a tier upgrade
     */
    public boolean qualifiesForUpgrade(String programId, int currentTierLevel,
                                       int referralCount, int conversionCount, BigDecimal revenue) {
        Optional<ReferralTier> nextTier = tierRepository.findNextTier(programId, referralCount);

        if (nextTier.isPresent() && nextTier.get().getTierLevel() > currentTierLevel) {
            return nextTier.get().qualifiesForTier(referralCount, conversionCount, revenue);
        }

        return false;
    }

    /**
     * Gets tier by ID
     */
    public ReferralTier getTierById(String tierId) {
        return tierRepository.findByTierId(tierId)
                .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierId));
    }

    /**
     * Activates a tier
     */
    @Transactional
    public void activateTier(String tierId) {
        ReferralTier tier = getTierById(tierId);
        tier.setIsActive(true);
        tierRepository.save(tier);
        log.info("Activated tier: {}", tierId);
    }

    /**
     * Deactivates a tier
     */
    @Transactional
    public void deactivateTier(String tierId) {
        ReferralTier tier = getTierById(tierId);
        tier.setIsActive(false);
        tierRepository.save(tier);
        log.info("Deactivated tier: {}", tierId);
    }
}
