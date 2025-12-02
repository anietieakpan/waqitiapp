package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.entity.TransactionVelocity;
import com.waqiti.frauddetection.repository.TransactionVelocityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VelocityCheckService {

    private final TransactionVelocityRepository velocityRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // CONFIGURE_IN_VAULT: Velocity check time windows - configured externally
    private static final String VELOCITY_KEY_PREFIX = "velocity:";

    public double checkVelocity(FraudCheckRequest request) {
        String userId = request.getUserId();

        // CONFIGURE_IN_VAULT: Time windows for velocity checks - configured externally
        int shortWindowMinutes = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_SHORT_WINDOW_MINUTES", "5"));
        int mediumWindowMinutes = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_MEDIUM_WINDOW_MINUTES", "60"));
        int longWindowHours = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_LONG_WINDOW_HOURS", "24"));

        // Check transaction velocity in different time windows
        int shortWindowCount = getTransactionCount(userId, shortWindowMinutes, ChronoUnit.MINUTES);
        int mediumWindowCount = getTransactionCount(userId, mediumWindowMinutes, ChronoUnit.MINUTES);
        int longWindowCount = getTransactionCount(userId, longWindowHours, ChronoUnit.HOURS);

        BigDecimal shortWindowAmount = getTransactionAmount(userId, shortWindowMinutes, ChronoUnit.MINUTES);
        BigDecimal mediumWindowAmount = getTransactionAmount(userId, mediumWindowMinutes, ChronoUnit.MINUTES);
        BigDecimal longWindowAmount = getTransactionAmount(userId, longWindowHours, ChronoUnit.HOURS);
        
        // CONFIGURE_IN_VAULT: Velocity thresholds and risk scores - configured externally
        double score = 0.0;

        // Short window checks - configured externally
        int shortCountThreshold = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_SHORT_COUNT_THRESHOLD", "3"));
        double shortCountScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SHORT_COUNT_SCORE", "0.3"));
        BigDecimal shortAmountThreshold = new BigDecimal(System.getenv().getOrDefault("FRAUD_VELOCITY_SHORT_AMOUNT_THRESHOLD", "1000"));
        double shortAmountScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SHORT_AMOUNT_SCORE", "0.2"));

        if (shortWindowCount > shortCountThreshold) score += shortCountScore;
        if (shortWindowAmount.compareTo(shortAmountThreshold) > 0) score += shortAmountScore;

        // Medium window checks - configured externally
        int mediumCountThreshold = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_MEDIUM_COUNT_THRESHOLD", "10"));
        double mediumCountScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_MEDIUM_COUNT_SCORE", "0.2"));
        BigDecimal mediumAmountThreshold = new BigDecimal(System.getenv().getOrDefault("FRAUD_VELOCITY_MEDIUM_AMOUNT_THRESHOLD", "5000"));
        double mediumAmountScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_MEDIUM_AMOUNT_SCORE", "0.15"));

        if (mediumWindowCount > mediumCountThreshold) score += mediumCountScore;
        if (mediumWindowAmount.compareTo(mediumAmountThreshold) > 0) score += mediumAmountScore;

        // Long window checks - configured externally
        int longCountThreshold = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_LONG_COUNT_THRESHOLD", "50"));
        double longCountScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_LONG_COUNT_SCORE", "0.1"));
        BigDecimal longAmountThreshold = new BigDecimal(System.getenv().getOrDefault("FRAUD_VELOCITY_LONG_AMOUNT_THRESHOLD", "10000"));
        double longAmountScore = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_LONG_AMOUNT_SCORE", "0.05"));

        if (longWindowCount > longCountThreshold) score += longCountScore;
        if (longWindowAmount.compareTo(longAmountThreshold) > 0) score += longAmountScore;
        
        // Check for sudden spike in activity
        double spikeScore = checkActivitySpike(userId, shortWindowCount, mediumWindowCount);
        score += spikeScore;
        
        // Update velocity tracking
        updateVelocityTracking(request);
        
        return Math.min(score, 1.0); // Cap at 1.0
    }

    private int getTransactionCount(String userId, int window, ChronoUnit unit) {
        String key = VELOCITY_KEY_PREFIX + "count:" + userId + ":" + unit.toString().toLowerCase();
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            // Fallback to database if not in cache
            LocalDateTime since = LocalDateTime.now().minus(window, unit);
            count = velocityRepository.countByUserIdAndTimestampAfter(userId, since);
            
            // Cache for future use
            redisTemplate.opsForValue().set(key, count, window, 
                unit == ChronoUnit.HOURS ? TimeUnit.HOURS : TimeUnit.MINUTES);
        }
        
        return count;
    }

    private BigDecimal getTransactionAmount(String userId, int window, ChronoUnit unit) {
        String key = VELOCITY_KEY_PREFIX + "amount:" + userId + ":" + unit.toString().toLowerCase();
        BigDecimal amount = (BigDecimal) redisTemplate.opsForValue().get(key);
        
        if (amount == null) {
            LocalDateTime since = LocalDateTime.now().minus(window, unit);
            amount = velocityRepository.sumAmountByUserIdAndTimestampAfter(userId, since);
            if (amount == null) amount = BigDecimal.ZERO;
            
            redisTemplate.opsForValue().set(key, amount, window,
                unit == ChronoUnit.HOURS ? TimeUnit.HOURS : TimeUnit.MINUTES);
        }
        
        return amount;
    }

    private double checkActivitySpike(String userId, int shortCount, int mediumCount) {
        // Get historical average
        String avgKey = VELOCITY_KEY_PREFIX + "avg:" + userId;
        Double historicalAvg = (Double) redisTemplate.opsForValue().get(avgKey);

        if (historicalAvg == null) {
            // Calculate from database
            historicalAvg = velocityRepository.getAverageHourlyTransactions(userId, 30);
            redisTemplate.opsForValue().set(avgKey, historicalAvg, 1, TimeUnit.HOURS);
        }

        // CONFIGURE_IN_VAULT: Activity spike thresholds and scores - configured externally
        if (historicalAvg > 0) {
            double currentRate = mediumCount; // Hourly rate
            double spikeRatio = currentRate / historicalAvg;

            double spikeRatio5x = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SPIKE_5X_THRESHOLD", "5"));
            double spikeScore5x = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SPIKE_5X_SCORE", "0.3"));
            double spikeRatio3x = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SPIKE_3X_THRESHOLD", "3"));
            double spikeScore3x = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SPIKE_3X_SCORE", "0.2"));
            double spikeRatio2x = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SPIKE_2X_THRESHOLD", "2"));
            double spikeScore2x = Double.parseDouble(System.getenv().getOrDefault("FRAUD_VELOCITY_SPIKE_2X_SCORE", "0.1"));

            if (spikeRatio > spikeRatio5x) return spikeScore5x;
            if (spikeRatio > spikeRatio3x) return spikeScore3x;
            if (spikeRatio > spikeRatio2x) return spikeScore2x;
        }

        return 0.0;
    }

    private void updateVelocityTracking(FraudCheckRequest request) {
        // Save to database
        TransactionVelocity velocity = TransactionVelocity.builder()
            .userId(request.getUserId())
            .transactionId(request.getTransactionId())
            .amount(request.getAmount())
            .timestamp(LocalDateTime.now())
            .build();
        
        velocityRepository.save(velocity);
        
        // Update Redis counters
        incrementRedisCounters(request.getUserId(), request.getAmount());
    }

    private void incrementRedisCounters(String userId, BigDecimal amount) {
        // CONFIGURE_IN_VAULT: Use externally configured time windows
        int shortWindowMinutes = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_SHORT_WINDOW_MINUTES", "5"));
        int mediumWindowMinutes = Integer.parseInt(System.getenv().getOrDefault("FRAUD_VELOCITY_MEDIUM_WINDOW_MINUTES", "60"));

        // Increment short window counter
        String shortKey = VELOCITY_KEY_PREFIX + "count:" + userId + ":minutes";
        redisTemplate.opsForValue().increment(shortKey);
        redisTemplate.expire(shortKey, shortWindowMinutes, TimeUnit.MINUTES);

        // Increment medium window counter
        String mediumKey = VELOCITY_KEY_PREFIX + "count:" + userId + ":minutes";
        redisTemplate.opsForValue().increment(mediumKey);
        redisTemplate.expire(mediumKey, mediumWindowMinutes, TimeUnit.MINUTES);

        // Update amounts
        String amountKey = VELOCITY_KEY_PREFIX + "amount:" + userId + ":current";
        BigDecimal currentAmount = (BigDecimal) redisTemplate.opsForValue().get(amountKey);
        if (currentAmount == null) currentAmount = BigDecimal.ZERO;
        redisTemplate.opsForValue().set(amountKey, currentAmount.add(amount), 1, TimeUnit.HOURS);
    }

    public boolean exceedsVelocityLimit(String userId, int timeWindow, int maxTransactions) {
        int count = getTransactionCount(userId, timeWindow, ChronoUnit.MINUTES);
        return count > maxTransactions;
    }

    public void resetUserVelocity(String userId) {
        // Clear all Redis keys for the user
        String pattern = VELOCITY_KEY_PREFIX + "*:" + userId + ":*";
        redisTemplate.delete(redisTemplate.keys(pattern));
        log.info("Reset velocity tracking for user: {}", userId);
    }
}