package com.waqiti.notification.repository;

import com.waqiti.notification.domain.PushNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface PushNotificationLogRepository extends JpaRepository<PushNotificationLog, String> {
    
    Page<PushNotificationLog> findByUserId(String userId, Pageable pageable);
    
    Page<PushNotificationLog> findByUserIdAndType(String userId, String type, Pageable pageable);
    
    List<PushNotificationLog> findByStatus(String status);
    
    List<PushNotificationLog> findByStatusAndRetryCountLessThan(String status, Integer maxRetries);
    
    @Query("SELECT pnl FROM PushNotificationLog pnl WHERE pnl.sentAt >= :since")
    List<PushNotificationLog> findBySentAtAfter(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(pnl) FROM PushNotificationLog pnl WHERE pnl.sentAt >= :since")
    long countBySentAtAfter(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(pnl) FROM PushNotificationLog pnl WHERE pnl.status = :status AND pnl.sentAt >= :since")
    long countByStatusAndSentAtAfter(@Param("status") String status, @Param("since") LocalDateTime since);
    
    @Query("SELECT pnl.type as type, COUNT(pnl) as count FROM PushNotificationLog pnl " +
           "WHERE pnl.sentAt >= :since GROUP BY pnl.type")
    List<Map<String, Object>> countByTypeGrouped(@Param("since") LocalDateTime since);
    
    @Query("SELECT pnl.status as status, COUNT(pnl) as count FROM PushNotificationLog pnl " +
           "WHERE pnl.sentAt >= :since GROUP BY pnl.status")
    List<Map<String, Object>> countByStatusGrouped(@Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(pnl.sentCount) FROM PushNotificationLog pnl WHERE pnl.sentAt >= :since AND pnl.sentCount > 0")
    Double getAverageSentCount(@Param("since") LocalDateTime since);
    
    @Query("SELECT SUM(pnl.sentCount) FROM PushNotificationLog pnl WHERE pnl.sentAt >= :since")
    Long getTotalSentCount(@Param("since") LocalDateTime since);
    
    @Query("SELECT SUM(pnl.failedCount) FROM PushNotificationLog pnl WHERE pnl.sentAt >= :since")
    Long getTotalFailedCount(@Param("since") LocalDateTime since);
    
    void deleteByNotificationId(String notificationId);
    
    @Modifying
    @Query("DELETE FROM PushNotificationLog pnl WHERE pnl.sentAt < :before")
    void deleteOldLogs(@Param("before") LocalDateTime before);
}