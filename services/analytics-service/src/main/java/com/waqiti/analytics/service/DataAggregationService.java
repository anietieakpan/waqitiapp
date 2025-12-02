package com.waqiti.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for aggregating data from multiple sources for analytics
 * Performs complex queries and data processing for business intelligence
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataAggregationService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Aggregate daily transaction data
     */
    public Map<String, Object> aggregateDailyData(LocalDate date) {
        log.debug("Aggregating daily data for {}", date);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Transaction metrics
            String transactionQuery = """
                SELECT 
                    COUNT(*) as total_transactions,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful_transactions,
                    COUNT(CASE WHEN status != 'COMPLETED' THEN 1 END) as failed_transactions,
                    COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as total_volume
                FROM transactions 
                WHERE DATE(created_at) = ?
                """;
            
            Map<String, Object> transactionData = jdbcTemplate.queryForMap(transactionQuery, date);
            result.putAll(transactionData);
            
            // User activity metrics
            String userQuery = """
                SELECT 
                    COUNT(DISTINCT user_id) as unique_users,
                    COUNT(DISTINCT CASE WHEN DATE(created_at) = ? THEN user_id END) as new_users
                FROM users 
                WHERE DATE(last_login_at) = ? OR DATE(created_at) = ?
                """;
            
            Map<String, Object> userData = jdbcTemplate.queryForMap(userQuery, date, date, date);
            result.putAll(userData);
            
            // Wallet activity
            String walletQuery = """
                SELECT COUNT(DISTINCT wallet_id) as active_wallets
                FROM transactions 
                WHERE DATE(created_at) = ?
                """;
            
            Long activeWallets = jdbcTemplate.queryForObject(walletQuery, Long.class, date);
            result.put("activeWallets", activeWallets);
            
            // Peak hour analysis
            String peakHourQuery = """
                SELECT 
                    EXTRACT(HOUR FROM created_at) as hour,
                    SUM(amount) as hour_volume
                FROM transactions 
                WHERE DATE(created_at) = ? AND status = 'COMPLETED'
                GROUP BY EXTRACT(HOUR FROM created_at)
                ORDER BY hour_volume DESC
                LIMIT 1
                """;
            
            try {
                Map<String, Object> peakData = jdbcTemplate.queryForMap(peakHourQuery, date);
                result.put("peakHour", peakData.get("hour"));
                result.put("peakHourVolume", peakData.get("hour_volume"));
            } catch (Exception e) {
                result.put("peakHour", 0);
                result.put("peakHourVolume", BigDecimal.ZERO);
            }
            
            log.debug("Daily aggregation completed for {} with {} total transactions", 
                date, result.get("total_transactions"));
            
        } catch (Exception e) {
            log.error("Error aggregating daily data for {}: {}", date, e.getMessage(), e);
            // Return default values on error
            result.put("totalTransactions", 0L);
            result.put("successfulTransactions", 0L);
            result.put("failedTransactions", 0L);
            result.put("totalVolume", BigDecimal.ZERO);
            result.put("uniqueUsers", 0L);
            result.put("newUsers", 0L);
            result.put("activeWallets", 0L);
            result.put("peakHour", 0);
            result.put("peakHourVolume", BigDecimal.ZERO);
        }
        
        return result;
    }

    /**
     * Aggregate hourly data for real-time metrics
     */
    public Map<String, Object> aggregateHourlyData(LocalDateTime hour) {
        log.debug("Aggregating hourly data for {}", hour);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            LocalDateTime hourEnd = hour.plusHours(1);
            
            String hourlyQuery = """
                SELECT 
                    COUNT(*) as transactions_count,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful_count,
                    COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as volume,
                    COUNT(DISTINCT user_id) as active_users,
                    AVG(CASE WHEN status = 'COMPLETED' THEN amount END) as avg_amount
                FROM transactions 
                WHERE created_at >= ? AND created_at < ?
                """;
            
            Map<String, Object> hourlyData = jdbcTemplate.queryForMap(hourlyQuery, hour, hourEnd);
            result.putAll(hourlyData);
            
            // Security events for the hour
            String securityQuery = """
                SELECT COUNT(*) as security_events_count
                FROM security_events 
                WHERE created_at >= ? AND created_at < ?
                """;
            
            try {
                Long securityEvents = jdbcTemplate.queryForObject(securityQuery, Long.class, hour, hourEnd);
                result.put("securityEventsCount", securityEvents);
            } catch (Exception e) {
                result.put("securityEventsCount", 0L);
            }
            
        } catch (Exception e) {
            log.error("Error aggregating hourly data for {}: {}", hour, e.getMessage(), e);
            // Return default values
            result.put("transactionsCount", 0L);
            result.put("successfulCount", 0L);
            result.put("volume", BigDecimal.ZERO);
            result.put("activeUsers", 0L);
            result.put("avgAmount", BigDecimal.ZERO);
            result.put("securityEventsCount", 0L);
        }
        
        return result;
    }

    /**
     * Aggregate weekly data for business intelligence reports
     */
    public Map<String, Object> aggregateWeeklyData(LocalDate startDate, LocalDate endDate) {
        log.debug("Aggregating weekly data from {} to {}", startDate, endDate);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Weekly transaction trends
            String weeklyQuery = """
                SELECT 
                    COUNT(*) as total_transactions,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful_transactions,
                    COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as total_volume,
                    COUNT(DISTINCT user_id) as unique_users,
                    COUNT(DISTINCT wallet_id) as active_wallets,
                    AVG(CASE WHEN status = 'COMPLETED' THEN amount END) as avg_transaction_amount
                FROM transactions 
                WHERE DATE(created_at) BETWEEN ? AND ?
                """;
            
            Map<String, Object> weeklyData = jdbcTemplate.queryForMap(weeklyQuery, startDate, endDate);
            result.putAll(weeklyData);
            
            // Growth metrics (compared to previous week)
            LocalDate prevWeekStart = startDate.minusDays(7);
            LocalDate prevWeekEnd = endDate.minusDays(7);
            
            String growthQuery = """
                SELECT 
                    COUNT(*) as prev_week_transactions,
                    COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as prev_week_volume,
                    COUNT(DISTINCT user_id) as prev_week_users
                FROM transactions 
                WHERE DATE(created_at) BETWEEN ? AND ?
                """;
            
            Map<String, Object> prevWeekData = jdbcTemplate.queryForMap(growthQuery, prevWeekStart, prevWeekEnd);
            
            // Calculate growth rates
            calculateGrowthMetrics(result, weeklyData, prevWeekData);
            
            // Top performing days
            String dailyBreakdownQuery = """
                SELECT 
                    DATE(created_at) as transaction_date,
                    COUNT(*) as daily_transactions,
                    COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as daily_volume
                FROM transactions 
                WHERE DATE(created_at) BETWEEN ? AND ?
                GROUP BY DATE(created_at)
                ORDER BY daily_volume DESC
                """;
            
            // This would be used for detailed breakdown in reports
            // Implementation would store this for report generation
            
        } catch (Exception e) {
            log.error("Error aggregating weekly data: {}", e.getMessage(), e);
            // Return default values
            initializeDefaultWeeklyMetrics(result);
        }
        
        return result;
    }

    /**
     * Get real-time metrics for dashboard
     */
    public Map<String, Object> getRealTimeMetrics() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Last 24 hours metrics
            String last24hQuery = """
                SELECT 
                    COUNT(*) as transactions_24h,
                    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful_24h,
                    COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as volume_24h,
                    COUNT(DISTINCT user_id) as active_users_24h
                FROM transactions 
                WHERE created_at >= NOW() - INTERVAL '24 HOURS'
                """;
            
            Map<String, Object> last24h = jdbcTemplate.queryForMap(last24hQuery);
            result.putAll(last24h);
            
            // Current hour metrics
            String currentHourQuery = """
                SELECT 
                    COUNT(*) as transactions_current_hour,
                    COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as volume_current_hour
                FROM transactions 
                WHERE created_at >= DATE_TRUNC('hour', NOW())
                """;
            
            Map<String, Object> currentHour = jdbcTemplate.queryForMap(currentHourQuery);
            result.putAll(currentHour);
            
        } catch (Exception e) {
            log.error("Error getting real-time metrics: {}", e.getMessage(), e);
            // Return defaults
            result.put("transactions24h", 0L);
            result.put("successful24h", 0L);
            result.put("volume24h", BigDecimal.ZERO);
            result.put("activeUsers24h", 0L);
            result.put("transactionsCurrentHour", 0L);
            result.put("volumeCurrentHour", BigDecimal.ZERO);
        }
        
        return result;
    }

    private void calculateGrowthMetrics(Map<String, Object> result, 
                                       Map<String, Object> currentWeek, 
                                       Map<String, Object> previousWeek) {
        try {
            Long currentTransactions = (Long) currentWeek.get("total_transactions");
            Long prevTransactions = (Long) previousWeek.get("prev_week_transactions");
            
            if (prevTransactions != null && prevTransactions > 0) {
                double transactionGrowth = ((double) (currentTransactions - prevTransactions) / prevTransactions) * 100;
                result.put("transactionGrowthPercent", Math.round(transactionGrowth * 100.0) / 100.0);
            } else {
                result.put("transactionGrowthPercent", 0.0);
            }
            
            BigDecimal currentVolume = (BigDecimal) currentWeek.get("total_volume");
            BigDecimal prevVolume = (BigDecimal) previousWeek.get("prev_week_volume");
            
            if (prevVolume != null && prevVolume.compareTo(BigDecimal.ZERO) > 0) {
                double volumeGrowth = currentVolume.subtract(prevVolume)
                    .divide(prevVolume, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
                result.put("volumeGrowthPercent", Math.round(volumeGrowth * 100.0) / 100.0);
            } else {
                result.put("volumeGrowthPercent", 0.0);
            }
            
        } catch (Exception e) {
            log.error("Error calculating growth metrics: {}", e.getMessage(), e);
            result.put("transactionGrowthPercent", 0.0);
            result.put("volumeGrowthPercent", 0.0);
        }
    }

    private void initializeDefaultWeeklyMetrics(Map<String, Object> result) {
        result.put("totalTransactions", 0L);
        result.put("successfulTransactions", 0L);
        result.put("totalVolume", BigDecimal.ZERO);
        result.put("uniqueUsers", 0L);
        result.put("activeWallets", 0L);
        result.put("avgTransactionAmount", BigDecimal.ZERO);
        result.put("transactionGrowthPercent", 0.0);
        result.put("volumeGrowthPercent", 0.0);
    }
}