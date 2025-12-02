package com.waqiti.common.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Custom exception handler for async operations
 */
@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    
    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("Uncaught exception in async method: {} with parameters: {}", 
            method.getName(), Arrays.toString(params), ex);
            
        // Additional error handling logic could be added here
        // For example: send alerts, record metrics, etc.
    }
}