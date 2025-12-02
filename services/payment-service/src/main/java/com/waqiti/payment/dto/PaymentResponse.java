/**
 * Payment Response DTO
 * Response for payment operations
 */
package com.waqiti.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    
    private UUID id;
    private UUID senderId;
    private String senderName;
    private UUID recipientId;
    private String recipientName;
    private String recipientType;
    
    // Payment details
    private BigDecimal amount;
    private String currency;
    private String description;
    private String paymentMethod;
    private String status;
    private String transactionReference;
    
    // Metadata
    private JsonNode metadata;
    private UUID scheduledPaymentId;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime completedAt;
    
    // Additional info
    private String errorMessage;
    private Error error;
    private BigDecimal fee;
    private BigDecimal netAmount;
    private Integer estimatedCompletionDays;
    private String transactionId;

    /**
     * Error details for failed payments
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        private String code;
        private String message;
        private String details;
    }
}