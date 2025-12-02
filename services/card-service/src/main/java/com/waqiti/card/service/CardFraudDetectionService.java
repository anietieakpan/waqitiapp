package com.waqiti.card.service;

import com.waqiti.card.dto.CardAuthorizationRequest;
import com.waqiti.card.entity.*;
import com.waqiti.card.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CardFraudDetectionService - Fraud detection and prevention
 *
 * Handles:
 * - Fraud scoring and risk assessment
 * - Velocity limit checks
 * - Pattern detection
 * - Fraud rule evaluation
 * - Alert generation
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardFraudDetectionService {

    private final CardTransactionRepository transactionRepository;
    private final CardAuthorizationRepository authorizationRepository;
    private final CardFraudRuleRepository fraudRuleRepository;
    private final CardFraudAlertRepository fraudAlertRepository;
    private final CardVelocityLimitRepository velocityLimitRepository;

    /**
     * Calculate fraud score for authorization
     */
    public BigDecimal calculateFraudScore(
        Card card, CardAuthorization authorization, CardAuthorizationRequest request) {

        log.debug("Calculating fraud score for card: {}", card.getCardId());

        BigDecimal score = BigDecimal.ZERO;

        // 1. Velocity check (30 points max)
        score = score.add(calculateVelocityScore(card.getId(), request.getAmount()));

        // 2. Amount anomaly (25 points max)
        score = score.add(calculateAmountAnomalyScore(card.getId(), request.getAmount()));

        // 3. Geographic anomaly (20 points max)
        score = score.add(calculateGeographicAnomalyScore(card.getId(), request.getMerchantCountry()));

        // 4. Time-based anomaly (15 points max)
        score = score.add(calculateTimeAnomalyScore(card.getId()));

        // 5. Merchant category risk (10 points max)
        score = score.add(calculateMerchantCategoryScore(request.getMerchantCategoryCode()));

        log.debug("Fraud score calculated: {} for card: {}", score, card.getCardId());

        return score;
    }

    /**
     * Check velocity limits
     */
    @Transactional(readOnly = true)
    public boolean checkVelocityLimits(UUID cardId, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();

        // Get effective velocity limits for card
        List<CardVelocityLimit> limits = velocityLimitRepository.findEffectiveLimitsForCard(cardId, now);

        for (CardVelocityLimit limit : limits) {
            // Check transaction count limits
            if (!checkTransactionCountLimits(cardId, limit)) {
                log.warn("Velocity limit breached - transaction count for card: {}", cardId);
                limit.recordBreach("Transaction count limit exceeded");
                velocityLimitRepository.save(limit);
                return false;
            }

            // Check amount limits
            if (!checkAmountLimits(cardId, amount, limit)) {
                log.warn("Velocity limit breached - amount limit for card: {}", cardId);
                limit.recordBreach("Amount limit exceeded");
                velocityLimitRepository.save(limit);
                return false;
            }
        }

        return true;
    }

    /**
     * Evaluate fraud rules for authorization
     */
    @Transactional
    public void evaluateFraudRules(Card card, CardAuthorization authorization, CardAuthorizationRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // Get effective fraud rules
        List<CardFraudRule> rules = fraudRuleRepository.findEffectiveRules(now);

        for (CardFraudRule rule : rules) {
            // Check if rule applies to this card
            if (!rule.appliesToCard(card.getId(), card.getCardType().name(), card.getProductId())) {
                continue;
            }

            // Evaluate rule
            boolean triggered = evaluateRule(rule, card, authorization, request);

            if (triggered) {
                log.warn("Fraud rule triggered: {} for card: {}", rule.getRuleId(), card.getCardId());

                // Record trigger
                rule.recordTrigger();
                fraudRuleRepository.save(rule);

                // Create fraud alert
                createFraudAlert(rule, card, authorization, request);

                // Take action if blocking rule
                if (rule.getIsBlocking()) {
                    authorization.setAuthorizationStatus(com.waqiti.card.enums.AuthorizationStatus.FRAUD_BLOCKED);
                    authorization.setFraudCheckResult("BLOCKED");
                }
            }
        }
    }

    /**
     * Create fraud alert
     */
    @Transactional
    public CardFraudAlert createFraudAlert(
        CardFraudRule rule, Card card, CardAuthorization authorization, CardAuthorizationRequest request) {

        String alertId = "ALERT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        CardFraudAlert alert = CardFraudAlert.builder()
            .alertId(alertId)
            .alertType("FRAUD_RULE_TRIGGERED")
            .alertCategory(rule.getRuleCategory())
            .cardId(card.getId())
            .userId(card.getUserId())
            .authorizationId(authorization.getId())
            .triggeredRuleId(rule.getId())
            .alertStatus("OPEN")
            .severity(rule.getAlertSeverity())
            .alertDate(LocalDateTime.now())
            .alertMessage("Fraud rule triggered: " + rule.getRuleName())
            .riskScore(authorization.getRiskScore())
            .riskLevel(authorization.getRiskLevel())
            .transactionAmount(request.getAmount())
            .currencyCode(request.getCurrencyCode())
            .merchantId(request.getMerchantId())
            .merchantName(request.getMerchantName())
            .merchantCategoryCode(request.getMerchantCategoryCode())
            .merchantCountry(request.getMerchantCountry())
            .manualReviewRequired(rule.getRequireManualReview())
            .fraudTeamNotified(rule.getNotifyFraudTeam())
            .userNotified(rule.getNotifyCardholder())
            .build();

        // Set fraud indicators
        alert.setVelocityBreach(authorization.getVelocityCheckPassed() != null && !authorization.getVelocityCheckPassed());
        alert.setAmountThresholdBreach(rule.getMaxTransactionAmount() != null &&
            request.getAmount().compareTo(rule.getMaxTransactionAmount()) > 0);

        if (rule.getIsBlocking()) {
            alert.blockTransaction();
        }

        alert = fraudAlertRepository.save(alert);

        log.info("Fraud alert created: {} for card: {}", alertId, card.getCardId());

        return alert;
    }

    /**
     * Check for duplicate transactions
     */
    @Transactional(readOnly = true)
    public boolean checkDuplicateTransaction(
        UUID cardId, String merchantId, BigDecimal amount, LocalDateTime transactionDate) {

        // Look for transactions in last 5 minutes
        LocalDateTime startWindow = transactionDate.minusMinutes(5);
        LocalDateTime endWindow = transactionDate.plusMinutes(5);

        List<CardTransaction> potentialDuplicates = transactionRepository.findPotentialDuplicates(
            cardId, merchantId, amount, startWindow, endWindow, "DUMMY");

        return !potentialDuplicates.isEmpty();
    }

    // ========================================================================
    // SCORING METHODS
    // ========================================================================

    private BigDecimal calculateVelocityScore(UUID cardId, BigDecimal amount) {
        BigDecimal score = BigDecimal.ZERO;

        // Check transaction count in last hour
        long countLastHour = transactionRepository.countByCardIdSince(
            cardId, LocalDateTime.now().minusHours(1));

        if (countLastHour > 10) {
            score = score.add(new BigDecimal("30"));
        } else if (countLastHour > 5) {
            score = score.add(new BigDecimal("15"));
        } else if (countLastHour > 3) {
            score = score.add(new BigDecimal("5"));
        }

        return score;
    }

    private BigDecimal calculateAmountAnomalyScore(UUID cardId, BigDecimal amount) {
        BigDecimal score = BigDecimal.ZERO;

        // Get average transaction amount for last 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<CardTransaction> recentTransactions = transactionRepository.findRecentTransactionsByCardId(
            cardId, thirtyDaysAgo);

        if (recentTransactions.isEmpty()) {
            // New card - higher risk for large first transaction
            if (amount.compareTo(new BigDecimal("1000")) > 0) {
                return new BigDecimal("20");
            }
            return BigDecimal.ZERO;
        }

        // Calculate average
        BigDecimal totalAmount = recentTransactions.stream()
            .map(CardTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageAmount = totalAmount.divide(
            new BigDecimal(recentTransactions.size()), 2, java.math.RoundingMode.HALF_UP);

        // Score based on deviation from average
        BigDecimal deviation = amount.divide(averageAmount, 2, java.math.RoundingMode.HALF_UP);

        if (deviation.compareTo(new BigDecimal("10")) > 0) {
            score = new BigDecimal("25");
        } else if (deviation.compareTo(new BigDecimal("5")) > 0) {
            score = new BigDecimal("15");
        } else if (deviation.compareTo(new BigDecimal("3")) > 0) {
            score = new BigDecimal("8");
        }

        return score;
    }

    private BigDecimal calculateGeographicAnomalyScore(UUID cardId, String merchantCountry) {
        BigDecimal score = BigDecimal.ZERO;

        if (merchantCountry == null) {
            return score;
        }

        // Get recent transactions
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<CardTransaction> recentTransactions = transactionRepository.findRecentTransactionsByCardId(
            cardId, sevenDaysAgo);

        if (recentTransactions.isEmpty()) {
            return score;
        }

        // Check if merchant country is different from recent pattern
        long sameCountryCount = recentTransactions.stream()
            .filter(t -> merchantCountry.equals(t.getMerchantCountry()))
            .count();

        if (sameCountryCount == 0) {
            // New country - higher risk
            score = new BigDecimal("20");

            // Check for multiple countries in short time
            long distinctCountries = recentTransactions.stream()
                .filter(t -> t.getMerchantCountry() != null)
                .map(CardTransaction::getMerchantCountry)
                .distinct()
                .count();

            if (distinctCountries >= 3) {
                score = score.add(new BigDecimal("10"));
            }
        }

        return score;
    }

    private BigDecimal calculateTimeAnomalyScore(UUID cardId) {
        BigDecimal score = BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        // Higher risk for transactions between midnight and 5 AM
        if (hour >= 0 && hour < 5) {
            score = new BigDecimal("10");

            // Check if user typically transacts at this time
            LocalDateTime thirtyDaysAgo = now.minusDays(30);
            List<CardTransaction> recentTransactions = transactionRepository.findRecentTransactionsByCardId(
                cardId, thirtyDaysAgo);

            long lateNightTransactions = recentTransactions.stream()
                .filter(t -> {
                    int txHour = t.getTransactionDate().getHour();
                    return txHour >= 0 && txHour < 5;
                })
                .count();

            if (lateNightTransactions == 0) {
                // User never transacts at this time - increase score
                score = score.add(new BigDecimal("5"));
            }
        }

        return score;
    }

    private BigDecimal calculateMerchantCategoryScore(String mcc) {
        if (mcc == null) {
            return BigDecimal.ZERO;
        }

        // High-risk merchant categories
        List<String> highRiskMCCs = List.of(
            "5967", // Direct Marketing - Inbound Teleservices
            "7995", // Gambling
            "6051", // Cryptocurrency
            "5816"  // Digital Goods - Games
        );

        if (highRiskMCCs.contains(mcc)) {
            return new BigDecimal("10");
        }

        return BigDecimal.ZERO;
    }

    // ========================================================================
    // RULE EVALUATION
    // ========================================================================

    private boolean evaluateRule(
        CardFraudRule rule, Card card, CardAuthorization authorization, CardAuthorizationRequest request) {

        // Check amount thresholds
        if (rule.getMaxTransactionAmount() != null &&
            request.getAmount().compareTo(rule.getMaxTransactionAmount()) > 0) {
            return true;
        }

        // Check country restrictions
        if (!rule.isCountryAllowed(request.getMerchantCountry())) {
            return true;
        }

        // Check merchant category restrictions
        if (!rule.isMerchantCategoryAllowed(request.getMerchantCategoryCode())) {
            return true;
        }

        // Check merchant restrictions
        if (rule.isMerchantBlocked(request.getMerchantId())) {
            return true;
        }

        // Check risk score threshold
        if (rule.getRiskScoreThreshold() != null &&
            authorization.getRiskScore() != null &&
            authorization.getRiskScore().compareTo(rule.getRiskScoreThreshold()) > 0) {
            return true;
        }

        return false;
    }

    // ========================================================================
    // VELOCITY CHECKS
    // ========================================================================

    private boolean checkTransactionCountLimits(UUID cardId, CardVelocityLimit limit) {
        // Check hourly limit
        if (limit.getMaxTransactionsPerHour() != null) {
            long countLastHour = transactionRepository.countByCardIdSince(
                cardId, LocalDateTime.now().minusHours(1));
            if (countLastHour >= limit.getMaxTransactionsPerHour()) {
                return false;
            }
        }

        // Check daily limit
        if (limit.getMaxTransactionsPerDay() != null) {
            long countLastDay = transactionRepository.countByCardIdSince(
                cardId, LocalDateTime.now().minusDays(1));
            if (countLastDay >= limit.getMaxTransactionsPerDay()) {
                return false;
            }
        }

        return true;
    }

    private boolean checkAmountLimits(UUID cardId, BigDecimal amount, CardVelocityLimit limit) {
        LocalDateTime now = LocalDateTime.now();

        // Check hourly amount limit
        if (limit.getMaxAmountPerHour() != null) {
            BigDecimal amountLastHour = transactionRepository.calculateTotalAmountByCardIdAndDateRange(
                cardId, now.minusHours(1), now);
            if (amountLastHour.add(amount).compareTo(limit.getMaxAmountPerHour()) > 0) {
                return false;
            }
        }

        // Check daily amount limit
        if (limit.getMaxAmountPerDay() != null) {
            BigDecimal amountLastDay = transactionRepository.calculateTotalAmountByCardIdAndDateRange(
                cardId, now.minusDays(1), now);
            if (amountLastDay.add(amount).compareTo(limit.getMaxAmountPerDay()) > 0) {
                return false;
            }
        }

        return true;
    }
}
