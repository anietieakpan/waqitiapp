package com.waqiti.notification.repository;

import com.waqiti.notification.domain.NotificationTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTopicRepository extends JpaRepository<NotificationTopic, String> {
    
    Optional<NotificationTopic> findByName(String name);
    
    List<NotificationTopic> findByActiveTrue();
    
    List<NotificationTopic> findByActiveTrueOrderByPriorityDescNameAsc();
    
    List<NotificationTopic> findByCategory(String category);
    
    List<NotificationTopic> findByActiveTrueAndCategory(String category);
    
    List<NotificationTopic> findByAutoSubscribeTrue();
    
    List<NotificationTopic> findByActiveTrueAndAutoSubscribeTrue();
    
    @Query("SELECT nt FROM NotificationTopic nt WHERE nt.active = true AND nt.name IN :topicNames")
    List<NotificationTopic> findActiveByNames(@Param("topicNames") List<String> topicNames);
    
    @Modifying
    @Query("UPDATE NotificationTopic nt SET nt.subscriberCount = nt.subscriberCount + :increment WHERE nt.name = :topicName")
    void incrementSubscriberCount(@Param("topicName") String topicName, @Param("increment") long increment);
    
    @Modifying
    @Query("UPDATE NotificationTopic nt SET nt.subscriberCount = nt.subscriberCount - :decrement WHERE nt.name = :topicName AND nt.subscriberCount > 0")
    void decrementSubscriberCount(@Param("topicName") String topicName, @Param("decrement") long decrement);
    
    boolean existsByName(String name);
    
    @Query("SELECT DISTINCT nt.category FROM NotificationTopic nt WHERE nt.active = true")
    List<String> findDistinctActiveCategories();
}