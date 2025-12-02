/**
 * Session Timeout Manager
 * Implements comprehensive session management with sophisticated timeout policies
 * Provides security through intelligent session lifecycle management
 */
package com.waqiti.common.security.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.JwtTokenProvider;
import com.waqiti.common.security.SecureJwtConfigurationService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enterprise-grade session timeout management
 * Implements adaptive timeout policies and security controls
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.session.timeout.enabled", havingValue = "true", matchIfMissing = true)
public class SessionTimeoutManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionRegistry sessionRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private JwtTokenProvider jwtTokenProvider;
    
    @Autowired(required = false)
    private SecureJwtConfigurationService secureJwtConfigurationService;
    
    @Autowired(required = false)
    private CsrfTokenRepository csrfTokenRepository;
    
    // In-memory session tracking for performance
    private final ConcurrentHashMap<String, SessionMetadata> activeSessions = new ConcurrentHashMap<>();
    
    // Configuration properties
    @Value("${security.session.timeout.default-minutes:30}")
    private int defaultTimeoutMinutes;
    
    @Value("${security.session.timeout.idle-minutes:15}")
    private int idleTimeoutMinutes;
    
    @Value("${security.session.timeout.absolute-minutes:480}") // 8 hours
    private int absoluteTimeoutMinutes;
    
    @Value("${security.session.timeout.warning-minutes:5}")
    private int warningTimeoutMinutes;
    
    @Value("${security.session.timeout.extension-minutes:10}")
    private int extensionMinutes;
    
    @Value("${security.session.concurrent-sessions.max:3}")
    private int maxConcurrentSessions;
    
    @Value("${security.session.concurrent-sessions.prevent-login:false}")
    private boolean preventConcurrentLogin;
    
    @Value("${security.session.tracking.user-activity:true}")
    private boolean trackUserActivity;
    
    @Value("${security.session.security.ip-binding:true}")
    private boolean enableIpBinding;
    
    @Value("${security.session.security.user-agent-binding:true}")
    private boolean enableUserAgentBinding;
    
    @Value("${security.session.adaptive.enabled:true}")
    private boolean enableAdaptiveTimeout;
    
    @Value("${security.session.cleanup.interval-minutes:5}")
    private int cleanupIntervalMinutes;
    
    @Value("${security.session.csrf.enabled:true}")
    private boolean csrfEnabled;
    
    @Value("${security.session.jwt-integration.enabled:true}")
    private boolean jwtIntegrationEnabled;
    
    @Value("${security.session.device-binding.enabled:true}")
    private boolean deviceBindingEnabled;
    
    @Value("${security.session.anomaly-detection.enabled:true}")
    private boolean anomalyDetectionEnabled;

    /**
     * Initialize session with security controls
     */
    public SessionInfo initializeSession(String sessionId, Authentication authentication, 
                                       HttpServletRequest request) {
        log.debug("Initializing session: {} for user: {}", sessionId, authentication.getName());
        
        // Check concurrent session limits
        enforceSessionLimits(authentication.getName());
        
        // Create session metadata with enhanced security
        SessionMetadata metadata = SessionMetadata.builder()
            .sessionId(sessionId)
            .username(authentication.getName())
            .creationTime(Instant.now())
            .lastActivity(Instant.now())
            .ipAddress(extractClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .roles(authentication.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet()))
            .timeoutPolicy(determineTimeoutPolicy(authentication))
            .securityLevel(determineSecurityLevel(authentication))
            .deviceFingerprint(generateDeviceFingerprint(request))
            .jwtTokenId(generateSessionJwtToken(authentication, sessionId))
            .csrfToken(generateSessionCsrfToken(request))
            .suspiciousActivityScore(0.0)
            .build();
        
        // Store session data
        storeSession(metadata);
        
        // Track in local cache for performance
        activeSessions.put(sessionId, metadata);
        
        // Publish session created event
        eventPublisher.publishEvent(new SessionCreatedEvent(metadata));
        
        return SessionInfo.builder()
            .sessionId(sessionId)
            .username(authentication.getName())
            .timeoutMinutes(metadata.getTimeoutPolicy().getIdleTimeoutMinutes())
            .warningMinutes(warningTimeoutMinutes)
            .securityLevel(metadata.getSecurityLevel())
            .build();
    }

    /**
     * Update session activity
     */
    public void updateSessionActivity(String sessionId) {
        SessionMetadata metadata = getSessionMetadata(sessionId);
        if (metadata == null) {
            log.warn("Session not found for activity update: {}", sessionId);
            return;
        }
        
        Instant now = Instant.now();
        
        // Update last activity
        metadata.setLastActivity(now);
        metadata.setActivityCount(metadata.getActivityCount() + 1);
        
        // Track user activity if enabled
        if (trackUserActivity) {
            recordUserActivity(metadata, now);
        }
        
        // Update timeout policy if adaptive timeout is enabled
        if (enableAdaptiveTimeout) {
            updateAdaptiveTimeout(metadata);
        }
        
        // Store updated metadata
        storeSession(metadata);
        activeSessions.put(sessionId, metadata);
        
        log.debug("Session activity updated: {}", sessionId);
    }

    /**
     * Check session timeout status
     */
    public SessionTimeoutStatus checkSessionTimeout(String sessionId) {
        SessionMetadata metadata = getSessionMetadata(sessionId);
        if (metadata == null) {
            return SessionTimeoutStatus.builder()
                .sessionId(sessionId)
                .status(TimeoutStatusType.EXPIRED)
                .message("Session not found")
                .build();
        }
        
        Instant now = Instant.now();
        TimeoutPolicy policy = metadata.getTimeoutPolicy();
        
        // Check absolute timeout
        long absoluteMinutes = ChronoUnit.MINUTES.between(metadata.getCreationTime(), now);
        if (absoluteMinutes >= policy.getAbsoluteTimeoutMinutes()) {
            expireSession(sessionId, "Absolute timeout exceeded");
            return SessionTimeoutStatus.builder()
                .sessionId(sessionId)
                .status(TimeoutStatusType.EXPIRED)
                .message("Session exceeded maximum duration")
                .build();
        }
        
        // Check idle timeout
        long idleMinutes = ChronoUnit.MINUTES.between(metadata.getLastActivity(), now);
        if (idleMinutes >= policy.getIdleTimeoutMinutes()) {
            expireSession(sessionId, "Idle timeout exceeded");
            return SessionTimeoutStatus.builder()
                .sessionId(sessionId)
                .status(TimeoutStatusType.EXPIRED)
                .message("Session idle timeout exceeded")
                .build();
        }
        
        // Check if warning should be issued
        if (idleMinutes >= (policy.getIdleTimeoutMinutes() - warningTimeoutMinutes)) {
            return SessionTimeoutStatus.builder()
                .sessionId(sessionId)
                .status(TimeoutStatusType.WARNING)
                .minutesRemaining((int) (policy.getIdleTimeoutMinutes() - idleMinutes))
                .message("Session will expire soon")
                .canExtend(metadata.getExtensionsUsed() < 3) // Limit extensions
                .build();
        }
        
        // Session is active
        return SessionTimeoutStatus.builder()
            .sessionId(sessionId)
            .status(TimeoutStatusType.ACTIVE)
            .minutesRemaining((int) (policy.getIdleTimeoutMinutes() - idleMinutes))
            .message("Session is active")
            .build();
    }

    /**
     * Extend session timeout
     */
    public boolean extendSession(String sessionId) {
        SessionMetadata metadata = getSessionMetadata(sessionId);
        if (metadata == null) {
            log.warn("Cannot extend non-existent session: {}", sessionId);
            return false;
        }
        
        // Check extension limits
        if (metadata.getExtensionsUsed() >= 3) {
            log.warn("Session extension limit exceeded: {}", sessionId);
            return false;
        }
        
        // Extend the session
        Instant now = Instant.now();
        metadata.setLastActivity(now);
        metadata.setExtensionsUsed(metadata.getExtensionsUsed() + 1);
        
        // Update timeout policy for extension
        TimeoutPolicy extendedPolicy = metadata.getTimeoutPolicy().toBuilder()
            .idleTimeoutMinutes(metadata.getTimeoutPolicy().getIdleTimeoutMinutes() + extensionMinutes)
            .build();
        metadata.setTimeoutPolicy(extendedPolicy);
        
        // Store updated metadata
        storeSession(metadata);
        activeSessions.put(sessionId, metadata);
        
        // Publish extension event
        eventPublisher.publishEvent(new SessionExtendedEvent(sessionId, metadata.getUsername()));
        
        log.info("Session extended: {} for user: {}", sessionId, metadata.getUsername());
        return true;
    }

    /**
     * Validate session security
     */
    public SessionValidationResult validateSessionSecurity(String sessionId, HttpServletRequest request) {
        SessionMetadata metadata = getSessionMetadata(sessionId);
        if (metadata == null) {
            return SessionValidationResult.invalid("Session not found");
        }
        
        List<String> violations = new ArrayList<>();
        
        // IP address binding validation
        if (enableIpBinding) {
            String currentIp = extractClientIp(request);
            if (!Objects.equals(metadata.getIpAddress(), currentIp)) {
                violations.add("IP address mismatch");
                log.warn("IP address mismatch for session {}: expected {}, got {}", 
                    sessionId, metadata.getIpAddress(), currentIp);
            }
        }
        
        // User agent binding validation
        if (enableUserAgentBinding) {
            String currentUserAgent = request.getHeader("User-Agent");
            if (!Objects.equals(metadata.getUserAgent(), currentUserAgent)) {
                violations.add("User agent mismatch");
                log.warn("User agent mismatch for session {}", sessionId);
            }
        }
        
        // Security level validation
        if (metadata.getSecurityLevel() == SecurityLevel.HIGH) {
            // Additional validations for high-security sessions
            if (ChronoUnit.HOURS.between(metadata.getCreationTime(), Instant.now()) > 2) {
                violations.add("High-security session exceeded time limit");
            }
        }
        
        if (!violations.isEmpty()) {
            // Log security violation
            eventPublisher.publishEvent(new SessionSecurityViolationEvent(sessionId, 
                metadata.getUsername(), violations));
            
            return SessionValidationResult.invalid("Security validation failed: " + 
                String.join(", ", violations));
        }
        
        return SessionValidationResult.valid();
    }

    /**
     * Expire session
     */
    public void expireSession(String sessionId, String reason) {
        SessionMetadata metadata = getSessionMetadata(sessionId);
        if (metadata == null) {
            return;
        }
        
        // Remove from stores
        removeSession(sessionId);
        activeSessions.remove(sessionId);
        
        // Invalidate in session registry
        sessionRegistry.getAllSessions(metadata.getUsername(), false)
            .stream()
            .filter(si -> sessionId.equals(si.getSessionId()))
            .forEach(SessionInformation::expireNow);
        
        // Publish expiration event
        eventPublisher.publishEvent(new SessionExpiredEvent(sessionId, metadata.getUsername(), reason));
        
        log.info("Session expired: {} for user: {} - Reason: {}", 
            sessionId, metadata.getUsername(), reason);
    }

    /**
     * Get session statistics
     */
    public SessionStatistics getSessionStatistics() {
        long totalSessions = activeSessions.size();
        
        Map<SecurityLevel, Long> sessionsByLevel = activeSessions.values().stream()
            .collect(Collectors.groupingBy(
                SessionMetadata::getSecurityLevel,
                Collectors.counting()
            ));
        
        Map<String, Long> sessionsByUser = activeSessions.values().stream()
            .collect(Collectors.groupingBy(
                SessionMetadata::getUsername,
                Collectors.counting()
            ));
        
        return SessionStatistics.builder()
            .totalActiveSessions(totalSessions)
            .sessionsBySecurityLevel(sessionsByLevel)
            .averageSessionDuration(calculateAverageSessionDuration())
            .concurrentSessionUsers(sessionsByUser.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .count())
            .build();
    }

    /**
     * Cleanup expired sessions
     */
    @Scheduled(fixedRateString = "${security.session.cleanup.interval-minutes:5}", timeUnit = TimeUnit.MINUTES)
    public void cleanupExpiredSessions() {
        log.debug("Starting session cleanup task");
        
        Instant now = Instant.now();
        List<String> expiredSessions = new ArrayList<>();
        
        for (SessionMetadata metadata : activeSessions.values()) {
            TimeoutPolicy policy = metadata.getTimeoutPolicy();
            
            // Check absolute timeout
            if (ChronoUnit.MINUTES.between(metadata.getCreationTime(), now) >= policy.getAbsoluteTimeoutMinutes()) {
                expiredSessions.add(metadata.getSessionId());
                continue;
            }
            
            // Check idle timeout
            if (ChronoUnit.MINUTES.between(metadata.getLastActivity(), now) >= policy.getIdleTimeoutMinutes()) {
                expiredSessions.add(metadata.getSessionId());
            }
        }
        
        // Expire identified sessions
        for (String sessionId : expiredSessions) {
            expireSession(sessionId, "Cleanup task - timeout exceeded");
        }
        
        if (!expiredSessions.isEmpty()) {
            log.info("Cleaned up {} expired sessions", expiredSessions.size());
        }
    }

    /**
     * Validate session integrity with enhanced security checks
     */
    public SessionSecurityValidationResult validateSessionIntegrity(String sessionId, HttpServletRequest request) {
        SessionMetadata metadata = getSessionMetadata(sessionId);
        if (metadata == null) {
            return SessionSecurityValidationResult.invalid("Session not found");
        }

        List<String> violations = new ArrayList<>();
        double riskScore = 0.0;

        // Basic security validation
        SessionValidationResult basicValidation = validateSessionSecurity(sessionId, request);
        if (!basicValidation.isValid()) {
            violations.add(basicValidation.getMessage());
            riskScore += 30.0;
        }

        // Device fingerprint validation
        if (deviceBindingEnabled) {
            String currentFingerprint = generateDeviceFingerprint(request);
            if (!Objects.equals(metadata.getDeviceFingerprint(), currentFingerprint)) {
                violations.add("Device fingerprint mismatch");
                riskScore += 25.0;
                log.warn("Device fingerprint mismatch for session {}", sessionId);
            }
        }

        // JWT token validation
        if (jwtIntegrationEnabled && metadata.getJwtTokenId() != null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                violations.add("Missing or invalid JWT token");
                riskScore += 20.0;
            } else {
                String token = authHeader.substring(7);
                if (jwtTokenProvider != null && !jwtTokenProvider.validateToken(token)) {
                    violations.add("Invalid JWT token");
                    riskScore += 40.0;
                }
            }
        }

        // CSRF token validation
        if (csrfEnabled && csrfTokenRepository != null) {
            CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
            if (csrfToken == null || !Objects.equals(metadata.getCsrfToken(), csrfToken.getToken())) {
                violations.add("CSRF token validation failed");
                riskScore += 15.0;
            }
        }

        // Anomaly detection
        if (anomalyDetectionEnabled) {
            double anomalyScore = detectSessionAnomalies(metadata, request);
            if (anomalyScore > 50.0) {
                violations.add("Suspicious session behavior detected");
                riskScore += anomalyScore;
            }
        }

        // Update session risk score
        metadata.setSuspiciousActivityScore(Math.max(metadata.getSuspiciousActivityScore(), riskScore));
        if (riskScore > 0) {
            storeSession(metadata);
        }

        return SessionSecurityValidationResult.builder()
            .sessionId(sessionId)
            .valid(violations.isEmpty())
            .violations(violations)
            .riskScore(riskScore)
            .requiresReauthentication(riskScore > 70.0)
            .build();
    }

    /**
     * Detect anomalies in session behavior
     */
    private double detectSessionAnomalies(SessionMetadata metadata, HttpServletRequest request) {
        double anomalyScore = 0.0;

        // Check for rapid activity patterns
        long timeSinceLastActivity = ChronoUnit.SECONDS.between(metadata.getLastActivity(), Instant.now());
        if (timeSinceLastActivity < 1 && metadata.getActivityCount() > 10) {
            anomalyScore += 20.0; // Possible automation/bot behavior
        }

        // Check for geographic anomalies (basic IP-based detection)
        String currentIp = extractClientIp(request);
        if (!Objects.equals(metadata.getIpAddress(), currentIp)) {
            // In a real implementation, this would use IP geolocation services
            anomalyScore += 15.0;
        }

        // Check for time-based anomalies
        Instant now = Instant.now();
        int currentHour = now.atZone(java.time.ZoneId.systemDefault()).getHour();
        if (currentHour < 6 || currentHour > 22) { // Outside normal business hours
            anomalyScore += 5.0;
        }

        // Check session duration anomalies
        long sessionDuration = ChronoUnit.HOURS.between(metadata.getCreationTime(), now);
        if (sessionDuration > 12) { // Very long session
            anomalyScore += 10.0;
        }

        return anomalyScore;
    }

    /**
     * Generate device fingerprint for enhanced security
     */
    private String generateDeviceFingerprint(HttpServletRequest request) {
        if (!deviceBindingEnabled) {
            return null;
        }

        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(request.getHeader("User-Agent")).append("|");
        fingerprint.append(request.getHeader("Accept-Language")).append("|");
        fingerprint.append(request.getHeader("Accept-Encoding")).append("|");
        fingerprint.append(request.getHeader("Accept")).append("|");
        
        // Add more device-specific headers if available
        String[] deviceHeaders = {
            "X-Requested-With", "DNT", "Connection", "Upgrade-Insecure-Requests",
            "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site"
        };
        
        for (String header : deviceHeaders) {
            String value = request.getHeader(header);
            fingerprint.append(value != null ? value : "").append("|");
        }

        // Generate hash of the fingerprint
        return Integer.toString(fingerprint.toString().hashCode());
    }

    /**
     * Generate session-specific JWT token
     */
    private String generateSessionJwtToken(Authentication authentication, String sessionId) {
        if (!jwtIntegrationEnabled || jwtTokenProvider == null) {
            return null;
        }

        try {
            // Create a special session token with session ID claim
            return jwtTokenProvider.generateToken(authentication);
        } catch (Exception e) {
            log.warn("Failed to generate session JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate session-specific CSRF token
     */
    private String generateSessionCsrfToken(HttpServletRequest request) {
        if (!csrfEnabled || csrfTokenRepository == null) {
            return null;
        }

        try {
            CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
            return csrfToken != null ? csrfToken.getToken() : null;
        } catch (Exception e) {
            log.warn("Failed to generate session CSRF token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Handle security incident
     */
    public void handleSecurityIncident(String sessionId, String incidentType, String details) {
        SessionMetadata metadata = getSessionMetadata(sessionId);
        if (metadata == null) {
            return;
        }

        // Record security incident
        SecurityIncident incident = SecurityIncident.builder()
            .sessionId(sessionId)
            .username(metadata.getUsername())
            .incidentType(incidentType)
            .details(details)
            .timestamp(Instant.now())
            .ipAddress(metadata.getIpAddress())
            .userAgent(metadata.getUserAgent())
            .riskScore(metadata.getSuspiciousActivityScore())
            .build();

        recordSecurityIncident(incident);

        // Take action based on incident severity
        if (metadata.getSuspiciousActivityScore() > 80.0) {
            expireSession(sessionId, "High-risk security incident: " + incidentType);
        } else if (metadata.getSuspiciousActivityScore() > 50.0) {
            // Force re-authentication
            metadata.setRequiresReauthentication(true);
            storeSession(metadata);
        }

        // Publish security event
        eventPublisher.publishEvent(new SessionSecurityIncidentEvent(incident));
    }

    /**
     * Record security incident in Redis
     */
    private void recordSecurityIncident(SecurityIncident incident) {
        String incidentKey = "security:incident:" + incident.getSessionId() + ":" + 
            incident.getTimestamp().toEpochMilli();
        
        try {
            String incidentData = objectMapper.writeValueAsString(incident);
            redisTemplate.opsForValue().set(incidentKey, incidentData, Duration.ofDays(30));
            
            // Also add to user's incident list for monitoring
            String userIncidentKey = "security:incidents:" + incident.getUsername();
            redisTemplate.opsForList().leftPush(userIncidentKey, incidentKey);
            redisTemplate.opsForList().trim(userIncidentKey, 0, 99); // Keep last 100
            redisTemplate.expire(userIncidentKey, Duration.ofDays(90));
            
        } catch (JsonProcessingException e) {
            log.error("Failed to record security incident", e);
        }
    }

    // Private helper methods
    
    private void enforceSessionLimits(String username) {
        List<SessionInformation> userSessions = sessionRegistry.getAllSessions(username, false);
        
        if (userSessions.size() >= maxConcurrentSessions) {
            if (preventConcurrentLogin) {
                throw new SessionAuthenticationException(
                    "Maximum concurrent sessions exceeded for user: " + username);
            } else {
                // Expire oldest session
                userSessions.stream()
                    .min(Comparator.comparing(SessionInformation::getLastRequest))
                    .ifPresent(SessionInformation::expireNow);
            }
        }
    }
    
    private TimeoutPolicy determineTimeoutPolicy(Authentication authentication) {
        SecurityLevel securityLevel = determineSecurityLevel(authentication);
        
        return switch (securityLevel) {
            case HIGH -> TimeoutPolicy.builder()
                .idleTimeoutMinutes(10) // Shorter timeout for high security
                .absoluteTimeoutMinutes(120) // 2 hours max
                .build();
            case MEDIUM -> TimeoutPolicy.builder()
                .idleTimeoutMinutes(idleTimeoutMinutes)
                .absoluteTimeoutMinutes(absoluteTimeoutMinutes)
                .build();
            case LOW -> TimeoutPolicy.builder()
                .idleTimeoutMinutes(idleTimeoutMinutes + 15) // Extended for low security
                .absoluteTimeoutMinutes(absoluteTimeoutMinutes + 240) // 12 hours max
                .build();
        };
    }
    
    private SecurityLevel determineSecurityLevel(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
        
        if (authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_SUPER_ADMIN")) {
            return SecurityLevel.HIGH;
        } else if (authorities.contains("ROLE_MANAGER") || authorities.contains("ROLE_OPERATOR")) {
            return SecurityLevel.MEDIUM;
        } else {
            return SecurityLevel.LOW;
        }
    }
    
    private void recordUserActivity(SessionMetadata metadata, Instant activityTime) {
        String activityKey = "user_activity:" + metadata.getUsername();
        redisTemplate.opsForList().leftPush(activityKey, activityTime.toString());
        redisTemplate.opsForList().trim(activityKey, 0, 99); // Keep last 100 activities
        redisTemplate.expire(activityKey, 24, TimeUnit.HOURS);
    }
    
    private void updateAdaptiveTimeout(SessionMetadata metadata) {
        // Analyze user activity patterns to adjust timeout
        int activityCount = metadata.getActivityCount();
        long sessionDuration = ChronoUnit.MINUTES.between(metadata.getCreationTime(), Instant.now());
        
        if (sessionDuration > 60 && activityCount > 10) {
            // User is very active, can extend timeout slightly
            TimeoutPolicy currentPolicy = metadata.getTimeoutPolicy();
            TimeoutPolicy adaptedPolicy = currentPolicy.toBuilder()
                .idleTimeoutMinutes(Math.min(currentPolicy.getIdleTimeoutMinutes() + 5, 45))
                .build();
            metadata.setTimeoutPolicy(adaptedPolicy);
        }
    }
    
    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "X-Originating-IP", "X-Cluster-Client-IP"};
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    private SessionMetadata getSessionMetadata(String sessionId) {
        // Try local cache first
        SessionMetadata cached = activeSessions.get(sessionId);
        if (cached != null) {
            return cached;
        }
        
        // Fall back to Redis
        String sessionKey = "session:" + sessionId;
        Object sessionData = redisTemplate.opsForValue().get(sessionKey);
        
        if (sessionData != null) {
            try {
                SessionMetadata metadata = objectMapper.readValue(sessionData.toString(), SessionMetadata.class);
                activeSessions.put(sessionId, metadata); // Cache locally
                return metadata;
            } catch (JsonProcessingException e) {
                log.error("Error deserializing session metadata", e);
            }
        }
        
        return null;
    }
    
    private void storeSession(SessionMetadata metadata) {
        String sessionKey = "session:" + metadata.getSessionId();
        try {
            String jsonData = objectMapper.writeValueAsString(metadata);
            redisTemplate.opsForValue().set(sessionKey, jsonData);
            redisTemplate.expire(sessionKey, metadata.getTimeoutPolicy().getAbsoluteTimeoutMinutes(), TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Error serializing session metadata", e);
        }
    }
    
    private void removeSession(String sessionId) {
        String sessionKey = "session:" + sessionId;
        redisTemplate.delete(sessionKey);
    }
    
    private double calculateAverageSessionDuration() {
        return activeSessions.values().stream()
            .mapToLong(metadata -> ChronoUnit.MINUTES.between(metadata.getCreationTime(), Instant.now()))
            .average()
            .orElse(0.0);
    }
    
    // Data classes and enums
    
    @Data
    @Builder(toBuilder = true)
    public static class SessionMetadata {
        private String sessionId;
        private String username;
        private Instant creationTime;
        private Instant lastActivity;
        private String ipAddress;
        private String userAgent;
        private Set<String> roles;
        private TimeoutPolicy timeoutPolicy;
        private SecurityLevel securityLevel;
        private int activityCount;
        private int extensionsUsed;
        private String deviceFingerprint;
        private String jwtTokenId;
        private String csrfToken;
        private double suspiciousActivityScore;
        private boolean requiresReauthentication;
    }
    
    @Data
    @Builder(toBuilder = true)
    public static class TimeoutPolicy {
        private int idleTimeoutMinutes;
        private int absoluteTimeoutMinutes;
    }
    
    @Data
    @Builder
    public static class SessionInfo {
        private String sessionId;
        private String username;
        private int timeoutMinutes;
        private int warningMinutes;
        private SecurityLevel securityLevel;
    }
    
    @Data
    @Builder
    public static class SessionTimeoutStatus {
        private String sessionId;
        private TimeoutStatusType status;
        private int minutesRemaining;
        private String message;
        private boolean canExtend;
    }
    
    @Data
    @Builder
    public static class SessionValidationResult {
        private boolean valid;
        private String message;
        
        public static SessionValidationResult valid() {
            return SessionValidationResult.builder().valid(true).build();
        }
        
        public static SessionValidationResult invalid(String message) {
            return SessionValidationResult.builder().valid(false).message(message).build();
        }
    }
    
    @Data
    @Builder
    public static class SessionStatistics {
        private long totalActiveSessions;
        private Map<SecurityLevel, Long> sessionsBySecurityLevel;
        private double averageSessionDuration;
        private long concurrentSessionUsers;
    }
    
    public enum TimeoutStatusType {
        ACTIVE, WARNING, EXPIRED
    }
    
    public enum SecurityLevel {
        LOW, MEDIUM, HIGH
    }
    
    // Events
    
    public static class SessionCreatedEvent {
        private final SessionMetadata metadata;
        public SessionCreatedEvent(SessionMetadata metadata) { this.metadata = metadata; }
        public SessionMetadata getMetadata() { return metadata; }
    }
    
    public static class SessionExtendedEvent {
        private final String sessionId;
        private final String username;
        public SessionExtendedEvent(String sessionId, String username) { 
            this.sessionId = sessionId; this.username = username; 
        }
        public String getSessionId() { return sessionId; }
        public String getUsername() { return username; }
    }
    
    public static class SessionExpiredEvent {
        private final String sessionId;
        private final String username;
        private final String reason;
        public SessionExpiredEvent(String sessionId, String username, String reason) { 
            this.sessionId = sessionId; this.username = username; this.reason = reason; 
        }
        public String getSessionId() { return sessionId; }
        public String getUsername() { return username; }
        public String getReason() { return reason; }
    }
    
    public static class SessionSecurityViolationEvent {
        private final String sessionId;
        private final String username;
        private final List<String> violations;
        public SessionSecurityViolationEvent(String sessionId, String username, List<String> violations) { 
            this.sessionId = sessionId; this.username = username; this.violations = violations; 
        }
        public String getSessionId() { return sessionId; }
        public String getUsername() { return username; }
        public List<String> getViolations() { return violations; }
    }
    
    // Enhanced security data classes
    
    @Data
    @Builder
    public static class SessionSecurityValidationResult {
        private String sessionId;
        private boolean valid;
        private List<String> violations;
        private double riskScore;
        private boolean requiresReauthentication;
        
        public static SessionSecurityValidationResult invalid(String message) {
            return SessionSecurityValidationResult.builder()
                .valid(false)
                .violations(List.of(message))
                .riskScore(100.0)
                .build();
        }
    }
    
    @Data
    @Builder
    public static class SecurityIncident {
        private String sessionId;
        private String username;
        private String incidentType;
        private String details;
        private Instant timestamp;
        private String ipAddress;
        private String userAgent;
        private double riskScore;
    }
    
    public static class SessionSecurityIncidentEvent {
        private final SecurityIncident incident;
        public SessionSecurityIncidentEvent(SecurityIncident incident) { 
            this.incident = incident; 
        }
        public SecurityIncident getIncident() { return incident; }
    }
}