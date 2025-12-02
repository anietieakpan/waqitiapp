package com.waqiti.notification.service;

import com.waqiti.notification.dto.*;
import java.util.List;

public interface TopicManagementService {
    
    // Topic CRUD operations
    NotificationTopicDto createTopic(CreateTopicRequest request);
    NotificationTopicDto updateTopic(String topicId, UpdateTopicRequest request);
    void deleteTopic(String topicId);
    
    // Topic retrieval
    NotificationTopicDto getTopicById(String topicId);
    NotificationTopicDto getTopicByName(String topicName);
    List<NotificationTopicDto> getAllTopics();
    List<NotificationTopicDto> getActiveTopics();
    List<NotificationTopicDto> getTopicsByCategory(String category);
    List<NotificationTopicDto> getAutoSubscribeTopics();
    
    // Topic categories
    List<TopicCategoryDto> getTopicCategories();
    
    // Topic statistics
    TopicStatisticsDto getTopicStatistics(String topicName);
    List<TopicStatisticsDto> getAllTopicStatistics();
    
    // Bulk operations
    BulkTopicOperationResponse bulkCreateTopics(BulkCreateTopicsRequest request);
    BulkTopicOperationResponse bulkUpdateTopics(BulkUpdateTopicsRequest request);
    
    // Topic validation
    boolean isValidTopicName(String topicName);
    void validateTopicName(String topicName);
}