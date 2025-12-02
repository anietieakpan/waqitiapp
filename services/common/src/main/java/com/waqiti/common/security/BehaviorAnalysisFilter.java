package com.waqiti.common.security;

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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Behavior Analysis Filter
 * Analyzes user behavior patterns to detect anomalies and potential security threats
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BehaviorAnalysisFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${security.behavior-analysis.enabled:true}")
    private boolean enabled;
    
    @Value("${security.behavior-analysis.anomaly-threshold:0.7}")
    private double anomalyThreshold;
    
    @Value("${security.behavior-analysis.learning-period-days:30}")
    private int learningPeriodDays;

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
            BehaviorProfile profile = getBehaviorProfile(userId);
            BehaviorEvent currentEvent = createBehaviorEvent(request);
            
            double anomalyScore = calculateAnomalyScore(profile, currentEvent);
            
            // Add behavior analysis to request context
            request.setAttribute("behavior.anomaly.score", anomalyScore);
            request.setAttribute("behavior.profile.maturity", profile.getMaturityLevel());
            
            if (anomalyScore > anomalyThreshold) {
                handleAnomalouseBehavior(userId, currentEvent, anomalyScore, response);
                return;
            }
            
            // Update behavior profile
            updateBehaviorProfile(userId, profile, currentEvent);
            
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Behavior analysis failed for user {}", userId, e);
            request.setAttribute("behavior.analysis.failed", true);
            filterChain.doFilter(request, response);
        }
    }

    private BehaviorProfile getBehaviorProfile(String userId) {
        String profileKey = "behavior:profile:" + userId;
        BehaviorProfile profile = (BehaviorProfile) redisTemplate.opsForValue().get(profileKey);
        
        if (profile == null) {
            profile = new BehaviorProfile(userId);
        }
        
        return profile;
    }

    private BehaviorEvent createBehaviorEvent(HttpServletRequest request) {
        return BehaviorEvent.builder()
            .timestamp(Instant.now())
            .endpoint(request.getRequestURI())
            .method(request.getMethod())
            .userAgent(request.getHeader("User-Agent"))
            .ip(getClientIp(request))
            .sessionId(request.getRequestedSessionId())
            .referrer(request.getHeader("Referer"))
            .contentLength(request.getContentLength())
            .build();
    }

    private double calculateAnomalyScore(BehaviorProfile profile, BehaviorEvent event) {
        if (profile.getEventCount() < 10) {
            return 0.1; // Not enough data for accurate analysis
        }
        
        double timeAnomaly = calculateTimeAnomaly(profile, event);
        double endpointAnomaly = calculateEndpointAnomaly(profile, event);
        double velocityAnomaly = calculateVelocityAnomaly(profile, event);
        double sequenceAnomaly = calculateSequenceAnomaly(profile, event);
        double sessionAnomaly = calculateSessionAnomaly(profile, event);
        
        // Weighted average
        double totalAnomaly = (timeAnomaly * 0.20) +
                             (endpointAnomaly * 0.25) +
                             (velocityAnomaly * 0.20) +
                             (sequenceAnomaly * 0.20) +
                             (sessionAnomaly * 0.15);
        
        log.debug("Anomaly scores for user {}: time={}, endpoint={}, velocity={}, sequence={}, session={}, total={}",
                profile.getUserId(), timeAnomaly, endpointAnomaly, velocityAnomaly, 
                sequenceAnomaly, sessionAnomaly, totalAnomaly);
        
        return totalAnomaly;
    }

    private double calculateTimeAnomaly(BehaviorProfile profile, BehaviorEvent event) {
        LocalTime eventTime = LocalTime.ofInstant(event.getTimestamp(), ZoneId.systemDefault());
        int hour = eventTime.getHour();
        
        // Check if this hour is typical for the user
        Map<Integer, Integer> hourlyActivity = profile.getHourlyActivity();
        int totalActivity = hourlyActivity.values().stream().mapToInt(Integer::intValue).sum();
        
        if (totalActivity == 0) return 0;
        
        int hourActivity = hourlyActivity.getOrDefault(hour, 0);
        double hourProbability = (double) hourActivity / totalActivity;
        
        // Low probability = high anomaly
        if (hourProbability < 0.01) return 0.9;
        if (hourProbability < 0.05) return 0.7;
        if (hourProbability < 0.10) return 0.5;
        
        return 0.1;
    }

    private double calculateEndpointAnomaly(BehaviorProfile profile, BehaviorEvent event) {
        String endpoint = normalizeEndpoint(event.getEndpoint());
        Map<String, Integer> endpointFrequency = profile.getEndpointFrequency();
        
        if (!endpointFrequency.containsKey(endpoint)) {
            // New endpoint
            if (profile.getEventCount() > 100) {
                return 0.8; // Unusual for established user
            }
            return 0.4; // Normal for new user
        }
        
        // Check relative frequency
        int totalEndpointAccess = endpointFrequency.values().stream().mapToInt(Integer::intValue).sum();
        double endpointProbability = (double) endpointFrequency.get(endpoint) / totalEndpointAccess;
        
        if (endpointProbability < 0.001) return 0.7;
        if (endpointProbability < 0.01) return 0.5;
        
        return 0.1;
    }

    private double calculateVelocityAnomaly(BehaviorProfile profile, BehaviorEvent event) {
        // Check request velocity
        String velocityKey = "behavior:velocity:" + profile.getUserId();
        Long recentRequests = redisTemplate.opsForValue().increment(velocityKey);
        
        if (recentRequests == 1) {
            redisTemplate.expire(velocityKey, Duration.ofMinutes(5));
        }
        
        double avgRequestsPerMinute = profile.getAverageRequestsPerMinute();
        double currentRate = recentRequests / 5.0; // Requests per minute over 5 minutes
        
        if (avgRequestsPerMinute > 0) {
            double deviation = Math.abs(currentRate - avgRequestsPerMinute) / avgRequestsPerMinute;
            if (deviation > 10) return 0.9;
            if (deviation > 5) return 0.7;
            if (deviation > 2) return 0.5;
        }
        
        return 0.1;
    }

    private double calculateSequenceAnomaly(BehaviorProfile profile, BehaviorEvent event) {
        // Check if endpoint sequence is normal
        List<String> recentEndpoints = profile.getRecentEndpoints();
        if (recentEndpoints.isEmpty()) return 0.1;
        
        String lastEndpoint = recentEndpoints.get(recentEndpoints.size() - 1);
        String currentEndpoint = normalizeEndpoint(event.getEndpoint());
        
        // Check common sequences
        String sequenceKey = lastEndpoint + "->" + currentEndpoint;
        Map<String, Integer> sequences = profile.getEndpointSequences();
        
        if (!sequences.containsKey(sequenceKey) && profile.getEventCount() > 50) {
            // Unusual sequence for established user
            return 0.6;
        }
        
        return 0.1;
    }

    private double calculateSessionAnomaly(BehaviorProfile profile, BehaviorEvent event) {
        if (event.getSessionId() == null) return 0.5;
        
        // Check session duration and activity
        String sessionKey = "session:activity:" + event.getSessionId();
        Long sessionRequests = redisTemplate.opsForValue().increment(sessionKey);
        
        if (sessionRequests > 1000) {
            // Excessive requests in single session
            return 0.9;
        }
        
        // Check concurrent sessions
        String userSessionsKey = "user:sessions:" + profile.getUserId();
        redisTemplate.opsForSet().add(userSessionsKey, event.getSessionId());
        Long concurrentSessions = redisTemplate.opsForSet().size(userSessionsKey);
        
        if (concurrentSessions > 5) {
            return 0.8; // Too many concurrent sessions
        }
        
        return 0.1;
    }

    private void updateBehaviorProfile(String userId, BehaviorProfile profile, BehaviorEvent event) {
        // Update event count
        profile.setEventCount(profile.getEventCount() + 1);
        profile.setLastActivity(event.getTimestamp());
        
        // Update hourly activity
        LocalTime eventTime = LocalTime.ofInstant(event.getTimestamp(), ZoneId.systemDefault());
        int hour = eventTime.getHour();
        profile.getHourlyActivity().merge(hour, 1, Integer::sum);
        
        // Update endpoint frequency
        String endpoint = normalizeEndpoint(event.getEndpoint());
        profile.getEndpointFrequency().merge(endpoint, 1, Integer::sum);
        
        // Update recent endpoints (sliding window)
        List<String> recentEndpoints = profile.getRecentEndpoints();
        recentEndpoints.add(endpoint);
        if (recentEndpoints.size() > 10) {
            recentEndpoints.remove(0);
        }
        
        // Update endpoint sequences
        if (recentEndpoints.size() > 1) {
            String prevEndpoint = recentEndpoints.get(recentEndpoints.size() - 2);
            String sequence = prevEndpoint + "->" + endpoint;
            profile.getEndpointSequences().merge(sequence, 1, Integer::sum);
        }
        
        // Update average request rate
        updateAverageRequestRate(profile);
        
        // Calculate maturity level
        profile.setMaturityLevel(calculateMaturityLevel(profile));
        
        // Save updated profile
        String profileKey = "behavior:profile:" + userId;
        redisTemplate.opsForValue().set(profileKey, profile, learningPeriodDays, TimeUnit.DAYS);
    }

    private void updateAverageRequestRate(BehaviorProfile profile) {
        if (profile.getFirstActivity() == null) {
            profile.setFirstActivity(Instant.now());
        }
        
        long durationMinutes = Duration.between(profile.getFirstActivity(), Instant.now()).toMinutes();
        if (durationMinutes > 0) {
            double avgRate = (double) profile.getEventCount() / durationMinutes;
            profile.setAverageRequestsPerMinute(avgRate);
        }
    }

    private String calculateMaturityLevel(BehaviorProfile profile) {
        if (profile.getEventCount() < 10) return "NEW";
        if (profile.getEventCount() < 100) return "LEARNING";
        if (profile.getEventCount() < 1000) return "ESTABLISHED";
        return "MATURE";
    }

    private String normalizeEndpoint(String endpoint) {
        // Remove IDs and normalize endpoints
        return endpoint.replaceAll("/\\d+", "/{id}")
                      .replaceAll("/[a-f0-9-]{36}", "/{uuid}");
    }

    private void handleAnomalouseBehavior(String userId, BehaviorEvent event, double anomalyScore,
                                        HttpServletResponse response) throws IOException {
        log.warn("Anomalous behavior detected for user {} with score {}", userId, anomalyScore);
        
        // Log security event
        String alertKey = "security:behavior-anomaly:" + userId;
        Map<String, Object> alert = new HashMap<>();
        alert.put("score", anomalyScore);
        alert.put("event", event);
        alert.put("timestamp", Instant.now());
        redisTemplate.opsForValue().set(alertKey, alert, 1, TimeUnit.HOURS);
        
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
            "{\"error\":\"Unusual activity detected\",\"code\":\"BEHAVIOR_ANOMALY\",\"score\":%.2f}",
            anomalyScore));
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
}

