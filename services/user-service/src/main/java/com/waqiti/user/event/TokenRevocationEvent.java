package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event for token revocation
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TokenRevocationEvent extends UserEvent {
    
    private String tokenId;
    private String tokenType; // ACCESS_TOKEN, REFRESH_TOKEN, API_KEY, SESSION_TOKEN
    private String revocationReason; // LOGOUT, SECURITY_BREACH, PASSWORD_CHANGE, ADMIN_ACTION, EXPIRED, SUSPICIOUS_ACTIVITY
    private String revokedBy; // USER, ADMIN, SYSTEM, SECURITY_TEAM
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String clientId;
    private String deviceId;
    private String ipAddress;
    private List<String> scopes;
    private boolean revokeAllTokens;
    private boolean forceLogout;
    private String securityTicketId;
    private String notes;
    
    public TokenRevocationEvent() {
        super("TOKEN_REVOCATION");
    }
    
    public static TokenRevocationEvent userLogout(String userId, String tokenId, String tokenType, 
                                                String ipAddress, String deviceId) {
        TokenRevocationEvent event = new TokenRevocationEvent();
        event.setUserId(userId);
        event.setTokenId(tokenId);
        event.setTokenType(tokenType);
        event.setRevocationReason("LOGOUT");
        event.setRevokedBy("USER");
        event.setIpAddress(ipAddress);
        event.setDeviceId(deviceId);
        event.setRevokedAt(LocalDateTime.now());
        event.setForceLogout(true);
        return event;
    }
    
    public static TokenRevocationEvent securityBreach(String userId, String tokenId, String tokenType, 
                                                    String reason, String ticketId) {
        TokenRevocationEvent event = new TokenRevocationEvent();
        event.setUserId(userId);
        event.setTokenId(tokenId);
        event.setTokenType(tokenType);
        event.setRevocationReason("SECURITY_BREACH");
        event.setRevokedBy("SECURITY_TEAM");
        event.setSecurityTicketId(ticketId);
        event.setNotes(reason);
        event.setRevokedAt(LocalDateTime.now());
        event.setRevokeAllTokens(true);
        event.setForceLogout(true);
        return event;
    }
    
    public static TokenRevocationEvent passwordChange(String userId, boolean revokeAll) {
        TokenRevocationEvent event = new TokenRevocationEvent();
        event.setUserId(userId);
        event.setRevocationReason("PASSWORD_CHANGE");
        event.setRevokedBy("SYSTEM");
        event.setRevokedAt(LocalDateTime.now());
        event.setRevokeAllTokens(revokeAll);
        event.setForceLogout(revokeAll);
        return event;
    }
}