package com.waqiti.common.validation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Interceptor that applies automatic input validation to controller methods
 * based on annotations and method parameters
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationInterceptor implements HandlerInterceptor {

    private final InputValidationService inputValidationService;

    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        
        // Check if method requires validation
        if (method.isAnnotationPresent(ValidateInput.class)) {
            ValidateInput validateAnnotation = method.getAnnotation(ValidateInput.class);
            
            // Perform basic security validation on request parameters
            ValidationResult result = validateRequestParameters(request, validateAnnotation);
            
            if (!result.isValid()) {
                handleValidationFailure(response, result);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate request parameters for security threats
     */
    private ValidationResult validateRequestParameters(HttpServletRequest request, 
                                                     ValidateInput annotation) {
        // Validate query parameters
        if (annotation.validateQueryParams()) {
            request.getParameterMap().forEach((name, values) -> {
                Arrays.stream(values).forEach(value -> {
                    ValidationResult paramResult = inputValidationService.validateSecureText(value, name);
                    if (!paramResult.isValid()) {
                        log.warn("Validation failed for query parameter '{}': {}", name, value);
                    }
                });
            });
        }
        
        // Validate headers for potential security threats
        if (annotation.validateHeaders()) {
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                String headerValue = request.getHeader(headerName);
                ValidationResult headerResult = inputValidationService.validateSecureText(headerValue, "header." + headerName);
                if (!headerResult.isValid()) {
                    log.warn("Validation failed for header '{}': {}", headerName, headerValue);
                }
            });
        }
        
        // For now, return success - full validation happens in controller methods
        return ValidationResult.success();
    }
    
    /**
     * Handle validation failure by returning appropriate error response
     */
    private void handleValidationFailure(HttpServletResponse response, 
                                       ValidationResult validationResult) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        
        StringBuilder jsonResponse = new StringBuilder();
        jsonResponse.append("{\"error\":\"Validation failed\",\"details\":[");
        
        for (int i = 0; i < validationResult.getErrors().size(); i++) {
            ValidationError error = validationResult.getErrors().get(i);
            if (i > 0) {
                jsonResponse.append(",");
            }
            jsonResponse.append(String.format(
                "{\"field\":\"%s\",\"code\":\"%s\",\"message\":\"%s\",\"severity\":\"%s\"}",
                error.getField(), error.getCode(), error.getMessage(), error.getSeverity()
            ));
        }
        
        jsonResponse.append("]}");
        
        response.getWriter().write(jsonResponse.toString());
        response.getWriter().flush();
        
        log.warn("VALIDATION_FAILURE: {}", validationResult.getErrorSummary());
    }
}