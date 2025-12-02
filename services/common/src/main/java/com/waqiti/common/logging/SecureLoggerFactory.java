/**
 * Secure Logger Factory with Built-in Data Masking
 * Provides enhanced loggers that automatically mask sensitive data
 * Maintains compatibility with standard SLF4J Logger interface
 */
package com.waqiti.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Factory for creating secure loggers with automatic data masking
 * Wraps standard SLF4J loggers with security enhancements
 */
/**
 * Pure Spring DI implementation - inject this factory wherever you need secure logging
 *
 * Usage:
 * @Autowired
 * public MyService(SecureLoggerFactory loggerFactory) {
 *     this.log = loggerFactory.getLogger(MyService.class);
 * }
 *
 * Thread-Safety: Fully thread-safe via ConcurrentHashMap
 */
@Slf4j
@Component
public class SecureLoggerFactory {

    private final DataMaskingService dataMaskingService;

    // Cache of secure loggers - thread-safe
    private final ConcurrentMap<String, SecureLogger> secureLoggerCache = new ConcurrentHashMap<>();

    /**
     * Constructor with required DataMaskingService dependency
     * @param dataMaskingService Service for masking sensitive data in logs
     */
    @Autowired
    public SecureLoggerFactory(DataMaskingService dataMaskingService) {
        this.dataMaskingService = dataMaskingService;
        log.info("SecureLoggerFactory initialized with DataMaskingService");
    }

    /**
     * Get a secure logger for the specified class
     * @param clazz Class for which to create the logger
     * @return SecureLogger instance with automatic data masking
     */
    public SecureLogger getLogger(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        return getSecureLogger(clazz.getName());
    }

    /**
     * Get a secure logger for the specified name
     * @param name Logger name
     * @return SecureLogger instance with automatic data masking
     */
    public SecureLogger getLogger(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Logger name cannot be null or empty");
        }
        return getSecureLogger(name);
    }

    /**
     * Internal method to get or create secure logger
     * Uses cache for performance
     */
    private SecureLogger getSecureLogger(String name) {
        return secureLoggerCache.computeIfAbsent(name, this::createSecureLogger);
    }

    /**
     * Create a new secure logger instance
     */
    private SecureLogger createSecureLogger(String name) {
        Logger standardLogger = LoggerFactory.getLogger(name);
        return new SecureLogger(standardLogger, dataMaskingService);
    }

    /**
     * Secure logger wrapper that applies data masking
     */
    public static class SecureLogger implements Logger {
        
        private final Logger delegate;
        private final DataMaskingService maskingService;

        public SecureLogger(Logger delegate, DataMaskingService maskingService) {
            this.delegate = delegate;
            this.maskingService = maskingService;
        }

        /**
         * Mask message if masking service is available
         */
        private String maskMessage(String message) {
            if (maskingService != null && message != null) {
                return maskingService.maskSensitiveData(message);
            }
            return message;
        }

        /**
         * Mask array of arguments
         */
        private Object[] maskArguments(Object[] arguments) {
            if (maskingService == null || arguments == null) {
                return arguments;
            }
            
            Object[] maskedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i] instanceof String) {
                    maskedArgs[i] = maskingService.maskSensitiveData((String) arguments[i]);
                } else if (arguments[i] != null) {
                    maskedArgs[i] = maskingService.maskSensitiveData(arguments[i].toString());
                } else {
                    maskedArgs[i] = null;
                }
            }
            return maskedArgs;
        }

        // SLF4J Logger interface implementation with masking

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public boolean isTraceEnabled() {
            return delegate.isTraceEnabled();
        }

        @Override
        public void trace(String msg) {
            delegate.trace(maskMessage(msg));
        }

        @Override
        public void trace(String format, Object arg) {
            delegate.trace(maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.trace(maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void trace(String format, Object... arguments) {
            delegate.trace(maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void trace(String msg, Throwable t) {
            delegate.trace(maskMessage(msg), t);
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return delegate.isTraceEnabled(marker);
        }

        @Override
        public void trace(Marker marker, String msg) {
            delegate.trace(marker, maskMessage(msg));
        }

        @Override
        public void trace(Marker marker, String format, Object arg) {
            delegate.trace(marker, maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void trace(Marker marker, String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.trace(marker, maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void trace(Marker marker, String format, Object... arguments) {
            delegate.trace(marker, maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void trace(Marker marker, String msg, Throwable t) {
            delegate.trace(marker, maskMessage(msg), t);
        }

        @Override
        public boolean isDebugEnabled() {
            return delegate.isDebugEnabled();
        }

        @Override
        public void debug(String msg) {
            delegate.debug(maskMessage(msg));
        }

        @Override
        public void debug(String format, Object arg) {
            delegate.debug(maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.debug(maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void debug(String format, Object... arguments) {
            delegate.debug(maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void debug(String msg, Throwable t) {
            delegate.debug(maskMessage(msg), t);
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return delegate.isDebugEnabled(marker);
        }

        @Override
        public void debug(Marker marker, String msg) {
            delegate.debug(marker, maskMessage(msg));
        }

        @Override
        public void debug(Marker marker, String format, Object arg) {
            delegate.debug(marker, maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void debug(Marker marker, String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.debug(marker, maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void debug(Marker marker, String format, Object... arguments) {
            delegate.debug(marker, maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void debug(Marker marker, String msg, Throwable t) {
            delegate.debug(marker, maskMessage(msg), t);
        }

        @Override
        public boolean isInfoEnabled() {
            return delegate.isInfoEnabled();
        }

        @Override
        public void info(String msg) {
            delegate.info(maskMessage(msg));
        }

        @Override
        public void info(String format, Object arg) {
            delegate.info(maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.info(maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void info(String format, Object... arguments) {
            delegate.info(maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void info(String msg, Throwable t) {
            delegate.info(maskMessage(msg), t);
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return delegate.isInfoEnabled(marker);
        }

        @Override
        public void info(Marker marker, String msg) {
            delegate.info(marker, maskMessage(msg));
        }

        @Override
        public void info(Marker marker, String format, Object arg) {
            delegate.info(marker, maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void info(Marker marker, String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.info(marker, maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void info(Marker marker, String format, Object... arguments) {
            delegate.info(marker, maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void info(Marker marker, String msg, Throwable t) {
            delegate.info(marker, maskMessage(msg), t);
        }

        @Override
        public boolean isWarnEnabled() {
            return delegate.isWarnEnabled();
        }

        @Override
        public void warn(String msg) {
            delegate.warn(maskMessage(msg));
        }

        @Override
        public void warn(String format, Object arg) {
            delegate.warn(maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.warn(maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void warn(String format, Object... arguments) {
            delegate.warn(maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void warn(String msg, Throwable t) {
            delegate.warn(maskMessage(msg), t);
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return delegate.isWarnEnabled(marker);
        }

        @Override
        public void warn(Marker marker, String msg) {
            delegate.warn(marker, maskMessage(msg));
        }

        @Override
        public void warn(Marker marker, String format, Object arg) {
            delegate.warn(marker, maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void warn(Marker marker, String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.warn(marker, maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void warn(Marker marker, String format, Object... arguments) {
            delegate.warn(marker, maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void warn(Marker marker, String msg, Throwable t) {
            delegate.warn(marker, maskMessage(msg), t);
        }

        @Override
        public boolean isErrorEnabled() {
            return delegate.isErrorEnabled();
        }

        @Override
        public void error(String msg) {
            delegate.error(maskMessage(msg));
        }

        @Override
        public void error(String format, Object arg) {
            delegate.error(maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.error(maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void error(String format, Object... arguments) {
            delegate.error(maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void error(String msg, Throwable t) {
            delegate.error(maskMessage(msg), t);
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return delegate.isErrorEnabled(marker);
        }

        @Override
        public void error(Marker marker, String msg) {
            delegate.error(marker, maskMessage(msg));
        }

        @Override
        public void error(Marker marker, String format, Object arg) {
            delegate.error(marker, maskMessage(format), maskArguments(new Object[]{arg})[0]);
        }

        @Override
        public void error(Marker marker, String format, Object arg1, Object arg2) {
            Object[] masked = maskArguments(new Object[]{arg1, arg2});
            delegate.error(marker, maskMessage(format), masked[0], masked[1]);
        }

        @Override
        public void error(Marker marker, String format, Object... arguments) {
            delegate.error(marker, maskMessage(format), maskArguments(arguments));
        }

        @Override
        public void error(Marker marker, String msg, Throwable t) {
            delegate.error(marker, maskMessage(msg), t);
        }
    }
}