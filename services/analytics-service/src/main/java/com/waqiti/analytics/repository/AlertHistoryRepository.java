package com.waqiti.analytics.repository;

import com.waqiti.analytics.domain.AlertHistory;
import com.waqiti.analytics.dto.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AlertHistory entities
 */
@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, UUID> {
    
    // Find alerts by user
    Page<AlertHistory> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);
    
    // Find unresolved alerts
    Page<AlertHistory> findByResolvedFalseOrderByTimestampDesc(Pageable pageable);
    
    // Find alerts by type
    Page<AlertHistory> findByAlertTypeOrderByTimestampDesc(Alert.AlertType alertType, Pageable pageable);
    
    // Find alerts by severity
    Page<AlertHistory> findBySeverityOrderByTimestampDesc(Alert.Severity severity, Pageable pageable);
    
    // Find alerts within time range
    Page<AlertHistory> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    // Find critical unresolved alerts
    List<AlertHistory> findBySeverityAndResolvedFalseOrderByTimestampDesc(Alert.Severity severity);
    
    // Count unresolved alerts by user
    long countByUserIdAndResolvedFalse(UUID userId);
    
    // Count alerts by type in time period
    @Query("SELECT COUNT(ah) FROM AlertHistory ah WHERE ah.alertType = :type " +
           "AND ah.timestamp BETWEEN :start AND :end")
    long countByTypeInPeriod(@Param("type") Alert.AlertType type, 
                           @Param("start") LocalDateTime start, 
                           @Param("end") LocalDateTime end);
    
    // Get alert statistics
    @Query("SELECT ah.alertType, ah.severity, COUNT(ah) FROM AlertHistory ah " +
           "WHERE ah.timestamp BETWEEN :start AND :end " +
           "GROUP BY ah.alertType, ah.severity")
    List<Object[]> getAlertStatistics(@Param("start") LocalDateTime start, 
                                    @Param("end") LocalDateTime end);
    
    // Find recent alerts for a user
    @Query("SELECT ah FROM AlertHistory ah WHERE ah.userId = :userId " +
           "AND ah.timestamp >= :since ORDER BY ah.timestamp DESC")
    List<AlertHistory> findRecentUserAlerts(@Param("userId") UUID userId, 
                                          @Param("since") LocalDateTime since);
    
    // Find alerts related to specific entity
    Page<AlertHistory> findByEntityIdAndEntityTypeOrderByTimestampDesc(
        UUID entityId, String entityType, Pageable pageable);
    
    // Find transaction-related alerts
    Page<AlertHistory> findByTransactionIdOrderByTimestampDesc(UUID transactionId, Pageable pageable);
    
    // Average resolution time by alert type
    @Query("SELECT ah.alertType, AVG(TIMESTAMPDIFF(MINUTE, ah.timestamp, ah.resolvedAt)) " +
           "FROM AlertHistory ah WHERE ah.resolved = true " +
           "AND ah.timestamp BETWEEN :start AND :end " +
           "GROUP BY ah.alertType")
    List<Object[]> getAverageResolutionTimeByType(@Param("start") LocalDateTime start, 
                                                @Param("end") LocalDateTime end);
    
    // Delete old resolved alerts (for cleanup)
    void deleteByResolvedTrueAndResolvedAtBefore(LocalDateTime cutoffDate);
}