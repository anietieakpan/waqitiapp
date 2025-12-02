package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralFraudCheck;
import com.waqiti.rewards.domain.ReferralClick;
import com.waqiti.rewards.repository.ReferralFraudCheckRepository;
import com.waqiti.rewards.repository.ReferralClickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for detecting and preventing referral fraud
 *
 * Implements fraud detection rules and risk scoring
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralFraudDetectionService {

    private final ReferralFraudCheckRepository fraudCheckRepository;
    private final ReferralClickRepository clickRepository;

    // Fraud detection thresholds
    private static final int MAX_REFERRALS_PER_IP_PER_DAY = 5;
    private static final int MAX_CLICKS_PER_IP_PER_HOUR = 10;
    private static final int VELOCITY_WINDOW_MINUTES = 60;
    private static final int VELOCITY_THRESHOLD = 3;

    /**
     * Performs comprehensive fraud check on a referral
     */
    @Transactional
    public ReferralFraudCheck performFraudCheck(String referralId, UUID referrerId,
                                                UUID refereeId, String ipAddress) {
        log.info("Performing fraud check: referralId={}, referrer={}, referee={}",
                referralId, referrerId, refereeId);

        // Generate check ID
        String checkId = "FRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ReferralFraudCheck fraudCheck = ReferralFraudCheck.builder()
                .checkId(checkId)
                .referralId(referralId)
                .fraudIndicators(new HashMap<>())
                .detectionRulesTriggered(new HashSet<>())
                .build();

        // Run all fraud detection rules
        BigDecimal riskScore = BigDecimal.ZERO;

        riskScore = riskScore.add(checkSelfReferral(fraudCheck, referrerId, refereeId));
        riskScore = riskScore.add(checkDuplicateIP(fraudCheck, ipAddress));
        riskScore = riskScore.add(checkVelocity(fraudCheck, referrerId, ipAddress));
        riskScore = riskScore.add(checkSuspiciousPatterns(fraudCheck, referralId));

        fraudCheck.setRiskScore(riskScore);

        // Determine check status based on risk score
        String status = determineCheckStatus(riskScore);
        fraudCheck.setCheckStatus(status);

        // Determine action
        String action = determineAction(status, riskScore);
        fraudCheck.setActionTaken(action);

        // Set check type (primary concern)
        fraudCheck.setCheckType(determinePrimaryCheckType(fraudCheck));

        ReferralFraudCheck saved = fraudCheckRepository.save(fraudCheck);

        log.info("Fraud check completed: checkId={}, status={}, riskScore={}, action={}",
                saved.getCheckId(), status, riskScore, action);

        return saved;
    }

    /**
     * Checks for self-referral (user referring themselves)
     */
    private BigDecimal checkSelfReferral(ReferralFraudCheck fraudCheck, UUID referrerId, UUID refereeId) {
        if (referrerId.equals(refereeId)) {
            log.warn("Self-referral detected: userId={}", referrerId);
            fraudCheck.addFraudIndicator("self_referral", true);
            fraudCheck.addTriggeredRule("SELF_REFERRAL");
            return new BigDecimal("100.00"); // Maximum risk
        }
        return BigDecimal.ZERO;
    }

    /**
     * Checks for duplicate IP address usage
     */
    private BigDecimal checkDuplicateIP(ReferralFraudCheck fraudCheck, String ipAddress) {
        if (ipAddress == null) {
            return BigDecimal.ZERO;
        }

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        List<ReferralClick> recentClicks = clickRepository.findRecentClicksByIp(ipAddress, oneDayAgo);

        int clickCount = recentClicks.size();
        fraudCheck.addFraudIndicator("duplicate_ip_count", clickCount);

        if (clickCount > MAX_REFERRALS_PER_IP_PER_DAY) {
            log.warn("Excessive referrals from IP: ip={}, count={}", ipAddress, clickCount);
            fraudCheck.addTriggeredRule("DUPLICATE_IP_EXCESSIVE");

            // Risk increases with count
            double riskMultiplier = Math.min(clickCount / (double) MAX_REFERRALS_PER_IP_PER_DAY, 5.0);
            return BigDecimal.valueOf(20.0 * riskMultiplier);
        } else if (clickCount > MAX_REFERRALS_PER_IP_PER_DAY / 2) {
            fraudCheck.addTriggeredRule("DUPLICATE_IP_SUSPICIOUS");
            return new BigDecimal("15.00");
        }

        return BigDecimal.ZERO;
    }

    /**
     * Checks for velocity attacks (too many referrals too quickly)
     */
    private BigDecimal checkVelocity(ReferralFraudCheck fraudCheck, UUID referrerId, String ipAddress) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(VELOCITY_WINDOW_MINUTES);

        // Check IP-based velocity
        if (ipAddress != null) {
            List<ReferralClick> recentClicks = clickRepository.findRecentClicksByIp(ipAddress, windowStart);
            int velocityCount = recentClicks.size();

            fraudCheck.addFraudIndicator("velocity_ip_count", velocityCount);

            if (velocityCount > VELOCITY_THRESHOLD) {
                log.warn("Velocity attack detected from IP: ip={}, count={} in {} minutes",
                        ipAddress, velocityCount, VELOCITY_WINDOW_MINUTES);
                fraudCheck.addTriggeredRule("VELOCITY_ATTACK_IP");
                return new BigDecimal("35.00");
            }
        }

        return BigDecimal.ZERO;
    }

    /**
     * Checks for suspicious patterns
     */
    private BigDecimal checkSuspiciousPatterns(ReferralFraudCheck fraudCheck, String referralId) {
        BigDecimal risk = BigDecimal.ZERO;

        // Check for bot-like behavior
        List<ReferralClick> clicks = clickRepository.findByReferralCode(
                referralId.substring(0, Math.min(8, referralId.length())),
                org.springframework.data.domain.Pageable.unpaged()
        ).getContent();

        long botClicks = clicks.stream().filter(ReferralClick::isPotentialBot).count();
        if (botClicks > 0) {
            fraudCheck.addFraudIndicator("bot_clicks", botClicks);
            fraudCheck.addTriggeredRule("BOT_DETECTED");
            risk = risk.add(new BigDecimal("25.00"));
        }

        // Check for rapid succession clicks (inhuman speed)
        if (clicks.size() >= 2) {
            List<ReferralClick> sortedClicks = new ArrayList<>(clicks);
            sortedClicks.sort(Comparator.comparing(ReferralClick::getClickedAt));

            for (int i = 1; i < sortedClicks.size(); i++) {
                long secondsBetween = java.time.Duration.between(
                        sortedClicks.get(i - 1).getClickedAt(),
                        sortedClicks.get(i).getClickedAt()
                ).getSeconds();

                if (secondsBetween < 2) {
                    fraudCheck.addFraudIndicator("rapid_succession_clicks", true);
                    fraudCheck.addTriggeredRule("INHUMAN_SPEED");
                    risk = risk.add(new BigDecimal("30.00"));
                    break;
                }
            }
        }

        return risk;
    }

    /**
     * Determines check status based on risk score
     */
    private String determineCheckStatus(BigDecimal riskScore) {
        if (riskScore.compareTo(new BigDecimal("80")) >= 0) {
            return "FAILED";
        } else if (riskScore.compareTo(new BigDecimal("50")) >= 0) {
            return "REVIEW_REQUIRED";
        } else if (riskScore.compareTo(new BigDecimal("20")) >= 0) {
            return "SUSPICIOUS";
        } else {
            return "PASSED";
        }
    }

    /**
     * Determines action based on status and risk score
     */
    private String determineAction(String status, BigDecimal riskScore) {
        switch (status) {
            case "FAILED":
                return "BLOCKED";
            case "REVIEW_REQUIRED":
                return "MANUAL_REVIEW";
            case "SUSPICIOUS":
                return "FLAGGED";
            default:
                return "ALLOWED";
        }
    }

    /**
     * Determines primary check type from triggered rules
     */
    private String determinePrimaryCheckType(ReferralFraudCheck fraudCheck) {
        Set<String> rules = fraudCheck.getDetectionRulesTriggered();

        if (rules.contains("SELF_REFERRAL")) return "SELF_REFERRAL";
        if (rules.contains("VELOCITY_ATTACK_IP")) return "VELOCITY";
        if (rules.contains("DUPLICATE_IP_EXCESSIVE")) return "DUPLICATE_IP";
        if (rules.contains("BOT_DETECTED")) return "PATTERN_MATCH";

        return "GENERAL";
    }

    /**
     * Reviews a flagged fraud check
     */
    @Transactional
    public void reviewFraudCheck(String checkId, String reviewer, String decision, String notes) {
        log.info("Reviewing fraud check: checkId={}, decision={}", checkId, decision);

        ReferralFraudCheck fraudCheck = fraudCheckRepository.findByCheckId(checkId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud check not found: " + checkId));

        fraudCheck.markAsReviewed(reviewer, decision, notes);
        fraudCheckRepository.save(fraudCheck);

        log.info("Reviewed fraud check: checkId={}, decision={}", checkId, decision);
    }

    /**
     * Gets fraud checks requiring manual review
     */
    public List<ReferralFraudCheck> getChecksRequiringReview() {
        return fraudCheckRepository.findPendingReviews();
    }

    /**
     * Gets high-risk fraud checks
     */
    public List<ReferralFraudCheck> getHighRiskChecks(BigDecimal minScore) {
        return fraudCheckRepository.findHighRiskChecks(minScore);
    }

    /**
     * Gets fraud checks for a referral
     */
    public List<ReferralFraudCheck> getFraudChecksForReferral(String referralId) {
        return fraudCheckRepository.findByReferralId(referralId);
    }

    /**
     * Checks if a referral has failed fraud checks
     */
    public boolean hasFailedFraudCheck(String referralId) {
        Long failedCount = fraudCheckRepository.countFailedChecksByReferral(referralId);
        return failedCount != null && failedCount > 0;
    }

    /**
     * Gets aggregate fraud statistics for monitoring
     */
    public Map<String, Object> getFraudStatistics() {
        List<ReferralFraudCheck> allChecks = fraudCheckRepository.findAll();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_checks", allChecks.size());
        stats.put("passed", allChecks.stream().filter(ReferralFraudCheck::isPassed).count());
        stats.put("failed", allChecks.stream().filter(ReferralFraudCheck::isFailed).count());
        stats.put("suspicious", allChecks.stream().filter(ReferralFraudCheck::isSuspicious).count());
        stats.put("pending_review", allChecks.stream().filter(ReferralFraudCheck::requiresManualReview).count());

        OptionalDouble avgRiskScore = allChecks.stream()
                .filter(c -> c.getRiskScore() != null)
                .mapToDouble(c -> c.getRiskScore().doubleValue())
                .average();
        stats.put("average_risk_score", avgRiskScore.isPresent() ? avgRiskScore.getAsDouble() : 0.0);

        return stats;
    }
}
