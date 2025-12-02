package com.waqiti.notification.service.impl;

import com.waqiti.notification.domain.NotificationTopic;
import com.waqiti.notification.dto.*;
import com.waqiti.notification.exception.TopicNotFoundException;
import com.waqiti.notification.exception.InvalidTopicException;
import com.waqiti.notification.repository.NotificationTopicRepository;
import com.waqiti.notification.repository.PushNotificationLogRepository;
import com.waqiti.notification.repository.TopicSubscriptionRepository;
import com.waqiti.notification.service.TopicManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TopicManagementServiceImpl implements TopicManagementService {
    
    private final NotificationTopicRepository topicRepository;
    private final TopicSubscriptionRepository subscriptionRepository;
    private final PushNotificationLogRepository notificationLogRepository;
    
    // Predefined topic categories for the payment application
    private static final Map<String, TopicCategoryDto> TOPIC_CATEGORIES = Map.of(
        "PAYMENT", TopicCategoryDto.builder()
            .name("PAYMENT")
            .displayName("Payment Notifications")
            .description("Notifications about payments, transfers, and transactions")
            .icon("payment")
            .color("#4CAF50")
            .priority(1)
            .build(),
        "SECURITY", TopicCategoryDto.builder()
            .name("SECURITY")
            .displayName("Security Alerts")
            .description("Security-related notifications and alerts")
            .icon("security")
            .color("#F44336")
            .priority(2)
            .build(),
        "SOCIAL", TopicCategoryDto.builder()
            .name("SOCIAL")
            .displayName("Social")
            .description("Friend requests, messages, and social interactions")
            .icon("people")
            .color("#2196F3")
            .priority(3)
            .build(),
        "PROMOTIONAL", TopicCategoryDto.builder()
            .name("PROMOTIONAL")
            .displayName("Promotions & Offers")
            .description("Special offers, rewards, and promotional content")
            .icon("local_offer")
            .color("#FF9800")
            .priority(4)
            .build(),
        "SYSTEM", TopicCategoryDto.builder()
            .name("SYSTEM")
            .displayName("System Updates")
            .description("App updates, maintenance notifications, and system messages")
            .icon("settings")
            .color("#9E9E9E")
            .priority(5)
            .build()
    );
    
    @Override
    public NotificationTopicDto createTopic(CreateTopicRequest request) {
        log.info("Creating topic: {}", request.getName());
        
        validateTopicName(request.getName());
        
        if (topicRepository.existsByName(request.getName())) {
            throw new InvalidTopicException("Topic already exists: " + request.getName());
        }
        
        NotificationTopic topic = NotificationTopic.builder()
                .name(request.getName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .category(request.getCategory())
                .active(request.isActive())
                .autoSubscribe(request.isAutoSubscribe())
                .priority(request.getPriority())
                .icon(request.getIcon())
                .color(request.getColor())
                .build();
        
        topic = topicRepository.save(topic);
        log.info("Topic created successfully: {}", topic.getName());
        
        return mapToDto(topic);
    }
    
    @Override
    public NotificationTopicDto updateTopic(String topicId, UpdateTopicRequest request) {
        log.info("Updating topic: {}", topicId);
        
        NotificationTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found: " + topicId));
        
        if (request.getDisplayName() != null) {
            topic.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            topic.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            topic.setCategory(request.getCategory());
        }
        if (request.getActive() != null) {
            topic.setActive(request.getActive());
        }
        if (request.getAutoSubscribe() != null) {
            topic.setAutoSubscribe(request.getAutoSubscribe());
        }
        if (request.getPriority() != null) {
            topic.setPriority(request.getPriority());
        }
        if (request.getIcon() != null) {
            topic.setIcon(request.getIcon());
        }
        if (request.getColor() != null) {
            topic.setColor(request.getColor());
        }
        
        topic = topicRepository.save(topic);
        log.info("Topic updated successfully: {}", topic.getName());
        
        return mapToDto(topic);
    }
    
    @Override
    public void deleteTopic(String topicId) {
        log.info("Deleting topic: {}", topicId);
        
        NotificationTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found: " + topicId));
        
        // Instead of hard delete, deactivate the topic
        topic.setActive(false);
        topicRepository.save(topic);
        
        log.info("Topic deactivated: {}", topic.getName());
    }
    
    @Override
    @Transactional(readOnly = true)
    public NotificationTopicDto getTopicById(String topicId) {
        NotificationTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found: " + topicId));
        return mapToDto(topic);
    }
    
    @Override
    @Transactional(readOnly = true)
    public NotificationTopicDto getTopicByName(String topicName) {
        NotificationTopic topic = topicRepository.findByName(topicName)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found: " + topicName));
        return mapToDto(topic);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<NotificationTopicDto> getAllTopics() {
        return topicRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<NotificationTopicDto> getActiveTopics() {
        return topicRepository.findByActiveTrueOrderByPriorityDescNameAsc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<NotificationTopicDto> getTopicsByCategory(String category) {
        return topicRepository.findByActiveTrueAndCategory(category).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<NotificationTopicDto> getAutoSubscribeTopics() {
        return topicRepository.findByActiveTrueAndAutoSubscribeTrue().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<TopicCategoryDto> getTopicCategories() {
        List<String> activeCategories = topicRepository.findDistinctActiveCategories();
        
        return TOPIC_CATEGORIES.values().stream()
                .map(category -> {
                    long topicCount = topicRepository.findByActiveTrueAndCategory(category.getName()).size();
                    return category.toBuilder()
                            .topicCount((int) topicCount)
                            .build();
                })
                .filter(category -> activeCategories.contains(category.getName()) || category.getTopicCount() > 0)
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public TopicStatisticsDto getTopicStatistics(String topicName) {
        NotificationTopic topic = topicRepository.findByName(topicName)
                .orElseThrow(() -> new TopicNotFoundException("Topic not found: " + topicName));
        
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        long notificationsLast7Days = notificationLogRepository.countByStatusAndSentAtAfter("SENT", sevenDaysAgo);
        long notificationsLast30Days = notificationLogRepository.countByStatusAndSentAtAfter("SENT", thirtyDaysAgo);
        
        // Calculate delivery rate (this would need more sophisticated logic in a real implementation)
        double deliveryRate = notificationsLast7Days > 0 ? 0.95 : 0.0; // Placeholder
        
        return TopicStatisticsDto.builder()
                .topicName(topic.getName())
                .displayName(topic.getDisplayName())
                .category(topic.getCategory())
                .subscriberCount(topic.getSubscriberCount())
                .notificationsSentLast7Days(notificationsLast7Days)
                .notificationsSentLast30Days(notificationsLast30Days)
                .deliveryRate(deliveryRate)
                .subscribersByPlatform(new HashMap<>()) // Would be populated from actual data
                .active(topic.isActive())
                .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<TopicStatisticsDto> getAllTopicStatistics() {
        return topicRepository.findByActiveTrue().stream()
                .map(topic -> getTopicStatistics(topic.getName()))
                .collect(Collectors.toList());
    }
    
    @Override
    public BulkTopicOperationResponse bulkCreateTopics(BulkCreateTopicsRequest request) {
        log.info("Bulk creating {} topics", request.getTopics().size());
        
        long startTime = System.currentTimeMillis();
        List<String> successfulTopics = new ArrayList<>();
        List<BulkTopicOperationResponse.BulkOperationError> errors = new ArrayList<>();
        int skippedCount = 0;
        
        for (CreateTopicRequest topicRequest : request.getTopics()) {
            try {
                if (topicRepository.existsByName(topicRequest.getName())) {
                    if (request.isSkipExisting()) {
                        skippedCount++;
                        continue;
                    } else {
                        errors.add(BulkTopicOperationResponse.BulkOperationError.builder()
                                .topicName(topicRequest.getName())
                                .errorCode("TOPIC_EXISTS")
                                .errorMessage("Topic already exists")
                                .build());
                        if (request.isStopOnError()) break;
                        continue;
                    }
                }
                
                createTopic(topicRequest);
                successfulTopics.add(topicRequest.getName());
                
            } catch (Exception e) {
                errors.add(BulkTopicOperationResponse.BulkOperationError.builder()
                        .topicName(topicRequest.getName())
                        .errorCode("CREATION_FAILED")
                        .errorMessage(e.getMessage())
                        .build());
                        
                if (request.isStopOnError()) break;
            }
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return BulkTopicOperationResponse.builder()
                .totalRequested(request.getTopics().size())
                .successCount(successfulTopics.size())
                .failureCount(errors.size())
                .skippedCount(skippedCount)
                .successfulTopics(successfulTopics)
                .errors(errors)
                .processingTimeMs(processingTime)
                .build();
    }
    
    @Override
    public BulkTopicOperationResponse bulkUpdateTopics(BulkUpdateTopicsRequest request) {
        log.info("Bulk updating {} topics", request.getTopicUpdates().size());
        
        long startTime = System.currentTimeMillis();
        List<String> successfulTopics = new ArrayList<>();
        List<BulkTopicOperationResponse.BulkOperationError> errors = new ArrayList<>();
        int skippedCount = 0;
        
        for (Map.Entry<String, UpdateTopicRequest> entry : request.getTopicUpdates().entrySet()) {
            String topicId = entry.getKey();
            UpdateTopicRequest updateRequest = entry.getValue();
            
            try {
                if (!topicRepository.existsById(topicId)) {
                    if (request.isSkipNotFound()) {
                        skippedCount++;
                        continue;
                    } else {
                        errors.add(BulkTopicOperationResponse.BulkOperationError.builder()
                                .topicName(topicId)
                                .errorCode("TOPIC_NOT_FOUND")
                                .errorMessage("Topic not found")
                                .build());
                        if (request.isStopOnError()) break;
                        continue;
                    }
                }
                
                updateTopic(topicId, updateRequest);
                successfulTopics.add(topicId);
                
            } catch (Exception e) {
                errors.add(BulkTopicOperationResponse.BulkOperationError.builder()
                        .topicName(topicId)
                        .errorCode("UPDATE_FAILED")
                        .errorMessage(e.getMessage())
                        .build());
                        
                if (request.isStopOnError()) break;
            }
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return BulkTopicOperationResponse.builder()
                .totalRequested(request.getTopicUpdates().size())
                .successCount(successfulTopics.size())
                .failureCount(errors.size())
                .skippedCount(skippedCount)
                .successfulTopics(successfulTopics)
                .errors(errors)
                .processingTimeMs(processingTime)
                .build();
    }
    
    @Override
    public boolean isValidTopicName(String topicName) {
        if (topicName == null || topicName.trim().isEmpty()) {
            return false;
        }
        
        // FCM topic naming requirements:
        // - Can contain letters, numbers, underscores, and hyphens
        // - Must start with a letter
        // - Cannot be longer than 900 characters
        return topicName.matches("^[a-zA-Z][a-zA-Z0-9_-]{0,899}$");
    }
    
    @Override
    public void validateTopicName(String topicName) {
        if (!isValidTopicName(topicName)) {
            throw new InvalidTopicException(
                "Invalid topic name: " + topicName + 
                ". Topic names must start with a letter and contain only letters, numbers, underscores, and hyphens."
            );
        }
    }
    
    private NotificationTopicDto mapToDto(NotificationTopic topic) {
        return NotificationTopicDto.builder()
                .id(topic.getId())
                .name(topic.getName())
                .displayName(topic.getDisplayName())
                .description(topic.getDescription())
                .category(topic.getCategory())
                .active(topic.isActive())
                .autoSubscribe(topic.isAutoSubscribe())
                .subscriberCount(topic.getSubscriberCount())
                .priority(topic.getPriority())
                .icon(topic.getIcon())
                .color(topic.getColor())
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .createdBy(topic.getCreatedBy())
                .build();
    }
}