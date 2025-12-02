package com.waqiti.security.rasp.detector;

import com.waqiti.security.rasp.RaspRequestWrapper;
import com.waqiti.security.rasp.model.SecurityEvent;
import com.waqiti.security.rasp.model.ThreatLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting detector to prevent abuse and DoS attacks
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitDetector implements AttackDetector {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${rasp.detectors.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${rasp.detectors.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${rasp.detectors.rate-limit.requests-per-hour:1000}")
    private int requestsPerHour;

    @Value("${rasp.detectors.rate-limit.burst-threshold:50}")
    private int burstThreshold;

    @Value("${rasp.detectors.rate-limit.burst-window-seconds:10}")
    private int burstWindowSeconds;

    @Override
    public SecurityEvent detectThreat(RaspRequestWrapper request) {
        if (!enabled) {
            return null;
        }

        String clientIp = getClientIp(request);
        if (clientIp == null) {
            return null;
        }

        // Check for burst attacks (many requests in short time)
        SecurityEvent burstThreat = checkBurstLimit(request, clientIp);
        if (burstThreat != null) {
            return burstThreat;
        }

        // Check per-minute rate limit
        SecurityEvent minuteThreat = checkMinuteLimit(request, clientIp);
        if (minuteThreat != null) {
            return minuteThreat;
        }

        // Check per-hour rate limit
        SecurityEvent hourThreat = checkHourLimit(request, clientIp);
        if (hourThreat != null) {
            return hourThreat;
        }

        return null;
    }

    private SecurityEvent checkBurstLimit(RaspRequestWrapper request, String clientIp) {
        String burstKey = "burst:" + clientIp;
        
        try {
            Long currentCount = redisTemplate.opsForValue().increment(burstKey);
            if (currentCount == 1) {
                redisTemplate.expire(burstKey, Duration.ofSeconds(burstWindowSeconds));
            }

            if (currentCount > burstThreshold) {
                return SecurityEvent.builder()
                    .threatType("RATE_LIMIT_BURST")
                    .description(String.format("Burst rate limit exceeded: %d requests in %d seconds", 
                        currentCount, burstWindowSeconds))
                    .detectorName(getDetectorName())
                    .threatLevel(ThreatLevel.HIGH)
                    .requestCount(currentCount.intValue())
                    .rateLimitWindow(burstWindowSeconds + "s")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error checking burst limit for IP {}: ", clientIp, e);
        }

        return null;
    }

    private SecurityEvent checkMinuteLimit(RaspRequestWrapper request, String clientIp) {
        String minuteKey = "minute:" + clientIp + ":" + (System.currentTimeMillis() / 60000);
        
        try {
            Long currentCount = redisTemplate.opsForValue().increment(minuteKey);
            if (currentCount == 1) {
                redisTemplate.expire(minuteKey, Duration.ofMinutes(1));
            }

            if (currentCount > requestsPerMinute) {
                ThreatLevel level = currentCount > requestsPerMinute * 2 ? 
                    ThreatLevel.HIGH : ThreatLevel.MEDIUM;
                    
                return SecurityEvent.builder()
                    .threatType("RATE_LIMIT_MINUTE")
                    .description(String.format("Per-minute rate limit exceeded: %d requests", currentCount))
                    .detectorName(getDetectorName())
                    .threatLevel(level)
                    .requestCount(currentCount.intValue())
                    .rateLimitWindow("1m")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error checking minute limit for IP {}: ", clientIp, e);
        }

        return null;
    }

    private SecurityEvent checkHourLimit(RaspRequestWrapper request, String clientIp) {
        String hourKey = "hour:" + clientIp + ":" + (System.currentTimeMillis() / 3600000);
        
        try {
            Long currentCount = redisTemplate.opsForValue().increment(hourKey);
            if (currentCount == 1) {
                redisTemplate.expire(hourKey, Duration.ofHours(1));
            }

            if (currentCount > requestsPerHour) {
                ThreatLevel level = currentCount > requestsPerHour * 2 ? 
                    ThreatLevel.CRITICAL : ThreatLevel.MEDIUM;
                    
                return SecurityEvent.builder()
                    .threatType("RATE_LIMIT_HOUR")
                    .description(String.format("Per-hour rate limit exceeded: %d requests", currentCount))
                    .detectorName(getDetectorName())
                    .threatLevel(level)
                    .requestCount(currentCount.intValue())
                    .rateLimitWindow("1h")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error checking hour limit for IP {}: ", clientIp, e);
        }

        return null;
    }

    private String getClientIp(RaspRequestWrapper request) {
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

    @Override
    public String getDetectorName() {
        return "RATE_LIMIT_DETECTOR";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return 5; // Medium priority
    }
}