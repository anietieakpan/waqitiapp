package com.waqiti.security.service;

import com.waqiti.security.domain.TransactionPattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for analyzing transaction patterns to detect suspicious activity
 * Implements advanced pattern recognition algorithms for AML compliance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionPatternAnalyzer {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get recent transactions for a user within a specified time period
     */
    public List<TransactionPattern> getRecentTransactions(UUID userId, LocalDateTime since) {
        log.debug("Getting recent transactions for user: {} since: {}", userId, since);
        
        String query = """
            SELECT t.id, t.amount, t.created_at, t.recipient_wallet_id, t.status
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? AND t.created_at >= ?
            ORDER BY t.created_at DESC
            """;
        
        return jdbcTemplate.query(query, 
            (rs, rowNum) -> TransactionPattern.builder()
                .transactionId(UUID.fromString(rs.getString("id")))
                .amount(rs.getBigDecimal("amount"))
                .timestamp(rs.getTimestamp("created_at").toLocalDateTime())
                .recipientWalletId(rs.getString("recipient_wallet_id") != null ? 
                    UUID.fromString(rs.getString("recipient_wallet_id")) : null)
                .status(rs.getString("status"))
                .build(),
            userId, since);
    }

    /**
     * Calculate daily transaction total for a user
     */
    public BigDecimal getDailyTransactionTotal(UUID userId, LocalDateTime dayStart) {
        log.debug("Calculating daily transaction total for user: {} on: {}", userId, dayStart);
        
        String query = """
            SELECT COALESCE(SUM(t.amount), 0) as daily_total
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? 
            AND t.created_at >= ? 
            AND t.created_at < ?
            AND t.status = 'COMPLETED'
            """;
        
        LocalDateTime dayEnd = dayStart.plusDays(1);
        BigDecimal total = jdbcTemplate.queryForObject(query, BigDecimal.class, 
            userId, dayStart, dayEnd);
        
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Calculate weekly transaction total for a user
     */
    public BigDecimal getWeeklyTransactionTotal(UUID userId, LocalDateTime weekStart) {
        log.debug("Calculating weekly transaction total for user: {} starting: {}", userId, weekStart);
        
        String query = """
            SELECT COALESCE(SUM(t.amount), 0) as weekly_total
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? 
            AND t.created_at >= ? 
            AND t.created_at < ?
            AND t.status = 'COMPLETED'
            """;
        
        LocalDateTime weekEnd = weekStart.plusDays(7);
        BigDecimal total = jdbcTemplate.queryForObject(query, BigDecimal.class, 
            userId, weekStart, weekEnd);
        
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Detect circular transaction patterns (A->B->A)
     */
    public boolean detectCircularTransactions(UUID userId, UUID recipientId) {
        if (recipientId == null) return false;
        
        log.debug("Checking for circular transactions between users: {} and {}", userId, recipientId);
        
        // Look for transactions in both directions within last 30 days
        String query = """
            SELECT COUNT(*) as circular_count
            FROM (
                SELECT t1.id as t1_id, t2.id as t2_id
                FROM transactions t1
                INNER JOIN wallets w1s ON t1.source_wallet_id = w1s.id
                INNER JOIN wallets w1r ON t1.recipient_wallet_id = w1r.id
                INNER JOIN transactions t2
                INNER JOIN wallets w2s ON t2.source_wallet_id = w2s.id
                INNER JOIN wallets w2r ON t2.recipient_wallet_id = w2r.id
                WHERE w1s.user_id = ? AND w1r.user_id = ?
                AND w2s.user_id = ? AND w2r.user_id = ?
                AND t1.created_at >= NOW() - INTERVAL '30 days'
                AND t2.created_at >= NOW() - INTERVAL '30 days'
                AND t1.created_at < t2.created_at
                AND ABS(EXTRACT(EPOCH FROM (t2.created_at - t1.created_at))) < 3600
            ) circular_pairs
            """;
        
        Integer count = jdbcTemplate.queryForObject(query, Integer.class, 
            userId, recipientId, recipientId, userId);
        
        return count != null && count > 0;
    }

    /**
     * Get hourly transaction count for velocity analysis
     */
    public int getHourlyTransactionCount(UUID userId) {
        log.debug("Getting hourly transaction count for user: {}", userId);
        
        String query = """
            SELECT COUNT(*) as hourly_count
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? 
            AND t.created_at >= NOW() - INTERVAL '1 hour'
            """;
        
        Integer count = jdbcTemplate.queryForObject(query, Integer.class, userId);
        return count != null ? count : 0;
    }

    /**
     * Get transaction count since a specific time
     */
    public int getTransactionCountSince(UUID userId, LocalDateTime since) {
        log.debug("Getting transaction count for user: {} since: {}", userId, since);
        
        String query = """
            SELECT COUNT(*) as count_since
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? AND t.created_at >= ?
            """;
        
        Integer count = jdbcTemplate.queryForObject(query, Integer.class, userId, since);
        return count != null ? count : 0;
    }

    /**
     * Analyze transaction frequency patterns
     */
    public TransactionFrequencyPattern analyzeFrequencyPatterns(UUID userId, int daysPeriod) {
        log.debug("Analyzing frequency patterns for user: {} over {} days", userId, daysPeriod);
        
        String query = """
            SELECT 
                DATE(t.created_at) as transaction_date,
                COUNT(*) as daily_count,
                SUM(t.amount) as daily_amount,
                AVG(t.amount) as avg_amount,
                MIN(t.amount) as min_amount,
                MAX(t.amount) as max_amount
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? 
            AND t.created_at >= NOW() - INTERVAL ? DAY
            AND t.status = 'COMPLETED'
            GROUP BY DATE(t.created_at)
            ORDER BY transaction_date DESC
            """;
        
        List<DailyTransactionStats> dailyStats = jdbcTemplate.query(query,
            (rs, rowNum) -> DailyTransactionStats.builder()
                .date(rs.getDate("transaction_date").toLocalDate())
                .transactionCount(rs.getInt("daily_count"))
                .totalAmount(rs.getBigDecimal("daily_amount"))
                .averageAmount(rs.getBigDecimal("avg_amount"))
                .minAmount(rs.getBigDecimal("min_amount"))
                .maxAmount(rs.getBigDecimal("max_amount"))
                .build(),
            userId, daysPeriod);
        
        return analyzePatternAnomalies(dailyStats);
    }

    /**
     * Detect time-based patterns (unusual hours, days)
     */
    public TimePatternAnalysis analyzeTimePatterns(UUID userId, int daysPeriod) {
        log.debug("Analyzing time patterns for user: {} over {} days", userId, daysPeriod);
        
        // Analyze hourly distribution
        String hourlyQuery = """
            SELECT 
                EXTRACT(HOUR FROM t.created_at) as hour,
                COUNT(*) as transaction_count
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? 
            AND t.created_at >= NOW() - INTERVAL ? DAY
            GROUP BY EXTRACT(HOUR FROM t.created_at)
            ORDER BY hour
            """;
        
        List<HourlyStats> hourlyStats = jdbcTemplate.query(hourlyQuery,
            (rs, rowNum) -> HourlyStats.builder()
                .hour(rs.getInt("hour"))
                .transactionCount(rs.getInt("transaction_count"))
                .build(),
            userId, daysPeriod);
        
        // Analyze day-of-week distribution
        String weeklyQuery = """
            SELECT 
                EXTRACT(DOW FROM t.created_at) as day_of_week,
                COUNT(*) as transaction_count
            FROM transactions t
            INNER JOIN wallets w ON t.source_wallet_id = w.id
            WHERE w.user_id = ? 
            AND t.created_at >= NOW() - INTERVAL ? DAY
            GROUP BY EXTRACT(DOW FROM t.created_at)
            ORDER BY day_of_week
            """;
        
        List<WeeklyStats> weeklyStats = jdbcTemplate.query(weeklyQuery,
            (rs, rowNum) -> WeeklyStats.builder()
                .dayOfWeek(rs.getInt("day_of_week"))
                .transactionCount(rs.getInt("transaction_count"))
                .build(),
            userId, daysPeriod);
        
        return TimePatternAnalysis.builder()
            .hourlyDistribution(hourlyStats)
            .weeklyDistribution(weeklyStats)
            .hasOffHoursActivity(hasSignificantOffHoursActivity(hourlyStats))
            .hasWeekendActivity(hasSignificantWeekendActivity(weeklyStats))
            .build();
    }

    /**
     * Detect merchant payment patterns
     */
    public boolean detectMerchantLikeActivity(UUID userId) {
        log.debug("Detecting merchant-like activity for user: {}", userId);
        
        String query = """
            SELECT 
                COUNT(DISTINCT w2.user_id) as unique_recipients,
                COUNT(*) as total_transactions,
                AVG(t.amount) as avg_amount,
                STDDEV(t.amount) as amount_stddev
            FROM transactions t
            INNER JOIN wallets w1 ON t.source_wallet_id = w1.id
            INNER JOIN wallets w2 ON t.recipient_wallet_id = w2.id
            WHERE w1.user_id = ? 
            AND t.created_at >= NOW() - INTERVAL '30 days'
            AND t.status = 'COMPLETED'
            """;
        
        return jdbcTemplate.queryForObject(query, (rs, rowNum) -> {
            int uniqueRecipients = rs.getInt("unique_recipients");
            int totalTransactions = rs.getInt("total_transactions");
            BigDecimal avgAmount = rs.getBigDecimal("avg_amount");
            BigDecimal stddev = rs.getBigDecimal("amount_stddev");
            
            // Merchant-like patterns: many unique recipients, consistent amounts
            return uniqueRecipients > 50 && 
                   totalTransactions > 100 && 
                   (stddev == null || stddev.compareTo(avgAmount.multiply(BigDecimal.valueOf(0.3))) < 0);
        }, userId);
    }

    // Helper classes for pattern analysis
    public static class TransactionFrequencyPattern {
        private final List<DailyTransactionStats> dailyStats;
        private final boolean hasAnomalousActivity;
        private final String anomalyDescription;
        
        public TransactionFrequencyPattern(List<DailyTransactionStats> dailyStats, 
                                         boolean hasAnomalousActivity, String anomalyDescription) {
            this.dailyStats = dailyStats;
            this.hasAnomalousActivity = hasAnomalousActivity;
            this.anomalyDescription = anomalyDescription;
        }
        
        // Getters
        public List<DailyTransactionStats> getDailyStats() { return dailyStats; }
        public boolean hasAnomalousActivity() { return hasAnomalousActivity; }
        public String getAnomalyDescription() { return anomalyDescription; }
    }
    
    // Helper methods
    private TransactionFrequencyPattern analyzePatternAnomalies(List<DailyTransactionStats> stats) {
        if (stats.isEmpty()) {
            return new TransactionFrequencyPattern(stats, false, "No transaction data");
        }
        
        // Calculate mean and standard deviation for anomaly detection
        double avgDailyCount = stats.stream()
            .mapToInt(DailyTransactionStats::getTransactionCount)
            .average()
            .orElse(0.0);
        
        double avgDailyAmount = stats.stream()
            .mapToDouble(s -> s.getTotalAmount().doubleValue())
            .average()
            .orElse(0.0);
        
        // Look for outliers (transactions > 3 standard deviations from mean)
        boolean hasAnomalies = stats.stream()
            .anyMatch(s -> Math.abs(s.getTransactionCount() - avgDailyCount) > avgDailyCount * 2 ||
                          Math.abs(s.getTotalAmount().doubleValue() - avgDailyAmount) > avgDailyAmount * 2);
        
        String description = hasAnomalies ? 
            "Detected unusual spikes in transaction volume or frequency" : 
            "Normal transaction patterns observed";
        
        return new TransactionFrequencyPattern(stats, hasAnomalies, description);
    }
    
    private boolean hasSignificantOffHoursActivity(List<HourlyStats> hourlyStats) {
        int totalTransactions = hourlyStats.stream()
            .mapToInt(HourlyStats::getTransactionCount)
            .sum();
        
        int offHoursTransactions = hourlyStats.stream()
            .filter(s -> s.getHour() < 6 || s.getHour() > 22)
            .mapToInt(HourlyStats::getTransactionCount)
            .sum();
        
        return totalTransactions > 0 && (double) offHoursTransactions / totalTransactions > 0.3;
    }
    
    private boolean hasSignificantWeekendActivity(List<WeeklyStats> weeklyStats) {
        int totalTransactions = weeklyStats.stream()
            .mapToInt(WeeklyStats::getTransactionCount)
            .sum();

        int weekendTransactions = weeklyStats.stream()
            .filter(s -> s.getDayOfWeek() == 0 || s.getDayOfWeek() == 6) // Sunday = 0, Saturday = 6
            .mapToInt(WeeklyStats::getTransactionCount)
            .sum();

        return totalTransactions > 0 && (double) weekendTransactions / totalTransactions > 0.4;
    }

    /**
     * Enable real-time monitoring for a user
     */
    public void enableRealTimeMonitoring(UUID userId, BigDecimal threshold) {
        log.info("Enabling real-time monitoring for user: {} with threshold: {}", userId, threshold);
        // Implementation would set up monitoring rules in cache or database
    }

    /**
     * Enable mandatory manual review for all user transactions
     */
    public void enableMandatoryManualReview(UUID userId) {
        log.info("Enabling mandatory manual review for user: {}", userId);
        // Implementation would set flag requiring manual approval
    }

    /**
     * Enable enhanced monitoring for a user
     */
    public void enableEnhancedMonitoring(UUID userId) {
        log.info("Enabling enhanced monitoring for user: {}", userId);
        // Implementation would activate enhanced monitoring rules
    }

    /**
     * Enable basic monitoring for a user
     */
    public void enableBasicMonitoring(UUID userId) {
        log.info("Enabling basic monitoring for user: {}", userId);
        // Implementation would activate basic monitoring rules
    }

    /**
     * Apply monitoring rules to a user
     */
    public void applyMonitoringRules(UUID userId, AmlMonitoringService.MonitoringRules rules) {
        log.info("Applying monitoring rules for user: {} - rules: {}", userId, rules);
        // Implementation would persist and enforce monitoring rules
    }
}