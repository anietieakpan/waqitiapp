/**
 * Sensitive Data Filter for Logback
 * Filters out log messages containing sensitive data patterns
 * Provides configurable filtering with different strictness levels
 */
package com.waqiti.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.regex.Pattern;

/**
 * Logback filter that prevents sensitive data from being logged
 * Can be configured for different environments and strictness levels
 */
public class SensitiveDataFilter extends Filter<ILoggingEvent> {

    private boolean enabled = true;
    
    private boolean strictMode = false;
    
    private String denialMessage = "[FILTERED - SENSITIVE DATA DETECTED]";

    // High-risk patterns that should always be filtered
    private static final Pattern[] HIGH_RISK_PATTERNS = {
        Pattern.compile("(?i)password\\s*[:=]\\s*['\"]?[^\\s'\"]+", Pattern.MULTILINE),
        Pattern.compile("(?i)secret\\s*[:=]\\s*['\"]?[^\\s'\"]+", Pattern.MULTILINE),
        Pattern.compile("(?i)api[_-]?key\\s*[:=]\\s*['\"]?[A-Za-z0-9_-]{20,}", Pattern.MULTILINE),
        Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b", Pattern.MULTILINE), // Credit cards
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b", Pattern.MULTILINE), // SSN
        Pattern.compile("eyJ[A-Za-z0-9_/+-]*={0,2}\\.[A-Za-z0-9_/+-]*={0,2}\\.[A-Za-z0-9_/+-]*={0,2}", Pattern.MULTILINE), // JWT tokens
    };

    // Medium-risk patterns that are filtered in strict mode
    private static final Pattern[] MEDIUM_RISK_PATTERNS = {
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", Pattern.MULTILINE), // Email addresses
        Pattern.compile("\\b(?:\\+?1[-. ]?)?\\(?([0-9]{3})\\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})\\b", Pattern.MULTILINE), // Phone numbers
        Pattern.compile("(?:https?://)(?:[-\\w.])+(?:[:\\d]+)?(?:/(?:[\\w/_.])*(?:\\?(?:[\\w&=%.])*)?(?:#(?:\\w*))?)?", Pattern.MULTILINE), // URLs
        Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b", Pattern.MULTILINE), // IP addresses
    };

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!enabled) {
            return FilterReply.NEUTRAL;
        }

        String message = event.getFormattedMessage();
        if (message == null) {
            return FilterReply.NEUTRAL;
        }

        // Check high-risk patterns (always filtered)
        for (Pattern pattern : HIGH_RISK_PATTERNS) {
            if (pattern.matcher(message).find()) {
                // In production, we might want to completely deny the message
                // For debugging purposes, we can modify it
                return FilterReply.DENY;
            }
        }

        // Check medium-risk patterns (filtered in strict mode)
        if (strictMode) {
            for (Pattern pattern : MEDIUM_RISK_PATTERNS) {
                if (pattern.matcher(message).find()) {
                    return FilterReply.DENY;
                }
            }
        }

        return FilterReply.NEUTRAL;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public void setDenialMessage(String denialMessage) {
        this.denialMessage = denialMessage;
    }
}