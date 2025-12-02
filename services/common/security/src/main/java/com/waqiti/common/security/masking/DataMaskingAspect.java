package com.waqiti.common.security.masking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * AOP aspect for automatic data masking in method responses
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataMaskingAspect {

    private final DataMaskingService maskingService;

    @Around("@annotation(maskSensitiveData)")
    public Object maskSensitiveData(ProceedingJoinPoint joinPoint, MaskSensitiveData maskSensitiveData) throws Throwable {
        Object result = joinPoint.proceed();
        
        if (result == null) {
            return null;
        }
        
        // Mask the result based on configuration
        return maskObject(result, maskSensitiveData.deep());
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController) && execution(* *(..))")
    public Object maskRestControllerResponses(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        
        if (result == null) {
            return null;
        }
        
        // Check if masking is enabled for this response
        if (shouldMaskResponse(joinPoint)) {
            return maskObject(result, true);
        }
        
        return result;
    }

    private boolean shouldMaskResponse(ProceedingJoinPoint joinPoint) {
        // Check for @NoMasking annotation
        try {
            if (joinPoint.getTarget().getClass().getMethod(
                    joinPoint.getSignature().getName(), 
                    getParameterTypes(joinPoint))
                    .isAnnotationPresent(NoMasking.class)) {
                return false;
            }
        } catch (NoSuchMethodException e) {
            // Method not found, default to masking
        }
        
        return true;
    }

    private Class<?>[] getParameterTypes(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Class<?>[] parameterTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }
        return parameterTypes;
    }

    private Object maskObject(Object obj, boolean deep) {
        if (obj == null) {
            return null;
        }
        
        // Handle collections
        if (obj instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) obj;
            collection.forEach(item -> maskObject(item, deep));
            return obj;
        }
        
        // Handle maps
        if (obj instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) obj;
            map.values().forEach(value -> maskObject(value, deep));
            return obj;
        }
        
        // Handle objects with fields
        maskFields(obj, deep);
        
        return obj;
    }

    private void maskFields(Object obj, boolean deep) {
        Class<?> clazz = obj.getClass();
        
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    
                    // Check for @MaskedData annotation
                    if (field.isAnnotationPresent(MaskedData.class)) {
                        MaskedData maskedData = field.getAnnotation(MaskedData.class);
                        Object value = field.get(obj);
                        
                        if (value instanceof String) {
                            String maskedValue = maskValue((String) value, maskedData.value());
                            field.set(obj, maskedValue);
                        }
                    }
                    
                    // Recursively mask nested objects if deep masking is enabled
                    if (deep && !field.getType().isPrimitive() && !field.getType().getName().startsWith("java.")) {
                        Object nestedObj = field.get(obj);
                        if (nestedObj != null) {
                            maskObject(nestedObj, deep);
                        }
                    }
                } catch (IllegalAccessException e) {
                    log.error("Failed to mask field: {}", field.getName(), e);
                }
            }
            
            clazz = clazz.getSuperclass();
        }
    }

    private String maskValue(String value, MaskingSerializer.MaskingType type) {
        if (value == null) {
            return null;
        }
        
        return switch (type) {
            case EMAIL -> maskingService.maskEmail(value);
            case PHONE -> maskingService.maskPhoneNumber(value);
            case SSN -> maskingService.maskSSN(value);
            case CARD_NUMBER -> maskingService.maskCardNumber(value);
            case ACCOUNT_NUMBER -> maskingService.maskAccountNumber(value);
            case NAME -> maskingService.maskName(value);
            case ADDRESS -> maskingService.maskAddress(value);
            case IP_ADDRESS -> maskingService.maskIpAddress(value);
            case GENERIC -> maskingService.maskGeneric(value);
        };
    }
}