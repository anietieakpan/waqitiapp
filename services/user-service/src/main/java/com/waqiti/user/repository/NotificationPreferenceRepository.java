package com.waqiti.user.repository;

import com.waqiti.user.entity.NotificationPreference;
import com.waqiti.user.entity.NotificationPreference.NotificationType;
import com.waqiti.user.entity.NotificationPreference.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Notification Preference management
 * 
 * PERFORMANCE: Optimized queries with composite indexes
 * COMPLIANCE: All queries support audit trail requirements
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    
    List<NotificationPreference> findByUserId(String userId);
    
    Optional<NotificationPreference> findByUserIdAndNotificationTypeAndChannel(
        String userId,
        NotificationType notificationType,
        NotificationChannel channel
    );
    
    List<NotificationPreference> findByUserIdAndNotificationType(
        String userId,
        NotificationType notificationType
    );
    
    @Query("SELECT np FROM NotificationPreference np WHERE np.userId = :userId " +
           "AND np.notificationType = :type AND np.enabled = true")
    List<NotificationPreference> findEnabledChannelsByUserIdAndType(
        @Param("userId") String userId,
        @Param("type") NotificationType type
    );
    
    @Query("SELECT np FROM NotificationPreference np WHERE np.userId = :userId " +
           "AND np.enabled = true")
    List<NotificationPreference> findEnabledByUserId(@Param("userId") String userId);
    
    @Query("SELECT np FROM NotificationPreference np WHERE np.userId = :userId " +
           "AND np.channel = :channel AND np.enabled = true")
    List<NotificationPreference> findEnabledByUserIdAndChannel(
        @Param("userId") String userId,
        @Param("channel") NotificationChannel channel
    );
    
    @Query("SELECT COUNT(np) FROM NotificationPreference np WHERE np.userId = :userId " +
           "AND np.enabled = true")
    long countEnabledByUserId(@Param("userId") String userId);
    
    @Query("SELECT np FROM NotificationPreference np WHERE np.notificationType = :type " +
           "AND np.enabled = true")
    List<NotificationPreference> findEnabledByType(@Param("type") NotificationType type);
    
    @Query("SELECT np FROM NotificationPreference np WHERE np.optOutDate IS NOT NULL " +
           "ORDER BY np.optOutDate DESC")
    List<NotificationPreference> findAllOptedOut();
    
    @Query("SELECT np FROM NotificationPreference np WHERE np.optOutDate BETWEEN :startDate AND :endDate")
    List<NotificationPreference> findOptedOutBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT np.notificationType, COUNT(np) FROM NotificationPreference np " +
           "WHERE np.enabled = true GROUP BY np.notificationType")
    List<Object[]> countEnabledByType();
    
    @Query("SELECT np.channel, COUNT(np) FROM NotificationPreference np " +
           "WHERE np.enabled = true GROUP BY np.channel")
    List<Object[]> countEnabledByChannel();
    
    boolean existsByUserIdAndNotificationTypeAndChannel(
        String userId,
        NotificationType notificationType,
        NotificationChannel channel
    );
}