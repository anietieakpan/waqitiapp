package com.waqiti.payment.service;

import com.waqiti.payment.domain.TransferLimit;
import com.waqiti.payment.domain.LimitType;
import com.waqiti.payment.domain.LimitPeriod;
import com.waqiti.payment.repository.TransferLimitRepository;
import com.waqiti.payment.repository.InstantTransferRepository;
import com.waqiti.payment.exception.TransferLimitExceededException;
import com.waqiti.payment.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade service for managing instant transfer limits and velocity controls
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstantTransferLimitService {

    private final TransferLimitRepository transferLimitRepository;
    private final InstantTransferRepository instantTransferRepository;
    private final UserServiceClient userServiceClient;

    @Value("${instant.transfer.default.daily.limit:5000.00}")
    private BigDecimal defaultDailyLimit;

    @Value("${instant.transfer.default.monthly.limit:20000.00}")
    private BigDecimal defaultMonthlyLimit;

    @Value("${instant.transfer.default.transaction.limit:2500.00}")
    private BigDecimal defaultTransactionLimit;

    @Value("${instant.transfer.velocity.max.per.hour:10}")
    private int maxTransactionsPerHour;

    @Value("${instant.transfer.velocity.max.per.day:50}")
    private int maxTransactionsPerDay;

    // Cache for user limits to reduce database hits
    private final Map<String, UserLimits> userLimitsCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MINUTES = 15;

    /**
     * Check if user can perform transfer within limits
     */
    public void validateTransferLimits(String userId, BigDecimal amount, String currency) {
        log.debug("Validating transfer limits for user: {}, amount: {} {}", userId, amount, currency);

        UserLimits limits = getUserLimits(userId);
        
        // Check single transaction limit
        validateSingleTransactionLimit(userId, amount, limits);
        
        // Check daily limits
        validateDailyLimits(userId, amount, currency, limits);
        
        // Check monthly limits
        validateMonthlyLimits(userId, amount, currency, limits);
        
        // Check velocity limits
        validateVelocityLimits(userId);

        log.debug("Transfer limits validation passed for user: {}", userId);
    }

    /**
     * Check remaining limits for a user
     */
    @Cacheable(value = "user-remaining-limits", key = "#userId")
    public RemainingLimits getRemainingLimits(String userId) {
        log.debug("Calculating remaining limits for user: {}", userId);

        UserLimits limits = getUserLimits(userId);
        
        // Calculate used amounts for different periods
        BigDecimal usedToday = getUsedAmountForPeriod(userId, LocalDateTime.now().truncatedTo(ChronoUnit.DAYS));
        BigDecimal usedThisMonth = getUsedAmountForPeriod(userId, LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS));
        
        // Calculate used transaction counts
        int transactionsToday = getTransactionCountForPeriod(userId, LocalDateTime.now().truncatedTo(ChronoUnit.DAYS));
        int transactionsThisHour = getTransactionCountForPeriod(userId, LocalDateTime.now().truncatedTo(ChronoUnit.HOURS));

        return RemainingLimits.builder()
            .userId(userId)
            .remainingDailyAmount(limits.getDailyLimit().subtract(usedToday))
            .remainingMonthlyAmount(limits.getMonthlyLimit().subtract(usedThisMonth))
            .remainingSingleTransactionLimit(limits.getTransactionLimit())
            .remainingDailyTransactions(maxTransactionsPerDay - transactionsToday)
            .remainingHourlyTransactions(maxTransactionsPerHour - transactionsThisHour)
            .currency(limits.getCurrency())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    /**
     * Update user limits (admin function)
     */
    public void updateUserLimits(String userId, BigDecimal dailyLimit, BigDecimal monthlyLimit, 
                                BigDecimal transactionLimit, String updatedBy) {
        log.info("Updating limits for user: {} by admin: {}", userId, updatedBy);

        TransferLimit limit = transferLimitRepository.findByUserIdAndLimitType(userId, LimitType.DAILY)
            .orElse(TransferLimit.builder()
                .userId(userId)
                .limitType(LimitType.DAILY)
                .build());

        // Update daily limit
        if (dailyLimit != null) {
            TransferLimit dailyLimitRecord = limit.toBuilder()
                .limitType(LimitType.DAILY)
                .amount(dailyLimit)
                .currency("USD")
                .period(LimitPeriod.DAILY)
                .updatedBy(updatedBy)
                .updatedAt(LocalDateTime.now())
                .build();
            transferLimitRepository.save(dailyLimitRecord);
        }

        // Update monthly limit
        if (monthlyLimit != null) {
            TransferLimit monthlyLimitRecord = limit.toBuilder()
                .id(null)
                .limitType(LimitType.MONTHLY)
                .amount(monthlyLimit)
                .currency("USD")
                .period(LimitPeriod.MONTHLY)
                .updatedBy(updatedBy)
                .updatedAt(LocalDateTime.now())
                .build();
            transferLimitRepository.save(monthlyLimitRecord);
        }

        // Update transaction limit
        if (transactionLimit != null) {
            TransferLimit transactionLimitRecord = limit.toBuilder()
                .id(null)
                .limitType(LimitType.PER_TRANSACTION)
                .amount(transactionLimit)
                .currency("USD")
                .period(LimitPeriod.PER_TRANSACTION)
                .updatedBy(updatedBy)
                .updatedAt(LocalDateTime.now())
                .build();
            transferLimitRepository.save(transactionLimitRecord);
        }

        // Clear cache for this user
        userLimitsCache.remove(userId);

        log.info("Limits updated successfully for user: {}", userId);
    }

    /**
     * Increase limits temporarily for verified users
     */
    public void applyTemporaryLimitIncrease(String userId, BigDecimal additionalAmount, 
                                          ChronoUnit duration, long durationValue, String reason) {
        log.info("Applying temporary limit increase for user: {}, additional: {}, duration: {} {}", 
            userId, additionalAmount, durationValue, duration);

        // This would typically create a temporary limit record
        // For now, we'll log the action
        // In production, you'd store this in a separate table for temporary limits

        userLimitsCache.remove(userId); // Clear cache
    }

    /**
     * Get user's risk-based limits
     */
    private UserLimits getUserLimits(String userId) {
        // Check cache first
        UserLimits cached = userLimitsCache.get(userId);
        if (cached != null && cached.getCachedAt().isAfter(LocalDateTime.now().minusMinutes(CACHE_TTL_MINUTES))) {
            return cached;
        }

        log.debug("Loading limits from database for user: {}", userId);

        // Load from database
        List<TransferLimit> limits = transferLimitRepository.findByUserIdAndActiveTrue(userId);
        
        BigDecimal dailyLimit = defaultDailyLimit;
        BigDecimal monthlyLimit = defaultMonthlyLimit;
        BigDecimal transactionLimit = defaultTransactionLimit;

        // Override with user-specific limits if they exist
        for (TransferLimit limit : limits) {
            switch (limit.getLimitType()) {
                case DAILY:
                    dailyLimit = limit.getAmount();
                    break;
                case MONTHLY:
                    monthlyLimit = limit.getAmount();
                    break;
                case PER_TRANSACTION:
                    transactionLimit = limit.getAmount();
                    break;
            }
        }

        // Apply risk-based adjustments
        UserRiskProfile riskProfile = getUserRiskProfile(userId);
        dailyLimit = applyRiskAdjustment(dailyLimit, riskProfile.getRiskLevel());
        monthlyLimit = applyRiskAdjustment(monthlyLimit, riskProfile.getRiskLevel());
        transactionLimit = applyRiskAdjustment(transactionLimit, riskProfile.getRiskLevel());

        UserLimits userLimits = UserLimits.builder()
            .userId(userId)
            .dailyLimit(dailyLimit)
            .monthlyLimit(monthlyLimit)
            .transactionLimit(transactionLimit)
            .currency("USD")
            .riskLevel(riskProfile.getRiskLevel())
            .cachedAt(LocalDateTime.now())
            .build();

        // Cache the result
        userLimitsCache.put(userId, userLimits);

        return userLimits;
    }

    private void validateSingleTransactionLimit(String userId, BigDecimal amount, UserLimits limits) {
        if (amount.compareTo(limits.getTransactionLimit()) > 0) {
            log.warn("Single transaction limit exceeded for user: {}, amount: {}, limit: {}", 
                userId, amount, limits.getTransactionLimit());
            throw new TransferLimitExceededException(
                String.format("Single transaction limit of %s exceeded", limits.getTransactionLimit()));
        }
    }

    private void validateDailyLimits(String userId, BigDecimal amount, String currency, UserLimits limits) {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal usedToday = getUsedAmountForPeriod(userId, startOfDay);
        BigDecimal newTotal = usedToday.add(amount);

        if (newTotal.compareTo(limits.getDailyLimit()) > 0) {
            log.warn("Daily limit exceeded for user: {}, used: {}, requested: {}, limit: {}", 
                userId, usedToday, amount, limits.getDailyLimit());
            throw new TransferLimitExceededException(
                String.format("Daily limit of %s would be exceeded. Used: %s, Requested: %s", 
                    limits.getDailyLimit(), usedToday, amount));
        }
    }

    private void validateMonthlyLimits(String userId, BigDecimal amount, String currency, UserLimits limits) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        BigDecimal usedThisMonth = getUsedAmountForPeriod(userId, startOfMonth);
        BigDecimal newTotal = usedThisMonth.add(amount);

        if (newTotal.compareTo(limits.getMonthlyLimit()) > 0) {
            log.warn("Monthly limit exceeded for user: {}, used: {}, requested: {}, limit: {}", 
                userId, usedThisMonth, amount, limits.getMonthlyLimit());
            throw new TransferLimitExceededException(
                String.format("Monthly limit of %s would be exceeded. Used: %s, Requested: %s", 
                    limits.getMonthlyLimit(), usedThisMonth, amount));
        }
    }

    private void validateVelocityLimits(String userId) {
        // Check hourly velocity
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        int transactionsInLastHour = getTransactionCountForPeriod(userId, oneHourAgo);
        
        if (transactionsInLastHour >= maxTransactionsPerHour) {
            log.warn("Hourly velocity limit exceeded for user: {}, transactions: {}, limit: {}", 
                userId, transactionsInLastHour, maxTransactionsPerHour);
            throw new TransferLimitExceededException(
                String.format("Hourly transaction limit of %d exceeded", maxTransactionsPerHour));
        }

        // Check daily velocity
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        int transactionsToday = getTransactionCountForPeriod(userId, startOfDay);
        
        if (transactionsToday >= maxTransactionsPerDay) {
            log.warn("Daily velocity limit exceeded for user: {}, transactions: {}, limit: {}", 
                userId, transactionsToday, maxTransactionsPerDay);
            throw new TransferLimitExceededException(
                String.format("Daily transaction limit of %d exceeded", maxTransactionsPerDay));
        }
    }

    private BigDecimal getUsedAmountForPeriod(String userId, LocalDateTime since) {
        return instantTransferRepository.sumAmountByUserIdAndCreatedAtAfter(userId, since);
    }

    private int getTransactionCountForPeriod(String userId, LocalDateTime since) {
        return instantTransferRepository.countByUserIdAndCreatedAtAfter(userId, since);
    }

    private UserRiskProfile getUserRiskProfile(String userId) {
        try {
            // This would call the user service or risk service to get the risk profile
            return userServiceClient.getUserRiskProfile(userId);
        } catch (Exception e) {
            log.warn("Failed to get risk profile for user: {}, using default", userId, e);
            return UserRiskProfile.builder()
                .userId(userId)
                .riskLevel("MEDIUM")
                .build();
        }
    }

    private BigDecimal applyRiskAdjustment(BigDecimal baseLimit, String riskLevel) {
        switch (riskLevel.toUpperCase()) {
            case "LOW":
                return baseLimit.multiply(new BigDecimal("1.5")); // 50% increase for low risk
            case "HIGH":
                return baseLimit.multiply(new BigDecimal("0.5")); // 50% reduction for high risk
            case "VERY_HIGH":
                return baseLimit.multiply(new BigDecimal("0.1")); // 90% reduction for very high risk
            case "MEDIUM":
            default:
                return baseLimit; // No adjustment for medium risk
        }
    }

    /**
     * Cleanup expired cache entries
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(CACHE_TTL_MINUTES);
        
        userLimitsCache.entrySet().removeIf(entry -> 
            entry.getValue().getCachedAt().isBefore(cutoff));
        
        log.debug("Cleaned up {} expired cache entries", userLimitsCache.size());
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class UserLimits {
        private String userId;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal transactionLimit;
        private String currency;
        private String riskLevel;
        private LocalDateTime cachedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class RemainingLimits {
        private String userId;
        private BigDecimal remainingDailyAmount;
        private BigDecimal remainingMonthlyAmount;
        private BigDecimal remainingSingleTransactionLimit;
        private int remainingDailyTransactions;
        private int remainingHourlyTransactions;
        private String currency;
        private LocalDateTime lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    public static class UserRiskProfile {
        private String userId;
        private String riskLevel;
        private BigDecimal riskScore;
        private LocalDateTime lastUpdated;
    }
}