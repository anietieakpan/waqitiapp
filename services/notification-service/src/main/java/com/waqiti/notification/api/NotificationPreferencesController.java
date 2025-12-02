package com.waqiti.notification.api;

import com.waqiti.notification.dto.NotificationPreferencesResponse;
import com.waqiti.notification.dto.UpdatePreferencesRequest;
import com.waqiti.notification.service.NotificationPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferencesController {
    private final NotificationPreferencesService preferencesService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationPreferencesResponse> getPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Get notification preferences request received");

        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(preferencesService.getPreferences(userId));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        log.info("Update notification preferences request received");

        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(preferencesService.updatePreferences(userId, request));
    }

    @PostMapping("/device-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updateDeviceToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody String deviceToken) {
        log.info("Update device token request received");

        UUID userId = getUserIdFromUserDetails(userDetails);
        preferencesService.updateDeviceToken(userId, deviceToken);

        return ResponseEntity.ok().build();
    }

    /**
     * Helper method to extract user ID from UserDetails
     */
    private UUID getUserIdFromUserDetails(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }
}