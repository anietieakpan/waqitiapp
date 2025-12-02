package com.waqiti.common.security.pii.aspects;

import com.waqiti.common.security.pii.ComprehensivePIIProtectionService;
import com.waqiti.common.security.pii.ComprehensivePIIProtectionService.*;
import com.waqiti.common.security.pii.annotations.PIIField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Aspect for automatic PII protection on annotated fields
 * 
 * Features:
 * - Automatic encryption/decryption of PII fields
 * - Role-based access control for PII
 * - Audit trail for PII access
 * - Dynamic masking based on user permissions
 * - GDPR compliance enforcement
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PIIProtectionAspect {

    private final ComprehensivePIIProtectionService piiProtectionService;
    
    /**
     * Intercept repository save operations to encrypt PII fields
     */
    @Around("@annotation(org.springframework.data.repository.Repository) && execution(* save*(..))")
    public Object protectPIIOnSave(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                args[i] = protectPIIFields(args[i], true);
            }
        }
        
        return joinPoint.proceed(args);
    }
    
    /**
     * Intercept repository find operations to decrypt/mask PII fields
     */
    @Around("@annotation(org.springframework.data.repository.Repository) && execution(* find*(..))")
    public Object unprotectPIIOnFind(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        
        if (result != null) {
            if (result instanceof Collection) {
                Collection<?> collection = (Collection<?>) result;
                for (Object item : collection) {
                    unprotectPIIFields(item);
                }
            } else if (result instanceof Optional) {
                Optional<?> optional = (Optional<?>) result;
                optional.ifPresent(this::unprotectPIIFields);
            } else {
                result = unprotectPIIFields(result);
            }
        }
        
        return result;
    }
    
    /**
     * Intercept REST controller responses to mask PII based on permissions
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController) && execution(* *(..))")
    public Object maskPIIInResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        
        if (result != null && shouldMaskResponse()) {
            result = maskPIIFields(result);
        }
        
        return result;
    }
    
    /**
     * Process PII protection for fields in an object
     */
    private Object protectPIIFields(Object obj, boolean encrypt) {
        if (obj == null) {
            return null;
        }
        
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            if (field.isAnnotationPresent(PIIField.class)) {
                PIIField annotation = field.getAnnotation(PIIField.class);
                field.setAccessible(true);
                
                try {
                    Object fieldValue = field.get(obj);
                    if (fieldValue != null && fieldValue instanceof String) {
                        String protectedValue = protectFieldValue(
                            (String) fieldValue, 
                            annotation,
                            encrypt
                        );
                        field.set(obj, protectedValue);
                    }
                } catch (Exception e) {
                    log.error("Failed to protect PII field: {}", field.getName(), e);
                }
            }
        }
        
        return obj;
    }
    
    /**
     * Unprotect PII fields based on user permissions
     */
    private Object unprotectPIIFields(Object obj) {
        if (obj == null) {
            return null;
        }
        
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        for (Field field : fields) {
            if (field.isAnnotationPresent(PIIField.class)) {
                PIIField annotation = field.getAnnotation(PIIField.class);
                field.setAccessible(true);
                
                try {
                    Object fieldValue = field.get(obj);
                    if (fieldValue != null && fieldValue instanceof String) {
                        // Check user permissions
                        if (hasPermissionToAccessPII(annotation, auth)) {
                            // Decrypt/detokenize
                            String unprotectedValue = unprotectFieldValue(
                                (String) fieldValue,
                                annotation
                            );
                            field.set(obj, unprotectedValue);
                        } else {
                            // Apply masking
                            String maskedValue = maskFieldValue(
                                (String) fieldValue,
                                annotation
                            );
                            field.set(obj, maskedValue);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to unprotect PII field: {}", field.getName(), e);
                }
            }
        }
        
        return obj;
    }
    
    /**
     * Mask PII fields for external responses
     */
    private Object maskPIIFields(Object obj) {
        if (obj == null) {
            return null;
        }
        
        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            for (Object item : collection) {
                maskPIIFieldsInObject(item);
            }
        } else {
            maskPIIFieldsInObject(obj);
        }
        
        return obj;
    }
    
    private void maskPIIFieldsInObject(Object obj) {
        if (obj == null) {
            return;
        }
        
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            if (field.isAnnotationPresent(PIIField.class)) {
                PIIField annotation = field.getAnnotation(PIIField.class);
                field.setAccessible(true);
                
                try {
                    Object fieldValue = field.get(obj);
                    if (fieldValue != null && fieldValue instanceof String) {
                        String maskedValue = maskFieldValue((String) fieldValue, annotation);
                        field.set(obj, maskedValue);
                    }
                } catch (Exception e) {
                    log.error("Failed to mask PII field: {}", field.getName(), e);
                }
            }
        }
    }
    
    /**
     * Protect a field value
     */
    private String protectFieldValue(String value, PIIField annotation, boolean encrypt) {
        PIIContext context = buildPIIContext(annotation);
        
        if (encrypt) {
            PIIProtectionResult result = piiProtectionService.protectPII(value, context);
            return result.getProtectedData();
        } else {
            return value; // Already protected
        }
    }
    
    /**
     * Unprotect a field value
     */
    private String unprotectFieldValue(String value, PIIField annotation) {
        PIIContext context = buildPIIContext(annotation);
        
        try {
            return piiProtectionService.unprotectPII(value, context);
        } catch (Exception e) {
            log.error("Failed to unprotect PII value", e);
            return maskFieldValue(value, annotation);
        }
    }
    
    /**
     * Mask a field value based on classification
     */
    private String maskFieldValue(String value, PIIField annotation) {
        // If value is encrypted/tokenized, return masked placeholder
        if (value.startsWith("TOK:") || value.startsWith("v1:")) {
            return getMaskedPlaceholder(annotation.classification());
        }
        
        // Otherwise apply masking
        return applyMasking(value, annotation.classification());
    }
    
    /**
     * Get masked placeholder for classification
     */
    private String getMaskedPlaceholder(PIIClassification classification) {
        switch (classification) {
            case CREDIT_CARD:
                return "**** **** **** ****";
            case SSN:
                return "***-**-****";
            case EMAIL:
                return "****@****.***";
            case PHONE_NUMBER:
                return "***-***-****";
            case NAME:
                return "****";
            case ADDRESS:
                return "****";
            case DATE_OF_BIRTH:
                return "****-**-**";
            case FINANCIAL_ACCOUNT:
                return "****";
            default:
                return "****";
        }
    }
    
    /**
     * Apply masking to actual value
     */
    private String applyMasking(String value, PIIClassification classification) {
        switch (classification) {
            case CREDIT_CARD:
                return maskCreditCard(value);
            case SSN:
                return maskSSN(value);
            case EMAIL:
                return maskEmail(value);
            case PHONE_NUMBER:
                return maskPhone(value);
            case NAME:
                return maskName(value);
            case ADDRESS:
                return maskAddress(value);
            default:
                return maskGeneric(value);
        }
    }
    
    // Masking implementations
    
    private String maskCreditCard(String card) {
        String cleaned = card.replaceAll("[^0-9]", "");
        if (cleaned.length() >= 12) {
            return "**** **** **** " + cleaned.substring(cleaned.length() - 4);
        }
        return "****";
    }
    
    private String maskSSN(String ssn) {
        String cleaned = ssn.replaceAll("[^0-9]", "");
        if (cleaned.length() == 9) {
            return "***-**-" + cleaned.substring(5);
        }
        return "***-**-****";
    }
    
    private String maskEmail(String email) {
        int atIdx = email.indexOf("@");
        if (atIdx > 0 && email.length() > atIdx + 1) {
            String domain = email.substring(atIdx);
            return "****" + domain;
        }
        return "****@****.***";
    }
    
    private String maskPhone(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.length() >= 10) {
            return "***-***-" + cleaned.substring(cleaned.length() - 4);
        }
        return "***-***-****";
    }
    
    private String maskName(String name) {
        if (name.length() > 0) {
            return name.charAt(0) + "****";
        }
        return "****";
    }
    
    private String maskAddress(String address) {
        String[] parts = address.split(",");
        if (parts.length >= 2) {
            return "**** " + parts[parts.length - 1].trim(); // Keep only state/country
        }
        return "****";
    }
    
    private String maskGeneric(String value) {
        if (value.length() > 4) {
            return value.substring(0, 2) + "****";
        }
        return "****";
    }
    
    /**
     * Check if user has permission to access PII
     */
    private boolean hasPermissionToAccessPII(PIIField annotation, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        
        // Check role requirement
        String requiredRole = annotation.minRole();
        boolean hasRole = auth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(requiredRole) ||
                                 authority.getAuthority().equals("ROLE_ADMIN"));
        
        // Check region constraint
        if (annotation.allowedRegions().length > 0) {
            String userRegion = getUserRegion(auth);
            boolean inAllowedRegion = Arrays.asList(annotation.allowedRegions())
                .contains(userRegion);
            
            return hasRole && inAllowedRegion;
        }
        
        return hasRole;
    }
    
    /**
     * Determine if response should be masked
     */
    private boolean shouldMaskResponse() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Mask for unauthenticated users
        if (auth == null || !auth.isAuthenticated()) {
            return true;
        }
        
        // Don't mask for admins
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        
        return !isAdmin;
    }
    
    /**
     * Build PII context from annotation
     */
    private PIIContext buildPIIContext(PIIField annotation) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getName() : "system";
        
        return PIIContext.builder()
            .userId(userId)
            .purpose(annotation.purpose())
            .classification(annotation.classification())
            .dataRegion(getUserRegion(auth))
            .admin(isAdmin(auth))
            .metadata(new HashMap<>())
            .build();
    }
    
    /**
     * Get user's region from authentication
     */
    private String getUserRegion(Authentication auth) {
        // Implementation would extract region from user details
        return "us-east-1"; // Default
    }
    
    /**
     * Check if user is admin
     */
    private boolean isAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        
        return auth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}