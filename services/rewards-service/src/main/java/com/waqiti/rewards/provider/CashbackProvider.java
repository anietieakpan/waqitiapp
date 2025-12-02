package com.waqiti.rewards.provider;

import com.waqiti.rewards.domain.CashbackTransaction;
import com.waqiti.rewards.domain.MerchantRewards;
import com.waqiti.rewards.dto.ProcessCashbackRequest;
import com.waqiti.rewards.entity.UserRewardsAccount;
import com.waqiti.rewards.repository.CashbackTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Provider for cashback calculation and processing logic
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CashbackProvider {

    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Category codes and their base cashback rates
    private static final Map<String, BigDecimal> CATEGORY_RATES = new HashMap<>();
    static {
        // MCC (Merchant Category Codes) to cashback rate mapping
        CATEGORY_RATES.put("5411", new BigDecimal("0.02")); // Grocery stores
        CATEGORY_RATES.put("5541", new BigDecimal("0.015")); // Gas stations
        CATEGORY_RATES.put("5812", new BigDecimal("0.02")); // Restaurants
        CATEGORY_RATES.put("5311", new BigDecimal("0.015")); // Department stores
        CATEGORY_RATES.put("4511", new BigDecimal("0.025")); // Airlines
        CATEGORY_RATES.put("4722", new BigDecimal("0.025")); // Travel agencies
        CATEGORY_RATES.put("5945", new BigDecimal("0.01")); // Hobby shops
        CATEGORY_RATES.put("5912", new BigDecimal("0.01")); // Drug stores
        CATEGORY_RATES.put("5814", new BigDecimal("0.03")); // Fast food
        CATEGORY_RATES.put("5942", new BigDecimal("0.01")); // Book stores
        CATEGORY_RATES.put("5734", new BigDecimal("0.01")); // Computer software stores
        CATEGORY_RATES.put("5944", new BigDecimal("0.01")); // Jewelry stores
        CATEGORY_RATES.put("7011", new BigDecimal("0.02")); // Hotels
        CATEGORY_RATES.put("5310", new BigDecimal("0.005")); // Discount stores
        CATEGORY_RATES.put("5999", new BigDecimal("0.01")); // Miscellaneous retail
    }

    // Special merchant partnerships with enhanced rates
    private static final Map<String, BigDecimal> PARTNER_MERCHANT_RATES = new HashMap<>();
    static {
        PARTNER_MERCHANT_RATES.put("AMAZON", new BigDecimal("0.05"));
        PARTNER_MERCHANT_RATES.put("WALMART", new BigDecimal("0.03"));
        PARTNER_MERCHANT_RATES.put("TARGET", new BigDecimal("0.03"));
        PARTNER_MERCHANT_RATES.put("BESTBUY", new BigDecimal("0.04"));
        PARTNER_MERCHANT_RATES.put("WHOLEFOODS", new BigDecimal("0.04"));
        PARTNER_MERCHANT_RATES.put("COSTCO", new BigDecimal("0.025"));
        PARTNER_MERCHANT_RATES.put("STARBUCKS", new BigDecimal("0.05"));
        PARTNER_MERCHANT_RATES.put("UBER", new BigDecimal("0.03"));
        PARTNER_MERCHANT_RATES.put("LYFT", new BigDecimal("0.03"));
        PARTNER_MERCHANT_RATES.put("NETFLIX", new BigDecimal("0.02"));
        PARTNER_MERCHANT_RATES.put("SPOTIFY", new BigDecimal("0.02"));
    }

    // Promotional multipliers for special events
    private static final Map<String, BigDecimal> PROMO_MULTIPLIERS = new HashMap<>();
    static {
        PROMO_MULTIPLIERS.put("DOUBLE_CASHBACK_WEEKEND", new BigDecimal("2.0"));
        PROMO_MULTIPLIERS.put("TRIPLE_TUESDAY", new BigDecimal("3.0"));
        PROMO_MULTIPLIERS.put("BLACK_FRIDAY", new BigDecimal("5.0"));
        PROMO_MULTIPLIERS.put("CYBER_MONDAY", new BigDecimal("4.0"));
        PROMO_MULTIPLIERS.put("NEW_USER_BONUS", new BigDecimal("10.0"));
        PROMO_MULTIPLIERS.put("BIRTHDAY_BONUS", new BigDecimal("2.0"));
    }

    /**
     * Calculate base cashback rate for a merchant category
     */
    @NonNull
    public BigDecimal getBaseCashbackRate(@Nullable String merchantCategory) {
        return CATEGORY_RATES.getOrDefault(merchantCategory, new BigDecimal("0.01")); // 1% default
    }

    /**
     * Get enhanced rate for partner merchants
     */
    @NonNull
    public BigDecimal getPartnerMerchantRate(@Nullable String merchantName) {
        if (merchantName == null) {
            log.debug("Merchant name is null - returning zero cashback rate");
            return BigDecimal.ZERO;
        }
        
        String normalizedName = merchantName.toUpperCase().replaceAll("[^A-Z0-9]", "");
        
        // Check exact match first
        if (PARTNER_MERCHANT_RATES.containsKey(normalizedName)) {
            return PARTNER_MERCHANT_RATES.get(normalizedName);
        }
        
        // Check if merchant name contains any partner name
        for (Map.Entry<String, BigDecimal> entry : PARTNER_MERCHANT_RATES.entrySet()) {
            if (normalizedName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // No partner rate found - return zero cashback
        log.debug("No partner cashback rate found for merchant: {}", merchantName);
        return BigDecimal.ZERO;
    }

    /**
     * Calculate promotional multiplier if applicable
     */
    @NonNull
    public BigDecimal getPromoMultiplier(@Nullable Map<String, Object> metadata) {
        if (metadata == null || !metadata.containsKey("promo_code")) {
            return BigDecimal.ONE;
        }
        
        String promoCode = (String) metadata.get("promo_code");
        return PROMO_MULTIPLIERS.getOrDefault(promoCode.toUpperCase(), BigDecimal.ONE);
    }

    /**
     * Calculate final cashback amount considering all factors
     */
    @NonNull
    public BigDecimal calculateCashback(@NonNull ProcessCashbackRequest request, @Nullable UserRewardsAccount account) {
        // Start with base rate for the category
        BigDecimal rate = getBaseCashbackRate(request.getMerchantCategory());
        
        // Check for partner merchant enhanced rate
        BigDecimal partnerRate = getPartnerMerchantRate(request.getMerchantName());
        if (partnerRate != null && partnerRate.compareTo(rate) > 0) {
            rate = partnerRate;
            log.debug("Applied partner merchant rate: {} for {}", partnerRate, request.getMerchantName());
        }
        
        // Apply tier multiplier based on user's loyalty tier
        BigDecimal tierMultiplier = getTierMultiplier(account);
        rate = rate.multiply(tierMultiplier);
        
        // Apply promotional multipliers
        BigDecimal promoMultiplier = getPromoMultiplier(request.getMetadata());
        rate = rate.multiply(promoMultiplier);
        
        // Apply spending-based bonus
        BigDecimal spendingBonus = calculateSpendingBonus(request.getTransactionAmount());
        rate = rate.add(spendingBonus);
        
        // Ensure rate doesn't exceed maximum allowed (10%)
        BigDecimal maxRate = new BigDecimal("0.10");
        if (rate.compareTo(maxRate) > 0) {
            rate = maxRate;
            log.info("Cashback rate capped at maximum: {}", maxRate);
        }
        
        // Calculate final cashback amount
        BigDecimal cashbackAmount = request.getTransactionAmount()
            .multiply(rate)
            .setScale(2, RoundingMode.DOWN);
        
        log.info("Calculated cashback: {} (rate: {}) for transaction amount: {}", 
            cashbackAmount, rate, request.getTransactionAmount());
        
        return cashbackAmount;
    }

    /**
     * Get tier multiplier based on user's loyalty tier
     */
    @NonNull
    private BigDecimal getTierMultiplier(@Nullable UserRewardsAccount account) {
        if (account == null || account.getCurrentTier() == null) {
            return BigDecimal.ONE;
        }
        
        switch (account.getCurrentTier()) {
            case BRONZE:
                return new BigDecimal("1.0");
            case SILVER:
                return new BigDecimal("1.25");
            case GOLD:
                return new BigDecimal("1.5");
            case PLATINUM:
                return new BigDecimal("2.0");
            default:
                return BigDecimal.ONE;
        }
    }

    /**
     * Calculate additional bonus based on transaction amount
     */
    @NonNull
    private BigDecimal calculateSpendingBonus(@NonNull BigDecimal transactionAmount) {
        if (transactionAmount.compareTo(new BigDecimal("1000")) >= 0) {
            return new BigDecimal("0.005"); // Additional 0.5% for transactions over $1000
        } else if (transactionAmount.compareTo(new BigDecimal("500")) >= 0) {
            return new BigDecimal("0.0025"); // Additional 0.25% for transactions over $500
        } else if (transactionAmount.compareTo(new BigDecimal("200")) >= 0) {
            return new BigDecimal("0.001"); // Additional 0.1% for transactions over $200
        }
        return BigDecimal.ZERO;
    }

    /**
     * Validate if cashback can be processed
     */
    public boolean canProcessCashback(@NonNull ProcessCashbackRequest request, @Nullable UserRewardsAccount account) {
        // Check if account is active
        if (account == null || !account.isActive()) {
            log.warn("Cannot process cashback - account not active");
            return false;
        }
        
        // Check minimum transaction amount ($1)
        if (request.getTransactionAmount().compareTo(BigDecimal.ONE) < 0) {
            log.debug("Transaction amount too small for cashback: {}", request.getTransactionAmount());
            return false;
        }
        
        // Check if merchant category is eligible
        if (isExcludedCategory(request.getMerchantCategory())) {
            log.debug("Merchant category excluded from cashback: {}", request.getMerchantCategory());
            return false;
        }
        
        // Check for duplicate transaction
        if (isDuplicateTransaction(request)) {
            log.warn("Duplicate transaction detected: {}", request.getTransactionId());
            return false;
        }
        
        return true;
    }

    /**
     * Check if merchant category is excluded from cashback
     */
    private boolean isExcludedCategory(@Nullable String category) {
        if (category == null) {
            return false;
        }
        
        // Excluded categories (financial services, gambling, etc.)
        String[] excludedCategories = {
            "6010", // Financial institutions
            "6011", // ATMs
            "6012", // Financial institutions merchandise
            "7995", // Gambling
            "9399", // Government services
            "4829", // Money transfers
            "6051", // Quasi cash
            "6540", // Stored value card purchase
            "8999", // Professional services
            "9211", // Court costs
            "9222", // Fines
            "9311", // Tax payments
        };
        
        for (String excluded : excludedCategories) {
            if (excluded.equals(category)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check for duplicate transactions using multiple detection strategies
     */
    private boolean isDuplicateTransaction(ProcessCashbackRequest request) {
        try {
            // Strategy 1: Check Redis cache for recent duplicate transactions
            String duplicateKey = generateDuplicateKey(request);
            Boolean recentDuplicate = (Boolean) redisTemplate.opsForValue().get(duplicateKey);
            if (Boolean.TRUE.equals(recentDuplicate)) {
                log.warn("Duplicate cashback detected in cache for transaction: {}", request.getTransactionId());
                return true;
            }

            // Strategy 2: Check database for transactions in sliding window (last 5 minutes)
            LocalDateTime windowStart = LocalDateTime.now().minus(5, ChronoUnit.MINUTES);
            boolean dbDuplicate = cashbackTransactionRepository.existsByUserIdAndMerchantIdAndAmountAndTransactionTimeBetween(
                request.getUserId(),
                request.getMerchantId(), 
                request.getTransactionAmount(),
                windowStart,
                LocalDateTime.now()
            );
            
            if (dbDuplicate) {
                log.warn("Duplicate cashback detected in database for user: {} merchant: {} amount: {}", 
                    request.getUserId(), request.getMerchantId(), request.getTransactionAmount());
                return true;
            }

            // Strategy 3: Check for exact transaction ID duplicates
            boolean transactionIdDuplicate = cashbackTransactionRepository.existsByTransactionId(request.getTransactionId());
            if (transactionIdDuplicate) {
                log.warn("Transaction ID already processed for cashback: {}", request.getTransactionId());
                return true;
            }

            // Strategy 4: Advanced pattern detection - same user, merchant, amount within 1 minute
            LocalDateTime strictWindow = LocalDateTime.now().minus(1, ChronoUnit.MINUTES);
            boolean patternDuplicate = cashbackTransactionRepository.countByUserIdAndMerchantIdAndAmountAndTransactionTimeBetween(
                request.getUserId(),
                request.getMerchantId(),
                request.getTransactionAmount(),
                strictWindow,
                LocalDateTime.now()
            ) > 0;

            if (patternDuplicate) {
                log.warn("Suspicious pattern detected - potential duplicate for user: {} merchant: {} amount: {}", 
                    request.getUserId(), request.getMerchantId(), request.getTransactionAmount());
                return true;
            }

            // Cache the transaction to prevent immediate duplicates
            redisTemplate.opsForValue().set(duplicateKey, true, 10, TimeUnit.MINUTES);
            
            return false;

        } catch (Exception e) {
            log.error("Error checking for duplicate transactions", e);
            // Default to safe behavior - treat as potential duplicate to prevent financial loss
            return true;
        }
    }

    /**
     * Generate unique key for duplicate detection caching
     */
    private String generateDuplicateKey(ProcessCashbackRequest request) {
        return String.format("cashback:duplicate:%s:%s:%s:%s", 
            request.getUserId(), 
            request.getMerchantId(), 
            request.getTransactionAmount().toString(),
            request.getTransactionId()
        );
    }

    /**
     * Check if transaction is eligible for cashback (not a refund, valid amount, etc.)
     */
    private boolean isTransactionEligible(ProcessCashbackRequest request) {
        // Check minimum transaction amount (e.g., $1.00)
        if (request.getTransactionAmount().compareTo(new BigDecimal("1.00")) < 0) {
            log.debug("Transaction amount too small for cashback: {}", request.getTransactionAmount());
            return false;
        }

        // Check maximum transaction amount (e.g., $10,000 to prevent fraud)
        if (request.getTransactionAmount().compareTo(new BigDecimal("10000.00")) > 0) {
            log.warn("Transaction amount too large for automatic cashback: {}", request.getTransactionAmount());
            return false;
        }

        // Check if transaction is a refund (negative amount)
        if (request.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Refund transaction not eligible for cashback: {}", request.getTransactionAmount());
            return false;
        }

        // Check if merchant is blacklisted
        if (isMerchantBlacklisted(request.getMerchantId())) {
            log.warn("Merchant blacklisted for cashback: {}", request.getMerchantId());
            return false;
        }

        return true;
    }

    /**
     * Check if merchant is blacklisted for cashback
     */
    private boolean isMerchantBlacklisted(String merchantId) {
        String blacklistKey = "cashback:merchant:blacklist:" + merchantId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
    }

    /**
     * Get cashback eligibility information
     */
    public Map<String, Object> getCashbackEligibility(String merchantCategory, BigDecimal transactionAmount) {
        Map<String, Object> eligibility = new HashMap<>();
        
        boolean isEligible = !isExcludedCategory(merchantCategory) && 
                           transactionAmount.compareTo(BigDecimal.ONE) >= 0;
        
        eligibility.put("eligible", isEligible);
        eligibility.put("baseRate", getBaseCashbackRate(merchantCategory));
        eligibility.put("excludedCategory", isExcludedCategory(merchantCategory));
        eligibility.put("minimumAmount", BigDecimal.ONE);
        
        if (!isEligible) {
            if (isExcludedCategory(merchantCategory)) {
                eligibility.put("reason", "Merchant category is excluded from cashback");
            } else if (transactionAmount.compareTo(BigDecimal.ONE) < 0) {
                eligibility.put("reason", "Transaction amount is below minimum");
            }
        }
        
        return eligibility;
    }

    /**
     * Apply special rules for specific merchants or situations
     */
    public BigDecimal applySpecialRules(BigDecimal calculatedCashback, ProcessCashbackRequest request) {
        // Apply daily cap per merchant
        BigDecimal dailyMerchantCap = new BigDecimal("50.00");
        if (calculatedCashback.compareTo(dailyMerchantCap) > 0) {
            log.info("Cashback capped at daily merchant limit: {}", dailyMerchantCap);
            return dailyMerchantCap;
        }
        
        // Apply monthly category cap
        String category = request.getMerchantCategory();
        if ("5411".equals(category)) { // Grocery stores
            BigDecimal monthlyCategoryCap = new BigDecimal("100.00");
            BigDecimal monthlyTotal = getMonthlyTotalForCategory(request.getUserId(), category);
            if (monthlyTotal.add(calculatedCashback).compareTo(monthlyCategoryCap) > 0) {
                BigDecimal cappedAmount = monthlyCategoryCap.subtract(monthlyTotal);
                if (cappedAmount.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Monthly grocery cashback capped at {}", cappedAmount);
                    return cappedAmount;
                } else {
                    log.info("Monthly grocery cashback limit reached");
                    return BigDecimal.ZERO;
                }
            }
        }
        
        return calculatedCashback;
    }

    /**
     * Get promotional campaigns for a specific date
     */
    public Map<String, BigDecimal> getActivePromotions(LocalDateTime transactionDate) {
        Map<String, BigDecimal> activePromos = new HashMap<>();
        
        // Check day of week promotions
        switch (transactionDate.getDayOfWeek()) {
            case TUESDAY:
                activePromos.put("TRIPLE_TUESDAY", new BigDecimal("3.0"));
                break;
            case FRIDAY:
            case SATURDAY:
            case SUNDAY:
                activePromos.put("DOUBLE_CASHBACK_WEEKEND", new BigDecimal("2.0"));
                break;
        }
        
        // Check special date promotions
        if (transactionDate.getMonthValue() == 11 && transactionDate.getDayOfMonth() == 24) {
            activePromos.put("BLACK_FRIDAY", new BigDecimal("5.0"));
        }
        
        if (transactionDate.getMonthValue() == 11 && transactionDate.getDayOfMonth() == 27) {
            activePromos.put("CYBER_MONDAY", new BigDecimal("4.0"));
        }
        
        return activePromos;
    }
    
    private BigDecimal getMonthlyTotalForCategory(String userId, String category) {
        try {
            String monthKey = String.format("cashback:monthly:%s:%s:%s", 
                userId, category, java.time.YearMonth.now().toString());
            String totalStr = (String) redisTemplate.opsForValue().get(monthKey);
            return totalStr != null ? new BigDecimal(totalStr) : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Error retrieving monthly cashback total", e);
            return BigDecimal.ZERO;
        }
    }
}