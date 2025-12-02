package com.waqiti.common.ratelimit;

import com.waqiti.common.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Aspect for implementing rate limiting across all services
 * Uses Bucket4j with Redis backend for distributed rate limiting
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitingAspect {

    private final ProxyManager<String> proxyManager;
    
    @Value("${rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;
    
    @Value("${rate-limiting.default.capacity:100}")
    private long defaultCapacity;
    
    @Value("${rate-limiting.default.refill-tokens:100}")
    private long defaultRefillTokens;
    
    @Value("${rate-limiting.default.refill-period-minutes:1}")
    private long defaultRefillPeriodMinutes;

    @Around("@annotation(rateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        if (!rateLimitingEnabled) {
            return joinPoint.proceed();
        }

        String key = resolveKey(joinPoint, rateLimited);
        Bucket bucket = resolveBucket(key, rateLimited);

        if (bucket.tryConsume(rateLimited.tokens())) {
            return joinPoint.proceed();
        } else {
            log.warn("Rate limit exceeded for key: {}", key);
            EstimationProbe probe = bucket.estimateAbilityToConsume(rateLimited.tokens());
            long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            throw new RateLimitExceededException(
                "Rate limit exceeded. Please try again later.",
                waitTimeSeconds
            );
        }
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimited rateLimited) {
        String baseKey = "";
        
        switch (rateLimited.keyType()) {
            case IP:
                baseKey = getClientIp();
                break;
            case USER:
                baseKey = getCurrentUserId();
                break;
            case API_KEY:
                baseKey = getApiKey();
                break;
            case CUSTOM:
                baseKey = resolveCustomKey(joinPoint, rateLimited);
                break;
            case METHOD:
                baseKey = getMethodKey(joinPoint);
                break;
            case GLOBAL:
                baseKey = "global";
                break;
        }
        
        // Add prefix if specified
        String prefix = rateLimited.prefix();
        if (prefix.isEmpty()) {
            prefix = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        }
        
        return prefix + ":" + baseKey;
    }

    private String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
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
        return "unknown";
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private String getApiKey() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey != null && !apiKey.isEmpty()) {
                return apiKey;
            }
        }
        return "no-api-key";
    }

    private String getMethodKey(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
    }

    private String resolveCustomKey(ProceedingJoinPoint joinPoint, RateLimited rateLimited) {
        String expression = rateLimited.customKeyExpression();
        if (expression.isEmpty()) {
            return "custom-default";
        }
        
        // Simple parameter extraction based on index
        if (expression.startsWith("#") && expression.contains("[") && expression.contains("]")) {
            try {
                int paramIndex = Integer.parseInt(expression.substring(expression.indexOf("[") + 1, expression.indexOf("]")));
                Object[] args = joinPoint.getArgs();
                if (paramIndex >= 0 && paramIndex < args.length && args[paramIndex] != null) {
                    return args[paramIndex].toString();
                }
            } catch (Exception e) {
                log.warn("Failed to resolve custom key expression: {}", expression, e);
            }
        }
        
        return expression;
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest();
        }
        return null;
    }

    private Bucket resolveBucket(String key, RateLimited rateLimited) {
        Supplier<BucketConfiguration> configSupplier = () -> {
            long capacity = rateLimited.capacity() > 0 ? rateLimited.capacity() : defaultCapacity;
            long refillTokens = rateLimited.refillTokens() > 0 ? rateLimited.refillTokens() : defaultRefillTokens;
            long refillPeriod = rateLimited.refillPeriodMinutes() > 0 ? rateLimited.refillPeriodMinutes() : defaultRefillPeriodMinutes;
            
            return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(refillTokens, Duration.ofMinutes(refillPeriod))))
                .build();
        };
        
        return proxyManager.builder()
            .build(key, configSupplier);
    }
}