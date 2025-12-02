package com.waqiti.notification.controller;

import com.waqiti.notification.dto.*;
import com.waqiti.notification.service.TopicManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/topics")
@RequiredArgsConstructor
@Validated
@Tag(name = "Topic Management", description = "Notification topic management")
public class TopicManagementController {

    private final TopicManagementService topicManagementService;

    @Operation(
        summary = "Create notification topic",
        description = "Create a new notification topic for push notifications"
    )
    @ApiResponse(responseCode = "201", description = "Topic created successfully")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationTopicDto> createTopic(
            @Valid @RequestBody CreateTopicRequest request) {
        log.info("Creating topic: {}", request.getName());
        
        NotificationTopicDto topic = topicManagementService.createTopic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(topic);
    }

    @Operation(
        summary = "Update notification topic",
        description = "Update an existing notification topic"
    )
    @ApiResponse(responseCode = "200", description = "Topic updated successfully")
    @PutMapping("/{topicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationTopicDto> updateTopic(
            @PathVariable @NotBlank String topicId,
            @Valid @RequestBody UpdateTopicRequest request) {
        log.info("Updating topic: {}", topicId);
        
        NotificationTopicDto topic = topicManagementService.updateTopic(topicId, request);
        return ResponseEntity.ok(topic);
    }

    @Operation(
        summary = "Delete notification topic",
        description = "Delete (deactivate) a notification topic"
    )
    @ApiResponse(responseCode = "204", description = "Topic deleted successfully")
    @DeleteMapping("/{topicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTopic(
            @PathVariable @NotBlank String topicId) {
        log.info("Deleting topic: {}", topicId);
        
        topicManagementService.deleteTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get topic by ID",
        description = "Retrieve a notification topic by its ID"
    )
    @ApiResponse(responseCode = "200", description = "Topic retrieved successfully")
    @GetMapping("/{topicId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<NotificationTopicDto> getTopicById(
            @PathVariable @NotBlank String topicId) {
        log.debug("Getting topic by ID: {}", topicId);
        
        NotificationTopicDto topic = topicManagementService.getTopicById(topicId);
        return ResponseEntity.ok(topic);
    }

    @Operation(
        summary = "Get topic by name",
        description = "Retrieve a notification topic by its name"
    )
    @ApiResponse(responseCode = "200", description = "Topic retrieved successfully")
    @GetMapping("/name/{topicName}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<NotificationTopicDto> getTopicByName(
            @PathVariable @NotBlank String topicName) {
        log.debug("Getting topic by name: {}", topicName);
        
        NotificationTopicDto topic = topicManagementService.getTopicByName(topicName);
        return ResponseEntity.ok(topic);
    }

    @Operation(
        summary = "Get all topics",
        description = "Retrieve all notification topics"
    )
    @ApiResponse(responseCode = "200", description = "Topics retrieved successfully")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationTopicDto>> getAllTopics() {
        log.debug("Getting all topics");
        
        List<NotificationTopicDto> topics = topicManagementService.getAllTopics();
        return ResponseEntity.ok(topics);
    }

    @Operation(
        summary = "Get active topics",
        description = "Retrieve all active notification topics"
    )
    @ApiResponse(responseCode = "200", description = "Active topics retrieved successfully")
    @GetMapping("/active")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<NotificationTopicDto>> getActiveTopics() {
        log.debug("Getting active topics");
        
        List<NotificationTopicDto> topics = topicManagementService.getActiveTopics();
        return ResponseEntity.ok(topics);
    }

    @Operation(
        summary = "Get topics by category",
        description = "Retrieve notification topics by category"
    )
    @ApiResponse(responseCode = "200", description = "Topics retrieved successfully")
    @GetMapping("/category/{category}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<NotificationTopicDto>> getTopicsByCategory(
            @PathVariable @NotBlank String category) {
        log.debug("Getting topics by category: {}", category);
        
        List<NotificationTopicDto> topics = topicManagementService.getTopicsByCategory(category);
        return ResponseEntity.ok(topics);
    }

    @Operation(
        summary = "Get auto-subscribe topics",
        description = "Retrieve topics that new users are automatically subscribed to"
    )
    @ApiResponse(responseCode = "200", description = "Auto-subscribe topics retrieved successfully")
    @GetMapping("/auto-subscribe")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<NotificationTopicDto>> getAutoSubscribeTopics() {
        log.debug("Getting auto-subscribe topics");
        
        List<NotificationTopicDto> topics = topicManagementService.getAutoSubscribeTopics();
        return ResponseEntity.ok(topics);
    }

    @Operation(
        summary = "Get topic categories",
        description = "Retrieve all available topic categories"
    )
    @ApiResponse(responseCode = "200", description = "Categories retrieved successfully")
    @GetMapping("/categories")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<TopicCategoryDto>> getTopicCategories() {
        log.debug("Getting topic categories");
        
        List<TopicCategoryDto> categories = topicManagementService.getTopicCategories();
        return ResponseEntity.ok(categories);
    }

    @Operation(
        summary = "Get topic statistics",
        description = "Retrieve statistics for a specific topic"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @GetMapping("/{topicName}/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TopicStatisticsDto> getTopicStatistics(
            @PathVariable @NotBlank String topicName) {
        log.debug("Getting statistics for topic: {}", topicName);
        
        TopicStatisticsDto statistics = topicManagementService.getTopicStatistics(topicName);
        return ResponseEntity.ok(statistics);
    }

    @Operation(
        summary = "Get all topic statistics",
        description = "Retrieve statistics for all topics"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TopicStatisticsDto>> getAllTopicStatistics() {
        log.debug("Getting statistics for all topics");
        
        List<TopicStatisticsDto> statistics = topicManagementService.getAllTopicStatistics();
        return ResponseEntity.ok(statistics);
    }

    @Operation(
        summary = "Bulk create topics",
        description = "Create multiple notification topics in a single operation"
    )
    @ApiResponse(responseCode = "200", description = "Bulk operation completed")
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkTopicOperationResponse> bulkCreateTopics(
            @Valid @RequestBody BulkCreateTopicsRequest request) {
        log.info("Bulk creating {} topics", request.getTopics().size());
        
        BulkTopicOperationResponse response = topicManagementService.bulkCreateTopics(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Bulk update topics",
        description = "Update multiple notification topics in a single operation"
    )
    @ApiResponse(responseCode = "200", description = "Bulk operation completed")
    @PutMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkTopicOperationResponse> bulkUpdateTopics(
            @Valid @RequestBody BulkUpdateTopicsRequest request) {
        log.info("Bulk updating {} topics", request.getTopicUpdates().size());
        
        BulkTopicOperationResponse response = topicManagementService.bulkUpdateTopics(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Validate topic name",
        description = "Validate if a topic name is valid for FCM"
    )
    @ApiResponse(responseCode = "200", description = "Validation result")
    @GetMapping("/validate/{topicName}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> validateTopicName(
            @PathVariable @NotBlank String topicName) {
        log.debug("Validating topic name: {}", topicName);
        
        boolean isValid = topicManagementService.isValidTopicName(topicName);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }
}