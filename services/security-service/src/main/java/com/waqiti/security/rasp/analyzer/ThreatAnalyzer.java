package com.waqiti.security.rasp.analyzer;

import com.waqiti.security.rasp.RaspRequestWrapper;
import com.waqiti.security.rasp.model.SecurityEvent;
import com.waqiti.security.rasp.model.ThreatLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Analyzes threats and determines appropriate threat levels
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ThreatAnalyzer {

    private final RedisTemplate<String, Object> redisTemplate;

    // Known malicious IPs, user agents, etc.
    private static final Set<String> KNOWN_MALICIOUS_USER_AGENTS = Set.of(
        "sqlmap", "nmap", "nikto", "dirbuster", "gobuster", "burpsuite",
        "owasp zap", "w3af", "acunetix", "nessus", "openvas", "masscan"
    );

    private static final Set<String> TOR_EXIT_NODES = Set.of(
        // This would be populated with known Tor exit node IPs
        // In production, this should be loaded from a dynamic source
    );

    private static final Set<String> SUSPICIOUS_PATHS = Set.of(
        "/admin", "/wp-admin", "/phpmyadmin", "/adminer", "/.env",
        "/config", "/backup", "/test", "/debug", "/.git", "/.svn"
    );

    /**
     * Analyze threat and determine appropriate threat level
     */
    public ThreatLevel analyzeThreat(SecurityEvent event, RaspRequestWrapper request) {
        ThreatLevel baseThreatLevel = event.getThreatLevel();
        
        // Start with the detector's assessment
        int threatScore = baseThreatLevel.getPriority();
        
        // Analyze various risk factors
        threatScore += analyzeClientReputation(request);
        threatScore += analyzeRequestCharacteristics(request);
        threatScore += analyzeAttackSophistication(event, request);
        threatScore += analyzeTargetSensitivity(request);
        threatScore += analyzeAttackFrequency(request);
        
        // Convert score back to threat level
        return determineFinalThreatLevel(threatScore);
    }

    private int analyzeClientReputation(RaspRequestWrapper request) {
        int score = 0;
        
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            String lowerUA = userAgent.toLowerCase();
            
            // Check for known attack tools
            for (String maliciousUA : KNOWN_MALICIOUS_USER_AGENTS) {
                if (lowerUA.contains(maliciousUA)) {
                    score += 2;
                    break;
                }
            }
            
            // Check for suspicious patterns in user agent
            if (userAgent.length() < 10 || userAgent.length() > 500) {
                score += 1;
            }
            
            // Check for missing or suspicious user agent
            if (userAgent.trim().isEmpty() || userAgent.equals("-")) {
                score += 1;
            }
        }
        
        // Check if IP is from Tor network
        String clientIp = getClientIp(request);
        if (TOR_EXIT_NODES.contains(clientIp)) {
            score += 1;
        }
        
        // Check IP reputation (simplified - in production use threat intelligence)
        if (isKnownMaliciousIp(clientIp)) {
            score += 3;
        }
        
        return score;
    }

    private int analyzeRequestCharacteristics(RaspRequestWrapper request) {
        int score = 0;
        
        // Large request bodies are more suspicious
        long requestSize = request.getRequestSize();
        if (requestSize > 100000) { // > 100KB
            score += 2;
        } else if (requestSize > 10000) { // > 10KB
            score += 1;
        }
        
        // Check for suspicious paths
        String uri = request.getRequestURI();
        if (uri != null) {
            for (String suspiciousPath : SUSPICIOUS_PATHS) {
                if (uri.toLowerCase().contains(suspiciousPath)) {
                    score += 1;
                    break;
                }
            }
        }
        
        // Check for unusual HTTP methods
        String method = request.getMethod();
        if (method != null && !Set.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS").contains(method)) {
            score += 1;
        }
        
        // Check for missing common headers
        if (request.getHeader("Accept") == null) {
            score += 1;
        }
        
        return score;
    }

    private int analyzeAttackSophistication(SecurityEvent event, RaspRequestWrapper request) {
        int score = 0;
        
        String payload = event.getAttackPayload();
        if (payload != null) {
            // Check for encoding/obfuscation
            if (payload.contains("%") || payload.contains("\\x") || payload.contains("\\u")) {
                score += 1;
            }
            
            // Check for multiple attack vectors in single request
            int vectorCount = 0;
            if (event.getSqlInjectionVector() != null) vectorCount++;
            if (event.getXssVector() != null) vectorCount++;
            if (event.getCommandInjectionVector() != null) vectorCount++;
            if (event.getPathTraversalVector() != null) vectorCount++;
            
            if (vectorCount > 1) {
                score += 2;
            }
            
            // Check for automated attack patterns
            if (payload.length() > 1000 || payload.split("\\s+").length > 50) {
                score += 1;
            }
        }
        
        return score;
    }

    private int analyzeTargetSensitivity(RaspRequestWrapper request) {
        int score = 0;
        
        String uri = request.getRequestURI();
        if (uri != null) {
            String lowerUri = uri.toLowerCase();
            
            // High-value targets
            if (lowerUri.contains("/api/") || lowerUri.contains("/admin") || 
                lowerUri.contains("/payment") || lowerUri.contains("/auth")) {
                score += 2;
            }
            
            // Database or file operations
            if (lowerUri.contains("/db") || lowerUri.contains("/file") || 
                lowerUri.contains("/upload") || lowerUri.contains("/download")) {
                score += 1;
            }
        }
        
        return score;
    }

    private int analyzeAttackFrequency(RaspRequestWrapper request) {
        int score = 0;
        
        String clientIp = getClientIp(request);
        if (clientIp != null) {
            try {
                String attackKey = "attacks:" + clientIp;
                Long attackCount = redisTemplate.opsForValue().increment(attackKey);
                
                if (attackCount == 1) {
                    redisTemplate.expire(attackKey, Duration.ofHours(1));
                }
                
                // Escalate based on attack frequency
                if (attackCount > 10) {
                    score += 3;
                } else if (attackCount > 5) {
                    score += 2;
                } else if (attackCount > 2) {
                    score += 1;
                }
            } catch (Exception e) {
                log.error("Error analyzing attack frequency for IP {}: ", clientIp, e);
            }
        }
        
        return score;
    }

    private ThreatLevel determineFinalThreatLevel(int score) {
        if (score >= 8) {
            return ThreatLevel.CRITICAL;
        } else if (score >= 5) {
            return ThreatLevel.HIGH;
        } else if (score >= 3) {
            return ThreatLevel.MEDIUM;
        } else {
            return ThreatLevel.LOW;
        }
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

    private boolean isKnownMaliciousIp(String ip) {
        // In production, this would check against threat intelligence feeds
        // For now, return false
        return false;
    }
}