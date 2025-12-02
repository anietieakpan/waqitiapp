package com.waqiti.notification.repository;

import com.waqiti.notification.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {
    
    List<NotificationHistory> findByUserId(UUID userId);
    
    List<NotificationHistory> findByUserIdAndType(UUID userId, String type);
    
    @Query("SELECT nh FROM NotificationHistory nh WHERE nh.userId = :userId " +
           "AND nh.templateCode = :templateCode " +
           "AND nh.createdAt >= :since " +
           "ORDER BY nh.createdAt DESC")
    List<NotificationHistory> findRecentByUserAndTemplate(
        @Param("userId") UUID userId,
        @Param("templateCode") String templateCode,
        @Param("since") LocalDateTime since
    );
    
    @Query("SELECT COUNT(nh) > 0 FROM NotificationHistory nh WHERE nh.userId = :userId " +
           "AND nh.templateCode = :templateCode " +
           "AND nh.createdAt >= :since")
    boolean existsRecentNotification(
        @Param("userId") UUID userId,
        @Param("templateCode") String templateCode,
        @Param("since") LocalDateTime since
    );
    
    @Query("SELECT nh FROM NotificationHistory nh WHERE nh.userId = :userId " +
           "AND nh.templateCode = 'low_balance_alert' " +
           "AND nh.createdAt >= :since " +
           "ORDER BY nh.createdAt DESC " +
           "LIMIT 1")
    Optional<NotificationHistory> findLastLowBalanceAlert(
        @Param("userId") UUID userId,
        @Param("since") LocalDateTime since
    );
    
    @Query("SELECT nh.userId, COUNT(nh) FROM NotificationHistory nh " +
           "WHERE nh.createdAt >= :since " +
           "GROUP BY nh.userId")
    List<Object[]> countNotificationsByUser(@Param("since") LocalDateTime since);
    
    @Query("SELECT nh FROM NotificationHistory nh WHERE nh.scheduledFor IS NOT NULL " +
           "AND nh.status = 'SCHEDULED' " +
           "AND nh.scheduledFor <= :now " +
           "ORDER BY nh.scheduledFor ASC")
    List<NotificationHistory> findScheduledNotificationsToSend(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(nh) FROM NotificationHistory nh WHERE nh.userId = :userId " +
           "AND nh.createdAt >= :startDate " +
           "AND nh.createdAt <= :endDate")
    long countUserNotificationsInPeriod(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}