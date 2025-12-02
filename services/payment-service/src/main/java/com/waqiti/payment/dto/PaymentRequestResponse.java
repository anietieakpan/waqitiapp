package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID; /**
 * Response for payment request operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentRequestResponse {
    private UUID id;
    private UUID requestorId;
    private UUID recipientId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String referenceNumber;
    private UUID transactionId;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Added user information (populated from User service)
    private String requestorName;
    private String recipientName;
}
