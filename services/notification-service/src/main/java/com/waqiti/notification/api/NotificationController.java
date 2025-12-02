package com.waqiti.notification.api;

import com.waqiti.notification.dto.NotificationListResponse;
import com.waqiti.notification.dto.NotificationResponse;
import com.waqiti.notification.dto.SendNotificationRequest;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.common.ratelimit.RateLimited;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    private final NotificationService notificationService;

    @PostMapping("/send")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM', 'NOTIFICATION_SENDER')")
    public ResponseEntity<List<NotificationResponse>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {
        log.info("Send notification request received");
        return ResponseEntity.ok(notificationService.sendNotification(request));
    }

    @GetMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 200, refillTokens = 200, refillPeriodMinutes = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Get notifications request received");

        UUID userId = getUserIdFromUserDetails(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return ResponseEntity.ok(notificationService.getNotifications(userId, pageable));
    }

    @GetMapping("/unread")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 200, refillTokens = 200, refillPeriodMinutes = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationListResponse> getUnreadNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Get unread notifications request received");

        UUID userId = getUserIdFromUserDetails(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId, pageable));
    }

    @GetMapping("/{id}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 300, refillTokens = 300, refillPeriodMinutes = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> getNotification(@PathVariable UUID id) {
        log.info("Get notification request received for ID: {}", id);
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    @PostMapping("/{id}/read")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable UUID id) {
        log.info("Mark notification as read request received for ID: {}", id);
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @PostMapping("/read-all")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("Mark all notifications as read request received");

        UUID userId = getUserIdFromUserDetails(userDetails);
        notificationService.markAllAsRead(userId);

        return ResponseEntity.ok().build();
    }

    /**
     * Helper method to extract user ID from UserDetails
     */
    private UUID getUserIdFromUserDetails(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }
}