// File: services/notification-service/src/main/java/com/waqiti/notification/api/TwoFactorNotificationController.java
package com.waqiti.notification.api;

import com.waqiti.notification.dto.TwoFactorNotificationRequest;
import com.waqiti.notification.service.TwoFactorNotificationService;
import com.waqiti.common.ratelimit.RateLimited;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/2fa")
@RequiredArgsConstructor
@Slf4j
public class TwoFactorNotificationController {

    private final TwoFactorNotificationService twoFactorService;

    /**
     * Sends a 2FA verification code via SMS
     */
    @PostMapping("/sms")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Boolean> sendTwoFactorSms(
            @Valid @RequestBody TwoFactorNotificationRequest request) {

        log.info("Received request to send 2FA SMS to user: {}", request.getUserId());

        boolean success = twoFactorService.sendTwoFactorSms(
                request.getUserId(),
                request.getRecipient(),
                request.getVerificationCode()
        );

        return ResponseEntity.ok(success);
    }

    /**
     * Sends a 2FA verification code via email
     */
    @PostMapping("/email")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Boolean> sendTwoFactorEmail(
            @Valid @RequestBody TwoFactorNotificationRequest request) {

        log.info("Received request to send 2FA email to user: {}", request.getUserId());

        boolean success = twoFactorService.sendTwoFactorEmail(
                request.getUserId(),
                request.getRecipient(),
                request.getVerificationCode()
        );

        return ResponseEntity.ok(success);
    }
}