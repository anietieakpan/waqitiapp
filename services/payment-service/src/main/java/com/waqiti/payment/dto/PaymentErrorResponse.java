package com.waqiti.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Error Response DTO
 * Standardized error response for payment processing failures
 */
@Data
@Builder
public class PaymentErrorResponse {
    private String errorId;
    private String errorCode;
    private String errorMessage;
    private String userFriendlyMessage;
    private String correlationId;
    private String operationType;
    private LocalDateTime timestamp;
    private String severity;
    private boolean retryable;
    private Map<String, Object> additionalData;
    private String supportReferenceId;
}