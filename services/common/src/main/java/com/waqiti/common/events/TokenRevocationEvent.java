package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when user tokens are revoked
 */
@Data
@Builder
public class TokenRevocationEvent {
    
    private UUID userId;
    private String reason;
    private LocalDateTime revokedAt;
    private boolean revokeAllTokens;
    private String tokenId;
    private String tokenType;
    private String revokingSystem;
    
    @Builder.Default
    private String eventType = "TOKENS_REVOKED";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}