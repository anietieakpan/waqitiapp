package com.waqiti.common.kyc.aspect;

import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.exception.KYCServiceUnavailableException;
import com.waqiti.common.kyc.exception.KYCVerificationRequiredException;
import com.waqiti.common.kyc.service.KYCClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class KYCVerificationAspect {

    private final KYCClientService kycClientService;

    @Around("@annotation(requireKYCVerification)")
    public Object verifyKYC(ProceedingJoinPoint joinPoint, RequireKYCVerification requireKYCVerification) throws Throwable {
        
        String userId = getCurrentUserId();
        if (userId == null) {
            throw new KYCVerificationRequiredException("User authentication required for KYC verification");
        }

        try {
            boolean hasRequiredVerification = checkKYCRequirement(userId, requireKYCVerification);
            
            if (!hasRequiredVerification) {
                String message = requireKYCVerification.message().isEmpty() ? 
                    "KYC verification required at level: " + requireKYCVerification.level() :
                    requireKYCVerification.message();
                throw new KYCVerificationRequiredException(message);
            }
            
            return joinPoint.proceed();
            
        } catch (KYCServiceUnavailableException e) {
            if (requireKYCVerification.allowOnServiceUnavailable()) {
                log.warn("KYC service unavailable but allowing operation for user: {}", userId);
                return joinPoint.proceed();
            } else {
                throw new KYCVerificationRequiredException("KYC verification service unavailable", e);
            }
        }
    }

    @Around("@within(requireKYCVerification)")
    public Object verifyKYCForClass(ProceedingJoinPoint joinPoint, RequireKYCVerification requireKYCVerification) throws Throwable {
        return verifyKYC(joinPoint, requireKYCVerification);
    }

    private boolean checkKYCRequirement(String userId, RequireKYCVerification annotation) {
        if (!annotation.action().isEmpty()) {
            return kycClientService.canUserPerformAction(userId, annotation.action());
        } else {
            return kycClientService.isUserVerified(userId, annotation.level().name());
        }
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // Extract user ID from authentication principal
            Object principal = authentication.getPrincipal();
            if (principal instanceof String) {
                return (String) principal;
            }
            // Handle custom user details if needed
            // return ((CustomUserDetails) principal).getUserId();
        }
        return null;
    }
}