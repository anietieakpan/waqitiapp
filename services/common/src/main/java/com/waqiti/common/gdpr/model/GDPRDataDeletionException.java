package com.waqiti.common.gdpr.model;

/**
 * GDPR Data Deletion Exception
 */
class GDPRDataDeletionException extends RuntimeException {
    public GDPRDataDeletionException(String message, Throwable cause) {
        super(message, cause);
    }
}
