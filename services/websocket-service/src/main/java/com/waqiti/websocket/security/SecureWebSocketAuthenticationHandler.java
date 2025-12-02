package com.waqiti.websocket.security;

import com.waqiti.common.security.SecureTokenVaultService;
import com.waqiti.common.fraud.ComprehensiveFraudBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Secure WebSocket authentication handler with comprehensive security features:
 * - JWT token validation with refresh capability
 * - Connection rate limiting and fraud detection
 * - Session management and cleanup
 * - IP-based blocking and geolocation validation
 * - Device fingerprinting and anomaly detection
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecureWebSocketAuthenticationHandler implements ChannelInterceptor {

    private final WebSocketJWTService jwtService;
    private final ComprehensiveFraudBlacklistService fraudService;
    private final SecureTokenVaultService tokenVaultService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${websocket.security.max-connections-per-user:5}")
    private int maxConnectionsPerUser;
    
    @Value("${websocket.security.rate-limit-per-ip:10}")
    private int rateLimitPerIp;
    
    @Value("${websocket.security.connection-timeout-minutes:30}")
    private int connectionTimeoutMinutes;
    
    @Value("${websocket.security.enable-geolocation-check:true}")
    private boolean enableGeolocationCheck;
    
    @Value("${websocket.security.enable-device-fingerprinting:true}")
    private boolean enableDeviceFingerprinting;

    // Connection tracking
    private final Map<String, AtomicInteger> ipConnectionCounts = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userConnections = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> connectionAttempts = new ConcurrentHashMap<>();
    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();

    // Cache keys
    private static final String WS_SESSION_PREFIX = "ws_session:";
    private static final String WS_IP_RATE_PREFIX = "ws_rate:";
    private static final String WS_BLOCKED_PREFIX = "ws_blocked:";
    private static final String WS_DEVICE_PREFIX = "ws_device:";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor == null) {
            return message;
        }

        try {
            switch (accessor.getCommand()) {
                case CONNECT:
                    return handleConnect(message, accessor);
                case DISCONNECT:
                    return handleDisconnect(message, accessor);
                case SUBSCRIBE:
                    return handleSubscribe(message, accessor);
                case SEND:
                    return handleSend(message, accessor);
                default:
                    return validateActiveSession(message, accessor);
            }
        } catch (Exception e) {
            log.error("WebSocket security error: {}", e.getMessage(), e);
            return null; // Block the message on security errors
        }
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        try {
            log.info("Processing WebSocket connection attempt: sessionId={}", accessor.getSessionId());

            // Extract connection metadata
            WebSocketConnectionMetadata metadata = extractConnectionMetadata(accessor);
            
            // 1. IP-based security checks
            SecurityCheckResult ipCheck = performIpSecurityCheck(metadata.getIpAddress());
            if (!ipCheck.isAllowed()) {
                log.warn("WebSocket connection blocked - IP security check failed: {} - {}", 
                        metadata.getIpAddress(), ipCheck.getReason());
                return null;
            }

            // 2. Rate limiting check
            if (!checkRateLimit(metadata.getIpAddress())) {
                log.warn("WebSocket connection blocked - rate limit exceeded: {}", metadata.getIpAddress());
                return null;
            }

            // 3. JWT token validation
            JWTValidationResult tokenValidation = validateJWTToken(accessor);
            if (!tokenValidation.isValid()) {
                log.warn("WebSocket connection blocked - invalid JWT: {}", tokenValidation.getErrorMessage());
                return null;
            }

            // 4. User connection limit check
            if (!checkUserConnectionLimit(tokenValidation.getUserId())) {
                log.warn("WebSocket connection blocked - user connection limit exceeded: {}", 
                        tokenValidation.getUserId());
                return null;
            }

            // 5. Device fingerprinting (if enabled)
            DeviceFingerprintResult deviceCheck = null;
            if (enableDeviceFingerprinting && metadata.getDeviceFingerprint() != null) {
                deviceCheck = validateDeviceFingerprint(metadata.getDeviceFingerprint(), 
                        tokenValidation.getUserId());
                if (!deviceCheck.isAllowed()) {
                    log.warn("WebSocket connection blocked - device fingerprint check failed: {}", 
                            deviceCheck.getReason());
                    return null;
                }
            }

            // 6. Geolocation validation (if enabled)
            GeolocationValidationResult geoCheck = null;
            if (enableGeolocationCheck) {
                geoCheck = validateGeolocation(metadata.getIpAddress(), 
                        tokenValidation.getUserId());
                if (!geoCheck.isAllowed()) {
                    log.warn("WebSocket connection blocked - geolocation check failed: {}", 
                            geoCheck.getReason());
                    return null;
                }
            }

            // 7. Fraud detection assessment
            FraudAssessmentResult fraudAssessment = performFraudAssessment(metadata, tokenValidation);
            if (fraudAssessment.getRiskLevel() == FraudRiskLevel.CRITICAL) {
                log.warn("WebSocket connection blocked - fraud detection: userId={}, riskLevel={}", 
                        tokenValidation.getUserId(), fraudAssessment.getRiskLevel());
                return null;
            }

            // 8. Create secure session
            WebSocketSession session = createSecureSession(accessor.getSessionId(), 
                    tokenValidation, metadata, fraudAssessment);
            
            // 9. Set authentication context
            setAuthenticationContext(accessor, tokenValidation, session);
            
            // 10. Store session and update connection tracking
            storeSession(session);
            updateConnectionTracking(tokenValidation.getUserId(), accessor.getSessionId(), 
                    metadata.getIpAddress());

            // 11. Log successful connection
            logSecurityEvent("WEBSOCKET_CONNECTION_SUCCESS", session, metadata, fraudAssessment);

            log.info("WebSocket connection authenticated successfully: userId={}, sessionId={}, " +
                    "fraudRisk={}, geoLocation={}", 
                    tokenValidation.getUserId(), accessor.getSessionId(), 
                    fraudAssessment.getRiskLevel(), 
                    geoCheck != null ? geoCheck.getLocation() : "unknown");

            return message;

        } catch (Exception e) {
            log.error("WebSocket connection authentication failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private Message<?> handleDisconnect(Message<?> message, StompHeaderAccessor accessor) {
        try {
            String sessionId = accessor.getSessionId();
            if (sessionId == null) {
                return message;
            }

            // Retrieve and cleanup session
            WebSocketSession session = getStoredSession(sessionId);
            if (session != null) {
                cleanupSession(session);
                cleanupConnectionTracking(session.getUserId(), sessionId, session.getIpAddress());
                
                log.info("WebSocket session cleaned up: userId={}, sessionId={}, " +
                        "connectionDuration={}ms", 
                        session.getUserId(), sessionId, 
                        System.currentTimeMillis() - session.getConnectedAt().getTime());
            }

            return message;
            
        } catch (Exception e) {
            log.error("WebSocket disconnect cleanup failed: {}", e.getMessage(), e);
            return message; // Allow disconnect even on cleanup failure
        }
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        try {
            // Validate active session
            WebSocketSession session = validateActiveSession(accessor);
            if (session == null) {
                log.warn("WebSocket subscription rejected - invalid session: {}", accessor.getSessionId());
                return null;
            }

            // Check subscription permissions
            String destination = accessor.getDestination();
            if (!hasSubscriptionPermission(session, destination)) {
                log.warn("WebSocket subscription rejected - insufficient permissions: userId={}, " +
                        "destination={}", session.getUserId(), destination);
                return null;
            }

            // Update session activity
            updateSessionActivity(session);
            
            log.debug("WebSocket subscription allowed: userId={}, destination={}", 
                    session.getUserId(), destination);

            return message;
            
        } catch (Exception e) {
            log.error("WebSocket subscription validation failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor) {
        try {
            // Validate active session
            WebSocketSession session = validateActiveSession(accessor);
            if (session == null) {
                log.warn("WebSocket send rejected - invalid session: {}", accessor.getSessionId());
                return null;
            }

            // Check send permissions and rate limits
            String destination = accessor.getDestination();
            if (!hasSendPermission(session, destination)) {
                log.warn("WebSocket send rejected - insufficient permissions: userId={}, " +
                        "destination={}", session.getUserId(), destination);
                return null;
            }

            // Check message rate limits
            if (!checkMessageRateLimit(session)) {
                log.warn("WebSocket send rejected - message rate limit exceeded: userId={}", 
                        session.getUserId());
                return null;
            }

            // Update session activity and message count
            updateSessionActivity(session);
            incrementMessageCount(session);
            
            return message;
            
        } catch (Exception e) {
            log.error("WebSocket send validation failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private Message<?> validateActiveSession(Message<?> message, StompHeaderAccessor accessor) {
        try {
            WebSocketSession session = validateActiveSession(accessor);
            if (session == null) {
                return null;
            }

            // Update session activity
            updateSessionActivity(session);
            
            return message;
            
        } catch (Exception e) {
            log.error("WebSocket session validation failed: {}", e.getMessage(), e);
            return null;
        }
    }

    // Security validation methods

    private SecurityCheckResult performIpSecurityCheck(String ipAddress) {
        try {
            // Check if IP is blocked
            if (blockedIps.contains(ipAddress) || isIpBlocked(ipAddress)) {
                return SecurityCheckResult.blocked("IP address is blocked");
            }

            // Check IP reputation using fraud service
            BlacklistCheckRequest checkRequest = BlacklistCheckRequest.builder()
                    .checkType(BlacklistCheckType.IP_ADDRESS)
                    .identifier(ipAddress)
                    .build();
            
            BlacklistCheckResult blacklistResult = fraudService.performBlacklistCheck(checkRequest);
            if (blacklistResult.isBlacklisted()) {
                blockIpAddress(ipAddress, "Blacklisted IP", Duration.ofHours(24));
                return SecurityCheckResult.blocked("IP address is blacklisted");
            }

            return SecurityCheckResult.allowed();
            
        } catch (Exception e) {
            log.error("IP security check failed for: {}", ipAddress, e);
            // Allow on error but log it
            return SecurityCheckResult.allowed();
        }
    }

    private JWTValidationResult validateJWTToken(StompHeaderAccessor accessor) {
        try {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return JWTValidationResult.invalid("Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            
            // Validate JWT token
            JWTValidationResult result = jwtService.validateToken(token);
            if (!result.isValid()) {
                return result;
            }

            // Check if token is blacklisted
            if (isTokenBlacklisted(token)) {
                return JWTValidationResult.invalid("Token is blacklisted");
            }

            // Check token freshness and auto-refresh if needed
            if (jwtService.shouldRefreshToken(token)) {
                String refreshedToken = jwtService.refreshToken(token);
                if (refreshedToken != null) {
                    result.setRefreshedToken(refreshedToken);
                }
            }

            return result;
            
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage(), e);
            return JWTValidationResult.invalid("JWT validation error");
        }
    }

    private DeviceFingerprintResult validateDeviceFingerprint(String deviceFingerprint, String userId) {
        try {
            // Check device reputation
            BlacklistCheckRequest checkRequest = BlacklistCheckRequest.builder()
                    .checkType(BlacklistCheckType.DEVICE_ID)
                    .identifier(deviceFingerprint)
                    .build();
            
            BlacklistCheckResult blacklistResult = fraudService.performBlacklistCheck(checkRequest);
            if (blacklistResult.isBlacklisted()) {
                return DeviceFingerprintResult.blocked("Device is blacklisted");
            }

            // Check for device anomalies
            DeviceAnomalyResult anomaly = checkDeviceAnomaly(deviceFingerprint, userId);
            if (anomaly.isAnomalous() && anomaly.getAnomalyScore() > 0.8) {
                return DeviceFingerprintResult.blocked("Suspicious device characteristics detected");
            }

            // Store device association
            storeDeviceAssociation(userId, deviceFingerprint);

            return DeviceFingerprintResult.allowed();
            
        } catch (Exception e) {
            log.error("Device fingerprint validation failed: {}", e.getMessage(), e);
            return DeviceFingerprintResult.blocked("Device validation error");
        }
    }

    private GeolocationValidationResult validateGeolocation(String ipAddress, String userId) {
        try {
            // Get IP geolocation
            GeolocationData currentLocation = getIpGeolocation(ipAddress);
            if (currentLocation == null) {
                return GeolocationValidationResult.allowed("Geolocation unavailable");
            }

            // Get user's typical locations
            List<GeolocationData> userLocations = getUserTypicalLocations(userId);
            
            // Check for suspicious location changes
            if (!userLocations.isEmpty()) {
                double minDistance = userLocations.stream()
                        .mapToDouble(loc -> calculateDistance(currentLocation, loc))
                        .min()
                        .orElse(Double.MAX_VALUE);
                
                // If user is connecting from a location more than 1000 km from any known location
                if (minDistance > 1000) {
                    // Check if this is a high-risk country
                    if (isHighRiskCountry(currentLocation.getCountryCode())) {
                        return GeolocationValidationResult.blocked(
                                "Connection from high-risk location: " + currentLocation.getCountry());
                    }
                    
                    // Log suspicious location but allow (could require additional verification)
                    log.warn("WebSocket connection from unusual location: userId={}, location={}, " +
                            "distance={}km", userId, currentLocation.getCountry(), minDistance);
                }
            }

            // Store location for future reference
            storeUserLocation(userId, currentLocation);

            return GeolocationValidationResult.allowed(currentLocation);
            
        } catch (Exception e) {
            log.error("Geolocation validation failed: {}", e.getMessage(), e);
            return GeolocationValidationResult.allowed("Geolocation check error");
        }
    }

    private FraudAssessmentResult performFraudAssessment(WebSocketConnectionMetadata metadata,
                                                         JWTValidationResult tokenValidation) {
        try {
            // Create fraud assessment request
            FraudAssessmentRequest request = FraudAssessmentRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(UUID.fromString(tokenValidation.getUserId()))
                    .ipAddress(metadata.getIpAddress())
                    .deviceFingerprint(metadata.getDeviceFingerprint())
                    .userAgent(metadata.getUserAgent())
                    .timestamp(LocalDateTime.now())
                    .transactionType("WEBSOCKET_CONNECTION")
                    .build();

            return fraudService.assessFraudRisk(request);
            
        } catch (Exception e) {
            log.error("Fraud assessment failed: {}", e.getMessage(), e);
            // Return low risk on error to avoid blocking legitimate connections
            return FraudAssessmentResult.builder()
                    .riskLevel(FraudRiskLevel.LOW)
                    .errorMessage("Fraud assessment error")
                    .build();
        }
    }

    // Helper methods

    private WebSocketConnectionMetadata extractConnectionMetadata(StompHeaderAccessor accessor) {
        Map<String, List<String>> nativeHeaders = accessor.toNativeHeaderMap();
        
        return WebSocketConnectionMetadata.builder()
                .sessionId(accessor.getSessionId())
                .ipAddress(extractIpAddress(nativeHeaders))
                .userAgent(extractUserAgent(nativeHeaders))
                .deviceFingerprint(extractDeviceFingerprint(nativeHeaders))
                .origin(extractOrigin(nativeHeaders))
                .referer(extractReferer(nativeHeaders))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private boolean checkRateLimit(String ipAddress) {
        String rateKey = WS_IP_RATE_PREFIX + ipAddress;
        String countStr = (String) redisTemplate.opsForValue().get(rateKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        
        if (count >= rateLimitPerIp) {
            return false;
        }
        
        redisTemplate.opsForValue().set(rateKey, String.valueOf(count + 1), Duration.ofMinutes(1));
        return true;
    }

    private boolean checkUserConnectionLimit(String userId) {
        List<String> connections = userConnections.get(userId);
        return connections == null || connections.size() < maxConnectionsPerUser;
    }

    private WebSocketSession createSecureSession(String sessionId, JWTValidationResult tokenValidation,
                                                WebSocketConnectionMetadata metadata,
                                                FraudAssessmentResult fraudAssessment) {
        return WebSocketSession.builder()
                .sessionId(sessionId)
                .userId(tokenValidation.getUserId())
                .ipAddress(metadata.getIpAddress())
                .userAgent(metadata.getUserAgent())
                .deviceFingerprint(metadata.getDeviceFingerprint())
                .connectedAt(new Date())
                .lastActivity(new Date())
                .authorities(tokenValidation.getAuthorities())
                .fraudRiskLevel(fraudAssessment.getRiskLevel())
                .fraudScore(fraudAssessment.getOverallFraudScore())
                .messageCount(0)
                .authenticated(true)
                .build();
    }

    private void setAuthenticationContext(StompHeaderAccessor accessor, JWTValidationResult tokenValidation,
                                         WebSocketSession session) {
        List<SimpleGrantedAuthority> authorities = tokenValidation.getAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(tokenValidation.getUserId(), null, authorities);
        
        accessor.setUser(authentication);
        
        // Store session reference in accessor for later use
        accessor.getSessionAttributes().put("wsSession", session);
    }

    private void storeSession(WebSocketSession session) {
        String sessionKey = WS_SESSION_PREFIX + session.getSessionId();
        redisTemplate.opsForValue().set(sessionKey, session, Duration.ofMinutes(connectionTimeoutMinutes));
    }

    private WebSocketSession getStoredSession(String sessionId) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;
        return (WebSocketSession) redisTemplate.opsForValue().get(sessionKey);
    }

    private WebSocketSession validateActiveSession(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return null;
        }

        WebSocketSession session = getStoredSession(sessionId);
        if (session == null || !session.isAuthenticated()) {
            return null;
        }

        // Check session timeout
        Date now = new Date();
        long inactiveTime = now.getTime() - session.getLastActivity().getTime();
        if (inactiveTime > Duration.ofMinutes(connectionTimeoutMinutes).toMillis()) {
            cleanupSession(session);
            return null;
        }

        return session;
    }

    private void updateSessionActivity(WebSocketSession session) {
        session.setLastActivity(new Date());
        storeSession(session); // Update in Redis
    }

    private void updateConnectionTracking(String userId, String sessionId, String ipAddress) {
        // Track user connections
        userConnections.computeIfAbsent(userId, k -> new ArrayList<>()).add(sessionId);
        
        // Track IP connections
        ipConnectionCounts.computeIfAbsent(ipAddress, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void cleanupConnectionTracking(String userId, String sessionId, String ipAddress) {
        // Remove from user connections
        List<String> connections = userConnections.get(userId);
        if (connections != null) {
            connections.remove(sessionId);
            if (connections.isEmpty()) {
                userConnections.remove(userId);
            }
        }
        
        // Decrement IP connections
        AtomicInteger ipCount = ipConnectionCounts.get(ipAddress);
        if (ipCount != null) {
            int newCount = ipCount.decrementAndGet();
            if (newCount <= 0) {
                ipConnectionCounts.remove(ipAddress);
            }
        }
    }

    private void cleanupSession(WebSocketSession session) {
        String sessionKey = WS_SESSION_PREFIX + session.getSessionId();
        redisTemplate.delete(sessionKey);
    }

    private void logSecurityEvent(String eventType, WebSocketSession session, 
                                 WebSocketConnectionMetadata metadata, 
                                 FraudAssessmentResult fraudAssessment) {
        // Log security events for monitoring and analysis
        log.info("WebSocket Security Event: type={}, userId={}, sessionId={}, ip={}, " +
                "fraudRisk={}, fraudScore={}", 
                eventType, session.getUserId(), session.getSessionId(), 
                session.getIpAddress(), fraudAssessment.getRiskLevel(),
                fraudAssessment.getOverallFraudScore() != null ? 
                        fraudAssessment.getOverallFraudScore().getOverallScore() : null);
    }

    // Placeholder methods for complex operations that would be implemented based on specific requirements
    
    private String extractIpAddress(Map<String, List<String>> headers) {
        // Extract real IP considering proxies
        List<String> xForwardedFor = headers.get("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.get(0).split(",")[0].trim();
        }
        
        List<String> xRealIp = headers.get("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.get(0);
        }
        
        return "unknown";
    }

    private String extractUserAgent(Map<String, List<String>> headers) {
        List<String> userAgent = headers.get("User-Agent");
        return userAgent != null && !userAgent.isEmpty() ? userAgent.get(0) : "unknown";
    }

    private String extractDeviceFingerprint(Map<String, List<String>> headers) {
        List<String> fingerprint = headers.get("X-Device-Fingerprint");
        return fingerprint != null && !fingerprint.isEmpty() ? fingerprint.get(0) : null;
    }

    private String extractOrigin(Map<String, List<String>> headers) {
        List<String> origin = headers.get("Origin");
        return origin != null && !origin.isEmpty() ? origin.get(0) : "unknown";
    }

    private String extractReferer(Map<String, List<String>> headers) {
        List<String> referer = headers.get("Referer");
        return referer != null && !referer.isEmpty() ? referer.get(0) : "unknown";
    }

    // Additional helper method implementations would continue here...
    
    // Data classes for security results
    
    public static class SecurityCheckResult {
        private boolean allowed;
        private String reason;
        
        public static SecurityCheckResult allowed() {
            SecurityCheckResult result = new SecurityCheckResult();
            result.allowed = true;
            return result;
        }
        
        public static SecurityCheckResult blocked(String reason) {
            SecurityCheckResult result = new SecurityCheckResult();
            result.allowed = false;
            result.reason = reason;
            return result;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
    }
    
    // Additional data classes would be defined here...
}