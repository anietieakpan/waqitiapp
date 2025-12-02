package com.waqiti.notification.repository;

import com.waqiti.notification.domain.DeviceToken;
import com.waqiti.notification.domain.TopicSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicSubscriptionRepository extends JpaRepository<TopicSubscription, String> {
    
    Optional<TopicSubscription> findByDeviceTokenAndTopic(DeviceToken deviceToken, String topic);
    
    @Query("SELECT ts FROM TopicSubscription ts WHERE ts.deviceToken = :deviceToken AND ts.active = true")
    List<TopicSubscription> findActiveByDeviceToken(@Param("deviceToken") DeviceToken deviceToken);
    
    @Query("SELECT ts FROM TopicSubscription ts WHERE ts.deviceToken IN :deviceTokens AND ts.active = true")
    List<TopicSubscription> findActiveByDeviceTokens(@Param("deviceTokens") List<DeviceToken> deviceTokens);
    
    @Query("SELECT ts FROM TopicSubscription ts WHERE ts.topic = :topic AND ts.active = true")
    List<TopicSubscription> findActiveByTopic(@Param("topic") String topic);
    
    @Query("SELECT COUNT(ts) FROM TopicSubscription ts WHERE ts.topic = :topic AND ts.active = true")
    long countActiveByTopic(@Param("topic") String topic);
    
    @Query("SELECT DISTINCT ts.topic FROM TopicSubscription ts WHERE ts.active = true")
    List<String> findAllActiveTopics();
    
    void deleteByDeviceTokenAndTopic(DeviceToken deviceToken, String topic);
    
    @Modifying
    @Query("UPDATE TopicSubscription ts SET ts.active = false WHERE ts.deviceToken = :deviceToken")
    void deactivateAllForDevice(@Param("deviceToken") DeviceToken deviceToken);
    
    boolean existsByDeviceTokenAndTopicAndActiveTrue(DeviceToken deviceToken, String topic);
}