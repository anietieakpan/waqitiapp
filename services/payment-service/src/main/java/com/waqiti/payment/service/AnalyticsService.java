package com.waqiti.payment.service;

import com.waqiti.common.exceptions.ServiceException;
import com.waqiti.payment.businessprofile.CustomerMetric;
import com.waqiti.payment.businessprofile.ProductMetric;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String ANALYTICS_EVENTS_TOPIC = "analytics-events";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Cacheable(value = "revenue-metrics", key = "#profileId + ':' + #startDate + ':' + #endDate")
    public BigDecimal calculateRevenue(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating revenue for profile: {} from {} to {}", profileId, startDate, endDate);
        
        try {
            String sql = """
                SELECT COALESCE(SUM(t.amount), 0) as total_revenue
                FROM transactions t
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                AND t.transaction_type IN ('PAYMENT', 'SALE')
                """;
            
            BigDecimal revenue = jdbcTemplate.queryForObject(sql, BigDecimal.class, 
                    profileId, startDate, endDate);
            
            log.info("Revenue calculated for profile {}: {}", profileId, revenue);
            return revenue != null ? revenue : BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Error calculating revenue for profile: {}", profileId, e);
            return BigDecimal.ZERO;
        }
    }

    @Cacheable(value = "transaction-count", key = "#profileId + ':' + #startDate + ':' + #endDate")
    public long getTransactionCount(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting transaction count for profile: {} from {} to {}", profileId, startDate, endDate);
        
        try {
            String sql = """
                SELECT COUNT(*) as transaction_count
                FROM transactions t
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                """;
            
            Long count = jdbcTemplate.queryForObject(sql, Long.class, 
                    profileId, startDate, endDate);
            
            return count != null ? count : 0L;
            
        } catch (Exception e) {
            log.error("Error getting transaction count for profile: {}", profileId, e);
            return 0L;
        }
    }

    public BigDecimal calculateAverageTransactionValue(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating average transaction value for profile: {}", profileId);
        
        BigDecimal revenue = calculateRevenue(profileId, startDate, endDate);
        long transactionCount = getTransactionCount(profileId, startDate, endDate);
        
        if (transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        
        return revenue.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP);
    }

    @Cacheable(value = "customer-metrics", key = "#profileId + ':' + #startDate + ':' + #endDate")
    public long getUniqueCustomerCount(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting unique customer count for profile: {}", profileId);
        
        try {
            String sql = """
                SELECT COUNT(DISTINCT t.customer_id) as unique_customers
                FROM transactions t
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                """;
            
            Long count = jdbcTemplate.queryForObject(sql, Long.class, 
                    profileId, startDate, endDate);
            
            return count != null ? count : 0L;
            
        } catch (Exception e) {
            log.error("Error getting unique customer count for profile: {}", profileId, e);
            return 0L;
        }
    }

    public long getNewCustomerCount(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting new customer count for profile: {}", profileId);
        
        try {
            String sql = """
                SELECT COUNT(DISTINCT t.customer_id) as new_customers
                FROM transactions t
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                AND NOT EXISTS (
                    SELECT 1 FROM transactions t2
                    WHERE t2.business_profile_id = t.business_profile_id
                    AND t2.customer_id = t.customer_id
                    AND t2.transaction_date < ?
                    AND t2.status IN ('COMPLETED', 'SETTLED')
                )
                """;
            
            Long count = jdbcTemplate.queryForObject(sql, Long.class, 
                    profileId, startDate, endDate, startDate);
            
            return count != null ? count : 0L;
            
        } catch (Exception e) {
            log.error("Error getting new customer count for profile: {}", profileId, e);
            return 0L;
        }
    }

    @CircuitBreaker(name = "analytics-db", fallbackMethod = "getTopProductsFallback")
    public List<ProductMetric> getTopProducts(UUID profileId, LocalDate startDate, LocalDate endDate, int limit) {
        log.debug("Getting top {} products for profile: {}", limit, profileId);
        
        try {
            String sql = """
                SELECT 
                    ti.product_id,
                    ti.product_name,
                    COUNT(*) as sales_count,
                    SUM(ti.quantity) as total_quantity,
                    SUM(ti.amount) as total_revenue
                FROM transaction_items ti
                JOIN transactions t ON ti.transaction_id = t.id
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                GROUP BY ti.product_id, ti.product_name
                ORDER BY total_revenue DESC
                LIMIT ?
                """;
            
            return jdbcTemplate.query(sql, (rs, rowNum) -> 
                ProductMetric.builder()
                    .productId(rs.getString("product_id"))
                    .productName(rs.getString("product_name"))
                    .salesCount(rs.getLong("sales_count"))
                    .totalQuantity(rs.getLong("total_quantity"))
                    .totalRevenue(rs.getBigDecimal("total_revenue"))
                    .build(),
                profileId, startDate, endDate, limit
            );
            
        } catch (Exception e) {
            log.error("Error getting top products for profile: {}", profileId, e);
            return getTopProductsFallback(profileId, startDate, endDate, limit, e);
        }
    }

    public List<CustomerMetric> getTopCustomers(UUID profileId, LocalDate startDate, LocalDate endDate, int limit) {
        log.debug("Getting top {} customers for profile: {}", limit, profileId);
        
        try {
            String sql = """
                SELECT 
                    t.customer_id,
                    c.name as customer_name,
                    COUNT(*) as transaction_count,
                    SUM(t.amount) as total_spent,
                    AVG(t.amount) as average_transaction_value,
                    MAX(t.transaction_date) as last_transaction_date
                FROM transactions t
                LEFT JOIN customers c ON t.customer_id = c.id
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                GROUP BY t.customer_id, c.name
                ORDER BY total_spent DESC
                LIMIT ?
                """;
            
            return jdbcTemplate.query(sql, (rs, rowNum) -> 
                CustomerMetric.builder()
                    .customerId(UUID.fromString(rs.getString("customer_id")))
                    .customerName(rs.getString("customer_name"))
                    .transactionCount(rs.getLong("transaction_count"))
                    .totalSpent(rs.getBigDecimal("total_spent"))
                    .averageTransactionValue(rs.getBigDecimal("average_transaction_value"))
                    .lastTransactionDate(rs.getDate("last_transaction_date").toLocalDate())
                    .build(),
                profileId, startDate, endDate, limit
            );
            
        } catch (Exception e) {
            log.error("Error getting top customers for profile: {}", profileId, e);
            return new ArrayList<>();
        }
    }

    public Map<String, BigDecimal> getRevenueByDay(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting revenue by day for profile: {}", profileId);
        
        try {
            String sql = """
                SELECT 
                    DATE(t.transaction_date) as day,
                    SUM(t.amount) as daily_revenue
                FROM transactions t
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                GROUP BY DATE(t.transaction_date)
                ORDER BY day
                """;
            
            Map<String, BigDecimal> revenueByDay = new LinkedHashMap<>();
            
            jdbcTemplate.query(sql, (rs) -> {
                String day = rs.getDate("day").toLocalDate().format(DATE_FORMATTER);
                BigDecimal revenue = rs.getBigDecimal("daily_revenue");
                revenueByDay.put(day, revenue);
            }, profileId, startDate, endDate);
            
            // Fill in missing days with zero revenue
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                String dayKey = currentDate.format(DATE_FORMATTER);
                revenueByDay.putIfAbsent(dayKey, BigDecimal.ZERO);
                currentDate = currentDate.plusDays(1);
            }
            
            return revenueByDay.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
            
        } catch (Exception e) {
            log.error("Error getting revenue by day for profile: {}", profileId, e);
            return new HashMap<>();
        }
    }

    public Map<String, BigDecimal> getRevenueByCategory(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting revenue by category for profile: {}", profileId);
        
        try {
            String sql = """
                SELECT 
                    COALESCE(ti.category, 'Uncategorized') as category,
                    SUM(ti.amount) as category_revenue
                FROM transaction_items ti
                JOIN transactions t ON ti.transaction_id = t.id
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                GROUP BY ti.category
                ORDER BY category_revenue DESC
                """;
            
            Map<String, BigDecimal> revenueByCategory = new LinkedHashMap<>();
            
            jdbcTemplate.query(sql, (rs) -> {
                String category = rs.getString("category");
                BigDecimal revenue = rs.getBigDecimal("category_revenue");
                revenueByCategory.put(category, revenue);
            }, profileId, startDate, endDate);
            
            return revenueByCategory;
            
        } catch (Exception e) {
            log.error("Error getting revenue by category for profile: {}", profileId, e);
            return new HashMap<>();
        }
    }

    public Map<String, Long> getPaymentMethodBreakdown(UUID profileId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting payment method breakdown for profile: {}", profileId);
        
        try {
            String sql = """
                SELECT 
                    t.payment_method,
                    COUNT(*) as method_count
                FROM transactions t
                WHERE t.business_profile_id = ?
                AND t.transaction_date >= ?
                AND t.transaction_date <= ?
                AND t.status IN ('COMPLETED', 'SETTLED')
                GROUP BY t.payment_method
                ORDER BY method_count DESC
                """;
            
            Map<String, Long> breakdown = new LinkedHashMap<>();
            
            jdbcTemplate.query(sql, (rs) -> {
                String method = rs.getString("payment_method");
                Long count = rs.getLong("method_count");
                breakdown.put(method, count);
            }, profileId, startDate, endDate);
            
            return breakdown;
            
        } catch (Exception e) {
            log.error("Error getting payment method breakdown for profile: {}", profileId, e);
            return new HashMap<>();
        }
    }

    public void trackEvent(String eventType, Map<String, Object> eventData) {
        try {
            AnalyticsEvent event = AnalyticsEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .eventData(eventData)
                    .timestamp(java.time.Instant.now())
                    .build();
            
            // Send to Kafka for real-time processing
            CompletableFuture.runAsync(() -> {
                try {
                    kafkaTemplate.send(ANALYTICS_EVENTS_TOPIC, event.getEventId().toString(), event);
                    log.debug("Analytics event sent: {} - {}", eventType, event.getEventId());
                } catch (Exception e) {
                    log.error("Failed to send analytics event to Kafka", e);
                }
            });
            
            // Also store in database for batch processing
            storeAnalyticsEvent(event);
            
        } catch (Exception e) {
            log.error("Error tracking analytics event: {}", eventType, e);
        }
    }

    private void storeAnalyticsEvent(AnalyticsEvent event) {
        try {
            String sql = """
                INSERT INTO analytics_events (event_id, event_type, event_data, timestamp)
                VALUES (?, ?, ?::jsonb, ?)
                """;
            
            jdbcTemplate.update(sql, 
                event.getEventId(),
                event.getEventType(),
                objectToJson(event.getEventData()),
                event.getTimestamp()
            );
            
        } catch (Exception e) {
            log.error("Error storing analytics event", e);
        }
    }

    private String objectToJson(Object object) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(object);
        } catch (Exception e) {
            return "{}";
        }
    }

    // Fallback method for circuit breaker
    private List<ProductMetric> getTopProductsFallback(UUID profileId, LocalDate startDate, 
                                                      LocalDate endDate, int limit, Exception ex) {
        log.info("Using fallback for top products due to: {}", ex.getMessage());
        return new ArrayList<>();
    }

    @lombok.Builder
    @lombok.Data
    private static class AnalyticsEvent {
        private UUID eventId;
        private String eventType;
        private Map<String, Object> eventData;
        private java.time.Instant timestamp;
    }

    // Balance Analytics Methods

    public void updateBalanceAnalytics(String accountId, BigDecimal currentBalance,
                                      BigDecimal balanceChange, String correlationId) {
        log.info("Updating balance analytics: accountId={}, balance={}, change={}, correlationId={}",
            accountId, currentBalance, balanceChange, correlationId);

        trackEvent("BALANCE_UPDATED", Map.of(
            "accountId", accountId,
            "currentBalance", currentBalance,
            "balanceChange", balanceChange,
            "correlationId", correlationId
        ));
    }

    public void updateDailyBalanceTrends(String accountId, BigDecimal currentBalance,
                                        LocalDate snapshotDate, String correlationId) {
        log.info("Updating daily balance trends: accountId={}, balance={}, date={}, correlationId={}",
            accountId, currentBalance, snapshotDate, correlationId);

        trackEvent("DAILY_BALANCE_TREND", Map.of(
            "accountId", accountId,
            "currentBalance", currentBalance,
            "snapshotDate", snapshotDate.toString(),
            "correlationId", correlationId
        ));
    }

    public void generateMonthlyBalanceSummary(String accountId,
                                             com.waqiti.common.events.BalanceHistoryEvent.MonthlyData monthlyData,
                                             String correlationId) {
        log.info("Generating monthly balance summary: accountId={}, month={}, correlationId={}",
            accountId, monthlyData.getMonth(), correlationId);

        trackEvent("MONTHLY_BALANCE_SUMMARY", Map.of(
            "accountId", accountId,
            "month", monthlyData.getMonth().toString(),
            "openingBalance", monthlyData.getOpeningBalance(),
            "closingBalance", monthlyData.getClosingBalance(),
            "averageBalance", monthlyData.getAverageBalance(),
            "correlationId", correlationId
        ));
    }

    public void analyzeBalanceTrends(String accountId,
                                     com.waqiti.common.events.BalanceHistoryEvent.TrendData trendData,
                                     String correlationId) {
        log.info("Analyzing balance trends: accountId={}, direction={}, correlationId={}",
            accountId, trendData.getTrendDirection(), correlationId);

        trackEvent("BALANCE_TREND_ANALYSIS", Map.of(
            "accountId", accountId,
            "trendDirection", trendData.getTrendDirection(),
            "trendPercentage", trendData.getTrendPercentage(),
            "periodDays", trendData.getPeriodDays(),
            "correlationId", correlationId
        ));
    }

    public void recordBalanceAnomaly(String accountId,
                                    com.waqiti.common.events.BalanceHistoryEvent.AnomalyDetails anomalyDetails,
                                    String correlationId) {
        log.warn("Recording balance anomaly: accountId={}, type={}, severity={}, correlationId={}",
            accountId, anomalyDetails.getType(), anomalyDetails.getSeverity(), correlationId);

        trackEvent("BALANCE_ANOMALY", Map.of(
            "accountId", accountId,
            "anomalyType", anomalyDetails.getType(),
            "severity", anomalyDetails.getSeverity(),
            "variance", anomalyDetails.getVariance(),
            "correlationId", correlationId
        ));
    }
}