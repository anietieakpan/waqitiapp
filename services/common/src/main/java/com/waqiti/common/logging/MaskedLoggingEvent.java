/**
 * Masked Logging Event Implementation
 * Wrapper around ILoggingEvent that provides masked message content
 * Maintains all original event properties while protecting sensitive data
 */
package com.waqiti.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom logging event that wraps original event with masked sensitive data
 * Provides transparent data masking without affecting logging infrastructure
 */
public class MaskedLoggingEvent implements ILoggingEvent {
    
    private static final Logger logger = LoggerFactory.getLogger(MaskedLoggingEvent.class);

    private final ILoggingEvent originalEvent;
    private final String maskedMessage;
    private final String maskedThrowableMessage;
    private final IThrowableProxy maskedThrowableProxy;

    public MaskedLoggingEvent(ILoggingEvent originalEvent, String maskedMessage, String maskedThrowableMessage) {
        this.originalEvent = originalEvent;
        this.maskedMessage = maskedMessage;
        this.maskedThrowableMessage = maskedThrowableMessage;
        
        // Create masked throwable proxy if needed
        if (maskedThrowableMessage != null && originalEvent.getThrowableProxy() != null) {
            this.maskedThrowableProxy = new MaskedThrowableProxy(
                originalEvent.getThrowableProxy(), 
                maskedThrowableMessage
            );
        } else {
            this.maskedThrowableProxy = originalEvent.getThrowableProxy();
        }
    }

    @Override
    public String getThreadName() {
        return originalEvent.getThreadName();
    }

    @Override
    public Level getLevel() {
        return originalEvent.getLevel();
    }

    @Override
    public String getMessage() {
        return maskedMessage;
    }

    @Override
    public Object[] getArgumentArray() {
        // Return empty array to prevent original arguments from being used
        return new Object[0];
    }

    @Override
    public String getFormattedMessage() {
        return maskedMessage;
    }

    @Override
    public String getLoggerName() {
        return originalEvent.getLoggerName();
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
        return originalEvent.getLoggerContextVO();
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
        return maskedThrowableProxy;
    }

    @Override
    public StackTraceElement[] getCallerData() {
        return originalEvent.getCallerData();
    }

    @Override
    public boolean hasCallerData() {
        return originalEvent.hasCallerData();
    }

    @Override
    public Marker getMarker() {
        return originalEvent.getMarker();
    }

    @Override
    public Map<String, String> getMDCPropertyMap() {
        Map<String, String> originalMDC = originalEvent.getMDCPropertyMap();
        if (originalMDC == null || originalMDC.isEmpty()) {
            return originalMDC;
        }
        
        // Create a copy of MDC and mask sensitive values
        Map<String, String> maskedMDC = new java.util.HashMap<>(originalMDC);
        
        // Common MDC keys that might contain sensitive data
        String[] sensitiveKeys = {
            "userId", "sessionId", "accountId", "transactionId", 
            "customerId", "token", "apiKey", "password", "secret"
        };
        
        for (String key : sensitiveKeys) {
            if (maskedMDC.containsKey(key)) {
                String value = maskedMDC.get(key);
                if (value != null && value.length() > 4) {
                    maskedMDC.put(key, value.substring(0, 2) + "***" + 
                        (value.length() > 6 ? value.substring(value.length() - 2) : ""));
                } else {
                    maskedMDC.put(key, "***");
                }
            }
        }
        
        return maskedMDC;
    }

    @Override
    public Map<String, String> getMdc() {
        return getMDCPropertyMap();
    }

    @Override
    public long getTimeStamp() {
        return originalEvent.getTimeStamp();
    }

    @Override
    public void prepareForDeferredProcessing() {
        originalEvent.prepareForDeferredProcessing();
    }
    
    /**
     * Provides key-value pairs for structured logging.
     * This method was added in newer versions of Logback for better structured logging support.
     * 
     * We implement a robust solution that:
     * 1. Attempts to delegate to the original event if it supports the method
     * 2. Falls back to an empty list for compatibility with older Logback versions
     * 3. Ensures no runtime errors regardless of Logback version
     * 
     * @return List of key-value pairs or empty list for compatibility
     */
    @Override
    public List<KeyValuePair> getKeyValuePairs() {
        try {
            // Use reflection to check if the original event supports this method
            // This ensures compatibility across different Logback versions
            java.lang.reflect.Method method = originalEvent.getClass().getMethod("getKeyValuePairs");
            if (method != null) {
                @SuppressWarnings("unchecked")
                List<KeyValuePair> originalPairs = (List<KeyValuePair>) method.invoke(originalEvent);
                
                if (originalPairs == null) {
                    return new ArrayList<>();
                }
                
                // Optionally mask sensitive key-value pairs here
                List<KeyValuePair> maskedPairs = new ArrayList<>();
                for (KeyValuePair pair : originalPairs) {
                    if (isSensitiveKey(pair.key)) {
                        // Mask sensitive values - KeyValuePair.value is a public field
                        maskedPairs.add(new KeyValuePair(pair.key, maskValue(pair.value)));
                    } else {
                        maskedPairs.add(pair);
                    }
                }
                return maskedPairs;
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist in this version of Logback
            // This is expected for older versions, not an error
        } catch (Exception e) {
            // Log the error but don't fail - robust error handling
            // Use static logger to avoid recursion issues
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to get key-value pairs from logging event: {}", e.getMessage());
            }
        }
        
        // Return empty list as safe fallback for compatibility
        return new ArrayList<>();
    }
    
    /**
     * Implementation of getSequenceNumber() for ILoggingEvent interface
     * This method was added in newer versions of Logback
     * 
     * @return The sequence number from the original event or 0 as fallback
     */
    @Override
    public long getSequenceNumber() {
        try {
            // Use reflection to check if the original event supports this method
            java.lang.reflect.Method method = originalEvent.getClass().getMethod("getSequenceNumber");
            if (method != null) {
                return (Long) method.invoke(originalEvent);
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist in this version of Logback
            // This is expected for older versions, not an error
        } catch (Exception e) {
            // Log the error but don't fail - robust error handling
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to get sequence number from logging event: {}", e.getMessage());
            }
        }
        
        // Return 0 as safe fallback for compatibility
        return 0L;
    }
    
    /**
     * Implementation of getNanoseconds() for ILoggingEvent interface
     * This method was added in newer versions of Logback for high-precision timestamps
     * 
     * @return The nanoseconds from the original event or 0 as fallback
     */
    @Override
    public int getNanoseconds() {
        try {
            // Use reflection to check if the original event supports this method
            java.lang.reflect.Method method = originalEvent.getClass().getMethod("getNanoseconds");
            if (method != null) {
                return (Integer) method.invoke(originalEvent);
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist in this version of Logback
            // This is expected for older versions, not an error
        } catch (Exception e) {
            // Log the error but don't fail - robust error handling
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to get nanoseconds from logging event: {}", e.getMessage());
            }
        }
        
        // Return 0 as safe fallback for compatibility
        return 0;
    }
    
    /**
     * Check if a key contains sensitive information
     */
    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") || 
               lowerKey.contains("token") || 
               lowerKey.contains("apikey") || 
               lowerKey.contains("api_key") ||
               lowerKey.contains("credit") ||
               lowerKey.contains("ssn");
    }
    
    /**
     * Mask a sensitive value
     */
    private Object maskValue(Object value) {
        if (value == null) return null;
        String strValue = value.toString();
        if (strValue.length() <= 4) {
            return "****";
        }
        return strValue.substring(0, 2) + "****" + 
               (strValue.length() > 6 ? strValue.substring(strValue.length() - 2) : "");
    }

    /**
     * Custom throwable proxy that masks sensitive data in exception messages
     */
    private static class MaskedThrowableProxy implements IThrowableProxy {
        private final IThrowableProxy originalProxy;
        private final String maskedMessage;

        public MaskedThrowableProxy(IThrowableProxy originalProxy, String maskedMessage) {
            this.originalProxy = originalProxy;
            this.maskedMessage = maskedMessage;
        }

        @Override
        public String getMessage() {
            return maskedMessage;
        }

        @Override
        public String getClassName() {
            return originalProxy.getClassName();
        }

        @Override
        public StackTraceElementProxy[] getStackTraceElementProxyArray() {
            return originalProxy.getStackTraceElementProxyArray();
        }

        @Override
        public int getCommonFrames() {
            return originalProxy.getCommonFrames();
        }

        @Override
        public IThrowableProxy getCause() {
            return originalProxy.getCause();
        }

        @Override
        public IThrowableProxy[] getSuppressed() {
            return originalProxy.getSuppressed();
        }

        @Override
        public boolean isCyclic() {
            return originalProxy.isCyclic();
        }
    }
    
    /**
     * Get marker list (for Logback compatibility)
     */
    @Override
    public java.util.List<org.slf4j.Marker> getMarkerList() {
        // Use reflection to check if the method exists in the original event
        try {
            java.lang.reflect.Method method = originalEvent.getClass().getMethod("getMarkerList");
            if (method != null) {
                return (java.util.List<org.slf4j.Marker>) method.invoke(originalEvent);
            }
        } catch (Exception e) {
            // Method doesn't exist in this version
        }
        // Return empty list if not available
        return new java.util.ArrayList<>();
    }

}