package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.exception.ReferralIneligibleException;
import com.waqiti.rewards.repository.ReferralRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for validating referral eligibility
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralValidationService {

    private final ReferralProgramService programService;
    private final ReferralRepository referralRepository;
    private final ReferralFraudDetectionService fraudDetectionService;

    /**
     * Validates if a user can be referred
     */
    public void validateReferralEligibility(UUID referrerId, UUID refereeId, String programId,
                                           BigDecimal transactionAmount, String ipAddress) {
        log.debug("Validating referral eligibility: referrer={}, referee={}, program={}",
                referrerId, refereeId, programId);

        List<String> errors = new ArrayList<>();

        // Check 1: Program is active
        ReferralProgram program = programService.getProgramByProgramId(programId);
        if (!program.isCurrentlyActive()) {
            errors.add("Referral program is not currently active");
        }

        // Check 2: Not self-referral
        if (referrerId.equals(refereeId)) {
            errors.add("Self-referral is not allowed");
        }

        // Check 3: Referee hasn't been referred before
        if (referralRepository.existsByRefereeId(refereeId.toString())) {
            errors.add("User has already been referred");
        }

        // Check 4: Referrer hasn't exceeded max referrals
        long referrerCount = referralRepository.countByReferrerId(referrerId.toString());
        if (program.getMaxReferralsPerUser() != null &&
            referrerCount >= program.getMaxReferralsPerUser()) {
            errors.add("Referrer has reached maximum referral limit");
        }

        // Check 5: Program budget not exceeded
        if (program.isBudgetExceeded()) {
            errors.add("Program budget has been exceeded");
        }

        // Check 6: Minimum transaction amount met (if required)
        if (program.getMinimumTransactionAmount() != null && transactionAmount != null) {
            if (transactionAmount.compareTo(program.getMinimumTransactionAmount()) < 0) {
                errors.add("Transaction amount below minimum requirement");
            }
        }

        // Check 7: Fraud check
        if (ipAddress != null) {
            // Quick fraud check
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            boolean hasSuspiciousActivity = checkSuspiciousActivity(ipAddress, oneDayAgo);
            if (hasSuspiciousActivity) {
                errors.add("Suspicious activity detected from IP address");
            }
        }

        if (!errors.isEmpty()) {
            String errorMessage = String.join("; ", errors);
            log.warn("Referral validation failed: {}", errorMessage);
            throw new ReferralIneligibleException(errorMessage);
        }

        log.debug("Referral validation passed");
    }

    /**
     * Validates if a referral can be converted
     */
    public boolean canConvertReferral(String referralId, LocalDateTime referredAt) {
        ReferralProgram program = programService.getProgramByProgramId(referralId.split("-")[0]);

        // Check conversion window
        if (program.getConversionWindowDays() != null) {
            LocalDateTime windowEnd = referredAt.plusDays(program.getConversionWindowDays());
            if (LocalDateTime.now().isAfter(windowEnd)) {
                log.warn("Referral conversion window expired: referralId={}", referralId);
                return false;
            }
        }

        return true;
    }

    /**
     * Quick check for suspicious activity
     */
    private boolean checkSuspiciousActivity(String ipAddress, LocalDateTime since) {
        // This would integrate with fraud detection service
        // For now, simplified check
        return false;
    }
}
