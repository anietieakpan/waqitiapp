package com.waqiti.payment.exception;

public class WebhookProcessingException extends PaymentProcessingException {
    public WebhookProcessingException(String message) {
        super(message, "WEBHOOK_PROCESSING_ERROR");
    }
    
    public WebhookProcessingException(String message, Throwable cause) {
        super(message, "WEBHOOK_PROCESSING_ERROR", cause);
    }
}
