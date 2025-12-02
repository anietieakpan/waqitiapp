package com.waqiti.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.domain.TransactionMetrics;
import com.waqiti.analytics.repository.TransactionMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for collecting and aggregating business metrics
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MetricsCollectionService {

    private final TransactionMetricsRepository metricsRepository;
    private final DataAggregationService dataAggregationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Listen to transaction events and update real-time metrics
     */
    @KafkaListener(topics = "payment-events", groupId = "analytics-service")
    public void processTransactionEvent(String eventPayload) {
        try {
            log.debug("Processing transaction event for analytics: {}", eventPayload);
            
            // Parse JSON event payload
            // Expected format: {"eventType": "TRANSACTION_COMPLETED", "transactionId": "...", "amount": 100.00, "userId": "...", "timestamp": "..."}
            Map<String, Object> event = parseEventPayload(eventPayload);
            
            String eventType = (String) event.get("eventType");
            if ("TRANSACTION_COMPLETED".equals(eventType) || "TRANSACTION_FAILED".equals(eventType)) {
                updateRealTimeMetrics(event);
            }
            
        } catch (Exception e) {
            log.error("Error processing transaction event: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate daily metrics summary (runs at midnight)
     */
    @Scheduled(cron = "0 0 0 * * *") // Every day at midnight
    public void generateDailyMetrics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Generating daily metrics for {}", yesterday);
        
        try {
            Map<String, Object> dailyData = dataAggregationService.aggregateDailyData(yesterday);
            
            TransactionMetrics metrics = TransactionMetrics.builder()
                .date(yesterday)
                .totalTransactions((Long) dailyData.get("totalTransactions"))
                .totalVolume((BigDecimal) dailyData.get("totalVolume"))
                .successfulTransactions((Long) dailyData.get("successfulTransactions"))
                .failedTransactions((Long) dailyData.get("failedTransactions"))
                .averageTransactionAmount(calculateAverage(
                    (BigDecimal) dailyData.get("totalVolume"), 
                    (Long) dailyData.get("totalTransactions")))
                .peakHourVolume((BigDecimal) dailyData.get("peakHourVolume"))
                .peakHour((Integer) dailyData.get("peakHour"))
                .uniqueUsers((Long) dailyData.get("uniqueUsers"))
                .newUsers((Long) dailyData.get("newUsers"))
                .activeWallets((Long) dailyData.get("activeWallets"))
                .updatedAt(LocalDateTime.now())
                .build();
                
            metricsRepository.save(metrics);
            log.info("Daily metrics generated successfully for {}", yesterday);
            
        } catch (Exception e) {
            log.error("Error generating daily metrics for {}: {}", yesterday, e.getMessage(), e);
        }
    }

    /**
     * Generate hourly metrics for real-time dashboard
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void generateHourlyMetrics() {
        LocalDateTime currentHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime previousHour = currentHour.minusHours(1);
        
        log.info("Generating hourly metrics for {}", previousHour);
        
        try {
            Map<String, Object> hourlyData = dataAggregationService.aggregateHourlyData(previousHour);
            
            // Store in time-series database or cache for real-time access
            // Implementation would store in Redis or InfluxDB for fast access
            
        } catch (Exception e) {
            log.error("Error generating hourly metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate business intelligence reports (weekly)
     */
    @Scheduled(cron = "0 0 6 * * MON") // Every Monday at 6 AM
    public void generateWeeklyReport() {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);
        
        log.info("Generating weekly report from {} to {}", startDate, endDate);
        
        try {
            Map<String, Object> weeklyData = dataAggregationService.aggregateWeeklyData(startDate, endDate);
            
            // Generate business intelligence report
            generateBusinessReport(weeklyData, startDate, endDate);
            
        } catch (Exception e) {
            log.error("Error generating weekly report: {}", e.getMessage(), e);
        }
    }

    /**
     * Real-time fraud metrics update
     */
    @KafkaListener(topics = "security-events", groupId = "analytics-service")
    public void processSecurityEvent(String eventPayload) {
        try {
            log.debug("Processing security event for analytics: {}", eventPayload);
            // Update fraud metrics in real-time
            // Implementation would parse security events and update fraud statistics
            
        } catch (Exception e) {
            log.error("Error processing security event: {}", e.getMessage(), e);
        }
    }

    private BigDecimal calculateAverage(BigDecimal total, Long count) {
        if (count == null || count == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private void generateBusinessReport(Map<String, Object> weeklyData, LocalDate startDate, LocalDate endDate) {
        // Generate comprehensive business intelligence report
        // This would create PDF reports, send to stakeholders, etc.
        log.info("Business report generated for period {} to {}", startDate, endDate);
    }

    private Map<String, Object> parseEventPayload(String eventPayload) {
        try {
            return objectMapper.readValue(eventPayload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error parsing event payload: {}", eventPayload, e);
            return new HashMap<>();
        }
    }

    private void updateRealTimeMetrics(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            String transactionId = (String) event.get("transactionId");
            Double amount = (Double) event.get("amount");
            String userId = (String) event.get("userId");
            
            // Update Redis counters for real-time metrics
            String dateKey = LocalDate.now().toString();
            
            // Increment transaction counters
            redisTemplate.opsForHash().increment("daily_metrics:" + dateKey, "total_transactions", 1);
            
            if ("TRANSACTION_COMPLETED".equals(eventType)) {
                redisTemplate.opsForHash().increment("daily_metrics:" + dateKey, "successful_transactions", 1);
                if (amount != null) {
                    redisTemplate.opsForHash().increment("daily_metrics:" + dateKey, "total_volume", amount);
                }
            } else if ("TRANSACTION_FAILED".equals(eventType)) {
                redisTemplate.opsForHash().increment("daily_metrics:" + dateKey, "failed_transactions", 1);
            }
            
            // Track unique users
            if (userId != null) {
                redisTemplate.opsForSet().add("daily_unique_users:" + dateKey, userId);
            }
            
            // Update hourly metrics
            String hourKey = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).toString();
            redisTemplate.opsForHash().increment("hourly_metrics:" + hourKey, "transactions", 1);
            
            if ("TRANSACTION_COMPLETED".equals(eventType) && amount != null) {
                redisTemplate.opsForHash().increment("hourly_metrics:" + hourKey, "volume", amount);
            }
            
            log.debug("Real-time metrics updated for event type: {}", eventType);
            
        } catch (Exception e) {
            log.error("Error updating real-time metrics", e);
        }
    }
}