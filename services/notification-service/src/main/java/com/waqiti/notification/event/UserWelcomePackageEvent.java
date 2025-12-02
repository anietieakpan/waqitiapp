package com.waqiti.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Represents the event sent when a user's KYC is complete and they
 * should receive a welcome package (e.g., a welcome email).
 * Received from the user-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWelcomePackageEvent {
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String accountTier;
    private List<String> features;
}
