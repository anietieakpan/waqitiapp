package com.waqiti.rewards.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a referral link is clicked
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralClickedEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;

    // Event payload
    private String clickId;
    private String linkId;
    private String referralCode;
    private UUID referrerId;
    private String ipAddress;
    private String userAgent;
    private String deviceType;
    private String browser;
    private String countryCode;
    private Boolean isUnique;
    private Instant clickedAt;

    public static ReferralClickedEvent create(
            String clickId, String linkId, String referralCode, UUID referrerId,
            String ipAddress, String userAgent, String deviceType, String browser,
            String countryCode, Boolean isUnique, String correlationId) {
        return ReferralClickedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REFERRAL_LINK_CLICKED")
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .clickId(clickId)
                .linkId(linkId)
                .referralCode(referralCode)
                .referrerId(referrerId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(deviceType)
                .browser(browser)
                .countryCode(countryCode)
                .isUnique(isUnique)
                .clickedAt(Instant.now())
                .build();
    }
}
