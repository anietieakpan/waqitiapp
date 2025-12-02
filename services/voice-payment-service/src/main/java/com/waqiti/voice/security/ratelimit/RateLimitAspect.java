package com.waqiti.voice.security.ratelimit;

import com.waqiti.voice.security.access.SecurityContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

/**
 * Rate Limit Aspect
 *
 * AOP-based rate limiting for service methods
 *
 * Usage:
 * @RateLimited(limitType = RateLimitType.VOICE_COMMAND)
 * public VoiceCommandResponse processVoiceCommand(UUID userId, ...) {
 *     // Automatically rate limited
 * }
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;
    private final SecurityContextService securityContextService;

    /**
     * Intercept methods annotated with @RateLimited
     */
    @Around("@annotation(rateLimited)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        // Get authenticated user
        UUID userId = securityContextService.getAuthenticatedUserId();

        // Check rate limit
        boolean allowed;
        RateLimitType limitType = rateLimited.limitType();

        switch (limitType) {
            case VOICE_COMMAND:
                allowed = rateLimitService.checkVoiceCommandLimit(userId);
                break;
            case ENROLLMENT:
                allowed = rateLimitService.checkEnrollmentLimit(userId);
                break;
            case TRANSACTION:
                allowed = rateLimitService.checkTransactionLimit(userId);
                break;
            case BIOMETRIC_VERIFICATION:
                allowed = rateLimitService.checkBiometricVerificationLimit(userId);
                break;
            case API:
            default:
                allowed = rateLimitService.checkApiLimit(userId);
                break;
        }

        if (!allowed) {
            // Get rate limit info for error message
            RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(
                    userId, limitType.getKey()
            );

            log.warn("RATE LIMIT BLOCKED: userId={}, limitType={}, current={}/{}",
                    userId, limitType, info.getCurrentCount(), info.getMaxRequests());

            throw new RateLimitService.RateLimitExceededException(
                    limitType.getKey(),
                    info.getMaxRequests(),
                    info.getWindowSeconds(),
                    info.getResetInSeconds()
            );
        }

        // Proceed with method execution
        return joinPoint.proceed();
    }

    /**
     * Rate limit annotation
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RateLimited {
        RateLimitType limitType() default RateLimitType.API;
    }

    /**
     * Rate limit types
     */
    public enum RateLimitType {
        VOICE_COMMAND("voice-command"),
        ENROLLMENT("enrollment"),
        TRANSACTION("transaction"),
        BIOMETRIC_VERIFICATION("biometric-verify"),
        API("api");

        private final String key;

        RateLimitType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }
}
