package com.waqiti.notification.repository;

import com.waqiti.notification.domain.NotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {
    /**
     * Find users who have enabled a specific notification type
     */
    @Query("SELECT np.userId FROM NotificationPreferences np WHERE " +
           "(:type = 'APP' AND np.appNotificationsEnabled = true) OR " +
           "(:type = 'EMAIL' AND np.emailNotificationsEnabled = true) OR " +
           "(:type = 'SMS' AND np.smsNotificationsEnabled = true) OR " +
           "(:type = 'PUSH' AND np.pushNotificationsEnabled = true)")
    List<UUID> findUserIdsWithEnabledNotificationType(@Param("type") String type);

    /**
     * Find users who have enabled a specific category
     */
    @Query("SELECT np.userId FROM NotificationPreferences np JOIN np.categoryPreferences cp " +
           "WHERE KEY(cp) = :category AND VALUE(cp) = true")
    List<UUID> findUserIdsWithEnabledCategory(@Param("category") String category);

    /**
     * Find users with device tokens for push notifications
     */
    @Query("SELECT np FROM NotificationPreferences np WHERE " +
           "np.pushNotificationsEnabled = true AND np.deviceToken IS NOT NULL")
    List<NotificationPreferences> findUsersForPushNotifications();
}