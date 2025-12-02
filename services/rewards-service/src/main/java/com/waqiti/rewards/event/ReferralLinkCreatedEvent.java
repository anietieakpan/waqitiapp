package com.waqiti.rewards.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a new referral link is created
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralLinkCreatedEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;

    // Event payload
    private String linkId;
    private UUID userId;
    private String programId;
    private String programName;
    private String referralCode;
    private String shortUrl;
    private String channel;
    private Instant createdAt;

    public static ReferralLinkCreatedEvent create(
            String linkId, UUID userId, String programId, String programName,
            String referralCode, String shortUrl, String channel, String correlationId) {
        return ReferralLinkCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REFERRAL_LINK_CREATED")
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .linkId(linkId)
                .userId(userId)
                .programId(programId)
                .programName(programName)
                .referralCode(referralCode)
                .shortUrl(shortUrl)
                .channel(channel)
                .createdAt(Instant.now())
                .build();
    }
}
