package com.waqiti.common.security;

import com.waqiti.common.client.SecurityServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Continuous Risk Assessment Filter
 * Continuously evaluates risk based on user behavior, transaction patterns, and security events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContinuousRiskAssessmentFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecurityServiceClient securityServiceClient;
    
    @Value("${security.risk-assessment.enabled:true}")
    private boolean enabled;
    
    @Value("${security.risk-assessment.threshold:0.7}")
    private double riskThreshold;
    
    @Value("${security.risk-assessment.cache-duration:300}")
    private int cacheDurationSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            double riskScore = assessRisk(userId, request);
            
            // Add risk score to request context
            request.setAttribute("user.risk.score", riskScore);
            
            if (riskScore > riskThreshold) {
                handleHighRisk(userId, riskScore, request, response);
                return;
            }
            
            // Update user activity for continuous monitoring
            updateUserActivity(userId, request);
            
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Risk assessment failed for user {}, allowing request with monitoring", userId, e);
            request.setAttribute("risk.assessment.failed", true);
            filterChain.doFilter(request, response);
        }
    }

    private double assessRisk(String userId, HttpServletRequest request) {
        // Check cache first
        String cacheKey = "risk:score:" + userId;
        Double cachedScore = (Double) redisTemplate.opsForValue().get(cacheKey);
        if (cachedScore != null) {
            return cachedScore;
        }

        // Calculate risk factors
        double behaviorRisk = assessBehaviorRisk(userId, request);
        double locationRisk = assessLocationRisk(userId, request);
        double deviceRisk = assessDeviceRisk(userId, request);
        double transactionRisk = assessTransactionRisk(userId);
        double velocityRisk = assessVelocityRisk(userId);

        // Weighted risk calculation
        double totalRisk = (behaviorRisk * 0.25) + 
                          (locationRisk * 0.20) + 
                          (deviceRisk * 0.20) + 
                          (transactionRisk * 0.20) + 
                          (velocityRisk * 0.15);

        // Cache the risk score
        redisTemplate.opsForValue().set(cacheKey, totalRisk, cacheDurationSeconds, TimeUnit.SECONDS);
        
        log.debug("Risk assessment for user {}: behavior={}, location={}, device={}, transaction={}, velocity={}, total={}",
                userId, behaviorRisk, locationRisk, deviceRisk, transactionRisk, velocityRisk, totalRisk);
        
        return totalRisk;
    }

    private double assessBehaviorRisk(String userId, HttpServletRequest request) {
        String behaviorKey = "behavior:pattern:" + userId;
        
        // Check unusual access patterns
        String endpoint = request.getRequestURI();
        String timeKey = "access:time:" + userId + ":" + endpoint;
        
        Long lastAccess = (Long) redisTemplate.opsForValue().get(timeKey);
        if (lastAccess != null) {
            long timeDiff = Instant.now().toEpochMilli() - lastAccess;
            if (timeDiff < 1000) { // Less than 1 second between requests
                return 0.8; // High risk - too fast
            }
        }
        
        // Check unusual endpoint access
        String endpointPattern = "endpoint:pattern:" + userId;
        Boolean isUnusual = !redisTemplate.opsForSet().isMember(endpointPattern, endpoint);
        
        return isUnusual ? 0.3 : 0.1;
    }

    private double assessLocationRisk(String userId, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String locationKey = "location:history:" + userId;
        
        // Check if IP is in known locations
        Boolean isKnownLocation = redisTemplate.opsForSet().isMember(locationKey, clientIp);
        
        if (!isKnownLocation) {
            // Check for impossible travel
            String lastLocationKey = "last:location:" + userId;
            String lastLocation = (String) redisTemplate.opsForValue().get(lastLocationKey);
            
            if (lastLocation != null && !lastLocation.equals(clientIp)) {
                // In production, use GeoIP to calculate actual distance
                // For now, different IP = potential risk
                return 0.6;
            }
            
            return 0.4; // New location
        }
        
        return 0.1; // Known location
    }

    private double assessDeviceRisk(String userId, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String deviceKey = "device:fingerprint:" + userId;
        
        if (userAgent == null || userAgent.isEmpty()) {
            return 0.9; // No user agent is suspicious
        }
        
        // Simple device fingerprint based on user agent
        String deviceFingerprint = generateDeviceFingerprint(userAgent, request);
        Boolean isKnownDevice = redisTemplate.opsForSet().isMember(deviceKey, deviceFingerprint);
        
        return isKnownDevice ? 0.1 : 0.5;
    }

    private double assessTransactionRisk(String userId) {
        // Check recent transaction patterns
        String txKey = "transaction:risk:" + userId;
        Double txRisk = (Double) redisTemplate.opsForValue().get(txKey);
        
        return txRisk != null ? txRisk : 0.2;
    }

    private double assessVelocityRisk(String userId) {
        // Check request velocity
        String velocityKey = "velocity:" + userId;
        Long requestCount = redisTemplate.opsForValue().increment(velocityKey);
        
        if (requestCount == 1) {
            redisTemplate.expire(velocityKey, Duration.ofMinutes(1));
        }
        
        // Risk increases with velocity
        if (requestCount > 100) return 0.9;
        if (requestCount > 50) return 0.7;
        if (requestCount > 20) return 0.5;
        if (requestCount > 10) return 0.3;
        
        return 0.1;
    }

    private void handleHighRisk(String userId, double riskScore, 
                               HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("High risk detected for user {} with score {}", userId, riskScore);
        
        // Log security event
        logSecurityEvent(userId, riskScore, request);
        
        // Require additional authentication for high risk
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Additional authentication required\",\"riskScore\":" + riskScore + "}");
    }

    private void updateUserActivity(String userId, HttpServletRequest request) {
        try {
            // Update last access time
            String endpoint = request.getRequestURI();
            String timeKey = "access:time:" + userId + ":" + endpoint;
            redisTemplate.opsForValue().set(timeKey, Instant.now().toEpochMilli(), 1, TimeUnit.HOURS);
            
            // Update endpoint pattern
            String endpointPattern = "endpoint:pattern:" + userId;
            redisTemplate.opsForSet().add(endpointPattern, endpoint);
            redisTemplate.expire(endpointPattern, Duration.ofDays(30));
            
            // Update location
            String clientIp = getClientIp(request);
            String locationKey = "location:history:" + userId;
            redisTemplate.opsForSet().add(locationKey, clientIp);
            redisTemplate.expire(locationKey, Duration.ofDays(30));
            
            String lastLocationKey = "last:location:" + userId;
            redisTemplate.opsForValue().set(lastLocationKey, clientIp, 1, TimeUnit.HOURS);
            
            // Update device
            String deviceFingerprint = generateDeviceFingerprint(
                request.getHeader("User-Agent"), request);
            String deviceKey = "device:fingerprint:" + userId;
            redisTemplate.opsForSet().add(deviceKey, deviceFingerprint);
            redisTemplate.expire(deviceKey, Duration.ofDays(90));
            
        } catch (Exception e) {
            log.error("Failed to update user activity for {}", userId, e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private String generateDeviceFingerprint(String userAgent, HttpServletRequest request) {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(userAgent);
        
        // Add screen resolution if available
        String screenRes = request.getHeader("X-Screen-Resolution");
        if (screenRes != null) {
            fingerprint.append(":").append(screenRes);
        }
        
        // Add language
        String language = request.getHeader("Accept-Language");
        if (language != null) {
            fingerprint.append(":").append(language.split(",")[0]);
        }
        
        return String.valueOf(fingerprint.toString().hashCode());
    }

    private void logSecurityEvent(String userId, double riskScore, HttpServletRequest request) {
        try {
            // In production, send to security service
            log.warn("Security event: High risk user {} with score {} accessing {}", 
                    userId, riskScore, request.getRequestURI());
        } catch (Exception e) {
            log.error("Failed to log security event", e);
        }
    }
}