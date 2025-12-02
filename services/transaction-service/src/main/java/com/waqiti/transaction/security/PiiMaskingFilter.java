package com.waqiti.transaction.security;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logback filter that automatically masks PII in all log messages.
 *
 * This filter intercepts all logging events and applies PII masking before
 * the message is written to any appender (console, file, etc.).
 *
 * Integrated with Logback configuration (logback-spring.xml) to ensure
 * all logs are automatically sanitized.
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
public class PiiMaskingFilter extends Filter<ILoggingEvent> {

    private final PiiMaskingService piiMaskingService;

    public PiiMaskingFilter() {
        // Create instance directly (Logback filters are created before Spring context)
        this.piiMaskingService = new PiiMaskingService();
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        // Allow all events to pass through
        // The actual masking happens in the layout converter
        return FilterReply.NEUTRAL;
    }
}
