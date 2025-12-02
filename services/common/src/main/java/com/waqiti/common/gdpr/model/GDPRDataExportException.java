package com.waqiti.common.gdpr.model;

/**
 * GDPR Data Export Exception
 */
class GDPRDataExportException extends RuntimeException {
    public GDPRDataExportException(String message, Throwable cause) {
        super(message, cause);
    }
}