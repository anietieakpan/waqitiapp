package com.waqiti.notification.controller;

import com.waqiti.notification.dto.*;
import com.waqiti.notification.service.PushNotificationService;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/push")
@RequiredArgsConstructor
@Validated
@Tag(name = "Push Notifications", description = "Push notification management")
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;

    @Operation(
        summary = "Register device token",
        description = "Register or update device token for push notifications"
    )
    @ApiResponse(responseCode = "200", description = "Device token registered successfully")
    @PostMapping("/devices/register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DeviceRegistrationResponse> registerDevice(
            @Valid @RequestBody DeviceRegistrationRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Registering device for user: {}, platform: {}", userId, request.getPlatform());
        
        DeviceRegistrationResponse response = pushNotificationService.registerDevice(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Update device token",
        description = "Update existing device token"
    )
    @ApiResponse(responseCode = "200", description = "Device token updated successfully")
    @PutMapping("/devices/{deviceId}/token")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DeviceRegistrationResponse> updateDeviceToken(
            @PathVariable String deviceId,
            @Valid @RequestBody UpdateTokenRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Updating device token for device: {}", deviceId);
        
        DeviceRegistrationResponse response = pushNotificationService.updateDeviceToken(
            userId, deviceId, request.getToken()
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Unregister device",
        description = "Remove device from push notifications"
    )
    @ApiResponse(responseCode = "204", description = "Device unregistered successfully")
    @DeleteMapping("/devices/{deviceId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> unregisterDevice(
            @PathVariable String deviceId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Unregistering device: {} for user: {}", deviceId, userId);
        
        pushNotificationService.unregisterDevice(userId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Subscribe to topic",
        description = "Subscribe device to a notification topic"
    )
    @ApiResponse(responseCode = "200", description = "Subscribed to topic successfully")
    @PostMapping("/topics/{topic}/subscribe")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TopicSubscriptionResponse> subscribeToTopic(
            @PathVariable @NotBlank String topic,
            @Valid @RequestBody TopicSubscriptionRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Subscribing user: {} to topic: {}", userId, topic);
        
        TopicSubscriptionResponse response = pushNotificationService.subscribeToTopic(
            userId, request.getDeviceId(), topic
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Unsubscribe from topic",
        description = "Unsubscribe device from a notification topic"
    )
    @ApiResponse(responseCode = "204", description = "Unsubscribed from topic successfully")
    @DeleteMapping("/topics/{topic}/unsubscribe")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> unsubscribeFromTopic(
            @PathVariable @NotBlank String topic,
            @RequestParam String deviceId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Unsubscribing user: {} from topic: {}", userId, topic);
        
        pushNotificationService.unsubscribeFromTopic(userId, deviceId, topic);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get user topics",
        description = "Get all topics user is subscribed to"
    )
    @ApiResponse(responseCode = "200", description = "Topics retrieved successfully")
    @GetMapping("/topics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserTopicsResponse> getUserTopics(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String deviceId) {
        log.debug("Getting topics for user: {}", userId);
        
        UserTopicsResponse response = pushNotificationService.getUserTopics(userId, deviceId);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get notification preferences",
        description = "Get user's push notification preferences"
    )
    @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully")
    @GetMapping("/preferences")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<NotificationPreferencesDto> getPreferences(
            @RequestHeader("X-User-Id") String userId) {
        log.debug("Getting notification preferences for user: {}", userId);
        
        NotificationPreferencesDto preferences = pushNotificationService.getPreferences(userId);
        return ResponseEntity.ok(preferences);
    }

    @Operation(
        summary = "Update notification preferences",
        description = "Update user's push notification preferences"
    )
    @ApiResponse(responseCode = "200", description = "Preferences updated successfully")
    @PutMapping("/preferences")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<NotificationPreferencesDto> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Updating notification preferences for user: {}", userId);
        
        NotificationPreferencesDto preferences = pushNotificationService.updatePreferences(userId, request);
        return ResponseEntity.ok(preferences);
    }

    @Operation(
        summary = "Send test notification",
        description = "Send a test push notification to user's devices"
    )
    @ApiResponse(responseCode = "200", description = "Test notification sent successfully")
    @PostMapping("/test")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TestNotificationResponse> sendTestNotification(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String deviceId) {
        log.info("Sending test notification to user: {}", userId);
        
        TestNotificationResponse response = pushNotificationService.sendTestNotification(userId, deviceId);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Send notification to user",
        description = "Send push notification to specific user (internal API)"
    )
    @ApiResponse(responseCode = "202", description = "Notification queued successfully")
    @PostMapping("/send/user/{userId}")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<PushNotificationResponse> sendToUser(
            @PathVariable String userId,
            @Valid @RequestBody PushSendNotificationRequest request) {
        log.info("Sending notification to user: {}, type: {}", userId, request.getType());
        
        PushNotificationResponse response = pushNotificationService.sendToUser(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(
        summary = "Send notification to topic",
        description = "Send push notification to all subscribers of a topic (internal API)"
    )
    @ApiResponse(responseCode = "202", description = "Notification queued successfully")
    @PostMapping("/send/topic/{topic}")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<PushNotificationResponse> sendToTopic(
            @PathVariable String topic,
            @Valid @RequestBody PushSendNotificationRequest request) {
        log.info("Sending notification to topic: {}, type: {}", topic, request.getType());
        
        PushNotificationResponse response = pushNotificationService.sendToTopic(topic, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(
        summary = "Send bulk notifications",
        description = "Send push notifications to multiple users (internal API)"
    )
    @ApiResponse(responseCode = "202", description = "Notifications queued successfully")
    @PostMapping("/send/bulk")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<BulkNotificationResponse> sendBulkNotifications(
            @Valid @RequestBody BulkNotificationRequest request) {
        log.info("Sending bulk notifications to {} users", request.getUserIds().size());
        
        BulkNotificationResponse response = pushNotificationService.sendBulkNotifications(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(
        summary = "Get device statistics",
        description = "Get push notification device statistics for user"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @GetMapping("/stats/devices")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DeviceStatisticsDto> getDeviceStatistics(
            @RequestHeader("X-User-Id") String userId) {
        log.debug("Getting device statistics for user: {}", userId);
        
        DeviceStatisticsDto stats = pushNotificationService.getDeviceStatistics(userId);
        return ResponseEntity.ok(stats);
    }

    @Operation(
        summary = "Admin: Get system statistics",
        description = "Get system-wide push notification statistics (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemPushStatsDto> getSystemStatistics() {
        log.info("Getting system push notification statistics");
        
        SystemPushStatsDto stats = pushNotificationService.getSystemStatistics();
        return ResponseEntity.ok(stats);
    }

    @Operation(
        summary = "Admin: Clean invalid tokens",
        description = "Remove invalid device tokens from the system (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Invalid tokens cleaned successfully")
    @PostMapping("/admin/clean-tokens")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CleanupResponse> cleanInvalidTokens() {
        log.info("Starting invalid token cleanup");
        
        CleanupResponse response = pushNotificationService.cleanInvalidTokens();
        return ResponseEntity.ok(response);
    }
}