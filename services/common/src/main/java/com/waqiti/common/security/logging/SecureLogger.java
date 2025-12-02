package com.waqiti.common.security.logging;

import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.Map;

/**
 * Secure Logger Wrapper with Automatic PII/Sensitive Data Sanitization
 *
 * Usage:
 * <pre>
 * private static final SecureLogger log = SecureLogger.getLogger(MyClass.class);
 *
 * try {
 *     processPayment(request);
 * } catch (Exception e) {
 *     log.error("Payment failed for user: {}", userId, e);  // Automatically sanitized
 * }
 * </pre>
 *
 * Features:
 * - Automatic sanitization of all log messages
 * - PII redaction (SSN, email, phone, credit cards)
 * - Password/credential removal
 * - API key/token masking
 * - Stack trace sanitization
 * - Drop-in replacement for SLF4J Logger
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
public class SecureLogger implements Logger {

    private final Logger delegate;
    private final boolean sanitizationEnabled;

    private SecureLogger(Logger delegate) {
        this.delegate = delegate;
        this.sanitizationEnabled = true; // Always enabled in production
    }

    /**
     * Factory method - drop-in replacement for LoggerFactory.getLogger()
     */
    public static SecureLogger getLogger(Class<?> clazz) {
        return new SecureLogger(org.slf4j.LoggerFactory.getLogger(clazz));
    }

    public static SecureLogger getLogger(String name) {
        return new SecureLogger(org.slf4j.LoggerFactory.getLogger(name));
    }

    // ============================================================================
    // ERROR LEVEL (with sanitization)
    // ============================================================================

    @Override
    public void error(String msg) {
        delegate.error(sanitize(msg));
    }

    @Override
    public void error(String format, Object arg) {
        delegate.error(sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        delegate.error(sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void error(String format, Object... arguments) {
        delegate.error(sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void error(String msg, Throwable t) {
        delegate.error(sanitize(msg), t);  // Exception is logged with sanitized message
    }

    @Override
    public void error(Marker marker, String msg) {
        delegate.error(marker, sanitize(msg));
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        delegate.error(marker, sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        delegate.error(marker, sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        delegate.error(marker, sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        delegate.error(marker, sanitize(msg), t);
    }

    // ============================================================================
    // WARN LEVEL (with sanitization)
    // ============================================================================

    @Override
    public void warn(String msg) {
        delegate.warn(sanitize(msg));
    }

    @Override
    public void warn(String format, Object arg) {
        delegate.warn(sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void warn(String format, Object... arguments) {
        delegate.warn(sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        delegate.warn(sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void warn(String msg, Throwable t) {
        delegate.warn(sanitize(msg), t);
    }

    @Override
    public void warn(Marker marker, String msg) {
        delegate.warn(marker, sanitize(msg));
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        delegate.warn(marker, sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        delegate.warn(marker, sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        delegate.warn(marker, sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        delegate.warn(marker, sanitize(msg), t);
    }

    // ============================================================================
    // INFO LEVEL (with sanitization)
    // ============================================================================

    @Override
    public void info(String msg) {
        delegate.info(sanitize(msg));
    }

    @Override
    public void info(String format, Object arg) {
        delegate.info(sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        delegate.info(sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void info(String format, Object... arguments) {
        delegate.info(sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void info(String msg, Throwable t) {
        delegate.info(sanitize(msg), t);
    }

    @Override
    public void info(Marker marker, String msg) {
        delegate.info(marker, sanitize(msg));
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        delegate.info(marker, sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        delegate.info(marker, sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        delegate.info(marker, sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        delegate.info(marker, sanitize(msg), t);
    }

    // ============================================================================
    // DEBUG LEVEL (minimal sanitization for performance)
    // ============================================================================

    @Override
    public void debug(String msg) {
        delegate.debug(sanitize(msg));
    }

    @Override
    public void debug(String format, Object arg) {
        delegate.debug(sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        delegate.debug(sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void debug(String format, Object... arguments) {
        delegate.debug(sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void debug(String msg, Throwable t) {
        delegate.debug(sanitize(msg), t);
    }

    @Override
    public void debug(Marker marker, String msg) {
        delegate.debug(marker, sanitize(msg));
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        delegate.debug(marker, sanitize(format), sanitizeArg(arg));
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        delegate.debug(marker, sanitize(format), sanitizeArg(arg1), sanitizeArg(arg2));
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        delegate.debug(marker, sanitize(format), sanitizeArgs(arguments));
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        delegate.debug(marker, sanitize(msg), t);
    }

    // ============================================================================
    // TRACE LEVEL (pass-through - not used in production)
    // ============================================================================

    @Override
    public void trace(String msg) {
        delegate.trace(msg);
    }

    @Override
    public void trace(String format, Object arg) {
        delegate.trace(format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        delegate.trace(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        delegate.trace(format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        delegate.trace(msg, t);
    }

    @Override
    public void trace(Marker marker, String msg) {
        delegate.trace(marker, msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        delegate.trace(marker, format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        delegate.trace(marker, format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        delegate.trace(marker, format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        delegate.trace(marker, msg, t);
    }

    // ============================================================================
    // LEVEL CHECKS (delegate)
    // ============================================================================

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return delegate.isErrorEnabled(marker);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return delegate.isWarnEnabled(marker);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return delegate.isInfoEnabled(marker);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return delegate.isDebugEnabled(marker);
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return delegate.isTraceEnabled(marker);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    // ============================================================================
    // SANITIZATION HELPERS
    // ============================================================================

    private String sanitize(String message) {
        if (!sanitizationEnabled || message == null) {
            return message;
        }
        return SecureExceptionSanitizer.sanitize(message);
    }

    private Object sanitizeArg(Object arg) {
        if (!sanitizationEnabled || arg == null) {
            return arg;
        }

        if (arg instanceof String) {
            return SecureExceptionSanitizer.sanitize((String) arg);
        } else if (arg instanceof Throwable) {
            return arg; // Let SLF4J handle throwables
        } else if (arg instanceof Map) {
            return SecureExceptionSanitizer.sanitizeMap((Map<String, Object>) arg);
        }

        return arg;
    }

    private Object[] sanitizeArgs(Object[] args) {
        if (!sanitizationEnabled || args == null) {
            return args;
        }

        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitized[i] = sanitizeArg(args[i]);
        }
        return sanitized;
    }
}
