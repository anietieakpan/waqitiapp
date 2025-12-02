package com.waqiti.transaction.security;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback message converter that automatically masks PII in log messages.
 *
 * This converter is registered in logback-spring.xml and automatically
 * applied to all log messages before they are written.
 *
 * Usage in logback-spring.xml:
 * <conversionRule conversionWord="maskedMsg" converterClass="com.waqiti.transaction.security.PiiMaskingConverter" />
 * <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %maskedMsg%n</pattern>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
public class PiiMaskingConverter extends MessageConverter {

    private static final PiiMaskingService piiMaskingService = new PiiMaskingService();

    @Override
    public String convert(ILoggingEvent event) {
        // Get the original message
        String originalMessage = super.convert(event);

        // Apply PII masking
        if (originalMessage != null && !originalMessage.isEmpty()) {
            return piiMaskingService.maskAllPii(originalMessage);
        }

        return originalMessage;
    }
}
