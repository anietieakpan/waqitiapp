package com.waqiti.analytics.service;

import com.waqiti.analytics.domain.AlertHistory;
import com.waqiti.analytics.dto.Alert;
import com.waqiti.analytics.repository.AlertHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling analytics alerts and notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertingService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AlertHistoryRepository alertHistoryRepository;
    
    /**
     * Send an alert through various channels
     */
    public void sendAlert(Alert alert) {
        try {
            log.info("Sending alert: type={}, severity={}, message={}", 
                alert.getType(), alert.getSeverity(), alert.getMessage());
            
            // Send to Kafka for processing by notification service
            kafkaTemplate.send("analytics-alerts", alert.getType().toString(), alert);
            
            // Log critical alerts
            if (alert.getSeverity() == Alert.Severity.CRITICAL) {
                log.error("CRITICAL ALERT: {}", alert);
            }
            
            // Store alert in database for historical tracking
            storeAlert(alert);
            
        } catch (Exception e) {
            log.error("Failed to send alert: {}", alert, e);
        }
    }
    
    /**
     * Send a simple alert with just type, severity and message
     */
    public void sendSimpleAlert(Alert.AlertType type, Alert.Severity severity, String message) {
        sendAlert(Alert.builder()
            .type(type)
            .severity(severity)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build());
    }
    
    /**
     * Send user-specific alert
     */
    public void sendUserAlert(UUID userId, Alert.AlertType type, Alert.Severity severity, 
                             String message, Map<String, Object> metrics) {
        sendAlert(Alert.builder()
            .type(type)
            .severity(severity)
            .message(message)
            .userId(userId)
            .metrics(metrics)
            .timestamp(LocalDateTime.now())
            .build());
    }
    
    /**
     * Send transaction-specific alert
     */
    public void sendTransactionAlert(UUID transactionId, Alert.AlertType type, 
                                   Alert.Severity severity, String message) {
        sendAlert(Alert.builder()
            .type(type)
            .severity(severity)
            .message(message)
            .transactionId(transactionId)
            .timestamp(LocalDateTime.now())
            .build());
    }
    
    @Transactional
    private void storeAlert(Alert alert) {
        try {
            // Store alert in database for audit trail
            AlertHistory alertHistory = AlertHistory.builder()
                .alertType(alert.getType())
                .severity(alert.getSeverity())
                .message(alert.getMessage())
                .entityId(alert.getEntityId())
                .entityType(alert.getEntityType())
                .transactionId(alert.getTransactionId())
                .userId(alert.getUserId())
                .metadata(alert.getMetadata())
                .timestamp(alert.getTimestamp())
                .resolved(false)
                .build();
            
            alertHistoryRepository.save(alertHistory);
            
            log.debug("Alert stored in database: {} - {}", alert.getType(), alert.getMessage());
            
        } catch (Exception e) {
            log.error("Failed to store alert in database: {}", alert, e);
            // Don't throw exception - alerting should be resilient
        }
    }
    
    /**
     * Get all alerts for a user
     */
    public Page<AlertHistory> getUserAlerts(UUID userId, Pageable pageable) {
        return alertHistoryRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }
    
    /**
     * Get unresolved alerts
     */
    public Page<AlertHistory> getUnresolvedAlerts(Pageable pageable) {
        return alertHistoryRepository.findByResolvedFalseOrderByTimestampDesc(pageable);
    }
    
    /**
     * Get alerts by type
     */
    public Page<AlertHistory> getAlertsByType(Alert.AlertType type, Pageable pageable) {
        return alertHistoryRepository.findByAlertTypeOrderByTimestampDesc(type, pageable);
    }
    
    /**
     * Get critical unresolved alerts
     */
    public List<AlertHistory> getCriticalUnresolvedAlerts() {
        return alertHistoryRepository.findBySeverityAndResolvedFalseOrderByTimestampDesc(Alert.Severity.CRITICAL);
    }
    
    /**
     * Resolve an alert
     */
    @Transactional
    public void resolveAlert(UUID alertId, String resolvedBy, String notes) {
        try {
            AlertHistory alert = alertHistoryRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
            
            alert.resolve(resolvedBy, notes);
            alertHistoryRepository.save(alert);
            
            log.info("Alert resolved: {} by {}", alertId, resolvedBy);
            
            // Send notification about resolution
            kafkaTemplate.send("analytics-alert-resolutions", alertId.toString(), 
                Map.of("alertId", alertId, "resolvedBy", resolvedBy, "notes", notes));
            
        } catch (Exception e) {
            log.error("Failed to resolve alert: {}", alertId, e);
            throw new RuntimeException("Failed to resolve alert", e);
        }
    }
    
    /**
     * Get alert statistics for dashboard
     */
    public Map<String, Object> getAlertStatistics(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> stats = alertHistoryRepository.getAlertStatistics(start, end);
            long totalAlerts = stats.stream().mapToLong(row -> (Long) row[2]).sum();
            long criticalAlerts = stats.stream()
                .filter(row -> Alert.Severity.CRITICAL.equals(row[1]))
                .mapToLong(row -> (Long) row[2])
                .sum();
            long unresolvedCount = alertHistoryRepository.findByResolvedFalseOrderByTimestampDesc(
                Pageable.unpaged()).getTotalElements();
            
            return Map.of(
                "totalAlerts", totalAlerts,
                "criticalAlerts", criticalAlerts,
                "unresolvedAlerts", unresolvedCount,
                "alertBreakdown", stats,
                "period", Map.of("start", start, "end", end)
            );
        } catch (Exception e) {
            log.error("Failed to get alert statistics", e);
            return Map.of("error", "Failed to retrieve statistics");
        }
    }
    
    /**
     * Get recent alerts for a user
     */
    public List<AlertHistory> getRecentUserAlerts(UUID userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return alertHistoryRepository.findRecentUserAlerts(userId, since);
    }
    
    /**
     * Check if user has too many recent alerts (rate limiting)
     */
    public boolean isUserAlertRateLimited(UUID userId, int maxAlertsPerHour) {
        List<AlertHistory> recentAlerts = getRecentUserAlerts(userId, 1);
        return recentAlerts.size() >= maxAlertsPerHour;
    }
    
    /**
     * Bulk resolve alerts by criteria
     */
    @Transactional
    public void bulkResolveAlerts(Alert.AlertType type, String resolvedBy, String notes) {
        try {
            List<AlertHistory> unresolvedAlerts = alertHistoryRepository
                .findBySeverityAndResolvedFalseOrderByTimestampDesc(Alert.Severity.LOW);
            
            unresolvedAlerts.stream()
                .filter(alert -> alert.getAlertType().equals(type))
                .forEach(alert -> {
                    alert.resolve(resolvedBy, notes);
                    alertHistoryRepository.save(alert);
                });
            
            log.info("Bulk resolved {} alerts of type {} by {}", 
                unresolvedAlerts.size(), type, resolvedBy);
                
        } catch (Exception e) {
            log.error("Failed to bulk resolve alerts", e);
            throw new RuntimeException("Failed to bulk resolve alerts", e);
        }
    }
    
    /**
     * Clean up old resolved alerts
     */
    @Transactional
    public void cleanupOldAlerts(int retentionDays) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            alertHistoryRepository.deleteByResolvedTrueAndResolvedAtBefore(cutoffDate);
            log.info("Cleaned up alerts older than {} days", retentionDays);
        } catch (Exception e) {
            log.error("Failed to cleanup old alerts", e);
        }
    }
}