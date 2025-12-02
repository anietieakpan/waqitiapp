/**
 * Secure Logback Encoder with Data Masking
 * Automatically masks sensitive data in log messages
 * Integrates with DataMaskingService for comprehensive protection
 */
package com.waqiti.common.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Custom Logback encoder that applies data masking to log messages
 * Ensures sensitive data is never written to log files in plain text
 */
@Slf4j
public class SecureLogbackEncoder extends PatternLayoutEncoder {

    private DataMaskingService dataMaskingService;
    
    public void setDataMaskingService(DataMaskingService dataMaskingService) {
        this.dataMaskingService = dataMaskingService;
    }

    private boolean maskingEnabled = true;

    private boolean validateBeforeLogging = true;

    private String riskThreshold = "MEDIUM";

    private boolean blockHighRiskLogs = false;

    // Performance optimization - pre-compiled patterns for quick checks
    private static final Pattern POTENTIAL_SENSITIVE_DATA = Pattern.compile(
        "(?i)(?:password|secret|token|key|card|ssn|account|email|phone|api[_-]?key)", 
        Pattern.MULTILINE
    );

    @PostConstruct
    public void init() {
        if (dataMaskingService != null) {
            dataMaskingService.init();
            log.info("SecureLogbackEncoder initialized with data masking enabled: {}", maskingEnabled);
        } else {
            log.warn("DataMaskingService not available - data masking disabled");
            maskingEnabled = false;
        }
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (!maskingEnabled || dataMaskingService == null) {
            return super.encode(event);
        }

        try {
            // Create a copy of the event with masked message
            ILoggingEvent maskedEvent = createMaskedEvent(event);
            return super.encode(maskedEvent);
            
        } catch (Exception e) {
            // If masking fails, log the error and return original event
            // to prevent logging system failure
            log.error("Data masking failed for log event", e);
            return super.encode(event);
        }
    }

    /**
     * Create a new logging event with masked sensitive data
     */
    private ILoggingEvent createMaskedEvent(ILoggingEvent originalEvent) {
        String originalMessage = originalEvent.getFormattedMessage();
        
        // Quick check to see if message might contain sensitive data
        if (!POTENTIAL_SENSITIVE_DATA.matcher(originalMessage).find()) {
            return originalEvent; // No potential sensitive data, return original
        }

        // Validate message for sensitive data if enabled
        if (validateBeforeLogging) {
            DataMaskingService.ValidationResult validation = 
                dataMaskingService.validateForSensitiveData(originalMessage);
            
            // Block high-risk logs if configured
            if (blockHighRiskLogs && isHighRisk(validation.getRiskLevel())) {
                return createBlockedEvent(originalEvent, validation);
            }
        }

        // Apply data masking
        String maskedMessage = dataMaskingService.maskSensitiveData(originalMessage);
        
        // Also mask exception messages if present
        String maskedThrowable = null;
        if (originalEvent.getThrowableProxy() != null) {
            String throwableMessage = originalEvent.getThrowableProxy().getMessage();
            if (throwableMessage != null) {
                maskedThrowable = dataMaskingService.maskSensitiveData(throwableMessage);
            }
        }

        return new MaskedLoggingEvent(originalEvent, maskedMessage, maskedThrowable);
    }

    /**
     * Create a blocked event for high-risk logs
     */
    private ILoggingEvent createBlockedEvent(ILoggingEvent originalEvent, 
                                           DataMaskingService.ValidationResult validation) {
        String blockedMessage = String.format(
            "[BLOCKED - SENSITIVE DATA DETECTED] Original log contained: %s (Risk: %s)", 
            validation.getDetectedTypes(), 
            validation.getRiskLevel()
        );
        
        return new MaskedLoggingEvent(originalEvent, blockedMessage, null);
    }

    /**
     * Check if risk level exceeds configured threshold
     */
    private boolean isHighRisk(DataMaskingService.RiskLevel riskLevel) {
        DataMaskingService.RiskLevel threshold;
        try {
            threshold = DataMaskingService.RiskLevel.valueOf(riskThreshold.toUpperCase());
        } catch (IllegalArgumentException e) {
            threshold = DataMaskingService.RiskLevel.MEDIUM;
        }
        
        return riskLevel.ordinal() >= threshold.ordinal();
    }

    // Setter methods for configuration
    public void setMaskingEnabled(boolean maskingEnabled) {
        this.maskingEnabled = maskingEnabled;
    }

    public void setValidateBeforeLogging(boolean validateBeforeLogging) {
        this.validateBeforeLogging = validateBeforeLogging;
    }

    public void setRiskThreshold(String riskThreshold) {
        this.riskThreshold = riskThreshold;
    }

    public void setBlockHighRiskLogs(boolean blockHighRiskLogs) {
        this.blockHighRiskLogs = blockHighRiskLogs;
    }
}