package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform Reserve Service - Manages platform-level financial reserves
 * 
 * Handles critical platform reserve operations for:
 * - Chargeback liability coverage and risk mitigation
 * - Fraud loss provisioning and operational risk reserves
 * - Regulatory capital adequacy and compliance requirements
 * - Emergency fund allocation and crisis management
 * - Platform liquidity management and cash flow optimization
 * - Reserve adequacy assessment and threshold monitoring
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformReserveService {

    private final LedgerServiceImpl ledgerService;
    private final RedisTemplate<String, Object> redisTemplate;

    // Reserve configuration constants
    private static final BigDecimal CHARGEBACK_RESERVE_RATE = new BigDecimal("0.15"); // 15%
    private static final BigDecimal FRAUD_RESERVE_RATE = new BigDecimal("0.10"); // 10%
    private static final BigDecimal OPERATIONAL_RESERVE_RATE = new BigDecimal("0.05"); // 5%
    private static final BigDecimal REGULATORY_RESERVE_RATE = new BigDecimal("0.08"); // 8%
    private static final BigDecimal EMERGENCY_RESERVE_MULTIPLIER = new BigDecimal("2.0");
    
    // Liquidity thresholds
    private static final BigDecimal MIN_LIQUIDITY_RATIO = new BigDecimal("0.20"); // 20%
    private static final BigDecimal CRITICAL_LIQUIDITY_RATIO = new BigDecimal("0.10"); // 10%
    private static final BigDecimal TARGET_RESERVE_RATIO = new BigDecimal("0.25"); // 25%

    @Value("${platform.reserve.enabled:true}")
    private boolean platformReserveEnabled;

    @Value("${platform.reserve.max.single:1000000}")
    private BigDecimal maxSingleReserveAmount;

    @Value("${platform.reserve.emergency.multiplier:3.0}")
    private BigDecimal emergencyReserveMultiplier;

    // Cache for tracking reserves
    private final Map<String, PlatformReserve> activeReserves = new ConcurrentHashMap<>();

    /**
     * Creates a new platform reserve allocation
     * 
     * @param reserveRequestId Unique reserve request identifier
     * @param amount Reserve amount
     * @param currency Currency code
     * @param reserveType Type of reserve
     * @param reason Reserve reason
     * @param durationDays Reserve duration in days
     * @param emergencyReserve Whether this is an emergency reserve
     * @return true if reserve created successfully
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean createPlatformReserve(
            String reserveRequestId,
            BigDecimal amount,
            String currency,
            ReserveType reserveType,
            String reason,
            Integer durationDays,
            boolean emergencyReserve) {

        if (!platformReserveEnabled) {
            log.warn("Platform reserve creation disabled: {}", reserveRequestId);
            return false;
        }

        try {
            log.info("Creating platform reserve: {} - Amount: {} {} - Type: {}", 
                reserveRequestId, amount, currency, reserveType);

            // Validate reserve request
            if (!validateReserveRequest(amount, currency, reserveType)) {
                log.error("Invalid reserve request: {}", reserveRequestId);
                return false;
            }

            // Calculate adjusted reserve amount
            BigDecimal adjustedAmount = calculateAdjustedReserveAmount(
                amount, reserveType, emergencyReserve);

            // Check platform liquidity capacity
            if (!checkLiquidityCapacity(adjustedAmount, currency)) {
                log.error("Insufficient platform liquidity for reserve: {}", reserveRequestId);
                return false;
            }

            // Create platform reserve record
            PlatformReserve reserve = createReserveRecord(
                reserveRequestId, adjustedAmount, currency, reserveType, 
                reason, durationDays, emergencyReserve);

            // Allocate funds from platform liquidity
            boolean allocationSuccessful = allocateReserveFunds(
                reserve, adjustedAmount, currency);

            if (allocationSuccessful) {
                // Store reserve in cache
                activeReserves.put(reserveRequestId, reserve);
                
                // Update reserve metrics in Redis
                updateReserveMetrics(adjustedAmount, currency, reserveType);
                
                log.info("Successfully created platform reserve: {} for {} {}", 
                    reserveRequestId, adjustedAmount, currency);
                return true;
            } else {
                log.error("Failed to allocate reserve funds: {}", reserveRequestId);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to create platform reserve: {}", reserveRequestId, e);
            return false;
        }
    }

    /**
     * Updates platform liquidity metrics
     * 
     * @param amount Reserve amount
     * @param currency Currency code
     * @param reserveType Reserve type
     */
    public void updatePlatformLiquidityMetrics(
            BigDecimal amount, String currency, ReserveType reserveType) {
        
        try {
            log.debug("Updating platform liquidity metrics for {} {} reserve", amount, currency);

            // Update total reserves by currency
            String totalReserveKey = "platform:reserves:total:" + currency;
            redisTemplate.opsForValue().increment(totalReserveKey, amount.doubleValue());
            redisTemplate.expire(totalReserveKey, Duration.ofDays(365));

            // Update reserves by type
            String typeReserveKey = "platform:reserves:" + reserveType.toString().toLowerCase() + ":" + currency;
            redisTemplate.opsForValue().increment(typeReserveKey, amount.doubleValue());
            redisTemplate.expire(typeReserveKey, Duration.ofDays(365));

            // Update reserve creation timestamp
            String timestampKey = "platform:reserves:last_update:" + currency;
            redisTemplate.opsForValue().set(timestampKey, LocalDateTime.now().toString(), Duration.ofDays(7));

            // Calculate and update reserve ratios
            updateReserveRatios(currency);

            log.debug("Successfully updated platform liquidity metrics for {} {}", amount, currency);

        } catch (Exception e) {
            log.error("Failed to update platform liquidity metrics", e);
        }
    }

    /**
     * Checks platform liquidity thresholds and alerts
     * 
     * @param currency Currency to check
     */
    public void checkLiquidityThresholds(String currency) {
        try {
            log.debug("Checking liquidity thresholds for currency: {}", currency);

            // Get current liquidity metrics
            BigDecimal totalLiquidity = getCurrentTotalLiquidity(currency);
            BigDecimal totalReserves = getCurrentTotalReserves(currency);
            
            if (totalLiquidity.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("Zero platform liquidity detected for currency: {}", currency);
                return;
            }

            // Calculate liquidity ratio
            BigDecimal liquidityRatio = totalLiquidity.subtract(totalReserves)
                .divide(totalLiquidity, 4, RoundingMode.HALF_UP);

            // Check thresholds
            if (liquidityRatio.compareTo(CRITICAL_LIQUIDITY_RATIO) <= 0) {
                triggerCriticalLiquidityAlert(currency, liquidityRatio, totalLiquidity, totalReserves);
            } else if (liquidityRatio.compareTo(MIN_LIQUIDITY_RATIO) <= 0) {
                triggerLowLiquidityAlert(currency, liquidityRatio, totalLiquidity, totalReserves);
            }

            // Update liquidity ratio in cache
            String ratioKey = "platform:liquidity:ratio:" + currency;
            redisTemplate.opsForValue().set(ratioKey, liquidityRatio.toString(), Duration.ofHours(1));

            log.debug("Liquidity threshold check completed for {}: ratio={}", currency, liquidityRatio);

        } catch (Exception e) {
            log.error("Failed to check liquidity thresholds for currency: {}", currency, e);
        }
    }

    /**
     * Validates reserve request parameters
     */
    private boolean validateReserveRequest(BigDecimal amount, String currency, ReserveType reserveType) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Invalid reserve amount: {}", amount);
            return false;
        }

        if (amount.compareTo(maxSingleReserveAmount) > 0) {
            log.error("Reserve amount exceeds maximum: {} > {}", amount, maxSingleReserveAmount);
            return false;
        }

        if (currency == null || currency.length() != 3) {
            log.error("Invalid currency code: {}", currency);
            return false;
        }

        if (reserveType == null) {
            log.error("Reserve type is required");
            return false;
        }

        return true;
    }

    /**
     * Calculates adjusted reserve amount based on type and emergency status
     */
    private BigDecimal calculateAdjustedReserveAmount(
            BigDecimal baseAmount, ReserveType reserveType, boolean emergencyReserve) {
        
        BigDecimal reserveRate = getReserveRateForType(reserveType);
        BigDecimal adjustedAmount = baseAmount.multiply(reserveRate);

        if (emergencyReserve) {
            adjustedAmount = adjustedAmount.multiply(emergencyReserveMultiplier);
        }

        return adjustedAmount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Gets reserve rate for specific reserve type
     */
    private BigDecimal getReserveRateForType(ReserveType reserveType) {
        switch (reserveType) {
            case CHARGEBACK:
                return CHARGEBACK_RESERVE_RATE;
            case FRAUD:
                return FRAUD_RESERVE_RATE;
            case OPERATIONAL:
                return OPERATIONAL_RESERVE_RATE;
            case REGULATORY:
                return REGULATORY_RESERVE_RATE;
            case EMERGENCY:
                return EMERGENCY_RESERVE_MULTIPLIER;
            default:
                return OPERATIONAL_RESERVE_RATE;
        }
    }

    /**
     * Checks if platform has sufficient liquidity capacity
     */
    private boolean checkLiquidityCapacity(BigDecimal amount, String currency) {
        try {
            BigDecimal currentLiquidity = getCurrentAvailableLiquidity(currency);
            return currentLiquidity.compareTo(amount) >= 0;
        } catch (Exception e) {
            log.error("Failed to check liquidity capacity", e);
            return false;
        }
    }

    /**
     * Creates platform reserve record
     */
    private PlatformReserve createReserveRecord(
            String reserveRequestId, BigDecimal amount, String currency, 
            ReserveType reserveType, String reason, Integer durationDays, 
            boolean emergencyReserve) {
        
        return PlatformReserve.builder()
            .reserveId(reserveRequestId)
            .amount(amount)
            .currency(currency)
            .reserveType(reserveType)
            .reason(reason)
            .durationDays(durationDays)
            .emergencyReserve(emergencyReserve)
            .status(ReserveStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .expiresAt(durationDays != null ? 
                LocalDateTime.now().plusDays(durationDays) : null)
            .build();
    }

    /**
     * Allocates reserve funds from platform liquidity
     */
    private boolean allocateReserveFunds(PlatformReserve reserve, BigDecimal amount, String currency) {
        try {
            // Create ledger entries for fund allocation
            ledgerService.createDoubleEntry(
                reserve.getReserveId() + "_allocation",
                "PLATFORM_RESERVE_ALLOCATION",
                reserve.getReason(),
                "PLATFORM",
                amount,
                currency,
                "PLATFORM_LIQUIDITY", // Debit available liquidity
                "PLATFORM_RESERVES", // Credit allocated reserves
                LocalDateTime.now(),
                Map.of(
                    "reserve_id", reserve.getReserveId(),
                    "reserve_type", reserve.getReserveType().toString(),
                    "emergency_reserve", String.valueOf(reserve.isEmergencyReserve())
                )
            );

            return true;
        } catch (Exception e) {
            log.error("Failed to allocate reserve funds for reserve: {}", reserve.getReserveId(), e);
            return false;
        }
    }

    /**
     * Updates reserve metrics in Redis
     */
    private void updateReserveMetrics(BigDecimal amount, String currency, ReserveType reserveType) {
        try {
            // Update reserve allocation count
            String countKey = "platform:reserves:count:" + reserveType.toString().toLowerCase();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(30));

            // Update reserve amount by type
            String amountKey = "platform:reserves:amount:" + reserveType.toString().toLowerCase() + ":" + currency;
            redisTemplate.opsForValue().increment(amountKey, amount.doubleValue());
            redisTemplate.expire(amountKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to update reserve metrics", e);
        }
    }

    /**
     * Updates reserve ratios for currency
     */
    private void updateReserveRatios(String currency) {
        try {
            BigDecimal totalLiquidity = getCurrentTotalLiquidity(currency);
            BigDecimal totalReserves = getCurrentTotalReserves(currency);

            if (totalLiquidity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal reserveRatio = totalReserves.divide(totalLiquidity, 4, RoundingMode.HALF_UP);
                
                String ratioKey = "platform:reserves:ratio:" + currency;
                redisTemplate.opsForValue().set(ratioKey, reserveRatio.toString(), Duration.ofHours(1));
            }

        } catch (Exception e) {
            log.error("Failed to update reserve ratios for currency: {}", currency, e);
        }
    }

    /**
     * Gets current total liquidity for currency
     */
    private BigDecimal getCurrentTotalLiquidity(String currency) {
        try {
            String liquidityKey = "platform:liquidity:total:" + currency;
            String liquidityStr = (String) redisTemplate.opsForValue().get(liquidityKey);
            return liquidityStr != null ? new BigDecimal(liquidityStr) : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to get current total liquidity", e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Gets current total reserves for currency
     */
    private BigDecimal getCurrentTotalReserves(String currency) {
        try {
            String reserveKey = "platform:reserves:total:" + currency;
            Double reserveValue = (Double) redisTemplate.opsForValue().get(reserveKey);
            return reserveValue != null ? BigDecimal.valueOf(reserveValue) : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to get current total reserves", e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Gets current available liquidity for currency
     */
    private BigDecimal getCurrentAvailableLiquidity(String currency) {
        BigDecimal totalLiquidity = getCurrentTotalLiquidity(currency);
        BigDecimal totalReserves = getCurrentTotalReserves(currency);
        return totalLiquidity.subtract(totalReserves);
    }

    /**
     * Triggers critical liquidity alert
     */
    private void triggerCriticalLiquidityAlert(String currency, BigDecimal ratio, 
            BigDecimal liquidity, BigDecimal reserves) {
        log.error("CRITICAL: Platform liquidity ratio below threshold for {}: {}% (Available: {} {}, Reserved: {} {})", 
            currency, ratio.multiply(new BigDecimal("100")), 
            liquidity.subtract(reserves), currency, reserves, currency);
        
        // Store alert in Redis for monitoring
        String alertKey = "platform:alerts:critical_liquidity:" + currency;
        Map<String, String> alertData = Map.of(
            "currency", currency,
            "ratio", ratio.toString(),
            "available_liquidity", liquidity.subtract(reserves).toString(),
            "total_reserves", reserves.toString(),
            "timestamp", LocalDateTime.now().toString()
        );
        redisTemplate.opsForHash().putAll(alertKey, alertData);
        redisTemplate.expire(alertKey, Duration.ofDays(1));
    }

    /**
     * Triggers low liquidity alert
     */
    private void triggerLowLiquidityAlert(String currency, BigDecimal ratio, 
            BigDecimal liquidity, BigDecimal reserves) {
        log.warn("WARNING: Platform liquidity ratio below minimum for {}: {}% (Available: {} {}, Reserved: {} {})", 
            currency, ratio.multiply(new BigDecimal("100")), 
            liquidity.subtract(reserves), currency, reserves, currency);
        
        // Store alert in Redis for monitoring
        String alertKey = "platform:alerts:low_liquidity:" + currency;
        Map<String, String> alertData = Map.of(
            "currency", currency,
            "ratio", ratio.toString(),
            "available_liquidity", liquidity.subtract(reserves).toString(),
            "total_reserves", reserves.toString(),
            "timestamp", LocalDateTime.now().toString()
        );
        redisTemplate.opsForHash().putAll(alertKey, alertData);
        redisTemplate.expire(alertKey, Duration.ofHours(6));
    }

    /**
     * Platform reserve data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class PlatformReserve {
        private String reserveId;
        private BigDecimal amount;
        private String currency;
        private ReserveType reserveType;
        private String reason;
        private Integer durationDays;
        private boolean emergencyReserve;
        private ReserveStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private LocalDateTime releasedAt;
    }

    /**
     * Reserve types
     */
    public enum ReserveType {
        CHARGEBACK,      // Chargeback liability coverage
        FRAUD,           // Fraud loss coverage
        OPERATIONAL,     // Operational risk coverage
        EMERGENCY,       // Emergency fund allocation
        REGULATORY       // Regulatory capital requirements
    }

    /**
     * Reserve status
     */
    public enum ReserveStatus {
        ACTIVE,          // Reserve is active
        EXPIRED,         // Reserve has expired
        RELEASED,        // Reserve has been released
        CANCELLED        // Reserve has been cancelled
    }

    /**
     * Custom exception for platform reserve operations
     */
    public static class PlatformReserveException extends RuntimeException {
        public PlatformReserveException(String message) {
            super(message);
        }
        
        public PlatformReserveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}